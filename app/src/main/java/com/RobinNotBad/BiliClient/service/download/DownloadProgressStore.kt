package com.RobinNotBad.BiliClient.service.download

import android.os.SystemClock
import com.RobinNotBad.BiliClient.model.DownloadSection
import com.RobinNotBad.BiliClient.service.DownloadService

/**
 * 单个下载任务的进度快照（key 为 section.id）。
 *
 * 原本是 `DownloadService.Companion` 里的嵌套 data class，随进度映射一起搬到本包。
 * 之所以放在 `object` **外面**做顶层类：`DownloadProgressStore` 是 internal，
 * 而 `DownloadService.getDownloadProgress()` 是对外公开（@JvmStatic）的接口，
 * 公开成员不能暴露 internal 类型，所以类型本身必须是 public。
 *
 * [downloadedBytes] / [totalBytes] 是**整条任务**（视频流 + 音频流）的字节数，不是当前文件的；
 * [speedBps] 是这条任务自己的速度，由 [DownloadProgressStore] 在读取时按"是否已停更"折算，
 * 所以拿到 0 表示"未知/卡住了"，UI 该显示占位符而不是 0 B/s。
 */
data class DownloadProgressInfo(
    val progress: Float,
    val state: String,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedBps: Long = 0
)

/**
 * 下载进度、字节预算、速度采样与暂停标志的持有者（E2 拆分 DownloadService 第 2 步）。
 *
 * 这些都是**与 Service 实例无关的进程级状态**：进度映射要跨 Activity 读、暂停标志要跨线程看，
 * 所以放在 object 里由 [DownloadService.Companion] 用 @JvmStatic 门面转发，
 * 对外契约（Java 侧仍写 `DownloadService.getDownloadProgress(...)`）一个字都不变。
 *
 * 进度与速度的口径（与 Bili23-Downloader 对齐，见 [DownloadSectionBytes]）：
 * - 一条任务的进度 = 真实字节比例，不再按阶段权重摊派；
 * - 每条任务一个 [SpeedSampler]，喂进去的是该任务自己的累计字节，所以列表里每行能显示各自的速度；
 * - 总速度 = 各活跃任务速度之和（只在 UI 读取时现算，不缓存），不再是"全局字节数的采样"。
 *
 * 刻意保留的现状（不要"顺手修"）：
 * - [isSpeedMode] 不是 volatile。UI 线程直接读、下载线程直接写，这是既有数据竞争，
 *   加 volatile 属于行为变更（会改变可见性语义），不归本次重构管。
 */
internal object DownloadProgressStore {

    /** 是否处于"高速下载"模式，仅用于通知与列表页加前缀（非 volatile，现状保留） */
    var isSpeedMode: Boolean = false

    // 下载进度追踪：key=section.id
    private val downloadProgressMap =
        java.util.concurrent.ConcurrentHashMap<Long, DownloadProgressInfo>()

    // 每条任务的字节预算：key=section.id
    private val sectionBytes =
        java.util.concurrent.ConcurrentHashMap<Long, DownloadSectionBytes>()

    // 每条任务的速度采样器：key=section.id
    private val samplers = java.util.concurrent.ConcurrentHashMap<Long, SpeedSampler>()

    // 被用户暂停的任务：key=section.id。暂停后该任务的下载循环会尽快退出，状态置为 paused，
    // 调度器（只取 state="none"）不会重新拾取；恢复时清除标志并置回 none。
    val pausedMap = java.util.concurrent.ConcurrentHashMap<Long, Boolean>()

    fun isPaused(id: Long): Boolean = pausedMap.containsKey(id)

    /**
     * 暂停单个下载任务（不影响其他并行任务）。
     * 正在下载的线程会在下一轮 IO 循环中检测到暂停标志并退出。
     */
    fun pauseDownload(id: Long) {
        pausedMap[id] = true
        DownloadRepository.setState(id, "paused")
    }

    /**
     * 恢复被暂停的下载任务，并触发调度器重新拾取。
     */
    fun resumeDownload(id: Long) {
        pausedMap.remove(id)
        DownloadRepository.setState(id, "none")
        DownloadService.start(id)
    }

