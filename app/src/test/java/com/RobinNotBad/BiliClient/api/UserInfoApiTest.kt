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

        // 空格编码成 +、& 编码成 %26、= 编码成 %3D：编码后的参数串才是服务端签名校验的那一份
        assertTrue(url, url.contains("keyword=a%26b%3Dc+d"))
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
}
