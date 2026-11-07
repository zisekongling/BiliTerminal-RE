package com.RobinNotBad.BiliClient.player

/**
 * 播放器控制按钮的**槽位布局**：左侧 / 右侧 / 底部左 / 底部右四个区域，每格放「一个按钮」或「一个组」。
 *
 * 纯 Kotlin，无 Android 框架依赖，可直接 JVM 单测。
 *
 * ## 存储格式
 *
 * 一个字符串，五段用 `;` 分隔，段内用 `,` 分隔槽位，组用 `{}` 包起来、组内用 `+` 分隔：
 *
 * ```text
 * 1;lock,speed;danmaku,rotate,{danmaku_send+subtitle+loop};quality,sound_cut;sound_add,page_selector
 * ```
 *
 * - 第一段是版本号，当前 [VERSION] = 1；版本不认识 → 整体回退默认；
 * - 段顺序固定为 左 / 右 / 底部左 / 底部右；
 * - 空段表示该区域没有槽位（合法）。
 *
 * **为什么不用 JSON**：仓库里同类的「用户自定义布局」`util/MySpaceConfig.kt` 用的就是分隔符串，
 * 两份配置格式风格保持一致；分隔符串在 JVM 单测里也不需要额外依赖就能逐字验证
 * （`org.json` 在单测里是可用的——`testImplementation 'org.json:json'`——但没必要为四个区域引入它）。
 *
 * ## 容错口径
 *
 * - **结构非法**（段数不对、版本不认识、花括号不配对）→ 整体回退默认布局（与 MySpaceConfig 一致，
 *   宁可回到默认，也不要把用户按钮弄丢一半）；
 * - **单个 key 没登记**（旧版本删掉的按钮、手改脏的数据）→ 只丢这个 key，其余保留；
 * - **组里有效 key 只剩 1 个** → 仍然保留成组，不降级成单按钮（用户是先建组再往里放成员的，
 *   中途必然出现单成员组；悄悄拆掉组会让他刚建的组消失），0 个整组丢弃；
 * - **超出容量** → 截到 [Region.capacity]。
 */
object PlayerUiLayout {

    /** 存储键。 */
    const val KEY = "player_ui_layout"

    /** 当前存储格式版本。 */
    const val VERSION = 1

    /** 布局的四个区域，容量即「最多几格」。 */
    enum class Region(val capacity: Int) {
        LEFT(5),
        RIGHT(5),
        BOTTOM_LEFT(3),
        BOTTOM_RIGHT(3)
    }

    /** 一格内容：单个按钮，或一排可横向滚动的按钮。 */
    sealed class Slot {
        data class Button(val key: String) : Slot()
        data class Group(val keys: List<String>) : Slot()
    }

    /** 一份布局。四个列表都当作不可变用。 */
    data class Layout(
        val left: List<Slot>,
        val right: List<Slot>,
        val bottomLeft: List<Slot>,
        val bottomRight: List<Slot>
    )

    /** 默认布局：左 `[锁定][倍速]`，右 `[弹幕][旋转][第二排 9 个的组]`，底部 `[清晰度][音量减] 播放 [音量加][分P]`。 */
    val DEFAULT: Layout = Layout(
        left = listOf(Slot.Button(PlayerButtons.LOCK), Slot.Button(PlayerButtons.SPEED)),
        right = listOf(
            Slot.Button(PlayerButtons.DANMAKU),
            Slot.Button(PlayerButtons.ROTATE),
            Slot.Group(PlayerButtons.DEFAULT_RIGHT_GROUP)
        ),
        bottomLeft = listOf(Slot.Button(PlayerButtons.QUALITY), Slot.Button(PlayerButtons.SOUND_CUT)),
        bottomRight = listOf(Slot.Button(PlayerButtons.SOUND_ADD), Slot.Button(PlayerButtons.PAGE_SELECTOR))
    )

    /** 默认布局（每次返回同一份不可变数据）。 */
    fun default(): Layout = DEFAULT

    /** 是否就是默认布局（供「恢复默认布局」按钮置灰用）。 */
    fun isDefault(layout: Layout): Boolean = layout == DEFAULT

    // ==================== 区域读写 ====================

    /** 取某区域的槽位列表。 */
    fun slots(layout: Layout, region: Region): List<Slot> = when (region) {
        Region.LEFT -> layout.left
        Region.RIGHT -> layout.right
        Region.BOTTOM_LEFT -> layout.bottomLeft
        Region.BOTTOM_RIGHT -> layout.bottomRight
    }

    /** 换掉某区域的槽位列表。 */
    fun withSlots(layout: Layout, region: Region, slots: List<Slot>): Layout = when (region) {
        Region.LEFT -> layout.copy(left = slots)
        Region.RIGHT -> layout.copy(right = slots)
        Region.BOTTOM_LEFT -> layout.copy(bottomLeft = slots)
        Region.BOTTOM_RIGHT -> layout.copy(bottomRight = slots)
    }

    /** 该区域是否已满。 */
    fun isFull(layout: Layout, region: Region): Boolean = slots(layout, region).size >= region.capacity