    // ---------- 字节预算 ----------

    /**
     * 给一条任务建立字节预算（视频流 + 音频流的大小）。每次开始下载时重建，
     * 避免上一次失败残留的字节数混进这次。
     *
     * [budgetBytes] <= 0 表示大小探测失败：不建预算，进度走 [DownloadProgressMath] 的阶段权重兜底。
     */
    fun beginSectionBytes(id: Long, budgetBytes: Long) {
        if (budgetBytes <= 0L) {
            sectionBytes.remove(id)
            return
        }
        sectionBytes[id] = DownloadSectionBytes(budgetBytes)
    }

    /**
     * 上报"当前文件已下 [downloadedInFile] 字节"。
     *
     * 有字节预算时进度 = (已完成文件字节 + 当前文件字节) / 预算；没有预算时退回改造前的
     * "阶段区间 + 文件内比例"（[baseProgress]、[endProgress]），文件总大小未知时再用伪进度。
     *
     * @param fileTotal 当前文件的总字节数；<=0 表示未知
     */
    @JvmStatic
    fun reportFileProgress(id: Long, state: String, downloadedInFile: Long, fileTotal: Long,
                           baseProgress: Float, endProgress: Float, pseudoProgress: Float) {
        val bytes = sectionBytes.computeIfAbsent(id) { DownloadSectionBytes() }
        val report = bytes.report(downloadedInFile)

        val progress: Float
        val shownDownloaded: Long
        val shownTotal: Long
        if (bytes.hasBudget) {
            progress = report.progress
            shownDownloaded = report.doneBytes
            shownTotal = report.totalBytes
        } else {
            progress = if (fileTotal > 0) {
                DownloadProgressMath.progressForBytes(downloadedInFile, fileTotal, baseProgress, endProgress)
            } else {
                DownloadProgressMath.progressForPseudo(pseudoProgress, baseProgress, endProgress)
            }
            // 兜底模式下没有整条任务的总额，只能显示当前文件自己的字节
            shownDownloaded = downloadedInFile
            shownTotal = if (fileTotal > 0) fileTotal else 0L
        }
        publish(id, progress, state, shownDownloaded, shownTotal)
        samplerFor(id).sample(report.doneBytes, now())
    }

    /** 一个文件下完了：把它实际下载的字节数并进这条任务的累计量 */
    @JvmStatic
    fun finishFileProgress(id: Long, actualBytes: Long) {
        val bytes = sectionBytes[id] ?: return
        val report = bytes.finishFile(actualBytes)
        val info = downloadProgressMap[id]
        if (info != null) publish(id, report.progress, info.state, report.doneBytes, report.totalBytes)
        samplerFor(id).sample(report.doneBytes, now())
    }

    /** 数据部分全部下完（后面只剩合并/转封装）：进度顶到 100% */
    fun finishAllProgress(id: Long, state: String) {
        val bytes = sectionBytes.computeIfAbsent(id) { DownloadSectionBytes() }
        val report = bytes.finishAll()
        publish(id, 1f, state, report.doneBytes, report.totalBytes)
    }

    /**
     * 任务收尾：把字节预算交给批次统计、丢掉这条任务的采样器。
     *
     * 成功任务会被从数据库删掉（[DownloadRepository.deleteSection]），所以它的字节必须在这里
     * 记进 [DownloadService.batchStats]，否则总进度会在它出队的瞬间往回掉。
     */
    fun retireSection(id: Long, success: Boolean) {
        val bytes = sectionBytes.remove(id)
        samplers.remove(id)
        if (success && bytes != null) {
            DownloadService.batchStats.recordSuccess(bytes.snapshot().totalBytes)
        }
    }

    // ---------- 进度映射 ----------

    fun getDownloadProgress(id: Long): DownloadProgressInfo? {
        val info = downloadProgressMap[id] ?: return null
        val speed = samplers[id]?.currentSpeed(now()) ?: 0L
        return if (speed == info.speedBps) info else info.copy(speedBps = speed)
    }

