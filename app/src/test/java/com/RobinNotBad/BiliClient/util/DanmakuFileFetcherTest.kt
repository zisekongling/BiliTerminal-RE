package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住 [DanmakuFileFetcher] 的「能不能落盘」判定。
 *
 * 背景（调研清单第 1 条）：4 份弹幕下载副本里有 2 份不判 HTTP 状态码，
 * B 站 CDN 对失效地址回 404/412 的 HTML 错误页被当成弹幕正文写进 `danmaku.xml`，
 * 播放器解析出空弹幕，用户看到的是「这个视频没弹幕」而下载状态仍是成功。
 * 这里锁死两条拒绝线：非 2xx、解压后正文为空；并把 2xx 的边界值都钉住，
 * 防止以后有人把判断写成 `code == 200`（204 等合法 2xx 会被误杀）。
 */
class DanmakuFileFetcherTest {

    // ==================== isAcceptableStatus ====================

    @Test
    fun `2xx 全部接受，包含上下边界`() {
        assertTrue(DanmakuFileFetcher.isAcceptableStatus(200))
        assertTrue(DanmakuFileFetcher.isAcceptableStatus(204))
        assertTrue(DanmakuFileFetcher.isAcceptableStatus(206))
        assertTrue(DanmakuFileFetcher.isAcceptableStatus(299))
    }

    @Test
    fun `非 2xx 一律拒绝`() {
        assertFalse(DanmakuFileFetcher.isAcceptableStatus(199))
        assertFalse(DanmakuFileFetcher.isAcceptableStatus(300))
        assertFalse(DanmakuFileFetcher.isAcceptableStatus(301))
        assertFalse(DanmakuFileFetcher.isAcceptableStatus(304))
        assertFalse(DanmakuFileFetcher.isAcceptableStatus(404))
        // 412 是 B 站风控/防盗链最常见的拒绝码，真实案例里就是它把错误页写进了 danmaku.xml
        assertFalse(DanmakuFileFetcher.isAcceptableStatus(412))
        assertFalse(DanmakuFileFetcher.isAcceptableStatus(500))
    }

    // ==================== describeFailure ====================

    @Test
    fun `错误页不当弹幕落盘`() {
        val errorPage = "<!DOCTYPE html><html><body>412 Precondition Failed</body></html>".toByteArray()
        assertEquals("HTTP 404", DanmakuFileFetcher.describeFailure(404, null))
        assertEquals("HTTP 412", DanmakuFileFetcher.describeFailure(412, errorPage))
        assertEquals("HTTP 500", DanmakuFileFetcher.describeFailure(500, errorPage))
    }

    @Test
    fun `2xx 但正文为空也要拒绝`() {
        assertEquals("empty danmaku body", DanmakuFileFetcher.describeFailure(200, null))
        assertEquals("empty danmaku body", DanmakuFileFetcher.describeFailure(200, ByteArray(0)))
    }

    @Test
    fun `2xx 且正文非空才放行`() {
        assertNull(DanmakuFileFetcher.describeFailure(200, "<i><d p=\"1.0,1,25\">hi</d></i>".toByteArray()))
        assertNull(DanmakuFileFetcher.describeFailure(206, byteArrayOf(1)))
    }
}
