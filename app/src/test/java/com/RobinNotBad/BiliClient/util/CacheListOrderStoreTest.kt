package com.RobinNotBad.BiliClient.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 缓存页未分类视频顺序持久化（[CacheListOrderStore]）的单测。
 *
 * 用共享的 [FakeSharedPreferences] 注入 [SharedPreferencesUtil.sharedPreferences]，
 * 不碰真实 SharedPreferences，也不需要 Robolectric。
 */
class CacheListOrderStoreTest {

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = FakeSharedPreferences()
    }

    @After
    fun tearDown() {
        SharedPreferencesUtil.sharedPreferences = null
    }

    @Test
    fun `保存后能原样读回`() {
        CacheListOrderStore.save("/storage/emulated/0/Android/media/com.RobinNotBad.BiliClient/video", listOf("c", "a", "b"))

        assertEquals(
            listOf("c", "a", "b"),
            CacheListOrderStore.load("/storage/emulated/0/Android/media/com.RobinNotBad.BiliClient/video")
        )
    }

    @Test
    fun `没存过的根目录返回空表`() {
        assertTrue(CacheListOrderStore.load("/some/new/root").isEmpty())
    }

    @Test
    fun `不同缓存根目录互不干扰`() {
        // 用户把缓存路径改来改去是常事：两套缓存各自拖出来的顺序都要留着
        CacheListOrderStore.save("/root/a", listOf("1", "2"))
        CacheListOrderStore.save("/root/b", listOf("2", "1"))

        assertEquals(listOf("1", "2"), CacheListOrderStore.load("/root/a"))
        assertEquals(listOf("2", "1"), CacheListOrderStore.load("/root/b"))
    }

    @Test
    fun `空顺序保存后再读回是空表`() {
        CacheListOrderStore.save("/root/a", listOf("1"))
        CacheListOrderStore.save("/root/a", emptyList())

        assertTrue(CacheListOrderStore.load("/root/a").isEmpty())
    }

    @Test
    fun `坏 JSON 回落到空表而不是抛异常`() {
        // 键名 = 前缀 + 根目录；这里模拟旧版本 / 手工改坏的存量数据
        SharedPreferencesUtil.putString("cache_list_order::/root/broken", "{不是数组")

        assertTrue(CacheListOrderStore.load("/root/broken").isEmpty())
    }

    @Test
    fun `读取时过滤空标题`() {
        SharedPreferencesUtil.putString("cache_list_order::/root/blank", "[\"a\",\"\",\"b\"]")

        assertEquals(listOf("a", "b"), CacheListOrderStore.load("/root/blank"))
    }
}
