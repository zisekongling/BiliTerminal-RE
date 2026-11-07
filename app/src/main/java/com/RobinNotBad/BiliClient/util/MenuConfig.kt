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
        "recommend", "short_video", "popular", BANGUMI, "precious", "ranking",
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

    /**
     * 「番剧分区」菜单 key（26.11.07 新增，页面见 `activity/video/BangumiActivity`）。
     *
     * 它是**内容区块**而不是工具页，默认启用；并且对老用户做一次回填（见 [ensureBangumi]）——
     * 否则这个新功能只有新装用户看得见，提需求的人自己反而要先翻「菜单设置」才能用上。
     */
    const val BANGUMI = "bangumi"

    /**
     * 动作项「退出」的菜单 key：不是页面（点了直接收摊），但和页面项一样进菜单配置——
     * 能排序、能关掉。26.11.02 之前它是硬编码在菜单最下方的一枚固定按钮。
     */
    const val EXIT = "exit"

    /**
     * 动作项「继续播放」的菜单 key：不是页面（拉起上次的播放会话后收掉主页）。
     *
     * 26.11.07 之前它是菜单页里硬编码的「有会话就插到第一位」——用户关不掉、也移不走
     * （反馈：不想让菜单第一项总被一个按钮占着）。现在和 [EXIT] 同待遇：进菜单配置，
     * 能排序、能关掉，**默认不启用**（[DEFAULT_ENABLED] 里不含它）。
     */
    const val RESUME = "resume"

    /** 全部动作项：不是页面，既不能当启动页，也不能按「页面」的默认值处理。 */
    val ACTION_ITEMS: List<String> = listOf(EXIT, RESUME)

    /** 全量菜单 key = 公共项 + 可选私有项 + 动作项（顺序即默认展示顺序）。 */
    val ALL_ITEMS: List<String>
        get() = BASE_ITEMS + OPTIONAL_ITEMS + ACTION_ITEMS

    /** 固定不可隐藏的菜单项。 */
    val FIXED_ITEMS = setOf("recommend", "search", "local", "settings")

    /** 历史上有独立显隐开关的菜单项（menu_<key> 布尔）。 */
    val SWITCHABLE_KEYS = setOf("popular", "short_video", "precious", "ranking", "live", "timeline", BANGUMI)

    /** 上述开关的默认值（与旧 SettingMenuActivity 的 getBoolean 默认值一致）。 */
    val SWITCH_DEFAULTS = mapOf(
        "popular" to true,
        "short_video" to true,
        "precious" to false,
        "ranking" to false,
        "live" to false,
        "timeline" to false,
        // 番剧分区没有历史开关，这里给 true 只是为了让「从 menu_sort 迁移」的老用户也能拿到它
        BANGUMI to true
    )

    /**
     * 默认已启用列表（新装用户、无旧数据时的结果）。
     *
     * [RESUME] 是**默认关闭**的动作项：它只在有未看完会话时才有意义，不该默认占着菜单
     * 第一项（26.11.07 用户要求默认隐藏，想要的话自己去菜单设置里打开）。
     */
    val DEFAULT_ENABLED: List<String> = ALL_ITEMS.filter { key ->
        key != RESUME && (key in FIXED_ITEMS || key !in SWITCHABLE_KEYS || SWITCH_DEFAULTS.getValue(key))
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
        // 旧排序非法时退回「默认已启用列表」，而不是 [ALL_ITEMS]：后者含 [RESUME]，
        // 会顺着下面的 else 分支被当成「不在开关表里 → 默认启用」，让老用户菜单里
        // 莫名多出「继续播放」。
        val base = parse(oldSort) ?: DEFAULT_ENABLED
        val enabled = base.filter { key ->
            if (key in FIXED_ITEMS) true
            else if (key in SWITCHABLE_KEYS) oldSwitches[key] ?: SWITCH_DEFAULTS.getValue(key)
            else true
        }.toMutableList()
        for (key in ALL_ITEMS) {
            // 固定项 / 可选私有项必须齐全；[EXIT] 是默认启用的动作项，旧排序里同样没有它，
            // 一并补在末尾（用户自己关掉它是另一回事：那时走的是 menu_enabled 直读路径）。
            // [RESUME] 故意**不补**：它默认就该是关的，老用户升级后菜单里不该突然多一项。
            if ((key in FIXED_ITEMS || key in OPTIONAL_ITEMS || key == EXIT) && key !in enabled) {
                enabled.add(key)
            }
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
    ): List<String> =
        ensureBangumi(
            ensureExit(loadStoredEnabled(readString, readBoolean, writeString), readString, writeString),
            readString,
            writeString
        )

    /**
     * 把 [BANGUMI]（番剧分区）补进启用列表：它是 26.11.07 才出现的菜单项，
     * 老用户存下的 `menu_enabled` 里没有这个 key，不回填的话新功能只有新装用户看得见。
     *
     * 与 [ensureExit] 同一套判据：[SharedPreferencesUtil.MENU_BANGUMI_STATE] 只要被
     * [saveEnabled] 写过一次，就说明用户动过菜单配置——缺项即「他自己关掉的」，不再加回来。
     *
     * 插入位置按 [DEFAULT_ENABLED] 的规范顺序算（排在「热门」之后），不直接追加到末尾：
     * 末尾是「退出」这类动作项的位置，追加在后面会让菜单看起来像错位了。
     */
    private fun ensureBangumi(
        enabled: List<String>,
        readString: (String) -> String?,
        writeString: (String, String) -> Unit
    ): List<String> {
        if (BANGUMI in enabled) return enabled
        if (!readString(SharedPreferencesUtil.MENU_BANGUMI_STATE).isNullOrBlank()) return enabled
        val completed = insertAtCanonicalPosition(enabled, BANGUMI)
        writeString(SharedPreferencesUtil.MENU_ENABLED, serialize(completed))
        return completed
    }

    /**
     * 把 [key] 按 [DEFAULT_ENABLED] 的相对顺序插进 [enabled]：紧跟最后一个「规范顺序里排在它前面」的项。
     *
     * 例：用户顺序是 `settings;recommend;local;search` 时插入番剧 → 落在 `recommend` 之后，
     * 而不是被自己的顺序硬拖到 `settings` 前面。找不到可比较的邻居（列表里全是动作项 / 未知 key）
     * 就插在最前面——这只影响观感，不影响功能。
     */
    private fun insertAtCanonicalPosition(enabled: List<String>, key: String): List<String> {
        val canonicalIndex = DEFAULT_ENABLED.indexOf(key)
        if (canonicalIndex < 0) return enabled + key
        val insertAfter = enabled.indexOfLast { existing ->
            val index = DEFAULT_ENABLED.indexOf(existing)
            index in 0 until canonicalIndex
        }
        val result = enabled.toMutableList()
        result.add(insertAfter + 1, key)
        return result
    }

    /**
     * 把动作项 [EXIT] 补进启用列表：它是 26.11.02 才进菜单配置的（此前是硬编码在菜单最下方的
     * 固定按钮），老用户存下的 `menu_enabled` 里没有这个 key，不补的话升级后菜单里再也找不到
     * 退出按钮——手表上没有别的彻底收摊入口。
     *
     * 区分「老数据缺这一项」与「用户自己把它关掉了」靠 [SharedPreferencesUtil.MENU_EXIT_STATE]：
     * 只要保存过一次菜单配置就写这个键。缺项 + 没写过 = 老数据 → 补在末尾（默认启用）；
     * 缺项 + 写过 = 用户关的 → 保持隐藏，不再偷偷加回来。
     */
    private fun ensureExit(
        enabled: List<String>,
        readString: (String) -> String?,
        writeString: (String, String) -> Unit
    ): List<String> {
        if (EXIT in enabled) return enabled
        if (!readString(SharedPreferencesUtil.MENU_EXIT_STATE).isNullOrBlank()) return enabled
        val completed = enabled + EXIT
        writeString(SharedPreferencesUtil.MENU_ENABLED, serialize(completed))
        return completed
    }

    private fun loadStoredEnabled(
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
        // 记下「用户动过退出项」：之后 menu_enabled 里没有 exit 就表示是他关掉的，
        // 不再当成老数据补回来（见 ensureExit）
        writeString(SharedPreferencesUtil.MENU_EXIT_STATE, if (EXIT in enabled) "1" else "0")
        // 同上，番剧分区的回填判据（见 ensureBangumi）
        writeString(SharedPreferencesUtil.MENU_BANGUMI_STATE, if (BANGUMI in enabled) "1" else "0")
    }

    /** 默认启动页：推荐（固定项，正常情况下必然在已启用列表里）。 */
    const val DEFAULT_START = "recommend"

    /** 断网时的启动页：缓存（本地列表不依赖网络，断网也打得开）。 */
    const val OFFLINE_START = "local"

    /**
     * 解析启动页 key：已存值仍在已启用列表里就沿用，否则退回 [DEFAULT_START]。
     * 极端情况下（启用列表里连推荐都没有）才退回列表首项，最后兜底 [DEFAULT_START]。
     *
     * [EXIT] / [RESUME] 是动作项不是页面，永远不能当启动页（不然开机就直接收摊 /
     * 直接进播放器），所以它们既不作沿用值、也不作列表首项兜底。
     */
    fun resolveStart(enabled: List<String>, stored: String?): String = when {
        stored != null && stored !in ACTION_ITEMS && stored in enabled -> stored
        DEFAULT_START in enabled -> DEFAULT_START
        else -> enabled.firstOrNull { it !in ACTION_ITEMS } ?: DEFAULT_START
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
        // 指向动作项的旧值（或已不在启用列表里的值）一样要改回默认：动作项不是页面
        if (stored != null && (stored in ACTION_ITEMS || stored !in enabled)) {
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
