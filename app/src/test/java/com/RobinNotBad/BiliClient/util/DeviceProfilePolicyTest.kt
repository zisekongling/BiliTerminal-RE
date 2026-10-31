package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.player.QualityAccessPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DeviceProfilePolicy] 的纯判定逻辑单测（26.10.31 设备分档移植）。
 *
 * 只测规则，不碰 Android：真实设备上的内存等级、屏幕尺寸由 [DeviceProfile] 取值，
 * 这里直接用数字覆盖每一档的边界条件。
 */
class DeviceProfilePolicyTest {

    // ===== classify：手表最优先，任何高配参数都救不回来 =====

    @Test
    fun classify_watchLikeAlwaysCompat() {
        assertEquals(Tier.COMPAT, DeviceProfilePolicy.classify(34, 1024, 8, true))
    }

    // ===== classify：SDK 边界（<=25 兼容，>=26 才可能高配） =====

    @Test
    fun classify_sdk25_isCompat_sdk26WithBigMemory_isHighEnd() {
        assertEquals(Tier.COMPAT, DeviceProfilePolicy.classify(25, 512, 8))
        assertEquals(Tier.HIGH_END, DeviceProfilePolicy.classify(26, 256, 8))
    }

    @Test
    fun classify_sdk19_isCompat() {
        assertEquals(Tier.COMPAT, DeviceProfilePolicy.classify(19, 512, 8))
    }

    // ===== classify：内存与核数 =====

    @Test
    fun classify_lowMemory_isCompat() {
        // 内存 <= 128MB：即便新系统、多核也压成兼容档
        assertEquals(Tier.COMPAT, DeviceProfilePolicy.classify(30, 128, 8))
    }

    @Test
    fun classify_midMemoryWithFewCores_isCompat() {
        // 内存 <= 192MB 且核数 <= 2
        assertEquals(Tier.COMPAT, DeviceProfilePolicy.classify(28, 192, 2))
        // 同样的内存但多核：落到均衡档
        assertEquals(Tier.BALANCED, DeviceProfilePolicy.classify(28, 192, 8))
    }

    @Test
    fun classify_highEndBoundaries() {
        // Android 10+ 且内存 >= 512MB
        assertEquals(Tier.HIGH_END, DeviceProfilePolicy.classify(29, 512, 8))
        // Android 8~9 且内存 >= 256MB
        assertEquals(Tier.HIGH_END, DeviceProfilePolicy.classify(26, 300, 4))
        // 内存不到 256MB 且多核：均衡档
        assertEquals(Tier.BALANCED, DeviceProfilePolicy.classify(26, 200, 8))
    }

    @Test
    fun classify_unknownMemoryOnNewSystem_isBalanced() {
        // 内存未知（0）时：Android 8+ 按均衡档，不知道就别当高配
        assertEquals(Tier.BALANCED, DeviceProfilePolicy.classify(26, 0, 8))
        assertEquals(Tier.BALANCED, DeviceProfilePolicy.classify(34, 0, 8))
        // 但 SDK <= 25 的规则优先，仍然是兼容档
        assertEquals(Tier.COMPAT, DeviceProfilePolicy.classify(25, 0, 8))
    }

    // ===== isWatchLike：标识命中与屏幕尺寸兜底 =====

    @Test
    fun isWatchLike_identityContainsWatchWords() {
        assertTrue(DeviceProfilePolicy.isWatchLike("Watch 2", "Xiaomi", "Xiaomi", "watch2", 454, 454))
        assertTrue(DeviceProfilePolicy.isWatchLike("Aqsh1", "oppo", "oppo", "aqsh", 0, 0))
        assertTrue(DeviceProfilePolicy.isWatchLike("dove_evb", "unknown", "unknown", "dove_evb", 0, 0))
        // 大小写不敏感
        assertTrue(DeviceProfilePolicy.isWatchLike("SMARTWATCH", "Brand", "Maker", "prod", 0, 0))
    }

    @Test
    fun isWatchLike_smallScreenFallback() {
        // 短边 480 / 长边 640 以内按手表算，即便机型名里没有 watch
        assertTrue(DeviceProfilePolicy.isWatchLike("MTK-Device", "brand", "maker", "product", 466, 466))
        assertTrue(DeviceProfilePolicy.isWatchLike("MTK-Device", "brand", "maker", "product", 480, 640))
        // 超出长边阈值就不是
        assertFalse(DeviceProfilePolicy.isWatchLike("Phone", "brand", "maker", "product", 480, 800))
        assertFalse(DeviceProfilePolicy.isWatchLike("Phone", "brand", "maker", "product", 1080, 1920))
    }

    @Test
    fun isWatchLike_unknownScreenOnlyIdentity() {
        // 宽高未知（0）时不许按尺寸误判，只看标识
        assertFalse(DeviceProfilePolicy.isWatchLike("Phone", "brand", "maker", "product", 0, 0))
        // null 字段也不许崩
        assertFalse(DeviceProfilePolicy.isWatchLike(null, null, null, null, 0, 0))
    }

    // ===== 覆盖值合并 =====

    @Test
    fun tierFromOverride_mapsValues() {
        assertNull(DeviceProfilePolicy.tierFromOverride(DeviceProfilePolicy.OVERRIDE_AUTO))
        assertEquals(Tier.COMPAT, DeviceProfilePolicy.tierFromOverride(DeviceProfilePolicy.OVERRIDE_COMPAT))
        assertEquals(Tier.HIGH_END, DeviceProfilePolicy.tierFromOverride(DeviceProfilePolicy.OVERRIDE_HIGH_END))
        // 非法值当作自动
        assertNull(DeviceProfilePolicy.tierFromOverride(99))
    }

