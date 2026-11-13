package com.RobinNotBad.BiliClient.service.download

import com.RobinNotBad.BiliClient.model.DownloadSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 钉住「分段清单」的纯逻辑：#87/#90 的清单要能存进数据库、能重新读回来、
 * 能判断这次拿到的清单与上次是不是同一份（决定临时分片能不能复用）。
 *
 * 这一层刻意不碰网络、不碰 Android：所有判定都只是 list/文件长度上的比较。
 */
class DownloadSegmentPlanTest {

    private fun seg(order: Int, url: String, size: Long) = DownloadSegment().apply {
        this.order = order
        this.url = url
        this.sizeBytes = size
        this.lengthMs = 1000L * order
        this.backupUrls = arrayListOf("$url-backup")
    }

    @Test
    fun 全部分段都声明了字节数时总长为各段之和() {
        val segments = listOf(
            seg(0, "https://a/0.mp4", 1000L),
            seg(1, "https://a/1.mp4", 2000L),
            seg(2, "https://a/2.mp4", 3000L)
        )

        assertEquals(6000L, DownloadSegmentPlan.totalBytes(segments))
    }

    @Test
    fun 有分段大小未知时总长按未知处理() {
        val segments = listOf(
            seg(0, "https://a/0.mp4", 1000L),
            seg(1, "https://a/1.mp4", -1L)
        )

        // -1 = 不知道，不能退化成"只有第一段的大小"（那会让进度条中途冲到 100%）
        assertEquals(-1L, DownloadSegmentPlan.totalBytes(segments))
    }

    @Test
    fun 空清单总长为未知() {
        assertEquals(-1L, DownloadSegmentPlan.totalBytes(emptyList()))
    }

    @Test
    fun 清单序列化往返保持顺序与字段() {
        val segments = listOf(
            seg(0, "https://a/0.mp4", 1000L),
            seg(1, "https://a/1.mp4", -1L)
        )

        val restored = DownloadSegmentPlan.fromJson(DownloadSegmentPlan.toJson(segments))

        assertEquals(segments.size, restored.size)
        assertEquals(segments.map { it.order }, restored.map { it.order })
        assertEquals(segments.map { it.url }, restored.map { it.url })
        assertEquals(segments.map { it.sizeBytes }, restored.map { it.sizeBytes })
        assertEquals(segments.map { it.lengthMs }, restored.map { it.lengthMs })
        assertEquals(segments.map { it.backupUrls }, restored.map { it.backupUrls })
    }

    @Test
    fun 老记录没有清单时读成空清单() {
        // download 表的老行 segments 列是 NULL；空串/脏数据同样只能当作"没有清单"，
        // 绝不能抛异常把整个下载列表带崩。
        assertTrue(DownloadSegmentPlan.fromJson(null).isEmpty())
        assertTrue(DownloadSegmentPlan.fromJson("").isEmpty())
        assertTrue(DownloadSegmentPlan.fromJson("   ").isEmpty())
        assertTrue(DownloadSegmentPlan.fromJson("not json at all").isEmpty())
        assertTrue(DownloadSegmentPlan.fromJson("{}").isEmpty())
    }

    @Test
    fun 同一份清单判定只看顺序url与字节数() {
        val a = listOf(seg(0, "https://a/0.mp4", 1000L), seg(1, "https://a/1.mp4", 2000L))
        val same = listOf(seg(0, "https://a/0.mp4", 1000L), seg(1, "https://a/1.mp4", 2000L))

        assertTrue(DownloadSegmentPlan.isSamePlan(a, same))
        // 段数不同：服务端换了切法，临时分片一律不许复用
        assertFalse(DownloadSegmentPlan.isSamePlan(a, a.take(1)))
        // url 不同：可能是另一条线路/另一份文件
        assertFalse(
            DownloadSegmentPlan.isSamePlan(
                a, listOf(seg(0, "https://a/0.mp4", 1000L), seg(1, "https://a/1.mp4", 9999L))
            )
        )
        // 老记录没有清单：不能复用任何分片
        assertFalse(DownloadSegmentPlan.isSamePlan(emptyList(), a))
    }

    @Test
    fun 分片只有写完成标记且长度相符才可复用() {
        val dir = File(System.getProperty("java.io.tmpdir"), "dseg-plan-" + System.nanoTime())
        assertTrue(dir.mkdirs())
        try {
            val segment = seg(0, "https://a/0.mp4", 4L)
            val partFile = File(dir, DownloadSegmentPlan.partFileName(0))
            val doneFile = File(dir, DownloadSegmentPlan.partDoneFileName(0))
            partFile.writeBytes(byteArrayOf(1, 2, 3, 4))

            // 有分片文件但没完成标记：可能是分片下载预分配的稀疏文件（长度已经是满的），不许复用
            assertFalse(DownloadSegmentPlan.isReusablePart(partFile, doneFile, segment))

            doneFile.writeText("4")
            assertTrue(DownloadSegmentPlan.isReusablePart(partFile, doneFile, segment))

            // 长度不符：残片，不许复用
            partFile.writeBytes(byteArrayOf(1, 2))
            assertFalse(DownloadSegmentPlan.isReusablePart(partFile, doneFile, segment))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun 分片临时文件名互不相同且带固定前缀() {
        val names = (0..3).map { DownloadSegmentPlan.partFileName(it) }
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.all { it.startsWith(DownloadSegmentPlan.PART_PREFIX) })
        // 完成标记与分片本身不重名
        assertTrue(names.none { it == DownloadSegmentPlan.partDoneFileName(0) })
    }
}
