package com.RobinNotBad.BiliClient.util

import android.os.Build
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.BuildConfig
import com.RobinNotBad.BiliClient.service.download.DownloadPathSpec
import java.io.File
import java.util.concurrent.Executors

/**
 * 「缓存里还留着 DASH 双文件」的机会式重封装（反馈 #231）。
 *
 * ## 为什么需要它
 *
 * 1080P 及以上是 DASH：视频流与音频流是两个文件。播放器遇到双文件时把音频挂在第二路
 * [android.media.MediaPlayer] 上（`PlayerActivity.initAudioTrack`），而倍速只作用在主播放器的视频流上
 * ——音轨不跟速。用户报的就是「缓存视频有时无法使用倍速，或倍速下音画不同步」。
 *
 * 下载侧早就解决了：`DownloadService.mergeDashPair` 在下载成功后调用 [MediaMerger.mergeAv] 把两条流
 * 重封装成一个 MP4。但**只有下载成功的那一批**会合并，盘上仍留着三类双文件：老版本下的、合并失败
 * 保留的、合并被中断留下的。这些永远等不到第二次合并，于是同一个问题会一直挂在用户身上。
 *
 * ## 做法
 *
 * 缓存页扫描时把发现的目录排进这里的单线程队列（[enqueueAll]），后台逐份重封装：
 * 成功则用合并产物替换 `video.mp4` 并删掉 `audio.m4a`（与下载侧完全同语义），失败**原样保留双文件**
 * ——播放器仍能挂外挂音轨正常播放，只是继续没有倍速。
 *
 * 刻意不做的事：
 * - **不阻塞、不改当前列表**：合并耗时几秒到几十秒，列表照旧显示这一条；合并产物在下次进入缓存页
 *   时自然被认成单文件（[CacheMediaPolicy.pickMediaName] 只认 `video.mp4`）。
 * - **不动外部缓存根**：那些目录属于别的分支，只读（调用方负责不排入）。
 * - **同一目录一个进程只试一次**：合并失败基本是永久性的（[MediaMuxer] 不支持该编码、容器异常），
 *   重试只是白烧电量与磁盘写入。
 * - **不动正在播放的目录**（[setPlayingDir]）：播放中替换/删除正在被 `MediaPlayer` 读的文件在
 *   Linux 上不会立刻出错，但用户下一次 seek / 重新载入就会撞上「文件不存在」。
 * - **Android 7.1 以下整体跳过**：这些缓存基本是带 B 帧的 H.264，而 MediaMuxer 从 API 25
 *   才支持 MP4 B 帧；更低版本写进去会 native 崩溃（catch 不住），只能保留双文件。
 */
object CacheAudioRepair {

    private const val TAG = "CacheAudioRepair"

    /** 本进程已经尝试过的目录（绝对路径），见类文档「同一目录一个进程只试一次」。 */
    private val attempted = HashSet<String>()

    /** 正在播放的本地媒体所在目录（绝对路径）。播放器在 `onPrepared` 上报、销毁时清空。 */
    @Volatile
    private var playingDir: String? = null

