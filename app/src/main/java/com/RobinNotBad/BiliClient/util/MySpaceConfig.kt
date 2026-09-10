package com.RobinNotBad.BiliClient.util

/**
 * 「我的」页面（MySpaceActivity）功能入口的排序 / 分区配置。
 *
 * 单一数据源是两份分号连接的有序 key 串：
 * - `myspace_main`：主列表顺序；
 * - `myspace_more`：更多列表顺序。
 *
 * 不变式：两份列表互斥，且并集恰好等于 [ALL_ITEMS]。任一非法（未知 key、重复、漏项）
 * 就整体回退默认并写回，避免半截配置把功能入口弄丢。
 *
 * 固定项不参与存储：用户卡片固定在最顶部，[KEY_MORE_BUTTON]（更多按钮）与
 * [KEY_LOGOUT]（退出登录）永远排在最后两位。
 *
 * 纯 Kotlin 对象，无 Android 框架依赖，可 JVM 单测。
 */
object MySpaceConfig {

    /** 主列表存储键。 */
    const val KEY_MAIN = "myspace_main"

    /** 更多列表存储键。 */
    const val KEY_MORE = "myspace_more"

    /** 「更多」按钮的固定 key，不参与存储。 */
    const val KEY_MORE_BUTTON = "more"

    /** 「退出登录」的固定 key，不参与存储。 */
    const val KEY_LOGOUT = "logout"

    /** 全部可配置的功能入口 key，顺序即默认主列表顺序。与 `MySpaceMenu.ITEMS` 一一对应。 */
    val ALL_ITEMS: List<String> = listOf(
        "follow", "watch_later", "favorite", "bangumi", "history",
        "creative", "vip", "login_record", "coin_log", "exp_log", "edit_profile"
    )

    /** 一份解析后的布局。 */
    data class Layout(val main: List<String>, val more: List<String>)

    /** 默认布局：全部功能在主列表，「更多」列表为空。 */
    val DEFAULT: Layout = Layout(ALL_ITEMS, emptyList())

    fun serialize(keys: List<String>): String = keys.joinToString(";")

    /** 解析单个列表串；空白视为空列表（合法），出现未知 key 或重复返回 null。 */
    fun parseList(raw: String?): List<String>? {
        val keys = raw?.split(";")?.filter { it.isNotBlank() } ?: emptyList()
        val seen = HashSet<String>()
        for (key in keys) {
            if (key !in ALL_ITEMS || !seen.add(key)) return null
        }
        return keys
    }

    /** 解析两份列表；必须互斥且并集完整，否则返回 null。 */
    fun resolve(mainRaw: String?, moreRaw: String?): Layout? {
        val main = parseList(mainRaw) ?: return null
        val more = parseList(moreRaw) ?: return null
        // 总数不多不少：多 = 跨列表重复，少 = 漏项
        if (main.size + more.size != ALL_ITEMS.size) return null
        val union = HashSet(main)
        union.addAll(more)
        if (union.size != ALL_ITEMS.size) return null
        return Layout(main, more)
    }

    /** 读取布局；缺失或非法时回退默认并写回。 */
    fun load(readString: (String) -> String?, writeString: (String, String) -> Unit): Layout {
        val resolved = resolve(readString(KEY_MAIN), readString(KEY_MORE))
        if (resolved != null) return resolved
        save(DEFAULT, writeString)
        return DEFAULT
    }

    /** 保存布局。 */
    fun save(layout: Layout, writeString: (String, String) -> Unit) {
        writeString(KEY_MAIN, serialize(layout.main))
        writeString(KEY_MORE, serialize(layout.more))
    }
}
