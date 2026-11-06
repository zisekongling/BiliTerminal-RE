package com.RobinNotBad.BiliClient.player

/**
 * 播放器音频焦点的纯判据。
 *
 * 为什么需要音频焦点：手表上不放焦点就会被别的音频应用「踩」——来电、导航播报、系统提示音
 * 都要求播放器主动让路；反过来，主动申请焦点后系统才会把本应用当成「正在出声」的那一个。
 *
 * 这里只放判据，不碰 `AudioManager`（数值常量直接写死在下面，与系统值一一对应），
 * 这样来电/导航这类只能在真机复现的分支也能在 JVM 单测里钉死。
 * 真正的 request/abandon 在 [com.RobinNotBad.BiliClient.activity.player.PlayerActivity]。
 *
 * 与后台播放的关系（重要）：开了「后台/熄屏继续播放」时，`onStop` **不能**放弃焦点——
 * 焦点一放，别的应用就能起播，本应用的播放会退化成「非焦点音频」，真实表现是两个应用
 * 同时出声。判据见 [shouldAbandonOnStop]。
 */
object AudioFocusPolicy {

    /** 与 `AudioManager.AUDIOFOCUS_GAIN` 一致。 */
    const val GAIN = 1

    /** 与 `AudioManager.AUDIOFOCUS_LOSS` 一致。 */
    const val LOSS = -1

    /** 与 `AudioManager.AUDIOFOCUS_LOSS_TRANSIENT` 一致。 */
    const val LOSS_TRANSIENT = -2

    /** 与 `AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` 一致。 */
    const val LOSS_TRANSIENT_CAN_DUCK = -3

    /** 焦点变化后播放器要执行的动作。 */
    enum class Action {
        /** 什么都不做。 */
        NONE,

        /** 暂停播放，且拿回焦点后**不自动续播**（交给用户决定）。 */
        PAUSE
    }

    /**
     * 焦点变化 → 播放器动作。
     *
     * - `LOSS`（别的应用长期占用，例如打开音乐 App）：暂停；
     * - `LOSS_TRANSIENT`（来电、导航播报等短暂占用）：同样暂停，且拿回焦点后不自动续播——
     *   用户可能已经离开播放页，凭空出声比「需要再点一下播放」更打扰；
     * - `LOSS_TRANSIENT_CAN_DUCK`：**不 duck**。本项目的音量是 ijk 软件增益，
     *   临时压低再恢复需要另存一份用户增益、并在 GAIN 时还原，容易和用户手调增益打架；
     *   手表上提示音很短，保持原音量播完更稳。
     */
    @JvmStatic
    fun actionFor(change: Int): Action = when (change) {
        LOSS, LOSS_TRANSIENT -> Action.PAUSE
        else -> Action.NONE
    }

    /** 是否应当因焦点变化暂停。 */
    @JvmStatic
    fun shouldPause(change: Int): Boolean = actionFor(change) == Action.PAUSE

    /**
     * `onStop` 时是否要放弃焦点。
     *
     * 只有「开了后台播放 + 确实在播 + 不是正在退出」这一种情况要保留焦点，
     * 其余（未开后台播放 / 已暂停 / 正在 finish）都应放弃，别占着焦点不让别人播。
     */
    @JvmStatic
    fun shouldAbandonOnStop(
        backgroundPlaybackEnabled: Boolean,
        isPlaying: Boolean,
        isFinishing: Boolean
    ): Boolean = !(backgroundPlaybackEnabled && isPlaying && !isFinishing)
}
