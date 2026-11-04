package com.RobinNotBad.BiliClient.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `PrivateMsgApi.getUsersInfo` 的 uid 参数拼接（反馈 #84）。
 *
 * 关键回归：uidList 为空时旧实现 `userString.substring(0, -1)` 抛
 * `StringIndexOutOfBoundsException`，消息页每次打开都弹「Adapter错误」。
 * 现在空列表返回空串、`getUsersInfo` 据此直接返回空 map，不发请求。
 */
class PrivateMsgUserCardsQueryTest {

    @Test
    fun buildUidsParam_空列表返回空串且不抛异常() {
        assertEquals("", PrivateMsgApi.buildUidsParam(ArrayList<Long>()))
    }

    @Test
    fun buildUidsParam_null返回空串() {
        assertEquals("", PrivateMsgApi.buildUidsParam(null))
    }

    @Test
    fun buildUidsParam_单个uid() {
        assertEquals("12345", PrivateMsgApi.buildUidsParam(arrayListOf(12345L)))
    }

    @Test
    fun buildUidsParam_多个uid逗号分隔且无尾逗号() {
        val param = PrivateMsgApi.buildUidsParam(arrayListOf(1001L, 1002L, 1003L))
        assertEquals("1001,1002,1003", param)
        assertTrue("旧实现会多拼一个逗号再砍掉，新实现直接不能有尾逗号", !param.endsWith(","))
    }

    @Test
    fun buildUidsParam_保留入参顺序() {
        // uid 顺序不影响 user/cards 的语义，但保持顺序能让日志/断点更好对照
        assertEquals("9,3,7", PrivateMsgApi.buildUidsParam(arrayListOf(9L, 3L, 7L)))
    }

    @Test
    fun getUsersInfo_空列表返回空map且不发请求() {
        // #84 的直接回归：旧实现在这里 substring(0, -1) 抛 StringIndexOutOfBoundsException，
        // 消息页每次打开都弹「Adapter错误」。现在必须在发请求之前就返回空 map。
        val map = PrivateMsgApi.getUsersInfo(ArrayList<Long>())
        assertTrue("空列表应返回空 map", map.isEmpty())
    }

    @Test
    fun buildUidsParam_与旧实现非空输入结果一致() {
        // 非空输入时 URL 必须与改动前逐字一致，避免悄悄改变请求参数
        val uids = arrayListOf(1L, 22L, 333L)
        // 旧实现等价写法：每个 uid 后拼逗号，再砍掉最后一个字符
        val oldStyle = buildString {
            for (uid in uids) append(uid).append(",")
        }.dropLast(1)
        assertEquals(oldStyle, PrivateMsgApi.buildUidsParam(uids))
    }
}
