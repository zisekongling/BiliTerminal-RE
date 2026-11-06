package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.VideoCard

/**
 * 首页「推荐」流的分页游标策略（纯逻辑，便于 JVM 单测）。
 *
 * 26.11.06 批次 3（task-16）：原来 `RecommendApi.getRecommend` 只带 `fresh_type`，
 * 翻页没有任何游标——服务端对「参数完全相同的两次请求」返回同一批内容，
 * 所以下拉加载更多只会把同一批卡片再追加一遍（本地 `loadedBvids` 去重后等于翻不动）。
 *
 * 接口实测（2026-11-06，匿名、不带 Cookie/WBI 签名，UA 为桌面 Chrome）：
 * - 相同参数连打两次：30/30 条、bvid 交集 30、顺序一致；
 * - `ps` 生效：ps=5→5 条、ps=20→20 条、不传→30 条；
 * - `fresh_idx` 生效：固定 ps/brush/fresh_type 时，fresh_idx=1/2/3 三批两两交集为 0；
 * - `brush` 单独改**无效**：fresh_idx 固定时 brush=1 与 brush=2 的 bvid 交集为 20（同一批）；
 * - `fresh_type` 单独改**无效**：3/4/不传 的交集为 20（同一批）；
 * - 深翻（fresh_idx=5/10/30/60/120）依然是 `code=0` + 20 条满页，**没有空页信号**。
 *
 * 结论：只下发 `ps` + `fresh_idx`（`fresh_type` 保留原值仅作兼容），**不伪造 `brush`**；
 * 下拉刷新用「page 归 1 + 清空本地 bvid 去重集合」表达，而不是靠一个服务端不认的计数参数。
 *
 * 26.11.07：删掉 `shouldStop`（连同它的单测）。原判据拿「过滤掉广告/番剧卡之后的可用条数」
 * 跟「请求页大小」比，一页 20 条里只要有 1 张非 av 卡就会 19 < 20 被判成到底，
 * 推荐页随之永久停在 bottom = true，既弹「没有更多了」也再也翻不动。
 * 推荐流本来就没有尽头，页面侧不该判到底。
 */
object RecommendPagePolicy {

    /** 第 1 页（`fresh_idx` 从 1 开始，0 与 1 返回同一批）。 */
    const val FIRST_PAGE: Int = 1

    /** 单页条数下限；`ps` 传 0/负数时接口行为未验证，这里直接夹住。 */
    const val MIN_PAGE_SIZE: Int = 1

    /** 单页条数上限：手表端一屏看不了这么多，也避免误传超大值拖慢解析。 */
    const val MAX_PAGE_SIZE: Int = 50

    /** 与现网一致的默认 fresh_type；实测不影响内容，只做兼容。 */
    const val DEFAULT_FRESH_TYPE: Int = 3

    /** 一次请求的分页参数。 */
    data class Params(val page: Int, val pageSize: Int, val freshType: Int)

    /**
     * 构造并夹取分页参数：[page] 最小为 1，[pageSize] 夹在
     * [MIN_PAGE_SIZE]..[MAX_PAGE_SIZE]。
     */
    @JvmStatic
    fun of(page: Int, pageSize: Int, freshType: Int = DEFAULT_FRESH_TYPE): Params =
        Params(
            page = page.coerceAtLeast(FIRST_PAGE),
            pageSize = pageSize.coerceIn(MIN_PAGE_SIZE, MAX_PAGE_SIZE),
            freshType = freshType
        )

    /**
     * 转成 query 参数。用 LinkedHashMap 固定顺序，便于日志复现与单测逐项比对。
     */
    @JvmStatic
    fun query(params: Params): Map<String, String> = linkedMapOf(
        "ps" to params.pageSize.toString(),
        "fresh_idx" to params.page.toString(),
        "fresh_type" to params.freshType.toString()
    )

    /** 下一页页码；非法输入按第 1 页处理后再 +1。 */
    @JvmStatic
    fun nextPage(current: Int): Int = current.coerceAtLeast(FIRST_PAGE) + 1

    /**
     * 按 bvid 去重：保留 [seen] 里没有的卡片，并把它们登记进 [seen]。
     *
     * 空 bvid 直接丢弃（广告卡没有 bvid，不能进列表也不该占位）。
     */
    @JvmStatic
    fun filterNewCards(cards: List<VideoCard>?, seen: MutableSet<String>): List<VideoCard> {
        if (cards.isNullOrEmpty()) return emptyList()
        val fresh = ArrayList<VideoCard>(cards.size)
        for (card in cards) {
            val bvid: String = card.bvid ?: ""
            if (bvid.isEmpty()) continue
            if (seen.add(bvid)) fresh.add(card)
        }
        return fresh
    }
}
