package com.RobinNotBad.BiliClient.ui.theme

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
 * [ThemeManager] 的纯 JVM 单测。
 *
 * 此前主题/配色相关单测为 0 个，而主题系统的历史 bug 全是「改一处漏一处」型：
 * 三份 `key → style` 的 `when`、一份 `key → 色表` 的 `when`、以及设置页另抄一份候选值列表。
 * 本测试把这四处的**一致性**钉住：
 *
 * 1. 7 套主题 key 是否都进了 [ThemeManager.themeResId] 的 `when`（漏一条会**静默回落到 B站粉**，
 *    没有任何编译期或运行期报错）；
 * 2. 7 套主题 key 是否都进了 [ThemeManager.getCurrentTheme] 的 `when`（同样静默回落）；
 * 3. 无 key 时的默认值是否真的是**经典终端**（而不是静默变成 else 分支的 B站粉）；
 * 4. 7 份色表是否互不相同（防「复制一个 object 忘了改值」）。
 *
 * 用 [SharedPreferencesUtil.sharedPreferences] 注入假实现，手法同 NetWorkUtilTest。
 */
class ThemeManagerTest {

    private val fakePrefs = FakeSharedPreferences()

    /**
     * 7 套可选主题的 key。
     *
     * 这份列表刻意手写而非从生产代码读取——它就是「设置页提供的候选值」的独立副本，
     * 若生产代码删掉一套主题而这里没改，测试会失败并提醒同步。
     */
    private val allThemeKeys = listOf(
        ThemeManager.THEME_BILIBILI_PINK,
        ThemeManager.THEME_ZHIHU_BLUE,
        ThemeManager.THEME_IQIYI_GREEN,
        ThemeManager.THEME_PURPLE_FANTASY,
        ThemeManager.THEME_RAINBOW_FANTASY,
        ThemeManager.THEME_CLASSIC_GRAY,
        ThemeManager.THEME_CLASSIC_TERMINAL
    )

