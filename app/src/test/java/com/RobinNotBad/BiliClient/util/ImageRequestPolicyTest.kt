package com.RobinNotBad.BiliClient.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 审计 P0-1 回归测试：图片请求的会话头必须按目标域名收敛。
 *
 * 原 bug：Glide 的拦截器把 NetWorkUtil.webHeaders（含完整登录 Cookie：SESSDATA / bili_jct）
 * 全量加到每个图片请求上，站外图床（afdiancdn.com 等）也会收到，等于把账号会话交给第三方。
 *
 * 这里锁住的是 [ImageRequestPolicy] 的判定：B 站白名单域才带会话头，其余一律不带；
 * 解析不出来的一律 fail-closed（宁可少带一次头，也不要把 Cookie 发出去）。
 */
class ImageRequestPolicyTest {

    /** 与 NetWorkUtil#buildWebHeaders 同构的扁平快照（key, value, key, value…）。 */
    private val fakeWebHeaders = listOf(
        "Cookie", "SESSDATA=secret; bili_jct=csrf",
        "Origin", "https://www.bilibili.com",
        "Referer", "https://www.bilibili.com/",
        "User-Agent", "Mozilla/5.0 test-agent",
        "Sec-Ch-Ua", "\"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"122\"",
        "Sec-Ch-Ua-Platform", "\"Windows\"",
        "Sec-Ch-Ua-Mobile", "?0"
    )

    @Before
    fun setUp() {
        // ImageRequestPolicy 会调用 NetWorkUtil.isBilibiliHost，触发 NetWorkUtil 类初始化
        // （静态字段 webHeaders = buildWebHeaders() 会读 SharedPreferences），先注入假实现。
        SharedPreferencesUtil.sharedPreferences = FakeSharedPreferences()
    }

    @After
    fun tearDown() {
        SharedPreferencesUtil.sharedPreferences = null
    }

    // ---------------- shouldAttachSession：B 站域返回 true ----------------

    @Test
    fun shouldAttachSession_bilibiliHosts_true() {
        listOf(
            "https://www.bilibili.com/bfs/face/member/noface.jpg",
            "https://api.bilibili.com/x/web-interface/card?mid=1",
            "https://i0.hdslb.com/bfs/archive/cover.jpg",
            "https://i0.hdslb.com/bfs/face/x.jpg?x-oss-process=image/resize,w_100",
            "https://upos-sz.bilivideo.com/upgcxcode/x.m4s",
            "https://b23.tv/av123",
            "https://api.bilibili.cn/x/test"
        ).forEach { url ->
            assertTrue("B 站域应允许带会话头：$url", ImageRequestPolicy.shouldAttachSession(url))
        }
    }

    @Test
    fun shouldAttachSession_uppercaseHost_stillMatches() {
        // HTTP 域名大小写不敏感，URI#getHost 会保留原大小写，策略里必须自己归一。
        assertTrue(ImageRequestPolicy.shouldAttachSession("HTTPS://I0.HDSLB.COM/bfs/x.jpg"))
        assertEquals("i0.hdslb.com", ImageRequestPolicy.hostOf("HTTPS://I0.HDSLB.COM/bfs/x.jpg"))
    }

    // ---------------- shouldAttachSession：站外域返回 false ----------------

    @Test
    fun shouldAttachSession_externalHosts_false() {
        listOf(
            "https://evilbilibili.com/x.jpg",          // 后缀包含 bilibili.com 但不是子域
            "https://www.bilibili.com.evil.com/x.jpg", // 前缀包含 bilibili.com 也不是子域
            "https://afdiancdn.com/img/avatar.jpg",    // 赞助榜头像的真实来源
            "https://example.com/x.jpg",
            "http://img.example.org/a.png"
        ).forEach { url ->
            assertFalse("站外域绝不能带会话头：$url", ImageRequestPolicy.shouldAttachSession(url))
        }
    }

