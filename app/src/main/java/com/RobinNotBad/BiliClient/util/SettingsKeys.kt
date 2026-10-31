package com.RobinNotBad.BiliClient.util

/**
 * 设置项 key 集中管理：所有设置项的 SharedPreferences key 统一在此定义，
 * 避免散落在各处导致改 key 漏改。
 *
 * 已在 [SharedPreferencesUtil] 中定义的 key 不重复声明，直接复用其常量。
 */
object SettingsKeys {

    // ==================== 账号 ====================
    /** 当前登录用户 mid（0 表示未登录）。 */
    const val MID = "mid"

    // ==================== 界面与外观 ====================
    const val PADDING_H = "paddingH_percent"
    const val PADDING_V = "paddingV_percent"
    const val DPI = "dpi"
    const val DENSITY = "density"
    const val UI_ROUND = "player_ui_round"
    const val THEME = "theme_selector"
    const val UI_LANDSCAPE = "ui_landscape"
    const val SPLASH_TEXT = "ui_splashtext"
    const val MARQUEE_ENABLE = "marquee_enable"
    /** 圆屏列表效果（实验性）：圆屏模式下给滚动列表加曲率缩放与边缘淡出，见 `ui/appearance/RoundListEffect`。 */
    const val UI_ROUND_LIST_EFFECT = "ui_round_list_effect"

    // ---- 外观三模块（配色 / 圆角 / 字体）-------------------------------
    // 配色沿用上面的 THEME，不另开 key（保持既有存档向前兼容）。
    /** 卡片圆角档位，见 `ui/appearance/CornerStyle`。 */
    const val UI_CORNER_RADIUS = "ui_corner_radius"
    /** 自定义字体（用户从文件管理器选的字体文件路径），见 `ui/appearance/FontStyle`。 */
    const val UI_FONT_PATH = "ui_font_path"
    /** 弥散光晕（页面背景的主题色光晕）开关，见 `ui/appearance/AmbientGlow`。 */
    const val UI_AMBIENT_GLOW = "ui_ambient_glow"
    /** 弥散光晕的光源位置（top / bottom / left / right / center），见 `AmbientGlow.SOURCE_VALUES`。 */
    const val UI_AMBIENT_GLOW_SOURCE = "ui_ambient_glow_source"
    /** 玻璃质感组件（卡片 / 按钮半透明化）开关，见 `ui/appearance/GlassStyle`。 */
    const val UI_GLASS_COMPONENTS = "ui_glass_components"
    /** 暗色 / 亮色模式（"dark" / "light"），见 `ui/appearance/ColorMode`。 */
    const val UI_COLOR_MODE = "ui_color_mode"

    // ==================== 详情页设置 ====================
    const val FAV_SINGLE = "fav_single"
    const val FAV_NOTICE = "fav_notice"
    const val COVER_PLAY_ENABLE = "cover_play_enable"
    const val TAGS_ENABLE = "tags_enable"
    const val RELATED_ENABLE = "related_enable"
    const val LIVE_BY_GUEST = "live_by_guest"
    const val LIKE_ONE_TRIPLE = "like_one_triple"

