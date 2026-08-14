package com.RobinNotBad.BiliClient.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.RobinNotBad.BiliClient.activity.MenuActivity
import com.RobinNotBad.BiliClient.activity.settings.login.LoginActivity
import com.RobinNotBad.BiliClient.ui.mobile.MobileShellActivity
import com.RobinNotBad.BiliClient.ui.theme.BiliColors
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

class ModernSplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var scheduledRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()

        setContentView(createSplashView())

        val hasLogin = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) != 0L
        val hasSetup = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.setup, false)

        val delay = if (hasSetup) 800L else 2000L
        val isMobileMode = SharedPreferencesUtil.getBoolean("ui_mobile_mode", false)
        val targetClass = when {
            !hasLogin -> LoginActivity::class.java
            isMobileMode -> MobileShellActivity::class.java
            else -> MenuActivity::class.java
        }

        scheduledRunnable = Runnable {
            if (!isFinishing && !isDestroyed) {
                startActivity(Intent(this, targetClass))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
        }
        handler.postDelayed(scheduledRunnable!!, delay)
    }

    override fun onDestroy() {
        scheduledRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun applyTheme() {
        window.statusBarColor = ThemeManager.BACKGROUND
        window.navigationBarColor = ThemeManager.BACKGROUND
    }

    private fun createSplashView(): android.view.View {
        return android.widget.FrameLayout(this).apply {
            setBackgroundColor(ThemeManager.BACKGROUND)

            val centerLayout = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
            }

            val appIcon = android.widget.TextView(context).apply {
                text = "哔哩终端"
                textSize = 28f
                setTextColor(ThemeManager.TEXT_PRIMARY)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = android.view.Gravity.CENTER
            }

            val subtitle = android.widget.TextView(context).apply {
                text = "BiliTerminal"
                textSize = 14f
                setTextColor(ThemeManager.TEXT_TERTIARY)
                gravity = android.view.Gravity.CENTER
                setPadding(0, 8, 0, 0)
            }

            val version = android.widget.TextView(context).apply {
                text = "v3.0.0"
                textSize = 12f
                setTextColor(ThemeManager.TEXT_TERTIARY)
                gravity = android.view.Gravity.CENTER
                setPadding(0, 4, 0, 0)
                alpha = 0.6f
            }

            val loadingIndicator = android.widget.ProgressBar(context).apply {
                isIndeterminate = true
                val padding = (24 * resources.displayMetrics.density).toInt()
                setPadding(0, padding, 0, 0)
                indeterminateTintList = android.content.res.ColorStateList.valueOf(ThemeManager.PRIMARY)
            }

            centerLayout.addView(appIcon)
            centerLayout.addView(subtitle)
            centerLayout.addView(loadingIndicator)
            centerLayout.addView(version)

            addView(centerLayout, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            ))

            val bottomText = android.widget.TextView(context).apply {
                text = "Made with ❤ for Bilibili"
                textSize = 11f
                setTextColor(ThemeManager.TEXT_TERTIARY)
                gravity = android.view.Gravity.CENTER
                alpha = 0.4f
            }

            val bottomParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            )
            bottomParams.bottomMargin = (40 * resources.displayMetrics.density).toInt()
            addView(bottomText, bottomParams)
        }
    }
}