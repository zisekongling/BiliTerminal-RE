package com.RobinNotBad.BiliClient.service

import com.RobinNotBad.BiliClient.service.download.DownloadProgressMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下载分片划分与进度换算的纯逻辑测试（E2 拆分 DownloadService 第 1 步）。
 *
 * 这些表达式原本内联在下载循环里，改错不会崩、只会让进度条数值或分片区间出错
 * （分片边界错一位会导致最后一段重复下载或漏下载，表现为文件"下载完成但播放损坏"），
 * 所以边界（0 字节、小文件、除零、最后一节、越界分片号）必须在这里锁死。
 */
class DownloadProgressMathTest {

    private val mb = 1024 * 1024L

    // ---------- segmentCount：分片数 ----------

    @Test
    fun segmentCount_约每2MB一片且受上限约束() {
        assertEquals("0 字节也要给 1 片，否则后面会除零", 1, DownloadProgressMath.segmentCount(0L, 16))
        assertEquals("不足 2MB 给 1 片", 1, DownloadProgressMath.segmentCount(mb, 16))
        assertEquals("恰好 2MB 给 1 片", 1, DownloadProgressMath.segmentCount(2 * mb, 16))
        // 整除，所以 2MB~4MB 之间都是 1 片：2097153/2097152 == 1
        assertEquals("刚过 2MB 仍然只有 1 片（整除语义）", 1, DownloadProgressMath.segmentCount(2 * mb + 1, 16))
        assertEquals("差一字节到 4MB 仍是 1 片", 1, DownloadProgressMath.segmentCount(4 * mb - 1, 16))
        assertEquals("恰好 4MB 才涨到 2 片", 2, DownloadProgressMath.segmentCount(4 * mb, 16))
        assertEquals("6MB 给 3 片", 3, DownloadProgressMath.segmentCount(6 * mb, 16))
    }

    @Test
    fun segmentCount_结果恒不小于1() {
        // 用户把 aria2_split 配成 0 / 负数时不能返回 0（segmentLen 会除零）
        assertEquals(1, DownloadProgressMath.segmentCount(100 * mb, 0))
        assertEquals(1, DownloadProgressMath.segmentCount(100 * mb, -3))
        assertEquals("上限小于 1 时也兜底为 1 片", 1, DownloadProgressMath.segmentCount(1L, 0))
    }

    @Test
    fun segmentCount_上限小于理想片数时取上限() {
        assertEquals(4, DownloadProgressMath.segmentCount(100 * mb, 4))
        assertEquals(1, DownloadProgressMath.segmentCount(100 * mb, 1))
    }

    // ---------- segmentLen：每片字节数 ----------

    @Test
    fun segmentLen_整除且余数留给最后一节() {
        assertEquals(0L, DownloadProgressMath.segmentLen(0L, 1))
        assertEquals(5L, DownloadProgressMath.segmentLen(10L, 2))
        assertEquals(3L, DownloadProgressMath.segmentLen(11L, 3))
        assertEquals(2L, DownloadProgressMath.segmentLen(7L, 3))
    }

    // ---------- segmentRange：分片闭区间 ----------

    @Test
    fun segmentRange_所有分片恰好覆盖整个文件() {
        val totalSize = 10L
        val segments = 3
        val segmentLen = DownloadProgressMath.segmentLen(totalSize, segments)
        val ranges = (0 until segments).map { DownloadProgressMath.segmentRange(it, segments, totalSize, segmentLen) }

        assertEquals("首片从 0 开始", 0L, ranges.first().first)
        assertEquals("末片吃到文件末尾", totalSize - 1, ranges.last().last)
        for (i in 0 until segments - 1) {
            assertEquals("分片 $i 与 ${i + 1} 必须首尾相接，不能重叠也不能留缝",
                ranges[i].last + 1, ranges[i + 1].first)
        }
    }

