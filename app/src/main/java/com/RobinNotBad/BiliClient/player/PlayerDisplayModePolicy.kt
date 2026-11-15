package com.RobinNotBad.BiliClient.player

/**
 * 播放器「显示方式」（`SettingsKeys.PLAYER_DISPLAY`）存档的解析与回写口径（纯逻辑，便于 JVM 单测）。
 *
 * <h3>为什么需要这个兜底</h3>
 * 这个键在设置页是「选择项」（[com.RobinNotBad.BiliClient.activity.settings.SettingChooseResult]），
 * 选中后按**字符串**写入 `"TextureView"` / `"SurfaceView"`；而播放器内核这边历史上是按**布尔**
 * 读的（`SharedPreferencesUtil.getBoolean("player_display", sdk < 26)`），
 * `getBoolean` 把 `ClassCastException` 吞掉并返回默认值——于是不管用户选什么，
 * 播放器永远按 `sdk < 26` 的默认值建控件，「显示方式」这个开关从来没生效过。
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
}
