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

    // ---- #281 私信未读消除不掉 ----

    /**
     * 私信角标只认 follow_unread + unfollow_unread。
     *
     * 其余六桶在会话列表里没有对应的未读行，算进来就是一个点不掉的数（#281）。
     */
    @Test
    fun parsePrivateUnread_只算两桶_忽略其余六桶() {
        val data = JSONObject(
            """{"follow_unread":1,"unfollow_unread":2,"unfollow_push_msg":3,"dustbin_push_msg":4,
                "dustbin_unread":5,"biz_msg_unfollow_unread":6,"biz_msg_follow_unread":7,"custom_unread":8}"""
        )
        assertEquals(3, MessageApi.parsePrivateUnread(data))
    }

    @Test
    fun parsePrivateUnread_缺字段或null按0() {
        assertEquals(0, MessageApi.parsePrivateUnread(null))
        assertEquals(0, MessageApi.parsePrivateUnread(JSONObject("{}")))
        assertEquals(7, MessageApi.parsePrivateUnread(JSONObject("""{"follow_unread":7}""")))
    }

    // ---- 系统通知：page_size / 游标 ----

    @Test
    fun parseSystemNotices_取system_notify_list() {
        val response = JSONObject(
            """{"code":0,"data":{"system_notify_list":[{"id":1,"cursor":"100","title":"a"},{"id":2,"cursor":"200","title":"b"}]}}"""
        )
        val items = MessageApi.parseSystemNotices(response)
        assertEquals(2, items.size)
        assertEquals(2L, items[1].getLong("id"))
    }

    @Test
    fun parseSystemNotices_缺data或缺列表返回空() {
        assertTrue(MessageApi.parseSystemNotices(null).isEmpty())
        assertTrue(MessageApi.parseSystemNotices(JSONObject("""{"code":-6}""")).isEmpty())
        assertTrue(MessageApi.parseSystemNotices(JSONObject("""{"data":{"system_notify_list":null}}""")).isEmpty())
    }

    /** cursor 在响应里是字符串，optLong 直接取会拿到 0 —— 这里必须 parse 出真值。 */
    @Test
    fun maxCursorOf_字符串游标取最大值() {
        val notices = MessageApi.parseSystemNotices(
            JSONObject("""{"data":{"system_notify_list":[{"cursor":"1700000000000000"},{"cursor":"1700000000000500"}]}}""")
        )
        assertEquals(1700000000000500L, MessageApi.maxCursorOf(notices))
    }

    @Test
    fun maxCursorOf_空表或缺cursor返回0() {
        assertEquals(0L, MessageApi.maxCursorOf(null))
        assertEquals(0L, MessageApi.maxCursorOf(ArrayList()))
        assertEquals(0L, MessageApi.maxCursorOf(MessageApi.parseSystemNotices(JSONObject("""{"data":{"system_notify_list":[{"id":1}]}}"""))))
    }
}
