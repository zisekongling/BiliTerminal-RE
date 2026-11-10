package com.RobinNotBad.BiliClient.service.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DownloadSectionBytes] 的纯逻辑测试。
 *
 * 这是"进度准不准"的核心：进度 = 真实已下字节 / 任务总字节。
 * 要钉住的语义：分段下完的文件要累加、实际字节比预算大时不能冲过 100%、
 * 回滚或重试时进度条不许往回掉。
 */
class DownloadSectionBytesTest {

    @Test
    fun 没有预算时hasBudget为false且进度无意义() {
        val bytes = DownloadSectionBytes()
        assertFalse(bytes.hasBudget)
        // 调用方在 !hasBudget 时必须走阶段权重兜底，这里只看字节统计
        val report = bytes.report(100L)
        assertEquals(100L, report.doneBytes)
        assertEquals("没有预算时总量兜底为已完成字节，避免除零", 100L, report.totalBytes)
    }

    @Test
    fun 有预算时进度等于真实字节比例() {
        val bytes = DownloadSectionBytes(1000L)
        assertTrue(bytes.hasBudget)
        val report = bytes.report(250L)
        assertEquals(250L, report.doneBytes)
        assertEquals(1000L, report.totalBytes)
        assertEquals(0.25f, report.progress, 1e-6f)
    }

    @Test
    fun 下完一个文件后累计量接上下一个文件() {
        val bytes = DownloadSectionBytes(1000L)
        bytes.report(400L)
        // 视频流下完 400 字节
        val afterFinish = bytes.finishFile(400L)
        assertEquals(400L, afterFinish.doneBytes)
        assertEquals(0.4f, afterFinish.progress, 1e-6f)
        // 音频流开始，报的是"当前文件已下 300 字节"，累计应是 700
        val report = bytes.report(300L)
        assertEquals("累计量必须含上一个文件", 700L, report.doneBytes)
        assertEquals(0.7f, report.progress, 1e-6f)
    }

    @Test
    fun 实际字节比预算大时总量跟着变大且不冲过100() {
        val bytes = DownloadSectionBytes(1000L)
        val report = bytes.finishFile(1200L)
        assertEquals(1200L, report.doneBytes)
        assertEquals("总量取 max(预算, 已完成)", 1200L, report.totalBytes)
        assertEquals(1f, report.progress, 1e-6f)
        val over = DownloadSectionBytes(1000L).report(1500L)
        assertEquals(1500L, over.totalBytes)
        assertEquals("多下的字节不能让进度超过 100%", 1f, over.progress, 1e-6f)
    }

    @Test
    fun 进度只许前进不许回退() {
        val bytes = DownloadSectionBytes(1000L)
        assertEquals(0.5f, bytes.report(500L).progress, 1e-6f)
        val rolledBack = bytes.report(100L)
        assertEquals("字节数回滚（分片重试）时进度保持不动", 0.5f, rolledBack.progress, 1e-6f)
        assertEquals("但已下字节如实反映回滚后的值", 100L, rolledBack.doneBytes)
        assertEquals("再往下走还能继续涨", 0.8f, bytes.report(800L).progress, 1e-6f)
    }

    @Test
    fun finishAll把数据部分顶到100() {
        val bytes = DownloadSectionBytes(1000L)
        bytes.finishFile(400L)
        val report = bytes.finishAll()
        assertEquals(1f, report.progress, 1e-6f)
        assertEquals("数据部分算完，总量取预算", 1000L, report.doneBytes)
        assertEquals(1000L, report.totalBytes)
    }

    @Test
    fun growBudget补登另一条流的大小() {
        val bytes = DownloadSectionBytes()
        assertFalse(bytes.hasBudget)
        bytes.growBudget(500L)
        assertTrue(bytes.hasBudget)
        assertEquals(0.2f, bytes.report(100L).progress, 1e-6f)
        bytes.growBudget(-100L)
        assertEquals("负增量忽略", 500L, bytes.report(100L).totalBytes)
    }

    @Test
    fun snapshot只读不改单调状态() {
        val bytes = DownloadSectionBytes(1000L)
        bytes.report(500L)
        val first = bytes.snapshot()
        assertEquals(0.5f, first.progress, 1e-6f)
        assertEquals(500L, first.doneBytes)
        // 快照之后进度状态没被改动：仍然能继续往前涨，也不会因为快照被"锁住"
        assertEquals(0.6f, bytes.report(600L).progress, 1e-6f)
    }
}
