package com.RobinNotBad.BiliClient.util

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * 设备分档：三档（兼容 / 均衡 / 高配）。
 *
 * 声明在 [DeviceProfile] 同一个文件里，纯 Kotlin 枚举、不碰 Android，
 * 所以单测里直接引用 `Tier.COMPAT` 不会连带加载 Android 侧代码。
 */
enum class Tier {
    /** 兼容档：手表、老系统、小内存设备。 */
    COMPAT,

    /** 均衡档：主流中端机。 */
    BALANCED,

    /** 高配档：新系统 + 大内存。 */
    HIGH_END
}

/**
 * 设备分档（移植自兄弟仓库 BiliTerminal-Revival 的 `util/DeviceProfile.java`）。
 *
 * 职责边界：
 * - 本对象只做 **Android 侧取值 + 缓存**（内存等级、屏幕尺寸、系统特性、SharedPreferences）；
 * - 所有判定规则与各档参数在纯函数对象 [DeviceProfilePolicy] 里，便于 JVM 单测；
 * - 首次调用 [init] 时检测一次并缓存，之后读缓存，避免播放器每次起播都查一遍系统 API。
 *
 * 用法：`BiliTerminal.onCreate` 里调一次 [init]；业务侧直接 `DeviceProfile.autoQuality()` 之类。
 * 若 [init] 还没跑（极端时序，如崩溃页进程），[get] 会退化为「无 Context 检测」并照样给结论。
 */
object DeviceProfile {

    /** SharedPreferences 键：设备档位覆盖（0=自动，1=强制兼容，2=强制高配）。 */
    const val KEY_DEVICE_PROFILE_OVERRIDE = "DEVICE_PROFILE_OVERRIDE"

    /**
     * SharedPreferences 键：**显式**关闭统一时钟播放器的降级开关（默认 false）。
     *
     * 只有它能让 [useUnifiedDashPlayer] 返回 false；设备被判定成兼容档（含手表）不影响播放器选择，
     * 因为 ExoPlayer 的 AAR minSdk 是 16，本仓库 minSdk 19 全设备可用。
     */
    const val KEY_UNIFIED_PLAYER_DISABLED = "device_profile_unified_player_disabled"

    /** 已检测出的档位；`null` 表示还没检测过。 */
    @Volatile
    private var cachedTier: Tier? = null

    /** 已检测出的「像不像手表」；`null` 表示还没检测过。 */
    @Volatile
    private var cachedWatchLike: Boolean? = null

    /** [init] 是否已执行过（幂等标记）。 */
    @Volatile
    private var initialized = false

    /** 保护检测与缓存写入的锁：可能被多个线程同时首次调用。 */
    private val detectLock = Any()

    /**
     * 初始化：检测一次设备并按结果缓存。**幂等**，重复调用直接返回。
     *
     * 这里不做后台线程：`ActivityManager.getMemoryClass()` / `DisplayMetrics` 都是廉价调用，
     * 不像 [PerformanceManager] 要读 sysfs；冷启动主线程同步检测没有卡顿风险。
     */
    @JvmStatic
    fun init(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext ?: context
        synchronized(detectLock) {
            if (initialized) return
            val detected = detect(appContext)
            cachedTier = detected.tier
            cachedWatchLike = detected.watchLike
            // 只在 init 阶段登记一次日志开关之外的信息：档位是排障时最常问的一句话。
            Logu.i("DeviceProfile", "设备分档=${detected.tier}，手表=${detected.watchLike}")
            initialized = true
        }
    }

    /** [init] 是否已执行（调试/测试用）。 */
    @JvmStatic
    fun isInitialized(): Boolean = initialized

    /** 清空缓存（仅单测使用；运行期没有重新检测的语义）。 */
    fun resetForTest() {
        synchronized(detectLock) {
            cachedTier = null
            cachedWatchLike = null
            initialized = false
        }
    }

    /**
     * 当前生效档位：SharedPreferences 显式覆盖优先，其次 init 的检测结果，最后退化为无 Context 检测。
     */
    @JvmStatic
    fun get(): Tier {
        val override = getOverride()
        overrideTier(override)?.let { return it }
        cachedTier?.let { return it }
        return synchronized(detectLock) {
            cachedTier ?: detectWithoutContext().also {
                cachedTier = it
            }
        }
    }

    /** 当前档位（等价于 [get]，语义更直白）。 */
    @JvmStatic
    fun tier(): Tier = get()

    /** 屏幕/设备标识看起来像手表。 */
    @JvmStatic
    fun isWatchLike(): Boolean {
        cachedWatchLike?.let { return it }
        return synchronized(detectLock) {
            cachedWatchLike ?: DeviceProfilePolicy.isWatchLike(
                Build.MODEL,
                Build.BRAND,
                Build.MANUFACTURER,
                Build.PRODUCT,
                0,
                0
            ).also { cachedWatchLike = it }
        }
    }

    // ===== 各档参数：一律转调纯函数，保证设备侧与单测走同一套规则 =====

    /** 手动切换清晰度时的最高档。 */
    @JvmStatic
    fun maxQuality(): Int = DeviceProfilePolicy.maxQuality(get())

    /** 「自动」清晰度。 */
    @JvmStatic
    fun autoQuality(): Int = DeviceProfilePolicy.autoQuality(get())

    /** 是否自动请求 4K。 */
    @JvmStatic
    fun requestTopTier4k(): Boolean = DeviceProfilePolicy.requestTopTier4k(get())

    /** 是否默认软件解码。 */
    @JvmStatic
    fun defaultSoftwareCodec(): Boolean = DeviceProfilePolicy.defaultSoftwareCodec(get())

