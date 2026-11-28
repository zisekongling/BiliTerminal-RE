package com.RobinNotBad.BiliClient.util

import android.os.Environment
import java.io.File

/**
 * 别的分支（分叉仓库）的缓存目录 —— 缓存页把它们当成额外的**只读**来源。
 *
 * ## 为什么按目录枚举，而不是按包名
 *
 * 分叉仓库改 applicationId 是常规操作（本仓库自己的共存版就是 `com.RobinNotBad.BiliClient.re`），
 * 名单里只要少一个名字，那台机器上就什么都扫不到。所以这里**不认识任何包名**：
 * `Android/media/<任意>` 与 `Android/data/<任意>` 下的目录全部当候选，**是不是缓存目录由内容决定**
 * （注意：Kotlin 的块注释会嵌套，注释里写「斜杠加星号」会把后面全吞掉，所以这里一律写 <任意>）
 * （判据统一走 [CacheMediaPolicy]，与本地扫描同源，改判据不会两边打架）。
 *
 * 列 `Android/media`、`Android/data` 这两层不需要任何权限；子目录读不进去时
 * `listFiles()` 返回 null，直接跳过即可 —— 于是「有没有存储权限」不是要分情况写的分支，
 * 只是命中条数的差别。真要在别人的目录里读到东西，要么有「所有文件访问」
 * （设置 → 下载路径 → 授予存储权限），要么有 root。
 *
 * ## 为什么不做成用户维护的路径列表
 *
 * 用户拍板「只自动扫」。多一个路径列表就多一处会过期、要解释、要清理的状态，
 * 而候选目录本身每次进缓存页现算一遍只有几十次 `stat`，手表上也看不出耗时。
 */
object ExternalCacheRoots {

    /**
     * `Android/data/<包名>/` 下真正放缓存的常见层级。
     *
     * 本仓库自己的下载根挂在 `Android/media/<包名>`，但别的实现有把缓存放
     * `files/`、`files/video/`、`files/download/` 的；再往下的目录名没有共识，
     * 继续猜下去只会扫到一堆无关目录。
     */
    private val DATA_SUBDIRS = listOf("files", "files/video", "files/download")

    /**
     * `Android/media/<包名>/` 下可能再套一层的缓存子目录名。
     *
     * 本仓库自己的下载根就是 `Android/media/<包名>`（见 `FileUtil.getVideoDownloadPath`），
     * 一级子目录就是视频标题。但别的实现 / 旧版本会再套一层 `video/`、`download/`，
     * **那一层的子目录才是标题**。不展开的话，用户看到的就是一条标题叫「video」、没有封面的
     * 假条目，迁移过去也只是把整层容器搬过来（反馈 #321）。
     */
    private val MEDIA_SUBDIRS = listOf("video", "download", "files", "media", "movies")

    /**
     * 所有可能的「别人的缓存根」。
     *
     * @param exclude 本机的下载根：扫到自己等于把同一批视频列两遍（导过的内容还会重复），
     *        按**规范路径**比较，符号链接、`/sdcard` 与 `/storage/emulated/0` 的写法差异都不影响
     */
    @JvmStatic
    fun candidateRoots(exclude: File?): List<File> {
        val out = ArrayList<File>()
        val seen = HashSet<String>()
        val external = externalStorageDir() ?: return out
        if (exclude != null) seen.add(canonical(exclude))

        // Android/media/<包名>：本仓库自己的下载根就在这一层；再套了 video/ 之类的则钻进去
        val mediaContainer = File(external, "Android/media")
        val mediaChildren = mediaContainer.listFiles() ?: emptyArray()
        for (child in mediaChildren) {
            if (!child.isDirectory) continue
            val nested = containerSubDirs(child)
            if (nested.isEmpty()) {
                addRoot(child, seen, out)
            } else {
                for (sub in nested) addRoot(sub, seen, out)
            }
        }

        // Android/data/<包名>/files[...]：容器多一层，候选根是那个子目录
        val dataContainer = File(external, "Android/data")
        val dataChildren = dataContainer.listFiles() ?: emptyArray()
        for (pkgDir in dataChildren) {
            if (!pkgDir.isDirectory) continue
            for (sub in DATA_SUBDIRS) addRoot(File(pkgDir, sub), seen, out)
        }
        return out
    }

    /**
     * `<包名>` 下是不是还套着一层容器目录（`video/` 之类），返回该用哪个当根。
     *
     * **只有 `<包名>` 自己不像缓存根时才展开**：它下面直接有媒体文件，或者有
     * `.folders_info.json`（文件夹记录），都说明标题就长在它下面，直接用它。
     *
     * 已知取舍：真有视频标题叫 `video` 又会命中白名单，此时会被当成容器钻进去一层 ——
     * 结果只是条目层级不同（视频还在，能播），比反过来漏扫一整批缓存轻。
     */
    private fun containerSubDirs(pkgDir: File): List<File> {
        val children = pkgDir.listFiles() ?: return emptyList()
        if (CacheMediaPolicy.pickMediaName(children.map { it.name }) != null) return emptyList()
        if (File(pkgDir, FOLDERS_FILE_NAME).exists()) return emptyList()
        val out = ArrayList<File>()
        for (name in MEDIA_SUBDIRS) {
            val sub = File(pkgDir, name)
            if (sub.isDirectory) out.add(sub)
        }
        return out
    }

    /** 与本机 `FolderManager` 的文件夹记录同名；这里只做存在性判断，不需要解析。 */
    private const val FOLDERS_FILE_NAME = ".folders_info.json"

    private fun addRoot(dir: File, seen: MutableSet<String>, out: MutableList<File>) {
        if (!dir.isDirectory) return
        val path = canonical(dir)
        // 同一个目录可能被两条路找到（Android/data/<包名>/files 与 .../files/download 互为父子时不会，
        // 但符号链接会），去重后再交给扫描，免得同一条视频被列两遍
        if (!seen.add(path)) return
        out.add(dir)
    }

    private fun externalStorageDir(): File? = try {
        Environment.getExternalStorageDirectory()
    } catch (e: Exception) {
        null
    }

    private fun canonical(file: File): String = try {
        file.canonicalPath
    } catch (e: Exception) {
        file.absolutePath
    }
}
