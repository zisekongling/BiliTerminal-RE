package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.activity.player.PlayerLockPolicy
import com.RobinNotBad.BiliClient.activity.player.PlayerLockPolicy.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 播放页触摸锁定状态机的纯逻辑回归（26.10.20 新增）。
 *
 * 注意：这里只锁状态转移与判据，不覆盖真实触摸链路——遮罩层级、返回键/右滑拦截、
 * 计时器行为仍需真机验证（见 docs/superpowers/specs/2026-10-20-player-touch-lock.md）。
 */
class PlayerLockPolicyTest {

    @Test
    fun initiallyUnlocked_notLocked() {
        assertFalse(PlayerLockPolicy.isLocked(Phase.UNLOCKED))
        assertFalse(PlayerLockPolicy.shouldBlockPlaybackInput(Phase.UNLOCKED))
        assertFalse(PlayerLockPolicy.shouldSwallowExit(Phase.UNLOCKED))
    }

    @Test
    fun lockButton_entersLockedHidden() {
        assertEquals(Phase.LOCKED_HIDDEN, PlayerLockPolicy.phaseAfterLockButtonClick(Phase.UNLOCKED))
    }

    @Test
    fun lockedStates_areLockedAndBlockEverything() {
        for (phase in listOf(Phase.LOCKED_HIDDEN, Phase.LOCKED_ICON_VISIBLE)) {
            assertTrue(PlayerLockPolicy.isLocked(phase))
            assertTrue(PlayerLockPolicy.shouldBlockPlaybackInput(phase))
            assertTrue(PlayerLockPolicy.shouldSwallowExit(phase))
        }
    }

    @Test
    fun lockButton_whileIconVisible_unlocks() {
        assertEquals(Phase.UNLOCKED, PlayerLockPolicy.phaseAfterLockButtonClick(Phase.LOCKED_ICON_VISIBLE))
    }

    @Test
    fun lockButton_whileHidden_unlocks_fallback() {
        // 正常路径下隐藏态按钮不可点；万一被点到了也必须能解开，不能卡在锁定里
        assertEquals(Phase.UNLOCKED, PlayerLockPolicy.phaseAfterLockButtonClick(Phase.LOCKED_HIDDEN))
    }

    @Test
    fun screenTap_whenLockedHidden_showsIcon() {
        assertEquals(Phase.LOCKED_ICON_VISIBLE, PlayerLockPolicy.phaseAfterScreenTap(Phase.LOCKED_HIDDEN))
    }

    @Test
    fun screenTap_keepsIconVisible_soCallerCanResetTimer() {
        assertEquals(Phase.LOCKED_ICON_VISIBLE, PlayerLockPolicy.phaseAfterScreenTap(Phase.LOCKED_ICON_VISIBLE))
    }

    @Test
    fun screenTap_whenUnlocked_doesNothing() {
        assertEquals(Phase.UNLOCKED, PlayerLockPolicy.phaseAfterScreenTap(Phase.UNLOCKED))
    }

    @Test
    fun timeout_hidesIcon() {
        assertEquals(Phase.LOCKED_HIDDEN, PlayerLockPolicy.phaseAfterTimeout(Phase.LOCKED_ICON_VISIBLE))
    }

    @Test
    fun timeout_onlyAffectsVisibleIcon() {
        assertEquals(Phase.UNLOCKED, PlayerLockPolicy.phaseAfterTimeout(Phase.UNLOCKED))
        assertEquals(Phase.LOCKED_HIDDEN, PlayerLockPolicy.phaseAfterTimeout(Phase.LOCKED_HIDDEN))
    }

    @Test
    fun exitAttempt_whenLockedHidden_showsIcon() {
        assertEquals(Phase.LOCKED_ICON_VISIBLE, PlayerLockPolicy.phaseAfterExitAttempt(Phase.LOCKED_HIDDEN))
    }

    @Test
    fun exitAttempt_keepsVisibleIcon_andUnlockedStaysUnlocked() {
        assertEquals(Phase.LOCKED_ICON_VISIBLE, PlayerLockPolicy.phaseAfterExitAttempt(Phase.LOCKED_ICON_VISIBLE))
        assertEquals(Phase.UNLOCKED, PlayerLockPolicy.phaseAfterExitAttempt(Phase.UNLOCKED))
    }

    @Test
    fun timers_arePositive() {
        assertTrue(PlayerLockPolicy.ICON_VISIBLE_MS > 0)
        assertTrue(PlayerLockPolicy.EXIT_HINT_MS > 0)
        // 返回被拦的反馈要比常规浮现更短，否则会挡住用户下一次点按钮
        assertTrue(PlayerLockPolicy.EXIT_HINT_MS < PlayerLockPolicy.ICON_VISIBLE_MS)
    }
}
