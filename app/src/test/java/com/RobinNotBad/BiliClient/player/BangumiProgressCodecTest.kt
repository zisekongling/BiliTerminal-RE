package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 番剧「每集独立进度」编解码单测。
 *
 * 服务端只认「当前一集」，本地的 epid→进度表要能可靠往返；顺带验证 128 条上限与坏行容错，
 * 避免一次异常写入把整张表读成空。
 */
class BangumiProgressCodecTest {

    private fun entry(epid: Long, position: Long, completed: Boolean = false, updatedAt: Long = epid) =
        BangumiProgress(epid, position, 1_440_000L, completed, updatedAt)

    @Test
    fun `多条记录往返保留字段`() {
        val entries = listOf(entry(1L, 1000L), entry(2L, 2000L), entry(3L, 0L, completed = true))
        val decoded = BangumiProgressCodec.decode(BangumiProgressCodec.encode(entries))
        assertEquals(3, decoded.size)
        assertEquals(1000L, decoded[1L]?.positionMs)
        assertTrue(decoded[3L]!!.completed)
    }

    @Test
    fun `超出上限时按更新时间保留最新 128 条`() {
        val entries = (1L..200L).map { entry(it, it * 10L, updatedAt = it) }
        val decoded = BangumiProgressCodec.decode(BangumiProgressCodec.encode(entries))
        assertEquals(128, decoded.size)
        assertTrue(decoded.containsKey(200L))
        assertTrue(!decoded.containsKey(1L))
    }

    @Test
    fun `坏行与非正 epid 被跳过`() {
        val text = "1\t100\t200\t0\t3\n\nbadline\n0\t1\t2\t0\t1\n2\tabc\t200\t0\t5\n3\t300\t400\t0\t6"
        val decoded = BangumiProgressCodec.decode(text)
        assertEquals(2, decoded.size)
        assertEquals(100L, decoded[1L]?.positionMs)
        assertEquals(300L, decoded[3L]?.positionMs)
    }

    @Test
    fun `空输入返回空表`() {
        assertEquals(0, BangumiProgressCodec.decode(null).size)
        assertEquals(0, BangumiProgressCodec.decode("").size)
        assertEquals("", BangumiProgressCodec.encode(emptyList()))
    }

    @Test
    fun `负位置被夹到 0`() {
        val decoded = BangumiProgressCodec.decode("9\t-5\t100\t0\t1")
        assertEquals(0L, decoded[9L]?.positionMs)
        assertNull(decoded[10L])
    }
}
