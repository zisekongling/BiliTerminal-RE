package com.RobinNotBad.BiliClient.service.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 失败任务残留的收拾判定（反馈 #136）。
 *
 * 这些用例对应真实后果：判成 DELETE_FOLDER 的任务目录会被删掉、视频详情页才肯重新入队；
 * 判错的代价是把用户已经下好的 `video.mp4` 删掉，所以"有成品只清临时产物"这条必须锁死。
 */
class DownloadResiduePolicyTest {

    @Test
    fun 只有下载中标记和半成品的目录判为纯残留整目录删掉() {
        // 反馈 #136 的现场：分片下到 512KB 就断，目录里只剩这些
        val names = listOf(
            DownloadPathSpec.FILE_DOWNLOADING,
            DownloadPathSpec.FILE_VIDEO_TMP
        )
        assertFalse(DownloadResiduePolicy.hasFinishedArtifact(names))
        assertEquals(DownloadResiduePolicy.Disposition.DELETE_FOLDER, DownloadResiduePolicy.disposition(names))
    }

    @Test
    fun 一个成品都没有就是需要重下() {
        // 半成品哪怕体积和成品相近，也不算下好了：本仓库不做断点续传，只有正式文件名才算成品
        val names = setOf(
            DownloadPathSpec.FILE_VIDEO_TMP,
            DownloadPathSpec.FILE_AUDIO_TMP,
            DownloadPathSpec.FILE_VIDEO_MERGING
        )
        assertFalse(DownloadResiduePolicy.hasFinishedArtifact(names))
    }

    @Test
    fun 下载自己生成的全部标记文件也算纯残留() {
        val names = listOf(
            DownloadPathSpec.FILE_DOWNLOADING,
            DownloadPathSpec.FILE_QUALITY,
            DownloadPathSpec.FILE_COVER,
            DownloadPathSpec.FILE_DANMAKU,
            DownloadPathSpec.DIR_SUBTITLES,
            DownloadPathSpec.FILE_VIDEO_TMP,
            DownloadPathSpec.FILE_AUDIO_TMP,
            DownloadPathSpec.FILE_VIDEO_MERGING,
            ".video_meta.json"
        )
        assertEquals(DownloadResiduePolicy.Disposition.DELETE_FOLDER, DownloadResiduePolicy.disposition(names))
    }

    @Test
    fun 有视频成品时只清临时产物() {
        val names = listOf(
            DownloadPathSpec.FILE_VIDEO,
            DownloadPathSpec.FILE_DOWNLOADING,
            DownloadPathSpec.FILE_VIDEO_TMP
        )
        assertTrue(DownloadResiduePolicy.hasFinishedArtifact(names))
        assertEquals(DownloadResiduePolicy.Disposition.CLEAN_TEMPS, DownloadResiduePolicy.disposition(names))
    }

    @Test
    fun 纯音频任务的audio成品也算成品() {
        val names = listOf(
            DownloadPathSpec.FILE_AUDIO,
            DownloadPathSpec.FILE_AUDIO_TMP,
            DownloadPathSpec.FILE_DOWNLOADING
        )
        assertTrue(DownloadResiduePolicy.hasFinishedArtifact(names))
        assertEquals(DownloadResiduePolicy.Disposition.CLEAN_TEMPS, DownloadResiduePolicy.disposition(names))
    }

    @Test
    fun 成品的bak备份算成品不能被当残留删掉() {
        // 替换在两次 rename 之间被打断：video.mp4.bak 是当前唯一的成品，删了就真丢视频
        val names = listOf("video.mp4.bak", DownloadPathSpec.FILE_DOWNLOADING)
        assertTrue(DownloadResiduePolicy.hasFinishedArtifact(names))
        assertEquals(DownloadResiduePolicy.Disposition.CLEAN_TEMPS, DownloadResiduePolicy.disposition(names))
    }

    @Test
    fun 混进陌生文件时保守只清临时产物() {
        // 用户自己往任务目录里放了东西，整目录删掉就是误删
        val names = listOf(
            DownloadPathSpec.FILE_DOWNLOADING,
            DownloadPathSpec.FILE_VIDEO_TMP,
            "我自己放的字幕.srt"
        )
        assertEquals(DownloadResiduePolicy.Disposition.CLEAN_TEMPS, DownloadResiduePolicy.disposition(names))
    }

    @Test
    fun 只有陌生文件时退化成只清临时产物不删目录() {
        val names = listOf("我自己放的字幕.srt", "笔记.txt")
        assertFalse(DownloadResiduePolicy.hasFinishedArtifact(names))
        assertEquals(DownloadResiduePolicy.Disposition.CLEAN_TEMPS, DownloadResiduePolicy.disposition(names))
    }

    @Test
    fun 目录不存在或为空时无事可做() {
        // folder.list() 在目录不存在/不可读时返回 null
        assertEquals(DownloadResiduePolicy.Disposition.NONE, DownloadResiduePolicy.disposition(null))
        assertEquals(DownloadResiduePolicy.Disposition.NONE, DownloadResiduePolicy.disposition(emptyList()))
        assertFalse(DownloadResiduePolicy.hasFinishedArtifact(null))
        assertFalse(DownloadResiduePolicy.hasFinishedArtifact(emptyList()))
    }
}
