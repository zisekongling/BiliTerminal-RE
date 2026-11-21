package com.RobinNotBad.BiliClient.util

/**
 * 本地缓存「页下标 ↔ 文件下标」的纯映射逻辑。
 *
 * ## 为什么需要
 *
 * 缓存页把多 P 合集的每一页存成一个目录；DASH 合并失败的页里会**同时留下**
 * `video.mp4` 与 `audio.m4a`，扫描时两页文件都会被塞进 `LocalVideo.videoFileList`
 * （见 `LocalListActivity` 扫描逻辑）。于是三条列表的长度并不相等：
 *
 * ```text
 * pageList        = [P1, P2, P3]                    // 每页 1 项
 * danmakuFileList = [d1, d2, d3]                    // 每页 1 项
 * videoFileList   = [P1/v.mp4, P2/v.mp4, P2/a.m4a, P3/v.mp4]  // 双文件页占 2 项
 * ```
 *
 * 旧实现在删页时对三条列表都做同一个 `removeAt(position)`：删 P2（position=1）只摘掉
 * P2 的 `video.mp4`，`audio.m4a` 留在列表里被当成 P3 的文件 → 删错页 / 播错文件。
 * 这个坑只在「旧缓存或合并失败的缓存」上可达（26.10.25 起新下载合并单文件），
 * 所以量小但一旦踩到就是数据错位。
 *
 * 这里按「文件所在目录名 == 页目录名」反查下标：扫描时写进列表的是真实绝对路径，
 * 目录名就是页名，单文件 / 双文件 / 混合（个别页合并失败）都能对上，
 * 不依赖「总项数 ÷ 页数」这种在混合场景下不成立的推算。
 */
object LocalPageFileIndexPolicy {

    /**
     * 取路径的父目录名（兼容 `/` 与 `\`）。
     *
     * @return 父目录名；路径没有分隔符（不是"目录里的文件"）时返回 null
     */
    @JvmStatic
    fun parentDirName(path: String?): String? {
        if (path.isNullOrEmpty()) return null
        val trimmed = path.trimEnd('/', '\\')
        val cut = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
        if (cut <= 0) return null
        val start = maxOf(trimmed.lastIndexOf('/', cut - 1), trimmed.lastIndexOf('\\', cut - 1)) + 1
        return trimmed.substring(start, cut).ifEmpty { null }
    }

    /**
     * [pageName] 这一页在 [videoFileList] 里占用的所有下标（升序）。
     *
     * 双文件页会返回两个下标（`video.mp4` 与 `audio.m4a` 同目录），单文件页返回一个。
     */
    @JvmStatic
    fun indicesOfPage(videoFileList: List<String>, pageName: String?): List<Int> {
        if (pageName.isNullOrEmpty()) return emptyList()
        return videoFileList.indices.filter { parentDirName(videoFileList[it]) == pageName }
    }

    /**
     * 从 [videoFileList] 里一次删掉若干下标时**必须从大到小删**：
     * `ArrayList.removeAt` 每删一项，后面所有下标左移一位，先删小的会把后面的下标删偏。
     */
    @JvmStatic
    fun removalOrder(indices: List<Int>): List<Int> = indices.sortedDescending()

    /**
     * 按目录名匹配不到时的兜底：旧格式列表可能只存文件名，退化成「一页一项」。
     *
     * @return 页 [pageIndex] 在文件列表里的起始下标；越界时返回 -1（调用方不要再删）
     */
    @JvmStatic
    fun fallbackStartIndex(pageIndex: Int, pageCount: Int, fileCount: Int): Int {
        if (pageIndex < 0 || pageCount <= 0) return -1
        if (fileCount == pageCount) return pageIndex
        // 混合长度时无法可靠推算，宁可不动文件列表，也不要删错别人的页
        return -1
    }
}
