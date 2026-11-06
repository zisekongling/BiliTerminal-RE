package com.RobinNotBad.BiliClient.util

import android.content.Context
import android.os.StatFs
import com.bumptech.glide.Glide
import java.io.File
import java.util.Locale

/**
 * 存储空间统计与**白名单清理**。
 *
 * ## 为什么单独一个类
 * 「清缓存」最容易写出两类事故：
 * 1. **删过头**：缓存目录和用户下载目录可能被配置到同一棵树下（用户手输路径），一把梭递归删会把
 *    已下载的视频/图片一起删掉；正在播放的文件被删则是立刻崩。所以这里的清理**只走白名单**
 *    （只有 [Context.getCacheDir] 下的一级子项），并且每个待删目标都要先过
 *    [StorageMath.isProtected] / [StorageMath.entriesToClean] 的重叠判定。
 * 2. **删不动还报成功**：删除结果必须回传给调用方，由界面决定提示什么。
 *
 * ## 职责边界
 * - 本类只做「统计 + 按白名单删」，**不含 UI**（页面在 `activity/settings/StorageActivity`）。
 * - 纯计算（体积换算、递归求和、路径重叠判定）放在 [StorageMath]，不 import android，可直接 JVM 单测。
 * - 更新安装包的路径与删除统一走 [UpdateManager]，避免同一路径有两份拼接逻辑。
 */
object AppStorageManager {

    /**
     * Glide 4 默认磁盘缓存目录名。
     *
     * Glide 的默认 `DiskLruCacheFactory` 就是 `context.getCacheDir()/<这个名字>`；
     * 我们没在 AppGlideModule 里改过磁盘缓存位置（见 `util/GlideUtil.java`），所以这里可以按默认名定位。
     * 即使将来改了，[clearImageCache] 也会退回「Glide 清完仍有剩余就按目录兜底删」。
     */
    const val IMAGE_CACHE_DIR = "image_manager_disk_cache"

    /**
     * 播放期临时文件：弹幕 XML 写在 `cacheDir` 根下（`PlayerActivity.kt:979`、`:4795`、`:5351`）。
     * 播放中把它清掉会让当前视频的弹幕装填失败，所以**永远跳过**——它不是「缓存垃圾」，
     * 而是正在使用的文件，下一次起播时播放器自己会删掉重写。
     */
    const val PLAYBACK_DANMAKU_CACHE = "danmaku.xml"

    /** 一次统计的结果。全部为字节数，[availableBytes]/[totalBytes] 是文件系统口径。 */
    data class Snapshot(
        val imageBytes: Long,
        val tempBytes: Long,
        val apkBytes: Long,
        val availableBytes: Long,
        val totalBytes: Long,
        val apkPath: String
    )

    /**
     * 一次清理的结果。
     *
     * @param freedBytes 实际腾出的字节数（删除失败的部分不计入）
     * @param failed 有文件没能删掉（通常是被占用/权限不足）
     * @param skipped 没有可清理的内容（不是错误，界面提示「没有需要清理的内容」）
     */
    data class ClearResult(val freedBytes: Long, val failed: Boolean, val skipped: Boolean)

    // ------------------------------------------------------------ 路径

    /** Glide 磁盘缓存目录（图片缓存）。 */
    @JvmStatic
    fun imageCacheDir(context: Context): File = File(context.cacheDir, IMAGE_CACHE_DIR)

    /** 已下载的更新安装包；没有则 null。 */
    @JvmStatic
    fun updateApkFile(context: Context): File? = UpdateManager.currentUpdatePackageFile(context)

