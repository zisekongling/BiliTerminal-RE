package com.RobinNotBad.BiliClient.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 缓存文件夹连播「根层一项」的归一逻辑测试（26.11.13）。
 *
 * 这条逻辑的价值在于**把扁平的 `videoFileList` 还原成"每页一组文件"**：
 * `LocalVideo.videoFileList` 里 DASH 未合并的页会同时留下 `video.mp4` 与 `audio.m4a`，
 * 于是「页下标」和「文件下标」不等长。旧实现（26.11.13 前）干脆只取第一个 mp4，
 * 于是文件夹里的多 P 缓存永远只播 P1（用户报的问题）。归一错了不会崩，
 * 只是点 P3 播成 P2 的声音——真机上极难一眼看出，所以在这里钉死。
 */
class LocalPlaylistEntryTest {

    private val root = "/storage/emulated/0/Android/data/app/video"

    private fun page(pageName: String, vararg files: String): List<String> =
        files.map { "$root/$pageName/$it" }

    private fun build(
        title: String = "视频",
        pageList: List<String>? = null,
        videoFileList: List<String>? = null,
        danmakuFileList: List<String>? = null,
        cover: String? = null
    ) = LocalPlaylistEntry.build(title, pageList, videoFileList, danmakuFileList, cover)

    @Test
    fun 多P且个别页DASH双文件_每页各归各的文件不串页() {
        val files = ArrayList<String>()
        files += page("P1", "video.mp4")
        files += page("P2", "video.mp4")
        files += page("P2", "audio.m4a")
        files += page("P3", "video.mp4")

        val entry = build(
            title = "三集连播",
            pageList = listOf("P1", "P2", "P3"),
            videoFileList = files,
            danmakuFileList = listOf("d1", "d2", "d3")
        )!!

        assertEquals(3, entry.pageCount())
        assertEquals(listOf("P1", "P2", "P3"), entry.pageNames)

        // 三页的主文件都是各自的 video.mp4，不能因为 P2 多了一项就把 P3 的文件挤过来
        assertEquals(page("P1", "video.mp4")[0], entry.videoFiles[0])
        assertEquals(page("P2", "video.mp4")[0], entry.videoFiles[1])
        assertEquals(page("P3", "video.mp4")[0], entry.videoFiles[2])

        // 只有 P2 是 DASH 未合并，它的音轨必须跟着 P2 走
        assertEquals("", entry.audioFiles[0])
        assertEquals(page("P2", "audio.m4a")[0], entry.audioFiles[1])
        assertEquals("", entry.audioFiles[2])

        // 弹幕是「一页一项」，与页下标对齐
        assertEquals(listOf("d1", "d2", "d3"), entry.danmakuFiles)

        val media = entry.mediaAt(1)!!
        assertEquals(page("P2", "video.mp4")[0], media.videoFile)
        assertEquals(page("P2", "audio.m4a")[0], media.audioFile)
        assertEquals("d2", media.danmakuFile)

        assertNull(entry.mediaAt(3))
    }

    @Test
    fun 单文件视频_pageList为空时当成一页且页名取视频标题() {
        val entry = build(
            title = "单个视频",
            pageList = emptyList(),
            videoFileList = page("单个视频", "video.mp4", "audio.m4a"),
            danmakuFileList = listOf("danmaku.xml")
        )!!

        assertEquals(1, entry.pageCount())
        assertEquals(listOf("单个视频"), entry.pageNames)
        assertEquals(page("单个视频", "video.mp4")[0], entry.videoFiles[0])
        assertEquals(page("单个视频", "audio.m4a")[0], entry.audioFiles[0])
        assertEquals("danmaku.xml", entry.danmakuFiles[0])
    }

    @Test
    fun pageList为null时同样退化成一页() {
        val entry = build(
            title = "无页名",
            pageList = null,
            videoFileList = page("无页名", "video.mp4")
        )!!

        assertEquals(1, entry.pageCount())
        assertEquals(listOf("无页名"), entry.pageNames)
    }

