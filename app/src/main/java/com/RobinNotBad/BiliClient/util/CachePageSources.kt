package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.LocalVideo

/**
 * 一条缓存条目「每一页各自落在哪个根」的纯逻辑（反馈 #217 / #214）。
 *
 * ## 为什么需要
 *
 * 番剧与多 P 合集在磁盘上是「季名父目录 + 集名子目录」，缓存页把整季当一个条目。用户改过
 * 「缓存位置」之后，同一季的集常常一半在新根、一半留在旧根（旧根会被 [ExternalCacheRoots]
 * 当成外部只读来源）。改动前的 [LocalListActivity.appendExternalSources] 按**标题**去重，
 * 遇到同名直接把外部那条整条丢掉 —— 于是「第一季点开只有 6/7 两集」，8/9/10 那三集在别的
 * 根上，看不见也播不了（反馈 #217）。同一个机制也让「合集夹里的视频全都不见了」（反馈 #214）。
 *
 * 现在的规则：
 *
 * - **合集与合集同名 → 按页合并**（页名重复的才跳过，本机优先），见 [mergePages]；
 * - 合并后的条目**不再是整条只读**，每个写盘动作自己问 [rootOfPage]：这一页在外部根上就跳过，
 *   删除按 [deleteTargets] 分组，各自删自己那一份。
 *
 * 纯 JDK（只碰 [LocalVideo] 的几个列表），可以直接跑 JVM 单测。
 */
object CachePageSources {

    /** 本机下载根在 [LocalVideo.pageRootList] 里的标记（空串，不是 null，免得和「没有页级根」混起来）。 */
    const val LOCAL_ROOT = ""

    /**
     * 第 [pageIndex] 页落在哪个根（空串 = 本机根）。
     *
     * [LocalVideo.pageRootList] 缺失（本版之前的条目、单文件条目）时退回到整条条目的
     * [LocalVideo.externalRoot]：老数据里「整条外部」与「每页都外部」是同一件事。
     */
    @JvmStatic
    fun rootOfPage(video: LocalVideo, pageIndex: Int): String {
        val root = video.pageRootList?.getOrNull(pageIndex)
        if (!root.isNullOrEmpty()) return root
        val external = video.externalRoot
        return if (external.isNullOrEmpty()) LOCAL_ROOT else external
    }

    /** 第 [pageIndex] 页的媒体文件在 [LocalVideo.videoFileList] 里的全部下标（DASH 合并失败的页有两个）。 */
    @JvmStatic
    fun mediaIndices(video: LocalVideo, pageIndex: Int): List<Int> {
        val pageName = video.pageList?.getOrNull(pageIndex) ?: return emptyList()
        return LocalPageFileIndexPolicy.indicesOfPage(video.videoFileList ?: emptyList(), pageName)
    }

    /**
     * 把 [source] 里 [target] 还没有的页追加到 [target] 尾部，返回追加的页数。
     *
     * 判据是**页名**（磁盘目录名），与 [LocalPageFileIndexPolicy] 同一口径：同名页视为同一集，
     * 本机那份留着，外部那份不再重复显示。返回 0 表示没合并（两边不是合集，或页全都重复），
     * 调用方按老规矩把 source 丢掉即可。
     */
    @JvmStatic
    fun mergePages(target: LocalVideo, source: LocalVideo): Int {
        val targetPages = target.pageList ?: return 0
        // 本机那条是单文件（不是合集）：不合并，保持「同名只留本机」的老行为
        if (targetPages.isEmpty()) return 0
        val sourcePages = source.pageList ?: return 0
        if (sourcePages.isEmpty()) return 0

        val targetFiles = target.videoFileList ?: ArrayList<String>().also { target.videoFileList = it }
        val targetDanmaku = target.danmakuFileList ?: ArrayList<String>().also { target.danmakuFileList = it }
        val targetSizes = target.sizeList ?: ArrayList<Long>().also { target.sizeList = it }
        val targetQualities = target.qualityList ?: ArrayList<String>().also { target.qualityList = it }
        val targetRoots = target.pageRootList ?: ArrayList<String>().also { target.pageRootList = it }
        // 老条目没有页根：补齐成本机根，保证三条「每页一项」的列表与 pageList 对齐
        while (targetRoots.size < targetPages.size) targetRoots.add(LOCAL_ROOT)

        val known = HashSet(targetPages)
        var added = 0
        for (i in sourcePages.indices) {
            val pageName = sourcePages[i] ?: continue
            if (known.contains(pageName)) continue
            val indices = LocalPageFileIndexPolicy.indicesOfPage(
                source.videoFileList ?: emptyList(),
                pageName
            )
            // 没有媒体文件就不是一页（扫描侧同样会跳过这种目录）
            if (indices.isEmpty()) continue
            for (fileIndex in indices) {
                source.videoFileList?.getOrNull(fileIndex)?.let { targetFiles.add(it) }
            }
            targetPages.add(pageName)
            targetSizes.add(source.sizeList?.getOrNull(i) ?: 0L)
            targetDanmaku.add(source.danmakuFileList?.getOrNull(i) ?: "")
            targetQualities.add(source.qualityList?.getOrNull(i) ?: "")
            targetRoots.add(rootOfPage(source, i))
            known.add(pageName)
            added++
        }
        if (added > 0) {
            if (source.lastModified > target.lastModified) target.lastModified = source.lastModified
            target.calcTotalSize()
        }
        return added
    }

    /**
     * 一条删除要落在哪些根上：每个根一条，带一个能反推出该根下目录的媒体文件路径。
     *
     * [CacheDeletePlan] 只认「根 + 标题（+ 媒体文件反推）」，所以混合来源的条目必须拆成多条
     * 删除计划：拿本机根去套外部的页删不到，拿外部根去套本机的页会删错。
     */
    data class DeleteTarget(val rootPath: String, val mediaFilePath: String?)

    @JvmStatic
    fun deleteTargets(video: LocalVideo, localRootPath: String?): List<DeleteTarget> {
        val external = video.externalRoot
        // 整条外部的条目调用方已经拦下了，这里再兜一次：只在它自己的根上算
        if (!external.isNullOrEmpty()) {
            return listOf(DeleteTarget(external, video.videoFileList?.firstOrNull()))
        }
        val local = localRootPath ?: return emptyList()
        val single = listOf(DeleteTarget(local, video.videoFileList?.firstOrNull()))
        val pages = video.pageList ?: return single
        val roots = video.pageRootList ?: return single
        if (pages.isEmpty() || roots.isEmpty()) return single

        val targets = LinkedHashMap<String, String?>()
        for (i in pages.indices) {
            val root = roots.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: local
            if (targets.containsKey(root)) continue
            val indices = mediaIndices(video, i)
            targets[root] = indices.firstOrNull()?.let { video.videoFileList?.getOrNull(it) }
        }
        if (targets.isEmpty()) return single
        return targets.entries.map { DeleteTarget(it.key, it.value) }
    }
}
