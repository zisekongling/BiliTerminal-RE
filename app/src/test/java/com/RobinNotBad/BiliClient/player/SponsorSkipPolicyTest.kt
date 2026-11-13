package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.api.SponsorBlockApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SponsorSkipPolicy] 的纯 JVM 单测（BilibiliSponsorBlock 接入）。
 *
 * 钉住的是**"脏数据不许把用户甩到片尾"那几道保险**——这些错了不会报错，只会在不该跳的时候跳：
 * 1. 档位之外（默认关闭）一段都不跳，且 `poi_highlight` 任何档位都不跳；
 * 2. 只执行 `skip`，`mute`/`full`（整段视频标签，区间是 `[0,0]`）一律不进片段表；
 * 3. 别的分 P（cid 不符）、换源前（时长对不上）的标注都要丢掉；
 * 4. 非法存档值必须回落"关闭"，不能被 coerce 成"全部类别"。
 */
class SponsorSkipPolicyTest {

    private val cid = 36656057057L
    private val duration = 600.0

    private fun raw(
        category: String = SponsorSkipPolicy.CAT_SPONSOR,
        action: String = SponsorSkipPolicy.ACTION_SKIP,
        from: Double = 10.0,
        to: Double = 40.0,
        cid: Long = this.cid,
        videoDuration: Double = duration,
    ) = SponsorBlockApi.RawSegment(cid, category, action, from, to, videoDuration, 0)

    private fun build(
        raw: List<SponsorBlockApi.RawSegment>,
        level: Int = SponsorSkipPolicy.LEVEL_ALL,
        cid: Long = this.cid,
        currentDurationSec: Double = duration,
    ) = SponsorSkipPolicy.buildSegments(raw, level, cid, currentDurationSec)

    // ---------- 档位 ----------

    @Test
    fun `默认档位关闭时一段都不跳`() {
        assertFalse(SponsorSkipPolicy.isEnabled(SponsorSkipPolicy.LEVEL_OFF))
        assertTrue(SponsorSkipPolicy.categoriesFor(SponsorSkipPolicy.LEVEL_OFF).isEmpty())
        assertTrue(
            "关闭档必须返回空表，哪怕上游给了 sponsor",
            build(listOf(raw()), SponsorSkipPolicy.LEVEL_OFF).isEmpty()
        )
    }

    @Test
    fun `档位逐级包含`() {
        val ad = SponsorSkipPolicy.categoriesFor(SponsorSkipPolicy.LEVEL_AD)
        val promo = SponsorSkipPolicy.categoriesFor(SponsorSkipPolicy.LEVEL_PROMO)
        val recommended = SponsorSkipPolicy.categoriesFor(SponsorSkipPolicy.LEVEL_RECOMMENDED)
        val all = SponsorSkipPolicy.categoriesFor(SponsorSkipPolicy.LEVEL_ALL)

        assertEquals(setOf(SponsorSkipPolicy.CAT_SPONSOR), ad)
        assertTrue(promo.containsAll(ad))
        assertTrue(recommended.containsAll(promo))
        assertTrue(all.containsAll(recommended))
        // 「仅恰饭广告」不该顺手把三连提醒也跳掉
        assertFalse(ad.contains(SponsorSkipPolicy.CAT_INTERACTION))
        assertFalse(promo.contains(SponsorSkipPolicy.CAT_INTERACTION))
        assertTrue(recommended.contains(SponsorSkipPolicy.CAT_INTERACTION))
    }

    @Test
    fun `任何档位都不跳精彩时刻`() {
        for (level in SponsorSkipPolicy.LEVEL_AD..SponsorSkipPolicy.LEVEL_ALL) {
            assertFalse(
                "档位 $level 不该包含 poi_highlight（那是跳转点，不是区间）",
                SponsorSkipPolicy.categoriesFor(level).contains(SponsorSkipPolicy.CAT_POI_HIGHLIGHT)
            )
        }
        assertTrue(
            build(listOf(raw(category = SponsorSkipPolicy.CAT_POI_HIGHLIGHT))).isEmpty()
        )
    }

    @Test
    fun `非法存档值回落到关闭`() {
        assertEquals(SponsorSkipPolicy.LEVEL_OFF, SponsorSkipPolicy.fromStored(null))
        assertEquals(SponsorSkipPolicy.LEVEL_OFF, SponsorSkipPolicy.fromStored(""))
        assertEquals(SponsorSkipPolicy.LEVEL_OFF, SponsorSkipPolicy.fromStored("abc"))
        assertEquals(SponsorSkipPolicy.LEVEL_OFF, SponsorSkipPolicy.fromStored("99"))
        assertEquals(SponsorSkipPolicy.LEVEL_OFF, SponsorSkipPolicy.fromStored("-1"))
        assertEquals(SponsorSkipPolicy.LEVEL_AD, SponsorSkipPolicy.fromStored(" 1 "))
        assertEquals(SponsorSkipPolicy.LEVEL_ALL, SponsorSkipPolicy.fromStored("4"))
    }

    // ---------- 动作类型 ----------

    @Test
    fun `只跳 skip 动作`() {
        val raws = listOf(
            raw(action = SponsorSkipPolicy.ACTION_SKIP, from = 10.0, to = 40.0),
            raw(action = "mute", from = 100.0, to = 140.0),
            raw(action = "full", from = 0.0, to = 0.0),
        )
        val segments = build(raws)
        assertEquals(1, segments.size)
        assertEquals(10.0, segments[0].fromSec, 0.001)
    }

    // ---------- 分 P / 版本 ----------

    @Test
    fun `别的一集的片段不跳`() {
        val segments = build(listOf(raw(cid = 999L)))
        assertTrue("cid 不符的标注必须丢掉", segments.isEmpty())
    }

