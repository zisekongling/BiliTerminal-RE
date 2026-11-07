package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.api.FavoriteApi
import com.RobinNotBad.BiliClient.model.VideoCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 收藏/历史移植批次（task-12）的纯逻辑单测：聚合合并、客户端搜索过滤、批量下载计划。
 *
 * <p>这些都是纯函数，不碰网络也不碰 Android 框架，所以走普通 JVM 单测
 * （`./gradlew.bat :app:testStandardDebugUnitTest`）。
 */
class FavoriteAggregatePolicyTest {

    // ==================== 构造辅助 ====================

    private fun card(aid: Long, title: String = "视频$aid", up: String = "UP主", bvid: String = "BV$aid"): VideoCard =
        VideoCard(title, up, "", "", aid, bvid)

    private fun fav(aid: Long, favTimeSec: Long, title: String = "视频$aid"): FavoriteApi.FavMedia =
        FavoriteApi.FavMedia(card(aid, title), favTimeSec)

    private fun page(mediaId: Long, name: String, vararg items: FavoriteApi.FavMedia): FavoriteAggregatePolicy.FolderPage =
        FavoriteAggregatePolicy.FolderPage(mediaId, name, items.toList())

    private fun failedPage(mediaId: Long, name: String): FavoriteAggregatePolicy.FolderPage =
        FavoriteAggregatePolicy.FolderPage(mediaId, name, emptyList(), failed = true)

    // ==================== FavoriteAggregatePolicy.merge ====================

    @Test
    fun merge_sortsByFavTimeDescThenFolderIdAsc() {
        val result = FavoriteAggregatePolicy.merge(
            listOf(
                page(9, "夹C", fav(3, 100)),
                page(2, "夹A", fav(1, 300)),
                page(5, "夹B", fav(2, 200)),
            )
        )
        assertEquals(listOf(1L, 2L, 3L), result.items.map { it.card.aid })
        assertEquals(3, result.folderCount)
        assertEquals(0, result.failedFolderCount)
    }

    @Test
    fun merge_breaksFavTimeTieByFolderId() {
        val result = FavoriteAggregatePolicy.merge(
            listOf(
                page(7, "夹B", fav(2, 500)),
                page(3, "夹A", fav(1, 500)),
            )
        )
        // 收藏时间相同 → 收藏夹 id 升序兜底
        assertEquals(listOf(1L, 2L), result.items.map { it.card.aid })
        assertEquals(listOf(3L, 7L), result.items.map { it.folderMediaId })
    }

    @Test
    fun merge_dedupesByAidKeepingLaterFavTimeAndFolder() {
        val result = FavoriteAggregatePolicy.merge(
            listOf(
                page(11, "旧夹", fav(42, 100)),
                page(22, "新夹", fav(42, 900)),
            )
        )
        assertEquals(1, result.items.size)
        val item = result.items[0]
        assertEquals(42L, item.card.aid)
        assertEquals(900L, item.favTimeSec)
        // 目录归属跟着「更晚的那次收藏」走
        assertEquals(22L, item.folderMediaId)
        assertEquals("新夹", item.folderName)
    }

    @Test
    fun merge_dedupeKeepsFirstCardWhenLaterFavTimeIsOlder() {
        val newer = card(42, title = "新标题")
        val result = FavoriteAggregatePolicy.merge(
            listOf(
                FavoriteAggregatePolicy.FolderPage(11, "旧夹", listOf(FavoriteApi.FavMedia(newer, 900))),
                page(22, "新夹", fav(42, 100)),
            )
        )
        assertEquals(1, result.items.size)
        // 保留已有的那条卡片对象（更晚收藏的那次），只更新时间对比更早的重复项
        assertSame(newer, result.items[0].card)
        assertEquals(900L, result.items[0].favTimeSec)
    }

    @Test
    fun merge_skipsFailedPagesButCountsThem() {
        val result = FavoriteAggregatePolicy.merge(
            listOf(
                page(1, "好夹", fav(1, 100)),
                failedPage(2, "坏夹"),
                page(3, "好夹2", fav(3, 300)),
            )
        )
        assertEquals(listOf(3L, 1L), result.items.map { it.card.aid })
        // 失败的夹一条都没贡献，不能算进「来自 N 个收藏夹」；两个数相加才是请求过的夹数
        assertEquals(2, result.folderCount)
        assertEquals(1, result.failedFolderCount)
    }

