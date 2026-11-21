package com.RobinNotBad.BiliClient.service.download

/**
 * 一条下载任务的字节预算与已完成字节（进度主算法）。
 *
 * 为什么要这个类：改造前进度是按"人为阶段权重"摊派的（附件 0→12%、视频 12→52%、
 * 音频 52→88%、合并 88→100%）。那组权重是拍出来的，与真实字节比例无关：
 * 一个 4K 视频的流可能是音频的 30 倍，而权重只给 40:36，于是视频下完时进度条只走到 52%，
 * 音频下完时却冲到 88%——用户看到的就是"进度和速度都不准"。
 *
 * 现在和 Bili23-Downloader 同口径：任务开始前先探测出视频流、音频流各自的字节数，
 * 两者之和就是这个任务的**总字节数**（预算），此后任何时刻
 *
 *     进度 = (已完成流的字节 + 当前文件已下字节) / 总字节数
 *
 * 于是进度只跟真实字节走：分片下载往 `shared.totalDownloaded` 里累加的字节、单连接下载累加的
 * `totalDown` 都直接当分子用，不需要另算一套，也不会再出现"音频阶段原地卡住"的假停滞。
 *
 * 线程安全：进度上报来自多个分片线程，全部状态在 [synchronized] 内改。
 */
internal class DownloadSectionBytes(budgetBytes: Long = 0L) {

    /** 任务总字节数（视频流 + 音频流，探测所得）；0 表示探测失败、没有预算 */
    private var budget: Long = budgetBytes.coerceAtLeast(0L)

    /** 已经下完的文件的字节数合计 */
    private var completed: Long = 0L

    /** 上一次算出的进度，用于单调护栏（只许前进） */
    private var lastProgress: Float = 0f

    /** 上一次上报的累计已下字节，供 [snapshot] 取值（`completed` 只在下完整个文件时才增长） */
    private var lastDone: Long = 0L

    /** 是否有字节预算。false 时调用方必须退回 `DownloadProgressMath` 的阶段权重兜底算法 */
    val hasBudget: Boolean
        @Synchronized get() = budget > 0L

    /**
     * 上报"当前文件已下 [downloadedInFile] 字节"。
     *
     * 返回的 [Report] 里 `doneBytes` 是全任务累计已下字节（含此前下完的文件），
     * 速度采样直接用它，不需要关心当前在下哪个文件。
     */
    @Synchronized
    fun report(downloadedInFile: Long): Report {
        val done = completed + downloadedInFile.coerceAtLeast(0L)
        return publish(done)
    }

    /**
     * 一个文件下完了：把它的**实际**字节数并进累计量。
     *
     * 传实际字节而不是预算份额：进度必须反映磁盘上真实存在多少数据，
     * 否则探测值与实际长度有偏差时进度条会永远差一截。
     */
    @Synchronized
    fun finishFile(actualBytes: Long): Report {
        completed += actualBytes.coerceAtLeast(0L)
        return publish(completed)
    }

    /**
     * 补登预算：只探测到其中一条流（比如音频探测成功、视频失败）时，
     * 另一条流在真正开始下载前用它的 Content-Length 补进来，避免预算偏小导致进度虚高。
     */
    @Synchronized
    fun growBudget(extraBytes: Long) {
        if (extraBytes > 0L) budget += extraBytes
    }

    /** 整条任务的数据部分结束（后面只剩合并）：直接顶到 100% */
    @Synchronized
    fun finishAll(): Report {
        completed = maxOf(completed, budget)
        lastDone = completed
        lastProgress = 1f
        return Report(completed, maxOf(budget, completed), 1f)
    }

    /** 只读快照（不改单调状态），用于批次统计收尾时取这条任务的总字节 */
    @Synchronized
    fun snapshot(): Report = Report(lastDone, maxOf(budget, lastDone), lastProgress)

    private fun publish(done: Long): Report {
        lastDone = done
        val total = maxOf(budget, done)
        lastProgress = DownloadProgressMath.monotoneProgress(
            lastProgress,
            DownloadProgressMath.byteRatio(done, total)
        )
        return Report(done, total, lastProgress)
    }
}

/**
 * [DownloadSectionBytes] 的一次计算结果。
 *
 * [totalBytes] 恒 ≥ 1（预算已知时就是预算，未知时兜底为已完成字节），可以直接拿来算剩余时间；
 * [progress] 在 `hasBudget == false` 时没有意义，调用方退回阶段权重算法。
 */
internal data class Report(
    val doneBytes: Long,
    val totalBytes: Long,
    val progress: Float
)
