package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlayerUiLayout]（播放器按钮槽位布局）的纯 JVM 单测。
 *
 * 这里钉住的是几条**一旦破了就会让用户按钮静默消失**的口径：
 * 1. 结构非法整体回退默认（宁可回默认，也不要丢一半）；
 * 2. 单个未知 key 只丢自己，不连坐（旧版本删按钮、手改脏数据都会走到这条路）；
 * 3. 组里全部 key 都不认识才丢整组；只剩 1 个也保留成组（建组中途的合法状态）；
 * 4. 超容量截断，不能让脏数据把 5 格变 50 格；
 * 5. 默认布局能原样往返编解码——[DEFAULT_SERIALIZED] 只是「落盘格式快照」（写回路径的用例对照它），
 *    真正的行为断言是 encode 之后还能 parse 回同一份布局。
 */
class PlayerUiLayoutTest {

    private val layout = PlayerUiLayout

    companion object {
        /** 默认布局的规范编码串（改格式或改默认布局都必须同步这里）。 */
        const val DEFAULT_SERIALIZED =
            "1;lock,speed;danmaku,rotate," +
                "{danmaku_send+subtitle+loop+audio_only+auto_next+viewpoint+video_info+jump_time+queue_mode};" +
                "quality,sound_cut;sound_add,page_selector"
    }

    // ==================== 默认布局 ====================

    @Test
    fun `default matches confirmed layout`() {
        val d = layout.default()
        assertEquals(
            listOf<PlayerUiLayout.Slot>(
                PlayerUiLayout.Slot.Button("lock"),
                PlayerUiLayout.Slot.Button("speed")
            ),
            d.left
        )
        assertEquals(3, d.right.size)
        assertEquals(PlayerUiLayout.Slot.Button("danmaku"), d.right[0])
        assertEquals(PlayerUiLayout.Slot.Button("rotate"), d.right[1])
        assertEquals(PlayerUiLayout.Slot.Group(PlayerButtons.DEFAULT_RIGHT_GROUP), d.right[2])
        assertEquals(
            listOf<PlayerUiLayout.Slot>(
                PlayerUiLayout.Slot.Button("quality"),
                PlayerUiLayout.Slot.Button("sound_cut")
            ),
            d.bottomLeft
        )
        assertEquals(
            listOf<PlayerUiLayout.Slot>(
                PlayerUiLayout.Slot.Button("sound_add"),
                PlayerUiLayout.Slot.Button("page_selector")
            ),
            d.bottomRight
        )
    }

    @Test
    fun `default layout survives encode parse round trip`() {
        // 行为口径：encode 出来的串必须能原样 parse 回去（默认布局里含一个组，正好覆盖组的编解码）。
        // 落盘串的具体字面量见 [DEFAULT_SERIALIZED]，那是格式快照，不是行为断言。
        val d = layout.default()
        assertEquals(d, layout.parse(layout.encode(d)))
    }

    @Test
    fun `default group holds the old second row in order`() {
        val group = layout.default().right[2] as PlayerUiLayout.Slot.Group
        assertEquals(
            listOf(
                "danmaku_send", "subtitle", "loop", "audio_only", "auto_next",
                "viewpoint", "video_info", "jump_time", "queue_mode"
            ),
            group.keys
        )
    }

    @Test
    fun `isDefault only true for exact default`() {
        assertTrue(layout.isDefault(layout.default()))
        assertFalse(layout.isDefault(layout.removeAt(layout.default(), PlayerUiLayout.Region.LEFT, 0)))
    }

    // ==================== 编解码 ====================

    @Test
    fun `parse of missing or blank falls back to default`() {
        assertSame(layout.default(), layout.parse(null))
        assertSame(layout.default(), layout.parse(""))
        assertSame(layout.default(), layout.parse("   "))
    }

    @Test
    fun `round trip keeps groups and order`() {
        val custom = PlayerUiLayout.Layout(
            left = listOf(PlayerUiLayout.Slot.Button("speed"), PlayerUiLayout.Slot.Group(listOf("loop", "auto_next"))),
            right = emptyList(),
            bottomLeft = listOf(PlayerUiLayout.Slot.Button("quality")),
            bottomRight = listOf(PlayerUiLayout.Slot.Button("sound_add"), PlayerUiLayout.Slot.Button("sound_add"))
        )
        assertEquals(custom, layout.parse(layout.encode(custom)))
    }

