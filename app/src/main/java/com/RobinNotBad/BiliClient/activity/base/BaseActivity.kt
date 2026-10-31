package com.RobinNotBad.BiliClient.activity.base

import com.RobinNotBad.BiliClient.util.UiText

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
import com.RobinNotBad.BiliClient.ui.widget.RotaryEncoderSupport
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomGridManager
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.ui.appearance.AppearanceApplier
import com.RobinNotBad.BiliClient.ui.appearance.AppearanceManager
import com.RobinNotBad.BiliClient.ui.appearance.RoundListEffect
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.ui.appearance.GlassStyle
import com.RobinNotBad.BiliClient.util.AsyncLayoutInflaterX
import com.RobinNotBad.BiliClient.util.DisguiseGesture
import com.RobinNotBad.BiliClient.util.DisguisePrefs
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.RobinNotBad.BiliClient.util.TopbarLongPressPrefs
import com.RobinNotBad.BiliClient.util.ViewCapabilityProbe
import com.RobinNotBad.BiliClient.util.view.EditorEnterKeyGuard

import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


open class BaseActivity : AppCompatActivity() {
    @JvmField var window_width: Int = 0
    @JvmField var window_height: Int = 0
    @JvmField var old_context: Context? = null
    @JvmField val relayDynamicLauncher: ActivityResultLauncher<Intent> = DynamicActivity.getRelayDynamicLauncher(this)
    @JvmField val editDynamicLauncher: ActivityResultLauncher<Intent> = DynamicActivity.getEditDynamicLauncher(this)

    /**
     * 编辑动态的结果回调槽。
     *
     * <p>ActivityResultLauncher 只能在 Activity 上注册一次，没有 per-holder 的回调注册点，
     * 而「编辑完怎么刷新这条动态」只有发起方（列表适配器 / 详情页）知道。
     * 于是发起前把回调放这里，结果回来时由 [editDynamicLauncher] 取走并清空。
     */
    @JvmField var pendingDynamicEdit: ((String) -> Unit)? = null

    @JvmField var force_single_column: Boolean = false

    /**
     * 子类是否**强制**使用带 `windowSwipeToDismiss=false` 的 NoSwipe 主题。
     *
     * 为什么做成钩子而不是让子类在 `onCreate` 里自己 `setTheme`：主题统一在本类
     * [onCreate] 里设置（在 `super.onCreate` 之前），窗口属性在窗口创建时就已生效，
     * 子类事后再调 `setTheme` 既会被这里覆盖、也改不动已经在用的窗口属性。
     *
     * 需要它的场景：短视频页要在应用层实现「右滑进 UP 主页 / 左滑返回」，
     * 必须先吃掉系统窗口层的右滑返回（它直接 `finish()`，不走 `onBackPressed`）。
     */
    protected open val forceNoSwipeTheme: Boolean
        get() = false

    // 记录本 Activity 创建时的「外观版本号」，用于返回前台时检测配色/圆角/字体任一项变更并即时重建。
    // 用单个 Int 而不是逐个比对各设置项：将来再加第 4 个外观模块时不需要改这里。
    private var appliedAppearanceVersion: Int = -1

    // 记录本页面尚未完成的异步 inflate，供 onDestroy 取消。
    // asyncInflate 的布局是在后台线程池里解析的，页面可能在此之前就被关掉；
    // 不持有这个引用就没法在 onDestroy 里调用 AsyncLayoutInflaterX.cancel()，
    // 回调会打到已销毁的页面上（AGENTS.md 记录的坑：cancel() 从未被调用）。
    private var pendingAsyncInflater: AsyncLayoutInflaterX? = null

    override fun attachBaseContext(newBase: Context) {
        old_context = newBase
        super.attachBaseContext(BiliTerminal.getFitDisplayContext(newBase))
    }

