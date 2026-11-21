package com.RobinNotBad.BiliClient.activity.player

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 画面是否仍处于缩放状态。
 *
 * 只认 `layout_video.scaleX/scaleY` 的实际比例：手势监听器里的 `scaling`、PlayerActivity 里的
 * `gesture_scaled` 都只表示「这一轮触摸里捏合过」，ACTION_UP 一到就复位，不能代表「画面现在仍被放大」。
 * 双击复位与左右滑动调进度都读这里，同一件事不要出现第二套判据。
 *
 * 26.10.20 修「缩放后左右滑动仍在调进度」：原判据读 `gesture_scaled`，捏合放大松手后标志已复位，
 * 下一次单指横滑又被当成调进度消费掉。
 */
object PlayerZoomPolicy {
    private const val SCALE_EPSILON = 0.001f

    /** 缩放下限：1 倍就是原始大小，再小没有意义。 */
    const val MIN_SCALE = 1f

    /** 缩放上限。与 ViewScaleGestureListener 里原先写死的 5f 是同一个数，收在这里只留一处。 */
    const val MAX_SCALE = 5f

    fun isZoomed(scaleX: Float, scaleY: Float): Boolean =
        abs(scaleX - 1f) > SCALE_EPSILON || abs(scaleY - 1f) > SCALE_EPSILON

    /** 把缩放比例夹进 [MIN_SCALE]..[MAX_SCALE]。 */
    fun clampScale(scale: Float): Float = max(MIN_SCALE, min(MAX_SCALE, scale))

    /**
     * 画面放大后中心点相对原始位置的可移动半径（像素）。
     *
     * 放大 s 倍时画面宽 w*s，比屏幕多出 w*(s-1)，居中放置后中心点左右各能挪一半——再往外挪
     * 就会露出黑边。缩回 1 倍时半径是 0，也就是必须回到原位。
     */
    fun offsetLimit(videoSize: Int, scale: Float): Float =
        0.5f * videoSize * (max(MIN_SCALE, scale) - 1f)

    /** 把 [value] 夹进「以 [center] 为中心、半径 [limit]」的区间。 */
    fun clampOffset(value: Float, center: Float, limit: Float): Float =
        max(center - limit, min(center + limit, value))
}

/**
 * 单指横向滑动是否应被「左右滑动控制进度」消费。
 *
 * [scaleX]/[scaleY] 传画面的**实际**缩放比例，不是手势过程的临时标志：已缩放时不消费，
 * 把单指拖动留给查看画面。这与设置项「左右滑动控制进度」的描述
 * 「在视频区域左右滑动快进/快退（未缩放视频、未长按加速时生效）」一致。
 *
 * [longPressBoost] 传「长按加速是否正被按住」：按住期间横滑不消费，松手恢复。
 * 26.10.30 修的正是「长按加速时横滑仍会改进度」——长按是个按住不放的瞬时动作，
 * 手指稍微横移一点就触发进度跳变，松手时还会把进度 seek 到那里。
 * **26.11.02 这条判据被 §3.90 的整体回退砍掉过，本次原样补回**，别再当成没做过。
 */
object PlayerSwipeSeekPolicy {
    fun isSeekAllowed(
        swipeSeekEnabled: Boolean,
        prepared: Boolean,
        isLive: Boolean,
        playerReady: Boolean,
        scaleX: Float,
        scaleY: Float,
        longPressBoost: Boolean
    ): Boolean =
        swipeSeekEnabled && prepared && !isLive && playerReady && !longPressBoost &&
            !PlayerZoomPolicy.isZoomed(scaleX, scaleY)
}
