package com.RobinNotBad.BiliClient.activity.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 番剧索引页请求闸门（26.11.08 验收：筛选 loading 竞态）。
 *
 * 锁住的口径：
 * 1. 同一时刻只放行一轮请求；
 * 2. 在飞期间「条件变了」不能丢，收尾时必须要求重拉一次；
 * 3. 在飞期间「触底翻页」照旧直接丢弃，不排重拉（否则到底了还会再拉一轮）；
 * 4. 待办只兑现一次，兑现后清零。
 */
class BangumiIndexLoadGateTest {

    @Test
    fun firstStartIsAllowed() {
        val gate = BangumiIndexLoadGate()
        assertTrue(gate.start(reload = true))
        assertTrue(gate.loading)
        assertFalse(gate.pendingReload)
    }

    @Test
    fun loadMoreDuringFlightIsDroppedWithoutPendingReload() {
        val gate = BangumiIndexLoadGate()
        assertTrue(gate.start(reload = false))
        assertFalse("在飞时的触底翻页要直接丢弃", gate.start(reload = false))
        assertFalse("翻页不算条件变更，不该留下待办", gate.pendingReload)
        assertFalse("没有待办时收尾不需要重拉", gate.finish())
        assertFalse(gate.loading)
    }

    @Test
    fun filterChangeDuringFlightIsRememberedAndReloaded() {
        val gate = BangumiIndexLoadGate()
        assertTrue(gate.start(reload = true))
        // 用户在请求在飞时改了筛选条件
        assertFalse(gate.start(reload = true))
        assertTrue("条件变更必须被记下来", gate.pendingReload)
        assertTrue("旧请求收尾后要立刻按新条件重拉", gate.finish())
        assertFalse("收尾后闸门必须放开", gate.loading)
        assertFalse("待办只能兑现一次", gate.pendingReload)
        assertTrue(gate.start(reload = true))
    }

    @Test
    fun repeatedFilterChangesCollapseIntoOnePendingReload() {
        val gate = BangumiIndexLoadGate()
        assertTrue(gate.start(reload = true))
        gate.start(reload = true)
        gate.start(reload = true)
        gate.start(reload = true)
        assertTrue("连点多次只需重拉一次", gate.finish())
        assertFalse("待办已兑现，重复收尾不该再要求重拉", gate.finish())
        assertFalse(gate.loading)
    }

    @Test
    fun finishWithoutFlightStillSafe() {
        val gate = BangumiIndexLoadGate()
        // 防御性：调用方重复收尾不应抛异常，也不该凭空产生待办
        assertFalse(gate.finish())
        assertFalse(gate.loading)
        assertFalse(gate.pendingReload)
    }
}