    // ==================== 偏好设置 ====================
    const val COPY_ENABLE = "copy_enable"
    /**
     * 长按评论/动态正文时弹「操作面板」而不是直接进复制界面。
     *
     * 默认值与判据见 `util/LongPressPrefs`，不要在别处写死字面量。
     */
    const val LONG_PRESS_PANEL_ENABLE = "long_press_panel_enable"
    /**
     * 长按顶部标题栏的手势模式（三态字符串：`off` / `video_only` / `all`）。
     *
     * 取值常量、默认值与判据统一在 `util/TopbarLongPressPrefs`，不要在别处写死字符串或默认值。
     */
    const val TOPBAR_LONG_PRESS_MODE = "topbar_long_press_mode"
    /**
     * 26.10.19 的旧布尔开关（开 = 普通页面打开菜单、视频详情页照旧；关 = 只有视频详情页照旧）。
     *
     * **只用于迁移，不要再写这个键**：新键读不到时 `util/TopbarLongPressPrefs.mode()` 按它回退
     * （true → `all`，false → `video_only`），保证老用户升级后行为不变。写入一律写 [TOPBAR_LONG_PRESS_MODE]。
     */
    const val TOPBAR_LONG_PRESS_MENU_ENABLE = "topbar_long_press_menu_enable"
    /**
     * 「伪装 / 安全」总开关（双击顶栏切到假计算器或假「蓝牙已断开」）。
     *
     * 默认值、样式取值与暗号校验统一在 `util/DisguisePrefs`，不要在别处写死默认值。
     */
    const val DISGUISE_ENABLED = "disguise_enabled"
    /** 伪装样式（`calculator` / `bluetooth`），取值见 `util/DisguisePrefs`。 */
    const val DISGUISE_STYLE = "disguise_style"
    /** 退出伪装用的数字暗号，长度 4~8。 */
    const val DISGUISE_CODE = "disguise_code"
    const val CREATIVE_ENABLE = "creative_enable"
    const val SEARCH_SUGGESTIONS_ENABLE = "search_suggestions_enable"
    const val BACK_DISABLE = "back_disable"
    const val SAVE_BAN_GALLERY = "save_ban_gallery"
    const val IMAGE_REQUEST_JPG = "image_request_jpg"
    const val IMAGE_NO_LOAD_ONSCROLL = "image_no_load_onscroll"
    const val UI_ROTATORY_ENABLE = "ui_rotatory_enable"
    const val UI_ROTATORY_RECYCLER = "ui_rotatory_recycler"
    const val UI_ROTATORY_SCROLL = "ui_rotatory_scroll"

    // ==================== 通知 ====================
    /**
     * 新私信通知栏通知开关。只在"未读数比上次检查时变多"时弹，纯逻辑见
     * `util/MsgNotifier.shouldNotify`。
     */
    const val PRIVATE_MSG_NOTIFY_ENABLE = "private_msg_notify_enable"
    /**
     * 追番更新提醒开关。打开应用时对比追番列表的最新集快照，有变化才提醒一次；
     * 纯逻辑见 `util/BangumiUpdateChecker.findUpdated`，项目不做后台定时检查。
     */
    const val BANGUMI_UPDATE_NOTIFY_ENABLE = "bangumi_update_notify_enable"

    // ==================== 缓存与下载 ====================
    const val ARIA2_ENABLED = "aria2_enabled"
    const val DEV_DOWNLOAD_OLD = "dev_download_old"
    const val ARIA2_SPLIT = "aria2_split"
    const val CACHE_QUICK_MODE = "cache_quick_mode"
    const val PARALLEL_DOWNLOAD_VIDEOS = "parallel_download_videos"
    const val CACHE_DEFAULT_QUALITY = "cache_default_quality"
    const val FORCE_HIGH_QUALITY = "force_high_quality_options"
    const val SAVE_PATH_VIDEO = "save_path_video"
    const val SAVE_PATH_PICTURES = "save_path_pictures"

    // ==================== 高级与实验 ====================
    const val DEV_PLAYER_ROTATE_SOFTWARE = "dev_player_rotate_software"
    const val PLAYER_SHOW_VIEWPOINTS = "player_show_viewpoints"
    const val PLAYER_INTERACTION_DEBUG = "player_interaction_debug"

    // ==================== 播放器 ====================
    /** 当前选择的播放器（null / terminalPlayer / mtvPlayer / aliangPlayer）。 */
    const val PLAYER = "player"
    /** 默认清晰度（qn）。 */
    const val PLAY_QN = "play_qn"

