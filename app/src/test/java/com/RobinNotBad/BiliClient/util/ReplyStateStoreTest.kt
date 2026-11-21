package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.Reply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 评论跨页状态共享存储的单测。
 *
 * 这里错一个字段，用户看到的就是「在详情页点了赞，退回列表还是没赞」或者
 * 「删掉的评论刷新一下又活了」。所以下面每条断言都对应一个具体症状。
 */
class ReplyStateStoreTest {

    private val account = 1001L
    private val type = 1
    private val oid = 42L

    private fun reply(
        rpid: Long,
        root: Long = 0L,
        likeCount: Int = 0,
        liked: Boolean = false,
        disliked: Boolean = false
    ) = Reply().apply {
        this.rpid = rpid
        this.root = root
        this.likeCount = likeCount
        this.liked = liked
        this.disliked = disliked
    }

    private fun store() = ReplyStateStore(8)

    // ---- 服务端快照合并 ----

    @Test
    fun observe_firstSnapshotIsSilentThenNotifiesOnChange() {
        val store = store()
        var notified = 0
        store.addListener(object : ReplyStateStore.Listener {
            override fun onReplyStateChanged(state: ReplyState) {
                notified++
            }
        })

        store.observe(account, type, oid, 100L, 0L, false, false, 7, store.beginRead())
        val state = store.current(account, type, oid, 100L)
        assertEquals("服务端赞数要落库", 7, state!!.likeCount)
        assertFalse(state.deleted)
        assertEquals("首次见到这条评论时没有人在看它，不该通知", 0, notified)

        // 内容没变再 observe 一次：不该把整行重新绑一遍
        store.observe(account, type, oid, 100L, 0L, false, false, 7, store.beginRead())
        assertEquals("内容没变化不该通知", 0, notified)

        // 内容真的变了（别处点了赞）：必须通知一次
        store.observe(account, type, oid, 100L, 0L, true, false, 8, store.beginRead())
        assertEquals("内容变化要通知一次", 1, notified)
    }

    @Test
    fun observe_keepsLocalReactionWhenServerResponseIsLate() {
        val store = store()
        // 请求发出时记下修订号
        val read = store.beginRead()
        // 响应回来之前用户点了赞
        store.recordReaction(account, type, oid, 100L, 0L, true, false, 8)
        // 晚到的服务端快照还是"没赞、7 个赞"：不能把用户的操作冲掉
        store.observe(account, type, oid, 100L, 0L, false, false, 7, read)

        val state = store.current(account, type, oid, 100L)!!
        assertTrue("晚到的响应不能把刚点的赞冲掉", state.liked)
        assertTrue("赞数也要保留本地结果", state.likeCount == 8)
    }

    @Test
    fun observe_keepsDeletedEvenIfServerListStillReturnsTheReply() {
        val store = store()
        store.markDeleted(account, type, oid, 100L, 0L)
        // 服务端列表还没同步删除，刷新时又把这条吐了回来
        store.observe(account, type, oid, 100L, 0L, false, false, 5, store.beginRead())
        assertTrue("本地已删除的不能被服务端列表复活", store.current(account, type, oid, 100L)!!.deleted)
    }

    @Test
    fun observe_newerReadOverridesStaleLocalState() {
        val store = store()
        store.recordReaction(account, type, oid, 100L, 0L, true, false, 8)
        // 这次读发生在本地动作之后（修订号更新），应当以服务端为准
        val read = store.beginRead()
        store.observe(account, type, oid, 100L, 0L, false, false, 6, read)

        val state = store.current(account, type, oid, 100L)!!
        assertFalse("更新的读要能覆盖旧状态（例如别处已经取消赞）", state.liked)
        assertEquals(6, state.likeCount)
    }

    // ---- 本地动作写入 ----

    @Test
    fun recordReaction_bumpsRevisionAndKeepsDislikeExclusive() {
        val store = store()
        val before = store.beginRead()
        val state = store.recordReaction(account, type, oid, 100L, 0L, false, true, 3)
        assertTrue("点踩要落库", state.disliked)
        assertFalse(state.liked)
        assertTrue("修订号必须自增，晚到的读才有据可依", state.revision > before)
    }

    @Test
    fun markDeleted_thenApplyToHidesTheReply() {
        val store = store()
        store.markDeleted(account, type, oid, 100L, 0L)

        val target = reply(100L, likeCount = 9)
        assertTrue("applyTo 返回 true 表示调用方要把它从列表里摘掉", store.applyTo(target, account, type, oid))
        assertEquals("删除后赞数归零", 0, target.likeCount)
    }

