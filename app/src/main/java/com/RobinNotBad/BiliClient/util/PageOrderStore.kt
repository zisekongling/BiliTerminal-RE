package com.RobinNotBad.BiliClient.util

import org.json.JSONArray
import java.io.File

/**
 * 缓存里「合集 / 多P视频」各页的显示顺序（反馈 #98）。
 *
 * ## 为什么需要它
 *
 * 合集（多P）下载落盘是 `缓存根/合集标题/每一P的目录/`，而缓存页扫描子目录用的是
 * `File.listFiles()` 的返回顺序 —— 这个顺序由文件系统决定，**不保证等于 UP 主发布合集
 * 的顺序**。用户缓存完整个合集后看到的就是「1~10 集乱序」，找不到正确顺序。
 *
 * 下载入口 [com.RobinNotBad.BiliClient.api.PlayerApi.startDownloading] 手上正好有
 * `VideoInfo.pagenames`（服务端按合集顺序下发的页名列表），所以在入队时把它写进
 * `缓存根/合集标题/.page_order.json`，缓存页扫描时按这份顺序排即可。
 *
 * ## 边界
 *
 * - **只影响「页」的排列**：不新增/删除条目，扫描到但记录里没有的页按扫描顺序补在末尾，
 *   记录里有但磁盘上已删掉的页忽略；这条和 [LocalListSort.mergeOrder] 是同一套「不许丢条目」口径。
 * - 老缓存没有这个文件时，[orderedPages] 原样返回扫描顺序（行为与改动前完全一致）。
 * - 页名在落盘时会被 [FileUtil.stringToFile] 清洗（非法字符替换），所以记录里保存的也是
 *   **清洗后的名字**，否则和目录名对不上。
 */
object PageOrderStore {

    /** 记录文件放在合集父目录下，和 `video.mp4` / `.video_meta.json` 同级。 */
    const val FILE_NAME = ".page_order.json"

    /**
     * 解析记录文件内容。坏数据一律当空表（老缓存 / 手工改坏都不该把缓存页搞崩）。
     */
    fun parse(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val result = ArrayList<String>(array.length())
            for (index in 0 until array.length()) {
                val name = array.optString(index, "")
                if (name.isNotEmpty()) result.add(name)
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 序列化顺序表；空表写 `[]`，下次读到就是空。 */
    fun serialize(pages: List<String>): String = JSONArray(pages).toString()

    /**
     * 按持久化的顺序排「实际扫描到的页名」。
     *
     * @param scanned 本次扫描到的页目录名（扫描顺序）
     * @param stored  持久化的顺序（可能比磁盘多/少）
     * @return 只包含 [scanned] 中存在的名字：先按 [stored] 的顺序，剩下的按扫描顺序补尾
     */
    fun orderedPages(scanned: List<String>, stored: List<String>): List<String> {
        if (scanned.size <= 1 || stored.isEmpty()) return ArrayList(scanned)
        val existing = HashSet<String>(scanned.size * 2)
        for (name in scanned) existing.add(name)
        val merged = LocalListSort.mergeOrder(stored, scanned)
        val result = ArrayList<String>(scanned.size)
        for (name in merged) {
            // mergeOrder 会把「记录里有、磁盘上已删」的页也带出来，这里必须再过滤一遍
            if (existing.contains(name)) result.add(name)
        }
        return result
    }

    /** 读某个合集父目录里记录的顺序；没有记录或坏数据返回空表。 */
    fun read(parentFolder: File): List<String> {
        val file = File(parentFolder, FILE_NAME)
        if (!file.isFile) return emptyList()
        return try {
            parse(file.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 写顺序表。先写临时文件再改名，避免写到一半被杀进程留下半截 JSON（只影响顺序，不丢缓存）。
     */
    fun save(parentFolder: File, pages: List<String>) {
        if (pages.size <= 1) return
        try {
            if (!parentFolder.exists() && !parentFolder.mkdirs()) return
            val target = File(parentFolder, FILE_NAME)
            val temp = File(parentFolder, FILE_NAME + ".tmp")
            temp.writeText(serialize(pages))
            if (!temp.renameTo(target)) {
                // 个别文件系统不支持覆盖式 rename：退化成直接写，顺序信息比原子性更重要
                target.writeText(serialize(pages))
                temp.delete()
            }
        } catch (_: Exception) {
            // 顺序只是显示优化，写失败绝不能影响下载本身
        }
    }

    /**
     * 下载入队时记住这个合集/多P的页顺序（供缓存页扫描时排序）。
     *
     * 只在多P（`pagenames.size > 1`）时写；单P视频没有「页序」概念。
     * 页名先过 [FileUtil.stringToFile]，与真正落盘的目录名保持一致。
     */
    @JvmStatic
    fun remember(parentTitle: String, pagenames: List<String>) {
        if (pagenames.size <= 1) return
        val cleanNames = ArrayList<String>(pagenames.size)
        for (name in pagenames) {
            val clean = FileUtil.stringToFile(name)
            if (clean.isNotEmpty()) cleanNames.add(clean)
        }
        if (cleanNames.size <= 1) return
        save(FileUtil.getVideoDownloadPath(parentTitle, null), cleanNames)
    }
}
