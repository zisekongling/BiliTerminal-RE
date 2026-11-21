package com.RobinNotBad.BiliClient.service.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * 钉住「分段拼接」的两条硬要求（#87/#90）：
 *
 * 1. 顺序：必须按清单顺序拼接。顺序错了一样能拼出一个大小对的文件，但播放会花屏/跳时间轴，
 *    这种错误在真机上极难分辨，只能在单测里钉住字节内容；
 * 2. 完整性：总长度必须等于各段之和；少一段（没下到/被删）必须报错，
 *    **绝不能**悄悄拼出一个短文件当成功 —— 那正是用户看到的"残片"。
 */
class DownloadSegmentMergerTest {

    private fun tempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "dseg-merge-" + System.nanoTime())
        assertTrue(dir.mkdirs())
        return dir
    }

    @Test
    fun 按清单顺序拼接且总长等于各段之和() {
        val dir = tempDir()
        try {
            val parts = listOf(
                File(dir, "p0").apply { writeBytes("AAA".toByteArray()) },
                File(dir, "p1").apply { writeBytes("BBBBB".toByteArray()) },
                File(dir, "p2").apply { writeBytes("C".toByteArray()) }
            )
            val target = File(dir, "video_new.mp4")

            val total = DownloadSegmentMerger.merge(parts, target)

            assertEquals(9L, total)
            assertEquals(9L, target.length())
            assertEquals("AAABBBBBC", target.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun 目标文件已存在时被整份覆盖而不是追加() {
        val dir = tempDir()
        try {
            val parts = listOf(File(dir, "p0").apply { writeBytes("new".toByteArray()) })
            val target = File(dir, "video_new.mp4").apply { writeBytes("old-content-留下残段".toByteArray()) }

            val total = DownloadSegmentMerger.merge(parts, target)

            assertEquals(3L, total)
            assertEquals(3L, target.length())
            assertEquals("new", target.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun 缺段时报错且不留下残片成品() {
        val dir = tempDir()
        try {
            val missing = File(dir, "p1")
            val parts = listOf(
                File(dir, "p0").apply { writeBytes("AAA".toByteArray()) },
                missing,
                File(dir, "p2").apply { writeBytes("CC".toByteArray()) }
            )
            val target = File(dir, "video_new.mp4")

            try {
                DownloadSegmentMerger.merge(parts, target)
                fail("缺段必须报错，不能拼出一个短文件当成功")
            } catch (expected: IOException) {
                // 期望：把"少了哪一段"写进异常消息，方便失败原因直接显示给用户
                assertTrue(expected.message!!.contains("1"))
            }
            // 失败时不允许留下一个能被当成品播放的残片
            assertFalse(target.exists() && target.length() > 0)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun 空清单不产出文件() {
        val dir = tempDir()
        try {
            val target = File(dir, "video_new.mp4")
            try {
                DownloadSegmentMerger.merge(emptyList(), target)
                fail("没有分段可拼时必须报错")
            } catch (expected: IOException) {
                assertEquals(0L, if (target.exists()) target.length() else 0L)
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