    @Test
    fun segmentRange_最后一节吃掉整除余数() {
        // 10 字节分 3 片：每片 3 字节，最后一节 3..9（4 字节）
        val segmentLen = DownloadProgressMath.segmentLen(10L, 3)
        assertEquals(0L..2L, DownloadProgressMath.segmentRange(0, 3, 10L, segmentLen))
        assertEquals(3L..5L, DownloadProgressMath.segmentRange(1, 3, 10L, segmentLen))
        assertEquals(6L..9L, DownloadProgressMath.segmentRange(2, 3, 10L, segmentLen))
    }

    @Test
    fun segmentRange_仅一片时覆盖全文件() {
        assertEquals(0L..0L, DownloadProgressMath.segmentRange(0, 1, 1L, 1L))
        assertEquals(0L..99L, DownloadProgressMath.segmentRange(0, 1, 100L, 100L))
    }

    @Test
    fun segmentRange_零字节文件得到空区间() {
        // 原内联写法就是 start=0、end=totalSize-1=-1，抽函数后必须保持一致
        val range = DownloadProgressMath.segmentRange(0, 1, 0L, 0L)
        assertEquals(0L, range.first)
        assertEquals(-1L, range.last)
        assertTrue("0..-1 是空区间，不会真的去请求一字节", range.isEmpty())
    }

    @Test
    fun segmentRange_小文件多分片时末节不越界() {
        // 3 字节切成 3 片：分片长度 1，末片 end = totalSize-1 = 2，全部有效
        val segments = 3
        val segmentLen = DownloadProgressMath.segmentLen(3L, segments)
        assertEquals(0L..0L, DownloadProgressMath.segmentRange(0, segments, 3L, segmentLen))
        assertEquals(1L..1L, DownloadProgressMath.segmentRange(1, segments, 3L, segmentLen))
        assertEquals(2L..2L, DownloadProgressMath.segmentRange(2, segments, 3L, segmentLen))
    }

    // ---------- progressForBytes：已知总大小的进度换算 ----------

    @Test
    fun progressForBytes_区间两端与中点() {
        assertEquals(0.3f, DownloadProgressMath.progressForBytes(0L, 100L, 0.3f, 1.0f), 1e-6f)
        assertEquals(0.65f, DownloadProgressMath.progressForBytes(50L, 100L, 0.3f, 1.0f), 1e-6f)
        assertEquals(1.0f, DownloadProgressMath.progressForBytes(100L, 100L, 0.3f, 1.0f), 1e-6f)
    }

    @Test
    fun progressForBytes_超过总大小时会越过阶段终点() {
        // 原实现没有 clamp，超出部分会把阶段进度顶过 endProgress，行为必须保持
        assertEquals(1.35f, DownloadProgressMath.progressForBytes(150L, 100L, 0.3f, 1.0f), 1e-6f)
    }

    @Test
    fun progressForBytes_零下载量与零区间() {
        assertEquals("区间退化成一点时进度恒为该点", 0.5f,
            DownloadProgressMath.progressForBytes(0L, 100L, 0.5f, 0.5f), 1e-6f)
        assertTrue("total==0 时 0/0 得 NaN，乘零区间也是 NaN",
            DownloadProgressMath.progressForBytes(0L, 0L, 0f, 0f).isNaN())
    }

    @Test
    fun progressForBytes_总大小为0时沿用浮点语义() {
        // total==0 时 1.0f*x/0 = Infinity，乘 0 得 NaN——调用方用 total>0 挡住，
        // 这里把"没挡住会怎样"记录下来，防止有人加个 != 0 的兜底悄悄改行为
        val result = DownloadProgressMath.progressForBytes(10L, 0L, 0f, 0f)
        assertTrue("total==0 时结果不是有限值", result.isNaN() || result.isInfinite())

        val infinite = DownloadProgressMath.progressForBytes(10L, 0L, 0f, 1f)
        assertTrue("base!=end 时应得到 Infinity", infinite.isInfinite())
    }

    // ---------- DASH 阶段进度 ----------

