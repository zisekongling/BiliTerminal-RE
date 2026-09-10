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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView

import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.MenuActivity
import com.RobinNotBad.BiliClient.activity.dynamic.DynamicActivity
import com.RobinNotBad.BiliClient.event.SnackEvent
import com.RobinNotBad.BiliClient.tutorial.TutorialPagerActivity
import com.RobinNotBad.BiliClient.tutorial.TutorialStore
import com.RobinNotBad.BiliClient.tutorial.Tutorials
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

    // 记录本 Activity 创建时应用的主题，用于返回前台时检测变更并即时重建
    private var appliedTheme: String? = null

    override fun attachBaseContext(newBase: Context) {
        old_context = newBase
        super.attachBaseContext(BiliTerminal.getFitDisplayContext(newBase))
    }

    override fun onCreate(@Nullable savedInstanceState: Bundle?) {
        val theme = SharedPreferencesUtil.getString(ThemeManager.PREF_KEY_THEME, ThemeManager.THEME_DEFAULT)
        setTheme(ThemeManager.themeResId(theme))

        setRequestedOrientation(
            if (SharedPreferencesUtil.getBoolean("ui_landscape", false))
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        )

        super.onCreate(savedInstanceState)

        appliedTheme = SharedPreferencesUtil.getString(ThemeManager.PREF_KEY_THEME, ThemeManager.THEME_DEFAULT)

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

        applySystemBarInsets()

        val density = SharedPreferencesUtil.getInt("density", -1)
        if (density >= 72) {
            setDensity(density)
        }
    }

    /**
     * 系统栏避让。
     *
     * [ThemeManager.applyWindowTheme] 里调用了 `setDecorFitsSystemWindows(false)`，内容会绘制到
     * 系统栏（状态栏/导航栏/刘海）下方。此前全工程没有任何 insets 处理，结果是贴底控件与列表
     * 最后一项被导航栏压住。这里把系统栏 inset 叠加到根布局已有的 padding 上，用户自定义的
     * 「界面边距」设置（paddingH/V_percent）仍然保留。
     */
    private fun applySystemBarInsets() {
        val root = window.decorView.rootView
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            // 同时消费 displayCutout：横屏/挖孔机型上系统可能把内容排进刘海区域
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                baseLeft + bars.left,
                baseTop + bars.top,
                baseRight + bars.right,
                baseBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
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
    private var tutorialAutoTriggered: Boolean = false

    override fun onStart() {
        super.onStart()
        if (this !is InstanceActivity) setTopbarExit()
        setRound()
        if (eventBusEnabled() && !eventBusInit) {
            EventBus.getDefault().register(this)
            eventBusInit = true
        }
        autoTriggerTutorial()
        setupTopbarLongPressToHome()
    }

    /**
     * 长按顶栏（右侧时间区域）快速回到主菜单。
     *
     * readme 与 `docs/FEATURES.md` 一直承诺了这个手势，但代码里此前只有视频详情页实现了类似行为
     * （见 `VideoInfoActivity.setupLongPressToRoot`），这里补齐通用实现。
     * 子类若已自定义顶栏长按（如视频详情页），此处不覆盖。
     */
    private fun setupTopbarLongPressToHome() {
        val topBar = findViewById<View>(R.id.top) ?: return
        if (topBar.hasOnLongClickListeners()) return
        if (this is MenuActivity) return
        topBar.setOnLongClickListener {
            val intent = Intent(this, MenuActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            true
        }
    }

    /**
     * 集中式教程触发：按当前页面类名去 [Tutorials] 注册表里找未读教程。
     *
     * 新增教程只需改注册表，页面本身不用动。旧实现靠每个页面手写
     * `TutorialHelper.showTutorialList(...)`，漏写就永不展示（`tutorial_article` 就是这么漏掉的）。
     * 每个 Activity 实例只检查一次；教程页自身覆写 [tutorialAutoTriggerEnabled] 返回 false，避免递归。
     */
    private fun autoTriggerTutorial() {
        if (tutorialAutoTriggered) return
        tutorialAutoTriggered = true
        if (!tutorialAutoTriggerEnabled()) return

        val pending = Tutorials.forPage(javaClass).filter { !TutorialStore.isRead(it) }
        if (pending.isEmpty()) return
        TutorialPagerActivity.start(this, pending)
    }

    /** 是否参与教程自动触发。 */
    protected open fun tutorialAutoTriggerEnabled(): Boolean = true

    override fun onResume() {
        super.onResume()
        // 主题在别处被修改后，返回本页时即时重建以应用新主题
        val currentTheme = SharedPreferencesUtil.getString(ThemeManager.PREF_KEY_THEME, ThemeManager.THEME_DEFAULT)
        if (appliedTheme != null && appliedTheme != currentTheme) {
            recreate()
            return
        }
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
        return if (SharedPreferencesUtil.getBoolean("ui_landscape", false) && !force_single_column)
            CustomGridManager(this, landscapeSpanCount())
        else
            CustomLinearManager(this)
    }

    /**
     * 横屏列数。
     *
     * 原来固定 3 列：窄屏（横屏 640dp 左右）上每列不到 210dp，视频卡封面被压得很扁；
     * 平板/大屏又太空。改成按「每列至少 220dp」换算，下限 2 列。
     */
    private fun landscapeSpanCount(): Int {
        val widthDp = resources.configuration.screenWidthDp
            .takeIf { it > 0 }
            ?: (window_width / resources.displayMetrics.density).toInt()
        return maxOf(2, widthDp / 220)
    }

    fun setForceSingleColumn() {
        force_single_column = true
    }

    override fun isDestroyed(): Boolean {
        return lifecycle.currentState == Lifecycle.State.DESTROYED || isFinishing
    }

    fun getClassName(): String {
        return this.javaClass.simpleName
    }
}
