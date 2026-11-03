package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.LocalVideo
import com.RobinNotBad.BiliClient.model.VideoFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 缓存页（LocalListActivity / CacheListAdapter）排序 + 搜索纯逻辑的单测。
 *
 * 这套逻辑特意做成不碰 Android 的纯函数，所以这里直接在 JVM 上跑，不需要 Robolectric；
 * 覆盖的是「排序档位 ↔ 方向 ↔ 持久化短名」的映射、稳定排序、文件夹聚合值与搜索匹配。
 */
class LocalListSortTest {

    // 用 also + 显式接收者赋值：apply 里成员属性和同名参数谁优先容易看错，这里不赌解析顺序
    private fun video(
        title: String,
        size: Long = 0L,
        lastModified: Long = 0L,
        folder: String = ""
    ): LocalVideo = LocalVideo().also { v ->
        v.title = title
        v.size = size
        v.lastModified = lastModified
        v.folderName = folder
    }

    private fun folder(name: String, createTime: Long = 0L): VideoFolder = VideoFolder().also { f ->
        f.name = name
        f.createTime = createTime
    }

    // ==================== 视频排序 ====================

    @Test
    fun `默认档保持扫描顺序_降序时整体反转`() {
        val videos = listOf(video("c"), video("a"), video("b"))

        assertEquals(
            listOf(0, 1, 2),
            LocalListSort.videoOrder(videos, LocalListSort.Key.DEFAULT, true)
        )
        assertEquals(
            listOf(2, 1, 0),
            LocalListSort.videoOrder(videos, LocalListSort.Key.DEFAULT, false)
        )
    }

    @Test
    fun `按标题升序忽略大小写`() {
        val videos = listOf(video("beta"), video("Alpha"), video("Gamma"))

        assertEquals(
            listOf(1, 0, 2),
            LocalListSort.videoOrder(videos, LocalListSort.Key.TITLE, true)
        )
    }

    @Test
    fun `按标题降序`() {
        val videos = listOf(video("beta"), video("Alpha"), video("Gamma"))

        assertEquals(
            listOf(2, 0, 1),
            LocalListSort.videoOrder(videos, LocalListSort.Key.TITLE, false)
        )
    }

    @Test
    fun `按更新时间_新到旧与旧到新`() {
        val videos = listOf(
            video("a", lastModified = 100),
            video("b", lastModified = 300),
            video("c", lastModified = 200)
        )

        assertEquals(
            listOf(1, 2, 0),
            LocalListSort.videoOrder(videos, LocalListSort.Key.TIME, false)
        )
        assertEquals(
            listOf(0, 2, 1),
            LocalListSort.videoOrder(videos, LocalListSort.Key.TIME, true)
        )
    }

    @Test
    fun `按大小_大到小且同值保持扫描顺序`() {
        val videos = listOf(
            video("a", size = 10),
            video("b", size = 30),
            video("c", size = 30),
            video("d", size = 20)
        )

        // b(1) 与 c(2) 大小相同，必须保持扫描原序（稳定排序），否则每次进页面顺序都会变
        assertEquals(
            listOf(1, 2, 3, 0),
            LocalListSort.videoOrder(videos, LocalListSort.Key.SIZE, false)
        )
        assertEquals(
            listOf(0, 3, 1, 2),
            LocalListSort.videoOrder(videos, LocalListSort.Key.SIZE, true)
        )
    }

    @Test
    fun `只有一个条目或空列表时不越界`() {
        assertEquals(
            listOf(0),
            LocalListSort.videoOrder(listOf(video("only")), LocalListSort.Key.TITLE, false)
        )
        assertTrue(LocalListSort.videoOrder(emptyList(), LocalListSort.Key.SIZE, false).isEmpty())
    }

    // ==================== 文件夹排序 ====================

    @Test
    fun `文件夹按标题排序忽略大小写`() {
        val folders = listOf(folder("zebra"), folder("Apple"), folder("mango"))

        assertEquals(
            listOf(1, 2, 0),
            LocalListSort.folderOrder(folders, emptyList(), LocalListSort.Key.TITLE, true)
        )
        assertEquals(
            listOf(0, 2, 1),
            LocalListSort.folderOrder(folders, emptyList(), LocalListSort.Key.TITLE, false)
        )
    }

