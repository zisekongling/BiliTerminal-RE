package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import org.json.JSONObject
import java.io.IOException

/**
 * 番剧索引 / 推荐 / 动态筛选条件。
 *
 * - 结果接口：`https://api.bilibili.com/pgc/season/index/result`
 * - 条件接口：`https://api.bilibili.com/pgc/season/index/condition`
 *
 * 两个接口都是 **web 端口子，无 WBI**，Cookie 可选（未登录也能拿到完整结果）。
 *
 * 响应字段的两个易错点（26.11.07 实测）：
 * 1. `list[]` 里的 `score` / `order` 都是**字符串**：`score` 是评分（"9.9"，未见过的剧集是 "0.0"），
 *    `order` 是「当前排序指标的展示文案」（order=3 时是 "762.3万追番"，order=4 时是 "9.9分"）。
 *    别按旧文档去解析 `rating` / `styles` 对象，那些字段已经不存在。
 * 2. 筛选**不硬编码**：`condition` 接口下发 `order[]`（排序项，`field` 即 query 值）与
 *    `filter[]`（地区/风格/年份等，每项 `values[].keyword` 才是 query 值）。
 *    年份这类字段的 keyword 是区间串（`[2026,2027)`），必须原样透传，不能自己拼数字。
 */
object BangumiIndexApi {

    private const val INDEX_URL = "https://api.bilibili.com/pgc/season/index/result"
    private const val CONDITION_URL = "https://api.bilibili.com/pgc/season/index/condition"

    /** season_type=1：番剧 */
    const val SEASON_TYPE_BANGUMI = 1

    /**
     * 推荐流固定排序：最多追番。
     *
     * 推荐页刻意**不给筛选**、也不用个性化接口（那个要登录），就是「按追番人数排的番剧流」——
     * 未登录也能看，和索引页的区别只在于条件固定。
     */
    const val ORDER_MOST_FOLLOWED = "3"

    /** 索引默认排序：综合排序（与网页端默认一致） */
    const val ORDER_DEFAULT = "8"

    /** 每页项数。接口定义域 1-∞，20 是网页端默认值。 */
    const val PAGE_SIZE = 20

    /** 「全部」对应的 query 值（各筛选字段通用）。 */
    private const val FILTER_ALL = "-1"

    /** 单个选项：`value` 是 query 值（排序的 field 或筛选的 keyword），`name` 是显示文案。 */
    data class Option(val value: String, val name: String)

    /** 一组筛选条件（地区 / 风格 / 年份 …）。[field] 是 query 参数名。 */
    class FilterGroup(val field: String, val name: String, val options: List<Option>)

    /** 动态筛选条件：[orders] 为排序项，[filters] 为筛选组。 */
    class Condition(val orders: List<Option>, val filters: List<FilterGroup>)

    /** 一页索引结果。[hasNext] 只表示分页状态，失败是抛异常而不是 hasNext=false。 */
    class IndexPage(val cards: List<VideoCard>, val hasNext: Boolean, val total: Int)

    /**
     * 默认筛选参数：全部字段都是「全部」。
     *
     * 键名与接口参数一一对应；请求时先把这份默认值铺上去，再用用户选中的值覆盖，
     * 这样「用户没碰过的字段」不会因为漏传而退化成服务端的另一个默认值（例如 is_finish 默认只给连载）。
     */
    fun defaultFilters(): LinkedHashMap<String, String> = linkedMapOf(
        "season_version" to FILTER_ALL,
        "spoken_language_type" to FILTER_ALL,
        "area" to FILTER_ALL,
        "is_finish" to FILTER_ALL,
        "copyright" to FILTER_ALL,
        "season_status" to FILTER_ALL,
        "season_month" to FILTER_ALL,
        "year" to FILTER_ALL,
        "style_id" to FILTER_ALL
    )

    /** 拉取动态筛选条件（排序项 + 各组筛选项）。 */
    fun getCondition(seasonType: Int = SEASON_TYPE_BANGUMI): Condition {
        val form = NetWorkUtil.FormData()
            .setUrlParam(true)
            .put("season_type", seasonType)
            .put("type", 0)
        return parseCondition(NetWorkUtil.getJson(CONDITION_URL + form))
    }

