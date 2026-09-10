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
import androidx.fragment.app.Fragment
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.SplashActivity
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

class QRLoginFragment : Fragment() {
    private lateinit var qrImageView: ImageView
    private lateinit var scanStat: TextView
    private lateinit var modeSwitchBtn: TextView
    var QRImage: Bitmap? = null
    var timer: Timer? = null
    var need_refresh: Boolean = false
    var from_setup: Boolean = false
    var isTVMode: Boolean = false

    /**
     * 二维码缩放档位。默认中号。
     *
     * 原默认 LARGE 是 0.01/0.99 —— 卡片几乎占满屏宽，而二维码卡片是 1:1 正方形，
     * 横屏/平板上高度会超出视口，状态提示文字被挤出屏幕。现在默认 0.15/0.85（70% 宽），
     * 点击二维码仍在 大→中→小 之间循环。
     */
    private enum class QrScale(val left: Float, val right: Float, val tip: String) {
        SMALL(0.25f, 0.75f, "切换为中等二维码"),
        MEDIUM(0.15f, 0.85f, "切换为大二维码"),
        LARGE(0.05f, 0.95f, "切换为小二维码")
    }

    private var qrScale = QrScale.MEDIUM

    companion object {
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
            timer?.cancel()
            if (isAdded) requireActivity().finish()
        }

        val jump = view.findViewById<MaterialCardView>(R.id.jump)
        jump.setOnClickListener {
            if (from_setup) startActivity(Intent(requireContext(), SplashActivity::class.java))
            timer?.cancel()
            if (isAdded) requireActivity().finish()
        }

        val special = view.findViewById<MaterialCardView>(R.id.special)
        special.setOnClickListener {
            val intent = Intent(requireContext(), SpecialLoginActivity::class.java)
            intent.putExtra("from_setup", from_setup)
            startActivity(intent)
            timer?.cancel()
            if (isAdded) requireActivity().finish()
        }

        val loginModeSwitch = view.findViewById<MaterialCardView>(R.id.loginModeSwitch)
        loginModeSwitch.setOnClickListener {
            isTVMode = !isTVMode
            updateModeUI()
            timer?.cancel()
            qrImageView.setImageResource(R.mipmap.loading_qr)
            qrImageView.isEnabled = false
            if (isAdded) refreshQrCode()
        }

        val helpIcon = view.findViewById<ImageView>(R.id.helpIcon)
        helpIcon.setOnClickListener {
            startActivity(Intent(requireContext(), QRLoginHelpActivity::class.java))
        }

        qrImageView.setOnClickListener {
            if (need_refresh) {
                qrImageView.setImageResource(R.mipmap.loading_qr)
                qrImageView.isEnabled = false
                refreshQrCode()
            } else {
                qrScale = when (qrScale) {
                    QrScale.LARGE -> QrScale.MEDIUM
                    QrScale.MEDIUM -> QrScale.SMALL
                    QrScale.SMALL -> QrScale.LARGE
                }
                applyQrScale()
                MsgUtil.showMsg(qrScale.tip)
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
            modeSwitchBtn.text = "TV端扫码登录"
            scanStat.text = "正在获取TV端二维码"
        } else {
            modeSwitchBtn.text = "WEB端扫码登录"
            scanStat.text = "正在获取二维码"
        }
    }

