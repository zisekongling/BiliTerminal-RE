package com.RobinNotBad.BiliClient.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * 「按网络类型选默认清晰度」。
 *
 * ## 规则
 * 起播清晰度默认值的优先级：
 * - 当前网络是 **Wi-Fi** → [SettingsKeys.PLAY_QN_WIFI]；
 * - 当前网络是**移动数据** → [SettingsKeys.PLAY_QN_MOBILE]；
 * - **未知网络 / VPN / 两个新键为「跟随默认」(-1)** → [SettingsKeys.PLAY_QN]。
 *
 * 两个新键的默认值都是 [FOLLOW_DEFAULT](-1)，所以「不配置 = 与改造前完全一致」，
 * 老用户不打开这两个设置项不会感知到任何变化。
 *
 * ## 为什么 VPN 回落「跟随默认」而不是按底层网络猜
 * VPN 下 `NetworkCapabilities` 在不同 ROM 上行为不一致：有的只报 `TRANSPORT_VPN`，
 * 有的把底层（Wi-Fi/移动）一起报上来。按底层猜会把「用户开了代理/加速器」的场景误判成
 * 移动网络，从而选到省流档。宁可回落「跟随默认」，也不要替用户做带偏见的决定。
 *
 * ## 可测性
 * 只有 [detectKind] 依赖 Android；决策（[resolveQn]）与分类（[kindFromFlags]）是纯函数，
 * 单测见 `app/src/test/.../util/NetworkPlaybackQualityTest.kt`。
 */
object NetworkPlaybackQuality {

    /** 「跟随默认清晰度」的哨兵值：两个网络键存 -1 表示不覆盖 [SettingsKeys.PLAY_QN]。 */
    const val FOLLOW_DEFAULT = -1

    /** 历史默认档位（360P），与改造前 `getInt(PLAY_QN, 16)` 的兜底保持一致。 */
    const val DEFAULT_QN = 16

    enum class NetworkKind { WIFI, MOBILE, UNKNOWN }

    /**
     * 当前该用的默认清晰度。**唯一对外入口**（Java 侧同样可用：`NetworkPlaybackQuality.defaultQn(ctx)`）。
     *
     * 纯读配置 + 一次网络类型探测，不做网络请求，主线程可调用。
     */
    @JvmStatic
    fun defaultQn(context: android.content.Context): Int = resolveQn(
        followQn = SharedPreferencesUtil.getInt(SettingsKeys.PLAY_QN, DEFAULT_QN),
        wifiQn = SharedPreferencesUtil.getInt(SettingsKeys.PLAY_QN_WIFI, FOLLOW_DEFAULT),
        mobileQn = SharedPreferencesUtil.getInt(SettingsKeys.PLAY_QN_MOBILE, FOLLOW_DEFAULT),
        kind = detectKind(context)
    )

    /**
     * 纯决策函数：给定三个配置值与网络类型，返回该用的 qn。
     *
     * 非正值（未配置 -1、配置损坏 0）一律视为「跟随默认」，**不会**把 0 当合法 qn 发给接口。
     */
    @JvmStatic
    fun resolveQn(followQn: Int, wifiQn: Int, mobileQn: Int, kind: NetworkKind): Int {
        val candidate = when (kind) {
            NetworkKind.WIFI -> wifiQn
            NetworkKind.MOBILE -> mobileQn
            NetworkKind.UNKNOWN -> FOLLOW_DEFAULT
        }
        return if (candidate > 0) candidate else followQn
    }

    /**
     * 纯分类函数：把「探测到的传输类型标记」折算成 [NetworkKind]。
     *
     * 优先级：VPN（回落未知）> Wi-Fi > 移动数据 > 未知。Wi-Fi 优先于移动数据是有意的：
     * 部分设备在「Wi-Fi 未联网但保持连接」时会同时报两种传输，此时按 Wi-Fi 档走更合理。
     */
    @JvmStatic
    fun kindFromFlags(isWifi: Boolean, isCellular: Boolean, isVpn: Boolean): NetworkKind = when {
        isVpn -> NetworkKind.UNKNOWN
        isWifi -> NetworkKind.WIFI
        isCellular -> NetworkKind.MOBILE
        else -> NetworkKind.UNKNOWN
    }

    /**
     * 探测当前活动网络的类型。**任何异常都回落 [NetworkKind.UNKNOWN]**：
     * 网络类型判定失败绝不该让「打开视频」失败。
     */
    @JvmStatic
    fun detectKind(context: Context): NetworkKind {
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return NetworkKind.UNKNOWN
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                detectByCapabilities(manager)
            } else {
                detectByLegacyType(manager)
            }
        } catch (t: Throwable) {
            NetworkKind.UNKNOWN
        }
    }

    /** API 23+：`NetworkCapabilities` 是唯一可靠来源（`activeNetworkInfo` 在 29 起已废弃）。 */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun detectByCapabilities(manager: ConnectivityManager): NetworkKind {
        val network = manager.activeNetwork ?: return NetworkKind.UNKNOWN
        val capabilities = manager.getNetworkCapabilities(network) ?: return NetworkKind.UNKNOWN
        // TRANSPORT_VPN 是 API 28；低版本上 VPN 会以「底层传输」上报，这里按能力位判断不到就交给 kindFromFlags
        val vpn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        return kindFromFlags(
            isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
            isVpn = vpn
        )
    }

    /** minSdk 19 ~ 22 的兼容路径：`activeNetworkInfo.type`。 */
    @Suppress("DEPRECATION")
    private fun detectByLegacyType(manager: ConnectivityManager): NetworkKind {
        val info = manager.activeNetworkInfo ?: return NetworkKind.UNKNOWN
        if (!info.isConnected) return NetworkKind.UNKNOWN
        return when (info.type) {
            ConnectivityManager.TYPE_WIFI -> NetworkKind.WIFI
            ConnectivityManager.TYPE_MOBILE -> NetworkKind.MOBILE
            // 含 TYPE_VPN(17) 等一切其它类型：一律按未知处理（见类注释「VPN 回落」）
            else -> NetworkKind.UNKNOWN
        }
    }
}
