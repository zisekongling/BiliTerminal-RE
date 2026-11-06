package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 番剧「每集独立进度」。
 *
 * 服务端的 `pgc/player/web/v2` 只回**当前一集**的 `last_play_time`；番剧详情页由服务端
 * 汇总 `last_ep_id`，但很多场景（多设备/弱网/服务端未及时回写）会丢，导致「上次看到第 3 集
 * 第 12 分钟」变成「第 1 集 0 分」。这里按 epid 本地记一份，进播放器时优先用本地值，
 * 上报成功/看完后再更新，纯属本地缓存，不新增任何网络请求。
 */
data class BangumiProgress(
    val epid: Long,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAt: Long
)

/** 纯函数编解码（JVM 单测用）。格式：一行一条 `epid\tposition\tduration\tcompleted\tts`。 */
object BangumiProgressCodec {
    private const val MAX_ENTRIES = 128

    fun encode(entries: Collection<BangumiProgress>): String {
        if (entries.isEmpty()) return ""
        val sorted = entries.sortedByDescending { it.updatedAt }.take(MAX_ENTRIES)
        return sorted.joinToString("\n") { p ->
            "${p.epid}\t${p.positionMs}\t${p.durationMs}\t${if (p.completed) 1 else 0}\t${p.updatedAt}"
        }
    }

    fun decode(text: String?): Map<Long, BangumiProgress> {
        if (text.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<Long, BangumiProgress>()
        for (line in text.split('\n')) {
            if (line.isBlank()) continue
            val parts = line.split('\t')
            if (parts.size < 5) continue
            val epid = parts[0].toLongOrNull() ?: continue
            if (epid <= 0L) continue
            val position = parts[1].toLongOrNull() ?: continue
            val duration = parts[2].toLongOrNull() ?: 0L
            val completed = parts[3] == "1"
            val updatedAt = parts[4].toLongOrNull() ?: 0L
            out[epid] = BangumiProgress(
                epid = epid,
                positionMs = position.coerceAtLeast(0L),
                durationMs = duration.coerceAtLeast(0L),
                completed = completed,
                updatedAt = updatedAt
            )
        }
        return out
    }
}

object BangumiProgressStore {
    const val KEY = "player_bangumi_progress_v1"
    private const val MAX_ENTRIES = 128

    @JvmStatic
    fun progress(epid: Long): BangumiProgress? {
        if (epid <= 0L) return null
        return load()[epid]
    }

    /** 播放器/PlayerApi 读取续播位置（毫秒）；看完的集返回 -1 表示「已看完，别再续播」。 */
    @JvmStatic
    fun resumePositionMs(epid: Long): Long {
        val p = progress(epid) ?: return 0L
        return if (p.completed) -1L else p.positionMs
    }

    @JvmStatic
    fun remember(epid: Long, positionMs: Long, durationMs: Long, completed: Boolean = false) {
        if (epid <= 0L) return
        val entries = load().toMutableMap()
        val pos = if (completed) 0L else positionMs.coerceAtLeast(0L)
        entries[epid] = BangumiProgress(
            epid = epid,
            positionMs = pos,
            durationMs = durationMs.coerceAtLeast(0L),
            completed = completed,
            updatedAt = System.currentTimeMillis()
        )
        val trimmed = entries.entries.sortedByDescending { it.value.updatedAt }
            .take(MAX_ENTRIES)
            .associate { it.key to it.value }
        SharedPreferencesUtil.putString(KEY, BangumiProgressCodec.encode(trimmed.values))
    }

    @JvmStatic
    fun markCompleted(epid: Long) {
        val p = progress(epid) ?: return
        remember(epid, 0L, p.durationMs, completed = true)
    }

    @JvmStatic
    fun forget(epid: Long) {
        val entries = load().toMutableMap()
        if (entries.remove(epid) != null) {
            SharedPreferencesUtil.putString(KEY, BangumiProgressCodec.encode(entries.values))
        }
    }

    /** 清理：最近一次会话已不需要时由调试/设置页调用（当前无入口）。 */
    @JvmStatic
    fun clear() {
        SharedPreferencesUtil.removeValue(KEY)
    }

    private fun load(): Map<Long, BangumiProgress> =
        BangumiProgressCodec.decode(SharedPreferencesUtil.getString(KEY, ""))
}
