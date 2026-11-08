package com.RobinNotBad.BiliClient.ui.widget

import com.RobinNotBad.BiliClient.util.IslandState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 顶栏灵动岛状态机（26.11.07）。
 *
 * 锁住用户拍板的四条口径：
 * 1. 优先级：瞬时事件 > 播放 > 下载 > 未读 > 时间；
 * 2. 持续事件每 30 秒让 5 秒给时间，然后回到事件；
 * 3. 没有事件时一直显示时间；
 * 4. 瞬时事件可以打断时间插播，结束后由状态源自己撤掉。
 */
class IslandPolicyTest {

    private fun input(
        now: Long,
        playing: Boolean = false,
        downloading: Boolean = false,
        unread: Int = 0,
        burst: IslandState? = null,
        showTime: Boolean = false,
        start: Long = 0L
    ) = IslandInput(now, playing, downloading, unread, burst, showTime, start)

    @Test
    fun idleShowsTime() {
        val d = IslandPolicy.decide(input(1_000L))
        assertEquals(IslandState.TIME, d.state)
        assertFalse(d.showTime)
    }

    @Test
    fun playingWinsOverDownloadAndUnread() {
        assertEquals(
            IslandState.PLAYING,
            IslandPolicy.ongoingState(playing = true, downloading = true, unread = 5)
        )
    }

    @Test
    fun downloadWinsOverUnread() {
        assertEquals(
            IslandState.DOWNLOADING,
            IslandPolicy.ongoingState(playing = false, downloading = true, unread = 5)
        )
    }

    @Test
    fun unreadIsTheLowestOngoingEvent() {
        assertEquals(
            IslandState.UNREAD,
            IslandPolicy.ongoingState(playing = false, downloading = false, unread = 1)
        )
    }

    @Test
    fun eventShowsForThirtySecondsThenTimeForFive() {
        val at29 = IslandPolicy.decide(input(29_999L, playing = true))
        assertEquals(IslandState.PLAYING, at29.state)
        assertFalse(at29.showTime)

        val at30 = IslandPolicy.decide(input(30_000L, playing = true))
        assertEquals(IslandState.TIME, at30.state)
        assertTrue(at30.showTime)
        assertEquals(30_000L, at30.phaseStartMs)
    }

    @Test
    fun timeSliceEndsAfterFiveSeconds() {
        val inSlice = IslandPolicy.decide(
            input(34_999L, playing = true, showTime = true, start = 30_000L)
        )
        assertEquals(IslandState.TIME, inSlice.state)
        assertTrue(inSlice.showTime)

        val afterSlice = IslandPolicy.decide(
            input(35_000L, playing = true, showTime = true, start = 30_000L)
        )
        assertEquals(IslandState.PLAYING, afterSlice.state)
        assertFalse(afterSlice.showTime)
        assertEquals(35_000L, afterSlice.phaseStartMs)
    }

    @Test
    fun burstPreemptsOngoingEvent() {
        val d = IslandPolicy.decide(
            input(1_000L, playing = true, burst = IslandState.BATTERY)
        )
        assertEquals(IslandState.BATTERY, d.state)
        assertFalse(d.showTime)
    }

    @Test
    fun burstCanInterruptTimeSlice() {
        val d = IslandPolicy.decide(
            input(
                1_000L,
                playing = true,
                burst = IslandState.ANNOUNCEMENT,
                showTime = true,
                start = 0L
            )
        )
        assertEquals(IslandState.ANNOUNCEMENT, d.state)
        assertTrue(d.showTime)
    }

    @Test
    fun everythingStopsBackToTime() {
        val d = IslandPolicy.decide(input(100_000L))
        assertEquals(IslandState.TIME, d.state)
        assertFalse(d.showTime)
    }

    @Test
    fun zeroUnreadDoesNotCountAsEvent() {
        assertEquals(
            null,
            IslandPolicy.ongoingState(playing = false, downloading = false, unread = 0)
        )
    }

    @Test
    fun timeSliceUsesStoredPhaseStartNotCurrentTime() {
        // 状态机只认调用方存回来的起点：即使 now 很大，只要还在 5 秒窗口内就继续显示时间。
        val d = IslandPolicy.decide(
            input(103_000L, unread = 2, showTime = true, start = 100_000L)
        )
        assertEquals(IslandState.TIME, d.state)
        assertTrue(d.showTime)
    }
}
