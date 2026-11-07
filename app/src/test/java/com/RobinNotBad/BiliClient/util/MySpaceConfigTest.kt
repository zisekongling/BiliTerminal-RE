package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.activity.user.MySpaceMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MySpaceConfigTest {

    private val allMain = MySpaceConfig.serialize(MySpaceConfig.ALL_ITEMS)

    @Test
    fun default_putsEverythingInMainList() {
        assertEquals(MySpaceConfig.ALL_ITEMS, MySpaceConfig.DEFAULT.main)
        assertTrue(MySpaceConfig.DEFAULT.more.isEmpty())
    }

    @Test
    fun allItems_andMenuKeys_match() {
        assertEquals(
            "MySpaceMenu.ITEMS 的 key 必须与 MySpaceConfig.ALL_ITEMS 一致",
            MySpaceConfig.ALL_ITEMS.toSet(),
            MySpaceMenu.ITEMS.map { it.key }.toSet()
        )
        assertEquals("key 不允许重复", MySpaceConfig.ALL_ITEMS.size, MySpaceConfig.ALL_ITEMS.toSet().size)
        assertEquals("图标文案不允许重复定义", MySpaceMenu.ITEMS.size, MySpaceMenu.ITEMS.map { it.key }.toSet().size)
    }

    @Test
    fun parseList_blankIsEmptyList() {
        assertEquals(emptyList<String>(), MySpaceConfig.parseList(null))
        assertEquals(emptyList<String>(), MySpaceConfig.parseList(""))
        assertEquals(emptyList<String>(), MySpaceConfig.parseList("   "))
    }

    @Test
    fun parseList_rejectsUnknownKeyAndDuplicate() {
        assertNull(MySpaceConfig.parseList("follow;bogus"))
        assertNull(MySpaceConfig.parseList("follow;follow"))
    }

    @Test
    fun serialize_parseList_roundTrip() {
        val keys = listOf("follow", "vip", "edit_profile")
        assertEquals("follow;vip;edit_profile", MySpaceConfig.serialize(keys))
        assertEquals(keys, MySpaceConfig.parseList(MySpaceConfig.serialize(keys)))
    }

    @Test
    fun resolve_completeSplit_isAccepted() {
        val main = listOf("follow", "vip")
        val more = MySpaceConfig.ALL_ITEMS - main.toSet()
        val layout = MySpaceConfig.resolve(MySpaceConfig.serialize(main), MySpaceConfig.serialize(more))
        assertEquals(main, layout.main)
        assertEquals(more, layout.more)
    }

    @Test
    fun resolve_allInMore_isAccepted() {
        val layout = MySpaceConfig.resolve("", allMain)
        assertEquals(emptyList<String>(), layout.main)
        assertEquals(MySpaceConfig.ALL_ITEMS, layout.more)
    }

    @Test
    fun resolve_missingItem_isAppendedToMainTail() {
        // 版本升级新增了入口（老用户的存储里没有它）：补到主列表末尾，其余顺序原样保住
        val partial = MySpaceConfig.ALL_ITEMS.drop(1)
        val layout = MySpaceConfig.resolve(MySpaceConfig.serialize(partial), "")
        assertEquals(partial + MySpaceConfig.ALL_ITEMS.first(), layout.main)
        assertEquals(emptyList<String>(), layout.more)
    }

    @Test
    fun resolve_duplicatedAcrossLists_keepsMainCopy() {
        // follow 同时出现在两个列表：主列表优先，从「更多」里去掉，不整份判死
        val layout = MySpaceConfig.resolve(allMain, "follow")
        assertEquals(MySpaceConfig.ALL_ITEMS, layout.main)
        assertEquals(emptyList<String>(), layout.more)
    }

    @Test
    fun parseListLenient_dropsUnknownAndDuplicate() {
        assertEquals(listOf("follow", "vip"), MySpaceConfig.parseListLenient("follow;bogus;vip;follow"))
        assertEquals(emptyList<String>(), MySpaceConfig.parseListLenient(null))
        assertEquals(emptyList<String>(), MySpaceConfig.parseListLenient("bogus;nope"))
    }

    @Test
    fun resolve_neverNull_evenForGarbage() {
        // 归一化永远给出一份合法布局：任何输入都不许把用户布局判死
        val layout = MySpaceConfig.resolve("bogus;follow;follow", ";;;")
        assertEquals("follow", layout.main.first())
        assertEquals(MySpaceConfig.ALL_ITEMS.toSet(), (layout.main + layout.more).toSet())
        assertEquals(MySpaceConfig.ALL_ITEMS.size, layout.main.size + layout.more.size)
    }

    @Test
    fun load_upgradeKeepsCustomOrderAndWritesBack() {
        // 老用户：不在主列表、其余全塞进「更多」，且存储里还没有新入口 subscription
        val oldAll = MySpaceConfig.ALL_ITEMS - "subscription"
        val main = listOf("vip")
        val more = oldAll - main.toSet()
        val written = HashMap<String, String>()
        val result = MySpaceConfig.load(
            readString = { key ->
                when (key) {
                    MySpaceConfig.KEY_MAIN -> MySpaceConfig.serialize(main)
                    MySpaceConfig.KEY_MORE -> MySpaceConfig.serialize(more)
                    else -> null
                }
            },
            writeString = { key, value -> written[key] = value }
        )
        // 自定义分区与顺序原样保留，新入口补在主列表末尾
        assertEquals(listOf("vip", "subscription"), result.main)
        assertEquals(more, result.more)
        // 归一化结果已经落盘，下次读不再重复迁移
        assertEquals(MySpaceConfig.serialize(result.main), written[MySpaceConfig.KEY_MAIN])
        assertEquals(MySpaceConfig.serialize(more), written[MySpaceConfig.KEY_MORE])
    }

    @Test
    fun load_validValue_returnsWithoutWrite() {
        var wrote = false
        val main = listOf("follow", "vip")
        val more = MySpaceConfig.ALL_ITEMS - main.toSet()
        val result = MySpaceConfig.load(
            readString = { key ->
                when (key) {
                    MySpaceConfig.KEY_MAIN -> MySpaceConfig.serialize(main)
                    MySpaceConfig.KEY_MORE -> MySpaceConfig.serialize(more)
                    else -> null
                }
            },
            writeString = { _, _ -> wrote = true }
        )
        assertEquals(main, result.main)
        assertEquals(more, result.more)
        assertEquals(false, wrote)
    }

    @Test
    fun load_missingValue_fallsBackToDefaultAndWritesBack() {
        val written = HashMap<String, String>()
        val result = MySpaceConfig.load(
            readString = { null },
            writeString = { key, value -> written[key] = value }
        )
        assertEquals(MySpaceConfig.DEFAULT, result)
        assertEquals(allMain, written[MySpaceConfig.KEY_MAIN])
        assertEquals("", written[MySpaceConfig.KEY_MORE])
    }

    @Test
    fun load_unknownKey_isDroppedNotReset() {
        // 未知 key 只丢它：跟着它一起躺在那份串里的其它入口必须留住
        val written = HashMap<String, String>()
        val result = MySpaceConfig.load(
            readString = { key -> if (key == MySpaceConfig.KEY_MAIN) "follow;bogus;vip" else "" },
            writeString = { key, value -> written[key] = value }
        )
        assertEquals("follow", result.main.first())
        assertEquals("vip", result.main[1])
        assertEquals(MySpaceConfig.ALL_ITEMS.toSet(), (result.main + result.more).toSet())
        assertEquals(MySpaceConfig.serialize(result.main), written[MySpaceConfig.KEY_MAIN])
    }

    @Test
    fun save_writesBothKeys() {
        val more = MySpaceConfig.ALL_ITEMS - "follow"
        val written = HashMap<String, String>()
        MySpaceConfig.save(
            MySpaceConfig.Layout(listOf("follow"), more),
            { key, value -> written[key] = value }
        )
        assertEquals("follow", written[MySpaceConfig.KEY_MAIN])
        assertEquals(MySpaceConfig.serialize(more), written[MySpaceConfig.KEY_MORE])
    }
}
