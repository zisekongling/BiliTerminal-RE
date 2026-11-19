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