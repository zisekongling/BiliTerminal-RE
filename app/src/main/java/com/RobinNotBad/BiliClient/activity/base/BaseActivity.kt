package com.RobinNotBad.BiliClient.activity.base

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.Display
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.TextView

import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView

import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.dynamic.DynamicActivity
import com.RobinNotBad.BiliClient.event.SnackEvent
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomGridManager
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import com.RobinNotBad.BiliClient.util.AsyncLayoutInflaterX
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil

import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


open class BaseActivity : AppCompatActivity() {
    @JvmField var window_width: Int = 0
    @JvmField var window_height: Int = 0
    @JvmField var old_context: Context? = null
    @JvmField val relayDynamicLauncher: ActivityResultLauncher<Intent> = DynamicActivity.getRelayDynamicLauncher(this)
    @JvmField var force_single_column: Boolean = false

    override fun attachBaseContext(newBase: Context) {
        old_context = newBase
        super.attachBaseContext(BiliTerminal.getFitDisplayContext(newBase))
    }

    override fun onCreate(@Nullable savedInstanceState: Bundle?) {
        val theme = SharedPreferencesUtil.getString(ThemeManager.PREF_KEY_THEME, ThemeManager.THEME_BILIBILI_PINK)
        val themeResId = when (theme) {
            ThemeManager.THEME_ZHIHU_BLUE -> R.style.Theme_ZhihuBlue
            ThemeManager.THEME_IQIYI_GREEN -> R.style.Theme_IQIYIGreen
            ThemeManager.THEME_PURPLE_FANTASY -> R.style.Theme_PurpleFantasy
            ThemeManager.THEME_RAINBOW_FANTASY -> R.style.Theme_RainbowFantasy
            ThemeManager.THEME_CLASSIC_GRAY -> R.style.Theme_ClassicGray
            else -> R.style.Theme_BiliClient
        }
        setTheme(themeResId)

        setRequestedOrientation(
            if (SharedPreferencesUtil.getBoolean("ui_landscape", false) && !SharedPreferencesUtil.getBoolean("ui_mobile_mode", false))
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        )

        super.onCreate(savedInstanceState)

        ThemeManager.applyWindowTheme(this)

        val paddingH_percent = SharedPreferencesUtil.getInt("paddingH_percent", 0)
        val paddingV_percent = SharedPreferencesUtil.getInt("paddingV_percent", 0)

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display: Display = windowManager.defaultDisplay
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= 17) display.getRealMetrics(metrics)
        else display.getMetrics(metrics)

        val scrW = metrics.widthPixels
        val scrH = metrics.heightPixels
        if (paddingH_percent != 0 || paddingV_percent != 0) {
            Logu.d("debug", "调整边距")
            val paddingH = scrW * paddingH_percent / 100
            val paddingT = scrH * paddingV_percent / 100
            var paddingB = paddingT
            if (SharedPreferencesUtil.getBoolean("player_ui_round", false))
                paddingB += (scrH * 0.03).toInt()
            window_width = scrW - paddingH * 2
            window_height = scrH - paddingT - paddingB
            val rootView = this.window.decorView.rootView
            rootView.setPadding(paddingH, paddingT, paddingH, paddingB)
        } else {
            window_width = scrW
            window_height = scrH
        }

