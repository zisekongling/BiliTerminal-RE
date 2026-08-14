package com.RobinNotBad.BiliClient.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.util.Pair
import android.widget.TextView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
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

        // 立即进行非网络相关的初始化，减少白屏时间
        CenterThreadPool.run {
            if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.setup, false)) {
                try {
                    var firstActivity: String? = null
                    val sortConf = SharedPreferencesUtil.getString(SharedPreferencesUtil.MENU_SORT, "")
                    if (!TextUtils.isEmpty(sortConf)) {
                        val splitName = sortConf.split(";")
                        for (name in splitName) {
                            if (!MenuActivity.btnNames.containsKey(name)) {
                                for (entry in MenuActivity.btnNames.entries) {
                                    firstActivity = entry.key
                                    break
                                }
                            } else {
                                firstActivity = name
                            }
                            break
                        }
                    } else {
                        for (entry in MenuActivity.btnNames.entries) {
                            firstActivity = entry.key
                            break
                        }
                    }

                    val activityClass: Class<out InstanceActivity> = MenuActivity.btnNames[firstActivity]!!.second

                    val intent = Intent()
                    intent.setClass(this@SplashActivity, activityClass ?: RecommendActivity::class.java)
                    intent.putExtra("from", firstActivity)

                    runOnUiThread {
                        stopTypewriter()
                        splashTextView.text = splashText
                        startActivity(intent)
                        finish()
                    }

                    // 网络相关初始化延迟到主界面后执行，不阻塞启动流程
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
        SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, "")
        SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, "")
        NetWorkUtil.refreshHeaders()
    }

    private fun applyTheme() {
        ThemeManager.applyWindowTheme(this)
    }
}