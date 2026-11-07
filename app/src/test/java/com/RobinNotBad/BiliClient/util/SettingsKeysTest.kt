package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [SettingsKeys] 里「已经在线上跑过、改了就静默丢用户数据」的 key 名钉死测试。
 *
 * 为什么值得写：这些 key 的字符串就是磁盘协议 —— 用户升级后读的还是同一个
 * SharedPreferences 文件。谁把常量后面的字面量改了（比如手滑改成 "player_id"），
 * 编译、单测、安装全都正常，只有用户那边的设置会悄悄回到默认值，且不会有任何报错。
 * 26.10.04 批次 4（E4）把散落的字面量收敛到常量后，这层保护才有意义。
 */
class SettingsKeysTest {

    @Test
    fun playerKey_isLocked() {
        assertEquals("player", SettingsKeys.PLAYER)
    }

    @Test
    fun playQnKey_isLocked() {
        assertEquals("play_qn", SettingsKeys.PLAY_QN)
    }

    @Test
    fun bangumiUpdateNotifyKey_isLocked() {
        // C16 新增：改了会让"追番更新提醒"开关静默失效（用户关不掉 / 开不了）
        assertEquals("bangumi_update_notify_enable", SettingsKeys.BANGUMI_UPDATE_NOTIFY_ENABLE)
    }

    /**
     * 本次审查范围（2d2b63b..0ea6ce4）新增/启用的持久化键，用 key → 常量映射表统一钉住。
     *
     * 为什么不逐个写 @Test：这些键是同一类风险（字面量即磁盘协议，改一位就静默丢数据），
     * 映射表一处加一行即可覆盖，失败信息里带原文便于定位。
     */
    @Test
    fun newlyPersistedKeys_areLocked() {
        val expected = linkedMapOf(
            // 「播放默认值 = 沿用上次」的记录位
            "pref_switch_danmaku" to SettingsKeys.PLAYER_LAST_DANMAKU,
            "player_last_audio_only" to SettingsKeys.PLAYER_LAST_AUDIO_ONLY,
            "player_last_loop" to SettingsKeys.PLAYER_LAST_LOOP,
            "player_last_autonext" to SettingsKeys.PLAYER_LAST_AUTONEXT,
            "player_last_speed" to SettingsKeys.PLAYER_LAST_SPEED,
            // 自建终端服务：遥测 / 隐私同意 / 崩溃报告 / 公告
            "terminal_telemetry_enable" to SettingsKeys.TELEMETRY_ENABLE,
            "terminal_install_id" to SettingsKeys.TELEMETRY_INSTALL_ID,
            "terminal_telemetry_last_day" to SettingsKeys.TELEMETRY_LAST_REPORT_DAY,
            "terminal_device_key" to SettingsKeys.TELEMETRY_DEVICE_KEY,
            "terminal_privacy_consent_version" to SettingsKeys.PRIVACY_CONSENT_VERSION,
            "terminal_privacy_prompted_version" to SettingsKeys.PRIVACY_PROMPTED_VERSION,
            "terminal_crash_report_auto" to SettingsKeys.CRASH_REPORT_AUTO,
            "terminal_feedback_attach_mid" to SettingsKeys.FEEDBACK_ATTACH_MID,
            "terminal_announcement_last" to SettingsKeys.TERMINAL_ANNOUNCEMENT_LAST,
            // 追番更新快照（整份 JSON 就存在这个键下，丢了会把老集当新集报）
            "bangumi_update_snapshot" to SharedPreferencesUtil.BANGUMI_UPDATE_SNAPSHOT,
            // 详情页动作区的两个默认隐藏入口（26.11.07）
            "detail_note_entry" to SettingsKeys.DETAIL_NOTE_ENTRY,
            "detail_share_row" to SettingsKeys.DETAIL_SHARE_ROW,
        )
        for ((literal, actual) in expected) {
            assertEquals("键名被改会静默丢用户数据：$literal", literal, actual)
        }
    }
}
