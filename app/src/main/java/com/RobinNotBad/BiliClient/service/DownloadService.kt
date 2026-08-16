package com.RobinNotBad.BiliClient.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.activity.video.local.DownloadListActivity
import com.RobinNotBad.BiliClient.activity.video.local.LocalListActivity
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.helper.sql.DownloadSqlHelper
import com.RobinNotBad.BiliClient.model.DownloadSection
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.SubtitleLink
import com.RobinNotBad.BiliClient.util.Aria2Util
import com.RobinNotBad.BiliClient.util.VideoMetaManager
import com.RobinNotBad.BiliClient.model.VideoMeta
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MediaMerger
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import okhttp3.Response
import okio.BufferedSink
import okio.Sink
import okio.buffer
import okio.sink
import org.json.JSONException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Timer
import java.util.TimerTask
import java.util.zip.Inflater

class DownloadService : Service() {

    companion object {
        @JvmStatic var started: Boolean = false
        @JvmStatic var exitCode: Int = 0
        @JvmStatic var percent: Float = -1f
        @JvmStatic var state: String? = null
        @JvmStatic var section: DownloadSection? = null
        @JvmStatic var speedStr: String = ""
        @JvmStatic var isSpeedMode: Boolean = false
        private var firstDown: Long = -1

        // 本次下载批次的总体统计（总进度条、通知栏使用）
        @JvmStatic val batchStats = DownloadBatchStats()

        // 全局已下载字节数（所有并行下载累计），用于聚合速度采样
        private val totalBytesDownloaded = java.util.concurrent.atomic.AtomicLong(0)

        @JvmStatic var activeDownloadsCount: Int = 0
            private set

        // 聚合速度采样（单线程调用）
        private val speedSampler = SpeedSampler()
        private val speedLock = Any()

        // 下载进度追踪：key=section.id, value=进度信息（含阶段进度与已下载/总字节数）
        data class DownloadProgressInfo(
            val progress: Float,
            val state: String,
            val downloadedBytes: Long = 0,
            val totalBytes: Long = 0
        )

        private val downloadProgressMap =
            java.util.concurrent.ConcurrentHashMap<Long, DownloadProgressInfo>()

        @JvmStatic
        fun getDownloadProgress(id: Long): DownloadProgressInfo? {
            return downloadProgressMap[id]
        }

        @JvmStatic
        fun getDownloadProgressMap(): java.util.concurrent.ConcurrentHashMap<Long, DownloadProgressInfo> {
            return downloadProgressMap
        }

        @JvmStatic
        fun setDownloadProgress(id: Long, progress: Float, state: String) {
            downloadProgressMap[id] = DownloadProgressInfo(progress, state)
        }

        @JvmStatic
        fun setDownloadProgress(id: Long, progress: Float, state: String,
                                downloadedBytes: Long, totalBytes: Long) {
            downloadProgressMap[id] =
                DownloadProgressInfo(progress, state, downloadedBytes, totalBytes)
        }

        @JvmStatic
        fun removeDownloadProgress(id: Long) {
            downloadProgressMap.remove(id)
        }

        @JvmStatic
        fun getDownloadedBytes(): Long = totalBytesDownloaded.get()

        @JvmStatic
        fun addDownloadedBytes(bytes: Long) {
            totalBytesDownloaded.addAndGet(bytes)
        }

        @JvmStatic
        fun resetDownloadedBytes() {
            totalBytesDownloaded.set(0)
        }

        /**
         * 根据当前数据库中的下载项与进度映射，计算本次批次的总体进度。
         */
        @JvmStatic
        fun computeOverallProgress(sections: List<DownloadSection>): Float {
            var activeProgressSum = 0f
            var activeCount = 0
            var waitingCount = 0
            for (s in sections) {
                val info = downloadProgressMap[s.id]
                if (info != null) {
                    activeProgressSum += info.progress.coerceIn(0f, 1f)
                    activeCount++
                } else if (s.state == "none") {
                    waitingCount++
                }
            }
            return batchStats.overallProgress(activeProgressSum, activeCount, waitingCount)
        }

        /** 当前仍在下载中的项目剩余字节数合计（用于预估剩余时间） */
        @JvmStatic
        fun getActiveRemainingBytes(sections: List<DownloadSection>): Long {
            var remaining = 0L
            for (s in sections) {
                val info = downloadProgressMap[s.id]
                if (info != null && info.totalBytes > 0) {
                    remaining += (info.totalBytes - info.downloadedBytes).coerceAtLeast(0)
                }
            }
            return remaining
        }

        private const val NORMAL = 0
        private const val ERR_NETWORK = -1
        private const val ERR_JSON = -2
        private const val ERR_FILE = -3
        private const val ERR_DATABASE = -4
        private const val ERR_UNKNOWN = -7

        @JvmStatic
        fun decompress(data: ByteArray): ByteArray {
            var output: ByteArray
            val decompresser = Inflater(true)
            decompresser.reset()
            decompresser.setInput(data)
            val o = ByteArrayOutputStream(data.size)
            try {
                val buf = ByteArray(2048)
                while (!decompresser.finished()) {
                    val i = decompresser.inflate(buf)
                    o.write(buf, 0, i)
                }
                output = o.toByteArray()
            } catch (e: Exception) {
                output = data
                e.printStackTrace()
            } finally {
                try {
                    o.close()
                    decompresser.end()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return output
        }

        @JvmStatic
        fun getFirst(): DownloadSection? {
            var cursor: Cursor? = null
            var database: SQLiteDatabase? = null
            return try {
                val helper = DownloadSqlHelper(BiliTerminal.context)
                database = helper.readableDatabase

                if (firstDown >= 0)
                    cursor = database.rawQuery("select * from download where id=? limit 1",
                        arrayOf(firstDown.toString()))
                if (cursor == null)
                    cursor = database.rawQuery("select * from download where state=? limit 1", arrayOf("none"))

                firstDown = -1

                if (cursor == null || cursor.count == 0)
                    return null

                cursor.moveToFirst()
                DownloadSection(cursor)
            } catch (e: Exception) {
                MsgUtil.err(e)
                null
            } finally {
                cursor?.close()
                database?.close()
            }
        }

        @JvmStatic
        fun getAll(): ArrayList<DownloadSection>? {
            var cursor: Cursor? = null
            var database: SQLiteDatabase? = null
            return try {
                val helper = DownloadSqlHelper(BiliTerminal.context)
                database = helper.readableDatabase
                cursor = database.rawQuery("select * from download", null)
                if (cursor == null || cursor.count == 0)
                    return null

                val list = ArrayList<DownloadSection>()
                while (cursor.moveToNext()) {
                    list.add(DownloadSection(cursor))
                }
                list
            } catch (e: Exception) {
                MsgUtil.err(e)
                ArrayList()
            } finally {
                cursor?.close()
                database?.close()
            }
        }

        @JvmStatic
        fun deleteSection(id: Long) {
            var database: SQLiteDatabase? = null
            try {
                val helper = DownloadSqlHelper(BiliTerminal.context)
                database = helper.writableDatabase
                database.execSQL("delete from download where id=?", arrayOf<Any>(id))
                database.close()
            } catch (e: Exception) {
                MsgUtil.err(e)
            } finally {
                database?.close()
            }
        }

        @JvmStatic
        fun clear() {
            var database: SQLiteDatabase? = null
            try {
                val helper = DownloadSqlHelper(BiliTerminal.context)
                database = helper.writableDatabase
                database.execSQL("delete from download", arrayOf<Any>())
                database.close()
            } catch (e: Exception) {
                MsgUtil.err(e)
            } finally {
                database?.close()
            }
        }

        @JvmStatic
        fun setState(id: Long, state: String) {
            var database: SQLiteDatabase? = null
            try {
                val helper = DownloadSqlHelper(BiliTerminal.context)
                database = helper.writableDatabase
                database.execSQL("update download set state=? where id=?", arrayOf<Any>(state, id))
                database.close()
            } catch (e: Exception) {
                MsgUtil.err(e)
            } finally {
                database?.close()
            }
        }

        /**
         * 保存视频元数据到缓存文件夹
         */
        private fun saveVideoMeta(folder: File, title: String, aid: Long, cid: Long, qn: Int, downloadType: String) {
            try {
                val meta = VideoMeta()
                meta.title = title
                meta.aid = aid
                meta.cid = cid
                meta.qn = qn
                meta.downloadType = downloadType
                VideoMetaManager.saveMeta(folder, meta)
            } catch (e: Exception) {
                Logu.e("saveVideoMeta", "保存视频元数据失败: ${e.message}")
            }
        }

        /**
         * 更新视频元数据中的画质列表（下载完成后回调）
         */
        private fun updateVideoMetaQualityLists(folder: File, qnStrList: Array<String>?, qnValueList: IntArray?) {
            try {
                if (qnStrList == null && qnValueList == null) return
                val meta = VideoMetaManager.readMeta(folder)
                meta.qnStrList = qnStrList
                meta.qnValueList = qnValueList
                VideoMetaManager.saveMeta(folder, meta)
            } catch (e: Exception) {
                Logu.e("updateVideoMeta", "更新画质列表失败: ${e.message}")
            }
        }

        /**
         * 重新下载视频（切换分辨率），删除旧下载记录后启动新下载
         */
        @JvmStatic
        fun startReDownload(title: String, aid: Long, cid: Long, cover: String, newQn: Int) {
            CenterThreadPool.run {
                var database: SQLiteDatabase? = null
                try {
                    val helper = DownloadSqlHelper(BiliTerminal.context)
                    database = helper.writableDatabase

                    // 删除旧的下载记录（避免重复检查拦截）
                    database.execSQL("delete from download where aid=? and cid=?",
                        arrayOf<Any>(aid.toString(), cid.toString()))

                    database.close()
                    database = null

                    // 删除旧视频文件，准备重新下载
                    val videoDir = FileUtil.getVideoDownloadPath(title, null)
                    val oldVideoFile = File(videoDir, "video.mp4")
                    if (oldVideoFile.exists()) oldVideoFile.delete()
                    val oldAudioFile = File(videoDir, "audio.m4a")
                    if (oldAudioFile.exists()) oldAudioFile.delete()
                    // 删除旧的.DOWNLOADING标记（如果存在）
                    val downloadingMark = File(videoDir, ".DOWNLOADING")
                    if (downloadingMark.exists()) downloadingMark.delete()

                    // 更新画质元数据
                    VideoMetaManager.updateQuality(title, newQn)

                    // 启动新的下载（封面已存在，传空字符串跳过封面下载）
                    startDownload(title, aid, cid, "", newQn, "video", "")
                } catch (e: Exception) {
                    MsgUtil.err(e)
                } finally {
                    database?.close()
                }
            }
        }

        @JvmStatic
        fun startDownload(title: String, aid: Long, cid: Long, cover: String, qn: Int, downloadType: String,
                          audioUrl: String) {
            CenterThreadPool.run {
                var database: SQLiteDatabase? = null
                var cursor: Cursor? = null
                try {
                    val helper = DownloadSqlHelper(BiliTerminal.context)
                    database = helper.writableDatabase

                    cursor = database.rawQuery("select * from download where aid=? and cid=?",
                        arrayOf(aid.toString(), cid.toString()))
                    if (cursor != null && cursor.count > 0) {
                        MsgUtil.showMsg("该视频已在下载队列中")
                        return@run
                    }
                    cursor?.close()

                    database.execSQL(
                        "insert into download(type,state,aid,cid,qn,title,child,cover,download_type,audio_url) values(?,?,?,?,?,?,?,?,?,?)",
                        arrayOf<Any>("video_single", "none", aid, cid, qn, title, "", GlideUtil.url(cover),
                            downloadType, audioUrl))

                    val path_single = FileUtil.getVideoDownloadPath(title, null)
                    path_single.mkdirs()

                    val file_sign = File(path_single, ".DOWNLOADING")
                    if (!file_sign.exists())
                        file_sign.createNewFile()

                    // 保存画质元数据
                    val qualityFile = File(path_single, ".quality")
                    val qualityContent = if ("audio_only" == downloadType) "audio_only" else qn.toString()
                    qualityFile.writeText(qualityContent)

                    // 保存完整视频元数据到 .video_meta.json
                    saveVideoMeta(path_single, title, aid, cid, qn, downloadType)

                    val msg = if ("audio_only" == downloadType) "已添加音频下载" else "已添加下载"
                    MsgUtil.showMsg(msg)

                    start(-1)
                } catch (e: Exception) {
                    MsgUtil.err(e)
                } finally {
                    cursor?.close()
                    database?.close()
                }
            }
        }

        @JvmStatic
        fun startDownload(parent: String, child: String, aid: Long, cid: Long, cover: String, qn: Int,
                          downloadType: String, audioUrl: String) {
            CenterThreadPool.run {
                var database: SQLiteDatabase? = null
                var cursor: Cursor? = null
                try {
                    val helper = DownloadSqlHelper(BiliTerminal.context)
                    database = helper.writableDatabase

                    cursor = database.rawQuery("select * from download where aid=? and cid=?",
                        arrayOf(aid.toString(), cid.toString()))
                    if (cursor != null && cursor.count > 0) {
                        MsgUtil.showMsg("该视频已在下载队列中")
                        return@run
                    }
                    cursor?.close()

                    database.execSQL(
                        "insert into download(type,state,aid,cid,qn,title,child,cover,download_type,audio_url) values(?,?,?,?,?,?,?,?,?,?)",
                        arrayOf<Any>("video_multi", "none", aid, cid, qn, parent, child, GlideUtil.url(cover),
                            downloadType, audioUrl))

                    val path_page = FileUtil.getVideoDownloadPath(parent, child)
                    path_page.mkdirs()

                    val file_sign = File(path_page, ".DOWNLOADING")
                    if (!file_sign.exists())
                        file_sign.createNewFile()

                    // 保存画质元数据
                    val qualityFile = File(path_page, ".quality")
                    val qualityContent = if ("audio_only" == downloadType) "audio_only" else qn.toString()
                    qualityFile.writeText(qualityContent)

                    // 保存完整视频元数据到 .video_meta.json
                    saveVideoMeta(path_page, child, aid, cid, qn, downloadType)

                    val msg = if ("audio_only" == downloadType) "已添加音频下载" else "已添加下载"
                    MsgUtil.showMsg(msg)

                    start(-1)
                } catch (e: Exception) {
                    MsgUtil.err(e)
                } finally {
                    cursor?.close()
                    database?.close()
                }
            }
        }

        @JvmStatic
        fun start(first: Long) {
            if (started)
                return
            started = true
            Logu.d("start")
            firstDown = first

            val context = BiliTerminal.context
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    context.startForegroundService(Intent(context, DownloadService::class.java))
                else
                    context.startService(Intent(context, DownloadService::class.java))
            } catch (e: Exception) {
                started = false
                Logu.e("start", e.message ?: "启动下载服务失败")
                CenterThreadPool.runOnUiThread {
                    MsgUtil.showMsg("启动下载服务失败，请重试")
                }
            }
        }

        /** 在批次开始时重置速度采样，避免把上一批次的字节计入 */
        @JvmStatic
        fun resetSpeedSampling() {
            synchronized(speedLock) {
                speedSampler.reset(System.currentTimeMillis())
                speedStr = ""
            }
        }

        /** 周期性采样全局已下载字节数，得到所有并行下载的聚合速度 */
        @JvmStatic
        fun sampleSpeed() {
            synchronized(speedLock) {
                val speed = speedSampler.sample(getDownloadedBytes(), System.currentTimeMillis())
                if (speed != null) {
                    speedStr = formatDownloadSpeed(speed)
                }
            }
        }
    }

    val NOTIFICATION_CHANNEL_ID = "biliterminal_download"
    val FOREGROUND_ID = 1027
    lateinit var statusBuilder: NotificationCompat.Builder
    lateinit var completionBuilder: NotificationCompat.Builder
    lateinit var notifyManager: NotificationManager

    private var exitMessage: String? = null

    private var toastTimer: Timer? = null
    private var notifyTimer: Timer? = null

    override fun onCreate() {
        super.onCreate()

        Logu.d("onCreate")

        notifyManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "哔哩终端下载服务",
                NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "哔哩终端下载服务"
            channel.setSound(null, null)
            channel.enableVibration(false)

            notifyManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, DownloadListActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        statusBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.icon)
            .setContentTitle("下载视频中")
            .setProgress(100, 0, false)
            .setContentIntent(pendingIntent)
            .setSound(null)
            .setVibrate(null)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        completionBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.icon)
            .setContentTitle("下载完成")
            .setContentIntent(pendingIntent)
            .setOngoing(false)
            .setSound(null)
            .setVibrate(null)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @SuppressLint("MutatingSharedPrefs")
    override fun onStartCommand(serviceIntent: Intent?, flags: Int, startId: Int): Int {
        Logu.d("onStartCommand")
        if (serviceIntent == null) {
            return START_STICKY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_ID, statusBuilder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(FOREGROUND_ID, statusBuilder.build())
        }

        exitCode = ERR_UNKNOWN
        startNotifyProgress()

        CenterThreadPool.run {
            try {
                // 恢复上次崩溃/中断遗留的"下载中"记录，避免卡死
                recoverStuckSections()
                // 开始新批次，重置统计
                batchStats.reset()
                resetDownloadedBytes()
                resetSpeedSampling()
                activeDownloadsCount = 0

                val parallelCount = Aria2Util.getParallelDownloadVideos().coerceIn(1, 10)
                if (parallelCount <= 1)
                    sequentialDownload()
                else
                    parallelDownload(parallelCount)

                section = null
                refreshDownloadList()

                exitCode = NORMAL
                exitMessage = if (batchStats.failed > 0)
                    "${batchStats.failed} 个任务下载失败，请重试"
                else
                    "全部下载完成"
            } catch (e: Exception) {
                MsgUtil.err(e)
                exitCode = ERR_UNKNOWN
                exitMessage = "下载失败，未知错误"
            }

            stopSelf()
        }

        return Service.START_STICKY
    }

