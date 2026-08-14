package com.RobinNotBad.BiliClient.service

import android.annotation.SuppressLint
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
import com.RobinNotBad.BiliClient.activity.video.local.DownloadListActivity
import com.RobinNotBad.BiliClient.util.Aria2Util
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class SpeedDownloadService : Service() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "biliterminal_speed_download"
        private const val FOREGROUND_ID = 1028

        @JvmStatic var active: Boolean = false
        @JvmStatic var speedStr: String = ""
        @JvmStatic var taskCount: Int = 0
        @JvmStatic var overallProgress: Float = 0f

        private var instance: SpeedDownloadService? = null

        @JvmStatic
        fun startDownload(context: Context, url: String, fileName: String, dir: String) {
            active = true
            val intent = Intent(context, SpeedDownloadService::class.java)
            intent.putExtra("action", "download")
            intent.putExtra("url", url)
            intent.putExtra("fileName", fileName)
            intent.putExtra("dir", dir)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        @JvmStatic
        fun getInstance(): SpeedDownloadService? {
            return instance
        }
    }

    class DownloadTask(
        var id: String,
        var url: String,
        var fileName: String,
        var dir: String
    ) {
        var totalSize: Long = 0
        var downloadedSize: Long = 0
        var progress: Int = 0
        var state: String = "waiting"
        var errorMsg: String = ""
    }

    private var notificationManager: NotificationManager? = null
    private var isDestroyed: Boolean = false

    private val totalBytesDownloaded = AtomicLong(0)
    private var lastSampleBytes: Long = 0
    private var lastSampleTime: Long = 0
    private val speedLock = Any()

    private val tasks = ConcurrentHashMap<String, DownloadTask>()
    private val taskQueue = CopyOnWriteArrayList<String>()
    private val running = AtomicBoolean(false)
    private val activeCount = AtomicInteger(0)
    private val httpClient: OkHttpClient

    private val SEGMENT_SIZE: Long = 2 * 1024 * 1024

    init {
        httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        isDestroyed = false
        active = true
        speedStr = ""
        taskCount = 0
        overallProgress = 0f
        lastSampleBytes = 0
        lastSampleTime = System.currentTimeMillis()

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "高速下载",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager!!.createNotificationChannel(channel)

        CenterThreadPool.run { speedMonitorLoop() }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_ID,
                buildNotification("高速下载服务运行中", 0).build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(FOREGROUND_ID, buildNotification("高速下载服务运行中", 0).build())
        }

        if (intent != null && "download" == intent.getStringExtra("action")) {
            val url = intent.getStringExtra("url") ?: return START_STICKY
            val fileName = intent.getStringExtra("fileName") ?: return START_STICKY
            val dir = intent.getStringExtra("dir") ?: return START_STICKY
            enqueue(url, fileName, dir)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isDestroyed = true
        running.set(false)
        active = false
        speedStr = ""
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    fun getTask(id: String): DownloadTask? {
        return tasks[id]
    }

    fun getTasks(): Map<String, DownloadTask> {
        return LinkedHashMap(tasks)
    }

    fun getActiveCount(): Int {
        return activeCount.get()
    }

    fun getWaitingCount(): Int {
        var count = 0
        for (id in taskQueue) {
            val task = tasks[id]
            if (task != null && "waiting" == task.state) count++
        }
        return count
    }

    fun getTotalCount(): Int {
        return tasks.size
    }

    private fun speedMonitorLoop() {
        while (!isDestroyed) {
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                break
            }

            synchronized(speedLock) {
                val now = System.currentTimeMillis()
                val currentBytes = totalBytesDownloaded.get()
                val elapsed = (now - lastSampleTime) / 1000.0
                if (elapsed > 0.5 && lastSampleBytes > 0) {
                    val speed = (currentBytes - lastSampleBytes) / elapsed
                    speedStr = if (speed >= 1048576) {
                        String.format(java.util.Locale.CHINA, "%.1f MB/s", speed / 1048576.0)
                    } else if (speed >= 1024) {
                        String.format(java.util.Locale.CHINA, "%.1f KB/s", speed / 1024.0)
                    } else {
                        String.format(java.util.Locale.CHINA, "%.0f B/s", speed)
                    }
                }
                lastSampleBytes = currentBytes
                lastSampleTime = now
            }

            taskCount = tasks.size

            var totalDone: Float = 0f
            var totalAll: Float = 0f
            for (t in tasks.values) {
                totalDone += t.downloadedSize
                totalAll += t.totalSize
            }
            if (totalAll > 0) {
                overallProgress = totalDone / totalAll
            }

            val notifText = if (speedStr.isEmpty()) "高速下载中" else speedStr
            notificationManager!!.notify(FOREGROUND_ID,
                buildNotification(notifText + " (" + getActiveCount() + "活动/" + taskCount + "总计)", 0).build())
        }
    }

    fun enqueue(url: String, fileName: String, dir: String) {
        val id = "bili_" + System.currentTimeMillis()
        val task = DownloadTask(id, url, fileName, dir)
        tasks[id] = task
        taskQueue.add(id)
        processQueue()
    }

    private fun processQueue() {
        if (!running.getAndSet(true)) {
            CenterThreadPool.run { dispatchLoop() }
        }
    }

    private fun dispatchLoop() {
        while (!isDestroyed) {
            while (activeCount.get() < Aria2Util.getMaxConcurrent() && !taskQueue.isEmpty()) {
                val taskId = taskQueue.removeAt(0)
                val task = tasks[taskId]
                if (task != null) {
                    activeCount.incrementAndGet()
                    CenterThreadPool.run { executeDownload(task) }
                }
            }

            if (taskQueue.isEmpty() && activeCount.get() == 0) {
                running.set(false)
                stopForeground(true)
                stopSelf()
                return
            }

            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    private fun executeDownload(task: DownloadTask) {
        task.state = "active"

        try {
            val dir = File(task.dir)
            if (!dir.exists()) dir.mkdirs()
            val outputFile = File(dir, task.fileName)

            task.totalSize = getContentLength(task.url)
            task.state = "downloading"

            if (task.totalSize <= 0 || task.totalSize <= SEGMENT_SIZE) {
                downloadSingle(task, outputFile)
            } else {
                var segments = Math.min(Aria2Util.getSplit().toLong(), task.totalSize / SEGMENT_SIZE).toInt()
                if (segments < 1) segments = 1
                downloadSegmented(task, outputFile, segments)
            }

            if (task.downloadedSize >= task.totalSize && task.totalSize > 0) {
                task.state = "complete"
                task.progress = 100
            }
        } catch (e: Exception) {
            task.state = "error"
            task.errorMsg = e.message ?: ""
        } finally {
            activeCount.decrementAndGet()
        }
    }

    @Throws(IOException::class)
    private fun getContentLength(url: String): Long {
        val request = Request.Builder().url(url).head().build()
        httpClient.newCall(request).execute().use { response ->
            val length = response.header("Content-Length")
            if (length != null) return length.toLong()

            if (response.header("Accept-Ranges", "")?.contains("bytes") == true) {
                return downloadChunk(url, 0, SEGMENT_SIZE - 1, null) + 1
            }

            return 0
        }
    }

    @Throws(IOException::class)
    private fun downloadSingle(task: DownloadTask, outputFile: File) {
        val request = Request.Builder().url(task.url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP " + response.code)
            response.body!!.byteStream().use { inputStream ->
                FileOutputStream(outputFile).use { fos ->
                    val buffer = ByteArray(8192)
                    var total: Long = 0
                    var read: Int
                    while ((inputStream.read(buffer).also { read = it }) != -1) {
                        fos.write(buffer, 0, read)
                        total += read
                        totalBytesDownloaded.addAndGet(read.toLong())
                        task.downloadedSize = total
                        if (task.totalSize > 0)
                            task.progress = (total * 100 / task.totalSize).toInt()
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun downloadSegmented(task: DownloadTask, outputFile: File, segments: Int) {
        val totalSize = task.totalSize
        val segmentLen = totalSize / segments

        FileOutputStream(outputFile).use { fos ->
            fos.channel.position(0)
            fos.channel.truncate(totalSize)
        }

        val threads = ArrayList<Thread>()
        val totalDownloaded = AtomicLong(0)
        val completedSegments = AtomicInteger(0)
        val segmentProgress = ConcurrentHashMap<Int, Long>()

        for (i in 0 until segments) {
            val seg = i
            val start = i * segmentLen
            val end = if (i == segments - 1) totalSize - 1 else start + segmentLen - 1

            val thread = Thread {
                try {
                    val bytesRead = downloadChunk(task.url, start, end, outputFile)
                    segmentProgress[seg] = bytesRead
                    totalBytesDownloaded.addAndGet(bytesRead)

                    var total: Long = 0
                    for (v in segmentProgress.values) total += v
                    totalDownloaded.set(total)

                    completedSegments.incrementAndGet()
                } catch (e: IOException) {
                    task.errorMsg = "分段下载失败: " + e.message
                }
            }
            threads.add(thread)
            thread.start()
        }

        for (thread in threads) {
            try {
                thread.join(60000)
            } catch (ignored: InterruptedException) {
            }
        }

        task.downloadedSize = totalDownloaded.get()
        if (task.totalSize > 0)
            task.progress = (task.downloadedSize * 100 / task.totalSize).toInt()

        for (thread in threads) {
            if (thread.isAlive) thread.interrupt()
        }
    }

    @Throws(IOException::class)
    private fun downloadChunk(url: String, start: Long, end: Long, outputFile: File?): Long {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$start-$end")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP " + response.code)
            response.body!!.byteStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var total: Long = 0
                var read: Int

                if (outputFile != null) {
                    FileOutputStream(outputFile, true).use { fos ->
                        fos.channel.position(start)
                        while ((inputStream.read(buffer).also { read = it }) != -1) {
                            fos.write(buffer, 0, read)
                            total += read
                        }
                    }
                } else {
                    while ((inputStream.read(buffer).also { read = it }) != -1) {
                        total += read
                    }
                }
                return total
            }
        }
    }

    @SuppressLint("LaunchActivityFromNotification")
    private fun buildNotification(text: String, progress: Int): NotificationCompat.Builder {
        val intent = Intent(this, DownloadListActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("哔哩终端 - 高速下载")
            .setContentText(text)
            .setSmallIcon(R.mipmap.akari)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, progress < 0)
    }
}