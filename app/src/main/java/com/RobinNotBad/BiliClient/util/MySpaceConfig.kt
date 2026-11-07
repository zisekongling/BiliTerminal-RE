package com.RobinNotBad.BiliClient.util

/**
 * 「我的」页面（MySpaceActivity）功能入口的排序 / 分区配置。
 *
 * 单一数据源是两份分号连接的有序 key 串：
 * - `myspace_main`：主列表顺序；
 * - `myspace_more`：更多列表顺序。
 *
 * 不变式：两份列表互斥，且并集恰好等于 [ALL_ITEMS]。
 *
 * 固定项不参与存储：用户卡片固定在最顶部，[KEY_MORE_BUTTON]（更多按钮）与
 * [KEY_LOGOUT]（退出登录）永远排在最后两位。
 *
 * <h3>容错口径：逐级放宽，绝不为了一处问题把整份布局清零</h3>
 *
 * - 未知 key（对应功能已删）：只丢它；
 * - 跨列表重复：以主列表为准，从「更多」里去掉；
 * - 漏项（**版本升级新增了入口**）：按 [ALL_ITEMS] 的顺序追加到主列表末尾；
 * - 归一化结果与存储值不同就写回一次，老用户的自定义顺序与分区留住。
 *
 * 26.11.07 之前漏项走的是「整体回退默认」：那一版给 [ALL_ITEMS] 加了 `subscription`，
 * 于是所有把入口挪进「更多」列表的老用户一升级就被静默重置成默认布局（自定义顺序与
 * 分区一起没了）。仓里 `MenuConfig` 加 `exit` 时用的是同一套思路，两份配置现在口径一致。
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
        "follow", "watch_later", "favorite", "subscription", "bangumi", "history",
        "creative", "vip", "login_record", "coin_log", "exp_log", "edit_profile"
    )

    /** 一份解析后的布局。 */
    data class Layout(val main: List<String>, val more: List<String>)

    /** 默认布局：全部功能在主列表，「更多」列表为空。 */
    val DEFAULT: Layout = Layout(ALL_ITEMS, emptyList())

    fun serialize(keys: List<String>): String = keys.joinToString(";")

    /**
     * 严格解析：出现未知 key 或重复返回 null。
     *
     * 只用于「要写进存储前」的自我校验；读配置请用 [resolve]（它不会把老用户的布局判死）。
     */
    fun parseList(raw: String?): List<String>? {
        val keys = splitRaw(raw)
        val seen = HashSet<String>()
        for (key in keys) {
            if (key !in ALL_ITEMS || !seen.add(key)) return null
        }
        return keys
    }

    /** 容错解析：丢掉未知 key 与重复项（重复只保留首次出现的位置），其余顺序不动。 */
    fun parseListLenient(raw: String?): List<String> {
        val seen = HashSet<String>()
        return splitRaw(raw).filter { it in ALL_ITEMS && seen.add(it) }
    }

    private fun splitRaw(raw: String?): List<String> =
        raw?.split(';')?.filter { it.isNotBlank() } ?: emptyList()

    /**
     * 归一化两份列表：主列表优先去重，漏项按 [ALL_ITEMS] 顺序补到主列表末尾。
     *
     * 不返回 null——任何输入都能归一化出一份合法布局（最差就是 [DEFAULT]）。
     * 读到一对互不重复、并集完整的值时原样返回，所以正常配置不会被改动。
     */
    fun resolve(mainRaw: String?, moreRaw: String?): Layout {
        val main = parseListLenient(mainRaw).toMutableList()
        val inMain = HashSet(main)
        val more = parseListLenient(moreRaw).filter { inMain.add(it) }
        val union = HashSet(main)
        union.addAll(more)
        main += ALL_ITEMS.filter { it !in union }
        return Layout(main, more)
    }

    /** 读取布局；与归一化结果不同就写回一次（补新入口、丢未知 key），绝不清零。 */
    fun load(readString: (String) -> String?, writeString: (String, String) -> Unit): Layout {
        val mainRaw = readString(KEY_MAIN)
        val moreRaw = readString(KEY_MORE)
        val layout = resolve(mainRaw, moreRaw)
        if (serialize(layout.main) != mainRaw.orEmpty() || serialize(layout.more) != moreRaw.orEmpty()) {
            save(layout, writeString)
        }
        return layout
    }

    /** 保存布局。 */
    fun save(layout: Layout, writeString: (String, String) -> Unit) {
        writeString(KEY_MAIN, serialize(layout.main))
        writeString(KEY_MORE, serialize(layout.more))
    }
}
