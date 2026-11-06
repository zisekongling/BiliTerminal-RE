package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D5 直播滚动弹幕停留时长倍率的纯逻辑单测。
 *
 * 三条不可退让的口径：
 * 1. 直播默认 2.0，且与录播的「弹幕速度」互相独立（录播 1.0 不该被直播倍率带跑）；
 * 2. 可调范围 1.0–3.0，脏存档（0、负数、NaN、超大）必须钳到范围内；
 * 3. 存档是 list_choose 的字符串，解析失败按默认 2.0，不能变成 0（0 = 弹幕瞬间消失）。
 */
class LiveDanmakuFactorPolicyTest {

    @Test
    fun `默认值为2倍`() {
        assertEquals(2.0f, LiveDanmakuFactorPolicy.DEFAULT, 0f)
        assertEquals(2.0f, LiveDanmakuFactorPolicy.parseStored(null), 0f)
        assertEquals(2.0f, LiveDanmakuFactorPolicy.parseStored(""), 0f)
        assertEquals(2.0f, LiveDanmakuFactorPolicy.parseStored("不是数字"), 0f)
    }

    @Test
    fun `解析合法存档`() {
        assertEquals(1.0f, LiveDanmakuFactorPolicy.parseStored("1.0"), 0f)
        assertEquals(1.5f, LiveDanmakuFactorPolicy.parseStored("1.5"), 0f)
        assertEquals(2.5f, LiveDanmakuFactorPolicy.parseStored("2.5"), 0f)
        assertEquals(3.0f, LiveDanmakuFactorPolicy.parseStored("3.0"), 0f)
    }

    @Test
    fun `脏值钳到可调范围`() {
        assertEquals(1.0f, LiveDanmakuFactorPolicy.sanitize(0f), 0f)
        assertEquals(1.0f, LiveDanmakuFactorPolicy.sanitize(-2f), 0f)
        assertEquals(3.0f, LiveDanmakuFactorPolicy.sanitize(9f), 0f)
        assertEquals(LiveDanmakuFactorPolicy.DEFAULT, LiveDanmakuFactorPolicy.sanitize(Float.NaN), 0f)
        assertEquals(
            LiveDanmakuFactorPolicy.DEFAULT,
            LiveDanmakuFactorPolicy.sanitize(Float.POSITIVE_INFINITY),
            0f
        )
        assertEquals(LiveDanmakuFactorPolicy.MIN, LiveDanmakuFactorPolicy.parseStored("0"), 0f)
    }

    @Test
    fun `直播用独立倍率`() {
        assertEquals(2.0f, LiveDanmakuFactorPolicy.scrollSpeedFactor(true, 1.0f, 2.0f), 0f)
        assertEquals(1.5f, LiveDanmakuFactorPolicy.scrollSpeedFactor(true, 1.0f, 1.5f), 0f)
        // 直播倍率被钳制后仍以策略为准
        assertEquals(
            LiveDanmakuFactorPolicy.MAX,
            LiveDanmakuFactorPolicy.scrollSpeedFactor(true, 1.0f, 99f),
            0f
        )
    }

    @Test
    fun `录播完全不看直播倍率`() {
        assertEquals(1.0f, LiveDanmakuFactorPolicy.scrollSpeedFactor(false, 1.0f, 3.0f), 0f)
        assertEquals(0.5f, LiveDanmakuFactorPolicy.scrollSpeedFactor(false, 0.5f, 3.0f), 0f)
        assertEquals(
            LiveDanmakuFactorPolicy.DEFAULT,
            LiveDanmakuFactorPolicy.scrollSpeedFactor(false, LiveDanmakuFactorPolicy.DEFAULT, 3.0f),
            0f
        )
    }

    @Test
    fun `设置档位与落盘值一一对应`() {
        assertEquals(
            LiveDanmakuFactorPolicy.displayNames.size,
            LiveDanmakuFactorPolicy.storedValues.size
        )
        assertTrue(LiveDanmakuFactorPolicy.storedValues.contains("2.0"))
        for (stored in LiveDanmakuFactorPolicy.storedValues) {
            val factor = LiveDanmakuFactorPolicy.parseStored(stored)
            assertEquals(sanitizeRange(factor), factor, 0f)
        }
    }

    private fun sanitizeRange(value: Float): Float = value.coerceIn(
        LiveDanmakuFactorPolicy.MIN,
        LiveDanmakuFactorPolicy.MAX
    )
}