    const val PLAYER_LONGCLICK = "player_longclick"
    const val PLAYER_DOUBLETAP_SEEK = "player_doubletap_seek"
    const val PLAYER_SWIPE_SEEK = "player_swipe_seek"
    /** 播放页触摸锁定的首次提示是否已经展示过（纯记账，不出现在设置页）。 */
    const val PLAYER_LOCK_HINT_SHOWN = "player_lock_hint_shown"
    /** 双击有缩放时优先复位；保留旧键兼容已保存的开关，不再还原屏幕方向。 */
    const val PLAYER_DOUBLETAP_RESTORE_SCREEN = "player_doubletap_restore_screen"
    const val PLAYER_DOUBLETAP_SEEK_SECONDS = "player_doubletap_seek_seconds"
    /** 旧「洗脑循环」开关；已被 [PLAYER_DEFAULT_LOOP] 取代，仅用于迁移默认模式。 */
    const val PLAYER_LOOP = "player_loop"
    const val PLAYER_BACKGROUND = "player_background"
    /** 旧「默认横屏」开关；已被 [PLAYER_DEFAULT_ORIENTATION] 取代，仅用于迁移默认方向。 */
    const val PLAYER_AUTOLANDSCAPE = "player_autolandscape"
    const val PLAYER_FROM_LAST = "player_from_last"
    const val PLAYER_SHOW_ONLINE = "player_show_online"
    /** 旧「听视频模式」开关；已被 [PLAYER_DEFAULT_AUDIO_ONLY] 取代，仅用于迁移默认模式。 */
    const val PLAYER_AUDIO_ONLY = "player_audio_only"
    const val PLAYER_SCALE = "player_scale"
    const val PLAYER_DOUBLEMOVE = "player_doublemove"
    const val PLAYER_DISPLAY = "player_display"
    const val PLAYER_CODEC = "player_codec"
    const val PLAYER_AUDIO = "player_audio"
    const val PLAYER_HIGH_ENERGY = "player_high_energy"
    /**
     * 自动跳过片头/片尾。数据来源是 `view_points` 里 `type=1`（片头）/`type=2`（片尾）的区间。
     * 默认关闭：跳过是「替用户做决定」，必须由用户显式开启，且跳过后要能撤回。
     */
    const val PLAYER_SKIP_OP_ED = "player_skip_op_ed"
    /**
     * 「自动跳过片头片尾」的首次引导是否已经弹过。纯记账用，不出现在设置页。
     */
    const val PLAYER_SKIP_OP_ED_GUIDED = "player_skip_op_ed_guided"
    const val PLAYER_DANMAKU_ALLOW_OVERLAP = "player_danmaku_allowoverlap"
    const val PLAYER_DANMAKU_MERGE_DUPLICATE = "player_danmaku_mergeduplicate"
    const val PLAYER_DANMAKU_FORCE_R2L = "player_danmaku_forceR2L"
    const val PLAYER_DANMAKU_SHOW_SENDER = "player_danmaku_showsender"
    const val PLAYER_DANMAKU_MAXLINE = "player_danmaku_maxline"
    const val PLAYER_DANMAKU_SIZE = "player_danmaku_size"
    const val PLAYER_DANMAKU_TRANSPARENCY = "player_danmaku_transparency"
    const val PLAYER_DANMAKU_SPEED = "player_danmaku_speed"
    const val PLAYER_SUBTITLE_AUTOSHOW = "player_subtitle_autoshow"
    const val PLAYER_SUBTITLE_AI_ALLOWED = "player_subtitle_ai_allowed"
    const val PLAYER_SUBTITLE_DELTA = "player_subtitle_delta"
    const val PLAYER_SUBTITLE_SIZE = "player_subtitle_size"
    /**
     * 「字幕字号大小」的默认值（sp）。
     *
     * 与 `res/layout/activity_player.xml` 的 `android:textSize="11sp"` 是同一个数，
     * 也必须是同一个数：设置页只负责覆盖，未设置时播放器直接用布局里的字号。
     * 改这里就顺手改布局（XML 里读不到 Kotlin 常量，只能两处一起看）。
     */
    const val PLAYER_SUBTITLE_SIZE_DEFAULT = 11
    const val PLAYER_UI_SHOW_ROTATE_BTN = "player_ui_showRotateBtn"
    const val PLAYER_UI_SHOW_DANMAKU_BTN = "player_ui_showDanmakuBtn"
    const val PLAYER_UI_SHOW_QUALITY_BTN = "player_ui_showQualityBtn"
    const val PLAYER_UI_SHOW_PAGE_BTN = "player_ui_showPageBtn"
    const val PLAYER_INTERACTION_CHOICE_SIZE = "player_interaction_choice_size"
    /**
     * 「互动选项字体大小」的默认值（sp）。
     *
     * 与 `res/layout/cell_interaction_choice.xml` 的 `android:textSize="17sp"` 是同一个数，
     * 也必须是同一个数：设置页只负责覆盖，未设置时播放器直接用布局里的字号。
     * 改这里就顺手改布局（XML 里读不到 Kotlin 常量，只能两处一起看）。
     */
    const val PLAYER_INTERACTION_CHOICE_SIZE_DEFAULT = 17f

