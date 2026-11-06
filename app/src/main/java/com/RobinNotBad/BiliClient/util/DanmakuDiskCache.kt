package com.RobinNotBad.BiliClient.util

import android.content.Context
import com.RobinNotBad.BiliClient.R
import java.io.File
import java.io.FileOutputStream

/**
 * 弹幕分段的磁盘缓存（批次 2）。
 *
 * 背景：弹幕是**按 360 秒分段**请求的（`DanmakuApi.SEGMENT_SECONDS`），一次播放只看几段，
 * 但同一视频反复播放、拖动进度、切 P 回来时会把同样的段重复下载。B 站的 `seg.so` 带 WBI 签名，
 * 请求本身不便宜（签名 + 限流），手表上还费电。所以按 **cid + 分段号** 把原始 protobuf 字节落盘：
 *
 * ```
 * cacheDir/danmaku_cache/dm_<cid>_<segmentIndex>.bin
 * ```
 *
 * 设计约束（逐条对应验收项）：
 * - **默认上限 64MB**，超过就按 LRU（最久未使用）淘汰，见 [DanmakuCachePolicy.namesToEvict]；
 * - **原子写入**：先写 `*.tmp` 再 rename，断电/被杀进程时不会留下半截文件被当成有效缓存；
 * - **异常一律不外抛**：缓存是纯加速，坏了最多是「这次没命中」，绝不能让播放失败；
 * - `cacheDir/danmaku_cache` 是 `cacheDir` 的**一级子目录**，且**不在**
 *   `util/AppStorageManager.protectedRoots()` 里 —— 所以用户在存储页点「清临时文件」会把它整棵删掉。
 *   这是**期望行为**（可再生缓存，清掉就是腾空间），不要把它加进保护清单。
 *
 * 缓存上限的档位值走 `list_choose`（见 `SettingTerminalPlayerActivity`），落盘为字符串，
 * 解析/钳制集中在 [parseMaxMb]（纯函数，单测见 `DanmakuDiskCacheTest`）。
 */
