package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.model.SubscriptionFolder
import com.RobinNotBad.BiliClient.model.VideoCard
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「我的订阅」解析逻辑的纯函数单测。
 *
 * 不碰网络、不构造 `android.util.Pair`，只喂 JSONObject——`org.json:json` 在 JVM 单测里可用。
 */
class SubscriptionApiTest {

    // ==================== collected/list：我的订阅 ====================

    @Test
    fun parseSubscribedFolders_mixedFolderAndSeason_mapsBothTypes() {
        // 真实响应形状见 bilibili-API/docs/fav/info.md「查询用户收藏的视频收藏夹」
        val data = JSONObject(
            """{"count":2,"has_more":true,"list":[
                {"id":496307088,"fid":4963070,"mid":412466388,"attr":22,
                 "title":"鬼畜小课堂","cover":"http://i0.hdslb.com/a.jpg",
                 "upper":{"mid":412466388,"name":"某个UP"},"state":0,
                 "media_count":25,"type":11},
                {"id":44123,"mid":9521360,"attr":0,"title":"某合集",
                 "cover":"http://i0.hdslb.com/b.jpg","upper":{"mid":9521360,"name":"合集作者"},
                 "state":1,"media_count":88,"type":21,"view_count":123456}
            ]}"""
        )
        val list = ArrayList<SubscriptionFolder>()

        val hasMore = SubscriptionApi.parseSubscribedFolders(data, list)

        assertTrue("has_more 原样返回", hasMore)
        assertEquals("两条都要解析出来", 2, list.size)
        assertEquals("收藏夹 id 是 media_id", 496307088L, list[0].id)
        assertEquals("type=11 是收藏夹", SubscriptionFolder.TYPE_FAV_FOLDER, list[0].type)
        assertFalse("收藏夹不是合集", list[0].isSeason())
        assertEquals("标题", "鬼畜小课堂", list[0].title)
        assertEquals("UP 主昵称取 upper.name", "某个UP", list[0].upperName)
        assertEquals("视频数", 25, list[0].mediaCount)
        assertFalse("state=0 正常", list[0].isInvalid())

        assertEquals("合集 id 是 season_id", 44123L, list[1].id)
        assertTrue("type=21 是合集", list[1].isSeason())
        assertEquals("失效条目标志要解析出来", 1, list[1].state)
        assertTrue("state=1 已失效，列表里要置灰且点了给提示", list[1].isInvalid())
        assertEquals("合集播放量", 123456L, list[1].viewCount)
    }

    @Test
    fun parseSubscribedFolders_missingOptionalFields_defaultsAndSkips() {
        val data = JSONObject(
            """{"list":[
                {"id":0,"title":"没有 id 的脏条目"},
                {"id":100,"title":"缺 type 的条目"},
                {"id":200,"title":"没有 upper 的条目","type":21}
            ]}"""
        )
        val list = ArrayList<SubscriptionFolder>()

        SubscriptionApi.parseSubscribedFolders(data, list)

        assertEquals("id<=0 的脏条目要跳过，否则点进去必然 404", 2, list.size)
        assertEquals("缺 type 时按收藏夹兜底", SubscriptionFolder.TYPE_FAV_FOLDER, list[0].type)
        assertEquals("缺 media_count 兜底 0", 0, list[0].mediaCount)
        assertEquals("缺 upper 时昵称为空串而不是 null", "", list[1].upperName)
        assertEquals("缺 has_more 时按没有下一页处理，宁可提前停", false,
            SubscriptionApi.parseSubscribedFolders(JSONObject("""{"list":[]}"""), ArrayList()))
    }

    @Test
    fun parseSubscribedFolders_nullData_returnsFalseAndNoCrash() {
        val list = ArrayList<SubscriptionFolder>()
        assertFalse("data=null 不崩溃", SubscriptionApi.parseSubscribedFolders(null, list))
        assertTrue("data=null 不塞条目", list.isEmpty())
    }

    // ==================== space/fav/season/list：订阅的合集详情 ====================

    @Test
    fun parseSeasonVideos_mapsVideosAndTotal() {
        val data = JSONObject(
            """{"info":{"id":44123,"title":"某合集","media_count":88,
                       "upper":{"mid":9521360,"name":"合集作者"}},
               "medias":[
                {"id":112233,"bvid":"BV1xx411c7mD","title":"第一集",
                 "cover":"http://i0.hdslb.com/c.jpg","duration":600,
                 "cnt_info":{"play":12345,"danmaku":67}},
                {"id":445566,"bvid":"BV1yy411c7mE","title":"第二集",
                 "cover":"http://i0.hdslb.com/d.jpg","duration":900}
               ]}"""
        )
        val list = ArrayList<VideoCard>()

        val total = SubscriptionApi.parseSeasonVideos(data, list)

        assertEquals("总集数取 info.media_count，用于算有没有下一页", 88, total)
        assertEquals("两条视频", 2, list.size)
        assertEquals("合集里的 id 是 avid", 112233L, list[0].aid)
        assertEquals("bvid", "BV1xx411c7mD", list[0].bvid)
        assertEquals("标题", "第一集", list[0].title)
        assertEquals("UP 名从 info.upper 回填到每张卡", "合集作者", list[0].upName)
        assertEquals("播放量格式化", "1.2万", list[0].view)
        assertEquals("缺 cnt_info 不崩，播放量留空", null, list[1].view)
        assertEquals("时长原样带过来", 900L, list[1].duration)
    }

    @Test
    fun parseSeasonVideos_missingMediasAndInfo_returnsZeroTotal() {
        val list = ArrayList<VideoCard>()
        assertEquals("data 里没有 info/medias 时总数为 0", 0,
            SubscriptionApi.parseSeasonVideos(JSONObject("""{"medias":[]}"""), list))
        assertTrue("没有视频时不塞卡片", list.isEmpty())
        assertEquals("data=null 不崩溃", 0, SubscriptionApi.parseSeasonVideos(null, list))
    }

    @Test
    fun parseSeasonVideos_skipsDirtyRows() {
        val data = JSONObject(
            """{"info":{"media_count":2},"medias":[
                {"id":0,"title":"没有 avid 的脏条目"},
                {"id":778899,"title":"正常条目"}
            ]}"""
        )
        val list = ArrayList<VideoCard>()

        SubscriptionApi.parseSeasonVideos(data, list)

        assertEquals("id<=0 的条目跳过", 1, list.size)
        assertEquals("正常条目", 778899L, list[0].aid)
    }
}
