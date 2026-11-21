package com.RobinNotBad.BiliClient.util.recommend

import com.RobinNotBad.BiliClient.util.FakeSharedPreferences
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 「屏蔽作者」名单单测：增删、去重、上限、坏数据。 */
class BlockedAuthorStoreTest {

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = FakeSharedPreferences()
        BlockedAuthorStore.clear()
    }

    @After
    fun tearDown() {
        BlockedAuthorStore.clear()
        SharedPreferencesUtil.sharedPreferences = null
    }

    @Test
    fun block_thenUnblock() {
        assertTrue(BlockedAuthorStore.block(11L, " 某UP "))
        val list = BlockedAuthorStore.list()
        assertEquals(1, list.size)
        assertEquals("名字去空白", "某UP", list[0].name)
        assertTrue(BlockedAuthorStore.isBlocked(11L))
        assertTrue(BlockedAuthorStore.mids().contains(11L))

        assertFalse("重复屏蔽返回 false", BlockedAuthorStore.block(11L, "某UP"))
        assertEquals(1, BlockedAuthorStore.list().size)

        assertTrue(BlockedAuthorStore.unblock(11L))
        assertFalse("再删一次没有可删的", BlockedAuthorStore.unblock(11L))
        assertTrue(BlockedAuthorStore.list().isEmpty())
    }

    @Test
    fun block_ignoresZeroMid() {
        assertFalse(BlockedAuthorStore.block(0L, "无名"))
        assertFalse(BlockedAuthorStore.isBlocked(0L))
        assertTrue(BlockedAuthorStore.list().isEmpty())
    }

    @Test
    fun block_capsAtMaxDroppingOldest() {
        for (i in 1..(BlockedAuthorStore.MAX + 2)) {
            BlockedAuthorStore.block(i.toLong(), "UP$i")
        }
        val list = BlockedAuthorStore.list()
        assertEquals(BlockedAuthorStore.MAX, list.size)
        assertEquals("最早的被挤掉", 3L, list.first().mid)
        assertEquals((BlockedAuthorStore.MAX + 2).toLong(), list.last().mid)
    }

    @Test
    fun encodeDecode_roundTripAndBadData() {
        val raw = BlockedAuthorStore.encode(listOf(BlockedAuthor(1L, "a"), BlockedAuthor(2L, "")))
        assertEquals(listOf(BlockedAuthor(1L, "a"), BlockedAuthor(2L, "")), BlockedAuthorStore.decode(raw))
        assertTrue(BlockedAuthorStore.decode(null).isEmpty())
        assertTrue(BlockedAuthorStore.decode("").isEmpty())
        assertTrue(BlockedAuthorStore.decode("{oops").isEmpty())
        assertEquals("坏条目跳过、重复 mid 只留一条",
            listOf(BlockedAuthor(5L, "x")),
            BlockedAuthorStore.decode("[{\"mid\":5,\"name\":\"x\"},{\"name\":\"无mid\"},{\"mid\":5,\"name\":\"y\"}]"))
    }

    @Test
    fun clear_emptiesList() {
        BlockedAuthorStore.block(1L, "a")
        BlockedAuthorStore.clear()
        assertTrue(BlockedAuthorStore.list().isEmpty())
    }
}
