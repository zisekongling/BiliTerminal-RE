package com.RobinNotBad.BiliClient.util.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 列表底部状态条显示策略的纯逻辑单测。
 *
 * 26.11.06 用户反馈「没有更多了 横幅一直固定在推荐和收藏夹底部不会隐藏」：
 * 那条横幅是列表外的兄弟节点，以前「到底」就常驻。这里钉住两件事——
 * 哪个状态要占位、哪个状态要**自动收起**（只有 END 收，LOADING 不能按时间收）。
 */
class LoadMoreTipPolicyTest {

    // ---- 状态判定 ----

    @Test
    fun stateOf_hiddenWhenNeitherFlag() {
        assertEquals("空闲 → 不占位", LoadMoreTipPolicy.State.HIDDEN, LoadMoreTipPolicy.stateOf(false, false))
    }

    @Test
    fun stateOf_loadingWhenOnlyLoading() {
        assertEquals("加载中 → 正在加载…", LoadMoreTipPolicy.State.LOADING, LoadMoreTipPolicy.stateOf(true, false))
    }

    @Test
    fun stateOf_endWhenOnlyEnd() {
        assertEquals("到底 → 没有更多了", LoadMoreTipPolicy.State.END, LoadMoreTipPolicy.stateOf(false, true))
    }

    @Test
    fun stateOf_endWinsOverLoading() {
        // 加载结束时基类传的是 loading=false、end=bottom，正常路径两者不会同时为真；
        // 但 setRefreshing(true) 期间 bottom 仍为真时会出现，此时应显示「到底」而不是「正在加载」
        assertEquals("到底优先于加载中", LoadMoreTipPolicy.State.END, LoadMoreTipPolicy.stateOf(true, true))
    }

    // ---- 是否占位 ----

    @Test
    fun isVisible_onlyHiddenTakesNoSpace() {
        assertFalse("HIDDEN 不占位", LoadMoreTipPolicy.isVisible(LoadMoreTipPolicy.State.HIDDEN))
        assertTrue("LOADING 占位", LoadMoreTipPolicy.isVisible(LoadMoreTipPolicy.State.LOADING))
        assertTrue("END 占位", LoadMoreTipPolicy.isVisible(LoadMoreTipPolicy.State.END))
    }

    // ---- 是否自动收起 ----

    @Test
    fun autoHides_onlyEndState() {
        assertTrue("只有「没有更多了」是一次性提示", LoadMoreTipPolicy.autoHides(LoadMoreTipPolicy.State.END))
        assertFalse(
            "「正在加载…」必须等到加载回调才收：请求慢时按时间收掉会像卡死",
            LoadMoreTipPolicy.autoHides(LoadMoreTipPolicy.State.LOADING)
        )
        assertFalse("本来就不可见，没有可收的", LoadMoreTipPolicy.autoHides(LoadMoreTipPolicy.State.HIDDEN))
    }

    // ---- 时长 ----

    @Test
    fun endVisibleMs_isShortEnoughToNotActLikeAPermanentBar() {
        assertTrue("必须是个正数", LoadMoreTipPolicy.END_VISIBLE_MS > 0)
        assertTrue(
            "太长就退化成用户反馈的「一直固定在那里」：上限 5 秒",
            LoadMoreTipPolicy.END_VISIBLE_MS <= 5000L
        )
        assertTrue(
            "太短用户根本看不清：下限 1 秒",
            LoadMoreTipPolicy.END_VISIBLE_MS >= 1000L
        )
    }
}
