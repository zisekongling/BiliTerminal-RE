package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.player.QualityAccessPolicy

/**
 * 设备分档的**纯判定逻辑**（只吃数字与字符串，不碰任何 Android 对象）。
 *
 * 背景：本项目从兄弟仓库 BiliTerminal-Revival 移植了 DeviceProfile 设备分档体系——
 * 按手表判定、SDK 版本、内存等级、CPU 核数把设备分成三档，各档用不同的清晰度、
 * 缓冲、弹幕、诊断参数。真正取值（`ActivityManager`、`PackageManager`、`DisplayMetrics`）
 * 放在 [DeviceProfile] 里；**判定规则全部收在这个纯函数对象里**，这样 JVM 单测能直接
 * 覆盖边界，而不需要真机或模拟器。
 *
 * 关键约定（用户 26.10.31 拍板，别自行改）：
 * - [useUnifiedDashPlayer] 对所有受支持设备（minSdk 19）都返回 true：ExoPlayer 的 AAR
 *   minSdk 是 16，统一时钟播放器在最低档设备上也能跑；只有用户在设置里**显式**关闭时才降级。
 * - [useDashDualPlayer] 恒为 false：旧「IjkMediaPlayer 播视频 + MediaPlayer 播音频」的
 *   双播放器方案整体作废，两套时钟正是音画不同步的根源。
 */
object DeviceProfilePolicy {

    // ===== 三档设备 =====
    // 枚举在 DeviceProfile.kt 里声明（保持"档位"这个类型的归属清晰），这里只做判定。

    // ===== SharedPreferences 覆盖值（0=自动，1=强制兼容，2=强制高配） =====
    const val OVERRIDE_AUTO = 0
    const val OVERRIDE_COMPAT = 1
    const val OVERRIDE_HIGH_END = 2

    /** 手表屏幕短边上限：短边 <= 480 且长边 <= 640 就按手表算（兄弟仓库同款阈值）。 */
    const val WATCH_MAX_SHORT_SIDE_PX = 480

    /** 手表屏幕长边上限。 */
    const val WATCH_MAX_LONG_SIDE_PX = 640

    /** 兼容档的弹幕分段上限（兄弟仓库 COMPAT_DANMAKU_LIMIT）。 */
    const val COMPAT_DANMAKU_LIMIT = 2000

    /**
     * 按硬件事实分档。**判定顺序不能乱**——前面的条件优先命中，改顺序会改变大量设备的归属。
     *
     * @param sdkInt    `Build.VERSION.SDK_INT`
     * @param memoryClassMb `ActivityManager.getMemoryClass()`；小于等于 0 表示"未知"
     * @param cores     `Runtime.availableProcessors()`
     * @param isWatchLike 屏幕尺寸/设备标识看起来像手表
     */
    fun classify(sdkInt: Int, memoryClassMb: Int, cores: Int, isWatchLike: Boolean): Tier = when {
        // 1. 手表一律兼容档：内存小、GPU 弱、系统还常年裁剪，先保稳。
        isWatchLike -> Tier.COMPAT
        // 2. Android 7.1 及以下：旧 ROM 的兼容性坑太多，一律兼容档。
        sdkInt <= 25 -> Tier.COMPAT
        // 3. 内存等级 <= 128MB：连正常解码都吃力。
        memoryClassMb in 1..128 -> Tier.COMPAT
        // 4. 内存 <= 192MB 且只有 1~2 核：能跑但别指望多任务。
        memoryClassMb in 1..192 && cores in 1..2 -> Tier.COMPAT
        // 5. Android 10+ 且内存 >= 512MB：高配。
        sdkInt >= 29 && memoryClassMb >= 512 -> Tier.HIGH_END
        // 6. Android 8+ 且内存 >= 256MB：也按高配。
        sdkInt >= 26 && memoryClassMb >= 256 -> Tier.HIGH_END
        // 7. Android 5+ 且内存未知或 >= 192MB：均衡档。
        sdkInt >= 21 && (memoryClassMb <= 0 || memoryClassMb >= 192) -> Tier.BALANCED
        // 8. 其余兜底兼容档。
        else -> Tier.COMPAT
    }

    /** 不需要手表判定的重载（纯 JVM 调用与单测更方便）。 */
    fun classify(sdkInt: Int, memoryClassMb: Int, cores: Int): Tier =
        classify(sdkInt, memoryClassMb, cores, false)

    /**
     * 只看设备标识与屏幕尺寸判断「像不像手表」。
     *
     * 标识里出现 watch/smartwatch/wear（含 `aqsh`、`dove_evb` 两个小众手表代号）直接算手表；
     * 否则按屏幕尺寸兜底：短边 <= 480 且长边 <= 640 也算——不少国产手表 ROM 机型名里
     * 根本没有 watch 字样。宽高未知（<= 0）时只看标识，不误判。
     */
    fun isWatchLike(
        model: String?,
        brand: String?,
        manufacturer: String?,
        product: String?,
        width: Int,
        height: Int
    ): Boolean {
        val identity = buildString {
            append(model ?: "")
            append(' ')
            append(brand ?: "")
            append(' ')
            append(manufacturer ?: "")
            append(' ')
            append(product ?: "")
        }.lowercase()
        if (identity.contains("watch") ||
            identity.contains("smartwatch") ||
            identity.contains("wear") ||
            identity.contains("aqsh") ||
            identity.contains("dove_evb")
        ) {
            return true
        }
        if (width <= 0 || height <= 0) return false
        return minOf(width, height) <= WATCH_MAX_SHORT_SIDE_PX &&
            maxOf(width, height) <= WATCH_MAX_LONG_SIDE_PX
    }

