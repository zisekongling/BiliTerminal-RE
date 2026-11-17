package com.RobinNotBad.BiliClient.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VideoInfo.slimForTransfer] / [Collection.slimForTransfer] 的纯 JVM 单测。
 *
 * 缘起：控制台崩溃 #201 —— 965 集的合集把「每集一份完整 VideoInfo（arc）」塞进 Intent，
 * 750KB 撞穿 binder 事务上限，点播放就杀进程。瘦身副本必须满足两件事：
 * 1. 播放器要用的东西一个都不能少（collection、cids、每集的 aid/cid/标题）；
 * 2. 入参对象**不能被改动**（详情页还拿着同一个实例在画合集卡片）。
 */
class VideoInfoSlimTransferTest {

    private fun arc(aid: Long): VideoInfo = VideoInfo().apply {
        this.aid = aid
        title = "第 ${aid} 集"
        cover = "http://i0.hdslb.com/bfs/archive/$aid.jpg"
        description = "描述".repeat(50)
    }

    private fun episode(aid: Long, cid: Long, title: String, withArc: Boolean = true): Collection.Episode =
        Collection.Episode().apply {
            this.aid = aid
            this.cid = cid
            this.title = title
            bvid = "BV$aid"
            if (withArc) arc = arc(aid)
        }

    private fun collection(episodes: List<Collection.Episode>): Collection = Collection().apply {
        id = 7945444
        title = "萝太沙雕2B切片（合集）"
        cover = "http://i0.hdslb.com/bfs/archive/season.jpg"
        intro = "简介"
        mid = 3546376524794441L
        view = "12.3万"
        sections = listOf(
            Collection.Section().apply {
                season_id = 7945444
                id = 0
                this.title = "正片"
                type = 1
                this.episodes = episodes
            }
        )
    }

    private fun videoInfo(c: Collection): VideoInfo = VideoInfo().apply {
        aid = 117612444916716L
        bvid = "BV1FPWy6QE1L"
        title = "主视频"
        cids = arrayListOf(43586358992L)
        pagenames = arrayListOf("P1")
        collection = c
    }

    @Test
    fun `每集的 arc 被剥掉，aid cid 标题原样保留`() {
        val original = videoInfo(collection(listOf(episode(1L, 11L, "第一集"), episode(2L, 22L, "第二集"))))

        val slim = original.slimForTransfer()
        val eps = slim.collection!!.sections[0].episodes

        assertEquals(2, eps.size)
        eps.forEach { assertNull(it.arc) }
        assertEquals(1L, eps[0].aid)
        assertEquals(11L, eps[0].cid)
        assertEquals("第一集", eps[0].title)
        assertEquals("BV1", eps[0].bvid)
    }

    @Test
    fun `入参对象不被改动`() {
        val original = videoInfo(collection(listOf(episode(1L, 11L, "第一集"))))

        original.slimForTransfer()

        assertNotNull("瘦身不能就地清空 arc，详情页还要用", original.collection!!.sections[0].episodes[0].arc)
    }

    @Test
    fun `播放器要的 collection 与 cids 都在，播放列表长度一致`() {
        val original = videoInfo(collection((1L..40L).map { episode(it, it * 10, "第 $it 集") }))

        val slim = original.slimForTransfer()

        assertEquals(original.aid, slim.aid)
        assertEquals(original.cids, slim.cids)
        assertEquals(original.collection!!.id, slim.collection!!.id)
        assertEquals(original.collection!!.title, slim.collection!!.title)
        assertEquals(
            Collection.toPlaylist(original.collection!!).size,
            Collection.toPlaylist(slim.collection!!).size
        )
        assertEquals(
            Collection.toPlaylist(original.collection!!).map { it.aid },
            Collection.toPlaylist(slim.collection!!).map { it.aid }
        )
    }

    @Test
    fun `收藏的合集走 cards，瘦身后照样能展平`() {
        val c = Collection().apply {
            id = 100
            title = "收藏的合集"
            // toPlaylist 会剔掉 cid 缺失的条目（播放列表没有 cid 就没法取流），所以这里必须给 cid
            cards = mutableListOf(
                VideoCard("标题一", "", "1.1万", "", 41L, "BV41").apply { cid = 410L },
                VideoCard("标题二", "", "2.2万", "", 42L, "BV42").apply { cid = 420L }
            )
        }

        val slim = videoInfo(c).slimForTransfer()

        assertSame("cards 本身就是轻量 VideoCard，应当共用而不是复制", c.cards, slim.collection!!.cards)
        assertEquals(listOf(41L, 42L), Collection.toPlaylist(slim.collection!!).map { it.aid })
    }

    @Test
    fun `没有合集时保持 null`() {
        val original = VideoInfo().apply { aid = 1L; title = "单视频" }

        assertNull(original.slimForTransfer().collection)
        assertTrue(original.slimForTransfer().title == "单视频")
    }
}
