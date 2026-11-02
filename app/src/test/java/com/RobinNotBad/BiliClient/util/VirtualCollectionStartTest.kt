package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 虚拟合集起播下标（26.11.02：「播放虚拟合集时无论选哪条都播第一个」）。
 *
 * 两种错法都在这钉死：
 *  · 收藏夹合集的 aid 为 0 或列表里 aid 重复 → 只按 aid 反查会恒停在第 0 条；
 *  · 本地缓存合集筛掉了没有文件的视频 → 全量列表下标 ≠ 合集下标。
 */
class VirtualCollectionStartTest {

    @Test
    fun 收藏夹合集按点击位置决定起播项() {
        val aids = listOf(101L, 202L, 303L, 404L)
        assertEquals(0, VirtualCollectionStart.resolveOnline(0, 101L, aids))
        assertEquals(2, VirtualCollectionStart.resolveOnline(2, 303L, aids))
        assertEquals(3, VirtualCollectionStart.resolveOnline(3, 404L, aids))
    }

    @Test
    fun 收藏夹合集在aid全是零时也不退回第一条() {
        // 接口没回 id：每条 aid 都是 0。原来 `v.aid == startAid` 从第 0 条就成立，
        // 于是点第二条也播第一条——位置此时是唯一可信的信息
        val aids = listOf(0L, 0L, 0L, 0L)
        assertEquals(1, VirtualCollectionStart.resolveOnline(1, 0L, aids))
        assertEquals(3, VirtualCollectionStart.resolveOnline(3, 0L, aids))
    }

    @Test
    fun 收藏夹合集在aid重复时按点击位置走() {
        val aids = listOf(7L, 7L, 7L)
        assertEquals(2, VirtualCollectionStart.resolveOnline(2, 7L, aids))
    }

    @Test
    fun 收藏夹合集位置越界时退回按aid反查() {
        val aids = listOf(101L, 202L, 303L)
        // 位置越界（列表被刷新过）：按 aid 找回真正那条，而不是 0
        assertEquals(1, VirtualCollectionStart.resolveOnline(99, 202L, aids))
        assertEquals(1, VirtualCollectionStart.resolveOnline(-1, 202L, aids))
    }

    @Test
    fun 收藏夹合集定不下来时返回负一而不是零() {
        val aids = listOf(101L, 202L)
        // 越界又没有 aid：不能猜，交给调用方提示
        assertEquals(-1, VirtualCollectionStart.resolveOnline(99, 0L, aids))
        // 位置与 aid 对不上（换过列表），aid 也找不到
        assertEquals(-1, VirtualCollectionStart.resolveOnline(0, 999L, aids))
        // 空列表
        assertEquals(-1, VirtualCollectionStart.resolveOnline(0, 101L, emptyList()))
    }

    @Test
    fun 收藏夹合集位置与aid矛盾时以aid为准() {
        val aids = listOf(101L, 202L, 303L)
        // 位置 0 说 aid=101，传进来的却是 303（列表变过）→ 按 aid 找回第 2 条
        assertEquals(2, VirtualCollectionStart.resolveOnline(0, 303L, aids))
    }

    @Test
    fun 本地合集要在筛掉不可播条目后换算下标() {
        // 全量列表里下标 5/7/9 属于这个文件夹，其中 7 没有文件被筛掉 → 合集是 [5, 9]
        val collectionFullIndices = listOf(5, 9)
        assertEquals(0, VirtualCollectionStart.resolveLocal(5, collectionFullIndices))
        assertEquals(1, VirtualCollectionStart.resolveLocal(9, collectionFullIndices))
        // 点的那条没有可播文件（7）、不在本文件夹（6）、或列表为空
        assertEquals(-1, VirtualCollectionStart.resolveLocal(7, collectionFullIndices))
        assertEquals(-1, VirtualCollectionStart.resolveLocal(6, collectionFullIndices))
        assertEquals(-1, VirtualCollectionStart.resolveLocal(5, emptyList()))
        assertEquals(-1, VirtualCollectionStart.resolveLocal(-1, collectionFullIndices))
    }

    @Test
    fun 本地合集里全局下标不能用着玩() {
        // 回归：folderVideos 是过滤后的列表，直接拿全量下标当合集下标会指到别的视频上
        val collectionFullIndices = listOf(3, 4, 5)
        assertEquals(2, VirtualCollectionStart.resolveLocal(5, collectionFullIndices))
        assertEquals(-1, VirtualCollectionStart.resolveLocal(2, collectionFullIndices))
    }
}
