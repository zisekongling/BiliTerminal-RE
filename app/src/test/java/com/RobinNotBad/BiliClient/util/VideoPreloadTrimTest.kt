package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.ShortVideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 短视频已播条目字段瘦身（内存泄露主因修复）的纯函数测试。
 *
 * <p>背景：`VideoPreloadManager.allItems` 无上限累积，每条 `ShortVideoItem` 在
 * `fetchVideoUrl` 之后带数 KB 的播放地址 / 弹幕地址 / 清晰度表——长时间刷短视频时
 * 内存线性膨胀，表现为「越用越卡、退出重进恢复」（用户反馈）。
 *
 * <p>修复思路是给离开回滑窗口的已播条目清掉这些**可重取**的大字段（回滑时
 * `setupPlayer()` 的 `videoUrl.isEmpty()` 分支会重新 `fetchVideoUrl`），
 * 保留对象壳维持绝对下标稳定。这里锁死瘦身函数本身的契约：
 * - 有大字段的条目被清空且返回 true（调用方据此记日志）；
 * - 本来就没有大字段的条目不动、返回 false（幂等，重复调用无害）；
 * - 元数据字段（aid/cid/title 等不可重取的壳）一个都不能动。
 */
class VideoPreloadTrimTest {

    private fun loadedItem(): ShortVideoItem = ShortVideoItem(
        aid = 42L, bvid = "BV1xx", cid = 4242L, title = "标题", cover = "http://cover",
        authorName = "up", videoUrl = "http://video/long-url-several-kb",
        danmakuUrl = "http://danmaku", qnStrList = arrayOf("1080P 高码率", "720P"),
        qnValueList = intArrayOf(80, 64), currentQuality = 80
    )

    @Test
    fun 有大字段的条目被清空并返回true() {
        val item = loadedItem()
        assertTrue("有大字段时应该返回 true", VideoPreloadManager.trimPlayedItemFields(item))
        assertEquals("videoUrl 应被清空", "", item.videoUrl)
        assertEquals("danmakuUrl 应被清空", "", item.danmakuUrl)
        assertEquals("qnStrList 应被置 null", null as Array<String>?, item.qnStrList)
        assertEquals("qnValueList 应被置 null", null as IntArray?, item.qnValueList)
    }

    @Test
    fun 瘦身是幂等的_重复调用返回false() {
        val item = loadedItem()
        assertTrue(VideoPreloadManager.trimPlayedItemFields(item))
        assertFalse("已瘦身的条目再调应返回 false（无需再动）", VideoPreloadManager.trimPlayedItemFields(item))
    }

    @Test
    fun 从未预加载过的条目直接返回false() {
        val item = ShortVideoItem(aid = 1L, cid = 2L, title = "t")
        assertFalse("没有大字段的条目不该被改动", VideoPreloadManager.trimPlayedItemFields(item))
        assertEquals("t", item.title)
    }

    @Test
    fun 元数据壳保持原样() {
        val item = loadedItem()
        VideoPreloadManager.trimPlayedItemFields(item)
        // 回滑重取播放地址靠 aid/cid；标题、封面、up 主信息不可由 fetchVideoUrl 重取，
        // 动了它们会让被瘦身的旧页显示空白。
        assertEquals(42L, item.aid)
        assertEquals(4242L, item.cid)
        assertEquals("BV1xx", item.bvid)
        assertEquals("标题", item.title)
        assertEquals("http://cover", item.cover)
        assertEquals("up", item.authorName)
        assertEquals(80, item.currentQuality)
    }

    @Test
    fun 部分字段存在时也算有大字段() {
        val onlyUrl = loadedItem().copy(danmakuUrl = "", qnStrList = null, qnValueList = null)
        assertTrue(VideoPreloadManager.trimPlayedItemFields(onlyUrl))
        val onlyQn = loadedItem().copy(videoUrl = "", danmakuUrl = "")
        assertTrue(VideoPreloadManager.trimPlayedItemFields(onlyQn))
    }
}
