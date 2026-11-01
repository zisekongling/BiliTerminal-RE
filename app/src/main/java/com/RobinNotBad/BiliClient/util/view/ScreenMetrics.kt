package com.RobinNotBad.BiliClient.util.view

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * 物理屏幕尺寸（px）。
 *
 * <h3>为什么要和 `resources.displayMetrics` 取小值</h3>
 * 用户在设置里把「界面大小」调大时，`BaseActivity.setDensity()` 会走
 * `resources.updateConfiguration(configuration, displayMetrics)`：有的 ROM 只改 `density`
 * 不重算 `widthPixels` / `heightPixels`，有的会重算——重算的那类上「屏宽 × 0.9」会算出比屏幕
 * 更宽的值，低分辨率设备上弹窗右半边被切掉正是这么来的。`getRealMetrics()` 读的是显示器物理
 * 尺寸，不受应用侧密度改动影响；再与 `resources` 的窗口尺寸取小值，顺带兜住多窗口 / 系统栏。
 *
 * 弹窗尺寸（`TerminalDialog.Sheet.screenSizePx`）与伪装页圆屏适配都用这一份，免得两边算出
 * 不同的基准。
 */
@Suppress("DEPRECATION")
fun physicalScreenSizePx(context: Context): IntArray {
    val metrics = DisplayMetrics()
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    if (windowManager != null) windowManager.defaultDisplay.getRealMetrics(metrics)
    else metrics.setTo(context.resources.displayMetrics)
    val window = context.resources.displayMetrics
    return intArrayOf(
        minOf(metrics.widthPixels, window.widthPixels),
        minOf(metrics.heightPixels, window.heightPixels)
    )
}
