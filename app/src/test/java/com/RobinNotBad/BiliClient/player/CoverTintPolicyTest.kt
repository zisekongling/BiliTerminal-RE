package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G06「封面取色」的纯判据单测。
 *
 * 只钉两件事：投票取色在黑白 / 透明 / 单调画面下的退化行为，以及调色后的亮度饱和度区间。
 * 真正的接线（Glide 取封面、染色到进度条）靠真机看画面，不在单测范围。
 */
class CoverTintPolicyTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun hsv(color: Int): FloatArray {
        val r = color ushr 16 and 0xFF
        val g = color ushr 8 and 0xFF
        val b = color and 0xFF
        return CoverTintPolicy.rgbToHsv(r, g, b)
    }

    @Test
    fun `空输入取不到颜色`() {
        assertNull(CoverTintPolicy.fromPixels(null))
        assertNull(CoverTintPolicy.fromPixels(IntArray(0)))
    }

    @Test
    fun `全白全黑全灰都取不到颜色`() {
        assertNull(CoverTintPolicy.fromPixels(IntArray(64) { argb(0xFF, 255, 255, 255) }))
        assertNull(CoverTintPolicy.fromPixels(IntArray(64) { argb(0xFF, 0, 0, 0) }))
        assertNull(CoverTintPolicy.fromPixels(IntArray(64) { argb(0xFF, 128, 128, 128) }))
    }

    @Test
    fun `透明像素不参与投票`() {
        // 半透明以下的鲜艳橙不该被算进去：有效像素只剩灰色，于是取不到颜色
        val pixels = IntArray(64) { argb(0x10, 255, 120, 0) }
        assertNull(CoverTintPolicy.fromPixels(pixels))
    }

    @Test
    fun `纯色封面取出同色系且亮饱和度达标`() {
        val color = CoverTintPolicy.fromPixels(IntArray(64) { argb(0xFF, 220, 40, 40) })
        assertNotNull(color)
        val hsv = hsv(color!!)
        assertTrue("色相应留在红色区间", hsv[0] < 20f || hsv[0] > 340f)
        assertTrue("明度要够亮", hsv[2] >= 0.5f)
        assertTrue("饱和度要够艳", hsv[1] >= 0.3f)
    }

    @Test
    fun `大面积灰底上的少量彩色仍被选中`() {
        // 96 个灰（无彩，直接跳过）+ 32 个蓝：蓝的票数占比不高，但它是唯一有彩的
        val pixels = IntArray(128) { index ->
            if (index < 96) argb(0xFF, 120, 120, 120) else argb(0xFF, 30, 90, 220)
        }
        val color = CoverTintPolicy.fromPixels(pixels)
        assertNotNull(color)
        val blue = color!! and 0xFF
        val red = color ushr 16 and 0xFF
        assertTrue("应偏蓝而不是偏灰", blue > red)
    }

    @Test
    fun `深色封面提亮 浅色封面压暗`() {
        val dark = CoverTintPolicy.tuneForDarkUi(argb(0xFF, 40, 0, 0))
        val darkHsv = hsv(dark)
        assertTrue("深色要提亮到 0.5 以上", darkHsv[2] >= 0.5f)

        val light = CoverTintPolicy.tuneForDarkUi(argb(0xFF, 255, 250, 250))
        val lightHsv = hsv(light)
        assertTrue("浅色要压到 0.95 以下", lightHsv[2] <= 0.96f)
        assertTrue("几乎无彩的颜色要补饱和度", lightHsv[1] >= 0.3f)
    }

    @Test
    fun `调色不改色相 灰色补饱和后仍是同色系`() {
        val blue = CoverTintPolicy.tuneForDarkUi(argb(0xFF, 0, 0, 255))
        val hsv = hsv(blue)
        assertTrue("蓝的色相仍在 200-260 之间", hsv[0] in 200f..260f)
        assertEquals(0xFF, blue ushr 24 and 0xFF)
    }

    @Test
    fun `HSV 换算与反换算能往返`() {
        val color = argb(0xFF, 18, 52, 86)
        val hsv = hsv(color)
        val back = CoverTintPolicy.hsvToRgb(hsv[0], hsv[1], hsv[2])
        // 浮点往返允许 1 个色阶的误差
        fun channel(value: Int, shift: Int) = value ushr shift and 0xFF
        assertTrue(kotlin.math.abs(channel(back, 16) - channel(color, 16)) <= 1)
        assertTrue(kotlin.math.abs(channel(back, 8) - channel(color, 8)) <= 1)
        assertTrue(kotlin.math.abs(channel(back, 0) - channel(color, 0)) <= 1)
        assertEquals(0xFF, back ushr 24 and 0xFF)
    }
}
