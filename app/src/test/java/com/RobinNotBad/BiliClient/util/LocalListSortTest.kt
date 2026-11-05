package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 缓存页（LocalListActivity / CacheListAdapter）手动排序 + 搜索纯逻辑的单测。
 *
 * 26.11.05 起排序从「七档预设」改成「用户自己拖」：显示顺序只由持久化的标题序决定，
 * 所以这里测的是「给定持久化顺序，怎么把扫描下标排出来」以及「拖动后的新顺序怎么和旧记录合并」。
 * 这套逻辑特意做成不碰 Android 的纯函数，直接在 JVM 上跑，不需要 Robolectric。
 */
class LocalListSortTest {

    private fun titles(vararg values: String): List<String> = values.toList()

    private fun titleAt(titles: List<String>): (Int) -> String = { index -> titles[index] }

    // ==================== 按持久化标题序排列 ====================

    @Test
    fun `持久化顺序优先_其余按扫描序补在末尾`() {
        val scan = listOf(0, 1, 2, 3)
        val stored = titles("d", "b")

        assertEquals(
            listOf(3, 1, 0, 2),
            LocalListSort.orderByStoredTitles(scan, stored, titleAt(titles("a", "b", "c", "d")))
        )
    }

    @Test
    fun `没有持久化记录时保持扫描顺序`() {
        val scan = listOf(0, 1, 2)
        assertEquals(scan, LocalListSort.orderByStoredTitles(scan, emptyList(), titleAt(titles("a", "b", "c"))))
    }

    @Test
    fun `持久化记录里已不存在的标题被忽略`() {
        val scan = listOf(0, 1, 2)
        assertEquals(
            listOf(1, 0, 2),
            LocalListSort.orderByStoredTitles(scan, titles("删掉的", "b"), titleAt(titles("a", "b", "c")))
        )
    }

    @Test
    fun `持久化记录里重复的标题只算一次`() {
        val scan = listOf(0, 1, 2)
        assertEquals(
            listOf(1, 0, 2),
            LocalListSort.orderByStoredTitles(scan, titles("b", "b", "a"), titleAt(titles("a", "b", "c")))
        )
    }

    @Test
    fun `夹内记录漏记的视频不会被丢掉_而是排到末尾`() {
        // 真实场景：文件被手工拷进夹里 / 旧版本只写了 meta 没写 videoTitles，
        // 扫描能看到它，但顺序记录里没有——必须还能显示出来，只是排在最后。
        val scan = listOf(0, 1, 2)
        assertEquals(
            listOf(2, 0, 1),
            LocalListSort.orderByStoredTitles(scan, titles("c"), titleAt(titles("a", "b", "c")))
        )
    }

    @Test
    fun `空标题的条目仍然保留在末尾`() {
        val titles = titles("a", "", "c")
        assertEquals(
            listOf(2, 0, 1),
            LocalListSort.orderByStoredTitles(listOf(0, 1, 2), titles("c"), titleAt(titles))
        )
    }

    @Test
    fun `空列表与单条目直接返回副本`() {
        assertTrue(LocalListSort.orderByStoredTitles(emptyList(), titles("a")) { "" }.isEmpty())
        assertEquals(
            listOf(7),
            LocalListSort.orderByStoredTitles(listOf(7), titles("a")) { "a" }
        )
    }

    @Test
    fun `排序不会改动传入的下标表`() {
        val scan = ArrayList(listOf(0, 1, 2))
        LocalListSort.orderByStoredTitles(scan, titles("c"), titleAt(titles("a", "b", "c")))
        assertEquals(listOf(0, 1, 2), scan)
    }

    // ==================== 拖动后的新旧顺序合并 ====================

    @Test
    fun `合并时新顺序在前_旧顺序里剩下的按原相对顺序接在后面`() {
        // 拖动只覆盖当前显示出来的条目；旧记录里暂时不显示的（搜索过滤 / 漏记）不能丢，位置也要留着
        assertEquals(
            titles("c", "a", "b", "d"),
            LocalListSort.mergeOrder(titles("c", "a"), titles("a", "b", "c", "d"))
        )
    }

    @Test
    fun `合并去重且过滤空标题`() {
        assertEquals(
            titles("a", "b"),
            LocalListSort.mergeOrder(titles("a", "a", ""), titles("b", "a", ""))
        )
    }

    @Test
    fun `旧记录为空时直接采用新顺序`() {
        assertEquals(titles("b", "a"), LocalListSort.mergeOrder(titles("b", "a"), emptyList()))
    }

    @Test
    fun `新顺序为空时保留旧顺序`() {
        assertEquals(titles("a", "b"), LocalListSort.mergeOrder(emptyList(), titles("a", "b")))
    }

    // ==================== 搜索 ====================

    @Test
    fun `搜索词归一化去掉首尾空白_null 当空串`() {
        assertEquals("", LocalListSort.normalizeKeyword(null))
        assertEquals("", LocalListSort.normalizeKeyword("   "))
        assertEquals("hello", LocalListSort.normalizeKeyword("  hello  "))
    }

    @Test
    fun `空搜索词匹配任何条目`() {
        assertTrue(LocalListSort.matches("任意标题", ""))
        assertTrue(LocalListSort.matches(null, ""))
    }

    @Test
    fun `搜索忽略大小写并支持中文包含`() {
        assertTrue(LocalListSort.matches("Alpha 合集", "alpha"))
        assertTrue(LocalListSort.matches("Alpha 合集", "合集"))
        assertFalse(LocalListSort.matches("Alpha", "beta"))
        assertFalse(LocalListSort.matches(null, "a"))
    }
}
