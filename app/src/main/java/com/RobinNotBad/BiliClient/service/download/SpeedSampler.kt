package com.RobinNotBad.BiliClient.service.download

/**
 * 单条下载任务的速度采样器（字节/秒）。纯逻辑、无 Android 依赖，便于单元测试。
 *
 * 改造前速度是"全局已下载字节"一个采样器、由通知栏定时器每秒推一次；
 * 那个数字既包含所有并行任务、又包含回滚（全局计数器允许被减），
 * 所以列表里每一行显示的都是同一个"总速度"，单任务的速度根本看不出来。
 * 现在每条任务一个采样器，喂给它的字节数就是该任务的累计已下字节。
 *
 * 三条设计要点（与 Bili23-Downloader 的 `_calculate_speed` 同思路）：
 * 1. 分母用**实测时间差** [now] - 上次采样时间，不用名义上的 1 秒——上报间隔本来就不均匀；
 * 2. 两次计算至少隔 [minWindowMs]，避免进度上报密集时把浮点噪声放大成跳变；
 * 3. 结果做指数移动平均，压住单次抖动；字节数没涨（卡住或回滚）时自然往 0 收敛。
 */
internal class SpeedSampler(
    private val minWindowMs: Long = 500L,
    private val staleMs: Long = 2500L
) {
    private var prevBytes: Long = 0L
    private var prevTime: Long = 0L
    private var speedBps: Long = 0L
    private var speedTime: Long = 0L

    /** 丢弃历史，从 [now] 重新开始采（用于暂停后恢复、任务重排等） */
    @Synchronized
    fun reset(now: Long) {
        prevBytes = 0L
        prevTime = now
        speedBps = 0L
        speedTime = 0L
    }

    /**
     * 上报一次累计字节数 [bytes]（单调递增）与采样时刻 [now]（毫秒，建议用 elapsedRealtime）。
     *
     * @return 本次算出的速度；时间窗不足或首次采样时返回 null（保持上一次的值）
     */
    @Synchronized
    fun sample(bytes: Long, now: Long): Long? {
        if (prevTime == 0L) {
            prevBytes = bytes
            prevTime = now
            return null
        }
        val elapsedMs = now - prevTime
        if (elapsedMs < minWindowMs) return null

        val delta = bytes - prevBytes
        val instant = if (delta > 0L) delta * 1000.0 / elapsedMs else 0.0
        val next = if (speedTime == 0L) {
            instant
        } else {
            EMA_WEIGHT * instant + (1 - EMA_WEIGHT) * speedBps
        }
        speedBps = next.toLong().coerceAtLeast(0L)
        prevBytes = bytes
        prevTime = now
        speedTime = now
        return speedBps
    }

    /**
     * 当前速度。超过 [staleMs] 没有任何新字节上报（下载卡了、任务被暂停、已经下完）时返回 0，
     * 让 UI 显示"未知"而不是把最后那个速度一直挂着。
     */
    @Synchronized
    fun currentSpeed(now: Long): Long {
        if (speedTime == 0L) return 0L
        if (now - speedTime > staleMs) return 0L
        return speedBps
    }

    private companion object {
        /** 指数移动平均权重：0.4 ≈ 两三次采样就跟上真实变化 */
        const val EMA_WEIGHT = 0.4
    }
}
