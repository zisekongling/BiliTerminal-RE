package com.RobinNotBad.BiliClient.activity.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MultiPageResumePolicy] 的纯 JVM 单测（反馈 #83「多P 视频再次打开要续到上次那个P」）。
 *
 * 数据契约见 `PlayerApi` 与 `PlayerData.lastPlayTimeMs` 的注释：
 * - `cidHistory` 是服务端 `last_play_cid`；
 * - `progress` 只在 `last_play_cid == cid` 时才采纳 `last_play_time`，所以"有没有上次记录"
 *   必须看 `lastPlayTimeMs` 原值，不能看 `progress == 0`；
 * - cid 必须真的属于这组 `cids`，否则是别的稿件/已重传的视频，不能乱跳。
 */
class MultiPageResumePolicyTest {

    private val cids = listOf(1001L, 1002L, 1003L)

    // ---------- resumePageIndex ----------

    @Test
    fun 续到上次观看的那一P() {
        assertEquals(2, MultiPageResumePolicy.resumePageIndex(cids, 1003L, 65_000L))
        assertEquals(1, MultiPageResumePolicy.resumePageIndex(cids, 1002L, 65_000L))
    }

    @Test
    fun 上次看的是P1时按默认处理() {
        // P1 本来就是默认页；这时仍走分P 选择页，别把"能选P"的入口吃掉
        assertEquals(0, MultiPageResumePolicy.resumePageIndex(cids, 1001L, 65_000L))
    }

    @Test
    fun 没有观看记录时不跳() {
        assertEquals("last_play_cid 为 0 = 服务端没有记录", 0, MultiPageResumePolicy.resumePageIndex(cids, 0L, 0L))
        assertEquals("last_play_cid 有值但 last_play_time 为 0，说明只是登记不是观看", 0,
            MultiPageResumePolicy.resumePageIndex(cids, 1003L, 0L))
        assertEquals("负数时间同样视为没有记录", 0,
            MultiPageResumePolicy.resumePageIndex(cids, 1003L, -1L))
    }

    @Test
    fun cid不属于本稿件时不跳() {
        assertEquals(
            "last_play_cid 可能是别的视频的（或视频已重传、cid 全变），不能乱跳",
            0,
            MultiPageResumePolicy.resumePageIndex(cids, 9999L, 65_000L)
        )
    }

    @Test
    fun 列表为空时不跳() {
        assertEquals(0, MultiPageResumePolicy.resumePageIndex(emptyList(), 1003L, 65_000L))
        assertEquals(0, MultiPageResumePolicy.resumePageIndex(null, 1003L, 65_000L))
    }

    @Test
    fun 单P视频不会命中() {
        val single = listOf(1001L)
        assertEquals(0, MultiPageResumePolicy.resumePageIndex(single, 1001L, 65_000L))
        assertEquals(0, MultiPageResumePolicy.resumePageIndex(single, 1002L, 65_000L))
    }

    // ---------- alignPageIndex：播放器内的「当前第几P」 ----------

    @Test
    fun 用cid纠正出错的下标() {
        assertEquals(
            "入口给了正确的 cid 但下标是旧的，选集卡片会高亮错一P",
            2,
            MultiPageResumePolicy.alignPageIndex(cids, 1003L, 0, virtualCollection = false)
        )
    }

    @Test
    fun cid找不到时保持原下标() {
        assertEquals(1, MultiPageResumePolicy.alignPageIndex(cids, 8888L, 1, virtualCollection = false))
        assertEquals(1, MultiPageResumePolicy.alignPageIndex(null, 8888L, 1, virtualCollection = false))
    }

    @Test
    fun 虚拟合集不能按cid纠正() {
        // 收藏夹虚拟合集的 cids 里存的是 aid，拿 cid 去 indexOf 必然 -1；硬改会把下标弄成 0
        assertEquals(
            3,
            MultiPageResumePolicy.alignPageIndex(cids, 1003L, 3, virtualCollection = true)
        )
    }

    // ---------- isValidPageIndex ----------

    @Test
    fun 分P下标越界判定() {
        assertTrue(MultiPageResumePolicy.isValidPageIndex(cids, 0))
        assertTrue(MultiPageResumePolicy.isValidPageIndex(cids, 2))
        assertFalse(MultiPageResumePolicy.isValidPageIndex(cids, 3))
        assertFalse(MultiPageResumePolicy.isValidPageIndex(cids, -1))
        assertFalse(MultiPageResumePolicy.isValidPageIndex(null, 0))
    }
}
