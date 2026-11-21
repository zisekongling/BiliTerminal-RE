package com.RobinNotBad.BiliClient.ui.shortvideo

import kotlin.math.abs
import kotlin.math.max

/**
 * 新版短视频「单击画面」的落点路由（纯函数，可单测）。
 *
 * 设计稿口径（design/short-video-redesign.html 手势表与 pause 状态注释）：
 * 单击画面 = 进出沉浸（只改显示、不改播放状态）；中心键仅暂停时出现，
 * 点中心键 = 继续播。两者在圆屏中心重叠，优先级定为：
 * **暂停且落在中心键热区内 = 播放/暂停，其余一切落点 = 切控件层**。
 * 播放中中心键不显示，中心落点自然回到「切控件层」，不存在歧义。
 */
object ShortVideoTapPolicy {

    enum class Action { TOGGLE_CONTROLS, TOGGLE_PLAY_PAUSE, IGNORE }

    /**
     * @param centerKeyVisible 中心键当前是否可见（设计稿：仅暂停）
     * @param hotHalf 中心键热区半边长（px），由 [ShortVideoLayoutSpec.playTouchSize] 推出
     * @param overlayOpen 面板 / 「更多」弹层开着：整屏归它们，页面手势不接管
     */
    fun route(
        x: Float,
        y: Float,
        centerKeyVisible: Boolean,
        centerX: Float,
        centerY: Float,
        hotHalf: Float,
        playing: Boolean,
        overlayOpen: Boolean,
    ): Action {
        if (overlayOpen) return Action.IGNORE
        if (!playing && centerKeyVisible && hotHalf > 0f &&
            abs(x - centerX) <= hotHalf && abs(y - centerY) <= hotHalf
        ) {
            return Action.TOGGLE_PLAY_PAUSE
        }
        return Action.TOGGLE_CONTROLS
    }

    /** 热区边长：视觉直径与 24dp 最小热区取大（手表小屏缩放后也不许低于 24dp）。 */
    fun touchSize(playSizeDp: Float): Float = max(playSizeDp, MIN_TOUCH_DP)

    const val MIN_TOUCH_DP = 24f
}