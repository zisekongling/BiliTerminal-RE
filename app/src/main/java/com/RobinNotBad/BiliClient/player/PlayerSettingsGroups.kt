package com.RobinNotBad.BiliClient.player

import android.content.Context
import android.os.Build
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.player.PlayerLongPressPolicy
import com.RobinNotBad.BiliClient.activity.player.PlayerSwipeSeekScalePolicy
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.util.DanmakuDiskCache
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 内置播放器的设置项分组表。
 *
 * 为什么要单独抽出来：同一个「完整设置」列表现在有两个消费方——
 * 「完整设置」页（[com.RobinNotBad.BiliClient.activity.settings.SettingTerminalPlayerActivity]）
 * 和假播放器的按钮设置卡片（点播放器上的某个按钮，弹出这个按钮管得着的那一组）。
 * 两边必须严格一致，否则会出现「卡片里能调、完整设置里找不到」的错位。
 *
 * 分组只影响**展示顺序与标题**，不改变任何设置项的 key / 类型 / 默认值：
 * 每项仍是 [SettingSection]，落盘键仍是 [SettingsKeys] 里的那串字符串。
 */
object PlayerSettingsGroups {

    /** 手势与滑动：长按、双击、横滑这些摸屏幕的操作。 */
    const val GESTURE = "gesture"

    /** 播放默认值：开播时自动应用的一组初值。 */
    const val DEFAULTS = "defaults"

    /** 画面与显示：进度条、人数、缩放。 */
    const val DISPLAY = "display"

    /** 渲染与解码：Surface/Texture、硬软解、音频通路。 */
    const val DECODE = "decode"

    /** 弹幕：开关、样式、行数、速度、缓存。 */
    const val DANMAKU = "danmaku"

    /** 字幕：自动弹出、AI、校准、字号。 */
    const val SUBTITLE = "subtitle"

    /** 界面按钮：播放器控制按钮的显隐（后续由按钮布局取代）。 */
    const val TOOLBAR = "toolbar"

    /**
     * 一组设置。
     *
     * @param key 组 id，见上面的常量；假界面按它取组。
     * @param title 已本地化的组标题（分组页/卡片上的小标题）。
     * @param sections 组内设置项，内部顺序即展示顺序。
     */
    data class Group(val key: String, val title: String, val sections: List<SettingSection>)

    /** 三态项的显示名（顺序与 [triStateValues] 一一对应；文案见 strings.xml 的 setting_player_option_*）。 */
    private fun triStateNames(context: Context) = listOf(
        context.getString(R.string.setting_player_option_on),
        context.getString(R.string.setting_player_option_off),
        context.getString(R.string.setting_player_option_last)
    )

    /** 三态项的取值，顺序与 [triStateNames] 一一对应。 */
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

