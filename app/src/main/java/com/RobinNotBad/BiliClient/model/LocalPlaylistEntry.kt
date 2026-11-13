package com.RobinNotBad.BiliClient.model

import com.RobinNotBad.BiliClient.util.LocalPageFileIndexPolicy
import java.io.Serializable

/**
 * 本地缓存文件夹虚拟合集的**根层一项**：夹里的一个缓存视频。
 *
 * ## 为什么需要这个类
 *
 * 文件夹连播和在线收藏夹的虚拟合集是**同构的两层**：
 *
 * ```
 * 根层   [视频A(单P)] [视频B(3个P)] [视频C(单P)]
 *                          └─ 点「进入分P（3）」→  [P1] [P2] [P3]
 * ```
 *
 * 在线那层的每个分P 是「aid + cid」，播放器可以现取；本地没有服务端可查，必须把
 * 「这一项的第几个 P 该读哪个文件」跟着 Intent 一起带进去，所以单独抽一个可序列化的条目。
 *
 * 26.11.13 之前的实现没有这一层：根层每条只塞了 `LocalVideo.title` 和**一个** mp4，
 * `LocalVideo.pageList` 完全没用上——于是文件夹里的多P 缓存只能看 P1（用户报的问题）。
 *
 * ## 文件列表的形状（坑）
 *
 * `LocalVideo.videoFileList` 是**扁平的**：DASH 合并失败的页会同时留下 `video.mp4` 与
 * `audio.m4a`，两项都在这一个列表里；所以「页下标」和「文件下标」不等长。
 * 构建时一律按**页目录名**反查（`util/LocalPageFileIndexPolicy`），不要按下标推算。
 */
class LocalPlaylistEntry : Serializable {

    /** 根层显示名 = 缓存目录名（= [LocalVideo.title]） */
    @JvmField
    var title: String = ""

    /** 分P 名，与 [videoFiles] / [audioFiles] 等长 */
    @JvmField
    var pageNames: ArrayList<String> = ArrayList()

    /** 每页的主文件（通常是 `video.mp4`） */
    @JvmField
    var videoFiles: ArrayList<String> = ArrayList()

    /** 每页的外部音轨（DASH 未合并时的 `audio.m4a`）；空串 = 该页只有合并好的单文件 */
    @JvmField
    var audioFiles: ArrayList<String> = ArrayList()

    /** 每页的弹幕文件；空串 = 该页没有弹幕 */
    @JvmField
    var danmakuFiles: ArrayList<String> = ArrayList()

    /** 封面本地文件路径（= [LocalVideo.cover]）；空串 = 没有封面 */
    @JvmField
    var cover: String = ""

    /** 有效页数（三个列表取最短，防止半截数据导致越界） */
    fun pageCount(): Int = minOf(pageNames.size, videoFiles.size)

    fun isPlayable(): Boolean = pageCount() > 0

    /** 第 [pageIndex] 页要播的文件组；越界返回 null */
    fun mediaAt(pageIndex: Int): LocalPageMedia? {
        if (pageIndex < 0 || pageIndex >= pageCount()) return null
        return LocalPageMedia(
            videoFiles[pageIndex],
            audioFiles.getOrElse(pageIndex) { "" },
            danmakuFiles.getOrElse(pageIndex) { "" }
        )
    }

