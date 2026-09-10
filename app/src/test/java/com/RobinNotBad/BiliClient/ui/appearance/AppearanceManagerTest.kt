package com.RobinNotBad.BiliClient.ui.appearance

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

    /** 模拟一个已安装到私有目录的字体文件路径。 */
    private val fontPath = "/data/user/0/com.RobinNotBad.BiliClient/files/custom_font/custom_font.ttf"

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = fakePrefs
        // 色表是单例缓存，借 setTheme 清一次；随后清空假 prefs 回到「无任何存档」状态。
        AppearanceManager.setTheme(ColorScheme.THEME_DEFAULT)
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
        AppearanceManager.setTheme(ColorScheme.THEME_ZHIHU_BLUE)
        assertEquals(
            "主题写入必须落盘，否则 recreate() 后读到旧主题",
            ColorScheme.THEME_ZHIHU_BLUE, ColorScheme.getCurrentThemeName()
        )
        assertEquals(
            "主题写入必须递增外观版本号，否则其它页面在 onResume 时不会重建",
            AppearanceManager.INITIAL_VERSION + 1, AppearanceManager.version()
        )
    }

    @Test
    fun setCornerRadius_persistsAndBumpsVersion() {
        AppearanceManager.setCornerRadius(CornerStyle.ROUNDED)
        assertEquals(CornerStyle.ROUNDED, CornerStyle.current())
        assertEquals(AppearanceManager.INITIAL_VERSION + 1, AppearanceManager.version())
    }

    @Test
    fun setFontPath_persistsAndBumpsVersion() {
        AppearanceManager.setFontPath(fontPath)
        assertEquals(fontPath, FontStyle.currentPath())
        assertEquals(AppearanceManager.INITIAL_VERSION + 1, AppearanceManager.version())
    }

    @Test
    fun clearFontPath_resetsToSystemFontAndBumpsVersion() {
        AppearanceManager.setFontPath(fontPath)
        AppearanceManager.clearFontPath()
        assertEquals("清除后应回到系统字体", "", FontStyle.currentPath())
        assertEquals(AppearanceManager.INITIAL_VERSION + 2, AppearanceManager.version())
    }

    @Test
    fun everyWriteBumpsVersion_independently() {
        AppearanceManager.setTheme(ColorScheme.THEME_IQIYI_GREEN)
        AppearanceManager.setCornerRadius(CornerStyle.ROUNDED)
        AppearanceManager.setFontPath(fontPath)
        AppearanceManager.clearFontPath()
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
        // 落盘的就是合法值，设置页不会显示空白
        assertEquals(CornerStyle.DEFAULT, CornerStyle.current())
    }

    // ==================== 快照 ====================

    @Test
    fun snapshot_withEmptyPrefs_reportsAllDefaults() {
        val snapshot = AppearanceManager.snapshot()
        assertEquals(ColorScheme.THEME_DEFAULT, snapshot.themeKey)
        assertEquals(CornerStyle.DEFAULT, snapshot.cornerRadius)
        assertEquals("", snapshot.fontPath)
        assertEquals(AppearanceManager.INITIAL_VERSION, snapshot.version)
    }

    @Test
    fun snapshot_reflectsEverySavedValue() {
        AppearanceManager.setTheme(ColorScheme.THEME_PURPLE_FANTASY)
        AppearanceManager.setCornerRadius(CornerStyle.ROUNDED)
        AppearanceManager.setFontPath(fontPath)

        val snapshot = AppearanceManager.snapshot()
        assertEquals(ColorScheme.THEME_PURPLE_FANTASY, snapshot.themeKey)
        assertEquals(CornerStyle.ROUNDED, snapshot.cornerRadius)
        assertEquals(fontPath, snapshot.fontPath)
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
