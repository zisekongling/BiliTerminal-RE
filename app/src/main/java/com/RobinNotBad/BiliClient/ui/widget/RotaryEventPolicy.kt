package com.RobinNotBad.BiliClient.ui.widget

import android.view.InputDevice
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 表冠（旋转编码器）事件的**纯判定**：不碰 `View`、不读设置，因而能进纯 JVM 单测。
 *
 * 为什么要单独拆出来：`RotaryEncoderSupport` 的三个入口（控件级监听器、dispatch 覆写、
 * DecorView 兜底）都建立在同一组判断上，而这组判断里有几个「差一位就静默失效」的坑
 * （见 26.11.07 修反馈 #103/#107）。放在纯函数里，改动前能先用单测把语义钉住。
 *
 * 术语：**ratio** 是系统给的滚动量（无量纲），**dy** 是换算后的像素位移。
 */
object RotaryEventPolicy {

    /**
     * 是不是一次表冠事件。
     *
     * 框架侧事实（Android 11 `View.dispatchGenericMotionEvent`）：只有
     * `SOURCE_CLASS_POINTER` 类事件才按坐标命中控件；表冠的
     * `SOURCE_ROTARY_ENCODER == 0x00400000 | SOURCE_CLASS_NONE` **不属于 pointer 类**，
     * 因此它只沿焦点链下钻、没人处理才落到 DecorView 的监听器——本工程的控件级入口与
     * 页面级兜底正是这么分工的。
     *
     * 反过来说：如果某个 ROM 把表冠报成鼠标/滚轮（pointer 类），`source and 0x400000` 为 0，
     * 这里判 false，本工程就完全不响应。这是**有意**的保守：放宽会改变所有设备的滚轮/触控板行为，
     * 需要设备侧证据后再单独评估（`RotaryEncoderSupport` 的 W 级诊断日志就是为拿这个证据加的）。
     */
    fun isRotaryEvent(action: Int, source: Int): Boolean =
        action == MotionEvent.ACTION_SCROLL &&
                (source and InputDevice.SOURCE_ROTARY_ENCODER) == InputDevice.SOURCE_ROTARY_ENCODER

    /** 是否为「滚一下」类事件（不看来源）。只给诊断用，不参与处理判定。 */
    fun isScrollEvent(action: Int): Boolean = action == MotionEvent.ACTION_SCROLL

    /**
     * 取本次事件的滚动量：**优先 `AXIS_SCROLL`，为 0 时回落 `AXIS_VSCROLL`**。
     *
     * 26.11.07 之前只读 `AXIS_SCROLL`。部分 ROM（含手表上的滚轮实现）把量放在
     * `AXIS_VSCROLL` 上，于是 ratio 恒为 0 → dy 恒为 0 → 页面「转表冠没反应」。
     * 两个轴都是 0 时仍返回 0（那才是真的没有位移），由 [shouldConsume] 决定不消费。
     */
    fun rawScrollRatio(axisScroll: Float, axisVScroll: Float): Float =
        if (axisScroll != 0f) axisScroll else axisVScroll

    /**
     * 换算成像素位移：`-ratio × scaledVerticalScrollFactor × 2 × multiple`，四舍五入。
     *
     * `× 2` 是沿用 26.10.17 定下的手感基准（灵敏度 1.0 = 原始滚动量 ×2），不要单独改；
     * 负号是因为 `AXIS_SCROLL` 正方向与「内容向上滚」相反。
     */
    fun pixelDelta(
        ratio: Float,
        scaledVerticalScrollFactor: Float,
        multiple: Float
    ): Int = (-ratio * scaledVerticalScrollFactor * 2 * multiple).roundToInt()

    /**
     * 是否应该把这次事件**吃掉**（返回 true 给框架）。
     *
     * 位移为 0 时必须返回 false：26.11.07 之前控件级 `handle()` 无论算出多少都 return true，
     * 事件被吞、画面不动、父级和 DecorView 兜底再也接不到——现象与「表冠不适配」完全一致。
     * 页面级兜底本来就是 `dy == 0 → false`，这里把两者对齐。
     */
    fun shouldConsume(dy: Int): Boolean = dy != 0

