package com.RobinNotBad.BiliClient.util

/**
 * 菜单（MenuActivity）的启用列表配置。
 *
 * 单一数据源 `menu_enabled`：分号连接的有序已启用菜单 key 列表，同时承担排序与显隐。
 * 旧的 `menu_sort`（全量排序）与 6 个 `menu_*` 布尔开关仅用于一次性迁移读取。
 *
 * 纯 Kotlin 对象，无 Android 框架依赖，可 JVM 单测。
 */
object MenuConfig {

    /** 全量菜单 key，顺序即默认展示顺序（对应 MenuActivity.btnNames 的全部 key）。 */
    val ALL_ITEMS = listOf(
        "recommend", "short_video", "popular", "precious", "ranking",
        "hotsearch", "live", "timeline", "search", "dynamic",
        "myspace", "message", "local", "settings"
    )

    /** 固定不可隐藏的菜单项。 */
    val FIXED_ITEMS = setOf("recommend", "search", "local", "settings")

    /** 历史上有独立显隐开关的菜单项（menu_<key> 布尔）。 */
    val SWITCHABLE_KEYS = setOf("popular", "short_video", "precious", "ranking", "live", "timeline")

    /** 上述开关的默认值（与旧 SettingMenuActivity 的 getBoolean 默认值一致）。 */
    val SWITCH_DEFAULTS = mapOf(
        "popular" to true,
        "short_video" to true,
        "precious" to false,
        "ranking" to false,
        "live" to false,
        "timeline" to false
    )

    /** 默认已启用列表（新装用户、无旧数据时的结果）。 */
    val DEFAULT_ENABLED: List<String> = ALL_ITEMS.filter { key ->
        key in FIXED_ITEMS || key !in SWITCHABLE_KEYS || SWITCH_DEFAULTS.getValue(key)
    }

    fun serialize(enabled: List<String>): String = enabled.joinToString(";")

    /**
     * 解析配置串。非法（空、含未知 key、重复）返回 null。
     * 注意：只做语法校验，不保证固定项齐全（是否齐全由调用方决定是否接受）。
     */
    fun parse(raw: String?): List<String>? {
        if (raw.isNullOrBlank()) return null
        val keys = raw.split(";").filter { it.isNotBlank() }
        if (keys.isEmpty()) return null
        val seen = HashSet<String>()
        for (key in keys) {
            if (key !in ALL_ITEMS || !seen.add(key)) return null
        }
        return keys
    }

    /**
     * 从旧数据解析出已启用列表：
     * 以旧排序为基准（非法则用默认顺序），按旧开关过滤，最后补全缺失的固定项。
     */
    fun resolveEnabledList(oldSort: String?, oldSwitches: Map<String, Boolean>): List<String> {
        val base = parse(oldSort) ?: ALL_ITEMS
        val enabled = base.filter { key ->
            if (key in FIXED_ITEMS) true
            else if (key in SWITCHABLE_KEYS) oldSwitches[key] ?: SWITCH_DEFAULTS.getValue(key)
            else true
        }.toMutableList()
        for (key in ALL_ITEMS) {
            if (key in FIXED_ITEMS && key !in enabled) enabled.add(key)
        }
        return enabled
    }

    /** 未启用列表 = 全量顺序减去已启用项（派生，顺序稳定）。 */
    fun disabledFrom(enabled: List<String>): List<String> =
        ALL_ITEMS.filter { it !in enabled }

    /**
     * 读取启用列表配置：合法且固定项齐全则直接用；否则从旧数据迁移并写回。
     * [readBoolean] 返回开关键的实际存储值，缺失（null）时使用 [SWITCH_DEFAULTS]。
     */
    fun loadEnabled(
        readString: (String) -> String?,
        readBoolean: (String) -> Boolean?,
        writeString: (String, String) -> Unit
    ): List<String> {
        val parsed = parse(readString(SharedPreferencesUtil.MENU_ENABLED))
        if (parsed != null && FIXED_ITEMS.all { it in parsed }) return parsed
        val oldSwitches = SWITCHABLE_KEYS.associateWith {
            readBoolean("menu_$it") ?: SWITCH_DEFAULTS.getValue(it)
        }
        val resolved = resolveEnabledList(readString(SharedPreferencesUtil.MENU_SORT), oldSwitches)
        writeString(SharedPreferencesUtil.MENU_ENABLED, serialize(resolved))
        return resolved
    }

    /** 保存启用列表配置。 */
    fun saveEnabled(enabled: List<String>, writeString: (String, String) -> Unit) {
        writeString(SharedPreferencesUtil.MENU_ENABLED, serialize(enabled))
    }
}