    /** 全部七组，顺序即「完整设置」页的顺序。每次调用都重新生成（含读存档的默认值迁移）。 */
    fun build(context: Context): List<Group> {
        // 「播放默认值」各项的默认模式统一由**旧开关**迁移而来，保证升级后行为不变：
        // 旧开关为 true → 默认「开」，否则「关」。（旧键仍在 SettingsKeys 里，标了 legacy）
        val loopDefault = if (SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_LOOP, false))
            SettingsKeys.PLAYER_DEFAULT_MODE_ON else SettingsKeys.PLAYER_DEFAULT_MODE_OFF
        val audioOnlyDefault = if (SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_AUDIO_ONLY, false))
            SettingsKeys.PLAYER_DEFAULT_MODE_ON else SettingsKeys.PLAYER_DEFAULT_MODE_OFF
        val orientationDefault = if (SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_AUTOLANDSCAPE, false))
            SettingsKeys.PLAYER_DEFAULT_ORIENTATION_LANDSCAPE else SettingsKeys.PLAYER_DEFAULT_ORIENTATION_PORTRAIT

        return listOf(
            Group(GESTURE, context.getString(R.string.setting_player_group_gesture), gesture(context)),
            Group(DEFAULTS, context.getString(R.string.setting_player_group_defaults),
                defaults(context, loopDefault, audioOnlyDefault, orientationDefault)),
            Group(DISPLAY, context.getString(R.string.setting_player_group_display), display(context)),
            Group(DECODE, context.getString(R.string.setting_player_group_decode), decode(context)),
            Group(DANMAKU, context.getString(R.string.setting_player_group_danmaku), danmaku(context)),
            Group(SUBTITLE, context.getString(R.string.setting_player_group_subtitle), subtitle(context)),
            Group(TOOLBAR, context.getString(R.string.setting_player_group_toolbar), toolbar(context))
        )
    }

    /** 按组 id 取一组；没这一组就返回 null（假界面的格子映射不到组时据此走「换一个 / 移除」）。 */
    fun group(context: Context, key: String): Group? = build(context).firstOrNull { it.key == key }

    /**
     * 拍平成「完整设置」页要的单层列表：每组前面插一行 `title`。
     *
     * 拍平后的 position 就是列表位置，[com.RobinNotBad.BiliClient.activity.settings.applyListChooseResult]
     * 与 `scrollToHighlight` 都直接按它反查，所以这里不能过滤、不能重排。
     */
    fun flatten(context: Context): List<SettingSection> {
        val out = ArrayList<SettingSection>(64)
        for (group in build(context)) {
            out.add(SettingSection("title", group.title, "", "", ""))
            out.addAll(group.sections)
        }
        return out
    }

    private fun gesture(context: Context) = listOf(
        SettingSection("switch", context.getString(R.string.setting_player_longpress_boost),
            SettingsKeys.PLAYER_LONGCLICK, "", "true"),
        // #61：长按加速的**倍率**。上面那个开关管「允不允许长按加速」，这一项管「加速到几倍」。
        // 存档键/档位/默认值全部来自 PlayerLongPressPolicy（播放页每次长按重读，改完即生效）。
        listChoose(
            context.getString(R.string.setting_player_longpress_speed_name),
            PlayerLongPressPolicy.KEY,
            context.getString(R.string.setting_player_longpress_speed_desc),
            PlayerLongPressPolicy.DEFAULT_STORED,
            PlayerLongPressPolicy.displayNames,
            PlayerLongPressPolicy.storedValues
        ),
        SettingSection("switch", context.getString(R.string.setting_player_doubletap_seek),
            SettingsKeys.PLAYER_DOUBLETAP_SEEK, "", "false"),
        // task-14 ③：长按倍速期间上下划切视频（上划=下一条）。默认开，无连播队列时静默。
        SettingSection("switch",
            context.getString(R.string.player_s_longpress_switch_name),
            SettingsKeys.PLAYER_LONGPRESS_SWITCH,
            context.getString(R.string.player_s_longpress_switch_desc),
            LongPressSwitchPolicy.DEFAULT_SWITCH_ENABLED.toString()),
        // task-14 ④：长按倍速提示「x 3.0」的文字大小（80%~150%）。
        listChoose(
            context.getString(R.string.player_s_hint_scale_name),
            SettingsKeys.PLAYER_LONGPRESS_HINT_SCALE,
            context.getString(R.string.player_s_hint_scale_desc),
            LongPressSwitchPolicy.HINT_DEFAULT_STORED,
            LongPressSwitchPolicy.hintDisplayNames,
            LongPressSwitchPolicy.hintStoredValues
        ),
        SettingSection("switch", context.getString(R.string.setting_player_swipe_seek),
            SettingsKeys.PLAYER_SWIPE_SEEK, context.getString(R.string.desc_setting_player_swipe_seek), "false"),
        // 反馈 #94a：手表 dpi 低时横滑一点点就跨过很长一段，倍率交给用户自己调。
        // 档位/默认值/换算都在 PlayerSwipeSeekScalePolicy 里，播放页每次横滑重读存档。
        listChoose(
            context.getString(R.string.setting_player_swipe_seek_scale_name),
            PlayerSwipeSeekScalePolicy.KEY,
            context.getString(R.string.setting_player_swipe_seek_scale_desc),
            PlayerSwipeSeekScalePolicy.DEFAULT_STORED,
            PlayerSwipeSeekScalePolicy.displayNames,
            PlayerSwipeSeekScalePolicy.storedValues
        ),
        SettingSection("switch", context.getString(R.string.setting_player_doubletap_restore_scale),
            SettingsKeys.PLAYER_DOUBLETAP_RESTORE_SCREEN,
            context.getString(R.string.desc_player_doubletap_restore_scale), "false"),
        SettingSection("input_int", context.getString(R.string.setting_player_doubletap_seek_seconds),
            SettingsKeys.PLAYER_DOUBLETAP_SEEK_SECONDS, "", "10"),
        SettingSection("switch", context.getString(R.string.setting_player_background),
            SettingsKeys.PLAYER_BACKGROUND, context.getString(R.string.desc_setting_player_background), "false")
    )

    private fun defaults(
        context: Context,
        loopDefault: String,
        audioOnlyDefault: String,
        orientationDefault: String
    ) = listOf(
        listChoose(context.getString(R.string.setting_player_default_danmaku),
            SettingsKeys.PLAYER_DEFAULT_DANMAKU,
            context.getString(R.string.desc_setting_player_default_danmaku),
            SettingsKeys.PLAYER_DEFAULT_MODE_LAST, triStateNames(context), triStateValues),
        listChoose(context.getString(R.string.setting_player_default_audio_only),
            SettingsKeys.PLAYER_DEFAULT_AUDIO_ONLY,
            context.getString(R.string.desc_audio_only),
            audioOnlyDefault, triStateNames(context), triStateValues),
        listChoose(context.getString(R.string.setting_player_default_loop),
            SettingsKeys.PLAYER_DEFAULT_LOOP,
            context.getString(R.string.desc_setting_player_default_loop),
            loopDefault, triStateNames(context), triStateValues),
        listChoose(context.getString(R.string.setting_player_default_autonext),
            SettingsKeys.PLAYER_DEFAULT_AUTONEXT,
            context.getString(R.string.desc_setting_player_default_autonext),
            SettingsKeys.PLAYER_DEFAULT_MODE_OFF, triStateNames(context), triStateValues),
        listChoose(context.getString(R.string.setting_player_default_speed),
            SettingsKeys.PLAYER_DEFAULT_SPEED,
            context.getString(R.string.desc_setting_player_default_speed),
            "1.0",
            listOf(
                "0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x", "3.0x",
                context.getString(R.string.setting_player_option_last)
            ),
            listOf("0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0", "3.0", SettingsKeys.PLAYER_DEFAULT_MODE_LAST)),
        listChoose(context.getString(R.string.setting_player_default_subtitle),
            SettingsKeys.PLAYER_DEFAULT_SUBTITLE,
            context.getString(R.string.desc_setting_player_default_subtitle),
            SettingsKeys.PLAYER_DEFAULT_SUBTITLE_MANUAL,
            listOf(
                context.getString(R.string.setting_player_option_subtitle_zh),
                context.getString(R.string.setting_player_option_subtitle_manual)
            ),
            listOf(SettingsKeys.PLAYER_DEFAULT_SUBTITLE_ZH, SettingsKeys.PLAYER_DEFAULT_SUBTITLE_MANUAL)),
        listChoose(context.getString(R.string.setting_player_default_orientation),
            SettingsKeys.PLAYER_DEFAULT_ORIENTATION,
            context.getString(R.string.desc_setting_player_default_orientation),
            orientationDefault,
            listOf(
                context.getString(R.string.setting_player_option_orientation_landscape),
                context.getString(R.string.setting_player_option_orientation_portrait),
                context.getString(R.string.setting_player_option_orientation_auto)
            ),
            listOf(
                SettingsKeys.PLAYER_DEFAULT_ORIENTATION_LANDSCAPE,
                SettingsKeys.PLAYER_DEFAULT_ORIENTATION_PORTRAIT,
                SettingsKeys.PLAYER_DEFAULT_ORIENTATION_AUTO
            )),
        SettingSection("switch", context.getString(R.string.setting_player_from_last),
            SettingsKeys.PLAYER_FROM_LAST, context.getString(R.string.desc_fromlast), "true")
    )

    private fun display(context: Context): List<SettingSection> {
        val out = ArrayList<SettingSection>(8)
        // 顶部标题栏三件套：原来标题栏是常显、没有开关，26.11.06 补上，默认全开与旧行为一致
        out.add(SettingSection("switch", context.getString(R.string.setting_player_ui_show_title),
            SettingsKeys.PLAYER_UI_SHOW_TITLE, "", "true"))
        out.add(SettingSection("switch", context.getString(R.string.setting_player_ui_show_battery),
            SettingsKeys.PLAYER_UI_SHOW_BATTERY, "", "true"))
        out.add(SettingSection("switch", context.getString(R.string.setting_player_ui_show_clock),
            SettingsKeys.PLAYER_UI_SHOW_CLOCK, "", "true"))
        out.add(SettingSection("switch", context.getString(R.string.setting_player_show_online),
            SettingsKeys.PLAYER_SHOW_ONLINE, context.getString(R.string.desc_showonline),
            "false"))
        out.add(SettingSection("switch", context.getString(R.string.setting_player_scale),
            SettingsKeys.PLAYER_SCALE, context.getString(R.string.desc_scale), "true"))
        out.add(SettingSection("switch", context.getString(R.string.setting_player_double_move),
            SettingsKeys.PLAYER_DOUBLEMOVE, context.getString(R.string.desc_doublemove),
            "true"))
        out.add(SettingSection("switch", context.getString(R.string.setting_player_high_energy),
            SettingsKeys.PLAYER_HIGH_ENERGY, context.getString(R.string.desc_player_high_energy), "false"))
        // 「自动跳过片头片尾」26.10.10 紧急撤回、26.10.22 恢复；task-14 起由布尔开关改成五档
        // （关/仅头/仅尾/都跳/询问），旧布尔键只在迁移时读，判据见 player/SkipOpEdPolicy。
        if (SkipOpEdPrefs.FEATURE_ENABLED) {
            out.add(listChoose(
                context.getString(R.string.player_s_skip_mode_name),
                SettingsKeys.PLAYER_SKIP_OP_ED_MODE,
                context.getString(R.string.player_s_skip_mode_desc),
                SkipOpEdPrefs.currentMode().toString(),
                listOf(
                    context.getString(R.string.player_s_skip_mode_off),
                    context.getString(R.string.player_s_skip_mode_op),
                    context.getString(R.string.player_s_skip_mode_ed),
                    context.getString(R.string.player_s_skip_mode_both),
                    context.getString(R.string.player_s_skip_mode_ask)
                ),
                listOf(
                    SkipOpEdPolicy.MODE_OFF.toString(),
                    SkipOpEdPolicy.MODE_OP.toString(),
                    SkipOpEdPolicy.MODE_ED.toString(),
                    SkipOpEdPolicy.MODE_BOTH.toString(),
                    SkipOpEdPolicy.MODE_ASK.toString()
                )
            ))
        }
        return out
    }

    private fun decode(context: Context) = listOf(
        SettingSection("choose", context.getString(R.string.setting_player_display_mode),
            SettingsKeys.PLAYER_DISPLAY, context.getString(R.string.desc_display),
            (Build.VERSION.SDK_INT < 26).toString(),
            arrayOf("TextureView", "SurfaceView")),
        SettingSection("choose", context.getString(R.string.setting_player_codec),
            SettingsKeys.PLAYER_CODEC, context.getString(R.string.desc_videocodec), "true",
            arrayOf(
                context.getString(R.string.setting_player_option_codec_hw),
                context.getString(R.string.setting_player_option_codec_sw)
            )),
        SettingSection("choose", context.getString(R.string.setting_player_audio_output),
            SettingsKeys.PLAYER_AUDIO, context.getString(R.string.desc_audiocodec), "false",
            arrayOf("OpenSles", "AudioTrack"))
    )

    private fun danmaku(context: Context) = listOf(
        SettingSection("switch", context.getString(R.string.setting_player_danmaku_allow_overlap),
            SettingsKeys.PLAYER_DANMAKU_ALLOW_OVERLAP, "", "true"),
        SettingSection("switch", context.getString(R.string.setting_player_danmaku_merge_duplicate),
            SettingsKeys.PLAYER_DANMAKU_MERGE_DUPLICATE, "", "false"),
        // 弹幕数量保护：超过阈值不自动加载（可在播放器里点弹幕按钮强制加载）。
        // 判据/阈值在 player/DanmakuLoadPolicy，这里只落开关。
        SettingSection("switch",
            context.getString(R.string.player_port_danmaku_guard_name),
            DanmakuLoadPolicy.KEY_PROTECT,
            context.getString(R.string.player_port_danmaku_guard_desc, DanmakuLoadPolicy.AUTO_LOAD_LIMIT),
            DanmakuLoadPolicy.DEFAULT_PROTECT.toString()),
        SettingSection("switch", context.getString(R.string.setting_player_danmaku_force_r2l),
            SettingsKeys.PLAYER_DANMAKU_FORCE_R2L,
            context.getString(R.string.desc_danmaku_force_r2l), "false"),
        SettingSection("switch", context.getString(R.string.setting_player_danmaku_show_sender),
            SettingsKeys.PLAYER_DANMAKU_SHOW_SENDER,
            context.getString(R.string.desc_danmaku_showsender), "true"),
        // 本人弹幕边框（批次 3）：按登录 mid 的 CRC32 比对弹幕发送者，给自己的弹幕描一圈主题色。
        // 判定与染色在 player/DanmakuSelfStylePolicy + DanmakuManager；开关/换肤在下次 prepare 生效。
        SettingSection("switch",
            context.getString(R.string.danmaku_self_border_name),
            SettingsKeys.PLAYER_DANMAKU_SELF_BORDER,
            context.getString(R.string.danmaku_self_border_desc), "true"),
        // 直播滚动弹幕停留时长倍率（与录播的「弹幕速度」相互独立）
        listChoose(
            context.getString(R.string.player_port_live_danmaku_factor_name),
            LiveDanmakuFactorPolicy.KEY,
            context.getString(R.string.player_port_live_danmaku_factor_desc),
            LiveDanmakuFactorPolicy.DEFAULT.toString(),
            LiveDanmakuFactorPolicy.displayNames,
            LiveDanmakuFactorPolicy.storedValues
        ),
        SettingSection("input_int", context.getString(R.string.setting_player_danmaku_maxline),
            SettingsKeys.PLAYER_DANMAKU_MAXLINE, "", "10"),
        SettingSection("input_float", context.getString(R.string.setting_player_danmaku_size),
            SettingsKeys.PLAYER_DANMAKU_SIZE, "", "0.7"),
        SettingSection("input_float", context.getString(R.string.setting_player_danmaku_transparency),
            SettingsKeys.PLAYER_DANMAKU_TRANSPARENCY, "", "0.5"),
        SettingSection("input_float", context.getString(R.string.setting_player_danmaku_speed),
            SettingsKeys.PLAYER_DANMAKU_SPEED, "", "1.0"),
        // 弹幕磁盘缓存上限（批次 2）：按 cid+360s 分段缓存弹幕，回看/重进视频不再重复请求。
        // LRU 淘汰与原子写在 util/DanmakuDiskCache；这里只落档位（存字符串 MB 数，与
        // LiveDanmakuFactorPolicy 等 list_choose 项同一套落盘格式）。
        listChoose(
            context.getString(R.string.danmaku_cache_limit_name),
            DanmakuDiskCache.KEY_MAX_MB,
            context.getString(R.string.danmaku_cache_limit_desc),
            DanmakuDiskCache.DEFAULT_MAX_MB.toString(),
            DanmakuDiskCache.displayNameRes.map { context.getString(it) },
            DanmakuDiskCache.storedValues
        )
    )

    private fun subtitle(context: Context) = listOf(
        SettingSection("switch", context.getString(R.string.setting_player_subtitle_autoshow),
            SettingsKeys.PLAYER_SUBTITLE_AUTOSHOW,
            context.getString(R.string.desc_subtitle_autoshow), "true"),
        SettingSection("switch", context.getString(R.string.setting_player_subtitle_ai_allowed),
            SettingsKeys.PLAYER_SUBTITLE_AI_ALLOWED,
            context.getString(R.string.desc_subtitle_ai_allowed), "false"),
        SettingSection("input_float", context.getString(R.string.setting_player_subtitle_delta),
            SettingsKeys.PLAYER_SUBTITLE_DELTA, context.getString(R.string.desc_setting_player_subtitle_delta), "0.3"),
        SettingSection("input_int", context.getString(R.string.setting_player_subtitle_size),
            SettingsKeys.PLAYER_SUBTITLE_SIZE, context.getString(R.string.desc_player_subtitle_size),
            SettingsKeys.PLAYER_SUBTITLE_SIZE_DEFAULT.toString())
    )

    /**
     * 界面按钮组。
     *
     * 26.11.06 起原来的 5 个「显示××按钮」开关（旋转/弹幕/清晰度/分P/锁定）全部删除：
     * 按钮显不显示改成由「播放界面布局」决定——放没放进槽位就是开关本身。
     * 旧键 [SettingsKeys.PLAYER_UI_SHOW_ROTATE_BTN] 等保留在存档里（不迁移、不读取），
     * 免得用户升级回来看到旧值被清空而疑惑。
     */
    private fun toolbar(context: Context) = listOf(
        SettingSection("input_float", context.getString(R.string.setting_player_interaction_choice_size),
            SettingsKeys.PLAYER_INTERACTION_CHOICE_SIZE, "",
            SettingsKeys.PLAYER_INTERACTION_CHOICE_SIZE_DEFAULT.toString())
    )
}
