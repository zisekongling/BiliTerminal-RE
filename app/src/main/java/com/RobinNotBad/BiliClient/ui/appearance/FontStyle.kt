package com.RobinNotBad.BiliClient.ui.appearance

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import java.io.File

/**
 * 外观模块三：**自定义字体**。
 *
 * 用户在设置页通过文件管理器挑一个字体文件（TTF/OTF/TTC），应用把它拷进私有目录并全局应用。
 *
 * ## 只读模块
 * 按外观三模块的分工（见 `docs/architecture-map.md` §8.7），本对象负责**存储、校验、加载**，
 * **写入走门面** [AppearanceManager.setFontPath] / [AppearanceManager.clearFontPath]
 * ——门面负责递增外观版本号，让其它页面在 `onResume` 时重建并重新套用字体。
 *
 * ## 为什么是「拷贝到私有目录」而不是记住 URI
 * 1. `minSdk 24`，`Typeface.Builder(FileDescriptor)` 要 API 26，用不了；安全可用的只有
 *    `Typeface.createFromFile(File)`，它需要一个**真实路径**。
 * 2. 用户随时可能删除或移动原文件；记住 URI 还得处理 `takePersistableUriPermission`。
 *    拷一份到 `filesDir/custom_font/` 里最稳，且与源文件解耦。
 *
 * ## 性能（手表优先）
 * - 拷贝与校验发生在**用户选完文件之后的子线程**里（由调用方包 `CenterThreadPool`），
 *   不占主线程。
 * - [typeface] 有进程级缓存：字体只在首次使用时从磁盘解析一次。
 * - **没装自定义字体时，本模块对渲染路径零开销**——[typeface] 返回 null，
 *   应用侧的遍历会立即短路（见 `CustomFont`）。
 */
object FontStyle {

    /** SharedPreferences key：自定义字体的私有路径。空串 = 未启用。 */
    const val KEY_PATH = SettingsKeys.UI_FONT_PATH

    /** 私有目录名与文件名（用户选的原文件名可能含奇怪字符，统一改名最省事）。 */
    private const val DIR_NAME = "custom_font"
    private const val FILE_NAME = "custom_font.ttf"

    /** 字体文件大小上限。手表存储紧张，超过就拒绝，避免用户误选视频/压缩包。 */
    const val MAX_BYTES = 32L * 1024 * 1024

    /** 进程级 Typeface 缓存；[loaded] 区分「没加载过」与「加载过但失败」。 */
    @Volatile
    private var cached: Typeface? = null

    @Volatile
    private var loaded = false

    // ==================== 路径与状态 ====================

    /** 自定义字体的私有存放路径。 */
    fun fontFile(context: Context): File = File(File(context.filesDir, DIR_NAME), FILE_NAME)

    /** 当前存档里的字体路径（空串表示未启用）。 */
    fun currentPath(): String = SharedPreferencesUtil.getString(KEY_PATH, "")

    /** 是否已启用自定义字体（只看到路径，不校验文件是否还在）。 */
    fun hasCustomFont(): Boolean = currentPath().isNotEmpty()