    // ---------------- shouldAttachSession：解析失败 / 非 http(s) 一律 fail-closed ----------------

    @Test
    fun shouldAttachSession_blankOrUnparsable_false() {
        assertFalse(ImageRequestPolicy.shouldAttachSession(null))
        assertFalse(ImageRequestPolicy.shouldAttachSession(""))
        assertFalse(ImageRequestPolicy.shouldAttachSession("   "))
        assertFalse(ImageRequestPolicy.shouldAttachSession("not a url"))
        assertFalse(ImageRequestPolicy.shouldAttachSession("https://"))
    }

    @Test
    fun shouldAttachSession_protocolRelativeUrl_false() {
        // 协议相对 URL（页面里常见的 //i0.hdslb.com/x.jpg）没有 scheme：
        // 无法确认最终协议与来源，按站外处理（fail-closed），宁可丢一次 Referer 也不发 Cookie。
        assertFalse(ImageRequestPolicy.shouldAttachSession("//i0.hdslb.com/bfs/x.jpg"))
        assertNull(ImageRequestPolicy.hostOf("//i0.hdslb.com/bfs/x.jpg"))
    }

    @Test
    fun shouldAttachSession_nonHttpScheme_false() {
        // Glide 也可能拿到 content:// / file:// 这类非网络 URI；这些不走 OkHttp，但策略不能误判。
        assertFalse(ImageRequestPolicy.shouldAttachSession("content://media/external/images/1"))
        assertFalse(ImageRequestPolicy.shouldAttachSession("file:///sdcard/x.jpg"))
        assertFalse(ImageRequestPolicy.shouldAttachSession("ftp://i0.hdslb.com/x.jpg"))
        assertFalse(ImageRequestPolicy.shouldAttachSession("i0.hdslb.com/bfs/x.jpg")) // 裸域名
    }

    // ---------------- headersFor：真正决定发什么头 ----------------

    @Test
    fun headersFor_bilibiliHost_keepsSessionHeaders() {
        val headers = ImageRequestPolicy.headersFor(
            "https://i0.hdslb.com/bfs/archive/cover.jpg", fakeWebHeaders
        )
        assertEquals("SESSDATA=secret; bili_jct=csrf", headers["Cookie"])
        assertEquals("https://www.bilibili.com", headers["Origin"])
        assertEquals("https://www.bilibili.com/", headers["Referer"])
        assertEquals("Mozilla/5.0 test-agent", headers["User-Agent"])
    }

    @Test
    fun headersFor_externalHost_dropsSessionHeaders_keepsOnlyUserAgent() {
        val headers = ImageRequestPolicy.headersFor("https://afdiancdn.com/img/avatar.jpg", fakeWebHeaders)
        assertEquals("站外域只允许保留 User-Agent", 1, headers.size)
        assertEquals("Mozilla/5.0 test-agent", headers["User-Agent"])
        assertFalse("Cookie 绝不能出现", headers.containsKey("Cookie"))
        assertFalse("Origin 也不能出现", headers.containsKey("Origin"))
        assertFalse("Referer 也不能出现", headers.containsKey("Referer"))
        headers.values.forEach { value ->
            assertFalse("任何头值里都不应出现登录凭证：$value", value.contains("SESSDATA"))
            assertFalse(value.contains("bili_jct"))
        }
    }

    @Test
    fun headersFor_externalHostWithoutUserAgent_returnsEmpty() {
        val headers = ImageRequestPolicy.headersFor("https://example.com/x.jpg", emptyList())
        assertTrue("没有可用头时返回空 Map，而不是抛异常", headers.isEmpty())
    }

    @Test
    fun headersFor_bilibiliHostWithEmptySnapshot_returnsEmpty() {
        // 防御式：webHeaders 快照异常为空时，B 站域也不会凭空造头。
        assertTrue(ImageRequestPolicy.headersFor("https://i0.hdslb.com/x.jpg", emptyList()).isEmpty())
    }

