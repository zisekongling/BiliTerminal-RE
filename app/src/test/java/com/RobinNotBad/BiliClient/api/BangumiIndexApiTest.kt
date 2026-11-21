package com.RobinNotBad.BiliClient.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 番剧索引 / 推荐 / 筛选条件解析测试。
 *
 * 这四条解析路径决定「番剧分区」三个页签能不能出内容，而且两个接口的响应字段跟旧文档已经对不上
 * （`score` / `order` 是字符串、`rating` / `styles` 对象已不存在），所以固定住几个关键判定：
 * 1. 筛选项来自 `condition` 接口下发（不是硬编码），缺 `keyword` / 缺 `values` 的组要被丢掉；
 * 2. 卡片的 media_id 缺失时退回 season_id（否则点进去是空详情页）；
 * 3. 评分是 "0.0"（还没开分）时，副标题退回当前排序指标的文案，不能显示「0.0分」；
 * 4. `has_next` 与 `total` 要一起判到底，接口报错必须抛异常（不能被当成「没有下一页」）。
 *
 * 只测纯解析，不发网络请求。
 */
class BangumiIndexApiTest {

    private fun indexRoot(list: String, hasNext: Int = 1, total: Int = 100, code: Int = 0): JSONObject =
        JSONObject("""{"code":$code,"message":"success","data":{"has_next":$hasNext,"num":1,"size":20,"total":$total,"list":[$list]}}""")

    private fun conditionRoot(code: Int = 0): JSONObject = JSONObject(
        """
        {"code":$code,"message":"success","data":{
          "order":[{"field":"8","name":"综合排序"},{"field":"3","name":"最多追番"}],
          "filter":[
            {"field":"area","name":"地区","values":[{"keyword":"-1","name":"全部地区"},{"keyword":"2","name":"日本"}]},
            {"field":"style_id","name":"风格","values":[{"keyword":"-1","name":"全部风格"},{"keyword":"10010","name":"原创"}]},
            {"field":"empty_group","name":"空组","values":[]}
          ]
        }}
        """.trimIndent()
    )

    @Test
    fun parseCondition_读出排序项与筛选组() {
        val condition = BangumiIndexApi.parseCondition(conditionRoot())

        assertEquals(listOf("8", "3"), condition.orders.map { it.value })
        assertEquals("综合排序", condition.orders[0].name)
        // 没有 values 的组被丢掉，只留两组
        assertEquals(listOf("area", "style_id"), condition.filters.map { it.field })
        assertEquals("地区", condition.filters[0].name)
        assertEquals(listOf("-1", "2"), condition.filters[0].options.map { it.value })
        assertEquals("日本", condition.filters[0].options[1].name)
    }

    @Test
    fun parseCondition_接口报错时抛异常() {
        val error = runCatching { BangumiIndexApi.parseCondition(conditionRoot(code = -412)) }.exceptionOrNull()
        assertTrue("应该是 IOException，实际是 $error", error is IOException)
    }

    @Test
    fun parseIndex_卡片字段映射() {
        val page = BangumiIndexApi.parseIndex(
            indexRoot(
                """
                {"title":"葬送的芙莉莲","cover":"https://i0.hdslb.com/x.png","media_id":28223483,
                 "season_id":46089,"score":"9.9","index_show":"全28话","order":"762.3万追番"},
                {"title":"还没开分的番","cover":"https://i0.hdslb.com/y.png","season_id":123456,
                 "score":"0.0","index_show":"更新至第3话","order":"全站首发"}
                """.trimIndent()
            ),
            page = 1,
            pageSize = 20
        )

        assertEquals(2, page.cards.size)
        val first = page.cards[0]
        assertEquals("葬送的芙莉莲", first.title)
        assertEquals("9.9分", first.view)          // 左边副信息：评分
        assertEquals("全28话", first.upName)        // 右边副信息：更新进度
        assertEquals(28223483L, first.aid)          // 点进详情页要用 media_id
        assertEquals("media_bangumi", first.type)

        val second = page.cards[1]
        // 没开分（"0.0"）时不能显示「0.0分」，退回排序指标文案
        assertEquals("全站首发", second.view)
        // media_id 缺失时退回 season_id，否则点进去是空详情页
        assertEquals(123456L, second.aid)
    }

    @Test
    fun parseIndex_没有有效条目的卡片被跳过() {
        val page = BangumiIndexApi.parseIndex(
            indexRoot(
                """
                {"title":"","media_id":1,"score":"9.0"},
                {"title":"没有 id 的条目","score":"9.0"}
                """.trimIndent()
            ),
            page = 1,
            pageSize = 20
        )
        assertTrue(page.cards.isEmpty())
    }

    @Test
    fun parseIndex_到底判据要同时看has_next与total() {
        // 还有下一页：has_next=1 且 page*pagesize < total
        val firstPage = BangumiIndexApi.parseIndex(indexRoot("[]", hasNext = 1, total = 100), 1, 20)
        assertTrue(firstPage.hasNext)
        assertEquals(100, firstPage.total)

        // 最后一页：total 说没有更多了，即使 has_next 还是 1
        val lastPage = BangumiIndexApi.parseIndex(indexRoot("[]", hasNext = 1, total = 100), 5, 20)
        assertFalse(lastPage.hasNext)

        // 服务端明确说没有下一页
        val endPage = BangumiIndexApi.parseIndex(indexRoot("[]", hasNext = 0, total = 100), 1, 20)
        assertFalse(endPage.hasNext)

        // total 缺失（0）时只能信 has_next
        val unknownTotal = BangumiIndexApi.parseIndex(indexRoot("[]", hasNext = 1, total = 0), 7, 20)
        assertTrue(unknownTotal.hasNext)
    }

    @Test
    fun parseIndex_接口报错时抛异常而不是返回空页() {
        // 关键差异：报错必须抛，调用方才能提示「加载失败」；返回空页会被当成「到底了」，
        // 页码永久停在半截且用户看不到任何提示
        val error = runCatching {
            BangumiIndexApi.parseIndex(indexRoot("[]", code = -412), 1, 20)
        }.exceptionOrNull()
        assertTrue("应该是 IOException，实际是 $error", error is IOException)
    }

    @Test
    fun defaultFilters_九个字段全是全部() {
        val filters = BangumiIndexApi.defaultFilters()
        assertEquals(9, filters.size)
        assertTrue(filters.values.all { it == "-1" })
        assertTrue("area" in filters)
        assertTrue("year" in filters)
        assertTrue("style_id" in filters)
    }
}
