package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「当前该用多少 DPI」这条决策的纯逻辑单测（反馈 #225：字体大小不统一）。
 *
 * 这块以前散在两个地方、两套算法：`BiliTerminal.getFitDisplayContext` 只认倍数，
 * `BaseActivity.setDensity` 只认绝对 DPI，于是同一进程里页面（绝对 DPI）与
 * Toast / `ToolsUtil.dp2px`（Application context，倍数）算出不同的像素。
 * 现在两边都走 [UiScaleInput.resolveDensityDpi]，这里钉住它的优先级与边界。
 *
 * 报告里的真机数字：OPPO Watch 3，系统 DPI 189、应用内 DPI 220（绝对值）、界面缩放 1.0。
 */
class UiScaleInputDensityTest {

    // ---- 没设置任何覆盖：必须原样返回基线 ----

    @Test
    fun resolveDensityDpi_withoutOverride_keepsBaseDensity() {
        assertEquals(189, UiScaleInput.resolveDensityDpi(189, -1, 1.0f))
        assertEquals(240, UiScaleInput.resolveDensityDpi(240, -1, 1.0f))
        assertEquals(160, UiScaleInput.resolveDensityDpi(160, -1, 1.0f))
    }

    // ---- 界面缩放倍数：与改造前的 `(densityDpi * dpiTimes).toInt()` 逐像素一致 ----

    @Test
    fun resolveDensityDpi_multipliesBaseByTimes() {
        assertEquals(226, UiScaleInput.resolveDensityDpi(189, -1, 1.2f))
        assertEquals(240, UiScaleInput.resolveDensityDpi(160, -1, 1.5f))
        assertEquals(378, UiScaleInput.resolveDensityDpi(189, -1, 2.0f))
    }

    @Test
    fun resolveDensityDpi_truncatesInsteadOfRounding() {
        // 189 × 1.2 = 226.8、189 × 1.15 = 217.35：旧代码 toInt() 截断，这里保持截断，
        // 否则同一个倍数升级前后会差 1 DPI，属于没必要的观感变化。
        assertEquals(226, UiScaleInput.resolveDensityDpi(189, -1, 1.2f))
        assertEquals(217, UiScaleInput.resolveDensityDpi(189, -1, 1.15f))
    }

    // ---- 绝对 DPI（「设置Density」）优先于倍数 ----

    @Test
    fun resolveDensityDpi_absoluteDensityWins() {
        // 反馈 #225 那台机器的实际组合：绝对值 220、倍数 1.0
        assertEquals(220, UiScaleInput.resolveDensityDpi(189, 220, 1.0f))
        // 两个都设了：绝对值赢（这是既有设计，本轮不改口径）
        assertEquals(220, UiScaleInput.resolveDensityDpi(189, 220, 1.2f))
        assertEquals(320, UiScaleInput.resolveDensityDpi(189, 320, 0.5f))
    }

    @Test
    fun resolveDensityDpi_absoluteBelowFloorIsIgnored() {
        // 72 是 `UiScaleInput.DENSITY_MIN`；低于它当作「没设置」（设置页的写盘阈值同源）
        assertEquals(189, UiScaleInput.resolveDensityDpi(189, 71, 1.0f))
        assertEquals(189, UiScaleInput.resolveDensityDpi(189, 0, 1.0f))
        assertEquals(189, UiScaleInput.resolveDensityDpi(189, -1, 1.0f))
        // 边界值本身必须生效
        assertEquals(72, UiScaleInput.resolveDensityDpi(189, 72, 1.0f))
    }

    // ---- 非法倍数：宁可不动，也不能把屏幕压成 0 ----

    @Test
    fun resolveDensityDpi_rejectsIllegalTimes() {
        // 旧代码 `(189 * 0f).toInt()` = 0 → 把 densityDpi 写成 0（DENSITY_DPI_UNDEFINED），
        // 等于整屏 0 像素。倍数只可能来自输入框（`parseDpi` 卡 0.1~10）或被改过的偏好文件。
        assertEquals(189, UiScaleInput.resolveDensityDpi(189, -1, 0f))
        assertEquals(189, UiScaleInput.resolveDensityDpi(189, -1, -1f))
        assertEquals(189, UiScaleInput.resolveDensityDpi(189, -1, 50f))
        assertEquals(189, UiScaleInput.resolveDensityDpi(189, -1, 0.05f))
        assertEquals(189, UiScaleInput.resolveDensityDpi(189, -1, Float.NaN))
    }

    @Test
    fun resolveDensityDpi_absoluteSurvivesIllegalTimes() {
        // 倍数是垃圾，但绝对 DPI 设过 —— 界面照旧按绝对值走
        assertEquals(220, UiScaleInput.resolveDensityDpi(189, 220, 0f))
        assertEquals(220, UiScaleInput.resolveDensityDpi(189, 220, Float.NaN))
    }

    // ---- 基线异常：不崩、不除零、不放大 ----

    @Test
    fun resolveDensityDpi_handlesNonPositiveBase() {
        assertEquals(0, UiScaleInput.resolveDensityDpi(0, -1, 1.2f))
        assertEquals(-1, UiScaleInput.resolveDensityDpi(-1, -1, 1.2f))
        // 绝对 DPI 与基线无关，仍然生效
        assertEquals(220, UiScaleInput.resolveDensityDpi(0, 220, 1.0f))
    }

    // ---- 全链路口径：基线取「未被覆写」的值，反复调用不会把倍数乘两次 ----

    @Test
    fun resolveDensityDpi_isIdempotentForSameBase() {
        val first = UiScaleInput.resolveDensityDpi(189, -1, 1.2f)
        val second = UiScaleInput.resolveDensityDpi(189, -1, 1.2f)
        assertEquals(first, second)
        assertEquals(226, first)
    }
}
