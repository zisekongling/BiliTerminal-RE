package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 弹幕磁盘缓存单测（批次 2）。
 *
 * 分两部分：
 * 1. **纯逻辑**（缓存键、档位解析、LRU 淘汰）：这些算错的表现往往要几天后才暴露
 *    （缓存永远超限、或刚写进去就被自己删掉），必须在单测里把边界钉死；
 * 2. **真文件往返**：用 [TemporaryFolder] 在真实文件系统上验证原子写（不留 `.tmp`）、
 *    0 字节文件被当未命中、关闭缓存时不写盘 —— 这些是文件系统行为，纯函数测不出来。
 */
class DanmakuDiskCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ------------------------------------------------------------ 缓存键

    @Test
    fun `缓存键由 cid 与分段号唯一确定`() {
        assertEquals("dm_123_1.bin", DanmakuDiskCache.fileName(123L, 1))
        assertEquals("同一个键必须稳定（缓存命中率的前提）", "dm_123_1.bin", DanmakuDiskCache.fileName(123L, 1))
        assertTrue(
            "不同分段不能撞键，否则第 2 段会读成第 1 段",
            DanmakuDiskCache.fileName(123L, 1) != DanmakuDiskCache.fileName(123L, 2)
        )
        assertTrue(
            "不同 cid 不能撞键，否则换视频会看到别的视频的弹幕",
            DanmakuDiskCache.fileName(123L, 1) != DanmakuDiskCache.fileName(456L, 1)
        )
    }

    @Test
    fun `分段号从 1 开始 cid 必须为正`() {
        assertTrue(DanmakuCachePolicy.isValidKey(1L, 1))
        assertTrue(DanmakuCachePolicy.isValidKey(Long.MAX_VALUE, Int.MAX_VALUE))
        assertFalse("segment_index=0 是无效值", DanmakuCachePolicy.isValidKey(1L, 0))
        assertFalse(DanmakuCachePolicy.isValidKey(1L, -1))
        assertFalse("cid<=0 说明还没拿到真实稿件", DanmakuCachePolicy.isValidKey(0L, 1))
        assertFalse(DanmakuCachePolicy.isValidKey(-1L, 1))
    }

    // ------------------------------------------------------------ 档位解析

    @Test
    fun `档位字符串解析 默认值 与 钳制`() {
        assertEquals("没设置过 ⇒ 默认 64MB", 64, DanmakuDiskCache.parseMaxMb(null))
        assertEquals("空串按默认", 64, DanmakuDiskCache.parseMaxMb(""))
        assertEquals("脏数据按默认，不能让缓存解析把播放搞崩", 64, DanmakuDiskCache.parseMaxMb("abc"))
        assertEquals("显式关闭", 0, DanmakuDiskCache.parseMaxMb("0"))
        assertEquals("负数按关闭处理", 0, DanmakuDiskCache.parseMaxMb("-5"))
        assertEquals(16, DanmakuDiskCache.parseMaxMb("16"))
        assertEquals("超上限钳到 128", 128, DanmakuDiskCache.parseMaxMb("999"))
        assertEquals("容忍前后空格", 32, DanmakuDiskCache.parseMaxMb(" 32 "))
    }

    @Test
    fun `档位字符串转字节上限`() {
        assertEquals(0L, DanmakuDiskCache.maxBytesFromStored("0"))
        assertEquals(16L * DanmakuDiskCache.MB, DanmakuDiskCache.maxBytesFromStored("16"))
        assertEquals(
            "默认档位与 DEFAULT_MAX_MB 必须一致，否则设置页显示 64 实际却用别的值",
            DanmakuDiskCache.DEFAULT_MAX_MB.toLong() * DanmakuDiskCache.MB,
            DanmakuDiskCache.maxBytesFromStored(null)
        )
        assertEquals("六档展示名与落盘值必须一一对应", DanmakuDiskCache.displayNameRes.size, DanmakuDiskCache.storedValues.size)
    }

    // ------------------------------------------------------------ LRU 纯逻辑

    private fun entry(name: String, size: Long, access: Long) =
        DanmakuCachePolicy.Entry(name, size, access)

    @Test
    fun `总占用是各条之和`() {
        assertEquals(0L, DanmakuCachePolicy.totalBytes(emptyList()))
        assertEquals(30L, DanmakuCachePolicy.totalBytes(listOf(entry("a", 10, 1), entry("b", 20, 2))))
    }

    @Test
    fun `空列表与预算内都不淘汰`() {
        assertTrue(DanmakuCachePolicy.namesToEvict(emptyList(), 100).isEmpty())
        val entries = listOf(entry("a", 10, 1), entry("b", 10, 2))
        assertTrue("正好等于上限时不应淘汰", DanmakuCachePolicy.namesToEvict(entries, 20).isEmpty())
        assertTrue("低于上限", DanmakuCachePolicy.namesToEvict(entries, 100).isEmpty())
    }

    @Test
    fun `关闭缓存时全部淘汰`() {
        val entries = listOf(entry("a", 10, 1), entry("b", 10, 2))
        assertEquals(listOf("a", "b"), DanmakuCachePolicy.namesToEvict(entries, 0))
        assertEquals(listOf("a", "b"), DanmakuCachePolicy.namesToEvict(entries, -1))
    }

    @Test
    fun `按最久未使用顺序淘汰直到回到预算内`() {
        // 三条各 10 字节，访问时间 a < b < c，总 30。
        val entries = listOf(entry("c", 10, 3), entry("a", 10, 1), entry("b", 10, 2))
        assertEquals("只差 5 字节：淘汰最旧的 a 就够了", listOf("a"), DanmakuCachePolicy.namesToEvict(entries, 25))
        assertEquals("预算 15：必须淘汰 a、b", listOf("a", "b"), DanmakuCachePolicy.namesToEvict(entries, 15))
        assertEquals("预算 10：只剩最新的一条", listOf("a", "b"), DanmakuCachePolicy.namesToEvict(entries, 10))
    }

    @Test
    fun `访问时间相同时按文件名排序保证结果稳定`() {
        // 真实场景里一次 trim 会见到大量 lastModified 相同的文件（同一秒写入），
        // 若比较器不稳定，淘汰结果会随目录列举顺序变化，缓存大小就不可控。
        val entries = listOf(entry("b", 10, 1), entry("a", 10, 1))
        assertEquals(listOf("a", "b"), DanmakuCachePolicy.namesToEvict(entries, 5))
    }

    @Test
    fun `单条超大文件也会被淘汰`() {
        val entries = listOf(entry("huge", 100, 1), entry("small", 1, 2))
        assertEquals(listOf("huge"), DanmakuCachePolicy.namesToEvict(entries, 10))
    }

    // ------------------------------------------------------------ 真文件往返

    @Test
    fun `写入后能原样读回且不留临时文件`() {
        val cache = DanmakuDiskCache(tmp.newFolder("cache"), 64 * DanmakuDiskCache.MB)
        val data = byteArrayOf(1, 2, 3, 4, 5)
        cache.write(42L, 1, data)

        assertArrayEquals("读回的内容必须与写进去的字节完全一致（protobuf 差一个字节就解析失败）", data, cache.read(42L, 1))
        val names = tmp.root.walkTopDown().filter { it.isFile }.map { it.name }.toList()
        assertTrue("原子写的中间文件不能在成功后残留：$names", names.none { it.endsWith(DanmakuDiskCache.TMP_SUFFIX) })
        assertTrue("占用统计要能反映实际字节", cache.sizeBytes() >= data.size.toLong())
    }

    @Test
    fun `未命中 无效键 与 关闭缓存都返回 null`() {
        val cache = DanmakuDiskCache(tmp.newFolder("cache"), 64 * DanmakuDiskCache.MB)
        assertNull("没写过的段", cache.read(42L, 1))
        assertNull("segment_index=0 无效", cache.read(42L, 0))

        val off = DanmakuDiskCache(tmp.newFolder("off"), 0)
        assertFalse(off.isEnabled())
        off.write(42L, 1, byteArrayOf(1))
        assertNull("关闭缓存时读不到（也没写进去）", off.read(42L, 1))
        assertFalse("关闭缓存不该创建文件", File(tmp.root, "off/" + DanmakuDiskCache.fileName(42L, 1)).exists())
    }

    @Test
    fun `零字节缓存文件被当作未命中并删除`() {
        // 旧版本/崩溃可能留下 0 字节文件；把它当命中会让弹幕整段丢失。
        val root = tmp.newFolder("cache")
        val broken = File(root, DanmakuDiskCache.fileName(7L, 1))
        broken.writeBytes(ByteArray(0))
        val cache = DanmakuDiskCache(root, 64 * DanmakuDiskCache.MB)

        assertNull(cache.read(7L, 1))
        assertFalse("损坏的缓存要顺手删掉", broken.exists())
    }

    @Test
    fun `trim 清掉崩溃残留的临时文件`() {
        val root = tmp.newFolder("cache")
        val residue = File(root, DanmakuDiskCache.fileName(9L, 2) + DanmakuDiskCache.TMP_SUFFIX)
        residue.writeBytes(byteArrayOf(9, 9))
        DanmakuDiskCache(root, 64 * DanmakuDiskCache.MB).trim()
        assertFalse("半截的 .tmp 永远不该被当成缓存，trim 要清理掉", residue.exists())
    }

    @Test
    fun `超出上限时淘汰最旧的缓存文件`() {
        val root = tmp.newFolder("cache")
        val big = DanmakuDiskCache(root, 64 * DanmakuDiskCache.MB)
        val payload = ByteArray(1000)

        big.write(1L, 1, payload)
        big.write(1L, 2, payload)
        big.write(1L, 3, payload)
        // 手工把访问时间排成 seg1 < seg2 < seg3（真实环境靠 read() 刷 lastModified）。
        val t0 = System.currentTimeMillis() - 30_000L
        File(root, DanmakuDiskCache.fileName(1L, 1)).setLastModified(t0)
        File(root, DanmakuDiskCache.fileName(1L, 2)).setLastModified(t0 + 10_000L)
        File(root, DanmakuDiskCache.fileName(1L, 3)).setLastModified(t0 + 20_000L)

        // 上限只够两条：触发一次淘汰。
        val tight = DanmakuDiskCache(root, 2_500L)
        tight.trim()

        assertFalse("最久未使用的第 1 段应被淘汰", File(root, DanmakuDiskCache.fileName(1L, 1)).exists())
        assertTrue("第 2 段还在", File(root, DanmakuDiskCache.fileName(1L, 2)).exists())
        assertTrue("最新写入的第 3 段必须保留", File(root, DanmakuDiskCache.fileName(1L, 3)).exists())
    }

    @Test
    fun `remove 与 clear 的行为`() {
        val root = tmp.newFolder("cache")
        val cache = DanmakuDiskCache(root, 64 * DanmakuDiskCache.MB)
        cache.write(5L, 1, byteArrayOf(1))
        cache.write(5L, 2, byteArrayOf(2))

        cache.remove(5L, 1)
        assertNull(cache.read(5L, 1))
        assertTrue("另一段不受影响", cache.read(5L, 2) != null)

        cache.clear()
        assertNull(cache.read(5L, 2))
        assertEquals(0L, cache.sizeBytes())
    }

    @Test
    fun `目录不可用时静默失败而不抛异常`() {
        // 缓存是纯加速：目录建不出来（权限/空间）也只能算「这次没命中」，绝不能把播放搞崩。
        val notADir = tmp.newFile("occupied")
        val cache = DanmakuDiskCache(notADir, 64 * DanmakuDiskCache.MB)
        cache.write(1L, 1, byteArrayOf(1, 2, 3))
        assertNull(cache.read(1L, 1))
        assertEquals(0L, cache.sizeBytes())
    }
}
