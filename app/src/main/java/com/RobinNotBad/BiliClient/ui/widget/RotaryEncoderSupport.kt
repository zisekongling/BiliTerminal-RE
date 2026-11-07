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
     * 页面级兜底：挂在 DecorView 的 generic motion 监听器上，谁都没处理时才被调用。
     *
     * 找出本页第一个「可见、且当前确实能上下滚」的竖直滚动容器并滚它。
     * 横向容器（如 `ViewPager2` 内部、横向 `RecyclerView`）的 `canScrollVertically` 恒为
     * false，天然被排除，不会把翻页手势抢成竖滚。
     *
     * @return 是否消费了这次事件
     */
    fun handlePage(root: View, event: MotionEvent): Boolean {
        if (!isEnabled()) return false
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
            multipleOf(SettingsKeys.UI_ROTATORY_RECYCLER)
        else
            multipleOf(SettingsKeys.UI_ROTATORY_SCROLL)
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

    private fun pixelDelta(view: View, event: MotionEvent, multiple: Float): Int =
        RotaryEventPolicy.pixelDelta(
            RotaryEventPolicy.rawScrollRatio(
                event.getAxisValue(MotionEvent.AXIS_SCROLL),
                event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            ),
            ViewConfigurationCompat.getScaledVerticalScrollFactor(
                ViewConfiguration.get(view.context), view.context
            ),
            multiple
        )

    // ==================== 诊断 ====================
    //
    // 只在「本该处理却没处理」和「本进程第一次真的滚动成功」时打日志，同一进程最多 PROBE_MAX 条。
    // 用 W 级：Logu 的内存环形缓冲只记 W/E/F，反馈页勾「附带应用日志」带的就是它。

    private const val PROBE_MAX = 6
    private val probeCount = AtomicInteger(0)
    private val successProbed = AtomicBoolean(false)

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
