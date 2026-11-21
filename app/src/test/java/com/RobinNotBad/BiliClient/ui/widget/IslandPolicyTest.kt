package com.RobinNotBad.BiliClient.ui.widget

import com.RobinNotBad.BiliClient.util.IslandState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 顶栏灵动岛状态机（26.11.14 三层优先级改版）。
 *
 * 锁住用户拍板的口径：
 * 1. 瞬时事件（低电 / 充电 / 公告 / 更新 / 新未读）短播 5 秒，不参与轮换；
 * 2. 常驻事件（播放 > 下载）默认一直显示，开了轮换间隔才对称地让位给「时间 + 电量」；
 * 3. 基础状态兜底显示时间，电量并排而不是互相轮换；
 * 4. 5 个分类开关只影响「显示」，不影响采集。
 */
class IslandPolicyTest {

    private fun input(
        now: Long,
        playing: Boolean = false,
        downloading: Boolean = false,
        unreadBurst: Boolean = false,
        burst: IslandState? = null,
        filters: IslandFilters = IslandFilters(),
        showBase: Boolean = false,
        start: Long = 0L,
        rotateMs: Long = 0L
    ) = IslandInput(now, playing, downloading, unreadBurst, burst, filters, showBase, start, rotateMs)

    @Test
    fun idleShowsBaseTime() {
        val d = IslandPolicy.decide(input(1_000L))
        assertEquals(IslandState.TIME, d.state)
        assertFalse(d.showBase)
    }

    @Test
    fun playingIsTheTopOngoingEvent() {
        assertEquals(IslandState.PLAYING, IslandPolicy.ongoingState(playing = true, downloading = true))
        assertEquals(IslandState.DOWNLOADING, IslandPolicy.ongoingState(playing = false, downloading = true))
        assertNull(IslandPolicy.ongoingState(playing = false, downloading = false))
    }

    @Test
    fun unreadIsNoLongerAnOngoingEvent() {
        // 未读改成「增加时短播」，不再常驻占着胶囊：没有播放/下载时就是时间。
        val d = IslandPolicy.decide(input(1_000L, unreadBurst = false))
        assertEquals(IslandState.TIME, d.state)
    }

    @Test
    fun noRotationKeepsTheEventForever() {
        // 默认「不轮换」：播放中一分钟、一小时都还是播放中，不会自己跳回时间。
        assertEquals(IslandState.PLAYING, IslandPolicy.decide(input(60_000L, playing = true)).state)
        assertEquals(
            IslandState.PLAYING,
            IslandPolicy.decide(input(3_600_000L, playing = true)).state
        )
    }

    @Test
    fun rotationIsSymmetric() {
        // 3 秒轮换：事件 3 秒 → 时间 3 秒 → 事件 3 秒……
        val inEvent = IslandPolicy.decide(input(2_999L, playing = true, rotateMs = 3_000L))
        assertEquals(IslandState.PLAYING, inEvent.state)
        assertFalse(inEvent.showBase)

        val toBase = IslandPolicy.decide(input(3_000L, playing = true, rotateMs = 3_000L))
        assertEquals(IslandState.TIME, toBase.state)
        assertTrue(toBase.showBase)
        assertEquals(3_000L, toBase.phaseStartMs)

        val stillBase = IslandPolicy.decide(
            input(5_999L, playing = true, rotateMs = 3_000L, showBase = true, start = 3_000L)
        )
        assertEquals(IslandState.TIME, stillBase.state)
        assertTrue(stillBase.showBase)

        val backToEvent = IslandPolicy.decide(
            input(6_000L, playing = true, rotateMs = 3_000L, showBase = true, start = 3_000L)
        )
        assertEquals(IslandState.PLAYING, backToEvent.state)
        assertFalse(backToEvent.showBase)
        assertEquals(6_000L, backToEvent.phaseStartMs)
    }

    @Test
    fun unreadBurstInterruptsNothingButBriefly() {
        // 「不轮换 + 未读短播 + 播放中」：短播期间压过播放，短播结束后立刻回到播放。
        val duringBurst = IslandPolicy.decide(
            input(1_000L, playing = true, unreadBurst = true, rotateMs = 0L)
        )
        assertEquals(IslandState.UNREAD, duringBurst.state)

        val afterBurst = IslandPolicy.decide(
            input(6_000L, playing = true, unreadBurst = false, rotateMs = 0L)
        )
        assertEquals(IslandState.PLAYING, afterBurst.state)
    }

