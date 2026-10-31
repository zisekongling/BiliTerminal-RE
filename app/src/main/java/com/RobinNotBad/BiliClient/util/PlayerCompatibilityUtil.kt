package com.RobinNotBad.BiliClient.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.WindowManager
import com.RobinNotBad.BiliClient.BiliTerminal
import java.util.Locale

/**
 * 播放器兼容性判定（移植自兄弟仓库 BiliTerminal-Revival 的 `util/PlayerCompatibilityUtil.java`）。
 *
 * 收录的都是「不同设备上该用哪套播放器 / 该降多少负载」的经验规则：
 * 华为系 ROM 的硬件解码兼容性差、MIPS/armeabi 老 ABI 上没有原生解码库、
 * 旧系统与低内存机器长按倍速要降一档。
 *
 * 所有判定都做了「纯参数重载」：Android 属性的读取走公开无参方法，
 * 规则本身只吃字符串/数字，JVM 单测可以直接覆盖（见 PlayerCompatibilityUtilTest）。
 */
object PlayerCompatibilityUtil {

    /**
     * 是否优先使用软件解码。
     *
     * 华为 / 鸿蒙机型的 OMX 硬件解码在某些分辨率下会花屏或直接黑屏，统一走软解更稳。
     */
    fun prefersSoftwareCodec(): Boolean =
        prefersSoftwareCodec(Build.MANUFACTURER, Build.BRAND, Build.DISPLAY)

    /**
     * 是否应该改用系统 `MediaPlayer` 而不是自带原生库的播放器。
     *
     * 本项目的原生 `.so` 只有 arm64-v8a / armeabi-v7a / x86 三个 ABI（见 app/libs/），
     * 源码里的 MIPS 目录是空占位。若设备的 ABI 都不是 armeabi-v7a / x86，
     * 继续走自带原生库会在 `System.loadLibrary` 阶段直接崩进程，只能退回系统播放器。
     */
    @Suppress("DEPRECATION")
    fun shouldUsePlatformMediaPlayer(): Boolean =
        shouldUsePlatformMediaPlayer(Build.CPU_ABI, Build.CPU_ABI2)

    /**
     * 长按倍速时是否要降低负载：Android 4.4 及以下，或内存等级 <= 192MB 的机器。
     *
     * 这些设备开 3 倍速容易掉帧/音画抖动，降到 2 倍速。
     */
    fun shouldReduceLongPressLoad(sdkInt: Int, memoryClassMb: Int): Boolean =
        sdkInt <= Build.VERSION_CODES.KITKAT || (memoryClassMb in 1..192)

    /** 无参版本：现场读系统版本与内存等级，供调用点直接使用。 */
    fun shouldReduceLongPressLoad(): Boolean =
        shouldReduceLongPressLoad(Build.VERSION.SDK_INT, memoryClassMb())

    /** 长按倍速值：需要降负载的机器 2.0x，其余 3.0x。 */
    fun longPressSpeed(sdkInt: Int, memoryClassMb: Int): Float =
        if (shouldReduceLongPressLoad(sdkInt, memoryClassMb)) 2.0f else 3.0f

    /** 无参版本：现场读系统版本与内存等级。 */
    fun longPressSpeed(): Float = longPressSpeed(Build.VERSION.SDK_INT, memoryClassMb())

    /**
     * 屏幕刷新率是否支持高帧率（>= 59Hz）。
     *
     * 取不到刷新率（部分手表 ROM 的 WindowManager 会抛异常）时按不支持处理。
     */
    @Suppress("DEPRECATION")
    fun supportsHighFrameRate(context: Context?): Boolean {
        if (context == null) return false
        return try {
            val manager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val display = manager?.defaultDisplay
            display != null && supportsHighFrameRate(display.refreshRate)
        } catch (t: Throwable) {
            // 这里必须 catch Throwable：个别 ROM 的 getDefaultDisplay() 会抛 Error 而不是 Exception
            false
        }
    }

    // ===== 纯参数重载（不读 Android 属性，供单测与内部复用） =====

    /** 见 [shouldUsePlatformMediaPlayer]：两个参数都拿不到支持的 ABI 时才退回系统播放器。 */
    fun shouldUsePlatformMediaPlayer(primaryAbi: String?, secondaryAbi: String?): Boolean =
        !isSupportedNativeAbi(primaryAbi) && !isSupportedNativeAbi(secondaryAbi)

    /** 本项目自带原生库支持的 ABI（忽略大小写）。 */
    private fun isSupportedNativeAbi(abi: String?): Boolean =
        "armeabi-v7a".equals(abi, ignoreCase = true) || "x86".equals(abi, ignoreCase = true)

    /** 见 [supportsHighFrameRate]：刷新率 >= 59Hz 视为支持高帧率。 */
    fun supportsHighFrameRate(refreshRate: Float): Boolean = refreshRate >= 59.0f

    /** 见 [prefersSoftwareCodec]：厂商/品牌/显示版本号里出现 huawei / harmony 即走软解。 */
    fun prefersSoftwareCodec(manufacturer: String?, brand: String?, display: String?): Boolean {
        val identity = ("${manufacturer ?: ""} ${brand ?: ""} ${display ?: ""}").lowercase(Locale.US)
        return identity.contains("huawei") || identity.contains("harmony")
    }

    /**
     * 取当前进程的 `ActivityManager.getMemoryClass()`（MB）；取不到返回 0（"未知"，按不降载处理）。
     */
    private fun memoryClassMb(): Int = try {
        val appContext = BiliTerminal.context
        if (appContext == null) {
            0
        } else {
            (appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.memoryClass ?: 0
        }
    } catch (t: Throwable) {
        0
    }
}
