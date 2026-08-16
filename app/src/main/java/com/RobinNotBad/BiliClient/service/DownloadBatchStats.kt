package com.RobinNotBad.BiliClient.service

/**
 * 单次下载批次（一轮下载服务运行）的总体进度统计。
 * 纯逻辑、无 Android 依赖，便于单元测试。
 *
 * 批次工作量 = 已完成 + 失败 + 下载中 + 等待中。
 * 总进度 = (已完成 + 失败 + 各下载中项目进度之和) / 总工作量。
 * 失败项目视作已完成（终态），不会卡住进度条；中途新增任务会让总工作量增大。
 */
class DownloadBatchStats {
    var completed: Int = 0
        private set
    var failed: Int = 0
        private set

    fun recordSuccess() {
        completed++
    }

    fun recordFailure() {
        failed++
    }

    fun reset() {
        completed = 0
        failed = 0
    }

    /**
     * @param activeProgressSum 所有仍在下载中项目的进度之和（每个 0..1）
     * @param activeCount       仍在下载中的项目数
     * @param waitingCount      仍在等待下载（队列中未开始）的项目数
     */
    fun overallProgress(activeProgressSum: Float, activeCount: Int, waitingCount: Int): Float {
        val totalUnits = completed + failed + activeCount + waitingCount
        if (totalUnits <= 0) return 0f
        val clampedSum = activeProgressSum.coerceIn(0f, activeCount.toFloat())
        val doneUnits = completed + failed + clampedSum
        return (doneUnits / totalUnits).coerceIn(0f, 1f)
    }
}

/**
 * 下载速度采样器（字节/秒）。调用方需保证单线程采样。
 */
class SpeedSampler {
    private var lastBytes: Long = 0
    private var lastTime: Long = 0

    fun reset(now: Long) {
        lastBytes = 0
        lastTime = now
    }

    /**
     * @return 本次采样的速度（字节/秒）；数据不足或两次采样间隔过短时返回 null
     */
    fun sample(bytes: Long, now: Long): Long? {
        val prevBytes = lastBytes
        val prevTime = lastTime
        lastBytes = bytes
        lastTime = now
        if (prevBytes <= 0) return null
        val elapsed = (now - prevTime) / 1000.0
        if (elapsed < 0.5) return null
        return ((bytes - prevBytes) / elapsed).toLong().coerceAtLeast(0)
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
