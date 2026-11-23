package com.RobinNotBad.BiliClient.util

/**
 * 「分享至动态」的目标类型映射：动态报文里 `web_repost_src.revs_id.dyn_type` 的取值。
 *
 * <p>为什么单独抽一个文件：转发视频那条链路（`api/DynamicApi.java` 的 `relayVideo`）里
 * `dyn_type` 一直是写死的 8。番剧（PGC）、收藏夹这些新入口要复用同一条发布路径，就得先把
 * 「这条内容在动态里算哪一种」讲清楚——取值集中在这里，页面里不再各写一份魔法数字，
 * 也方便单测把每个类型逐个钉住。
 *
 * <p>取值口径来自开源第三方客户端 PiliPlus 的分享面板实现（`lib/pages/video/introduction/pgc/controller.dart`
 * 的 `dynType` 映射、收藏夹页的 `RepostPanel(rid: mediaId, dynType: 4300)`）；本仓库 `bilibili-API/`
 * 的公开快照里只有 1 / 2 / 8 / 2048 这几档，其余取值以线上实际渲染为准。
 *
 * <p>**未知类型一律返回 -1**，不回退成一个「看起来像」的值：错的 `dyn_type` 会让服务端把内容
 * 渲染成别的卡片（或者干脆回失败），宁可在发布页提前拦下来（`SendDynamicActivity` 就是这么做的）。
 */
object ForwardTargets {

    /** 投稿视频（av）。既有转发链路一直在用的值。 */
    const val DYN_TYPE_VIDEO = 8

    /** 收藏夹（整个夹子转发到动态）。 */
    const val DYN_TYPE_FAV = 4300

    /** 投稿视频：与既有 `relayVideo` 完全同一口径。 */
    @JvmStatic
    fun videoDynType(): Int = DYN_TYPE_VIDEO

    /** 收藏夹：把整个夹子当成一个可转发的目标。 */
    @JvmStatic
    fun favDynType(): Int = DYN_TYPE_FAV

    /**
     * 番剧（PGC）按剧集信息里的 `pgc_type` 分组。
     *
     * @param pgcType 1 番剧、2 电影、3 纪录片、4 国创、5 电视剧、7 综艺
     * @return 对应的 `dyn_type`；未知类型（含 0 / 6 / 99 这类）返回 -1，调用方应据此拦下发布
     */
    @JvmStatic
    fun bangumiDynType(pgcType: Int): Int = when (pgcType) {
        1 -> 4097  // 番剧
        2 -> 4098  // 电影
        3 -> 4101  // 纪录片
        4 -> 4100  // 国创
        5 -> 4099  // 电视剧
        7 -> 4099  // 综艺：服务端与电视剧同一档
        else -> -1
    }
}