    @SuppressLint("SetTextI18n")
    fun refreshQrCode() {
        timer?.cancel()
        CenterThreadPool.run {
            try {
                need_refresh = false
                qrImageView.isEnabled = false

                if (isTVMode) {
                    CenterThreadPool.runOnUiThread { scanStat.text = "正在获取TV端二维码" }
                    QRImage = LoginApi.getTVLoginQR()
                } else {
                    CenterThreadPool.runOnUiThread { scanStat.text = "正在获取二维码" }
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
                    startLoginDetect()
                }
            } catch (e: IOException) {
                CenterThreadPool.runOnUiThread {
                    qrImageView.isEnabled = true
                    need_refresh = true
                    scanStat.text = "获取二维码失败，网络错误"
                }
                e.printStackTrace()
            } catch (e: JSONException) {
                CenterThreadPool.runOnUiThread {
                    qrImageView.isEnabled = true
                    need_refresh = true
                    scanStat.text = "登录接口可能失效，请找开发者"
                }
                e.printStackTrace()
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread {
                    qrImageView.isEnabled = true
                    need_refresh = true
                    scanStat.text = "遇到其他错误：\n" + e.message
                }
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    fun startLoginDetect() {
        timer = Timer()
        timer!!.schedule(object : TimerTask() {
            @SuppressLint("SetTextI18n")
            override fun run() {
                try {
                    if (!isAdded) {
                        this.cancel()
                        return
                    }

                    if (isTVMode) {
                        detectTVLoginState()
                    } else {
                        detectWebLoginState()
                    }
                } catch (e: Exception) {
                    if (isAdded) CenterThreadPool.runOnUiThread {
                        qrImageView.isEnabled = true
                        need_refresh = true
                        scanStat.text = "无法获取二维码信息，点击上方重试\n" + e.message
                        MsgUtil.err(e)
                    }
                    this.cancel()
                }
            }
        }, 500, 1000)
    }

    @SuppressLint("SetTextI18n")
    private fun detectTVLoginState() {
        try {
            val response = LoginApi.getTVLoginState()
            if (!isAdded) {
                this@QRLoginFragment.timer?.cancel()
                return
            }

            val str = response.body!!.string()
            val loginJson = JSONObject(str)
            Logu.v("tv_login_state", str)

            val code = loginJson.getInt("code")
            when (code) {
                86039 -> CenterThreadPool.runOnUiThread {
                    scanStat.text = "请使用手机端哔哩哔哩扫码登录（TV端模式）\n点击二维码可以进行放大和缩小"
                }
                86090 -> CenterThreadPool.runOnUiThread {
                    scanStat.text = "已扫描，请在手机上点击确认登录"
                }
                86038 -> {
                    CenterThreadPool.runOnUiThread {
                        scanStat.text = "二维码已失效，点击上方重新获取"
                        qrImageView.isEnabled = true
                        need_refresh = true
                    }
                    this@QRLoginFragment.timer?.cancel()
                }
                0 -> {
                    this@QRLoginFragment.timer?.cancel()
                    CenterThreadPool.runOnUiThread { scanStat.text = "正在处理TV端登录……" }

                    val data = loginJson.getJSONObject("data")
                    val accessToken = data.getString("access_token")
                    val refreshToken = data.getString("refresh_token")
                    val mid = data.getLong("mid")

                    SharedPreferencesUtil.putString(SharedPreferencesUtil.access_key, accessToken)
                    SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, refreshToken)
                    SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, mid)

                    val cookieInfo = data.optJSONObject("cookie_info")
                    if (cookieInfo != null) {
                        val cookiesArray = cookieInfo.optJSONArray("cookies")
                        if (cookiesArray != null) {
                            val cookies = Cookies(SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))
                            for (i in 0 until cookiesArray.length()) {
                                val cookie = cookiesArray.getJSONObject(i)
                                val name = cookie.getString("name")
                                val value = cookie.getString("value")
                                cookies.set(name, value)
                            }
                            NetWorkUtil.setCookiesString(cookies.toString())
                            SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf,
                                cookies.getOrDefault("bili_jct", ""))
                        }
                    }

                    SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.cookie_refresh, true)
                    SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.setup, true)

                    AccountManager.saveCurrentAccount()

                    // 敏感凭证不写入日志

                    val instance: InstanceActivity? = BiliTerminal.getInstanceActivityOnTop()
                    if (instance != null && !instance.isDestroyed) instance.finish()

                    try {
                        LoginApi.requestSSOs()
                    } catch (ignored: Exception) {
                    }

                    startActivity(Intent(requireContext(), SplashActivity::class.java))

                    if (isAdded) requireActivity().finish()
                }
                else -> CenterThreadPool.runOnUiThread {
                    scanStat.text = "TV端登录API可能变动，\n但你仍然可以尝试扫码登录。\n建议反馈给开发者"
                }
            }
        } catch (e: JSONException) {
            if (isAdded) CenterThreadPool.runOnUiThread {
                qrImageView.isEnabled = true
                need_refresh = true
                scanStat.text = "TV端登录接口返回异常\n" + e.message
            }
            this@QRLoginFragment.timer?.cancel()
        } catch (e: IOException) {
            if (isAdded) CenterThreadPool.runOnUiThread {
                qrImageView.isEnabled = true
                need_refresh = true
                scanStat.text = "TV端网络错误，点击重试\n" + e.message
            }
            this@QRLoginFragment.timer?.cancel()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun detectWebLoginState() {
        val response = LoginApi.getLoginState()
        if (!isAdded) {
            this@QRLoginFragment.timer?.cancel()
            return
        }

        val str = response.body!!.string()
        val loginJson = JSONObject(str)
        Logu.v("login_state", str)

        val code = loginJson.getJSONObject("data").getInt("code")
        when (code) {
            86090 -> CenterThreadPool.runOnUiThread { scanStat.text = "已扫描，请在手机上点击登录" }
            86101 -> CenterThreadPool.runOnUiThread { scanStat.text = "请使用官方手机端哔哩哔哩扫码登录\n点击二维码可以进行放大和缩小" }
            86038 -> {
                CenterThreadPool.runOnUiThread {
                    scanStat.text = "二维码已失效，点击上方重新获取"
                    qrImageView.isEnabled = true
                    need_refresh = true
                }
                this@QRLoginFragment.timer?.cancel()
            }
            0 -> {
                this@QRLoginFragment.timer?.cancel()
                CenterThreadPool.runOnUiThread { scanStat.text = "正在处理登录……" }
                val cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")

                SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, NetWorkUtil.getInfoFromCookie("DedeUserID", cookies).toLong())
                SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf, NetWorkUtil.getInfoFromCookie("bili_jct", cookies))
                SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, loginJson.getJSONObject("data").getString("refresh_token"))

                SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.cookie_refresh, true)
                SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.setup, true)

                AccountManager.saveCurrentAccount()

                // 敏感凭证不写入日志

                val instance: InstanceActivity? = BiliTerminal.getInstanceActivityOnTop()
                if (instance != null && !instance.isDestroyed) instance.finish()

                NetWorkUtil.refreshHeaders()

                LoginApi.requestSSOs()
                if (loginJson.getJSONObject("data").has("url")) {
                    try {
                        NetWorkUtil.get(loginJson.getJSONObject("data").optString("url"))
                    } catch (ignored: Throwable) {
                    }
                }

                startActivity(Intent(requireContext(), SplashActivity::class.java))

                if (isAdded) requireActivity().finish()
            }
            else -> CenterThreadPool.runOnUiThread { scanStat.text = "二维码登录API可能变动，\n但你仍然可以尝试扫码登录。\n建议反馈给开发者" }
        }
    }
}