package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlayerCompatPolicy] 的纯判据（O6）。
 *
 * 两类取值都直接照抄真机的 `Build.MANUFACTURER/BRAND/MODEL` 与 `Build.SUPPORTED_ABIS`，
 * 所以这些用例本质是「把几台真机搬进单测」：华为/鸿蒙默认软解、ABI 覆盖不到时回退系统播放器。
 */
class PlayerCompatPolicyTest {

    // ==================== 默认软解：华为 / 鸿蒙 ====================

    @Test
    fun prefersSoftwareCodec_华为与荣耀与鸿蒙都命中() {
        assertTrue(PlayerCompatPolicy.prefersSoftwareCodec("HUAWEI", "HUAWEI", "ELS-AN00"))
        // 分家后的荣耀：MANUFACTURER 是 HONOR，字符串里没有 huawei
        assertTrue(PlayerCompatPolicy.prefersSoftwareCodec("HONOR", "HONOR", "PGT-AN10"))
        // 部分鸿蒙设备的 MANUFACTURER 仍是 HUAWEI，但版本串/型号里带 HarmonyOS 字样
        assertTrue(PlayerCompatPolicy.prefersSoftwareCodec("HUAWEI", "HarmonyOS", "Mate 60"))
        assertTrue(PlayerCompatPolicy.prefersSoftwareCodec("huawei", "huawei", null))
    }

    @Test
    fun prefersSoftwareCodec_关键字大小写不敏感() {
        assertTrue(PlayerCompatPolicy.prefersSoftwareCodec("HuaWei", "HoNoR", null))
        assertTrue(PlayerCompatPolicy.prefersSoftwareCodec(null, "harmony", null))
    }

    @Test
    fun prefersSoftwareCodec_其它厂商不命中() {
        assertFalse(PlayerCompatPolicy.prefersSoftwareCodec("Xiaomi", "Xiaomi", "Mi Watch"))
        assertFalse(PlayerCompatPolicy.prefersSoftwareCodec("OPPO", "OPPO", "OW19W3"))
        assertFalse(PlayerCompatPolicy.prefersSoftwareCodec("samsung", "samsung", "SM-R860"))
    }

    @Test
    fun prefersSoftwareCodec_空值不命中() {
        assertFalse(PlayerCompatPolicy.prefersSoftwareCodec(null, null, null))
        assertFalse(PlayerCompatPolicy.prefersSoftwareCodec("", "", ""))
        assertFalse(PlayerCompatPolicy.prefersSoftwareCodec("   ", null, null))
        // 只有一处有值也能判：品牌串缺失时不应该因为 null 就放弃判断
        assertTrue(PlayerCompatPolicy.prefersSoftwareCodec(null, null, "HUAWEI WATCH 4"))
    }

    // ==================== ABI 覆盖范围 ====================

    @Test
    fun isSupportedNativeAbi_覆盖随包三套so() {
        assertTrue(PlayerCompatPolicy.isSupportedNativeAbi("arm64-v8a"))
        assertTrue(PlayerCompatPolicy.isSupportedNativeAbi("armeabi-v7a"))
        assertTrue(PlayerCompatPolicy.isSupportedNativeAbi("x86"))
        // 大小写与两端空白不敏感
        assertTrue(PlayerCompatPolicy.isSupportedNativeAbi(" ARM64-V8A "))
    }

    @Test
    fun isSupportedNativeAbi_其它ABI不支持() {
        assertFalse(PlayerCompatPolicy.isSupportedNativeAbi("x86_64"))
        assertFalse(PlayerCompatPolicy.isSupportedNativeAbi("mips"))
        assertFalse(PlayerCompatPolicy.isSupportedNativeAbi("armeabi"))
        assertFalse(PlayerCompatPolicy.isSupportedNativeAbi(""))
        assertFalse(PlayerCompatPolicy.isSupportedNativeAbi(null))
    }

    @Test
    fun shouldUsePlatformMediaPlayer_主流arm64与32位都不回退() {
        // 真机最常见的一组
        assertFalse(PlayerCompatPolicy.shouldUsePlatformMediaPlayer(listOf("arm64-v8a", "armeabi-v7a", "armeabi")))
        assertFalse(PlayerCompatPolicy.shouldUsePlatformMediaPlayer(listOf("armeabi-v7a", "armeabi")))
        assertFalse(PlayerCompatPolicy.shouldUsePlatformMediaPlayer(listOf("x86")))
    }

    @Test
    fun shouldUsePlatformMediaPlayer_只有64位x86的设备回退() {
        assertTrue(PlayerCompatPolicy.shouldUsePlatformMediaPlayer(listOf("x86_64")))
        assertTrue(PlayerCompatPolicy.shouldUsePlatformMediaPlayer(listOf("mips64")))
    }

    @Test
    fun shouldUsePlatformMediaPlayer_列表里有一个支持的就不回退() {
        // x86_64 模拟器装了 32 位兼容库时，SUPPORTED_ABIS 会同时列出 x86
        assertFalse(PlayerCompatPolicy.shouldUsePlatformMediaPlayer(listOf("x86_64", "x86")))
        assertFalse(PlayerCompatPolicy.shouldUsePlatformMediaPlayer(listOf("x86_64", "arm64-v8a")))
    }

    @Test
    fun shouldUsePlatformMediaPlayer_读不到ABI时不擅自回退() {
        // 信息缺失不构成回退理由：宁可照旧尝试，也不要因为读不到就把所有人的播放器换掉
        assertFalse(PlayerCompatPolicy.shouldUsePlatformMediaPlayer(null))
        assertFalse(PlayerCompatPolicy.shouldUsePlatformMediaPlayer(emptyList()))
        assertFalse(PlayerCompatPolicy.shouldUsePlatformMediaPlayer(listOf("", "  ")))
    }

    @Test
    fun shouldUsePlatformMediaPlayer_两参重载与列表版一致() {
        assertFalse(PlayerCompatPolicy.shouldUsePlatformMediaPlayer("arm64-v8a", "armeabi-v7a"))
        assertTrue(PlayerCompatPolicy.shouldUsePlatformMediaPlayer("x86_64", null))
        // API 21 以前 CPU_ABI2 可能是 null：不能因为 null 就判「不支持」
        assertFalse(PlayerCompatPolicy.shouldUsePlatformMediaPlayer("x86", null))
        assertFalse(PlayerCompatPolicy.shouldUsePlatformMediaPlayer(null, null))
    }

    // ==================== 常量与 app/libs 的一致性 ====================

    @Test
    fun supportedNativeAbis_就是随包发布的三套() {
        // 改了 app/libs 下的目录却忘了改这个列表，会让新 ABI 被误判成不支持
        assertTrue(PlayerCompatPolicy.SUPPORTED_NATIVE_ABIS.containsAll(listOf("arm64-v8a", "armeabi-v7a", "x86")))
        assertFalse(PlayerCompatPolicy.SUPPORTED_NATIVE_ABIS.contains("x86_64"))
        assertFalse(PlayerCompatPolicy.SUPPORTED_NATIVE_ABIS.contains("mips"))
    }
}
