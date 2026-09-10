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
        assertEquals(main, layout?.main)
        assertEquals(more, layout?.more)
    }

    @Test
    fun resolve_allInMore_isAccepted() {
        val layout = MySpaceConfig.resolve("", allMain)
        assertEquals(emptyList<String>(), layout?.main)
        assertEquals(MySpaceConfig.ALL_ITEMS, layout?.more)
    }

    @Test
    fun resolve_missingItem_isRejected() {
        val partial = MySpaceConfig.serialize(MySpaceConfig.ALL_ITEMS.drop(1))
        assertNull(MySpaceConfig.resolve(partial, ""))
    }

    @Test
    fun resolve_duplicatedAcrossLists_isRejected() {
        // follow 同时出现在两个列表：并集虽然完整，但总数超标
        assertNull(MySpaceConfig.resolve(allMain, "follow"))
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
    fun load_corruptedValue_fallsBackToDefault() {
        val written = HashMap<String, String>()
        val result = MySpaceConfig.load(
            readString = { key -> if (key == MySpaceConfig.KEY_MAIN) "follow;bogus" else "" },
            writeString = { key, value -> written[key] = value }
        )
        assertEquals(MySpaceConfig.DEFAULT, result)
        assertEquals(allMain, written[MySpaceConfig.KEY_MAIN])
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
