package com.RobinNotBad.BiliClient.util

/**
 * 缓存/下载目录的纯决策逻辑。
 *
 * 这里刻意**不引用任何 Android API**（API 等级、路径都从外面传进来），
 * 于是可以直接跑 JVM 单测（见 `app/src/test/.../util/StoragePathPolicyTest.kt`），
 * 和 `HotSearchApi.parseHotSearch`、`PictureMediaStore.displayFolder` 是同一套写法。
 *
 * 背景（issue #42 / Wear OS）：手表 ROM 往往不提供「所有文件访问」设置页，
 * 用户**永远授不到** MANAGE_EXTERNAL_STORAGE。所以应用不能再把「有全盘权限」当成
 * 缓存下载的前提：默认目录必须是任何版本、任何设备都免权限的路径，
 * 用户自己改到公共目录后写不进去时也要能自动降级（见 `FileUtil.resolveVideoDownloadDir`）。
 */
object StoragePathPolicy {

    /**
     * 默认视频缓存目录。
     *
     * - **API 21+**：`<外部存储>/Android/media/<包名>/`。它属于应用专属媒体目录
     *   （等价于 `Context.getExternalMediaDirs()`），任何版本都不需要存储权限；
     *   老版本客户端的默认值本来就是它，保持默认值不变，老用户的缓存不会「消失」。
     * - **API 19/20**：`Android/media` 在这些旧系统上仍是公共存储语义，直接写要
     *   WRITE_EXTERNAL_STORAGE（而下载链路不想再为默认目录要权限），因此改用应用专属
     *   外部目录 `getExternalFilesDir("video")`；取不到再退内部 `filesDir/video`。
     *
     * @param apiLevel        当前设备 API 等级
     * @param mediaDir        老默认目录（`Android/media/<包名>/`）
     * @param appExternalDir  应用专属外部目录（`getExternalFilesDir("video")`），可能为 null
     * @param internalDir     应用专属内部目录（`filesDir/video`），可能为 null
     */
    @JvmStatic
    fun defaultVideoDir(
        apiLevel: Int,
        mediaDir: String?,
        appExternalDir: String?,
        internalDir: String?
    ): String {
        if (apiLevel >= 21 && !mediaDir.isNullOrEmpty()) return mediaDir
        if (!appExternalDir.isNullOrEmpty()) return appExternalDir
        if (!internalDir.isNullOrEmpty()) return internalDir
        return mediaDir.orEmpty()
    }

    /**
     * 结合用户配置决定本次进程使用的「正式」视频目录（不含运行时降级）。
     *
     * 三条优先级，顺序不能换：
     * 1. 用户自己设置过 `save_path_video` → 用用户的（哪怕它写不进去，也先按它算，
     *    写不进去才由调用方降级，这样设置页显示的和用户填的一致）；
     * 2. 用户没设置过、但老默认目录里**还有缓存** → 继续用老目录。升级后列表不能突然变空；
     * 3. 否则用 [defaultVideoDir] 算出的默认目录。
     *
     * @param configured       用户配置的路径，没配置过传 null/空串
     * @param mediaDir         老默认目录
     * @param mediaDirHasContent 老默认目录里是否还有用户内容（见 [hasRealContent]）
     * @param defaultDir       按 API 算出的默认目录
     */
    @JvmStatic
    fun chooseVideoDir(
        configured: String?,
        mediaDir: String?,
        mediaDirHasContent: Boolean,
        defaultDir: String
    ): String {
        if (!configured.isNullOrBlank()) return configured
        if (mediaDirHasContent && !mediaDir.isNullOrEmpty()) return mediaDir
        return defaultDir
    }

    /**
     * 正式目录可写时，要不要把降级目录里落下的缓存搬回来。
     *
     * 两个触发条件（任一）：
     * - `wasFallback`：本进程刚从降级态切回来（原来唯一的判据）；
     * - `fallbackIsSaved`：**上次进程**实际用的就是降级目录。进程重启后 `wasFallback` 必然是
     *   false，少了这一条，降级期间写下的缓存重启后既不会被搬回、也不会被扫描，
     *   用户看到的就是「更新一次缓存全没」（反馈 #187）。
     *
     * 降级目录里没有真实内容时不做无意义的移动。
     */
    @JvmStatic
    fun shouldReclaimFallback(
        wasFallback: Boolean,
        fallbackIsSaved: Boolean,
        fallbackHasContent: Boolean,
        configuredHasContent: Boolean
    ): Boolean {
        if (!fallbackHasContent) return false
        // 还没装上「记录实际根」这一版之前就降级过的用户：正式目录是空的、降级目录里有货，
        // 那就是上次更新后再也扫不到的缓存，直接搬回来（搬完降级目录就空了，天然幂等）。
        if (!configuredHasContent) return true
        return wasFallback || fallbackIsSaved
    }

    /**
     * 两个路径是否指同一个目录（只忽略尾部路径分隔符）。
     *
     * 用来判断「上次记录的根 == 降级目录」这类关系。不碰文件系统、不解析软链接，
     * 也不做大小写折叠——Android 的存储就是大小写敏感的。
     */
    @JvmStatic
    fun sameDir(a: String?, b: String?): Boolean {
        val na = normalizeDir(a)
        val nb = normalizeDir(b)
        return na.isNotEmpty() && na == nb
    }

    private fun normalizeDir(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return path.trimEnd('/', '\\')
    }

    /**
     * 目录项里是否有「用户内容」。
     *
     * 全隐藏文件不算：`.nomedia`（本应用自己建的）、`.DOWNLOADING`/`.write_probe`
     * 这类标记文件都不代表这里有缓存。用「非隐藏」做判据，简单且不会因为一个空标记
     * 就把老用户永远钉在旧目录上。
     */
    @JvmStatic
    fun hasRealContent(names: List<String>?): Boolean {
        if (names.isNullOrEmpty()) return false
        return names.any { !it.startsWith(".") }
    }
}