class DanmakuDiskCache(
    private val root: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES
) {

    /** 是否启用缓存（上限为 0 = 用户选了「关闭」）。 */
    fun isEnabled(): Boolean = maxBytes > 0L

    /**
     * 读一条缓存。
     *
     * 命中时顺手刷新 `lastModified`（LRU 的「最近使用」标记，Android 上没有 atime），
     * 读到的内容为空/解析前就发现损坏（0 字节）时删掉并当作未命中。
     */
    fun read(cid: Long, segmentIndex: Int): ByteArray? {
        if (!isEnabled()) return null
        if (!DanmakuCachePolicy.isValidKey(cid, segmentIndex)) return null
        return try {
            val file = File(root, fileName(cid, segmentIndex))
            if (!file.isFile) return null
            val bytes = file.readBytes()
            if (bytes.isEmpty()) {
                file.delete()
                return null
            }
            file.setLastModified(System.currentTimeMillis())
            bytes
        } catch (e: Exception) {
            Logu.w("弹幕缓存", "读取失败 cid=$cid seg=$segmentIndex：${e.message}")
            null
        }
    }

    /**
     * 写一条缓存（原子写 + 超限淘汰）。
     *
     * 顺序：写 `xxx.tmp` → `fd.sync()` → 删旧目标 → rename 覆盖。rename 在同一个目录内是原子的，
     * 所以读侧要么看到旧文件、要么看到完整的新文件，不会读到半截。
     * 任何一步失败都只记日志，不影响调用方。
     */
    fun write(cid: Long, segmentIndex: Int, data: ByteArray) {
        if (!isEnabled() || data.isEmpty()) return
        if (!DanmakuCachePolicy.isValidKey(cid, segmentIndex)) return
        try {
            if (!root.exists() && !root.mkdirs()) {
                Logu.w("弹幕缓存", "创建缓存目录失败：${root.absolutePath}")
                return
            }
            val target = File(root, fileName(cid, segmentIndex))
            val tmp = File(root, target.name + TMP_SUFFIX)
            FileOutputStream(tmp).use { out ->
                out.write(data)
                out.flush()
                out.fd.sync()
            }
            // 目标已存在时部分文件系统 rename 会失败，先删旧文件；
            // 删不掉（被占用等）就退化为直接覆盖写 —— 宁可牺牲这一次的原子性，也不能丢缓存。
            if (target.exists() && !target.delete()) {
                tmp.delete()
                FileOutputStream(target).use { it.write(data) }
                trim()
                return
            }
            if (!tmp.renameTo(target)) {
                FileOutputStream(target).use { it.write(data) }
                tmp.delete()
            }
            trim()
        } catch (e: Exception) {
            Logu.w("弹幕缓存", "写入失败 cid=$cid seg=$segmentIndex：${e.message}")
        }
    }

    /** 丢弃一条缓存（内容损坏时由调用方触发）。 */
    fun remove(cid: Long, segmentIndex: Int) {
        if (!DanmakuCachePolicy.isValidKey(cid, segmentIndex)) return
        try {
            File(root, fileName(cid, segmentIndex)).delete()
        } catch (_: Exception) {
        }
    }

    /** 按 LRU 淘汰到上限以内；顺手清掉崩溃残留的 `*.tmp`。 */
    fun trim() {
        if (!root.isDirectory) return
        try {
            val files = root.listFiles() ?: return
            for (file in files) {
                if (file.name.endsWith(TMP_SUFFIX)) file.delete()
            }
            val entries = files
                .filter { it.isFile && !it.name.endsWith(TMP_SUFFIX) }
                .map { DanmakuCachePolicy.Entry(it.name, it.length(), it.lastModified()) }
            for (name in DanmakuCachePolicy.namesToEvict(entries, maxBytes)) {
                File(root, name).delete()
            }
        } catch (e: Exception) {
            Logu.w("弹幕缓存", "淘汰失败：${e.message}")
        }
    }

    /** 当前缓存占用（字节）。只读统计，出错按 0 处理。 */
    fun sizeBytes(): Long = try {
        root.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
    } catch (_: Exception) {
        0L
    }

    /** 清空整棵缓存（设置页显式清理用；存储页的「清临时文件」也会直接删目录）。 */
    fun clear() {
        try {
            root.listFiles()?.forEach { if (it.isFile) it.delete() }
        } catch (e: Exception) {
            Logu.w("弹幕缓存", "清空失败：${e.message}")
        }
    }

    companion object {

        /** 缓存目录名（`context.cacheDir` 的一级子目录，**不要**加进 AppStorageManager 保护清单）。 */
        const val DIR_NAME = "danmaku_cache"

        /** 临时文件后缀（原子写的中间态，读侧永不读取）。 */
        const val TMP_SUFFIX = ".tmp"

        /** 1MB 的字节数。 */
        const val MB = 1024L * 1024L

        /** 缓存上限的设置键（`list_choose` 存字符串）。 */
        const val KEY_MAX_MB = "player_danmaku_cache_max_mb"

        /** 默认上限 64MB：约可缓存 6 分钟视频的 400 段，实际远超日常观看量。 */
        const val DEFAULT_MAX_MB = 64

        /** 档位上限 128MB。 */
        const val MAX_MB = 128

        /** 默认上限（字节）。 */
        const val DEFAULT_MAX_BYTES = DEFAULT_MAX_MB * MB

        /** 设置页展示用的档位名（资源 id，与 [storedValues] 一一对应）。 */
        @JvmStatic
        val displayNameRes: List<Int> = listOf(
            R.string.danmaku_cache_limit_off,
            R.string.danmaku_cache_limit_16,
            R.string.danmaku_cache_limit_32,
            R.string.danmaku_cache_limit_64,
            R.string.danmaku_cache_limit_128
        )

        /** 落盘用的档位值（`list_choose` 存字符串；"0" = 关闭）。 */
        @JvmStatic
        val storedValues: List<String> = listOf("0", "16", "32", "64", "128")

        /** 缓存目录：`cacheDir/danmaku_cache`。 */
        @JvmStatic
        fun dirFor(context: Context): File = File(context.cacheDir, DIR_NAME)

        /** 缓存键（纯函数）：`dm_<cid>_<segmentIndex>.bin`。 */
        @JvmStatic
        fun fileName(cid: Long, segmentIndex: Int): String = "dm_${cid}_${segmentIndex}.bin"

        /** 解析档位字符串为 MB 数（纯函数）：空/非法按默认 64，负数按 0（关闭），超过 128 按 128。 */
        @JvmStatic
        fun parseMaxMb(raw: String?): Int {
            val parsed = raw?.trim()?.toIntOrNull() ?: DEFAULT_MAX_MB
            return parsed.coerceIn(0, MAX_MB)
        }

        /** 档位字符串 → 字节上限（纯函数）。 */
        @JvmStatic
        fun maxBytesFromStored(raw: String?): Long = parseMaxMb(raw) * MB

        /** 读设置里的字节上限；读不到偏好（例如单测）时按默认 64MB。 */
        @JvmStatic
        fun resolveMaxBytes(context: Context): Long = try {
            maxBytesFromStored(
                SharedPreferencesUtil.getString(KEY_MAX_MB, DEFAULT_MAX_MB.toString())
            )
        } catch (_: Exception) {
            DEFAULT_MAX_BYTES
        }

        /** 供播放器直接取一实例。 */
        @JvmStatic
        fun forContext(context: Context): DanmakuDiskCache =
            DanmakuDiskCache(dirFor(context), resolveMaxBytes(context))
    }
}

