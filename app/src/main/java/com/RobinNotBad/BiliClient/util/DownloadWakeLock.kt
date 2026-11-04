package com.RobinNotBad.BiliClient.util

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

/**
 * 下载期间的「暂时不休眠」锁（反馈 #91：图片和视频前台下载时能加个暂时不休眠吗）。
 *
 * **现象与根因**：下载都跑在后台线程里（[com.RobinNotBad.BiliClient.service.DownloadService] 的前台服务线程、
 * [com.RobinNotBad.BiliClient.activity.DownloadActivity] 的 CenterThreadPool）。屏幕一熄灭，
 * 没有前台交互的设备会进入休眠，CPU 停转后网络 `read()` 被挂起，下载进度就停在原地 ——
 * 用户看到的是「不亮屏就不下载」。只加 `KEEP_SCREEN_ON` 治不了根本：用户主动熄屏、
 * 或离开下载页后它就不再生效。
 *
 * **做法**：在**真正有下载在跑**时申请一把 [PowerManager.PARTIAL_WAKE_LOCK]（只保 CPU，
 * 不点亮屏幕），顺手加一把 Wi-Fi 锁，避免熄屏后 Wi-Fi 进入省电模式把下载速度压到接近 0。
 *
 * **何时持锁 / 何时释放**：
 * - 批量下载服务：`DownloadService.onStartCommand` 里真正进入下载分支前 acquire；
 * - 前台单文件下载：`DownloadActivity` 的 `CenterThreadPool.run { }` 块开头 acquire；
 * - 两者都用 try/finally 配对 release，下载批次结束 / 异常 / 服务被停止都会走到；
 *   全部持有者都 release 之后系统锁才真正释放。空闲时绝不持有 —— 常驻一把 PARTIAL_WAKE_LOCK
 *   会吞掉待机续航，属于用户一定会投诉的耗电问题。
 * - 进程被系统杀掉时锁由 PowerManagerService 随进程死亡回收，不会跨进程残留。
 *
 * 两个入口共用同一把锁，所以必须计数，见 [DownloadWakeLockCounter]（否则一方先结束会把另一方的锁放掉）。
 *
 * 权限：只需清单里的 `android.permission.WAKE_LOCK`。Wi-Fi 锁也只依赖这一枚权限，
 * **不需要** ACCESS_WIFI_STATE（那是给「查询 Wi-Fi 状态」用的，见清单里审计 M12-c 的说明）。
 */
object DownloadWakeLock {

    private const val TAG = "DownloadWakeLock"

    /** 系统 wake lock 的调试 tag（`adb shell dumpsys power` 里能看到这个名字）。 */
    private const val WAKE_LOCK_TAG = "BiliTerminal:download"

    private const val WIFI_LOCK_TAG = "BiliTerminal:download:wifi"

    private val refs = DownloadWakeLockCounter()

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    /**
     * 申请一次「下载期间不休眠」。
     *
     * 调用方必须用 try/finally 配对 [release]，并且**只在返回 true 时** release：
     * 返回 false 表示这次没记上持有（没有 Context / 系统服务不可用），
     * 此时 release 会去减别人的计数。
     *
     * @return true 表示本次调用确实记了一笔持有（系统锁可能由别的持有者先申请好，这里只计数）。
     */
    @SuppressLint("WakelockTimeout") // 故意不设超时：持锁时长由下载批次生命周期决定，见下方 try/finally 说明
    @JvmStatic
    @Synchronized
    fun acquire(context: Context?): Boolean {
        if (context == null) return false
        val appContext = context.applicationContext ?: context
        // 已经有人在持有：系统锁已经生效，只加计数不重复申请
        if (!refs.retain()) return true

        val lock = try {
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
        } catch (e: Exception) {
            Logu.w(TAG, "申请 PARTIAL_WAKE_LOCK 失败：${e.message}")
            null
        }
        if (lock == null) {
            // 回滚计数：没拿到锁就不能留一笔持有，否则以后再也等不到 1
            refs.release()
            return false
        }
        // 自己维护计数（关掉系统那层的引用计数），acquire/release 的次数语义只和本类一致
        lock.setReferenceCounted(false)
        try {
            // 不设超时：解锁时机由下载生命周期决定（finally 里 release），设超时会让超长下载
            // 中途失去保护；进程死亡时系统会自动回收，不会泄漏。
            lock.acquire()
        } catch (e: Exception) {
            Logu.w(TAG, "持有 PARTIAL_WAKE_LOCK 失败：${e.message}")
            refs.release()
            return false
        }
        wakeLock = lock
        wifiLock = acquireWifiLock(appContext)
        return true
    }

    /** 释放一次持有；只有在最后一个持有者退出时才真正释放系统锁。未配对的多余调用按忽略处理。 */
    @JvmStatic
    @Synchronized
    fun release() {
        if (!refs.release()) return
        releaseWifiLock()
        val lock = wakeLock
        wakeLock = null
        try {
            lock?.release()
        } catch (_: Exception) {
            // 已经被系统回收（服务被杀等）时再 release 会抛，忽略即可
        }
    }

    /**
     * 申请 Wi-Fi 锁：熄屏后 Wi-Fi 会进省电模式，下载速度可能掉到接近 0；
     * 这层失败不影响下载本身（CPU 锁才是关键），所以失败只记日志不返回错误。
     */
    @Suppress("DEPRECATION")
    private fun acquireWifiLock(context: Context): WifiManager.WifiLock? {
        return try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFI_LOCK_TAG)
            lock?.setReferenceCounted(false)
            lock?.acquire()
            lock
        } catch (e: Exception) {
            Logu.w(TAG, "申请 Wi-Fi 锁失败（不影响下载，只影响熄屏后的速度）：${e.message}")
            null
        }
    }

    private fun releaseWifiLock() {
        val lock = wifiLock
        wifiLock = null
        try {
            lock?.release()
        } catch (_: Exception) {
        }
    }
}
