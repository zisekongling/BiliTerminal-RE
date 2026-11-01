package com.RobinNotBad.BiliClient.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 「虚拟合集」拆成两个功能后的默认值、键名稳定性与点击行为判定。
 *
 * 钉住三件**不会崩、只会静默错**的事：
 *
 * 1. **功能二（点卡片直接播）默认关闭**——用户拍板的口径是「点卡片回视频详情页」；
 *    默认值一旦写错，装上就是「点一下卡片直接进播放器」，跟改版前一样突兀，且没有任何报错。
 * 2. **键名不能被改掉**——改了等于把老用户的设置读成「没设过」，
 *    功能一会被默认值重新打开、功能二会丢掉用户显式打开过的值。
 * 3. **点击判定要覆盖 4 种开关组合 + 多选态**——这是「两个功能各自可关」的全部语义，
 *    组合判反了不会崩，只会表现成「开关关了还按开着走」。
 */
class VirtualCollectionPrefsTest {

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = FakeSharedPreferences()
    }

    @After
    fun tearDown() {
        SharedPreferencesUtil.sharedPreferences = null
    }

    // ---------- 默认值 ----------

    @Test
    fun 默认值是功能一开功能二关() {
        assertTrue("虚拟合集（组合集）必须默认开启", VirtualCollectionPrefs.DEFAULT_ENABLE)
        assertFalse("点卡片直接播必须默认关闭", VirtualCollectionPrefs.DEFAULT_CLICK_PLAY)
        assertTrue("从未存过时功能一应为开启", VirtualCollectionPrefs.collectionEnabled())
        assertFalse("从未存过时功能二应为关闭", VirtualCollectionPrefs.clickPlayEnabled())
    }

    @Test
    fun 老用户显式关掉功能一之后仍然是关闭() {
        SharedPreferencesUtil.putBoolean(VirtualCollectionPrefs.ENABLE, false)
        assertFalse("用户显式关过的设置不能被默认值覆盖掉", VirtualCollectionPrefs.collectionEnabled())
        assertFalse("功能二的默认值不受功能一存档影响", VirtualCollectionPrefs.clickPlayEnabled())
    }

    @Test
    fun 显式打开功能二之后生效() {
        SharedPreferencesUtil.putBoolean(VirtualCollectionPrefs.CLICK_PLAY, true)
        assertTrue(VirtualCollectionPrefs.clickPlayEnabled())
    }

    /** 键名不能被改掉：改了等于把老用户的设置读成「没设过」。 */
    @Test
    fun 键名是稳定的() {
        assertEquals("virtual_collection_enable", VirtualCollectionPrefs.ENABLE)
        assertEquals("virtual_collection_click_play", VirtualCollectionPrefs.CLICK_PLAY)
    }

    // ---------- 收藏夹点击判定 ----------

    @Test
    fun 多选态优先于两个开关() {
        val cases = listOf(
            Triple(true, true, true),
            Triple(true, true, false),
            Triple(true, false, true),
            Triple(true, false, false),
        )
        for ((selection, collection, clickPlay) in cases) {
            assertEquals(
                "多选态下点条目只该勾选，与开关无关",
                VirtualCollectionPrefs.ClickAction.SELECT,
                VirtualCollectionPrefs.favoriteClickAction(selection, collection, clickPlay)
            )
        }
    }

    @Test
    fun 功能二关闭时点卡片进详情页() {
        assertEquals(
            "功能二关、功能一开：点卡片应进视频详情页，合集走长按菜单",
            VirtualCollectionPrefs.ClickAction.OPEN_DETAIL,
            VirtualCollectionPrefs.favoriteClickAction(
                selectionMode = false, collectionEnabled = true, clickPlayEnabled = false
            )
        )
        assertEquals(
            "两个都关：回到默认的视频详情页",
            VirtualCollectionPrefs.ClickAction.OPEN_DETAIL,
            VirtualCollectionPrefs.favoriteClickAction(
                selectionMode = false, collectionEnabled = false, clickPlayEnabled = false
            )
        )
    }

    @Test
    fun 功能二开启且功能一开启时整夹连播() {
        assertEquals(
            VirtualCollectionPrefs.ClickAction.PLAY_COLLECTION,
            VirtualCollectionPrefs.favoriteClickAction(
                selectionMode = false, collectionEnabled = true, clickPlayEnabled = true
            )
        )
    }

    @Test
    fun 功能二开启但功能一关闭时只播这一条() {
        assertEquals(
            "功能一只负责组合集，关掉后点卡片仍可直接播，只是不组列表",
            VirtualCollectionPrefs.ClickAction.PLAY_SINGLE,
            VirtualCollectionPrefs.favoriteClickAction(
                selectionMode = false, collectionEnabled = false, clickPlayEnabled = true
            )
        )
    }

    // ---------- 缓存页点击判定 ----------

    @Test
    fun 缓存文件夹内功能一开启时整夹连播() {
        assertEquals(
            VirtualCollectionPrefs.ClickAction.PLAY_COLLECTION,
            VirtualCollectionPrefs.cacheClickAction(inFolder = true, collectionEnabled = true)
        )
    }

    @Test
    fun 缓存页未进文件夹或功能一关闭时播单条() {
        assertEquals(
            "根目录（未分类视频）没有整夹的概念",
            VirtualCollectionPrefs.ClickAction.PLAY_SINGLE,
            VirtualCollectionPrefs.cacheClickAction(inFolder = false, collectionEnabled = true)
        )
        assertEquals(
            "功能一关闭后点文件夹里的视频也只播这一条",
            VirtualCollectionPrefs.ClickAction.PLAY_SINGLE,
            VirtualCollectionPrefs.cacheClickAction(inFolder = true, collectionEnabled = false)
        )
    }
}