    @Test
    fun `empty sections are legal`() {
        val empty = PlayerUiLayout.Layout(emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(empty, layout.parse(layout.encode(empty)))
        assertEquals("1;;;;", layout.encode(empty))
    }

    // ==================== 结构非法 → 整体回退默认 ====================

    @Test
    fun `wrong section count falls back to default`() {
        assertSame(layout.default(), layout.parse("1;lock,speed;danmaku"))
        assertSame(layout.default(), layout.parse("1;lock,speed;danmaku;quality;sound_add;extra"))
    }

    @Test
    fun `unknown version falls back to default`() {
        assertSame(layout.default(), layout.parse("2;lock;;;"))
        assertSame(layout.default(), layout.parse(";lock;;;"))
        assertSame(layout.default(), layout.parse("v1;lock;;;"))
    }

    @Test
    fun `unbalanced braces fall back to default`() {
        assertSame(layout.default(), layout.parse("1;{lock,speed;;;"))
        assertSame(layout.default(), layout.parse("1;{lock;;;"))
        assertSame(layout.default(), layout.parse("1;{;;;"))
    }

    @Test
    fun `empty token falls back to default`() {
        assertSame(layout.default(), layout.parse("1;lock,,speed;;;"))
        assertSame(layout.default(), layout.parse("1;,lock;;;"))
    }

    // ==================== 脏数据只丢自己 ====================

    @Test
    fun `unknown key is dropped alone`() {
        val parsed = layout.parse("1;lock,nosuch,speed;;;")
        assertEquals(
            listOf<PlayerUiLayout.Slot>(
                PlayerUiLayout.Slot.Button("lock"),
                PlayerUiLayout.Slot.Button("speed")
            ),
            parsed.left
        )
    }

    @Test
    fun `group with single known key stays a group`() {
        // 用户是先建组再往里加成员的，单成员组是合法中间态
        val parsed = layout.parse("1;{loop};;;")
        assertEquals(listOf<PlayerUiLayout.Slot>(PlayerUiLayout.Slot.Group(listOf("loop"))), parsed.left)
    }

    @Test
    fun `group with no known key is dropped`() {
        val parsed = layout.parse("1;{nosuch},{loop+auto_next};;;")
        assertEquals(
            listOf<PlayerUiLayout.Slot>(PlayerUiLayout.Slot.Group(listOf("loop", "auto_next"))),
            parsed.left
        )
    }

    @Test
    fun `group keeps known keys only`() {
        val parsed = layout.parse("1;{loop+nosuch+auto_next};;;")
        assertEquals(
            listOf<PlayerUiLayout.Slot>(PlayerUiLayout.Slot.Group(listOf("loop", "auto_next"))),
            parsed.left
        )
    }

    @Test
    fun `overflow is truncated from the tail`() {
        // 只断 size 是不够的：从**头部**截断同样能得到正确长度，却会把用户排在前面的按钮全砍掉。
        // 这里把「保住靠前的、从尾部丢弃」这条口径连具体槽位一起钉住。
        val sixButtons = "lock,speed,danmaku,rotate,subtitle,loop"
        assertEquals(
            listOf<PlayerUiLayout.Slot>(
                PlayerUiLayout.Slot.Button("lock"),
                PlayerUiLayout.Slot.Button("speed"),
                PlayerUiLayout.Slot.Button("danmaku"),
                PlayerUiLayout.Slot.Button("rotate"),
                PlayerUiLayout.Slot.Button("subtitle")
            ),
            layout.parse("1;$sixButtons;;;").left
        )
        val fiveBottom = "quality,sound_cut,sound_add,page_selector,lock"
        assertEquals(
            listOf<PlayerUiLayout.Slot>(
                PlayerUiLayout.Slot.Button("quality"),
                PlayerUiLayout.Slot.Button("sound_cut"),
                PlayerUiLayout.Slot.Button("sound_add")
            ),
            layout.parse("1;;;$fiveBottom;").bottomLeft
        )
    }

    // ==================== 区域操作 ====================

    @Test
    fun `add appends and stops at capacity`() {
        var current = layout.withSlots(
            layout.default(), PlayerUiLayout.Region.BOTTOM_LEFT, emptyList()
        )
        repeat(PlayerUiLayout.Region.BOTTOM_LEFT.capacity) {
            current = layout.add(current, PlayerUiLayout.Region.BOTTOM_LEFT, PlayerUiLayout.Slot.Button("loop"))
        }
        assertEquals(3, layout.slots(current, PlayerUiLayout.Region.BOTTOM_LEFT).size)
        assertTrue(layout.isFull(current, PlayerUiLayout.Region.BOTTOM_LEFT))
        // 满了之后再 add 原样返回
        val full = current
        assertSame(full, layout.add(full, PlayerUiLayout.Region.BOTTOM_LEFT, PlayerUiLayout.Slot.Button("lock")))
    }

    @Test
    fun `duplicate buttons are allowed`() {
        val twice = layout.add(layout.default(), PlayerUiLayout.Region.LEFT, PlayerUiLayout.Slot.Button("lock"))
        assertEquals(2, twice.left.count { it == PlayerUiLayout.Slot.Button("lock") })
        assertEquals(twice, layout.parse(layout.encode(twice)))
    }

    @Test
    fun `removeAt out of range returns same layout`() {
        val d = layout.default()
        assertSame(d, layout.removeAt(d, PlayerUiLayout.Region.LEFT, 5))
        assertSame(d, layout.removeAt(d, PlayerUiLayout.Region.LEFT, -1))
    }

    @Test
    fun `replaceAt swaps one slot`() {
        val replaced = layout.replaceAt(
            layout.default(), PlayerUiLayout.Region.LEFT, 1, PlayerUiLayout.Slot.Button("danmaku")
        )
        assertEquals(
            listOf<PlayerUiLayout.Slot>(
                PlayerUiLayout.Slot.Button("lock"),
                PlayerUiLayout.Slot.Button("danmaku")
            ),
            replaced.left
        )
        assertSame(layout.default(), layout.replaceAt(layout.default(), PlayerUiLayout.Region.LEFT, 9, PlayerUiLayout.Slot.Button("lock")))
    }

    @Test
    fun `move reorders within region`() {
        val moved = layout.move(layout.default(), PlayerUiLayout.Region.RIGHT, 2, 0)
        assertEquals(PlayerUiLayout.Slot.Button("danmaku"), moved.right[1])
        assertEquals(PlayerUiLayout.Slot.Button("rotate"), moved.right[2])
        assertTrue(moved.right[0] is PlayerUiLayout.Slot.Group)
        assertSame(layout.default(), layout.move(layout.default(), PlayerUiLayout.Region.RIGHT, 2, 9))
    }

    @Test
    fun `uses sees buttons inside groups`() {
        val d = layout.default()
        assertTrue(layout.uses(d, "lock"))
        assertTrue(layout.uses(d, "jump_time"))
        assertFalse(layout.uses(d, "nosuch"))
    }

    // ==================== 存取 ====================

    @Test
    fun `load normalizes dirty raw and writes back`() {
        val writes = HashMap<String, String>()
        val stored = "1;lock,nosuch,speed;;;"
        val loaded = layout.load({ stored }, { k, v -> writes[k] = v })
        assertEquals(listOf<PlayerUiLayout.Slot>(PlayerUiLayout.Slot.Button("lock"), PlayerUiLayout.Slot.Button("speed")), loaded.left)
        assertEquals("1;lock,speed;;;", writes[PlayerUiLayout.KEY])
    }

    @Test
    fun `load of missing key writes default`() {
        val writes = HashMap<String, String>()
        val loaded = layout.load({ null }, { k, v -> writes[k] = v })
        assertSame(layout.default(), loaded)
        assertEquals(DEFAULT_SERIALIZED, writes[PlayerUiLayout.KEY])
    }

    @Test
    fun `load of already canonical raw does not write`() {
        var wrote = false
        layout.load({ DEFAULT_SERIALIZED }, { _, _ -> wrote = true })
        assertFalse(wrote)
    }

    @Test
    fun `save writes canonical string`() {
        val writes = HashMap<String, String>()
        layout.save(layout.default(), { k, v -> writes[k] = v })
        assertEquals(DEFAULT_SERIALIZED, writes[PlayerUiLayout.KEY])
    }

    // ==================== 注册表自检 ====================

    @Test
    fun `default layout only uses registered keys`() {
        val d = layout.default()
        val keys = buildList {
            d.left.forEach { add(it) }
            d.right.forEach { add(it) }
            d.bottomLeft.forEach { add(it) }
            d.bottomRight.forEach { add(it) }
        }
        for (slot in keys) {
            when (slot) {
                is PlayerUiLayout.Slot.Button -> assertTrue(slot.key, PlayerButtons.isKnown(slot.key))
                is PlayerUiLayout.Slot.Group -> slot.keys.forEach { assertTrue(it, PlayerButtons.isKnown(it)) }
            }
        }
    }

    @Test
    fun `registry keys are unique`() {
        // 重名会让 byKey 静默吞掉后一个按钮：布局里放出来的其实是另一个按钮，用户找不到它。
        val keys = PlayerButtons.ALL.map { it.key }
        assertEquals("候选列表里不能有重名 key：$keys", keys.size, keys.toSet().size)
    }

    @Test
    fun `every registered key survives a layout round trip`() {
        // 比「key 全小写」更有行为意义的口径：注册表里的每个 key 都必须能编进布局串再原样解析回来。
        // key 里混进分隔符（; , + {}）、带首尾空格、大小写与注册表不一致，都会在这里被抓住——
        // 在界面上表现为「点了添加按钮，布局里却什么都没出现」。
        PlayerButtons.ALL.forEach { button ->
            val single = PlayerUiLayout.Layout(
                left = listOf(PlayerUiLayout.Slot.Button(button.key)),
                right = emptyList(),
                bottomLeft = emptyList(),
                bottomRight = emptyList()
            )
            assertEquals(button.key, single, layout.parse(layout.encode(single)))
        }
    }

    @Test
    fun `registry has no separator characters in keys`() {
        // 编码格式用 ; , + {} 做分隔，key 里出现任何一个都会把布局切坏
        val forbidden = charArrayOf(';', ',', '+', '{', '}')
        PlayerButtons.ALL.forEach { button ->
            forbidden.forEach { c -> assertFalse(button.key, button.key.contains(c)) }
        }
    }

    @Test
    fun `byKey returns null for unknown`() {
        assertNull(PlayerButtons.byKey("nosuch"))
        assertEquals(PlayerButtons.QUALITY, PlayerButtons.byKey("quality")?.key)
        assertEquals(PlayerButtons.Action.OPEN_QUALITY, PlayerButtons.byKey("quality")?.action)
        assertEquals(PlayerSettingsGroups.DANMAKU, PlayerButtons.byKey("danmaku")?.settingsGroup)
    }
}
