package com.RobinNotBad.BiliClient.util

/**
 * 本地文件「能不能交给别的应用读」的纯决策逻辑（只做路径字符串判断，不碰 Android API）。
 *
 * ## 为什么要另起一套
 *
 * `res/xml/file_paths.xml` 里能声明的 FileProvider 根全部是「主卷」语义：
 * androidx 的 `FileProvider` 解析 `<external-path>` 时取 `Environment.getExternalStorageDirectory()`，
 * 解析 `<external-media-path>` / `<external-files-path>` / `<external-cache-path>` 时只取
 * `getExternalMediaDirs()[0]` / `getExternalFilesDirs()[0]` 这些**数组里的第一个**（= 主卷）。
 * 于是外置卷（SD 卡，路径形如 `/storage/5C00-1D0E/...`）上的缓存目录一个根都匹配不到，
 * `FileProvider.getUriForFile()` 直接抛 `IllegalArgumentException: Failed to find configured root`。
 *
 * 真实崩溃（用户 Android 6.0 手表）：把「缓存路径」设到 SD 卡后，点缓存视频用凉腕播放器播放，
 * 在 `PlayerApi.getVideoUri()` 里挂掉。FileProvider 的 XML 是编译期资源，没法按当前挂载的卷
 * 动态长出新根，因此外置卷这条路由 [LocalFileProvider] 自己算、自己授权。
 *
 * 本对象刻意不引用任何 Android API（路径全是字符串），于是能在纯 JVM 里跑单测
 * （见 `SharedFilePolicyTest`），和 [StoragePathPolicy] 是同一套写法。
 */
object SharedFilePolicy {

    /** 应用专属目录里 `/Android/` 这一段；从它往前截就是存储卷根。 */
    private const val ANDROID_SEGMENT = "/Android/"

    /**
     * [filePath] 是否落在 [rootPath] 之内，**按路径分段判断**。
     *
     * 不能拿 `startsWith` 裸比：`/storage/emulated/0/cache2` 确实以 `/storage/emulated/0/cache`
     * 开头，但它不是那个目录的子路径。所以要么完全相等，要么以 `根 + "/"` 开头。
     * 两边的尾斜杠先去掉（`root/` 与 `root` 视为同一个根）。
     */
    @JvmStatic
    fun isUnder(rootPath: String?, filePath: String?): Boolean {
        if (rootPath.isNullOrEmpty() || filePath.isNullOrEmpty()) return false
        val root = rootPath.trimEnd('/')
        val file = filePath.trimEnd('/')
        if (root.isEmpty() || file.isEmpty()) return false
        return file == root || file.startsWith("$root/")
    }

    /**
     * 从应用专属外部目录反推它所在的存储卷根：
     * `/storage/5C00-1D0E/Android/data/<包名>/files` → `/storage/5C00-1D0E`。
     *
     * 为什么需要反推：Android 5.0 起每块外置卷上都有 `/<卷>/Android/{data,media}/<包名>`，
     * 但「列出所有卷根」在 API 19~23 上没有公开 API（`StorageManager.getVolumeList` 是隐藏 API，
     * `StorageManager.storageVolumes` 要 API 24），而 minSdk 是 19。用户完全可能把缓存路径
     * 设到卷上任意可写目录（例：`/storage/5C00-1D0E/BiliCache`），所以用系统给的应用外部目录
     * 反推卷根，才能覆盖「卷内任意路径」而不是只有 `Android/media/<包名>` 那一格。
     *
     * @return 卷根；路径里没有 `/Android/`（不是应用外部目录）或缺少卷名前缀时返回 null
     */
    @JvmStatic
    fun volumeRoot(appDirPath: String?): String? {
        if (appDirPath.isNullOrEmpty()) return null
        // 从开头找：/storage/5C00-1D0E/Android/media/... 的卷名里不可能自带 "/Android/"
        val index = appDirPath.indexOf(ANDROID_SEGMENT)
        if (index <= 0) return null
        return appDirPath.substring(0, index).trimEnd('/').ifEmpty { null }
    }

    /**
     * 在 [roots] 里找出包含 [canonicalPath] 的根；多条命中时取**最长**（最具体）的那条。
     *
     * 最长优先只是为了让日志/测试里的归属可预期：`/a` 与 `/a/b` 同时登记时，`/a/b/c` 应归 `/a/b`。
     */
    @JvmStatic
    fun findContainingRoot(roots: List<String>, canonicalPath: String?): String? {
        if (canonicalPath.isNullOrEmpty()) return null
        var best: String? = null
        for (root in roots) {
            if (!isUnder(root, canonicalPath)) continue
            if (best == null || root.length > best.length) best = root
        }
        return best
    }

    /** 从一批应用专属外部目录里剥出所有卷根（去重并保持顺序）。 */
    @JvmStatic
    fun volumeRoots(appDirPaths: List<String?>): List<String> {
        val out = LinkedHashSet<String>()
        for (path in appDirPaths) volumeRoot(path)?.let { out.add(it) }
        return out.toList()
    }
}
