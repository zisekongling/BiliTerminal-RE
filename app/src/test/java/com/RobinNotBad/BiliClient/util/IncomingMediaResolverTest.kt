package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [IncomingMediaResolver] 纯函数部分的单测：标题怎么起、临时文件扩展名怎么取、
 * 空间够不够、哪些旧临时文件该删。碰 ContentResolver 的 [IncomingMediaResolver.resolve]
 * 不在这里测（需要 Android 运行时，走真机 L1）。
 */
class IncomingMediaResolverTest {

    @Test
    fun `标题用显示名并去掉扩展名`() {
        assertEquals("鬼灭之刃 柱训练篇", IncomingMediaResolver.titleOf("鬼灭之刃 柱训练篇.mp4", "video.mp4"))
    }

    @Test
    fun `显示名为空时退回路径里的文件名`() {
        assertEquals("movie", IncomingMediaResolver.titleOf(null, "movie.mkv"))
        assertEquals("movie", IncomingMediaResolver.titleOf("   ", "movie.mkv"))
    }

    @Test
    fun `没有文件名时返回空串交给调用方兜底`() {
        assertEquals("", IncomingMediaResolver.titleOf(null, null))
        assertEquals("", IncomingMediaResolver.titleOf("", ""))
    }

    @Test
    fun `隐藏文件的点不算扩展名分隔`() {
        assertEquals(".nomedia", IncomingMediaResolver.titleOf(".nomedia", null))
    }

    @Test
    fun `扩展名取小写`() {
        assertEquals("mp4", IncomingMediaResolver.extensionOf("A.MP4"))
        assertEquals("mkv", IncomingMediaResolver.extensionOf("片子.mkv"))
        assertEquals("", IncomingMediaResolver.extensionOf("没有扩展名"))
        assertEquals("", IncomingMediaResolver.extensionOf("结尾有点."))
        assertEquals("", IncomingMediaResolver.extensionOf(null))
    }

    @Test
    fun `异常扩展名不拿来命名临时文件`() {
        assertEquals("", IncomingMediaResolver.extensionOf("a.abcdefg"))
        assertEquals("", IncomingMediaResolver.extensionOf("a.m p4"))
    }

    @Test
    fun `空间判据留足余量`() {
        val size = 100L * 1024 * 1024
        val reserve = IncomingMediaResolver.SPACE_RESERVE_BYTES
        assertTrue(IncomingMediaResolver.enoughSpace(size, size + reserve))
        assertFalse(IncomingMediaResolver.enoughSpace(size, size + reserve - 1))
        // 大小未知时不拦：宁可直接试复制，也不因为查不到 SIZE 就不让播
        assertTrue(IncomingMediaResolver.enoughSpace(0, 0))
    }

    @Test
    fun `只清理超过保留时长的临时文件`() {
        // 必须是真实存在的文件：不存在的 File 改不了 lastModified，时间恒为 0，会全被当成过期
        val dir = java.nio.file.Files.createTempDirectory("incoming-test").toFile()
        try {
            val now = System.currentTimeMillis()
            val ttl = 60_000L
            val fresh = File(dir, "fresh.mp4").apply { writeText("x") }.apply { setLastModified(now - 1_000) }
            val stale = File(dir, "stale.mp4").apply { writeText("x") }.apply { setLastModified(now - 600_000) }
            val picked = IncomingMediaResolver.staleFiles(listOf(fresh, stale), now, ttl)
            assertEquals(listOf(stale), picked)
        } finally {
            dir.deleteRecursively()
        }
    }
}
