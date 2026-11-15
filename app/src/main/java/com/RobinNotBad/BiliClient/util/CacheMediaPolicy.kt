package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.service.download.DownloadPathSpec

/**
 * 「这个缓存目录里到底有没有能播的东西」——缓存列表扫描、点击播放、「已下载」提示三处共用的唯一判据。
 *
 * ## 为什么需要它（反馈 #164）
 *
 * 用户报的是「多 P 视频缓存了第 2、3 集，缓存列表里找不到，想重新缓存又提示该视频已下载」。
 * 根子是三处各说各话：
 *
 * - 缓存页把**整部多 P 合集当成一个条目**（[com.RobinNotBad.BiliClient.activity.video.local.LocalListActivity.scanVideos]
 *   的 else 分支），卡片上却只有总大小和画质，用户看不出自己缓存了几集；
 * - 点卡片播放时按**扁平文件数**猜布局（1 个 = 单文件、2 个 = DASH 双文件、更多 = 选页）。而
 *   「两个已缓存的分 P、每页一个合并好的 mp4」也正好是 2 个文件，于是被当成「一页的 video+audio」：
 *   第 2 集既点不到，还会被当作音轨给第 1 集配音；
 * - 「已下载」判据看的是**目录存不存在**（`MultiPageActivity` / `VideoInfoFragment`），而目录里
 *   可能只有封面、`.page_order.json` 或一次失败的残留——列表里看不见，却永远不让重下。
 *
 * 这里把「目录里有什么」收敛成**纯文件名判定**（不碰文件系统，可直接跑 JVM 单测），扫描与各处判据
 * 共用一份；分 P 的「页目录 → 该页媒体文件」也在这里抽出来，供扫描组装 `LocalVideo`。
 */
object CacheMediaPolicy {

    /**
     * DASH 合并中断留下的旧临时名（[DownloadPathSpec.FILE_VIDEO_MERGING] 之前那一版用的名字）。
     *
     * 它带 `.mp4` 后缀、内容就是合并出来的视频，所以扫缓存时要认它：老缓存里只剩这个文件时
     * 仍然算「能播的一集」，否则用户会看到「列表里没有、重下又说已下载」。
     */
    const val FILE_VIDEO_MERGED_TEMP = "video_merged_temp.mp4"

    /** 能直接播的成品文件名，顺序即优先级：合并好的视频 > 旧合并残留 > 纯音频 */
    private val MEDIA_NAMES = listOf(
        DownloadPathSpec.FILE_VIDEO,
        FILE_VIDEO_MERGED_TEMP,
        DownloadPathSpec.FILE_AUDIO
    )

    /**
     * 目录里有成品时返回它的文件名，没有则 null。
     *
     * 只有 `audio.m4a` 也算成品：那是「仅下载音频」的缓存，能听就该显示、也不该被当成没下载。
     */
    @JvmStatic
    fun pickMediaName(names: Collection<String>?): String? {
        if (names.isNullOrEmpty()) return null
        for (name in MEDIA_NAMES) {
            if (names.contains(name)) return name
        }
        return null
    }

    /** 这个目录还在下载中（`.DOWNLOADING` 存在）：既不算已缓存，也不该出现在缓存列表里。 */
    @JvmStatic
    fun isDownloading(names: Collection<String>?): Boolean {
        if (names.isNullOrEmpty()) return false
        return names.contains(DownloadPathSpec.FILE_DOWNLOADING)
    }

    /**
     * 这个目录算不算「已经缓存好的一集 / 一个视频」：有成品，且不在下载中。
     *
     * 这是**缓存列表与「已下载」提示必须共用的口径**：列表按它决定条目出不出现，
     * 下载入口按它决定要不要拦下重下。
     */
    @JvmStatic
    fun isCachedFolder(names: Collection<String>?): Boolean =
        pickMediaName(names) != null && !isDownloading(names)

    /** 扫描到的一页：目录名 + 该页的成品文件名（`video.mp4` / 旧合并残留 / `audio.m4a`）。 */
    data class ScannedPage(val name: String, val mediaName: String)

    /**
     * 「扫到的分 P 目录名」→「真的有成品的那几页」，顺序保持传入顺序（调用方已按 `.page_order.json` 排好）。
     *
     * @param pageNames 一级目录下的子目录名（顺序即显示顺序）
     * @param namesOf 取某个子目录里的文件名列表；目录读不出来时返回 null
     *
     * 下载中的页、只有封面/顺序表/字幕的空页都会被跳过——和「点卡片播放」看到的页保持一致，
     * 免得列表上有这一集、点进去却播不了（旧实现每页各写一遍 `exists()` 判断，容易与别处走偏）。
     */
    fun pagesWithMedia(
        pageNames: List<String>,
        namesOf: (String) -> Collection<String>?
    ): List<ScannedPage> {
        val result = ArrayList<ScannedPage>(pageNames.size)
        for (name in pageNames) {
            val names = namesOf(name)
            val mediaName = pickMediaName(names) ?: continue
            if (isDownloading(names)) continue
            result.add(ScannedPage(name, mediaName))
        }
        return result
    }
}
