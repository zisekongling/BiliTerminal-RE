package com.RobinNotBad.BiliClient.util

/**
 * 缓存视频「真实落盘目录」的解析（纯逻辑，不碰 Android API，可直接跑 JVM 单测）。
 *
 * 为什么要有这个类（反馈 #58「已缓存文件无法在应用内删除」）：
 *
 * 删除缓存时原来的实现是 `File(FileUtil.getVideoDownloadPath(), video.title)` —— 拿
 * 「根目录 + 列表里显示的标题」硬拼路径。这套假设只在「目录名恰好等于标题原样」时成立，
 * 而真实落盘目录可能来自三个不同的地方：
 *
 * 1. **下载侧清洗过标题**：`DownloadService` 用 `FileUtil.getVideoDownloadPath(title, child)`
 *    建目录，它会走 `FileUtil.stringToFile()`（`| : * ? " < > / \` 换成全角、截到 85 字符）。
 *    缓存列表里的 `LocalVideo.title` 是**磁盘目录名**，两者通常一致，但历史版本 / 手工搬进来的
 *    目录可能不一致；
 * 2. **多 P 合集有子目录**：`<根>/<标题>/<分P名>/video.mp4`，缓存列表把「整部合集」当一个条目，
 *    它的目录是 `<根>/<标题>`，不能只看 `videoFileList[0]` 的父目录；
 * 3. **根目录会被换掉**：用户改「缓存路径」、或写不进去降级到应用专属目录时，根目录不是常量
 *    （见 `FileUtil.getVideoDownloadPath`）。
 *
 * 所以这里反过来做：**优先用模型里的实际文件路径**（`LocalVideo.videoFileList` 就是扫描时拿到的
 * 真实绝对路径）反推它属于根目录下的哪个一级子目录，再退回「清洗标题」「原始标题」两种拼接。
 * 返回的是**候选列表**（按可信度排序），是否真的存在、要不要删由调用方判断——这样本类保持纯函数，
 * 单测不需要文件系统。
 *
 * 约束：调用方传进来的两个路径必须来自同一套根（都用 `FileUtil.getVideoDownloadPath()`），
 * 否则 `isUnderRoot` 会直接判否，候选里只剩按标题拼的那两个。
 */
object CachePathResolver {

    /**
     * 把路径规范成便于比较的形式：统一成 `/` 分隔、去掉重复分隔符、去掉末尾分隔符与 `.` 段。
     *
     * 只做字符串层面的事（不访问文件系统），所以对不存在的路径同样有效。
     */
    @JvmStatic
    fun normalizePath(path: String?): String {
        if (path.isNullOrEmpty()) return ""
        val unified = path.replace('\\', '/')
        val parts = ArrayList<String>()
        for (segment in unified.split('/')) {
            // 空段来自 "//" 或末尾的 "/"，"." 段没有意义，都丢掉；".." 原样保留（不猜父目录）
            if (segment.isEmpty() || segment == ".") continue
            parts.add(segment)
        }
        val isAbsolute = unified.startsWith("/")
        val joined = parts.joinToString("/")
        return when {
            isAbsolute && joined.isEmpty() -> "/"
            isAbsolute -> "/$joined"
            else -> joined
        }
    }

    /** [path] 是否就是 [rootPath] 或它下面的路径（按规范化后的字符串比较，不碰文件系统）。 */
    @JvmStatic
    fun isUnderRoot(rootPath: String?, path: String?): Boolean {
        val root = normalizePath(rootPath)
        val target = normalizePath(path)
        if (root.isEmpty() || target.isEmpty()) return false
        if (target == root) return true
        val prefix = if (root.endsWith("/")) root else "$root/"
        return target.startsWith(prefix)
    }

    /**
     * 取 [path] 在根目录下的**一级子目录**路径。
     *
     * 这是本类的核心：缓存列表是按「根目录下的一级子目录 = 一部视频（合集则是一部合集的根）」组织的，
     * 所以 `<根>/<标题>/video.mp4` 与 `<根>/<标题>/<分P>/video.mp4` 都应该归到 `<根>/<标题>`。
     *
     * @return 一级子目录的规范化绝对路径；[path] 不在 [rootPath] 下、或就是根自己时返回 null
     */
    @JvmStatic
    fun directChildOf(rootPath: String?, path: String?): String? {
        val root = normalizePath(rootPath)
        val target = normalizePath(path)
        if (root.isEmpty() || target.isEmpty() || target == root) return null
        val prefix = if (root.endsWith("/")) root else "$root/"
        if (!target.startsWith(prefix)) return null
        val relative = target.substring(prefix.length)
        val first = relative.substringBefore('/')
        if (first.isEmpty()) return null
        return join(root, first)
    }

    /**
     * 一条缓存视频可能的落盘目录，按可信度排序、已去重。
     *
     * 顺序（不能换）：
     * 1. **实际文件路径反推**（`mediaFilePath` = 列表里 `videoFileList[0]`）：扫描时拿到的真实路径，
     *    能同时命中「标题被清洗过」「多 P 子目录」「根目录刚换过」这三种情况；
     * 2. `根/清洗(title)`：与下载侧 `FileUtil.getVideoDownloadPath(title, null)` 完全同一套拼接；
     * 3. `根/title`：兜底老数据——早期版本/手工搬进来的目录名可能没走清洗。
     *
     * @param rootPath       当前进程实际使用的缓存根目录（`FileUtil.getVideoDownloadPath().path`）
     * @param title          缓存列表里的标题（磁盘目录名，或 `LocalVideo.title`）
     * @param mediaFilePath  模型里记录的媒体文件绝对路径，可为 null/空
     */
    @JvmStatic
    @JvmOverloads
    fun videoDirCandidates(
        rootPath: String?,
        title: String?,
        mediaFilePath: String? = null
    ): List<String> {
        val root = normalizePath(rootPath)
        if (root.isEmpty()) return emptyList()

        val candidates = LinkedHashSet<String>()
        // ① 实际文件路径反推：多 P 也归到合集根目录（缓存列表一个条目 = 整部合集）
        directChildOf(root, mediaFilePath)?.let { candidates.add(it) }

        // ② 下载侧同一套清洗 + 拼接
        val safeTitle = if (title.isNullOrEmpty()) "" else FileUtil.stringToFile(title)
        if (safeTitle.isNotEmpty()) candidates.add(join(root, safeTitle))

        // ③ 原始标题（未清洗）兜底
        if (!title.isNullOrEmpty() && title != safeTitle) candidates.add(join(root, title))

        return candidates.toList()
    }

    /** 拼接根目录与子项，避免根已经以 `/` 结尾时拼出 `//`。 */
    private fun join(root: String, child: String): String =
        if (root.endsWith("/")) root + child else "$root/$child"
}
