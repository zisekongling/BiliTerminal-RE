package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.FeedbackItem
import com.RobinNotBad.BiliClient.model.FeedbackReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FeedbackState] 的三态派生。
 *
 * 钉住两条容易被「顺手改坏」的约定：
 * 1. 服务端给了合法 state 就**原样**用它（本地不重新推断，否则两端口径会漂）；
 * 2. 老服务端没给 state 时按回复情况回落，**绝不看 handled**（handled 是开发者内部待办，
 *    拿它判状态会对用户说谎）。
 */
class FeedbackStateTest {

    private fun dev(id: Int) =
        FeedbackReply(id, "开发者回复", "内容", "2026-11-16 12:00", FeedbackReply.AUTHOR_DEV)

    private fun user(id: Int) =
        FeedbackReply(id, "", "用户补充", "2026-11-16 13:00", FeedbackReply.AUTHOR_USER)

    private fun item(state: String?, handled: Boolean, vararg replies: FeedbackReply) =
        FeedbackItem(1, "bug", "正文", "", "2026-11-16 11:00", handled,
            ArrayList(replies.toList()), state)

    // ==================== isValid ====================

    @Test
    fun isValid_acceptsThreeStates() {
        assertTrue(FeedbackState.isValid(FeedbackState.WAITING_DEV))
        assertTrue(FeedbackState.isValid(FeedbackState.REPLIED))
        assertTrue(FeedbackState.isValid(FeedbackState.CLOSED))
    }

    @Test
    fun isValid_rejectsEmptyAndUnknown() {
        assertFalse(FeedbackState.isValid(null))
        assertFalse(FeedbackState.isValid(""))
        assertFalse(FeedbackState.isValid("handled"))
        assertFalse(FeedbackState.isValid("WAITING_DEV"))
    }

    // ==================== hasDevReply ====================

    @Test
    fun hasDevReply_falseWhenEmpty() {
        assertFalse(FeedbackState.hasDevReply(null))
        assertFalse(FeedbackState.hasDevReply(emptyList()))
    }

    @Test
    fun hasDevReply_ignoresUserReplies() {
        assertFalse(FeedbackState.hasDevReply(listOf(user(1), user(2))))
        assertTrue(FeedbackState.hasDevReply(listOf(user(1), dev(2))))
    }

    // ==================== resolve：服务端给了合法值 ====================

    @Test
    fun resolve_keepsServerState() {
        // 即便时间线上最后一条是用户、最新回复也是开发者，服务端的判断优先
        assertEquals(FeedbackState.CLOSED,
            FeedbackState.resolve(FeedbackState.CLOSED, listOf(dev(1))))
        assertEquals(FeedbackState.WAITING_DEV,
            FeedbackState.resolve(FeedbackState.WAITING_DEV, listOf(dev(1))))
        assertEquals(FeedbackState.REPLIED,
            FeedbackState.resolve(FeedbackState.REPLIED, emptyList()))
    }

    // ==================== resolve：老服务端回落 ====================

    @Test
    fun resolve_fallsBackToWaitingWhenNoReplies() {
        assertEquals(FeedbackState.WAITING_DEV, FeedbackState.resolve("", null))
        assertEquals(FeedbackState.WAITING_DEV, FeedbackState.resolve(null, emptyList()))
    }

    @Test
    fun resolve_fallsBackToRepliedWhenDevReplied() {
        assertEquals(FeedbackState.REPLIED, FeedbackState.resolve("", listOf(dev(1))))
        assertEquals(FeedbackState.REPLIED, FeedbackState.resolve(null, listOf(user(1), dev(2))))
    }

    @Test
    fun resolve_fallsBackToWaitingWhenOnlyUserReplied() {
        assertEquals(FeedbackState.WAITING_DEV, FeedbackState.resolve("", listOf(user(1))))
    }

    @Test
    fun resolve_fallsBackForUnknownState() {
        assertEquals(FeedbackState.REPLIED, FeedbackState.resolve("done", listOf(dev(1))))
    }

    @Test
    fun resolve_ignoresHandledOnFallback() {
        // handled=true（开发者已受理）但一条回复都没有：对用户来说仍然是「待回复」
        assertEquals(FeedbackState.WAITING_DEV, FeedbackState.resolve("", emptyList()))
        val handledOnly = item("", true)
        assertEquals(FeedbackState.WAITING_DEV, FeedbackState.resolve(handledOnly))
    }

    // ==================== resolve(item) ====================

    @Test
    fun resolve_itemUsesStateField() {
        assertEquals(FeedbackState.CLOSED, FeedbackState.resolve(item(FeedbackState.CLOSED, false)))
        // 未知串（如未来服务端新增的第四态）+ 有开发者回复 + handled=true：回落应为 REPLIED，
        // 且 handled 不参与判断
        assertEquals(FeedbackState.REPLIED, FeedbackState.resolve(item("weird", true, dev(3))))
        // 老服务端：state 落空串且还没人回过 → 待回复
        assertEquals(FeedbackState.WAITING_DEV, FeedbackState.resolve(item("", true)))
        assertEquals(FeedbackState.REPLIED, FeedbackState.resolve(item(null, false, dev(3))))
    }

    @Test
    fun resolve_nullItemIsWaiting() {
        assertEquals(FeedbackState.WAITING_DEV, FeedbackState.resolve(null))
    }
}
