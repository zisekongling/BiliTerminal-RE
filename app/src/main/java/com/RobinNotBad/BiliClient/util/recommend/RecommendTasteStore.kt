package com.RobinNotBad.BiliClient.util.recommend

import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 画像与负反馈的本地存储（SharedPreferences + [RecommendTasteCodec]）。
 *
 * 两份数据分开存、分开过期：
 * - 画像快照 `recommend_taste_snapshot`：来自观看历史，**6 小时**有效（对齐参考实现 TTL）；
 *   过期后下次重排会重新拉历史（见 `api/RecommendTasteApi`）。
 * - 负反馈 `recommend_taste_negative`：用户点「不感兴趣 / 屏蔽作者」记下的，**不过期**——
 *   拉历史失败也要保留，否则用户刚屏蔽的 UP 会重新冒出来。
 *
 * 所有方法都是同步的（读 SP 很快），网络拉历史在调用侧放后台线程。
 */
object RecommendTasteStore {

    private const val KEY_SNAPSHOT = "recommend_taste_snapshot"
    private const val KEY_NEGATIVE = "recommend_taste_negative"

    /** 画像有效期，与参考实现的 6 小时一致。 */
    const val TTL_MS: Long = 6L * 60L * 60L * 1000L

    /** 读快照（不看有效期）；没有则 null。 */
    @JvmStatic
    fun snapshot(): RecommendTasteSnapshot? =
        RecommendTasteCodec.decodeSnapshot(SharedPreferencesUtil.getString(KEY_SNAPSHOT, null))

    /** 保存画像；`watchedBvids` 超长时只留前 [RecommendTasteProfile.SEEN_LIMIT] 条。 */
    @JvmStatic
    fun save(
        upScores: Map<Long, Double>,
        watchedBvids: Set<String>,
        nowMs: Long = System.currentTimeMillis()
    ): RecommendTasteSnapshot {
        val kept = if (watchedBvids.size <= RecommendTasteProfile.SEEN_LIMIT) watchedBvids
        else LinkedHashSet(watchedBvids.take(RecommendTasteProfile.SEEN_LIMIT))
        val snapshot = RecommendTasteSnapshot(builtAtMs = nowMs, upScores = upScores, watchedBvids = kept)
        SharedPreferencesUtil.putString(KEY_SNAPSHOT, RecommendTasteCodec.encodeSnapshot(snapshot))
        return snapshot
    }

    /** 当前负反馈表。 */
    @JvmStatic
    fun negative(): Map<Long, Int> =
        RecommendTasteCodec.decodeNegative(SharedPreferencesUtil.getString(KEY_NEGATIVE, null))

    /** 记一笔负反馈（「减少推荐」与「屏蔽作者」都记）；返回更新后的表。 */
    @JvmStatic
    fun noteNegative(upMid: Long): Map<Long, Int> {
        val updated = RecommendTasteProfile.noteNegative(negative(), upMid)
        if (upMid != 0L) {
            SharedPreferencesUtil.putString(KEY_NEGATIVE, RecommendTasteCodec.encodeNegative(updated))
        }
        return updated
    }

    /** 画像是否还在有效期内。 */
    @JvmStatic
    fun hasFreshSnapshot(nowMs: Long = System.currentTimeMillis()): Boolean =
        snapshot()?.isFresh(nowMs, TTL_MS) == true

    /**
     * 重排要用的信号。画像缺失或过期时 [RecommendTasteSignal.upScores] 与
     * [RecommendTasteSignal.watchedBvids] 为空，但负反馈照常带上。
     */
    @JvmStatic
    fun signal(nowMs: Long = System.currentTimeMillis()): RecommendTasteSignal {
        val snap = snapshot()
        val fresh = snap != null && snap.isFresh(nowMs, TTL_MS)
        return RecommendTasteSignal(
            upScores = if (fresh) snap!!.upScores else emptyMap(),
            negative = negative(),
            watchedBvids = if (fresh) snap!!.watchedBvids else emptySet()
        )
    }

    /** 清空画像与负反馈（设置页「重置推荐画像」用）。 */
    @JvmStatic
    fun clear() {
        SharedPreferencesUtil.putString(KEY_SNAPSHOT, "")
        SharedPreferencesUtil.putString(KEY_NEGATIVE, "")
    }
}
