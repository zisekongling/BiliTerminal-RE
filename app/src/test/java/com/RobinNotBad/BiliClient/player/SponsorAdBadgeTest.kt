package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.api.SponsorBlockApi
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 视频详情页「广告」胶囊的判定（26.11.14）。
 *
 * 钉住三件事：
 * 1. 整片标签（`actionType=full`）且类别属于恰饭类 → [SponsorSkipPolicy.BADGE_FULL]；
 * 2. 有恰饭类片段但不是整片 → [SponsorSkipPolicy.BADGE_PARTIAL]；
 * 3. 片头 / 三连提示这类**不是广告**的类别，以及别的分 P、换源前的旧标注，都不该让详情页挂胶囊。
 */
class SponsorAdBadgeTest {

    private val cid = 36656057057L
    private val duration = 600.0

    private fun raw(
        category: String = SponsorSkipPolicy.CAT_SPONSOR,
        action: String = SponsorSkipPolicy.ACTION_SKIP,
        from: Double = 10.0,
        to: Double = 40.0,
        cid: Long = this.cid,
        videoDuration: Double = duration,
    ) = SponsorBlockApi.RawSegment(cid, category, action, from, to, videoDuration, 0, "")

    @Test
    fun `没有标注就不挂胶囊`() {
        assertEquals(SponsorSkipPolicy.BADGE_NONE, SponsorSkipPolicy.adBadge(emptyList(), cid, duration))
    }

    @Test
    fun `整片广告标签给出广告胶囊`() {
        val list = listOf(raw(action = SponsorSkipPolicy.ACTION_FULL, from = 0.0, to = 0.0))
        assertEquals(SponsorSkipPolicy.BADGE_FULL, SponsorSkipPolicy.adBadge(list, cid, duration))
    }

    @Test
    fun `整片自我推广也算广告`() {
        val list = listOf(raw(category = SponsorSkipPolicy.CAT_SELFPROMO, action = SponsorSkipPolicy.ACTION_FULL, from = 0.0, to = 0.0))
        assertEquals(SponsorSkipPolicy.BADGE_FULL, SponsorSkipPolicy.adBadge(list, cid, duration))
    }

    @Test
    fun `整片但类别不是恰饭类就不算广告`() {
        val list = listOf(raw(category = SponsorSkipPolicy.CAT_FILLER, action = SponsorSkipPolicy.ACTION_FULL, from = 0.0, to = 0.0))
        assertEquals(SponsorSkipPolicy.BADGE_NONE, SponsorSkipPolicy.adBadge(list, cid, duration))
    }

    @Test
    fun `有广告片段给出含广告`() {
        val list = listOf(raw(from = 100.0, to = 130.0))
        assertEquals(SponsorSkipPolicy.BADGE_PARTIAL, SponsorSkipPolicy.adBadge(list, cid, duration))
    }

    @Test
    fun `只有片头不算广告`() {
        val list = listOf(
            raw(category = SponsorSkipPolicy.CAT_INTRO, from = 0.0, to = 20.0),
            raw(category = SponsorSkipPolicy.CAT_INTERACTION, from = 40.0, to = 50.0),
        )
        assertEquals(SponsorSkipPolicy.BADGE_NONE, SponsorSkipPolicy.adBadge(list, cid, duration))
    }

    @Test
    fun `别的分P的广告不挂在这条上`() {
        val list = listOf(raw(from = 100.0, to = 130.0, cid = cid + 1))
        assertEquals(SponsorSkipPolicy.BADGE_NONE, SponsorSkipPolicy.adBadge(list, cid, duration))
    }

    @Test
    fun `换源前的旧广告标注丢掉`() {
        val list = listOf(raw(from = 100.0, to = 130.0, videoDuration = 300.0))
        assertEquals(SponsorSkipPolicy.BADGE_NONE, SponsorSkipPolicy.adBadge(list, cid, duration))
    }

    @Test
    fun `总时长未知时不做版本校验`() {
        val list = listOf(raw(from = 100.0, to = 130.0, videoDuration = 300.0))
        assertEquals(SponsorSkipPolicy.BADGE_PARTIAL, SponsorSkipPolicy.adBadge(list, 0L, 0.0))
    }
}