    /** 展示用文件名（取路径最后一段）；未启用返回 null。 */
    fun currentFileName(): String? =
        currentPath().takeIf { it.isNotEmpty() }?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }

    // ==================== 加载 ====================

    /**
     * 取当前自定义字体；未启用或加载失败返回 `null`（调用方据此短路，退回系统字体）。
     *
     * 结果有进程级缓存：正常路径下每个进程只解析一次字体文件。
     */
    fun typeface(context: Context): Typeface? {
        if (loaded) return cached
        synchronized(this) {
            if (loaded) return cached
            cached = loadFromDisk(context)
            loaded = true
            return cached
        }
    }

    /** 作废 [typeface] 的缓存。安装/清除字体后必须调用。 */
    fun invalidateCache() {
        synchronized(this) {
            cached = null
            loaded = false
        }
    }

    /**
     * 纯逻辑：给定存档路径，是否应当尝试加载字体。
     *
     * 抽成纯函数是为了让「未配置自定义字体 → 不加载 → 渲染路径零开销」这条性能约定
     * 能被单测直接钉住（不需要 Context）。
     */
    fun shouldLoad(path: String): Boolean = path.isNotEmpty()

    private fun loadFromDisk(context: Context): Typeface? {
        val path = currentPath()
        if (!shouldLoad(path)) return null
        val file = File(path)
        if (!file.isFile) return null
        return try {
            Typeface.createFromFile(file)
        } catch (e: Throwable) {
            // 文件被删/损坏/超出平台解析能力：静默退回系统字体，不让应用崩在渲染路径上
            null
        }
    }

    // ==================== 安装与清除 ====================

    /**
     * 安装用户选中的字体文件：拷进私有目录 → 校验 → 落盘路径。
     *
     * **必须在子线程调用**（拷贝可能几 MB）。
     *
     * @return `null` 表示成功；否则返回中文失败原因，供 UI 直接展示。
     */
    fun install(context: Context, uri: Uri): String? {
        val header = readHeader(context, uri) ?: return "无法读取该文件"
        val reject = rejectReason(header)
        if (reject != null) return reject

        val target = fontFile(context)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "$FILE_NAME.tmp")

        try {
            val size = context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: return "无法读取该文件"
            if (size <= 0L) return "文件为空"
            if (size > MAX_BYTES) return "文件过大（超过 ${MAX_BYTES / 1024 / 1024}MB）"
        } catch (e: Throwable) {
            temp.delete()
            return "复制失败：${e.message ?: e.javaClass.simpleName}"
        }

        // 换名到目标位置（同一目录内，原子性足够）
        if (target.exists() && !target.delete()) {
            temp.delete()
            return "无法覆盖旧字体文件"
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            return "无法保存字体文件"
        }

        AppearanceManager.setFontPath(target.absolutePath)
        return null
    }

    /** 删除已安装的字体并回到系统字体。 */
    fun clear(context: Context) {
        fontFile(context).delete()
        File(fontFile(context).parentFile, "$FILE_NAME.tmp").delete()
        AppearanceManager.clearFontPath()
    }

    // ==================== 校验（纯函数，可单测） ====================

    /** 读文件头 4 字节。 */
    private fun readHeader(context: Context, uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(4)
            val read = input.read(buf)
            if (read < 4) null else buf
        }
    } catch (e: Throwable) {
        null
    }

    /**
     * 文件头校验：是否为 Android 能解析的字体。
     *
     * @return `null` 表示看起来是合法字体；否则返回中文拒绝原因。
     */
    fun rejectReason(header: ByteArray?): String? {
        if (header == null || header.size < 4) return "文件不是字体（内容太短）"
        val tag = String(header, 0, 4, Charsets.US_ASCII)
        return when {
            // WOFF/WOFF2 是网页字体，Android 的 Typeface 解析不了——单独给出提示，
            // 这是用户最容易挑错的一类文件。
            tag == "wOFF" || tag == "wOF2" -> "不支持 WOFF/WOFF2 网页字体，请选 TTF / OTF / TTC"
            isSupportedMagic(header) -> null
            else -> "不是可识别的字体文件（支持 TTF / OTF / TTC）"
        }
    }

    /**
     * 支持的字体文件魔数：
     * - `00 01 00 00`：TrueType
     * - `4F 54 54 4F`（"OTTO"）：OpenType/CFF
     * - `74 72 75 65`（"true"）：Apple TrueType
     * - `74 74 63 66`（"ttcf"）：字体集合
     */
    private fun isSupportedMagic(h: ByteArray): Boolean {
        fun at(i: Int) = h[i].toInt() and 0xFF
        return (at(0) == 0x00 && at(1) == 0x01 && at(2) == 0x00 && at(3) == 0x00) ||
            (at(0) == 0x4F && at(1) == 0x54 && at(2) == 0x54 && at(3) == 0x4F) ||
            (at(0) == 0x74 && at(1) == 0x72 && at(2) == 0x75 && at(3) == 0x65) ||
            (at(0) == 0x74 && at(1) == 0x74 && at(2) == 0x63 && at(3) == 0x66)
    }
}