    /** 播放器 prepare 超时（毫秒）。 */
    @JvmStatic
    fun prepareTimeoutMs(): Long = DeviceProfilePolicy.prepareTimeoutMs(get())

    /** 网络连接超时（秒）。 */
    @JvmStatic
    fun connectTimeoutSec(): Int = DeviceProfilePolicy.connectTimeoutSec(get())

    /** 播放器最大缓冲（字节）。 */
    @JvmStatic
    fun maxBufferBytes(): Long = DeviceProfilePolicy.maxBufferBytes(get())

    /** 最大缓存时长（毫秒）。 */
    @JvmStatic
    fun maxCachedDurationMs(): Long = DeviceProfilePolicy.maxCachedDurationMs(get())

    /** 起播最小缓冲帧数。 */
    @JvmStatic
    fun minBufferedFrames(): Int = DeviceProfilePolicy.minBufferedFrames(get())

    /** 弹幕窗口半径（屏数）。 */
    @JvmStatic
    fun danmakuWindowRadius(): Int = DeviceProfilePolicy.danmakuWindowRadius(get())

    /** 每段弹幕上限（0 = 不限）。 */
    @JvmStatic
    fun maxDanmakuPerSegment(): Int = DeviceProfilePolicy.maxDanmakuPerSegment(get())

    /** 诊断日志批量上限（0 = 不限）。 */
    @JvmStatic
    fun diagnosticBatchLimit(): Int = DeviceProfilePolicy.diagnosticBatchLimit(get())

    /**
     * 是否使用 ExoPlayer 单时钟播放器。
     *
     * **对全部受支持设备（minSdk 19）返回 true**，唯一 false 的来源是用户在设置里打开
     * [KEY_UNIFIED_PLAYER_DISABLED] 显式降级。不要在这里加"手表就关掉"之类的档位判断。
     */
    @JvmStatic
    fun useUnifiedDashPlayer(): Boolean =
        DeviceProfilePolicy.useUnifiedDashPlayer(
            SharedPreferencesUtil.getBoolean(KEY_UNIFIED_PLAYER_DISABLED, false)
        )

    /** 是否使用旧双播放器方案：**恒 false**（该方案已作废）。 */
    @JvmStatic
    fun useDashDualPlayer(): Boolean = DeviceProfilePolicy.useDashDualPlayer()

    /** 少数老机型旋转必须走软件播放器（见 [DeviceProfilePolicy.requiresSoftwarePlayerRotation]）。 */
    @JvmStatic
    fun requiresSoftwarePlayerRotation(): Boolean =
        DeviceProfilePolicy.requiresSoftwarePlayerRotation(Build.MODEL, Build.MANUFACTURER, Build.VERSION.SDK_INT)

    // ===== 内部：SharedPreferences 覆盖与设备检测 =====

    private fun getOverride(): Int =
        SharedPreferencesUtil.getInt(KEY_DEVICE_PROFILE_OVERRIDE, DeviceProfilePolicy.OVERRIDE_AUTO)

    private fun overrideTier(override: Int): Tier? = DeviceProfilePolicy.tierFromOverride(override)

    /** 检测结果（档位 + 是否手表），detect 与 detectWithoutContext 共用。 */
    private class Detected(val tier: Tier, val watchLike: Boolean)

    /**
     * 有 Context 时的完整检测。
     *
     * 每个系统取值都单独 try/catch：某些精简手表 ROM 会缺 `FEATURE_WATCH` 声明、
     * 或 `getMemoryClass()` 抛异常，不能因为一个探测失败就让整个应用启动失败。
     */
    private fun detect(context: Context): Detected {
        val sdkInt = Build.VERSION.SDK_INT

        var memoryClassMb = 0
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            memoryClassMb = activityManager?.memoryClass ?: 0
        } catch (e: Exception) {
            Logu.w("DeviceProfile", "读取内存等级失败：${e.message}")
        }

        val cores = Runtime.getRuntime().availableProcessors()

        var systemWatch = false
        try {
            systemWatch = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
        } catch (e: Exception) {
            Logu.w("DeviceProfile", "读取 FEATURE_WATCH 失败：${e.message}")
        }

        var width = 0
        var height = 0
        try {
            val metrics = context.resources.displayMetrics
            width = metrics.widthPixels
            height = metrics.heightPixels
        } catch (e: Exception) {
            Logu.w("DeviceProfile", "读取屏幕尺寸失败：${e.message}")
        }

        val watchLike = systemWatch || DeviceProfilePolicy.isWatchLike(
            Build.MODEL,
            Build.BRAND,
            Build.MANUFACTURER,
            Build.PRODUCT,
            width,
            height
        )
        return Detected(DeviceProfilePolicy.classify(sdkInt, memoryClassMb, cores, watchLike), watchLike)
    }

    /**
     * 没有 Context 时的退化检测：只读 [Build] 与 CPU 核数，内存等级当作"未知"（0）。
     *
     * 只会在 [init] 尚未执行时走到（例如崩溃页进程、或单测里直接调 [get]）。
     */
    private fun detectWithoutContext(): Tier {
        val sdkInt = Build.VERSION.SDK_INT
        val cores = Runtime.getRuntime().availableProcessors()
        val watchLike = DeviceProfilePolicy.isWatchLike(
            Build.MODEL,
            Build.BRAND,
            Build.MANUFACTURER,
            Build.PRODUCT,
            0,
            0
        )
        return DeviceProfilePolicy.classify(sdkInt, 0, cores, watchLike)
    }
}
