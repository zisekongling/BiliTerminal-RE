package com.RobinNotBad.BiliClient.ui.shortvideo

/** 新版短视频覆盖层与面板的纯状态；把时间推进留在 JVM 中验证。 */
class ShortVideoOverlayState {
    var controlsVisible = true
        private set
    var panelVisible = false
        private set
    var playing = false
        private set
    var favourite = false
        private set
    var lastInteractionAt = 0L
        private set

    fun bind(isPlaying: Boolean, isFavourite: Boolean = favourite) {
        playing = isPlaying
        favourite = isFavourite
        controlsVisible = true
        panelVisible = false
        lastInteractionAt = 0L
    }

    fun toggleControls(now: Long): Boolean {
        if (panelVisible) return false
        controlsVisible = !controlsVisible
        lastInteractionAt = now
        return controlsVisible
    }

    fun onPlayingChanged(isPlaying: Boolean, now: Long) {
        playing = isPlaying
        lastInteractionAt = now
        // 暂停必须有稳定、不会突然消失的播放按钮。
        if (!isPlaying) controlsVisible = true
    }

    fun onInteraction(now: Long) {
        lastInteractionAt = now
    }

    fun showPanel(now: Long) {
        panelVisible = true
        lastInteractionAt = now
    }

    fun closePanel(): Boolean {
        val changed = panelVisible
        panelVisible = false
        return changed
    }

    fun setFavourite(value: Boolean) { favourite = value }

    fun shouldAutoHide(now: Long): Boolean =
        controlsVisible && !panelVisible && playing && now - lastInteractionAt >= AUTO_HIDE_MS

    fun hideForIdle(now: Long): Boolean {
        if (!shouldAutoHide(now)) return false
        controlsVisible = false
        lastInteractionAt = now
        return true
    }

    companion object { const val AUTO_HIDE_MS = 3_000L }
}

/**
 * 表冠换条的累积阈值（纯函数，可单测）。
 *
 * 与 `TutorialPagerActivity` 的旋冠翻页同一套口径：累积 `-轴值 × 灵敏度`，
 * 满 [STEP] 切一条并清零。方向与 `RotaryScrollView` 一致：向下滚（轴值为负）= 往后翻。
 *
 * **单次事件最多切一条**：设计稿要求「表冠一格一条」。把表冠报成滚轮的 ROM
 * 一格可能报 ±16（见 `CrownSourcePolicy`），不封顶就会一格跳十几条；
 * 快转的加速交给事件数承担（与 `CrownSourcePolicy.wheelScrollDelta` 的封顶思路一致）。
 */
object ShortVideoRotaryPolicy {

    /** 切一条所需的累积量，与 TutorialPagerActivity.ROTARY_STEP 相同。 */
    const val STEP = 1f

    /**
     * 喂入一次表冠滚动量，返回「新的累积值」与「本次该切几条（-1 / 0 / +1）」。
     *
     * @param accumulated 之前攒下的量
     * @param ratio 本次轴值（`RotaryEventPolicy.rawScrollRatio` 的结果，双轴回落）
     * @param multiple 灵敏度倍率；≤0 是用户显式关闭的逃生口，原样返回不累积
     */
    fun feed(accumulated: Float, ratio: Float, multiple: Float): Pair<Float, Int> {
        if (multiple <= 0f) return accumulated to 0
        val next = accumulated + (-ratio * multiple)
        if (kotlin.math.abs(next) < STEP) return next to 0
        // 触发即清零（不保留余数）：与教程页一致，避免余数把下一格提前触发。
        return 0f to (if (next > 0f) 1 else -1)
    }
}