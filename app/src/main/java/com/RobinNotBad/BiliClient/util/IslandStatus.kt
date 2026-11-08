package com.RobinNotBad.BiliClient.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.service.DownloadService
import com.RobinNotBad.BiliClient.service.PlaybackService
import kotlin.math.roundToInt

/**
 * 顶栏灵动岛的状态源。
 *
 * 只负责「从各模块读一个瞬时快照」，不做 UI、不做调度；所有字段都是进程级状态，
 * 不持有 Activity（电量用传入的应用 Context 读，读不到就跳过）。
 *
 * ## 瞬时事件为什么要自己记 armed
 * 低电量、充电、新公告、新版本如果每次 tick 都返回 `BATTERY` / `ANNOUNCEMENT`，
 * 会永远压住播放/下载/未读。这里用 `xxxArmed` 保证同一件事只弹一次 10 秒：
 * 条件触发一次后置 false，等条件恢复正常才重新武装。
 *
 * ## 线程
 * `PlaybackService` / `DownloadService` 的读取都是 `@Volatile` 字段，可以从主线程直接读；
 * 公告标记由启动线程写入，用 `@Volatile` 保证可见性。
 */
object IslandStatus {

    private const val TAG = "IslandStatus"

    /** 瞬时事件显示时长。 */
    const val BURST_MS = 10_000L

    /** 低于等于这个电量算低电量。 */
    private const val BATTERY_LOW_PERCENT = 20

    /** 电量变化不频繁，15 秒读一次足够。 */
    private const val BATTERY_POLL_MS = 15_000L

    /** 更新检查要读包管理器，1 秒一次太贵；1 分钟一次足够。 */
    private const val UPDATE_POLL_MS = 60_000L

    @Volatile private var batteryPercent = -1
    @Volatile private var charging = false
    @Volatile private var lastBatteryPoll = 0L
    @Volatile private var batteryArmed = true
    @Volatile private var batteryBurstUntil = 0L

    @Volatile private var updateArmed = true
    @Volatile private var updateBurstUntil = 0L
    @Volatile private var lastUpdatePoll = 0L

    @Volatile private var announcementBurstUntil = 0L
    @Volatile private var announcementPending = false

    /**
     * 启动时拉到新公告（`TerminalApi.checkAnnouncement`）后调一次；
     * 第一页顶栏挂上灵动岛时消费，显示 10 秒「公告」。
     */
    @JvmStatic
    fun markAnnouncement() {
        announcementPending = true
    }

    /** 读一次状态快照。主线程调用。 */
    fun read(context: Context? = BiliTerminal.context): IslandSnapshot {
        val now = SystemClock.uptimeMillis()
        val ctx = context ?: BiliTerminal.context
        if (ctx != null) pollBattery(ctx, now)
        pollUpdate(now)

        if (announcementPending) {
            announcementPending = false
            announcementBurstUntil = now + BURST_MS
        }

        val updateBurst = now < updateBurstUntil
        val burst = when {
            now < batteryBurstUntil -> IslandState.BATTERY
            now < announcementBurstUntil -> IslandState.ANNOUNCEMENT
            updateBurst -> IslandState.ANNOUNCEMENT
            else -> null
        }

        return IslandSnapshot(
            playing = readPlaying(),
            downloading = readDownloading(),
            downloadPercent = readDownloadPercent(),
            unread = readUnread(),
            batteryPercent = batteryPercent,
            charging = charging,
            burst = burst,
            // 公告与新版本共用 ANNOUNCEMENT 状态，文案在绑定层区分。
            burstIsUpdate = burst == IslandState.ANNOUNCEMENT && updateBurst
        )
    }

    private fun readPlaying(): Boolean = try {
        PlaybackService.isBackgroundPlaying()
    } catch (e: Throwable) {
        Logu.w(TAG, "读取后台播放状态失败：${e.message}")
        false
    }

    private fun readDownloading(): Boolean = try {
        DownloadService.started && DownloadService.activeDownloadsCount > 0
    } catch (e: Throwable) {
        Logu.w(TAG, "读取下载状态失败：${e.message}")
        false
    }

    private fun readDownloadPercent(): Int = try {
        val p = DownloadService.percent
        if (p.isFinite()) p.roundToInt().coerceIn(0, 100) else -1
    } catch (e: Throwable) {
        -1
    }

    private fun readUnread(): Int = try {
        SharedPreferencesUtil.getInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, 0).coerceAtLeast(0)
    } catch (e: Throwable) {
        0
    }

    private fun pollBattery(context: Context, now: Long) {
        if (lastBatteryPoll != 0L && now - lastBatteryPoll < BATTERY_POLL_MS) return
        lastBatteryPoll = now
        try {
            val intent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ) ?: return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level < 0 || scale <= 0) return
            val percent = (level * 100f / scale).roundToInt().coerceIn(0, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            batteryPercent = percent
            charging = isCharging

            val low = percent <= BATTERY_LOW_PERCENT
            if ((low || isCharging) && batteryArmed) {
                batteryBurstUntil = now + BURST_MS
                batteryArmed = false
            } else if (!low && !isCharging) {
                batteryArmed = true
            }
        } catch (e: Throwable) {
            // 部分手表框架的 registerReceiver(null, …) 可能抛；读不到就跳过，不影响其他状态。
            Logu.w(TAG, "读取电量失败：${e.message}")
        }
    }

    private fun pollUpdate(now: Long) {
        if (lastUpdatePoll != 0L && now - lastUpdatePoll < UPDATE_POLL_MS) return
        lastUpdatePoll = now
        val available = try {
            UpdateManager.getCachedConfig()?.let { UpdateManager.hasUpdate(it) } == true
        } catch (e: Throwable) {
            false
        }
        if (available && updateArmed) {
            updateBurstUntil = now + BURST_MS
            updateArmed = false
        } else if (!available) {
            updateArmed = true
        }
    }
}
