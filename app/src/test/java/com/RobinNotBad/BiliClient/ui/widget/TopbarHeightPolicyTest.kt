package com.RobinNotBad.BiliClient.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「顶栏高度」设置项的纯逻辑单测。
 *
 * 这块错了的后果：设置页写入只判「能不能解析成整数」，脏值 / 越界值全靠读侧钳制；
 * 钳制失效会让顶栏把整个列表挤没（上限失效）或让默认值不再等于改动前（0 被当成有效高度）。
 */
class TopbarHeightPolicyTest {

    // ---- 钳制 ----

    @Test
    fun clampDp_keepsLegalValues() {
        assertEquals(0, TopbarHeightPolicy.clampDp(0))
        assertEquals(1, TopbarHeightPolicy.clampDp(1))
        assertEquals(30, TopbarHeightPolicy.clampDp(30))
        assertEquals(TopbarHeightPolicy.MAX_DP, TopbarHeightPolicy.clampDp(TopbarHeightPolicy.MAX_DP))
    }

    @Test
    fun clampDp_trimsOverflowAndNegatives() {
        assertEquals(TopbarHeightPolicy.MAX_DP, TopbarHeightPolicy.clampDp(999))
        assertEquals(0, TopbarHeightPolicy.clampDp(-1))
        assertEquals(0, TopbarHeightPolicy.clampDp(Int.MIN_VALUE))
        assertEquals(TopbarHeightPolicy.MAX_DP, TopbarHeightPolicy.clampDp(Int.MAX_VALUE))
    }

    // ---- dp → px ----

    @Test
    fun resolveMinHeightPx_zeroMeansNoChange() {
        // 0 = 跟随内容：必须返回 0，调用方据此设 minimumHeight=0（= 不干预）
        assertEquals(0, TopbarHeightPolicy.resolveMinHeightPx(0, 3.0f))
        assertEquals(0, TopbarHeightPolicy.resolveMinHeightPx(-8, 3.0f))
    }

    @Test
    fun resolveMinHeightPx_convertsByDensity() {
        assertEquals(96, TopbarHeightPolicy.resolveMinHeightPx(32, 3.0f))
        assertEquals(96, TopbarHeightPolicy.resolveMinHeightPx(64, 1.5f))
        // 非整数密度按四舍五入（26.11.11 只做取整，不做对齐）
        assertEquals(75, TopbarHeightPolicy.resolveMinHeightPx(25, 3.0f))
    }

    @Test
    fun resolveMinHeightPx_clampsBeforeConverting() {
        assertEquals(
            (TopbarHeightPolicy.MAX_DP * 2.0f).toInt(),
            TopbarHeightPolicy.resolveMinHeightPx(1000, 2.0f)
        )
    }

    @Test
    fun resolveMinHeightPx_ignoresNonPositiveDensity() {
        // density 异常（0 / 负数）时宁可当作「不干预」，也不要给出 0 高度以外的怪值
        assertEquals(0, TopbarHeightPolicy.resolveMinHeightPx(32, 0f))
        assertEquals(0, TopbarHeightPolicy.resolveMinHeightPx(32, -1f))
    }

    // ---- 竖直居中 ----

    @Test
    fun shouldCenterVertically_onlyWhenTallerAndSquare() {
        assertTrue(TopbarHeightPolicy.shouldCenterVertically(96, roundMode = false))
        assertFalse("默认 0 时不该动任何 layoutParams", TopbarHeightPolicy.shouldCenterVertically(0, roundMode = false))
        assertFalse("圆屏自己排版，居中会重叠", TopbarHeightPolicy.shouldCenterVertically(96, roundMode = true))
    }
}