    private fun sequentialDownload() {
        while (started) {
            val section_tmp = getFirst()
            if (section_tmp == null)
                break

            section = section_tmp
            // 串行模式：遇到失败即停止，剩余任务保持排队，可再次启动续传
            if (!runDownloadSection(section!!))
                break
        }
    }

    private fun parallelDownload(parallelCount: Int) {
        val semaphore = java.util.concurrent.Semaphore(parallelCount)
        val activeCount = java.util.concurrent.atomic.AtomicInteger(0)

        while (started) {
            // 等待一个空闲槽位
            try {
                semaphore.acquire()
            } catch (e: InterruptedException) {
                break
            }

            if (!started) {
                semaphore.release()
                break
            }

            val sectionToProcess = getFirst()
            if (sectionToProcess == null) {
                // 没有可下载的任务了
                semaphore.release()
                if (activeCount.get() == 0)
                    break  // 无任务且无活跃下载，真正结束
                // 还有活跃下载在跑，等待后再试（允许新加入的任务被拾取）
                try {
                    Thread.sleep(500)
                } catch (ignored: InterruptedException) {
                }
                continue
            }

            // 立即标记为下载中，防止竞态条件导致同一任务被重复调度
            setState(sectionToProcess.id, "downloading")
            setDownloadProgress(sectionToProcess.id, 0f, "准备中")

            activeCount.incrementAndGet()
            activeDownloadsCount = activeCount.get()

            section = sectionToProcess
            state = "准备中"
            percent = 0f

            val taskSection = sectionToProcess
            CenterThreadPool.run {
                try {
                    // 单个任务失败不会中止整个批次，其余任务继续下载
                    runDownloadSection(taskSection)
                } catch (e: Exception) {
                    MsgUtil.err(e)
                } finally {
                    val remaining = activeCount.decrementAndGet()
                    activeDownloadsCount = remaining
                    semaphore.release()
                    refreshDownloadList()
                }
            }
        }

        // 等待所有活跃下载完成
        while (activeCount.get() > 0 && started) {
            try {
                Thread.sleep(300)
            } catch (ignored: InterruptedException) {
            }
        }
    }

