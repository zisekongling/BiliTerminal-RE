package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * 「更多」九宫格自适应几何（[PlayerNewUiFormSpec.gridFit]）的纯 JVM 测试。
 *
 * 覆盖任务要求的五种尺寸（192 / 233 / 153.6 / 256 / 310.7dp）外加参考尺寸与手机/长条屏，
 * 核心不变式：
 * - 网格右缘（含页码点热区）不越屏宽；
 * - 圆屏网格与点列四角都在表圈半径内、网格对屏幕中心水平居中；
 * - 标题不压第一行、收起按钮不压顶栏时钟（底边 32dp）；
 * - 热区恒 ≥24dp，只缩视觉与间距；
 * - 参考尺寸（方 240×280 / 圆 233）逐项复现设计稿。
 */
class PlayerNewUiGridFitTest {

    private val watch = PlayerNewUiFormSpec.WATCH
    private val eps = 0.01f

    /** 16 个「更多」按钮按每页 9 格估的页数（与控制器 gridPageEstimate 同口径）。 */
    private val estimate = 2

    private fun fitSquare(w: Float, h: Float, spec: PlayerNewUiFormSpec.Spec = watch) =
        PlayerNewUiFormSpec.gridFit(spec, round = false, width = w, height = h, pageCount = estimate)

    private fun fitRound(d: Float) =
        PlayerNewUiFormSpec.gridFit(watch, round = true, width = d, height = d, pageCount = estimate)

