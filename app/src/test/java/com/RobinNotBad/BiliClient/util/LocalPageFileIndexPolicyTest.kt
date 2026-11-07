package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 锁住 [LocalPageFileIndexPolicy] 的「页 → 文件下标」映射。
 *
 * 背景（调研清单第 7 条，B2）：`LocalPageChooseActivity` 删页时对 `videoFileList` 也做
 * `removeAt(position)`，而 DASH 合并失败的页（`video.mp4` + `audio.m4a` 同时存在）在扫描时
 * 占两个下标 —— 删中间页只摘掉视频、把音频留在列表里顶替下一页的位置，删完再播放就是错的文件。
 */
class LocalPageFileIndexPolicyTest {

    // 真实扫描产出的形状：P1 单文件、P2 合并失败（双文件）、P3 单文件
    private val dualFileList = listOf(
        "/sdcard/BiliCache/合集/P1/video.mp4",
        "/sdcard/BiliCache/合集/P2/video.mp4",
        "/sdcard/BiliCache/合集/P2/audio.m4a",
        "/sdcard/BiliCache/合集/P3/video.mp4"
    )

    @Test
    fun `父目录名兼容正斜杠与反斜杠`() {
        assertEquals("P2", LocalPageFileIndexPolicy.parentDirName("/a/b/P2/video.mp4"))
        assertEquals("P2", LocalPageFileIndexPolicy.parentDirName("D:\\cache\\P2\\video.mp4"))
        assertEquals("P2", LocalPageFileIndexPolicy.parentDirName("D:/cache/P2/audio.m4a"))
        assertNull(LocalPageFileIndexPolicy.parentDirName("video.mp4"))
        assertNull(LocalPageFileIndexPolicy.parentDirName(""))
        assertNull(LocalPageFileIndexPolicy.parentDirName(null))
    }

    @Test
    fun `双文件页返回两个下标`() {
        assertEquals(listOf(1, 2), LocalPageFileIndexPolicy.indicesOfPage(dualFileList, "P2"))
    }

    @Test
    fun `单文件页只返回自己的下标`() {
        assertEquals(listOf(0), LocalPageFileIndexPolicy.indicesOfPage(dualFileList, "P1"))
        assertEquals(listOf(3), LocalPageFileIndexPolicy.indicesOfPage(dualFileList, "P3"))
    }

    @Test
    fun `找不到的页返回空，不误删别人的文件`() {
        assertEquals(emptyList<Int>(), LocalPageFileIndexPolicy.indicesOfPage(dualFileList, "P9"))
        assertEquals(emptyList<Int>(), LocalPageFileIndexPolicy.indicesOfPage(dualFileList, null))
        assertEquals(emptyList<Int>(), LocalPageFileIndexPolicy.indicesOfPage(emptyList(), "P1"))
    }

    @Test
    fun `删除顺序必须从大到小`() {
        // 先删下标 1 再删 2 的话，第二个 removeAt(2) 删的是原下标 3 —— 正是旧实现的错位方式
        assertEquals(listOf(2, 1), LocalPageFileIndexPolicy.removalOrder(listOf(1, 2)))
        assertEquals(listOf(3, 0), LocalPageFileIndexPolicy.removalOrder(listOf(0, 3)))
        assertEquals(emptyList<Int>(), LocalPageFileIndexPolicy.removalOrder(emptyList()))
    }

    @Test
    fun `兜底只在一页一项时给下标，混合长度一律不动`() {
        assertEquals(2, LocalPageFileIndexPolicy.fallbackStartIndex(2, 3, 3))
        assertEquals(-1, LocalPageFileIndexPolicy.fallbackStartIndex(1, 3, 4))
        assertEquals(-1, LocalPageFileIndexPolicy.fallbackStartIndex(-1, 3, 3))
        assertEquals(-1, LocalPageFileIndexPolicy.fallbackStartIndex(0, 0, 0))
    }
}
