package com.RobinNotBad.BiliClient.service.download

import com.RobinNotBad.BiliClient.model.Bangumi

/**
 * 番剧缓存入队的一次「季」载荷：缓存页勾好一批集之后，按季拼成它交给下载服务。
 *
 * 缓存页允许跨季混选，所以入队参数是 `List<BangumiCacheSeason>`——一季一条，
 * 每条最终都落成「季名当父目录、集名当子目录」的多个下载任务。
 *
 * @param seasonTitle 季名，落库的 title，也是父目录名
 * @param seasonCover 季封面；某集自己没有封面时兜底用它
 * @param seasonId    季 id，落 epid / season_id / season_type 三列用
 * @param seasonType  季类型（1=番剧、2=电影…），0 表示未知，交给调用方兜底
 * @param episodes    用户勾选的剧集，按详情页顺序
 */
class BangumiCacheSeason(
    @JvmField val seasonTitle: String,
    @JvmField val seasonCover: String,
    @JvmField val seasonId: Long,
    @JvmField val seasonType: Int,
    @JvmField val episodes: List<Bangumi.Episode>
)
