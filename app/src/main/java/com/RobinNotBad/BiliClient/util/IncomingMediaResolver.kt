package com.RobinNotBad.BiliClient.util

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

/**
 * 把外部应用递进来的视频 Uri（ACTION_VIEW / ACTION_SEND）解析成内置播放器能直接打开的本地路径。
 *
 * 为什么需要它：内置播放器（PlayerActivity）只吃一个字符串地址，交给 ijk 的 setDataSource(path)。
 * 文件管理器、相册、分享菜单递过来的是 content:// 或 file://，而 Android 11+ 的分区存储下
 * 别家的文件通常**没有可直读的真实路径**，所以这里分两步走：
 * 1. 能拿到真实路径且读得动（自家缓存、旧系统、部分 ROM）→ 直接用，零拷贝；
 * 2. 否则把内容流复制到 cacheDir/incoming_video/ 下的临时文件再播，播完靠 TTL 清理。
 *
 * 只做与 Uri 无关的判据放在纯函数里（[titleOf] / [extensionOf] / [enoughSpace] / [staleFiles]），
 * 便于单测；真正碰 ContentResolver 的只有 [resolve]。
 */
object IncomingMediaResolver {

    private const val TAG = "ExternalVideo"

    /** 临时文件目录名（位于 cacheDir 下，随卸载/清理缓存一起消失）。 */
    const val TEMP_DIR_NAME = "incoming_video"

    /** 临时文件保留时长：一天。比一天还旧的文件在下一次进入播放时清掉。 */
    const val TEMP_TTL_MS = 24L * 60 * 60 * 1000

    /** 复制前要给系统留的余量，避免把缓存盘写满导致播放器自己写不了临时数据。 */
    const val SPACE_RESERVE_BYTES = 64L * 1024 * 1024

    /** 解析结果：[path] 是能直接喂给 ijk 的本地路径，[temporary] 表示它是我们复制出来的临时文件。 */
    data class Resolved(val path: String, val title: String, val temporary: Boolean)

    /**
     * 解析外部 Uri。失败时抛 [IOException]（调用方负责提示并结束），不返回 null：
     * 「为什么播不了」需要区分（没权限 / 空路径 / 空间不足），全塌成 null 就没法给用户解释。
     */
    fun resolve(context: Context, uri: Uri): Resolved {
        val scheme = uri.scheme?.lowercase()

        // file:// 或裸路径：先按真实文件试一次，读不动再走流复制（file:// 的 openInputStream 也是合法的）
        if (scheme == null || scheme == "file") {
            val path = uri.path
            if (!path.isNullOrEmpty()) {
                val file = File(path)
                if (file.canRead()) return Resolved(file.absolutePath, titleOf(null, file.name), false)
            }
        }

        val displayName = queryDisplayName(context, uri)
        val declaredSize = querySize(context, uri)

        // content:// 先试 MediaStore 的 DATA 列：旧系统与部分 ROM 上这条路能直接拿到可读路径
        val realPath = queryDataPath(context, uri)
        if (realPath != null) {
            val file = File(realPath)
            if (file.canRead()) return Resolved(file.absolutePath, titleOf(displayName, file.name), false)
        }

        // 兜底：复制到 cacheDir。分区存储下这是唯一稳的路，代价是占一份空间
        val dir = File(context.cacheDir, TEMP_DIR_NAME)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("无法创建临时目录：" + dir.absolutePath)
        cleanStaleFiles(dir)
        if (declaredSize > 0 && !enoughSpace(declaredSize, dir.usableSpace)) {
            throw IOException("剩余空间不足：需要 " + declaredSize + "，可用 " + dir.usableSpace)
        }

        val ext = extensionOf(displayName ?: uri.lastPathSegment)
        val target = File(dir, "incoming_" + System.currentTimeMillis() + if (ext.isEmpty()) "" else "." + ext)
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("调用方没有授予可读权限")
        input.use { source ->
            target.outputStream().use { sink -> source.copyTo(sink, 256 * 1024) }
        }
        if (target.length() <= 0L) {
            target.delete()
            throw IOException("读到的内容是空的")
        }
        Logu.d(TAG, "已复制到临时文件：" + target.absolutePath)
        return Resolved(target.absolutePath, titleOf(displayName, target.name), true)
    }

    // ---------------- 以下为纯函数，单测覆盖 ----------------

    /**
     * 播放器标题：优先用调用方给的文件显示名，去掉扩展名；都没有就返回空串，
     * 由调用方换成 strings.xml 里的兜底文案（本文件不硬编码用户可见文案）。
     */
    fun titleOf(displayName: String?, fallbackName: String?): String {
        val raw = displayName?.trim().takeUnless { it.isNullOrEmpty() }
            ?: fallbackName?.trim().takeUnless { it.isNullOrEmpty() }
            ?: return ""
        val dot = raw.lastIndexOf('.')
        val title = if (dot > 0) raw.substring(0, dot) else raw
        return title.trim()
    }

    /** 取扩展名（小写、只留字母数字），给临时文件起名用；没有扩展名返回空串。 */
    fun extensionOf(name: String?): String {
        if (name.isNullOrEmpty()) return ""
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return ""
        val ext = name.substring(dot + 1).lowercase()
        if (ext.length > 5) return ""
        return if (ext.all { it.isLetterOrDigit() }) ext else ""
    }

    /** 复制前的空间判据：要放得下这个文件，并且留足余量。 */
    fun enoughSpace(size: Long, freeBytes: Long): Boolean {
        if (size <= 0L) return true
        return freeBytes - size >= SPACE_RESERVE_BYTES
    }

    /** 挑出该删的旧临时文件：比 TTL 还旧的（正在播的那个文件时间是新的，不会命中）。 */
    fun staleFiles(files: List<File>, now: Long, ttlMs: Long = TEMP_TTL_MS): List<File> =
        files.filter { now - it.lastModified() > ttlMs }

    // ---------------- ContentResolver 探测 ----------------

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
            }
    }.getOrNull()

    private fun querySize(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use 0L
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else 0L
            } ?: 0L
    }.getOrDefault(0L)

    private fun queryDataPath(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
            }
    }.getOrNull()

    private fun cleanStaleFiles(dir: File) {
        runCatching {
            val files = dir.listFiles()?.toList() ?: return
            val now = System.currentTimeMillis()
            staleFiles(files, now).forEach { if (it.delete()) Logu.d(TAG, "清掉过期临时文件：" + it.name) }
        }
    }
}
