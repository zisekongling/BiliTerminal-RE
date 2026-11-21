package com.RobinNotBad.BiliClient.service

/**
 * 单次下载批次（一轮下载服务运行）的总体进度统计。
 * 纯逻辑、无 Android 依赖，便于单元测试。
 *
 * **总进度按字节算，不再按任务个数算。** 改造前是
 * `(已完成 + 失败 + 各任务进度之和) / 任务数`——两种做法都不够准：
 * 一个 500MB 的视频和一个 3MB 的音频各算一份，进度条会为了那个小文件"原地等"；
 * 而各任务进度本身还是按阶段权重摊派出来的，误差会一起传进总量。
 * 现在一次下载批次的总量就是各任务字节预算之和，进度就是真实字节比例。
 *
 * 已完成任务会被从数据库里删掉（下载成功即出队），所以已下完的字节必须由这里累计，
 * 不能指望从 `DownloadSection` 列表里再读一遍。
 */
class DownloadBatchStats {
    var completed: Int = 0
        private set
    var failed: Int = 0
        private set

    /** 已成功完成的那些任务的字节数合计（同时计入分子与分母：它们已经 100% 下完） */
    private var completedBytes: Long = 0L

    private var completedTotalBytes: Long = 0L

    /** 一个任务成功完成。传该任务的总字节数（预算已知时 = 视频流 + 音频流的大小）。 */
    fun recordSuccess(totalBytes: Long) {
        completed++
        val bytes = totalBytes.coerceAtLeast(0L)
        completedBytes += bytes
        completedTotalBytes += bytes
    }

    /**
     * 一个任务失败。失败任务只记个数、不计字节：它下了多少字节我们自己也知道，
     * 但把它算进总量会让进度条在失败瞬间"跳一格"，而失败本来就该是终态而非进度。
     */
    fun recordFailure() {
        failed++
    }

    fun reset() {
        completed = 0
        failed = 0
        completedBytes = 0L
        completedTotalBytes = 0L
    }

    /**
     * @param activeDoneBytes  仍在下载中的任务累计已下字节之和
     * @param activeTotalBytes 仍在下载中的任务总字节之和
     * @param waitingCount     仍在排队（尚未开始）的任务数
     */
    fun overallProgress(activeDoneBytes: Long, activeTotalBytes: Long, waitingCount: Int): Float {
        val done = completedBytes + activeDoneBytes.coerceAtLeast(0L)
        var total = completedTotalBytes + activeTotalBytes.coerceAtLeast(0L)
        // 排队中的任务还没有字节预算。用已完成任务的平均大小给它们估一份：
        // 不估的话分母偏小、进度会虚高到 100%，等它们真正开始下载又猛地掉下来。
        if (waitingCount > 0) {
            val finishedCount = completed + failed
            if (finishedCount > 0) total += completedTotalBytes / finishedCount * waitingCount
        }
        if (total <= 0L) return 0f
        return (done.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }
}

/** 将字节/秒格式化为人类可读的速度字符串 */
fun formatDownloadSpeed(speedBps: Long): String {
    return if (speedBps >= 1048576) {
        String.format(java.util.Locale.CHINA, "%.1f MB/s", speedBps / 1048576.0)
    } else if (speedBps >= 1024) {
        String.format(java.util.Locale.CHINA, "%.1f KB/s", speedBps / 1024.0)
    } else {
        String.format(java.util.Locale.CHINA, "%.0f B/s", speedBps.toDouble())
    }
}
