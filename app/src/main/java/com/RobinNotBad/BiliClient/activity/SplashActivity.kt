package com.RobinNotBad.BiliClient.activity

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
import com.RobinNotBad.BiliClient.BiliTerminalApp
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.settings.setup.SetupUIActivity
import com.RobinNotBad.BiliClient.activity.video.RecommendActivity
import com.RobinNotBad.BiliClient.activity.video.local.LocalListActivity
import com.RobinNotBad.BiliClient.api.AppInfoApi
import com.RobinNotBad.BiliClient.api.CookieRefreshApi
import com.RobinNotBad.BiliClient.api.CookiesApi
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import com.RobinNotBad.BiliClient.util.AccountManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.PerformanceManager
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

@SuppressLint("CustomSplashScreen")
class SplashActivity : Activity() {

    private lateinit var splashTextView: TextView
    private var splashText: String = "欢迎使用\nRE:哔哩终端"

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

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(BiliTerminal.getFitDisplayContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        setContentView(R.layout.activity_splash)

        splashTextView = findViewById(R.id.splashText)
        splashText = SharedPreferencesUtil.getString("ui_splashtext", "欢迎使用\nRE:哔哩终端")
        startTypewriter(splashText)

        // Debug 构建下：若未授予悬浮窗权限，先跳去授权再继续启动流程，确保 UETool 能显示
        if (ensureUEToolOverlayPermission()) return

        // 启动主流程（抽取成单独方法，供授权回调再次调用）
        proceedSplashFlow()
    }

    @Throws(IOException::class)
    private fun checkCookieRefresh() {
        try {
            val cookieInfo = CookieRefreshApi.cookieInfo()
            if (cookieInfo.optBoolean("refresh")) {
                Log.e("Cookies", "需要刷新")
                if (SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "") != "") {
                    val correspondPath = CookieRefreshApi.getCorrespondPath(cookieInfo.getLong("timestamp"))
                    Log.e("CorrespondPath", correspondPath)
                    val refreshCsrf = CookieRefreshApi.getRefreshCsrf(correspondPath)
                    Log.e("RefreshCsrf", refreshCsrf)
                    if (CookieRefreshApi.refreshCookie(refreshCsrf)) {
                        MsgUtil.showMsg("Cookies已刷新")
                        AccountManager.saveCurrentAccount()
                    } else {
                        MsgUtil.showMsgLong("登录信息过期，请重新登录！")
                        resetLogin()
                    }
                }
            }
        } catch (e: JSONException) {
            MsgUtil.showMsgLong("登录信息过期，请重新登录！")
            resetLogin()
        }
    }

    private fun resetLogin() {
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, 0L)
        SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf, "")
        NetWorkUtil.setCookiesString("")
        SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, "")
    }

    private fun applyTheme() {
        ThemeManager.applyWindowTheme(this)
    }

    /**
     * Debug 构建下，检查悬浮窗权限：
     * - 已授权 → 返回 false，让启动流程继续
     * - 未授权 → 跳转系统设置授权页并返回 true，在 onActivityResult 再继续启动
     *
     * Release 构建不做任何处理。
     */
    private fun ensureUEToolOverlayPermission(): Boolean {
        if (!BiliTerminalApp.isDebugBuild()) return false
        if (BiliTerminalApp.canDrawOverlaysCompat(this)) {
            // 已授权：直接显示 UETool 悬浮窗
            Handler(Looper.getMainLooper()).postDelayed({ BiliTerminalApp.showUEToolMenu() }, 300L)
            return false
        }
        // 未授权：先跳转授予悬浮窗权限
        BiliTerminalApp.requestOverlayPermission(this)
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // 从悬浮窗授权页返回
        if (requestCode == BiliTerminalApp.REQUEST_OVERLAY_PERMISSION_FOR_UETOOL) {
            if (BiliTerminalApp.canDrawOverlaysCompat(this)) {
                // 授权成功：立即显示 UETool，然后继续原来的启动流程
                Handler(Looper.getMainLooper()).postDelayed({ BiliTerminalApp.showUEToolMenu() }, 200L)
            } else {
                // 用户未授予：Toast 提示，不阻塞启动
                try {
                    android.widget.Toast.makeText(
                        this,
                        "未授予悬浮窗权限，UETool 调试悬浮窗不会显示",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } catch (_: Throwable) {
                }
            }
            // 继续启动主流程
            proceedSplashFlow()
        }
    }

    /**
     * 从 onCreate 里抽取出来的启动主流程，供授权页返回后再次调用
     */
    private fun proceedSplashFlow() {
        CenterThreadPool.run {
            if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.setup, false)) {
                try {
                    val firstActivity = SharedPreferencesUtil.loadMenuEnabled().firstOrNull()

                    val activityClass = MenuActivity.btnNames[firstActivity]?.second

                    val intent = Intent()
                    intent.setClass(this@SplashActivity, activityClass ?: RecommendActivity::class.java)
                    intent.putExtra("from", firstActivity)

                    runOnUiThread {
                        stopTypewriter()
                        splashTextView.text = splashText
                        startActivity(intent)
                        finish()
                    }

                    if (SharedPreferencesUtil.getLong("mid", 0) != 0L) {
                        CenterThreadPool.run {
                            try {
                                checkCookieRefresh()
                            } catch (e: Exception) {
                                Log.e("Splash", "Cookie刷新失败: ${e.message}")
                            }
                            try {
                                CookiesApi.checkCookies()
                            } catch (e: Exception) {
                                Log.e("Splash", "Cookies检查失败: ${e.message}")
                            }
                        }
                    }
                    CenterThreadPool.run { AppInfoApi.check(this@SplashActivity) }

                } catch (e: JSONException) {
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
            }
        }
    }
}