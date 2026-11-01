package com.RobinNotBad.BiliClient.util

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException

/**
 * 收集「本应用允许交给外部应用读」的存储根目录 —— **所有存储卷**，不只主卷。
 *
 * 与 [LocalFileProvider] 配套：`res/xml/file_paths.xml` 能声明的 FileProvider 根只覆盖主卷
 * （原因见 [SharedFilePolicy] 的类注释），外置卷（SD 卡）上的缓存路径只能由本对象在运行时收集。
 *
 * 收集原则是「**用户存得进去的地方，就得播得出来**」：
 *
 * - `Environment.getExternalStorageDirectory()`：主卷根。用户自定义缓存路径可能落在主卷任意目录
 *   （`/storage/emulated/0/Download/Bili` 之类），FileProvider 的 `<external-path>` 虽然也覆盖它，
 *   这里重复一份是为了让本 provider 自己就能独立判断，不依赖 FileProvider 的配置；
 * - 每块卷上的应用专属外部目录（`getExternalFilesDirs` / `getExternalCacheDirs` /
 *   `getExternalMediaDirs` 的全部元素，不只是 `[0]`）：Android 5.0+ 这些目录免存储权限，
 *   默认缓存目录 `Android/media/<包名>` 就在其中；
 * - 由上面这些目录**反推出来的卷根**（见 [SharedFilePolicy.volumeRoot]）：覆盖用户把缓存路径
 *   设到外置卷上非 `Android/media` 目录的情况；
 * - 应用内部 `filesDir/video`：外部存储都取不到时的降级缓存目录（`FileUtil` 的兜底）。
 *   只登记这一个子目录，**不**把整个 `filesDir` / `cacheDir` 放进来——那里还有日志、WebView 缓存、
 *   SharedPreferences，没必要为了「播放本地视频」把它们也登记进可授权范围（审计 M12-e 的口径）。
 *
 * 只收集、不创建：`getExternalMediaDirs()` 在部分 ROM 上会顺手建目录，但本对象只读路径，
 * 建目录是系统自己做的，这里不做额外 IO 写入。
 */
object SharedFileRoots {

    /**
     * 当前可用的共享根（已规范化、去重）。
     *
     * 不缓存：用户可能在两次点击之间插拔 SD 卡、切换默认存储，缓存一份过期列表等于「刚插的卡
     * 播不了」。每次解析只做十来个路径的 canonical 化，调用点（点一次播放/一次 openFile）远谈不上频繁。
     */
    @JvmStatic
    fun collect(context: Context): List<String> {
        val appDirs = ArrayList<File?>()

        // 主卷根。它不一定是 /storage/emulated/0：只挂 SD 卡的设备/手表上这里直接就是 SD 卡路径，
        // 而那种机器上 getExternalMediaDirs()[0] 与它一致 —— 不一致才是本类要解决的外置卷情形。
        appDirs.add(Environment.getExternalStorageDirectory())

        // 所有卷上的应用专属外部目录。元素可能为 null（卷不可用/未挂载），逐个判空。
        appDirs.addAll(ContextCompat.getExternalFilesDirs(context, null).orEmpty().toList())
        appDirs.addAll(ContextCompat.getExternalCacheDirs(context).orEmpty().toList())
        // getExternalMediaDirs 是 API 21+：低版本不能摸，否则 NoSuchMethodError。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            appDirs.addAll(context.externalMediaDirs.orEmpty().toList())
        }

        // 内部降级目录：外部存储都取不到时视频缓存会落到 filesDir/video
        // （见 FileUtil.getFallbackVideoDownloadPath()）。只登记这一个子目录，不把整个
        // filesDir / cacheDir 纳入共享面（日志、WebView 缓存、SharedPreferences 都在它们下面）。
        appDirs.add(File(context.filesDir, "video"))

        val roots = LinkedHashSet<String>()
        for (dir in appDirs) {
            if (dir == null) continue
            canonical(dir)?.let { roots.add(it) }
        }

        // 卷根：用户自定义缓存路径可以落在卷上任意可写目录，不只 Android/media/<包名>。
        for (root in SharedFilePolicy.volumeRoots(appDirs.map { it?.absolutePath })) {
            canonical(File(root))?.let { roots.add(it) }
        }
        return roots.toList()
    }

    /** canonical 化失败（路径被删、权限不足）时退回绝对路径，至少还能参与前缀判断。 */
    private fun canonical(file: File): String? = try {
        file.canonicalPath
    } catch (e: IOException) {
        file.absolutePath
    }
}
