package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.api.ReplyApi
import com.RobinNotBad.BiliClient.model.Reply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 评论区「三层兜底」策略的纯逻辑单测。
 *
 * 这些判定决定了界面在服务端抽风时的表现：该重试的没重试 → 用户看到空白评论区；
 * 不该重试的重试了 → 请求风暴 / 用错类型去拉数据。全是纯函数与纯内存缓存，可直接在 JVM 上钉死。
 */
class ReplyFetchPolicyTest {

    // ---- 换评论区类型：主类型必须被排除，顺序不能乱 ----

    @Test
    fun alternativeReplyTypes_excludesPrimaryAndKeepsPriorityOrder() {
        assertEquals(
            "视频评论区兜底顺序是 动态 → 专栏",
            listOf(ReplyApi.REPLY_TYPE_DYNAMIC, ReplyApi.REPLY_TYPE_ARTICLE),
            ReplyFetchPolicy.alternativeReplyTypes(ReplyApi.REPLY_TYPE_VIDEO)
        )
        assertEquals(
            "动态评论区不该再试动态",
            listOf(ReplyApi.REPLY_TYPE_ARTICLE, ReplyApi.REPLY_TYPE_VIDEO),
            ReplyFetchPolicy.alternativeReplyTypes(ReplyApi.REPLY_TYPE_DYNAMIC)
        )
        assertEquals(
            "专栏评论区同理",
            listOf(ReplyApi.REPLY_TYPE_DYNAMIC, ReplyApi.REPLY_TYPE_VIDEO),
            ReplyFetchPolicy.alternativeReplyTypes(ReplyApi.REPLY_TYPE_ARTICLE)
        )
    }

    @Test
    fun alternativeReplyTypes_unknownPrimaryTriesAll() {
        // 冷门类型（如楼中楼 type=0）不在兜底表里时，三个候选都要试，不能因为"没匹配上"就一个都不试
        assertEquals(
            listOf(
                ReplyApi.REPLY_TYPE_DYNAMIC,
                ReplyApi.REPLY_TYPE_ARTICLE,
                ReplyApi.REPLY_TYPE_VIDEO
            ),
            ReplyFetchPolicy.alternativeReplyTypes(ReplyApi.REPLY_TYPE_VIDEO_CHILD)
        )
    }

    // ---- 首屏兜底计划：先换排序，再换类型 ----

    @Test
    fun firstPageFallbackPlan_sortFirstThenType() {
        val plan = ReplyFetchPolicy.firstPageFallbackPlan(ReplyApi.REPLY_TYPE_VIDEO, Reply.SORT_LIKE)
        assertEquals("最多三次尝试", 3, plan.size)
        assertEquals(
            "第一步必须先换『按时间』：热度档位对冷门内容常回空页，换类型是更贵的一步",
            ReplyFetchPolicy.FirstPageAttempt(ReplyApi.REPLY_TYPE_VIDEO, Reply.SORT_TIME),
            plan[0]
        )
        assertEquals(ReplyApi.REPLY_TYPE_DYNAMIC, plan[1].type)
        assertEquals(ReplyApi.REPLY_TYPE_ARTICLE, plan[2].type)
        for (attempt in plan) {
            assertEquals("兜底一律用按时间排序", Reply.SORT_TIME, attempt.sort)
        }
    }

    @Test
    fun firstPageFallbackPlan_alreadyTimeSortedSkipsDuplicateSortAttempt() {
        // 已经是按时间排了，再"换成按时间"就是原样重试一次，纯浪费请求
        val plan = ReplyFetchPolicy.firstPageFallbackPlan(ReplyApi.REPLY_TYPE_VIDEO, Reply.SORT_TIME)
        assertEquals(2, plan.size)
        assertEquals(listOf(ReplyApi.REPLY_TYPE_DYNAMIC, ReplyApi.REPLY_TYPE_ARTICLE), plan.map { it.type })
    }

    // ---- 要不要进入首屏兜底 ----

    @Test
    fun shouldTryFirstPageFallback_onlyWhenEmptyButCountSaysThereAreReplies() {
        assertTrue("空列表 + 服务端说有评论 + 不是 seek：该重试", ReplyFetchPolicy.shouldTryFirstPageFallback(0, 10, 0))
    }

