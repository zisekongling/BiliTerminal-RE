package com.RobinNotBad.BiliClient.service

import com.RobinNotBad.BiliClient.util.UiText

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.player.PlayerActivity
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

/**
 * 后台/熄屏播放的前台服务与通知栏遥控。
 *
 * 设计上刻意保持"服务是壳、播放器是主"：
 * - 播放器实例（IjkMediaPlayer、弹幕、全部状态）仍然只归 [PlayerActivity] 持有，
 *   本服务不接管、也不重建任何播放逻辑，只在进程被保活期间每秒刷一次通知。
 * - 服务通过 [WeakReference] 反查播放页状态，反过来播放页通过本服务的公开方法
 *   转发"暂停/播放/关闭"指令，全程在主线程执行。
 *
 * 之所以必须用前台服务而不是普通后台线程：Android 8 起后台进程随时可能被回收，
 * 而本服务的目标恰恰是"熄屏后不被回收"。
 *
 * ── "5 秒义务"（26.10.11 修线上崩溃后补充的硬约束，改本文件前必读）────────────
 * `Context.startForegroundService()` 一旦调用，系统就给这次启动记一笔账：
 * 服务必须在 **5 秒内** 调 `startForeground()`，否则系统直接往 app 主线程抛
 * `RemoteServiceException: Context.startForegroundService() did not then call
 * Service.startForeground()`，**整个进程崩掉**（26.10.10 线上实际发生）。
 * 兑现义务有两条硬性要求，两条都在下面代码里，别改回去：
 * 1. **越早越好**：`onCreate()` 里就进前台（不能只在 `onStartCommand()` 里）。
 *    `onCreate` 与 `onStartCommand` 是主线程上两条**独立的消息**，主线程忙（本机
 *    3906MB/2591MB 的手表上创建服务本来就慢）时两者之间可以隔很久。
 * 2. **进前台之前不许停服务**：见 [stop] 的注释——`stopService()` 会把还没兑现的
 *    义务连人带账一起作废，5 秒后照样崩。
 */
class PlaybackService : Service() {

