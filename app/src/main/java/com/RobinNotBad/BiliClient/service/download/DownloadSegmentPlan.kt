package com.RobinNotBad.BiliClient.service.download

import com.RobinNotBad.BiliClient.model.DownloadSegment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 分段清单的纯逻辑（反馈 #87/#90）。
 *
 * 老接口（720P 及以下的合一 MP4/flv）可能把同一份文件切成多段 durl。下载器要按清单顺序
 * 逐段下载、再按顺序拼接，所以清单需要：算总字节（进度预算的分母）、序列化进数据库、
 * 判断"这次拿到的清单和上次是不是同一份"（决定临时分片能不能复用）。
 *
 * 这里**只**做 list / 文件长度上的判断，不碰网络也不碰 Android —— 全部可在 JVM 单测里钉住。
 */
internal object DownloadSegmentPlan {

    /** 临时分片的文件名前缀。同前缀的残留文件都可以安全清掉（它们是本模块自己造的）。 */
    const val PART_PREFIX = "video_seg"

    /** 第 [index] 段的临时分片文件名。 */
    fun partFileName(index: Int): String = "$PART_PREFIX$index.part"

    /**
     * 第 [index] 段的"写完了"标记文件名。
     *
     * 为什么需要它：Aria2 分片下载会先把文件 `setLength(totalSize)` 预分配满长度，
     * 只看 `File.length()` 会把一个刚开头的稀疏文件误判成"已经下完"。标记只在下载返回
     * [DownloadPathSpec.NORMAL] 之后才写。
     */
    fun partDoneFileName(index: Int): String = "${partFileName(index)}.done"

    /**
     * 清单声明总字节数；有任何一段大小未知就返回 -1（= 未知）。
     *
     * 不能"跳过未知段只加已知段"：那样进度分母偏小，进度条会先冲到 100% 再挂住，
     * 与 [DownloadService.probeSectionBytes] 对 DASH 的处理口径一致。
     */
    fun totalBytes(segments: List<DownloadSegment>): Long {
        if (segments.isEmpty()) return -1L
        var total = 0L
        for (segment in segments) {
            if (segment.sizeBytes <= 0) return -1L
            total += segment.sizeBytes
        }
        return total
    }

    /** 序列化成 download 表 `segments` 列的内容。 */
    fun toJson(segments: List<DownloadSegment>): String {
        val array = JSONArray()
        for (segment in segments) {
            val obj = JSONObject()
            obj.put("order", segment.order)
            obj.put("url", segment.url ?: "")
            obj.put("size", segment.sizeBytes)
            obj.put("length", segment.lengthMs)
            val backups = JSONArray()
            segment.backupUrls?.forEach { backups.put(it) }
            obj.put("backups", backups)
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * 从 download 表 `segments` 列反序列化。
     *
     * 老记录该列是 NULL：返回空清单（"没有清单"），调用方按单段/重新取流处理。
     * 脏数据同样只当成"没有清单"——绝不能让一条坏记录把整个下载列表带崩。
     */
    fun fromJson(json: String?): List<DownloadSegment> {
        if (json.isNullOrBlank()) return emptyList()
        val array = try {
            JSONArray(json)
        } catch (e: Throwable) {
            return emptyList()
        }
        val segments = ArrayList<DownloadSegment>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val url = obj.optString("url")
            if (url.isEmpty()) continue
            val segment = DownloadSegment()
            // 顺序以清单下标为准（与 PlayerApi.parseDurlSegments 一致），落盘顺序才是拼接顺序
            segment.order = segments.size
            segment.url = url
            segment.sizeBytes = obj.optLong("size", -1L)
            segment.lengthMs = obj.optLong("length", -1L)
            val backups = obj.optJSONArray("backups")
            if (backups != null) {
                for (j in 0 until backups.length()) {
                    val backup = backups.optString(j)
                    if (backup.isNotEmpty()) segment.backupUrls.add(backup)
                }
            }
            segments.add(segment)
        }
        return segments
    }

    /**
     * 两份清单能不能算"同一份文件的不同时刻"（顺序、地址、字节数全一致）。
     *
     * 只有同一份才允许复用上次下完的临时分片：服务端换了切法时拼起来的就是一堆错位的字节。
     */
    fun isSamePlan(stored: List<DownloadSegment>, fresh: List<DownloadSegment>): Boolean {
        if (stored.isEmpty() || stored.size != fresh.size) return false
        for (i in stored.indices) {
            val a = stored[i]
            val b = fresh[i]
            if (a.url != b.url || a.sizeBytes != b.sizeBytes) return false
        }
        return true
    }

    /** 这个临时分片是不是"上次已经完整下完的那一段"（有完成标记 + 长度与声明一致）。 */
    fun isReusablePart(partFile: File, doneFile: File, segment: DownloadSegment): Boolean {
        if (segment.sizeBytes <= 0) return false
        if (!partFile.isFile || !doneFile.isFile) return false
        return partFile.length() == segment.sizeBytes
    }

    /**
     * 清掉任务目录里的临时分片与完成标记（拼接成功后、失败清理时都要调）。
     *
     * 只认 [PART_PREFIX] 开头的文件，不碰成品（`video.mp4` 等）与其它临时产物 ——
     * 目录里可能还有用户已经下好的视频。
     */
    fun clearTempParts(folder: File?) {
        val names = folder?.list() ?: return
        for (name in names) {
            if (!name.startsWith(PART_PREFIX)) continue
            val file = File(folder, name)
            if (file.isFile) file.delete()
        }
    }
}
