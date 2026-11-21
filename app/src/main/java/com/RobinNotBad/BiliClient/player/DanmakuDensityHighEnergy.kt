package com.RobinNotBad.BiliClient.player

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * 高能进度条的**本地兜底曲线**（G07，26.11.13）。
 *
 * ## 为什么要它
 *
 * 高能曲线的官方数据来自 `bvc.bilivideo.com/pbp/data`（`api/PlayerApi.getHighEnergyData`）。
 * 这个接口对不少视频直接返回空（新稿、番剧、部分地区、风控），旧实现拿到 null 就只打一行
 * 日志——**进度条上那条高能曲线整片消失**，用户看到的是「我明明开了高能进度条，怎么什么都没有」。
 *
 * 兜底思路与兄弟分支一致：**用已经加载进内存的弹幕自己算密度**。弹幕在哪一段密集，
 * 基本就等价于「那一段是高能片段」，而且不需要任何额外请求。
 *
 * ## 口径
 *
 * - 固定 [STEP_SEC] = 10 秒一桶，与官方 `step_sec` 常见取值（10）对齐，进度条不必区分来源；
 * - 时长未知（[build] 的 `durationSec <= 0`）、弹幕太少（< [MIN_DANMAKU] 条）一律返回 null：
 *   样本不足时画出来的曲线是噪声，比没有更误导；
 * - 归一化用 **95 分位**而不是最大值：一条爆款弹幕（比如满屏「哈哈」集中在某一秒）
 *   会把最大值拉爆，其余片段全被压成一条平线；取分位后极端峰值只被截到 1.0；
 * - 再套一次 `sqrt`：弹幕密度的长尾很重，线性归一化会让「中等热闹」的段落看起来几乎没有起伏，
 *   开方压缩头部、抬高低密度段，曲线更像官方那张图。
 *
 * 纯函数、无 Android 依赖，可直接 JVM 单测（`DanmakuDensityHighEnergyTest`）。
 */
object DanmakuDensityHighEnergy {

    /** 采样间隔（秒），与官方接口的常见值一致。 */
    const val STEP_SEC = 10

    /** 至少要有这么多条弹幕才画兜底曲线。 */
    const val MIN_DANMAKU = 30

    /** 视频至少要有这么长（秒）才画兜底曲线。 */
    const val MIN_DURATION_SEC = 60

    /** 归一化用的分位点（0..1）。 */
    private const val PERCENTILE = 0.95

    /**
     * 用弹幕时间点算一条密度曲线。
     *
     * @param timesMs 已加载弹幕的时间点（毫秒，DFM 的 `danmaku.time`），乱序、重复都可以
     * @param durationSec 视频总时长（秒）；未知传 0（直接返回 null）
     * @param stepSec 采样间隔（秒），非法值回退 [STEP_SEC]
     * @return 每条 0..1 的曲线，长度 = `ceil(durationSec / stepSec)`；样本不足返回 null
     */
    fun build(timesMs: List<Long>, durationSec: Int, stepSec: Int = STEP_SEC): FloatArray? {
        if (durationSec < MIN_DURATION_SEC) return null
        if (timesMs.size < MIN_DANMAKU) return null
        val step = if (stepSec > 0) stepSec else STEP_SEC
        val buckets = ceil(durationSec.toDouble() / step).toInt()
        if (buckets <= 0) return null

        val counts = IntArray(buckets)
        var used = 0
        val durationMs = durationSec * 1000L
        for (time in timesMs) {
            if (time < 0L || time >= durationMs) continue
            val index = (time / 1000L / step).toInt()
            if (index !in counts.indices) continue
            counts[index]++
            used++
        }
        if (used < MIN_DANMAKU) return null

        // 95 分位作为归一化基准（只看非零桶：大片零桶会把分位压到 0）
        val nonZero = counts.filter { it > 0 }.sorted()
        if (nonZero.isEmpty()) return null
        val pivotIndex = ((nonZero.size - 1) * PERCENTILE).toInt().coerceIn(0, nonZero.size - 1)
        val baseline = nonZero[pivotIndex].toFloat()
        if (baseline <= 0f) return null

        val out = FloatArray(buckets)
        for (i in 0 until buckets) {
            val raw = counts[i] / baseline
            out[i] = sqrt(raw).coerceIn(0f, 1f)
        }
        return out
    }
}
