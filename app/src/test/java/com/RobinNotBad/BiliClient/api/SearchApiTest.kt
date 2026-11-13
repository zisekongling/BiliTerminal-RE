package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.model.VideoCard
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索结果解析的纯函数单测。
 *
 * 26.10.19 之前聚合搜索（all/v2）的 media_bangumi 分支与单独搜索（search_type=media_bangumi）
 * 是两套解析：前者用 getString 取字段（缺 index_show 整页 JSONException）、封面也拿不到 scheme。
 * 现在两处共用 [SearchApi.getBangumiFromSearchResult]，这里同时钉住单独搜索与聚合搜索两条入口。
 */
class SearchApiTest {

    private fun single(input: JSONArray): List<VideoCard> {
        val list = ArrayList<VideoCard>()
        SearchApi.getBangumiFromSearchResult(input, list)
        return list
    }

    private fun aggregate(input: JSONArray): List<VideoCard> {
        val list = ArrayList<VideoCard>()
        SearchApi.getVideosFromSearchResult(input, list, true)
        return list
    }

    @Test
    fun bangumi_parsesEveryField() {
        val cards = single(
            JSONArray(
                """[{"title":"<em class=\"keyword\">孤独</em>摇滚!","cover":"//i0.hdslb.com/bfs/x.jpg",
                    "areas":"日本","index_show":"全14话","media_id":123,"season_id":"456"}]"""
            )
        )

        assertEquals(1, cards.size)
        val card = cards[0]
        assertEquals("孤独摇滚!", card.title)
        assertEquals("日本", card.upName)
        assertEquals("全14话", card.view)
        assertEquals("https://i0.hdslb.com/bfs/x.jpg", card.cover)
        assertEquals(123L, card.aid)
        assertEquals("456", card.bvid)
        assertEquals("media_bangumi", card.type)
    }

    @Test
    fun bangumi_missingIndexShowFallsBackToStyles() {
        val cards = single(
            JSONArray("""[{"title":"某番","cover":"//i0.hdslb.com/a.jpg","areas":"日本","styles":"原创/科幻","media_id":1,"season_id":"2"}]""")
        )

        // 单独搜索接口没有 index_show，空着会让卡片第三行整行消失，故退用 styles
        assertEquals("原创/科幻", cards[0].view)
    }

    @Test
    fun bangumi_toleratesMissingFields() {
        // 旧实现用 getString 取 index_show/cover，缺字段直接抛 JSONException 整页失败
        val cards = single(JSONArray("""[{"title":"只有标题"}]"""))

        assertEquals(1, cards.size)
        assertEquals("", cards[0].cover)
        assertEquals("", cards[0].view)
        assertEquals(0L, cards[0].aid)
        assertEquals("", cards[0].bvid)
    }

    @Test
    fun bangumi_coverWithoutSchemeGetsHttps() {
        val cards = single(
            JSONArray("""[{"title":"x","cover":"//i0.hdslb.com/b.jpg"},{"title":"y","cover":"https://i0.hdslb.com/c.jpg"}]""")
        )
        assertEquals("https://i0.hdslb.com/b.jpg", cards[0].cover)
        assertEquals("https://i0.hdslb.com/c.jpg", cards[1].cover)
    }

    @Test
    fun bangumi_skipsNullEntriesAndToleratesNullInput() {
        val cards = single(JSONArray("""[null,{"title":"有效"}]"""))
        assertEquals(1, cards.size)
        assertEquals("有效", cards[0].title)

        val nullList = ArrayList<VideoCard>()
        // 防御式接口：null 不该抛
        SearchApi.getBangumiFromSearchResult(null, nullList)
        assertTrue(nullList.isEmpty())
    }

    @Test
    fun aggregateSearch_bangumiBranchReusesTheSameParser() {
        val cards = aggregate(
            JSONArray(
                """[{"result_type":"media_bangumi","data":[
                    {"title":"<em class=\"keyword\">间谍</em>过家家","cover":"//i0.hdslb.com/d.jpg","areas":"日本",
                     "styles":"搞笑/日常","media_id":999,"season_id":"888"}]}]"""
            )
        )

        assertEquals(1, cards.size)
        val card = cards[0]
        assertEquals("间谍过家家", card.title)
        assertEquals("https://i0.hdslb.com/d.jpg", card.cover)
        assertEquals("搞笑/日常", card.view)
        assertEquals(999L, card.aid)
        assertEquals("media_bangumi", card.type)
    }

    private fun typeResult(input: JSONArray?): List<VideoCard> {
        val list = ArrayList<VideoCard>()
        SearchApi.getVideosFromTypeResult(input, list)
        return list
    }

