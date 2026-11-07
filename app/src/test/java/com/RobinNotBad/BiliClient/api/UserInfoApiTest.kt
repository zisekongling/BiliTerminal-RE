package com.RobinNotBad.BiliClient.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 空间投稿列表请求参数的纯函数单测。
 *
 * 26.11.07 加「用户主页 → TA 的投稿」的空间内搜索与排序：`order` 会直接拼进 WBI 签名串，
 * 所以只放行白名单；`keyword` 是用户输入，必须先编码再签名（含 `&` / `=` 的关键词
 * 会破坏 query 结构并让签名失配）。两条规则都抽成了静态纯函数，在这里钉住。
 */
class UserInfoApiTest {

    @Test
    fun normalizeOrder_keepsWhitelistedOrders() {
        assertEquals(UserInfoApi.ORDER_PUBDATE, UserInfoApi.normalizeOrder(UserInfoApi.ORDER_PUBDATE))
        assertEquals(UserInfoApi.ORDER_CLICK, UserInfoApi.normalizeOrder(UserInfoApi.ORDER_CLICK))
        assertEquals(UserInfoApi.ORDER_STOW, UserInfoApi.normalizeOrder(UserInfoApi.ORDER_STOW))
    }

    @Test
    fun normalizeOrder_fallsBackToPubdateForAnythingElse() {
        assertEquals(UserInfoApi.ORDER_PUBDATE, UserInfoApi.normalizeOrder(null))
        assertEquals(UserInfoApi.ORDER_PUBDATE, UserInfoApi.normalizeOrder(""))
        assertEquals(UserInfoApi.ORDER_PUBDATE, UserInfoApi.normalizeOrder("fans"))
        // 带 query 分隔符的输入不能原样透传，否则等于让调用方改请求结构
        assertEquals(UserInfoApi.ORDER_PUBDATE, UserInfoApi.normalizeOrder("pubdate&pn=2"))
    }

    @Test
    fun buildUserVideoUrl_encodesKeyword() {
        val url = UserInfoApi.buildUserVideoUrl(2L, 3, "a&b=c d", UserInfoApi.ORDER_CLICK)

        // 空格按 encodeURIComponent 编成 %20（不是表单编码的 +）、& 编成 %26、= 编成 %3D：
        // 编码后的参数串才是服务端签名校验的那一份
        assertTrue(url, url.contains("keyword=a%26b%3Dc%20d"))
        assertFalse(url, url.contains("keyword=a&b=c"))
        assertTrue(url, url.contains("&mid=2&"))
        assertTrue(url, url.contains("&order=click&"))
        assertTrue(url, url.contains("&pn=3&"))
    }

    @Test
    fun buildUserVideoUrl_blankKeywordAndUnknownOrder() {
        val url = UserInfoApi.buildUserVideoUrl(9L, 1, "", "whatever")

        assertTrue(url, url.contains("keyword=&"))
        assertTrue(url, url.contains("&order=pubdate&"))
    }

    /**
     * 关键词编码口径：WBI 要的是 `encodeURIComponent`，`URLEncoder`（表单编码）的差异项必须掰回来。
     * 26.11.07 实测：`a~b` 用 `a%7Eb` 签 WBI 会被服务端判 -352「风控校验失败」。
     */
    @Test
    fun encodeKeyword_matchesEncodeUriComponent() {
        assertEquals("", UserInfoApi.encodeKeyword(null))
        assertEquals("", UserInfoApi.encodeKeyword(""))
        assertEquals("%E4%B8%AD%E6%96%87", UserInfoApi.encodeKeyword("中文"))
        assertEquals("a%20b", UserInfoApi.encodeKeyword("a b"))
        // encodeURIComponent 不编码这几个：~ ! ' ( ) * - _ .
        assertEquals("a~b!c'd(e)f*g-h_i.j", UserInfoApi.encodeKeyword("a~b!c'd(e)f*g-h_i.j"))
        assertEquals("a%26b%3Dc%23d%2Be", UserInfoApi.encodeKeyword("a&b=c#d+e"))
    }
}
