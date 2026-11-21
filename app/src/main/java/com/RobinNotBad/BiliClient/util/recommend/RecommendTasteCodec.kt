package com.RobinNotBad.BiliClient.util.recommend

import org.json.JSONArray
import org.json.JSONObject

/**
 * 画像快照：`upScores` 是 UP 偏好分，`watchedBvids` 是近期看过的 bvid（重排时用来剔除
 * 「已经看过的」），`builtAtMs` 是构建时间，用来判 6 小时有效期。
 */
data class RecommendTasteSnapshot(
    val builtAtMs: Long,
    val upScores: Map<Long, Double>,
    val watchedBvids: Set<String>
) {

    /** 是否还在 [ttlMs] 有效期内；[builtAtMs] <= 0 一律算过期。 */
    fun isFresh(nowMs: Long, ttlMs: Long): Boolean =
        builtAtMs > 0L && nowMs - builtAtMs >= 0L && nowMs - builtAtMs < ttlMs
}

/**
 * 重排真正要用的信号：画像分 + 负反馈 + 已看过的 bvid。
 *
 * 三者来源不同、有效期也不同（画像 6 小时过期，负反馈与本地屏蔽都不设过期），
 * 所以重排入口收的是这个合成对象而不是快照本身。
 */
data class RecommendTasteSignal(
    val upScores: Map<Long, Double>,
    val negative: Map<Long, Int>,
    val watchedBvids: Set<String>
) {

    /** 某个 UP 的偏好分（没有画像时 0）。 */
    fun upScore(upMid: Long): Double = upScores[upMid] ?: 0.0

    /** 某个 UP 的负反馈次数。 */
    fun negativeOf(upMid: Long): Int = negative[upMid] ?: 0

    companion object {
        /** 没有画像也没有负反馈时用的空信号（重排仍会做「先验 + 多样性」）。 */
        @JvmStatic
        val EMPTY: RecommendTasteSignal = RecommendTasteSignal(emptyMap(), emptyMap(), emptySet())
    }
}

/**
 * 画像 / 负反馈的 JSON 编解码（纯函数，JVM 单测直接吃字符串）。
 *
 * 之所以单独拆出来：写进 SharedPreferences 的是字符串，编码格式一旦改坏（少个引号、
 * mid 写成 double）就会把用户的画像读崩，而这段逻辑不需要 Android 就能测。
 */
object RecommendTasteCodec {

    /** 编码画像快照。 */
    @JvmStatic
    fun encodeSnapshot(snapshot: RecommendTasteSnapshot): String {
        val ups = JSONObject()
        for ((mid, score) in snapshot.upScores) {
            if (mid == 0L) continue
            ups.put(mid.toString(), score)
        }
        val seen = JSONArray()
        for (bvid in snapshot.watchedBvids) {
            if (bvid.isEmpty()) continue
            seen.put(bvid)
        }
        return JSONObject()
            .put("at", snapshot.builtAtMs)
            .put("ups", ups)
            .put("seen", seen)
            .toString()
    }

    /** 解码画像快照；空串 / 坏 JSON / 类型不对一律返回 null（调用方当「没有画像」处理）。 */
    @JvmStatic
    fun decodeSnapshot(raw: String?): RecommendTasteSnapshot? {
        if (raw.isNullOrEmpty()) return null
        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            return null
        }
        val builtAt = root.optLong("at", 0L)
        if (builtAt <= 0L) return null

        val scores = HashMap<Long, Double>()
        val ups = root.optJSONObject("ups")
        if (ups != null) {
            val keys = ups.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val mid = key.toLongOrNull() ?: continue
                val score = ups.optDouble(key, 0.0)
                if (mid == 0L || score.isNaN()) continue
                scores[mid] = score
            }
        }

        val seen = LinkedHashSet<String>()
        val seenArray = root.optJSONArray("seen")
        if (seenArray != null) {
            for (i in 0 until seenArray.length()) {
                val bvid = seenArray.optString(i, "")
                if (bvid.isNotEmpty()) seen.add(bvid)
            }
        }
        return RecommendTasteSnapshot(builtAtMs = builtAt, upScores = scores, watchedBvids = seen)
    }

    /** 编码负反馈（mid → 次数）。 */
    @JvmStatic
    fun encodeNegative(negative: Map<Long, Int>): String {
        val json = JSONObject()
        for ((mid, count) in negative) {
            if (mid == 0L || count <= 0) continue
            json.put(mid.toString(), count)
        }
        return json.toString()
    }

    /** 解码负反馈；坏数据当空的处理。 */
    @JvmStatic
    fun decodeNegative(raw: String?): Map<Long, Int> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            return emptyMap()
        }
        val result = HashMap<Long, Int>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val mid = key.toLongOrNull() ?: continue
            val count = root.optInt(key, 0)
            if (mid == 0L || count <= 0) continue
            result[mid] = count
        }
        return result
    }
}
