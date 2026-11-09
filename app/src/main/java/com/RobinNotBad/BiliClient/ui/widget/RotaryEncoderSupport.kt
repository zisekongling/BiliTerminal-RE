package com.RobinNotBad.BiliClient.ui.widget

import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.view.ViewConfigurationCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 表冠（旋转编码器）滚动的公共实现。
 *
 * 合并自原先三份近乎逐行的实现：[RotaryRecyclerView]、[RotaryScrollView]、[RotaryNestedScrollView]。
 * 三者只有「读哪个灵敏度设置键」与「滚动后是否抢焦点」两处差异，但基类分别是
 * `RecyclerView` / `ScrollView` / `NestedScrollView`，无法用共同父类归并，
 * 因此这里用**组合**：控件各自保留自己的事件接入方式（监听器 / dispatch 覆写），
 * 把「判定 + 换轴值 + 滚动」这段真正重复的逻辑收进来。纯判定部分在 [RotaryEventPolicy]。
 *
 * 两类入口：
 * 1. 控件级——[handle]，由三个 `Rotary*` 控件自己的监听器调用（只在该控件拿到事件时触发）；
 * 2. 页面级兜底——[handlePage]，由 `BaseActivity` 挂在 **DecorView** 的 generic motion 监听器上。
 *
 * **为什么必须有页面级兜底**：旋转编码器事件只沿焦点路径分发，页面上没有任何控件获焦时，
 * 事件会一路退到 DecorView 而没人消费。列表页的 `RecyclerView` 默认不抢焦点，于是
 * 「设置页里能用、退出就失灵」——设置页能用只是因为点过输入框，`EditText` 拿了焦点，
 * 事件恰好沿焦点链经过外层的 `RotaryRecyclerView`。DecorView 监听器只在**所有子 View
 * 都没处理**时才会被调用，所以不会和控件级监听器重复滚动。
 *
 * **26.10.17 起开关与灵敏度改成「每次事件现读」**（原先只在 `onAttachedToWindow` 读一次）：
 * 手表上改完设置通常不会重进页面（返回栈里的页面只是不可见，`onAttachedToWindow` 不会再触发），
 * 旧写法等于「改完不生效」。SharedPreferences 的读取是内存 Map，热路径上没有性能问题。
 *
 * **26.11.07 修反馈 #103/#107（「视频详情-简介里表冠不适配」）做了三件事**，都是能静态指认的缺陷：
 * 1. 位移算出 0 时**不再吞事件**（[RotaryEventPolicy.shouldConsume]）。原先控件级 `handle()`
 *    不论算出多少都 return true，事件被吃掉、画面不动，父级和 DecorView 兜底也接不到；
 * 2. 滚动量在 `AXIS_SCROLL` 为 0 时**回落 `AXIS_VSCROLL`**（[RotaryEventPolicy.rawScrollRatio]）。
 *    只读单轴时，把量放在另一轴上的 ROM 会恒算出 0，与第 1 条叠加就是「彻底没反应」；
 * 3. 页面级兜底找目标时**要求与窗口有实际交集**（[isCandidateVisible]），不再只看 `isShown()`。
 *    `isShown()` 只看 VISIBILITY 位，而视频详情页 `viewPager.offscreenPageLimit = 页数`，
 *    离屏页始终 attached + VISIBLE，照旧写法可能滚到用户看不见的那一页。
 *
 * 另外加了 W 级诊断日志（[probe]）：内存环形缓冲只留 W/E/F，反馈页勾「附带应用日志」会把
 * 它一起带上来。同一个进程最多 [PROBE_MAX] 条，正常用户看不到任何变化，但下次「转不动」的
 * 反馈里就能直接读出事发时的 action / source / 两个轴值 / 命中目标——这比再猜一轮省事。
 *
 * **26.11.07.1 起总开关关闭时也接管，但顺序反过来：系统优先、App 补位**（issue #21：
 * Wear OS 5 / OneUI 6 上「几乎所有界面失去了系统滚动，只有视频详情、评论区正常」）。
 * 原先开关关闭等于「App 什么都不做」，只能吃框架的默认行为：`NestedScrollView` /
 * `ScrollView` / `RecyclerView` 三种容器各自的 `onGenericMotionEvent` 只认 `AXIS_SCROLL`
 * （`RecyclerView.java:3961-3967` 与 `NestedScrollView.java:1337-1344`），且前两者的
 * 命中依赖事件沿焦点链经过它们——页面里没有可获焦控件时事件根本到不了容器，于是
 * 「原版能用、这里转不动」。现在的顺序是：先让框架处理（[handleSystemFirst]），
 * 框架动了就**把事件吃掉**（防止冒泡到页面级兜底再滚一遍 = 「叠加」），框架没动才由 App
 * 按双轴补位。开关开启（[isEnabled]）仍是老的「App 先接管」，灵敏度可调的手感不变。
 *
 * **26.11.09 修反馈 #132（「关闭表冠适配后详情页滑动速率小，得一直转」）**：系统优先模式原先
 * 是「框架一动就整段吞掉」，可框架各容器的换算基准并不相同，而且都小于 App 的
 * [RotaryEventPolicy.pixelDelta] 口径——`NestedScrollView`（详情页）用主题的
 * `listPreferredItemHeight`（Material/AppCompat 默认 48dp，见 androidx 的
 * `getVerticalScrollFactorCompat()`），平台 `ScrollView` 与 `RecyclerView` 用
 * `config_verticalScrollFactor`（64dp），而 App 口径是 64dp × 2。于是同一格表冠，详情页只走
 * 约三分之一的距离、列表页约一半——**只有详情页慢**，与用户描述一致。现在吞之前先补足差额
 * （[RotaryEventPolicy.topUpDelta]）：框架滚了多少就抵扣多少，不够的由 App 补到与「开启表冠
 * 适配」一致的距离。框架本来就给得够的机型（issue #21 那一类）分毫不动，同一次手势也不会
 * 被算两次。
 */