    @Test
    fun dashStageEnds_严格递增() {
        assertEquals("视频段起点", 0.12f, DownloadProgressMath.DASH_ATTACH_END, 1e-6f)
        assertEquals("视频段终点", 0.52f, DownloadProgressMath.DASH_VIDEO_END, 1e-6f)
        assertEquals("音频段终点（也是数据部分的终点）", 0.88f, DownloadProgressMath.DASH_AUDIO_END, 1e-6f)
        assertTrue("阶段边界必须递增，否则进度条会倒退",
            DownloadProgressMath.DASH_ATTACH_END < DownloadProgressMath.DASH_VIDEO_END
                && DownloadProgressMath.DASH_VIDEO_END < DownloadProgressMath.DASH_AUDIO_END)
    }

    // ---------- byteRatio：按真实字节算进度 ----------

    @Test
    fun byteRatio_零点与满额() {
        assertEquals(0f, DownloadProgressMath.byteRatio(0L, 100L), 1e-6f)
        assertEquals(0.5f, DownloadProgressMath.byteRatio(50L, 100L), 1e-6f)
        assertEquals(1f, DownloadProgressMath.byteRatio(100L, 100L), 1e-6f)
    }

    @Test
    fun byteRatio_夹住越界与未知总量() {
        assertEquals("多下的字节不能让进度冲过 100%", 1f,
            DownloadProgressMath.byteRatio(150L, 100L), 1e-6f)
        assertEquals("负字节按 0 处理", 0f, DownloadProgressMath.byteRatio(-10L, 100L), 1e-6f)
        assertEquals("总量未知时返回 0，不能是 NaN", 0f,
            DownloadProgressMath.byteRatio(10L, 0L), 1e-6f)
    }

    @Test
    fun byteRatio_十GB级数字不丢精度() {
        // Float 只有 24 位有效位，直接 float 相除会把 9.9GB/10GB 算成 1.0
        assertEquals(0.99f, DownloadProgressMath.byteRatio(99L * 1024 * 1024 * 1024 / 10,
            10L * 1024 * 1024 * 1024), 1e-4f)
    }

    // ---------- monotoneProgress：进度只许前进 ----------

    @Test
    fun monotoneProgress_取较大值并夹住范围() {
        assertEquals("变大就跟着变大", 0.6f, DownloadProgressMath.monotoneProgress(0.3f, 0.6f), 1e-6f)
        assertEquals("变小保持原值", 0.6f, DownloadProgressMath.monotoneProgress(0.6f, 0.2f), 1e-6f)
        assertEquals("不超过 1", 1f, DownloadProgressMath.monotoneProgress(0.9f, 1.4f), 1e-6f)
        assertEquals("不低于 0", 0f, DownloadProgressMath.monotoneProgress(-0.5f, -0.1f), 1e-6f)
    }

    // ---------- 伪进度 ----------

    @Test
    fun pseudoProgressStep_步进并封顶九成() {
        assertEquals(0.02f, DownloadProgressMath.pseudoProgressStep(0f), 1e-6f)
        assertEquals(0.9f, DownloadProgressMath.pseudoProgressStep(0.88f), 1e-6f)
        assertEquals("封顶后不再增长", 0.9f, DownloadProgressMath.pseudoProgressStep(0.9f), 1e-6f)
        assertEquals("已超额也压回 0.9", 0.9f, DownloadProgressMath.pseudoProgressStep(0.95f), 1e-6f)
    }

    @Test
    fun progressForPseudo_映射到阶段区间() {
        assertEquals(0.3f, DownloadProgressMath.progressForPseudo(0f, 0.3f, 1.0f), 1e-6f)
        assertEquals(0.93f, DownloadProgressMath.progressForPseudo(0.9f, 0.3f, 1.0f), 1e-6f)
        assertEquals("总大小未知时最多走到阶段区间 90%", 0.93f,
            DownloadProgressMath.progressForPseudo(DownloadProgressMath.pseudoProgressStep(0.95f), 0.3f, 1.0f), 1e-6f)
    }
}