    /**
     * 清理时**绝不能删**的根目录清单。
     *
     * 语义是「保守」：只要待删目标和这里任何一项存在父子重叠（任一方向），就整棵跳过。
     * 包含视频缓存（正式 + 降级）、图片目录（正式 + 降级）、系统公共下载目录、更新安装包。
     *
     * 注意 [StorageMath.isProtected] 用的是**双向**重叠判定：用户如果把下载目录手输进
     * `cacheDir` 里（例如 `cacheDir/download`），那么整个 `cacheDir` 都会被判为受保护，
     * 于是临时文件清理一个都不做——宁可少清，不可误删。
     */
    @JvmStatic
    fun protectedRoots(context: Context): List<File> {
        val roots = ArrayList<File>(6)
        // 用「配置路径 + 降级路径」而不是 getVideoDownloadPath()：后者会顺手往缓存根写 .nomedia，
        // 只是打开一个统计页不该有写盘副作用（两条路径都进保护清单，实际写入目录必被覆盖）。
        addSafely(roots) { FileUtil.getConfiguredVideoDownloadPath() }
        addSafely(roots) { FileUtil.getFallbackVideoDownloadPath() }
        addSafely(roots) { FileUtil.getConfiguredPictureDownloadPath() }
        addSafely(roots) { FileUtil.getFallbackPictureDownloadPath() }
        addSafely(roots) { FileUtil.getDownloadPath() }
        addSafely(roots) { updateApkFile(context) }
        // 当前播放的弹幕文件（见 PLAYBACK_DANMAKU_CACHE）
        addSafely(roots) { File(context.cacheDir, PLAYBACK_DANMAKU_CACHE) }
        return roots
    }

    /** 取路径可能抛异常（存储不可用、外部目录被拔掉），单条失败不影响整份清单。 */
    private inline fun addSafely(roots: MutableList<File>, block: () -> File?) {
        try {
            block()?.let { roots.add(it) }
        } catch (t: Throwable) {
            // 忽略：少一条保护路径不该让整个页面打不开；真要误删也得先有可写的目录
        }
    }

    // ------------------------------------------------------------ 统计

    /**
     * 统计一次占用。**要在后台线程调用**（递归扫目录是 IO）。
     *
     * 口径：
     * - [Snapshot.imageBytes] = Glide 磁盘缓存目录；
     * - [Snapshot.tempBytes] = 内部 cacheDir 里**可清理**的临时文件（与 [clearTempFiles] 同一套白名单，
     *   已排除图片缓存、下载/播放相关受保护项、当前播放的弹幕文件）；
     * - [Snapshot.apkBytes] = 已下载的更新安装包（它在 `externalCacheDir/update/` 下，**不**计入 temp，
     *   单列一行让用户能独立删除）；
     * - 可用/总空间取 `filesDir` 所在文件系统，`StatFs.availableBytes` 是 API 18+，
     *   本工程 minSdk 19 可直接用。
     */
    @JvmStatic
    fun inspect(context: Context): Snapshot {
        val imageDir = imageCacheDir(context)
        val imageBytes = StorageMath.sumBytes(imageDir)
        // 临时文件口径 =「可清理量」：与 clearTempFiles 用同一套白名单求和，界面数字才不会大于
        // 实际能腾出的体积（受保护项、当前播放的弹幕文件都不计入）。
        var tempBytes = 0L
        val protectedPaths = protectedRoots(context)
        for (child in StorageMath.entriesToClean(context.cacheDir, protectedPaths)) {
            if (child.name == IMAGE_CACHE_DIR) continue
            tempBytes += StorageMath.sumBytes(child)
        }

        val apk = updateApkFile(context)
        val apkBytes = try {
            if (apk != null && apk.isFile) apk.length() else 0L
        } catch (t: Throwable) {
            0L
        }

        var available = 0L
        var total = 0L
        try {
            val stat = StatFs(context.filesDir.absolutePath)
            available = stat.availableBytes
            total = stat.totalBytes
        } catch (t: Throwable) {
            // 存储不可用（只读挂载/被格式化）时保持 0，界面显示「未知」
        }

        return Snapshot(
            imageBytes = imageBytes,
            tempBytes = tempBytes,
            apkBytes = apkBytes,
            availableBytes = available,
            totalBytes = total,
            apkPath = apk?.absolutePath ?: ""
        )
    }

    // ------------------------------------------------------------ 清理

