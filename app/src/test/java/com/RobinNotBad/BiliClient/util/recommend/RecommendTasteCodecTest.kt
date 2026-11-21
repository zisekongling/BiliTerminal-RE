package com.RobinNotBad.BiliClient.util.recommend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 画像 / 负反馈编解码单测：坏数据必须退化成「没有画像」，绝不能让推荐页崩。 */
class RecommendTasteCodecTest {

    @Test
    fun snapshot_roundTrip() {
        val snapshot = RecommendTasteSnapshot(
            builtAtMs = 1_700_000_000_000L,
            upScores = mapOf(11L to 1.25, 22L to -0.3),
            watchedBvids = linkedSetOf("BV1", "BV2")
        )
        val decoded = RecommendTasteCodec.decodeSnapshot(RecommendTasteCodec.encodeSnapshot(snapshot))!!
        assertEquals(1_700_000_000_000L, decoded.builtAtMs)
        assertEquals(1.25, decoded.upScores[11L]!!, 1e-9)
        assertEquals(-0.3, decoded.upScores[22L]!!, 1e-9)
        assertEquals(setOf("BV1", "BV2"), decoded.watchedBvids)
    }

    @Test
    fun snapshot_skipsZeroMidAndEmptyBvid() {
        val raw = RecommendTasteCodec.encodeSnapshot(
            RecommendTasteSnapshot(1L, mapOf(0L to 1.0, 5L to 2.0), setOf("", "BV9"))
        )
        val decoded = RecommendTasteCodec.decodeSnapshot(raw)!!
        assertFalse(decoded.upScores.containsKey(0L))
        assertEquals(2.0, decoded.upScores[5L]!!, 1e-9)
        assertEquals(setOf("BV9"), decoded.watchedBvids)
    }

    @Test
    fun snapshot_badInputReturnsNull() {
        assertNull(RecommendTasteCodec.decodeSnapshot(null))
        assertNull(RecommendTasteCodec.decodeSnapshot(""))
        assertNull(RecommendTasteCodec.decodeSnapshot("{not json"))
        assertNull("缺少构建时间当没有画像", RecommendTasteCodec.decodeSnapshot("{\"ups\":{}}"))
        assertNull(RecommendTasteCodec.decodeSnapshot("{\"at\":0}"))
    }

    @Test
    fun snapshot_isFresh() {
        val snapshot = RecommendTasteSnapshot(builtAtMs = 1000L, upScores = emptyMap(), watchedBvids = emptySet())
        val ttl = 6L * 60L * 60L * 1000L
        assertTrue(snapshot.isFresh(1000L, ttl))
        assertTrue(snapshot.isFresh(1000L + ttl - 1, ttl))
        assertFalse("正好过期", snapshot.isFresh(1000L + ttl, ttl))
        assertFalse("时钟回拨也算过期", snapshot.isFresh(999L, ttl))
        assertFalse(RecommendTasteSnapshot(0L, emptyMap(), emptySet()).isFresh(10L, ttl))
    }

    @Test
    fun negative_roundTripAndBadInput() {
        val decoded = RecommendTasteCodec.decodeNegative(RecommendTasteCodec.encodeNegative(mapOf(1L to 3, 2L to 5)))
        assertEquals(mapOf(1L to 3, 2L to 5), decoded)
        assertEquals(emptyMap<Long, Int>(), RecommendTasteCodec.decodeNegative(null))
        assertEquals(emptyMap<Long, Int>(), RecommendTasteCodec.decodeNegative("[1,2]"))
        assertEquals("0 与负数不记", emptyMap<Long, Int>(), RecommendTasteCodec.decodeNegative("{\"0\":2,\"3\":0}"))
    }

    @Test
    fun signal_accessors() {
        val signal = RecommendTasteSignal(mapOf(7L to 2.0), mapOf(8L to 4), setOf("BV1"))
        assertEquals(2.0, signal.upScore(7L), 1e-9)
        assertEquals(0.0, signal.upScore(9L), 1e-9)
        assertEquals(4, signal.negativeOf(8L))
        assertEquals(0, signal.negativeOf(9L))
        assertTrue(RecommendTasteSignal.EMPTY.upScores.isEmpty())
    }
}
