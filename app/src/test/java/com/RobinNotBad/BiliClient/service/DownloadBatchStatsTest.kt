package com.RobinNotBad.BiliClient.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 下载批次统计与速度格式化的纯逻辑测试（E2 拆分 DownloadService 第 1 步补测）。
 *
 * 这批逻辑原本就没在 DownloadService 里，但没有单测覆盖：
 * 总进度算错只会让进度条停在半路（不报错、不影响文件），速度算错只会显示奇怪数字，
 * 都属于"没人报 bug 但用户看得见"的问题，所以在这里钉住语义。
 *
 * 改造后**总进度按字节算**（原来是按任务个数），见 [DownloadBatchStats] 类注释。
 * 速度采样器已搬到 `service.download.SpeedSampler`，测试在 SpeedSamplerTest。
 */
class DownloadBatchStatsTest {

    private companion object {
        const val MB = 1024L * 1024L
    }

    // ---------- DownloadBatchStats ----------

    @Test
    fun overallProgress_没有任何字节时返回0() {
        val stats = DownloadBatchStats()
        assertEquals(0f, stats.overallProgress(0L, 0L, 0), 0f)
        assertEquals("没有分母时不能算比例", 0f, stats.overallProgress(MB, 0L, 0), 0f)
    }

    @Test
    fun overallProgress_完成与失败都算终态() {
        val stats = DownloadBatchStats()
        stats.recordSuccess(100L)
        stats.recordFailure()
        assertEquals("完成的任务已 100% 下完，进度就是满的",
            1f, stats.overallProgress(0L, 0L, 0), 1e-6f)
        assertEquals(1, stats.completed)
        assertEquals(1, stats.failed)
    }

    @Test
    fun overallProgress_按字节而不是按任务个数() {
        // 已下完一个 100MB 的任务，另一个 100MB 的任务下了一半
        val stats = DownloadBatchStats()
        stats.recordSuccess(100 * MB)
        // 改造前：两个任务各算一份 → 1.0/2 = 0.5；按字节：(100+50)/(100+100) = 0.75
        assertEquals(0.75f, stats.overallProgress(50 * MB, 100 * MB, 0), 1e-6f)
    }

    @Test
    fun overallProgress_小文件不会拖住大文件() {
        // 一个 500MB 的视频下完，一个 3MB 的音频刚开始（0 字节）
        val stats = DownloadBatchStats()
        stats.recordSuccess(500 * MB)
        val progress = stats.overallProgress(0L, 3 * MB, 0)
        assertEquals("500/(500+3) ≈ 0.994", 500.0 / 503.0, progress.toDouble(), 1e-6)
    }

    @Test
    fun overallProgress_排队任务用已完成任务的平均大小估分母() {
        // 完成一个 1000 字节的任务、失败一个 → 平均 500 字节
        val stats = DownloadBatchStats()
        stats.recordSuccess(1000L)
        stats.recordFailure()
        // 分母 = 已完成 1000 + 排队 2 × 500 = 2000，分子 = 1000
        assertEquals(0.5f, stats.overallProgress(0L, 0L, 2), 1e-6f)
    }

    @Test
    fun overallProgress_没有已完成任务时不给排队任务凭空造分母() {
        val stats = DownloadBatchStats()
        assertEquals("一个都还没下完，没有平均大小可用",
            0f, stats.overallProgress(0L, 0L, 3), 0f)
    }

    @Test
    fun overallProgress_结果恒在0到1之间() {
        val stats = DownloadBatchStats()
        stats.recordSuccess(100L)
        assertEquals("已下字节远超总字节（探测偏小）也要夹住",
            1f, stats.overallProgress(999L, 100L, 0), 1e-6f)
        val fresh = DownloadBatchStats()
        assertEquals("负数已下字节按 0 处理", 0f, fresh.overallProgress(-5L, 100L, 0), 1e-6f)
    }

    @Test
    fun reset_清空完成与失败计数() {
        val stats = DownloadBatchStats()
        stats.recordSuccess(100L)
        stats.recordSuccess(200L)
        stats.recordFailure()
        stats.reset()
        assertEquals(0, stats.completed)
        assertEquals(0, stats.failed)
        assertEquals("清空后字节也归零", 0f, stats.overallProgress(0L, 0L, 0), 0f)
    }

    // ---------- formatDownloadSpeed ----------

    @Test
    fun formatDownloadSpeed_按量级切换单位() {
        assertEquals("0 B/s", formatDownloadSpeed(0L))
        assertEquals("1 B/s", formatDownloadSpeed(1L))
        assertEquals("1023 B/s", formatDownloadSpeed(1023L))
        assertEquals("1.0 KB/s", formatDownloadSpeed(1024L))
        assertEquals("1024.0 KB/s", formatDownloadSpeed(1048575L))
        assertEquals("1.0 MB/s", formatDownloadSpeed(1048576L))
        assertEquals("2.5 MB/s", formatDownloadSpeed(2621440L))
    }

    @Test
    fun formatDownloadSpeed_百万级速度不出现科学计数法() {
        assertEquals("1000.0 MB/s", formatDownloadSpeed(1048576000L))
    }
}
