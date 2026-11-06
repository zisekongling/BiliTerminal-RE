package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.model.VideoCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 观看记录多选删除的纯函数单测（批次 1「社交与信息」）。
 *
 * <p>只测不碰 Android / 网络的部分：批量上限、全选判据、快照整理、勾选 → aid 映射。
 * 真机上「删三条挂一条」这种场景靠单测复现不了，但「上限算错」「去重漏掉」
 * 「勾选的顺序和 aid 对不上」这类最容易写错的地方都能在这里钉死。
 */
class HistoryBatchDeleteTest {

    private fun card(aid: Long, title: String = "视频$aid"): VideoCard =
        VideoCard(title, "up", "看到1:00", "cover", aid, "BV$aid")

    @Test
    fun exceedsBatchLimit_boundaryIsExactlyMax() {
        // 用 Long 显式断言：JUnit4 没有 (int,int) 重载，避免落到 (long,long) 的隐式转换歧义
        assertEquals(100L, HistoryApi.MAX_BATCH_DELETE.toLong())
        assertFalse(HistoryApi.exceedsBatchLimit(0))
        assertFalse(HistoryApi.exceedsBatchLimit(1))
        assertFalse(HistoryApi.exceedsBatchLimit(HistoryApi.MAX_BATCH_DELETE))
        assertTrue(HistoryApi.exceedsBatchLimit(HistoryApi.MAX_BATCH_DELETE + 1))
    }

    @Test
    fun isAllSelected_emptyIsNotAll() {
        assertFalse(HistoryApi.isAllSelected(null))
        assertFalse(HistoryApi.isAllSelected(booleanArrayOf()))
        assertFalse(HistoryApi.isAllSelected(booleanArrayOf(true, false)))
        assertFalse(HistoryApi.isAllSelected(booleanArrayOf(false, false)))
        assertTrue(HistoryApi.isAllSelected(booleanArrayOf(true)))
        assertTrue(HistoryApi.isAllSelected(booleanArrayOf(true, true, true)))
    }

    @Test
    fun countChecked_handlesNullAndEmpty() {
        assertEquals(0, HistoryApi.countChecked(null))
        assertEquals(0, HistoryApi.countChecked(booleanArrayOf()))
        assertEquals(2, HistoryApi.countChecked(booleanArrayOf(true, false, true)))
    }

    @Test
    fun buildSelectableSnapshot_dedupsByAidAndSkipsInvalid() {
        // 同一稿件在历史里可能出现多次（不同分P/不同时间）；aid<=0 的是脏条目
        val loaded = listOf(
            card(1), card(2), card(1), card(0), card(-5), card(3), card(2)
        )
        val snapshot = HistoryApi.buildSelectableSnapshot(loaded, HistoryApi.MAX_BATCH_DELETE)
        assertEquals(listOf(1L, 2L, 3L), snapshot.map { it.aid })
    }

    @Test
    fun buildSelectableSnapshot_respectsLimitAndNullInput() {
        val loaded = (1L..150L).map { card(it) }
        assertEquals(
            HistoryApi.MAX_BATCH_DELETE.toLong(),
            HistoryApi.buildSelectableSnapshot(loaded, HistoryApi.MAX_BATCH_DELETE).size.toLong()
        )
        assertEquals(5, HistoryApi.buildSelectableSnapshot(loaded, 5).size)
        // limit<=0 视为「不收任何条目」，而不是「不限」
        assertEquals(0, HistoryApi.buildSelectableSnapshot(loaded, 0).size)
        assertEquals(0, HistoryApi.buildSelectableSnapshot(null, 10).size)
    }

    @Test
    fun collectSelectedAids_keepsSnapshotOrder() {
        val snapshot = listOf(card(11), card(22), card(33))
        assertEquals(
            listOf(22L, 33L),
            HistoryApi.collectSelectedAids(snapshot, booleanArrayOf(false, true, true))
        )
        assertEquals(
            emptyList<Long>(),
            HistoryApi.collectSelectedAids(snapshot, booleanArrayOf(false, false, false))
        )
    }

    @Test
    fun collectSelectedAids_ignoresTrailingChecksBeyondSnapshot() {
        // checked 比 snapshot 长时按 snapshot 截断：多出来的勾选不该产生越界或幽灵 aid
        val snapshot = listOf(card(7))
        assertEquals(listOf(7L), HistoryApi.collectSelectedAids(snapshot, booleanArrayOf(true, true)))
    }
}