    @Test
    fun shouldTryFirstPageFallback_rejectsEverySingleCondition() {
        assertFalse("列表有内容就不该重拉", ReplyFetchPolicy.shouldTryFirstPageFallback(3, 10, 0))
        assertFalse("本来就没人评论，重试三次只是浪费请求", ReplyFetchPolicy.shouldTryFirstPageFallback(0, 0, 0))
        assertFalse("评论数未知（负数/脏数据）也不该猜着重试", ReplyFetchPolicy.shouldTryFirstPageFallback(0, -1, 0))
        assertFalse(
            "seek 模式请求的是一段楼中楼，换排序/换类型会破坏定位",
            ReplyFetchPolicy.shouldTryFirstPageFallback(0, 10, 123456L)
        )
    }

    // ---- 本地补回缓存 ----

    @Test
    fun localCache_mergesInSendOrder() {
        val cache = LocalReplyCache()
        cache.remember(reply(1), 1_000L)
        cache.remember(reply(2), 2_000L)

        val target = ArrayList<Reply>()
        val merged = cache.mergeInto(target, 3_000L)
        assertEquals("先发的在上、后发的在下", listOf(1L, 2L), target.map { it.rpid })
        assertEquals(2, merged.size)
        // 返回值的顺序以前是插入顺序（与最终顺序相反），拿它去登记「已加载 rpid」虽不受影响，
        // 但任何按顺序使用的调用方都会拿到倒序列表
        assertEquals("返回值顺序必须与列表最终顺序一致", listOf(1L, 2L), merged.map { it.rpid })
    }

    @Test
    fun localCache_skipsRepliesAlreadyInTheServerList() {
        val cache = LocalReplyCache()
        cache.remember(reply(1), 1_000L)
        cache.remember(reply(2), 1_000L)

        // 服务端已经同步到了 2，只剩 1 需要补
        val target = arrayListOf(reply(2))
        val merged = cache.mergeInto(target, 2_000L)
        assertEquals("服务端已有的不能被补成第二条；本地补回插在列表顶部（自己刚发的在最上面）", listOf(1L, 2L), target.map { it.rpid })
        assertEquals(listOf(1L), merged.map { it.rpid })
    }

    @Test
    fun localCache_skipsRepliesAlreadyInsideChildList() {
        val cache = LocalReplyCache()
        cache.remember(reply(11), 1_000L)

        // 楼中楼里已经有这条：再补到顶层就变成"同一回复出现两次"，还挂错了父评论
        val parent = reply(10).apply { childMsgList.add(reply(11)) }
        val target = arrayListOf(parent)
        assertTrue(cache.mergeInto(target, 2_000L).isEmpty())
        assertEquals("列表没被改动", 1, target.size)
    }

    @Test
    fun localCache_dropsExpiredEntries() {
        val cache = LocalReplyCache()
        cache.remember(reply(1), 0L)

        assertTrue("超过窗口就该认为服务端早已同步", cache.mergeInto(ArrayList(), ReplyFetchPolicy.LOCAL_REPLY_WINDOW_MS + 1).isEmpty())
        assertEquals(0, cache.size(ReplyFetchPolicy.LOCAL_REPLY_WINDOW_MS + 1))
    }

    @Test
    fun localCache_ignoresInvalidRpid() {
        val cache = LocalReplyCache()
        cache.remember(reply(0), 0L)
        cache.remember(reply(-1), 0L)
        // 没有 id 就无法去重，补回去只会制造重复
        assertEquals(0, cache.size(0L))
    }

    @Test
    fun localCache_respectsMaxSize() {
        val cache = LocalReplyCache(maxSize = 2)
        cache.remember(reply(1), 0L)
        cache.remember(reply(2), 0L)
        cache.remember(reply(3), 0L)

        val target = ArrayList<Reply>()
        cache.mergeInto(target, 100L)
        assertEquals("超上限先丢最早的", listOf(2L, 3L), target.map { it.rpid })
    }

    @Test
    fun localCache_clearRemovesEverything() {
        val cache = LocalReplyCache()
        cache.remember(reply(1), 0L)
        cache.clear()
        assertEquals(0, cache.size(0L))
        assertTrue(cache.mergeInto(ArrayList(), 0L).isEmpty())
    }

    @Test
    fun localCache_rememberSameRpidTwiceKeepsSingleEntry() {
        // EventBus sticky 事件会重投递，同一条不能进两次
        val cache = LocalReplyCache()
        cache.remember(reply(1), 0L)
        cache.remember(reply(1), 100L)
        assertEquals(1, cache.size(100L))
    }

    private fun reply(rpid: Long) = Reply().apply {
        this.rpid = rpid
        this.root = 0L
    }
}
