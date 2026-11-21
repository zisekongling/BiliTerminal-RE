package com.RobinNotBad.BiliClient.service.download

/**
 * 下载进度与分片划分的纯计算。
 *
 * 这些表达式原本内联在 DownloadService 的下载循环里（进度换算 7 处、伪进度 2 处、
 * 分片划分 2 处）。抽成纯函数后可以脱离 Android 跑 JVM 单测，
 * 同时表达式保持与原实现逐字符一致，浮点结果不会因为改写顺序而变化。
 *
 * 进度的主算法已经搬到 [DownloadSectionBytes]（按真实字节算，与 Bili23-Downloader 同口径）；
 * 本文件里的 DASH_* 阶段常量现在只作**兜底**：流大小探测失败、拿不到字节预算时才退回
 * "按阶段权重摊派"的老算法。
 *
 * 无 Android 依赖。
 */
internal object DownloadProgressMath {

    /** 动态分片的目标片大小：约每 2MB 一片 */
    private const val SEGMENT_TARGET_BYTES = 2 * 1024 * 1024L

    /** 未知总大小时伪进度的步长 */
    private const val PSEUDO_STEP = 0.02f

    /** 未知总大小时伪进度的封顶值 */
    private const val PSEUDO_MAX = 0.9f

    // ---------- DASH 下载各阶段在整条进度条上的边界（仅探测失败时的兜底） ----------
    // 视频 0.12 → 0.52、音频 0.52 → 0.88；音频下完即视为数据部分完成，进度直接顶到 100%
    // （合并只换容器、不产生新字节，不再单独占一段区间）。
    // 这组权重是"猜"的：真实比例取决于视频/音频码率（4K 视频可能比音频大 30 倍），
    // 所以正常路径改由 [DownloadSectionBytes] 按探测到的真实字节数算；
    // 只有流大小探测失败（拿不到字节预算）时才退回这套固定摊派。

    /** DASH 视频流阶段的起点 */
    const val DASH_ATTACH_END = 0.12f

    /** DASH 视频流阶段的终点 */
    const val DASH_VIDEO_END = 0.52f

    /** DASH 音频流阶段的终点（也是数据部分的终点） */
    const val DASH_AUDIO_END = 0.88f

    /**
     * 分片数：约每 2MB 一片，且不超过用户配置的分片数上限，最少 1 片。
     *
     * 恒 ≥1 是硬要求——[segmentLen] 会拿它做除数，返回 0 会导致除零。
     * 用户配置被改成 0 或负数时同样兜底为 1 片。
     */
    @JvmStatic
    fun segmentCount(totalSize: Long, maxSplit: Int): Int {
        var segments = Math.min(maxSplit, Math.max(1, (totalSize / SEGMENT_TARGET_BYTES).toInt()))
        if (segments < 1) segments = 1
        return segments
    }

    /** 每片的字节数（整除，余数留给最后一节） */
    @JvmStatic
    fun segmentLen(totalSize: Long, segments: Int): Long = totalSize / segments

    /**
     * 第 [i] 片在文件中的闭区间 `[start, end]`。
     * 最后一节吃掉整除余数（`end = totalSize - 1`），保证所有分片恰好覆盖整个文件。
     */
    @JvmStatic
    fun segmentRange(i: Int, segments: Int, totalSize: Long, segmentLen: Long): LongRange {
        val start = i * segmentLen
        val end = if (i == segments - 1) totalSize - 1 else start + segmentLen - 1
        return start..end
    }

    /**
     * 已下载 [downloaded] 字节、总大小 [total] 时，映射到 `[baseProgress, endProgress]`
     * 区间内的进度值。
     *
     * 调用方需保证 `total > 0`（总大小未知的路径走 [progressForPseudo]）：
     * `total == 0` 时按浮点语义得到 Infinity/NaN，与原内联表达式一致。
     */
    @JvmStatic
    fun progressForBytes(downloaded: Long, total: Long, baseProgress: Float, endProgress: Float): Float {
        return baseProgress + (1.0f * downloaded / total) * (endProgress - baseProgress)
    }

    /** 推进一次伪进度并封顶（总大小未知时使用，避免进度条瞬间满或卡死） */
    @JvmStatic
    fun pseudoProgressStep(current: Float): Float = Math.min(current + PSEUDO_STEP, PSEUDO_MAX)

    /** 把伪进度映射到 `[baseProgress, endProgress]` 区间内的进度值 */
    @JvmStatic
    fun progressForPseudo(pseudoProgress: Float, baseProgress: Float, endProgress: Float): Float {
        return baseProgress + (endProgress - baseProgress) * pseudoProgress
    }

    /**
     * 字节比率：`downloaded / total`，夹在 `0..1`。
     *
     * 用 Double 中转再转 Float，1GB 级数字除以总长也不会因为 Float 只有 24 位有效位而抖动。
     * `total <= 0`（大小未知）时返回 0，不返回 NaN。
     */
    @JvmStatic
    fun byteRatio(downloaded: Long, total: Long): Float {
        if (total <= 0) return 0f
        val ratio = downloaded.coerceAtLeast(0).toDouble() / total.toDouble()
        return ratio.coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * 单调护栏：进度条只许前进，不许后退。
     *
     * 一条任务的字节预算定下来之后，字节数只会越来越多；但下面几种情况会让算出来的进度变小——
     * 分片失败回滚已计字节、重试从头再下一段、以及"预算已知但实际文件比预算大"时 total 变化。
     * 后退的进度条比慢一点的进度条更让人以为出错了，所以这里统一夹住。
     */
    @JvmStatic
    fun monotoneProgress(previous: Float, next: Float): Float {
        return maxOf(previous, next).coerceIn(0f, 1f)
    }
}
