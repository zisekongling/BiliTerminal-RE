package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.VideoFolder

/**
 * 缓存条目「属于哪个文件夹」的解析口径（反馈 #254）。
 *
 * <h3>为什么要单独一份口径</h3>
 * 同一条归属在两处各存了一份：
 * 1. `.folders_info.json`（[FolderManager]）里的 `VideoFolder.videoTitles`——标题列表；
 * 2. 每个视频目录里 `.video_meta.json`（[VideoMetaManager]）的 `folderName` 字段。
 *
 * 而两边的用处并不对称：夹内页的可见判据一直只认后者
 * （`CacheListAdapter.rebuildFlatList` 的 `video.folderName == 当前夹名`），可是文件夹卡片上的
 * 「N 个视频」、夹的封面、夹页扫描白名单（`LocalListActivity.folderScanTitles`）、
 * 文件夹列表页的计数（`LocalFolderListActivity.countExistingTitles`）全部取自前者。
 * 两边一旦对不上，就会出现「卡片上写着 N 个，点进去一条都没有」——
 * 老版本只往 json 里写过归属、改夹名时 [VideoMetaManager.updateFolderName] 没找到目录直接 return、
 * 手改过 json、目录被改过名，都会留下这种状态。
 *
 * <h3>口径本身</h3>
 * **记录优先，元数据兜底**：json 记录是卡片数字与夹页白名单的来源，只有让它当主口径，卡片上的数字
 * 和点进去的条数才是同一套事实；元数据兜底保住「记录被清过、但视频自己还记得夹名」这种情况，
 * 不至于把条目从列表里整个弄丢。夹名还要在 [folderNames]（当前夹表里的夹名）里才认，
 * 指向已经不存在的夹等于谁都看不见它。
 *
 * 解析出结果后调用方按 [needsMetaHeal] / [needsRecordHeal] 把两边写回一致（自愈一次，之后不再写盘）。
 *
 * 纯函数、不碰磁盘、不读 Android 上下文，所以能在纯 JVM 单测里锁住，见 `CacheFolderResolverTest`。
 */
object CacheFolderResolver {

    /**
     * 按夹记录建「归一标题 → 夹名」的索引。
     *
     * 键用 [normalize] 清洗（默认与 `FolderManager.titleMatches`、[CacheMigrator.folderIndex] 同一套
     * [FileUtil.stringToFile] 口径）：夹记录里存的可能是原始标题，磁盘目录名是清洗过的。
     * 同一条标题被写进多个夹（老数据 / 手改 json）时取先出现的那个——正常操作路径
     * （`FolderManager.addVideoToFolder` 先摘掉别的夹里的同一标题）产生不了这种数据，
     * 这里只是不让它把整张索引搞乱。
     */
    @JvmStatic
    @JvmOverloads
    fun titleIndex(
        folders: List<VideoFolder>,
        normalize: (String) -> String = { FileUtil.stringToFile(it) }
    ): Map<String, String> {
        val index = HashMap<String, String>(folders.size * 4)
        for (folder in folders) {
            val name = folder.name
            if (name.isNullOrEmpty()) continue
            val titles: List<String> = folder.videoTitles ?: emptyList()
            for (title in titles) {
                if (title.isNullOrEmpty()) continue
                val key = normalize(title)
                // 不用 Map.putIfAbsent：minSdk 低于 24，那是 Java 8 的默认方法
                if (!index.containsKey(key)) index[key] = name
            }
        }
        return index
    }

    /**
     * 解析一条本机缓存条目的夹名，空串表示「未分类」。
     *
     * @param recordName 夹记录（`.folders_info.json`）里属于这条视频的夹名，没有则空串
     * @param metaName 这条视频 `.video_meta.json` 里记的夹名
     * @param folderNames 当前夹表里的全部夹名（不在这里的夹名一律不认）
     */
    @JvmStatic
    fun resolve(recordName: String?, metaName: String?, folderNames: Set<String>): String {
        val record = recordName.orEmpty()
        if (record.isNotEmpty() && folderNames.contains(record)) return record
        val meta = metaName.orEmpty()
        if (meta.isNotEmpty() && folderNames.contains(meta)) return meta
        return ""
    }

    /**
     * 解析结果与 `.video_meta.json` 里记的不一致 → 该把元数据写回 [resolved]。
     *
     * [folderNames] 为空（json 读坏了 / 被清过 / 这次压根没有夹）时**一律不写**：
     * 那种情况下 [resolve] 对每条都会返回空串，照着写会把所有视频的夹名抹成空，等于毁掉唯一一份记录。
     */
    @JvmStatic
    fun needsMetaHeal(resolved: String, metaName: String?, folderNames: Set<String>): Boolean {
        if (folderNames.isEmpty()) return false
        return resolved != metaName.orEmpty()
    }

    /**
     * 解析结果有夹名、夹记录里却没这条标题 → 该把标题补进该夹的 `videoTitles`。
     *
     * 补上之后夹卡计数、夹页白名单与夹内的可见条目才对得上（不补的话夹页白名单里没有它，
     * 夹内连扫都扫不到这一条）。
     */
    @JvmStatic
    fun needsRecordHeal(resolved: String, recordName: String?): Boolean {
        if (resolved.isEmpty()) return false
        return recordName.orEmpty().isEmpty()
    }
}