    override fun onCreate(@Nullable savedInstanceState: Bundle?) {
        val theme = SharedPreferencesUtil.getString(ColorScheme.PREF_KEY_THEME, ColorScheme.THEME_DEFAULT)
        // 「禁用返回键」为的是拦住手表自带的右滑返回；那只手势在窗口层直接 finish，不走 onBackPressed，
        // 所以这里必须同时换到带 windowSwipeToDismiss=false 的 NoSwipe 主题。
        // 此前只改了 onBackPressed，右滑返回照旧生效——这就是 issue #1 里「全局屏蔽右滑返回失效」。
        setTheme(
            ColorScheme.themeResId(
                theme,
                noSwipe = forceNoSwipeTheme ||
                    SharedPreferencesUtil.getBoolean(SettingsKeys.BACK_DISABLE, false)
            )
        )

        setRequestedOrientation(
            if (SharedPreferencesUtil.getBoolean("ui_landscape", false))
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        )

        super.onCreate(savedInstanceState)

        appliedAppearanceVersion = AppearanceManager.version()

        ColorScheme.applyWindowTheme(this)
        // 玻璃质感：给主题叠卡片 / 按钮的覆盖层，必须早于子类的 setContentView（关闭时直接 return）
        GlassStyle.applyTo(this)

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

        installRotaryFallback()

        // 圆屏列表效果（实验性）：开关关闭时内部直接 return，零开销。
        // 设置在 onCreate 读取，改开关后重新进入页面生效。
        RoundListEffect.install(this)
    }

    /**
     * 系统栏避让。
     *
     * [ColorScheme.applyWindowTheme] 里调用了 `setDecorFitsSystemWindows(false)`，内容会绘制到
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

    /**
     * 表冠事件兜底（26.10.17）。
     *
     * 旋转编码器事件**只沿焦点路径分发**：页面上没有任何控件获焦时，事件一路退到 DecorView
     * 也没人消费。列表页的 `RecyclerView` 默认不抢焦点，旧实现只在 `Rotary*` 控件自身上挂
     * 监听，于是「设置页里能用、退出就失灵」——设置页能用只是因为点过输入框，`EditText`
     * 拿了焦点，事件恰好沿焦点链经过外层的 `RotaryRecyclerView`。
     *
     * 这里挂在 **DecorView** 上：`ViewRootImpl` 总是先把事件派给 DecorView，而 DecorView 只在
     * 所有子 View 都没处理时才调用自己的 `onGenericMotionEvent` 监听器，
     * 因此不会与控件级监听器重复滚动。
     */
    private fun installRotaryFallback() {
        val decor = window?.decorView ?: return
        decor.setOnGenericMotionListener { _, event ->
            RotaryEncoderSupport.handlePage(decor, event)
        }
    }

    fun setPageName(name: String) {
        val textView = findViewById<TextView>(R.id.pageName)
        textView?.text = name
    }

