package com.RobinNotBad.BiliClient.ui.appearance

import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import com.RobinNotBad.BiliClient.util.FakeSharedPreferences
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * [AppearanceManager] 的纯 JVM 单测。
 *
 * 核心是**版本号机制**：它是「Activity 只比一个 Int 就知道外观变没变」的唯一依据。
 * 任何一个写入点漏了 `bumpVersion`，症状都是「改了设置但页面不刷新」——
 * 而且在手工测试里很容易被 `recreate()` 掩盖（设置页自己会重建，返回上一页才发现没变）。
 */
class AppearanceManagerTest {

    private val fakePrefs = FakeSharedPreferences()

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = fakePrefs
        // 色表是单例缓存，借 setTheme 清一次；随后清空假 prefs 回到「无任何存档」状态。
        ThemeManager.setTheme(ThemeManager.THEME_DEFAULT)
        fakePrefs.edit().clear().apply()
    }

    @After
    fun tearDown() {
        SharedPreferencesUtil.sharedPreferences = null
    }

    // ==================== 版本号 ====================

    @Test
    fun initialVersion_isZero() {
        assertEquals(AppearanceManager.INITIAL_VERSION, AppearanceManager.version())
    }

    @Test
    fun setTheme_persistsAndBumpsVersion() {
        AppearanceManager.setTheme(ThemeManager.THEME_ZHIHU_BLUE)
        assertEquals(
            "主题写入必须落盘，否则 recreate() 后读到旧主题",
            ThemeManager.THEME_ZHIHU_BLUE, ThemeManager.getCurrentThemeName()
        )
        assertEquals(
            "主题写入必须递增外观版本号，否则其它页面在 onResume 时不会重建",
            AppearanceManager.INITIAL_VERSION + 1, AppearanceManager.version()
        )
    }

    @Test
    fun themeManagerSetTheme_alsoBumpsVersion() {
        // 既有入口（设置页走的就是 ThemeManager.setTheme）也必须计入版本号，
        // 否则「版本号」对主题变更视而不见。
        ThemeManager.setTheme(ThemeManager.THEME_CLASSIC_GRAY)
        assertEquals(AppearanceManager.INITIAL_VERSION + 1, AppearanceManager.version())
    }

    @Test
    fun setCornerRadius_persistsAndBumpsVersion() {
        AppearanceManager.setCornerRadius(CornerStyle.ROUNDED)
        assertEquals(CornerStyle.ROUNDED, CornerStyle.current())
        assertEquals(AppearanceManager.INITIAL_VERSION + 1, AppearanceManager.version())
    }

    @Test
    fun setFontScale_persistsAndBumpsVersion() {
        AppearanceManager.setFontScale(FontStyle.SCALE_LARGE)
        assertEquals(FontStyle.SCALE_LARGE, FontStyle.currentScale())
        assertEquals(AppearanceManager.INITIAL_VERSION + 1, AppearanceManager.version())
    }

    @Test
    fun setFontFamily_persistsAndBumpsVersion() {
        AppearanceManager.setFontFamily(FontStyle.FAMILY_MONOSPACE)
        assertEquals(FontStyle.FAMILY_MONOSPACE, FontStyle.currentFamily())
        assertEquals(AppearanceManager.INITIAL_VERSION + 1, AppearanceManager.version())
    }

    @Test
    fun everyWriteBumpsVersion_independently() {
        AppearanceManager.setTheme(ThemeManager.THEME_IQIYI_GREEN)
        AppearanceManager.setCornerRadius(CornerStyle.ROUNDED)
        AppearanceManager.setFontScale(FontStyle.SCALE_XLARGE)
        AppearanceManager.setFontFamily(FontStyle.FAMILY_MONOSPACE)
        assertEquals(AppearanceManager.INITIAL_VERSION + 4, AppearanceManager.version())
    }

    @Test
    fun version_isMonotonic() {
        val before = AppearanceManager.version()
        AppearanceManager.setCornerRadius(CornerStyle.ROUNDED)
        AppearanceManager.setCornerRadius(CornerStyle.SQUARE)
        assertEquals(before + 2, AppearanceManager.version())
    }

    // ==================== 写入前规整 ====================

    @Test
    fun writes_normalizeInvalidValuesBeforeSaving() {
        AppearanceManager.setCornerRadius("garbage")
        AppearanceManager.setFontScale("garbage")
        AppearanceManager.setFontFamily("garbage")
        // 落盘的就是合法值，设置页不会显示空白
        assertEquals(CornerStyle.DEFAULT, CornerStyle.current())
        assertEquals(FontStyle.SCALE_DEFAULT, FontStyle.currentScale())
        assertEquals(FontStyle.FAMILY_DEFAULT, FontStyle.currentFamily())
    }

    // ==================== 快照 ====================

    @Test
    fun snapshot_withEmptyPrefs_reportsAllDefaults() {
        val snapshot = AppearanceManager.snapshot()
        assertEquals(ThemeManager.THEME_DEFAULT, snapshot.themeKey)
        assertEquals(CornerStyle.DEFAULT, snapshot.cornerRadius)
        assertEquals(FontStyle.SCALE_DEFAULT, snapshot.fontScale)
        assertEquals(FontStyle.FAMILY_DEFAULT, snapshot.fontFamily)
        assertEquals(AppearanceManager.INITIAL_VERSION, snapshot.version)
    }

    @Test
    fun snapshot_reflectsEverySavedValue() {
        AppearanceManager.setTheme(ThemeManager.THEME_PURPLE_FANTASY)
        AppearanceManager.setCornerRadius(CornerStyle.ROUNDED)
        AppearanceManager.setFontScale(FontStyle.SCALE_SMALL)
        AppearanceManager.setFontFamily(FontStyle.FAMILY_MONOSPACE)

        val snapshot = AppearanceManager.snapshot()
        assertEquals(ThemeManager.THEME_PURPLE_FANTASY, snapshot.themeKey)
        assertEquals(CornerStyle.ROUNDED, snapshot.cornerRadius)
        assertEquals(FontStyle.SCALE_SMALL, snapshot.fontScale)
        assertEquals(FontStyle.FAMILY_MONOSPACE, snapshot.fontFamily)
        assertEquals(AppearanceManager.version(), snapshot.version)
    }

    @Test
    fun snapshot_isNotCached_staleReadsAreImpossible() {
        // 刻意不缓存：外部直接改 prefs（模拟其它进程/旧代码路径）后，快照必须立即反映，
        // 否则又会引入一类「缓存没失效」的 bug。
        assertEquals(CornerStyle.DEFAULT, AppearanceManager.snapshot().cornerRadius)
        SharedPreferencesUtil.putString(CornerStyle.KEY, CornerStyle.ROUNDED)
        assertEquals(CornerStyle.ROUNDED, AppearanceManager.snapshot().cornerRadius)
    }
}