    fun getDownloadProgressMap(): java.util.concurrent.ConcurrentHashMap<Long, DownloadProgressInfo> {
        return downloadProgressMap
    }

    /** 只改进度与状态：字节数沿用上一次上报的值（下载弹幕、阶段切换等处用） */
    fun setDownloadProgress(id: Long, progress: Float, state: String) {
        val prev = downloadProgressMap[id]
        publish(id, progress, state, prev?.downloadedBytes ?: 0L, prev?.totalBytes ?: 0L)
    }

    fun setDownloadProgress(id: Long, progress: Float, state: String,
                            downloadedBytes: Long, totalBytes: Long) {
        publish(id, progress, state, downloadedBytes, totalBytes)
    }

    /**
     * 只换状态文案，进度与字节数原样保留。
     *
     * 阶段切换（视频→音频→合并）用它：进度已经由真实字节比例决定，再塞一个阶段常量进去
     * 只会让进度条跳一下。任务还没有进度记录（还没开始下）时什么都不做。
     */
    fun setDownloadState(id: Long, state: String) {
        val info = downloadProgressMap[id] ?: return
        downloadProgressMap[id] = info.copy(state = state)
    }

    fun removeDownloadProgress(id: Long) {
        downloadProgressMap.remove(id)
        samplers.remove(id)
    }

    /** 该任务是否还有下载中的进度记录（调度器判"遗留记录"用，等同于原来直接 containsKey） */
    fun hasProgress(id: Long): Boolean = downloadProgressMap.containsKey(id)

    // ---------- 批次统计 ----------

    /**
     * 当前所有活跃任务的已下字节与总字节之和，交给 [DownloadBatchStats.overallProgress] 算总进度。
     *
     * 注意 [sections] 里成功完成的任务已经被删掉了，它们贡献的字节由 batchStats 自己累计。
     */
    fun computeOverallProgress(sections: List<DownloadSection>): Float {
        var doneBytes = 0L
        var totalBytes = 0L
        var waitingCount = 0
        for (s in sections) {
            val info = downloadProgressMap[s.id]
            if (info != null) {
                doneBytes += info.downloadedBytes.coerceAtLeast(0L)
                totalBytes += info.totalBytes.coerceAtLeast(0L)
            } else if (s.state == "none") {
                waitingCount++
            }
        }
        return DownloadService.batchStats.overallProgress(doneBytes, totalBytes, waitingCount)
    }

    /** 当前仍在下载中的项目剩余字节数合计（用于预估剩余时间） */
    fun getActiveRemainingBytes(sections: List<DownloadSection>): Long {
        var remaining = 0L
        for (s in sections) {
            val info = downloadProgressMap[s.id]
            if (info != null && info.totalBytes > 0) {
                remaining += (info.totalBytes - info.downloadedBytes).coerceAtLeast(0)
            }
        }
        return remaining
    }

    /** 所有活跃任务的速度之和（字节/秒）。0 表示暂时测不出来（刚开下、或都卡住了） */
    fun getTotalSpeedBps(): Long {
        val at = now()
        var sum = 0L
        for (sampler in samplers.values) sum += sampler.currentSpeed(at)
        return sum
    }

    /** 批次开始时清空采样器，避免把上一批次的速度带进这一批次 */
    fun resetBatchState() {
        samplers.clear()
    }

    // ---------- 内部 ----------

    private fun samplerFor(id: Long): SpeedSampler =
        samplers.computeIfAbsent(id) { SpeedSampler() }

    private fun publish(id: Long, progress: Float, state: String,
                        downloadedBytes: Long, totalBytes: Long) {
        val previous = downloadProgressMap[id]?.progress ?: 0f
        val monotone = DownloadProgressMath.monotoneProgress(previous, progress)
        downloadProgressMap[id] = DownloadProgressInfo(
            monotone, state, downloadedBytes, totalBytes, downloadProgressMap[id]?.speedBps ?: 0L
        )
    }

    /** 单调时钟：速度采样不能用墙钟，用户改系统时间会把间隔算成负数 */
    private fun now(): Long = SystemClock.elapsedRealtime()
}
