package com.RobinNotBad.BiliClient.activity.player

import kotlin.math.abs

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

    fun isZoomed(scaleX: Float, scaleY: Float): Boolean =
        abs(scaleX - 1f) > SCALE_EPSILON || abs(scaleY - 1f) > SCALE_EPSILON
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
