package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [NetworkPlaybackQuality] 的纯函数单测（不碰 Android：`detectKind` 不在这里测）。
 *
 * 重点钉三件事：
 * 1. **默认值不改变老行为**：两个网络键都是 -1 时，无论什么网络都返回 `PLAY_QN`；
 * 2. **非法值不被当成 qn**：0 / 负数一律视为「跟随默认」，不能把 0 发给接口；
 * 3. **VPN 回落**：VPN 下不猜底层网络，直接跟随默认。
 *
 * 另附两条 key 名钉死断言：`play_qn_wifi` / `play_qn_mobile` 是**磁盘协议**，
 * 改了就等于把已配置用户的设置静默清空（与 `SettingsKeysTest` 同类保护）。
 */
class NetworkPlaybackQualityTest {

    @Test
    fun resolveQn_wifiUsesWifiValue() {
        assertEquals(80, NetworkPlaybackQuality.resolveQn(16, 80, 64, NetworkPlaybackQuality.NetworkKind.WIFI))
    }

    @Test
    fun resolveQn_mobileUsesMobileValue() {
        assertEquals(64, NetworkPlaybackQuality.resolveQn(16, 80, 64, NetworkPlaybackQuality.NetworkKind.MOBILE))
    }

    @Test
    fun resolveQn_unknownFallsBackToFollow() {
        assertEquals(16, NetworkPlaybackQuality.resolveQn(16, 80, 64, NetworkPlaybackQuality.NetworkKind.UNKNOWN))
    }

    @Test
    fun resolveQn_vpnIsUnknownAndFallsBack() {
        // detectKind 对 VPN 返回 UNKNOWN，这条把「VPN → 跟随默认」的语义钉住
        val kind = NetworkPlaybackQuality.kindFromFlags(isWifi = true, isCellular = true, isVpn = true)
        assertEquals(NetworkPlaybackQuality.NetworkKind.UNKNOWN, kind)
        assertEquals(16, NetworkPlaybackQuality.resolveQn(16, 80, 64, kind))
    }

    @Test
    fun resolveQn_followDefaultSentinelUsesFollowValue() {
        assertEquals(
            16,
            NetworkPlaybackQuality.resolveQn(
                16,
                NetworkPlaybackQuality.FOLLOW_DEFAULT,
                NetworkPlaybackQuality.FOLLOW_DEFAULT,
                NetworkPlaybackQuality.NetworkKind.WIFI
            )
        )
    }

    @Test
    fun resolveQn_nonPositiveIsTreatedAsFollow() {
        // 0 是损坏/空值，绝不能当 qn 发给接口
        assertEquals(16, NetworkPlaybackQuality.resolveQn(16, 0, 0, NetworkPlaybackQuality.NetworkKind.MOBILE))
        assertEquals(16, NetworkPlaybackQuality.resolveQn(16, -5, -5, NetworkPlaybackQuality.NetworkKind.WIFI))
    }

    @Test
    fun kindFromFlags_wifiWinsOverCellular() {
        assertEquals(
            NetworkPlaybackQuality.NetworkKind.WIFI,
            NetworkPlaybackQuality.kindFromFlags(isWifi = true, isCellular = true, isVpn = false)
        )
    }

    @Test
    fun kindFromFlags_cellularAndNone() {
        assertEquals(
            NetworkPlaybackQuality.NetworkKind.MOBILE,
            NetworkPlaybackQuality.kindFromFlags(isWifi = false, isCellular = true, isVpn = false)
        )
        assertEquals(
            NetworkPlaybackQuality.NetworkKind.UNKNOWN,
            NetworkPlaybackQuality.kindFromFlags(isWifi = false, isCellular = false, isVpn = false)
        )
    }

    @Test
    fun constants_areStable() {
        assertEquals(-1, NetworkPlaybackQuality.FOLLOW_DEFAULT)
        assertEquals(16, NetworkPlaybackQuality.DEFAULT_QN)
    }

    @Test
    fun settingsKeys_areDiskProtocol() {
        assertEquals("play_qn", SettingsKeys.PLAY_QN)
        assertEquals("play_qn_wifi", SettingsKeys.PLAY_QN_WIFI)
        assertEquals("play_qn_mobile", SettingsKeys.PLAY_QN_MOBILE)
    }
}