    /**
     * 处理单个视频下载并统计成功/失败。
     * 失败项目会被置为终态(error)：不会被再次拾取，也不会卡在"下载中"。
     */
    private fun runDownloadSection(downloadSection: DownloadSection): Boolean {
        val success = try {
            processDownloadSection(downloadSection)
        } catch (e: Exception) {
            Logu.e("DownloadService", "下载异常: ${e.message}")
            MsgUtil.err(e)
            removeDownloadProgress(downloadSection.id)
            false
        }

        if (success) {
            batchStats.recordSuccess()
        } else {
            batchStats.recordFailure()
            removeDownloadProgress(downloadSection.id)
            setState(downloadSection.id, "error")
        }
        return success
    }

    /** 恢复上次会话遗留的"下载中"记录，并清理残留进度 */
    private fun recoverStuckSections() {
        getDownloadProgressMap().let { it.clear() }
        val all = getAll()
        if (all != null) {
            for (s in all) {
                if (s.state == "downloading") {
                    setState(s.id, "none")
                }
            }
        }
    }

    /**
     * 处理单个视频的下载流程，返回是否成功
     */
    private fun processDownloadSection(downloadSection: DownloadSection): Boolean {
        val url_video: String
        val url_danmaku: String
        val url_audio: String
        val useDash: Boolean // 是否使用DASH格式（需要合并音视频）
        try {
            val data = downloadSection.toPlayerData()

            if (downloadSection.isAudioOnly) {
                PlayerApi.getVideoDash(data)
                url_audio = if (!downloadSection.audioUrl.isNullOrEmpty())
                    downloadSection.audioUrl!!
                else
                    data.audioUrl
                url_video = ""
                useDash = false // 纯音频不需要合并
            } else {
                // 720P及以下用旧方法(MP4单文件)，1080P及以上用DASH新方法
                if (downloadSection.qn <= 64) {
                    PlayerApi.getVideo(data, true)
                    url_video = data.videoUrl
                    url_audio = ""
                    useDash = false
                } else {
                    PlayerApi.getVideoDash(data)
                    url_video = data.videoUrl
                    url_audio = data.audioUrl
                    useDash = true
                }
            }
            url_danmaku = data.danmakuUrl

            // 保存画质列表到元数据文件
            val downloadPath = downloadSection.getPath()
            if (downloadPath != null && downloadPath.exists()) {
                updateVideoMetaQualityLists(downloadPath, data.qnStrList, data.qnValueList)
            }
        } catch (e: JSONException) {
            setState(downloadSection.id, "error")
            notifyCompletion("下载链接获取失败：\n" + downloadSection.name_short, downloadSection.id.toInt())
            section = null
            refreshDownloadList()
            return false
        } catch (e: IOException) {
            exitCode = ERR_NETWORK
            setState(downloadSection.id, "none")
            return false
        }

        try {
            setState(downloadSection.id, "downloading")

            // 更新进度追踪
            setDownloadProgress(downloadSection.id, 0f, "开始下载")

            // 更新当前显示的section
            section = downloadSection
            percent = 0f
            state = "开始下载"
            refreshDownloadList()

            var file_sign: File? = null
            var result: Int

            when (downloadSection.type) {
                "video_single" -> {
                    val path_single = downloadSection.getPath()

                    file_sign = File(path_single, ".DOWNLOADING")
                    if (!file_sign.exists() && !file_sign.createNewFile()) {
                        exitCode = ERR_FILE
                        return false
                    }

                    state = "下载封面"
                    setDownloadProgress(downloadSection.id, 0.05f, "下载封面")
                    val coverFile = File(path_single, "cover.png")
                    if (!coverFile.exists() && downloadSection.url_cover.isNotEmpty()) {
                        result = downFile(downloadSection.url_cover, coverFile, downloadSection.id, 0.05f, 0.1f)
                        if (result != NORMAL) {
                            exitCode = result
                            return false
                        }
                    }

                    if (!downloadSection.isAudioOnly) {
                        state = "下载字幕"
                        setDownloadProgress(downloadSection.id, 0.1f, "下载字幕")
                        downSubtitles(downloadSection.aid, downloadSection.cid, path_single)

                        state = "下载弹幕"
                        setDownloadProgress(downloadSection.id, 0.15f, "下载弹幕")
                        result = downDanmaku(url_danmaku, File(path_single, "danmaku.xml"), downloadSection.id, 0.15f)
                        if (result != NORMAL) {
                            exitCode = result
                            return false
                        }
                    }

                    if (downloadSection.isAudioOnly) {
                        state = "下载音频"
                        setDownloadProgress(downloadSection.id, 0.2f, "下载音频")
                        result = downFile(url_audio, File(path_single, "audio.m4a"), downloadSection.id, 0.2f)
                        if (result != NORMAL) {
                            exitCode = result
                            return false
                        }
                    } else if (useDash) {
                        // DASH格式：分别下载视频流和音频流，然后合并为单个文件
                        val videoFile = File(path_single, "video.mp4")
                        val audioFile = File(path_single, "audio.m4a")
                        state = "下载视频"
                        setDownloadProgress(downloadSection.id, 0.2f, "下载视频")
                        result = downFile(url_video, videoFile, downloadSection.id, 0.2f, 0.6f)
                        if (result != NORMAL) {
                            exitCode = result
                            return false
                        }
                        state = "下载音频"
                        setDownloadProgress(downloadSection.id, 0.6f, "下载音频")
                        result = downFile(url_audio, audioFile, downloadSection.id, 0.6f, 0.85f)
                        if (result != NORMAL) {
                            exitCode = result
                            return false
                        }
                        // 合并视频和音频为单个文件，兼容旧格式播放
                        state = "合并音视频"
                        setDownloadProgress(downloadSection.id, 0.85f, "合并音视频")
                        if (!MediaMerger.mergeAv(videoFile, audioFile)) {
                            Logu.e("DownloadService", "音视频合并失败，保留分离文件")
                        }
                    } else {
                        // MP4格式：直接下载单个文件（音视频已合并）
                        state = "下载视频"
                        setDownloadProgress(downloadSection.id, 0.2f, "下载视频")
                        result = downFile(url_video, File(path_single, "video.mp4"), downloadSection.id, 0.2f)
                        if (result != NORMAL) {
                            exitCode = result
                            return false
                        }
                    }
                }
                "video_multi" -> {
                    val path_page = downloadSection.getPath()
                    val path_parent = path_page.parentFile

                    if (!path_page.exists() && !path_page.mkdirs()) {
                        exitCode = ERR_FILE
                        return false
                    }

                    file_sign = File(path_page, ".DOWNLOADING")
                    if (!file_sign.exists() && !file_sign.createNewFile()) {
                        exitCode = ERR_FILE
                        return false
                    }

                    state = "下载封面"
                    setDownloadProgress(downloadSection.id, 0.05f, "下载封面")
                    val cover_multi = File(path_parent, "cover.png")
                    if (!cover_multi.exists()) {
                        result = downFile(downloadSection.url_cover, cover_multi, downloadSection.id, 0.05f, 0.1f)
                        if (result != NORMAL) {
                            exitCode = result
                            return false
                        }
                    }

                    if (!downloadSection.isAudioOnly) {
                        state = "下载字幕"
                        setDownloadProgress(downloadSection.id, 0.1f, "下载字幕")
                        downSubtitles(downloadSection.aid, downloadSection.cid, path_page)

                        state = "下载弹幕"
                        setDownloadProgress(downloadSection.id, 0.15f, "下载弹幕")
                        result = downDanmaku(url_danmaku, File(path_page, "danmaku.xml"), downloadSection.id, 0.15f)
                        if (result != NORMAL) {
                            exitCode = result
                            return false
                        }
                    }

                    if (downloadSection.isAudioOnly) {
                        state = "下载音频"
                        setDownloadProgress(downloadSection.id, 0.2f, "下载音频")
                        result = downFile(url_audio, File(path_page, "audio.m4a"), downloadSection.id, 0.2f)
                        if (result != NORMAL) {
                            exitCode = result
                            return false
                        }
                    } else if (useDash) {
                        // DASH格式：分别下载视频流和音频流，然后合并为单个文件
                        val videoFile = File(path_page, "video.mp4")
                        val audioFile = File(path_page, "audio.m4a")
                        state = "下载视频"
                        setDownloadProgress(downloadSection.id, 0.2f, "下载视频")
                        result = downFile(url_video, videoFile, downloadSection.id, 0.2f, 0.6f)
                        if (result != NORMAL) {
                            exitCode = result
                            return false
                        }
                        state = "下载音频"
                        setDownloadProgress(downloadSection.id, 0.6f, "下载音频")
                        result = downFile(url_audio, audioFile, downloadSection.id, 0.6f, 0.85f)
                        if (result != NORMAL) {
                            exitCode = result
                            return false
                        }
                        // 合并视频和音频为单个文件，兼容旧格式播放
                        state = "合并音视频"
                        setDownloadProgress(downloadSection.id, 0.85f, "合并音视频")
                        if (!MediaMerger.mergeAv(videoFile, audioFile)) {
                            Logu.e("DownloadService", "音视频合并失败，保留分离文件")
                        }
                    } else {
                        // MP4格式：直接下载单个文件（音视频已合并）
                        state = "下载视频"
                        setDownloadProgress(downloadSection.id, 0.2f, "下载视频")
                        result = downFile(url_video, File(path_page, "video.mp4"), downloadSection.id, 0.2f)
                        if (result != NORMAL) {
                            exitCode = result
                            return false
                        }
                    }
                }
            }

            notifyCompletion("下载成功：\n" + downloadSection.name_short, downloadSection.id.toInt())

            // 移除进度映射
            removeDownloadProgress(downloadSection.id)

            if (file_sign != null && file_sign.exists())
                file_sign.delete()

            deleteSection(downloadSection.id)
            refreshLocalList()

            return true
        } catch (e: IOException) {
            exitCode = ERR_FILE
            setState(downloadSection.id, "error")
            return false
        }
    }

