package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 弹幕「空降」时间戳解析的单测。
 *
 * 反例和正例同样重要：误判的代价是用户点一条普通弹幕就被拽到别处，
 * 所以 `1080p` / `1:2` / `99:99` / `12:34:56:78` 这些边界都要钉住。
 */
class DanmakuTimestampPolicyTest {

    @Test
    fun `解析 mm ss`() {
        assertEquals(83_000L, DanmakuTimestampPolicy.parseToken("01:23"))
        assertEquals(83_000L, DanmakuTimestampPolicy.parseToken("1:23"))
        assertEquals(0L, DanmakuTimestampPolicy.parseToken("00:00"))
        assertEquals(59 * 60_000L + 59_000L, DanmakuTimestampPolicy.parseToken("59:59"))
    }

    @Test
    fun `解析 hh mm ss`() {
        assertEquals(3_600_000L, DanmakuTimestampPolicy.parseToken("1:00:00"))
        assertEquals(1 * 3_600_000L + 23 * 60_000L + 45_000L, DanmakuTimestampPolicy.parseToken("01:23:45"))
        assertEquals(99 * 3_600_000L, DanmakuTimestampPolicy.parseToken("99:00:00"))
    }

    @Test
    fun `非法格式一律拒绝`() {
        // 秒不是两位
        assertNull(DanmakuTimestampPolicy.parseToken("1:2"))
        // 分/秒超过 59
        assertNull(DanmakuTimestampPolicy.parseToken("99:99"))
        // 四段（12:34:56:78）
        assertNull(DanmakuTimestampPolicy.parseToken("12:34:56:78"))
        // 分钟三位
        assertNull(DanmakuTimestampPolicy.parseToken("123:45"))
        // 前缀/后缀/空段
        assertNull(DanmakuTimestampPolicy.parseToken(":12"))
        assertNull(DanmakuTimestampPolicy.parseToken("12:"))
        assertNull(DanmakuTimestampPolicy.parseToken(""))
        assertNull(DanmakuTimestampPolicy.parseToken(null))
        // 无冒号
        assertNull(DanmakuTimestampPolicy.parseToken("1080"))
    }

    @Test
    fun `在正文中找到独立时间戳`() {
        val match = requireNotNull(DanmakuTimestampPolicy.find("空降 01:23"))
        assertEquals(3, match.startIndex)
        assertEquals(8, match.endIndex)
        assertEquals(83_000L, match.timeMs)
        assertEquals("01:23", match.raw)
    }

    @Test
    fun `优先取第一个合法时间戳`() {
        val match = requireNotNull(DanmakuTimestampPolicy.find("前面 1:2 不算，空降 02:03 才是"))
        assertEquals("02:03", match.raw)
        assertEquals(123_000L, match.timeMs)
    }

    @Test
    fun `普通数字与分辨率不误判`() {
        assertNull(DanmakuTimestampPolicy.find("1080p 60fps av12345"))
        assertNull(DanmakuTimestampPolicy.find("第 12 集 34 分 56 秒"))
        assertNull(DanmakuTimestampPolicy.find(""))
        assertNull(DanmakuTimestampPolicy.find(null))
    }

    @Test
    fun `四段时间戳不会被截成三段`() {
        // 整段 12:34:56:78 是一个 token，解析失败后不能从中间再截一次
        assertNull(DanmakuTimestampPolicy.find("12:34:56:78"))
    }

    @Test
    fun `小时格式的正文命中`() {
        val match = requireNotNull(DanmakuTimestampPolicy.find("空降1:02:03走起"))
        assertEquals("1:02:03", match.raw)
        assertEquals(1 * 3_600_000L + 2 * 60_000L + 3_000L, match.timeMs)
    }
}
