package com.RobinNotBad.BiliClient.ui.appearance

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.util.FakeSharedPreferences
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ColorScheme]（外观模块一：配色）的纯 JVM 单测。
 *
 * 此前主题/配色相关单测为 0 个，而主题系统的历史 bug 全是「改一处漏一处」型：
 * 三份 `key → style` 的 `when`、一份 `key → 色表` 的 `when`、以及设置页另抄一份候选值列表。
 * 本测试把这四处的**一致性**钉住：
 *
 * 1. 7 套主题 key 是否都进了 [ColorScheme.themeResId] 的 `when`（漏一条会**静默回落到 B站粉**，
 *    没有任何编译期或运行期报错）；
 * 2. 7 套主题 key 是否都进了 [ColorScheme.getCurrentTheme] 的 `when`（同样静默回落）；
 * 3. 无 key 时的默认值是否真的是**经典终端**（而不是静默变成 else 分支的 B站粉）；
 * 4. 7 份色表是否互不相同（防「复制一个 object 忘了改值」）。
 *
 * 用 [SharedPreferencesUtil.sharedPreferences] 注入假实现，手法同 NetWorkUtilTest。
 * 注意：写入走门面 [AppearanceManager.setTheme]——配色模块本身不再提供写入入口。
 */
class ColorSchemeTest {

    private val fakePrefs = FakeSharedPreferences()

    /**
     * 7 套可选主题的 key。
     *
     * 这份列表刻意手写而非从生产代码读取——它就是「设置页提供的候选值」的独立副本，
     * 若生产代码删掉一套主题而这里没改，测试会失败并提醒同步。
     */
    private val allThemeKeys = listOf(
        ColorScheme.THEME_BILIBILI_PINK,
        ColorScheme.THEME_ZHIHU_BLUE,
        ColorScheme.THEME_IQIYI_GREEN,
        ColorScheme.THEME_PURPLE_FANTASY,
        ColorScheme.THEME_RAINBOW_FANTASY,
        ColorScheme.THEME_CLASSIC_GRAY,
        ColorScheme.THEME_CLASSIC_TERMINAL
    )