/**
 * 缓存淘汰的纯逻辑（不碰文件系统，单测见 `DanmakuDiskCacheTest`）。
 *
 * 只做一件事：给定一批缓存条目和上限，算出该删哪些文件名。抽出来的原因很实际 ——
 * LRU 算错的代价是「缓存永远超限不再增长」或「刚写进去就被自己删掉」，而这两种在真机上
 * 都只能靠翻目录猜；纯函数则可以在单测里把所有边界一次钉死。
 */
object DanmakuCachePolicy {

    /** 一条缓存文件的元信息。 */
    data class Entry(
        val name: String,
        val sizeBytes: Long,
        val lastAccessMs: Long
    )

    /** 分段号是否可用：`seg.so` 的 `segment_index` **从 1 开始**，0 是无效值。 */
    @JvmStatic
    fun isValidKey(cid: Long, segmentIndex: Int): Boolean = cid > 0L && segmentIndex >= 1

    /** 总占用。 */
    @JvmStatic
    fun totalBytes(entries: List<Entry>): Long = entries.sumOf { it.sizeBytes }

    /**
     * 算出要淘汰的文件名，直到总占用 <= [maxBytes]。
     *
     * - 按 `lastAccessMs` 升序（最久未使用先删）；时间相同时按文件名排，保证结果稳定可测；
     * - `maxBytes <= 0`（关闭缓存）⇒ 全部删除；
     * - 已在预算内 ⇒ 返回空列表（不碰任何文件）。
     */
    @JvmStatic
    fun namesToEvict(entries: List<Entry>, maxBytes: Long): List<String> {
        if (entries.isEmpty()) return emptyList()
        if (maxBytes <= 0L) return entries.map { it.name }
        var total = totalBytes(entries)
        if (total <= maxBytes) return emptyList()
        val oldestFirst = entries.sortedWith(compareBy({ it.lastAccessMs }, { it.name }))
        val evicted = ArrayList<String>()
        for (entry in oldestFirst) {
            if (total <= maxBytes) break
            evicted.add(entry.name)
            total -= entry.sizeBytes
        }
        return evicted
    }
}
