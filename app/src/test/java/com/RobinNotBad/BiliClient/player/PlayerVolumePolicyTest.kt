package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「无极调音」纯逻辑：只测百分比与采样级增益的换算、步进和边界钳制，
 * 不把 Android/ijk 桩当作实际播放测试（真机听感仍要装机验证）。
 */
class PlayerVolumePolicyTest {

    private val delta = 1e-6f

    @Test
    fun 默认增益是满格一百且键名稳定() {
        assertEquals(100, PlayerVolumePolicy.DEFAULT_PERCENT)
        assertEquals("player_volume_gain_percent", PlayerVolumePolicy.KEY_GAIN_PERCENT)
    }

    @Test
    fun 零与一百分别对应静音和不放大() {
        assertEquals(0f, PlayerVolumePolicy.percentToGain(0), delta)
        assertEquals(1f, PlayerVolumePolicy.percentToGain(100), delta)
    }

    @Test
    fun 中间百分比按百等分换算() {
        assertEquals(0.01f, PlayerVolumePolicy.percentToGain(1), delta)
        assertEquals(0.5f, PlayerVolumePolicy.percentToGain(50), delta)
        assertEquals(0.99f, PlayerVolumePolicy.percentToGain(99), delta)
    }

    @Test
    fun 越界百分比在读取侧被钳制() {
        assertEquals(0, PlayerVolumePolicy.sanitize(-1))
        assertEquals(0, PlayerVolumePolicy.sanitize(-999))
        assertEquals(100, PlayerVolumePolicy.sanitize(101))
        assertEquals(100, PlayerVolumePolicy.sanitize(999))
        assertEquals(50, PlayerVolumePolicy.sanitize(50))
    }

    @Test
    fun 越界百分比换算增益时同样被钳制() {
        assertEquals(0f, PlayerVolumePolicy.percentToGain(-20), delta)
        assertEquals(1f, PlayerVolumePolicy.percentToGain(300), delta)
    }

    @Test
    fun 增益反向换算按四舍五入回百分比() {
        assertEquals(0, PlayerVolumePolicy.gainToPercent(0f))
        assertEquals(42, PlayerVolumePolicy.gainToPercent(0.42f))
        assertEquals(0, PlayerVolumePolicy.gainToPercent(0.004f))
        assertEquals(50, PlayerVolumePolicy.gainToPercent(0.5f))
        assertEquals(100, PlayerVolumePolicy.gainToPercent(1f))
    }

    @Test
    fun 越界增益反向换算被钳制而不是绕回() {
        assertEquals(0, PlayerVolumePolicy.gainToPercent(-1f))
        assertEquals(100, PlayerVolumePolicy.gainToPercent(2f))
    }

    @Test
    fun 非数字增益退回默认值而不是静音() {
        assertEquals(PlayerVolumePolicy.DEFAULT_PERCENT, PlayerVolumePolicy.gainToPercent(Float.NaN))
    }

    @Test
    fun 步进会在上下界停住() {
        assertEquals(0, PlayerVolumePolicy.step(0, -1))
        assertEquals(100, PlayerVolumePolicy.step(100, 1))
        assertEquals(100, PlayerVolumePolicy.step(99, 1))
        assertEquals(0, PlayerVolumePolicy.step(1, -1))
    }

    @Test
    fun 步进常规加减() {
        assertEquals(55, PlayerVolumePolicy.step(50, 1))
        assertEquals(45, PlayerVolumePolicy.step(50, -1))
        assertEquals(51, PlayerVolumePolicy.step(50, 1, PlayerVolumePolicy.FINE_STEP_PERCENT))
        assertEquals(49, PlayerVolumePolicy.step(50, -1, PlayerVolumePolicy.FINE_STEP_PERCENT))
    }

    @Test
    fun 零或负数步长退化成最小步长() {
        assertEquals(51, PlayerVolumePolicy.step(50, 1, 0))
        assertEquals(51, PlayerVolumePolicy.step(50, 1, -5))
        assertEquals(49, PlayerVolumePolicy.step(50, -1, 0))
    }

    @Test
    fun 过大步长不会绕圈而是贴边() {
        assertEquals(100, PlayerVolumePolicy.step(95, 1, 1000))
        assertEquals(0, PlayerVolumePolicy.step(5, -1, 1000))
    }

    @Test
    fun 方向为零时不动() {
        assertEquals(50, PlayerVolumePolicy.step(50, 0))
    }

    @Test
    fun 只有零个百分点算静音() {
        assertTrue(PlayerVolumePolicy.isMuted(0))
        assertFalse(PlayerVolumePolicy.isMuted(1))
        assertFalse(PlayerVolumePolicy.isMuted(100))
    }

    @Test
    fun 双声道增益一致() {
        val pair = PlayerVolumePolicy.gainPair(30)
        assertEquals(0.3f, pair.first, delta)
        assertEquals(pair.first, pair.second, delta)
    }
    @Test
    fun 系统音量步进按一档加减并在两端停住() {
        assertEquals(6, PlayerVolumePolicy.stepStream(5, 15, 1))
        assertEquals(4, PlayerVolumePolicy.stepStream(5, 15, -1))
        // 到顶再按 + 不绕回 0：手表系统音量只有十几档，绕回等于「按一下静音」
        assertEquals(15, PlayerVolumePolicy.stepStream(15, 15, 1))
        assertEquals(0, PlayerVolumePolicy.stepStream(0, 15, -1))
    }

    @Test
    fun 系统音量步进方向为零时原样返回且不越界() {
        assertEquals(5, PlayerVolumePolicy.stepStream(5, 15, 0))
        assertEquals(15, PlayerVolumePolicy.stepStream(99, 15, 0))
    }

    @Test
    fun 系统音量上限非法时按零处理() {
        // 个别设备的 STREAM_MUSIC 会返回 0，调用方据此跳过，不能算出负数档位
        assertEquals(0, PlayerVolumePolicy.stepStream(3, 0, 1))
        assertEquals(0, PlayerVolumePolicy.stepStream(3, -1, -1))
    }

    @Test
    fun 系统音量档位换算成百分比用于回显() {
        assertEquals(0, PlayerVolumePolicy.streamPercent(0, 15))
        assertEquals(100, PlayerVolumePolicy.streamPercent(15, 15))
        assertEquals(33, PlayerVolumePolicy.streamPercent(5, 15))
        // 上限非法时不显示「0/0」算出的垃圾值，直接按 0% 回显
        assertEquals(0, PlayerVolumePolicy.streamPercent(3, 0))
    }
}
