package com.RobinNotBad.BiliClient.api

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「收到的赞 → 弹幕」跳视频用的 BV 号提取（反馈 #44）。 */
class MessageApiTest {

    @Test
    fun parseBvid_plainVideoUri() {
        assertEquals("BV1xx411c7mD", MessageApi.parseBvid("https://www.bilibili.com/video/BV1xx411c7mD"))
    }

    @Test
    fun parseBvid_ignoresQueryAndFragment() {
        assertEquals(
            "BV1xx411c7mD",
            MessageApi.parseBvid("https://www.bilibili.com/video/BV1xx411c7mD/?p=2&dm_progress=12#reply")
        )
    }

    @Test
    fun parseBvid_noBvid_returnsEmpty() {
        assertEquals("", MessageApi.parseBvid("https://www.bilibili.com/bangumi/play/ep12345"))
        assertEquals("", MessageApi.parseBvid(""))
        assertEquals("", MessageApi.parseBvid(null))
    }

    // ---- #150 点赞消息：items / 游标 / 翻页参数 ----

    /** 真实响应是 data.total.items（比 reply 流多一层 total），必须从这里取到。 */
    @Test
    fun parseLikeItems_prefersTotalWrapper() {
        val data = JSONObject(
            """{"total":{"items":[{"id":1},{"id":2}]},"cursor":{"is_end":false,"id":9,"time":1700000000}}"""
        )
        val items: JSONArray = MessageApi.parseLikeItems(data)
        assertEquals(2, items.length())
        assertEquals(1L, items.getJSONObject(0).getLong("id"))
    }

    /** 兼容只有 data.items 的形态，别把一整页点赞直接判成空。 */
    @Test
    fun parseLikeItems_fallsBackToPlainItems() {
        val data = JSONObject("""{"items":[{"id":7}]}""")
        assertEquals(1, MessageApi.parseLikeItems(data).length())
    }

    @Test
    fun parseLikeItems_missingEverything_returnsEmpty() {
        assertEquals(0, MessageApi.parseLikeItems(JSONObject("{}")).length())
        assertEquals(0, MessageApi.parseLikeItems(null).length())
    }

    /** 游标与 items 同层，同样两处都认；原来只看 data.cursor，取不到就再也不翻页。 */
    @Test
    fun parseLikeCursor_readsBothShapes() {
        val wrapped = JSONObject("""{"total":{"items":[],"cursor":{"is_end":true,"id":3,"time":44}}}""")
        val cursorWrapped = MessageApi.parseLikeCursor(wrapped)
        assertEquals(true, cursorWrapped!!.getBoolean("is_end"))
        assertEquals(44L, cursorWrapped.getLong("time"))

        val plain = JSONObject("""{"items":[],"cursor":{"is_end":false,"id":5,"time":66}}""")
        val cursorPlain = MessageApi.parseLikeCursor(plain)
        assertEquals(false, cursorPlain!!.getBoolean("is_end"))
        assertEquals(5L, cursorPlain.getLong("id"))

        assertNull(MessageApi.parseLikeCursor(JSONObject("{}")))
        assertNull(MessageApi.parseLikeCursor(null))
    }

    /** 翻页参数的时间字段是 like_time，照抄 reply 的 reply_time 服务端不认（第 2 页仍是第 1 页）。 */
    @Test
    fun likePageQuery_usesLikeTime() {
        assertEquals("&id=9&like_time=1700000000", MessageApi.likePageQuery(9L, 1700000000L))
        assertTrue(MessageApi.likePageQuery(9L, 1700000000L).contains("like_time="))
    }

    @Test
    fun likePageQuery_firstPage_isEmpty() {
        assertEquals("", MessageApi.likePageQuery(0L, 0L))
        assertEquals("", MessageApi.likePageQuery(9L, 0L))
        assertEquals("", MessageApi.likePageQuery(0L, 1700000000L))
    }

    // ---- #150 未读角标 ----

    /** 未读角标 = at + reply + like；原来漏了 like，点赞再多角标也不涨。 */
    @Test
    fun sumUnread_includesLike() {
        val data = JSONObject("""{"at":1,"reply":2,"like":40,"sys_msg":5}""")
        assertEquals(43, MessageApi.sumUnread(data))
    }

    @Test
    fun sumUnread_missingFieldsCountAsZero() {
        assertEquals(0, MessageApi.sumUnread(JSONObject("{}")))
        assertEquals(3, MessageApi.sumUnread(JSONObject("""{"like":3}""")))
        assertEquals(0, MessageApi.sumUnread(null))
    }
}