    // ---------------- headersToRemove：跨主机重定向的显式清理 ----------------

    @Test
    fun headersToRemove_external_returnsSessionHeaderNames() {
        val toRemove = ImageRequestPolicy.headersToRemove("https://example.com/x.jpg")
        assertEquals(listOf("Cookie", "Origin", "Referer"), toRemove)
    }

    @Test
    fun headersToRemove_bilibili_returnsEmpty() {
        assertTrue(ImageRequestPolicy.headersToRemove("https://i0.hdslb.com/x.jpg").isEmpty())
    }

    // ---------------- supportsResizeSuffix：只有 B 站图床认 @…w.webp 缩放后缀 ----------------

    @Test
    fun supportsResizeSuffix_bilibiliCdn_true() {
        listOf(
            "https://i0.hdslb.com/bfs/archive/cover.jpg",
            "https://s1.hdslb.com/bfs/face/x.png",
            "https://album.biliimg.com/bfs/new_dyn/x.jpg",
            "https://upos-sz-mirrorcos.bilivideo.com/x.jpg"
        ).forEach { url ->
            assertTrue("B 站图床应允许拼缩放后缀：$url", ImageRequestPolicy.supportsResizeSuffix(url))
        }
    }

    @Test
    fun supportsResizeSuffix_externalHosts_false() {
        // 站外拼后缀一律 404。26.11.06「赞赏」入口就是这么坏的：gitee 上的收款码被拼成
        // ma.jpg@0e_75q_640w.webp，图片直接加载不出来。
        listOf(
            "https://gitee.com/zisekongling/bili-terminal-re/raw/master/ma.jpg",
            "https://afdiancdn.com/img/avatar.jpg",
            "https://example.com/x.jpg"
        ).forEach { url ->
            assertFalse("站外地址不能拼缩放后缀：$url", ImageRequestPolicy.supportsResizeSuffix(url))
        }
    }

    @Test
    fun supportsResizeSuffix_bilibiliMainSite_false() {
        // 主站域名属于 B 站（isBilibiliHost 为 true），但同样不认 @…w 后缀，拼上去一样是坏地址。
        assertFalse(ImageRequestPolicy.supportsResizeSuffix("https://www.bilibili.com/x/web-interface/card"))
        assertFalse(ImageRequestPolicy.supportsResizeSuffix("https://api.bilibili.com/x/test.jpg"))
    }

    @Test
    fun supportsResizeSuffix_unparsable_false() {
        assertFalse(ImageRequestPolicy.supportsResizeSuffix(null))
        assertFalse(ImageRequestPolicy.supportsResizeSuffix(""))
        assertFalse(ImageRequestPolicy.supportsResizeSuffix("//i0.hdslb.com/bfs/x.jpg")) // 协议相对
        assertFalse(ImageRequestPolicy.supportsResizeSuffix("i0.hdslb.com/bfs/x.jpg")) // 裸域名
    }

    // ---------------- pairsOf：扁平列表的解析（防御式） ----------------

    @Test
    fun pairsOf_oddTrailingKeyIgnored() {
        val pairs = ImageRequestPolicy.pairsOf(listOf("Cookie", "SESSDATA=x", "User-Agent"))
        assertEquals(mapOf("Cookie" to "SESSDATA=x"), pairs)
    }

    @Test
    fun pairsOf_emptyList_returnsEmptyMap() {
        assertTrue(ImageRequestPolicy.pairsOf(emptyList()).isEmpty())
    }

    @Test
    fun headersFor_handlesLowercaseUserAgentKey() {
        // HTTP 头名大小写不敏感：webHeaders 形态变成小写时同样要能识别出 UA。
        val headers = ImageRequestPolicy.headersFor(
            "https://example.com/x.jpg", listOf("user-agent", "Mozilla/5.0 lower")
        )
        assertEquals("Mozilla/5.0 lower", headers["User-Agent"])
        assertEquals(1, headers.size)
    }
}