    /** 每套主题 key 对应的色表 object（期望值，来自生产代码本体）。 */
    private val expectedTables = listOf(
        ColorScheme.THEME_BILIBILI_PINK to ColorScheme.BilibiliPink,
        ColorScheme.THEME_ZHIHU_BLUE to ColorScheme.ZhihuBlue,
        ColorScheme.THEME_IQIYI_GREEN to ColorScheme.IQIYIGreen,
        ColorScheme.THEME_PURPLE_FANTASY to ColorScheme.PurpleFantasy,
        ColorScheme.THEME_RAINBOW_FANTASY to ColorScheme.RainbowFantasy,
        ColorScheme.THEME_CLASSIC_GRAY to ColorScheme.ClassicGray,
        ColorScheme.THEME_CLASSIC_TERMINAL to ColorScheme.ClassicTerminal
    )

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = fakePrefs
        // ColorScheme 的色表是单例缓存，会在多个测试方法之间串味。
        // setTheme 是缓存的唯一失效点，借它清一次；随后清空假 prefs，
        // 回到「完全没有 theme key」的初始状态，以便测试默认值路径。
        AppearanceManager.setTheme(ColorScheme.THEME_DEFAULT)
        fakePrefs.edit().clear().apply()
    }

    @After
    fun tearDown() {
        SharedPreferencesUtil.sharedPreferences = null
    }

    // ==================== key 单一真源 ====================

    @Test
    fun prefKey_theme_isSingleSourceOfTruth() {
        assertEquals(
            "配色模块的主题 key 必须转发 SettingsKeys.THEME，不能各写一份字符串",
            SettingsKeys.THEME, ColorScheme.PREF_KEY_THEME
        )
    }

    @Test
    fun themeKeys_areDistinct() {
        assertEquals("7 套主题的 key 不能重复", allThemeKeys.size, allThemeKeys.distinct().size)
    }

    // ==================== key → style 映射 ====================

    @Test
    fun themeResId_mapsEachThemeToItsOwnStyle() {
        assertEquals(R.style.Theme_BiliClient, ColorScheme.themeResId(ColorScheme.THEME_BILIBILI_PINK))
        assertEquals(R.style.Theme_ZhihuBlue, ColorScheme.themeResId(ColorScheme.THEME_ZHIHU_BLUE))
        assertEquals(R.style.Theme_IQIYIGreen, ColorScheme.themeResId(ColorScheme.THEME_IQIYI_GREEN))
        assertEquals(R.style.Theme_PurpleFantasy, ColorScheme.themeResId(ColorScheme.THEME_PURPLE_FANTASY))
        assertEquals(R.style.Theme_RainbowFantasy, ColorScheme.themeResId(ColorScheme.THEME_RAINBOW_FANTASY))
        assertEquals(R.style.Theme_ClassicGray, ColorScheme.themeResId(ColorScheme.THEME_CLASSIC_GRAY))
        assertEquals(R.style.Theme_ClassicTerminal, ColorScheme.themeResId(ColorScheme.THEME_CLASSIC_TERMINAL))
    }

    @Test
    fun themeResId_everyThemeGetsDistinctStyle() {
        val ids = allThemeKeys.map { ColorScheme.themeResId(it) }
        assertEquals(
            "7 套主题必须映射到 7 个互不相同的 style——" +
                "themeResId 的 when 漏掉任一条分支都会静默回落到 else 的 B站粉，不报任何错",
            allThemeKeys.size, ids.distinct().size
        )
    }

    @Test
    fun themeResId_unknownKey_fallsBackToBiliClient() {
        assertEquals(R.style.Theme_BiliClient, ColorScheme.themeResId("no_such_theme"))
        assertEquals(R.style.Theme_BiliClient, ColorScheme.themeResId(""))
    }

    // ==================== 中文显示名 ====================

    @Test
    fun displayName_mapsEachThemeToItsOwnChineseName() {
        // 同样手写期望值：防「分支互换」（比如知乎蓝被标成爱奇艺绿）这类静默错误。
        val expected = mapOf(
            ColorScheme.THEME_BILIBILI_PINK to "B站粉",
            ColorScheme.THEME_ZHIHU_BLUE to "知乎蓝",
            ColorScheme.THEME_IQIYI_GREEN to "爱奇艺绿",
            ColorScheme.THEME_PURPLE_FANTASY to "紫色空灵",
            ColorScheme.THEME_RAINBOW_FANTASY to "五彩斑斓",
            ColorScheme.THEME_CLASSIC_GRAY to "经典灰",
            ColorScheme.THEME_CLASSIC_TERMINAL to "经典终端"
        )
        for ((key, name) in expected) {
            AppearanceManager.setTheme(key)
            assertEquals("主题 $key 的中文名不对", name, ColorScheme.getThemeDisplayName())
        }
    }

    @Test
    fun displayName_everyThemeGetsDistinctName() {
        val names = allThemeKeys.map { key ->
            AppearanceManager.setTheme(key)
            ColorScheme.getThemeDisplayName()
        }
        assertEquals(allThemeKeys.size, names.distinct().size)
        assertTrue("中文名不能为空", names.none { it.isEmpty() })
    }

    // ==================== 默认值 ====================

    @Test
    fun defaultTheme_withoutSavedKey_isClassicTerminal() {
        assertEquals(
            "无 key 时的默认值必须是经典终端",
            ColorScheme.THEME_CLASSIC_TERMINAL, ColorScheme.getCurrentThemeName()
        )
        assertEquals(ColorScheme.THEME_CLASSIC_TERMINAL, ColorScheme.THEME_DEFAULT)
        assertEquals("默认主题的中文名不对", "经典终端", ColorScheme.getThemeDisplayName())

        assertEquals(ColorScheme.ClassicTerminal.PRIMARY, ColorScheme.PRIMARY)
        // 关键：经典终端与 B站粉的 PRIMARY 恰好都是 0xFFFF6699，
        // 只比 PRIMARY 无法发现「默认值静默回落到 else 的 B站粉」，必须比一个能区分二者的字段。
        assertNotEquals(
            "默认主题落到了 B站粉的 else 分支（BACKGROUND 不同才能区分二者）",
            ColorScheme.BilibiliPink.BACKGROUND, ColorScheme.BACKGROUND
        )
        assertEquals(ColorScheme.ClassicTerminal.BACKGROUND, ColorScheme.BACKGROUND)
    }

    @Test
    fun themeDefault_isOneOfSelectableThemes() {
        assertTrue(
            "THEME_DEFAULT 必须是设置页能选到的一档，否则用户切走后再也回不到默认主题",
            ColorScheme.THEME_DEFAULT in allThemeKeys
        )
    }

    // ==================== key → 色表 映射 ====================

    @Test
    fun currentColors_matchEachThemeColorTable() {
        for ((key, table) in expectedTables) {
            AppearanceManager.setTheme(key)
            assertEquals("$key 的 PRIMARY 取错色表", table.PRIMARY, ColorScheme.PRIMARY)
            assertEquals("$key 的 BACKGROUND 取错色表", table.BACKGROUND, ColorScheme.BACKGROUND)
            assertEquals("$key 的 TEXT_PRIMARY 取错色表", table.TEXT_PRIMARY, ColorScheme.TEXT_PRIMARY)
            assertEquals("$key 的 CARD 取错色表", table.CARD, ColorScheme.CARD)
            assertEquals("$key 的 STATUS_BAR_COLOR 取错色表", table.STATUS_BAR_COLOR, ColorScheme.STATUS_BAR_COLOR)
        }
    }

    @Test
    fun colorTables_ofAllThemes_areMutuallyDistinct() {
        // 取这三元的理由：BACKGROUND 在 6 套暗色主题里完全相同，TEXT_PRIMARY 在
        // 经典灰与经典终端之间相同，PRIMARY 在 B站粉与经典终端之间相同——单取任何一个
        // 都不足以区分 7 套，三元组合可以。
        val triples = mutableListOf<Triple<Int, Int, Int>>()
        for (key in allThemeKeys) {
            AppearanceManager.setTheme(key)
            triples.add(Triple(ColorScheme.PRIMARY, ColorScheme.BACKGROUND, ColorScheme.TEXT_PRIMARY))
        }
        assertEquals(
            "7 套主题的色表两两不同——相同说明某个 object 是复制来的却忘了改值",
            allThemeKeys.size, triples.distinct().size
        )
    }

    // ==================== 缓存失效（步骤 1 新增缓存的守卫） ====================

    @Test
    fun themeCache_isInvalidatedOnEverySetTheme() {
        AppearanceManager.setTheme(ColorScheme.THEME_ZHIHU_BLUE)
        assertEquals(ColorScheme.ZhihuBlue.PRIMARY, ColorScheme.PRIMARY)

        AppearanceManager.setTheme(ColorScheme.THEME_IQIYI_GREEN)
        assertEquals(
            "換主题后色表没跟着变——getCurrentTheme 的缓存没在 setTheme 里失效",
            ColorScheme.IQIYIGreen.PRIMARY, ColorScheme.PRIMARY
        )
        // 顺手确认落盘，否则 recreate() 后读到旧主题
        assertEquals(ColorScheme.THEME_IQIYI_GREEN, ColorScheme.getCurrentThemeName())

        AppearanceManager.setTheme(ColorScheme.THEME_CLASSIC_TERMINAL)
        assertEquals(ColorScheme.ClassicTerminal.PRIMARY, ColorScheme.PRIMARY)
        assertEquals(ColorScheme.ClassicTerminal.BACKGROUND, ColorScheme.BACKGROUND)
    }

    /**
     * 步骤 1 的性能目标：色表只在首次读取时碰一次 SharedPreferences。
     *
     * 手表上列表滚动时，一个 item 的绑定就要调多次色表 getter（36 个 getter 全走 getCurrentTheme）。
     * 此前每次都会重新读一遍 SharedPreferences 再跑一遍 `when`；本测试把「读一次就够」钉死，
     * 防止将来有人把缓存改回直读而不自知。
     */
    @Test
    fun colorGetters_doNotTouchSharedPreferencesAfterFirstRead() {
        AppearanceManager.setTheme(ColorScheme.THEME_ZHIHU_BLUE)

        // 第一次 getter 触发一次真实读取，之后应命中缓存
        ColorScheme.PRIMARY
        val readsAfterWarmUp = fakePrefs.stringReadCount
        assertEquals("首次读取应当恰好读一次 SharedPreferences", 1, readsAfterWarmUp)

        repeat(100) {
            ColorScheme.PRIMARY
            ColorScheme.BACKGROUND
            ColorScheme.TEXT_PRIMARY
            ColorScheme.CARD
            ColorScheme.STATUS_BAR_COLOR
        }
        assertEquals(
            "缓存生效后，后续 500 次 getter 不应再读 SharedPreferences",
            readsAfterWarmUp, fakePrefs.stringReadCount
        )
    }

    // ==================== 工具函数 ====================

    @Test
    fun withPrimaryAlpha_replacesAlphaChannelAndKeepsRgb() {
        AppearanceManager.setTheme(ColorScheme.THEME_BILIBILI_PINK)
        val rgb = ColorScheme.BilibiliPink.PRIMARY and 0x00FFFFFF

        assertEquals((0xFF shl 24) or rgb, ColorScheme.withPrimaryAlpha(0xFF))
        assertEquals((0x80 shl 24) or rgb, ColorScheme.withPrimaryAlpha(0x80))
        assertEquals((0x00 shl 24) or rgb, ColorScheme.withPrimaryAlpha(0x00))
        // 超出 0xFF 的入参按低 8 位截断，不得污染 RGB
        assertEquals(ColorScheme.withPrimaryAlpha(0xFF), ColorScheme.withPrimaryAlpha(0x1FF))
    }
}
