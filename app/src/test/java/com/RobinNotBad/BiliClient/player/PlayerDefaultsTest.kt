package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.util.SettingsKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlayerDefaults]（「播放默认值」解析）的纯 JVM 单测。
 *
 * 这里钉住两条容易静默出错的地方：
 * 1. 三态里的「沿用上次」必须走 lastUsed，而 lastUsed 由调用方带**该功能的原默认值**读出 ——
 *    否则旧版本用户升级后行为会变；
 * 2. 中文识别要同时认「中文」和「zh」：B 站字幕给的是 `lan_doc`（「中文（中国）」），
 *    不是标准语言标签。
 */
class PlayerDefaultsTest {

    // ==================== 三态 ====================

    @Test
    fun triState_onAndOff_overrideLastUsed() {
        assertTrue(PlayerDefaults.resolveTriState(SettingsKeys.PLAYER_DEFAULT_MODE_ON, lastUsed = false))
        assertTrue(PlayerDefaults.resolveTriState(SettingsKeys.PLAYER_DEFAULT_MODE_ON, lastUsed = true))
        assertFalse(PlayerDefaults.resolveTriState(SettingsKeys.PLAYER_DEFAULT_MODE_OFF, lastUsed = true))
        assertFalse(PlayerDefaults.resolveTriState(SettingsKeys.PLAYER_DEFAULT_MODE_OFF, lastUsed = false))
    }

    @Test
    fun triState_last_usesLastUsed() {
        assertTrue(PlayerDefaults.resolveTriState(SettingsKeys.PLAYER_DEFAULT_MODE_LAST, lastUsed = true))
        assertFalse(PlayerDefaults.resolveTriState(SettingsKeys.PLAYER_DEFAULT_MODE_LAST, lastUsed = false))
    }

    @Test
    fun triState_unknownMode_fallsBackToLastUsed() {
        // 脏数据/旧值不应该把功能锁死成开或关
        assertTrue(PlayerDefaults.resolveTriState("", lastUsed = true))
        assertTrue(PlayerDefaults.resolveTriState("whatever", lastUsed = true))
        assertFalse(PlayerDefaults.resolveTriState("whatever", lastUsed = false))
    }

    // ==================== 倍速 ====================

    @Test
    fun speed_numericValueWins() {
        // 用两参比较（而非带 delta 的三参）：resolveSpeed 返回的就是 toFloat() 的精确值，
        // 且避免踩 JUnit 浮点重载的坑。
        assertEquals(0.75f, PlayerDefaults.resolveSpeed("0.75", lastUsed = 1.0f))
        assertEquals(3.0f, PlayerDefaults.resolveSpeed("3.0", lastUsed = 1.0f))
        assertEquals(1.0f, PlayerDefaults.resolveSpeed("1.0", lastUsed = 2.0f))
    }

    @Test
    fun speed_lastOrGarbage_usesLastUsed() {
        assertEquals(1.25f, PlayerDefaults.resolveSpeed(SettingsKeys.PLAYER_DEFAULT_MODE_LAST, lastUsed = 1.25f))
        assertEquals(1.5f, PlayerDefaults.resolveSpeed("不是数字", lastUsed = 1.5f))
        assertEquals(1.5f, PlayerDefaults.resolveSpeed("", lastUsed = 1.5f))
    }

    // ==================== 屏幕方向 ====================

    @Test
    fun orientation_forcedModesIgnoreResolution() {
        assertTrue(PlayerDefaults.resolveLandscape(SettingsKeys.PLAYER_DEFAULT_ORIENTATION_LANDSCAPE, 0, 0))
        assertTrue(PlayerDefaults.resolveLandscape(SettingsKeys.PLAYER_DEFAULT_ORIENTATION_LANDSCAPE, 1080, 1920))
        assertFalse(PlayerDefaults.resolveLandscape(SettingsKeys.PLAYER_DEFAULT_ORIENTATION_PORTRAIT, 1920, 1080))
        assertFalse(PlayerDefaults.resolveLandscape(SettingsKeys.PLAYER_DEFAULT_ORIENTATION_PORTRAIT, 0, 0))
    }

    @Test
    fun orientation_auto_followsVideoResolution() {
        val auto = SettingsKeys.PLAYER_DEFAULT_ORIENTATION_AUTO
        assertTrue("横屏视频应判为横屏", PlayerDefaults.resolveLandscape(auto, 1920, 1080))
        assertTrue("正方形按竖屏处理", PlayerDefaults.resolveLandscape(auto, 1080, 1080).not())
        assertFalse("竖屏视频应判为竖屏", PlayerDefaults.resolveLandscape(auto, 1080, 1920))
        assertFalse("宽高未知时先按竖屏，等 changeVideoSize 再切", PlayerDefaults.resolveLandscape(auto, 0, 0))
        assertFalse(PlayerDefaults.resolveLandscape(auto, 0, 1080))
        assertFalse(PlayerDefaults.resolveLandscape(auto, 1920, 0))
    }

    // ==================== 字幕字号 ====================

    @Test
    fun subtitleSize_inRange_keptAsIs() {
        assertEquals(PlayerDefaults.SUBTITLE_SIZE_MIN, PlayerDefaults.clampSubtitleSize(PlayerDefaults.SUBTITLE_SIZE_MIN))
        assertEquals(11, PlayerDefaults.clampSubtitleSize(11))
        assertEquals(PlayerDefaults.SUBTITLE_SIZE_MAX, PlayerDefaults.clampSubtitleSize(PlayerDefaults.SUBTITLE_SIZE_MAX))
    }

