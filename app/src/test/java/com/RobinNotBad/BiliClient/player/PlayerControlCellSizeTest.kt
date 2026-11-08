package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 播放器按钮热区尺寸判据（26.11.07 调研清单第 11 条）。
 *
 * 纯函数，锁住三条口径：
 * 1. 空间够就放大，上限 48dp（Material 最小可点尺寸）；
 * 2. 空间不够就均分，下限回落到改造前的 28dp，**不会**比改造前更小；
 * 3. 高度还没量出来（0 / 负数）时保持 28dp，避免首帧先大后小。
 */
class PlayerControlCellSizeTest {

    @Test
    fun twoSlotsOnWatchGetFullSize() {
        // 233dp 高的小手表：左列 2 格，可用约 200dp
        assertEquals(48f, PlayerControlCellSize.cellSizeDp(200f, 2), 0.001f)
    }

    @Test
    fun threeSlotsShareAvailableHeight() {
        // 右列默认 3 格、可用 108dp → 36dp（改造前 28dp）
        assertEquals(36f, PlayerControlCellSize.cellSizeDp(108f, 3), 0.001f)
    }

    @Test
    fun denseColumnsFallBackToLegacySize() {
        // 5 格、可用 108dp → 21dp 小于下限，回落到改造前的 28dp
        assertEquals(28f, PlayerControlCellSize.cellSizeDp(108f, 5), 0.001f)
    }

    @Test
    fun unknownHeightKeepsLegacySize() {
        assertEquals(28f, PlayerControlCellSize.cellSizeDp(0f, 3), 0.001f)
        assertEquals(28f, PlayerControlCellSize.cellSizeDp(-1f, 3), 0.001f)
    }

    @Test
    fun emptyColumnIsSafe() {
        assertEquals(28f, PlayerControlCellSize.cellSizeDp(108f, 0), 0.001f)
    }

    @Test
    fun neverExceedsPreferredSize() {
        // 屏幕再大也不超过 48dp：48dp 已经满足 Material 最小可点尺寸，
        // 再大只会把视频画面挤掉。
        assertEquals(48f, PlayerControlCellSize.cellSizeDp(1000f, 2), 0.001f)
    }

    // ==================== 竖列可用高度的口径（26.11.08 验收回归） ====================

    @Test
    fun leftColumnBorrowsBoundedHeight() {
        // 左列父容器量到的是整窗 233dp，但真正能用的是右列量到的 108dp：
        // 左列必须借用这个上限，不能按整窗算。
        assertEquals(
            108,
            PlayerControlCellSize.columnAvailableHeightPx(PlayerUiLayout.Region.LEFT, 233, 108)
        )
    }

    @Test
    fun unboundedLeftColumnDoesNotEnlarge() {
        // 右列还没量出高度（0）→ 左列返回 0，调用方据此跳过放大，保持改造前的 28dp
        assertEquals(
            0,
            PlayerControlCellSize.columnAvailableHeightPx(PlayerUiLayout.Region.LEFT, 233, 0)
        )
        assertEquals(
            0,
            PlayerControlCellSize.columnAvailableHeightPx(PlayerUiLayout.Region.LEFT, 233, -1)
        )
    }

    @Test
    fun rightColumnUsesItsOwnParentHeight() {
        assertEquals(
            108,
            PlayerControlCellSize.columnAvailableHeightPx(PlayerUiLayout.Region.RIGHT, 108, 108)
        )
        assertEquals(
            108,
            PlayerControlCellSize.columnAvailableHeightPx(PlayerUiLayout.Region.RIGHT, 108, 0)
        )
    }

    @Test
    fun bottomRegionsNeverUseColumnHeight() {
        assertEquals(
            0,
            PlayerControlCellSize.columnAvailableHeightPx(PlayerUiLayout.Region.BOTTOM_LEFT, 108, 108)
        )
        assertEquals(
            0,
            PlayerControlCellSize.columnAvailableHeightPx(PlayerUiLayout.Region.BOTTOM_RIGHT, 108, 108)
        )
    }

    @Test
    fun threeLeftSlotsStayBetweenBars() {
        // 回归复现：用户把 3 个键放左列时，按整窗（233dp）算会得到 48dp × 3 = 144dp，
        // 超过顶栏与底栏之间的 108dp，压住进度条并抢触摸；按夹住后的上限算则是 36dp × 3 = 108dp，正好放得下。
        val available = PlayerControlCellSize.columnAvailableHeightPx(PlayerUiLayout.Region.LEFT, 233, 108)
        val cell = PlayerControlCellSize.cellSizeDp(available.toFloat(), 3)
        assertEquals(36f, cell, 0.001f)
        assertTrue("整列高度不得超过夹住后的可用高度", cell * 3 <= available)
    }
}
