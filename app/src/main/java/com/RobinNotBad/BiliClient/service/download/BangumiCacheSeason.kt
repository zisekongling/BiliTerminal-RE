package com.RobinNotBad.BiliClient.service.download

import com.RobinNotBad.BiliClient.model.Bangumi

/**
 * 番剧缓存入队的一次「季」载荷：缓存页勾好一批集之后，按季拼成它交给下载服务。
 *
 * 缓存页允许跨季混选，所以入队参数是 `List<BangumiCacheSeason>`——一季一条，
 * 每条最终都落成「番剧名-季名当父目录、集名当子目录」的多个下载任务。
 *
 * **父目录必须带番剧名**（26.11.18 修）：B 站的季名是「TV」「第二季」这种通用词，
 * 只用季名当目录时《孤独摇滚！》和《魔女之旅》的「TV」季会落进同一个目录，
 * 于是缓存页的「已下载」互相串台、后下载的那部直接把先下载的成品覆盖掉。
 * 目录名由 [seasonFolderName] 统一算，下载服务与缓存页共用。
 *
 * @param bangumiTitle 番剧名，与季名一起拼父目录
 * @param seasonTitle 季名，与番剧名一起拼父目录
 * @param seasonCover 季封面；某集自己没有封面时兜底用它
 * @param seasonId    季 id，落 epid / season_id / season_type 三列用
 * @param seasonType  季类型（1=番剧、2=电影…），0 表示未知，交给调用方兜底
 * @param episodes    用户勾选的剧集，按详情页顺序
 */
class BangumiCacheSeason(
    @JvmField val bangumiTitle: String,
    @JvmField val seasonTitle: String,
    @JvmField val seasonCover: String,
    @JvmField val seasonId: Long,
    @JvmField val seasonType: Int,
    @JvmField val episodes: List<Bangumi.Episode>
) {
    companion object {
        /**
         * 这一季落盘时用的父目录名（= 下载表 title 列 = 缓存目录名）。
         *
         * 规则是「番剧名 - 季名」，两种情况退化成单边，避免出现「孤独摇滚！ - 孤独摇滚！」这种重复：
         * - 季名为空，或季名与番剧名相同（单季番常把番剧名当季名）→ 只留番剧名
         * - 番剧名为空（拿不到标题的调用方）→ 退回只用季名，与修这个 bug 之前的行为一致
         *
         * 与 [childNameOf] 同理，下载服务和缓存页**必须用同一个函数算**：缓存页要靠
         * 「这个目录名 + 集名」推算目录、判断这一集是不是已经下过。两边各写一套，
         * 改完一边就会出现「明明下过了却还让勾」，或者更糟——两部番的季互相覆盖。
         */
        fun seasonFolderName(bangumiTitle: String, seasonTitle: String): String {
            val bangumi = bangumiTitle.trim()
            val season = seasonTitle.trim()
            return when {
                bangumi.isEmpty() -> season
                season.isEmpty() || season == bangumi -> bangumi
                else -> "$bangumi - $season"
            }
        }

        /**
         * 这一集落盘时用的子目录名（= 下载表 child 列）。
         *
         * 下载服务和缓存页**必须用同一个函数算**：缓存页要靠「季名 + 这个名字」推算目录、
         * 判断这一集是不是已经下载过。两边各写一套，改完一边就会出现「明明下过了却还让勾」。
         *
         * @param unknown 标题全空时的兜底文案（调用方传本地化字符串）
         */
        fun childNameOf(episode: Bangumi.Episode, unknown: String): String =
            episode.title.orEmpty().ifEmpty {
                episode.title_long.orEmpty().ifEmpty { unknown }
            }
    }
}