    @Test
    fun applyTo_copiesLikeStateWithoutDeleting() {
        val store = store()
        store.recordReaction(account, type, oid, 100L, 0L, true, false, 5)

        val target = reply(100L)
        assertFalse("只是点赞，不该被当成删除", store.applyTo(target, account, type, oid))
        assertTrue(target.liked)
        assertEquals(5, target.likeCount)
    }

    @Test
    fun applyTo_ignoresUnknownReply() {
        val store = store()
        assertFalse("没记录过的评论保持原样", store.applyTo(reply(999L), account, type, oid))
    }

    // ---- 整页应用：删除的条目要从列表消失 ----

    @Test
    fun applyLatest_removesDeletedTopLevelReply() {
        val store = store()
        store.markDeleted(account, type, oid, 200L, 0L)

        val list = mutableListOf(reply(100L), reply(200L), reply(300L))
        assertTrue(store.applyLatest(list, account, type, oid))
        assertEquals("被删的那条要从列表消失", listOf(100L, 300L), list.map { it.rpid })
    }

    @Test
    fun applyLatest_removesDeletedChildAndFixesChildCount() {
        val store = store()
        store.markDeleted(account, type, oid, 101L, 100L)

        val parent = reply(100L).apply {
            childCount = 2
            childMsgList.add(reply(101L, root = 100L))
            childMsgList.add(reply(102L, root = 100L))
        }
        val list = mutableListOf(parent)
        assertTrue(store.applyLatest(list, account, type, oid))

        assertEquals("楼中楼里被删的也要摘掉", listOf(102L), parent.childMsgList.map { it.rpid })
        assertEquals("『共 N 条回复』要跟着减，否则数字对不上", 1, parent.childCount)
    }

    @Test
    fun applyLatest_untouchedListReportsNoChange() {
        val store = store()
        val list = mutableListOf(reply(100L))
        assertFalse("没有可删的就不该让调用方白 notify 一次", store.applyLatest(list, account, type, oid))
        assertFalse(store.applyLatest(null, account, type, oid))
    }

    // ---- 监听器 ----

    @Test
    fun listeners_canBeRemovedAndExceptionsDoNotBreakOthers() {
        val store = store()
        var first = 0
        var second = 0
        val firstListener = object : ReplyStateStore.Listener {
            override fun onReplyStateChanged(state: ReplyState) {
                first++
            }
        }
        val secondListener = object : ReplyStateStore.Listener {
            override fun onReplyStateChanged(state: ReplyState) {
                second++
            }
        }
        store.addListener(firstListener)
        store.addListener(secondListener)

        store.recordReaction(account, type, oid, 100L, 0L, true, false, 1)
        assertEquals(1, first)
        assertEquals(1, second)

        store.removeListener(firstListener)
        store.recordReaction(account, type, oid, 100L, 0L, false, false, 0)
        assertEquals("摘掉的监听器不该再收到回调（页面销毁后必须能停）", 1, first)
        assertEquals(2, second)
    }

    @Test
    fun thrashingListenerDoesNotStopOtherListeners() {
        val store = store()
        var survived = 0
        store.addListener(object : ReplyStateStore.Listener {
            override fun onReplyStateChanged(state: ReplyState) {
                throw IllegalStateException("页面已销毁")
            }
        })
        store.addListener(object : ReplyStateStore.Listener {
            override fun onReplyStateChanged(state: ReplyState) {
                survived++
            }
        })
        store.recordReaction(account, type, oid, 100L, 0L, true, false, 1)
        assertEquals("一个页面崩了不能拖累其它页面", 1, survived)
    }

    // ---- 容量与 key ----

    @Test
    fun lru_evictsLeastRecentlyUsedBeyondCapacity() {
        val store = ReplyStateStore(2)
        store.observe(account, type, oid, 1L, 0L, false, false, 0, store.beginRead())
        store.observe(account, type, oid, 2L, 0L, false, false, 0, store.beginRead())
        store.observe(account, type, oid, 3L, 0L, false, false, 0, store.beginRead())

        assertNull("超容量先淘汰最早那条，防止长期刷评论区把内存吃掉", store.current(account, type, oid, 1L))
        assertTrue(store.current(account, type, oid, 2L) != null)
        assertTrue(store.current(account, type, oid, 3L) != null)
    }

    @Test
    fun keyOf_doesNotCollideAcrossFields() {
        assertNotEquals(
            "oid/rpid 拼接不能互相冒充",
            ReplyStateStore.keyOf(1, 1, 23, 4),
            ReplyStateStore.keyOf(1, 1, 2, 34)
        )
        assertNotEquals(
            "账号维度必须参与 key，切号不能串状态",
            ReplyStateStore.keyOf(1, 1, 2, 3),
            ReplyStateStore.keyOf(2, 1, 2, 3)
        )
    }
}
