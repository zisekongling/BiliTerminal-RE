package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlayerCompatibilityUtil] 的纯参数判定单测（26.10.31 兼容性工具移植）。
 *
 * 无参方法要读 `Build.*` / `WindowManager`，JVM 单测里拿不到真实值；
 * 这里只测纯参数重载，规则本身与设备侧调用的是同一份代码。
 */
class PlayerCompatibilityUtilTest {

    // ===== prefersSoftwareCodec：华为/鸿蒙系走软解 =====

    @Test
    fun prefersSoftwareCodec_huaweiAndHarmony() {
        assertTrue(PlayerCompatibilityUtil.prefersSoftwareCodec("HUAWEI", "HONOR", "EMUI 12"))
        assertTrue(PlayerCompatibilityUtil.prefersSoftwareCodec("unknown", "HarmonyOS", "1.0"))
        // 大小写不敏感
        assertTrue(PlayerCompatibilityUtil.prefersSoftwareCodec("huawei", "brand", "display"))
    }

    @Test
    fun prefersSoftwareCodec_otherVendorsAndNulls() {
        assertFalse(PlayerCompatibilityUtil.prefersSoftwareCodec("Xiaomi", "Redmi", "MIUI 14"))
        assertFalse(PlayerCompatibilityUtil.prefersSoftwareCodec("samsung", "samsung", "TP1A"))
        // null 不许崩
        assertFalse(PlayerCompatibilityUtil.prefersSoftwareCodec(null, null, null))
    }

    // ===== shouldUsePlatformMediaPlayer：只有 armeabi-v7a / x86 才有原生库 =====

    @Test
    fun shouldUsePlatformMediaPlayer_supportedAbis() {
        assertFalse(PlayerCompatibilityUtil.shouldUsePlatformMediaPlayer("armeabi-v7a", ""))
        assertFalse(PlayerCompatibilityUtil.shouldUsePlatformMediaPlayer("x86", "x86_64"))
        // 任意一个 ABI 命中就必须不需要系统播放器
        assertFalse(PlayerCompatibilityUtil.shouldUsePlatformMediaPlayer("mips", "x86"))
        // 大小写不敏感
        assertFalse(PlayerCompatibilityUtil.shouldUsePlatformMediaPlayer("ARMEABI-V7A", null))
    }

    @Test
    fun shouldUsePlatformMediaPlayer_unsupportedAbis() {
        // arm64-v8a 不在自带原生库列表里：必须退回系统播放器，否则 loadLibrary 会崩
        assertTrue(PlayerCompatibilityUtil.shouldUsePlatformMediaPlayer("arm64-v8a", ""))
        assertTrue(PlayerCompatibilityUtil.shouldUsePlatformMediaPlayer("mips", "mips64"))
        assertTrue(PlayerCompatibilityUtil.shouldUsePlatformMediaPlayer(null, null))
    }

    // ===== shouldReduceLongPressLoad：Android 4.4 及以下或内存 <= 192MB =====

    @Test
    fun shouldReduceLongPressLoad_boundaries() {
        // Android 4.4 及以下：不论内存都降
        assertTrue(PlayerCompatibilityUtil.shouldReduceLongPressLoad(19, 1024))
        assertTrue(PlayerCompatibilityUtil.shouldReduceLongPressLoad(19, 0))
        // Android 5+：内存 1..192 才降
        assertTrue(PlayerCompatibilityUtil.shouldReduceLongPressLoad(21, 192))
        assertTrue(PlayerCompatibilityUtil.shouldReduceLongPressLoad(34, 128))
        // 内存 193 或未知（0）：不降
        assertFalse(PlayerCompatibilityUtil.shouldReduceLongPressLoad(21, 193))
        assertFalse(PlayerCompatibilityUtil.shouldReduceLongPressLoad(34, 0))
    }

    @Test
    fun longPressSpeed_twoOrThree() {
        assertEquals(2.0f, PlayerCompatibilityUtil.longPressSpeed(19, 512), 0.0f)
        assertEquals(2.0f, PlayerCompatibilityUtil.longPressSpeed(30, 192), 0.0f)
        assertEquals(3.0f, PlayerCompatibilityUtil.longPressSpeed(30, 512), 0.0f)
    }

    // ===== supportsHighFrameRate：>= 59Hz =====

    @Test
    fun supportsHighFrameRate_threshold() {
        assertTrue(PlayerCompatibilityUtil.supportsHighFrameRate(59.0f))
        assertTrue(PlayerCompatibilityUtil.supportsHighFrameRate(60.0f))
        assertTrue(PlayerCompatibilityUtil.supportsHighFrameRate(120.0f))
        assertFalse(PlayerCompatibilityUtil.supportsHighFrameRate(58.9f))
        assertFalse(PlayerCompatibilityUtil.supportsHighFrameRate(0.0f))
    }
}
