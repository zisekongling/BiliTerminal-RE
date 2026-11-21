package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.api.FavoriteApi
import com.RobinNotBad.BiliClient.model.VideoCard

/**
 * 跨夹「全部收藏」聚合的**纯计算**部分（不含任何网络与 UI，便于 JVM 单测）。
 *
 * <p>背景：B 站没有跨收藏夹聚合接口。`x/v3/fav/resource/list?type=1`（文档里写作「全部收藏夹」）
 * 在未登录 / 访问他人收藏夹的场景实测直接返回 `code=11010 您访问的内容不存在`，所以聚合页的
 * 做法是「每个收藏夹各取第一页 → 本地合并去重排序」。夹数与条数都设上限，避免一次打太多请求。
 *
 * <p>合并规则（与 [merge] 的 KDoc 一致）：
 * - 失败的收藏夹整页跳过，但计入 [Result.failedFolderCount]；
 * - `aid <= 0` 的脏数据跳过；
 * - 去重键按收藏内容类型区分：视频 `av:` / 音频 `au:` / 合集 `ss:`，避免不同类型撞同一个数字 id；
 * - 同一内容出现在多个夹时保留**收藏时间更晚**的那条（目录归属也跟着那条走）；
 * - 排序：收藏时间降序 → 收藏夹 id 升序（稳定兜底），最后截断到上限。
 */
object FavoriteAggregatePolicy {
    /** 聚合时最多读取的收藏夹个数（每个夹只取第一页，20 条） */
    const val DEFAULT_MAX_FOLDERS = 10

    /** 聚合结果最多展示的条数 */
    const val DEFAULT_MAX_ITEMS = 100

    /** 一个收藏夹的读取结果；failed=true 表示这一页没取到（网络/权限/接口错误） */
    data class FolderPage(
        val folderMediaId: Long,
        val folderName: String,
        val items: List<FavoriteApi.FavMedia>,
        val failed: Boolean = false,
    )

    /** 聚合后的一条：卡片 + 收藏时间（秒）+ 来源收藏夹 */
    data class AggregateItem(
        val card: VideoCard,
        val favTimeSec: Long,
        val folderMediaId: Long,
        val folderName: String,
    )

    /** 聚合结果；folderCount 是**真正取到内容**的收藏夹数，failedFolderCount 是读取失败的个数（两者相加才是请求过的夹数） */
    data class Result(
        val items: List<AggregateItem>,
        val folderCount: Int,
        val failedFolderCount: Int,
    )

    /**
     * 去重键。视频稿件 / 音频 / 合集的 id 空间是各自独立的，
     * 只用 aid 去重会把「avid=123 的视频」和「auid=123 的音频」误判成同一条。
     */
    fun dedupeKey(card: VideoCard): String = when (card.favType) {
        FavoriteApi.FAV_TYPE_AUDIO -> "au:" + card.aid
        FavoriteApi.FAV_TYPE_COLLECTION -> "ss:" + card.aid
        else -> "av:" + card.aid
    }

    /**
     * 合并多个收藏夹的第一页内容。
     *
     * @param pages    各收藏夹的读取结果（顺序即读取顺序，用于稳定排序兜底）
     * @param maxItems 结果上限，<=0 表示一条都不要
     */
    fun merge(pages: List<FolderPage>, maxItems: Int = DEFAULT_MAX_ITEMS): Result {
        val merged = LinkedHashMap<String, AggregateItem>()
        for (page in pages) {
            if (page.failed) continue
            for (media in page.items) {
                val card = media.card ?: continue
                if (card.aid <= 0) continue
                val key = dedupeKey(card)
                val existing = merged[key]
                if (existing == null) {
                    merged[key] = AggregateItem(card, media.favTimeSec, page.folderMediaId, page.folderName)
                } else if (media.favTimeSec > existing.favTimeSec) {
                    // 同一内容在多个夹里都有时，目录归属跟着「更晚的那次收藏」走
                    merged[key] = existing.copy(
                        favTimeSec = media.favTimeSec,
                        folderMediaId = page.folderMediaId,
                        folderName = page.folderName,
                    )
                }
            }
        }
        val sorted = merged.values.sortedWith(
            compareByDescending<AggregateItem> { it.favTimeSec }.thenBy { it.folderMediaId }
        )
        // 失败的夹一条都没贡献，不能算进「来自 N 个收藏夹」里，否则汇总文案会自相矛盾
        val failedFolderCount = pages.count { it.failed }
        return Result(
            items = sorted.take(maxItems.coerceAtLeast(0)),
            folderCount = pages.size - failedFolderCount,
            failedFolderCount = failedFolderCount,
        )
    }
}

