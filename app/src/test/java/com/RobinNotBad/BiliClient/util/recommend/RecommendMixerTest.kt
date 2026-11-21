package com.RobinNotBad.BiliClient.util.recommend

import com.RobinNotBad.BiliClient.model.VideoCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 混合推荐合流（2 网页 : 1 APP 交替）与本地屏蔽过滤的单测。 */
class RecommendMixerTest {

    private fun card(bvid: String, upMid: Long = 1L): VideoCard = VideoCard().apply {
        this.bvid = bvid
        this.upMid = upMid
        this.title = bvid
    }

    private fun web(count: Int): List<VideoCard> = (1..count).map { card("W$it") }

    private fun app(count: Int): List<VideoCard> = (1..count).map { card("A$it") }

    @Test
    fun mix_alternatesTwoWebThenOneApp() {
        val merged = RecommendMixer.mix(web(4), app(2))
        assertEquals(listOf("W1", "W2", "A1", "W3", "W4", "A2"), merged.map { it.bvid })
    }

    @Test
    fun mix_dropsDuplicateBvidKeepingWebCard() {
        val webCards = listOf(card("X"), card("Y"))
        val appCards = listOf(card("Y"), card("Z"))
        val merged = RecommendMixer.mix(webCards, appCards)
        assertEquals(listOf("X", "Y", "Z"), merged.map { it.bvid })
        // 网页源优先：保留下来的 Y 是网页那张实例
        assertEquals(true, merged[1] === webCards[1])
    }

    @Test
    fun mix_dropsEmptyBvid() {
        val webCards = listOf(card(""), card("X"))
        val merged = RecommendMixer.mix(webCards, null)
        assertEquals(listOf("X"), merged.map { it.bvid })
    }

    @Test
    fun mix_finishesRemainingSideWhenOtherRunsOut() {
        val merged = RecommendMixer.mix(web(1), app(3))
        assertEquals(listOf("W1", "A1", "A2", "A3"), merged.map { it.bvid })
        val merged2 = RecommendMixer.mix(web(3), app(0))
        assertEquals(listOf("W1", "W2", "W3"), merged2.map { it.bvid })
    }

    @Test
    fun mix_handlesNullAndEmpty() {
        assertTrue(RecommendMixer.mix(null, null).isEmpty())
        assertTrue(RecommendMixer.mix(emptyList(), emptyList()).isEmpty())
        assertEquals(listOf("A1"), RecommendMixer.mix(null, app(1)).map { it.bvid })
    }

    @Test
    fun mix_customRatio() {
        val merged = RecommendMixer.mix(web(3), app(3), webPerApp = 1)
        assertEquals(listOf("W1", "A1", "W2", "A2", "W3", "A3"), merged.map { it.bvid })
    }

    @Test
    fun mix_zeroRatioTreatedAsOne() {
        val merged = RecommendMixer.mix(web(2), app(2), webPerApp = 0)
        assertEquals(listOf("W1", "A1", "W2", "A2"), merged.map { it.bvid })
    }

    // ---- 屏蔽作者过滤 ----

    @Test
    fun withoutBlocked_removesBlockedAndKeepsUnknownMid() {
        val cards = listOf(
            card("B1", upMid = 7L),
            card("B2", upMid = 8L),
            card("B3", upMid = 0L)
        )
        val kept = RecommendFilter.withoutBlocked(cards, setOf(7L))
        assertEquals(listOf("B2", "B3"), kept.map { it.bvid })
    }

    @Test
    fun withoutBlocked_emptyBlocklistReturnsInput() {
        val cards = listOf(card("B1"))
        assertTrue(RecommendFilter.withoutBlocked(cards, emptySet()) === cards)
        assertTrue(RecommendFilter.withoutBlocked(null, setOf(1L)).isEmpty())
    }
}