    @Test
    fun unreadBurstDoesNotResetTheRotationPhase() {
        // 短播只是插播：不该把常驻事件的轮换窗口清零。
        val d = IslandPolicy.decide(
            input(
                4_000L,
                playing = true,
                unreadBurst = true,
                showBase = false,
                start = 1_000L,
                rotateMs = 3_000L
            )
        )
        assertEquals(IslandState.UNREAD, d.state)
        assertEquals(1_000L, d.phaseStartMs)
    }

    @Test
    fun burstPreemptsEverything() {
        val d = IslandPolicy.decide(input(1_000L, playing = true, burst = IslandState.BATTERY))
        assertEquals(IslandState.BATTERY, d.state)
        assertFalse(d.showBase)
    }

    @Test
    fun burstCanInterruptTheBaseWindow() {
        val d = IslandPolicy.decide(
            input(
                1_000L,
                playing = true,
                burst = IslandState.ANNOUNCEMENT,
                showBase = true,
                start = 0L,
                rotateMs = 3_000L
            )
        )
        assertEquals(IslandState.ANNOUNCEMENT, d.state)
        assertTrue(d.showBase)
    }

    @Test
    fun batteryFilterSilencesTheBatteryBurst() {
        val d = IslandPolicy.decide(
            input(
                1_000L,
                playing = true,
                burst = IslandState.BATTERY,
                filters = IslandFilters(battery = false)
            )
        )
        assertEquals(IslandState.PLAYING, d.state)
    }

    @Test
    fun unreadFilterSilencesTheUnreadBurst() {
        val d = IslandPolicy.decide(
            input(
                1_000L,
                unreadBurst = true,
                filters = IslandFilters(unread = false)
            )
        )
        assertEquals(IslandState.TIME, d.state)
    }

    @Test
    fun announcementFilterSilencesTheAnnouncementBurst() {
        val d = IslandPolicy.decide(
            input(
                1_000L,
                burst = IslandState.ANNOUNCEMENT,
                filters = IslandFilters(announcement = false)
            )
        )
        assertEquals(IslandState.TIME, d.state)
    }

    @Test
    fun playingFilterFallsBackToDownloading() {
        val d = IslandPolicy.decide(
            input(
                1_000L,
                playing = true,
                downloading = true,
                filters = IslandFilters(playing = false)
            )
        )
        assertEquals(IslandState.DOWNLOADING, d.state)
    }

    @Test
    fun allOngoingFiltersOffShowsTime() {
        val d = IslandPolicy.decide(
            input(
                1_000L,
                playing = true,
                downloading = true,
                filters = IslandFilters(playing = false, downloading = false)
            )
        )
        assertEquals(IslandState.TIME, d.state)
    }

    @Test
    fun rotateMsOfAcceptsOnlyOfferedValues() {
        assertEquals(0L, IslandPolicy.rotateMsOf(null))
        assertEquals(0L, IslandPolicy.rotateMsOf(""))
        assertEquals(0L, IslandPolicy.rotateMsOf("abc"))
        assertEquals(0L, IslandPolicy.rotateMsOf("7000"))
        assertEquals(3_000L, IslandPolicy.rotateMsOf("3000"))
        assertEquals(5_000L, IslandPolicy.rotateMsOf(" 5000 "))
        assertEquals(10_000L, IslandPolicy.rotateMsOf("10000"))
    }

    @Test
    fun islandSwitchFollowsUserOnceSet() {
        // 用户在新开关上设过就完全听他的，与「圆屏适配」无关。
        assertTrue(IslandPolicy.resolveEnabled(explicit = true, legacyRound = false))
        assertFalse(IslandPolicy.resolveEnabled(explicit = false, legacyRound = true))
    }

    @Test
    fun islandSwitchInheritsRoundWhenUserNeverTouchedIt() {
        // 没设过（含新装用户）：继承旧行为，开着圆屏适配的老用户升级后不丢功能。
        assertTrue(IslandPolicy.resolveEnabled(explicit = null, legacyRound = true))
        assertFalse(IslandPolicy.resolveEnabled(explicit = null, legacyRound = false))
    }

    @Test
    fun islandWidthIsTheSameEverywhere() {
        // 26.11.10 口径：胶囊宽度全局固定，与状态 / 页面 / 标题长短无关。
        val wanted = 216 // 72dp @ density 3
        assertEquals(wanted, IslandPolicy.pillWidthPx(wanted, 1080))
        assertEquals(wanted, IslandPolicy.pillWidthPx(wanted, 1080))
    }

    @Test
    fun islandWidthIsCappedOnSmallScreens() {
        // 小屏（233px 宽的表盘）按屏宽 31% 封顶，避免胶囊被圆盘切角。
        assertEquals(72, IslandPolicy.pillWidthPx(216, 233))
        // 屏宽拿不到时退回理想宽度。
        assertEquals(216, IslandPolicy.pillWidthPx(216, 0))
    }
}
