package com.RobinNotBad.BiliClient.util.recommend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 画像公式（对齐 bilibili-margin rank.ts 的 buildTaste）单测。 */
class RecommendTasteProfileTest {

    private fun entry(upMid: Long, ageDays: Double, progress: Long, duration: Long) =
        RecommendTasteEntry(upMid, ageDays, progress, duration)

    @Test
    fun decay_isExpMinusDaysOver30() {
        assertEquals(1.0, RecommendTasteProfile.decay(0.0), 1e-9)
        assertEquals(Math.exp(-1.0), RecommendTasteProfile.decay(30.0), 1e-9)
        assertEquals("负数当 0 天", 1.0, RecommendTasteProfile.decay(-5.0), 1e-9)
    }

    @Test
    fun entry_flags() {
        assertTrue(entry(1, 0.0, -1, 100).finished)
        assertFalse(entry(1, 0.0, 50, 100).finished)
        assertTrue("10 秒内退且视频够长", entry(1, 0.0, 9, 61).quitEarly)
        assertFalse("视频太短不算秒退", entry(1, 0.0, 9, 60).quitEarly)
        assertFalse("看了 10 秒不算秒退", entry(1, 0.0, 10, 600).quitEarly)
        assertFalse("已看完不算秒退", entry(1, 0.0, -1, 600).quitEarly)
    }

    @Test
    fun completion() {
        assertEquals(1.0, entry(1, 0.0, -1, 0).completion, 1e-9)
        assertEquals(0.0, entry(1, 0.0, 30, 0).completion, 1e-9)
        assertEquals(0.5, entry(1, 0.0, 50, 100).completion, 1e-9)
        assertEquals("进度超过时长按 1", 1.0, entry(1, 0.0, 200, 100).completion, 1e-9)
    }

    @Test
    fun weight_matchesReferenceFormula() {
        // 今天看完：decay=1，权重 = 0.3 + 1 = 1.3
        assertEquals(1.3, RecommendTasteProfile.weight(entry(1, 0.0, -1, 100)), 1e-9)
        // 今天秒退：-0.3
        assertEquals(-0.3, RecommendTasteProfile.weight(entry(1, 0.0, 5, 300)), 1e-9)
        // 30 天前半程：exp(-1) * (0.3 + 0.5)
        assertEquals(Math.exp(-1.0) * 0.8, RecommendTasteProfile.weight(entry(1, 30.0, 50, 100)), 1e-9)
    }

    @Test
    fun build_sumsPerUpAndSkipsZeroMid() {
        val scores = RecommendTasteProfile.build(
            listOf(
                entry(1, 0.0, -1, 100),
                entry(1, 0.0, -1, 100),
                entry(2, 0.0, 5, 300),
                entry(0, 0.0, -1, 100)
            )
        )
        assertEquals(2.6, scores[1]!!, 1e-9)
        assertEquals(-0.3, scores[2]!!, 1e-9)
        assertFalse(scores.containsKey(0L))
        assertTrue(RecommendTasteProfile.build(null).isEmpty())
    }

    @Test
    fun noteNegative_capsAndDoesNotMutateInput() {
        val start = mapOf(7L to 4)
        val once = RecommendTasteProfile.noteNegative(start, 7L)
        assertEquals(5, once[7L])
        assertEquals("入参不变", 4, start[7L])
        val capped = RecommendTasteProfile.noteNegative(start, 7L, weight = 10)
        assertEquals(RecommendTasteProfile.NEGATIVE_LIMIT, capped[7L])
        assertEquals("mid 0 不记", emptyMap<Long, Int>(), RecommendTasteProfile.noteNegative(null, 0L))
    }

    @Test
    fun of_convertsViewAtToAgeDays() {
        val e = RecommendTasteEntry.of(3L, viewAtSeconds = 0L, progressSeconds = 10L, durationSeconds = 100L, nowSeconds = 86400L * 2)
        assertEquals(2.0, e.ageDays, 1e-9)
        val future = RecommendTasteEntry.of(3L, viewAtSeconds = 86400L, progressSeconds = 10L, durationSeconds = 100L, nowSeconds = 0L)
        assertEquals("未来时间当 0 天", 0.0, future.ageDays, 1e-9)
    }
}
