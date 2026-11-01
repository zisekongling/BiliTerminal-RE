package com.RobinNotBad.BiliClient.util

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * 把本地文件以只读 `content://` 交给外部应用（凉腕播放器等）的 provider。
 *
 * ## 为什么不用现成的 FileProvider
 *
 * `FileProvider` 的根是 `res/xml/file_paths.xml` 里编译期写死的，而它支持的几个 tag 全是
 * 「主卷」语义（`<external-path>` 只认 `Environment.getExternalStorageDirectory()`，
 * `<external-media-path>` 只认 `getExternalMediaDirs()[0]`）。用户在设置里把「缓存路径」改到
 * 外置 SD 卡后，缓存文件在 `/storage/5C00-1D0E/Android/media/<包名>/...`，没有任何配好的根包含它，
 * `FileProvider.getUriForFile()` 抛 `IllegalArgumentException: Failed to find configured root`，
 * 整个点击流程崩掉（现场见 `docs/review/fix-progress.md`）。
 *
 * XML 是静态资源，没法按当前挂载的卷动态长根，所以外置卷这条路改由本 provider 在运行时算：
 * 根目录来自 [SharedFileRoots]（所有卷 + 卷根），URI 里直接带绝对路径。
 *
 * ## URI 形态
 *
 * `content://<包名>.LocalFileProvider/f/<Uri.encode(规范化绝对路径)>`
 *
 * 用「绝对路径」而不是「根 + 相对路径」，是为了让 URI 在应用重启、SD 卡重新挂载后仍然可解析：
 * 解析时不依赖「当初是用哪个根算出来的」，每次都用当前的卷列表重新校验一遍。
 *
 * ## 安全边界（逐条与审计对齐）
 *
 * - `android:exported="false"`：只有本应用能构造 URI。外部应用拿到 URI 的**唯一**途径是它作为
 *   `ACTION_VIEW` 的接收方收到本应用显式授予的一次性读权限（`FLAG_GRANT_READ_URI_PERMISSION`）。
 * - 只读：`openFile` 只接受 `"r"`，`insert` 直接抛异常，`update` / `delete` 返回 0。
 * - 路径校验：解析出的路径先 `canonicalPath`（消解 `..` 与符号链接），再要求它仍在
 *   [SharedFileRoots] 收集到的某个存储根之内。即使有人伪造出带 `../../data/...` 的 URI，
 *   也只会被拒——私有数据目录只有 `filesDir/video`（视频降级缓存）这一小块在共享根里，
 *   日志（`filesDir/logs`）、SharedPreferences 等都不在。
 * - 不列目录：`query` 只回答单个文件的 `DISPLAY_NAME` / `SIZE`，不提供任何枚举能力。
 *
 * 之所以**没有**改回 `file_paths.xml` 里已经被审计删掉的 `<root-path path="" />`：那等于把
 * `/data`、`/system` 一起登记进 FileProvider 的可授权范围（审计 M12-e）。这里只放行存储卷根，
 * 且只在「本应用自己要分享某个文件」时才会生成 URI，暴露面比 root-path 小得多。
 */
class LocalFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    /**
     * 媒体类型按扩展名给。外部播放器通常靠它决定用哪个解码器/是否当媒体处理；
     * 未知扩展名退回 `application/octet-stream`，让播放器自己嗅探。
     */
    override fun getType(uri: Uri): String? = resolveFile(uri)?.let { mimeTypeOf(it) }

    /**
     * 只回答 OpenableColumns 两个标准列：外部应用（尤其相册/播放器）会先查文件名与大小。
     * 没有文件的行返回 null（等价「这个 URI 不存在」），不抛异常。
     */
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val file = resolveFile(uri) ?: return null
        val columns = projection ?: DEFAULT_PROJECTION
        val cursor = MatrixCursor(columns, 1)
        val row = cursor.newRow()
        for (column in columns) {
            when (column) {
                OpenableColumns.DISPLAY_NAME -> row.add(OpenableColumns.DISPLAY_NAME, file.name)
                OpenableColumns.SIZE -> row.add(OpenableColumns.SIZE, file.length())
                else -> row.add(null)
            }
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        // 只读：播放/分享都只读，写模式一律拒绝，避免被当成「任意文件写入」的入口
        if (mode != MODE_READ) throw FileNotFoundException("LocalFileProvider 只提供只读访问：$mode")
        val file = resolveFile(uri) ?: throw FileNotFoundException("路径不在可共享的存储卷范围内：$uri")
        if (!file.isFile) throw FileNotFoundException("不是普通文件：$file")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("LocalFileProvider 是只读的")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    /**
     * URI → 文件。任何一步不成立都返回 null（调用方据此回落到内置播放器），不抛异常。
     *
     * 路径校验分两步，缺一不可：
     * 1. `canonicalPath` 消解 `..`、`.` 和符号链接；
     * 2. 规范化后的路径必须仍落在某个共享根内（[SharedFilePolicy] 的分段前缀判断）。
     *    注意规范化**之后**再判断，否则 `/a/../data` 这种字符串能骗过前缀匹配。
     */
    private fun resolveFile(uri: Uri): File? {
        val ctx = context ?: return null
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != PATH_PREFIX) return null
        val rawPath = segments[1]
        if (rawPath.isEmpty()) return null

        val canonical = try {
            File(rawPath).canonicalPath
        } catch (e: IOException) {
            Logu.w("LocalFileProvider", "路径无法规范化，拒绝：$rawPath")
            return null
        }
        if (SharedFilePolicy.findContainingRoot(SharedFileRoots.collect(ctx), canonical) == null) {
            Logu.w("LocalFileProvider", "路径不在任何存储根内，拒绝：$canonical")
            return null
        }
        return File(canonical)
    }

    companion object {
        /** authority 后缀：清单里写的是 `${applicationId}.LocalFileProvider`，与这里保持一致。 */
        const val AUTHORITY_SUFFIX = ".LocalFileProvider"

        /** URI 第一段路径，纯粹用来和以后可能新增的其它共享类型区分开。 */
        private const val PATH_PREFIX = "f"
        private const val MODE_READ = "r"

        private val DEFAULT_PROJECTION = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)

        /** 与清单声明的 authority 对齐（用包名而非写死，共存版/调试版都不会串）。 */
        @JvmStatic
        fun authorityOf(context: Context): String = context.packageName + AUTHORITY_SUFFIX

        /**
         * 生成可交给外部应用的只读 URI。
         *
         * @return [file] 落在某个存储卷根内时返回 URI；路径异常或不在任何卷内（例如某些 ROM 的
         *   特殊挂载点）返回 null —— 由调用方降级（改用内置播放器），**绝不让一次分享失败把页面崩掉**。
         */
        @JvmStatic
        fun getUriForFile(context: Context, file: File): Uri? {
            val canonical = try {
                file.canonicalPath
            } catch (e: IOException) {
                Logu.w("LocalFileProvider", "路径无法规范化，不共享：$file")
                return null
            }
            if (SharedFilePolicy.findContainingRoot(SharedFileRoots.collect(context), canonical) == null) {
                Logu.w("LocalFileProvider", "路径不在任何存储根内，不共享：$canonical")
                return null
            }
            return Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authorityOf(context))
                .appendPath(PATH_PREFIX)
                // 先自己 Uri.encode 再 appendEncodedPath："/" 会被编成 %2F，整条绝对路径只占
                // 一个 pathSegment —— resolveFile 里 `pathSegments.size == 2` 的校验依赖这一点。
                // （appendPath(canonical) 是等价写法，它内部同样会编码；这里显式写出来是为了让
                // 「路径整体当成一个段」这件事一眼可见。别改成 path()/encodedPath()：那两个设置的是
                // 整条 path，不是追加一个段。）
                .appendEncodedPath(Uri.encode(canonical))
                .build()
        }

        /**
         * 按扩展名给 MIME。本地缓存里最常见的是 mp4 / m4a，直接给准确类型；未知扩展名退回
         * `application/octet-stream`，让播放器自己嗅探（比瞎猜一个更具体的类型安全）。
         */
        private fun mimeTypeOf(file: File): String {
            val extension = file.name.substringAfterLast('.', "").lowercase()
            return when (extension) {
                "mp4" -> "video/mp4"
                "m4s" -> "video/mp4"
                "m4a" -> "audio/mp4"
                "webm" -> "video/webm"
                "mkv" -> "video/x-matroska"
                "flv" -> "video/x-flv"
                "ts" -> "video/mp2t"
                "mp3" -> "audio/mpeg"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    ?: "application/octet-stream"
            }
        }
    }
}
