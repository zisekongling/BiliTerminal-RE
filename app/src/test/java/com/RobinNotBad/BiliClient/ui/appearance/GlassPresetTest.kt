package com.RobinNotBad.BiliClient.ui.appearance

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.util.FakeSharedPreferences
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [GlassPreset] 的纯 JVM 单测。
 *
 * 钉住三件「错了不会报错、只会静静显示错样子」的事：
 * 1. 档位值与显示名必须一一对应——顺序错位会让用户选「稳扎稳打」却得到「晶莹剔透」；
 * 2. 默认档必须是 [GlassPreset.BALANCED] 且**不叠覆盖层**——否则老用户观感会被这次改动改掉；
 * 3. 三档合并出的「常态 / 选中」两种形态必须真的不同，且**选中态在任何档位都不是常态色**。
 */
class GlassPresetTest {

    private val fakePrefs = FakeSharedPreferences()

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = fakePrefs
    }

    @After
    fun tearDown() {
        SharedPreferencesUtil.sharedPreferences = null
    }

    // ==================== 档位本身 ====================

    @Test
    fun default_isBalanced() {
        assertEquals(
            "默认必须是「主次分明」——它就是引入本设置项之前的观感，老用户升级后不该看到任何变化",
            GlassPreset.BALANCED, GlassPreset.DEFAULT
        )
        assertEquals(GlassPreset.BALANCED, GlassPreset.current())
    }

    @Test
    fun normalize_unknownValueFallsBackToDefault() {
        assertEquals(GlassPreset.DEFAULT, GlassPreset.normalize(null))
        assertEquals(GlassPreset.DEFAULT, GlassPreset.normalize(""))
        assertEquals(GlassPreset.DEFAULT, GlassPreset.normalize("garbage"))
        // 大小写敏感：不做模糊匹配，坏值一律回落
        assertEquals(GlassPreset.DEFAULT, GlassPreset.normalize("CRYSTAL"))
    }

    @Test
    fun normalize_keepsValidValues() {
        assertEquals(GlassPreset.BALANCED, GlassPreset.normalize(GlassPreset.BALANCED))
        assertEquals(GlassPreset.STEADY, GlassPreset.normalize(GlassPreset.STEADY))
        assertEquals(GlassPreset.CRYSTAL, GlassPreset.normalize(GlassPreset.CRYSTAL))
    }

    @Test
    fun current_reflectsSavedValue() {
        SharedPreferencesUtil.putString(GlassPreset.KEY, GlassPreset.CRYSTAL)
        assertEquals(GlassPreset.CRYSTAL, GlassPreset.current())

        SharedPreferencesUtil.putString(GlassPreset.KEY, GlassPreset.STEADY)
        assertEquals(GlassPreset.STEADY, GlassPreset.current())
    }

    @Test
    fun current_unknownSavedValueFallsBackToDefault() {
        SharedPreferencesUtil.putString(GlassPreset.KEY, "some_removed_option")
        assertEquals(GlassPreset.DEFAULT, GlassPreset.current())
    }

    @Test
    fun valuesAndDisplayNames_areParallel() {
        assertEquals(
            "候选值与显示名必须逐项对应，错位会让用户选「稳扎稳打」却得到「晶莹剔透」",
            GlassPreset.VALUES.size, GlassPreset.DISPLAY_NAMES.size
        )
        assertEquals(GlassPreset.VALUES.size, GlassPreset.VALUES.distinct().size)
        assertEquals(GlassPreset.VALUES.size, GlassPreset.DISPLAY_NAMES.distinct().size)
        assertEquals(
            "三档都要在候选列表里，否则用户永远选不到",
            listOf(GlassPreset.BALANCED, GlassPreset.STEADY, GlassPreset.CRYSTAL), GlassPreset.VALUES
        )
        assertEquals(
            "默认档必须排在第一位：listChoose 用它当前选中项，顺序也决定用户第一眼看到什么",
            GlassPreset.BALANCED, GlassPreset.VALUES.first()
        )
    }

    @Test
    fun key_isTheSettingsKeysConstant() {
        assertEquals(SettingsKeys.UI_GLASS_PRESET, GlassPreset.KEY)
    }

    // ==================== 档位 → 覆盖层（让按钮 / 卡片 / 三连卡片跟着变的那一步） ====================

    /** 七套主题全部要测：漏一套就会有主题在换档后「没反应」。 */
    private val allThemes = listOf(
        ColorScheme.THEME_BILIBILI_PINK,
        ColorScheme.THEME_ZHIHU_BLUE,
        ColorScheme.THEME_IQIYI_GREEN,
        ColorScheme.THEME_PURPLE_FANTASY,
        ColorScheme.THEME_RAINBOW_FANTASY,
        ColorScheme.THEME_CLASSIC_GRAY,
        ColorScheme.THEME_CLASSIC_TERMINAL
    )

    @Test
    fun presetOverlayResId_isNullForBalanced() {
        for (theme in allThemes) {
            assertNull(
                "主次分明 = 现状，不能叠第二层覆盖层（否则默认档也会变样，且白付一次 applyStyle）",
                GlassStyle.presetOverlayResId(theme, GlassPreset.BALANCED)
            )
        }
    }

    @Test
    fun presetOverlayResId_isNullForUnknownPreset() {
        // 存档坏了按默认档处理：不叠层，而不是抛异常或叠错层
        assertNull(GlassStyle.presetOverlayResId(ColorScheme.THEME_ZHIHU_BLUE, "garbage"))
    }

    @Test
    fun presetOverlayResId_mapsEveryThemeAndPresetToItsOwnStyle() {
        val steadyOf = allThemes.map { GlassStyle.presetOverlayResId(it, GlassPreset.STEADY) }
        val crystalOf = allThemes.map { GlassStyle.presetOverlayResId(it, GlassPreset.CRYSTAL) }

        assertTrue(
            "每套主题 × 两档都必须有覆盖层",
            (steadyOf + crystalOf).all { it != null && it != 0 }
        )
        assertEquals(
            "同一档下每套主题必须是各自的覆盖层，不能共用一份色值",
            steadyOf.size, steadyOf.distinct().size
        )
        assertEquals(crystalOf.size, crystalOf.distinct().size)
        for (i in allThemes.indices) {
            assertNotEquals(
                "同一套主题的两档必须指向不同覆盖层，否则切换档位没有任何变化",
                steadyOf[i], crystalOf[i]
            )
        }
    }

    // ==================== 代码侧合并出的「常态 / 选中」 ====================

    // 用移位拼 ARGB，避免 Kotlin 里 0xFF...... 字面量的 Int / Long 归属问题。
    private val card = (0xC0 shl 24) or 0x1A1014 // 卡片色（约 78% 不透明）
    private val stroke = (0x38 shl 24) or 0xFF6699 // 柔和描边
    private val fill = (0x33 shl 24) or 0xFF6699 // 主色 20% 淡底
    private val fillStroke = (0x99 shl 24) or 0xFF6699 // 主色 60% 亮描边

    @Test
    fun surfaces_balancedAndSteadyKeepUnselectedCardsQuiet() {
        for (preset in listOf(GlassPreset.BALANCED, GlassPreset.STEADY)) {
            val s = GlassStyle.surfaces(preset, card, stroke, fill, fillStroke)
            assertEquals(preset, card, s.normalFill)
            assertEquals(preset, stroke, s.normalStroke)
            assertEquals(preset, fill, s.selectedFill)
            assertEquals(preset, fillStroke, s.selectedStroke)
        }
    }

    @Test
    fun surfaces_crystalLiftsCardsToAccentButKeepsSelectionDistinct() {
        val s = GlassStyle.surfaces(GlassPreset.CRYSTAL, card, stroke, fill, fillStroke)
        assertEquals("晶莹剔透下卡片改用主题色淡底", fill, s.normalFill)
        assertEquals(
            "大面积用 60% 亮描边会像被框住的按钮，常态描边仍用柔和的玻璃描边",
            stroke, s.normalStroke
        )
        assertEquals("选中态再浓一档", GlassStyle.accentFill(fill), s.selectedFill)
        assertEquals(fillStroke, s.selectedStroke)
        assertNotEquals(
            "选中态不能与常态同色，否则看不出选了哪个",
            s.normalFill, s.selectedFill
        )
    }

    @Test
    fun accentFill_doublesAlphaAndKeepsRgb() {
        val boosted = GlassStyle.accentFill(fill)
        assertEquals("alpha 翻倍（暗色 20% → 40%）", 0x33 * 2, (boosted ushr 24) and 0xFF)
        assertEquals("RGB 通道一个都不能动", fill and 0x00FFFFFF, boosted and 0x00FFFFFF)
    }

    @Test
    fun accentFill_capsAtOpaque() {
        // 亮色模式下主色淡底是 14%，翻倍成 28%；暗色 20% → 40%。常规值都不会溢出，
        // 但改大了 alpha 就会——必须封顶到 0xFF，而不是回绕成透明或变成别的颜色。
        assertEquals(0xFF, (GlassStyle.accentFill((0xF0 shl 24) or 0xFF6699) ushr 24) and 0xFF)
        assertEquals(0xFF, (GlassStyle.accentFill((0xFF shl 24) or 0xFF6699) ushr 24) and 0xFF)
        // 极小 alpha 也要保持偶数翻倍，不因封顶逻辑影响其它档位
        assertEquals(0x0A, (GlassStyle.accentFill((0x05 shl 24) or 0xFF6699) ushr 24) and 0xFF)
    }
}
