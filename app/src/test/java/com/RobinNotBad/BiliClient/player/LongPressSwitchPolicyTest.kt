package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 长按后纵向划切视频 + 长按倍速提示尺寸的纯逻辑单测。
 *
 * 手势判定的容错边界是重点：宁可少切（用户再划一次），也不能把按住时的手抖
 * 当成切视频——那会在用户没预期时换掉正在看的视频。
 */
class LongPressSwitchPolicyTest {

    private val height = 400 // 屏高 400px，纵向阈值 72px
    private val slop = 8f

    @Test
    fun `阈值按屏高比例`() {
        assertEquals(72f, LongPressSwitchPolicy.thresholdPx(400), 0.01f)
        assertEquals(108f, LongPressSwitchPolicy.thresholdPx(600), 0.01f)
        assertEquals(0f, LongPressSwitchPolicy.thresholdPx(0), 0.01f)
    }

    @Test
    fun `向上划是下一条`() {
        val dir = LongPressSwitchPolicy.direction(
            dxPx = 0f, dyPx = -80f, screenHeightPx = height, durationMs = 200L, touchSlopPx = slop
        )
        assertEquals(LongPressSwitchPolicy.NEXT, dir)
    }

    @Test
    fun `向下划是上一条`() {
        val dir = LongPressSwitchPolicy.direction(
            dxPx = 0f, dyPx = 90f, screenHeightPx = height, durationMs = 200L, touchSlopPx = slop
        )
        assertEquals(LongPressSwitchPolicy.PREVIOUS, dir)
    }

    @Test
    fun `位移不够不切`() {
        val dir = LongPressSwitchPolicy.direction(
            dxPx = 0f, dyPx = -60f, screenHeightPx = height, durationMs = 200L, touchSlopPx = slop
        )
        assertEquals(LongPressSwitchPolicy.NONE, dir)
    }

    @Test
    fun `斜划不切`() {
        // 横向 100px、纵向 -80px：横向远超纵向的 1/1.8，应视为横滑
        val dir = LongPressSwitchPolicy.direction(
            dxPx = 100f, dyPx = -80f, screenHeightPx = height, durationMs = 200L, touchSlopPx = slop
        )
        assertEquals(LongPressSwitchPolicy.NONE, dir)
    }

    @Test
    fun `轻微横向偏移仍可切`() {
        val dir = LongPressSwitchPolicy.direction(
            dxPx = 20f, dyPx = -80f, screenHeightPx = height, durationMs = 200L, touchSlopPx = slop
        )
        assertEquals(LongPressSwitchPolicy.NEXT, dir)
    }

    @Test
    fun `太慢的拖动不切`() {
        val dir = LongPressSwitchPolicy.direction(
            dxPx = 0f, dyPx = -200f, screenHeightPx = height, durationMs = 900L, touchSlopPx = slop
        )
        assertEquals(LongPressSwitchPolicy.NONE, dir)
    }

    @Test
    fun `屏高未知不切`() {
        val dir = LongPressSwitchPolicy.direction(
            dxPx = 0f, dyPx = -200f, screenHeightPx = 0, durationMs = 200L, touchSlopPx = slop
        )
        assertEquals(LongPressSwitchPolicy.NONE, dir)
    }

    @Test
    fun `提示尺寸按档位换算`() {
        assertEquals(0.8f, LongPressSwitchPolicy.hintScale("80"), 0.001f)
        assertEquals(1.0f, LongPressSwitchPolicy.hintScale("100"), 0.001f)
        assertEquals(1.2f, LongPressSwitchPolicy.hintScale("120"), 0.001f)
        assertEquals(1.5f, LongPressSwitchPolicy.hintScale("150"), 0.001f)
    }

    @Test
    fun `提示尺寸脏值回默认或被夹紧`() {
        assertEquals(1.0f, LongPressSwitchPolicy.hintScale(null), 0.001f)
        assertEquals(1.0f, LongPressSwitchPolicy.hintScale(""), 0.001f)
        assertEquals(1.0f, LongPressSwitchPolicy.hintScale("abc"), 0.001f)
        // 低于 80 夹到 0.8，高于 150 夹到 1.5
        assertEquals(0.8f, LongPressSwitchPolicy.hintScale("10"), 0.001f)
        assertEquals(1.5f, LongPressSwitchPolicy.hintScale("999"), 0.001f)
        // 范围内非档位值按比例保留（110% → 1.1）
        assertEquals(1.1f, LongPressSwitchPolicy.hintScale("110"), 0.001f)
    }

    @Test
    fun `默认开关为开`() {
        assertEquals(true, LongPressSwitchPolicy.DEFAULT_SWITCH_ENABLED)
    }
}