    private fun dist(x: Float, y: Float, cx: Float, cy: Float) = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))

    /** 两矩形不相交：水平或竖直至少一个方向分离。 */
    private fun separated(
        ax: Float, ay: Float, aw: Float, ah: Float,
        bx: Float, by: Float, bw: Float, bh: Float,
    ): Boolean = ax >= bx + bw - eps || bx >= ax + aw - eps || ay >= by + bh - eps || by >= ay + ah - eps

    private fun assertSquareInvariants(fit: PlayerNewUiFormSpec.GridFit, u: Float) {
        val w = fit.screenW
        val h = fit.screenH
        assertTrue("缩放系数必须在 (0,1]", fit.fitScale > 0f && fit.fitScale <= 1f)
        assertEquals(u * fit.fitScale, fit.unit, eps)
        // 网格与整行（网格 + 留白 + 点列热区）都在屏内。
        assertTrue("网格左缘不能为负", fit.gridLeft >= -eps)
        assertTrue("网格右缘越屏宽", fit.gridLeft + fit.gridW <= w + eps)
        assertTrue("页码点热区越屏宽", fit.dotsLeft + fit.dots.hot <= w + eps)
        // 标题不压第一行；提示整行留在屏内。
        assertTrue("标题压第一行", fit.titleTop + fit.titleH <= fit.gridTop + eps)
        assertTrue("提示出屏", fit.gridTop + fit.gridH + fit.hintGap + fit.hintH <= h + eps)
        // 点列在屏内，热区不缩。
        assertTrue(fit.dotsTop >= -eps)
        assertTrue("点列出屏", fit.dotsTop + fit.dots.height <= h + eps)
        assertTrue("热区小于 24dp", fit.dots.hot >= PlayerNewUiFormSpec.MIN_TOUCH_DP - eps)
        assertTrue("点距小于热区", fit.dots.spacing >= fit.dots.hot - eps)
        // 收起按钮：热区 ≥24、在时钟底边（32·u）以下、屏内，且不压网格与点列。
        assertTrue(fit.collapseSize >= PlayerNewUiFormSpec.MIN_TOUCH_DP - eps)
        assertTrue("收起压时钟", fit.collapseTop >= 32f * u - eps)
        assertTrue(fit.collapseLeft >= -eps)
        assertTrue(fit.collapseLeft + fit.collapseSize <= w + eps)
        assertTrue(fit.collapseTop + fit.collapseSize <= h + eps)
        assertTrue(
            "收起压网格",
            separated(
                fit.collapseLeft, fit.collapseTop, fit.collapseSize, fit.collapseSize,
                fit.gridLeft, fit.gridTop, fit.gridW, fit.gridH,
            ),
        )
        assertTrue(
            "收起压点列",
            separated(
                fit.collapseLeft, fit.collapseTop, fit.collapseSize, fit.collapseSize,
                fit.dotsLeft, fit.dotsTop, fit.dots.hot, fit.dots.height,
            ),
        )
        // 标题右缘不压收起（收起在顶部时）。
        if (!fit.collapseAtBottom) {
            assertTrue(fit.titleLeft + fit.titleWidth <= fit.collapseLeft + eps)
        }
    }

    private fun assertRoundInvariants(fit: PlayerNewUiFormSpec.GridFit) {
        val r = fit.screenW / 2f
        val cx = r
        val cy = fit.screenH / 2f
        assertTrue(fit.fitScale > 0f && fit.fitScale <= 1f)
        // 网格对屏幕中心水平居中（修实测中心偏移）。
        assertEquals("网格未水平居中", cx, fit.gridLeft + fit.gridW / 2f, eps)
        // 网格四角在表圈内。
        for (x in listOf(fit.gridLeft, fit.gridLeft + fit.gridW)) {
            for (y in listOf(fit.gridTop, fit.gridTop + fit.gridH)) {
                assertTrue("网格角被表圈切 ($x,$y)", dist(x, y, cx, cy) <= r + eps)
            }
        }
        // 点列四角也在表圈内。
        for (x in listOf(fit.dotsLeft, fit.dotsLeft + fit.dots.hot)) {
            for (y in listOf(fit.dotsTop, fit.dotsTop + fit.dots.height)) {
                assertTrue("点列角被表圈切 ($x,$y)", dist(x, y, cx, cy) <= r + eps)
            }
        }
        assertTrue("标题压第一行", fit.titleTop + fit.titleH <= fit.gridTop + eps)
        assertTrue("提示出屏", fit.gridTop + fit.gridH + fit.hintGap + fit.hintH <= fit.screenH + eps)
        assertTrue(fit.dots.hot >= PlayerNewUiFormSpec.MIN_TOUCH_DP - eps)
        assertTrue(fit.dots.spacing >= fit.dots.hot - eps)
        // 圆屏没有收起按钮（设计稿 morePanel 口径）。
        assertEquals(0f, fit.collapseSize, 0f)
    }

    @Test
    fun `square reference reproduces design spec`() {
        val fit = fitSquare(240f, 280f)
        assertEquals(1f, fit.fitScale, eps)
        assertEquals(64f, fit.cellW, eps)
        assertEquals(58f, fit.cellH, eps)
        assertEquals(6f, fit.gap, eps)
        assertEquals(204f, fit.gridW, eps)
        assertEquals(186f, fit.gridH, eps)
        assertEquals(46f, fit.gridTop, eps)
        assertEquals(16f, fit.titleTop, eps)
        assertEquals(18f, fit.titleH, eps)
        assertEquals(13f, fit.titleText, eps)
        // 提示 top244 行高12 9sp。
        assertEquals(244f, fit.gridTop + fit.gridH + fit.hintGap, eps)
        assertEquals(12f, fit.hintH, eps)
        assertEquals(9f, fit.hintText, eps)
        // 页码点视觉 8dp、选中 12dp、间距 12→热区口径 36。
        assertEquals(8f, fit.dots.dot, eps)
        assertEquals(12f, fit.dots.dotActive, eps)
        assertEquals(36f, fit.dots.spacing, eps)
        assertSquareInvariants(fit, u = 1f)
    }

    @Test
    fun `round reference reproduces design spec`() {
        val fit = fitRound(233f)
        assertEquals(1f, fit.fitScale, eps)
        assertEquals(46f, fit.cellW, eps)
        assertEquals(46f, fit.cellH, eps)
        assertEquals(4f, fit.gap, eps)
        assertEquals(146f, fit.gridW, eps)
        assertEquals(146f, fit.gridH, eps)
        assertEquals(43.5f, fit.gridLeft, eps)
        assertEquals(42f, fit.gridTop, eps)
        assertEquals(20f, fit.titleTop, eps)
        assertEquals(16f, fit.titleH, eps)
        assertEquals(12f, fit.titleText, eps)
        assertEquals(196f, fit.gridTop + fit.gridH + fit.hintGap, eps)
        assertEquals(9f, fit.hintText, eps)
        assertEquals(8f, fit.dots.dot, eps)
        assertEquals(12f, fit.dots.dotActive, eps)
        // 点列对屏幕中心竖直居中（设计 dy116.5）。
        assertEquals(116.5f, fit.dotsTop + fit.dots.height / 2f, eps)
        assertRoundInvariants(fit)
    }

    @Test
    fun `square screens of all required sizes stay on screen`() {
        for (size in listOf(192f, 153.6f, 240f, 256f, 310.7f)) {
            val fit = fitSquare(size, if (size == 240f) 280f else size)
            assertSquareInvariants(fit, u = 1f)
            // 小屏必须真的缩了（192 上旧实现格子不缩、右边裁 12dp）。
            if (size < 240f) assertTrue("小屏未缩放", fit.fitScale < 1f)
            // 3 行必须放得下（页数估计才保守安全）。
            assertTrue(PlayerNewUiGeometry.gridCapacity(fit.cellH, fit.gap, fit.availH) >= 9)
        }
    }

    @Test
    fun `round screens of all required sizes stay inside bezel`() {
        for (d in listOf(233f, 192f, 310.7f, 466f)) {
            assertRoundInvariants(fitRound(d))
        }
        // 小表盘必须真的缩了。
        assertTrue(fitRound(192f).fitScale < 1f)
    }

    @Test
    fun `hot zones never shrink below 24dp on any form`() {
        val cases = listOf(
            fitSquare(192f, 192f), fitSquare(153.6f, 153.6f), fitSquare(256f, 256f),
            fitSquare(310.7f, 310.7f), fitRound(233f), fitRound(192f), fitRound(310.7f),
            fitSquare(411f, 731f, PlayerNewUiFormSpec.PHONE),
            fitSquare(320f, 100f, PlayerNewUiFormSpec.STRIP),
        )
        for (fit in cases) {
            assertTrue(fit.dots.hot >= 24f - eps)
            assertTrue(fit.dots.spacing >= fit.dots.hot - eps)
            // 视觉点可以缩，但必须仍是正数且不超过热区。
            assertTrue(fit.dots.dot > 0f && fit.dots.dot <= fit.dots.hot)
            assertTrue(fit.dots.dotActive >= fit.dots.dot)
        }
    }

    @Test
    fun `phone keeps all more buttons on one page`() {
        val fit = fitSquare(411f, 731f, PlayerNewUiFormSpec.PHONE)
        assertEquals(1f, fit.fitScale, eps)
        assertTrue(PlayerNewUiGeometry.gridCapacity(fit.cellH, fit.gap, fit.availH) >= 16)
        assertSquareInvariants(fit, u = PlayerNewUiFormSpec.PHONE.scale)
    }

    @Test
    fun `strip moves collapse to bottom right and keeps dots on screen`() {
        val fit = fitSquare(320f, 100f, PlayerNewUiFormSpec.STRIP)
        assertTrue("极矮屏收起应挪到底部", fit.collapseAtBottom)
        assertSquareInvariants(fit, u = PlayerNewUiFormSpec.STRIP.scale)
        // 点列不再被收起顶下去，整列落在提示行上方。
        assertTrue(fit.dotsTop + fit.dots.height <= fit.gridTop + fit.gridH + fit.hintGap + eps)
    }

    @Test
    fun `smaller screens never scale up`() {
        assertTrue(fitSquare(153.6f, 153.6f).fitScale < fitSquare(192f, 192f).fitScale)
        assertTrue(fitSquare(192f, 192f).fitScale < fitSquare(240f, 280f).fitScale)
        assertTrue(fitRound(192f).fitScale < fitRound(233f).fitScale)
        assertEquals(1f, fitRound(310.7f).fitScale, eps)
    }

    @Test
    fun `actual page count helpers stay consistent`() {
        // 实际页数 ≤ 估计值（行数 ≥3 → 每页 ≥9 格），这里逐页数验证重算口径。
        for (fit in listOf(fitSquare(192f, 192f), fitSquare(153.6f, 153.6f), fitRound(233f), fitRound(192f))) {
            for (count in 1..estimate) {
                val dots = fit.dotsFor(count)
                assertEquals(dots.spacing * count, dots.height, eps)
                val top = fit.dotsTopFor(count)
                assertTrue(top >= -eps)
                assertTrue(top + dots.height <= fit.screenH + eps)
                if (fit.round) {
                    assertEquals(fit.screenH / 2f, top + dots.height / 2f, eps)
                    assertEquals(0f, fit.dotsShiftFor(count), 0f)
                } else {
                    assertTrue(top >= fit.dotsTopMin - eps)
                    assertTrue(fit.dotsShiftFor(count) >= 0f)
                }
            }
        }
    }
}