package com.RobinNotBad.BiliClient.ui.shortvideo

import com.RobinNotBad.BiliClient.api.SpaceStatApi
import com.RobinNotBad.BiliClient.model.ShortVideoItem
import com.RobinNotBad.BiliClient.model.VideoCard
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「只看 TA」与作者卡纯逻辑的 JVM 单测（26.11）。
 * 覆盖：排序 / 去重、退出落点、到底判定、统计副行降级拼接、chip 宽度、
 * VideoCard → ShortVideoItem 映射、空间统计接口解析。
 */
class ShortVideoAuthorPolicyTest {

    private fun item(aid: Long, title: String = "t$aid") = ShortVideoItem(aid = aid, title = title)

    // ==================== orderAuthorItems ====================

    @Test
    fun `当前条钉在第 0 位且列表里的重复条被去掉`() {
        val head = item(1)
        val fetched = listOf(item(2), item(1), item(3))
        val result = ShortVideoAuthorPolicy.orderAuthorItems(head, fetched)
        assertEquals(listOf(1L, 2L, 3L), result.map { it.aid })
        assertTrue(result[0] === head)
    }

    @Test
    fun `服务端发布时间倒序原样保留`() {
        val fetched = listOf(item(9), item(7), item(5))
        val result = ShortVideoAuthorPolicy.orderAuthorItems(item(1), fetched)
        assertEquals(listOf(1L, 9L, 7L, 5L), result.map { it.aid })
    }

    @Test
    fun `分页重复 aid 去重`() {
        val fetched = listOf(item(2), item(3), item(2))
        val result = ShortVideoAuthorPolicy.orderAuthorItems(null, fetched)
        assertEquals(listOf(2L, 3L), result.map { it.aid })
    }

    @Test
    fun `head 为 null 时只返回去重后的列表`() {
        val result = ShortVideoAuthorPolicy.orderAuthorItems(null, listOf(item(4), item(4)))
        assertEquals(listOf(4L), result.map { it.aid })
    }

    @Test
    fun `非法 aid 被过滤`() {
        val result = ShortVideoAuthorPolicy.orderAuthorItems(item(0), listOf(item(0), item(-1), item(2)))
        assertEquals(listOf(2L), result.map { it.aid })
    }

    // ==================== exitRestoreIndex ====================

    @Test
    fun `退出落到记住位置的下一条`() {
        assertEquals(6, ShortVideoAuthorPolicy.exitRestoreIndex(5, 10))
    }

    @Test
    fun `记住位置已是最后一条时收到最后一条`() {
        assertEquals(9, ShortVideoAuthorPolicy.exitRestoreIndex(9, 10))
        assertEquals(9, ShortVideoAuthorPolicy.exitRestoreIndex(20, 10))
    }

    @Test
    fun `推荐流为空或负下标时回 0`() {
        assertEquals(0, ShortVideoAuthorPolicy.exitRestoreIndex(3, 0))
        assertEquals(0, ShortVideoAuthorPolicy.exitRestoreIndex(-2, 5))
        assertEquals(0, ShortVideoAuthorPolicy.exitRestoreIndex(0, 1))
    }

    // ==================== shouldNotifyEnd ====================

    @Test
    fun `拉空且停在最后一条才提示到底`() {
        assertTrue(ShortVideoAuthorPolicy.shouldNotifyEnd(4, 5, true))
        assertFalse(ShortVideoAuthorPolicy.shouldNotifyEnd(3, 5, true))
        assertFalse(ShortVideoAuthorPolicy.shouldNotifyEnd(4, 5, false))
        assertFalse(ShortVideoAuthorPolicy.shouldNotifyEnd(0, 0, true))
    }

    // ==================== statLine ====================

    @Test
    fun `三段齐全用中点拼接`() {
        assertEquals(
            "52.3万粉丝 · 893投稿 · 1.7亿获赞",
            ShortVideoAuthorPolicy.statLine("52.3万粉丝", "893投稿", "1.7亿获赞")
        )
    }

    @Test
    fun `未登录缺获赞段不留多余分隔符`() {
        assertEquals("52.3万粉丝 · 893投稿", ShortVideoAuthorPolicy.statLine("52.3万粉丝", "893投稿", null))
    }

    @Test
    fun `投稿数失败时跳过该段`() {
        assertEquals("52.3万粉丝 · 1.7亿获赞", ShortVideoAuthorPolicy.statLine("52.3万粉丝", null, "1.7亿获赞"))
    }

