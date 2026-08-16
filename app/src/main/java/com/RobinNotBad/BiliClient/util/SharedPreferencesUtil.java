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
    public static final String SEARCH_CATEGORY_SORT = "search_category_sort";
    public static final String SEARCH_CATEGORY_ARTICLE_SHOW = "search_category_article_show";
    public static final String SEARCH_CATEGORY_USER_SHOW = "search_category_user_show";
    public static final String SEARCH_CATEGORY_AUDIO_SHOW = "search_category_audio_show";
    public static final String SEARCH_CATEGORY_LIVE_SHOW = "search_category_live_show";
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
    public static final String VIRTUAL_COLLECTION_ENABLE = "virtual_collection_enable";
    public static final String PRIVACY_MODE = "privacy_mode";

    public static String cookies = "cookies";
    public static String mid = "mid";
    public static String csrf = "csrf";
    public static String access_key = "access_key";
    public static String refresh_token = "refresh_token";
    public static String setup = "setup";
    public static String last_version = "last_version";
    public static String player = "player";
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
        return sharedPreferences.getString(key, def);
    }

    public static void putString(String key, String value) {
        sharedPreferences.edit().putString(key, value).apply();
    }

    public static int getInt(String key, int def) {
        return sharedPreferences.getInt(key, def);
    }

    public static void putInt(String key, int value) {
        sharedPreferences.edit().putInt(key, value).apply();
    }

    public static long getLong(String key, long def) {
        return sharedPreferences.getLong(key, def);
    }

    public static void putLong(String key, long value) {
        sharedPreferences.edit().putLong(key, value).apply();
    }

    public static boolean getBoolean(String key, boolean def) {
        return sharedPreferences.getBoolean(key, def);
    }

    public static void putBoolean(String key, boolean value) {
        sharedPreferences.edit().putBoolean(key, value).apply();
    }

    public static void putFloat(String key, float value) {
        sharedPreferences.edit().putFloat(key, value).apply();
    }

    public static float getFloat(String key, float def) {
        return sharedPreferences.getFloat(key, def);
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

    /**
     * 批量写入 - 将多个键值对一次性提交，减少I/O次数
     * 适用于需要同时更新多个设置的场景
     */
    public static void beginBatchEdit() {
        sharedPreferences.edit(); // 该方法内部已延迟初始化editor
    }

    public static void applyBatch(Runnable batchOperation) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        // 将editor传入操作（通过ThreadLocal或回调）
        batchOperation.run();
    }

    /**
     * 原子批量写入：多个键值对合并为一次 apply()
     */
    public static void edit(java.util.function.Consumer<SharedPreferences.Editor> operation) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        operation.accept(editor);
        editor.apply();
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
