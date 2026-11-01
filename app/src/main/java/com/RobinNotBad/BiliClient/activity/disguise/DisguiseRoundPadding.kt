package com.RobinNotBad.BiliClient.activity.disguise

import android.view.View
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.view.RoundInsets
import com.RobinNotBad.BiliClient.util.view.physicalScreenSizePx

/**
 * 给伪装页套上「圆屏适配」的内边距。
 *
 * 两个伪装页都**刻意不继承 `BaseActivity`**（那边会带进应用主题、教程触发、EventBus，
 * 任何一项都会让伪装露馅），所以 `BaseActivity` 里那段留白用不上。但**圆屏留白是设备适配、
 * 不是外观**：圆屏手表上不缩进的话，最外圈的按键/文字会被切掉。
 *
 * <h3>26.11.02 修的两处</h3>
 * 1. **单位错了**：这里原来把「界面边距」的**百分比**当 dp 用（`padH * density`），
 *    380 屏上 5% 只有 5px，而 `BaseActivity` 的同一设置是 19px——同一个开关，伪装页只缩进四分之一。
 *    现在统一走 [RoundInsets.compute]（与页面、弹窗同源）。
 * 2. **整屏铺满的界面收不进圆**：`CalculatorDisguiseActivity` 是 4×4 键盘铺满全屏，
 *    圆屏上四个角的按键落在圆外（看不见也点不到）。圆屏适配打开时再按圆的内接正方形收一圈
 *    （[RoundInsets.inscribedInsetPx]，380 屏上 55px），键盘整体落进圆内。
 *
 * 内边距是**叠加**在布局自己的 padding 上的：伪装页的 padding 属于外观（计算器那 4dp），
 * 不能被覆盖掉。
 */
internal fun applyDisguiseRoundPadding(root: View) {
    val screen = physicalScreenSizePx(root.context)
    val round = SharedPreferencesUtil.getBoolean(SettingsKeys.UI_ROUND, false)
    val insets = RoundInsets.compute(
        screen[0],
        screen[1],
        SharedPreferencesUtil.getInt(SettingsKeys.PADDING_H, 0),
        SharedPreferencesUtil.getInt(SettingsKeys.PADDING_V, 0),
        round
    )
    // 圆屏适配打开时四个角也要收进圆内（计算器键盘铺满全屏，角上按键否则点不到）。
    // 方框的边距是圆内接正方形的要求，比「界面边距」通常更大，取两边的较大值。
    val edge = if (round) RoundInsets.inscribedInsetPx(minOf(screen[0], screen[1])) else 0
    val left = maxOf(insets.left, edge)
    val top = maxOf(insets.top, edge)
    val right = maxOf(insets.right, edge)
    val bottom = maxOf(insets.bottom, edge)
    // 与 BaseActivity 同一条门槛：算不出任何边距时不动根布局（布局自己的 padding 保持原样）
    if (left == 0 && top == 0) return
    root.setPadding(
        root.paddingLeft + left,
        root.paddingTop + top,
        root.paddingRight + right,
        root.paddingBottom + bottom
    )
}
