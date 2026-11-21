package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.api.SponsorBlockApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 进度条标记与「跳过策略」解耦后的纯 JVM 单测（26.11.14）。
 *
 * 钉住三件事：
 * 1. [SponsorSkipPolicy.markSegments] 的类别集合来自**标记设置自己**，与三档无关
 *    （用户原话：「能只显示条子不跳、或者反过来」）；
 * 2. 除了 `buildSegments` 那套口径，这里**多滤掉 `hidden`**——上游把一条标注藏了说明本身有争议；
 * 3. 每条标记自带 [SponsorSkipPolicy.markColor]，设置页色块与进度条用同一个值。
 */
class SponsorMarkTest {

    private val cid = 43509023673L
    private val duration = 107.033

    private fun raw(
        uuid: String = "",
        category: String = SponsorSkipPolicy.CAT_SPONSOR,
        action: String = SponsorSkipPolicy.ACTION_SKIP,
        from: Double = 34.033,
        to: Double = 47.5,
        cid: Long = this.cid,
        videoDuration: Double = duration,
        hidden: Boolean = false,
    ) = SponsorBlockApi.RawSegment(
        cid = cid,
        category = category,
        actionType = action,
        fromSec = from,
        toSec = to,
        videoDuration = videoDuration,
        votes = 0,
        uuid = uuid,
        locked = false,
        hidden = hidden,
    )

    // ---------- markColor ----------

    @Test
    fun `每个类别的颜色互不相同`() {
        val colors = SponsorSkipPolicy.ALL_CATEGORIES.map { SponsorSkipPolicy.markColor(it) }
        assertEquals(colors.size, colors.toSet().size)
    }

    @Test
    fun `未知类别走兜底色`() {
        assertEquals(0xFFFAAD14.toInt(), SponsorSkipPolicy.markColor("no_such_category"))
    }

    @Test
    fun `精彩时刻与广告不同色`() {
        assertNotEquals(
            SponsorSkipPolicy.markColor(SponsorSkipPolicy.CAT_SPONSOR),
            SponsorSkipPolicy.markColor(SponsorSkipPolicy.CAT_POI_HIGHLIGHT)
        )
    }

    // ---------- markSegments：解耦 ----------

    @Test
    fun `标记集合为空时什么都不画`() {
        val kept = SponsorSkipPolicy.markSegments(
            listOf(raw()), emptySet(), cid, duration
        )
        assertTrue(kept.isEmpty())
    }

    @Test
    fun `勾了但三档全关也能拿到条子`() {
        // 这里刻意不涉及任何档位：markSegments 根本不知道三档是什么
        val kept = SponsorSkipPolicy.markSegments(
            listOf(raw()), setOf(SponsorSkipPolicy.CAT_SPONSOR), cid, duration
        )
        assertEquals(1, kept.size)
    }

    @Test
    fun `标记里没勾的类别不画`() {
        val kept = SponsorSkipPolicy.markSegments(
            listOf(
                raw(category = SponsorSkipPolicy.CAT_SPONSOR),
                raw(category = SponsorSkipPolicy.CAT_INTRO, from = 0.0, to = 8.0),
            ),
            setOf(SponsorSkipPolicy.CAT_INTRO),
            cid,
            duration,
        )
        assertEquals(1, kept.size)
        assertEquals(SponsorSkipPolicy.CAT_INTRO, kept[0].category)
    }

    @Test
    fun `每条标记自带自己类别的颜色`() {
        val kept = SponsorSkipPolicy.markSegments(
            listOf(raw(category = SponsorSkipPolicy.CAT_SELFPROMO)),
            setOf(SponsorSkipPolicy.CAT_SELFPROMO),
            cid,
            duration,
        )
        assertEquals(
            SponsorSkipPolicy.markColor(SponsorSkipPolicy.CAT_SELFPROMO),
            kept[0].color
        )
    }

    // ---------- markSegments：口径 ----------

    @Test
    fun `隐藏的标注不画`() {
        val kept = SponsorSkipPolicy.markSegments(
            listOf(raw(hidden = true)),
            setOf(SponsorSkipPolicy.CAT_SPONSOR),
            cid,
            duration,
        )
        assertTrue(kept.isEmpty())
    }

    @Test
    fun `忽略集里的 UUID 不画`() {
        val kept = SponsorSkipPolicy.markSegments(
            listOf(raw(uuid = "aaa"), raw(uuid = "bbb", from = 80.0, to = 90.0)),
            setOf(SponsorSkipPolicy.CAT_SPONSOR),
            cid,
            duration,
            setOf("aaa"),
        )
        assertEquals(1, kept.size)
        assertEquals(80.0, kept[0].segment.fromSec, 0.001)
    }

    @Test
    fun `非跳过型标注不画`() {
        val kept = SponsorSkipPolicy.markSegments(
            listOf(
                raw(action = SponsorSkipPolicy.ACTION_POI, to = 34.033),
                raw(action = SponsorSkipPolicy.ACTION_FULL, from = 0.0, to = 0.0),
            ),
            setOf(SponsorSkipPolicy.CAT_SPONSOR),
            cid,
            duration,
        )
        assertTrue(kept.isEmpty())
    }

    @Test
    fun `别的分 P 的标注不画`() {
        val kept = SponsorSkipPolicy.markSegments(
            listOf(raw(cid = 999L)),
            setOf(SponsorSkipPolicy.CAT_SPONSOR),
            cid,
            duration,
        )
        assertTrue(kept.isEmpty())
    }

    @Test
    fun `零长度片段不画`() {
        val kept = SponsorSkipPolicy.markSegments(
            listOf(raw(from = 30.0, to = 30.0)),
            setOf(SponsorSkipPolicy.CAT_SPONSOR),
            cid,
            duration,
        )
        assertTrue(kept.isEmpty())
    }

    @Test
    fun `换源前的旧标注不画`() {
        val kept = SponsorSkipPolicy.markSegments(
            listOf(raw(videoDuration = 300.0)),
            setOf(SponsorSkipPolicy.CAT_SPONSOR),
            cid,
            duration,
        )
        assertTrue(kept.isEmpty())
    }

    @Test
    fun `总时长未知时不校验版本`() {
        val kept = SponsorSkipPolicy.markSegments(
            listOf(raw(videoDuration = 300.0)),
            setOf(SponsorSkipPolicy.CAT_SPONSOR),
            cid,
            0.0,
        )
        assertEquals(1, kept.size)
    }

    @Test
    fun `按起点排序`() {
        val kept = SponsorSkipPolicy.markSegments(
            listOf(
                raw(from = 60.0, to = 70.0),
                raw(from = 10.0, to = 20.0),
            ),
            setOf(SponsorSkipPolicy.CAT_SPONSOR),
            cid,
            duration,
        )
        assertEquals(2, kept.size)
        assertEquals(10.0, kept[0].segment.fromSec, 0.001)
    }
}