    /** 每套主题 key 对应的色表 object（期望值，来自生产代码本体）。 */
    private val expectedTables = listOf(
        ThemeManager.THEME_BILIBILI_PINK to ThemeManager.BilibiliPink,
        ThemeManager.THEME_ZHIHU_BLUE to ThemeManager.ZhihuBlue,
        ThemeManager.THEME_IQIYI_GREEN to ThemeManager.IQIYIGreen,
        ThemeManager.THEME_PURPLE_FANTASY to ThemeManager.PurpleFantasy,
        ThemeManager.THEME_RAINBOW_FANTASY to ThemeManager.RainbowFantasy,
        ThemeManager.THEME_CLASSIC_GRAY to ThemeManager.ClassicGray,
        ThemeManager.THEME_CLASSIC_TERMINAL to ThemeManager.ClassicTerminal
    )

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = fakePrefs
        // ThemeManager 的色表是单例缓存，会在多个测试方法之间串味。
        // setTheme 是缓存的唯一失效点，借它清一次；随后清空假 prefs，
        // 回到「完全没有 theme key」的初始状态，以便测试默认值路径。
        ThemeManager.setTheme(ThemeManager.THEME_DEFAULT)
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
            "ThemeManager 的主题 key 必须转发 SettingsKeys.THEME，不能各写一份字符串",
            SettingsKeys.THEME, ThemeManager.PREF_KEY_THEME
        )
    }

    @Test
    fun themeKeys_areDistinct() {
        assertEquals("7 套主题的 key 不能重复", allThemeKeys.size, allThemeKeys.distinct().size)
    }

    // ==================== key → style 映射 ====================

    @Test
    fun themeResId_mapsEachThemeToItsOwnStyle() {
        assertEquals(R.style.Theme_BiliClient, ThemeManager.themeResId(ThemeManager.THEME_BILIBILI_PINK))
        assertEquals(R.style.Theme_ZhihuBlue, ThemeManager.themeResId(ThemeManager.THEME_ZHIHU_BLUE))
        assertEquals(R.style.Theme_IQIYIGreen, ThemeManager.themeResId(ThemeManager.THEME_IQIYI_GREEN))
        assertEquals(R.style.Theme_PurpleFantasy, ThemeManager.themeResId(ThemeManager.THEME_PURPLE_FANTASY))
        assertEquals(R.style.Theme_RainbowFantasy, ThemeManager.themeResId(ThemeManager.THEME_RAINBOW_FANTASY))
        assertEquals(R.style.Theme_ClassicGray, ThemeManager.themeResId(ThemeManager.THEME_CLASSIC_GRAY))
        assertEquals(R.style.Theme_ClassicTerminal, ThemeManager.themeResId(ThemeManager.THEME_CLASSIC_TERMINAL))
    }

    @Test
    fun themeResId_everyThemeGetsDistinctStyle() {
        val ids = allThemeKeys.map { ThemeManager.themeResId(it) }
        assertEquals(
            "7 套主题必须映射到 7 个互不相同的 style——" +
                "themeResId 的 when 漏掉任一条分支都会静默回落到 else 的 B站粉，不报任何错",
            allThemeKeys.size, ids.distinct().size
        )
    }

    @Test
    fun themeResId_unknownKey_fallsBackToBiliClient() {
        assertEquals(R.style.Theme_BiliClient, ThemeManager.themeResId("no_such_theme"))
        assertEquals(R.style.Theme_BiliClient, ThemeManager.themeResId(""))
    }

    // ==================== 中文显示名 ====================

    @Test
    fun displayName_mapsEachThemeToItsOwnChineseName() {
        // 同样手写期望值：防「分支互换」（比如知乎蓝被标成爱奇艺绿）这类静默错误。
        val expected = mapOf(
            ThemeManager.THEME_BILIBILI_PINK to "B站粉",
            ThemeManager.THEME_ZHIHU_BLUE to "知乎蓝",
            ThemeManager.THEME_IQIYI_GREEN to "爱奇艺绿",
            ThemeManager.THEME_PURPLE_FANTASY to "紫色空灵",
            ThemeManager.THEME_RAINBOW_FANTASY to "五彩斑斓",
            ThemeManager.THEME_CLASSIC_GRAY to "经典灰",
            ThemeManager.THEME_CLASSIC_TERMINAL to "经典终端"
        )
        for ((key, name) in expected) {
            ThemeManager.setTheme(key)
            assertEquals("主题 $key 的中文名不对", name, ThemeManager.getThemeDisplayName())
        }
    }

    @Test
    fun displayName_everyThemeGetsDistinctName() {
        val names = allThemeKeys.map { key ->
            ThemeManager.setTheme(key)
            ThemeManager.getThemeDisplayName()
        }
        assertEquals(allThemeKeys.size, names.distinct().size)
        assertTrue("中文名不能为空", names.none { it.isEmpty() })
    }

    // ==================== 默认值 ====================

    @Test
    fun defaultTheme_withoutSavedKey_isClassicTerminal() {
        assertEquals(
            "无 key 时的默认值必须是经典终端",
            ThemeManager.THEME_CLASSIC_TERMINAL, ThemeManager.getCurrentThemeName()
        )
        assertEquals(ThemeManager.THEME_CLASSIC_TERMINAL, ThemeManager.THEME_DEFAULT)
        assertEquals("默认主题的中文名不对", "经典终端", ThemeManager.getThemeDisplayName())

        assertEquals(ThemeManager.ClassicTerminal.PRIMARY, ThemeManager.PRIMARY)
        // 关键：经典终端与 B站粉的 PRIMARY 恰好都是 0xFFFF6699，
        // 只比 PRIMARY 无法发现「默认值静默回落到 else 的 B站粉」，必须比一个能区分二者的字段。
        assertNotEquals(
            "默认主题落到了 B站粉的 else 分支（BACKGROUND 不同才能区分二者）",
            ThemeManager.BilibiliPink.BACKGROUND, ThemeManager.BACKGROUND
        )
        assertEquals(ThemeManager.ClassicTerminal.BACKGROUND, ThemeManager.BACKGROUND)
    }

    @Test
    fun themeDefault_isOneOfSelectableThemes() {
        assertTrue(
            "THEME_DEFAULT 必须是设置页能选到的一档，否则用户切走后再也回不到默认主题",
            ThemeManager.THEME_DEFAULT in allThemeKeys
        )
    }

    // ==================== key → 色表 映射 ====================

    @Test
    fun currentColors_matchEachThemeColorTable() {
        for ((key, table) in expectedTables) {
            ThemeManager.setTheme(key)
            assertEquals("$key 的 PRIMARY 取错色表", table.PRIMARY, ThemeManager.PRIMARY)
            assertEquals("$key 的 BACKGROUND 取错色表", table.BACKGROUND, ThemeManager.BACKGROUND)
            assertEquals("$key 的 TEXT_PRIMARY 取错色表", table.TEXT_PRIMARY, ThemeManager.TEXT_PRIMARY)
            assertEquals("$key 的 CARD 取错色表", table.CARD, ThemeManager.CARD)
            assertEquals("$key 的 STATUS_BAR_COLOR 取错色表", table.STATUS_BAR_COLOR, ThemeManager.STATUS_BAR_COLOR)
        }
    }

    @Test
    fun colorTables_ofAllThemes_areMutuallyDistinct() {
        // 取这三元的理由：BACKGROUND 在 6 套暗色主题里完全相同，TEXT_PRIMARY 在
        // 经典灰与经典终端之间相同，PRIMARY 在 B站粉与经典终端之间相同——单取任何一个
        // 都不足以区分 7 套，三元组合可以。
        val triples = mutableListOf<Triple<Int, Int, Int>>()
        for (key in allThemeKeys) {
            ThemeManager.setTheme(key)
            triples.add(Triple(ThemeManager.PRIMARY, ThemeManager.BACKGROUND, ThemeManager.TEXT_PRIMARY))
        }
        assertEquals(
            "7 套主题的色表两两不同——相同说明某个 object 是复制来的却忘了改值",
            allThemeKeys.size, triples.distinct().size
        )
    }

    // ==================== 缓存失效（步骤 1 新增缓存的守卫） ====================

    @Test
    fun themeCache_isInvalidatedOnEverySetTheme() {
        ThemeManager.setTheme(ThemeManager.THEME_ZHIHU_BLUE)
        assertEquals(ThemeManager.ZhihuBlue.PRIMARY, ThemeManager.PRIMARY)

        ThemeManager.setTheme(ThemeManager.THEME_IQIYI_GREEN)
        assertEquals(
            "換主题后色表没跟着变——getCurrentTheme 的缓存没在 setTheme 里失效",
            ThemeManager.IQIYIGreen.PRIMARY, ThemeManager.PRIMARY
        )
        // 顺手确认落盘，否则 recreate() 后读到旧主题
        assertEquals(ThemeManager.THEME_IQIYI_GREEN, ThemeManager.getCurrentThemeName())

        ThemeManager.setTheme(ThemeManager.THEME_CLASSIC_TERMINAL)
        assertEquals(ThemeManager.ClassicTerminal.PRIMARY, ThemeManager.PRIMARY)
        assertEquals(ThemeManager.ClassicTerminal.BACKGROUND, ThemeManager.BACKGROUND)
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
        ThemeManager.setTheme(ThemeManager.THEME_ZHIHU_BLUE)

        // 第一次 getter 触发一次真实读取，之后应命中缓存
        ThemeManager.PRIMARY
        val readsAfterWarmUp = fakePrefs.stringReadCount
        assertEquals("首次读取应当恰好读一次 SharedPreferences", 1, readsAfterWarmUp)

        repeat(100) {
            ThemeManager.PRIMARY
            ThemeManager.BACKGROUND
            ThemeManager.TEXT_PRIMARY
            ThemeManager.CARD
            ThemeManager.STATUS_BAR_COLOR
        }
        assertEquals(
            "缓存生效后，后续 500 次 getter 不应再读 SharedPreferences",
            readsAfterWarmUp, fakePrefs.stringReadCount
        )
    }

    // ==================== 工具函数 ====================

    @Test
    fun withPrimaryAlpha_replacesAlphaChannelAndKeepsRgb() {
        ThemeManager.setTheme(ThemeManager.THEME_BILIBILI_PINK)
        val rgb = ThemeManager.BilibiliPink.PRIMARY and 0x00FFFFFF

        assertEquals((0xFF shl 24) or rgb, ThemeManager.withPrimaryAlpha(0xFF))
        assertEquals((0x80 shl 24) or rgb, ThemeManager.withPrimaryAlpha(0x80))
        assertEquals((0x00 shl 24) or rgb, ThemeManager.withPrimaryAlpha(0x00))
        // 超出 0xFF 的入参按低 8 位截断，不得污染 RGB
        assertEquals(ThemeManager.withPrimaryAlpha(0xFF), ThemeManager.withPrimaryAlpha(0x1FF))
    }
}
