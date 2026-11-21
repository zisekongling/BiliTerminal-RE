package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.service.download.DownloadPathSpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CacheAudioRepair.shouldRepair] 的判定规则（反馈 #231）。
 *
 * 只测纯文件名判定：真合并要 MediaMuxer，属于设备上的事，靠 L2 人工听/看验收。
 */
class CacheAudioRepairTest {

    private fun names(vararg names: String): List<String> = names.toList()

    @Test
    fun 视频与音频同时存在才需要重封装() {
        assertTrue(CacheAudioRepair.shouldRepair(names(DownloadPathSpec.FILE_VIDEO, DownloadPathSpec.FILE_AUDIO)))
    }

    @Test
    fun 已经合并成单文件就不用动() {
        assertFalse(CacheAudioRepair.shouldRepair(names(DownloadPathSpec.FILE_VIDEO, DownloadPathSpec.FILE_COVER)))
    }

    @Test
    fun 仅音频缓存不是双文件() {
        assertFalse(CacheAudioRepair.shouldRepair(names(DownloadPathSpec.FILE_AUDIO)))
    }

    @Test
    fun 只有视频流时不需要重封装() {
        assertFalse(CacheAudioRepair.shouldRepair(names(DownloadPathSpec.FILE_VIDEO)))
    }

    @Test
    fun 下载中的目录交给下载流程收尾() {
        assertFalse(
            CacheAudioRepair.shouldRepair(
                names(DownloadPathSpec.FILE_VIDEO, DownloadPathSpec.FILE_AUDIO, DownloadPathSpec.FILE_DOWNLOADING)
            )
        )
    }

    @Test
    fun 合并临时文件不算已合并() {
        // 上次重封装被中断留下的半成品：双文件还在，仍要再试一次
        assertTrue(
            CacheAudioRepair.shouldRepair(
                names(DownloadPathSpec.FILE_VIDEO, DownloadPathSpec.FILE_AUDIO, DownloadPathSpec.FILE_VIDEO_MERGING)
            )
        )
    }

    @Test
    fun 空目录与读不出来的目录都跳过() {
        assertFalse(CacheAudioRepair.shouldRepair(null))
        assertFalse(CacheAudioRepair.shouldRepair(emptyList()))
    }
}