    /**
     * 单线程：容器重封装是 IO 与 CPU 双密集的操作，串行做才不会和下载、缓存扫描抢资源，
     * 也避免多份合并同时把存储写满。
     */
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "cache-audio-repair").apply { isDaemon = true }
    }

    /**
     * 播放器告知「正在放这个目录里的文件」，缓存页据此跳过它。
     *
     * 传目录而不是文件：播放页本来就只维护 `localMediaDir` 这一层，分P 切换时它跟着换，
     * 由调用方直接给目录最不容易传错。
     *
     * @param dir 本地媒体所在目录；在线播放/直播传 null 即视为「没有本地目录要避让」
     */
    @JvmStatic
    fun setPlayingDir(dir: File?) {
        playingDir = dir?.absoluteFile?.absolutePath
    }

    /**
     * 这个目录是不是「DASH 双文件还留着」：`video.mp4` 与 `audio.m4a` 同时存在，且不在下载中。
     *
     * 纯文件名判定（不碰文件系统），与 [CacheMediaPolicy] 一样可以直接跑 JVM 单测。
     * 下载中的目录（`.DOWNLOADING` 存在）跳过：那份双文件是下载流程自己的中间态，
     * `DownloadService.mergeDashPair` 收尾时会处理。
     */
    @JvmStatic
    fun shouldRepair(names: Collection<String>?): Boolean {
        if (names.isNullOrEmpty()) return false
        if (CacheMediaPolicy.isDownloading(names)) return false
        return names.contains(DownloadPathSpec.FILE_VIDEO) &&
            names.contains(DownloadPathSpec.FILE_AUDIO)
    }

    /**
     * 把一批缓存目录排进后台重封装。
     *
     * 已试过、正在播放的目录会被静默跳过；真正缺文件的目录在 [repairOne] 里还会再判一次
     * （排队期间用户可能把文件删了）。
     */
    @JvmStatic
    fun enqueueAll(dirs: Collection<File>) {
        if (dirs.isEmpty()) return
        // 这些缓存基本都是带 B 帧的 H.264；Android 7.1 之前 MediaMuxer 写 B 帧会 native 崩溃，
        // 不是 Java 异常、catch 不住，只能整段跳过，保留双文件继续按外挂音轨播放。
        if (!MediaMuxerSupport.supportsBFrameMuxing(Build.VERSION.SDK_INT)) {
            Logu.i(TAG, "跳过缓存重封装：Android 7.1（API 25）以下 MediaMuxer 不支持 B 帧")
            return
        }
        val accepted = ArrayList<File>(dirs.size)
        synchronized(attempted) {
            for (dir in dirs) {
                val key = dir.absolutePath
                if (key == playingDir) continue
                if (!attempted.add(key)) continue
                accepted.add(dir)
            }
        }
        for ((index, dir) in accepted.withIndex()) {
            try {
                worker.execute { repairOne(dir, index + 1, accepted.size) }
            } catch (e: Exception) {
                Logu.e(TAG, "排入重封装失败：${dir.name} ${e.message}")
            }
        }
    }

    /** 真正干活：重封装 → 替换视频 → 删音频。任何一步失败都保留原样（双文件仍可播放）。 */
    private fun repairOne(dir: File, index: Int, total: Int) {
        val videoFile = File(dir, DownloadPathSpec.FILE_VIDEO)
        val audioFile = File(dir, DownloadPathSpec.FILE_AUDIO)
        val where = "index=$index/$total dir=${dir.absolutePath}"
        var end = "unknown"

        // 同步落盘：native MediaMuxer 崩溃时进程会当场死，异步队列里的日志会丢。
        syncMarker("REPAIR-START", "$where video=${videoFile.length()} audio=${audioFile.length()}")
        try {
            // 排队期间用户可能已经开始播它了：开抢之前再看一眼
            if (dir.absolutePath == playingDir) {
                end = "skip-playing"
                return
            }
            if (!shouldRepair(dir.list()?.toList())) {
                end = "skip-not-pair"
                return
            }
            val mergedTmp = File(dir, DownloadPathSpec.FILE_VIDEO_MERGING)
            val startedAt = System.currentTimeMillis()
            val result = try {
                MediaMerger.mergeAv(
                    videoFile = videoFile,
                    audioFile = audioFile,
                    mergedFile = mergedTmp,
                    onStage = { stage ->
                        syncMarker("REPAIR-STAGE", "$where stage=$stage")
                    }
                )
            } catch (e: Exception) {
                Logu.e(TAG, "重封装异常：${dir.name} ${e.message}")
                end = "exception:${e.javaClass.simpleName}"
                return
            }
            val cost = System.currentTimeMillis() - startedAt
            if (result != MediaMerger.RESULT_OK) {
                Logu.w(TAG, "重封装未完成（result=$result，耗时${cost}ms），保留双文件：${dir.name}")
                end = "result=$result"
                return
            }
            if (!mergedTmp.isFile || mergedTmp.length() <= 0L) {
                Logu.e(TAG, "重封装产物为空，保留双文件：${dir.name}")
                mergedTmp.delete()
                end = "empty-output"
                return
            }
            if (!replaceTemp(mergedTmp, videoFile)) {
                Logu.e(TAG, "重封装产物替换失败，保留双文件：${dir.name}")
                mergedTmp.delete()
                end = "replace-failed"
                return
            }
            if (!audioFile.delete()) {
                // 视频已经换成单文件了，音频删不掉只是多占一份空间，不影响播放
                Logu.w(TAG, "已换成合并视频，但 audio.m4a 删不掉：${dir.name}")
            }
            Logu.i(TAG, "重封装完成（耗时${cost}ms）：${dir.name}")
            // 缓存文件真的变了：让还活着的缓存页下次回来时重扫，条目大小才对得上（反馈 #162）
            CacheChangeSignal.markChanged()
            end = "ok"
        } finally {
            syncMarker("REPAIR-END", "$where result=$end")
        }
    }

    /** 诊断包专用：同步写一条重封装标记；普通包是 no-op。 */
    private fun syncMarker(title: String, body: String) {
        if (!BuildConfig.DEBUG || !BuildConfig.DIAG_FULL_LOG) return
        PublicLogRecorder.appendSync(BiliTerminal.context, title, body)
    }

    /**
     * 「备份 → 替换 → 失败回滚」，与 `DownloadService.safeReplaceTemp` 同语义。
     *
     * 这里不复用那一份：它在 DownloadService 内部且是 private，为一次后台替换把下载服务的方法
     * 提出去不划算；两个方法的差异只在于这里**绝不**在替换失败后动源文件。
     */
    private fun replaceTemp(tmpFile: File, finalFile: File): Boolean {
        val bakFile = File(finalFile.parentFile, finalFile.name + ".bak")
        return try {
            if (!finalFile.exists() || finalFile.renameTo(bakFile)) {
                if (tmpFile.renameTo(finalFile)) {
                    bakFile.delete()
                    true
                } else {
                    // 替换失败：把旧视频恢复原位，临时文件由调用方删掉
                    if (bakFile.exists()) bakFile.renameTo(finalFile)
                    false
                }
            } else {
                // 备份不成（罕见）：Android 的 rename 能覆盖目标，直接覆盖
                tmpFile.renameTo(finalFile)
            }
        } catch (e: Exception) {
            Logu.e(TAG, "替换异常：${finalFile.name} ${e.message}")
            if (bakFile.exists() && !finalFile.exists()) {
                try { bakFile.renameTo(finalFile) } catch (_: Exception) {}
            }
            false
        }
    }
}
