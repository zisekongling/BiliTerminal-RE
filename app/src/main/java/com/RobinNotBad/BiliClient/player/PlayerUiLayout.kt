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
 *   中途必然出现单成员组；悄悄拆掉组会让他刚建的组消失）；**0 个也保留**，空组就是「收纳格」
 *   （见下）；
 * - **超出容量** → 截到 [Region.capacity]。
 *
 * ## 收纳格＝空组（26.11.15 统一）
 *
 * 摆进槽位的「组」有两种状态，共用 [Slot.Group] 这一个类型、播放页里也共用同一条渲染与展开路径：
 *
 * - **有成员**：固定展开这 [Slot.Group.keys]，与改造前完全一样；
 * - **没成员（空组）**：自动收纳「注册表里没有摆进任何槽位（含组）的按钮」，即原来那个独立的
 *   「更多」收纳格。
 *
 * 统一的原因：收纳格与组在播放页长得、点着都一样（都是一格 `morehide` 图标 + 一条展开条），
 * 只有在设置页里是两套东西（收纳格不能改成组、组也不能自动收纳，两个入口、两种卡片）。现在
 * 「加一组按钮」就是加一个空组，往里加成员就成了固定组——概念只剩一种，且组里 0 个 / 1 个按钮
 * 都仍然是一个格子，不会被拆散。老存档里独立的 `more` 单格与 `{more}` 组在解析时迁移成空组。
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

    /** 一格内容：单个按钮，或一排可横向滚动的按钮（空组＝收纳格）。 */
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

    /** 已经摆进槽位的全部 key（组内成员算已摆放；空组不贡献任何 key）。 */
    fun placedKeys(layout: Layout): Set<String> {
        val placed = HashSet<String>()
        for (region in Region.values()) {
            for (slot in slots(layout, region)) {
                when (slot) {
                    is Slot.Button -> placed.add(slot.key)
                    is Slot.Group -> placed.addAll(slot.keys)
                }
            }
        }
        return placed
    }

    /**
     * 还没摆进任何槽位的按钮 key，按候选列表（[PlayerButtons.ALL]）顺序——也就是一个**空组
     * （收纳格）展开时该收的东西**。多个空组各自展开的都是这一份清单。
     *
     * 播放页拿它算收纳内容，再按「本播放器里真有这个按钮的真身」过一遍；
     * 设置页只把收纳格当普通的一格展示（成员是动态的，不逐条列出来）。
     */
    fun unplacedKeys(layout: Layout): List<String> {
        val placed = placedKeys(layout)
        return PlayerButtons.ALL.map { it.key }.filter { it !in placed }
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
            // 老存档里独立的「更多」单格：收纳格现在就是「空的一组按钮」，顺手迁移成空组，
            // 不然用户升级后这一格会连同里面的收纳内容一起消失。
            if (token == PlayerButtons.MORE) {
                slots += Slot.Group(emptyList())
                continue
            }
            if (token.startsWith("{")) {
                if (!token.endsWith("}") || token.length < 2) return null
                val rawKeys = token.substring(1, token.length - 1)
                    .split('+')
                    .map { it.trim() }
                val keys = rawKeys
                    .filter { PlayerButtons.isKnown(it) }
                    // 收纳格（空组）不能再被放进组里当成员：它没有真身，组里只会渲染出一个
                    // 点不动的死格子。旧版设置页写出过 `{more}`，这里统一清掉。
                    .filterNot { it == PlayerButtons.MORE }
                    // 同一按钮在组里出现两次会渲染出两个影子，点了互相抢状态，去重。
                    .distinct()
                when {
                    // 有成员 → 固定组，与改造前完全一样（只留 1 个也仍然是组：用户是先建组、
                    // 再往组里加成员的，中途必然出现单成员组，不能悄悄降级成单按钮）。
                    keys.isNotEmpty() -> slots += Slot.Group(keys)
                    // 空组＝收纳格：`{}` 是合法存档，老存档的 `{more}` 也迁到这里。
                    rawKeys.all { it.isEmpty() } || PlayerButtons.MORE in rawKeys ->
                        slots += Slot.Group(emptyList())
                    // 只认识 0 个成员的脏数据（`{nosuch}`）仍然丢掉：别凭空给用户多出一格。
                    else -> Unit
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
