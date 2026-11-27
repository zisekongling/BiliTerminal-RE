package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.FeedbackItem
import com.RobinNotBad.BiliClient.model.FeedbackReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FeedbackUnread] 的纯 JVM 单测（26.11.16）。
 *
 * 背景：开发者回复后用户完全不知情——服务端把回复写成定向公告，客户端只能自己点进
 * 「我的反馈」详情才看得到。这里的「已读」判据是**开发者回复 id 的水位线**：
 * 回复 id = 公告 id + 1e9，天然单调递增，所以「最新一条开发者回复的 id 大于已读水位线」
 * 就等于「有没看过的新回复」，不需要额外的布尔量。
 *
 * 这里钉住三件事：
 * 1. 水位线只算**开发者**的消息，用户自己追加的说明不能把他自己的未读顶掉；
 * 2. 水位线相等算已读（读详情时会推到最新一条）；
 * 3. 一条回复都没有的反馈不算未读。
 */
class FeedbackUnreadTest {

    private fun dev(id: Int) = FeedbackReply(id, "开发者回复", "内容", "2026-11-16 12:00", FeedbackReply.AUTHOR_DEV)

    private fun user(id: Int) = FeedbackReply(id, "", "用户补充", "2026-11-16 13:00", FeedbackReply.AUTHOR_USER)

    private fun item(id: Int, vararg replies: FeedbackReply) =
        FeedbackItem(id, "bug", "正文", "", "2026-11-16 11:00", false, ArrayList(replies.toList()), "")

    // ==================== latestDevReplyId ====================

    @Test
    fun noReplies_returnsZero() {
        assertEquals(0, FeedbackUnread.latestDevReplyId(emptyList()))
        assertEquals(0, FeedbackUnread.latestDevReplyId(null))
    }

    @Test
    fun onlyUserReplies_returnsZero() {
        assertEquals(
            "只有用户自己追加的说明时没有「开发者回复」可读，水位线保持 0",
            0,
            FeedbackUnread.latestDevReplyId(listOf(user(1000000001), user(1000000002)))
        )
    }

    @Test
    fun mixedReplies_returnsLatestDevId() {
        assertEquals(
            "用户消息的 id 更大，但它不是开发者回复，不能当水位线",
            1000000007,
            FeedbackUnread.latestDevReplyId(
                listOf(dev(1000000005), user(1000000009), dev(1000000007))
            )
        )
    }

    // ==================== isUnread ====================

    @Test
    fun newerDevReplyThanSeen_isUnread() {
        assertTrue(
            "最新开发者回复比已读水位线更新，是未读",
            FeedbackUnread.isUnread(item(1, dev(1000000042)), 1000000041)
        )
    }

    @Test
    fun sameIdAsSeen_isRead() {
        assertFalse(
            "水位线相等表示这条已经看过了（详情页会把水位线推到最新一条）",
            FeedbackUnread.isUnread(item(1, dev(1000000042)), 1000000042)
        )
    }

    @Test
    fun nullItem_isNotUnread() {
        assertFalse(FeedbackUnread.isUnread(null, 0))
    }

    @Test
    fun userReplyAfterDevReply_doesNotBecomeUnread() {
        assertFalse(
            "用户自己后来又说了话，不该被算成「开发者有新回复」",
            FeedbackUnread.isUnread(item(1, dev(1000000050), user(1000000060)), 1000000050)
        )
    }

    // ==================== countUnread ====================

    @Test
    fun countUnread_countsOnlyItemsAboveWatermark() {
        val items = listOf(
            item(1, dev(1000000001)),
            item(2, dev(1000000010)),
            item(3, dev(1000000020)),
            item(4)
        )
        // 已读水位线：1 号看过、2 号没看过、3 号没看过、4 号没有回复
        val seen = mapOf(1 to 1000000001, 2 to 1000000005, 3 to 0, 4 to 0)
        assertEquals(2, FeedbackUnread.countUnread(items) { id -> seen[id] ?: 0 })
    }

    @Test
    fun countUnread_emptyOrNull_isZero() {
        assertEquals(0, FeedbackUnread.countUnread(emptyList()) { 0 })
        assertEquals(0, FeedbackUnread.countUnread(null) { 0 })
    }
}
