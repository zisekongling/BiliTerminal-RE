package com.RobinNotBad.BiliClient.activity

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.BuildConfig
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.settings.UpdateHistoryActivity
import com.RobinNotBad.BiliClient.activity.settings.setup.SetupUIActivity
import com.RobinNotBad.BiliClient.activity.video.RecommendActivity
import com.RobinNotBad.BiliClient.activity.video.local.LocalListActivity
import com.RobinNotBad.BiliClient.api.AppInfoApi
import com.RobinNotBad.BiliClient.api.TerminalApi
import com.RobinNotBad.BiliClient.api.AppTokenRefreshApi
import com.RobinNotBad.BiliClient.api.CookieRefreshApi
import com.RobinNotBad.BiliClient.api.CookiesApi
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.util.AccountManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.ClockSanity
import com.RobinNotBad.BiliClient.util.MenuConfig
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.PerformanceManager
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TelemetryReporter
import com.RobinNotBad.BiliClient.util.TerminalDialog
import java.io.IOException

@SuppressLint("CustomSplashScreen")
class SplashActivity : Activity() {

    private lateinit var splashTextView: TextView
    private var splashText: String = UiText.of("欢迎使用\nRE:哔哩终端")

    /** F4：本次启动是否需要自动展示当版更新日志（只在"版本升级"时为 true）。 */
    private var pendingUpdateLog = false

