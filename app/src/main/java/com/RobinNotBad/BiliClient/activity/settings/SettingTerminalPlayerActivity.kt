package com.RobinNotBad.BiliClient.activity.settings

import com.RobinNotBad.BiliClient.util.UiText

import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.activity.player.PlayerLongPressPolicy
import com.RobinNotBad.BiliClient.activity.player.PlayerSwipeSeekScalePolicy
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.player.DanmakuLoadPolicy
import com.RobinNotBad.BiliClient.player.LiveDanmakuFactorPolicy
import com.RobinNotBad.BiliClient.player.SkipOpEdPrefs
import com.RobinNotBad.BiliClient.util.DanmakuDiskCache
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

class SettingTerminalPlayerActivity : RefreshListActivity() {

    /** 设置项全表。`list_choose` 的选择结果要靠它按 position 反查回写（见 [onActivityResult]）。 */
    private var sections: List<SettingSection> = emptyList()
    private var settingsAdapter: SettingsAdapter? = null

    /** 三态项的显示名与取值，顺序一一对应。 */
    private val triStateNames = listOf(UiText.of("开"), "关", "沿用上次")
    private val triStateValues = listOf(
        SettingsKeys.PLAYER_DEFAULT_MODE_ON,
        SettingsKeys.PLAYER_DEFAULT_MODE_OFF,
        SettingsKeys.PLAYER_DEFAULT_MODE_LAST
    )

