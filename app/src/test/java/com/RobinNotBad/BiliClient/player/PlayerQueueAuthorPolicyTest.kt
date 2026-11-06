package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 连播作者判据的回归钉：**作者只来自视频本身，绝不回退到登录账号**。
 *
 * 26.11.06 修的 bug：`PlayerData.mid`（登录账号）被当成作者，连播拉的是自己的投稿。
 * 这里把「什么时候能点连播」「换到另一条视频后作者取谁」两条判据逐格钉死。
 */
class PlayerQueueAuthorPolicyTest {

    private fun canLoad(
        isLive: Boolean = false,
        isPgc: Boolean = false,
        isOnline: Boolean = true,
        uploaderMid: Long = 0L,
        aid: Long = 0L
    ) = PlayerQueueAuthorPolicy.canLoadUploader(isLive, isPgc, isOnline, uploaderMid, aid)

    @Test
    fun `在线投稿已知作者时能点连播`() {
        assertTrue(canLoad(uploaderMid = 5566L, aid = 114514L))
    }

    @Test
    fun `作者未知但知道 aid 也能点（点下去按 aid 现查作者）`() {
        assertTrue(canLoad(uploaderMid = 0L, aid = 114514L))
    }

    @Test
    fun `作者和 aid 都没有时不显示连播`() {
        assertFalse(canLoad(uploaderMid = 0L, aid = 0L))
    }

    @Test
    fun `直播与番剧不给 UP 主连播入口`() {
        assertFalse(canLoad(isLive = true, uploaderMid = 5566L, aid = 114514L))
        assertFalse(canLoad(isPgc = true, uploaderMid = 5566L, aid = 114514L))
    }

    @Test
    fun `本地与缓存视频不给连播入口`() {
        assertFalse(canLoad(isOnline = false, uploaderMid = 5566L, aid = 114514L))
    }

    @Test
    fun `登录态与作者无关：作者已知就能点，不需要登录 mid`() {
        // 这个判据只看 uploaderMid / aid，签名里根本没有登录 mid——
        // 曾经「mid > 0」既是登录闸又是作者闸，才让连播指向登录账号
        assertTrue(canLoad(uploaderMid = 1L, aid = 0L))
    }

    @Test
    fun `换到另一条视频时采用新作者`() {
        assertEquals(2222L, PlayerQueueAuthorPolicy.authorAfterSwitch(2222L, 1111L))
    }

    @Test
    fun `新条目作者未知时清零，不沿用上一条视频的作者`() {
        assertEquals(0L, PlayerQueueAuthorPolicy.authorAfterSwitch(0L, 1111L))
    }

    @Test
    fun `同一条视频换P（没有新条目）时保留当前作者`() {
        assertEquals(1111L, PlayerQueueAuthorPolicy.authorAfterSwitch(null, 1111L))
    }
}
