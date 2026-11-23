package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.VideoFolder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 文件夹管理器，负责文件夹的增删改查和持久化
 * 数据存储在视频下载目录下的 .folders_info.json 文件中
 */
object FolderManager {

    private const val FOLDERS_FILE = ".folders_info.json"

    /**
     * 获取文件夹信息文件的路径
     */
    private fun getFoldersFile(): File {
        return File(FileUtil.getVideoDownloadPath(), FOLDERS_FILE)
    }

    /**
     * 获取所有文件夹列表
     */
    @JvmStatic
    fun getAllFolders(): ArrayList<VideoFolder> = getAllFoldersAt(FileUtil.getVideoDownloadPath())

    /**
     * 读**任意根目录**下的文件夹记录。
     *
     * 加这个重载是为了「别的分支的缓存」：他们的 .folders_info.json 与本机同一套格式，
     * 但记在那个分支的根目录里，用 [getAllFolders] 永远读不到（它写死本机下载根）。
     * 缓存页据此知道外部条目在对方属于哪个夹，迁移时好把分类一起带过来。
     *
     * 只读：写回仍然只走本机根的 [saveAllFolders]。
     */
    @JvmStatic
    fun getAllFoldersAt(root: File): ArrayList<VideoFolder> {
        val folders = ArrayList<VideoFolder>()
        val file = File(root, FOLDERS_FILE)
        if (!file.exists()) return folders

        try {
            val content = file.readText()
            if (content.isBlank()) return folders
            val jsonArray = JSONArray(content)
            for (i in 0 until jsonArray.length()) {
                folders.add(VideoFolder.fromJson(jsonArray.getJSONObject(i)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return folders
    }

    /**
     * 夹记录文件是否存在、且内容能被解析成合法的夹数组。
     *
     * 与 [getAllFolders] 的区别：那个把「文件不存在 / 内容坏了 / 真的一个夹都没有」三种情况
     * 一律返回空表，调用方据此分不清该不该按「没有夹」处理。反馈 #254 的残留就卡在这里——
     * 用户把最后一个夹拆掉后夹表合法地变空，而 [CacheFolderResolver.needsMetaHeal] 见到空表
     * 一律不写，视频元数据便永远停在旧夹名上。
     *
     * 判据只认「文件存在 + 非空白 + 能 parse 成 JSONArray」：空白文件是写到一半的残骸，
     * 读坏了更不敢当成「用户真的没有夹」，这两种都返回 false（继续不自愈）。
     */
    @JvmStatic
    fun isFoldersFileIntact(): Boolean {
        val file = getFoldersFile()
        if (!file.exists()) return false
        return try {
            val content = file.readText()
            if (content.isBlank()) return false
            JSONArray(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 把某条视频的归属写进它的 `.video_meta.json`，目录按实际落盘位置解析。
     *
     * 不能用 [VideoMetaManager.updateFolderName]：那条按「缓存根 / 标题」拼目录，目录名被清洗过、
     * 多 P 合集、缓存根刚换过时都拼不中，而它拼不中就静默返回——归属只剩 json 那一份，
     * 于是夹卡有数字、夹内为空（反馈 #254 的原始成因）。这里走 [CachePathResolver.videoDirCandidates]，
     * 与缓存页扫描用的是同一套候选；实在一个都没命中才退回老写法碰一次运气。
     */
    private fun writeMetaFolder(title: String, folderName: String) {
        val root = FileUtil.getVideoDownloadPath()
        val dir = CachePathResolver.videoDirCandidates(root.path, title)
            .asSequence()
            .map { File(it) }
            .firstOrNull { it.isDirectory }
        if (dir != null) {
            VideoMetaManager.updateFolderNameIn(dir, folderName)
        } else {
            VideoMetaManager.updateFolderName(title, folderName)
        }
    }

    /**
     * 保存所有文件夹到文件
     */
    private fun saveAllFolders(folders: List<VideoFolder>) {
        try {
            val jsonArray = JSONArray()
            for (folder in folders) {
                jsonArray.put(folder.toJson())
            }
            val file = getFoldersFile()
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 创建新文件夹
     * @return true 成功，false 失败（名称重复或名称无效）
     */
    @JvmStatic
    fun createFolder(name: String): Boolean {
        if (!isValidFolderName(name)) return false

        val folders = getAllFolders()
        // 检查重名
        if (folders.any { it.name == name }) return false

        val newFolder = VideoFolder(name)
        folders.add(newFolder)
        saveAllFolders(folders)
        return true
    }

    /**
     * 重命名文件夹
     * @return true 成功，false 失败
     */
    @JvmStatic
    fun renameFolder(oldName: String, newName: String): Boolean {
        if (!isValidFolderName(newName)) return false
        if (oldName == newName) return true

        val folders = getAllFolders()
        // 检查新名称是否已存在
        if (folders.any { it.name == newName }) return false

        val folder = folders.find { it.name == oldName } ?: return false

        // 更新文件夹名称
        folder.name = newName
        saveAllFolders(folders)

        // 更新所有属于该文件夹的视频元数据
        for (title in folder.videoTitles) {
            writeMetaFolder(title, newName)
        }

        return true
    }

    /**
     * 删除文件夹（拆散，将视频移回未分类）
     */
    @JvmStatic
    fun deleteFolder(name: String): Boolean {
        val folders = getAllFolders()
        val folder = folders.find { it.name == name } ?: return false

        // 将所有视频的文件夹属性清空
        for (title in folder.videoTitles) {
            writeMetaFolder(title, "")
        }

        folders.remove(folder)
        saveAllFolders(folders)
        return true
    }

    /**
     * 将视频添加到文件夹
     */
    @JvmStatic
    fun addVideoToFolder(title: String, folderName: String): Boolean {
        val folders = getAllFolders()
        val folder = folders.find { it.name == folderName } ?: return false

        // 先从旧文件夹移除
        removeVideoFromAnyFolder(title, folders)

        folder.addVideo(title)
        saveAllFolders(folders)
        writeMetaFolder(title, folderName)
        return true
    }

    /**
     * 迁移专用：确保本机有这个夹（没有就建一个），再把这条视频放进去。
     *
     * 夹名来自对方分支的 .folders_info.json。为什么不能直接 [addVideoToFolder]：那条要求夹已存在，
     * 而迁移过来的夹名本机多半还没有——先建再放，用户整理好的分类才不会在搬迁这一步丢掉。
     *
     * 夹名不合法（超 30 字符、含斜杠之类）、或建夹失败时返回 false：视频仍留在未分类，
     * 不为了一个夹名把整次迁移判失败。
     *
     * @return 是否真的落进了夹里
     */
    @JvmStatic
    fun ensureFolderAndAdd(title: String, folderName: String): Boolean {
        if (title.isBlank() || folderName.isBlank()) return false
        if (!isValidFolderName(folderName)) return false
        if (getAllFolders().none { it.name == folderName }) createFolder(folderName)
        return addVideoToFolder(title, folderName)
    }

    /**
     * 将视频从文件夹移出到未分类
     */
    @JvmStatic
    fun removeVideoFromFolder(title: String): Boolean {
        val folders = getAllFolders()
        val result = removeVideoFromAnyFolder(title, folders)
        if (result) {
            saveAllFolders(folders)
            writeMetaFolder(title, "")
        }
        return result
    }

    /**
     * 把一条视频记录从**所有**文件夹里摘掉，删除缓存文件后调用。
     *
     * 与 [removeVideoFromFolder] 的区别只有两点，都跟「列表里的标题」和「文件夹里存的标题」
     * 可能不是同一串字符有关（下载侧会用 [FileUtil.stringToFile] 清洗标题、历史版本写入的是原始标题）：
     * 1. 匹配走 [titleMatches]，不要求逐字相同；
     * 2. 遍历所有文件夹而不是命中第一个就返回（老数据里同一条视频可能被写进多个文件夹）。
     *
     * 不碰视频元数据：文件都删了，`.video_meta.json` 也一起没了。
     *
     * @return 是否有文件夹记录被摘掉（删除结果不受它影响）
     */
    @JvmStatic
    fun removeVideoFromAllFolders(title: String): Boolean {
        if (title.isBlank()) return false
        val folders = getAllFolders()
        var removed = false
        for (folder in folders) {
            val stored = folder.videoTitles?.firstOrNull { titleMatches(it, title) } ?: continue
            if (folder.removeVideo(stored)) removed = true
        }
        if (removed) saveAllFolders(folders)
        return removed
    }

    /**
     * 把一批「元数据里有、夹记录里漏掉」的标题补回对应夹（反馈 #254 自愈的另一半）。
     *
     * 归属解析统一走 [CacheFolderResolver] 的「记录优先、元数据兜底」；靠元数据兜底救回来的那条，
     * 夹记录里本来就没有它——不补的话夹卡数字、夹页扫描白名单（`LocalListActivity.folderScanTitles`）
     * 都数不到这一条，夹里还是看不全。整批读完一次、写一次，不逐条读重写 json。
     *
     * 只认已经存在的夹（[byFolder] 里对不上夹名的直接丢掉）；比对沿用 [titleMatches]，
     * 记录里存的是清洗前的原始标题时也不会重复补一份。
     *
     * @param byFolder 夹名 → 要补进去的标题
     * @return 真正补进去的条数（0 表示没动过盘）
     */
    @JvmStatic
    fun adoptTitles(byFolder: Map<String, Collection<String>>): Int {
        if (byFolder.isEmpty()) return 0
        val folders = getAllFolders()
        if (folders.isEmpty()) return 0
        var added = 0
        for (folder in folders) {
            val titles = byFolder[folder.name] ?: continue
            val existing = folder.videoTitles
            for (title in titles) {
                if (title.isBlank()) continue
                if (existing != null && existing.any { titleMatches(it, title) }) continue
                folder.addVideo(title)
                added++
            }
        }
        if (added > 0) saveAllFolders(folders)
        return added
    }

    private fun removeVideoFromAnyFolder(title: String, folders: ArrayList<VideoFolder>): Boolean {
        for (folder in folders) {
            val stored = folder.videoTitles?.firstOrNull { titleMatches(it, title) } ?: continue
            if (folder.removeVideo(stored)) return true
        }
        return false
    }

    /**
     * 文件夹记录里的标题与「当前标题」是否指同一条视频。
     *
     * 逐字相同当然算；否则按下载侧同一套清洗规则（[FileUtil.stringToFile]）比一次——
     * 用户把 `/`、`:` 这类字符换成全角只是落盘时的处理，不该让它从文件夹里「失联」。
     */
    private fun titleMatches(stored: String?, target: String): Boolean {
        if (stored.isNullOrEmpty() || target.isEmpty()) return false
        if (stored == target) return true
        return FileUtil.stringToFile(stored) == FileUtil.stringToFile(target)
    }

    /**
     * 获取文件夹内的视频标题列表
     */
    @JvmStatic
    fun getFolderVideos(folderName: String): List<String> {
        val folders = getAllFolders()
        val folder = folders.find { it.name == folderName }
        return folder?.videoTitles ?: emptyList()
    }

    /**
     * 保存文件夹之间的手动顺序（缓存页长按拖动文件夹后调用）。
     *
     * 顺序的载体就是 `.folders_info.json` 的数组顺序，所以这里按名字重排整个数组再写回。
     * [orderedNames] 里没有的文件夹（例如拖动期间在别处新建的）保持原来的相对顺序排到最后，
     * 不至于因为一次拖动把它们弄丢。
     */
    @JvmStatic
    fun saveFolderOrder(orderedNames: List<String>) {
        if (orderedNames.size <= 1) return
        val folders = getAllFolders()
        if (folders.size <= 1) return

        val rank = HashMap<String, Int>(orderedNames.size * 2)
        for (index in orderedNames.indices) {
            val name = orderedNames[index]
            if (name.isNotEmpty() && !rank.containsKey(name)) rank[name] = index
        }
        // 未列出的用 Int.MAX_VALUE；sortedWith 是稳定排序，它们之间保持文件里的原顺序
        val sorted = folders.sortedWith(Comparator { a, b ->
            val rankA = rank[a.name] ?: Int.MAX_VALUE
            val rankB = rank[b.name] ?: Int.MAX_VALUE
            when {
                rankA < rankB -> -1
                rankA > rankB -> 1
                else -> 0
            }
        })
        if (sorted.map { it.name } == folders.map { it.name }) return
        saveAllFolders(sorted)
    }

    /**
     * 保存文件夹内视频的手动顺序（缓存页在夹内长按拖动后调用）。
     *
     * 顺序存在 [VideoFolder.videoTitles] 里。拖动时只看到当前扫描到的视频，
     * 记录里可能还有暂时扫不到的标题，用 [LocalListSort.mergeOrder] 把它们按原相对顺序留在后面，
     * 等文件回来时位置还在。
     *
     * @return 是否找到该文件夹（找不到时不写文件）
     */
    @JvmStatic
    fun saveFolderVideoOrder(folderName: String, orderedTitles: List<String>): Boolean {
        if (folderName.isEmpty()) return false
        val folders = getAllFolders()
        val folder = folders.find { it.name == folderName } ?: return false
        val merged = LocalListSort.mergeOrder(orderedTitles, folder.videoTitles ?: emptyList())
        if (merged == folder.videoTitles) return true
        folder.videoTitles = ArrayList(merged)
        saveAllFolders(folders)
        return true
    }

    /**
     * 验证文件夹名称是否合法
     */
    @JvmStatic
    fun isValidFolderName(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.length > 30) return false
        // 不允许包含特殊字符
        val invalidChars = Regex("[/\\\\:*?\"<>|]")
        if (invalidChars.containsMatchIn(name)) return false
        return true
    }
}