package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.activity.player.PlayerSwipeSeekPolicy
import com.RobinNotBad.BiliClient.activity.player.PlayerZoomPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 26.10.20「缩放后左右滑动仍在调进度」的纯判据回归。
 *
 * 注意：这里只锁判据本身，不覆盖真实触摸链路——手势分发、ACTION_UP 的顺序
 * 仍需真机验证（见 docs/review/fix-progress.md §3.32）。
 */
class PlayerGesturePoliciesTest {

    private fun seekAllowed(
        enabled: Boolean = true,
        prepared: Boolean = true,
        live: Boolean = false,
        playerReady: Boolean = true,
        scaleX: Float = 1f,
        scaleY: Float = scaleX
    ) = PlayerSwipeSeekPolicy.isSeekAllowed(enabled, prepared, live, playerReady, scaleX, scaleY)

    @Test
    fun normalScale_isNotZoomed() {
        assertFalse(PlayerZoomPolicy.isZoomed(1f, 1f))
    }

    @Test
    fun zoomedBothAxes_isZoomed() {
        assertTrue(PlayerZoomPolicy.isZoomed(2f, 2f))
        assertTrue(PlayerZoomPolicy.isZoomed(5f, 5f))
    }

    @Test
    fun eitherAxisZoomed_isZoomed() {
        assertTrue(PlayerZoomPolicy.isZoomed(2f, 1f))
        assertTrue(PlayerZoomPolicy.isZoomed(1f, 2f))
    }

    @Test
    fun floatNoise_doesNotCountAsZoom() {
        assertFalse(PlayerZoomPolicy.isZoomed(1.0005f, 0.9995f))
        assertFalse(PlayerZoomPolicy.isZoomed(0.999f, 1f))
        assertFalse(PlayerZoomPolicy.isZoomed(1f, 1.0009f))
    }

    @Test
    fun unzoomed_everythingReady_allowsSwipeSeek() {
        assertTrue(seekAllowed())
    }

    @Test
    fun zoomedVideo_permanentlyDisablesSwipeSeek() {
        // 用户捏合放大后松手，手势标志已复位，但画面仍是 2 倍——横滑必须不再调进度。
        assertFalse(seekAllowed(scaleX = 2f))
        assertFalse(seekAllowed(scaleX = 1f, scaleY = 2f))
        assertFalse(seekAllowed(scaleX = 5f, scaleY = 5f))
    }

    @Test
    fun zoomReset_restoresSwipeSeek() {
        assertFalse(seekAllowed(scaleX = 2f))
        assertTrue(seekAllowed(scaleX = 1f))
    }

    @Test
    fun tinyScaleRounding_stillAllowsSwipeSeek() {
        assertTrue(seekAllowed(scaleX = 1.0005f, scaleY = 0.9995f))
    }

    @Test
    fun settingsAndPlayerStateStillGate() {
        assertFalse(seekAllowed(enabled = false))
        assertFalse(seekAllowed(prepared = false))
        assertFalse(seekAllowed(live = true))
        assertFalse(seekAllowed(playerReady = false))
        // 缩放之外的条件都满足时，缩放是唯一否决项。
        assertTrue(seekAllowed(enabled = true, prepared = true, live = false, playerReady = true))
    }
}
