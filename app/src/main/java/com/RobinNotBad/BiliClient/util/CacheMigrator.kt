package com.RobinNotBad.BiliClient.util

import java.io.File

/**
 * 把「别的分支缓存目录」里的一条缓存搬到本机下载根（缓存页长按外部条目 →「迁移到本机」）。
 *
 * 为什么要这么一层：外部来源是只读的（见 [ExternalCacheRoots] 与 LocalVideo.externalRoot），
 * 想删、想换分辨率都得先变成「本机条目」，而用户能做的动作只有「搬过来」。
 *
 * 搬迁 = 把整个视频目录挪到下载根下：
 * 1. 先试 renameTo —— 两个目录通常同在 /sdcard 同一卷上，rename 是瞬时的，不复制一个字节；
 * 2. rename 失败（跨卷、ROM 拦了跨应用目录 rename）才退化成「复制到临时目录 → 改名 → 删源」，
 *    复制不完整就整个清掉，绝不在本机留一条半截缓存；
 * 3. 目标名冲突时加 (2)、(3) 后缀，不覆盖已有缓存。
 *
 * 源目录删不掉不算搬迁失败（文件已经在本机，能播就行），但结果里会标出来，让 UI 提示
 * 「原目录里还剩一份，去那个版本删掉」，比假装成功干净。
 */
object CacheMigrator {

    /** 复制时的目录层数上限，防意外的巨大嵌套把扫描卡死。 */
    private const val MAX_COPY_DEPTH = 12

    /**
     * 搬迁结果。
     *
     * @param dest 落地后的本机目录；null = 失败（源目录还原封不动地留在原处）
     * @param sourceCleaned 源目录是否已经清掉。false 且 [dest] 非空 = 搬成功了但原处还剩一份
     */
    data class MoveResult(@JvmField val dest: File?, @JvmField val sourceCleaned: Boolean) {
        val ok: Boolean get() = dest != null
    }

    /**
     * 在 [taken] 里没被占用的名字：优先用 [preferred]，撞了就依次试「名字 (2)」「名字 (3)」……
     * 后缀试到 999 之后用时间戳兜底，不会死循环。
     */
    @JvmStatic
    fun uniqueDirName(taken: Set<String>, preferred: String): String {
        if (!taken.contains(preferred)) return preferred
        for (i in 2..999) {
            val candidate = "$preferred ($i)"
            if (!taken.contains(candidate)) return candidate
        }
        return "$preferred (${System.currentTimeMillis()})"
    }

    /**
     * 把 [source] 这个视频目录搬进 [destRoot]。
     *
     * 自身不切线程、不碰 UI、不写文件夹记录——那些是调用方（缓存页）的事。
     */
    @JvmStatic
    fun moveDir(source: File, destRoot: File): MoveResult {
        if (!source.isDirectory) return MoveResult(null, false)
        if (!destRoot.isDirectory && !destRoot.mkdirs()) return MoveResult(null, false)

        // 已经就在本机根下（理论上外部条目走不到，但别让一次误调把本机缓存 rename 到自己身上）
        if (sameDir(source, destRoot)) return MoveResult(source, true)

        val name = uniqueDirName(listDirNames(destRoot), source.name)
        val target = File(destRoot, name)
        if (source.renameTo(target)) return MoveResult(target, true)

        // rename 失败：复制到临时目录，成功后再改名，任何一步不完整就把临时目录清掉
        val temp = File(destRoot, "$name.migrating")
        deleteTree(temp)
        if (!copyTree(source, temp, 0)) {
            deleteTree(temp)
            return MoveResult(null, false)
        }
        if (!temp.renameTo(target)) {
            deleteTree(temp)
            return MoveResult(null, false)
        }
        return MoveResult(target, deleteTree(source))
    }

    /**
     * 把别的分支根目录下的 .folders_info.json 读成「清洗后的标题 → 夹名」。
     *
     * 键用 [normalize] 清洗过（默认与 [FolderManager] 的比对口径一致：[FileUtil.stringToFile]）：
     * 磁盘上的目录名是清洗后的，夹记录里存的可能是原始标题。
     */
    @JvmStatic
    @JvmOverloads
    fun folderIndex(root: File, normalize: (String) -> String = { FileUtil.stringToFile(it) }): Map<String, String> {
        val index = HashMap<String, String>()
        for (folder in FolderManager.getAllFoldersAt(root)) {
            val name = folder.name
            if (name.isNullOrEmpty()) continue
            val titles = folder.videoTitles ?: continue
            for (title in titles) {
                if (title.isNullOrEmpty()) continue
                index[normalize(title)] = name
            }
        }
        return index
    }

    /** 这条标题在 [index] 里属于哪个夹，找不到返回空串（= 未分类）。 */
    @JvmStatic
    @JvmOverloads
    fun folderOf(
        index: Map<String, String>,
        title: String?,
        normalize: (String) -> String = { FileUtil.stringToFile(it) }
    ): String {
        if (index.isEmpty() || title.isNullOrEmpty()) return ""
        return index[normalize(title)] ?: index[title] ?: ""
    }

    /**
     * 从「对方分支的文件夹名」里挑出本机还缺的那几个。
     *
     * 只做筛选，不碰文件：去空、去重、保持首次出现的顺序，并跳过
     * [FolderManager.isValidFolderName] 不过的名字 —— 建不出来的夹不能算「本机已有」，
     * 否则条目会被归进一个根本不存在的分组，从列表里彻底消失。
     */
    @JvmStatic
    fun foldersToCreate(existingNames: Set<String>, wanted: Collection<String>): List<String> {
        val result = ArrayList<String>()
        val seen = HashSet<String>(wanted.size * 2)
        for (raw in wanted) {
            val name = raw.trim()
            if (name.isEmpty()) continue
            if (existingNames.contains(name)) continue
            if (!seen.add(name)) continue
            if (!FolderManager.isValidFolderName(name)) continue
            result.add(name)
        }
        return result
    }

    /** 目录里已有的名字（用来算不冲突的目标名）。 */
    @JvmStatic
    fun listDirNames(root: File): Set<String> {
        val names = root.list() ?: return emptySet()
        return names.toHashSet()
    }

    private fun sameDir(a: File, b: File): Boolean = try {
        a.canonicalPath == b.canonicalPath
    } catch (_: Exception) {
        a.absolutePath == b.absolutePath
    }

    /**
     * 递归复制目录（连 mtime 一起，按更新时间排序时不会因为搬迁而乱序）。
     *
     * @return 是否完整复制成功；false 时调用方负责清掉写了一半的目标
     */
    private fun copyTree(src: File, dst: File, depth: Int): Boolean {
        if (depth > MAX_COPY_DEPTH) return false
        if (src.isDirectory) {
            if (!dst.isDirectory && !dst.mkdirs()) return false
            val children = src.listFiles() ?: return false
            for (child in children) {
                if (!copyTree(child, File(dst, child.name), depth + 1)) return false
            }
            return true
        }
        if (!src.isFile) return false
        return try {
            src.copyTo(dst, overwrite = true)
            dst.setLastModified(src.lastModified())
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 递归删除目录（只用于「临时目录回滚」和「搬完删源」）。
     *
     * @return 是否删干净；false = 还有残留（多半是没权限）
     */
    @JvmStatic
    fun deleteTree(target: File): Boolean {
        if (!target.exists()) return true
        if (target.isDirectory) {
            val children = target.listFiles()
            if (children != null) {
                for (child in children) {
                    if (!deleteTree(child)) return false
                }
            }
        }
        return target.delete() || !target.exists()
    }
}
