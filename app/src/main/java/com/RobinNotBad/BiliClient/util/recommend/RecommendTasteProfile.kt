package com.RobinNotBad.BiliClient.util.recommend

import kotlin.math.exp

/**
 * 一条观看历史（只留画像需要的字段）。纯数据，[ageDays] 由调用方按「现在」换算好，
 * 这样 [RecommendTasteProfile] 完全无副作用、可 JVM 单测。
 *
 * @param upMid            作者 mid（0 = 接口没给，无法计入画像）
 * @param ageDays          这条记录距今多少天（>= 0）
 * @param progressSeconds  观看进度（秒）；历史接口用 -1 表示「已看完」
 * @param durationSeconds  视频总时长（秒，0 = 未知）
 */
data class RecommendTasteEntry(
    val upMid: Long,
    val ageDays: Double,
    val progressSeconds: Long,
    val durationSeconds: Long
) {

    /** 已看完（历史接口的约定值）。 */
    val finished: Boolean
        get() = progressSeconds < 0

    /** 秒退：看了不到 [QUIT_SECONDS] 秒就退，且视频本身够长（长视频里秒退才说明不喜欢）。 */
    val quitEarly: Boolean
        get() = progressSeconds >= 0 &&
                progressSeconds < QUIT_SECONDS &&
                durationSeconds > QUIT_MIN_DURATION_SECONDS

    /** 完成度 0..1；「已看完」直接按 1。 */
    val completion: Double
        get() = when {
            progressSeconds < 0 -> 1.0
            durationSeconds <= 0 -> 0.0
            else -> (progressSeconds.toDouble() / durationSeconds).coerceIn(0.0, 1.0)
        }

    companion object {
        /** 不到这么多秒算秒退。 */
        const val QUIT_SECONDS: Long = 10L

        /** 只有长于这个时长的视频，秒退才算「不喜欢」（短视频本来就短，秒退没有信息量）。 */
        const val QUIT_MIN_DURATION_SECONDS: Long = 60L

        /** 用「记录距今秒数」构造，[viewAtSeconds] 为历史接口的 view_at（秒）。 */
        @JvmStatic
        fun of(upMid: Long, viewAtSeconds: Long, progressSeconds: Long, durationSeconds: Long, nowSeconds: Long): RecommendTasteEntry =
            RecommendTasteEntry(
                upMid = upMid,
                ageDays = ((nowSeconds - viewAtSeconds).coerceAtLeast(0L)) / 86400.0,
                progressSeconds = progressSeconds,
                durationSeconds = durationSeconds
            )
    }
}

/**
 * 从观看历史算「对每个 UP 的偏好分」（对齐 bilibili-margin 的 `rank.ts` / `buildTaste`）。
 *
 * 公式与原实现逐项一致：
 * - 时间衰减 `decay = exp(-days / 30)`：一个月前的观看权重约等于今天的一半以下；
 * - 单条权重 `decay * (秒退 ? -0.3 : 0.3 + 完成度)`：点开本身就带正的 0.3，
 *   看完再 +1；秒退记 -0.3（弱负反馈，不等于屏蔽）；
 * - 同一 UP 的多条记录直接累加。
 *
 * 画像只在本机算，历史数据来自 B 站自己的观看历史接口（见 `api/RecommendTasteApi`）。
 */
object RecommendTasteProfile {

    /** 衰减半衰期口径里的天数常量（与参考实现一致：exp(-days/30)）。 */
    const val DECAY_DAYS: Double = 30.0

    /** 点开即计入的正权重。 */
    const val WEIGHT_BASE: Double = 0.3

    /** 秒退的负权重。 */
    const val WEIGHT_QUIT: Double = -0.3

    /** 画像里最多记多少条「已看过」的 bvid（防止 SP 无限膨胀）。 */
    const val SEEN_LIMIT: Int = 400

    /** 负反馈权重上限（连点多次「不感兴趣」也不至于把 UP 永远压到底）。 */
    const val NEGATIVE_LIMIT: Int = 5

    /** 时间衰减系数。 */
    @JvmStatic
    fun decay(ageDays: Double): Double = exp(-ageDays.coerceAtLeast(0.0) / DECAY_DAYS)

    /** 单条观看记录对所在 UP 的权重（未乘衰减前的时间以外部分都已算进来）。 */
    @JvmStatic
    fun weight(entry: RecommendTasteEntry): Double =
        decay(entry.ageDays) * (if (entry.quitEarly) WEIGHT_QUIT else WEIGHT_BASE + entry.completion)

    /** 汇总成 upMid → 偏好分；mid 为 0 的记录忽略。 */
    @JvmStatic
    fun build(entries: List<RecommendTasteEntry>?): Map<Long, Double> {
        if (entries.isNullOrEmpty()) return emptyMap()
        val scores = HashMap<Long, Double>()
        for (entry in entries) {
            if (entry.upMid == 0L) continue
            scores[entry.upMid] = (scores[entry.upMid] ?: 0.0) + weight(entry)
        }
        return scores
    }

    /** 记一笔负反馈，返回新表（不修改入参）。 */
    @JvmStatic
    fun noteNegative(current: Map<Long, Int>?, upMid: Long, weight: Int = 1): Map<Long, Int> {
        val result = HashMap<Long, Int>()
        if (!current.isNullOrEmpty()) result.putAll(current)
        if (upMid == 0L || weight <= 0) return result
        val next = ((result[upMid] ?: 0) + weight).coerceAtMost(NEGATIVE_LIMIT)
        result[upMid] = next
        return result
    }
}
