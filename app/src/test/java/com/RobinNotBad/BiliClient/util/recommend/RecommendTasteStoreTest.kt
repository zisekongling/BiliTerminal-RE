package com.RobinNotBad.BiliClient.util.recommend

import com.RobinNotBad.BiliClient.util.FakeSharedPreferences
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 画像存储单测：6 小时 TTL、负反馈不过期、超长 seen 截断。 */
class RecommendTasteStoreTest {

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = FakeSharedPreferences()
        RecommendTasteStore.clear()
    }

    @After
    fun tearDown() {
        RecommendTasteStore.clear()
        SharedPreferencesUtil.sharedPreferences = null
    }

    @Test
    fun save_thenRead() {
        val now = 1_700_000_000_000L
        RecommendTasteStore.save(mapOf(5L to 1.5), setOf("BV1"), now)
        val snapshot = RecommendTasteStore.snapshot()!!
        assertEquals(now, snapshot.builtAtMs)
        assertEquals(1.5, snapshot.upScores[5L]!!, 1e-9)
        assertTrue(RecommendTasteStore.hasFreshSnapshot(now))
        assertFalse(RecommendTasteStore.hasFreshSnapshot(now + RecommendTasteStore.TTL_MS))
    }

    @Test
    fun save_truncatesWatchedBvids() {
        val many = LinkedHashSet((1..(RecommendTasteProfile.SEEN_LIMIT + 50)).map { "BV$it" })
        RecommendTasteStore.save(emptyMap(), many, 1000L)
        assertEquals(RecommendTasteProfile.SEEN_LIMIT, RecommendTasteStore.snapshot()!!.watchedBvids.size)
    }

    @Test
    fun signal_expiredSnapshotDropsTasteButKeepsNegative() {
        RecommendTasteStore.save(mapOf(5L to 2.0), setOf("BV1"), 1000L)
        RecommendTasteStore.noteNegative(9L)
        val now = 1000L + RecommendTasteStore.TTL_MS + 1
        val signal = RecommendTasteStore.signal(now)
        assertTrue("画像过期后偏好分为空", signal.upScores.isEmpty())
        assertTrue(signal.watchedBvids.isEmpty())
        assertEquals("负反馈不过期", 1, signal.negativeOf(9L))
    }

    @Test
    fun signal_freshSnapshotKeepsEverything() {
        RecommendTasteStore.save(mapOf(5L to 2.0), setOf("BV1"), 1000L)
        val signal = RecommendTasteStore.signal(1000L + 1000L)
        assertEquals(2.0, signal.upScore(5L), 1e-9)
        assertTrue(signal.watchedBvids.contains("BV1"))
    }

    @Test
    fun noteNegative_accumulatesAndCaps() {
        repeat(3) { RecommendTasteStore.noteNegative(4L) }
        assertEquals(3, RecommendTasteStore.negative()[4L])
        repeat(10) { RecommendTasteStore.noteNegative(4L) }
        assertEquals(RecommendTasteProfile.NEGATIVE_LIMIT, RecommendTasteStore.negative()[4L])
        // mid 0 不写盘：返回的是「当前表」（不是空表），里面既没有 0 这一项，也不动已有的项
        val afterZero = RecommendTasteStore.noteNegative(0L)
        assertNull("mid 0 不写盘", afterZero[0L])
        assertEquals(RecommendTasteProfile.NEGATIVE_LIMIT, afterZero[4L])
        assertNull("mid 0 不写盘（落盘后也没有）", RecommendTasteStore.negative()[0L])
    }

    @Test
    fun clear_removesEverything() {
        RecommendTasteStore.save(mapOf(1L to 1.0), setOf("BV1"), 1000L)
        RecommendTasteStore.noteNegative(2L)
        RecommendTasteStore.clear()
        assertEquals(null, RecommendTasteStore.snapshot())
        assertTrue(RecommendTasteStore.negative().isEmpty())
        assertFalse(RecommendTasteStore.hasFreshSnapshot(1000L))
    }
}
