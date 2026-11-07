package com.RobinNotBad.BiliClient.ui.widget

import android.view.InputDevice
import android.view.MotionEvent
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
     */
    fun shouldConsumeAfterFramework(frameworkHandled: Boolean, frameworkMoved: Boolean): Boolean =
        frameworkHandled || frameworkMoved

    /**
     * 「系统优先」路径：框架什么都没滚时，App 是否补位。
     *
     * 框架已经滚过就绝不再补（见 [shouldConsumeAfterFramework]）；换算后没有位移也不补，
     * 与 [shouldConsume] 同一条语义。
     */
    fun shouldFillIn(frameworkMoved: Boolean, dy: Int): Boolean = !frameworkMoved && dy != 0
}
