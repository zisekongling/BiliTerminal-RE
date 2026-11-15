package com.RobinNotBad.BiliClient.service.download

import com.RobinNotBad.BiliClient.util.FileUtil
import java.io.File

/**
 * 缓存页判断「这一集是不是已经在缓存里了」用的小工具。
 *
 * 为什么要问磁盘而不是只查下载表：任务成功落盘后那一行就被删了（见下载服务的收尾），
 * 下载表里只剩「还没下完/失败」的任务。所以「已下载」只能靠缓存目录里的成品文件判断，
 * 「队列中」才查下载表。
 */
object BangumiCacheDownloadIndex {

    /**
     * 下载表里还挂着的任务对应的 cid。
     *
     * 用来把「已排队、还没下完」的集标出来：这些集再入队只会被下载服务按 aid+cid 去重掉，
     * 提前标出来比让用户勾完再弹「已在队列中」友好。
     */
    fun queuedCids(): Set<Long> =
        DownloadRepository.getAll().orEmpty().map { it.cid }.toSet()

    /**
     * 这一集是不是已经下好了：目录里有成品文件，且没有「下载中」标记。
     *
     * 目录名必须和下载服务落盘时一模一样，所以两个入参就是下载表里的 title / child
     * （季名与 [BangumiCacheSeason.childNameOf] 算出的集名），由 [FileUtil.getVideoDownloadPath]
     * 统一做文件名清洗。
     */
    fun isDownloaded(seasonTitle: String, childName: String): Boolean {
        if (seasonTitle.isEmpty() || childName.isEmpty()) return false
        val dir = FileUtil.getVideoDownloadPath(seasonTitle, childName)
        if (!dir.isDirectory) return false
        if (File(dir, DownloadPathSpec.FILE_DOWNLOADING).exists()) return false
        return File(dir, DownloadPathSpec.FILE_VIDEO).exists() ||
            File(dir, DownloadPathSpec.FILE_AUDIO).exists()
    }
}