    private fun toastState(newState: String) {
        state = newState
        percent = 0f
        toastTimer?.cancel()
    }

    private fun startNotifyProgress() {
        notifyTimer = Timer()
        notifyTimer!!.schedule(object : TimerTask() {
            override fun run() {
                // 周期性采样所有并行下载的聚合速度
                DownloadService.sampleSpeed()

                if (section == null || notifyTimer == null)
                    return

                val overall = DownloadService.computeOverallProgress(
                    DownloadService.getAll() ?: emptyList()
                )
                statusBuilder.setContentText(
                    "总进度 " + (overall * 100).toInt() + "% · " + (section?.name_short ?: "下载中")
                )
                statusBuilder.setProgress(100, (overall * 100).toInt(), false)
                notifyManager.notify(FOREGROUND_ID, statusBuilder.build())
            }
        }, 500, 1000)
    }

    private fun notifyExit(content: String) {
        MsgUtil.showMsg(content)
        notifyManager.cancel(FOREGROUND_ID)
        completionBuilder.setContentTitle("下载结束")
        completionBuilder.setContentText(content)
        completionBuilder.setProgress(0, 0, false)
        notifyManager.notify(2, completionBuilder.build())
    }

    private fun notifyCompletion(content: String, id: Int) {
        MsgUtil.showMsg(content)
        completionBuilder.setContentText(content)
        notifyManager.notify(id % 100 + 100, completionBuilder.build())
    }