    companion object {
        const val ACTION_TOGGLE = "com.RobinNotBad.BiliClient.action.PLAYBACK_TOGGLE"
        const val ACTION_STOP = "com.RobinNotBad.BiliClient.action.PLAYBACK_STOP"

        private const val CHANNEL_ID = "playback_channel"
        private const val NOTIFICATION_ID = 1028 // 1027 被 DownloadService 占用，勿改
        private const val REFRESH_INTERVAL_MS = 1000L

        /** 只持有弱引用：播放页被销毁后服务必须能自己结束，不能把 Activity 泄漏到进程级 */
        @Volatile
        private var playerRef: WeakReference<PlayerActivity>? = null

        /**
         * 服务是否已经成功进入前台，也就是 `startForegroundService()` 的"5 秒义务"是否已兑现。
         * [stop] 靠它决定"现在能不能直接 stopService"。
         *
         * 进程级单实例：`foregrounded == false` 就意味着**当前没有任何前台的服务实例**
         * （服务活着 ⇒ 一定走过 `enterForeground()`），[stop] 的延迟语义建立在这个不变式上。
         */
        @Volatile
        private var foregrounded = false

        /**
         * 在服务进入前台**之前**收到的 [stop] 请求。
         *
         * 此时绝不能 `stopService()`（会把义务作废、5 秒后崩进程，见 [stop]），
         * 于是记一笔，交给 `onStartCommand` 在进场之后立刻消费掉（`stopSelf()`）。
         * 每轮 [start] 都会复位它，所以它永远不会误伤一次新的启动。
         */
        @Volatile
        private var stopPending = false

        /**
         * 启动后台播放保活。调用点只在 [PlayerActivity.onPause]，
         * 此时应用仍处于"有可见界面"的豁免范围内，不会被 Android 12 的后台启动限制拦截。
         */
        @JvmStatic
        fun start(context: Context, activity: PlayerActivity) {
            playerRef = WeakReference(activity)
            // 新一轮启动作废上一次"进场前收到的 stop"：否则这一轮刚进场就会被自己人 stopSelf 掉
            stopPending = false
            val intent = Intent(context, PlaybackService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Android 12+ 的 ForegroundServiceStartNotAllowedException、厂商省电策略等
                playerRef = null
                Logu.e("PlaybackService.start", e.message ?: "启动后台播放服务失败")
                CenterThreadPool.runOnUiThread { MsgUtil.showMsg(UiText.of("后台播放启动失败")) }
            }
        }

        /**
         * 回到前台或退出播放页时调用，通知栏遥控不应残留。
         *
         * ⚠ 26.10.10 线上崩溃（`RemoteServiceException: Context.startForegroundService()
         * did not then call Service.startForeground()`）的根因就在这几十行里：
         * `start()`（`onPause`）与本方法（`onResume` / `onDestroy`）本来就紧挨着，
         * 用户在起服务后几秒内回到前台、或者页面被快速销毁，都会走到这里。
         * 而"创建服务"是异步的——低内存手表上可能几百毫秒到数秒才轮到 `onCreate`。
         * 若在服务还没进前台时直接 `stopService()`，这次 `startForegroundService()`
         * 留下的义务就**永远没人兑现**，5 秒后系统判定"服务没进前台"，
         * 直接崩掉整个进程（崩的是主线程，用户看到的是应用闪退）。
         *
         * 所以：**没进前台就不发停止指令**，只记一笔 [stopPending]，让服务自己在
         * `onStartCommand` 里先 `startForeground()`（义务兑现）再 `stopSelf()`。
         * 结果一样干净——服务不会残留、通知不会残留——但不会崩。
         */
        @JvmStatic
        fun stop(context: Context) {
            playerRef = null
            if (!foregrounded) {
                stopPending = true
                return
            }
            try {
                context.stopService(Intent(context, PlaybackService::class.java))
            } catch (e: Exception) {
                Logu.e("PlaybackService.stop", e.message ?: "停止后台播放服务失败")
            }
        }

        /**
         * 是否正在后台播放（顶栏灵动岛用）。
         *
         * 只读内存字段：服务没进前台、播放页已销毁、或播放器实际没在播，都返回 false。
         * 不做 JNI 调用（[PlayerActivity.serviceIsPlaying] 内部只读 `@Volatile` 字段）。
         */
        @JvmStatic
        fun isBackgroundPlaying(): Boolean {
            val player = playerRef?.get() ?: return false
            return foregrounded && !player.serviceGone() && player.serviceIsPlaying()
        }
    }

    private lateinit var notifyManager: NotificationManager
    private var timer: Timer? = null

