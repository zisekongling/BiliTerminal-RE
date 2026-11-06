package com.RobinNotBad.BiliClient.player

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.RobinNotBad.BiliClient.R

/**
 * 播放器控制按钮注册表——「按钮槽位自定义」的唯一数据源。
 *
 * 每个可自定义的按钮登记三件事：
 * - [Button.nameRes]：设置页里显示的名字（短名，如「锁定」「跳转时间」）；
 * - [Button.iconRes]：图标（与 `activity_player.xml` 里现有按钮用的图标保持一致）；
 * - [Button.settingsGroup] / [Button.action]：点开它的设置卡片时展示什么。
 *
 * **不在这里的两样东西**：
 * - 播放时的点击行为（那是 `PlayerActivity` 的既有逻辑，按 key 派发）；
 * - 「更多」那个组格本身——它不占 [ALL]，图标用 `mipmap/morehide` + `mipmap/moreshow`。
 *
 * 纯 Kotlin 对象，除资源 id 外无 Android 依赖，可 JVM 单测（[PlayerUiLayout] 用它校验 key）。
 */
object PlayerButtons {

    const val LOCK = "lock"
    const val SPEED = "speed"
    const val DANMAKU = "danmaku"
    const val ROTATE = "rotate"
    const val DANMAKU_SEND = "danmaku_send"
    const val SUBTITLE = "subtitle"
    const val LOOP = "loop"
    const val AUDIO_ONLY = "audio_only"
    const val AUTO_NEXT = "auto_next"
    const val VIEWPOINT = "viewpoint"
    const val VIDEO_INFO = "video_info"
    const val JUMP_TIME = "jump_time"
    const val QUEUE_MODE = "queue_mode"
    const val QUALITY = "quality"
    const val SOUND_CUT = "sound_cut"
    const val SOUND_ADD = "sound_add"
    const val PAGE_SELECTOR = "page_selector"

    /** 卡片不是「列设置项」而是「跳转到另一个页面」时用这个。 */
    enum class Action { OPEN_QUALITY }

    /**
     * @param settingsGroup 点开卡片时展示的设置分组 key（见 [PlayerSettingsGroups]，值即 `GESTURE` 等）；
     *   `null` 表示这个按钮没有可调项，卡片退化成「一行说明 + 换一个 / 移除」。
     * @param action 非 `null` 时卡片改为跳转（目前只有清晰度按钮）。
     * @param textOnly 槽位里不画图标而画文字（只有「倍速」——它本来就显示 `×1.0`）。
     * @param mayHide 这个按钮在某些视频里会被播放页收起来（直播、本地文件、单P视频、没有字幕等），
     *   设置页据此给它标一个「注意」——不然用户摆好位置后在那个视频里找不着，会以为布局坏了。
     */
    data class Button(
        val key: String,
        @StringRes val nameRes: Int,
        @DrawableRes val iconRes: Int,
        val settingsGroup: String? = null,
        val action: Action? = null,
        val textOnly: Boolean = false,
        val mayHide: Boolean = false
    )

    /**
     * 全部可自定义按钮，顺序即候选列表顺序（先左列习惯用的，再右侧第二排的，最后底部的）。
     *
     * 图标现状：倍速是自绘的 `ic_player_speed`；跳转时间 / 连播队列 / 看点是**借来的**图标，
     * 等用户补图后替换这里一行即可。
     */
    val ALL: List<Button> = listOf(
        Button(LOCK, R.string.setting_player_btn_lock, R.drawable.icon_player_lock),
        Button(SPEED, R.string.setting_player_btn_speed, R.drawable.ic_player_speed, PlayerSettingsGroups.DEFAULTS, textOnly = true, mayHide = true),
        Button(DANMAKU, R.string.setting_player_btn_danmaku, R.mipmap.danmakuoff, PlayerSettingsGroups.DANMAKU),
        Button(ROTATE, R.string.setting_player_btn_rotate, R.mipmap.rotate),
        Button(DANMAKU_SEND, R.string.setting_player_btn_danmaku_send, R.drawable.send, PlayerSettingsGroups.DANMAKU, mayHide = true),
        Button(SUBTITLE, R.string.setting_player_btn_subtitle, R.mipmap.subtitle_off, PlayerSettingsGroups.SUBTITLE),
        Button(LOOP, R.string.setting_player_btn_loop, R.mipmap.loopoff, PlayerSettingsGroups.DEFAULTS, mayHide = true),
        Button(AUDIO_ONLY, R.string.setting_player_btn_audio_only, R.drawable.icon_audio_only_off, PlayerSettingsGroups.DEFAULTS, mayHide = true),
        Button(AUTO_NEXT, R.string.setting_player_btn_auto_next, R.drawable.icon_auto_next_off, PlayerSettingsGroups.DEFAULTS, mayHide = true),
        Button(VIEWPOINT, R.string.setting_player_btn_viewpoint, R.drawable.icon_star, mayHide = true),
        Button(VIDEO_INFO, R.string.setting_player_btn_video_info, R.drawable.icon_info, mayHide = true),
        Button(JUMP_TIME, R.string.setting_player_btn_jump_time, R.drawable.icon_time, mayHide = true),
        Button(QUEUE_MODE, R.string.setting_player_btn_queue_mode, R.drawable.icon_sort_filter, mayHide = true),
        Button(QUALITY, R.string.setting_player_btn_quality, R.drawable.icon_menu, action = Action.OPEN_QUALITY, mayHide = true),
        Button(SOUND_CUT, R.string.setting_player_btn_sound_cut, R.mipmap.soundcut),
        Button(SOUND_ADD, R.string.setting_player_btn_sound_add, R.mipmap.soundadd),
        Button(PAGE_SELECTOR, R.string.setting_player_btn_page_selector, R.drawable.icon_folder, mayHide = true)
    )

    /** 默认布局里右侧那个「组」的内容——即改造前 `right_second` 那一排，顺序与原 XML 一致。 */
    val DEFAULT_RIGHT_GROUP: List<String> = listOf(
        DANMAKU_SEND, SUBTITLE, LOOP, AUDIO_ONLY, AUTO_NEXT,
        VIEWPOINT, VIDEO_INFO, JUMP_TIME, QUEUE_MODE
    )

    private val BY_KEY: Map<String, Button> = ALL.associateBy { it.key }

    /** 按 key 取按钮；未知 key 返回 `null`（布局解析时据此丢弃脏数据）。 */
    fun byKey(key: String): Button? = BY_KEY[key]

    /** key 是否登记过。 */
    fun isKnown(key: String): Boolean = BY_KEY.containsKey(key)
}