    private fun refreshDownloadList() {
        if (DownloadListActivity.weakRef != null && DownloadListActivity.weakRef!!.get() != null) {
            DownloadListActivity.weakRef!!.get()!!.refreshList(true)
        }
    }

    private fun refreshLocalList() {
        val instance = BiliTerminal.getInstanceActivityOnTop()
        if (instance is LocalListActivity && !instance.isDestroyed)
            (instance as LocalListActivity).refresh()
    }

    private fun downSubtitles(aid: Long, cid: Long, folder: File): Int {
        try {
            val subtitleLinks = PlayerApi.getSubtitleLinks(aid, cid)
            if (subtitleLinks.size <= 1)
                return NORMAL

            val subtitleFolder = File(folder, "subtitles")
            if (!subtitleFolder.mkdirs())
                return ERR_FILE
            for (subtitleLink in subtitleLinks) {
                if (subtitleLink.id != -1L) {
                    val subtitleFile = File(subtitleFolder, subtitleLink.lang + ".json")
                    if (!subtitleFile.createNewFile())
                        return ERR_FILE
                    val result = downFile(subtitleLink.url, subtitleFile)
                    if (result != NORMAL)
                        return result
                }
            }
        } catch (e: IOException) {
            return ERR_NETWORK
        } catch (e: JSONException) {
            return ERR_JSON
        }
        return NORMAL
    }

