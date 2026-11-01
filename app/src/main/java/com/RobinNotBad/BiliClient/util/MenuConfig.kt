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
    private val BASE_ITEMS = listOf(
        "recommend", "short_video", "popular", "precious", "ranking",
        "hotsearch", "live", "timeline", "search", "dynamic",
        "myspace", "message", "local", "settings"
    )

    /**
     * 可选菜单 key：只有带私有功能的构建才有，由 [OptionalFeatures] 反射问出来（详见
     * `docs/optional-features.md`）。公共构建里恒为空列表 —— 于是 ALL_ITEMS / 默认值 /
     * 老用户迁移 / 单测行为与从前完全一致。
     * 包在 try/catch 里：连 OptionalFeatures 这个类都加载不了时也退化为空列表。
     */
    val OPTIONAL_ITEMS: List<String> by lazy {
        try {
            listOfNotNull(OptionalFeatures.menuKey())
        } catch (e: Throwable) {
            emptyList()
        }
    }

    /** 全量菜单 key = 公共项 + 可选私有项（顺序即默认展示顺序）。 */
    val ALL_ITEMS: List<String>
        get() = BASE_ITEMS + OPTIONAL_ITEMS

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
     * 容错解析配置串：只保留本版本认识的 key（按原顺序、去重），不认识的一律丢弃。
     *
     * 用在新版本读取 `menu_enabled` 时：更早/更晚的构建、或带私有功能的构建可能写下
     * 本版本没有的菜单 key（例如私有项 `scan_qr`）。严格 [parse] 遇到这种值会整份判死，
     * 调用方于是回退到「默认顺序 + 默认显隐」并写回——用户的菜单排序与显隐被静默清空，
     * 表现就是「改了保存不住、会恢复默认」（反馈 #81）。这里先清掉不认识的 key，
     * 让剩下的有效配置继续生效。
     *
     * @return 一个非空列表；若清理后什么都不剩（或输入为空）返回 null。
     */
    fun parseLenient(raw: String?): List<String>? {
        if (raw.isNullOrBlank()) return null
        val seen = HashSet<String>()
        val result = ArrayList<String>()
        for (key in raw.split(";")) {
            if (key.isBlank() || key !in ALL_ITEMS) continue
            if (seen.add(key)) result.add(key)
        }
        return result.ifEmpty { null }
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
            if ((key in FIXED_ITEMS || key in OPTIONAL_ITEMS) && key !in enabled) enabled.add(key)
        }
        return enabled
    }

    /** 未启用列表 = 全量顺序减去已启用项（派生，顺序稳定）。 */
    fun disabledFrom(enabled: List<String>): List<String> =
        ALL_ITEMS.filter { it !in enabled }

    /**
     * 读取启用列表：合法且固定项齐全则直接用；否则从旧数据迁移并写回。
     * 可选私有项（[OPTIONAL_ITEMS]）也要求齐全：老用户在带私有功能的版本之前存下的
     * menu_enabled 里没有那些 key，必须走一次迁移把它们补进来，而不是当作配置合法直接返回。
     */
    fun loadEnabled(
        readString: (String) -> String?,
        readBoolean: (String) -> Boolean?,
        writeString: (String, String) -> Unit
    ): List<String> {
        val raw = readString(SharedPreferencesUtil.MENU_ENABLED)
        val parsed = parse(raw)
        if (parsed != null && FIXED_ITEMS.all { it in parsed } && OPTIONAL_ITEMS.all { it in parsed }) {
            return parsed
        }
        // 配置里混进了本版本不认识的 key（更早/更晚的构建、或私有构建特有的菜单项）：
        // 严格 parse 会整份判死，以前这里就静默回退成默认值并写回，用户的排序与显隐被清空
        // （反馈 #81「菜单设置改了保存不住、会恢复默认」）。先容错清掉不认识的 key，
        // 只要固定项还齐全就沿用用户配置，并顺手把清理结果写回。
        val lenient = parseLenient(raw)
        if (lenient != null && FIXED_ITEMS.all { it in lenient }) {
            val completed = lenient + OPTIONAL_ITEMS.filter { it !in lenient }
            writeString(SharedPreferencesUtil.MENU_ENABLED, serialize(completed))
            return completed
        }
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

    /** 默认启动页：推荐（固定项，正常情况下必然在已启用列表里）。 */
    const val DEFAULT_START = "recommend"

    /** 断网时的启动页：缓存（本地列表不依赖网络，断网也打得开）。 */
    const val OFFLINE_START = "local"

    /**
     * 解析启动页 key：已存值仍在已启用列表里就沿用，否则退回 [DEFAULT_START]。
     * 极端情况下（启用列表里连推荐都没有）才退回列表首项，最后兜底 [DEFAULT_START]。
     */
    fun resolveStart(enabled: List<String>, stored: String?): String = when {
        stored != null && stored in enabled -> stored
        DEFAULT_START in enabled -> DEFAULT_START
        else -> enabled.firstOrNull() ?: DEFAULT_START
    }

    /**
     * 读取启动页配置：
     * - 从未设置过（null/空白）：返回默认值，**不写盘**（缺省语义，与旧行为解耦）；
     * - 已设置但目标已被移入「未启用」：立即改回默认值并写回（不允许指向不可达页面）。
     */
    fun loadStart(
        enabled: List<String>,
        readString: (String) -> String?,
        writeString: (String, String) -> Unit
    ): String {
        val stored = readString(SharedPreferencesUtil.MENU_START)?.takeIf { it.isNotBlank() }
        val resolved = resolveStart(enabled, stored)
        if (stored != null && stored !in enabled) {
            writeString(SharedPreferencesUtil.MENU_START, resolved)
        }
        return resolved
    }

    /**
     * 解析**本次启动**实际要进入的菜单页：有网走用户设置，没网一律进缓存页。
     *
     * 自定义启动页（推荐/动态/消息……）全都要联网，断网进去只能看到加载失败，
     * 用户体感就是「打开应用卡在报错页」。缓存页读的是本地数据库，断网也完整可用，
     * 所以断网时把启动目标改成 [OFFLINE_START]，网络正常时再按 [resolveStart] 进用户选的页。
     *
     * 只影响本次启动，不改写 `menu_start`：网络恢复后仍按用户原来的设置走。
     */
    fun resolveLaunchStart(enabled: List<String>, stored: String?, online: Boolean): String =
        if (online) resolveStart(enabled, stored) else OFFLINE_START

    /** 保存启动页配置。 */
    fun saveStart(key: String, writeString: (String, String) -> Unit) {
        writeString(SharedPreferencesUtil.MENU_START, key)
    }
}
