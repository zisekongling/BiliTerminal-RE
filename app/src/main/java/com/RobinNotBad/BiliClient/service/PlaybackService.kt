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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
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
 * 2. **进前台之前不许停服务**：见 [stop] 的注释。
 *
 * ── 26.11.17 复核 AOSP 语义（8.1 / 11 两版 `ActiveServices` 一致，别照着旧注释想当然）──
 * - 服务**已经在**前台时又收到一次 `startForegroundService()`：`sendServiceArgsLocked` 里
 *   `if (r.fgRequired && !r.fgWaiting) { if (!r.isForeground) 排计时器 else r.fgRequired = false; }`
 *   ——义务当场作废、根本不排 5 秒计时。所以 [stop] 的 delay 只是防"**新起的**服务还没进来"，
 *   并不需要防"已在台的服务再收一次 start"。
 * - 但"没兑现就把服务销毁"**不是安静作废**：`bringDownServiceLocked` 在 `r.fgRequired` 仍为
 *   true 时会立刻 `sendMessage(SERVICE_FOREGROUND_CRASH_MSG)`，一样崩，只是崩得更快。
 *   所以失败路径（`enterForeground()` 返回 false）同样必须当崩溃路径看待。
 * - 由此剩下两条真实可崩路径：**(a) 主线程被占满/进程被冻结，`onCreate` 迟迟跑不到，5 秒计时到期；
 *   (b) `enterForeground()` 失败**。下面的日志与看门狗就是为 (a) 留证据。
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

        /** `start()` 记下的 `SystemClock.uptimeMillis()`，定位"义务为什么没在 5 秒内兑现"用；0 表示当前没有未结的启动请求 */
        @Volatile
        private var lastStartElapsed = 0L

        /** 从 start() 到 onCreate/onStartCommand 超过这个时长（毫秒）就以 W 级记一笔（release 包里 v/d/i 不落缓冲区） */
        private const val START_DELAY_WARN_MS = 1000L

        /** 看门狗：start() 之后这么久还没进前台，就记一条 W */
        private const val START_WATCHDOG_MS = 3000L

        /**
         * [lastStartElapsed] 的保鲜期：超过这个时长就不再拿它算差值。
         * 服务不通过 [start]（比如通知栏按钮的 PendingIntent 直接 startService）启动时，
         * 那个字段可能还是几小时前留下的，算出来的差值毫无意义。
         */
        private const val START_DELAY_MAX_AGE_MS = 30_000L

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
            val startedAt = SystemClock.uptimeMillis()
            lastStartElapsed = startedAt
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                armStartWatchdog(startedAt)
            } catch (e: Exception) {
                lastStartElapsed = 0L
                // Android 12+ 的 ForegroundServiceStartNotAllowedException、厂商省电策略等
                playerRef = null
                Logu.e("PlaybackService.start", e.message ?: "启动后台播放服务失败")
                CenterThreadPool.runOnUiThread { MsgUtil.showMsg(UiText.of("后台播放启动失败")) }
            }
        }

        /**
         * 看门狗：`start()` 之后 [START_WATCHDOG_MS] 还没进前台，就以 W 级记一条。
         *
         * 这条日志是给「5 秒义务超时」攒证据用的：release 包里只有 w/e/wtf 会进
         * [Logu] 的内存缓冲，而崩溃报告正是把那份缓冲一起带上来的。
         * 它能区分两种情形——有这条 ⇒ 服务确实起晚了（主线程被占满）；
         * 没有这条又崩了 ⇒ 主线程连这个延时任务都没跑到，或走的是失败路径。
         *
         * 到期时若已有更新的启动（[lastStartElapsed] 被改写）或服务已销毁（[onDestroy] 清零），静默跳过。
         */
        private fun armStartWatchdog(startedAt: Long) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (lastStartElapsed == startedAt && !foregrounded) {
                    Logu.w(
                        "PlaybackService.start",
                        "启动后 " + START_WATCHDOG_MS + "ms 仍未进入前台（onCreate 可能被主线程拖住，也可能已收到 stop）"
                    )
                }
            }, START_WATCHDOG_MS)
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
                Logu.d("PlaybackService.stop", "服务尚未进前台，登记 stopPending 交给 onStartCommand 收尾")
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
        logStartDelta("onCreate")
        // 通知管理器与渠道创建都可能因厂商策略在这一步抛异常；它们绝不能挡在 startForeground 前面，
        // 否则 onCreate 半途退出、义务永远没人兑现（按类注释，那是必然崩进程）。
        try {
            notifyManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, UiText.of("后台播放"), NotificationManager.IMPORTANCE_LOW)
                channel.description = UiText.of("后台/熄屏播放时的常驻遥控通知")
                channel.setShowBadge(false)
                channel.setSound(null, null)
                channel.enableVibration(false)
                notifyManager.createNotificationChannel(channel)
            }
        } catch (e: Exception) {
            Logu.e("PlaybackService.onCreate", e.message ?: "初始化通知渠道失败")
        }
        // 义务要在 onCreate 就兑现：onStartCommand 是主线程上另一条消息，可能被拖到 5 秒之外。
        // 失败时**不**在这里 stopSelf：onStartCommand 紧接着还会再试一次，而「没兑现就销毁」按 AOSP
        // bringDownServiceLocked 的语义同样是立即崩，早崩不如把第二次机会留给 onStartCommand。
        if (!enterForeground()) Logu.w("PlaybackService.onCreate", "进入前台失败，交给 onStartCommand 重试一次")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logStartDelta("onStartCommand")
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
        // 清零后，尚未到期的看门狗不会再为这一次启动报警（服务确实起过了）
        lastStartElapsed = 0L
        super.onDestroy()
    }

    private fun player(): PlayerActivity? = playerRef?.get()

    /**
     * 打一条「距 [start] 请求过了多久」的日志。
     *
     * 正常情况（不到 [START_DELAY_WARN_MS]）只走 d 级，release 包不落盘、崩溃报告里也不会多出噪声；
     * 超时才升到 W 级——而 W 才会进 [Logu] 的内存缓冲，随崩溃报告一起上传。
     * 「5 秒义务」超时的现场证据就靠这几行。
     */
    private fun logStartDelta(stage: String) {
        val startedAt = lastStartElapsed
        if (startedAt <= 0L) {
            Logu.d("PlaybackService." + stage, "无未结的 start() 请求")
            return
        }
        val cost = SystemClock.uptimeMillis() - startedAt
        if (cost > START_DELAY_MAX_AGE_MS) {
            // 记录过期：这次启动不是 start() 发起的（通知栏按钮 / 系统重启服务等）
            Logu.d("PlaybackService." + stage, "无未结的 start() 请求（记录已过期）")
            return
        }
        if (cost >= START_DELAY_WARN_MS) {
            Logu.w("PlaybackService." + stage, "距 start() 请求已过 " + cost + "ms")
        } else {
            Logu.d("PlaybackService." + stage, "距 start() 请求 " + cost + "ms")
        }
    }

    /**
     * 兑现"5 秒义务"：把通知挂上去、把服务推进前台。
     *
     * @return 是否成功进入前台。失败同样是一条崩溃路径：按 AOSP `bringDownServiceLocked`，
     *         带着未兑现的义务销毁服务会**立刻**抛 `SERVICE_FOREGROUND_CRASH_MSG`（不是安静作废）。
     *         所以现在先让 `onStartCommand` 再试一次，两处都失败才 `stopSelf()` 收尾。
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
            logStartDelta("enterForeground")
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