    /**
     * 造一个「点进去选一项」的设置行。
     *
     * 用 `list_choose` 而不是 `choose`：后者是**两选一的 RadioButton、存 Boolean**，
     * 承载不了三态/九档，也存不了字符串值。
     */
    private fun listChoose(
        name: String,
        key: String,
        desc: String,
        default: String,
        displayNames: List<String>,
        actualValues: List<String>
    ) = SettingSection(
        "list_choose", name, key, desc, default,
        SettingsAdapter.ListChooseHolder.ListChooseExtra(displayNames, actualValues)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName(getString(R.string.pagename_settingterminalplayeractivity_001))

        // 「播放默认值」各项的默认模式统一由**旧开关**迁移而来，保证升级后行为不变：
        // 旧开关为 true → 默认「开」，否则「关」。（旧键仍在 SettingsKeys 里，标了 legacy）
        val loopDefault = if (SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_LOOP, false))
            SettingsKeys.PLAYER_DEFAULT_MODE_ON else SettingsKeys.PLAYER_DEFAULT_MODE_OFF
        val audioOnlyDefault = if (SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_AUDIO_ONLY, false))
            SettingsKeys.PLAYER_DEFAULT_MODE_ON else SettingsKeys.PLAYER_DEFAULT_MODE_OFF
        val orientationDefault = if (SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_AUTOLANDSCAPE, false))
            SettingsKeys.PLAYER_DEFAULT_ORIENTATION_LANDSCAPE else SettingsKeys.PLAYER_DEFAULT_ORIENTATION_PORTRAIT

        sections = ArrayList<SettingSection>().apply {
            add(SettingSection("switch", "长按倍速", SettingsKeys.PLAYER_LONGCLICK, "", "true"))
            // #61：长按加速的**倍率**。上面那个开关管「允不允许长按加速」，这一项管「加速到几倍」。
            // 存档键/档位/默认值全部来自 PlayerLongPressPolicy（播放页每次长按重读，改完即生效）。
            add(listChoose(
                getString(R.string.setting_player_longpress_speed_name),
                PlayerLongPressPolicy.KEY,
                getString(R.string.setting_player_longpress_speed_desc),
                PlayerLongPressPolicy.DEFAULT_STORED,
                PlayerLongPressPolicy.displayNames,
                PlayerLongPressPolicy.storedValues
            ))
            add(SettingSection("switch", "双击快进快退", SettingsKeys.PLAYER_DOUBLETAP_SEEK, "", "false"))
            add(SettingSection("switch", "左右滑动控制进度", SettingsKeys.PLAYER_SWIPE_SEEK, "在视频区域左右滑动快进/快退（未缩放视频时生效）", "false"))
            // 反馈 #94a：手表 dpi 低时横滑一点点就跨过很长一段，倍率交给用户自己调。
            // 档位/默认值/换算都在 PlayerSwipeSeekScalePolicy 里，播放页每次横滑重读存档。
            add(listChoose(
                getString(R.string.setting_player_swipe_seek_scale_name),
                PlayerSwipeSeekScalePolicy.KEY,
                getString(R.string.setting_player_swipe_seek_scale_desc),
                PlayerSwipeSeekScalePolicy.DEFAULT_STORED,
                PlayerSwipeSeekScalePolicy.displayNames,
                PlayerSwipeSeekScalePolicy.storedValues
            ))
            add(SettingSection("switch", "有缩放时，双击优先恢复缩放", SettingsKeys.PLAYER_DOUBLETAP_RESTORE_SCREEN,
                getString(R.string.desc_player_doubletap_restore_scale), "false"))
            add(SettingSection("input_int", "快进快退秒数", SettingsKeys.PLAYER_DOUBLETAP_SEEK_SECONDS, "", "10"))
            add(SettingSection("switch", "后台/熄屏继续播放", SettingsKeys.PLAYER_BACKGROUND,
                "退到后台或熄屏时继续播放，并挂出通知栏遥控", "false"))

            // ---- 播放默认值：开播时自动应用，省得每次进播放器再手动调一遍 ----
            add(SettingSection("title", "播放默认值", "", "", ""))

            add(listChoose("弹幕", SettingsKeys.PLAYER_DEFAULT_DANMAKU,
                "开播时的弹幕开关；「沿用上次」= 上次退出播放器时的实际状态",
                SettingsKeys.PLAYER_DEFAULT_MODE_LAST, triStateNames, triStateValues))
            add(listChoose("听视频模式", SettingsKeys.PLAYER_DEFAULT_AUDIO_ONLY,
                getString(R.string.desc_audio_only),
                audioOnlyDefault, triStateNames, triStateValues))
            add(listChoose("循环播放", SettingsKeys.PLAYER_DEFAULT_LOOP,
                "单集播放结束后循环重播当前视频",
                loopDefault, triStateNames, triStateValues))
            add(listChoose("自动连播", SettingsKeys.PLAYER_DEFAULT_AUTONEXT,
                "多分P视频播完当前分P后自动播下一个",
                SettingsKeys.PLAYER_DEFAULT_MODE_OFF, triStateNames, triStateValues))
            add(listChoose("倍速", SettingsKeys.PLAYER_DEFAULT_SPEED,
                "开播时的播放速度；「沿用上次」= 上次用过的倍速",
                "1.0",
                listOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x", "3.0x", UiText.of("沿用上次")),
                listOf("0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0", "3.0", SettingsKeys.PLAYER_DEFAULT_MODE_LAST)))
            add(listChoose("字幕", SettingsKeys.PLAYER_DEFAULT_SUBTITLE,
                "中文 = 开播自动选中文字幕（优先人工，其次 AI）；自行选择 = 是否弹出选择框由下方「自动弹出字幕选择」决定",
                SettingsKeys.PLAYER_DEFAULT_SUBTITLE_MANUAL,
                listOf(UiText.of("中文"), "自行选择"),
                listOf(SettingsKeys.PLAYER_DEFAULT_SUBTITLE_ZH, SettingsKeys.PLAYER_DEFAULT_SUBTITLE_MANUAL)))
            add(listChoose("屏幕方向", SettingsKeys.PLAYER_DEFAULT_ORIENTATION,
                "播放器的默认屏幕方向；「按视频分辨率选择」= 视频宽大于高则横屏",
                orientationDefault,
                listOf(UiText.of("默认横屏"), "竖屏", "按视频分辨率选择"),
                listOf(
                    SettingsKeys.PLAYER_DEFAULT_ORIENTATION_LANDSCAPE,
                    SettingsKeys.PLAYER_DEFAULT_ORIENTATION_PORTRAIT,
                    SettingsKeys.PLAYER_DEFAULT_ORIENTATION_AUTO
                )))

            add(SettingSection("switch", "从历史位置播放", SettingsKeys.PLAYER_FROM_LAST,
                getString(R.string.desc_fromlast),
                "true"))
            add(SettingSection("switch", "显示实时人数", SettingsKeys.PLAYER_SHOW_ONLINE,
                getString(R.string.desc_showonline),
                "false"))
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
                arrayOf(UiText.of("硬件解码"), "软件解码")))
            add(SettingSection("choose", "音频输出", SettingsKeys.PLAYER_AUDIO,
                getString(R.string.desc_audiocodec), "false",
                arrayOf("OpenSles", "AudioTrack")))

            add(SettingSection("divider", "", "", "", ""))

            add(SettingSection("switch", "显示高能进度条", SettingsKeys.PLAYER_HIGH_ENERGY,
                getString(R.string.desc_player_high_energy), "false"))
            // 「自动跳过片头片尾」26.10.10 紧急撤回、26.10.22 恢复（默认关闭，见 SkipOpEdPrefs）。
            add(SettingSection("switch", "自动跳过片头片尾", SettingsKeys.PLAYER_SKIP_OP_ED,
                getString(R.string.desc_player_skip_op_ed), SkipOpEdPrefs.DEFAULT_ENABLED.toString()))
            add(SettingSection("switch", "弹幕允许重叠", SettingsKeys.PLAYER_DANMAKU_ALLOW_OVERLAP, "", "true"))
            add(SettingSection("switch", "合并重复弹幕", SettingsKeys.PLAYER_DANMAKU_MERGE_DUPLICATE, "", "false"))
            // 弹幕数量保护：超过阈值不自动加载（可在播放器里点弹幕按钮强制加载）。
            // 判据/阈值在 player/DanmakuLoadPolicy，这里只落开关。
            add(SettingSection("switch",
                getString(R.string.player_port_danmaku_guard_name),
                DanmakuLoadPolicy.KEY_PROTECT,
                getString(R.string.player_port_danmaku_guard_desc, DanmakuLoadPolicy.AUTO_LOAD_LIMIT),
                DanmakuLoadPolicy.DEFAULT_PROTECT.toString()))
            add(SettingSection("switch", "强制为滚动弹幕", SettingsKeys.PLAYER_DANMAKU_FORCE_R2L,
                getString(R.string.desc_danmaku_force_r2l), "false"))
            add(SettingSection("switch", "显示直播弹幕发送者", SettingsKeys.PLAYER_DANMAKU_SHOW_SENDER,
                getString(R.string.desc_danmaku_showsender), "true"))
            // 直播滚动弹幕停留时长倍率（与录播的「弹幕速度」相互独立）
            add(listChoose(
                getString(R.string.player_port_live_danmaku_factor_name),
                LiveDanmakuFactorPolicy.KEY,
                getString(R.string.player_port_live_danmaku_factor_desc),
                LiveDanmakuFactorPolicy.DEFAULT.toString(),
                LiveDanmakuFactorPolicy.displayNames,
                LiveDanmakuFactorPolicy.storedValues
            ))
            add(SettingSection("input_int", "弹幕最大行数", SettingsKeys.PLAYER_DANMAKU_MAXLINE, "", "10"))
            add(SettingSection("input_float", "弹幕字号大小", SettingsKeys.PLAYER_DANMAKU_SIZE, "", "0.7"))
            add(SettingSection("input_float", "弹幕不透明度", SettingsKeys.PLAYER_DANMAKU_TRANSPARENCY, "", "0.5"))
            add(SettingSection("input_float", "弹幕速度", SettingsKeys.PLAYER_DANMAKU_SPEED, "", "1.0"))
            // 弹幕磁盘缓存上限（批次 2）：按 cid+360s 分段缓存弹幕，回看/重进视频不再重复请求。
            // LRU 淘汰与原子写在 util/DanmakuDiskCache；这里只落档位（存字符串 MB 数，与
            // LiveDanmakuFactorPolicy 等 list_choose 项同一套落盘格式）。
            add(listChoose(
                getString(R.string.danmaku_cache_limit_name),
                DanmakuDiskCache.KEY_MAX_MB,
                getString(R.string.danmaku_cache_limit_desc),
                DanmakuDiskCache.DEFAULT_MAX_MB.toString(),
                DanmakuDiskCache.displayNameRes.map { getString(it) },
                DanmakuDiskCache.storedValues
            ))

            add(SettingSection("divider", "", "", "", ""))

            add(SettingSection("switch", "自动弹出字幕选择", SettingsKeys.PLAYER_SUBTITLE_AUTOSHOW,
                getString(R.string.desc_subtitle_autoshow), "true"))
            add(SettingSection("switch", "允许仅AI字幕", SettingsKeys.PLAYER_SUBTITLE_AI_ALLOWED,
                getString(R.string.desc_subtitle_ai_allowed), "false"))

            add(SettingSection("divider", "", "", "", ""))

            add(SettingSection("input_float", "字幕校准", SettingsKeys.PLAYER_SUBTITLE_DELTA,
                "将字幕提前/退后一段时间，从而与视频对齐", "0.3"))
            add(SettingSection("input_int", "字幕字号大小", SettingsKeys.PLAYER_SUBTITLE_SIZE,
                getString(R.string.desc_player_subtitle_size), SettingsKeys.PLAYER_SUBTITLE_SIZE_DEFAULT.toString()))

            add(SettingSection("divider", "", "", "", ""))

            add(SettingSection("switch", "显示旋转按钮", SettingsKeys.PLAYER_UI_SHOW_ROTATE_BTN, "", "true"))
            add(SettingSection("switch", "显示弹幕按钮", SettingsKeys.PLAYER_UI_SHOW_DANMAKU_BTN, "", "true"))
            add(SettingSection("switch", "显示清晰度按钮", SettingsKeys.PLAYER_UI_SHOW_QUALITY_BTN, "", "true"))
            add(SettingSection("switch", "显示分P按钮", SettingsKeys.PLAYER_UI_SHOW_PAGE_BTN, "", "true"))
            // 反馈 #7：锁定按钮紧挨着底部进度条，拖进度时容易误触。关掉后按钮不显示、也不会进入锁定态。
            add(SettingSection("switch", getString(R.string.setting_player_lock_button_name),
                SettingsKeys.PLAYER_UI_SHOW_LOCK_BTN, getString(R.string.setting_player_lock_button_desc), "true"))
            add(SettingSection("input_float", "互动选项字体大小", SettingsKeys.PLAYER_INTERACTION_CHOICE_SIZE, "", SettingsKeys.PLAYER_INTERACTION_CHOICE_SIZE_DEFAULT.toString()))
        }

        recyclerView.setHasFixedSize(true)

        val adapter = SettingsAdapter(this, sections)
        settingsAdapter = adapter
        setAdapter(adapter)

        setRefreshing(false)

        scrollToHighlight(sections, intent.getStringExtra("highlight"))
    }

    /**
     * 承接 list_choose 的选择结果。
     *
     * SettingsAdapter 用 SettingsAdapter.REQUEST_CODE_LIST_CHOOSE 打开
     * [com.RobinNotBad.BiliClient.activity.ListChooseActivity]；回写逻辑与分组页共用
     * [applyListChooseResult]（此前只有分组页处理这个回调，所以本页的 list_choose「点了没反应」）。
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        applyListChooseResult(requestCode, resultCode, data, sections) { settingsAdapter?.notifyItemChanged(it) }
    }
}