    // ==================== 播放默认值（开播时自动应用，解析逻辑见 player/PlayerDefaults.kt） ====================
    // 设置页「播放默认值」分组；每项存「模式」，「沿用上次」时再读对应的 PLAYER_LAST_* 记录。
    /** 三态模式取值：开。 */
    const val PLAYER_DEFAULT_MODE_ON = "on"
    /** 三态模式取值：关。 */
    const val PLAYER_DEFAULT_MODE_OFF = "off"
    /** 三态模式取值：沿用上次。 */
    const val PLAYER_DEFAULT_MODE_LAST = "last"

    const val PLAYER_DEFAULT_DANMAKU = "player_default_danmaku"
    const val PLAYER_DEFAULT_AUDIO_ONLY = "player_default_audio_only"
    const val PLAYER_DEFAULT_LOOP = "player_default_loop"
    const val PLAYER_DEFAULT_AUTONEXT = "player_default_autonext"
    /** 倍速：存 "0.5"~"3.0" 或 [PLAYER_DEFAULT_MODE_LAST]。 */
    const val PLAYER_DEFAULT_SPEED = "player_default_speed"
    const val PLAYER_DEFAULT_SUBTITLE = "player_default_subtitle"
    const val PLAYER_DEFAULT_ORIENTATION = "player_default_orientation"

    /** 字幕默认值：开播自动选中文（优先人工，其次 AI）。 */
    const val PLAYER_DEFAULT_SUBTITLE_ZH = "zh"
    /** 字幕默认值：自行选择（是否弹选择框交给 [PLAYER_SUBTITLE_AUTOSHOW]）。 */
    const val PLAYER_DEFAULT_SUBTITLE_MANUAL = "manual"

    /** 屏幕方向：总是横屏。 */
    const val PLAYER_DEFAULT_ORIENTATION_LANDSCAPE = "landscape"
    /** 屏幕方向：总是竖屏。 */
    const val PLAYER_DEFAULT_ORIENTATION_PORTRAIT = "portrait"
    /** 屏幕方向：按视频分辨率（宽>高则横屏）。 */
    const val PLAYER_DEFAULT_ORIENTATION_AUTO = "auto"

    // ---- 「沿用上次」的记录位：播放中实际用过的值，跨重启保存 ----
    /** 上一次弹幕的实际可见状态；沿用既有键（播放中切换弹幕时写入）。 */
    const val PLAYER_LAST_DANMAKU = "pref_switch_danmaku"
    const val PLAYER_LAST_AUDIO_ONLY = "player_last_audio_only"
    const val PLAYER_LAST_LOOP = "player_last_loop"
    const val PLAYER_LAST_AUTONEXT = "player_last_autonext"
    const val PLAYER_LAST_SPEED = "player_last_speed"

    // ==================== 自建终端服务（反馈 / 遥测 / 崩溃报告 / 公告） ====================
    /**
     * 匿名使用统计开关。默认开，**默认值与读取落点只在 [TerminalReportPrefs] 定义一次**，
     * 不要在别处写死 `true`。
     *
     * 上报内容只有随机 install_id + 版本号 + 机型/系统版本，不带任何账号信息，
     * 也绝不携带 Cookie；用途仅为统计唯一安装数与日活。
     */
    const val TELEMETRY_ENABLE = "terminal_telemetry_enable"

    /** 匿名安装标识：首次启动生成的随机 UUID，与 B 站账号完全无关，卸载重装即换新。 */
    const val TELEMETRY_INSTALL_ID = "terminal_install_id"

