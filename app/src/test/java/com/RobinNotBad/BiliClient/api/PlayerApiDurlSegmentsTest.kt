package com.RobinNotBad.BiliClient.api

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「playurl 的 durl 可能是多段」这件事。
 *
 * 反馈 #87/#90（26.11.11）的根因就是下载链路只取 `durl.getJSONObject(0)`：服务端把同一份
 * 文件切成 N 段时，下载器只下了第一段，落盘的就是一个能打开但只有前几秒的残片。
 * 因此解析层必须吐出**完整清单**（url / 字节数 / 时长 / 顺序 / 备用线路），
 * 由下载器按 [com.RobinNotBad.BiliClient.model.DownloadSegment.order] 依次下载再拼接。
 */
class PlayerApiDurlSegmentsTest {

    private fun part(
        order: Int,
        url: String,
        size: Long,
        length: Long,
        backups: List<String> = emptyList()
    ): JSONObject {
        val part = JSONObject()
        part.put("order", order)
        part.put("url", url)
        part.put("size", size)
        part.put("length", length)
        if (backups.isNotEmpty()) {
            val array = JSONArray()
            backups.forEach { array.put(it) }
            part.put("backup_url", array)
        }
        return part
    }

    @Test
    fun 多段durl解析出完整清单且顺序与数组一致() {
        val durl = JSONArray()
        durl.put(part(0, "https://a/seg0.mp4", 1_000_000L, 20_000L, listOf("https://b/seg0.mp4")))
        durl.put(part(1, "https://a/seg1.mp4", 2_000_000L, 40_000L))
        durl.put(part(2, "https://a/seg2.mp4", 3_000_000L, 60_000L))

        val segments = PlayerApi.parseDurlSegments(durl)

        assertEquals(3, segments.size)
        assertEquals(listOf(0, 1, 2), segments.map { it.order })
        assertEquals(
            listOf("https://a/seg0.mp4", "https://a/seg1.mp4", "https://a/seg2.mp4"),
            segments.map { it.url }
        )
        assertEquals(listOf(1_000_000L, 2_000_000L, 3_000_000L), segments.map { it.sizeBytes })
        assertEquals(listOf(20_000L, 40_000L, 60_000L), segments.map { it.lengthMs })
        assertEquals(listOf("https://b/seg0.mp4"), segments[0].backupUrls)
    }

    @Test
    fun 单段durl也给出只有一段的清单() {
        val durl = JSONArray()
        durl.put(part(0, "https://a/only.mp4", 8_000_000L, 200_000L))

        val segments = PlayerApi.parseDurlSegments(durl)

        assertEquals(1, segments.size)
        assertEquals(0, segments[0].order)
        assertEquals("https://a/only.mp4", segments[0].url)
        assertEquals(8_000_000L, segments[0].sizeBytes)
    }

    @Test
    fun 缺url的段被跳过且顺序重新连续() {
        val durl = JSONArray()
        durl.put(part(0, "https://a/seg0.mp4", 100L, 10L))
        durl.put(part(1, "", 0L, 0L))
        durl.put(part(2, "https://a/seg2.mp4", 300L, 30L))

        val segments = PlayerApi.parseDurlSegments(durl)

        // 跳过的段不能留下空洞：拼接顺序按清单下标走，order 必须是 0..size-1 连续
        assertEquals(2, segments.size)
        assertEquals(listOf(0, 1), segments.map { it.order })
        assertEquals(listOf("https://a/seg0.mp4", "https://a/seg2.mp4"), segments.map { it.url })
    }

    @Test
    fun 空durl给空清单() {
        assertTrue(PlayerApi.parseDurlSegments(JSONArray()).isEmpty())
        assertTrue(PlayerApi.parseDurlSegments(null).isEmpty())
    }

    @Test
    fun 段上没有size和length时按未知处理而不是当成0() {
        val durl = JSONArray()
        val part = JSONObject()
        part.put("url", "https://a/seg0.mp4")
        durl.put(part)

        val segments = PlayerApi.parseDurlSegments(durl)

        assertEquals(1, segments.size)
        assertEquals(-1L, segments[0].sizeBytes)
        assertEquals(-1L, segments[0].lengthMs)
    }
}