    /**
     * 少数老机器的旋转必须走软件播放器：Android 4.4 及以下 + 展讯平台的 A15 机型，
     * 硬件层旋转会花屏（兄弟仓库的原判定，照搬）。
     */
    fun requiresSoftwarePlayerRotation(model: String?, manufacturer: String?, sdkInt: Int): Boolean =
        sdkInt <= 19 &&
            "a15".equals(model?.trim(), ignoreCase = true) &&
            (manufacturer ?: "").lowercase().contains("spreadtrum")

    /**
     * SharedPreferences 里的覆盖值 → 档位；[OVERRIDE_AUTO] 或非法值返回 null（表示"跟随自动检测"）。
     */
    fun tierFromOverride(override: Int): Tier? = when (override) {
        OVERRIDE_COMPAT -> Tier.COMPAT
        OVERRIDE_HIGH_END -> Tier.HIGH_END
        else -> null
    }

    /**
     * 合并覆盖值与自动检测结果：用户显式覆盖优先，否则用检测值，再没有就退回兼容档。
     */
    fun effectiveTier(override: Int, detected: Tier?): Tier =
        tierFromOverride(override) ?: detected ?: Tier.COMPAT

    // ===== 各档参数（数值照搬兄弟仓库，别自行调） =====

    /** 手动切换清晰度时可选到的最高档：兼容/均衡 1080P，高配 4K。 */
    fun maxQuality(tier: Tier): Int = when (tier) {
        Tier.HIGH_END -> QualityAccessPolicy.QN_4K
        else -> QualityAccessPolicy.QN_1080P
    }

    /** 「自动」清晰度：兼容/均衡 720P，高配 1080P。 */
    fun autoQuality(tier: Tier): Int = when (tier) {
        Tier.HIGH_END -> QualityAccessPolicy.QN_1080P
        else -> QualityAccessPolicy.QN_720P
    }

    /** 是否自动请求 4K 档（只有高配才敢要）。 */
    fun requestTopTier4k(tier: Tier): Boolean = tier == Tier.HIGH_END

    /** 是否默认用软件解码（兼容档手表 GPU 解码容易花屏/绿屏）。 */
    fun defaultSoftwareCodec(tier: Tier): Boolean = tier == Tier.COMPAT

    /** 播放器 prepare 超时：兼容档 30s（慢），其余 20s。 */
    fun prepareTimeoutMs(tier: Tier): Long = if (tier == Tier.COMPAT) 30_000L else 20_000L

    /** 网络连接超时（秒）：兼容档 5s，其余 8s。 */
    fun connectTimeoutSec(tier: Tier): Int = if (tier == Tier.COMPAT) 5 else 8

    /** 播放器最大缓冲：兼容档 6MB，其余 15MB。 */
    fun maxBufferBytes(tier: Tier): Long =
        if (tier == Tier.COMPAT) 6L * 1024 * 1024 else 15L * 1024 * 1024

    /** 最大缓存时长：兼容档 6s，其余 12s。 */
    fun maxCachedDurationMs(tier: Tier): Long = if (tier == Tier.COMPAT) 6_000L else 12_000L

    /** 起播所需的最小缓冲帧数：兼容档 5，其余 8。 */
    fun minBufferedFrames(tier: Tier): Int = if (tier == Tier.COMPAT) 5 else 8

    /** 弹幕窗口半径（屏数）：兼容档 0（只当前屏），其余 1。 */
    fun danmakuWindowRadius(tier: Tier): Int = if (tier == Tier.COMPAT) 0 else 1

    /** 每段弹幕上限：兼容档 2000（[COMPAT_DANMAKU_LIMIT]），其余 0 表示不限。 */
    fun maxDanmakuPerSegment(tier: Tier): Int =
        if (tier == Tier.COMPAT) COMPAT_DANMAKU_LIMIT else 0

    /** 诊断日志批量上限：兼容档 200，其余 0 表示不限。 */
    fun diagnosticBatchLimit(tier: Tier): Int = if (tier == Tier.COMPAT) 200 else 0

    /**
     * 是否使用 ExoPlayer 统一时钟播放器。
     *
     * **全设备为 true**（用户拍板）：唯一返回 false 的情况是用户在设置里显式打开降级开关
     * （[com.RobinNotBad.BiliClient.util.DeviceProfile.KEY_UNIFIED_PLAYER_DISABLED]）。
     * 注意这里只认"降级开关"，不认同为把设备档位覆盖成兼容档——
     * 「手表 → COMPAT 档」只影响清晰度与缓冲参数，不该把单时钟播放器也一并关掉。
     */
    fun useUnifiedDashPlayer(unifiedPlayerDisabled: Boolean): Boolean = !unifiedPlayerDisabled

    /**
     * 是否使用旧的双播放器方案（IjkMediaPlayer 视频 + MediaPlayer 音频）。
     *
     * **恒 false**：该方案已整体作废，两套时钟必然音画不同步。
     */
    fun useDashDualPlayer(): Boolean = false
}
