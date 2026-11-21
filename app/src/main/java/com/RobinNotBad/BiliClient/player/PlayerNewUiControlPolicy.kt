package com.RobinNotBad.BiliClient.player

/**
 * 新版播放器控制层「该不该自动隐藏」的纯判据。
 *
 * 设计稿 NOTES.paused：暂停时控件常驻，直到继续播放。但常驻的只是**自动**隐藏——
 * 用户单击收起是主动行为，暂停时也照收，否则暂停后控件就再也关不掉了。
 * 老播放器不走这里（判据只被 [PlayerNewUiController] 调用），行为逐字节不变。
 */
object PlayerNewUiControlPolicy {

    /**
     * @param playing 播放器当前是否在播放（Activity 的 isPlaying）
     * @param manual 这次收起是不是用户主动触发的（单击 / 手势），而不是 4 秒定时器
     */
    fun autoHideAllowed(playing: Boolean, manual: Boolean): Boolean = manual || playing
}