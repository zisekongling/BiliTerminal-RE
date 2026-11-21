package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * 菜单未读角标刷新的纯逻辑测试（E 未读提示补齐）。
 *
 * <p>这个类的每一处判据错判都**不会报错**，只会表现为「角标不动」「多打接口」「弹通知重复」
 * 这类用户才看得见的现象，所以必须在这里锁死：
 * - 未登录不该发请求；
 * - 最小间隔与单飞（并发下只能有一次在飞）；
 * - 切号 / 登出后回来的旧响应必须丢弃（否则把别人的未读数记到当前账号头上）；
 * - 检查失败不清零（清零会让下次成功把「老未读」当新增，重复弹通知）。
 *
 * <p>只测纯逻辑，不碰同文件的 [MessageUnreadRefresher]（那个 object 会读 SP / 发网络）。
 */
class MessageUnreadRefreshTest {

    private val acc = 111L
    private val other = 222L

    @Test
    fun begin_未登录不刷() {
        val state = MessageUnreadRefresh()
        assertNull("mid=0 时不该发请求", state.begin(0, 1_000))
        assertNull("mid 为负数同样不刷", state.begin(-5, 1_000))
        assertFalse(state.isInFlight())
        assertEquals(0, state.count(acc))
    }

    @Test
    fun complete_首刷成功后给出总数() {
        val state = MessageUnreadRefresh()
        assertEquals("还没刷过时角标是 0", 0, state.count(acc))
        val ticket = state.begin(acc, 1_000)
        assertNotNull(ticket)
        assertEquals("私信 4 + 其它 3", 7, state.complete(ticket, acc, 3, 4))
        assertEquals(7, state.count(acc))
        assertFalse(state.isInFlight())
    }

    @Test
    fun begin_同一时刻只允许一次在飞() {
        val state = MessageUnreadRefresh()
        val first = state.begin(acc, 1_000)
        assertNotNull(first)
        assertNull("第一次还没回来，第二次必须被单飞挡下", state.begin(acc, 1_001))
        state.complete(first, acc, 1, 1)
        // 收尾后单飞闩锁已放开；这里必须跨过 5 秒最小间隔才能看到「能再发」，
        // 间隔内的拦截由 begin_最小间隔内不重复请求 单独钉住。
        assertNotNull("上一次收尾后应能再发", state.begin(acc, 6_000))
    }

    @Test
    fun begin_最小间隔内不重复请求() {
        val state = MessageUnreadRefresh(5_000)
        val first = state.begin(acc, 10_000)
        assertNotNull(first)
        state.complete(first, acc, 1, 0)
        assertNull("间隔不足 5 秒不该再刷", state.begin(acc, 14_999))
        assertNotNull("满 5 秒可以再刷", state.begin(acc, 15_000))
    }

    @Test
    fun begin_force忽略最小间隔但不忽略单飞() {
        val state = MessageUnreadRefresh(5_000)
        val first = state.begin(acc, 0, force = true)
        assertNotNull(first)
        state.complete(first, acc, 1, 0)
        // 单飞已经放开，但最小间隔仍然拦着普通刷新
        assertNull(state.begin(acc, 1))
        assertNotNull("冷启动那一次要拉新的，不受间隔限制", state.begin(acc, 1, force = true))
    }

    @Test
    fun begin_换号不受最小间隔限制() {
        val state = MessageUnreadRefresh(5_000)
        val first = state.begin(acc, 10_000)
        assertNotNull(first)
        state.complete(first, acc, 1, 0)
        assertNotNull("换号后的第一次刷新不该被上一个账号的节流挡住", state.begin(other, 10_100))
    }