    @Test
    fun subtitleSize_outOfRange_clamped() {
        // 0 会让字幕直接看不见，负数/超大值会让布局被撑爆，读取侧必须钳死。
        assertEquals(PlayerDefaults.SUBTITLE_SIZE_MIN, PlayerDefaults.clampSubtitleSize(0))
        assertEquals(PlayerDefaults.SUBTITLE_SIZE_MIN, PlayerDefaults.clampSubtitleSize(-5))
        assertEquals(PlayerDefaults.SUBTITLE_SIZE_MAX, PlayerDefaults.clampSubtitleSize(999))
        assertEquals(PlayerDefaults.SUBTITLE_SIZE_MAX, PlayerDefaults.clampSubtitleSize(Int.MAX_VALUE))
    }

    // ==================== 中文识别 ====================

    @Test
    fun chineseLang_acceptsLanDocAndStandardTag() {
        assertTrue(PlayerDefaults.isChineseLang("中文（中国）"))
        assertTrue(PlayerDefaults.isChineseLang("中文（自动生成）"))
        assertTrue(PlayerDefaults.isChineseLang("zh-CN"))
        assertTrue(PlayerDefaults.isChineseLang("AI-zh"))
    }

    @Test
    fun chineseLang_rejectsOthersAndBlanks() {
        assertFalse(PlayerDefaults.isChineseLang("英语（美国）"))
        assertFalse(PlayerDefaults.isChineseLang("日语（日本）"))
        assertFalse("「不显示字幕」项不能被当成中文", PlayerDefaults.isChineseLang("不显示字幕"))
        assertFalse(PlayerDefaults.isChineseLang(""))
        assertFalse(PlayerDefaults.isChineseLang(null))
    }

    // ==================== 选中文字幕 ====================

    @Test
    fun pickChinese_prefersManualOverAi() {
        val list = listOf(
            PlayerDefaults.SubtitleCandidate("英语（美国）", false),
            PlayerDefaults.SubtitleCandidate("中文（自动生成）", true),
            PlayerDefaults.SubtitleCandidate("中文（中国）", false),
            PlayerDefaults.SubtitleCandidate("不显示字幕", false)
        )
        assertEquals("必须优先人工中文，而不是先撞上的 AI 中文", 2, PlayerDefaults.pickChineseSubtitleIndex(list))
    }

    @Test
    fun pickChinese_fallsBackToAiChinese() {
        val list = listOf(
            PlayerDefaults.SubtitleCandidate("英语（美国）", false),
            PlayerDefaults.SubtitleCandidate("中文（自动生成）", true),
            PlayerDefaults.SubtitleCandidate("不显示字幕", false)
        )
        assertEquals("没有人工中文时应回落到 AI 中文", 1, PlayerDefaults.pickChineseSubtitleIndex(list))
    }

    @Test
    fun pickChinese_noneReturnsMinusOne() {
        val list = listOf(
            PlayerDefaults.SubtitleCandidate("英语（美国）", false),
            PlayerDefaults.SubtitleCandidate("不显示字幕", false)
        )
        assertEquals(-1, PlayerDefaults.pickChineseSubtitleIndex(list))
        assertEquals(-1, PlayerDefaults.pickChineseSubtitleIndex(emptyList()))
    }

    // ==================== 字幕默认值三档（#268） ====================

    @Test
    fun subtitleStartAction_offMode_ignoresAutoshow() {
        // 「关闭」是硬关：自动弹框开着也不能弹（反馈 #268）
        assertEquals(
            PlayerDefaults.SubtitleStartAction.OFF,
            PlayerDefaults.subtitleStartAction(SettingsKeys.PLAYER_DEFAULT_SUBTITLE_OFF, autoshow = true)
        )
        assertEquals(
            PlayerDefaults.SubtitleStartAction.OFF,
            PlayerDefaults.subtitleStartAction(SettingsKeys.PLAYER_DEFAULT_SUBTITLE_OFF, autoshow = false)
        )
    }

    @Test
    fun subtitleStartAction_zhMode_alwaysAutoChinese() {
        assertEquals(
            PlayerDefaults.SubtitleStartAction.CHINESE,
            PlayerDefaults.subtitleStartAction(SettingsKeys.PLAYER_DEFAULT_SUBTITLE_ZH, autoshow = true)
        )
        assertEquals(
            PlayerDefaults.SubtitleStartAction.CHINESE,
            PlayerDefaults.subtitleStartAction(SettingsKeys.PLAYER_DEFAULT_SUBTITLE_ZH, autoshow = false)
        )
    }

    @Test
    fun subtitleStartAction_manualMode_followsAutoshow() {
        assertEquals(
            PlayerDefaults.SubtitleStartAction.PICK,
            PlayerDefaults.subtitleStartAction(SettingsKeys.PLAYER_DEFAULT_SUBTITLE_MANUAL, autoshow = true)
        )
        assertEquals(
            PlayerDefaults.SubtitleStartAction.NONE,
            PlayerDefaults.subtitleStartAction(SettingsKeys.PLAYER_DEFAULT_SUBTITLE_MANUAL, autoshow = false)
        )
    }

    @Test
    fun subtitleStartAction_unknownMode_behavesLikeManual() {
        // 旧包留下的脏值不该锁死成「关闭」，按改造前的「自行选择」处理
        assertEquals(
            PlayerDefaults.SubtitleStartAction.PICK,
            PlayerDefaults.subtitleStartAction("whatever", autoshow = true)
        )
        assertEquals(
            PlayerDefaults.SubtitleStartAction.NONE,
            PlayerDefaults.subtitleStartAction(null, autoshow = false)
        )
    }
}
