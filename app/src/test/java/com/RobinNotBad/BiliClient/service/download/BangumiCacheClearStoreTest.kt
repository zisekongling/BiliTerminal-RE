package com.RobinNotBad.BiliClient.service.download

import com.RobinNotBad.BiliClient.util.FakeSharedPreferences
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 「长按缓存按钮强制清除番剧缓存状态」记号表（[BangumiCacheClearStore]）的单测。
 *
 * 用共享的 [FakeSharedPreferences] 注入 [SharedPreferencesUtil.sharedPreferences]，
 * 不碰真实 SharedPreferences，也不需要 Robolectric。
 *
 * 这里钉的是三条容易改坏的约定：清除是**累加**的、入队后**只撤销入队的那几季**、
 * 坏数据只能让这一条功能失效（不能让缓存页打不开）。
 */
class BangumiCacheClearStoreTest {

    /** 与 BangumiCacheClearStore 里的私有常量同值；测试要能确认「撤销干净后键被删掉」。 */
    private val key = "bangumi_cache_cleared_seasons"

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = FakeSharedPreferences()
    }

    @After
    fun tearDown() {
        SharedPreferencesUtil.sharedPreferences = null
    }

    @Test
    fun `没清过的时候是空的`() {
        assertTrue(BangumiCacheClearStore.load().isEmpty())
        assertFalse(BangumiCacheClearStore.isCleared(12345L))
    }

    @Test
    fun `清除后那一季算清过，别的季不受影响`() {
        BangumiCacheClearStore.clear(listOf(100L))

        assertTrue(BangumiCacheClearStore.isCleared(100L))
        assertFalse(BangumiCacheClearStore.isCleared(200L))
        assertEquals(setOf(100L), BangumiCacheClearStore.load())
    }

    @Test
    fun `重复清除同一季只记一条`() {
        BangumiCacheClearStore.clear(listOf(100L))
        BangumiCacheClearStore.clear(listOf(100L))

        assertEquals(setOf(100L), BangumiCacheClearStore.load())
    }

    @Test
    fun `多次清除会累加（整部番的所有季一起清）`() {
        BangumiCacheClearStore.clear(listOf(100L, 101L))
        BangumiCacheClearStore.clear(listOf(102L))

        assertEquals(setOf(100L, 101L, 102L), BangumiCacheClearStore.load())
    }

    @Test
    fun `入队后只撤销入队的那几季`() {
        BangumiCacheClearStore.clear(listOf(100L, 101L, 102L))

        BangumiCacheClearStore.restore(listOf(100L))

        assertEquals(setOf(101L, 102L), BangumiCacheClearStore.load())
        assertFalse(BangumiCacheClearStore.isCleared(100L))
    }

    @Test
    fun `撤销不存在的季不会写坏数据`() {
        BangumiCacheClearStore.clear(listOf(100L))

        BangumiCacheClearStore.restore(listOf(999L))

        assertEquals(setOf(100L), BangumiCacheClearStore.load())
    }

    @Test
    fun `全部撤销后键被删掉`() {
        BangumiCacheClearStore.clear(listOf(100L, 101L))

        BangumiCacheClearStore.restore(listOf(100L, 101L))

        assertTrue(BangumiCacheClearStore.load().isEmpty())
        // 键不删的话会一直留着一个 "[]"，属于无意义写入
        assertNull(SharedPreferencesUtil.sharedPreferences.getString(key, null))
    }

    @Test
    fun `坏数据按没清过处理`() {
        // 旧版本 / 手工改坏的数据：只该让这一条功能失效，不该让缓存页打不开
        SharedPreferencesUtil.putString(key, "这不是 JSON")

        assertTrue(BangumiCacheClearStore.load().isEmpty())
    }

    @Test
    fun `非正数的季 id 不会写进去`() {
        BangumiCacheClearStore.clear(listOf(0L, -5L))

        assertTrue(BangumiCacheClearStore.load().isEmpty())
        assertFalse(BangumiCacheClearStore.isCleared(0L))
    }
}
