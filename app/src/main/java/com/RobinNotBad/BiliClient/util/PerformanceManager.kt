package com.RobinNotBad.BiliClient.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.RobinNotBad.BiliClient.BiliTerminal
import java.io.RandomAccessFile
import java.util.regex.Pattern

/**
 * 性能管理器 - 用于设备性能检测、高性能模式管理与智能优化策略
 *
 * 功能：
 * 1. 设备性能等级检测（高/中/低）
 * 2. 高性能模式开关管理
 * 3. 根据设备性能自动调整运行时参数
 */
object PerformanceManager {

    // SharedPreferences 键
    const val KEY_HIGH_PERFORMANCE_MODE = "high_performance_mode"
    const val KEY_DEVICE_PERFORMANCE_LEVEL = "device_performance_level"
    const val KEY_PERFORMANCE_AUTO_DETECTED = "performance_auto_detected"

    // 性能等级
    const val PERF_LEVEL_HIGH = 2
    const val PERF_LEVEL_MEDIUM = 1
    const val PERF_LEVEL_LOW = 0

    @Volatile
    private var currentPerfLevel: Int = PERF_LEVEL_MEDIUM

    /**
     * 用户显式开启的高性能模式（只代表**用户的意图**，不再由自动判定写入）。
     *
     * 26.11.08 修正：旧实现探测到高档设备时会把 `high_performance_mode` 直接写成 true，
     * 于是用户从没碰过开关却看到它是「开」的；在高档机上再关一次又会被
     * `currentPerfLevel == PERF_LEVEL_HIGH` 兜回开，等于那个开关根本关不掉。
     * 现在拆成两个来源：[userSetHighPerf] 记录「用户设过没有」，[autoDetectedHighPerf]
     * 记录「设备自动判定的默认值」，用户一旦设过就完全听用户的。
     */
    @Volatile
    private var highPerformanceMode: Boolean = false

    /** 用户是否在设置里设过「高性能模式」（存档键存在即算设过）。 */
    @Volatile
    private var userSetHighPerf: Boolean = false

    /** 设备自动判定为高档（只用于「用户没设过」时的默认值）。 */
    @Volatile
    private var autoDetectedHighPerf: Boolean = false

    @Volatile
    private var initialized: Boolean = false

    /**
     * 获取设备硬件性能总分（RAM、CPU核心数、CPU频率）
     */
    fun getHardwareScore(): Int {
        var score = 0

        // 1. RAM评分 (0-40)
        val totalRamMB = getTotalRamMB()
        score += when {
            totalRamMB >= 8192 -> 40
            totalRamMB >= 6144 -> 35
            totalRamMB >= 4096 -> 28
            totalRamMB >= 3072 -> 22
            totalRamMB >= 2048 -> 15
            totalRamMB >= 1536 -> 10
            totalRamMB >= 1024 -> 5
            else -> 2
        }

        // 2. CPU核心数评分 (0-25)
        val cpuCores = Runtime.getRuntime().availableProcessors()
        score += when {
            cpuCores >= 8 -> 25
            cpuCores >= 6 -> 20
            cpuCores >= 4 -> 15
            cpuCores >= 2 -> 8
            else -> 3
        }

        // 3. CPU最大频率评分 (0-25)
        val maxFreqMHz = getCpuMaxFreqMHz()
        score += when {
            maxFreqMHz >= 2800 -> 25
            maxFreqMHz >= 2400 -> 22
            maxFreqMHz >= 2000 -> 18
            maxFreqMHz >= 1600 -> 14
            maxFreqMHz >= 1200 -> 10
            maxFreqMHz >= 800 -> 5
            else -> 2
        }

        // 4. Android版本加分 (0-10)
        score += when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> 10
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> 8
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> 6
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> 5
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> 4
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> 3
            else -> 1
        }

