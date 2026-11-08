package com.RobinNotBad.BiliClient.player

/**
 * 播放器控制按钮的「格」尺寸策略（26.11.07 调研清单第 11 条：触控热区 <48dp）。
 *
 * 纯 Kotlin、无 Android 依赖，可直接 JVM 单测。
 *
 * ## 为什么不是一律 48dp
 *
 * 手表屏幕只有 192~233dp 高：播放器顶栏约 26dp、底栏在放大后约 98dp，
 * 右列可用的纵向空间只有 100~110dp。默认布局右列有 3 格（弹幕/旋转/更多），
 * 3 × 48 = 144dp 直接溢出到顶栏和底栏上。所以竖列只能「在可用高度里尽量放大」：
 *
 * - 2 格（左列默认布局）：高度 48dp；
 * - 3 格（右列默认布局）：高度约 28~36dp，仍比改造前的 28dp 大；
 * - 4~5 格：按可用高度均分，最低回落到改造前的 28dp（再小会互相压住）。
 *
 * 竖列的**宽度**不参与这个函数：左右两列固定 48dp 宽、热区向屏幕内侧扩，图标仍贴原来的
 * 屏幕边缘（见 [com.RobinNotBad.BiliClient.player.PlayerButtonSlots] 的 iconAlign）。
 * 底部按钮行没有纵向争用，格子固定 44dp × 44dp —— 5 个键都放 48dp 会超过 233dp 屏宽，
 * 44dp 是仓库里已经用过的「手表紧凑档触控下限」（`dialog_item_min_height`）。
 * 图标本身不放大，靠内边距把视觉尺寸保持在改造前的大小 —— 观感不变、误触变少。
 */
object PlayerControlCellSize {

    /** 改造前竖列的格尺寸；也是手表上「再小就会互相压住」的下限。 */
    const val MIN_DP = 28f

    /** Material 最小可点尺寸，与 `res/values/dimens.xml` 的 `touch_min` 同值。 */
    const val MAX_DP = 48f

    /**
     * 竖列每格的边长。
     *
     * @param availableDp 这一列可用的纵向高度（dp）。
     * @param slotCount 这一列有几格（含「更多」组）。
     * @return 28~48dp 之间的整数值；[availableDp] 还没量出来（<=0）时返回 [MIN_DP]，
     *   保持改造前的尺寸，避免首帧先画大再缩回去。
     */
    fun cellSizeDp(availableDp: Float, slotCount: Int): Float {
        if (slotCount <= 0 || availableDp <= 0f) return MIN_DP
        val raw = availableDp / slotCount
        return raw.coerceIn(MIN_DP, MAX_DP).toInt().toFloat()
    }

    /**
     * 竖列可用的纵向高度（px）。
     *
     * **左右两列的上限是同一个**：两列都贴屏幕左右边缘，纵向都被顶栏与底栏夹住。右列的容器
     * （`activity_player.xml` 的 `right_control`，`layout_below=top` + `layout_above=bottom_control`）
     * 在布局里就被约束在这两条栏之间，父容器高度就是上限；左列（`left_control`）在 root 层
     * `centerVertical`，父容器量到的是**整个窗口高度**，不能直接拿它算格子 —— 按整窗算的话
     * 3 格 × 48dp = 144dp 会压到进度条与底部按钮行上，还会抢走进度条的触摸
     * （26.11.08 验收发现的回归）。所以左列借用右列量到的那个上限。
     *
     * 上限量不到（<=0，例如右列还没布局）就返回 0 = 不放大：宁可保持改造前的 28dp，
     * 也不要把格子放大到压住别的控件。
     *
     * @param region 槽位区；底部两区不参与按高度放大，恒返回 0
     * @param parentHeightPx 本列父容器高度
     * @param boundedColumnHeightPx 已被上下栏夹住的竖列高度（右列父容器高度）
     */
    fun columnAvailableHeightPx(
        region: PlayerUiLayout.Region,
        parentHeightPx: Int,
        boundedColumnHeightPx: Int,
    ): Int = when (region) {
        PlayerUiLayout.Region.LEFT -> boundedColumnHeightPx.coerceAtLeast(0)
        PlayerUiLayout.Region.RIGHT -> parentHeightPx.coerceAtLeast(0)
        else -> 0
    }
}
