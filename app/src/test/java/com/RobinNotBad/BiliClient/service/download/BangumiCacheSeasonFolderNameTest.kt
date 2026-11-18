package com.RobinNotBad.BiliClient.service.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 番剧缓存目录名的单测（[BangumiCacheSeason.seasonFolderName]）。
 *
 * 这里钉的是「同名季不能共用目录」这件事：B 站的季名是「TV」「第二季」这种通用词，
 * 26.11.18 之前父目录只用了季名，《孤独摇滚！》与《魔女之旅》的「TV」季落进同一个目录，
 * 缓存页的「已下载」互相串台、后下的那部直接覆盖先下的成品。
 */
class BangumiCacheSeasonFolderNameTest {

    @Test
    fun `两部番的同名季不会落进同一个目录`() {
        val witch = BangumiCacheSeason.seasonFolderName("魔女之旅", "TV")
        val rock = BangumiCacheSeason.seasonFolderName("孤独摇滚！", "TV")

        assertEquals("魔女之旅 - TV", witch)
        assertEquals("孤独摇滚！ - TV", rock)
        assertNotEquals("这是这个 bug 的核心：同名季必须是两个目录", witch, rock)
    }

    @Test
    fun `季名与番剧名相同时不重复写两遍`() {
        // 单季番常把番剧名直接当季名，拼出来不能是「孤独摇滚！ - 孤独摇滚！」
        assertEquals("孤独摇滚！", BangumiCacheSeason.seasonFolderName("孤独摇滚！", "孤独摇滚！"))
    }

    @Test
    fun `季名为空时只用番剧名`() {
        assertEquals("魔女之旅", BangumiCacheSeason.seasonFolderName("魔女之旅", ""))
        assertEquals("魔女之旅", BangumiCacheSeason.seasonFolderName("魔女之旅", "   "))
    }

    @Test
    fun `番剧名为空时退回只用季名`() {
        // 拿不到番剧名的调用方仍要能落盘：行为与修这个 bug 之前一致
        assertEquals("TV", BangumiCacheSeason.seasonFolderName("", "TV"))
        assertEquals("TV", BangumiCacheSeason.seasonFolderName("   ", "TV"))
    }

    @Test
    fun `首尾空格会被去掉`() {
        assertEquals("魔女之旅 - TV", BangumiCacheSeason.seasonFolderName(" 魔女之旅 ", " TV "))
        assertEquals("魔女之旅", BangumiCacheSeason.seasonFolderName(" 魔女之旅 ", " 魔女之旅 "))
    }

    @Test
    fun `同一部番的不同季仍然是两个目录`() {
        val tv = BangumiCacheSeason.seasonFolderName("孤独摇滚！", "TV")
        val second = BangumiCacheSeason.seasonFolderName("孤独摇滚！", "第二季")
        assertEquals("孤独摇滚！ - TV", tv)
        assertEquals("孤独摇滚！ - 第二季", second)
        assertNotEquals(tv, second)
    }

    @Test
    fun `两边都空时返回空串由调用方兜底`() {
        // 缓存页会给季名兜一条本地化文案，真走到这里说明上游也没标题；
        // 返回空串让 isDownloaded 直接判「没下过」，不要凭空造一个目录出来
        assertEquals("", BangumiCacheSeason.seasonFolderName("", ""))
    }
}