internal object RotaryEncoderSupport {

    /**
     * 灵敏度默认值。
     *
     * 与设置页 `SettingPrefActivity` 里 `input_float` 的 `defaultValue` 必须一致（单一来源）。
     * 1.0 = 系统原始滚动量 ×2（`pixelDelta` 里那个 `* 2`），是「不用调手感就对」的经验值；
     * 用户反馈过的可用值也是 1.0（issue：三星表圈只能在设置页内用）。
     */
    const val DEFAULT_MULTIPLE = "1.0"

    /** 表冠滚动总开关（`SettingsKeys.UI_ROTATORY_ENABLE`，且需要 Android 8.0+）。 */
    fun isEnabled(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                SharedPreferencesUtil.getBoolean(SettingsKeys.UI_ROTATORY_ENABLE, false)

    /**
     * 读取某个灵敏度设置键。
     *
     * 总开关关闭时恒返回 0——各控件原本都是 `rotaryEnabled && scrollMultiple > 0` 双条件，
     * 归并成一个「倍数 ≤ 0 即不生效」的判断。用户显式把灵敏度填 0 也就等于关掉该类控件。
     */
    fun multipleOf(key: String): Float =
        if (isEnabled()) SharedPreferencesUtil.getFloat(key, DEFAULT_MULTIPLE.toFloat()) else 0f

    /**
     * 补位/兜底用的倍率：开关关闭时是 [DEFAULT_MULTIPLE]，开启时是用户填的灵敏度。
     *
     * 与 [multipleOf] 的区别只在开关关闭时——[multipleOf] 返回 0 表示「App 先接管」这条路不走，
     * 而补位这条路关闭时也要走（否则就是 issue #21 的「大部分界面没有表冠滚动」）。
     * 用户显式填 0 仍是「这类页面不响应表冠」的逃生口。
     */
    fun sensitivityOf(key: String): Float =
        if (isEnabled()) SharedPreferencesUtil.getFloat(key, DEFAULT_MULTIPLE.toFloat())
        else DEFAULT_MULTIPLE.toFloat()

    /** 总开关关闭时走「系统优先、App 补位」，开启时走老的「App 先接管」。 */
    fun isSystemFirstMode(): Boolean = !isEnabled()

    /**
     * 处理一次 generic motion 事件（控件级）。
     *
     * 命中表冠滚动则滚动并返回 `true`；否则返回 `false`，由调用方继续走自己的默认分发。
     * **位移为 0 也返回 `false`**——见 [RotaryEventPolicy.shouldConsume] 与类注释第 1 条。
     *
     * @param multiple    灵敏度倍率，≤ 0 直接不处理
     * @param requestFocus 是否在滚动后抢焦点。`RecyclerView`/`ScrollView` 原本会抢，
     *        `NestedScrollView` 原本不抢——这个差异原样保留，避免改变现有手感。
     * @param smoothScrollBy 竖直平滑滚动动作。`View` 本身没有可访问的 `smoothScrollBy`，
     *        只有各具体子类有，所以由调用方传进来（`{ smoothScrollBy(0, it) }`）。
     */
    fun handle(
        view: View,
        event: MotionEvent,
        multiple: Float,
        requestFocus: Boolean,
        smoothScrollBy: (Int) -> Unit
    ): Boolean {
        if (multiple <= 0f) return false
        if (!RotaryEventPolicy.isRotaryEvent(event.action, event.source)) {
            if (RotaryEventPolicy.isScrollEvent(event.action)) {
                probe("控件级：非表冠源，未处理", event, "view=${view.javaClass.simpleName}")
            }
            return false
        }

        val dy = pixelDelta(view, event, multiple)
        if (!RotaryEventPolicy.shouldConsume(dy)) {
            probe("控件级：位移为 0，不吞事件", event, "view=${view.javaClass.simpleName} multiple=$multiple")
            return false
        }
        smoothScrollBy(dy)
        if (requestFocus) view.requestFocus()
        return true
    }

    /**
     * 「系统优先、App 补位」的控件级入口（总开关关闭时走这条，见类注释最后两段）。
     *
     * 顺序是先让框架自己处理（`runFramework` 里就是 `super.onGenericMotionEvent` /
     * `super.dispatchGenericMotionEvent`），再用**滚动位置的变化量**量出框架到底滚了多少：
     *
     * - 框架动了（或框架自己说处理了）→ 按 [RotaryEventPolicy.topUpDelta] 补上差额，再
     *   `return true` 把事件吃掉。吃掉是「不叠加」的关键：`RecyclerView.onGenericMotionEvent`
     *   滚完仍然 `return false`，不吞就会一路冒泡到 DecorView 的页面级兜底，兜底再把同一个
     *   容器滚一遍。补差额而不是「一动不动」是 26.11.09 修反馈 #132 的改动——框架的换算
     *   基准比 App 口径小（详情页用 `listPreferredItemHeight`、列表用 `config_verticalScrollFactor`，
     *   而 App 是后者 × 2），不补的话详情页每格只走三分之一的距离。
     * - 没动 → 按双轴换算整段位移补位（`AXIS_VSCROLL` 的 ROM 就是靠这一步才有反应）。
     *
     * 用**带符号差值**而不是「有没有变」做判定：同一个读数既要回答「动没动」，又要拿来抵扣
     * 补位量，一次读值两用，也就不会出现「前后读了两次偏移、两次之间框架又滚了一下」的偏差。
     *
     * @param multiple 补位倍率（开关关闭时用 [DEFAULT_MULTIPLE]，用户填 0 则不补位）
     * @param scrollOffset 竖直滚动位置的读数，用来量框架滚了多少
     * @param runFramework 交给框架处理，返回框架自己的「是否已处理」
     * @param smoothScrollBy 补位 / 补差额时的平滑滚动动作
     */
    fun handleSystemFirst(
        view: View,
        event: MotionEvent,
        multiple: Float,
        scrollOffset: () -> Int,
        runFramework: () -> Boolean,
        smoothScrollBy: (Int) -> Unit
    ): Boolean {
        if (multiple <= 0f) return runFramework()
        if (!RotaryEventPolicy.isRotaryEvent(event.action, event.source)) return runFramework()

        val before = scrollOffset()
        val frameworkHandled = runFramework()
        val frameworkDelta = scrollOffset() - before

        if (RotaryEventPolicy.shouldConsumeAfterFramework(frameworkHandled, frameworkDelta != 0)) {
            // 框架说自己处理了、偏移却没变：可能是异步滚动（惯性/动画还没落到 scrollY 上），
            // 也可能已经到顶/到底。此时一律不补——补了会和随后到来的框架滚动叠加，
            // 正是 1789a853 要防的「同一次手势被算两次」。
            if (frameworkDelta == 0) return true

            val target = systemFirstTargetDelta(view, event, multiple)
            val topUp = RotaryEventPolicy.topUpDelta(target, frameworkDelta)
            if (topUp == 0) {
                if (systemFirstProbed.compareAndSet(false, true)) {
                    probe(
                        "系统优先：框架已滚够，吞掉事件（本进程首次）", event,
                        "target=$target frameworkDelta=$frameworkDelta view=${view.javaClass.simpleName}"
                    )
                }
                return true
            }
            smoothScrollBy(topUp)
            if (topUpProbed.compareAndSet(false, true)) {
                probe(
                    "系统优先：框架滚得不够，补足差额（本进程首次）", event,
                    "target=$target frameworkDelta=$frameworkDelta topUp=$topUp" +
                            " view=${view.javaClass.simpleName}"
                )
            }
            return true
        }

        val dy = systemFirstTargetDelta(view, event, multiple)
        if (!RotaryEventPolicy.shouldConsume(dy)) {
            probe(
                "系统优先：框架没动、补位位移为 0，不吞事件", event,
                "view=${view.javaClass.simpleName} multiple=$multiple"
            )
            return false
        }
        smoothScrollBy(dy)
        if (successProbed.compareAndSet(false, true)) {
            probe("补位：框架没动，App 接管（本进程首次）", event, "dy=$dy view=${view.javaClass.simpleName}")
        }
        return true
    }

    /**
     * 页面级兜底：挂在 DecorView 的 generic motion 监听器上，谁都没处理时才被调用。
     *
     * 找出本页第一个「可见、且当前确实能上下滚」的竖直滚动容器并滚它。
     * 横向容器（如 `ViewPager2` 内部、横向 `RecyclerView`）的 `canScrollVertically` 恒为
     * false，天然被排除，不会把翻页手势抢成竖滚。
     *
     * @return 是否消费了这次事件
     */
    fun handlePage(root: View, event: MotionEvent): Boolean {
        if (!RotaryEventPolicy.isRotaryEvent(event.action, event.source)) {
            if (RotaryEventPolicy.isScrollEvent(event.action)) {
                probe("兜底：非表冠源，未处理", event, "root=${root.javaClass.simpleName}")
            }
            return false
        }

        val target = findScrollTarget(root)
        if (target == null) {
            probe("兜底：本页找不到可滚容器", event)
            return false
        }
        val multiple = if (target is RecyclerView)
            sensitivityOf(SettingsKeys.UI_ROTATORY_RECYCLER)
        else
            sensitivityOf(SettingsKeys.UI_ROTATORY_SCROLL)
        if (multiple <= 0f) return false

        val dy = pixelDelta(target, event, multiple)
        if (!RotaryEventPolicy.shouldConsume(dy)) {
            probe("兜底：位移为 0，不吞事件", event, "target=${target.javaClass.simpleName} multiple=$multiple")
            return false
        }
        when (target) {
            is RecyclerView -> target.smoothScrollBy(0, dy)
            is NestedScrollView -> target.smoothScrollBy(0, dy)
            is ScrollView -> target.smoothScrollBy(0, dy)
            else -> return false
        }
        if (successProbed.compareAndSet(false, true)) {
            probe("兜底：滚动成功（本进程首次）", event, "dy=$dy target=${target.javaClass.simpleName}")
        }
        return true
    }

    /** 本次事件的滚动量：优先 `AXIS_SCROLL`，为 0 时回落 `AXIS_VSCROLL`。 */
    private fun rawScrollRatio(event: MotionEvent): Float =
        RotaryEventPolicy.rawScrollRatio(
            event.getAxisValue(MotionEvent.AXIS_SCROLL),
            event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        )

    /**
     * 本机 `ViewConfiguration` 的竖直滚动缩放值（`config_verticalScrollFactor`，即 64dp × density）。
     *
     * 这是 **App 口径**的基准。框架各容器自己算表冠滚动量时未必用这个值（`NestedScrollView`
     * 用的是主题的 `listPreferredItemHeight`），所以系统优先模式下的补位要按 App 口径重新算一遍
     * 目标距离，见 [RotaryEventPolicy.systemFirstTargetDelta]。
     */
    private fun scaledVerticalScrollFactorOf(view: View): Float =
        ViewConfigurationCompat.getScaledVerticalScrollFactor(
            ViewConfiguration.get(view.context), view.context
        )

    private fun pixelDelta(view: View, event: MotionEvent, multiple: Float): Int =
        RotaryEventPolicy.pixelDelta(rawScrollRatio(event), scaledVerticalScrollFactorOf(view), multiple)

    /** 系统优先模式下这一格「应该」走的距离（与开启表冠适配 + 灵敏度 1.0 一致）。 */
    private fun systemFirstTargetDelta(view: View, event: MotionEvent, multiple: Float): Int =
        RotaryEventPolicy.systemFirstTargetDelta(
            rawScrollRatio(event), scaledVerticalScrollFactorOf(view), multiple
        )

    // ==================== 诊断 ====================
    //
    // 只在「本该处理却没处理」「本进程第一次真的滚动成功」「本进程第一次补差额」时打日志，
    // 同一进程最多 PROBE_MAX 条。用 W 级：Logu 的内存环形缓冲只记 W/E/F，反馈页勾
    // 「附带应用日志」带的就是它。
    //
    // 补差额那条特意带 target / frameworkDelta / topUp 三个数：反馈 #132 这类「手感偏慢」
    // 的问题没有这三个数就只能靠猜（维护者手上没有表冠设备，density 与各容器的换算基准
    // 都得从日志反推），下次再报慢时一眼就能算出框架给少了多少。

    private const val PROBE_MAX = 6
    private val probeCount = AtomicInteger(0)
    private val successProbed = AtomicBoolean(false)
    private val systemFirstProbed = AtomicBoolean(false)
    private val topUpProbed = AtomicBoolean(false)

    private fun probe(reason: String, event: MotionEvent, extra: String = "") {
        if (probeCount.getAndIncrement() >= PROBE_MAX) return
        val source = event.source
        val text = buildString {
            append(reason)
            append(" action=").append(event.action)
            append(" src=0x").append(source.toString(16))
            append(" rotary=").append(RotaryEventPolicy.isRotaryEvent(event.action, source))
            append(" axisScroll=").append(event.getAxisValue(MotionEvent.AXIS_SCROLL))
            append(" axisVScroll=").append(event.getAxisValue(MotionEvent.AXIS_VSCROLL))
            if (extra.isNotEmpty()) {
                append(' ').append(extra)
            }
        }
        Logu.w("RotaryEncoder", text)
    }

    // ==================== 找目标 ====================

    /**
     * 深度优先找本页第一个「可见且当前确实能上下滚」的竖直滚动容器。
     *
     * 两轮：先要求候选与窗口有实际交集（[isCandidateVisible]），一个都没有时再退回只看
     * `isShown()`（26.11.07 之前的行为）——布局还没完成、尺寸都是 0 的瞬间，严格条件会全灭，
     * 此时按老办法至少不会比原来更差。
     */
    private fun findScrollTarget(root: View): View? {
        val rect = Rect()
        return findScrollTarget(root, rect, requireOnScreen = true)
            ?: findScrollTarget(root, rect, requireOnScreen = false)
    }

    private fun findScrollTarget(view: View, rect: Rect, requireOnScreen: Boolean): View? {
        if (isScrollable(view) && isCandidateVisible(view, rect, requireOnScreen)) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findScrollTarget(view.getChildAt(i), rect, requireOnScreen)?.let { return it }
            }
        }
        return null
    }

    private fun isScrollable(view: View): Boolean =
        (view is RecyclerView || view is NestedScrollView || view is ScrollView) &&
                (view.canScrollVertically(1) || view.canScrollVertically(-1))

    private fun isCandidateVisible(view: View, rect: Rect, requireOnScreen: Boolean): Boolean {
        if (!view.isShown) return false
        if (!requireOnScreen) return true
        // isShown() 只看 VISIBILITY 位与 attach 状态，不看「是否真的落在可视区域内」：
        // ViewPager 的离屏页、被上层盖住的页都仍算 shown。这里再要求与窗口有实际交集。
        if (view.width <= 0 || view.height <= 0) return false
        return view.getGlobalVisibleRect(rect) && rect.height() > 0
    }
}