    companion object {
        private const val serialVersionUID = 1L

        /** 播放器 Intent extra：根层条目表（`ArrayList<LocalPlaylistEntry>`，Serializable） */
        const val EXTRA_LOCAL_PLAYLIST = "local_playlist"

        /** 播放器 Intent extra：根层名字（文件夹名），只用于选集卡片的层级标题 */
        const val EXTRA_LOCAL_PLAYLIST_TITLE = "local_playlist_title"

        /**
         * 用缓存视频的**原始三张列表**构建根层条目（纯函数，单测见 `LocalPlaylistEntryTest`）。
         *
         * @param pageList 页名（多P 合集已按 `.page_order.json` 排好）；空/单条 = 当成单文件视频
         * @param videoFileList **扁平**的文件路径列表（DASH 未合并的页会把 `video.mp4` 与
         *        `audio.m4a` 一起塞进来，一页占两个下标），所以按**页目录名**反查下标
         * @param danmakuFileList 弹幕文件，一页一项（与页名同下标，不受双文件影响的扁平列表无关）
         * @return 可播的条目；一个文件都对不上时返回 null（调用方整条跳过，保持各列表长度一致）
         */
        @JvmStatic
        fun build(
            title: String,
            pageList: List<String>?,
            videoFileList: List<String>?,
            danmakuFileList: List<String>?,
            cover: String?
        ): LocalPlaylistEntry? {
            val files = videoFileList?.filter { it.isNotEmpty() } ?: return null
            if (files.isEmpty()) return null

            val pageNames = ArrayList<String>()
            if (!pageList.isNullOrEmpty()) pageNames.addAll(pageList) else pageNames.add(title)

            val entry = LocalPlaylistEntry()
            entry.title = title
            entry.cover = cover ?: ""

            // 按目录分组的兜底表（组顺序 = 文件出现顺序）：目录名对不上页名时，第 i 页取第 i 组
            val dirGroups = dirGroupsOf(files)

            for ((pageIndex, pageName) in pageNames.withIndex()) {
                val indices = LocalPageFileIndexPolicy.indicesOfPage(files, pageName)
                // 查不到目录名（旧格式只存文件名、或目录被用户改名）时退化成「按目录分组的第 i 组」
                val pageFiles = if (indices.isNotEmpty()) {
                    indices.map { files[it] }
                } else {
                    dirGroups.getOrNull(pageIndex) ?: listOfNotNull(files.getOrNull(pageIndex))
                }
                if (pageFiles.isEmpty()) continue

                val videoFile = pageFiles.firstOrNull { it.endsWith(".mp4", ignoreCase = true) }
                    ?: pageFiles.first()
                // 双文件页（DASH 未合并）：主文件是视频轨，声音在另一条音轨里
                // 只有音频的页：主文件本身就是那条 m4a，别再当外部音轨喂一遍（会叠两路声音）
                val audioFile = pageFiles
                    .firstOrNull { it.endsWith(".m4a", ignoreCase = true) }
                    ?.takeIf { it != videoFile }
                    ?: ""

                entry.pageNames.add(pageName)
                entry.videoFiles.add(videoFile)
                entry.audioFiles.add(audioFile)
                entry.danmakuFiles.add(danmakuFileList?.getOrNull(pageIndex) ?: "")
            }

            return entry.takeIf { it.isPlayable() }
        }

        /**
         * 按**父目录**把扁平文件表分组，组顺序 = 文件在列表里出现的顺序。
         *
         * 单文件缓存 / 旧格式只存文件名时，同一个目录下的 `video.mp4` 与 `audio.m4a`
         * 会落进同一组——这正是"一页的一组文件"，比按文件下标硬猜可靠。
         */
        private fun dirGroupsOf(files: List<String>): List<List<String>> {
            val order = ArrayList<String>()
            val grouped = HashMap<String, MutableList<String>>()
            for (file in files) {
                val dir = LocalPageFileIndexPolicy.parentDirName(file) ?: ""
                val group = grouped.getOrPut(dir) {
                    order.add(dir)
                    ArrayList()
                }
                group.add(file)
            }
            return order.map { grouped.getValue(it) }
        }
    }
}

/**
 * 本地连播里**一页**要播的文件组。
 *
 * [audioFile] 非空时是 DASH 未合并的缓存：主文件只有视频轨，声音在这条外部音轨里，
 * 切P 时必须跟着换，否则新的一P 会没声音（反馈 #51 的另一半）。
 */
class LocalPageMedia(
    @JvmField val videoFile: String,
    @JvmField val audioFile: String,
    @JvmField val danmakuFile: String
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
