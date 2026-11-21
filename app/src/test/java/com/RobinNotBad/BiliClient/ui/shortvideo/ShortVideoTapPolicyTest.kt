package com.RobinNotBad.BiliClient.ui.shortvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 单击落点路由：中心键热区与「单击切控件层」在圆屏中心重叠时的优先级。
 * 口径见 design/short-video-redesign.html 手势表：单击画面只切显示，中心键仅暂停时接管中心。
 */
class ShortVideoTapPolicyTest {

    private fun route(
        x: Float,
        y: Float,
        playing: Boolean,
        centerKeyVisible: Boolean = !playing,
        overlayOpen: Boolean = false,
    ) = ShortVideoTapPolicy.route(
        x, y,
        centerKeyVisible = centerKeyVisible,
        centerX = 100f, centerY = 100f, hotHalf = 28f,
        playing = playing,
        overlayOpen = overlayOpen,
    )

    @Test
    fun playingCenterTapStillTogglesControls() {
        // 播放中中心键不该存在；即便视图残留可见，中心落点也归「切控件层」
        assertEquals(ShortVideoTapPolicy.Action.TOGGLE_CONTROLS, route(100f, 100f, playing = true))
        assertEquals(
            ShortVideoTapPolicy.Action.TOGGLE_CONTROLS,
            route(100f, 100f, playing = true, centerKeyVisible = true),
        )
    }

    @Test
    fun pausedCenterTapResumesPlayback() {
        assertEquals(ShortVideoTapPolicy.Action.TOGGLE_PLAY_PAUSE, route(100f, 100f, playing = false))
        // 热区边界含等号：边上这一下也算中心键
        assertEquals(ShortVideoTapPolicy.Action.TOGGLE_PLAY_PAUSE, route(128f, 100f, playing = false))
        assertEquals(ShortVideoTapPolicy.Action.TOGGLE_PLAY_PAUSE, route(100f, 72f, playing = false))
    }

    @Test
    fun pausedTapOutsideHotzoneTogglesControls() {
        assertEquals(ShortVideoTapPolicy.Action.TOGGLE_CONTROLS, route(129f, 100f, playing = false))
        assertEquals(ShortVideoTapPolicy.Action.TOGGLE_CONTROLS, route(10f, 10f, playing = false))
        // 沉浸态（控件层已收）点空白也要能唤回：路由不关心控件层当前显隐
        assertEquals(ShortVideoTapPolicy.Action.TOGGLE_CONTROLS, route(60f, 160f, playing = false))
    }

    @Test
    fun hiddenCenterKeyNeverSwallowsCenterTap() {
        assertEquals(
            ShortVideoTapPolicy.Action.TOGGLE_CONTROLS,
            route(100f, 100f, playing = false, centerKeyVisible = false),
        )
    }

    @Test
    fun openOverlayOwnsWholeScreen() {
        assertEquals(ShortVideoTapPolicy.Action.IGNORE, route(100f, 100f, playing = false, overlayOpen = true))
        assertEquals(ShortVideoTapPolicy.Action.IGNORE, route(5f, 5f, playing = true, overlayOpen = true))
    }

    @Test
    fun hotzoneNeverShrinksBelow24dp() {
        assertEquals(56f, ShortVideoTapPolicy.touchSize(56f), 0f)
        assertEquals(ShortVideoTapPolicy.MIN_TOUCH_DP, ShortVideoTapPolicy.touchSize(10f), 0f)
        // 各档屏幕度量下中心键热区都不低于 24dp（手表小屏缩放后也成立）
        for (width in intArrayOf(120, 192, 233, 320, 466)) {
            val metrics = ShortVideoLayoutSpec.Metrics(width.toFloat(), width.toFloat(), true)
            assertTrue(ShortVideoTapPolicy.touchSize(ShortVideoLayoutSpec.playSize(metrics)) >= 24f)
        }
    }
}