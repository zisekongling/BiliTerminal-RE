package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.VideoCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页推荐分页游标策略的纯逻辑单测。
 *
 * 26.11.06 批次 3（task-16）：接口实测结论（`ps` 生效、`fresh_idx` 是真正的游标、
 * `brush`/`fresh_type` 单独改无效、深翻没有空页信号）都固化在这里，
 * 避免以后被人"顺手"加回无效参数、或改回「等空页才停」。
 *
 * 26.11.07：`shouldStop` 连同它的用例已删除（误判见 `RecommendPagePolicy` 类注释）——
 * 推荐页是无底流，不该判到底。
 */
class RecommendPagePolicyTest {

    private fun card(bvid: String): VideoCard = VideoCard().apply { this.bvid = bvid }

    // ---- 参数构造与夹取 ----

    @Test
    fun of_clampsPageAndPageSize() {
        val params = RecommendPagePolicy.of(page = 0, pageSize = 0, freshType = 3)
        assertEquals("页码最小为 1", 1, params.page)
        assertEquals("页大小最小为 1", 1, params.pageSize)
        assertEquals("fresh_type 原样保留", 3, params.freshType)
    }

    @Test
    fun of_capsPageSizeAtMax() {
        assertEquals("超过上限按 50", 50, RecommendPagePolicy.of(1, 999).pageSize)
    }

    @Test
    fun query_onlySendsPsFreshIdxAndFreshType() {
        val query = RecommendPagePolicy.query(RecommendPagePolicy.of(page = 2, pageSize = 20, freshType = 3))
        assertEquals("参数项数", 3, query.size)
        assertEquals("ps", "20", query["ps"])
        assertEquals("fresh_idx 就是页码", "2", query["fresh_idx"])
        assertEquals("fresh_type 兼容保留", "3", query["fresh_type"])
        assertFalse("实测无效的 brush 不许塞进去", query.containsKey("brush"))
    }

    @Test
    fun query_hasStableKeyOrder() {
        val keys = RecommendPagePolicy.query(RecommendPagePolicy.of(1, 20)).keys.toList()
        assertEquals("顺序固定，便于日志复现", listOf("ps", "fresh_idx", "fresh_type"), keys)
    }

    @Test
    fun nextPage_incrementsAndNormalizesInvalidInput() {
        assertEquals("1 的下一页是 2", 2, RecommendPagePolicy.nextPage(1))
        assertEquals("0 按第一页处理", 2, RecommendPagePolicy.nextPage(0))
        assertEquals("负数按第一页处理", 2, RecommendPagePolicy.nextPage(-5))
        assertEquals("100 的下一页是 101", 101, RecommendPagePolicy.nextPage(100))
    }

    // ---- bvid 去重 ----

    @Test
    fun filterNewCards_keepsOrderAndRegistersSeen() {
        val seen = mutableSetOf<String>()
        val fresh = RecommendPagePolicy.filterNewCards(listOf(card("BV1"), card("BV2")), seen)
        assertEquals("两条都新", listOf("BV1", "BV2"), fresh.map { it.bvid })
        assertEquals("登记进 seen", setOf("BV1", "BV2"), seen)
    }

    @Test
    fun filterNewCards_dedupesAcrossPages() {
        val seen = mutableSetOf<String>()
        RecommendPagePolicy.filterNewCards(listOf(card("BV1"), card("BV2")), seen)
        val second = RecommendPagePolicy.filterNewCards(listOf(card("BV2"), card("BV3")), seen)
        assertEquals("BV2 已在第一页出现", listOf("BV3"), second.map { it.bvid })
    }

    @Test
    fun filterNewCards_dropsEmptyBvidWithoutRegistering() {
        val seen = mutableSetOf<String>()
        val fresh = RecommendPagePolicy.filterNewCards(listOf(card(""), card("BV9")), seen)
        assertEquals("空 bvid 不算卡片", listOf("BV9"), fresh.map { it.bvid })
        assertFalse("空 bvid 不登记", seen.contains(""))
    }

    @Test
    fun filterNewCards_toleratesNullAndEmpty() {
        val seen = mutableSetOf<String>()
        assertTrue("null 返回空列表", RecommendPagePolicy.filterNewCards(null, seen).isEmpty())
        assertTrue("空列表返回空列表", RecommendPagePolicy.filterNewCards(emptyList(), seen).isEmpty())
        assertTrue("什么都没有登记", seen.isEmpty())
    }
}