    @Throws(IOException::class)
    private fun downFile(url: String, file: File, sectionId: Long = -1, baseProgress: Float = 0f, endProgress: Float = 1.0f): Int {
        if (Aria2Util.isEnabled()) {
            isSpeedMode = true
            return downFileSpeed(url, file, sectionId, baseProgress, endProgress)
        }
        isSpeedMode = false
        return downFileNormal(url, file, sectionId, baseProgress, endProgress)
    }

    private fun resetFile(file: File): Boolean {
        return try {
            if (file.exists()) {
                file.delete() && file.createNewFile()
            } else {
                file.createNewFile()
            }
        } catch (e: IOException) {
            false
        }
    }

    @Throws(IOException::class)
    private fun downFileNormal(url: String, file: File, sectionId: Long = -1, baseProgress: Float = 0f, endProgress: Float = 1.0f): Int {
        val response: Response
        try {
            response = NetWorkUtil.get(url)
        } catch (e: IOException) {
            return ERR_NETWORK
        }
        var inputStream: InputStream? = null
        var fileOutputStream: FileOutputStream? = null
        try {
            if (!resetFile(file))
                return ERR_FILE

            val body = response.body
            if (body == null) return ERR_NETWORK

            inputStream = body.byteStream()
            fileOutputStream = FileOutputStream(file)
            var len: Int
            val bytes = ByteArray(256 * 1024)
            val TotalFileSize = body.contentLength()
            var totalDown: Long = 0
            var lastProgressUpdate = 0L
            val progressUpdateInterval = if (TotalFileSize > 0) Math.max(TotalFileSize / 1000, 65536) else 1L
            while ((inputStream.read(bytes).also { len = it }) != -1 && started) {
                fileOutputStream.write(bytes, 0, len)
                totalDown += len
                addDownloadedBytes(len.toLong())
                if (totalDown - lastProgressUpdate >= progressUpdateInterval) {
                    lastProgressUpdate = totalDown
                    if (TotalFileSize > 0) {
                        percent = baseProgress + (1.0f * totalDown / TotalFileSize) * (endProgress - baseProgress)
                    }

                    // 更新进度到进度映射表
                    if (sectionId > 0 && TotalFileSize > 0) {
                        val fileProgress = 1.0f * totalDown / TotalFileSize
                        val actualProgress = baseProgress + fileProgress * (endProgress - baseProgress)
                        setDownloadProgress(sectionId, actualProgress, state ?: "下载中", totalDown, TotalFileSize)
                    }
                }
            }
            if (TotalFileSize <= 0) {
                percent = endProgress
            } else {
                percent = baseProgress + (1.0f * totalDown / TotalFileSize) * (endProgress - baseProgress)
            }
            if (!started)
                return ERR_UNKNOWN
        } catch (e: IOException) {
            return ERR_FILE
        } finally {
            inputStream?.close()
            fileOutputStream?.close()
            response.body?.close()
            response.close()
        }
        return NORMAL
    }