    /** 追加一格；满了返回原布局（调用方据此提示）。 */
    fun add(layout: Layout, region: Region, slot: Slot): Layout {
        if (isFull(layout, region)) return layout
        return withSlots(layout, region, slots(layout, region) + slot)
    }

    /** 移除某格；下标越界返回原布局。 */
    fun removeAt(layout: Layout, region: Region, index: Int): Layout {
        val current = slots(layout, region)
        if (index !in current.indices) return layout
        return withSlots(layout, region, current.filterIndexed { i, _ -> i != index })
    }

    /** 替换某格；下标越界返回原布局。 */
    fun replaceAt(layout: Layout, region: Region, index: Int, slot: Slot): Layout {
        val current = slots(layout, region)
        if (index !in current.indices) return layout
        return withSlots(layout, region, current.toMutableList().also { it[index] = slot })
    }

    /** 在同一区域内换顺序（拖动排序用）；下标越界返回原布局。 */
    fun move(layout: Layout, region: Region, from: Int, to: Int): Layout {
        val current = slots(layout, region).toMutableList()
        if (from !in current.indices || to !in current.indices || from == to) return layout
        current.add(to, current.removeAt(from))
        return withSlots(layout, region, current)
    }

    /** 某个 key 是否已经出现在布局里（同一按钮允许放多份，这里只回答「有没有」）。 */
    fun uses(layout: Layout, key: String): Boolean {
        val all = layout.left + layout.right + layout.bottomLeft + layout.bottomRight
        return all.any { slot ->
            when (slot) {
                is Slot.Button -> slot.key == key
                is Slot.Group -> key in slot.keys
            }
        }
    }

    // ==================== 编解码 ====================

    /** 编码成存储串。 */
    fun encode(layout: Layout): String = buildString {
        append(VERSION)
        append(';')
        append(encodeSection(layout.left))
        append(';')
        append(encodeSection(layout.right))
        append(';')
        append(encodeSection(layout.bottomLeft))
        append(';')
        append(encodeSection(layout.bottomRight))
    }

    private fun encodeSection(slots: List<Slot>): String = slots.joinToString(",") { slot ->
        when (slot) {
            is Slot.Button -> slot.key
            is Slot.Group -> "{" + slot.keys.joinToString("+") + "}"
        }
    }

    /** 解析存储串；任何结构性问题都回退 [DEFAULT]。 */
    fun parse(raw: String?): Layout {
        if (raw.isNullOrBlank()) return DEFAULT
        val parts = raw.trim().split(';')
        if (parts.size != 5) return DEFAULT
        if (parts[0].trim().toIntOrNull() != VERSION) return DEFAULT
        val left = parseSection(parts[1]) ?: return DEFAULT
        val right = parseSection(parts[2]) ?: return DEFAULT
        val bottomLeft = parseSection(parts[3]) ?: return DEFAULT
        val bottomRight = parseSection(parts[4]) ?: return DEFAULT
        return Layout(
            left = left.take(Region.LEFT.capacity),
            right = right.take(Region.RIGHT.capacity),
            bottomLeft = bottomLeft.take(Region.BOTTOM_LEFT.capacity),
            bottomRight = bottomRight.take(Region.BOTTOM_RIGHT.capacity)
        )
    }

    /** 解析一段；`null` 表示结构非法（花括号不配对、出现空 token）。 */
    private fun parseSection(raw: String): List<Slot>? {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()
        val slots = ArrayList<Slot>()
        for (tokenRaw in text.split(',')) {
            val token = tokenRaw.trim()
            if (token.isEmpty()) return null
            if (token.startsWith("{")) {
                if (!token.endsWith("}") || token.length < 2) return null
                val keys = token.substring(1, token.length - 1)
                    .split('+')
                    .map { it.trim() }
                    .filter { PlayerButtons.isKnown(it) }
                when (keys.size) {
                    0 -> Unit
                    // 只留 1 个按钮也保留成「组」：用户是先建组、再往组里加成员的，
                    // 中途必然出现单成员组，这时候不能悄悄降级成单按钮。
                    else -> slots += Slot.Group(keys)
                }
            } else {
                if (!PlayerButtons.isKnown(token)) continue
                slots += Slot.Button(token)
            }
        }
        return slots
    }

    // ==================== 存取 ====================

    /**
     * 读布局；读到的东西和「规范化后的结果」不一致就写回。
     *
     * 这样默认布局、旧格式、手改脏的数据都会在第一次读取时被修正成规范串，
     * 下次读就不用再判断。读写都注入成函数，纯逻辑这层不碰 Android。
     */
    fun load(read: (String) -> String?, write: (String, String) -> Unit): Layout {
        val raw = read(KEY)
        val layout = parse(raw)
        val encoded = encode(layout)
        if (raw != encoded) write(KEY, encoded)
        return layout
    }

    /** 保存布局（同时写规范化后的串）。 */
    fun save(layout: Layout, write: (String, String) -> Unit) {
        write(KEY, encode(layout))
    }
}