    private var typewriterIndex = 0
    private var typewriterRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private val typewriterRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!typewriterRunning || typewriterIndex > splashText.length) return
            splashTextView.text = splashText.substring(0, typewriterIndex)
            typewriterIndex++
            handler.postDelayed(this, 100)
        }
    }

    private fun startTypewriter(text: String) {
        typewriterRunning = true
        typewriterIndex = 0
        handler.post(typewriterRunnable)
    }

    private fun stopTypewriter() {
        typewriterRunning = false
        handler.removeCallbacks(typewriterRunnable)
    }

    override fun onDestroy() {
        // 打字机 Runnable 通过 handler 间接持有 Activity 并持续 postDelayed，
        // 不随生命周期停止就会泄漏。这里统一停掉（等价于对方 onDestroy 里 cancel splashTimer）。
        stopTypewriter()
        super.onDestroy()
    }

    override fun attachBaseContext(newBase: Context?) {
        // forceDark：闪屏底色是纯黑（冷启动窗口由系统画，没法跟随模式），文字也必须保持浅色
        super.attachBaseContext(BiliTerminal.getFitDisplayContext(newBase, forceDark = true))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        setContentView(R.layout.activity_splash)

        splashTextView = findViewById(R.id.splashText)
        splashText = SharedPreferencesUtil.getString("ui_splashtext", "欢迎使用\nRE:哔哩终端")
        startTypewriter(splashText)

        // F4：在这里（而不是进主流程之后）判一次版本，是因为下面可能为了 UETool 悬浮窗权限
        // 直接 return、稍后由 onActivityResult 再走一遍 proceedSplashFlow；判定结果用字段记住，
        // 保证"升级后首次启动弹更新日志"只算一次。
        pendingUpdateLog = shouldShowUpdateLogAfterUpgrade()

        // Debug 构建下：若未授予悬浮窗权限，先跳去授权再继续启动流程，确保 UETool 能显示
        if (ensureUEToolOverlayPermission()) return

        // 启动主流程（抽取成单独方法，供授权回调再次调用）
        proceedSplashFlowWithPrivacyGate()
    }

    /**
     * 本地是否还持有登录凭证。
     *
     * 判据用 Cookie 里的 SESSDATA：它是 Web 端唯一的身份凭证，只要它还在，就说明用户
     * 仍然是"已登录"状态。刷新接口的返回值（false / 解析异常 / 服务端抖动 / 返回体缺 data）
     * 都只能说明"这次问不到服务端"，不能证明凭证失效——这正是之前"一次网络波动就清空登录态、
     * 强制用户重新登录"的根因。
     */
    private fun hasLocalSession(): Boolean {
        val cookie = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")
        return NetWorkUtil.getInfoFromCookie("SESSDATA", cookie).isNotEmpty()
    }

    @Throws(IOException::class)
    private fun checkCookieRefresh() {
        // CookieRefreshApi.cookieInfo() 内部直接 `result.getJSONObject("data")`，
        // 网络抖动、返回体缺 data、服务端 code != 0 都会抛异常。这类失败与"登录已失效"
        // 无法从返回值区分，因此单独兜住它、跳过本次刷新检查，绝不清登录态。
        val cookieInfo = try {
            CookieRefreshApi.cookieInfo()
        } catch (e: Exception) {
            Log.e("Cookies", "cookieInfo 获取/解析失败，跳过本次刷新检查：${e.message}")
            return
        }

        if (!cookieInfo.optBoolean("refresh")) return

        Log.e("Cookies", "需要刷新")
        if (SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "") == "") {
            Log.e("Cookies", "没有 refresh_token，跳过刷新")
            return
        }

        try {
            val correspondPath = CookieRefreshApi.getCorrespondPath(cookieInfo.getLong("timestamp"))
            Log.e("CorrespondPath", correspondPath)
            val refreshCsrf = CookieRefreshApi.getRefreshCsrf(correspondPath)
            Log.e("RefreshCsrf", refreshCsrf)
            // 26.11.06.1：刷新结果分三档。只有服务端**明确**说会话失效（-101，NEED_LOGIN）才清
            // 登录态；RETRY（网络抖动 / 服务端拒绝 / 新凭据没落全）一律保留本地凭证——「刷新失败
            // 就等于掉登录」是历史上最招骂的一类问题（审计 P29 / 反馈 #60）。
            when (CookieRefreshApi.refreshCookie(refreshCsrf)) {
                CookieRefreshApi.RefreshResult.OK -> {
                    MsgUtil.showMsg(UiText.of("Cookies已刷新"))
                    AccountManager.saveCurrentAccount()
                    return
                }
                CookieRefreshApi.RefreshResult.NEED_LOGIN -> {
                    MsgUtil.showMsgLong(UiText.of("登录信息过期，请重新登录！"))
                    resetLogin("刷新接口返回 -101（会话/口令已被服务端作废）")
                    return
                }
                CookieRefreshApi.RefreshResult.RETRY ->
                    Log.e("Cookies", "Cookie 刷新未成功，保留本地登录态")
            }
        } catch (e: Exception) {
            // 放宽到 Exception：原实现只接 JSONException，其余异常（IOException 等）会穿透到外层。
            // 刷新流程里的任何异常都只代表这次刷新没成功，不应清登录态。
            Log.e("Cookies", "Cookie 刷新异常，保留本地登录态：${e.message}")
        }

        // 只有本地连 SESSDATA 都没有了，才是明确意义上的"未登录/凭证失效"，
        // 这时才需要提示并清空登录态引导用户重新登录。
        // （Cookie 合并层已保证普通响应不会把 SESSDATA 抹掉，见 CookieJarPolicy）
        if (!hasLocalSession()) {
            MsgUtil.showMsgLong(UiText.of("登录信息过期，请重新登录！"))
            resetLogin("本地 Cookie 里没有 SESSDATA")
        }
    }

    private fun resetLogin(reason: String) {
        // 走统一入口：除了 mid/cookies/csrf/refresh_token，它还会清 access_key
        // （原实现漏了这个 —— 全清之后仍有 access_key 时，CookieRefreshScheduler 和启动页
        // 会继续按「TV 登录」分流去刷 APP token，等于每次启动都白刷一次还报错），
        // 并升账号代际把在飞的请求作废、清掉按账号走的本地缓存。
        //
        // 原因是 26.11.06.1 补的：这条路径会清掉整套登录凭据，是「掉登录」投诉里唯一由 App
        // 自己动手的分支，所以必须把触发原因留在日志里，别再出现「用户说掉登录、日志里查不到」。
        Log.w("Splash", "清空登录态：$reason")
        AccountManager.clearCurrentLogin()
    }

    private fun applyTheme() {
        ColorScheme.applyWindowTheme(this)
    }

    /**
     * F4：判断本次启动是否需要自动展示当版更新日志（仅「版本升级」需要）。
     *
     * 记录的是 **versionCode**，不是 versionName：versionCode 由本项目版本号规则 YYMMDD0 推出、
     * 单调递增且唯一；versionName 在 beta 包上带「-BETA<n>」后缀、又靠人肉维护，不适合当基准。
     *
     * 判定口径：
     * - 存的值 == 当前 versionCode：不是升级，不弹；
     * - 存的值 >= 0（这个键生效之后的任意一次启动）：只要不相等就算升级，弹；
     * - 存的值 < 0（键不存在，即用户从"还没有这段逻辑"的旧版本升上来）：退回用 PackageInfo 的
     *   lastUpdateTime > firstInstallTime 区分「覆盖安装（升级）」与「首次安装」——前者弹，
     *   后者不弹（首装用户刚走完 SetupUIActivity，再糊一脸更新日志纯属噪音）；
     * - 无论哪种情况都把当前 versionCode 写回，保证同一版本只弹一次。
     *
     * 读取包了 try/catch：万一这个键将来被写成非 Int，getInt 的 ClassCastException 不该拖垮
     * 闪屏，退化成 -1（走上面的"未知"分支）即可。写入前判 sharedPreferences 是否就绪，
     * 因为 util 里的 putInt 没有判空。
     */
    private fun shouldShowUpdateLogAfterUpgrade(): Boolean {
        val currentCode = BuildConfig.VERSION_CODE
        val lastCode = try {
            SharedPreferencesUtil.getInt(SharedPreferencesUtil.last_version, -1)
        } catch (e: Exception) {
            Log.e("Splash", "读取 last_version 失败，按未记录处理：${e.message}")
            -1
        }

        val upgraded = when {
            lastCode == currentCode -> false
            lastCode >= 0 -> true
            else -> isCoverInstall()
        }

        if (SharedPreferencesUtil.sharedPreferences != null) {
            SharedPreferencesUtil.putInt(SharedPreferencesUtil.last_version, currentCode)
        } else {
            Log.e("Splash", "sharedPreferences 未就绪，跳过 last_version 写回")
        }
        return upgraded
    }

    /**
     * last_version 这个键还不存在时的兜底判据：
     * 包的最后更新时间晚于首次安装时间 => 覆盖安装（版本升级）；两者相等 => 首次安装。
     */
    private fun isCoverInstall(): Boolean {
        return try {
            val info = packageManager.getPackageInfo(packageName, 0)
            info.lastUpdateTime > info.firstInstallTime
        } catch (e: Exception) {
            Log.e("Splash", "读取安装时间失败，按非升级处理：${e.message}")
            false
        }
    }

    /** F4：构造自动打开更新日志页的 Intent，并预选当前版本选项卡。 */
    private fun buildUpdateLogIntent(): Intent {
        val intent = Intent(this, UpdateHistoryActivity::class.java)
        intent.putExtra(
            UpdateHistoryActivity.EXTRA_VERSION_INDEX,
            UpdateHistoryActivity.indexOfCurrentVersion(this)
        )
        return intent
    }

    /**
     * Debug 构建下，检查悬浮窗权限：
     * - 已授权 → 返回 false，让启动流程继续
     * - 未授权且设置页拉得起来 → 跳转系统设置授权页并返回 true，在 onActivityResult 再继续启动
     * - 未授权且设置页拉不起来（手表 ROM 缺该页）→ 提示一句并返回 false，**照常继续启动**
     *
     * Release 构建不做任何处理。
     *
     * 26.10.14：Agent 测试模式下**整个跳过**。这条流程对人工调试是便利，对自动化却是灾难——
     * 未授权时会直接 `return` 掉启动主流程、把界面顶到系统设置页，agent 看到的就是"应用起不来"；
     * 授权之后那个悬浮窗还会盖在界面上，遮住控件、吞掉点击。开关见 [BiliTerminal.AGENT_TEST_MODE]。
     */
    private fun ensureUEToolOverlayPermission(): Boolean {
        if (!BiliTerminal.isDebugBuild()) return false
        if (BiliTerminal.AGENT_TEST_MODE) return false
        if (BiliTerminal.canDrawOverlaysCompat(this)) {
            // 已授权：直接显示 UETool 悬浮窗
            Handler(Looper.getMainLooper()).postDelayed({ BiliTerminal.showUEToolMenu() }, 300L)
            return false
        }
        // 未授权：先跳转授予悬浮窗权限。拉不起来（手表 ROM 没有该设置页）就返回 false，
        // 让启动流程继续——调试便利不该把启动页卡在一个永远不会回来的 onActivityResult 上。
        return BiliTerminal.requestOverlayPermission(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // 从悬浮窗授权页返回
        if (requestCode == BiliTerminal.REQUEST_OVERLAY_PERMISSION_FOR_UETOOL) {
            if (BiliTerminal.canDrawOverlaysCompat(this)) {
                // 授权成功：立即显示 UETool，然后继续原来的启动流程
                Handler(Looper.getMainLooper()).postDelayed({ BiliTerminal.showUEToolMenu() }, 200L)
            } else {
                // 用户未授予：Toast 提示，不阻塞启动
                try {
                    android.widget.Toast.makeText(
                        this,
                        UiText.of("未授予悬浮窗权限，UETool 调试悬浮窗不会显示"),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } catch (_: Throwable) {
                }
            }
            // 继续启动主流程
            proceedSplashFlowWithPrivacyGate()
        }
    }

    /**
     * 隐私说明再确认闸门（26.10.09）。
     *
     * <p>「默认开启匿名统计 + 崩溃自动上报」这件事必须先拿到用户点过的「同意」：
     * 只要 [TerminalApi.needsPrivacyConsent] 为真（当前版本的隐私说明还没被回答过），
     * 就先弹一次，表过态再继续启动。文案承诺与真实行为这两件事从此有了一道可执行的口子。
     *
     * <p>两条路都**继续启动流程**，不拦人：
     * - 「同意」→ [TerminalApi.acceptPrivacyConsent]（记同意 + 两个开关复位为开）→ 继续；
     * - 「不同意」→ [TerminalApi.declinePrivacyConsent]（只记「问过了」+ 关掉两个开关，
     *   同意版本号保持原值，于是 [TerminalApi.hasPrivacyConsent] 仍为 false，一律不上报）→ 继续。
     *
     * <p>`setCancelable(false)` 是必要的：这里是 [TerminalDialog.choice] 而不是 `confirm`，
     * 两个按钮各自带回调；如果允许返回键/点空白关掉，弹窗消失后既没表态也不会继续启动，
     * 用户就卡在闪屏上了。
     */
    private fun proceedSplashFlowWithPrivacyGate() {
        if (!TerminalApi.needsPrivacyConsent()) {
            proceedSplashFlow()
            return
        }
        TerminalDialog.choice(
            context = this,
            title = getString(R.string.privacy_consent_title),
            message = getString(R.string.privacy_consent_message),
            primaryText = getString(R.string.privacy_consent_agree),
            secondaryText = getString(R.string.privacy_consent_decline),
            hint = getString(R.string.privacy_consent_scroll_hint),
            onPrimary = {
                TerminalApi.acceptPrivacyConsent()
                proceedSplashFlow()
            },
            onSecondary = {
                TerminalApi.declinePrivacyConsent()
                MsgUtil.showMsgLong(getString(R.string.privacy_consent_declined))
                proceedSplashFlow()
            }
        ).apply { setCancelable(false) }.show()
    }

    /**
     * 从 onCreate 里抽取出来的启动主流程，供授权页返回后再次调用
     */
    private fun proceedSplashFlow() {
        CenterThreadPool.run {
            if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.setup, false)) {
                try {
                    // 启动页：菜单设置里勾选的那一项；缺省为「推荐」。
                    // loadMenuStart 内部会纠正失效配置（选中项已被移入未启用 → 改回默认并写回）
                    val enabled = SharedPreferencesUtil.loadMenuEnabled()
                    // 本地连通性判断（不发起请求）：断网时用户自定义的启动页（推荐 / 动态 / 消息…）
                    // 全是等待网络的页面，进去只能看到加载失败，所以断网一律改去缓存页；
                    // 网络正常时才按用户设置进自定义启动页。只影响本次启动，不改写 menu_start。
                    val online = NetWorkUtil.isNetworkAvailable(this@SplashActivity)
                    val startActivityKey = MenuConfig.resolveLaunchStart(
                        enabled,
                        SharedPreferencesUtil.loadMenuStart(enabled),
                        online
                    )

                    val activityClass = MenuActivity.btnNames[startActivityKey]?.second

                    val intent = Intent()
                    intent.setClass(this@SplashActivity, activityClass ?: RecommendActivity::class.java)
                    intent.putExtra("from", startActivityKey)

                    runOnUiThread {
                        stopTypewriter()
                        splashTextView.text = splashText
                        startActivity(intent)
                        finish()
                        // 断网兜底：进的是缓存页而不是用户设置的启动页，给一句提示，
                        // 免得用户以为是启动页设置没生效（提示走 BaseActivity 的 Snackbar，
                        // 缓存页 onCreate 注册 EventBus 后就能收到这条 sticky 消息）。
                        if (!online) {
                            MsgUtil.showMsg(getString(R.string.msg_splash_offline_to_cache))
                        }
                        // 时间防呆：系统时间早于本包构建日期时，所有 HTTPS 证书都会被判成「尚未生效」，
                        // 先提醒用户改时间，免得他们对着一屏「网络错误」干着急（见 ClockSanity）
                        if (ClockSanity.isClockBehind(System.currentTimeMillis(), BuildConfig.VERSION_CODE)) {
                            MsgUtil.showMsgLong(getString(R.string.msg_clock_behind))
                        }
                        // F4：版本升级后的首次启动，自动展示当版更新日志。
                        // 排在这一句之后：日志页叠在首屏之上、返回即回到首屏，闪屏阶段既不阻塞
                        // UI 线程，也不插入登录 / Cookie 刷新流程中间。setup 尚未完成、要走
                        // SetupUIActivity 的分支不弹，免得和初始化向导抢屏幕。
                        //
                        // 已知重叠（本次未处理，需 AppInfoApi 侧配合）：AppInfoApi.check() 里
                        // 还有一条老链路——app_version_last < version 时会再弹一个内容重复的
                        // 「更新公告」ShowTextActivity，并负责删除已下载的旧更新包。详见报告。
                        if (pendingUpdateLog) {
                            pendingUpdateLog = false
                            startActivity(buildUpdateLogIntent())
                        }
                        // 启动通知（免责声明 / 夜深了）必须排在首屏 startActivity 之后，且在同一个
                        // UI 线程上弹出；否则 check() 的后台线程会抢跑，把 DialogActivity 压在首屏下面
                        AppInfoApi.showStartupNotices(this@SplashActivity)
                    }

                    // 断网时不发起任何启动网络请求：AppInfoApi.check 失败会弹「终端接口出现问题」，
                    // Cookie 刷新 / 公告 / 统计也只会一路超时——这些都留到下次有网启动再做。
                    if (online) {
                        if (SharedPreferencesUtil.getLong("mid", 0) != 0L) {
                            CenterThreadPool.run {
                                // 按凭证类型分流：TV/APP 登录（有 access_key）走统一的 APP token 刷新，
                                // 该接口会同时刷新 access_token 与 cookies；Web 登录走 web cookie 刷新。
                                if (SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, "").isNotEmpty()) {
                                    try {
                                        AppTokenRefreshApi.refreshAppToken()
                                    } catch (e: Exception) {
                                        Log.e("Splash", "APP token刷新失败: ${e.message}")
                                    }
                                } else {
                                    try {
                                        checkCookieRefresh()
                                    } catch (e: Exception) {
                                        Log.e("Splash", "Cookie刷新失败: ${e.message}")
                                    }
                                }
                                try {
                                    CookiesApi.checkCookies()
                                } catch (e: Exception) {
                                    Log.e("Splash", "Cookies检查失败: ${e.message}")
                                }
                            }
                        }
                        CenterThreadPool.run { AppInfoApi.check(this@SplashActivity) }

                        // 自建服务器这一路：公告（含服务端拉取、管理员逐条审核后透传的上游公告）
                        // 的启动差量弹窗，以及匿名使用统计（唯一安装数 + 日活），一天一次，设置里可关。
                        // 客户端已不再直连上游公告接口，所以这里只有这一个公告源。
                        // 两件事都包了异常：自建接口挂了不能影响启动流程。
                        CenterThreadPool.run {
                            try {
                                TerminalApi.checkAnnouncement()
                            } catch (e: Exception) {
                                Log.e("Splash", "公告拉取失败: ${e.message}")
                            }
                        }
                        CenterThreadPool.run { TelemetryReporter.reportIfNeeded() }
                    }

                } catch (e: Exception) {
                    // 放宽到 Exception：原来只接 JSONException，其它异常会直接打断启动流程
                    // （闪退或停在启动页）。任何失败都退化成进本地列表页，保证能进主界面。
                    stopTypewriter()
                    runOnUiThread { MsgUtil.err(e) }
                    val intent = Intent()
                    intent.setClass(this@SplashActivity, LocalListActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            } else {
                stopTypewriter()
                val intent = Intent()
                intent.setClass(this@SplashActivity, SetupUIActivity::class.java)
                startActivity(intent)
                finish()
                // 首次安装更容易碰上（新机 / 刚恢复出厂），初始化向导之后就要登录，同样提醒一次
                if (ClockSanity.isClockBehind(System.currentTimeMillis(), BuildConfig.VERSION_CODE)) {
                    MsgUtil.showMsgLong(getString(R.string.msg_clock_behind))
                }
            }
        }
    }
}