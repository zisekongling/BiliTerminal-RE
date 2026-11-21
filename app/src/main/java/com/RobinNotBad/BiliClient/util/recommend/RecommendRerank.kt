package com.RobinNotBad.BiliClient.util.recommend

import com.RobinNotBad.BiliClient.model.VideoCard
import kotlin.math.pow
import kotlin.math.tanh

/**
 * X 式重排（对齐 github.com/jiuyang5354/bilibili-margin 的 `src/content/rank.ts`）。
 *
 * 三段式，公式与原实现逐项对齐：
 *
 * 1. **过滤**：画像里「已看过」的 bvid 直接剔除（推荐流里刷到刚看完的视频是最大的噪声）。
 * 2. **打分**：先验取 B 站原顺序 `s = 1 - (i / n) * 0.5`（第 1 张 1.0，最后一张 ~0.5，
 *    保留平台排序但不让它一锤定音）；再叠加 `+ 0.6 * tanh(up / 2)`（越常看越前，tanh 防单个
 *    UP 霸榜）与 `- 0.8 * 负反馈次数`。
 * 3. **多样性贪心**：每轮取调整后分数最高的一张；「最近 3 张里出现过的 UP」扣 0.5，
 *    本批里每重复出现一次再乘 0.6。同一个 UP 因此不会连着出，也不会一屏全是它。
 *
 * 纯函数，不读 SP / 不碰网络：画像在 [RecommendTasteStore] 里准备好后传进来，便于单测。
 */
object RecommendRerank {

    /** 先验的压降幅度：最后一张只保留 (1 - 0.5) = 0.5 分。 */
    const val PRIOR_SPAN: Double = 0.5

    /** 偏好分权重。 */
    const val TASTE_WEIGHT: Double = 0.6

    /** 负反馈权重。 */
    const val NEGATIVE_WEIGHT: Double = 0.8

    /** tanh 的缩放分母（参考实现 `tanh(up / 2)`）。 */
    const val TASTE_SCALE: Double = 2.0

    /** 「最近出现过的 UP」扣分。 */
    const val RECENT_REPEAT_PENALTY: Double = 0.5

    /** 本批里重复出现的 UP，每多一次乘这个系数。 */
    const val BATCH_REPEAT_FACTOR: Double = 0.6

    /** 「最近 3 张」这个窗口的大小。 */
    const val RECENT_WINDOW: Int = 3

    /**
     * 重排一批推荐。
     *
     * @param cards          原始一批（顺序有意义：它就是先验的来源）
     * @param signal         画像信号；null 按 [RecommendTasteSignal.EMPTY] 处理
     * @param recentUpMids   上一批最后几张卡的 upMid（跨批次保持「同一 UP 不连着出」）
     * @return 重排后的新列表（不修改入参）；被剔除的卡片直接消失
     */
    @JvmStatic
    fun rerank(
        cards: List<VideoCard>?,
        signal: RecommendTasteSignal? = null,
        recentUpMids: List<Long>? = null
    ): List<VideoCard> {
        if (cards.isNullOrEmpty()) return emptyList()
        val taste = signal ?: RecommendTasteSignal.EMPTY
        val prior = cards.size.coerceAtLeast(1)

        val pool = ArrayList<Scored>(cards.size)
        for (i in cards.indices) {
            val card = cards[i]
            val bvid: String = card.bvid ?: ""
            if (bvid.isNotEmpty() && taste.watchedBvids.contains(bvid)) continue
            val upMid = card.upMid
            var score = 1.0 - (i.toDouble() / prior) * PRIOR_SPAN
            if (upMid != 0L) {
                score += TASTE_WEIGHT * tanh(taste.upScore(upMid) / TASTE_SCALE)
                score -= NEGATIVE_WEIGHT * taste.negativeOf(upMid)
            }
            pool.add(Scored(card, score))
        }
        if (pool.isEmpty()) return emptyList()

        val recent = java.util.ArrayDeque<Long>()
        recentUpMids?.takeLast(RECENT_WINDOW)?.forEach { if (it != 0L) recent.addLast(it) }
        val picked = HashMap<Long, Int>()
        val out = ArrayList<VideoCard>(pool.size)

        while (pool.isNotEmpty()) {
            var bestIndex = 0
            var bestScore = Double.NEGATIVE_INFINITY
            for (index in pool.indices) {
                val upMid = pool[index].card.upMid
                var score = pool[index].base
                if (upMid != 0L) {
                    if (recent.contains(upMid)) score -= RECENT_REPEAT_PENALTY
                    score *= BATCH_REPEAT_FACTOR.pow((picked[upMid] ?: 0).toDouble())
                }
                if (score > bestScore) {
                    bestScore = score
                    bestIndex = index
                }
            }
            val chosen = pool.removeAt(bestIndex)
            out.add(chosen.card)
            val upMid = chosen.card.upMid
            if (upMid != 0L) {
                picked[upMid] = (picked[upMid] ?: 0) + 1
                recent.addLast(upMid)
                if (recent.size > RECENT_WINDOW) recent.removeFirst()
            }
        }
        return out
    }

    private class Scored(val card: VideoCard, val base: Double)
}
