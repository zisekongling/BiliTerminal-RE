package com.RobinNotBad.BiliClient.activity.settings.login

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.Guideline
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.SplashActivity
import com.RobinNotBad.BiliClient.activity.base.BaseFragment
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.api.CookiesApi
import com.RobinNotBad.BiliClient.api.LoginApi
import com.RobinNotBad.BiliClient.util.AccountManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.Cookies
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.card.MaterialCardView
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Timer
import java.util.TimerTask

class QRLoginFragment : BaseFragment() {
    private lateinit var qrImageView: ImageView
    private lateinit var scanStat: TextView
    private lateinit var modeSwitchBtn: TextView
    var QRImage: Bitmap? = null
    var timer: Timer? = null
    var need_refresh: Boolean = false
    var from_setup: Boolean = false
    var isTVMode: Boolean = false

    /** 本轮轮询已经发出的次数，用来对齐官方的分档节奏与总量上限。 */
    private var pollCount = 0

    /** 连续失败次数，见 onPollFailure()。 */
    private var pollFailCount = 0

    /**
     * 轮询是否已被显式停掉。
     *
     * 现在每个 tick 结束时会自己排下一发，所以"停"必须有标志位——否则已经 cancel 的那一拍
     * 跑完还会把下一发补上，等于白停。UI 线程写、Timer 线程读，故 volatile。
     */
    @Volatile
    private var pollStopped = true

    /**
     * 二维码缩放档位。默认中号。
     *
     * 原默认 LARGE 是 0.01/0.99 —— 卡片几乎占满屏宽，而二维码卡片是 1:1 正方形，
     * 横屏/平板上高度会超出视口，状态提示文字被挤出屏幕。现在默认 0.15/0.85（70% 宽），
     * 点击二维码仍在 大→中→小 之间循环。
     */
    private enum class QrScale(val left: Float, val right: Float, @androidx.annotation.StringRes val tipRes: Int) {
        SMALL(0.25f, 0.75f, R.string.a11y_qr_zoom_medium),
        MEDIUM(0.15f, 0.85f, R.string.a11y_qr_zoom_large),
        LARGE(0.05f, 0.95f, R.string.a11y_qr_zoom_small)
    }

    private var qrScale = QrScale.MEDIUM

