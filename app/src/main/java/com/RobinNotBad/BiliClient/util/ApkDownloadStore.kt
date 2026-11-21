package com.RobinNotBad.BiliClient.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException

/**
 * 把校验通过的更新安装包另存一份进系统的 Download 目录（26.11.18 用户要求）。
 *
 * 为什么是「另存」而不是直接下到 Download：
 *
 * - 安装包现在下在应用专属缓存（<外部缓存>/update/，见 [UpdateManager.getApkFile]），断点续传靠残留
 *   文件、「静默更新」靠「文件还在就不重下」、存储空间页靠 [AppStorageManager.updateApkFile] 统计——
 *   换目录要连带改这三处；
 * - 用户要的只是「装完还能在文件管理器里找到这个包」，所以复制一份即可，缓存继续负责安装与续传
 *   （26.11.18 用户选定：复制到 Download、缓存保留）。
 *
 * 两条写入路径按系统版本分：Android 10+ 走 MediaStore（写公共目录不需要任何权限）；Android 9 及以下
 * 只能裸写 getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)，需要 WRITE_EXTERNAL_STORAGE——
 * 权限由调用方（更新页）在下载前问一次，没给就只是不落盘（26.11.18 用户选定：申请一次、拒绝才跳过）。
 *
 * 保存失败一律不影响更新本身：只记日志，调用方继续走安装。
 */
object ApkDownloadStore {

    private const val TAG = "更新保存"

    /** 安装包的 MIME：文件管理器据此认出这是可安装的包。 */
    const val APK_MIME = "application/vnd.android.package-archive"

    /** 文件名前缀用桌面图标名（app_name 是「RE：哔哩终端」），去掉全角冒号便于文件系统与排序。 */
    private const val NAME_PREFIX = "RE哔哩终端"

    /** 共存版与普通版同版本会撞名，靠这个后缀区分，免得系统把其中一个改名成 (1)。 */
    private const val COEXIST_SUFFIX = "-共存"

    /** 拿不到版本号时的兜底（内测包只有分享文件名，且它未必是版本号）。 */
    private const val FALLBACK_VERSION = "未知版本"

    /** 展示给用户的前缀：真实目录就是公共 Download 根目录，没必要拼绝对路径。 */
    private const val DISPLAY_DIR = "Download"

    /** 文件名主干上限：部分 ROM 的 MediaStore 对 display_name 长度有限制。 */
    private const val MAX_BASE_LENGTH = 80

    private val APK_EXT = Regex("\\.apk$", RegexOption.IGNORE_CASE)

