package com.RobinNotBad.BiliClient.util.recommend

import com.RobinNotBad.BiliClient.model.VideoCard

/**
 * 混合推荐（网页源 + APP 源）的合流策略（纯逻辑，便于 JVM 单测）。
 *
 * 口径（用户本轮指定）：**2 条网页推荐 : 1 条 APP 推荐**交替排布，不是简单拼接——
 * 简单拼接会变成「先刷一屏网页的、再刷一屏 APP 的」，同源内容扎堆，等于没混合。
 * 某一侧先耗尽时，剩下的一侧直接按原序补在后面（推荐流本来就是无底流，不允许因为一侧少了
 * 就整页变短）。
 *
 * 去重：按 bvid 全局去重，**网页源优先**（先收网页、再收 APP，APP 里重复的 bvid 直接丢），
 * 因为网页源的字段更全（track_id / 播放数格式与网页侧埋点一致）。空 bvid 的卡片（广告等）
 * 一律丢弃，同 [RecommendPagePolicy.filterNewCards] 的口径。
 */
object RecommendMixer {

    /** 每 [WEB_PER_APP] 条网页推荐后面插 1 条 APP 推荐。 */
    const val WEB_PER_APP: Int = 2

    /**
     * 合流。[web] / [app] 允许为 null 或空（对应「只开一侧」「某一侧请求失败」）。
     *
     * @param webPerApp 网页:APP 的比例分子，非法值按 1 处理（至少穿插，不至于变成纯拼接）
     */
    @JvmStatic
    fun mix(web: List<VideoCard>?, app: List<VideoCard>?, webPerApp: Int = WEB_PER_APP): List<VideoCard> {
        val seen = HashSet<String>()
        val webQueue = java.util.ArrayDeque<VideoCard>()
        val appQueue = java.util.ArrayDeque<VideoCard>()
        collect(web, webQueue, seen)
        collect(app, appQueue, seen)

        val ratio = webPerApp.coerceAtLeast(1)
        val merged = ArrayList<VideoCard>(webQueue.size + appQueue.size)
        while (webQueue.isNotEmpty() || appQueue.isNotEmpty()) {
            var takenFromWeb = 0
            while (takenFromWeb < ratio && webQueue.isNotEmpty()) {
                merged.add(webQueue.removeFirst())
                takenFromWeb++
            }
            if (appQueue.isNotEmpty()) merged.add(appQueue.removeFirst())
        }
        return merged
    }

    private fun collect(source: List<VideoCard>?, target: java.util.ArrayDeque<VideoCard>, seen: MutableSet<String>) {
        if (source.isNullOrEmpty()) return
        for (card in source) {
            val bvid: String = card.bvid ?: ""
            if (bvid.isEmpty()) continue
            if (seen.add(bvid)) target.addLast(card)
        }
    }
}

/**
 * 本地屏蔽作者的过滤（纯逻辑）。
 *
 * 被屏蔽的作者只在本机过滤：命中 [VideoCard.upMid] 的卡片直接不显示，不影响接口请求。
 * [VideoCard.upMid] 为 0（接口没给 mid）时无法判定，按「不屏蔽」放行——宁可多显示一张，
 * 也不能因为取不到 mid 就把整屏内容滤空。
 */
object RecommendFilter {

    @JvmStatic
    fun withoutBlocked(cards: List<VideoCard>?, blockedMids: Set<Long>?): List<VideoCard> {
        if (cards.isNullOrEmpty()) return emptyList()
        if (blockedMids.isNullOrEmpty()) return cards
        val kept = ArrayList<VideoCard>(cards.size)
        for (card in cards) {
            if (card.upMid != 0L && blockedMids.contains(card.upMid)) continue
            kept.add(card)
        }
        return kept
    }
}
