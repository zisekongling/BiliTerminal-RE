package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 逐类别三档（26.11.14）的纯函数校验。
 *
 * 这套三档取代了原来的六档：每个类别各存一个「关闭 / 询问我 / 自动跳转」，
 * 老存档由 [SponsorSkipPolicy.migrateLevel] 一次性折算，所以这里盯两件事——
 * 折算前后「跳的类别」必须一模一样，以及写坏/手改的存档绝不能变成「自动跳转」。
 */
class SponsorModeTest {

    // ---- 存档原文 → 三档 ----

    @Test
    fun `空存档回落关闭`() {
        assertEquals(SponsorSkipPolicy.MODE_OFF, SponsorSkipPolicy.fromStoredMode(null))
        assertEquals(SponsorSkipPolicy.MODE_OFF, SponsorSkipPolicy.fromStoredMode(""))
        assertEquals(SponsorSkipPolicy.MODE_OFF, SponsorSkipPolicy.fromStoredMode("   "))
    }

    @Test
    fun `三档原样读回`() {
        assertEquals(SponsorSkipPolicy.MODE_OFF, SponsorSkipPolicy.fromStoredMode("0"))
        assertEquals(SponsorSkipPolicy.MODE_ASK, SponsorSkipPolicy.fromStoredMode("1"))
        assertEquals(SponsorSkipPolicy.MODE_AUTO, SponsorSkipPolicy.fromStoredMode("2"))
        assertEquals(SponsorSkipPolicy.MODE_ASK, SponsorSkipPolicy.fromStoredMode(" 1 "))
    }

    @Test
    fun `老六档数字与乱码一律回落关闭而不是自动`() {
        // 3/4/5 是老的「推荐 / 全部 / 自定义」，直接当三档读会越界——宁可全关，不许变成乱跳。
        assertEquals(SponsorSkipPolicy.MODE_OFF, SponsorSkipPolicy.fromStoredMode("3"))
        assertEquals(SponsorSkipPolicy.MODE_OFF, SponsorSkipPolicy.fromStoredMode("4"))
        assertEquals(SponsorSkipPolicy.MODE_OFF, SponsorSkipPolicy.fromStoredMode("5"))
        assertEquals(SponsorSkipPolicy.MODE_OFF, SponsorSkipPolicy.fromStoredMode("-1"))
        assertEquals(SponsorSkipPolicy.MODE_OFF, SponsorSkipPolicy.fromStoredMode("abc"))
    }

    // ---- 按类别取三档 ----

    @Test
    fun `没有这个类别就是关闭`() {
        val modes = mapOf(SponsorSkipPolicy.CAT_SPONSOR to SponsorSkipPolicy.MODE_AUTO)
        assertEquals(SponsorSkipPolicy.MODE_OFF, SponsorSkipPolicy.modeOf(modes, SponsorSkipPolicy.CAT_INTRO))
        assertEquals(SponsorSkipPolicy.MODE_AUTO, SponsorSkipPolicy.modeOf(modes, SponsorSkipPolicy.CAT_SPONSOR))
    }

    @Test
    fun `越界的存档值按关闭处理`() {
        val modes = mapOf(SponsorSkipPolicy.CAT_SPONSOR to 99, SponsorSkipPolicy.CAT_INTRO to -5)
        assertEquals(SponsorSkipPolicy.MODE_AUTO, SponsorSkipPolicy.modeOf(modes, SponsorSkipPolicy.CAT_SPONSOR))
        assertEquals(SponsorSkipPolicy.MODE_OFF, SponsorSkipPolicy.modeOf(modes, SponsorSkipPolicy.CAT_INTRO))
    }

    @Test
    fun `按档位取类别集合且保持固定顺序`() {
        val modes = mapOf(
            SponsorSkipPolicy.CAT_OUTRO to SponsorSkipPolicy.MODE_AUTO,
            SponsorSkipPolicy.CAT_SPONSOR to SponsorSkipPolicy.MODE_AUTO,
            SponsorSkipPolicy.CAT_INTRO to SponsorSkipPolicy.MODE_ASK,
        )
        val auto = SponsorSkipPolicy.categoriesWithMode(modes, SponsorSkipPolicy.MODE_AUTO)
        // 顺序跟 MODE_CATEGORIES 走（sponsor 在 outro 之前），不跟 map 的插入顺序走。
        assertEquals(listOf(SponsorSkipPolicy.CAT_SPONSOR, SponsorSkipPolicy.CAT_OUTRO), auto.toList())
        assertEquals(
            setOf(SponsorSkipPolicy.CAT_INTRO),
            SponsorSkipPolicy.categoriesWithMode(modes, SponsorSkipPolicy.MODE_ASK)
        )
    }

