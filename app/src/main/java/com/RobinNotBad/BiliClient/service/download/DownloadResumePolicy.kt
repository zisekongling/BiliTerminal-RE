package com.RobinNotBad.BiliClient.service.download

import org.json.JSONArray
import org.json.JSONObject

/**
 * 断点续传的判定与存档（反馈 #250 / #252 / #253）。
 *
 * 背景：原来每次开始下载都会先把临时文件删掉重建，失败/暂停时还会把半成品一起清掉，所以
 * 「下载到 99% 断网」「暂停后继续」都会从 0% 重下。现在把「已经落盘多少」记在临时文件旁边的
 * sidecar（[DownloadPathSpec.SUFFIX_RESUME]）里，下次开始下载时先问这里：能不能接着写。
 *
 * 两条路径的「已下载量」来源不同，这一点必须分清：
 * - 单连接（[DownloadStreamTransfer] 那条）：文件是顺序追加写的，`file.length()` 就是已下载量；
 * - 分片：文件一开始就被 `setLength(totalSize)` 预分配成整长，长度永远是满的，只能信 sidecar 里
 *   逐片记下的已写字节。
 *
 * 任务 id 不能当身份标识：失败的任务重新入队时 `startDownload` 会先 delete 旧行再 insert，
 * id 会变，而磁盘上的半成品还是同一份。所以标识一律内容派生（aid-cid-画质-哪个流）。
 *
 * 纯逻辑 + org.json，无 Android 依赖，可直接跑 JVM 单测。
 */
internal object DownloadResumePolicy {

    /** 视频流（DASH 的视频、或普通单流视频） */
    const val STREAM_VIDEO = "v"

    /** 音频流（DASH 的音频、或纯音频任务的音频） */
    const val STREAM_AUDIO = "a"

    /** 多段 durl 拼接出来的整段视频流 */
    const val STREAM_MERGED = "m"

    private const val KEY_VERSION = "v"
    private const val KEY_TAG = "tag"
    private const val KEY_TOTAL = "total"
    private const val KEY_SEGMENTS = "segments"

    /** sidecar 格式版本：字段含义变了就加一，老版本一律当"没有存档"处理 */
    private const val JSON_VERSION = 1

    /**
     * 一份续传存档。
     *
     * [total] 是这次下载认为的文件总长（服务器给的 Content-Length / Content-Range 总长）；
     * 服务器换线路后总长对不上就整份作废——不同 CDN 的转码版本可能真的不一样长。
     *
     * [segments] 为空表示单连接模式（此时已下载量看文件长度）；非空表示分片模式下逐片已写字节。
     */
    data class Record(val tag: String, val total: Long, val segments: List<Long> = emptyList())

    /** 单连接模式下该怎么做 */
    enum class Action {
        /** 没有可用存档：从头下载（覆盖写） */
        FRESH,

        /** 接着写：从 [Plan.offset] 开始追加 */
        APPEND,

        /** 文件已经是完整的：不用再下载，直接走收尾 */
        COMPLETE
    }

    /** 单连接模式的处置结果 */
    data class Plan(val action: Action, val offset: Long = 0L)

    /**
     * 内容派生的续传标识。
     *
     * 同一集视频换画质、换流（视频/音频）就是不同的文件，标识必须跟着变，否则会把 1080P 的
     * 半成品当成 480P 的续传起点。
     */
    fun tag(aid: Long, cid: Long, qn: Int, stream: String): String = "$aid-$cid-$qn-$stream"

    /**
     * 单连接模式的处置：返回 FRESH / APPEND / COMPLETE。
     *
     * 判 FRESH 的几种情况都是"不能信这个文件"：
     * - 没有存档、或存档属于别的视频/画质/流；
     * - 存档是分片模式留下的（文件被预分配成整长，长度不能当已下载量）；
     * - 存档里的总长未知（没有总长就没法判断文件是不是已经写全）；
     * - 文件长度为 0（什么都没下到，不如重建）；
     * - 文件比总长还大（不是同一份数据，接着写只会拼出坏文件）。
     */
    fun singlePlan(record: Record?, tag: String, existingBytes: Long): Plan {
        if (record == null || record.tag != tag) return Plan(Action.FRESH)
        if (record.segments.isNotEmpty()) return Plan(Action.FRESH)
        if (record.total <= 0L) return Plan(Action.FRESH)
        if (existingBytes <= 0L) return Plan(Action.FRESH)
        if (existingBytes > record.total) return Plan(Action.FRESH)
        if (existingBytes == record.total) return Plan(Action.COMPLETE, record.total)
        return Plan(Action.APPEND, existingBytes)
    }

    /**
     * 分片模式下每片已经落盘的字节；返回 null 表示从 0 重下。
     *
     * 片数、总长任何一项对不上就整份作废：分片边界是按总长算出来的，总长变了边界全变，
     * 拿着旧偏移去续写会写错位置。
     */
    fun segmentOffsets(record: Record?, tag: String, totalSize: Long, segments: Int): LongArray? {
        if (record == null || record.tag != tag) return null
        if (totalSize <= 0L || segments <= 0) return null
        if (record.total != totalSize) return null
        if (record.segments.size != segments) return null
        if (record.segments.any { it < 0L }) return null
        var sum = 0L
        for (bytes in record.segments) {
            sum += bytes
            if (sum > totalSize) return null
        }
        val offsets = LongArray(record.segments.size)
        for (i in record.segments.indices) offsets[i] = record.segments[i]
        return offsets
    }

    /** 分片模式是否已经全部落地（每片都写满） */
    fun isComplete(record: Record?, tag: String, totalSize: Long): Boolean {
        if (record == null || record.tag != tag) return false
        if (totalSize <= 0L || record.total != totalSize) return false
        if (record.segments.isEmpty()) return false
        var sum = 0L
        for (bytes in record.segments) {
            if (bytes < 0L) return false
            sum += bytes
            if (sum > totalSize) return false
        }
        return sum == totalSize
    }

    /** 存档序列化；失败（不该发生）时返回空串，调用方据此当"没写成功" */
    fun toJson(record: Record): String {
        return try {
            val segments = JSONArray()
            for (bytes in record.segments) segments.put(bytes)
            JSONObject()
                .put(KEY_VERSION, JSON_VERSION)
                .put(KEY_TAG, record.tag)
                .put(KEY_TOTAL, record.total)
                .put(KEY_SEGMENTS, segments)
                .toString()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 反序列化。脏数据一律返回 null（=当没有存档，从头下）：
     * 老版本、字段缺失、类型不对、负数，都不值得为了省一次重下而冒写出坏文件的风险。
     */
    fun fromJson(json: String?): Record? {
        if (json.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(json)
            if (obj.optInt(KEY_VERSION, 0) != JSON_VERSION) return null
            val tag = obj.optString(KEY_TAG, "")
            if (tag.isEmpty()) return null
            val total = obj.optLong(KEY_TOTAL, -1L)
            if (total <= 0L) return null
            val array = obj.optJSONArray(KEY_SEGMENTS) ?: JSONArray()
            val segments = ArrayList<Long>(array.length())
            for (i in 0 until array.length()) {
                val bytes = array.optLong(i, -1L)
                if (bytes < 0L) return null
                segments.add(bytes)
            }
            Record(tag, total, segments)
        } catch (e: Exception) {
            null
        }
    }
}
