package com.RobinNotBad.BiliClient.util;

import android.content.SharedPreferences;

/**
 * 被 luern0313 创建于 2020/5/4.
 * #以下代码部分来源于腕上哔哩的开源项目，有修改。感谢开源者做出的贡献！
 */
public class SharedPreferencesUtil {
    public static final String LINK_ENABLE = "link_enable";
    public static final String RCMD_API_NEW_PARAM = "rcmd_api_new_param";
    public static final String MENU_SORT = "menu_sort";
    public static final String MENU_ENABLED = "menu_enabled";
    /** 启动时直接进入的菜单页 key（缺省 = 推荐）。 */
    public static final String MENU_START = "menu_start";
    /** 菜单设置页顶部的使用说明是否已被用户长按隐藏。 */
    public static final String MENU_HELP_HIDDEN = "menu_help_hidden";
    public static final String SEARCH_CATEGORY_SORT = "search_category_sort";
    public static final String SEARCH_CATEGORY_ARTICLE_SHOW = "search_category_article_show";
    public static final String SEARCH_CATEGORY_USER_SHOW = "search_category_user_show";
    public static final String SEARCH_CATEGORY_LIVE_SHOW = "search_category_live_show";
    public static final String SEARCH_CATEGORY_BANGUMI_SHOW = "search_category_bangumi_show";
    public static final String ASYNC_INFLATE_ENABLE = "async_inflate_enable";
    public static final String LOAD_TRANSITION = "load_transition";
    public static final String SNACKBAR_ENABLE = "snackbar_enable";
    public static final String STRICT_URL_MATCH = "strict_url_match";
    public static final String NO_VIP_COLOR = "no_vip_color";
    public static final String NO_MEDAL = "no_medal";
    public static final String REPLY_MARQUEE_NAME = "reply_marquee_name";
    public static final String NEW_DANMAKU_API = "new_danmaku_api";
    public static final String DYNAMIC_UPDATE_CHECK_ENABLE = "dynamic_update_check_enable";
    public static final String DYNAMIC_UPDATE_NUM = "dynamic_update_num";
    public static final String MESSAGE_UPDATE_CHECK_ENABLE = "message_update_check_enable";
    public static final String MESSAGE_UPDATE_NUM = "message_update_num";
    /**
     * C16：上次检查到的「追番最新集」快照，JSON 形如 {"media_id": new_ep_id, ...}。
     * 由 BangumiUpdateChecker 读写：只在快照里已存在的番剧的 new_ep.id 变化时才算「更新了」，
     * 所以首次检查只会写下快照、不会提醒；拉取失败时绝不清空（否则下下次会把老集当新集报）。
     */
    public static final String BANGUMI_UPDATE_SNAPSHOT = "bangumi_update_snapshot";
    public static final String PRIVATE_MSG_UNREAD_BADGE_ENABLE = "private_msg_unread_badge_enable";
    public static final String PRIVATE_MSG_AUTO_READ_ENABLE = "private_msg_auto_read_enable";
    public static final String FOLLOW_GROUP_MODE = "follow_group_mode";
    public static final String NIGHT_REMINDER_ENABLE = "night_reminder_enable";
    public static final String SEARCH_DEFAULT_CONTENT_ENABLE = "search_default_content_enable";
    public static final String PLAYER_MEDIA_SESSION_ENABLE = "player_media_session_enable";
    public static final String RECENT_UP_DISPLAY_ENABLE = "recent_up_display_enable";
    public static final String AUTO_UPDATE_CHECK_ENABLE = "auto_update_check_enable";
    public static final String RECOMMEND_DEDUP_ENABLE = "recommend_dedup_enable";
    public static final String RECOMMEND_SOURCE = "recommend_source";
    public static final String RECOMMEND_SOURCE_WEB = "recommend_source_web";
    public static final String RECOMMEND_SOURCE_APP = "recommend_source_app";
    public static final String RECOMMEND_SOURCE_BOTH = "recommend_source_both";
    /**
     * 虚拟合集「组合集」（功能一）：把同一文件夹/收藏夹的视频组成一个合集播放列表。
     * 26.11.01 起与 [VIRTUAL_COLLECTION_CLICK_PLAY] 拆成两个独立功能；
     * 默认值与读取统一走 `util/VirtualCollectionPrefs.kt`，别在调用点写死。
     */
    public static final String VIRTUAL_COLLECTION_ENABLE = "virtual_collection_enable";
    /**
     * 虚拟合集「点卡片直接播放」（功能二）：收藏夹里点视频卡片时直接起播，而不是进视频详情页。
     * 默认关闭；默认值与读取统一走 `util/VirtualCollectionPrefs.kt`。
     */
    public static final String VIRTUAL_COLLECTION_CLICK_PLAY = "virtual_collection_click_play";
    public static final String PRIVACY_MODE = "privacy_mode";