    companion object {
        /** 轮询总量上限。官方客户端约 90 次就不再续 poll，改让用户重新取码。 */
        private const val MAX_POLL_COUNT = 90

        /**
         * 连续失败多少次才放弃轮询。
         *
         * 1 次抖动就停会把页面搞成"静默死亡"（用户以为还在等），一直重试又会在真没网时白烧电；
         * 3 次（约 9 秒）刚好能跨过一次瞬时断网。
         */
        private const val MAX_POLL_FAIL = 3

        /** 单次失败后的重试间隔。 */
        private const val POLL_RETRY_DELAY_MS = 3000L

        /** 首次 poll 的延迟，给二维码渲染留出时间。 */
        private const val FIRST_POLL_DELAY_MS = 500L

        fun newInstance(from_setup: Boolean): QRLoginFragment {
            val args = Bundle()
            args.putBoolean("from_setup", from_setup)
            val fragment = QRLoginFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstance: Bundle?) {
        super.onCreate(savedInstance)

        val bundle = arguments
        if (bundle != null) from_setup = bundle.getBoolean("from_setup", false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_qr_login, container, false)

        qrImageView = view.findViewById(R.id.qrImage)
        scanStat = view.findViewById(R.id.scanStat)
        modeSwitchBtn = view.findViewById(R.id.modeSwitchBtn)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val switchAccount = view.findViewById<MaterialCardView>(R.id.switchAccount)
        switchAccount.setOnClickListener {
            val intent = Intent(requireContext(), AccountSwitchActivity::class.java)
            startActivity(intent)
            // 必须走 stopPolling() 而不是 timer?.cancel()：现在每个 tick 会自己排下一发，
            // 光 cancel 标志位没置上，正在跑的那一拍结束时会把它续回来
            stopPolling()
            if (isAdded) requireActivity().finish()
        }

        val jump = view.findViewById<MaterialCardView>(R.id.jump)
        jump.setOnClickListener {
            if (from_setup) startActivity(Intent(requireContext(), SplashActivity::class.java))
            stopPolling()
            if (isAdded) requireActivity().finish()
        }

        val special = view.findViewById<MaterialCardView>(R.id.special)
        special.setOnClickListener {
            val intent = Intent(requireContext(), SpecialLoginActivity::class.java)
            intent.putExtra("from_setup", from_setup)
            startActivity(intent)
            stopPolling()
            if (isAdded) requireActivity().finish()
        }

        val loginModeSwitch = view.findViewById<MaterialCardView>(R.id.loginModeSwitch)
        loginModeSwitch.setOnClickListener {
            // 一次点击就要重建二维码并重启轮询：卡片在取码期间由 refreshQrCode() 禁用，
            // 否则连点会算两次模式、并起两套 Timer 打同一张码
            isTVMode = !isTVMode
            updateModeUI()
            stopPolling()
            if (isAdded) refreshQrCode()
        }

        val helpIcon = view.findViewById<ImageView>(R.id.helpIcon)
        helpIcon.setOnClickListener {
            startActivity(Intent(requireContext(), QRLoginHelpActivity::class.java))
        }

        qrImageView.setOnClickListener {
            if (need_refresh) {
                // 加载图、禁用、停旧轮询都交给 refreshQrCode() 一处处理，避免两处状态写岔
                refreshQrCode()
            } else {
                qrScale = when (qrScale) {
                    QrScale.LARGE -> QrScale.MEDIUM
                    QrScale.MEDIUM -> QrScale.SMALL
                    QrScale.SMALL -> QrScale.LARGE
                }
                applyQrScale()
                MsgUtil.showMsg(getString(qrScale.tipRes))
            }
        }

        // 默认大号二维码
        applyQrScale()

        if (isAdded) refreshQrCode()
    }

    /**
     * 把当前档位写入两条 Guideline。
     *
     * 注意：Guideline 自身 onMeasure 恒为 0×0，改动 guidePercent 不会让父级
     * ConstraintLayout 重新测量（它又是 wrap_content 嵌在 ScrollView 里的），
     * 所以必须自己调 requestLayout()，否则界面不会有任何变化。
     */
    private fun applyQrScale() {
        val root = view ?: return
        val guidelineLeft = root.findViewById<Guideline>(R.id.guideline33) ?: return
        val guidelineRight = root.findViewById<Guideline>(R.id.guideline34) ?: return
        guidelineLeft.setGuidelinePercent(qrScale.left)
        guidelineRight.setGuidelinePercent(qrScale.right)
        root.requestLayout()
    }

    private fun updateModeUI() {
        if (isTVMode) {
            modeSwitchBtn.text = getAppContext().getString(R.string.text_qr_mode_tv)
            scanStat.text = getAppContext().getString(R.string.text_qr_stat_getting_tv)
        } else {
            modeSwitchBtn.text = getAppContext().getString(R.string.text_qr_mode_web)
            scanStat.text = getAppContext().getString(R.string.text_qr_stat_getting)
        }
    }

    /**
     * 取一张新二维码并重启轮询。
     *
     * 开头这几行 UI 状态原本写在后台线程里（Android 要求 View 只能主线程访问），
     * 现在统一在调用方线程（UI）完成；同时也把"正在取码"期间的点击挡掉：
     * 二维码点击会再触发一次取值，模式切换卡片还会再起一套轮询。
     */
    fun refreshQrCode() {
        stopPolling()
        qrImageView.setImageResource(R.mipmap.loading_qr)
        qrImageView.isEnabled = false
        need_refresh = false
        setModeSwitchEnabled(false)
        CenterThreadPool.run {
            try {
                if (isTVMode) {
                    CenterThreadPool.runOnUiThread { scanStat.text = getAppContext().getString(R.string.text_qr_stat_getting_tv) }
                    QRImage = LoginApi.getTVLoginQR()
                } else {
                    CenterThreadPool.runOnUiThread { scanStat.text = getAppContext().getString(R.string.text_qr_stat_getting) }
                    // checkCookies 失败不应阻塞登录流程，内部捕获异常
                    try {
                        CookiesApi.checkCookies()
                    } catch (e: IOException) {
                        Log.e("QRLogin", "checkCookies网络请求失败: ${e.message}")
                    } catch (e: JSONException) {
                        Log.e("QRLogin", "checkCookies数据解析失败: ${e.message}")
                    }
                    QRImage = LoginApi.getLoginQR()
                }

                CenterThreadPool.runOnUiThread {
                    Log.e("debug-image", QRImage!!.width.toString() + "," + QRImage!!.height)
                    qrImageView.setImageBitmap(QRImage)
                    // 二维码拿到后必须重新启用，否则 ImageView 收不到点击事件，
                    // 缩放（setOnClickListener）就永远不会触发。
                    qrImageView.isEnabled = true
                    setModeSwitchEnabled(true)
                    startLoginDetect()
                }
            } catch (e: IOException) {
                CenterThreadPool.runOnUiThread {
                    qrImageView.isEnabled = true
                    need_refresh = true
                    setModeSwitchEnabled(true)
                    scanStat.text = getAppContext().getString(R.string.text_qr_get_failed_network)
                }
                e.printStackTrace()
            } catch (e: JSONException) {
                CenterThreadPool.runOnUiThread {
                    qrImageView.isEnabled = true
                    need_refresh = true
                    setModeSwitchEnabled(true)
                    scanStat.text = getAppContext().getString(R.string.text_qr_api_invalid)
                }
                e.printStackTrace()
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread {
                    qrImageView.isEnabled = true
                    need_refresh = true
                    setModeSwitchEnabled(true)
                    scanStat.text = getAppContext().getString(R.string.err_qr_other, e.message ?: "")
                }
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        stopPolling()
        super.onDestroy()
    }

    /**
     * 启动轮询。幂等：无论调用几次，最终只会有一套 Timer 在跑。
     */
    fun startLoginDetect() {
        // 幂等：模式切换卡片连点、多次刷新都会走到这里。旧实现直接 `timer = Timer()`
        // 覆盖字段，前一个 Timer 失去引用后仍在 poll 同一张码（两套轮询、文案互相覆盖）。
        stopPolling()
        pollCount = 0
        pollFailCount = 0
        pollStopped = false
        scheduleNextPoll(FIRST_POLL_DELAY_MS)
    }

    /**
     * 安排"下一发"单次 poll。
     *
     * 用单次 schedule 而不是固定 period：节奏要随次数变（2s/4s/6s），
     * 而 Timer.schedule 的 period 是固定值，只能每个 tick 自己重排。
     */
    private fun scheduleNextPoll(delayMs: Long) {
        timer?.cancel()
        val nextTimer = Timer()
        timer = nextTimer
        nextTimer.schedule(object : TimerTask() {
            @SuppressLint("SetTextI18n")
            override fun run() {
                if (pollStopped || !isAdded) {
                    stopPolling()
                    return
                }

                pollCount++
                val keepPolling = try {
                    if (isTVMode) detectTVLoginState() else detectWebLoginState()
                } catch (e: Exception) {
                    // 失败路径自己决定"退避重试"还是"放弃"，这里不能再补排一发
                    onPollFailure(e)
                    return
                }

                if (!keepPolling || pollStopped) return

                if (pollFailCount > 0) {
                    // 从重试状态恢复：重试期间为了防重复取码把二维码禁用了，现在放开
                    pollFailCount = 0
                    if (isAdded) CenterThreadPool.runOnUiThread { qrImageView.isEnabled = true }
                }

                if (pollCount >= MAX_POLL_COUNT) {
                    onPollTimeout()
                    return
                }

                scheduleNextPoll(nextPollIntervalMs())
            }
        }, delayMs)
    }

    /**
     * 按官方客户端的节奏取下一次间隔：前 30 次 2s、30–59 次 4s、之后 6s。
     *
     * 旧的固定 1s 在 180s 内要打约 180 次（同期官方约 60 次），既容易吃 412 限流，
     * 也让手表白白多耗电。
     */
    private fun nextPollIntervalMs(): Long = when {
        pollCount < 30 -> 2000L
        pollCount < 60 -> 4000L
        else -> 6000L
    }

    /**
     * poll 异常处理：连续失败 < MAX_POLL_FAIL 次就退避续轮询，达到才放弃。
     *
     * 旧实现捕获任何异常就直接 cancel —— 一次抖动后页面会静默停在"请扫码"上，
     * 用户以为还在等，其实早就没在轮询了。3 次（约 9 秒）能跨过瞬时断网，
     * 又不至于在真没网时无限重试。
     */
    private fun onPollFailure(e: Exception) {
        pollFailCount++
        if (isAdded) CenterThreadPool.runOnUiThread {
            // 重试期间二维码不可点：避免用户在恢复过程中又触发一次取码
            qrImageView.isEnabled = false
            scanStat.text = getAppContext().getString(R.string.text_qr_retrying)
        }

        if (pollFailCount < MAX_POLL_FAIL) {
            scheduleNextPoll(POLL_RETRY_DELAY_MS)
            return
        }

        // TV 与 web 的失败文案分开：TV 侧本来就分"接口异常/网络错误"，
        // 合并成一句会让排查时看不出是哪一端
        if (isAdded) CenterThreadPool.runOnUiThread {
            qrImageView.isEnabled = true
            need_refresh = true
            setModeSwitchEnabled(true)
            scanStat.text = when {
                isTVMode && e is IOException -> getAppContext().getString(R.string.err_qr_tv_network, e.message ?: "")
                isTVMode -> getAppContext().getString(R.string.err_qr_tv_api, e.message ?: "")
                // web 侧不再把 e.message 拼进界面：那里是英文异常原文（如
                // "Unable to resolve host …"），对用户毫无意义；详情走 MsgUtil.err 的日志。
                e is IOException -> getAppContext().getString(R.string.err_qr_web_network)
                else -> getAppContext().getString(R.string.err_qr_web_api)
            }
            MsgUtil.err(e)
        }
        stopPolling()
    }

    /**
     * 轮询总量到上限。
     *
     * 服务端正常情况下早就回 86038 了，走到这里说明它没按约定回；继续轮询既拿不到结果
     * 又白烧流量，交给用户点一下重新取码。文案复用"已失效"。
     */
    private fun onPollTimeout() {
        if (isAdded) CenterThreadPool.runOnUiThread {
            qrImageView.isEnabled = true
            need_refresh = true
            setModeSwitchEnabled(true)
            scanStat.text = getAppContext().getString(R.string.text_qr_expired)
        }
        stopPolling()
    }

    /**
     * 停轮询。
     *
     * 先写标志位再 cancel：正在跑的那一拍结束时会看到 pollStopped，从而不补排下一发。
     */
    private fun stopPolling() {
        pollStopped = true
        timer?.cancel()
        timer = null
    }

    /** 取码期间禁用模式切换卡片，理由见 refreshQrCode()。 */
    private fun setModeSwitchEnabled(enabled: Boolean) {
        view?.findViewById<MaterialCardView>(R.id.loginModeSwitch)?.isEnabled = enabled
    }

    /**
     * @return true = 继续轮询；false = 本轮流程已结束（成功 / 过期 / 未知码）。
     */
    @SuppressLint("SetTextI18n")
    private fun detectTVLoginState(): Boolean {
        try {
            val response = LoginApi.getTVLoginState()
            if (!isAdded) {
                // 提前 return 时 body 还没读，必须显式关闭，否则连接不归还
                response.close()
                stopPolling()
                return false
            }

            val str = response.body!!.string()
            val loginJson = JSONObject(str)
            Logu.v("tv_login_state", str)

            val code = loginJson.getInt("code")
            return when (code) {
                86039 -> {
                    CenterThreadPool.runOnUiThread {
                        scanStat.text = getAppContext().getString(R.string.text_qr_hint_tv)
                    }
                    true
                }
                86090 -> {
                    CenterThreadPool.runOnUiThread {
                        scanStat.text = getAppContext().getString(R.string.text_qr_scanned_confirm)
                    }
                    true
                }
                86038 -> {
                    CenterThreadPool.runOnUiThread {
                        scanStat.text = getAppContext().getString(R.string.text_qr_expired)
                        qrImageView.isEnabled = true
                        need_refresh = true
                        setModeSwitchEnabled(true)
                    }
                    stopPolling()
                    false
                }
                0 -> {
                    stopPolling()
                    CenterThreadPool.runOnUiThread { scanStat.text = getAppContext().getString(R.string.text_qr_processing_tv) }

                    val data = loginJson.getJSONObject("data")
                    val accessToken = data.getString("access_token")
                    val refreshToken = data.getString("refresh_token")
                    val mid = data.getLong("mid")

                    // 一次性把整套凭证交给 AccountManager：mid / cookies / refresh_token /
                    // access_key / csrf 在同一把锁里一起写，并升账号代际（旧账号在飞的请求作废）。
                    // 原来这里手写 5 个 putXxx，漏写一个就是「切号后凭据混乱」的来源之一。
                    //
                    // Cookie 从**空串**开始拼：TV 登录的 cookie_info 是完整的一套新 Cookie，
                    // 若拿 SP 里现有 Cookie 去合并，就把上一个账号的 SESSDATA 带过来了。
                    val cookies = Cookies("")
                    val cookieInfo = data.optJSONObject("cookie_info")
                    if (cookieInfo != null) {
                        val cookiesArray = cookieInfo.optJSONArray("cookies")
                        if (cookiesArray != null) {
                            for (i in 0 until cookiesArray.length()) {
                                val cookie = cookiesArray.getJSONObject(i)
                                cookies.set(cookie.getString("name"), cookie.getString("value"))
                            }
                        }
                    }

                    AccountManager.applyCredentials(
                        mid,
                        "",
                        cookies.toString(),
                        refreshToken,
                        accessToken,
                        ""
                    )

                    AccountManager.saveCurrentAccount()

                    // 敏感凭证不写入日志

                    val instance: InstanceActivity? = BiliTerminal.getInstanceActivityOnTop()
                    if (instance != null && !instance.isDestroyed) instance.finish()

                    try {
                        LoginApi.requestSSOs()
                    } catch (ignored: Exception) {
                    }

                    // TV 扫码成功这段跑在 Timer 线程上：判活之后用户可能已经返回/被 finish，
                    // requireContext() 在 detach 后会抛 ISE，所以整段导航都放进判活里
                    if (isAdded) {
                        startActivity(Intent(requireContext(), SplashActivity::class.java))
                        requireActivity().finish()
                    }
                    false
                }
                else -> {
                    // 未知状态码说明服务端协议变了。旧实现只改一句文案就继续 1s 一次地轮询，
                    // 既永远等不到结果，又白白打接口；这里直接收口让用户重扫
                    CenterThreadPool.runOnUiThread {
                        qrImageView.isEnabled = true
                        need_refresh = true
                        setModeSwitchEnabled(true)
                        scanStat.text = getAppContext().getString(R.string.err_qr_unknown_code, code)
                    }
                    stopPolling()
                    false
                }
            }
        } catch (e: JSONException) {
            onPollFailure(e)
            return false
        } catch (e: IOException) {
            onPollFailure(e)
            return false
        }
    }

    /**
     * @return true = 继续轮询；false = 本轮流程已结束（成功 / 过期 / 未知码）。
     */
    @SuppressLint("SetTextI18n")
    private fun detectWebLoginState(): Boolean {
        val response = LoginApi.getLoginState()
        if (!isAdded) {
            // 提前 return 时 body 还没读，必须显式关闭，否则连接不归还
            response.close()
            stopPolling()
            return false
        }

        val str = response.body!!.string()
        val loginJson = JSONObject(str)
        Logu.v("login_state", str)

        val data = loginJson.getJSONObject("data")
        val code = data.getInt("code")
        return when (code) {
            86090 -> {
                CenterThreadPool.runOnUiThread { scanStat.text = getAppContext().getString(R.string.text_qr_scanned_login) }
                true
            }
            86101 -> {
                CenterThreadPool.runOnUiThread { scanStat.text = getAppContext().getString(R.string.text_qr_hint_official) }
                true
            }
            86038 -> {
                CenterThreadPool.runOnUiThread {
                    scanStat.text = getAppContext().getString(R.string.text_qr_expired)
                    qrImageView.isEnabled = true
                    need_refresh = true
                    setModeSwitchEnabled(true)
                }
                stopPolling()
                false
            }
            0 -> {
                handleWebLoginSuccess(data)
                false
            }
            else -> {
                // 同 TV 分支：未知码只改一句文案会让 1s 轮询永不停止，这里收口让用户重扫
                CenterThreadPool.runOnUiThread {
                    qrImageView.isEnabled = true
                    need_refresh = true
                    setModeSwitchEnabled(true)
                    scanStat.text = getAppContext().getString(R.string.err_qr_unknown_code, code)
                }
                stopPolling()
                false
            }
        }
    }

    /**
     * web 扫码成功的落地。
     *
     * Cookie 由 CookieSaveInterceptor 在 poll 响应里写进存储，这里只读结果、补 mid/csrf/refresh_token。
     * 注意 web 协议不下发 access_token。
     */
    private fun handleWebLoginSuccess(data: JSONObject) {
        stopPolling()
        CenterThreadPool.runOnUiThread { scanStat.text = getAppContext().getString(R.string.text_qr_processing) }

        // 走缓存入口而不是直读 SP：NetWorkUtil 的内存缓存才是刚被拦截器写入的那份，
        // 直接读 SP 拿到的是磁盘副本，可能在写回前还是旧值
        val cookies = NetWorkUtil.getCachedCookies()
        val mid = NetWorkUtil.getInfoFromCookie("DedeUserID", cookies).toLongOrNull()

        // DedeUserID 缺失/非数字说明服务端没走完整的 web 授权：SESSDATA 可能已在 cookie 里，
        // 但 mid 写不进去就是"半个登录态"（Splash 按 mid==0 判未登录）。
        // 旧实现直接 .toLong()，空串会抛 NumberFormatException，最后被外层当成网络错误报出来。
        if (mid == null || mid <= 0L) {
            if (isAdded) CenterThreadPool.runOnUiThread {
                qrImageView.isEnabled = true
                need_refresh = true
                setModeSwitchEnabled(true)
                val text = getAppContext().getString(R.string.err_qr_no_mid)
                scanStat.text = text
                MsgUtil.showMsg(text)
            }
            return
        }

        // 整套凭证一次性落库（含「web 协议没有 access_token → 必须显式清空」这一点）：
        // 原来这里手写 putXxx，access_key 只是"写空串"，其余字段各写各的；现在统一走
        // AccountManager.applyCredentials，缺失字段一律显式写空、并升账号代际，
        // 切号窗口里迟到的旧响应用 Set-Cookie 写不回新账号。
        AccountManager.applyCredentials(
            mid,
            "",
            cookies,
            // JSON 显式 null 时 optString 会给字符串 "null"，那会被当成 refresh_token 存下来
            if (data.isNull("refresh_token")) "" else data.optString("refresh_token"),
            // web 协议不返回 access_token，必须显式清掉可能残留的 TV token：
            // 否则 SplashActivity 按"有 access_key = TV 登录"分流去刷 APP token，
            // web 的 cookie 续期链路整条被跳过，会话只能等 SESSDATA 自然失效
            "",
            ""
        )

        AccountManager.saveCurrentAccount()

        // 敏感凭证不写入日志

        val instance: InstanceActivity? = BiliTerminal.getInstanceActivityOnTop()
        if (instance != null && !instance.isDestroyed) instance.finish()

        NetWorkUtil.refreshHeaders()

        if (data.has("url")) {
            try {
                // 只用来触发服务端记录，响应内容无用；不读 body 必须显式关闭
                NetWorkUtil.get(data.optString("url")).close()
            } catch (ignored: Throwable) {
            }
        }

        // 先导航再同步 SSO：登录态已经落库，SSO 只是顺手把会话铺到其它域。
        // 旧实现把它放在导航之前且没有 try，一次 SSO 失败就会跳过 Splash，
        // 让用户看到"登录失败"——其实已经登录成功了。
        if (isAdded) {
            startActivity(Intent(requireContext(), SplashActivity::class.java))
            requireActivity().finish()
        }

        try {
            LoginApi.requestSSOs()
        } catch (ignored: Exception) {
        }
    }
}