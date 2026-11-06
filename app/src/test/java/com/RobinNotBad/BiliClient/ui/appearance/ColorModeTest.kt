package com.RobinNotBad.BiliClient.ui.appearance

import com.RobinNotBad.BiliClient.util.FakeSharedPreferences
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ColorMode] 的「夜间位」判定单测。
 *
 * 这里只测**纯判定**，不调 `applyGlobal()`（那会碰 `AppCompatDelegate`，JVM 单测跑不了），
 * 也刻意不 import `android.content.res.Configuration`：单测里只比数值，避免依赖 mockable jar 的常量。
 *
 * 为什么值得单独测：整个亮/暗模式都靠「我们自己写 uiMode 的夜间位」生效——
 * 暗色写 `NIGHT_YES` 命中默认 `values/`，亮色写 `NIGHT_NO` 命中 `values-notnight/`。
 * 这个换位一旦写反，症状是「切了亮色但界面没变」或「暗色用户看到亮色」，
 * 而这在手表上极难一眼看出来（手表系统本身几乎常暗）。这也是为什么**不做** `values-night/`：
 * 手动两态已经由 `values/` + `values-notnight/` 表达完，再加 `values-night/` 只会把同一套暗色拆两处。
 */
class ColorModeTest {

    private val fakePrefs = FakeSharedPreferences()

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = fakePrefs
        fakePrefs.edit().clear().apply()
    }

    @After
    fun tearDown() {
        SharedPreferencesUtil.sharedPreferences = null
    }

    @Test
    fun default_isDarkMode() {
        // 没存过任何值 → 暗色（默认），夜间位必须写 YES 才能命中默认 values/
        assertEquals(ColorMode.DARK, ColorMode.current())
        assertFalse(ColorMode.isLight())
        assertEquals(NIGHT_YES, ColorMode.nightBits())
    }

    @Test
    fun lightMode_writesNightNo() {
        SharedPreferencesUtil.putStringSync(ColorMode.KEY, ColorMode.LIGHT)
        assertTrue(ColorMode.isLight())
        assertEquals(NIGHT_NO, ColorMode.nightBits())
    }

    @Test
    fun forceDark_overridesLightMode() {
        // 播放器 / 闪屏用 forceDark=true：即使全局是亮色，这两处也必须拿暗色上下文
        SharedPreferencesUtil.putStringSync(ColorMode.KEY, ColorMode.LIGHT)
        assertEquals(NIGHT_YES, ColorMode.nightBits(forceDark = true))
    }

    @Test
    fun unknownStoredValue_fallsBackToDark() {
        SharedPreferencesUtil.putStringSync(ColorMode.KEY, "follow_system")
        assertEquals(ColorMode.DARK, ColorMode.current())
        assertFalse(ColorMode.isLight())
        assertEquals(NIGHT_YES, ColorMode.nightBits())
    }

    private companion object {
        /** 与 `android.content.res.Configuration` 的取值一致：`UI_MODE_NIGHT_NO = 0x10`、`UI_MODE_NIGHT_YES = 0x20`。 */
        const val NIGHT_NO = 0x10
        const val NIGHT_YES = 0x20
    }
}
