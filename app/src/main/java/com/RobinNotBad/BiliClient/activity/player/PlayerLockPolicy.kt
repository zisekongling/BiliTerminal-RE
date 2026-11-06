package com.RobinNotBad.BiliClient.activity.player

/**
 * 播放页触摸锁定（防误触）的状态机。
 *
 * 手表上播放视频时，手背/衣袖/手腕转动很容易误触屏幕：暂停、调进度、退出播放页。
 * 锁定后播放器不响应任何播放操作输入，必须点浮现出来的锁定按钮才能解锁。
 *
 * 三个状态：
 * - [Phase.UNLOCKED]：正常播放，控制栏与手势都可用，锁定按钮随控制栏显示（闭锁图标，点它=锁定）；
 * - [Phase.LOCKED_HIDDEN]：已锁定，只有画面，锁定按钮隐藏；
 * - [Phase.LOCKED_ICON_VISIBLE]：已锁定，锁定按钮浮现中（开锁图标，点它=解锁）。
 *
 * 这里只放状态转移和判据，不碰 View/Handler——计时与显隐由 PlayerActivity 按
 * [ICON_VISIBLE_MS] / [EXIT_HINT_MS] 落地，这样才能用普通 JVM 单测覆盖（见 PlayerLockPolicyTest）。
 */
object PlayerLockPolicy {

    /** 锁定按钮浮现后，无操作多久自动隐去。 */
    const val ICON_VISIBLE_MS = 3000L

    /** 锁定期间尝试退出（返回键/右滑/finish）被拦下时，按钮浮现多久作为反馈。 */
    const val EXIT_HINT_MS = 1500L

    enum class Phase {
        UNLOCKED,
        LOCKED_HIDDEN,
        LOCKED_ICON_VISIBLE
    }

    /** 是否处于锁定（含按钮浮现中）。 */
    fun isLocked(phase: Phase): Boolean = phase != Phase.UNLOCKED

    /**
     * 锁定按钮被点击：未锁定时进入锁定；按钮浮现中点击则解锁。
     * 隐藏状态下按钮不可点（`View.GONE`），所以这里把 [Phase.LOCKED_HIDDEN] 也当解锁处理，
     * 避免出现「点了没反应」的死状态。
     */
    fun phaseAfterLockButtonClick(phase: Phase): Phase = when (phase) {
        Phase.UNLOCKED -> Phase.LOCKED_HIDDEN
        Phase.LOCKED_HIDDEN, Phase.LOCKED_ICON_VISIBLE -> Phase.UNLOCKED
    }

    /** 锁定状态下点击画面：浮现锁定按钮（计时由调用方重置）。 */
    fun phaseAfterScreenTap(phase: Phase): Phase = when (phase) {
        Phase.LOCKED_HIDDEN -> Phase.LOCKED_ICON_VISIBLE
        Phase.UNLOCKED, Phase.LOCKED_ICON_VISIBLE -> phase
    }

    /** 浮现计时到点：按钮自动隐去，只剩画面。 */
    fun phaseAfterTimeout(phase: Phase): Phase = when (phase) {
        Phase.LOCKED_ICON_VISIBLE -> Phase.LOCKED_HIDDEN
        Phase.UNLOCKED, Phase.LOCKED_HIDDEN -> phase
    }

    /**
     * 锁定期间尝试退出（返回键 / 手表右滑 / `finish()`）：拦住，并让按钮浮现作为反馈。
     * 按钮已经浮现时保持原状态，由调用方重置计时。
     */
    fun phaseAfterExitAttempt(phase: Phase): Phase = when (phase) {
        Phase.LOCKED_HIDDEN -> Phase.LOCKED_ICON_VISIBLE
        Phase.UNLOCKED, Phase.LOCKED_ICON_VISIBLE -> phase
    }

    /** 锁定期间是否屏蔽播放器输入（手势、按键、按钮）。 */
    fun shouldBlockPlaybackInput(phase: Phase): Boolean = isLocked(phase)

    /** 锁定期间是否拦截离开播放页（返回键、右滑、`finish()`）。 */
    fun shouldSwallowExit(phase: Phase): Boolean = isLocked(phase)
}
