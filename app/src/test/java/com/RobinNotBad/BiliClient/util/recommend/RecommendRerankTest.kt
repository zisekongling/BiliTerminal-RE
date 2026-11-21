package com.RobinNotBad.BiliClient.util.recommend

import com.RobinNotBad.BiliClient.model.VideoCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** X 式重排单测：先验顺序、画像加分、负反馈、已看过剔除、多样性贪心。 */
class RecommendRerankTest {

    private fun card(bvid: String, upMid: Long): VideoCard = VideoCard().apply {
        this.bvid = bvid
        this.upMid = upMid
        this.title = bvid
    }

    private fun signal(
        upScores: Map<Long, Double> = emptyMap(),
        negative: Map<Long, Int> = emptyMap(),
        watched: Set<String> = emptySet()
    ) = RecommendTasteSignal(upScores, negative, watched)

    @Test
    fun rerank_withoutSignal_keepsOriginalOrder() {
        val cards = listOf(card("B1", 1L), card("B2", 2L), card("B3", 3L))
        assertEquals(listOf("B1", "B2", "B3"), RecommendRerank.rerank(cards).map { it.bvid })
        assertEquals(listOf("B1", "B2", "B3"), RecommendRerank.rerank(cards, null).map { it.bvid })
    }

    @Test
    fun rerank_emptyInput() {
        assertTrue(RecommendRerank.rerank(null).isEmpty())
        assertTrue(RecommendRerank.rerank(emptyList()).isEmpty())
    }

    @Test
    fun rerank_removesWatchedBvid() {
        val cards = listOf(card("B1", 1L), card("B2", 2L), card("B3", 3L))
        val result = RecommendRerank.rerank(cards, signal(watched = setOf("B2")))
        assertEquals(listOf("B1", "B3"), result.map { it.bvid })
    }

    @Test
    fun rerank_allWatchedReturnsEmpty() {
        val cards = listOf(card("B1", 1L), card("B2", 2L))
        assertTrue(RecommendRerank.rerank(cards, signal(watched = setOf("B1", "B2"))).isEmpty())
    }

    @Test
    fun rerank_tasteBoostLiftsLaterCard() {
        // 第 3 张先验 0.6667，加上 tanh(4/2)*0.6 ≈ 0.578 后超过第 1 张的 1.0
        val cards = listOf(card("B1", 1L), card("B2", 2L), card("B3", 3L))
        val result = RecommendRerank.rerank(cards, signal(upScores = mapOf(3L to 4.0)))
        assertEquals(listOf("B3", "B1", "B2"), result.map { it.bvid })
    }

    @Test
    fun rerank_negativeSinksUp() {
        val cards = listOf(card("B1", 1L), card("B2", 2L), card("B3", 3L))
        val result = RecommendRerank.rerank(cards, signal(negative = mapOf(1L to 5)))
        assertEquals(listOf("B2", "B3", "B1"), result.map { it.bvid })
    }

    @Test
    fun rerank_batchRepeatPenaltySpreadsSameUp() {
        val cards = listOf(card("B1", 1L), card("B2", 1L), card("B3", 2L))
        // B1 先验 1.0 先出；随后同 UP 的 B2 从 0.8333 掉到 (0.8333-0.5)*0.6=0.2，让位给 B3
        val result = RecommendRerank.rerank(cards)
        assertEquals(listOf("B1", "B3", "B2"), result.map { it.bvid })
    }

    @Test
    fun rerank_recentWindowPenalty() {
        val cards = listOf(card("B1", 1L), card("B2", 2L))
        // 上一批最后是 UP 1，扣 0.5 后 B1 只剩 0.5，B2 的 0.8333 反超
        val result = RecommendRerank.rerank(cards, null, listOf(1L))
        assertEquals(listOf("B2", "B1"), result.map { it.bvid })
        // 只看最近 3 个：更早的那个 UP 不再计罚
        assertEquals("窗口外的旧 UP 不罚", listOf("B1", "B2"), RecommendRerank.rerank(cards, null, listOf(2L, 9L, 9L, 9L)).map { it.bvid })
    }

    @Test
    fun rerank_zeroUpMidCardsSurviveAndKeepPriorOrder() {
        val cards = listOf(card("B1", 0L), card("B2", 0L))
        val result = RecommendRerank.rerank(cards, signal(negative = mapOf(1L to 5)))
        assertEquals(listOf("B1", "B2"), result.map { it.bvid })
    }
}
