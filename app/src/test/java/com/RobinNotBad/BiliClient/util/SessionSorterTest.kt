package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.PrivateMsgSession
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [SessionSorter] 的单测。
 *
 * 重点是回归「置顶会话被未读排序挤到下面」这个线上问题：
 * 服务端返回的顺序已经是「置顶在前 + 时间倒序」，本地排序**不能**把置顶项挪下去。
 */
class SessionSorterTest {

    private fun session(
        uid: Long,
        unread: Int = 0,
        topTs: Long = 0,
        bizUnread: Int = 0,
        systemMsgType: Int = 0,
        accountName: String = ""
    ): PrivateMsgSession {
        val s = PrivateMsgSession()
        s.talkerUid = uid
        s.unread = unread
        s.topTs = topTs
        s.bizUnread = bizUnread
        s.systemMsgType = systemMsgType
        s.accountName = accountName
        return s
    }

    private fun uids(list: List<PrivateMsgSession>): List<Long> = list.map { it.talkerUid }

    @Test
    fun `置顶会话排在最前，即使它没有未读`() {
        // 服务端原序：未读的普通会话在前，置顶但已读的会话在后
        val list = mutableListOf(
            session(uid = 1, unread = 3),
            session(uid = 2, unread = 0, topTs = 1000),
        )

        SessionSorter.sort(list)

        // 这正是修复前会排错的地方：旧逻辑会把 uid=1 排到前面
        assertEquals(listOf(2L, 1L), uids(list))
    }

    @Test
    fun `两个置顶会话按置顶时间倒序`() {
        val list = mutableListOf(
            session(uid = 1, topTs = 1000),
            session(uid = 2, topTs = 5000),
        )

        SessionSorter.sort(list)

        assertEquals(listOf(2L, 1L), uids(list))
    }

    @Test
    fun `都未置顶时有未读的排前面`() {
        val list = mutableListOf(
            session(uid = 1, unread = 0),
            session(uid = 2, unread = 1),
        )

        SessionSorter.sort(list)

        assertEquals(listOf(2L, 1L), uids(list))
    }

    @Test
    fun `系统会话只有通知类未读时也要排到前面`() {
        // 系统会话（UP主小助手 / 系统通知…）的 unread_count 常为 0，未读记在 biz_msg_unread_count。
        // 旧逻辑只看 unread，会把「有 3 条官方未读」的会话当成已读排到后面（26.11.20）
        val list = mutableListOf(
            session(uid = 1, unread = 0),
            session(uid = 2, unread = 0, bizUnread = 3, systemMsgType = 7, accountName = "UP主小助手"),
        )

        SessionSorter.sort(list)

        assertEquals(listOf(2L, 1L), uids(list))
    }

    @Test
    fun `未读会话不会越过置顶会话`() {
        val list = mutableListOf(
            session(uid = 1, unread = 9),
            session(uid = 2, unread = 0, topTs = 1),
            session(uid = 3, unread = 5),
        )

        SessionSorter.sort(list)

        assertEquals(2L, list.first().talkerUid)
    }

    @Test
    fun `排序是稳定的，同优先级保持服务端原序`() {
        val list = mutableListOf(
            session(uid = 1),
            session(uid = 2),
            session(uid = 3),
        )

        SessionSorter.sort(list)

        assertEquals(listOf(1L, 2L, 3L), uids(list))
    }

    @Test
    fun `空列表与单元素列表不炸`() {
        val empty = mutableListOf<PrivateMsgSession>()
        SessionSorter.sort(empty)
        assertEquals(0, empty.size)

        val single = mutableListOf(session(uid = 7))
        SessionSorter.sort(single)
        assertEquals(listOf(7L), uids(single))
    }

    @Test
    fun `topTs 为 0 视为未置顶`() {
        val list = mutableListOf(
            session(uid = 1, topTs = 0),
            session(uid = 2, topTs = 1),
        )

        SessionSorter.sort(list)

        assertEquals(listOf(2L, 1L), uids(list))
    }
}