    @Test
    fun 没有任何文件时返回null_让调用方整条跳过() {
        assertNull(build(title = "空目录", pageList = listOf("P1"), videoFileList = emptyList()))
        assertNull(build(title = "空目录", pageList = listOf("P1"), videoFileList = null))
        // 列表里只有空串（扫描到一半的数据）也当成没文件
        assertNull(build(title = "空目录", pageList = listOf("P1"), videoFileList = listOf("", "")))
    }

    @Test
    fun 目录名对不上时退化成按下标一一对应_旧缓存只存文件名也不至于整条丢() {
        val legacy = listOf("$root/video.mp4", "$root/audio.m4a")

        val entry = build(title = "旧缓存", pageList = null, videoFileList = legacy)!!

        assertEquals(1, entry.pageCount())
        assertEquals(legacy[0], entry.videoFiles[0])
        assertEquals(legacy[1], entry.audioFiles[0])
    }

    @Test
    fun 页比文件多时只保留真正能播的页_各列表仍然等长() {
        val entry = build(
            title = "半截数据",
            pageList = listOf("P1", "P2", "P3"),
            videoFileList = page("P1", "video.mp4"),
            danmakuFileList = listOf("d1", "d2", "d3")
        )!!

        assertEquals(1, entry.pageCount())
        assertEquals(1, entry.videoFiles.size)
        assertEquals(1, entry.audioFiles.size)
        assertEquals(1, entry.danmakuFiles.size)
        assertTrue(entry.isPlayable())
    }

    @Test
    fun 只有音频的页_不再把同一条m4a当外部音轨叠第二路声音() {
        val entry = build(
            title = "纯音频",
            pageList = listOf("P1"),
            videoFileList = page("P1", "audio.m4a")
        )!!

        assertEquals(page("P1", "audio.m4a")[0], entry.videoFiles[0])
        assertEquals("", entry.audioFiles[0])
    }

    @Test
    fun 封面原样带进去_没有封面时为空串() {
        assertEquals("/x/cover.jpg", build(videoFileList = page("A", "video.mp4"), cover = "/x/cover.jpg")!!.cover)
        assertEquals("", build(videoFileList = page("A", "video.mp4"), cover = null)!!.cover)
    }

    @Test
    fun 整份条目表能过Binder往返_字段一个不少() {
        // 条目表是随 Intent 走的（ArrayList<LocalPlaylistEntry>），等于要过一遍 Binder 序列化。
        // 这里用 Java 序列化近似钉住：字段漏 Serializable、或 serialVersionUID 写错崩不在这测，
        // 但「序列化后字段对不上」能立刻发现。
        val entries = arrayListOf(
            build(
                title = "第一条",
                pageList = listOf("P1", "P2"),
                videoFileList = page("P1", "video.mp4") + page("P2", "video.mp4"),
                danmakuFileList = listOf("/d1.xml", "/d2.xml"),
                cover = "/c.jpg"
            )!!,
            build(title = "第二条", videoFileList = page("A", "video.mp4"))!!
        )

        val bytes = java.io.ByteArrayOutputStream()
        java.io.ObjectOutputStream(bytes).use { it.writeObject(entries) }
        @Suppress("UNCHECKED_CAST")
        val copy = java.io.ObjectInputStream(java.io.ByteArrayInputStream(bytes.toByteArray()))
            .use { it.readObject() } as ArrayList<LocalPlaylistEntry>

        assertEquals(entries.size, copy.size)
        for (i in entries.indices) {
            val origin = entries[i]
            val back = copy[i]
            assertEquals(origin.title, back.title)
            assertEquals(origin.pageNames, back.pageNames)
            assertEquals(origin.videoFiles, back.videoFiles)
            assertEquals(origin.audioFiles, back.audioFiles)
            assertEquals(origin.danmakuFiles, back.danmakuFiles)
            assertEquals(origin.cover, back.cover)
            assertEquals(origin.pageCount(), back.pageCount())
        }
    }
}
