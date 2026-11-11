package com.RobinNotBad.BiliClient.util.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SettingsHighlight] 的纯数学部分单测（高亮曲线与 ARGB 插值）。
 *
 * 只测能脱离 Android 跑的部分：绑定/动画本身要看画面，属于 L2 人工验收。
 * 锁住两件事：
 * 1. **满亮保持 + 单调淡出**：曲线前段必须恒为 1（否则「高亮」刚出现就开始变暗，看不清）；
 * 2. **插值端点准确**：t=0 必须回到原色、t=1 必须是目标色——还原样式靠这条性质。
 */
class SettingsHighlightTest {

    /** 1500ms 满亮 + 450ms 淡出，满亮占总时长的比例。 */
    private val holdRatio = 1500f / 1950f

    @Test
    fun fadeFactor_staysFullDuringHold() {
        assertEquals(1f, SettingsHighlight.fadeFactor(0f), 1e-6f)
        assertEquals(1f, SettingsHighlight.fadeFactor(holdRatio / 2f), 1e-6f)
        assertEquals(1f, SettingsHighlight.fadeFactor(holdRatio), 1e-6f)
    }

    @Test
    fun fadeFactor_reachesZeroAtEnd() {
        assertEquals(0f, SettingsHighlight.fadeFactor(1f), 1e-6f)
    }

    @Test
    fun fadeFactor_decreasesMonotonicallyAfterHold() {
        var previous = SettingsHighlight.fadeFactor(holdRatio)
        var step = holdRatio
        while (step < 1f) {
            step += 0.01f
            val current = SettingsHighlight.fadeFactor(step)
            assertTrue("淡出段必须单调不增：$previous -> $current", current <= previous + 1e-6f)
            previous = current
        }
    }

    @Test
    fun lerpColor_returnsEndpointsExactly() {
        val from = 0xFF3366CC.toInt()
        val to = 0xFFEE8822.toInt()
        assertEquals(from, SettingsHighlight.lerpColor(from, to, 0f))
        assertEquals(to, SettingsHighlight.lerpColor(from, to, 1f))
    }

    @Test
    fun lerpColor_clampsOutOfRangeRatio() {
        val from = 0xFF000000.toInt()
        val to = 0xFFFFFFFF.toInt()
        assertEquals(from, SettingsHighlight.lerpColor(from, to, -3f))
        assertEquals(to, SettingsHighlight.lerpColor(from, to, 3f))
    }

    @Test
    fun lerpColor_interpolatesEveryChannelIncludingAlpha() {
        // 透明 -> 不透明主色，一半进度：四通道都该在中点（alpha≈128）
        val half = SettingsHighlight.lerpColor(0x00000000, 0xFF204080.toInt(), 0.5f)
        assertEquals(128, half ushr 24 and 0xFF)
        assertEquals(0x10, half ushr 16 and 0xFF)
        assertEquals(0x20, half ushr 8 and 0xFF)
        assertEquals(0x40, half and 0xFF)
    }

    @Test
    fun blend_isJustLerpTowardsTarget() {
        val base = 0xFF404040.toInt()
        val primary = 0xFFFB7299.toInt()
        assertEquals(base, SettingsHighlight.blend(base, primary, 0f))
        assertEquals(primary, SettingsHighlight.blend(base, primary, 1f))
    }
}
