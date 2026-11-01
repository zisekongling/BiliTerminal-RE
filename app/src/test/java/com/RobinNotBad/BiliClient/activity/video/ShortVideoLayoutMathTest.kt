package com.RobinNotBad.BiliClient.activity.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 短视频画面尺寸计算。
 *
 * 钉住两类真实缺陷的边界：
 * 1. **尺寸未知**（`onPrepared` 时容器元数据没带分辨率）时不能返回「铺满」，否则
 *    `TextureView` 会留在 `MATCH_PARENT`，用户看到的就是「画面被强行拉伸成全屏」；
 *    调用方会改用 [ShortVideoLayoutMath.DEFAULT_RATIO] 之类的退化比例。
 * 2. **旋转 90/270**（容器给未旋转尺寸、解码器出旋转后的帧）必须交换宽高，
 *    否则竖屏视频会按横屏比例摆框，观感同样是「拉伸」。
 */
class ShortVideoLayoutMathTest {

    // 手表方屏的典型数值：466×466
    private val area = 466

    @Test
    fun portraitVideo_letterboxesWithBlackBars() {
        // 360x640（9:16）摆进 466x466 方屏：宽 262、高 466，左右留黑边
        val size = ShortVideoLayoutMath.fit(360f / 640f, area, area)!!

        assertEquals(262, size[0])
        assertEquals(466, size[1])
    }

    @Test
    fun landscapeVideo_letterboxesTopBottom() {
        // 16:9 摆进 466x466 方屏：满宽 466、高 262
        val size = ShortVideoLayoutMath.fit(16f / 9f, area, area)!!

        assertEquals(466, size[0])
        assertEquals(262, size[1])
    }

    @Test
    fun squareVideo_fillsSquareAreaExactly() {
        val size = ShortVideoLayoutMath.fit(1f, area, area)!!

        assertArrayEquals(intArrayOf(466, 466), size)
    }

    @Test
    fun illegalRatio_fallsBackToDefaultRatio() {
        // 0 / 负数 / NaN / Infinity 都不能参与除法，按 DEFAULT_RATIO（16:9）退化
        for (bad in listOf(0f, -1.5f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(
                "非法比例 $bad 必须退化",
                ShortVideoLayoutMath.DEFAULT_RATIO,
                ShortVideoLayoutMath.sanitizeRatio(bad),
                0.0001f
            )
            val size = ShortVideoLayoutMath.fit(bad, area, area)!!
            assertEquals("16:9 退化后应满宽", 466, size[0])
            assertEquals("16:9 退化后高 262", 262, size[1])
        }
    }

    @Test
    fun illegalArea_returnsNullInsteadOfStretching() {
        // 区域还没测量出来（0 或负）：返回 null，调用方保持原样等下一次布局，
        // 绝不能自己编一个「铺满整屏」的尺寸
        assertNull(ShortVideoLayoutMath.fit(360f / 640f, 0, 466))
        assertNull(ShortVideoLayoutMath.fit(360f / 640f, 466, 0))
        assertNull(ShortVideoLayoutMath.fit(360f / 640f, -1, 466))
    }

    @Test
    fun swapOnlyForQuarterTurns() {
        assertTrue(ShortVideoLayoutMath.swapsWidthHeight(90))
        assertTrue(ShortVideoLayoutMath.swapsWidthHeight(270))
        assertFalse(ShortVideoLayoutMath.swapsWidthHeight(0))
        assertFalse(ShortVideoLayoutMath.swapsWidthHeight(180))
        assertFalse(ShortVideoLayoutMath.swapsWidthHeight(360))
    }

    @Test
    fun roundScreen_portraitVideoUsesInscribedCircleRectangle() {
        // 圆屏 466 方屏 + 9:16：内接圆矩形的宽 = sqrt(466² / ((16/9)²+1)) ≈ 228，高 ≈ 406
        val size = ShortVideoLayoutMath.fitRound(360f / 640f, area, area, area)!!

        assertEquals(228, size[0])
        assertEquals(406, size[1])
        // 比例必须保持（±1px 取整误差）
        val ratio = size[0].toFloat() / size[1].toFloat()
        assertEquals(360f / 640f, ratio, 0.01f)
    }

    @Test
    fun roundScreen_landscapeVideoNotWiderThanScreen() {
        // 圆屏 + 16:9：内接圆矩形给 406x228；不能因为取长边当直径而算出比屏还宽
        val size = ShortVideoLayoutMath.fitRound(16f / 9f, area, area, area)!!

        assertEquals(406, size[0])
        assertEquals(228, size[1])
        assertTrue("宽度不能超过屏幕", size[0] <= area)
        assertTrue("高度不能超过屏幕", size[1] <= area)
    }

    @Test
    fun roundScreen_clampedIntoSmallArea() {
        // 极端长宽比 + 很小的可用区域：必须等比夹进区域，不能溢出被裁掉
        val size = ShortVideoLayoutMath.fitRound(21f / 9f, area, 300, area)!!

        assertNotNull(size)
        assertTrue("宽度不能超过可用区域", size[0] <= 300)
        assertTrue("高度不能超过可用区域", size[1] <= area)
        val ratio = size[0].toFloat() / size[1].toFloat()
        assertEquals(21f / 9f, ratio, 0.02f)
    }

    @Test
    fun roundScreen_illegalDiameter_returnsNull() {
        assertNull(ShortVideoLayoutMath.fitRound(16f / 9f, 0, area, area))
        assertNull(ShortVideoLayoutMath.fitRound(16f / 9f, area, 0, area))
    }
}
