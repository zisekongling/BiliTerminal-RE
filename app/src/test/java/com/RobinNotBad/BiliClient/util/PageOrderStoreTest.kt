package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 缓存合集页序（反馈 #98）的纯逻辑单测。
 *
 * 只测 [PageOrderStore] 里不碰 Android 的三件事：JSON 读写、按记录排扫描结果、坏数据兜底。
 * 真正的落盘 / 扫描走 `LocalListActivity.scanVideos`，需要设备验证，见 docs/review/fix-progress.md。
 */
class PageOrderStoreTest {

    @Test
    fun parse_emptyOrNull_returnsEmpty() {
        assertEquals(emptyList<String>(), PageOrderStore.parse(null))
        assertEquals(emptyList<String>(), PageOrderStore.parse(""))
        assertEquals(emptyList<String>(), PageOrderStore.parse("   "))
    }

    @Test
    fun parse_brokenJson_returnsEmpty() {
        assertEquals(emptyList<String>(), PageOrderStore.parse("{not json"))
        assertEquals(emptyList<String>(), PageOrderStore.parse("{\"a\":1}"))
    }

    @Test
    fun serializeAndParse_roundTrip_keepsOrderAndChinese() {
        val pages = listOf("第一集 序章", "P2 中段 / 带斜杠", "P10 最终话")
        val restored = PageOrderStore.parse(PageOrderStore.serialize(pages))
        assertEquals(pages, restored)
    }

    @Test
    fun parse_emptyArray_returnsEmpty() {
        assertEquals(emptyList<String>(), PageOrderStore.parse("[]"))
    }

    @Test
    fun orderedPages_noStored_keepsScanOrder() {
        val scanned = listOf("P1", "P2", "P3")
        assertEquals(scanned, PageOrderStore.orderedPages(scanned, emptyList()))
    }

    @Test
    fun orderedPages_storedOrderWins() {
        val scanned = listOf("P3", "P1", "P2")
        val stored = listOf("P1", "P2", "P3")
        assertEquals(listOf("P1", "P2", "P3"), PageOrderStore.orderedPages(scanned, stored))
    }

    @Test
    fun orderedPages_unknownPagesAppendedInScanOrder() {
        val scanned = listOf("P3", "P9", "P1", "P2")
        val stored = listOf("P1", "P2", "P3")
        assertEquals(listOf("P1", "P2", "P3", "P9"), PageOrderStore.orderedPages(scanned, stored))
    }

    @Test
    fun orderedPages_deletedPagesAreIgnored() {
        val scanned = listOf("P2", "P4")
        val stored = listOf("P1", "P2", "P3", "P4")
        assertEquals(listOf("P2", "P4"), PageOrderStore.orderedPages(scanned, stored))
    }

    @Test
    fun orderedPages_duplicateStoredNamesOnlyCountOnce() {
        val scanned = listOf("P1", "P2", "P3")
        val stored = listOf("P2", "P2", "P1")
        assertEquals(listOf("P2", "P1", "P3"), PageOrderStore.orderedPages(scanned, stored))
    }

    @Test
    fun orderedPages_singlePage_unchanged() {
        val scanned = listOf("P1")
        assertEquals(scanned, PageOrderStore.orderedPages(scanned, listOf("P2", "P1")))
    }

    @Test
    fun orderedPages_neverDropsOrDuplicatesScannedPages() {
        val scanned = listOf("A", "B", "C", "D")
        val stored = listOf("D", "B")
        val ordered = PageOrderStore.orderedPages(scanned, stored)
        assertEquals(scanned.size, ordered.size)
        assertTrue(ordered.toSet() == scanned.toSet())
    }
}