    @Test
    fun complete_切号后旧响应必须丢弃() {
        val state = MessageUnreadRefresh()
        val stale = state.begin(acc, 1_000)
        assertNotNull(stale)
        // 请求在飞的时候用户换号：begin 会重置状态，并让旧凭据作废
        val next = state.begin(other, 2_000)
        assertNotNull(next)
        assertNull("旧账号的回包不能落账", state.complete(stale, other, 9, 9))
        assertEquals("旧值不能顶到新账号头上", 0, state.count(other))
        assertEquals(0, state.count(acc))
        assertEquals(5, state.complete(next, other, 2, 3))
        assertEquals(5, state.count(other))
    }

    @Test
    fun complete_登出后回包丢弃且角标归零() {
        val state = MessageUnreadRefresh()
        val first = state.begin(acc, 1_000)
        assertNotNull(first)
        state.complete(first, acc, 4, 1)
        assertEquals(5, state.count(acc))
        val second = state.begin(acc, 9_000)
        assertNotNull(second)
        state.reset(0)
        assertNull("登出后回来的响应不许再写角标", state.complete(second, 0, 2, 2))
        assertEquals(0, state.count(acc))
        assertEquals(0, state.count(0))
    }

    @Test
    fun fail_保留上次确认值并放开单飞() {
        val state = MessageUnreadRefresh(5_000)
        val first = state.begin(acc, 1_000)
        assertNotNull(first)
        state.complete(first, acc, 4, 1)
        val second = state.begin(acc, 10_000)
        assertNotNull(second)
        assertTrue(state.fail(second, acc))
        assertEquals("失败不能清零（否则下次成功会把老未读当新增、重复弹通知）", 5, state.count(acc))
        assertFalse(state.isInFlight())
        assertNotNull("失败后应能再试一次", state.begin(acc, 15_000))
    }

    @Test
    fun fail_过期凭据不生效() {
        val state = MessageUnreadRefresh()
        val first = state.begin(acc, 1_000)
        assertNotNull(first)
        state.complete(first, acc, 1, 0)
        assertFalse("同一张凭据不能交回两次", state.fail(first, acc))
    }

    @Test
    fun complete_负数未读按零处理且总和钳到上限() {
        val state = MessageUnreadRefresh()
        val first = state.begin(acc, 0)
        assertNotNull(first)
        assertEquals(3, state.complete(first, acc, -5, 3))
        val second = state.begin(acc, 10_000)
        assertNotNull(second)
        assertEquals(
            "两个巨大值相加不能溢出成负数",
            Int.MAX_VALUE,
            state.complete(second, acc, Int.MAX_VALUE, Int.MAX_VALUE)
        )
    }

    @Test
    fun begin_并发调用只有一个能领到凭据() {
        val state = MessageUnreadRefresh()
        val threads = 8
        val start = CountDownLatch(1)
        val issued = AtomicInteger(0)
        val workers = (0 until threads).map {
            Thread {
                start.await()
                if (state.begin(acc, 1_000) != null) issued.incrementAndGet()
            }
        }
        workers.forEach { it.start() }
        start.countDown()
        workers.forEach { it.join(5_000) }
        assertEquals("单飞：并发下只能有一次请求真的发出去", 1, issued.get())
        assertTrue(state.isInFlight())
    }

    @Test
    fun shouldCheck_开关与登录态都要满足() {
        assertTrue(MessageUnreadRefresh.shouldCheck(true, acc))
        assertFalse("关了「消息数量检查」不刷", MessageUnreadRefresh.shouldCheck(false, acc))
        assertFalse("未登录不刷", MessageUnreadRefresh.shouldCheck(true, 0))
        assertFalse(MessageUnreadRefresh.shouldCheck(false, 0))
    }

    @Test
    fun formatCountLabel_有未读才带括号数字() {
        val format = "%1\$s (%2\$d)"
        assertEquals("消息", MessageUnreadRefresh.formatCountLabel("消息", 0, format))
        assertEquals("消息", MessageUnreadRefresh.formatCountLabel("消息", -3, format))
        assertEquals("消息 (3)", MessageUnreadRefresh.formatCountLabel("消息", 3, format))
    }
}
