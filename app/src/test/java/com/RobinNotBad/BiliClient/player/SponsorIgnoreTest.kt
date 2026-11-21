package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.api.SponsorBlockApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「不准」忽略集与整片标签的纯 JVM 单测（26.11.14）。
 *
 * 钉住三件容易悄悄错掉的事：
 * 1. 忽略**按 UUID** 生效，且只吃掉那一条——同一类别的其他片段照样跳；
 * 2. 上游没给 UUID 的老数据（`uuid` 为空）**不能**被忽略集连带吞掉，否则整张表会空掉；
 * 3. `full`（整片标签）永远不进片段表，但能被 [SponsorSkipPolicy.fullVideoCategories] 认出来。
 */
class SponsorIgnoreTest {

    private val cid = 36656057057L
    private val duration = 600.0

    private fun raw(
        uuid: String = "",
        category: String = SponsorSkipPolicy.CAT_SPONSOR,
        action: String = SponsorSkipPolicy.ACTION_SKIP,
        from: Double = 10.0,
        to: Double = 40.0,
        cid: Long = this.cid,
        videoDuration: Double = duration,
    ) = SponsorBlockApi.RawSegment(cid, category, action, from, to, videoDuration, 0, uuid)

    // ---------- buildSegments 的忽略集 ----------

    @Test
    fun `忽略集按 UUID 只吃掉那一条`() {
        val list = listOf(
            raw(uuid = "aaa", from = 10.0, to = 40.0),
            raw(uuid = "bbb", from = 100.0, to = 130.0),
        )
        val kept = SponsorSkipPolicy.buildSegments(
            list, setOf(SponsorSkipPolicy.CAT_SPONSOR), cid, duration, setOf("aaa")
        )
        assertEquals(1, kept.size)
        assertEquals(100.0, kept[0].fromSec, 0.001)
    }

    @Test
    fun `忽略集不影响没有 UUID 的老数据`() {
        val list = listOf(raw(uuid = "", from = 10.0, to = 40.0))
        val kept = SponsorSkipPolicy.buildSegments(
            list, setOf(SponsorSkipPolicy.CAT_SPONSOR), cid, duration, setOf("aaa")
        )
        assertEquals("uuid 为空时必须放行，宁可跳错也别把整张表清空", 1, kept.size)
    }

    @Test
    fun `空忽略集与不传参等价`() {
        val list = listOf(raw(uuid = "aaa"), raw(uuid = "bbb", from = 100.0, to = 130.0))
        val withDefault = SponsorSkipPolicy.buildSegments(list, SponsorSkipPolicy.LEVEL_ALL, cid, duration)
        val withEmpty = SponsorSkipPolicy.buildSegments(
            list, SponsorSkipPolicy.LEVEL_ALL, cid, duration, emptySet()
        )
        assertEquals(withDefault, withEmpty)
    }

    // ---------- 整片标签 ----------

    @Test
    fun `整片标签不进片段表`() {
        val list = listOf(
            raw(uuid = "full1", action = SponsorSkipPolicy.ACTION_FULL, from = 0.0, to = 0.0),
        )
        assertTrue(
            "full 的区间是 [0,0]，不该被当成可跳片段",
            SponsorSkipPolicy.buildSegments(list, SponsorSkipPolicy.LEVEL_ALL, cid, duration).isEmpty()
        )
    }

    @Test
    fun `整片标签能被单独认出来且不看档位`() {
        val list = listOf(
            raw(uuid = "full1", category = SponsorSkipPolicy.CAT_SPONSOR, action = SponsorSkipPolicy.ACTION_FULL, from = 0.0, to = 0.0),
        )
        assertEquals(
            listOf(SponsorSkipPolicy.CAT_SPONSOR),
            SponsorSkipPolicy.fullVideoCategories(list, cid, duration)
        )
    }

    @Test
    fun `整片标签按类别表顺序去重`() {
        val list = listOf(
            raw(uuid = "f1", category = SponsorSkipPolicy.CAT_INTERACTION, action = SponsorSkipPolicy.ACTION_FULL, from = 0.0, to = 0.0),
            raw(uuid = "f2", category = SponsorSkipPolicy.CAT_SPONSOR, action = SponsorSkipPolicy.ACTION_FULL, from = 0.0, to = 0.0),
            raw(uuid = "f3", category = SponsorSkipPolicy.CAT_SPONSOR, action = SponsorSkipPolicy.ACTION_FULL, from = 0.0, to = 0.0),
        )
        val expected = SponsorSkipPolicy.ALL_CATEGORIES.filter {
            it == SponsorSkipPolicy.CAT_SPONSOR || it == SponsorSkipPolicy.CAT_INTERACTION
        }
        assertEquals(expected, SponsorSkipPolicy.fullVideoCategories(list, cid, duration))
    }

    @Test
    fun `整片标签也要过 cid 与时长校验`() {
        val otherCid = raw(
            uuid = "f1", action = SponsorSkipPolicy.ACTION_FULL, from = 0.0, to = 0.0, cid = cid + 1
        )
        assertTrue(
            "别的分 P 的整片标签不能提示到本P",
            SponsorSkipPolicy.fullVideoCategories(listOf(otherCid), cid, duration).isEmpty()
        )
        val otherDuration = raw(
            uuid = "f2", action = SponsorSkipPolicy.ACTION_FULL, from = 0.0, to = 0.0, videoDuration = duration + 120
        )
        assertTrue(
            "时长对不上（换源前）的标签同样要丢掉",
            SponsorSkipPolicy.fullVideoCategories(listOf(otherDuration), cid, duration).isEmpty()
        )
    }

    // ---------- 忽略集存档（纯函数） ----------

    @Test
    fun `解析忽略集时脏值一律当没有`() {
        assertTrue(SponsorSkipPrefs.parseIgnored(null).isEmpty())
        assertTrue(SponsorSkipPrefs.parseIgnored("").isEmpty())
        assertTrue(SponsorSkipPrefs.parseIgnored("null").isEmpty())
        assertEquals(
            setOf("a", "b", "c"),
            SponsorSkipPrefs.parseIgnored(" a , ,b,,c ,")
        )
    }

    @Test
    fun `追加忽略集时去重且保留最早那条`() {
        assertEquals("u1", SponsorSkipPrefs.addIgnored(null, "u1", 100))
        assertEquals("u1,u2", SponsorSkipPrefs.addIgnored("u1", "u2", 100))
        assertEquals("u1,u2", SponsorSkipPrefs.addIgnored("u1,u2", "u1", 100))
        assertEquals("已存在的不该被挪到末尾", "u1,u2", SponsorSkipPrefs.addIgnored("u1,u2", "u1", 100))
    }

    @Test
    fun `追加忽略集时超过上限丢最早的`() {
        assertEquals("u3,u4", SponsorSkipPrefs.addIgnored("u1,u2,u3", "u4", 2))
        assertEquals("u2,u3", SponsorSkipPrefs.addIgnored("u1,u2", "u3", 2))
    }

    @Test
    fun `空 UUID 不写进忽略集`() {
        assertEquals("", SponsorSkipPrefs.addIgnored(null, "", 100))
        assertEquals("u1", SponsorSkipPrefs.addIgnored("u1", "", 100))
    }
}
