package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.LocalPlaylistEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住 [CacheMediaPolicy]：反馈 #164「多 P 视频缓存了第 2、3 集，缓存列表里找不到，想重新缓存
 * 又说该视频已下载」的修法靠这里三条判据统一口径，所以全用字符串层面的断言（不碰文件系统）。
 *
 * 相关单测：[LocalPlaylistEntryTest]（点卡片播放按页折叠）、[CachePathResolverTest]（删除路径候选）。
 */
class CacheMediaPolicyTest {

    // ---------- pickMediaName：目录里能播的成品是谁 ----------

    @Test
    fun `合并好的视频优先于旧合并残留和音频`() {
        assertEquals(
            "video.mp4",
            CacheMediaPolicy.pickMediaName(listOf("video_merged_temp.mp4", "audio.m4a", "video.mp4"))
        )
    }

    @Test
    fun `只有旧合并残留时也认它`() {
        // DASH 合并中断留下的 video_merged_temp.mp4 内容就是视频，认它才不至于「列表里没有、重下说已下载」
        assertEquals("video_merged_temp.mp4", CacheMediaPolicy.pickMediaName(listOf("video_merged_temp.mp4")))
    }

    @Test
    fun `只有音频的缓存也算能播`() {
        assertEquals("audio.m4a", CacheMediaPolicy.pickMediaName(listOf("audio.m4a", "cover.png")))
    }

    @Test
    fun `只有封面与元数据时没有成品`() {
        assertNull(CacheMediaPolicy.pickMediaName(listOf("cover.png", ".page_order.json", ".nomedia")))
        assertNull(CacheMediaPolicy.pickMediaName(emptyList()))
        assertNull(CacheMediaPolicy.pickMediaName(null))
    }

    // ---------- isCachedFolder / isDownloading ----------

    @Test
    fun `有成品且不在下载中才算已缓存`() {
        assertTrue(CacheMediaPolicy.isCachedFolder(listOf("video.mp4")))
        assertTrue(CacheMediaPolicy.isCachedFolder(listOf("video.mp4", ".quality", "danmaku.xml")))
    }

    @Test
    fun `下载中不算已缓存`() {
        val names = listOf(".DOWNLOADING", "video.mp4")
        assertTrue(CacheMediaPolicy.isDownloading(names))
        assertFalse(CacheMediaPolicy.isCachedFolder(names))
    }

    @Test
    fun `只有残留的空壳目录既不算已缓存也不算下载中`() {
        // 反馈 #164 里「重下提示已下载」骗人的正是这一种：目录在、但里面一个成品都没有
        val names = listOf("cover.png", ".page_order.json")
        assertFalse(CacheMediaPolicy.isDownloading(names))
        assertFalse(CacheMediaPolicy.isCachedFolder(names))
    }

    // ---------- pagesWithMedia：分 P 目录 → 真正的页 ----------

    @Test
    fun `两个分 P 目录都缓存好时得到两页`() {
        val dirs = mapOf(
            "第一集" to listOf("video.mp4", ".quality", "danmaku.xml"),
            "第二集" to listOf("video.mp4")
        )

        val pages = CacheMediaPolicy.pagesWithMedia(listOf("第一集", "第二集")) { dirs[it] }

        assertEquals(listOf("第一集", "第二集"), pages.map { it.name })
        assertEquals(listOf("video.mp4", "video.mp4"), pages.map { it.mediaName })
    }

    @Test
    fun `顺序按传进来的页序走，而不是按目录名排序`() {
        val dirs = mapOf("P2" to listOf("video.mp4"), "P1" to listOf("video.mp4"))

        val pages = CacheMediaPolicy.pagesWithMedia(listOf("P2", "P1")) { dirs[it] }

        assertEquals(listOf("P2", "P1"), pages.map { it.name })
    }

    @Test
    fun `下载中的页与空页被跳过`() {
        val dirs = mapOf(
            "P1" to listOf("video.mp4"),
            "P2" to listOf(".DOWNLOADING"),
            "P3" to listOf("cover.png"),
            "P4" to listOf("audio.m4a")
        )

        val pages = CacheMediaPolicy.pagesWithMedia(listOf("P1", "P2", "P3", "P4")) { dirs[it] }

        assertEquals(listOf("P1", "P4"), pages.map { it.name })
    }

    @Test
    fun `目录读不出来时整页跳过`() {
        val pages = CacheMediaPolicy.pagesWithMedia(listOf("P1", "P2")) { null }

        assertTrue(pages.isEmpty())
    }

    // ---------- 与播放侧的联动：2 页就是 2 页，不再被当成 DASH 双文件 ----------

    @Test
    fun `两个分 P 各一个文件折叠成两页而不是一页的 video 加 audio`() {
        // 这就是 #164 的核心：旧实现按「扁平文件数 == 2」当成 DASH 双文件，
        // 于是第 2 集永远点不到，还会被当音轨塞给第 1 集。按页目录名折叠后必须是 2 页。
        val root = "/storage/emulated/0/Android/media/com.RobinNotBad.BiliClient/某个合集"
        val entry = LocalPlaylistEntry.build(
            "某个合集",
            listOf("第一集", "第二集"),
            listOf("$root/第一集/video.mp4", "$root/第二集/video.mp4"),
            listOf("$root/第一集/danmaku.xml", "$root/第二集/danmaku.xml"),
            "$root/cover.png"
        )

        assertEquals(2, entry!!.pageCount())
        assertEquals("$root/第一集/video.mp4", entry.mediaAt(0)!!.videoFile)
        assertEquals("", entry.mediaAt(0)!!.audioFile)
        assertEquals("$root/第二集/video.mp4", entry.mediaAt(1)!!.videoFile)
        assertEquals("$root/第二集/danmaku.xml", entry.mediaAt(1)!!.danmakuFile)
    }

    @Test
    fun `单 P 视频折叠后仍是一页，直接起播`() {
        val root = "/storage/emulated/0/Android/media/com.RobinNotBad.BiliClient/单集视频"
        val entry = LocalPlaylistEntry.build(
            "单集视频",
            null,
            listOf("$root/video.mp4"),
            listOf("$root/danmaku.xml"),
            null
        )

        assertEquals(1, entry!!.pageCount())
        assertEquals("$root/video.mp4", entry.mediaAt(0)!!.videoFile)
    }

    // ---------- isStaleDownloadMarker：下好了但标记没来得及删 ----------

    @Test
    fun `成品加上下载标记算陈旧标记`() {
        // 反馈 #187：更新时进程被杀，成品已转正、.DOWNLOADING 没删，
        // 缓存页按 isDownloading 跳过整条 → 用户看到「缓存全没」。
        assertTrue(CacheMediaPolicy.isStaleDownloadMarker(listOf("video.mp4", ".DOWNLOADING")))
        assertTrue(CacheMediaPolicy.isStaleDownloadMarker(listOf("audio.m4a", ".DOWNLOADING", "cover.png")))
    }

    @Test
    fun `只有下载标记没有成品时不是陈旧标记`() {
        // 真正的下载中：没有成品，标记要留给失败收尾做残留判定
        assertFalse(CacheMediaPolicy.isStaleDownloadMarker(listOf(".DOWNLOADING", ".quality")))
        assertFalse(CacheMediaPolicy.isStaleDownloadMarker(listOf("video.mp4")))
        assertFalse(CacheMediaPolicy.isStaleDownloadMarker(null))
    }
}
