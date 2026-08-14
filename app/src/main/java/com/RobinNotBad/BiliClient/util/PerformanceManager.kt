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

    @Volatile
    private var highPerformanceMode: Boolean = false

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
     * 根据硬件分数获取性能等级
     */
    fun getPerformanceLevel(): Int {
        val score = getHardwareScore()
        return when {
            score >= 65 -> PERF_LEVEL_HIGH
            score >= 35 -> PERF_LEVEL_MEDIUM
            else -> PERF_LEVEL_LOW
        }
    }

    /**
     * 初始化性能管理器
     */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true

            // 检查用户是否已手动设置高性能模式
            if (SharedPreferencesUtil.sharedPreferences.contains(KEY_HIGH_PERFORMANCE_MODE)) {
                highPerformanceMode = SharedPreferencesUtil.getBoolean(KEY_HIGH_PERFORMANCE_MODE, false)
            }

            // 检测设备性能等级
            if (SharedPreferencesUtil.sharedPreferences.contains(KEY_DEVICE_PERFORMANCE_LEVEL)) {
                currentPerfLevel = SharedPreferencesUtil.getInt(KEY_DEVICE_PERFORMANCE_LEVEL, PERF_LEVEL_MEDIUM)
            } else {
                currentPerfLevel = getPerformanceLevel()
                SharedPreferencesUtil.putInt(KEY_DEVICE_PERFORMANCE_LEVEL, currentPerfLevel)
            }

            // 高性能手机自动启用高性能模式
            if (currentPerfLevel == PERF_LEVEL_HIGH && !SharedPreferencesUtil.sharedPreferences.contains(KEY_HIGH_PERFORMANCE_MODE)) {
                highPerformanceMode = true
                SharedPreferencesUtil.putBoolean(KEY_HIGH_PERFORMANCE_MODE, true)
            }

            applyPerformanceSettings()
            Logu.i("PerformanceManager", "初始化完成: perfLevel=$currentPerfLevel, highPerfMode=$highPerformanceMode, score=${getHardwareScore()}")
        }
    }

    /**
     * 设置高性能模式
     */
    fun setHighPerformanceMode(enabled: Boolean) {
        highPerformanceMode = enabled
        SharedPreferencesUtil.putBoolean(KEY_HIGH_PERFORMANCE_MODE, enabled)
        applyPerformanceSettings()
        Logu.i("PerformanceManager", "高性能模式: $enabled")
    }

    /**
     * 获取高性能模式状态
     */
    fun isHighPerformanceMode(): Boolean = highPerformanceMode

    /**
     * 获取当前设备性能等级
     */
    fun getCurrentPerfLevel(): Int = currentPerfLevel

    /**
     * 是否低性能设备
     */
    fun isLowPerfDevice(): Boolean = currentPerfLevel == PERF_LEVEL_LOW && !highPerformanceMode

    /**
     * 是否高性能设备或启用了高性能模式
     */
    fun isEffectiveHighPerf(): Boolean = highPerformanceMode || currentPerfLevel == PERF_LEVEL_HIGH

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

    /** 图片加载质量 */
    fun getImageQuality(): Int = when {
        isLowPerfDevice() -> GlideUtil.QUALITY_LOW
        isEffectiveHighPerf() -> GlideUtil.QUALITY_HIGH
        else -> GlideUtil.QUALITY_HIGH
    }

    /** 图片最大宽度 */
    fun getImageMaxWidth(): Int = when {
        isLowPerfDevice() -> GlideUtil.MAX_W_LOW
        isEffectiveHighPerf() -> GlideUtil.MAX_W_HIGH
        else -> GlideUtil.MAX_W_HIGH
    }

    /** OkHttp连接池大小 */
    fun getOkHttpConnectionPoolSize(): Int = when {
        isLowPerfDevice() -> 2
        else -> 5
    }

    /** OkHttp连接保活时间（分钟） */
    fun getOkHttpKeepAliveMinutes(): Int = when {
        isLowPerfDevice() -> 3
        else -> 5
    }

    /** 是否启用图片过渡动画 */
    fun isImageTransitionEnabled(): Boolean = isEffectiveHighPerf()

    /** 是否启用硬件位图解码 */
    fun isHardwareBitmapEnabled(): Boolean = isEffectiveHighPerf()

    /** 是否启用视频预加载 */
    fun isVideoPreloadEnabled(): Boolean = isEffectiveHighPerf()

    /** 列表分页大小 */
    fun getPageSize(): Int = when {
        isLowPerfDevice() -> 10
        else -> 20
    }

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