    fun setTopbarExit() {
        val view = findViewById<View>(R.id.top) ?: return
        // 首选框架能力：部分手表框架裁掉了 hasOnClickListeners()，探测失败就退回自己的标志位。
        // 两条路都保证「同一个 Activity 实例只装一次监听器」。
        val installed = ViewCapabilityProbe.probeBoolean(view, "hasOnClickListeners") { name, e ->
            Logu.d("view-probe", "$name 在本机不可用，已降级：${e.javaClass.simpleName}")
        } ?: topbarExitInstalled
        if (installed) return
        topbarExitInstalled = true
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
                    val paddingH = (window_width * 0.18).toInt()
                    val paddingV = (window_width * 0.03).toInt()
                    pagename.setPadding(paddingH, paddingV, paddingH, 0)

                    // 时钟的宿主容器有两种：绝大多数布局里 top 是 RelativeLayout，时钟直接挂在它下面；
                    // 首页那种带菜单区的布局里，时钟在 top 内的 menuArea(LinearLayout) 里。
                    // 必须按【真实父容器】决定 LayoutParams 类型 —— 给 LinearLayout 的子 View 塞
                    // RelativeLayout.LayoutParams，LinearLayout.measureHorizontal 强转时必崩
                    // （ClassCastException，且抛在 measure 阶段，外面的 try 根本兜不住）。
                    clock?.let { clockView ->
                        val clockParams = newTopbarParams(clockView)
                        if (clockParams is RelativeLayout.LayoutParams)
                            clockParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
                        clockView.layoutParams = clockParams
                        clockView.alpha = 0.85f
                        clockView.textSize = 12f
                    }

                    val pnParams = newTopbarParams(pagename)
                    if (pnParams is RelativeLayout.LayoutParams)
                        pnParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
                    pnParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    pnParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    pnParams.topMargin = (window_height * 0.01).toInt() + ToolsUtil.sp2px(12f)
                    pnParams.bottomMargin = (window_height * 0.01).toInt()
                    pagename.layoutParams = pnParams
                    pagename.setPadding(0, 0, ToolsUtil.dp2px(5f), 0)
                    Logu.d("round", "ok")
                } catch (e: Throwable) {
                    MsgUtil.err(UiText.of("圆屏适配执行错误："), e)
                }
            }
        }
    }

    /**
     * 按顶栏 View 的【真实父容器】类型造一份 LayoutParams。
     *
     * 圆屏适配要把 pageName / timeText 改成 WRAP_CONTENT + 居中，但这两个控件的宿主容器
     * 不统一：大多数布局里父容器是 `@id/top`(RelativeLayout)，个别布局（如顶栏带菜单区的
     * `activity_simple_main_refresh`）时钟在 `@id/menuArea`(LinearLayout) 里。直接把
     * RelativeLayout.LayoutParams 塞给 LinearLayout 的子 View，会在下一帧
     * `LinearLayout.measureHorizontal` 强转时抛 ClassCastException —— 那是 measure 阶段，
     * 调用处的 try/catch 兜不住，直接崩。
     *
     * 这里只覆盖工程里实际存在的两种容器，其余（FrameLayout 等）退化为 MarginLayoutParams，
     * 至少不会崩。
     */
    private fun newTopbarParams(view: View): ViewGroup.MarginLayoutParams {
        val width = ViewGroup.LayoutParams.WRAP_CONTENT
        val height = ViewGroup.LayoutParams.WRAP_CONTENT
        val current = view.layoutParams
        return when (view.parent) {
            is RelativeLayout -> RelativeLayout.LayoutParams(current ?: RelativeLayout.LayoutParams(width, height))
            is android.widget.LinearLayout -> android.widget.LinearLayout.LayoutParams(
                current ?: android.widget.LinearLayout.LayoutParams(width, height)
            )
            else -> ViewGroup.MarginLayoutParams(current ?: ViewGroup.MarginLayoutParams(width, height))
        }
    }

    /**
     * 兜住平台在「回车推进焦点」时自己抛的 ISE（线上崩溃 #66；成因与判据不一致的两端都在
     * [EditorEnterKeyGuard] 里）。
     *
     * <h3>为什么覆写 dispatchKeyEvent 而不是 onKeyDown / onKeyUp</h3>
     * 崩溃发生在**视图**的 `TextView.onKeyUp` 里，而它是被 `super.dispatchKeyEvent(event)`
     * 一路调到的（`Activity.dispatchKeyEvent` → `PhoneWindow.superDispatchKeyEvent` →
     * `DecorView` → 视图树），只有在这一层包住才能赶在异常冒泡回 `ViewRootImpl`（那里会直接
     * 崩进程）之前接住；`onKeyDown` / `onKeyUp` 是视图消费不掉后才轮到的回调，根本走不到。
     *
     * 只吞「消息原文等于平台那句」的 ISE，其它异常照旧抛出——同名的 ISE 别处可能是真 bug。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return try {
            super.dispatchKeyEvent(event)
        } catch (e: IllegalStateException) {
            if (!EditorEnterKeyGuard.swallowFocusSearchFailure("BaseActivity", e)) throw e
            true
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

    // 顶栏两个监听器的「降级标志位」。
    // View.hasOnClickListeners() / hasOnLongClickListeners() 虽然是 API 15+ 的公开 API，
    // 但部分杂牌手表的框架把方法裁掉了，裸调直接 NoSuchMethodError 闪退。现在统一走
    // ViewCapabilityProbe：能调就走框架判断，调不了就退回这两个标志位，功能不受影响。
    private var topbarExitInstalled: Boolean = false
    private var topbarLongPressInstalled: Boolean = false

    /** 顶栏双击伪装延迟装载已重试的次数（见 [installTopbarDisguise]，必须封顶）。 */
    private var topbarDisguiseRetries: Int = 0

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
        setupTopbarDoubleTapDisguise()
    }

    /**
     * 长按顶栏（整条 `R.id.top`，不只是右侧时间区域）快速打开菜单列表。
     *
     * readme 与 `docs/FEATURES.md` 一直承诺了这个手势，但代码里此前只有视频详情页实现了类似行为
     * （见 `VideoInfoActivity.setupLongPressToRoot`），这里补齐通用实现。
     * 子类若自己接管了顶栏长按（视频详情页），[handlesTopbarLongPress] 返回 true，此处不再安装。
     *
     * 手势本身受 `SettingsKeys.TOPBAR_LONG_PRESS_MODE`（`util/TopbarLongPressPrefs`）控制：
     * 只有「全部页面」模式下普通页面才打开菜单列表；其余模式监听器**仍然安装**，
     * 只是在按下的回调里返回 true 把手势吞掉。
     */
    private fun setupTopbarLongPressToHome() {
        // 子类自己接管顶栏长按（视频详情页的「关闭所有详情页」）时，基类不安装通用监听器。
        // 不能只靠 hasOnLongClickListeners() 探测：部分手表框架没有这个方法，探测失败（返回 null）
        // 时这里会覆盖掉子类刚装好的监听器，视频详情页的长按就会莫名变成「打开菜单」。
        if (handlesTopbarLongPress()) return
        val topBar = findViewById<View>(R.id.top) ?: return
        // 同上：这个 hasOnLongClickListeners() 就是在手表上抛 NoSuchMethodError 的那个方法
        val installed = ViewCapabilityProbe.probeBoolean(topBar, "hasOnLongClickListeners") { name, e ->
            Logu.d("view-probe", "$name 在本机不可用，已降级：${e.javaClass.simpleName}")
        } ?: topbarLongPressInstalled
        if (installed) return
        if (this is MenuActivity) return
        topbarLongPressInstalled = true
        topBar.setOnLongClickListener {
            // 模式在按下这一刻才读：用户可能在别的页面改完设置再回来，监听器不必重装。
            // 「不响应」与「仅视频详情页」两种模式下本页都必须返回 true 把手势吃掉，不能返回 false——
            // 长按回调返回 false 时 Android 会在抬手时把它回落成一次单击，而顶栏单击是
            // 「退出当前页 / 打开菜单」，等于把用户想关掉的手势换成了另一种误触。
            if (!TopbarLongPressPrefs.isEnabledForNormalPages()) return@setOnLongClickListener true
            val intent = Intent(this, MenuActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            true
        }
    }

    /**
     * 双击顶栏 → 打开伪装页（issue #22，建在 Gitee 侧，GitHub 镜像上没有该编号）。
     *
     * 实现见 [DisguiseGesture]，需求与验收见 `docs/superpowers/specs/2026-10-22-disguise-mode.md`，
     * 本节只处理**本页的安装时序**。
     *
     * 每次 `onStart` 都重新判断一遍：开关可能在别的页面被改过，用户也可能刚关掉它。
     */
    private fun setupTopbarDoubleTapDisguise() {
        topbarDisguiseRetries = 0
        installTopbarDisguise()
    }

    /**
     * 真正的安装逻辑，必要时延迟重试一次。
     *
     * **为什么要重试**：异步 inflate 的页面（`asyncInflate`，20 处）在 `onStart` 时
     * `setContentView` 还没执行完，`findViewById(R.id.top)` 返回 null；直接 return 会让这些页面
     * 首次进入时手势完全无效（退出再进才生效）。
     *
     * **为什么必须限次数**：有 11 个 `BaseActivity` 页面**布局里本来就没有** `R.id.top`
     * （Dialog / ConfirmDialog / ListDialog / InputDialog / Catch / Tutorial / TutorialPager /
     * CaptchaWebView / QRLoginRegister / JumpToPlayer / Download）。无上限的 `post` 会让这些页面
     * 每帧投递一次、一直耗电到页面结束——所以这里封顶 [DISGUISE_INSTALL_MAX_RETRIES] 次、
     * 每次间隔 [DISGUISE_INSTALL_RETRY_MS]，并在页面已经开始销毁时立刻停。
     */
    private fun installTopbarDisguise() {
        if (isFinishing || isDestroyed) return
        val enabled = DisguisePrefs.isEnabled()
        val topBar = findViewById<View?>(R.id.top)
        if (topBar == null) {
            if (enabled && topbarDisguiseRetries < DISGUISE_INSTALL_MAX_RETRIES) {
                topbarDisguiseRetries++
                window?.decorView?.postDelayed(
                    { installTopbarDisguise() },
                    DISGUISE_INSTALL_RETRY_MS
                )
            } else if (enabled) {
                // 本页布局里没有顶栏（11 个页面如此），或视图一直没装好。
                // 留一条痕迹：用户报"这页双击没反应"时，先看这句就知道是"页面没有顶栏"而不是手势坏了。
                Logu.d("disguise", "${getClassName()} 没有 R.id.top，本页不安装双击伪装")
            }
            return
        }
        if (!enabled) {
            DisguiseGesture.detach(topBar)
            return
        }
        DisguiseGesture.install(this, topBar)
    }

    /**
     * 子类是否自己接管了顶栏（`R.id.top`）长按。
     *
     * 视频详情页长按顶栏是「关闭所有详情页」（见 `VideoInfoActivity.setupLongPressToRoot`），
     * 与普通页面的「打开菜单列表」不是同一件事，所以它在 `onCreate` 里自己装监听器、
     * 并让本方法返回 true，基类就不再安装通用监听器。
     *
     * **不要**再靠 `hasOnLongClickListeners()` 探测来避免覆盖：那套探测在裁掉该方法的杂牌手表上
     * 返回 null，基类照样会覆盖子类监听器（视频详情页长按变成打开菜单）。
     */
    protected open fun handlesTopbarLongPress(): Boolean = false

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
        // 外观（配色/圆角/字体任一项）在别处被修改后，返回本页时即时重建以应用新外观。
        // 只比一个 Int：三个模块共用一个版本号，详见 AppearanceManager。
        if (appliedAppearanceVersion >= 0 && appliedAppearanceVersion != AppearanceManager.version()) {
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
        // 取消还没交付的异步 inflate：布局在线程池里已经跑起来时无法中断，
        // 但可以让它到达主线程后丢弃结果，避免 setContentView / finishInflate 打在已销毁的页面上。
        pendingAsyncInflater?.cancel()
        pendingAsyncInflater = null
        if (eventBusInit) {
            try {
                EventBus.getDefault().unregister(this)
            } catch (e: Exception) {
                Logu.e("BaseActivity", "Error unregistering event bus: ${e.message}")
            }
            eventBusInit = false
        }
    }

    /**
     * `setContentView` 之后必被触发的钩子，用来套用自定义字体与圆角档位。
     *
     * 选这里是因为它能**同时覆盖**普通布局与 `asyncInflate` 的替换布局，
     * 且只在内容变化时跑一次。两项都未启用/默认时 [AppearanceApplier] 立即返回，零开销。
     */
    override fun onContentChanged() {
        super.onContentChanged()
        AppearanceApplier.applyToContentView(this)
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
        val inflater = AsyncLayoutInflaterX(this)
        pendingAsyncInflater = inflater
        inflater.inflate(id, null) { view, layoutId, _ ->
            // 布局是在后台线程池里解析的，完成时页面可能已经销毁（本基类有 20 个页面在用）。
            // 对已销毁的页面 setContentView 会失败，而且 onDestroy 里已经 cancel 过，
            // 这里再兜一层守卫，保证回调不会跑到宿主上。
            if (isDestroyed) return@inflate
            setContentView(view)

            // 直接 setContentView 是「硬切」：loading 页被内容页瞬间替换，
            // 低性能手表上转场早已结束，观感就是白屏跳变；这里做一次约 3 帧 / 300ms 的淡入。
            // 实现放在 AsyncLayoutInflaterX.fadeIn（纯代码，不依赖系统动画缩放，也不新增任何 res 资源）。
            AsyncLayoutInflaterX.fadeIn(view) { isDestroyed }

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

    private companion object {
        /**
         * 顶栏伪装延迟装载的最大重试次数。
         *
         * 有 11 个页面布局里根本没有 `R.id.top`，与「asyncInflate 还没 inflate 完」外观一致，
         * 只能靠重试封顶来区分：20 次 × 50ms = 1 秒，足够慢设备把布局装完，
         * 又没有 top 的页面最多耗 1 秒就彻底停手。
         */
        const val DISGUISE_INSTALL_MAX_RETRIES = 20

        /** 顶栏伪装延迟装载的重试间隔（毫秒）。 */
        const val DISGUISE_INSTALL_RETRY_MS = 50L
    }
}