    @Test
    fun `文件夹时间取创建时间与夹内最新视频的较大值`() {
        val folders = listOf(folder("old", createTime = 100), folder("new", createTime = 500))
        val videos = listOf(
            video("v1", lastModified = 900, folder = "old"),  // old 夹内视频更新，取 900
            video("v2", lastModified = 200, folder = "new")   // new 保持创建时间 500
        )

        assertEquals(
            listOf(0, 1),
            LocalListSort.folderOrder(folders, videos, LocalListSort.Key.TIME, false)
        )
        assertEquals(
            listOf(1, 0),
            LocalListSort.folderOrder(folders, videos, LocalListSort.Key.TIME, true)
        )
    }

    @Test
    fun `文件夹大小是夹内视频大小之和`() {
        val folders = listOf(folder("small"), folder("big"))
        val videos = listOf(
            video("v1", size = 100, folder = "small"),
            video("v2", size = 700, folder = "big"),
            video("v3", size = 400, folder = "big"),
            video("v4", size = 999, folder = "")   // 未分类视频不计入任何文件夹
        )

        assertEquals(
            listOf(1, 0),
            LocalListSort.folderOrder(folders, videos, LocalListSort.Key.SIZE, false)
        )
    }

    // ==================== 搜索 ====================

    @Test
    fun `搜索忽略大小写_空词匹配全部`() {
        assertTrue(LocalListSort.matches("进击的巨人 Final Season", "final"))
        assertTrue(LocalListSort.matches("【4K】某番", "4k"))
        assertFalse(LocalListSort.matches("进击的巨人", "EVA"))

        // 空关键词 = 不过滤（清除搜索后恢复全量）
        assertTrue(LocalListSort.matches("任意标题", ""))
        assertTrue(LocalListSort.matches(null, ""))

        // 标题为空而关键词非空：不匹配，但不能崩
        assertFalse(LocalListSort.matches(null, "标题"))
        assertFalse(LocalListSort.matches("", "标题"))
    }

    @Test
    fun `关键词归一化去首尾空白_null当空串`() {
        assertEquals("进击", LocalListSort.normalizeKeyword("  进击  "))
        assertEquals("", LocalListSort.normalizeKeyword(null))
        assertEquals("", LocalListSort.normalizeKeyword("   "))
    }

    // ==================== 档位 / 持久化映射 ====================

    @Test
    fun `七个档位的下标与键和方向往返一致`() {
        for (option in 0 until LocalListSort.OPTION_COUNT) {
            val key = LocalListSort.keyAt(option)
            val ascending = LocalListSort.ascendingAt(option)
            assertEquals(
                "档位 $option 应能回读到自身",
                option,
                LocalListSort.optionIndex(key, ascending)
            )
        }
        // 「默认」只有扫描顺序一种，方向传什么都不该跑到别的档位
        assertEquals(0, LocalListSort.optionIndex(LocalListSort.Key.DEFAULT, false))
    }

    @Test
    fun `档位下标越界时夹回合法范围`() {
        assertEquals(LocalListSort.Key.DEFAULT, LocalListSort.keyAt(-1))
        assertEquals(LocalListSort.Key.SIZE, LocalListSort.keyAt(99))
        assertTrue(LocalListSort.ascendingAt(99))
    }

    @Test
    fun `排序键的持久化短名往返_未知值回落默认`() {
        for (key in LocalListSort.Key.values()) {
            assertEquals(key, LocalListSort.keyOf(LocalListSort.nameOf(key)))
        }
        assertEquals(LocalListSort.Key.DEFAULT, LocalListSort.keyOf(null))
        assertEquals(LocalListSort.Key.DEFAULT, LocalListSort.keyOf(""))
        // 只认小写短名；旧版本/脏数据不生效，避免进页面就是奇怪的顺序
        assertEquals(LocalListSort.Key.DEFAULT, LocalListSort.keyOf("TITLE"))
    }
}