    /**
     * `search_type=video` 的 `data.result` 直接就是视频条目数组（外面没有 result_type 那一层）。
     * 26.10.24 加搜索排序后视频分类改走这条接口，解析与聚合搜索共用 [SearchApi.parseVideoEntry]。
     */
    @Test
    fun typeSearch_video_parsesEveryField() {
        val cards = typeResult(
            JSONArray(
                """[{"type":"video","title":"<em class=\"keyword\">原神</em>攻略","pic":"//i0.hdslb.com/e.jpg",
                    "author":"某个UP","play":12345,"aid":111,"bvid":"BV1xx411c7mD"}]"""
            )
        )

        assertEquals(1, cards.size)
        val card = cards[0]
        assertEquals("原神攻略", card.title)
        assertEquals("某个UP", card.upName)
        // toWan 之后拼中文单位，这里只钉住"播放量被读到了"与后缀
        assertTrue(card.view.endsWith("观看"))
        assertEquals("https://i0.hdslb.com/e.jpg", card.cover)
        assertEquals(111L, card.aid)
        assertEquals("BV1xx411c7mD", card.bvid)
        assertEquals("video", card.type)
    }

    @Test
    fun typeSearch_video_skipsNonVideoEntries() {
        // 阿B会把直播间放进视频结果里（聚合与分类搜索都可能），非 video 条目要跳过
        val cards = typeResult(
            JSONArray(
                """[{"type":"live_room","title":"直播间","pic":"//i0.hdslb.com/f.jpg","author":"主播",
                    "play":1,"aid":1,"bvid":"x"},
                   {"type":"video","title":"真视频","pic":"//i0.hdslb.com/g.jpg","author":"UP",
                    "play":2,"aid":2,"bvid":"BV1"}]"""
            )
        )

        assertEquals(1, cards.size)
        assertEquals("真视频", cards[0].title)
    }

    @Test
    fun typeSearch_video_toleratesNullInputAndNullEntries() {
        assertTrue(typeResult(null).isEmpty())
        assertTrue(typeResult(JSONArray("""[null]""")).isEmpty())
        assertTrue(typeResult(JSONArray("[]")).isEmpty())
    }

    @Test
    fun aggregateSearch_videoBranchUsesTheSameEntryParser() {
        val cards = aggregate(
            JSONArray(
                """[{"result_type":"video","data":[
                    {"type":"video","title":"聚合里的视频","pic":"//i0.hdslb.com/h.jpg","author":"UP",
                     "play":3,"aid":3,"bvid":"BV3"}]}]"""
            )
        )

        assertEquals(1, cards.size)
        assertEquals("聚合里的视频", cards[0].title)
        // 与分类搜索同一份封面规则：协议相对地址一律补 https
        assertEquals("https://i0.hdslb.com/h.jpg", cards[0].cover)
        assertEquals("video", cards[0].type)
    }

    /**
     * 风控回退的判据。
     *
     * 26.10.24 审查指出：只有 `v_voucher` 一种形态被认出来，`code=-412/-352` 的响应往往连
     * `data` 都没有，会在主链路上静默返回 null → 页面显示成「啥都木有」。
     */
    @Test
    fun riskControl_recognizesBothShapes() {
        // 形态二：code=0 但 data 里只有 v_voucher（实测抓到的那种）
        assertTrue(SearchApi.shouldFallback(JSONObject("""{"code":0,"data":{"v_voucher":"voucher_x"}}""")))
        // 形态一：code=-412/-352，通常没有 data
        assertTrue(SearchApi.shouldFallback(JSONObject("""{"code":-412,"message":"请求被拦截"}""")))
        assertTrue(SearchApi.shouldFallback(JSONObject("""{"code":-352,"message":"风控校验失败"}""")))
        assertTrue(SearchApi.shouldFallback(JSONObject("""{"code":-412,"data":null}""")))
    }

    @Test
    fun riskControl_normalResponsesAreNotFallback() {
        // 真的没有匹配结果：带 result（空数组），不能当成风控
        assertTrue(!SearchApi.shouldFallback(JSONObject("""{"code":0,"data":{"seid":"1","result":[]}}""")))
        // 正常有结果
        assertTrue(!SearchApi.shouldFallback(JSONObject("""{"code":0,"data":{"result":[{"type":"video"}]}}""")))
        // 其它错误码交给 throwIfServerError 抛，不当风控回退
        assertTrue(!SearchApi.shouldFallback(JSONObject("""{"code":-400,"message":"请求错误"}""")))

        assertTrue(!SearchApi.isRiskControl(JSONObject("""{"seid":"1","result":[]}""")))
        assertTrue(SearchApi.isRiskControl(JSONObject("""{"v_voucher":"voucher_x"}""")))
    }

    // ---- 风控回退的 legacy 请求必须带上 seid（反馈 #148） ----

    @Test
    fun appendSeid_appendsWhenPresent() {
        val url = "https://api.bilibili.com/x/web-interface/search/type?page=1&keyword=abc"
        assertEquals("$url&seid=12345", SearchApi.appendSeid(url, "12345"))
    }

    @Test
    fun appendSeid_keepsUrlWhenSeidMissing() {
        val url = "https://api.bilibili.com/x/web-interface/search/type?page=1&keyword=abc"
        assertEquals("没拿到 seid 时原样返回", url, SearchApi.appendSeid(url, ""))
        assertEquals("seid 为 null 时原样返回", url, SearchApi.appendSeid(url, null))
        assertNull("url 为 null 时返回 null", SearchApi.appendSeid(null, "12345"))
    }
}
