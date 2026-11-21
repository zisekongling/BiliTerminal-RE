package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.model.VideoCard
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * APP 推荐流解析单测（纯解析，不发请求）。
 *
 * 用例直接抄 2026-11 亲测的真实响应结构：外层 data.items，每条有 goto/card_goto/bvid/idx，
 * 作者与 aid 藏在 args 与 player_args 里，播放数是**已经格式化好的** cover_left_text_1。
 */
class AppRecommendApiTest {

    private fun item(
        idx: String,
        bvid: String,
        goto: String = "av",
        cardGoto: String = "av",
        aid: Long = 100L,
        upMid: Long = 200L,
        upName: String = "某UP",
        cover: String = "http://i0.hdslb.com/a.jpg",
        views: String = "193.9万",
        title: String = "标题"
    ): JSONObject = JSONObject()
        .put("idx", idx)
        .put("goto", goto)
        .put("card_goto", cardGoto)
        .put("bvid", bvid)
        .put("title", title)
        .put("cover", cover)
        .put("cover_left_text_1", views)
        .put("args", JSONObject().put("up_id", upMid).put("up_name", upName).put("aid", aid))
        .put("player_args", JSONObject().put("aid", aid).put("cid", 9L).put("duration", 600L))

    private fun result(vararg items: JSONObject): JSONObject =
        JSONObject().put("code", 0).put("data", JSONObject().put("items", JSONArray(items.toList())))

    @Test
    fun parseItems_mapsFields() {
        val out = ArrayList<VideoCard>()
        val next = AppRecommendApi.parseItems(result(item("111", "BV1xx", aid = 55L, upMid = 77L, upName = "老王")), out)
        assertEquals("111", next)
        assertEquals(1, out.size)
        val card = out[0]
        assertEquals("BV1xx", card.bvid)
        assertEquals(55L, card.aid)
        assertEquals(77L, card.upMid)
        assertEquals("老王", card.upName)
        assertEquals("标题", card.title)
        assertEquals("https://i0.hdslb.com/a.jpg", card.cover)
        assertEquals("播放数不再二次换算", "193.9万观看", card.view)
        assertEquals("APP 侧 track_id 留空", "", card.trackId)
    }

    @Test
    fun parseItems_cursorIsLastItemIdx() {
        val out = ArrayList<VideoCard>()
        val next = AppRecommendApi.parseItems(result(item("111", "BV1"), item("222", "BV2")), out)
        assertEquals("222", next)
        assertEquals(2, out.size)
    }

    @Test
    fun parseItems_skipsAdsAndMissingBvid() {
        val out = ArrayList<VideoCard>()
        AppRecommendApi.parseItems(
            result(
                item("1", "BV1"),
                item("2", "BVad", goto = "ad_av", cardGoto = "cm_v1"),
                item("3", ""),
                item("4", "BV4")
            ),
            out
        )
        assertEquals(listOf("BV1", "BV4"), out.map { it.bvid })
    }

    @Test
    fun parseItems_emptyOrBrokenReturnsStartCursor() {
        assertEquals(AppRecommendApi.CURSOR_START, AppRecommendApi.parseItems(null, ArrayList()))
        assertEquals(AppRecommendApi.CURSOR_START, AppRecommendApi.parseItems(JSONObject(), ArrayList()))
        assertEquals(AppRecommendApi.CURSOR_START, AppRecommendApi.parseItems(result(), ArrayList()))
        assertTrue(AppRecommendApi.parseItems(result(), null) == AppRecommendApi.CURSOR_START)
    }

    @Test
    fun parseItems_aidFallsBackToArgsThenParam() {
        val out = ArrayList<VideoCard>()
        val noPlayerArgs = item("1", "BV1", aid = 0L).apply { remove("player_args") }
            .put("args", JSONObject().put("aid", 33L).put("up_id", 5L).put("up_name", "a"))
        AppRecommendApi.parseItems(result(noPlayerArgs), out)
        assertEquals(33L, out[0].aid)
    }

    @Test
    fun hasCredential_requiresAccessKey() {
        // 没有注入 SharedPreferences 时读不到 access_key，必须判成无凭据（降级网页源的前提）
        assertTrue(!AppRecommendApi.hasCredential())
    }
}