    public static String cookies = "cookies";
    public static String mid = "mid";
    public static String csrf = "csrf";
    public static String access_key = "access_key";
    public static String refresh_token = "refresh_token";
    public static String setup = "setup";
    /**
     * F4：上一次启动时的 versionCode，用于判断「更新后首次启动」。
     * 由 SplashActivity.shouldShowUpdateLogAfterUpgrade() 读写：与当前 versionCode 不一致
     * 且属于版本升级时自动打开更新日志页；首次安装只写回、不弹页。
     */
    public static String last_version = "last_version";
    // 26.10.04 批次 4（E4）：原来这里还有一个 `public static String player = "player";`，
    // 零引用、也不是 final，纯属第三处「player」定义，已删除。
    // 该 key 现在只有唯一来源：SettingsKeys.PLAYER（键名不变，仍是 "player"）。
    public static String padding_horizontal = "padding_horizontal";
    public static String padding_vertical = "padding_vertical";
    public static String cookie_refresh = "cookie_refresh";
    public static String search_history = "search_history";
    public static String cover_play_enabled = "cover_play_enabled";
    public static String tutorial_version = "tutorial_version";

    public static SharedPreferences sharedPreferences;

    public static SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }

    public static String getString(String key, String def) {
        // sharedPreferences 由 Application.onCreate 赋值，静态初始化或早期调用可能早于赋值，
        // 直接解引用会 NPE。未就绪时返回调用方传入的默认值（BiliTerminal.kt 有 getString(key, null)
        // 的调用点，故这里必须原样返回 def，不能擅自把 null 换成空串）。
        return sharedPreferences != null ? sharedPreferences.getString(key, def) : def;
    }

    public static void putString(String key, String value) {
        sharedPreferences.edit().putString(key, value).apply();
    }

    public static int getInt(String key, int def) {
        // 同 getString：sharedPreferences 未就绪时不能解引用，返回默认值（审计 P10）
        return sharedPreferences != null ? sharedPreferences.getInt(key, def) : def;
    }

    public static void putInt(String key, int value) {
        sharedPreferences.edit().putInt(key, value).apply();
    }

    public static long getLong(String key, long def) {
        // 同 getString：sharedPreferences 未就绪时不能解引用，返回默认值（审计 P10）
        return sharedPreferences != null ? sharedPreferences.getLong(key, def) : def;
    }

    public static void putLong(String key, long value) {
        sharedPreferences.edit().putLong(key, value).apply();
    }

    public static boolean getBoolean(String key, boolean def) {
        // 同 getString：sharedPreferences 未就绪时不能解引用，返回默认值（审计 P10）
        return sharedPreferences != null ? sharedPreferences.getBoolean(key, def) : def;
    }

    public static void putBoolean(String key, boolean value) {
        sharedPreferences.edit().putBoolean(key, value).apply();
    }

    public static void putFloat(String key, float value) {
        sharedPreferences.edit().putFloat(key, value).apply();
    }

    public static float getFloat(String key, float def) {
        // 同 getString：sharedPreferences 未就绪时不能解引用，返回默认值（审计 P10）
        return sharedPreferences != null ? sharedPreferences.getFloat(key, def) : def;
    }

    public static void removeValue(String key) {
        sharedPreferences.edit().remove(key).apply();
    }

    /** 读取菜单启用列表（含旧数据迁移回退），供 MenuActivity / SplashActivity / SettingMenuActivity 使用。 */
    public static java.util.List<String> loadMenuEnabled() {
        return MenuConfig.INSTANCE.loadEnabled(
                key -> getString(key, ""),
                key -> {
                    if (sharedPreferences != null && sharedPreferences.contains(key)) {
                        return sharedPreferences.getBoolean(key, false);
                    }
                    return null;
                },
                (key, value) -> { SharedPreferencesUtil.putString(key, value); return kotlin.Unit.INSTANCE; }
        );
    }

    /** 保存菜单启用列表。 */
    public static void saveMenuEnabled(java.util.List<String> enabled) {
        MenuConfig.INSTANCE.saveEnabled(enabled, (key, value) -> { SharedPreferencesUtil.putString(key, value); return kotlin.Unit.INSTANCE; });
    }

    /** 读取启动页 key（缺省=推荐；已存值失效时立即改回默认并写回）。 */
    public static String loadMenuStart(java.util.List<String> enabled) {
        return MenuConfig.INSTANCE.loadStart(
                enabled,
                key -> getString(key, ""),
                (key, value) -> { SharedPreferencesUtil.putString(key, value); return kotlin.Unit.INSTANCE; }
        );
    }

    /** 保存启动页 key。 */
    public static void saveMenuStart(String key) {
        MenuConfig.INSTANCE.saveStart(key, (k, value) -> { SharedPreferencesUtil.putString(k, value); return kotlin.Unit.INSTANCE; });
    }

    /** 读取「我的」页面功能布局（非法或缺失时回退默认并写回），供 MySpaceActivity / MySpaceMoreActivity / SettingMySpaceActivity 使用。 */
    public static MySpaceConfig.Layout loadMySpaceLayout() {
        return MySpaceConfig.INSTANCE.load(
                key -> getString(key, ""),
                (key, value) -> { SharedPreferencesUtil.putString(key, value); return kotlin.Unit.INSTANCE; }
        );
    }

    /** 保存「我的」页面功能布局。 */
    public static void saveMySpaceLayout(MySpaceConfig.Layout layout) {
        MySpaceConfig.INSTANCE.save(layout, (key, value) -> { SharedPreferencesUtil.putString(key, value); return kotlin.Unit.INSTANCE; });
    }

    /**
     * 原子批量写入：多个键值对合并为一次 apply()
     */
    public static void edit(EditorAction operation) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        operation.run(editor);
        editor.apply();
    }

    /**
     * 批量写操作回调。
     *
     * 为什么不直接用 java.util.function.Consumer：该包是 Java 8 才加进 Android 的（API 24），
     * 本工程 minSdk 21，运行到 21~23 的设备上会在类加载时抛 NoClassDefFoundError。
     * 这里自带一个单方法接口，Kotlin/Java 的 lambda 都能照常做 SAM 转换，调用点无需改动。
     */
    public interface EditorAction {
        void run(SharedPreferences.Editor editor);
    }

    /**
     * 使用commit同步写入（仅在必要时使用，如应用退出前）
     */
    public static void putBooleanSync(String key, boolean value) {
        sharedPreferences.edit().putBoolean(key, value).commit();
    }

    public static void putStringSync(String key, String value) {
        sharedPreferences.edit().putString(key, value).commit();
    }

    public static void putIntSync(String key, int value) {
        sharedPreferences.edit().putInt(key, value).commit();
    }

    public static void putLongSync(String key, long value) {
        sharedPreferences.edit().putLong(key, value).commit();
    }

}
