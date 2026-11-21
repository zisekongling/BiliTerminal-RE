package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * G02「±N 秒快退 / 快进」步长判据的单测。
 *
 * 钉死三件事：脏数据回落到默认/边界、偏移只做加法不夹时长、按钮文字按步长格式化。
 */
class PlayerSeekStepPolicyTest {

    @Test
    fun `没设过或脏数据回落到默认值`() {
        assertEquals(PlayerSeekStepPolicy.DEFAULT_SECONDS, PlayerSeekStepPolicy.sanitize(0))
        assertEquals(PlayerSeekStepPolicy.DEFAULT_SECONDS, PlayerSeekStepPolicy.sanitize(-30))
        assertEquals(PlayerSeekStepPolicy.DEFAULT_SECONDS, PlayerSeekStepPolicy.sanitize(Int.MIN_VALUE))
    }

    @Test
    fun `合法步长原样保留`() {
        assertEquals(5, PlayerSeekStepPolicy.sanitize(5))
        assertEquals(30, PlayerSeekStepPolicy.sanitize(30))
        assertEquals(PlayerSeekStepPolicy.MAX_SECONDS, PlayerSeekStepPolicy.sanitize(120))
    }

    @Test
    fun `越界夹到上下限`() {
        assertEquals(PlayerSeekStepPolicy.MIN_SECONDS, PlayerSeekStepPolicy.sanitize(1))
        assertEquals(PlayerSeekStepPolicy.MIN_SECONDS, PlayerSeekStepPolicy.sanitize(2))
        assertEquals(PlayerSeekStepPolicy.MAX_SECONDS, PlayerSeekStepPolicy.sanitize(9999))
        assertEquals(PlayerSeekStepPolicy.MAX_SECONDS, PlayerSeekStepPolicy.sanitize(Int.MAX_VALUE))
    }

    @Test
    fun `偏移按秒数加减`() {
        assertEquals(60_000L, PlayerSeekStepPolicy.offsetMs(50_000L, 10))
        assertEquals(40_000L, PlayerSeekStepPolicy.offsetMs(50_000L, -10))
        assertEquals(50_000L, PlayerSeekStepPolicy.offsetMs(50_000L, 0))
    }

    @Test
    fun `偏移可以被减成负数 交给上层夹时长`() {
        // 这里**不**夹到 0：夹了会让「时长未知」看起来像成功跳到开头
        assertEquals((-5_000L), PlayerSeekStepPolicy.offsetMs(5_000L, -10))
    }

    @Test
    fun `单次偏移不会超过上限步长`() {
        val huge = PlayerSeekStepPolicy.offsetMs(0L, 100_000)
        assertEquals(PlayerSeekStepPolicy.MAX_SECONDS * 1000L, huge)
        val hugeBack = PlayerSeekStepPolicy.offsetMs(0L, -100_000)
        assertEquals(-PlayerSeekStepPolicy.MAX_SECONDS * 1000L, hugeBack)
    }

    @Test
    fun `按钮文字按步长格式化`() {
        assertEquals("«10s", PlayerSeekStepPolicy.label("«%1\$ds", 10))
        assertEquals("10s»", PlayerSeekStepPolicy.label("%1\$ds»", 10))
        // 越界值同样先夹再显示，避免按钮上出现 «9999s
        assertEquals("«120s", PlayerSeekStepPolicy.label("«%1\$ds", 9999))
        assertEquals("«10s", PlayerSeekStepPolicy.label("«%1\$ds", 0))
    }
}
