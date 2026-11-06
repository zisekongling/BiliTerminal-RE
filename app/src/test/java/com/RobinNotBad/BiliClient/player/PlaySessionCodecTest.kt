package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 播放会话哨兵的编解码单测：进程被杀后要靠这段文本把「播哪、播到哪、队列是什么」还原出来，
 * 任何一处字段顺序/转义错了都会静默丢会话，所以逐个字段钉死。
 */
class PlaySessionCodecTest {

    private fun session(
        title: String = "【测试】第 1 集",
        source: PlayQueueSource = PlayQueueSource.FAVORITE,
        hasQueueToken: Boolean = true
    ) = PlaySession(
        aid = 114514L,
        cid = 223344L,
        mid = 5566L,
        epid = 7788L,
        seasonId = 9900L,
        seasonType = 1,
        type = 1,
        title = title,
        positionMs = 123456L,
        durationMs = 654321L,
        source = source,
        pageIndex = 3,
        hasQueueToken = hasQueueToken,
        updatedAt = 1700000000000L
    )

    @Test
    fun `往返编码保留全部字段`() {
        val original = session()
        val decoded = PlaySessionCodec.decode(PlaySessionCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `标题里的制表符反斜杠换行不破坏编码`() {
        val original = session(title = "标题\t带\\反斜杠\n换行")
        val decoded = PlaySessionCodec.decode(PlaySessionCodec.encode(original))
        assertEquals("标题\t带\\反斜杠\n换行", decoded?.title)
        assertEquals(1, decoded?.type)
    }

    @Test
    fun `空标题与无队列标记也能恢复`() {
        val decoded = PlaySessionCodec.decode(PlaySessionCodec.encode(session(title = "", hasQueueToken = false)))
        assertEquals("", decoded?.title)
        assertFalse(decoded!!.hasQueueToken)
        assertTrue(decoded.isMeaningful())
    }

    @Test
    fun `非法输入返回空而不抛异常`() {
        assertNull(PlaySessionCodec.decode(null))
        assertNull(PlaySessionCodec.decode(""))
        assertNull(PlaySessionCodec.decode("garbage"))
        assertNull(PlaySessionCodec.decode("PLAYSESSION1\t1\t2"))
        assertNull(PlaySessionCodec.decode("PLAYSESSION2\t1\t2\t3\t4\t5\t1\t1\t1\t1\tfavorite\t0\t1\t1\tt"))
        assertNull(PlaySessionCodec.decode("PLAYSESSION1\ta\t2\t3\t4\t5\t1\t1\t1\t1\tfavorite\t0\t1\t1\tt"))
    }
}