    /**
     * 清 Glide 磁盘缓存。**必须在后台线程调用**（[Glide.clearDiskCache] 是阻塞 IO）。
     *
     * 只有图片缩略图，不碰下载目录；清完仍有剩余时按目录兜底删一次（Glide 换过缓存位置、
     * 或缓存目录里有非 Glide 写入的残留文件时）。
     */
    @JvmStatic
    fun clearImageCache(context: Context): ClearResult {
        val dir = imageCacheDir(context)
        val before = StorageMath.sumBytes(dir)
        var failed = false
        try {
            Glide.get(context).clearDiskCache()
        } catch (t: Throwable) {
            failed = true
        }
        var after = if (dir.exists()) StorageMath.sumBytes(dir) else 0L
        if (after > 0L) {
            try {
                if (!FileUtil.deleteFolderRecursively(dir)) failed = true
            } catch (t: Throwable) {
                failed = true
            }
            after = if (dir.exists()) StorageMath.sumBytes(dir) else 0L
        }
        val freed = (before - after).coerceAtLeast(0L)
        return ClearResult(freedBytes = freed, failed = failed, skipped = before == 0L)
    }

    /**
     * 清内部缓存目录里的临时文件（**白名单：只清 [Context.getCacheDir] 的一级子项**）。
     *
     * - 跳过 [IMAGE_CACHE_DIR]：图片缓存有自己的入口与 Glide 托管，不在这里删两遍；
     * - 每个待删目标都过 [StorageMath.entriesToClean] 的重叠判定，下载/播放相关目录整棵跳过；
     * **受保护根与 cacheDir 有父子重叠（任一方向）时整个 cacheDir 都不清**（见 [StorageMath.entriesToClean]）；
     * - 外部缓存目录（`externalCacheDir`）**完全不动**：更新安装包就在里面。
     *
     * **为什么不直接复用 [FileUtil.clearCache]：** 后者是「把整个 cacheDir 一把删」，
     * 既没有受保护判定（下载目录被配进 cacheDir 时会误删），也不区分图片缓存；
     * 本页要求「只走白名单、绝不删下载目录/当前播放文件」，所以这里复用它的底层
     * [FileUtil.deleteFolderRecursively] 而不是入口函数。
     *
     * **要在后台线程调用**。
     */
    @JvmStatic
    fun clearTempFiles(context: Context): ClearResult {
        val cacheDir = context.cacheDir
        val protectedPaths = protectedRoots(context)
        val targets = StorageMath.entriesToClean(cacheDir, protectedPaths)
            .filter { it.name != IMAGE_CACHE_DIR }
        if (targets.isEmpty()) return ClearResult(0L, false, skipped = true)

        var freed = 0L
        var failed = false
        for (target in targets) {
            val size = StorageMath.sumBytes(target)
            val ok = try {
                FileUtil.deleteFolderRecursively(target)
            } catch (t: Throwable) {
                false
            }
            if (ok) freed += size else failed = true
        }
        return ClearResult(freedBytes = freed, failed = failed, skipped = false)
    }

    /**
     * 删除已下载的更新安装包。
     *
     * 只删 [UpdateManager] 登记的那一个文件（`externalCacheDir/update/bili_terminal_update.apk`），
     * 不扫目录、不按后缀匹配。删完再调一次 [UpdateManager.deleteOldApkFile] 保证两侧状态一致（幂等）。
     */
    @JvmStatic
    fun deleteUpdatePackage(context: Context): ClearResult {
        val apk = updateApkFile(context) ?: return ClearResult(0L, false, skipped = true)
        val size = try {
            apk.length()
        } catch (t: Throwable) {
            0L
        }
        val deleted = try {
            apk.delete() || !apk.exists()
        } catch (t: Throwable) {
            false
        }
        if (deleted) {
            try {
                UpdateManager.deleteOldApkFile()
            } catch (t: Throwable) {
                // 同路径，已经删掉了；这里失败不影响结果
            }
        }
        return ClearResult(
            freedBytes = if (deleted) size else 0L,
            failed = !deleted,
            skipped = false
        )
    }
}

