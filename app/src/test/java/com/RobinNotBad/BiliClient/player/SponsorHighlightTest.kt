package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.api.SponsorBlockApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「精彩时刻」跳转点的纯 JVM 单测（26.11.14 第二轮）。
 *
 * 钉住的是几条容易把跳转点弄丢或弄错位置的规则：
 * 1. `poi` 是**点**：`from == to` 也必须有结果（[SponsorSkipPolicy.buildSegments] 那边
 *    `to > from` 的过滤会把它整条丢掉，两条路径的判据不能混用）；
 * 2. 别的分 P、换源前的旧标注一样要丢掉（cid 与时长版本校验同 `buildSegments` 口径）；
 * 3. 起点写在 `to` 上的个别标注要能兜住；
 * 4. 不看档位：关掉自动跳过的人也要能点着看。
 */
class SponsorHighlightTest {

    private val cid = 36656057057L
    private val duration = 600.0

    private fun raw(
        category: String = SponsorSkipPolicy.CAT_POI_HIGHLIGHT,
        action: String = SponsorSkipPolicy.ACTION_POI,
        from: Double = 75.0,
        to: Double = 75.0,
        cid: Long = this.cid,
        videoDuration: Double = duration,
    ) = SponsorBlockApi.RawSegment(cid, category, action, from, to, videoDuration, 0, "")

    @Test
    fun `零长度的点也要认出来`() {
        val out = SponsorSkipPolicy.highlights(listOf(raw(from = 75.0, to = 75.0)), cid, duration)
        assertEquals(1, out.size)
        assertEquals(75.0, out[0].posSec, 0.001)
        assertEquals(SponsorSkipPolicy.CAT_POI_HIGHLIGHT, out[0].category)
    }

    @Test
    fun `skip 型的区间不会被当作跳转点`() {
        val list = listOf(
            raw(action = SponsorSkipPolicy.ACTION_SKIP, from = 10.0, to = 40.0),
            raw(from = 75.0, to = 75.0),
        )
        val out = SponsorSkipPolicy.highlights(list, cid, duration)
        assertEquals(1, out.size)
        assertEquals(75.0, out[0].posSec, 0.001)
    }

    @Test
    fun `别的分P的点丢掉`() {
        val list = listOf(raw(cid = cid + 1), raw(from = 30.0, to = 30.0))
        val out = SponsorSkipPolicy.highlights(list, cid, duration)
        assertEquals(1, out.size)
        assertEquals(30.0, out[0].posSec, 0.001)
    }

    @Test
    fun `上游没给 cid 的点放行`() {
        val out = SponsorSkipPolicy.highlights(listOf(raw(cid = 0L)), cid, duration)
        assertEquals(1, out.size)
    }

    @Test
    fun `换源前的旧标注丢掉`() {
        // videoDuration 差得远 = 这份标注属于另一个版本的视频
        val out = SponsorSkipPolicy.highlights(listOf(raw(videoDuration = duration + 30)), cid, duration)
        assertTrue(out.isEmpty())
    }

    @Test
    fun `越过末尾太多的点丢掉`() {
        val out = SponsorSkipPolicy.highlights(listOf(raw(from = duration + 10, to = duration + 10)), cid, duration)
        assertTrue(out.isEmpty())
    }

    @Test
    fun `起点为0时退回终点`() {
        val out = SponsorSkipPolicy.highlights(listOf(raw(from = 0.0, to = 42.0)), cid, duration)
        assertEquals(1, out.size)
        assertEquals(42.0, out[0].posSec, 0.001)
    }

    @Test
    fun `按时间排序`() {
        val list = listOf(
            raw(from = 200.0, to = 200.0),
            raw(from = 30.0, to = 30.0),
            raw(from = 120.0, to = 120.0),
        )
        val positions = SponsorSkipPolicy.highlights(list, cid, duration).map { it.posSec }
        assertEquals(listOf(30.0, 120.0, 200.0), positions)
    }

    @Test
    fun `同一位置的重复标注只留一条`() {
        val list = listOf(raw(from = 75.0, to = 75.0), raw(from = 75.0, to = 75.0))
        assertEquals(1, SponsorSkipPolicy.highlights(list, cid, duration).size)
    }

    @Test
    fun `总时长未知时不做版本校验`() {
        val list = listOf(raw(from = 75.0, to = 75.0, videoDuration = 0.0))
        assertEquals(1, SponsorSkipPolicy.highlights(list, cid, 0.0).size)
    }

    @Test
    fun `档位关闭也能拿到跳转点`() {
        // highlights 不看档位：这里直接证明它与 effectiveCategories 无关
        val out = SponsorSkipPolicy.highlights(listOf(raw()), cid, duration)
        assertTrue(out.isNotEmpty())
        assertTrue(SponsorSkipPolicy.CAT_POI_HIGHLIGHT !in SponsorSkipPolicy.categoriesFor(SponsorSkipPolicy.LEVEL_ALL))
    }
}