    /**
     * 「系统优先」路径：框架处理完之后，App 是否必须把事件吃掉。
     *
     * 两个条件任一成立就得吞：
     * 1. `frameworkHandled`——框架自己说处理了（`NestedScrollView`/`ScrollView` 滚完返回 true）；
     * 2. `frameworkMoved`——框架滚了却返回 false（`RecyclerView.onGenericMotionEvent`
     *    在 `RecyclerView.java:3985` 恒 return false）。
     *
     * 为什么必须吞：不吞的话事件会一路冒泡到 DecorView 的页面级兜底，兜底再把同一个容器
     * 滚一遍——这就是 issue #21 报的「软件和系统滚动效果叠加」。
     *
     * 注意「吞」不等于「什么都不做」：吞之前要按 [topUpDelta] 把框架滚不足的差额补上，
     * 否则会把这个小基准当成用户该得的全部距离（反馈 #132）。
     */
    fun shouldConsumeAfterFramework(frameworkHandled: Boolean, frameworkMoved: Boolean): Boolean =
        frameworkHandled || frameworkMoved

    /**
     * 「系统优先」模式补位口径的缩放系数，见 [systemFirstTargetDelta]。
     *
     * 1.0 = 与「开启表冠适配 + 灵敏度 1.0」的距离完全一致，也是默认值。真机上觉得还不够快
     * 就只改这一个数——注意系统优先模式下的灵敏度**不读**设置页那两个倍数（[RotaryEncoderSupport.sensitivityOf]
     * 在总开关关闭时恒取默认值），所以这里是为数不多的整体调节点。
     */
    const val SYSTEM_FIRST_TARGET_SCALE = 1.0f

    /**
     * 「系统优先」模式下每一格表冠的**目标**位移（像素），口径与「开启表冠适配 + 灵敏度 1.0」
     * 时的 [pixelDelta] 完全一致。
     *
     * 为什么要有这个独立入口：框架自己处理表冠时，各容器用的换算基准根本不是一个量——
     * - `androidx.core.widget.NestedScrollView`（详情页）的 `getVerticalScrollFactorCompat()`
     *   取的是**主题属性 `android:listPreferredItemHeight`**（Material/AppCompat 默认 48dp）；
     * - `android.widget.ScrollView` 与 `RecyclerView` 取 `ViewConfiguration.getScaledVerticalScrollFactor()`
     *   （AOSP `config_verticalScrollFactor` = 64dp）；
     * - App 自己的 [pixelDelta] 又是后者的 **2 倍**。
     *
     * 于是「框架滚了一点就整段吞掉」等于让用户吃下最小的那个基准：同一格表冠，详情页只走
     * 48dp 的量、列表页走 64dp、而开启适配时走 128dp——这正是反馈 #132 里「关闭表冠适配后
     * 详情页转半天不动，别的页面还好」的来源（详情页是唯一吃 48dp 基准的容器）。
     */
    fun systemFirstTargetDelta(
        ratio: Float,
        scaledVerticalScrollFactor: Float,
        multiple: Float
    ): Int =
        (pixelDelta(ratio, scaledVerticalScrollFactor, multiple) * SYSTEM_FIRST_TARGET_SCALE)
            .roundToInt()

    /**
     * 「系统优先」路径：框架这一格已经滚了 [frameworkDelta] 像素之后，App 还要补多少像素。
     *
     * 语义是**补足差额，不是叠加**：
     * - 框架一点没滚（`frameworkDelta == 0`）→ 整段 [targetDy] 由 App 给（1789a853 的补位）；
     * - 框架滚了但不够 → 只补 `|targetDy| - |frameworkDelta|` 的差额；
     * - 框架滚够了 → 返回 0，一丝不加；
     * - 框架方向与目标相反（用户先反着转了一下就被框架吃掉）→ 按整段 [targetDy] 补，
     *   否则这次手势会被「抵消」掉一半，手感上像是丢了一格。
     *
     * 为什么是差额而不是「框架滚过就绝不补」：框架的换算基准比 App 小（见
     * [systemFirstTargetDelta] 的注释），旧逻辑把这个小基准当成用户该得的全部距离，
     * 详情页因此只有开启适配时 37% 的速度。改成补差额后：
     * 1. 框架给得够的机型（issue #21 那类，系统本身就滚得比 App 多）分毫不动，不会叠加；
     * 2. 框架给得少的机型补到 App 口径，与「页面级兜底」「App 先接管」两条路径的距离一致；
     * 3. 因为如实减掉了框架已滚的那部分，**同一次手势不会被算两次**。
     *
     * 也正因为是「如实抵扣」，这里不需要「框架滚了多少才算滚过」的阈值——任意小的框架位移
     * 都会被抵扣掉，剩下的由 App 补齐，不必去猜各 ROM 的基准值。
     */
    fun topUpDelta(targetDy: Int, frameworkDelta: Int): Int {
        if (targetDy == 0) return 0
        if (frameworkDelta == 0) return targetDy
        if ((frameworkDelta > 0) != (targetDy > 0)) return targetDy
        val gap = abs(targetDy) - abs(frameworkDelta)
        if (gap <= 0) return 0
        return if (targetDy > 0) gap else -gap
    }
}