/**
 * [AppStorageManager] 的纯计算部分：**不 import android**，可直接 JVM 单测。
 *
 * 所有路径判断都先取 `canonicalPath`：用户配置的路径里会有 `..`、软链接、大小写差异，
 * 只按字符串前缀比较会出现「`/a/bc` 被当成 `/a/b` 的子路径」这类经典的误删来源。
 */
object StorageMath {

    /** 体积文案："0 B" / "1023 B" / "1.5 KB" / "12.3 MB" / "1.0 GB"。 */
    @JvmStatic
    fun formatBytes(bytes: Long): String {
        val value = if (bytes < 0L) 0L else bytes
        if (value < 1024L) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var unit = -1
        while (size >= 1024.0 && unit < units.size - 1) {
            size /= 1024.0
            unit++
        }
        return String.format(Locale.US, "%.1f %s", size, units[unit])
    }

    /** 递归求体积（不跟随软链接、深度上限防套娃）。取不到的项按 0 计。 */
    @JvmStatic
    @JvmOverloads
    fun sumBytes(root: File?, maxDepth: Int = 24, depth: Int = 0): Long {
        if (root == null || depth > maxDepth) return 0L
        return try {
            if (root.isFile) root.length()
            else {
                val children = root.listFiles() ?: return 0L
                var total = 0L
                for (child in children) total += sumBytes(child, maxDepth, depth + 1)
                total
            }
        } catch (t: Throwable) {
            0L
        }
    }

    /** canonical 路径；取不到（权限/不存在）时退回绝对路径。 */
    @JvmStatic
    fun canonicalPath(file: File): String = try {
        file.canonicalPath
    } catch (t: Throwable) {
        file.absolutePath
    }

    /** [path] 是否等于 [root] 或位于其下（按路径段判定，`/a/bc` 不算 `/a/b` 的子路径）。 */
    @JvmStatic
    fun isInsideOrSame(path: File, root: File): Boolean {
        val p = canonicalPath(path)
        val r = canonicalPath(root)
        if (p == r) return true
        val prefix = if (r.endsWith(File.separator)) r else r + File.separator
        return p.startsWith(prefix)
    }

    /** 两个路径是否有父子重叠（任一方向）。 */
    @JvmStatic
    fun overlaps(a: File, b: File): Boolean = isInsideOrSame(a, b) || isInsideOrSame(b, a)

    /** [target] 是否落在任一受保护根内（或反过来包含受保护根）。保守判定，用于「绝不删」。 */
    @JvmStatic
    fun isProtected(target: File, protectedRoots: List<File>): Boolean {
        for (root in protectedRoots) {
            if (overlaps(target, root)) return true
        }
        return false
    }

    /**
     * 白名单清理入口：返回 [root] 一级子项里**可以删**的那些。
     *
     * 规则（宁可少清）：
     * 1. [root] 不是目录 → 空；
     * 2. **[root] 与任一受保护根有父子重叠（任一方向）→ 整棵 [root] 一个都不删**。
     *    这包括「受保护根位于 [root] 内部」的情形：例如用户把下载目录配成 `<cacheDir>/download`，
     *    那么整个 cacheDir 都会被判为受保护、临时文件清理一个都不做——宁可少清，不可误删；
     * 3. 逐个子项再判一次 [isProtected]（**防御性代码**，见下）。
     *
     * 为什么规则 3 在当前规则下不可达仍保留：任一子项若与受保护根重叠，那么该受保护根必然
     * 也与 [root] 重叠（父子传递），规则 2 已经提前返回空。留着它是为了将来有人放宽规则 2
     * （例如只允许单方向判定）时，子项过滤仍然生效——**改规则 2 之前请先看这里**。
     */
    @JvmStatic
    fun entriesToClean(root: File, protectedRoots: List<File>): List<File> {
        if (!root.isDirectory) return emptyList()
        if (isProtected(root, protectedRoots)) return emptyList()
        val children = root.listFiles() ?: return emptyList()
        val result = ArrayList<File>(children.size)
        for (child in children) {
            // 防御性：规则 2 成立时走不到这里；规则 2 若被放宽，这一行才是最后一道闸
            if (!isProtected(child, protectedRoots)) result.add(child)
        }
        return result
    }
}