    @Throws(IOException::class)
    private fun downFileSpeed(url: String, file: File, sectionId: Long = -1, baseProgress: Float = 0f, endProgress: Float = 1.0f): Int {
        if (!resetFile(file))
            return ERR_FILE

        val client = NetWorkUtil.getOkHttpInstance()
        val headers = NetWorkUtil.webHeaders

        val headBuilder = okhttp3.Request.Builder().url(url).head()
        var i = 0
        while (i < headers.size) {
            headBuilder.addHeader(headers[i], headers[i + 1])
            i += 2
        }
        val headReq = headBuilder.build()

        val (totalSize, supportsRange) = try {
            client.newCall(headReq).execute().use { headRes ->
                val len = headRes.header("Content-Length")
                val size = if (len != null) len.toLong() else 0L
                val range = "bytes" == headRes.header("Accept-Ranges")
                Pair(size, range)
            }
        } catch (e: Exception) {
            Pair(0L, false)
        }

        return if (totalSize <= 0 || !supportsRange || totalSize <= 2 * 1024 * 1024) {
            downFileSpeedSingle(url, file, client, headers, totalSize, sectionId, baseProgress, endProgress)
        } else {
            downFileSpeedSeg(url, file, client, headers, totalSize, sectionId, baseProgress, endProgress)
        }
    }