    @Test
    fun effectiveTier_overrideWinsOverDetected() {
        assertEquals(Tier.HIGH_END, DeviceProfilePolicy.effectiveTier(DeviceProfilePolicy.OVERRIDE_HIGH_END, Tier.COMPAT))
        assertEquals(Tier.COMPAT, DeviceProfilePolicy.effectiveTier(DeviceProfilePolicy.OVERRIDE_COMPAT, Tier.HIGH_END))
        assertEquals(Tier.BALANCED, DeviceProfilePolicy.effectiveTier(DeviceProfilePolicy.OVERRIDE_AUTO, Tier.BALANCED))
        // 自动且检测未知：兜底兼容档
        assertEquals(Tier.COMPAT, DeviceProfilePolicy.effectiveTier(DeviceProfilePolicy.OVERRIDE_AUTO, null))
    }

    // ===== 播放器选择：全设备统一 ExoPlayer，双播放器方案作废 =====

    @Test
    fun useUnifiedDashPlayer_trueForAllDevices() {
        // 默认不降级时恒为 true，与设备档位无关
        assertTrue(DeviceProfilePolicy.useUnifiedDashPlayer(unifiedPlayerDisabled = false))
        // 只有显式降级开关才返回 false
        assertFalse(DeviceProfilePolicy.useUnifiedDashPlayer(unifiedPlayerDisabled = true))
    }

    @Test
    fun useDashDualPlayer_alwaysFalse() {
        assertFalse(DeviceProfilePolicy.useDashDualPlayer())
    }

    // ===== 清晰度档位 =====

    @Test
    fun quality_tiers() {
        assertEquals(QualityAccessPolicy.QN_1080P, DeviceProfilePolicy.maxQuality(Tier.COMPAT))
        assertEquals(QualityAccessPolicy.QN_1080P, DeviceProfilePolicy.maxQuality(Tier.BALANCED))
        assertEquals(QualityAccessPolicy.QN_4K, DeviceProfilePolicy.maxQuality(Tier.HIGH_END))

        assertEquals(QualityAccessPolicy.QN_720P, DeviceProfilePolicy.autoQuality(Tier.COMPAT))
        assertEquals(QualityAccessPolicy.QN_720P, DeviceProfilePolicy.autoQuality(Tier.BALANCED))
        assertEquals(QualityAccessPolicy.QN_1080P, DeviceProfilePolicy.autoQuality(Tier.HIGH_END))

        assertFalse(DeviceProfilePolicy.requestTopTier4k(Tier.COMPAT))
        assertFalse(DeviceProfilePolicy.requestTopTier4k(Tier.BALANCED))
        assertTrue(DeviceProfilePolicy.requestTopTier4k(Tier.HIGH_END))
    }

    // ===== 各档阈值：只有 COMPAT 与非 COMPAT 两套 =====

    @Test
    fun thresholds_compatVsNonCompat() {
        assertEquals(30_000L, DeviceProfilePolicy.prepareTimeoutMs(Tier.COMPAT))
        assertEquals(20_000L, DeviceProfilePolicy.prepareTimeoutMs(Tier.BALANCED))
        assertEquals(20_000L, DeviceProfilePolicy.prepareTimeoutMs(Tier.HIGH_END))

        assertEquals(5, DeviceProfilePolicy.connectTimeoutSec(Tier.COMPAT))
        assertEquals(8, DeviceProfilePolicy.connectTimeoutSec(Tier.HIGH_END))

        assertEquals(6L * 1024 * 1024, DeviceProfilePolicy.maxBufferBytes(Tier.COMPAT))
        assertEquals(15L * 1024 * 1024, DeviceProfilePolicy.maxBufferBytes(Tier.BALANCED))

        assertEquals(6_000L, DeviceProfilePolicy.maxCachedDurationMs(Tier.COMPAT))
        assertEquals(12_000L, DeviceProfilePolicy.maxCachedDurationMs(Tier.HIGH_END))

        assertEquals(5, DeviceProfilePolicy.minBufferedFrames(Tier.COMPAT))
        assertEquals(8, DeviceProfilePolicy.minBufferedFrames(Tier.BALANCED))

        assertEquals(0, DeviceProfilePolicy.danmakuWindowRadius(Tier.COMPAT))
        assertEquals(1, DeviceProfilePolicy.danmakuWindowRadius(Tier.HIGH_END))

        assertEquals(DeviceProfilePolicy.COMPAT_DANMAKU_LIMIT, DeviceProfilePolicy.maxDanmakuPerSegment(Tier.COMPAT))
        assertEquals(0, DeviceProfilePolicy.maxDanmakuPerSegment(Tier.HIGH_END))

        assertEquals(200, DeviceProfilePolicy.diagnosticBatchLimit(Tier.COMPAT))
        assertEquals(0, DeviceProfilePolicy.diagnosticBatchLimit(Tier.BALANCED))

        // 软解名额只给兼容档
        assertTrue(DeviceProfilePolicy.defaultSoftwareCodec(Tier.COMPAT))
        assertFalse(DeviceProfilePolicy.defaultSoftwareCodec(Tier.BALANCED))
    }

    @Test
    fun requiresSoftwarePlayerRotation_onlySpreadtrumA15OnKitKat() {
        assertTrue(DeviceProfilePolicy.requiresSoftwarePlayerRotation("a15", "SPREADTRUM", 19))
        // 只有该组合；系统版本高于 4.4 或厂商不是展讯都不算
        assertFalse(DeviceProfilePolicy.requiresSoftwarePlayerRotation("a15", "Spreadtrum", 21))
        assertFalse(DeviceProfilePolicy.requiresSoftwarePlayerRotation("a15", "MediaTek", 19))
        assertFalse(DeviceProfilePolicy.requiresSoftwarePlayerRotation("a16", "Spreadtrum", 19))
    }
}
