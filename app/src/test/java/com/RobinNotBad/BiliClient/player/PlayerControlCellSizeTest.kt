package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
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
}
