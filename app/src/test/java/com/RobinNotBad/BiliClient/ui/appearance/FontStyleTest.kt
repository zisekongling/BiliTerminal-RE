package com.RobinNotBad.BiliClient.ui.appearance

import com.RobinNotBad.BiliClient.util.FakeSharedPreferences
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [FontStyle] 的纯 JVM 单测。
 *
 * 最要紧的一条是 [scaleFactor_standardIsExactlyOne]：默认档位**必须**是 1.0f，
 * 因为下游靠「系数 == 1.0f 就短路、不遍历视图树」来兑现「手表性能优先」。
 * 这个不变量一旦被破坏，默认档位会凭空产生一次全树遍历，而且不会有任何报错。
 */
class FontStyleTest {

    private val fakePrefs = FakeSharedPreferences()

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = fakePrefs
    }

    @After
    fun tearDown() {
        SharedPreferencesUtil.sharedPreferences = null
    }

    // ==================== 字号：性能不变量 ====================

    @Test
    fun scaleFactor_standardIsExactlyOne() {
        assertEquals(
            "默认档位必须恰好是 1.0f——下游的「零运行时开销」短路依赖于此",
            1.0f, FontStyle.scaleFactor(FontStyle.SCALE_DEFAULT), 0.0f
        )
        assertEquals(
            "未知档位也必须回落到 1.0f，不能变成别的倍数",
            1.0f, FontStyle.scaleFactor("garbage"), 0.0f
        )
    }

    @Test
    fun scaleFactor_increasesMonotonically() {
        val small = FontStyle.scaleFactor(FontStyle.SCALE_SMALL)
        val standard = FontStyle.scaleFactor(FontStyle.SCALE_STANDARD)
        val large = FontStyle.scaleFactor(FontStyle.SCALE_LARGE)
        val xlarge = FontStyle.scaleFactor(FontStyle.SCALE_XLARGE)

        assertTrue("小档必须真的更小（小于 1.0）", small < 1.0f)
        assertTrue("大档必须真的更大（大于 1.0）", large > 1.0f)
        assertTrue("四档必须严格单调递增", small < standard && standard < large && large < xlarge)
        assertTrue("特大档不应夸张到撑破手表布局", xlarge <= 1.5f)
    }

    @Test
    fun scaleFactor_normalizesBeforeMapping() {
        // 合法值只认常量本身
        for (value in FontStyle.SCALE_VALUES) {
            assertEquals(
                FontStyle.scaleFactor(FontStyle.normalizeScale(value)),
                FontStyle.scaleFactor(value),
                0.0f
            )
        }
    }

    // ==================== 字族 ====================

    @Test
    fun fontFamilyValue_systemIsNull_monospaceIsMonospace() {
        assertNull(
            "系统默认档必须返回 null（= 不覆盖），这样该档不会改变任何既有渲染",
            FontStyle.fontFamilyValue(FontStyle.FAMILY_SYSTEM)
        )
        assertNull(
            "未知字族同样回落为「不覆盖」",
            FontStyle.fontFamilyValue("garbage")
        )
        assertEquals("monospace", FontStyle.fontFamilyValue(FontStyle.FAMILY_MONOSPACE))
    }

    // ==================== 读取与规整 ====================

    @Test
    fun currentScale_defaultsToStandard() {
        assertEquals(FontStyle.SCALE_STANDARD, FontStyle.SCALE_DEFAULT)
        assertEquals(FontStyle.SCALE_STANDARD, FontStyle.currentScale())
    }

    @Test
    fun currentFamily_defaultsToSystem() {
        assertEquals(FontStyle.FAMILY_SYSTEM, FontStyle.FAMILY_DEFAULT)
        assertEquals(FontStyle.FAMILY_SYSTEM, FontStyle.currentFamily())
    }

    @Test
    fun current_reflectsSavedValues() {
        SharedPreferencesUtil.putString(FontStyle.KEY_SCALE, FontStyle.SCALE_LARGE)
        SharedPreferencesUtil.putString(FontStyle.KEY_FAMILY, FontStyle.FAMILY_MONOSPACE)
        assertEquals(FontStyle.SCALE_LARGE, FontStyle.currentScale())
        assertEquals(FontStyle.FAMILY_MONOSPACE, FontStyle.currentFamily())
    }

    @Test
    fun current_unknownSavedValuesFallBackToDefaults() {
        SharedPreferencesUtil.putString(FontStyle.KEY_SCALE, "huge")
        SharedPreferencesUtil.putString(FontStyle.KEY_FAMILY, "comic_sans")
        assertEquals(FontStyle.SCALE_DEFAULT, FontStyle.currentScale())
        assertEquals(FontStyle.FAMILY_DEFAULT, FontStyle.currentFamily())
    }

    // ==================== 选项列表一致性 ====================

    @Test
    fun scaleOptions_areParallelAndDistinct() {
        assertEquals(FontStyle.SCALE_VALUES.size, FontStyle.SCALE_DISPLAY_NAMES.size)
        assertEquals(4, FontStyle.SCALE_VALUES.size)
        assertEquals(FontStyle.SCALE_VALUES.size, FontStyle.SCALE_VALUES.distinct().size)
        assertEquals(FontStyle.SCALE_DISPLAY_NAMES.size, FontStyle.SCALE_DISPLAY_NAMES.distinct().size)
    }

    @Test
    fun familyOptions_areParallelAndDistinct() {
        assertEquals(FontStyle.FAMILY_VALUES.size, FontStyle.FAMILY_DISPLAY_NAMES.size)
        assertEquals(listOf(FontStyle.FAMILY_SYSTEM, FontStyle.FAMILY_MONOSPACE), FontStyle.FAMILY_VALUES)
    }

    @Test
    fun keys_areTheSettingsKeysConstants() {
        assertEquals(SettingsKeys.UI_FONT_SCALE, FontStyle.KEY_SCALE)
        assertEquals(SettingsKeys.UI_FONT_FAMILY, FontStyle.KEY_FAMILY)
        assertTrue("字号与字族必须是两个独立 key", FontStyle.KEY_SCALE != FontStyle.KEY_FAMILY)
    }
}
