package com.RobinNotBad.BiliClient.activity.settings

import android.os.Build
import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.util.SettingsKeys

class SettingTerminalPlayerActivity : RefreshListActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName("内置播放器设置")

        val sectionList: List<SettingSection> = ArrayList<SettingSection>().apply {
            add(SettingSection("switch", "长按倍速", SettingsKeys.PLAYER_LONGCLICK, "", "true"))
            add(SettingSection("switch", "双击快进快退", SettingsKeys.PLAYER_DOUBLETAP_SEEK, "", "false"))
            add(SettingSection("switch", "双击优先还原屏幕", SettingsKeys.PLAYER_DOUBLETAP_RESTORE_SCREEN, "双击时若处于横屏则优先退出全屏，而不是暂停", "false"))
            add(SettingSection("input_int", "快进快退秒数", SettingsKeys.PLAYER_DOUBLETAP_SEEK_SECONDS, "", "10"))
            add(SettingSection("switch", "洗脑循环", SettingsKeys.PLAYER_LOOP, "", "false"))
            add(SettingSection("switch", "熄屏继续播放", SettingsKeys.PLAYER_BACKGROUND, "", "false"))
            add(SettingSection("switch", "默认横屏", SettingsKeys.PLAYER_AUTOLANDSCAPE, "", "false"))
            add(SettingSection("switch", "从历史位置播放", SettingsKeys.PLAYER_FROM_LAST,
                getString(R.string.desc_fromlast),
                "true"))
            add(SettingSection("switch", "显示实时人数", SettingsKeys.PLAYER_SHOW_ONLINE,
                getString(R.string.desc_showonline),
                "false"))
            add(SettingSection("switch", "听视频模式", SettingsKeys.PLAYER_AUDIO_ONLY,
                getString(R.string.desc_audio_only), "false"))
            add(SettingSection("switch", "视频可缩放", SettingsKeys.PLAYER_SCALE,
                getString(R.string.desc_scale), "true"))
            add(SettingSection("switch", "缩放时可移动", SettingsKeys.PLAYER_DOUBLEMOVE,
                getString(R.string.desc_doublemove),
                "true"))

            add(SettingSection("divider", "", "", "", ""))

            add(SettingSection("choose", "显示方式", SettingsKeys.PLAYER_DISPLAY,
                getString(R.string.desc_display),
                (Build.VERSION.SDK_INT < 26).toString(),
                arrayOf("TextureView", "SurfaceView")))
            add(SettingSection("choose", "解码方式", SettingsKeys.PLAYER_CODEC,
                getString(R.string.desc_videocodec), "true",
                arrayOf("硬件解码", "软件解码")))
            add(SettingSection("choose", "音频输出", SettingsKeys.PLAYER_AUDIO,
                getString(R.string.desc_audiocodec), "false",
                arrayOf("OpenSles", "AudioTrack")))

            add(SettingSection("divider", "", "", "", ""))

            add(SettingSection("switch", "显示高能进度条", SettingsKeys.PLAYER_HIGH_ENERGY,
                getString(R.string.desc_player_high_energy), "false"))
            add(SettingSection("switch", "弹幕允许重叠", SettingsKeys.PLAYER_DANMAKU_ALLOW_OVERLAP, "", "true"))
            add(SettingSection("switch", "合并重复弹幕", SettingsKeys.PLAYER_DANMAKU_MERGE_DUPLICATE, "", "false"))
            add(SettingSection("switch", "强制为滚动弹幕", SettingsKeys.PLAYER_DANMAKU_FORCE_R2L,
                getString(R.string.desc_danmaku_force_r2l), "false"))
            add(SettingSection("switch", "显示直播弹幕发送者", SettingsKeys.PLAYER_DANMAKU_SHOW_SENDER,
                getString(R.string.desc_danmaku_showsender), "true"))
            add(SettingSection("input_int", "弹幕最大行数", SettingsKeys.PLAYER_DANMAKU_MAXLINE, "", "10"))
            add(SettingSection("input_float", "弹幕字号大小", SettingsKeys.PLAYER_DANMAKU_SIZE, "", "0.7"))
            add(SettingSection("input_float", "弹幕不透明度", SettingsKeys.PLAYER_DANMAKU_TRANSPARENCY, "", "0.5"))
            add(SettingSection("input_float", "弹幕速度", SettingsKeys.PLAYER_DANMAKU_SPEED, "", "1.0"))

            add(SettingSection("divider", "", "", "", ""))

            add(SettingSection("switch", "自动弹出字幕选择", SettingsKeys.PLAYER_SUBTITLE_AUTOSHOW,
                getString(R.string.desc_subtitle_autoshow), "true"))
            add(SettingSection("switch", "允许仅AI字幕", SettingsKeys.PLAYER_SUBTITLE_AI_ALLOWED,
                getString(R.string.desc_subtitle_ai_allowed), "false"))

            add(SettingSection("divider", "", "", "", ""))

            add(SettingSection("input_float", "字幕校准", SettingsKeys.PLAYER_SUBTITLE_DELTA,
                "将字幕提前/退后一段时间，从而与视频对齐", "0.3"))

            add(SettingSection("divider", "", "", "", ""))

            add(SettingSection("switch", "显示旋转按钮", SettingsKeys.PLAYER_UI_SHOW_ROTATE_BTN, "", "true"))
            add(SettingSection("switch", "显示弹幕按钮", SettingsKeys.PLAYER_UI_SHOW_DANMAKU_BTN, "", "true"))
            add(SettingSection("switch", "显示清晰度按钮", SettingsKeys.PLAYER_UI_SHOW_QUALITY_BTN, "", "true"))
            add(SettingSection("switch", "显示分P按钮", SettingsKeys.PLAYER_UI_SHOW_PAGE_BTN, "", "true"))
            add(SettingSection("input_float", "互动选项字体大小", SettingsKeys.PLAYER_INTERACTION_CHOICE_SIZE, "", "17.0"))
        }

        recyclerView.setHasFixedSize(true)

        val adapter = SettingsAdapter(this, sectionList)
        setAdapter(adapter)

        setRefreshing(false)

        scrollToHighlight(sectionList, intent.getStringExtra("highlight"))
    }
}
