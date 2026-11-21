package com.RobinNotBad.BiliClient.ui.shortvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortVideoOverlayStateTest {
    @Test
    fun singleTapTogglesControlsButPanelOwnsTheScreen() {
        val state = ShortVideoOverlayState()
        state.onPlayingChanged(true, 100)
        assertFalse(state.toggleControls(200))
        assertTrue(state.toggleControls(300))
        state.showPanel(400)
        assertTrue(state.toggleControls(500).not())
        assertTrue(state.controlsVisible)
    }

    @Test
    fun playingHidesAfterThreeSecondsAndInteractionRestartsClock() {
        val state = ShortVideoOverlayState()
        state.onPlayingChanged(true, 1_000)
        assertFalse(state.hideForIdle(3_999))
        assertTrue(state.hideForIdle(4_000))
        assertFalse(state.controlsVisible)
        state.toggleControls(5_000)
        assertFalse(state.hideForIdle(7_999))
        assertTrue(state.hideForIdle(8_000))
    }

    @Test
    fun pausedPlaybackAndOpenPanelsNeverAutoHide() {
        val state = ShortVideoOverlayState()
        state.onPlayingChanged(false, 1_000)
        assertTrue(state.controlsVisible)
        assertFalse(state.hideForIdle(20_000))
        state.onPlayingChanged(true, 21_000)
        state.showPanel(21_100)
        assertFalse(state.hideForIdle(50_000))
        assertTrue(state.closePanel())
        assertTrue(state.hideForIdle(50_000))
    }

    @Test
    fun bindingNewItemRestoresVisibleControlsAndFavourite() {
        val state = ShortVideoOverlayState()
        state.setFavourite(true)
        state.onPlayingChanged(true, 1_000)
        state.hideForIdle(4_000)
        state.bind(false, false)
        assertTrue(state.controlsVisible)
        assertFalse(state.playing)
        assertFalse(state.favourite)
    }
}

/** 表冠换条的累积阈值：一格一条、单次封顶一条、关闭逃生口、余数清零。 */
class ShortVideoRotaryPolicyTest {

    @Test
    fun disabledMultipleNeverAccumulates() {
        // multiple ≤ 0 是用户显式关闭：原样返回，不累积也不切。
        assertEquals(0.3f to 0, ShortVideoRotaryPolicy.feed(0.3f, -1f, 0f))
        assertEquals(0.3f to 0, ShortVideoRotaryPolicy.feed(0.3f, -1f, -2f))
    }

    @Test
    fun oneNotchSwitchesOneVideoForward() {
        // 向下滚（轴值为负）= 往后翻，与 RotaryScrollView 同向。
        val (acc, steps) = ShortVideoRotaryPolicy.feed(0f, -1f, 1f)
        assertEquals(0f, acc, 0.0001f)
        assertEquals(1, steps)
    }

    @Test
    fun reverseDirectionSwitchesBack() {
        val (acc, steps) = ShortVideoRotaryPolicy.feed(0f, 1f, 1f)
        assertEquals(0f, acc, 0.0001f)
        assertEquals(-1, steps)
    }

    @Test
    fun fastSpinCapsAtOnePerEvent() {
        // 滚轮 ROM 一格可能报 ±16：单次仍只切一条，加速交给事件数。
        val (acc, steps) = ShortVideoRotaryPolicy.feed(0f, -16f, 8f)
        assertEquals(0f, acc, 0.0001f)
        assertEquals(1, steps)
        var accumulated = 0f
        var total = 0
        repeat(5) {
            val (next, step) = ShortVideoRotaryPolicy.feed(accumulated, -16f, 1f)
            accumulated = next
            total += step
        }
        assertEquals(5, total)
    }

    @Test
    fun remainderAccumulatesThenClearsOnTrigger() {
        val (a1, s1) = ShortVideoRotaryPolicy.feed(0f, -0.4f, 1f)
        assertEquals(0.4f, a1, 0.0001f)
        assertEquals(0, s1)
        val (a2, s2) = ShortVideoRotaryPolicy.feed(a1, -0.4f, 1f)
        assertEquals(0.8f, a2, 0.0001f)
        assertEquals(0, s2)
        // 第三格凑满 1.2：切一条并清零，不保留 0.2 余数把下一格提前触发。
        val (a3, s3) = ShortVideoRotaryPolicy.feed(a2, -0.4f, 1f)
        assertEquals(0f, a3, 0.0001f)
        assertEquals(1, s3)
        val (_, s4) = ShortVideoRotaryPolicy.feed(a3, -0.4f, 1f)
        assertEquals(0, s4)
    }

    @Test
    fun oppositeDirectionCancelsAccumulation() {
        val (a1, _) = ShortVideoRotaryPolicy.feed(0f, -0.6f, 1f)
        val (a2, s2) = ShortVideoRotaryPolicy.feed(a1, 0.6f, 1f)
        assertEquals(0f, a2, 0.0001f)
        assertEquals(0, s2)
    }

    @Test
    fun sensitivityScalesTheThreshold() {
        // 灵敏度 0.5：两格才凑满一条。
        val (a1, s1) = ShortVideoRotaryPolicy.feed(0f, -1f, 0.5f)
        assertEquals(0.5f, a1, 0.0001f)
        assertEquals(0, s1)
        val (_, s2) = ShortVideoRotaryPolicy.feed(a1, -1f, 0.5f)
        assertEquals(1, s2)
    }
}