    @Test
    fun merge_skipsDirtyAid() {
        val result = FavoriteAggregatePolicy.merge(
            listOf(page(1, "夹", fav(0, 100), fav(-5, 200), fav(8, 300)))
        )
        assertEquals(listOf(8L), result.items.map { it.card.aid })
    }

    @Test
    fun merge_truncatesToMaxItems() {
        val pages = listOf(page(1, "夹", fav(1, 300), fav(2, 200), fav(3, 100)))
        assertEquals(2, FavoriteAggregatePolicy.merge(pages, maxItems = 2).items.size)
        assertTrue(FavoriteAggregatePolicy.merge(pages, maxItems = 0).items.isEmpty())
        assertTrue(FavoriteAggregatePolicy.merge(pages, maxItems = -1).items.isEmpty())
    }

    @Test
    fun merge_emptyInputYieldsEmptyResult() {
        val result = FavoriteAggregatePolicy.merge(emptyList())
        assertTrue(result.items.isEmpty())
        assertEquals(0, result.folderCount)
        assertEquals(0, result.failedFolderCount)
    }

    @Test
    fun dedupeKey_separatesVideoAudioAndCollection() {
        val video = card(5).also { it.favType = FavoriteApi.FAV_TYPE_VIDEO }
        val audio = card(5).also { it.favType = FavoriteApi.FAV_TYPE_AUDIO }
        val collection = card(5).also { it.favType = FavoriteApi.FAV_TYPE_COLLECTION }
        assertEquals("av:5", FavoriteAggregatePolicy.dedupeKey(video))
        assertEquals("au:5", FavoriteAggregatePolicy.dedupeKey(audio))
        assertEquals("ss:5", FavoriteAggregatePolicy.dedupeKey(collection))
    }

    @Test
    fun merge_doesNotCollideDifferentFavTypesWithSameNumericId() {
        val video = card(5).also { it.favType = FavoriteApi.FAV_TYPE_VIDEO }
        val audio = card(5).also { it.favType = FavoriteApi.FAV_TYPE_AUDIO }
        val result = FavoriteAggregatePolicy.merge(
            listOf(
                FavoriteAggregatePolicy.FolderPage(
                    1, "夹",
                    listOf(FavoriteApi.FavMedia(video, 100), FavoriteApi.FavMedia(audio, 200))
                )
            )
        )
        assertEquals(2, result.items.size)
    }

    // ==================== FavoriteSearchPolicy ====================

    @Test
    fun normalize_trimsAndTreatsNullAsEmpty() {
        assertEquals("", FavoriteSearchPolicy.normalize(null))
        assertEquals("", FavoriteSearchPolicy.normalize("   "))
        assertEquals("abc", FavoriteSearchPolicy.normalize("  abc  "))
    }

    @Test
    fun matches_hitsTitleOrUpNameIgnoringCase() {
        val c = card(1, title = "Bilibili 纪录片", up = "籽岷")
        assertTrue(FavoriteSearchPolicy.matches(c, "纪录片"))
        assertTrue(FavoriteSearchPolicy.matches(c, "bilibili"))
        assertTrue(FavoriteSearchPolicy.matches(c, "籽岷"))
        assertFalse(FavoriteSearchPolicy.matches(c, "不存在的关键词"))
    }

    @Test
    fun matches_emptyKeywordAlwaysTrue() {
        val c = card(1)
        assertTrue(FavoriteSearchPolicy.matches(c, ""))
        assertTrue(FavoriteSearchPolicy.matches(c, "   "))
    }

    @Test
    fun filter_emptyKeywordReturnsSameListInstance() {
        val cards = listOf(card(1), card(2))
        assertSame(cards, FavoriteSearchPolicy.filter(cards, ""))
        assertSame(cards, FavoriteSearchPolicy.filter(cards, "  "))
    }

    @Test
    fun filter_keepsMatchingOrder() {
        val cards = listOf(card(1, title = "猫"), card(2, title = "狗"), card(3, title = "猫咪"))
        assertEquals(listOf(1L, 3L), FavoriteSearchPolicy.filter(cards, "猫").map { it.aid })
    }

