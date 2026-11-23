package com.RobinNotBad.BiliClient.util.view

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ShareTexts] 的纯函数单测：只验链接形态与文案拼装，不碰 Android。
 */
class ShareTextsTest {

    @Test
    fun videoUrl_正常bvid() {
        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD", ShareTexts.videoUrl("BV1xx411c7mD"))
    }

    @Test
    fun videoUrl_空值返回空串() {
        assertEquals("", ShareTexts.videoUrl(null))
        assertEquals("", ShareTexts.videoUrl(""))
        assertEquals("", ShareTexts.videoUrl("   "))
    }

    @Test
    fun bangumiUrl_指针到集() {
        assertEquals("https://www.bilibili.com/bangumi/play/ep12345", ShareTexts.bangumiUrl(12345L))
        assertEquals("", ShareTexts.bangumiUrl(0L))
        assertEquals("", ShareTexts.bangumiUrl(-1L))
    }

    @Test
    fun liveUrl_正常房间号() {
        assertEquals("https://live.bilibili.com/2233", ShareTexts.liveUrl(2233L))
        assertEquals("", ShareTexts.liveUrl(0L))
    }

    @Test
    fun articleUrl_正常cv号() {
        assertEquals("https://www.bilibili.com/read/cv123456", ShareTexts.articleUrl(123456L))
        assertEquals("", ShareTexts.articleUrl(0L))
    }

    @Test
    fun favoriteUrl_正常收藏夹() {
        assertEquals("https://www.bilibili.com/medialist/detail/ml98765", ShareTexts.favoriteUrl(98765L))
        assertEquals("", ShareTexts.favoriteUrl(0L))
    }

    @Test
    fun opusUrl_动态号() {
        assertEquals("https://www.bilibili.com/opus/123456789", ShareTexts.opusUrl("123456789"))
        assertEquals("", ShareTexts.opusUrl(null))
        assertEquals("", ShareTexts.opusUrl("  "))
    }

    @Test
    fun trimTitle_超长截断加省略号() {
        val long = "标".repeat(ShareTexts.TITLE_MAX + 10)
        val trimmed = ShareTexts.trimTitle(long)
        assertEquals(ShareTexts.TITLE_MAX + 1, trimmed.length)
        assertEquals("…", trimmed.substring(trimmed.length - 1))
    }

    @Test
    fun trimTitle_边界不截断() {
        val exact = "标".repeat(ShareTexts.TITLE_MAX)
        assertEquals(exact, ShareTexts.trimTitle(exact))
        assertEquals("", ShareTexts.trimTitle(null))
        assertEquals("标题", ShareTexts.trimTitle("  标题  "))
    }

    @Test
    fun videoShareText_完整三段() {
        assertEquals(
            "标题 UP主: 某人 - https://www.bilibili.com/video/BV1",
            ShareTexts.videoShareText("标题", "BV1", "某人")
        )
    }

    @Test
    fun videoShareText_没有作者时不留半截UP主() {
        assertEquals(
            "标题 - https://www.bilibili.com/video/BV1",
            ShareTexts.videoShareText("标题", "BV1", null)
        )
        assertEquals(
            "标题 - https://www.bilibili.com/video/BV1",
            ShareTexts.videoShareText("标题", "BV1", "   ")
        )
    }

    @Test
    fun videoShareText_bvid为空只剩标题() {
        assertEquals("标题", ShareTexts.videoShareText("标题", null, "某人").replace(" UP主: 某人", ""))
    }

    @Test
    fun bangumiShareText_剧名加集标题() {
        assertEquals(
            "剧名 第1话 - https://www.bilibili.com/bangumi/play/ep1",
            ShareTexts.bangumiShareText("剧名", "第1话", 1L)
        )
    }

    @Test
    fun bangumiShareText_集标题与剧名相同时不重复() {
        assertEquals(
            "剧名 - https://www.bilibili.com/bangumi/play/ep1",
            ShareTexts.bangumiShareText("剧名", "剧名", 1L)
        )
    }

    @Test
    fun messageText_标题加换行加链接() {
        assertEquals("标题\nhttps://a", ShareTexts.messageText("标题", "https://a"))
    }

    @Test
    fun messageText_缺一个也不拼空行() {
        assertEquals("标题", ShareTexts.messageText("标题", null))
        assertEquals("标题", ShareTexts.messageText("标题", ""))
        assertEquals("https://a", ShareTexts.messageText(null, "https://a"))
        assertEquals("", ShareTexts.messageText(null, null))
    }
}
