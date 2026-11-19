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