    /** 最近一次上报成功的日期（yyyy-MM-dd，UTC+8），用于「一天一次」。 */
    const val TELEMETRY_LAST_REPORT_DAY = "terminal_telemetry_last_day"

    /**
     * 设备级去重键：`sha256(ANDROID_ID + 固定盐)` 的截断值，见 `TerminalApi.getDeviceKey()`。
     *
     * 用途**只有**一个：让同一台手机同时装「普通版 + 共存版」时被算作**一台设备**，
     * 而不是两台。两个 flavor 用同一个签名，系统给的 ANDROID_ID 相同，所以哈希也相同。
     *
     * 原始 ANDROID_ID **绝不上报、也不落盘**，这里只存哈希。
     * 恢复出厂设置或切换系统用户会得到新值，此时视为一台新设备（可接受）。
     */
    const val TELEMETRY_DEVICE_KEY = "terminal_device_key"

    /**
     * 客户端公告已读到的最大 id（唯一游标）：本项目公告与上游逐条审核透传的公告共用。
     * id 是服务端自增 id + 10 亿偏移；上游 `api.biliterminal.cn` 的 `app_announcement_last`
     * 客户端已不再使用（26.10.23 起不再直连上游公告接口）。
     */
    const val TERMINAL_ANNOUNCEMENT_LAST = "terminal_announcement_last"

    /**
     * 崩溃后是否自动上传错误报告。默认开（默认值见 [TerminalReportPrefs]）；关掉后崩溃页仍保留手动上传按钮。
     */
    const val CRASH_REPORT_AUTO = "terminal_crash_report_auto"

    /**
     * 反馈/崩溃报告是否附带 B 站 UID。
     *
     * **默认关**：用户可见文案里承诺「不收集账号信息」，所以 UID 必须由用户
     * 在反馈页显式勾选后才附带（价值是开发者能查这个账号的具体问题，
     * 代价是把匿名反馈变成实名反馈，选择权交给用户）。
     */
    const val FEEDBACK_ATTACH_MID = "terminal_feedback_attach_mid"

    /**
     * 用户已**同意**的隐私说明版本号（0 = 从未同意）。
     *
     * 与 [PRIVACY_PROMPTED_VERSION] 分开存是刻意的：「同意」决定能不能上报，
     * 「问过没有」决定还要不要弹窗——「不同意」同样是一次回答，不该每次启动都再问一遍。
     * 判据统一走 `TerminalApi.hasPrivacyConsent()` / `needsPrivacyConsent()`，别在别处读这个键。
     */
    const val PRIVACY_CONSENT_VERSION = "terminal_privacy_consent_version"

    /** 用户已就哪个版本的隐私说明表过态（同意或不同意都算）。 */
    const val PRIVACY_PROMPTED_VERSION = "terminal_privacy_prompted_version"

    // ==================== 调试 ====================
    const val DEV_LOGV = "dev_logv"
    const val DEV_LOGD = "dev_logd"
    const val DEV_LOGI = "dev_logi"
    const val DEV_JSONERR_DETAILED = "dev_jsonerr_detailed"
    const val DEV_RECYCLERERR_DETAILED = "dev_recyclererr_detailed"

    /**
     * Agent 测试模式总开关（26.10.14）。
     *
     * 给 AI agent / 自动化脚本用的一键开关：打开后一次性把「测试期间该关的干扰关掉、该开的观测打开」——
     * 日志与 HTTP trace 全量打开并落盘、跳过 UETool 悬浮窗的权限跳转与界面遮挡。
     * 它**只在 Debug 构建下生效**（见 `BiliTerminal.applyLogSwitches`），Release 包读到 true 也会被忽略。
     *
     * 默认 false：不改变任何既有用户的行为。
     */
    const val DEV_AGENT_MODE = "dev_agent_mode"

    /** Agent 模式下是否把日志与 HTTP trace 落盘（默认开）。落盘位置见 `Logu.getLogDir()`。 */
    const val DEV_LOG_FILE = "dev_log_file"
}