    /**
     * 纯解析：把 condition 接口响应转成 [Condition]（抽出来是为了能被 JVM 单测覆盖）。
     *
     * @throws IOException code 非 0 或结构缺失：调用方据此跳过筛选栏，但**不能**因此让索引页不可用
     */
    fun parseCondition(root: JSONObject?): Condition {
        if (root == null) throw IOException("筛选条件接口返回为空")
        val code = root.optInt("code", -1)
        if (code != 0) throw IOException("筛选条件接口 code=$code ${root.optString("message")}")
        val data = root.optJSONObject("data") ?: throw IOException("筛选条件接口 data 为空")

        val orders = ArrayList<Option>()
        val orderArray = data.optJSONArray("order")
        if (orderArray != null) {
            for (i in 0 until orderArray.length()) {
                val item = orderArray.optJSONObject(i) ?: continue
                val value = item.optString("field", "")
                if (value.isEmpty()) continue
                orders.add(Option(value, item.optString("name", value)))
            }
        }

        val filters = ArrayList<FilterGroup>()
        val filterArray = data.optJSONArray("filter")
        if (filterArray != null) {
            for (i in 0 until filterArray.length()) {
                val item = filterArray.optJSONObject(i) ?: continue
                val field = item.optString("field", "")
                if (field.isEmpty()) continue
                val options = ArrayList<Option>()
                val values = item.optJSONArray("values")
                if (values != null) {
                    for (j in 0 until values.length()) {
                        val value = values.optJSONObject(j) ?: continue
                        val keyword = value.optString("keyword", "")
                        if (keyword.isEmpty()) continue
                        options.add(Option(keyword, value.optString("name", keyword)))
                    }
                }
                // 没有任何选项的组画出来只是个空行，直接丢掉
                if (options.isEmpty()) continue
                filters.add(FilterGroup(field, item.optString("name", field), options))
            }
        }
        return Condition(orders, filters)
    }

    /**
     * 拉一页索引 / 推荐结果。
     *
     * @param filters 用户选中的筛选条件 field → keyword（见 [Condition.filters]），null 表示全部
     * @param order   排序 field（见 [Condition.orders]），空串按 [ORDER_DEFAULT] 处理
     * @param page    页码，从 1 开始
     * @throws IOException 网络或业务错误：**与「没有下一页」严格区分**，
     *                     否则接口挂掉会被上层误判成到底、页码永久停在半截
     */
    fun getIndex(
        filters: Map<String, String>?,
        order: String?,
        page: Int,
        pageSize: Int = PAGE_SIZE
    ): IndexPage {
        val params = defaultFilters()
        if (filters != null) params.putAll(filters)

        val form = NetWorkUtil.FormData().setUrlParam(true)
        for ((key, value) in params) form.put(key, value)
        form.put("order", if (order.isNullOrEmpty()) ORDER_DEFAULT else order)
            .put("sort", 0)
            .put("page", page)
            .put("season_type", SEASON_TYPE_BANGUMI)
            .put("pagesize", pageSize)
            .put("type", 1)

        return parseIndex(NetWorkUtil.getJson(INDEX_URL + form), page, pageSize)
    }

    /**
     * 纯解析：把结果接口响应转成 [IndexPage]（抽出来是为了能被 JVM 单测覆盖）。
     *
     * `has_next` 与 `total` 两个信号要一起看：接口在最后一页也会给 `has_next=0`，
     * 但深翻到页码超过 total/pagesize 时 `has_next` 可能仍为 1（网页端自己也不信它），
     * 所以再用 `page * pageSize < total` 兜一道。
     */
    fun parseIndex(root: JSONObject?, page: Int, pageSize: Int): IndexPage {
        if (root == null) throw IOException("索引接口返回为空")
        val code = root.optInt("code", -1)
        if (code != 0) throw IOException("索引接口 code=$code ${root.optString("message")}")
        val data = root.optJSONObject("data") ?: throw IOException("索引接口 data 为空")
        val items = data.optJSONArray("list") ?: throw IOException("索引接口 list 为空")

        val cards = ArrayList<VideoCard>(items.length())
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val card = toVideoCard(item)
            if (card != null) cards.add(card)
        }

        val total = data.optInt("total", 0)
        val hasNext = data.optInt("has_next", 0) == 1 &&
            (total <= 0 || page * pageSize < total)
        return IndexPage(cards, hasNext, total)
    }

    /**
     * 单条番剧 → 列表卡片。
     *
     * 卡片只有两个副信息槽（左边带播放图标、右边带头像图标），这里放：
     * 左边 = 评分（拿不到评分时退回当前排序指标的文案，例如「762.3万追番」），
     * 右边 = 更新进度（「全24话」「更新至第13话」）。
     */
    private fun toVideoCard(item: JSONObject): VideoCard? {
        val title = item.optString("title", "")
        if (title.isEmpty()) return null
        var mediaId = item.optLong("media_id", 0L)
        if (mediaId <= 0L) mediaId = item.optLong("season_id", 0L)
        if (mediaId <= 0L) return null

        val score = item.optString("score", "").trim()
        val orderText = item.optString("order", "").trim()
        val indexShow = item.optString("index_show", "").trim()
        val sub = when {
            score.isEmpty() || score == "0.0" -> orderText
            else -> "${score}分"
        }

        val card = VideoCard(
            title,
            indexShow,
            sub,
            item.optString("cover", ""),
            mediaId,
            null
        )
        card.type = "media_bangumi"
        return card
    }
}
