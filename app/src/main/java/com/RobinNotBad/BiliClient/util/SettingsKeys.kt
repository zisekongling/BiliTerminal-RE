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
    const val CREATIVE_ENABLE = "creative_enable"
    const val SEARCH_SUGGESTIONS_ENABLE = "search_suggestions_enable"
    const val BACK_DISABLE = "back_disable"
    const val SAVE_BAN_GALLERY = "save_ban_gallery"
    const val IMAGE_REQUEST_JPG = "image_request_jpg"
    const val IMAGE_NO_LOAD_ONSCROLL = "image_no_load_onscroll"
    const val UI_ROTATORY_ENABLE = "ui_rotatory_enable"
    const val UI_ROTATORY_RECYCLER = "ui_rotatory_recycler"
    const val UI_ROTATORY_SCROLL = "ui_rotatory_scroll"

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
    const val PLAYER_DOUBLETAP_RESTORE_SCREEN = "player_doubletap_restore_screen"
    const val PLAYER_DOUBLETAP_SEEK_SECONDS = "player_doubletap_seek_seconds"
    const val PLAYER_LOOP = "player_loop"
    const val PLAYER_BACKGROUND = "player_background"
    const val PLAYER_AUTOLANDSCAPE = "player_autolandscape"
    const val PLAYER_FROM_LAST = "player_from_last"
    const val PLAYER_SHOW_ONLINE = "player_show_online"
    const val PLAYER_AUDIO_ONLY = "player_audio_only"
    const val PLAYER_SCALE = "player_scale"
    const val PLAYER_DOUBLEMOVE = "player_doublemove"
    const val PLAYER_DISPLAY = "player_display"
    const val PLAYER_CODEC = "player_codec"
    const val PLAYER_AUDIO = "player_audio"
    const val PLAYER_HIGH_ENERGY = "player_high_energy"
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
    const val PLAYER_UI_SHOW_ROTATE_BTN = "player_ui_showRotateBtn"
    const val PLAYER_UI_SHOW_DANMAKU_BTN = "player_ui_showDanmakuBtn"
    const val PLAYER_UI_SHOW_QUALITY_BTN = "player_ui_showQualityBtn"
    const val PLAYER_UI_SHOW_PAGE_BTN = "player_ui_showPageBtn"
    const val PLAYER_INTERACTION_CHOICE_SIZE = "player_interaction_choice_size"

    // ==================== 调试 ====================
    const val DEV_LOGV = "dev_logv"
    const val DEV_LOGD = "dev_logd"
    const val DEV_LOGI = "dev_logi"
    const val DEV_JSONERR_DETAILED = "dev_jsonerr_detailed"
    const val DEV_RECYCLERERR_DETAILED = "dev_recyclererr_detailed"
}