        return score
    }

    /**
     * 根据硬件分数换算性能等级（纯函数，便于 JVM 单测）
     */
    @JvmStatic
    fun levelFromScore(score: Int): Int = when {
        score >= 65 -> PERF_LEVEL_HIGH
        score >= 35 -> PERF_LEVEL_MEDIUM
        else -> PERF_LEVEL_LOW
    }

    /**
     * 检测设备性能等级。
     *
     * 注意：内部会读 sysfs / `/proc/cpuinfo`（见 [getHardwareScore]），**不要在冷启动主线程调用**，
     * 首次检测请走 [init] 里的后台路径。
     */
    fun getPerformanceLevel(): Int = levelFromScore(getHardwareScore())

    /**
     * 初始化性能管理器
     *
     * 26.10.04 批次 3（B2）：首次运行（没有档位缓存）**不再在冷启动主线程同步检测**。
     * [getHardwareScore] 要读 sysfs、失败时还会整文件扫 `/proc/cpuinfo`，在低端手表上足够卡出一帧白屏。
     * 现在的顺序是：先按中档立即生效 → 把检测丢到 [CenterThreadPool] → 结果出来后写缓存并重跑 [applyPerformanceSettings]。
     */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true

            // 检查用户是否已手动设置高性能模式
            userSetHighPerf = SharedPreferencesUtil.sharedPreferences.contains(KEY_HIGH_PERFORMANCE_MODE)
            if (userSetHighPerf) {
                highPerformanceMode = SharedPreferencesUtil.getBoolean(KEY_HIGH_PERFORMANCE_MODE, false)
            }
            // 自动判定的结果单独记一个键（见 KEY_PERFORMANCE_AUTO_DETECTED 的注释）
            autoDetectedHighPerf = SharedPreferencesUtil.getBoolean(KEY_PERFORMANCE_AUTO_DETECTED, false)

            if (SharedPreferencesUtil.sharedPreferences.contains(KEY_DEVICE_PERFORMANCE_LEVEL)) {
                // 有缓存：直接读，零硬件探测
                currentPerfLevel = SharedPreferencesUtil.getInt(KEY_DEVICE_PERFORMANCE_LEVEL, PERF_LEVEL_MEDIUM)
                applyPerformanceSettings()
                Logu.i("PerformanceManager", "初始化完成: perfLevel=$currentPerfLevel, highPerfMode=$highPerformanceMode")
            } else {
                // 首次运行：先按中档生效，检测放后台
                currentPerfLevel = PERF_LEVEL_MEDIUM
                applyPerformanceSettings()
                CenterThreadPool.run { detectAndApplyPerformanceLevel() }
            }
        }
    }

    /**
     * 后台检测设备性能等级（仅首次运行、没有档位缓存时跑一次）
     *
     * 检测完必须重跑 [applyPerformanceSettings]：这时才可能从"中档"切到低档/高档，
     * 而列表与图片缓存参数是每次调用现取的，所以后续页面会自然跟上。
     */
    private fun detectAndApplyPerformanceLevel() {
        try {
            val score = getHardwareScore()
            val level = levelFromScore(score)
            currentPerfLevel = level
            SharedPreferencesUtil.putInt(KEY_DEVICE_PERFORMANCE_LEVEL, level)

            // 自动判定的结果只写自动判定键，**不碰用户开关**：
            // 旧实现这里会把 high_performance_mode 报成 true，用户看到开关是「开」却不是自己开的，
            // 而高档机上那个开关又被 isEffectiveHighPerf 兜回开，等于关不掉。
            autoDetectedHighPerf = level == PERF_LEVEL_HIGH
            SharedPreferencesUtil.putBoolean(KEY_PERFORMANCE_AUTO_DETECTED, autoDetectedHighPerf)

            applyPerformanceSettings()
            Logu.i("PerformanceManager", "后台检测完成: perfLevel=$level, score=$score, autoHighPerf=$autoDetectedHighPerf, userSet=$userSetHighPerf")
        } catch (e: Exception) {
            // 检测失败保持中档即可，不影响使用
            Logu.e("PerformanceManager", "性能检测失败: ${e.message}")
        }
    }

    /**
     * 设置高性能模式（用户在设置页拨开关）。
     *
     * 一旦调用过，[userSetHighPerf] 即为 true，此后完全听用户的：
     * 高档设备上关掉它，[isEffectiveHighPerf] 就真的返回 false（26.11.08 修正）。
     */
    fun setHighPerformanceMode(enabled: Boolean) {
        highPerformanceMode = enabled
        userSetHighPerf = true
        SharedPreferencesUtil.putBoolean(KEY_HIGH_PERFORMANCE_MODE, enabled)
        applyPerformanceSettings()
        Logu.i("PerformanceManager", "高性能模式: $enabled")
    }

    /**
     * 获取当前设备性能等级
     */
    fun getCurrentPerfLevel(): Int = currentPerfLevel

    /**
     * 是否低端档位（纯函数，便于 JVM 单测）
     *
     * 语义：低端设备 **且** 用户没手动开高性能模式——低端机手动开高性能后不再按低端处理，
     * 与 [isEffectiveHighPerf] 保持一致。
     */
    @JvmStatic
    fun isLowPerfLevel(level: Int, highPerformanceMode: Boolean): Boolean =
        level == PERF_LEVEL_LOW && !highPerformanceMode

    /**
     * 是否低性能设备
     */
    fun isLowPerfDevice(): Boolean = isLowPerfLevel(currentPerfLevel, highPerformanceMode)

    /**
     * 「高性能模式实际生效没有」（纯函数，便于 JVM 单测）
     *
     * 26.11.08 修正的规则：**用户设过就完全听用户的**——高档设备上把开关关掉就是真的关掉；
     * 只有「用户从没设过」时才用设备自动判定的默认值兜着。
     * 旧实现是 `highPerformanceMode || level == HIGH`，导致高档机上那个开关怎么关都无效。
     *
     * @param userSet     用户在设置页设过没有（存档键是否存在）
     * @param userEnabled 用户设成的值（[userSet] 为 false 时忽略）
     * @param autoDetected 设备自动判定是否为高档
     */
    @JvmStatic
    fun effectiveHighPerf(userSet: Boolean, userEnabled: Boolean, autoDetected: Boolean): Boolean =
        if (userSet) userEnabled else autoDetected

    /**
     * 是否高性能设备或启用了高性能模式
     */
    fun isEffectiveHighPerf(): Boolean =
        effectiveHighPerf(userSetHighPerf, highPerformanceMode, autoDetectedHighPerf)

    /**
     * 用户在设置页有没有设过「高性能模式」。
     * 设置页用它决定开关该显示什么：没设过时显示自动判定的结果，设过之后显示用户自己的值。
     */
    fun isHighPerfUserSet(): Boolean = userSetHighPerf

    /**
     * 应用性能设置 - 根据性能等级调整运行时参数
     */
    private fun applyPerformanceSettings() {
        // 根据最终有效性能等级调整
        if (isLowPerfDevice()) {
            applyLowPerfSettings()
        } else if (isEffectiveHighPerf()) {
            applyHighPerfSettings()
        } else {
            applyMediumPerfSettings()
        }
    }

    private fun applyLowPerfSettings() {
        // 低性能设备优化策略（手表等）
        Logu.i("PerformanceManager", "应用低性能优化策略")
    }

    private fun applyMediumPerfSettings() {
        Logu.i("PerformanceManager", "应用中性能策略")
    }

    private fun applyHighPerfSettings() {
        Logu.i("PerformanceManager", "应用高性能策略")
    }

    // ===== 供外部使用的运行时参数获取 =====

    /** Glide内存缓存大小（MB） */
    fun getGlideMemoryCacheSizeMB(): Int = when {
        isLowPerfDevice() -> 16
        isEffectiveHighPerf() -> 64
        else -> 32
    }

    /** Glide磁盘缓存大小（MB） */
    fun getGlideDiskCacheSizeMB(): Long = when {
        isLowPerfDevice() -> 64L
        isEffectiveHighPerf() -> 256L
        else -> 128L
    }

    /** RecyclerView预加载数量 */
    fun getRecyclerViewPrefetchCount(): Int = when {
        isLowPerfDevice() -> 2
        isEffectiveHighPerf() -> 6
        else -> 4
    }

    /** RecyclerView ViewHolder缓存大小 */
    fun getRecyclerViewCacheSize(): Int = when {
        isLowPerfDevice() -> 4
        isEffectiveHighPerf() -> 20
        else -> 10
    }

    // ----- 图片请求档位（26.10.04 批次 3 / B1+B3 接线）-----
    //
    // 现状：GlideUtil.url()/url_hq() 之前写死 512w/60q 与 1024w/80q，档位形同不存在。
    // 接入时发现台账"中/高档一律 HIGH(80q/1024w)"对列表图是反优化：手表屏宽 450px 左右，
    // 512w 已够，1080p 手机列表也只要 512w；再往上只白烧 4 倍像素与内存。
    // 所以列表图改成"低档 320w/50q、其余 512w/60q"，只有 url_hq()（详情大图）保留 1024w/80q。
    // 换算抽成带参数的纯函数（除 0 外都要能被 JVM 单测覆盖），下面的 getter 只负责喂当前档位。
    //
    // 注意：低端档位同时并入 `highPerformanceMode`（用户在设置里手动开的高性能模式），
    // 与 [isLowPerfDevice] 保持一致。

    /** 列表/卡片图质量（低端 50，其余 60） */
    @JvmStatic
    fun listImageQuality(level: Int, highPerformanceMode: Boolean): Int =
        if (isLowPerfLevel(level, highPerformanceMode)) 50 else 60

    /** 列表/卡片图最大宽度（低端 320，其余 512） */
    @JvmStatic
    fun listImageMaxWidth(level: Int, highPerformanceMode: Boolean): Int =
        if (isLowPerfLevel(level, highPerformanceMode)) 320 else 512

    /** 大图质量（低端 60，其余 80） */
    @JvmStatic
    fun hqImageQuality(level: Int, highPerformanceMode: Boolean): Int =
        if (isLowPerfLevel(level, highPerformanceMode)) 60 else 80

    /** 大图最大宽度（低端 512，其余 1024） */
    @JvmStatic
    fun hqImageMaxWidth(level: Int, highPerformanceMode: Boolean): Int =
        if (isLowPerfLevel(level, highPerformanceMode)) 512 else 1024

    /**
     * 按屏幕长边封顶请求宽度（26.10.27 性能审查，纯函数）。
     *
     * 档位里的 1024w 是给 1080p 手机定的；466px 的手表上封面再大也就占满屏宽，多出来的像素只是白下、白解码。
     * 屏幕长边向上取到固定梯度（320 / 480 / 640 / 720 / 1024），让同类设备请求同一个 URL，CDN 与 Glide 磁盘缓存都能复用。
     * 用长边而不是短边：横屏时卡片可能更宽，不能因此变糊。屏幕尺寸未知（≤0）时不封顶。
     */
    @JvmStatic
    fun capWidthToScreen(tierWidth: Int, screenLongSidePx: Int): Int {
        if (screenLongSidePx <= 0) return tierWidth
        val bucket = SCREEN_WIDTH_BUCKETS.firstOrNull { it >= screenLongSidePx } ?: return tierWidth
        return minOf(tierWidth, bucket)
    }

    private val SCREEN_WIDTH_BUCKETS = intArrayOf(320, 480, 640, 720, 1024)

    /** 屏幕长边像素；拿不到 Context 时返回 0（= 不封顶）。 */
    private fun screenLongSidePx(): Int {
        val metrics = BiliTerminal.context?.resources?.displayMetrics ?: return 0
        return maxOf(metrics.widthPixels, metrics.heightPixels)
    }

    /** 列表/卡片图质量（供 GlideUtil.url 调用） */
    @JvmStatic
    fun getImageQuality(): Int = listImageQuality(currentPerfLevel, highPerformanceMode)

    /** 列表/卡片图最大宽度（供 GlideUtil.url 调用），已按屏幕封顶 */
    @JvmStatic
    fun getImageMaxWidth(): Int =
        capWidthToScreen(listImageMaxWidth(currentPerfLevel, highPerformanceMode), screenLongSidePx())

    /** 大图质量（供 GlideUtil.url_hq 调用） */
    @JvmStatic
    fun getHqImageQuality(): Int = hqImageQuality(currentPerfLevel, highPerformanceMode)

    /** 大图最大宽度（供 GlideUtil.url_hq 调用），已按屏幕封顶 */
    @JvmStatic
    fun getHqImageMaxWidth(): Int =
        capWidthToScreen(hqImageMaxWidth(currentPerfLevel, highPerformanceMode), screenLongSidePx())

    /** 可缩放大图（图片查看器）的最大宽度：**不**按屏幕封顶，放大 6 倍时还要看得清 */
    @JvmStatic
    fun getZoomImageMaxWidth(): Int = hqImageMaxWidth(currentPerfLevel, highPerformanceMode)

    /** 是否启用硬件位图解码 */
    fun isHardwareBitmapEnabled(): Boolean = isEffectiveHighPerf()

    /**
     * 列表分页大小
     *
     * 26.10.04 批次 3（B1+B5）：只接到"纯追加列表"的三处
     * （`api/RecommendApi.java` 的热门 / 入站必刷、`api/SeriesApi.java` 的系列视频列表）。
     * 其余接口的 ps 是各接口自己的选择（如收藏夹一次拉 100 条、私信游标 35 条），**故意不接**。
     */
    @JvmStatic
    fun getPageSize(): Int = if (isLowPerfDevice()) 10 else 20

    // ===== 硬件信息获取 =====

    private fun getTotalRamMB(): Long {
        return try {
            val context = BiliTerminal.context
            if (context != null) {
                val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                actManager.getMemoryInfo(memInfo)
                memInfo.totalMem / (1024 * 1024)
            } else 1024L
        } catch (e: Exception) {
            1024L
        }
    }

    private fun getCpuMaxFreqMHz(): Int {
        return try {
            var maxFreq = 0
            val reader = RandomAccessFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq", "r")
            val line = reader.readLine()
            reader.close()
            if (line != null) {
                maxFreq = (line.toInt() / 1000)
            }
            maxFreq
        } catch (e: Exception) {
            // 备用方案：通过/proc/cpuinfo获取
            try {
                val reader = RandomAccessFile("/proc/cpuinfo", "r")
                var maxBogoMips = 0f
                val pattern = Pattern.compile("BogoMIPS\\s*:\\s*(\\d+\\.?\\d*)")
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val matcher = pattern.matcher(line!!)
                    if (matcher.find()) {
                        val bogoMips = matcher.group(1)?.toFloatOrNull() ?: 0f
                        if (bogoMips > maxBogoMips) maxBogoMips = bogoMips
                    }
                }
                reader.close()
                if (maxBogoMips > 0) (maxBogoMips / 2).toInt() else 1000
            } catch (e2: Exception) {
                1000
            }
        }
    }
}