    /** 文件系统不认的字符 + 控制字符，一律换成下划线。 */
    private val ILLEGAL_CHARS = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F\\u007F]")

    private val WHITESPACE = Regex("\\s+")

    /**
     * 纯函数：算出保存到 Download 时用的文件名，形如 RE哔哩终端-26.11.17.apk。
     *
     * 版本号来自 UpdateConfig.versionName——稳定通道是 26.11.17，测试通道带 -beta.1 这类后缀（原样保留，
     * 正好能区分通道）；内测包那里传进来的其实是分享文件名，可能带 .apk 后缀、也可能本来就以应用名开头，
     * 都要先剥掉再拼，免得拼出「RE哔哩终端-RE哔哩终端-xxx.apk」。
     *
     * @param versionName 版本号（可为空）
     * @param coexisting  是否共存版（BuildConfig.COEXIST）
     */
    @JvmStatic
    fun fileName(versionName: String?, coexisting: Boolean): String {
        val withoutExt = APK_EXT.replace(versionName.orEmpty(), "")
        val cleaned = ILLEGAL_CHARS.replace(WHITESPACE.replace(withoutExt, " "), "_")
            .trim()
            .trim('.', '-', '_')
        // 分享文件名可能写作「RE：哔哩终端-…」（桌面图标名就带全角冒号），比对前缀前先去掉冒号
        val normalized = cleaned.replace("：", "")
        val stripped = if (normalized.startsWith(NAME_PREFIX)) {
            normalized.removePrefix(NAME_PREFIX).trim().trim(':', '-', '_')
        } else {
            cleaned
        }
        val base = (if (stripped.isEmpty()) FALLBACK_VERSION else stripped).take(MAX_BASE_LENGTH)
        val suffix = if (coexisting) COEXIST_SUFFIX else ""
        return NAME_PREFIX + "-" + base + suffix + ".apk"
    }

    /**
     * 当前这台设备**现在**能不能往系统 Download 里放文件。只做判断，不申请权限、不弹提示。
     *
     * Android 10+ 恒为 true（MediaStore 不需要运行时权限）；更老的系统沿用 [FileUtil.canWriteDirectory]
     * 的「试写」判据——比起查 checkSelfPermission(WRITE_EXTERNAL_STORAGE)，试写还能覆盖「权限给了但存储
     * 是只读」这类实际情况。
     */
    @JvmStatic
    fun canSave(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        return FileUtil.canWriteDirectory(downloadDir())
    }

    /**
     * 把 [source] 复制一份进系统 Download，文件名用 [fileName]。
     *
     * @return 成功时返回给用户看的路径（Download/RE哔哩终端-26.11.17.apk），失败返回 null
     */
    @JvmStatic
    fun saveToDownloads(context: Context, source: File, fileName: String): String? {
        if (!source.isFile || source.length() <= 0L) {
            Logu.w(TAG, "源文件不存在或为空，跳过另存：" + source.absolutePath)
            return null
        }
        if (fileName.isBlank()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, source, fileName)
        } else {
            saveViaFile(context, source, fileName)
        }
    }

    /**
     * Android 10+：经 MediaStore 写公共 Download。
     *
     * 结构与 [PictureMediaStore.publishToGallery] 一致——先插一行 IS_PENDING=1 的记录拿到 uri，再往里写
     * 字节，写完把 pending 清零；中途失败必须删掉这一行，否则文件管理器里会留一个打不开的空壳。
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(context: Context, source: File, fileName: String): String? {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/"

        // 同名旧包先删：重下同一版本、或上一次留了半成品时，不该在 Download 里堆成「xxx (1).apk」
        try {
            resolver.delete(
                collection,
                MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?",
                arrayOf(fileName, relativePath)
            )
        } catch (e: Exception) {
            Logu.w(TAG, "清理同名旧包失败：" + e.message)
        }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, APK_MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = try {
            resolver.insert(collection, values)
        } catch (e: Exception) {
            Logu.e(TAG, "MediaStore 插入失败：" + e.message)
            null
        }
        if (uri == null) {
            Logu.e(TAG, "MediaStore 拒收：" + fileName)
            return null
        }

        return try {
            val output = resolver.openOutputStream(uri) ?: throw IOException("openOutputStream 返回 null")
            output.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            Logu.i(TAG, "已保存到下载目录：" + relativePath + fileName)
            DISPLAY_DIR + "/" + fileName
        } catch (e: Exception) {
            Logu.e(TAG, "写入下载目录失败：" + e.message)
            try {
                resolver.delete(uri, null, null)
            } catch (ignored: Exception) {
                // 删不掉也不过是留个空壳，不值得再抛
            }
            null
        }
    }

    /** Android 9 及以下：直接写公共下载目录，写完扫一次媒体库（不扫则部分文件管理器看不到新文件）。 */
    @Suppress("DEPRECATION")
    private fun saveViaFile(context: Context, source: File, fileName: String): String? {
        val dir = downloadDir() ?: return null
        if (!dir.exists() && !dir.mkdirs()) {
            Logu.w(TAG, "下载目录建不出来，跳过另存：" + dir.absolutePath)
            return null
        }
        val target = File(dir, fileName)
        return try {
            source.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            try {
                MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(APK_MIME), null)
            } catch (e: Exception) {
                Logu.w(TAG, "扫描媒体库失败：" + e.message)
            }
            Logu.i(TAG, "已保存到下载目录：" + target.absolutePath)
            DISPLAY_DIR + "/" + fileName
        } catch (e: Exception) {
            Logu.e(TAG, "写入下载目录失败：" + e.message)
            // 没权限时就是走到这里（FileNotFoundException: EACCES），留下的半截文件必须删掉
            try {
                target.delete()
            } catch (ignored: Exception) {
            }
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun downloadDir(): File? = try {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    } catch (e: Throwable) {
        Logu.w(TAG, "取下载目录失败：" + e.message)
        null
    }
}
