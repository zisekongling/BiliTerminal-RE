package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.api.SponsorBlockApi
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「扣掉广告后的有效时长」这条纯逻辑（+ 一段真实上游数据的回归样本）。
 *
 * 用户 26.11.14 报「进度条的时间括号没生效」，这条括号画的是 `01:43 (1:34)` 里的 1:34，
 * 算法就是 [SponsorSkipPolicy.savedSeconds]。真实样本用 `BV13yW16VEGT`：上游确实有一条
 * `sponsor` 标注（34.033~47.5 s），所以档位在「仅恰饭广告」及以上时括号必然出现。
 */
class SponsorTimeSavedTest {

    /** 上游 `/skipSegments/26f84aec` 的原样返回（26.11.14 抓取，未改动）。 */
    private val realPayload = """
        [{"videoID":"BV13yW16VEGT","segments":[{"cid":"43509023673",
        "category":"sponsor","actionType":"skip","segment":[34.033,47.5],
        "UUID":"004f6e48e1db8f5ab6d8ab57f321f39b6f6100bb96aac1c76c154a7b588250307",
        "videoDuration":107.033,"locked":0,"votes":0,"description":""}]}]
    """.trimIndent()

    private fun segment(from: Double, to: Double) = ViewPointSkip.Segment(
        type = ViewPointSkip.TYPE_OP,
        fromSec = from,
        toSec = to,
        content = "sponsor",
    )

    @Test
    fun `没有片段时省下0秒`() {
        assertEquals(0, SponsorSkipPolicy.savedSeconds(emptyList()))
    }

    @Test
    fun `单段13点5秒四舍五入成13秒`() {
        // 47.5 - 34.033 = 13.467 → 13 秒（向下）；这就是 BV13yW16VEGT 那条：107 秒的片子省 13 秒
        assertEquals(13, SponsorSkipPolicy.savedSeconds(listOf(segment(34.033, 47.5))))
    }

    @Test
    fun `多段累加后再取整而不是每段各自取整`() {
        // 两段各 10.4 秒：逐段取整会得 10+10=20，先加后取整才是 21
        val saved = SponsorSkipPolicy.savedSeconds(listOf(segment(0.0, 10.4), segment(20.0, 30.4)))
        assertEquals(21, saved)
    }

    @Test
    fun `终点早于起点的脏数据按0算`() {
        assertEquals(0, SponsorSkipPolicy.savedSeconds(listOf(segment(50.0, 50.0))))
        assertEquals(0, SponsorSkipPolicy.savedSeconds(listOf(segment(50.0, 40.0))))
    }

    @Test
    fun `真实样本在仅恰饭广告档位下有1段可跳`() {
        val raw = SponsorBlockApi.parseSegments(realPayload, "BV13yW16VEGT")
        assertEquals(1, raw.size)
        val segments = SponsorSkipPolicy.buildSegments(
            raw,
            SponsorSkipPolicy.categoriesFor(SponsorSkipPolicy.LEVEL_PROMO),
            43509023673L,
            107.033,
        )
        assertEquals(1, segments.size)
        assertEquals(34.033, segments[0].fromSec, 0.001)
        assertEquals(47.5, segments[0].toSec, 0.001)
        // 107 - 13 = 94 → 括号里应该显示 01:34
        assertEquals(13, SponsorSkipPolicy.savedSeconds(segments))
    }

    @Test
    fun `真实样本在档位关闭时括号不出现`() {
        val raw = SponsorBlockApi.parseSegments(realPayload, "BV13yW16VEGT")
        val segments = SponsorSkipPolicy.buildSegments(
            raw,
            SponsorSkipPolicy.categoriesFor(SponsorSkipPolicy.LEVEL_OFF),
            43509023673L,
            107.033,
        )
        assertEquals(0, SponsorSkipPolicy.savedSeconds(segments))
    }

    @Test
    fun `真实样本在别的分P上不算`() {
        val raw = SponsorBlockApi.parseSegments(realPayload, "BV13yW16VEGT")
        val segments = SponsorSkipPolicy.buildSegments(
            raw,
            SponsorSkipPolicy.categoriesFor(SponsorSkipPolicy.LEVEL_PROMO),
            43509023674L,
            107.033,
        )
        assertEquals(0, segments.size)
    }
}
