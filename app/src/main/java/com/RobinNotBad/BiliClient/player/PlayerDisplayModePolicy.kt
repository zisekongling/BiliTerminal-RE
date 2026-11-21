package com.RobinNotBad.BiliClient.player

/**
 * 播放器「显示方式」（`SettingsKeys.PLAYER_DISPLAY`）存档的解析与回写口径（纯逻辑，便于 JVM 单测）。
 *
 * <h3>为什么需要这个兜底</h3>
 * 这个键历史上两边各说各话：设置页那一行是 `choose`（两选一 RadioButton），写的是**布尔**；
 * 播放器内核按**字符串**读（`getString("player_display", "")`）。`SharedPreferencesUtil.getString`
 * 把 `ClassCastException` 吞掉并返回默认值——于是不管用户选什么，播放器永远按 `sdk < 26` 的
 * 默认值建控件，「显示方式」这个开关从来没生效过（26.11.16 用户反馈「播放器设置失效」的根因）。
 *
 * 现在设置页那一行改成 `list_choose`，按**字符串**写规范取值；播放器按字符串读。历史存档里
 * 可能还是布尔（老用户选过一次），由 [migrateLegacy] 在启动时一次性改成规范字符串。
 *
 * 这里把「原始存档字符串 → 用哪种控件」收敛成一个纯函数：既认现在的字符串取值，
 * 也认早期版本可能留下的 `"true"` / `"false"`，认不出来（空、乱码、从未设置）时回落
 * `sdkInt < 26`，保证「没动过设置的人」观感与改动前完全一致。
 */
object PlayerDisplayModePolicy {

    /** 存档与设置页选项里的「纹理视图」取值。 */
    const val TEXTURE_VIEW = "TextureView"

    /** 存档与设置页选项里的「表面视图」取值。 */
    const val SURFACE_VIEW = "SurfaceView"

    /**
     * 该 sdk 上的默认显示方式（与 `PlayerSettingsGroups` 里这一项的默认值必须一致）。
     *
     * Android 8.0 以下（API 26 以前）没有 SurfaceView 的透明/动画同步能力，项目一直默认走
     * TextureView；26 起默认 SurfaceView（省电、HDR 路径完整）。
     */
    @JvmStatic
    fun defaultFor(sdkInt: Int): String = if (sdkInt < 26) TEXTURE_VIEW else SURFACE_VIEW

    /**
     * 原始存档字符串 → 是否使用 TextureView。
     *
     * @param raw `SharedPreferencesUtil.getString(PLAYER_DISPLAY, "")` 的**原始**返回值；
     *            可能为 null（键不存在）、空串、`"TextureView"` / `"SurfaceView"`、
     *            历史遗留的 `"true"` / `"false"`，或任何认不出来的垃圾
     * @param sdkInt `Build.VERSION.SDK_INT`
     */
    @JvmStatic
    fun useTextureView(raw: String?, sdkInt: Int): Boolean =
        when (raw?.trim()?.lowercase()) {
            TEXTURE_VIEW.lowercase() -> true
            SURFACE_VIEW.lowercase() -> false
            // 历史遗留：这个键曾经按布尔存过（默认值也写成 "true"/"false"）
            "true" -> true
            "false" -> false
            // 从未设置 / 认不出来：与改动前的实际行为一致
            else -> sdkInt < 26
        }

    /** 布尔 → 写回设置页用的规范字符串（设置项默认值也取它）。 */
    @JvmStatic
    fun valueOf(useTextureView: Boolean): String =
        if (useTextureView) TEXTURE_VIEW else SURFACE_VIEW

    /**
     * 旧存档（布尔 / `"true"` / `"false"`）→ 规范字符串，供启动时一次性迁移。
     *
     * 老用户的存档是布尔：设置页 `choose` 行调 `putBoolean`，而布尔 `true` 表示选中了
     * 第一个单选项，也就是「纹理视图」。不迁移的话，改版后 `getString` 只会拿到空串、
     * 回落 sdk 默认值，用户「明明选过」的设置又丢一次。
     *
     * @param raw `SharedPreferences.all[PLAYER_DISPLAY]` 的原始对象
     * @return 需要写回的规范字符串；已经是规范串（或 `null`/认不出来的类型）时返回 `null`，
     *         调用方据此「什么都不做」，保证重复迁移是幂等的
     */
    @JvmStatic
    fun migrateLegacy(raw: Any?): String? = when (raw) {
        is Boolean -> valueOf(raw)
        is String -> when (raw.trim().lowercase()) {
            "true" -> TEXTURE_VIEW
            "false" -> SURFACE_VIEW
            else -> null
        }
        else -> null
    }
}
