package com.RobinNotBad.BiliClient.service.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 失败任务目录的收拾判定（反馈 #136 + #250 / #252 / #253）。
 *
 * 锁死两件事：**挡住重试的标记必须删**（否则同一个视频永远进不了队列），
 * **半成品必须留**（否则用户下了几十分钟的东西白白丢掉）。
 */
class DownloadResiduePolicyTest {

    private fun deleteNames(vararg names: String): List<String> =
        DownloadResiduePolicy.cleanupPlan(names.toList()).deleteNames

    private fun restoreNames(vararg names: String): List<String> =
        DownloadResiduePolicy.cleanupPlan(names.toList()).restoreFromBackup

    @Test
    fun 下载中标记必须删掉否则详情页一直以为还在队列里() {
        // 反馈 #136 的现场：分片下到一半就断，目录里只剩这两样
        val plan = DownloadResiduePolicy.cleanupPlan(
            listOf(DownloadPathSpec.FILE_DOWNLOADING, DownloadPathSpec.FILE_VIDEO_TMP)
        )
        assertEquals(listOf(DownloadPathSpec.FILE_DOWNLOADING), plan.deleteNames)
        assertTrue(plan.restoreFromBackup.isEmpty())
    }

    @Test
    fun 半成品和续传存档一个都不能删() {
        // 反馈 #250「下到 99% 断网要从 0 开始」的现场
        val names = listOf(
            DownloadPathSpec.FILE_DOWNLOADING,
            DownloadPathSpec.FILE_VIDEO_TMP,
            DownloadPathSpec.FILE_AUDIO_TMP,
            DownloadPathSpec.FILE_VIDEO_TMP + DownloadPathSpec.SUFFIX_RESUME,
            DownloadPathSpec.FILE_AUDIO_TMP + DownloadPathSpec.SUFFIX_RESUME,
            DownloadPathSpec.FILE_QUALITY,
            "video_seg0.part",
            "video_seg0.part.done",
            ".video_meta.json"
        )
        assertEquals(listOf(DownloadPathSpec.FILE_DOWNLOADING), deleteNames(*names.toTypedArray()))
    }

    @Test
    fun 封面弹幕字幕元数据都留着() {
        val names = listOf(
            DownloadPathSpec.FILE_COVER,
            DownloadPathSpec.FILE_DANMAKU,
            DownloadPathSpec.DIR_SUBTITLES,
            ".video_meta.json"
        )
        assertTrue(DownloadResiduePolicy.cleanupPlan(names).isNoop)
    }

    @Test
    fun 用户自己放的文件不动() {
        val names = listOf(DownloadPathSpec.FILE_DOWNLOADING, "我自己放的字幕.srt", "笔记.txt")
        assertEquals(listOf(DownloadPathSpec.FILE_DOWNLOADING), deleteNames(*names.toTypedArray()))
    }

    @Test
    fun 坏合并件要删掉() {
        assertEquals(
            listOf(DownloadPathSpec.FILE_VIDEO_MERGING),
            deleteNames(DownloadPathSpec.FILE_VIDEO_MERGING, DownloadPathSpec.FILE_VIDEO_TMP)
        )
    }

    @Test
    fun 临时文件没了留下的孤儿存档要删掉() {
        // 临时文件被别处清掉后，存档留着只会在下次误命中
        assertEquals(
            listOf(DownloadPathSpec.FILE_VIDEO_TMP + DownloadPathSpec.SUFFIX_RESUME),
            deleteNames(DownloadPathSpec.FILE_VIDEO_TMP + DownloadPathSpec.SUFFIX_RESUME)
        )
    }

    @Test
    fun 成品缺失时bak要改名回去而不是删掉() {
        // 替换在两次 rename 之间被打断：video.mp4.bak 是当前唯一的成品
        assertEquals(listOf("video.mp4.bak"), restoreNames("video.mp4.bak", DownloadPathSpec.FILE_DOWNLOADING))
    }

    @Test
    fun 成品还在时陈旧bak删掉() {
        assertEquals(
            listOf("video.mp4.bak"),
            deleteNames(DownloadPathSpec.FILE_VIDEO, "video.mp4.bak")
        )
    }

    @Test
    fun 目录不存在或为空时无事可做() {
        // folder.list() 在目录不存在/不可读时返回 null
        assertTrue(DownloadResiduePolicy.cleanupPlan(null).isNoop)
        assertTrue(DownloadResiduePolicy.cleanupPlan(emptyList()).isNoop)
    }

    @Test
    fun 续传存档的名字换算正确() {
        val resume = DownloadPathSpec.resumeNameFor(DownloadPathSpec.FILE_VIDEO_TMP)
        assertEquals("video_new.mp4.resume", resume)
        assertTrue(DownloadResiduePolicy.isResumeName(resume))
        assertEquals(DownloadPathSpec.FILE_VIDEO_TMP, DownloadResiduePolicy.resumeBaseName(resume))
        assertFalse(DownloadResiduePolicy.isResumeName(DownloadPathSpec.FILE_VIDEO_TMP))
    }
}
