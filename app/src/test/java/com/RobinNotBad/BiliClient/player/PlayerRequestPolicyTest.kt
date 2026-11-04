package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 播放请求头判据的单测。
 *
 * 钉死两件事：
 * 1. B 站 playurl 返回的 mcdn/PCDN 直链（host 不在白名单）必须照样带 B 站 Referer——少了它 CDN 403，
 *    IJK 报 -10000，这就是 26.11.04 那个「部分视频报错」。
 * 2. 第三方片源仍然一个 Cookie 都拿不到（含它不认识的 host、空 Cookie 等边界）。
 */
class PlayerRequestPolicyTest {

    private val mcdnUrlHost = false // *.edge.mountaintoys.cn 不在 isBilibiliHost 白名单
    private val uposUrlHost = true // upos-sz-mirror.bilivideo.com 在白名单

    @Test
    fun `B站mcdn直链要带Referer但不能带Cookie`() {
        // 真实场景：aid=117436804242218, cid=42726132853，直链落在 edge.mountaintoys.cn
        assertFalse(PlayerRequestPolicy.isThirdPartySource(false, 117436804242218L, 42726132853L, mcdnUrlHost))
        val headers = PlayerRequestPolicy.buildHeaders(
            thirdParty = false,
            bilibiliHost = mcdnUrlHost,
            cookies = "SESSDATA=secret",
            thirdPartyReferer = null,
            thirdPartyUserAgent = null
        )
        assertEquals("https://www.bilibili.com/", headers["Referer"])
        assertNull(headers["Cookie"])
    }

    @Test
    fun `B站自有CDN同时带Referer与Cookie`() {
        val headers = PlayerRequestPolicy.buildHeaders(
            thirdParty = false,
            bilibiliHost = uposUrlHost,
            cookies = "SESSDATA=secret",
            thirdPartyReferer = null,
            thirdPartyUserAgent = null
        )
        assertEquals("https://www.bilibili.com/", headers["Referer"])
        assertEquals("SESSDATA=secret", headers["Cookie"])
    }

    @Test
    fun `未登录时不带空Cookie头`() {
        val headers = PlayerRequestPolicy.buildHeaders(false, true, "", null, null)
        assertEquals("https://www.bilibili.com/", headers["Referer"])
        assertNull(headers["Cookie"])
        assertNull(PlayerRequestPolicy.buildHeaders(false, true, null, null, null)["Cookie"])
        assertNull(PlayerRequestPolicy.buildHeaders(false, true, "   ", null, null)["Cookie"])
    }

    @Test
    fun `第三方片源只拿自己的Referer与UA`() {
        assertTrue(PlayerRequestPolicy.isThirdPartySource(false, 0L, 0L, mcdnUrlHost))
        val headers = PlayerRequestPolicy.buildHeaders(
            thirdParty = true,
            bilibiliHost = false,
            cookies = "SESSDATA=secret",
            thirdPartyReferer = "https://www.example-source.com/",
            thirdPartyUserAgent = "Mozilla/5.0 test"
        )
        assertEquals("https://www.example-source.com/", headers["Referer"])
        assertEquals("Mozilla/5.0 test", headers["User-Agent"])
        assertNull(headers["Cookie"])
    }

    @Test
    fun `第三方片源没配Referer时一个头都不发`() {
        val headers = PlayerRequestPolicy.buildHeaders(true, false, "SESSDATA=secret", null, null)
        assertTrue(headers.isEmpty())
        // 空串 / 纯空白不算有效配置，不能变成 `Referer: `
        assertTrue(PlayerRequestPolicy.buildHeaders(true, false, "SESSDATA=secret", "", "  ").isEmpty())
    }

    @Test
    fun `直播不算第三方片源`() {
        // 直播的 aid 可能为 0，但它来自 B 站侧入口，必须按 B 站内容发 Referer
        assertFalse(PlayerRequestPolicy.isThirdPartySource(true, 0L, 0L, mcdnUrlHost))
        val headers = PlayerRequestPolicy.buildHeaders(false, false, "SESSDATA=secret", null, null)
        assertEquals("https://www.bilibili.com/", headers["Referer"])
        assertNull(headers["Cookie"])
    }

    @Test
    fun `aid与cid缺一就不算第三方`() {
        // 只有 aid 或只有 cid 的入口仍按 B 站内容处理：宁可多发一个 Referer，也不要误判成第三方
        assertFalse(PlayerRequestPolicy.isThirdPartySource(false, 1L, 0L, false))
        assertFalse(PlayerRequestPolicy.isThirdPartySource(false, 0L, 1L, false))
    }

    @Test
    fun `负数id等同缺失`() {
        // 负数 id 在 B 站侧不存在，按"没有 aid/cid"处理：两个都缺 ⇒ 第三方
        assertTrue(PlayerRequestPolicy.isThirdPartySource(false, -1L, 0L, false))
        assertTrue(PlayerRequestPolicy.isThirdPartySource(false, 0L, -1L, false))
    }

    @Test
    fun `第三方直链若落在B站域仍按B站内容处理`() {
        // 私有功能把 B 站直链当片源传进来时（aid/cid 为 0），不能凭空把 Referer 换成第三方规则里的值
        assertFalse(PlayerRequestPolicy.isThirdPartySource(false, 0L, 0L, uposUrlHost))
    }
}
