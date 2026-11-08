package com.RobinNotBad.BiliClient.adapter.video

import com.RobinNotBad.BiliClient.adapter.video.VideoCardMenu.Action
import com.RobinNotBad.BiliClient.model.VideoCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 长按菜单「这张卡片该出现哪几项」的判据。
 *
 * 判错会直接把错误的 aid 发出去：番剧卡片的 `aid` 装的是 media_id、直播卡片装的是 room_id，
 * 拿它们去调稍后再看/收藏只会得到 -400，所以这几条负例比正例更重要。
 */
class VideoCardMenuTest {

    private fun card(type: String): VideoCard = VideoCard().apply {
        this.title = "标题"
        this.aid = 114514
        this.bvid = "BV1xx411c7mD"
        this.cover = "http://i0.hdslb.com/bfs/archive/cover.jpg"
        this.type = type
    }

    @Test
    fun actionsFor_普通视频_信息流里五项齐全() {
        assertEquals(
            listOf(
                Action.WATCH_LATER, Action.FAVORITE, Action.CACHE,
                Action.VIEW_COVER, Action.REDUCE_RECOMMEND
            ),
            VideoCardMenu.actionsFor(card("video"), true)
        )
    }

    @Test
    fun actionsFor_普通视频_非信息流不给减少推荐() {
        assertEquals(
            listOf(Action.WATCH_LATER, Action.FAVORITE, Action.CACHE, Action.VIEW_COVER),
            VideoCardMenu.actionsFor(card("video"), false)
        )
    }

    @Test
    fun actionsFor_封面为空_不给看封面() {
        // 个别接口不返回 pic，点「看封面」只会得到一张空白大图，索性不出现这一项。
        val noCover = card("video").apply { cover = "" }
        val actions = VideoCardMenu.actionsFor(noCover, false)
        assertEquals(listOf(Action.WATCH_LATER, Action.FAVORITE, Action.CACHE), actions)
        assertFalse(actions.contains(Action.VIEW_COVER))
    }

    @Test
    fun actionsFor_type为空串_仍当作普通视频() {
        // VideoCard.type 的默认值是 "video"，但服务端字段缺失时解析出来可能是空串；
        // 空串得当成普通视频，否则一圈卡片长按全哑。
        assertEquals(4, VideoCardMenu.actionsFor(card(""), false).size)
    }

    @Test
    fun actionsFor_直播卡片_一个动作都不给() {
        // 直播卡的 aid 是 room_id，进稍后再看/收藏没有意义。
        assertTrue(VideoCardMenu.actionsFor(card("live"), true).isEmpty())
    }

    @Test
    fun actionsFor_番剧卡片_一个动作都不给() {
        // 番剧卡（type=media_bangumi）的 aid 是 media_id；旧行为是长按弹「番剧暂不支持快速缓存」，
        // 现在干脆不弹菜单。
        assertTrue(VideoCardMenu.actionsFor(card("media_bangumi"), true).isEmpty())
    }

    @Test
    fun actionsFor_未知type_保守不给() {
        // 认不出的 type 一律不给：宁可长按没反应，也不要发一个可能错位的 aid。
        assertTrue(VideoCardMenu.actionsFor(card("series"), true).isEmpty())
    }
}