    @Test
    fun `全部关闭才算功能没开`() {
        val allOff = SponsorSkipPolicy.MODE_CATEGORIES.associateWith { SponsorSkipPolicy.MODE_OFF }
        assertFalse(SponsorSkipPolicy.isAnyModeEnabled(allOff))
        assertFalse("空表也算全关", SponsorSkipPolicy.isAnyModeEnabled(emptyMap()))

        // 「询问我」也算开着——要发请求才能问得出来。
        val asking = mapOf(SponsorSkipPolicy.CAT_SPONSOR to SponsorSkipPolicy.MODE_ASK)
        assertTrue(SponsorSkipPolicy.isAnyModeEnabled(asking))
    }

    // ---- 老六档 → 三档 ----

    @Test
    fun `关闭档折算成全关`() {
        val modes = SponsorSkipPolicy.migrateLevel(SponsorSkipPolicy.LEVEL_OFF, setOf(SponsorSkipPolicy.CAT_SPONSOR))
        // 老逻辑里「关闭」优先于勾选，勾了什么都不跳。
        assertTrue(modes.values.all { it == SponsorSkipPolicy.MODE_OFF })
    }

    @Test
    fun `仅恰饭档只折算广告`() {
        val modes = SponsorSkipPolicy.migrateLevel(SponsorSkipPolicy.LEVEL_AD, emptySet())
        assertEquals(
            setOf(SponsorSkipPolicy.CAT_SPONSOR),
            SponsorSkipPolicy.categoriesWithMode(modes, SponsorSkipPolicy.MODE_AUTO)
        )
    }

    @Test
    fun `推荐档折算成原来那五个类别`() {
        val modes = SponsorSkipPolicy.migrateLevel(SponsorSkipPolicy.LEVEL_RECOMMENDED, emptySet())
        assertEquals(
            SponsorSkipPolicy.categoriesFor(SponsorSkipPolicy.LEVEL_RECOMMENDED),
            SponsorSkipPolicy.categoriesWithMode(modes, SponsorSkipPolicy.MODE_AUTO)
        )
    }

    @Test
    fun `全部档折算成全部可选类别`() {
        val modes = SponsorSkipPolicy.migrateLevel(SponsorSkipPolicy.LEVEL_ALL, emptySet())
        assertEquals(
            SponsorSkipPolicy.MODE_CATEGORIES.toSet(),
            SponsorSkipPolicy.categoriesWithMode(modes, SponsorSkipPolicy.MODE_AUTO)
        )
    }

    @Test
    fun `自定义档折算成当时勾上的类别`() {
        val picked = setOf(SponsorSkipPolicy.CAT_SPONSOR, SponsorSkipPolicy.CAT_OUTRO)
        val modes = SponsorSkipPolicy.migrateLevel(SponsorSkipPolicy.LEVEL_CUSTOM, picked)
        assertEquals(picked, SponsorSkipPolicy.categoriesWithMode(modes, SponsorSkipPolicy.MODE_AUTO))
        // 老「自定义」勾选了 poi_highlight 也不算——它本来就不是可跳类型。
        val withPoi = SponsorSkipPolicy.migrateLevel(
            SponsorSkipPolicy.LEVEL_CUSTOM,
            setOf(SponsorSkipPolicy.CAT_POI_HIGHLIGHT)
        )
        assertTrue(withPoi.values.all { it == SponsorSkipPolicy.MODE_OFF })
    }

    @Test
    fun `折算结果覆盖全部可选类别且不含精彩时刻`() {
        val modes = SponsorSkipPolicy.migrateLevel(SponsorSkipPolicy.LEVEL_ALL, emptySet())
        assertEquals(SponsorSkipPolicy.MODE_CATEGORIES.toSet(), modes.keys)
        assertFalse("精彩时刻是跳转点，不该出现在三档里", SponsorSkipPolicy.CAT_POI_HIGHLIGHT in modes)
    }

    @Test
    fun `折算不产生询问档`() {
        // 老存档里没有「询问」这个概念，折算只出关闭与自动两种。
        val modes = SponsorSkipPolicy.migrateLevel(SponsorSkipPolicy.LEVEL_RECOMMENDED, emptySet())
        assertTrue(modes.values.all { it == SponsorSkipPolicy.MODE_OFF || it == SponsorSkipPolicy.MODE_AUTO })
    }
}