    override fun onCreate() {
        super.onCreate()
        notifyManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, UiText.of("后台播放"), NotificationManager.IMPORTANCE_LOW)
            channel.description = UiText.of("后台/熄屏播放时的常驻遥控通知")
            channel.setShowBadge(false)
            channel.setSound(null, null)
            channel.enableVibration(false)
            notifyManager.createNotificationChannel(channel)
        }
        // 义务要在 onCreate 就兑现：onStartCommand 是主线程上另一条消息，可能被拖到 5 秒之外。
        // 这里失败也没有更好的办法——服务连前台都进不去，只能自尽让义务随销毁一起作废。
        if (!enterForeground()) stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // onCreate 已经兑现过一次；但每次 startForegroundService 都会重新记一笔账，
        // 而 onCreate 只跑一次，所以这里必须再兑现一次（重复调用 startForeground 是无害的）。
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 进场前就收到过 stop()：义务已经兑现，现在可以安全收尾了。
        // 绝不能提前到 enterForeground() 之前 stopSelf——那正是崩溃的成因。
        if (stopPending) {
            stopPending = false
            stopSelf()
            return START_NOT_STICKY
        }

        // 定时器必须等进了前台再开：它 1 秒后就会尝试 stopSelf()，
        // 若在 onCreate 里提前开、又碰上主线程被占满，它会抢在 onStartCommand 之前把服务停掉，
        // 义务同样兑现不了。
        startUpdateTimer()

        when (intent?.action) {
            ACTION_TOGGLE -> player()?.serviceTogglePlay()
            ACTION_STOP -> {
                player()?.serviceStopPlayback()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 从最近任务里划掉时进程可能很快被杀，先补一次进度上报
        player()?.serviceReportNow()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        timer?.cancel()
        timer = null
        playerRef = null
        // 复位进程级状态：服务已销毁，下一次 start() 从零开始
        foregrounded = false
        stopPending = false
        super.onDestroy()
    }

    private fun player(): PlayerActivity? = playerRef?.get()

    /**
     * 兑现"5 秒义务"：把通知挂上去、把服务推进前台。
     *
     * @return 是否成功进入前台。返回 false 时调用方必须 `stopSelf()`——
     *         服务被销毁，那笔义务才会随 ServiceRecord 一起作废，否则 5 秒后照样崩进程。
     */
    private fun enterForeground(): Boolean {
        val notification = try {
            buildNotification()
        } catch (e: Exception) {
            // 组装通知（读播放页状态、查 launchIntent）失败也绝不能挡在 startForeground 前面：
            // 宁可先挂一条兜底通知把义务兑现掉，也不能让系统 5 秒后崩掉整个进程。
            Logu.e("PlaybackService.buildNotification", e.message ?: "组装播放通知失败")
            buildFallbackNotification()
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foregrounded = true
            true
        } catch (e: Exception) {
            // Android 14+ 缺 FOREGROUND_SERVICE_MEDIA_PLAYBACK 权限/清单类型、厂商策略拦截等
            Logu.e("PlaybackService.startForeground", e.message ?: "进入前台失败")
            false
        }
    }

    private fun startUpdateTimer() {
        if (timer != null) return
        timer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    try {
                        val a = player()
                        if (a == null || a.isFinishing || a.serviceGone()) {
                            // 播放页没了，服务自己收尾（此时早已在前台，stopSelf 是安全的）
                            stopSelf()
                            cancel()
                        } else {
                            notifyManager.notify(NOTIFICATION_ID, buildNotification())
                        }
                    } catch (e: Exception) {
                        // TimerTask 抛未捕获异常会永久终止整个 Timer，通知从此静默失效
                        Logu.e("PlaybackService.timer", e.message ?: "刷新播放通知失败")
                    }
                }
            }, REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS)
        }
    }

    private fun buildNotification(): Notification {
        val a = player()
        val playing = a?.serviceIsPlaying() == true
        val position = a?.servicePositionMs() ?: 0
        val duration = a?.serviceDurationMs() ?: 0
        val title = a?.serviceTitle()?.takeIf { it.isNotBlank() } ?: UiText.of("RE：哔哩终端正在播放")

        val toggleIntent = PendingIntent.getService(
            this, 0,
            Intent(this, PlaybackService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // 用 FLAG_ACTIVITY_REORDER_TO_FRONT 复用已有任务，尽量不新建播放页实例；
        // 回到前台后 onResume 会自行停掉本服务。
        val contentIntent = PendingIntent.getActivity(
            this, 2,
            (packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(this, PlayerActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.icon)
            .setContentTitle(title)
            .setContentText("${formatTime(position)} / ${formatTime(duration)}")
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply { if (duration > 0) setProgress(duration, position.coerceIn(0, duration), false) }
            .clearActions()
            .addAction(
                if (playing) R.drawable.btn_player_pause else R.drawable.btn_player_play,
                if (playing) UiText.of("暂停") else UiText.of("播放"), toggleIntent
            )
            .addAction(0, UiText.of("关闭"), stopIntent)
            .build()
    }

    /**
     * 兜底通知：只保证"能挂上去把 5 秒义务兑现"。
     * 不含 `PendingIntent`，因为组装通知时崩的往往就是查 `launchIntent` 那一步。
     */
    private fun buildFallbackNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.icon)
            .setContentTitle(UiText.of("RE：哔哩终端正在播放"))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun formatTime(ms: Int): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%d:%02d", totalSec / 60, totalSec % 60)
    }
}