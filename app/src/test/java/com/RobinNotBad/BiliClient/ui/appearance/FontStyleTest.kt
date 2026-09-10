package com.RobinNotBad.BiliClient.ui.appearance

import com.RobinNotBad.BiliClient.util.FakeSharedPreferences
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [FontStyle]（外观模块三：自定义字体）的纯 JVM 单测。
 *
 * 重点是**文件头校验**：它是唯一挡住"用户选错文件"的关卡，而选错文件的后果不是报错，
 * 而是字体静默不生效或应用在渲染路径上出问题。校验是纯函数，正好可测。
 *
 * 安装/清除/加载 Typeface 需要真实 `Context` 与文件系统，不在纯 JVM 单测范围内
 * （见类注释里对 `install` / `typeface` 的说明）。
 */
class FontStyleTest {

    private val fakePrefs = FakeSharedPreferences()

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = fakePrefs
        FontStyle.invalidateCache()
    }

    @After
    fun tearDown() {
        SharedPreferencesUtil.sharedPreferences = null
        FontStyle.invalidateCache()
    }

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    // ==================== 文件头校验 ====================

    @Test
    fun rejectReason_trueTypeMagic_isAccepted() {
        assertNull(FontStyle.rejectReason(bytes(0x00, 0x01, 0x00, 0x00)))
    }

    @Test
    fun rejectReason_openTypeCffMagic_isAccepted() {
        // "OTTO"
        assertNull(FontStyle.rejectReason(bytes(0x4F, 0x54, 0x54, 0x4F)))
    }

    @Test
    fun rejectReason_appleTrueTypeMagic_isAccepted() {
        // "true"
        assertNull(FontStyle.rejectReason(bytes(0x74, 0x72, 0x75, 0x65)))
    }

    @Test
    fun rejectReason_fontCollectionMagic_isAccepted() {
        // "ttcf"
        assertNull(FontStyle.rejectReason(bytes(0x74, 0x74, 0x63, 0x66)))
    }

    @Test
    fun rejectReason_woff_isRejectedWithSpecificHint() {
        // WOFF 是网页字体，Android 的 Typeface 解析不了。这是用户最容易挑错的一类文件，
        // 必须给出**专门**提示，而不是笼统的「不是字体」。
        val woff = FontStyle.rejectReason(bytes(0x77, 0x4F, 0x46, 0x46))
        assertNotNull(woff)
        assertTrue("提示里应点名 WOFF：$woff", woff!!.contains("WOFF"))

        val woff2 = FontStyle.rejectReason(bytes(0x77, 0x4F, 0x46, 0x32))
        assertNotNull(woff2)
        assertTrue("提示里应点名 WOFF：$woff2", woff2!!.contains("WOFF"))
    }

    @Test
    fun rejectReason_nullOrTooShort_isRejected() {
        assertNotNull(FontStyle.rejectReason(null))
        assertNotNull(FontStyle.rejectReason(ByteArray(0)))
        assertNotNull(FontStyle.rejectReason(bytes(0x00, 0x01, 0x00)))
    }

    @Test
    fun rejectReason_arbitraryContent_isRejected() {
        // 例如用户误选了图片或文本文件
        assertNotNull(FontStyle.rejectReason(bytes(0x89, 0x50, 0x4E, 0x47))) // PNG
        assertNotNull(FontStyle.rejectReason(bytes(0x50, 0x4B, 0x03, 0x04))) // ZIP
        assertNotNull(FontStyle.rejectReason(bytes(0x68, 0x65, 0x6C, 0x6C))) // "hell"
    }

    // ==================== 存档与展示 ====================

    @Test
    fun default_isSystemFont() {
        assertEquals("", FontStyle.currentPath())
        assertFalse(FontStyle.hasCustomFont())
        assertNull(FontStyle.currentFileName())
    }

    @Test
    fun currentFileName_isDerivedFromStoredPath() {
        SharedPreferencesUtil.putString(FontStyle.KEY_PATH, "/data/user/0/app/files/custom_font/custom_font.ttf")
        assertTrue(FontStyle.hasCustomFont())
        assertEquals("custom_font.ttf", FontStyle.currentFileName())
    }

    @Test
    fun currentFileName_handlesNameWithoutSlash() {
        // 存档里直接是文件名（异常但不应崩）
        SharedPreferencesUtil.putString(FontStyle.KEY_PATH, "myfont.ttf")
        assertEquals("myfont.ttf", FontStyle.currentFileName())
    }

    @Test
    fun key_isTheSettingsKeysConstant() {
        assertEquals(SettingsKeys.UI_FONT_PATH, FontStyle.KEY_PATH)
    }

    @Test
    fun sizeLimit_isSaneForWatchStorage() {
        assertTrue("上限应大于 1MB，否则正常字体都装不下", FontStyle.MAX_BYTES > 1024L * 1024L)
        assertTrue("上限不应超过 64MB，手表存储紧张", FontStyle.MAX_BYTES <= 64L * 1024 * 1024)
    }

    // ==================== 未启用时的零开销约定 ====================

    @Test
    fun shouldLoad_isFalseWhenNoFontConfigured() {
        // 未配置自定义字体时必须不加载——CustomFont 靠 typeface() 返回 null 立即短路，
        // 「默认不装字体 = 渲染路径零开销」这条性能约定就落在这个判断上。
        assertFalse(FontStyle.shouldLoad(""))
        assertTrue(FontStyle.shouldLoad("/any/path/font.ttf"))
        // 与存档联动：未配置时自然不该加载
        assertFalse(FontStyle.shouldLoad(FontStyle.currentPath()))
    }
}
