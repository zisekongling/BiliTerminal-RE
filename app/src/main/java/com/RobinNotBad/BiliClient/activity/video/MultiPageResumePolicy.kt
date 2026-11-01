package com.RobinNotBad.BiliClient.activity.video

/**
 * 「多P 视频再次打开时续到上次那个P」的纯判定（零 Android 依赖，可直接 JVM 单测）。
 *
 * **为什么需要它**（反馈 #83）：服务端 `x/player/wbi/playurl` 的响应里带了 aid 级的
 * `last_play_cid` / `last_play_time`（"上次看的是哪一P、看到哪"）。本仓库的
 * `PlayerApi` 早把它们解析进了 `PlayerData.cidHistory`，但**没有任何消费方**：
 * 多P 视频每次打开都从第 1P 开始，用户得手动重新选一遍。
 *
 * 数据契约（不能想当然）：
 * - `cidHistory` 是 `last_play_cid`，`last_play_time <= 0` 时没有意义（只是登记，不是观看记录）；
 * - `progress` **只在 `last_play_cid == 当前 cid` 时**才采纳 `last_play_time`（见
 *   `PlayerApi.adoptLastPlayTime`），所以 `progress == 0` 不能用来判断"有没有上次记录"，
 *   必须看 `PlayerData.lastPlayTimeMs` 这个原值；
 * - `last_play_cid` 可能属于别的稿件（或本稿件已重传、cid 全变），`cids` 里找不到就不能乱跳；
 * - **番剧/短视频不走这里**（番剧的续播是"季/集"维度，由 PGC 心跳与剧集列表负责）。
 */
object MultiPageResumePolicy {

    /**
     * 上次观看的分P 在当前 `cids` 列表里的下标；`0` 表示"按默认 P1 处理"。
     *
     * 返回 0 的四种情况都**必须**走原来的"让用户选"路径，不能自动起播：
     * 没有观看记录、`last_play_cid` 不在本稿件里、只有一P、以及上次看的本来就是 P1
     * （P1 是默认页，把选集列表留给用户更有价值）。
     *
     * @param cids 当前视频的分P cid 列表（`VideoInfo.cids`，真实多P 时是真 cid）
     * @param lastPlayCid 服务端 `last_play_cid`
     * @param lastPlayTimeMs 服务端 `last_play_time`（毫秒原值，未做单位换算前也够用：只要 > 0）
     */
    fun resumePageIndex(cids: List<Long>?, lastPlayCid: Long, lastPlayTimeMs: Long): Int {
        if (cids.isNullOrEmpty()) return 0
        if (lastPlayCid <= 0L) return 0
        if (lastPlayTimeMs <= 0L) return 0
        val index = cids.indexOf(lastPlayCid)
        return if (index > 0) index else 0
    }

    /** 该下标能否安全地当成分P 下标用（越界/负值一律不算）。 */
    fun isValidPageIndex(cids: List<Long>?, index: Int): Boolean =
        cids != null && index >= 0 && index < cids.size

    /**
     * 用 cid 纠正一次「当前第几P」的页面下标。
     *
     * 播放器里 `currentPageIndex` 决定选集卡片高亮哪一项、以及自动连播的下一P。
     * 入口若给了**正确的 cid 但下标是旧的**（多个入口各自拼 PlayerData，容易漏），
     * 界面就会高亮错一P。cid 在 `cids` 里找得到就以它为准；找不到/虚拟合集原样返回。
     *
     * **虚拟合集不能纠正**：那一层的 `cids` 里存的是 aid（见 `PlayerData.virtualCollection`），
     * 拿 cid 去 `indexOf` 必然找不到，硬改会把下标弄成 0。
     */
    fun alignPageIndex(cids: List<Long>?, cid: Long, currentIndex: Int, virtualCollection: Boolean): Int {
        if (virtualCollection) return currentIndex
        if (cids.isNullOrEmpty() || cid <= 0L) return currentIndex
        val index = cids.indexOf(cid)
        return if (index >= 0) index else currentIndex
    }
}
