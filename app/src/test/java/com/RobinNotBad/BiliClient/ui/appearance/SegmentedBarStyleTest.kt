package com.RobinNotBad.BiliClient.ui.appearance

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [SegmentedBarStyle] 里那一套透明度的纯 JVM 单测。
 *
 * <p>要钉住的是「**颜色从哪来**」而不是「好不好看」：轨道必须由 `TEXT_PRIMARY` 派生
 * （这样亮色主题下自动变成深色雾层），胶囊必须由 `PRIMARY` / `PRIMARY_LIGHT` 派生
 * （跟着主题换色），文字必须分别是 `TEXT_PRIMARY` / `TEXT_SECONDARY`。
 *
 * <p>这些数值是设计稿 E 量出来的：改错了界面不会崩，只会「差一点点」——
 * 肉眼盯着截图对半天也未必看得出来，所以用单测钉死。
 */
class SegmentedBarStyleTest {

    // 故意选四个互不相同的色，任何一处「取错源色」都会让断言失败
    private val primary = 0xFFFF6699.toInt()
    private val primaryLight = 0xFFFF8CB0.toInt()
    private val textPrimary = 0xFFEBE0E2.toInt()
    private val textSecondary = 0xFFB8AEB2.toInt()

    private fun colors() = SegmentedBarStyle.colorsFor(primary, primaryLight, textPrimary, textSecondary)

    /** ARGB 里只保留 alpha 字节，便于逐项断言透明度。 */
    private fun alphaOf(color: Int): Int = (color ushr 24) and 0xFF

    /** 去掉 alpha，便于断言「RGB 来自哪个源色」。 */
    private fun rgbOf(color: Int): Int = color and 0x00FFFFFF

    @Test
    fun `轨道由文字主色派生，亮暗主题都能看见`() {
        val colors = colors()
        assertEquals(rgbOf(textPrimary), rgbOf(colors.trackFill))
        assertEquals(0x12, alphaOf(colors.trackFill))
        assertEquals(rgbOf(textPrimary), rgbOf(colors.trackStroke))
        assertEquals(0x21, alphaOf(colors.trackStroke))
    }

    @Test
    fun `胶囊是主色到浅主色的水平渐变`() {
        val colors = colors()
        assertEquals(rgbOf(primary), rgbOf(colors.pillStart))
        assertEquals(0x57, alphaOf(colors.pillStart))
        assertEquals(rgbOf(primaryLight), rgbOf(colors.pillEnd))
        assertEquals(0x1A, alphaOf(colors.pillEnd))
    }

    @Test
    fun `胶囊描边用浅主色、光晕用主色`() {
        val colors = colors()
        assertEquals(rgbOf(primaryLight), rgbOf(colors.pillStroke))
        assertEquals(0x6B, alphaOf(colors.pillStroke))
        assertEquals(rgbOf(primary), rgbOf(colors.pillGlow))
        assertEquals(0x1A, alphaOf(colors.pillGlow))
    }

    @Test
    fun `左侧竖条是主色本身，不做透明处理`() {
        // 竖条是整条里唯一的实色，透明度被压过就会糊在胶囊渐变里看不出来
        assertEquals(primary, colors().accent)
    }

    @Test
    fun `选中文字亮、未选中文字灰`() {
        val colors = colors()
        assertEquals(textPrimary, colors.textSelected)
        assertEquals(textSecondary, colors.textUnselected)
    }

    @Test
    fun `未选中格子的按压反馈也是低透明度文字色`() {
        val colors = colors()
        assertEquals(rgbOf(textPrimary), rgbOf(colors.pressed))
        assertEquals(0x0F, alphaOf(colors.pressed))
    }

    @Test
    fun `换一套主题色，派生结果跟着换`() {
        // 亮色主题：主色是深蓝、文字主色是近黑，派生出的仍是同样的透明度结构
        val light = SegmentedBarStyle.colorsFor(
            0xFF1A73E8.toInt(),
            0xFF4C8DF6.toInt(),
            0xFF1A1A1A.toInt(),
            0xFF666666.toInt()
        )
        assertEquals(rgbOf(0xFF1A1A1A.toInt()), rgbOf(light.trackFill))
        assertEquals(0x12, alphaOf(light.trackFill))
        assertEquals(rgbOf(0xFF1A73E8.toInt()), rgbOf(light.pillStart))
        assertEquals(0xFF1A73E8.toInt(), light.accent)
    }
}