    // ==================== FavoriteBatchDownloadPolicy ====================

    @Test
    fun resolveQuality_mapsSettings() {
        assertEquals(FavoriteBatchDownloadPolicy.Quality.Highest, FavoriteBatchDownloadPolicy.resolveQuality(null))
        assertEquals(FavoriteBatchDownloadPolicy.Quality.Highest, FavoriteBatchDownloadPolicy.resolveQuality(""))
        assertEquals(FavoriteBatchDownloadPolicy.Quality.Highest, FavoriteBatchDownloadPolicy.resolveQuality("dialog"))
        assertEquals(FavoriteBatchDownloadPolicy.Quality.Highest, FavoriteBatchDownloadPolicy.resolveQuality("HIGHEST"))
        assertEquals(FavoriteBatchDownloadPolicy.Quality.AudioOnly, FavoriteBatchDownloadPolicy.resolveQuality("audio_only"))
        assertEquals(FavoriteBatchDownloadPolicy.Quality.Fixed(64), FavoriteBatchDownloadPolicy.resolveQuality("64"))
        assertEquals(FavoriteBatchDownloadPolicy.Quality.Fixed(80), FavoriteBatchDownloadPolicy.resolveQuality(" 80 "))
        // 解析失败回落到 FALLBACK_QN，而不是抛异常
        assertEquals(
            FavoriteBatchDownloadPolicy.Quality.Fixed(FavoriteBatchDownloadPolicy.FALLBACK_QN),
            FavoriteBatchDownloadPolicy.resolveQuality("极清")
        )
    }

    @Test
    fun plan_skipsUnsupportedTypesAndBangumi() {
        val video = card(1)
        val bangumi = card(2).also { it.type = "media_bangumi" }
        val audio = card(3).also { it.favType = FavoriteApi.FAV_TYPE_AUDIO }
        val zeroAid = card(0)
        val plan = FavoriteBatchDownloadPolicy.plan(listOf(video, bangumi, audio, zeroAid))
        assertEquals(listOf(1L), plan.cards.map { it.aid })
        assertEquals(3, plan.skippedUnsupported)
        assertEquals(0, plan.skippedDuplicate)
        assertEquals(0, plan.skippedOverLimit)
    }

    @Test
    fun plan_dedupesByAid() {
        val plan = FavoriteBatchDownloadPolicy.plan(listOf(card(7), card(7), card(8)))
        assertEquals(listOf(7L, 8L), plan.cards.map { it.aid })
        assertEquals(1, plan.skippedDuplicate)
    }

    @Test
    fun plan_truncatesToMaxAndCountsOverLimit() {
        val selected = (1L..25L).map { card(it) }
        val plan = FavoriteBatchDownloadPolicy.plan(selected, max = FavoriteBatchDownloadPolicy.MAX_BATCH)
        assertEquals(FavoriteBatchDownloadPolicy.MAX_BATCH, plan.cards.size)
        assertEquals(5, plan.skippedOverLimit)
        // 按输入顺序截断，不重排
        assertEquals(1L, plan.cards.first().aid)
        assertEquals(FavoriteBatchDownloadPolicy.MAX_BATCH.toLong(), plan.cards.last().aid)
    }

    @Test
    fun plan_maxZeroQueuesNothing() {
        val plan = FavoriteBatchDownloadPolicy.plan(listOf(card(1), card(2)), max = 0)
        assertTrue(plan.cards.isEmpty())
        assertEquals(2, plan.skippedOverLimit)
    }

    @Test
    fun isDownloadable_requiresPositiveAidVideoTypeAndNotBangumi() {
        assertTrue(FavoriteBatchDownloadPolicy.isDownloadable(card(1)))
        assertFalse(FavoriteBatchDownloadPolicy.isDownloadable(card(0)))
        assertFalse(FavoriteBatchDownloadPolicy.isDownloadable(card(1).also { it.type = "media_bangumi" }))
        assertFalse(
            FavoriteBatchDownloadPolicy.isDownloadable(
                card(1).also { it.favType = FavoriteApi.FAV_TYPE_COLLECTION }
            )
        )
    }
}
