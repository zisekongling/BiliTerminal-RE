package com.RobinNotBad.BiliClient.ui.appearance

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.util.FakeSharedPreferences
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [CornerStyle] 的纯 JVM 单测。
 *
 * 重点钉住两件容易出错、且**错了不会报错**的事：
 * 1. 档位存档位名与设置页显示名必须**一一对应**——顺序错位会让用户选「方角」实际得到「圆角」；
 * 2. 未知存档值必须回落到默认档——否则下游 `when` 会走进未定义分支。
 */
class CornerStyleTest {

    private val fakePrefs = FakeSharedPreferences()

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = fakePrefs
    }

    @After
    fun tearDown() {
        SharedPreferencesUtil.sharedPreferences = null
    }

    @Test
    fun default_isSquare() {
        assertEquals(CornerStyle.SQUARE, CornerStyle.DEFAULT)
        assertEquals(
            "无存档时必须是方角——它是「还原原项目」档，也是当前默认主题经典终端本来的观感",
            CornerStyle.SQUARE, CornerStyle.current()
        )
    }

    @Test
    fun normalize_unknownValueFallsBackToDefault() {
        assertEquals(CornerStyle.DEFAULT, CornerStyle.normalize(null))
        assertEquals(CornerStyle.DEFAULT, CornerStyle.normalize(""))
        assertEquals(CornerStyle.DEFAULT, CornerStyle.normalize("garbage"))
        // 大小写敏感：不合法值一律回落，不做模糊匹配
        assertEquals(CornerStyle.DEFAULT, CornerStyle.normalize("SQUARE"))
    }

    @Test
    fun normalize_keepsValidValues() {
        assertEquals(CornerStyle.SQUARE, CornerStyle.normalize(CornerStyle.SQUARE))
        assertEquals(CornerStyle.ROUNDED, CornerStyle.normalize(CornerStyle.ROUNDED))
    }

    @Test
    fun current_reflectsSavedValue() {
        SharedPreferencesUtil.putString(CornerStyle.KEY, CornerStyle.ROUNDED)
        assertEquals(CornerStyle.ROUNDED, CornerStyle.current())

        SharedPreferencesUtil.putString(CornerStyle.KEY, CornerStyle.SQUARE)
        assertEquals(CornerStyle.SQUARE, CornerStyle.current())
    }

    @Test
    fun current_unknownSavedValueFallsBackToDefault() {
        SharedPreferencesUtil.putString(CornerStyle.KEY, "some_removed_option")
        assertEquals(CornerStyle.DEFAULT, CornerStyle.current())
    }

    @Test
    fun valuesAndDisplayNames_areParallel() {
        assertEquals(
            "候选值与显示名必须逐项对应，错位会让用户选「方角」却得到「圆角」",
            CornerStyle.VALUES.size, CornerStyle.DISPLAY_NAMES.size
        )
        assertEquals(CornerStyle.VALUES.size, CornerStyle.VALUES.distinct().size)
        assertEquals(CornerStyle.DISPLAY_NAMES.size, CornerStyle.DISPLAY_NAMES.distinct().size)
        // 两档都在候选列表里，否则用户永远选不到
        assertEquals(listOf(CornerStyle.SQUARE, CornerStyle.ROUNDED), CornerStyle.VALUES)
        assertEquals(CornerStyle.SQUARE, CornerStyle.VALUES.first())
    }

    @Test
    fun key_isTheSettingsKeysConstant() {
        assertEquals(
            com.RobinNotBad.BiliClient.util.SettingsKeys.UI_CORNER_RADIUS, CornerStyle.KEY
        )
    }

    // ==================== 档位 → 覆盖样式（真正让档位生效的那一步） ====================

    @Test
    fun overlayStyleResId_mapsEachValueToItsOwnStyle() {
        assertEquals(
            R.style.Appearance_CornerSquare,
            CornerStyle.overlayStyleResId(CornerStyle.SQUARE)
        )
        assertEquals(
            R.style.Appearance_CornerRounded,
            CornerStyle.overlayStyleResId(CornerStyle.ROUNDED)
        )
        assertTrue(
            "两档必须映射到不同的覆盖样式，否则切换档位不产生任何视觉变化",
            CornerStyle.overlayStyleResId(CornerStyle.SQUARE) !=
                CornerStyle.overlayStyleResId(CornerStyle.ROUNDED)
        )
    }

    @Test
    fun overlayStyleResId_unknownValueFallsBackToDefaultStyle() {
        // 存档坏了不能让圆角解析失败：必须回落到默认档的样式（而不是 0 / 未定义）
        assertEquals(
            R.style.Appearance_CornerSquare,
            CornerStyle.overlayStyleResId("garbage")
        )
    }

    @Test
    fun overlayStyleResId_defaultsToCurrentPreference() {
        assertEquals(
            "无参调用应跟随当前设置",
            CornerStyle.overlayStyleResId(CornerStyle.current()),
            CornerStyle.overlayStyleResId()
        )
        SharedPreferencesUtil.putString(CornerStyle.KEY, CornerStyle.ROUNDED)
        assertEquals(
            R.style.Appearance_CornerRounded,
            CornerStyle.overlayStyleResId()
        )
    }
}
