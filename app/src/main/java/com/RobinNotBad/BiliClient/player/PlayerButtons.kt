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
 * - 组格的展开/收起逻辑（在 `PlayerButtonSlots`；收纳格＝空组，只留一个旧存档标记 [MORE]）。
 *
 * 图标口径（26.11.09 对齐播放器真身）：锁定用 `icon_player_unlock`（播放页默认态就是解锁图）、
 * 看点用 `icon_folder`（播放页真身 `viewpoint_btn` 用的就是 folder，不再是借来的 star）。
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

    /**
     * ±N 秒快退 / 快进（26.11.13 新增，来自兄弟分支 cyq 的 `player_seek_buttons`）。
     *
     * **文字按钮**：槽位里不画图标，直接显示当前步长（`«10s` / `10s»`）——步长可配置，
     * 画死图标就和配置对不上了；「倍速」按钮本来就是文字，渲染器已支持这一形态。
     */
    const val SEEK_BACK = "seek_back"
    const val SEEK_FORWARD = "seek_forward"

    /**
     * 26.11.15 新增的 7 个「动作类」按钮。
     *
     * 与老按钮的区别：它们不是某个设置项的入口，点一下就直接产生一次操作（点赞、投币、下载、
     * 切上一个/下一个）或弹一张只有本页用的小卡片（睡眠定时、投币数量、收藏夹）。
     * 因此 [Button.settingsGroup] 全是 `null`——设置页卡片对它们只显示「这个按钮没有可单独调整的设置」。
     *
     * **默认不摆进任何槽位**：老用户升级后布局不变，它们先躺在「收纳格」里（一个没加成员的
     * 组格），用户想固定住再自己摆出去（这正是收纳格该有的用途）。
     */
    const val SLEEP_TIMER = "sleep_timer"
    const val LIKE = "like"
    const val COIN = "coin"
    const val FAVORITE = "favorite"
    const val DOWNLOAD = "download"
    const val PREV_VIDEO = "prev_video"
    const val NEXT_VIDEO = "next_video"

    /**
     * 「更多」：**旧存档里的收纳格标记，已经不是按钮了**（26.11.15 起）。
     *
     * 收纳格现在就是「空的一组按钮」——[PlayerUiLayout.Slot.Group] 里 keys 为空，播放页对
     * 它展开「没摆进任何槽位的按钮」。所以这里只留一个字符串，供 [PlayerUiLayout] 解析老存档
     * （独立的 `more` 单格、`{more}` 组）时迁移成空组；它**不在 [ALL] 里**，设置页的候选列表、
     * 组内成员都不会再出现它。
     */
    const val MORE = "more"

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
     * 图标现状：倍速是自绘的 `ic_player_speed`（播放页真身是文字 `×1.0`，设置页用示意图标）；
     * 看点 26.11.09 起改用与真身一致的 `icon_folder`。跳转时间（lucide clock-fading）与
     * 连播队列（lucide corner-down-right）26.11.06 已换成用户给的专用图标，播放页里那两个
     * 按钮本体也从文字改成了同一张图。
     */
    val ALL: List<Button> = listOf(
        Button(LOCK, R.string.setting_player_btn_lock, R.drawable.icon_player_unlock),
        Button(SPEED, R.string.setting_player_btn_speed, R.drawable.ic_player_speed, PlayerSettingsGroups.DEFAULTS, textOnly = true, mayHide = true),
        Button(DANMAKU, R.string.setting_player_btn_danmaku, R.mipmap.danmakuoff, PlayerSettingsGroups.DANMAKU),
        Button(ROTATE, R.string.setting_player_btn_rotate, R.mipmap.rotate),
        Button(DANMAKU_SEND, R.string.setting_player_btn_danmaku_send, R.drawable.send, PlayerSettingsGroups.DANMAKU, mayHide = true),
        Button(SUBTITLE, R.string.setting_player_btn_subtitle, R.mipmap.subtitle_off, PlayerSettingsGroups.SUBTITLE),
        Button(LOOP, R.string.setting_player_btn_loop, R.mipmap.loopoff, PlayerSettingsGroups.DEFAULTS, mayHide = true),
        Button(AUDIO_ONLY, R.string.setting_player_btn_audio_only, R.drawable.icon_audio_only_off, PlayerSettingsGroups.DEFAULTS, mayHide = true),
        Button(AUTO_NEXT, R.string.setting_player_btn_auto_next, R.drawable.icon_auto_next_off, PlayerSettingsGroups.DEFAULTS, mayHide = true),
        Button(VIEWPOINT, R.string.setting_player_btn_viewpoint, R.drawable.icon_folder, mayHide = true),
        Button(VIDEO_INFO, R.string.setting_player_btn_video_info, R.drawable.icon_info, mayHide = true),
        Button(JUMP_TIME, R.string.setting_player_btn_jump_time, R.drawable.icon_player_clock_fading, mayHide = true),
        Button(QUEUE_MODE, R.string.setting_player_btn_queue_mode, R.drawable.icon_player_corner_down_right, mayHide = true),
        Button(QUALITY, R.string.setting_player_btn_quality, R.drawable.icon_menu, action = Action.OPEN_QUALITY, mayHide = true),
        // ±N 秒：文字按钮，步长在「播放设置 → 默认行为」里调；图标只是设置页的示意
        Button(SEEK_BACK, R.string.setting_player_btn_seek_back, R.drawable.icon_player_clock_fading, PlayerSettingsGroups.DEFAULTS, textOnly = true),
        Button(SEEK_FORWARD, R.string.setting_player_btn_seek_forward, R.drawable.icon_player_clock_fading, PlayerSettingsGroups.DEFAULTS, textOnly = true),
        Button(SOUND_CUT, R.string.setting_player_btn_sound_cut, R.mipmap.soundcut),
        Button(SOUND_ADD, R.string.setting_player_btn_sound_add, R.mipmap.soundadd),
        Button(PAGE_SELECTOR, R.string.setting_player_btn_page_selector, R.drawable.icon_folder, mayHide = true),
        // 动作类按钮（26.11.15）：点一下就有动作，没有可调设置，默认收在「更多」里
        Button(SLEEP_TIMER, R.string.setting_player_btn_sleep_timer, R.drawable.icon_time),
        // 点赞状态会变（已赞/未赞），真身图标由 PlayerActivity 按 relation 接口动态换；这里登记未赞态。
        Button(LIKE, R.string.setting_player_btn_like, R.drawable.icon_like_0, mayHide = true),
        Button(COIN, R.string.setting_player_btn_coin, R.drawable.icon_coin_0, mayHide = true),
        Button(FAVORITE, R.string.setting_player_btn_favorite, R.drawable.icon_fav_0, mayHide = true),
        Button(DOWNLOAD, R.string.setting_player_btn_download, R.drawable.icon_download, mayHide = true),
        // 上一个/下一个复用线控跳曲那套逻辑（队列优先、再回落分P），单P且无连播队列时点了没反应，故标注意。
        Button(PREV_VIDEO, R.string.setting_player_btn_prev_video, R.drawable.icon_player_prev, mayHide = true),
        Button(NEXT_VIDEO, R.string.setting_player_btn_next_video, R.drawable.icon_player_next, mayHide = true)
    )

    /**
     * 默认布局里右侧那个「组」的内容——即改造前 `right_second` 那一排，顺序与原 XML 一致。
     *
     * 26.11.13 起追加了 ±N 秒两个文字按钮：它们默认收在「更多」格子里，
     * 不占默认可见槽位（老用户升级后不会突然多出两个格子）。
     */
    val DEFAULT_RIGHT_GROUP: List<String> = listOf(
        DANMAKU_SEND, SUBTITLE, LOOP, AUDIO_ONLY, AUTO_NEXT,
        VIEWPOINT, VIDEO_INFO, JUMP_TIME, QUEUE_MODE, SEEK_BACK, SEEK_FORWARD
    )

    private val BY_KEY: Map<String, Button> = ALL.associateBy { it.key }

    /** 按 key 取按钮；未知 key 返回 `null`（布局解析时据此丢弃脏数据）。 */
    fun byKey(key: String): Button? = BY_KEY[key]

    /** key 是否登记过。 */
    fun isKnown(key: String): Boolean = BY_KEY.containsKey(key)
}