/**
 * 历史记录等「服务端不支持关键词」场景的客户端过滤（纯函数）。
 *
 * <p>历史接口（`x/web-interface/history/cursor`）的参数只有 max/business/view_at/type/ps，
 * 没有 keyword/q（见 `bilibili-API/docs/historytoview/history.md:17-23`），所以只能在
 * 已加载的记录里过滤，界面上要如实说明搜索范围。
 */
object FavoriteSearchPolicy {
    /** 统一口径：去掉首尾空白；null 视作空 */
    fun normalize(keyword: String?): String = keyword?.trim().orEmpty()

    /** 标题或 UP 名包含关键词（忽略大小写）即命中；空关键词恒为 true */
    fun matches(card: VideoCard, keyword: String): Boolean {
        val normalized = normalize(keyword)
        if (normalized.isEmpty()) return true
        if (card.title?.contains(normalized, ignoreCase = true) == true) return true
        if (card.upName?.contains(normalized, ignoreCase = true) == true) return true
        return false
    }

    /** 过滤列表；空关键词原样返回（不新建列表） */
    fun filter(cards: List<VideoCard>, keyword: String): List<VideoCard> {
        val normalized = normalize(keyword)
        if (normalized.isEmpty()) return cards
        return cards.filter { matches(it, normalized) }
    }
}

/**
 * 收藏夹多选「批量下载」的纯计算部分。
 *
 * <p>不重新实现下载：真正入队沿用 `VideoQuickCache` 用的那组原语
 * （`PlayerApi.getVideo` / `getVideoDash` / `startDownloading` / `startDownloadingAudioOnly`），
 * 这里只决定「哪些能下、按什么清晰度下、最多下多少」。
 */
object FavoriteBatchDownloadPolicy {
    /** 一次批量下载最多提交的条数（每条约一次视频信息请求 + 一次下载，太多会长时间并发打接口） */
    const val MAX_BATCH = 20

    /** 清晰度设置读不到 / 解析失败时的兜底：64 = 720P（与 VideoQuickCache 的兜底一致） */
    const val FALLBACK_QN = 64

    /** 批量下载的清晰度策略（设置项 cache_default_quality 的语义映射） */
    sealed class Quality {
        /** 最高可用清晰度（含设置项为最高，或为 dialog 但批量场景无法逐条弹窗） */
        object Highest : Quality()

        /** 仅音频 */
        object AudioOnly : Quality()

        /** 固定清晰度 */
        data class Fixed(val qn: Int) : Quality()
    }

    /** 下载计划：真正要提交的卡片 + 三类被跳过的原因计数（用于汇总提示） */
    data class Plan(
        val cards: List<VideoCard>,
        val skippedUnsupported: Int,
        val skippedDuplicate: Int,
        val skippedOverLimit: Int,
    )

    /**
     * 把 `cache_default_quality` 的原始值映射成批量下载的清晰度策略。
     *
     * <p>`dialog` 在单条下载时会弹清晰度选择框，批量场景不能逐条弹，
     * 所以按「最高可用」处理（这也是多数用户对批量下载的预期）。
     */
    fun resolveQuality(rawSetting: String?): Quality {
        val raw = rawSetting?.trim()?.lowercase().orEmpty()
        return when {
            raw.isEmpty() || raw == "dialog" || raw == "highest" -> Quality.Highest
            raw == "audio_only" -> Quality.AudioOnly
            else -> Quality.Fixed(raw.toIntOrNull() ?: FALLBACK_QN)
        }
    }

    /** 能否走视频下载：aid 有效、内容是视频稿件、不是番剧（番剧下载走另一套，见 VideoQuickCache 的拦截） */
    fun isDownloadable(card: VideoCard): Boolean =
        card.aid > 0 && card.favType == FavoriteApi.FAV_TYPE_VIDEO && card.type != "media_bangumi"

    /**
     * 生成下载计划：跳过不支持的、按 aid 去重、截断到上限。
     *
     * @param max 上限，<=0 表示一条都不下
     */
    fun plan(selected: List<VideoCard>, max: Int = MAX_BATCH): Plan {
        val limit = max.coerceAtLeast(0)
        val seen = HashSet<Long>()
        val cards = ArrayList<VideoCard>()
        var unsupported = 0
        var duplicate = 0
        var overLimit = 0
        for (card in selected) {
            if (!isDownloadable(card)) {
                unsupported++
                continue
            }
            if (!seen.add(card.aid)) {
                duplicate++
                continue
            }
            if (cards.size >= limit) {
                overLimit++
                continue
            }
            cards.add(card)
        }
        return Plan(cards, unsupported, duplicate, overLimit)
    }
}
