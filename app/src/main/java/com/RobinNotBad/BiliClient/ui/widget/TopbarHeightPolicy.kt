package com.RobinNotBad.BiliClient.ui.widget

import kotlin.math.roundToInt

/**
 * 「顶栏高度」设置项（`SettingsKeys.TOPBAR_HEIGHT`）的纯逻辑：上下限钳制、dp→px 换算、
 * 加高后要不要竖直居中。
 *
 * **为什么是「最小高度」而不是固定高度**：顶栏是 50 份手抄布局里的 `wrap_content` 容器，
 * 内容并不都只有标题和时钟（`activity_setting_player_ui` 还多一个 `btnFull`），固定高度
 * 会在小屏 / 大字号下把内容压扁；用 `minimumHeight` 则是「内容不够高才补空白」，
 * 0 表示完全不干预，所以默认值与改动前像素级一致。
 *
 * **为什么调高会连带改触摸范围**：三套顶栏手势（单击 `BaseActivity.setTopbarExit`、长按
 * `setupTopbarLongPressToHome`、双击伪装 `DisguiseGesture`）都装在整条 `R.id.top` 上，
 * top 变高即可点区域变大，不需要另加 TouchDelegate。
 *
 * 纯函数不读 SharedPreferences / 全局 Context：换算所需的 density 由调用方传入
 * （用户可能用「设置Density」改过 resources 的密度），这样也能用纯 JVM 单测覆盖。
 */
object TopbarHeightPolicy {

    /** 默认值：0 = 跟随内容，视觉与改动前完全一致。 */
    const val DEFAULT_DP = 0

    /**
     * 上限（dp）。手表屏高通常只有 200~300dp，顶栏再高就把列表挤没了；
     * 下限即 [DEFAULT_DP]（不能调得比内容还小，那是固定高度才有的语义）。
     */
    const val MAX_DP = 64

    /** 把设置里的任意整数钳到合法范围：负数 / 脏值按 0（跟随内容）处理。 */
    fun clampDp(rawDp: Int): Int = rawDp.coerceIn(DEFAULT_DP, MAX_DP)

    /**
     * 换算成 `View.minimumHeight` 需要的像素值；返回 0 表示「不干预」。
     *
     * @param rawDp 设置里存的原始 dp 值（可能越界，这里统一钳制）
     * @param density 当前 resources 的 density（px = dp × density）
     */
    fun resolveMinHeightPx(rawDp: Int, density: Float): Int {
        val dp = clampDp(rawDp)
        if (dp <= 0 || density <= 0f) return 0
        return (dp * density).roundToInt()
    }

    /**
     * 加高后是否把顶栏里的子 View 竖直居中。
     *
     * 只给方屏那套布局加：圆屏适配（`BaseActivity.setRound`）已经把「时钟在上、标题在下」
     * 排好版（给标题算了一个按屏高比例的 topMargin），再叠 `CENTER_VERTICAL` 会让两者重叠。
     */
    fun shouldCenterVertically(minHeightPx: Int, roundMode: Boolean): Boolean =
        minHeightPx > 0 && !roundMode
}