    @Throws(IOException::class)
    private fun downFileSpeedSingle(url: String, file: File, client: okhttp3.OkHttpClient,
                                     headers: ArrayList<String>, totalSize: Long,
                                     sectionId: Long = -1, baseProgress: Float = 0f, endProgress: Float = 1.0f): Int {
        val reqBuilder = okhttp3.Request.Builder().url(url).get()
        var i = 0
        while (i < headers.size) {
            reqBuilder.addHeader(headers[i], headers[i + 1])
            i += 2
        }
        val request = reqBuilder.build()

        var effectiveTotalSize = totalSize
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ERR_NETWORK
            val body = response.body
            if (body == null) return ERR_NETWORK
            if (effectiveTotalSize <= 0) effectiveTotalSize = body.contentLength()
            if (effectiveTotalSize <= 0) effectiveTotalSize = 1
            body.byteStream().use { inputStream ->
                FileOutputStream(file).use { fos ->
                    val buffer = ByteArray(65536)
                    var downloaded: Long = 0
                    var read: Int
                    var lastProgressUpdate = 0L
                    val progressUpdateInterval = if (effectiveTotalSize > 1) Math.max(effectiveTotalSize / 1000, 65536) else 1L
                    while ((inputStream.read(buffer).also { read = it }) != -1 && started) {
                        fos.write(buffer, 0, read)
                        downloaded += read
                        addDownloadedBytes(read.toLong())
                        if (downloaded - lastProgressUpdate >= progressUpdateInterval) {
                            lastProgressUpdate = downloaded
                            percent = baseProgress + (1.0f * downloaded / effectiveTotalSize) * (endProgress - baseProgress)

                            // 更新进度到进度映射表
                            if (sectionId > 0) {
                                val fileProgress = 1.0f * downloaded / effectiveTotalSize
                                val actualProgress = baseProgress + fileProgress * (endProgress - baseProgress)
                                setDownloadProgress(sectionId, actualProgress, state ?: "下载中", downloaded, effectiveTotalSize)
                            }
                        }
                    }
                    percent = baseProgress + (1.0f * downloaded / effectiveTotalSize) * (endProgress - baseProgress)
                }
            }
        }
        return if (started) NORMAL else ERR_UNKNOWN
    }

    @Throws(IOException::class)
    private fun downFileSpeedSeg(url: String, file: File, client: okhttp3.OkHttpClient,
                                  headers: ArrayList<String>, totalSize: Long,
                                  sectionId: Long = -1, baseProgress: Float = 0f, endProgress: Float = 1.0f): Int {
        java.io.RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(totalSize)
        }

        // 动态分片：约每 2MB 一片，上限受用户配置的分片数约束
        var segments = Math.min(Aria2Util.getSplit(), Math.max(1, (totalSize / (2 * 1024 * 1024)).toInt()))
        if (segments < 1) segments = 1
        val segmentLen = totalSize / segments

        val totalDownloaded = java.util.concurrent.atomic.AtomicLong(0)
        val anyFailed = java.util.concurrent.atomic.AtomicBoolean(false)

        val threads = java.util.ArrayList<Thread>(segments)

        for (i in 0 until segments) {
            val start = i * segmentLen
            val end = if (i == segments - 1) totalSize - 1 else start + segmentLen - 1
            val idx = i

            val thread = Thread({
                downloadSegment(url, file, client, headers, start, end, idx, totalDownloaded, anyFailed)
            }, "DL-Segment-$idx")
            threads.add(thread)
            thread.start()
        }

        // 轮询进度，直到全部分片结束、失败或取消
        while (completedSegments(threads) < segments && started && !anyFailed.get()) {
            percent = baseProgress + (1.0f * totalDownloaded.get() / totalSize) * (endProgress - baseProgress)

            // 更新进度到进度映射表
            if (sectionId > 0) {
                val fileProgress = 1.0f * totalDownloaded.get() / totalSize
                val actualProgress = baseProgress + fileProgress * (endProgress - baseProgress)
                setDownloadProgress(sectionId, actualProgress, state ?: "下载中", totalDownloaded.get(), totalSize)
            }

            try {
                Thread.sleep(200)
            } catch (ignored: InterruptedException) {
            }
        }

        // 等待线程结束（带超时），避免遗留写盘
        for (t in threads) {
            try {
                t.join(30000)
            } catch (ignored: InterruptedException) {
            }
        }

        // 完整性校验：任一失败或下载字节不足都视为失败，回退整文件单线程重下
        if (anyFailed.get() || totalDownloaded.get() < totalSize) {
            return downFileSpeedSingle(url, file, client, headers, totalSize, sectionId, baseProgress, endProgress)
        }

        return if (started) NORMAL else ERR_UNKNOWN
    }

    /** 下载单个分片（最多重试 3 次），成功后累加已下载字节。 */
    private fun downloadSegment(url: String, file: File, client: okhttp3.OkHttpClient,
                                headers: ArrayList<String>, start: Long, end: Long, idx: Int,
                                totalDownloaded: java.util.concurrent.atomic.AtomicLong,
                                anyFailed: java.util.concurrent.atomic.AtomicBoolean) {
        val expectLen = end - start + 1
        var attempts = 0
        while (attempts < 3 && started && !anyFailed.get()) {
            var written = 0L
            var ok = false
            try {
                val reqBuilder = okhttp3.Request.Builder()
                    .url(url)
                    .header("Range", "bytes=$start-$end")
                    .get()
                var j = 0
                while (j < headers.size) {
                    reqBuilder.addHeader(headers[j], headers[j + 1])
                    j += 2
                }
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful || resp.body == null) throw IOException("HTTP ${resp.code}")
                    val buffer = ByteArray(65536)
                    var read: Int
                    resp.body!!.byteStream().use { inputStream ->
                        java.io.RandomAccessFile(file, "rw").use { rafInner ->
                            rafInner.seek(start)
                            while ((inputStream.read(buffer).also { read = it }) != -1 && started) {
                                rafInner.write(buffer, 0, read)
                                written += read
                                addDownloadedBytes(read.toLong())
                            }
                        }
                    }
                }
                ok = started && written == expectLen
            } catch (e: Exception) {
                ok = false
            }
            if (ok) {
                totalDownloaded.addAndGet(written)
                return
            }
            attempts++
            if (attempts < 3) {
                try {
                    Thread.sleep(500L * attempts)
                } catch (ignored: InterruptedException) {
                }
            }
        }
        anyFailed.set(true)
    }

    /** 已结束的分片线程数（用于进度轮询退出条件）。 */
    private fun completedSegments(threads: java.util.ArrayList<Thread>): Int {
        var n = 0
        for (t in threads) if (!t.isAlive) n++
        return n
    }

    @Throws(IOException::class)
    private fun downDanmaku(danmaku: String, danmakuFile: File, sectionId: Long = -1, baseProgress: Float = 0f): Int {
        val response: Response
        try {
            response = NetWorkUtil.get(danmaku)
        } catch (e: IOException) {
            return ERR_NETWORK
        }
        var bufferedSink: BufferedSink? = null
        try {
            if (!resetFile(danmakuFile))
                return ERR_FILE

            val sink: Sink = danmakuFile.sink()
            val decompressBytes = decompress(response.body!!.bytes())
            bufferedSink = sink.buffer()
            bufferedSink.write(decompressBytes)
            bufferedSink.close()
            
            // 更新进度
            if (sectionId > 0) {
                setDownloadProgress(sectionId, baseProgress + 0.05f, "下载弹幕")
            }
        } catch (e: IOException) {
            return ERR_FILE
        } finally {
            bufferedSink?.close()
            response.body?.close()
            response.close()
        }
        return NORMAL
    }

    override fun onDestroy() {
        Logu.d("结束")

        started = false
        percent = -1f
        state = null
        speedStr = ""
        getDownloadProgressMap().let { it.clear() }
        batchStats.reset()
        resetDownloadedBytes()
        activeDownloadsCount = 0

        toastTimer?.cancel()
        toastTimer = null

        notifyTimer?.cancel()
        notifyTimer = null

        if (exitMessage == null)
            exitMessage = "下载服务已退出"

        Logu.d("退出下载服务")
        if (section != null) {
            val id = section!!.id
            val folder = section!!.getPath()
            section = null

            CenterThreadPool.run {
                notifyExit(exitMessage!!)
                if (exitCode != NORMAL) {
                    setState(id, "none")
                    FileUtil.deleteFolder(folder)
                }
                refreshDownloadList()
            }
        }

        super.onDestroy()
    }
}