package com.RobinNotBad.BiliClient.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 私信会话列表解析测试。
 *
 * 重点覆盖 account_info 过滤：该字段仅在系统会话中出现，
 * 旧实现 `!has && isNull` 恒等于 `!has`，会把 account_info 显式为 null 的普通会话一起丢弃。
 */
class PrivateMsgApiTest {

    private fun sessionJson(
        talkerId: Long,
        unread: Int = 0,
        accountInfo: String = "",
        lastMsg: String = ""
    ): String = buildString {
        append("""{"talker_id":$talkerId,"unread_count":$unread""")
        if (accountInfo.isNotEmpty()) append(""","account_info":$accountInfo""")
        if (lastMsg.isNotEmpty()) append(""","last_msg":$lastMsg""")
        append("}")
    }

    private fun rootWith(vararg sessions: String): JSONObject =
        JSONObject("""{"code":0,"data":{"session_list":[${sessions.joinToString(",")}]}}""")

    @Test
    fun parseSessionsList_普通会话_全部保留() {
        val root = rootWith(
            sessionJson(1001L, unread = 2),
            sessionJson(1002L, unread = 0)
        )

        val list = PrivateMsgApi.parseSessionsList(root)

        assertEquals("两个普通会话都应保留", 2, list.size)
        assertEquals(1001L, list[0].talkerUid)
        assertEquals(2, list[0].unread)
    }

    @Test
    fun parseSessionsList_accountInfo为null_应保留() {
        // 回归用例：字段存在但值为 null 的是普通会话，不能被过滤掉
        val root = rootWith(
            sessionJson(2001L, accountInfo = "null"),
            sessionJson(2002L)
        )

        val list = PrivateMsgApi.parseSessionsList(root)

        assertEquals("account_info 为 null 的会话应保留", 2, list.size)
    }

    @Test
    fun parseSessionsList_系统会话_应被过滤() {
        val root = rootWith(
            sessionJson(3001L, accountInfo = """{"name":"系统消息"}"""),
            sessionJson(3002L)
        )

        val list = PrivateMsgApi.parseSessionsList(root)

        assertEquals("系统会话应被过滤", 1, list.size)
        assertEquals(3002L, list[0].talkerUid)
    }

    @Test
    fun parseSessionsList_解析lastMsg与未读数() {
        val root = rootWith(
            sessionJson(4001L, unread = 5, lastMsg = """{"msg_type":1,"content":"你好"}"""),
            sessionJson(4002L, unread = 0, lastMsg = """{"msg_type":7,"content":"{\"text\":\"卡片\"}"}""")
        )

        val list = PrivateMsgApi.parseSessionsList(root)

        assertEquals(1, list[0].contentType)
        assertEquals(5, list[0].unread)
        assertNull("纯文本内容不应解析成 JSONObject", list[0].content)
        assertEquals(7, list[1].contentType)
        assertEquals("卡片", list[1].content?.optString("text"))
    }

    @Test
    fun parseSessionsList_data为null_返回空列表() {
        val root = JSONObject("""{"code":0,"data":null}""")

        assertTrue("data 为 null 时应返回空列表", PrivateMsgApi.parseSessionsList(root).isEmpty())
    }

    @Test
    fun parseSessionsList_null输入_返回空列表() {
        assertTrue("null 输入应返回空列表", PrivateMsgApi.parseSessionsList(null).isEmpty())
    }
}
