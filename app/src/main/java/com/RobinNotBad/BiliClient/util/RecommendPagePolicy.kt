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
     * 下拉刷新时游标往后跳的最大步数。
     *
     * 26.11.21（反馈 #248「登录了推荐还是很多重复」）：接口对「参数完全相同的两次请求」返回同一批
     * （26.11.06 实测：连打两次 30/30 条、bvid 交集 30、顺序一致），而进程内的 uniq_id 又是固定的，
     * 所以刷新如果照旧把 `fresh_idx` 归 1，拿回来的就是刚才那一批——用户看到的就是「刷了还是这些」。
     * 把游标往后推才是真正换一批（深翻没有空页信号，见类注释）。
     */
    const val REFRESH_STEP_MAX: Int = 8

    /**
     * 刷新后的起始页：在当前页之后跳 [step] 页，[step] 夹在 1..[REFRESH_STEP_MAX]。
     *
     * 负数/0 也按 1 步算：刷新**必须**换一批，不能退回原页。
     */
    @JvmStatic
    fun refreshPage(current: Int, step: Int): Int =
        current.coerceAtLeast(FIRST_PAGE) + step.coerceIn(1, REFRESH_STEP_MAX)

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

    /**
     * 「同 UP + 同标题」的二次判重键（反馈 #248）。
     *
     * bvid 只能挡住「同一条视频被服务端重复下发」；同一个 UP 把同一条内容重投（换 bvid）、
     * 或者推荐池里换了稿件号再推一次，bvid 全不一样，用户看到的就是「又是这个视频」。
     *
     * UP 未知（`owner.mid` 缺失 → [VideoCard.upMid] = 0）时返回 null：不同 UP 撞同名视频太常见，
     * 拿标题当全局键会把不相干的内容也滤掉。
     */
    @JvmStatic
    fun titleKey(card: VideoCard?): String? {
        if (card == null) return null
        if (card.upMid <= 0L) return null
        val title: String = card.title?.trim() ?: ""
        if (title.isEmpty()) return null
        return card.upMid.toString() + "|" + title
    }

    /**
     * bvid + (UP, 标题) 双重判重：两个集合都会登记命中的卡片。
     *
     * 顺序与 [filterNewCards] 的单键版一致，两个集合都由调用方持有（刷新换批时自行 clear）。
     */
    @JvmStatic
    fun filterNewCards(
        cards: List<VideoCard>?,
        seen: MutableSet<String>,
        seenTitles: MutableSet<String>
    ): List<VideoCard> {
        if (cards.isNullOrEmpty()) return emptyList()
        val fresh = ArrayList<VideoCard>(cards.size)
        for (card in cards) {
            val bvid: String = card.bvid ?: ""
            if (bvid.isEmpty()) continue
            if (bvid in seen) continue
            val key = titleKey(card)
            if (key != null && key in seenTitles) continue
            seen.add(bvid)
            if (key != null) seenTitles.add(key)
            fresh.add(card)
        }
        return fresh
    }
}
