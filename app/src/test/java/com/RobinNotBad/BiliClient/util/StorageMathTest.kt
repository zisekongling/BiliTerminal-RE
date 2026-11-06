package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [StorageMath] 的纯函数单测。
 *
 * 这个类的判定直接决定「清缓存时会不会删到用户文件」，所以每条规则都要有反例：
 * - 体积换算的档位边界（1023 / 1024 / 1.5K 这类四舍五入最容易错）；
 * - 路径重叠必须按**路径段**判定（`/a/bc` 不能被当成 `/a/b` 的子路径，否则清理会越界）；
 * - 受保护根在缓存目录**里面**时，整棵缓存都不能清（宁可少清，不可误删）。
 *
 * 全部用 [TemporaryFolder] 造真实目录，不依赖 Android 环境。
 */
class StorageMathTest {

    @get:Rule
    val temp = TemporaryFolder()

    // ------------------------------------------------------------ formatBytes

    @Test
    fun formatBytes_usesByteBelow1K() {
        assertEquals("0 B", StorageMath.formatBytes(0L))
        assertEquals("1023 B", StorageMath.formatBytes(1023L))
    }

    @Test
    fun formatBytes_switchesUnitsAt1024() {
        assertEquals("1.0 KB", StorageMath.formatBytes(1024L))
        assertEquals("1.5 KB", StorageMath.formatBytes(1536L))
        assertEquals("1.0 MB", StorageMath.formatBytes(1024L * 1024))
        assertEquals("1.0 GB", StorageMath.formatBytes(1024L * 1024 * 1024))
    }

    @Test
    fun formatBytes_negativeTreatedAsZero() {
        assertEquals("0 B", StorageMath.formatBytes(-1L))
        assertEquals("0 B", StorageMath.formatBytes(Long.MIN_VALUE))
    }

    // ------------------------------------------------------------ sumBytes

    @Test
    fun sumBytes_countsNestedFiles() {
        val root = temp.newFolder("root")
        val sub = File(root, "sub/deep").apply { mkdirs() }
        File(root, "a.bin").writeBytes(ByteArray(3))
        File(sub, "b.bin").writeBytes(ByteArray(5))
        assertEquals(8L, StorageMath.sumBytes(root))
    }

    @Test
    fun sumBytes_missingPathIsZero() {
        assertEquals(0L, StorageMath.sumBytes(File(temp.root, "nope")))
        assertEquals(0L, StorageMath.sumBytes(null))
    }

    @Test
    fun sumBytes_respectsDepthLimit() {
        val root = temp.newFolder("depth")
        val deep = File(root, "1/2").apply { mkdirs() }
        File(deep, "c.bin").writeBytes(ByteArray(7))
        // 深度 0 只看 root 的一级子项：目录体积不计、也不往下走 → 0
        assertEquals(0L, StorageMath.sumBytes(root, maxDepth = 0))
        // 正常深度能看到
        assertEquals(7L, StorageMath.sumBytes(root, maxDepth = 24))
    }

    // ------------------------------------------------------------ 路径重叠

    @Test
    fun isInsideOrSame_doesNotMatchSiblingWithSamePrefix() {
        val base = temp.newFolder("base")
        val downstream = File(base, "download").apply { mkdirs() }
        val sibling = File(base, "downloads").apply { mkdirs() }
        assertFalse(StorageMath.isInsideOrSame(sibling, downstream))
        assertFalse(StorageMath.isInsideOrSame(downstream, sibling))
    }

    @Test
    fun isInsideOrSame_handlesSameAndChild() {
        val base = temp.newFolder("base2")
        val child = File(base, "x/y").apply { mkdirs() }
        assertTrue(StorageMath.isInsideOrSame(base, base))
        assertTrue(StorageMath.isInsideOrSame(child, base))
        assertFalse(StorageMath.isInsideOrSame(base, child))
    }

    @Test
    fun isInsideOrSame_normalizesDotDot() {
        val base = temp.newFolder("base3")
        val child = File(base, "keep").apply { mkdirs() }
        // 形如 <base>/keep/.. 的路径规范化后等于 base 本身
        assertTrue(StorageMath.isInsideOrSame(File(child, ".."), base))
    }

    // ------------------------------------------------------------ 受保护判定

    @Test
    fun isProtected_downloadRootInsideCacheProtectsCacheItself() {
        val cache = temp.newFolder("cache")
        val downloadInside = File(cache, "download").apply { mkdirs() }
        assertTrue(StorageMath.isProtected(cache, listOf(downloadInside)))
    }

    @Test
    fun isProtected_siblingRootDoesNotProtect() {
        val parent = temp.newFolder("parent")
        val cache = File(parent, "cache").apply { mkdirs() }
        val download = File(parent, "download").apply { mkdirs() }
        assertFalse(StorageMath.isProtected(cache, listOf(download)))
    }

    // ------------------------------------------------------------ 白名单清理

    @Test
    fun entriesToClean_skipsProtectedChildrenOnly() {
        val cache = temp.newFolder("cache2")
        val image = File(cache, "image_manager_disk_cache").apply { mkdirs() }
        val temp1 = File(cache, "tmp1").apply { mkdirs() }
        val download = temp.newFolder("download2")
        val protected = listOf(download)

        val targets = StorageMath.entriesToClean(cache, protected).map { it.name }.toSet()
        assertTrue(targets.contains("image_manager_disk_cache"))
        assertTrue(targets.contains("tmp1"))
        assertFalse(targets.contains("download2"))
        // 受保护根在缓存外面，不影响这两个
        assertTrue(image.exists() && temp1.exists())
    }

    /**
     * 受保护根**位于 cache 内部**时的保守口径：整个 cache 一个都不清。
     *
     * 规则来自 [StorageMath.isProtected] 的**双向**重叠判定：只要受保护根与 cache 有父子重叠
     * （不管谁包含谁），cache 整棵跳过。宁可少清，也不冒「清缓存把用户下载目录删了」的风险。
     * 这条与 [entriesToClean_returnsEmptyWhenRootIsProtected] 是同一条规则的两个入口，
     * 一处用已存在的子目录、一处用不存在的路径。
     */
    @Test
    fun protectedRootInsideCacheProtectsEverything() {
        val cache = temp.newFolder("cache3")
        val protectedChild = File(cache, "keep").apply { mkdirs() }
        File(protectedChild, "inner.bin").writeBytes(ByteArray(4))
        File(cache, "tmp.bin").writeBytes(ByteArray(2))

        val names = StorageMath.entriesToClean(cache, listOf(protectedChild)).map { it.name }.toSet()
        assertTrue("受保护根在 cache 内 → 整棵 cache 不清", names.isEmpty())
        // 最外层断言：调用方没拿到任何待删项，磁盘上自然一个都没少
        assertTrue(File(protectedChild, "inner.bin").exists())
        assertTrue(File(cache, "tmp.bin").exists())
    }

    @Test
    fun entriesToClean_returnsEmptyWhenRootIsProtected() {
        val cache = temp.newFolder("cache4")
        File(cache, "anything").mkdirs()
        // 整个 cache 被判为受保护（例如用户把下载目录配到 cache 里）
        val names = StorageMath.entriesToClean(cache, listOf(File(cache, "download")))
        assertTrue(names.isEmpty())
    }

    @Test
    fun entriesToClean_nonDirectoryReturnsEmpty() {
        val file = temp.newFile("plain.bin")
        assertTrue(StorageMath.entriesToClean(file, emptyList()).isEmpty())
    }
}