        val density = SharedPreferencesUtil.getInt("density", -1)
        if (density >= 72) {
            setDensity(density)
        }
    }

    override fun onBackPressed() {
        if (!SharedPreferencesUtil.getBoolean("back_disable", false)) super.onBackPressed()
    }

    fun setPageName(name: String) {
        val textView = findViewById<TextView>(R.id.pageName)
        textView?.text = name
    }

    fun setTopbarExit() {
        val view = findViewById<View>(R.id.top) ?: return
        if (Build.VERSION.SDK_INT > 17 && view.hasOnClickListeners()) return
        view.setOnClickListener {
            if (Build.VERSION.SDK_INT < 17 || !isDestroyed) {
                finish()
            }
        }
        Logu.d("debug", "set_exit")
    }

    fun setRound() {
        val pagename = findViewById<TextView>(R.id.pageName)
        val clock = findViewById<TextView>(R.id.timeText)
        if (pagename != null) {
            pagename.maxLines = 1
            pagename.ellipsize = TextUtils.TruncateAt.END
            if (SharedPreferencesUtil.getBoolean("player_ui_round", false)) {
                try {
                    val params = pagename.layoutParams
                    val paddingH = (window_width * 0.18).toInt()
                    val paddingV = (window_width * 0.03).toInt()
                    pagename.setPadding(paddingH, paddingV, paddingH, 0)
                    if (params is RelativeLayout.LayoutParams) {
                        val clockParams = RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        clockParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
                        clock.layoutParams = clockParams
                        clock.alpha = 0.85f
                        clock.textSize = 12f

                        val pnParams = RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        pnParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
                        pnParams.topMargin = (window_height * 0.01).toInt() + ToolsUtil.sp2px(12f)
                        pnParams.bottomMargin = (window_height * 0.01).toInt()
                        pagename.layoutParams = pnParams
                        pagename.setPadding(0, 0, ToolsUtil.dp2px(5f), 0)
                        Logu.d("round", "ok")
                    }
                } catch (e: Throwable) {
                    MsgUtil.err("圆屏适配执行错误：", e)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (Build.VERSION.SDK_INT < 17 || !isDestroyed) {
                finish()
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    fun report(e: Exception) {
        runOnUiThread { MsgUtil.err(getClassName(), e) }
    }

    private var eventBusInit: Boolean = false

    override fun onStart() {
        super.onStart()
        if (this !is InstanceActivity) setTopbarExit()
        setRound()
        if (eventBusEnabled() && !eventBusInit) {
            EventBus.getDefault().register(this)
            eventBusInit = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (eventBusEnabled() && !isFinishing && !isDestroyed) {
            try {
                EventBus.getDefault().getStickyEvent(SnackEvent::class.java)?.let { onEvent(it) }
            } catch (e: Exception) {
                Logu.e("BaseActivity", "Error processing snack event in onResume: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (eventBusInit) {
            try {
                EventBus.getDefault().unregister(this)
            } catch (e: Exception) {
                Logu.e("BaseActivity", "Error unregistering event bus: ${e.message}")
            }
            eventBusInit = false
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onEvent(event: SnackEvent) {
        if (isDestroyed || isFinishing) return
        try {
            val rootView = window.decorView.rootView
            if (rootView != null) {
                MsgUtil.processSnackEvent(event, rootView)
            }
        } catch (e: Exception) {
            Logu.e("BaseActivity", "Error processing snack event: ${e.message}")
        }
    }

    protected open fun eventBusEnabled(): Boolean {
        return SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.SNACKBAR_ENABLE, true)
    }

    fun setDensity(targetDensityDpi: Int) {
        if (Build.VERSION.SDK_INT < 17) return
        val resources: Resources = resources

        if (resources.configuration.densityDpi == targetDensityDpi) return

        val configuration: Configuration = resources.configuration
        configuration.densityDpi = targetDensityDpi
        configuration.fontScale = 1f
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }

    protected fun asyncInflate(id: Int, callBack: InflateCallBack) {
        setContentView(R.layout.activity_loading)
        AsyncLayoutInflaterX(this).inflate(id, null) { view, layoutId, _ ->
            setContentView(view)

            if (this is InstanceActivity) (this as InstanceActivity).setMenuClick()
            else setTopbarExit()

            setRound()
            callBack.finishInflate(view, layoutId)
        }
    }

    protected fun interface InflateCallBack {
        fun finishInflate(view: View, id: Int)
    }

    fun getLayoutManager(): RecyclerView.LayoutManager {
        return if (SharedPreferencesUtil.getBoolean("ui_landscape", false) && !SharedPreferencesUtil.getBoolean("ui_mobile_mode", false) && !force_single_column)
            CustomGridManager(this, 3)
        else
            CustomLinearManager(this)
    }

    fun setForceSingleColumn() {
        force_single_column = true
    }

    /**
     * 判断是否启用了手机模式
     */
    fun isMobileMode(): Boolean {
        return SharedPreferencesUtil.getBoolean("ui_mobile_mode", false)
    }

    /**
     * 获取当前模式下的卡片圆角半径（px）
     * 手机模式使用pilipala的10dp，普通模式使用默认6dp
     */
    fun getCardCornerRadiusPx(): Float {
        val dp = if (isMobileMode()) 10f else 6f
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics
        )
    }

    override fun isDestroyed(): Boolean {
        return lifecycle.currentState == Lifecycle.State.DESTROYED || isFinishing
    }

    fun getClassName(): String {
        return this.javaClass.simpleName
    }
}
