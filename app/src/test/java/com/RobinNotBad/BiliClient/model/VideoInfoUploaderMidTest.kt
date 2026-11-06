package com.RobinNotBad.BiliClient.model

import com.RobinNotBad.BiliClient.util.FakeSharedPreferences
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 连播作者 mid 的来源：**必须取视频的 UP 主，不能取登录账号**。
 *
 * 回归背景（26.11.06）：`VideoInfo.toPlayerData` 曾把 `SharedPreferencesUtil` 里的登录 mid
 * 塞进 `PlayerData.mid`，播放器的连播按钮又拿它当作者去拉「UP 主投稿」——结果是连播里放的是
 * 登录账号自己的投稿，连播层标题也变成自己的昵称。现在作者单独走 `uploaderMid`。
 */
class VideoInfoUploaderMidTest {

    private fun up(mid: Long, name: String = "UP$mid"): UserInfo =
        UserInfo(mid, name, "", "", 0, 0, 6, false, "", 0, "", 0)

    private fun video(
        staff: List<UserInfo> = emptyList(),
        aid: Long = 114514L,
        title: String = "标题"
    ): VideoInfo {
        val info = VideoInfo()
        info.aid = aid
        info.title = title
        info.pagenames = arrayListOf("P1")
        info.cids = arrayListOf(223344L)
        info.staff = ArrayList(staff)
        return info
    }

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = FakeSharedPreferences()
    }

    @After
    fun tearDown() {
        SharedPreferencesUtil.sharedPreferences = null
    }

    @Test
    fun `单P投稿的作者 mid 取 staff 第一个`() {
        assertEquals(5566L, video(staff = listOf(up(5566L))).uploaderMid())
    }

    @Test
    fun `联合投稿取第一作者`() {
        val info = video(staff = listOf(up(11L), up(22L), up(33L)))
        assertEquals(11L, info.uploaderMid())
    }

    @Test
    fun `没有 staff 时作者 mid 为 0 而不是登录账号`() {
        SharedPreferencesUtil.putLong("mid", 999888L)
        val info = video(staff = emptyList())
        assertEquals(0L, info.uploaderMid())
        // 关键回归点：登录 mid 只进 mid，绝不进 uploaderMid
        val data = info.toPlayerData(0)
        assertEquals(999888L, data.mid)
        assertEquals(0L, data.uploaderMid)
    }

    @Test
    fun `toPlayerData 把作者写进 uploaderMid 且不与登录 mid 混用`() {
        SharedPreferencesUtil.putLong("mid", 999888L)
        val data = video(staff = listOf(up(5566L))).toPlayerData(0)
        assertEquals(999888L, data.mid)
        assertEquals(5566L, data.uploaderMid)
    }
}
