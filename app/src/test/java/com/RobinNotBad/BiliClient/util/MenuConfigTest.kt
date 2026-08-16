package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuConfigTest {

    @Test
    fun defaultEnabled_matchesSpec() {
        assertEquals(
            listOf(
                "recommend", "short_video", "popular", "hotsearch", "search",
                "dynamic", "myspace", "message", "local", "settings"
            ),
            MenuConfig.DEFAULT_ENABLED
        )
    }

    @Test
    fun resolve_noOldData_returnsDefaults() {
        assertEquals(MenuConfig.DEFAULT_ENABLED, MenuConfig.resolveEnabledList(null, emptyMap()))
    }

    @Test
    fun resolve_validOldSort_preservesOrderAndFiltersBySwitches() {
        val oldSort = "recommend;short_video;popular;precious;ranking;hotsearch;live;timeline;search;dynamic;myspace;message;local;settings"
        val switches = mapOf(
            "popular" to true, "short_video" to true, "precious" to false,
            "ranking" to false, "live" to true, "timeline" to false
        )
        assertEquals(
            listOf(
                "recommend", "short_video", "popular", "hotsearch", "live",
                "search", "dynamic", "myspace", "message", "local", "settings"
            ),
            MenuConfig.resolveEnabledList(oldSort, switches)
        )
    }

    @Test
    fun resolve_invalidOldSort_fallsBackToCanonicalOrder() {
        assertEquals(
            MenuConfig.DEFAULT_ENABLED,
            MenuConfig.resolveEnabledList("recommend;not_a_key", emptyMap())
        )
    }

    @Test
    fun resolve_missingSwitchKeys_useDefaults() {
        val oldSort = "recommend;short_video;popular;precious;ranking;hotsearch;live;timeline;search;dynamic;myspace;message;local;settings"
        assertEquals(MenuConfig.DEFAULT_ENABLED, MenuConfig.resolveEnabledList(oldSort, emptyMap()))
    }

    @Test
    fun resolve_fixedItemsAlwaysPresent_missingFixedAppendedLast() {
        val oldSort = "recommend;short_video;popular;precious;ranking;hotsearch;live;timeline;search;dynamic;myspace;message;local"
        val switches = mapOf(
            "popular" to true, "short_video" to true, "precious" to true,
            "ranking" to true, "live" to true, "timeline" to true
        )
        val result = MenuConfig.resolveEnabledList(oldSort, switches)
        assertTrue("固定项必须全部在结果中", MenuConfig.FIXED_ITEMS.all { it in result })
        assertEquals("缺失的固定项追加到末尾", "settings", result.last())
    }

    @Test
    fun serialize_parse_roundTrip() {
        val list = listOf("recommend", "search", "local", "settings")
        assertEquals("recommend;search;local;settings", MenuConfig.serialize(list))
        assertEquals(list, MenuConfig.parse(MenuConfig.serialize(list)))
    }

    @Test
    fun parse_rejectsBlank() {
        assertNull(MenuConfig.parse(""))
        assertNull(MenuConfig.parse(null))
        assertNull(MenuConfig.parse("   "))
    }

    @Test
    fun parse_rejectsUnknownKey() {
        assertNull(MenuConfig.parse("recommend;bogus"))
    }

    @Test
    fun parse_rejectsDuplicate() {
        assertNull(MenuConfig.parse("recommend;recommend"))
    }

    @Test
    fun disabledFrom_returnsCanonicalComplement() {
        assertEquals(
            listOf("precious", "ranking", "live", "timeline"),
            MenuConfig.disabledFrom(MenuConfig.DEFAULT_ENABLED)
        )
    }

    @Test
    fun loadEnabled_existingValidValue_returnsWithoutWrite() {
        var wrote = false
        val valid = "recommend;short_video;popular;hotsearch;search;dynamic;myspace;message;local;settings"
        val result = MenuConfig.loadEnabled(
            readString = { if (it == SharedPreferencesUtil.MENU_ENABLED) valid else null },
            readBoolean = { false },
            writeString = { _, _ -> wrote = true }
        )
        assertEquals(valid.split(";"), result)
        assertEquals(false, wrote)
    }

    @Test
    fun loadEnabled_invalidValue_migratesFromOldAndWritesBack() {
        var written: String? = null
        val result = MenuConfig.loadEnabled(
            readString = { key ->
                when (key) {
                    SharedPreferencesUtil.MENU_ENABLED -> "recommend;short_video"
                    else -> null
                }
            },
            readBoolean = { null },
            writeString = { _, value -> written = value }
        )
        assertEquals(MenuConfig.DEFAULT_ENABLED, result)
        assertEquals(MenuConfig.serialize(MenuConfig.DEFAULT_ENABLED), written)
    }
}