    @Test
    fun `全缺时返回空串`() {
        assertEquals("", ShortVideoAuthorPolicy.statLine(null, "", null))
    }

    // ==================== itemFromCard ====================

    @Test
    fun `空间投稿卡片映射为短视频条目`() {
        val card = VideoCard("标题", "UP 名", "288.3万观看", "http://cover", 123L, "BV1xx")
        val result = ShortVideoAuthorPolicy.itemFromCard(card, 42L, "http://face")
        assertEquals(123L, result.aid)
        assertEquals("BV1xx", result.bvid)
        assertEquals("标题", result.title)
        assertEquals("UP 名", result.authorName)
        assertEquals(42L, result.authorMid)
        assertEquals("http://face", result.authorFace)
        assertEquals("http://cover", result.cover)
        // 接口侧拼的「观看」后缀剥掉，只留数字文案
        assertEquals("288.3万", result.viewCount)
        // 这条链路拿不到 cid / duration，取流前由 AuthorFeedSource 补 cid
        assertEquals(0L, result.cid)
        assertEquals(0L, result.duration)
    }

    @Test
    fun `view 不带观看后缀时原样保留`() {
        val card = VideoCard("t", "u", "123", "c", 1L, "BV")
        assertEquals("123", ShortVideoAuthorPolicy.itemFromCard(card, 1L, "").viewCount)
    }

    // ==================== chipTextMaxWidth ====================

    @Test
    fun `chip 名字宽度等于总上限减头像关闭间距内边距`() {
        // 圆屏参考：141 - 12 - 16 - 5*2 - 7（两侧内边距合计 3.5*2）= 96
        assertEquals(96f, ShortVideoAuthorPolicy.chipTextMaxWidth(141f, 12f, 16f, 5f, 7f), 0.001f)
    }

    @Test
    fun `极端小屏不为负`() {
        assertEquals(0f, ShortVideoAuthorPolicy.chipTextMaxWidth(10f, 12f, 16f, 5f, 4f), 0.001f)
    }

    @Test
    fun `chip 总宽上限随屏缩放且圆方分档`() {
        val roundRef = ShortVideoLayoutSpec.Metrics(233f, 233f, true)
        val squareRef = ShortVideoLayoutSpec.Metrics(240f, 280f, false)
        assertEquals(141f, ShortVideoLayoutSpec.authorChipMaxWidth(roundRef), 0.001f)
        assertEquals(150f, ShortVideoLayoutSpec.authorChipMaxWidth(squareRef), 0.001f)
        // 小表盘按短边缩放，夹在 0.80 下限
        val small = ShortVideoLayoutSpec.Metrics(180f, 180f, true)
        assertEquals(141f * 0.80f, ShortVideoLayoutSpec.authorChipMaxWidth(small), 0.001f)
    }

    // ==================== SpaceStatApi 解析 ====================

    @Test
    fun `navnum 正常解析投稿数`() {
        val json = JSONObject("""{"code":0,"data":{"video":893}}""")
        assertEquals(893, SpaceStatApi.parseVideoCount(json))
    }

    @Test
    fun `navnum 零投稿是合法值`() {
        val json = JSONObject("""{"code":0,"data":{"video":0}}""")
        assertEquals(0, SpaceStatApi.parseVideoCount(json))
    }

    @Test
    fun `navnum 失败或缺字段返回 -1`() {
        assertEquals(-1, SpaceStatApi.parseVideoCount(JSONObject("""{"code":-400}""")))
        assertEquals(-1, SpaceStatApi.parseVideoCount(JSONObject("""{"code":0}""")))
        assertEquals(-1, SpaceStatApi.parseVideoCount(JSONObject("""{"code":0,"data":{}}""")))
        assertEquals(-1, SpaceStatApi.parseVideoCount(null))
    }

    @Test
    fun `upstat 正常解析获赞数`() {
        val json = JSONObject("""{"code":0,"data":{"likes":170000000}}""")
        assertEquals(170000000L, SpaceStatApi.parseLikes(json))
    }

    @Test
    fun `upstat 未登录无 data 时返回 -1 触发降级`() {
        assertEquals(-1L, SpaceStatApi.parseLikes(JSONObject("""{"code":0,"message":"0"}""")))
        assertEquals(-1L, SpaceStatApi.parseLikes(JSONObject("""{"code":-101}""")))
        assertEquals(-1L, SpaceStatApi.parseLikes(JSONObject("""{"code":0,"data":{}}""")))
        assertEquals(-1L, SpaceStatApi.parseLikes(null))
    }
}