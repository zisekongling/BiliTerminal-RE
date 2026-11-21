package com.RobinNotBad.BiliClient.service.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.video.local.DownloadListActivity
import com.RobinNotBad.BiliClient.service.DownloadService
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import java.util.Timer
import java.util.TimerTask

/**
 * 下载服务的通知栏逻辑：通道、两个 Builder、进度定时刷新、退出与完成通知。
 *
 * 刻意写成**普通 class 并持有 Service 引用**，而不是 object。
 * object 是进程级单例，它会跨批次抓住第一个 Service 的 Context 与 Timer：
 * 服务销毁后定时器还在跑（线程泄漏），并且继续往通知栏写一个已经死掉的服务的内容。
 * 本类由 DownloadService 在 onCreate 里 new 出来、放在 Service 的实例字段上，随 Service 一起回收。
 *
 * 通知相关的可变状态（statusBuilder / completionBuilder / notifyManager / notifyTimer）
 * **仍然声明在 DownloadService 实例上**，这里只读写、不另存一份，
 * 避免出现"两处真相"（谁是当前 Builder 取决于谁先写）。
 */
internal class DownloadNotifier(private val service: DownloadService) {

    /**
     * 创建通知通道与两个 Builder（原 DownloadService.onCreate 的通知部分）。
     * 只在 Service.onCreate 里调用一次。
     */
    fun init() {
        service.notifyManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(service.NOTIFICATION_CHANNEL_ID, service.getString(R.string.channel_download),
                NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = service.getString(R.string.channel_download)
            channel.setSound(null, null)
            channel.enableVibration(false)

            service.notifyManager.createNotificationChannel(channel)
        }

        val intent = Intent(service, DownloadListActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(service, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        service.statusBuilder = NotificationCompat.Builder(service, service.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.icon)
            .setContentTitle(service.getString(R.string.text_download_video))
            .setProgress(100, 0, false)
            .setContentIntent(pendingIntent)
            .setSound(null)
            .setVibrate(null)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        service.completionBuilder = NotificationCompat.Builder(service, service.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.icon)
            .setContentTitle(service.getString(R.string.text_update_download_complete))
            .setContentIntent(pendingIntent)
            .setOngoing(false)
            .setSound(null)
            .setVibrate(null)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    }

    /**
     * 启动每 1 秒刷新一次的通知栏进度。
     * 定时器本身挂在 Service 的 notifyTimer 上，由 Service.onDestroy 负责 cancel。
     */
    fun startNotifyProgress() {
        // 幂等（审计 M11-d）：重复进入时不能无条件新建 Timer，否则旧 Timer 既没 cancel
        // 又丢掉了引用，泄漏一个线程并让它每秒继续往通知栏写。
        if (service.notifyTimer != null) return
        service.notifyTimer = Timer()
        service.notifyTimer!!.schedule(object : TimerTask() {
            override fun run() {
                try {
                    if (DownloadService.section == null || service.notifyTimer == null)
                        return

                    val overall = DownloadService.computeOverallProgress(
                        DownloadRepository.getAll() ?: emptyList()
                    )
                    service.statusBuilder.setContentText(
                        service.getString(R.string.text_download_total_progress, (overall * 100).toInt(), DownloadService.section?.name_short ?: service.getString(R.string.text_download_in_progress))
                    )
                    service.statusBuilder.setProgress(100, (overall * 100).toInt(), false)
                    service.notifyManager.notify(service.FOREGROUND_ID, service.statusBuilder.build())
                } catch (e: Throwable) {
                    // TimerTask 抛出的未捕获异常会永久终止整个 Timer，进度通知从此静默失效且无任何提示。
                    // 这里必须吞掉异常让 Timer 继续跑，但要留下日志便于定位。
                    Logu.e("DownloadService", "刷新下载通知失败：${e.message}")
                }
            }
        }, 500, 1000)
    }

    /** 批次结束（无论成败）时的通知：撤掉前台通知，改用固定 id 2 的"下载结束"。 */
    fun notifyExit(content: String) {
        MsgUtil.showMsg(content)
        service.notifyManager.cancel(service.FOREGROUND_ID)
        service.completionBuilder.setContentTitle(service.getString(R.string.text_download_end))
        service.completionBuilder.setContentText(content)
        service.completionBuilder.setProgress(0, 0, false)
        service.notifyManager.notify(2, service.completionBuilder.build())
    }

    /** 单个任务完成/失败时的通知，id 由任务 id 派生（`id % 100 + 100`）。 */
    fun notifyCompletion(content: String, id: Int) {
        MsgUtil.showMsg(content)
        service.completionBuilder.setContentText(content)
        service.notifyManager.notify(id % 100 + 100, service.completionBuilder.build())
    }
}