    @Test
    fun `上游没给 cid 时放行`() {
        val segments = build(listOf(raw(cid = 0L)))
        assertEquals(1, segments.size)
    }

    @Test
    fun `换源后时长对不上就丢`() {
        // 标注是 169 秒版本，现在视频 600 秒 —— 秒数已整体错位
        assertTrue(build(listOf(raw(videoDuration = 169.0))).isEmpty())
        // 差 1.5 秒（补帧/去广告后的常见微差）仍算同一版本
        assertEquals(1, build(listOf(raw(videoDuration = 601.5))).size)
    }

    @Test
    fun `起点越过视频末尾就丢`() {
        assertTrue(build(listOf(raw(from = 700.0, to = 720.0))).isEmpty())
    }

    @Test
    fun `时长未知时不做版本校验`() {
        // video_all 在 onPrepared 之前是 0，此时别把标注全丢了
        val segments = build(listOf(raw(videoDuration = 169.0)), currentDurationSec = 0.0)
        assertEquals(1, segments.size)
    }

    // ---------- 脏区间 ----------

    @Test
    fun `脏区间丢弃`() {
        val raws = listOf(
            raw(from = 50.0, to = 50.0),                       // 空区间
            raw(from = 80.0, to = 60.0),                       // 倒挂
            raw(from = 100.0, to = 100.0 + 601.0),             // 超过 600 秒上限
        )
        assertTrue(build(raws).isEmpty())
    }

    // ---------- 交给播放页的形状 ----------

    @Test
    fun `片段带 sponsor 类型与类别名并按起点排序`() {
        val raws = listOf(
            raw(category = SponsorSkipPolicy.CAT_SELFPROMO, from = 200.0, to = 230.0),
            raw(category = SponsorSkipPolicy.CAT_SPONSOR, from = 30.0, to = 60.0),
        )
        val segments = build(raws, SponsorSkipPolicy.LEVEL_ALL)

        assertEquals(2, segments.size)
        assertEquals("按起点升序", 30.0, segments[0].fromSec, 0.001)
        assertEquals(200.0, segments[1].fromSec, 0.001)
        // type 必须与片头(1)/片尾(2)区分开，否则去重 key 会撞
        assertTrue(segments.all { it.type == SponsorSkipPolicy.TYPE_SPONSOR })
        assertEquals(SponsorSkipPolicy.CAT_SPONSOR, segments[0].content)
        assertEquals(SponsorSkipPolicy.CAT_SELFPROMO, segments[1].content)
    }

    @Test
    fun `宽档才放行的类别在窄档下不跳`() {
        val raws = listOf(raw(category = SponsorSkipPolicy.CAT_FILLER, from = 100.0, to = 130.0))
        assertTrue(build(raws, SponsorSkipPolicy.LEVEL_RECOMMENDED).isEmpty())
        assertEquals(1, build(raws, SponsorSkipPolicy.LEVEL_ALL).size)
    }

    // ---------- 自定义档（逐类别勾选） ----------

    @Test
    fun `自定义档按勾选算集合`() {
        val picked = setOf(SponsorSkipPolicy.CAT_SPONSOR, SponsorSkipPolicy.CAT_FILLER)
        val allowed = SponsorSkipPolicy.resolveCategories(SponsorSkipPolicy.LEVEL_CUSTOM, picked)
        assertEquals(picked, allowed)

        val raws = listOf(
            raw(category = SponsorSkipPolicy.CAT_SPONSOR, from = 10.0, to = 40.0),
            raw(category = SponsorSkipPolicy.CAT_FILLER, from = 100.0, to = 130.0),
            raw(category = SponsorSkipPolicy.CAT_INTERACTION, from = 200.0, to = 210.0),
        )
        val segments = SponsorSkipPolicy.buildSegments(raws, allowed, cid, duration)
        assertEquals("只跳勾了的两类", 2, segments.size)
        assertTrue(segments.none { it.content == SponsorSkipPolicy.CAT_INTERACTION })
    }

    @Test
    fun `自定义档一个都没勾时一段都不跳`() {
        val allowed = SponsorSkipPolicy.resolveCategories(SponsorSkipPolicy.LEVEL_CUSTOM, emptySet())
        assertTrue(allowed.isEmpty())
        assertTrue(SponsorSkipPolicy.buildSegments(listOf(raw()), allowed, cid, duration).isEmpty())
    }

    @Test
    fun `勾选集合里混进的精彩时刻与未知类别被清洗掉`() {
        val sanitized = SponsorSkipPolicy.sanitizeCategories(
            setOf(
                SponsorSkipPolicy.CAT_SPONSOR,
                SponsorSkipPolicy.CAT_POI_HIGHLIGHT,
                "some_future_category",
                ""
            )
        )
        assertEquals(setOf(SponsorSkipPolicy.CAT_SPONSOR), sanitized)
        // 固定档位走的重载对自定义档没有预设集合，必须返回空表而不是"全部"
        assertTrue(SponsorSkipPolicy.categoriesFor(SponsorSkipPolicy.LEVEL_CUSTOM).isEmpty())
        assertTrue(build(listOf(raw()), SponsorSkipPolicy.LEVEL_CUSTOM).isEmpty())
    }

    @Test
    fun `存档里的自定义档位能读回来`() {
        assertEquals(SponsorSkipPolicy.LEVEL_CUSTOM, SponsorSkipPolicy.fromStored("5"))
        assertTrue(SponsorSkipPolicy.isEnabled(SponsorSkipPolicy.LEVEL_CUSTOM))
        // 越界值仍然回落关闭，不能 coerce 成"最后一档"
        assertEquals(SponsorSkipPolicy.LEVEL_OFF, SponsorSkipPolicy.fromStored("6"))
    }
}
