package com.RobinNotBad.BiliClient.service

import android.annotation.SuppressLint
import android.app.NotificationManager
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
import com.RobinNotBad.BiliClient.service.download.DownloadNotifier
import com.RobinNotBad.BiliClient.service.download.DownloadPathSpec
import com.RobinNotBad.BiliClient.service.download.DownloadProgressMath
import com.RobinNotBad.BiliClient.service.download.DownloadStreamTransfer
import com.RobinNotBad.BiliClient.service.download.DownloadProgressStore
import com.RobinNotBad.BiliClient.service.download.DownloadProgressInfo
import com.RobinNotBad.BiliClient.service.download.DownloadRepository
import com.RobinNotBad.BiliClient.service.download.SegmentDownloadPolicy
import com.RobinNotBad.BiliClient.model.DownloadSection
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.SubtitleLink
import com.RobinNotBad.BiliClient.util.Aria2Util
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.DownloadWakeLock
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
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Timer

class DownloadService : Service() {

    companion object {
        // 这几个字段会被下载协程与主线程同时读写，必须 @Volatile 保证可见性：
        // started 决定调度循环是否继续，exitCode/percent/state 决定通知与退出时的清理动作。
        @JvmStatic @Volatile var started: Boolean = false

        /**
         * 本服务的下载批次是否已在执行。
         *
         * [onStartCommand] 可能被重复投递（START_STICKY 重建、或多次 startForegroundService），
         * 没有这道守卫就会并发跑起两个下载批次、双线程写同一文件（审计 S6）。
         * 不能用 [started] 代替：start() 是先置 started 再 startForegroundService，
         * 首次合法调用进来时 started 已经是 true，用它判断会把正常请求也挡掉。
         */
        @Volatile private var batchRunning: Boolean = false

        /**
         * 本次运行（进程）是否已判定"分片下载在当前网络下不可用"。
         *
         * 触发场景见 [SegmentDownloadPolicy]：服务端忽略 Range 回了 200 全量文件，
         * 或分片线程长时间一个字节都收不到。一旦置位，后续任务直接走单连接下载——
         * 否则用户每下一个文件都要先卡一轮 12s 看门狗才退化成单线程。
         * 不复位：网络变好前重新试分片的收益远小于再卡一次的代价。
         */
        @Volatile private var segmentModeDisabled: Boolean = false

        @JvmStatic @Volatile var exitCode: Int = 0
        @JvmStatic @Volatile var percent: Float = -1f
        @JvmStatic @Volatile var state: String? = null
        @JvmStatic @Volatile var section: DownloadSection? = null
        // 速度显示字符串与"是否高速模式"由 DownloadProgressStore 持有，这里只留 @JvmStatic 门面，
        // Java/Kotlin 侧照旧写 DownloadService.speedStr / DownloadService.isSpeedMode。
        // 刻意保持非 volatile：UI 线程直接读、下载线程直接写是既有数据竞争，加 volatile 属于行为变更。
        @JvmStatic
        var speedStr: String
            get() = DownloadProgressStore.speedStr
            set(value) {
                DownloadProgressStore.speedStr = value
            }

        @JvmStatic
        var isSpeedMode: Boolean
            get() = DownloadProgressStore.isSpeedMode
            set(value) {
                DownloadProgressStore.isSpeedMode = value
            }

        // 用户指定优先下载的那一集（firstDown）已搬到 DownloadRepository，与 DB 查询放在一起

        // 本次下载批次的总体统计（总进度条、通知栏使用）
        @JvmStatic val batchStats = DownloadBatchStats()

        @JvmStatic var activeDownloadsCount: Int = 0
            private set

        // 被用户暂停的任务：key=section.id。暂停后该任务的下载循环会尽快退出，状态置为 paused，
        // 调度器（只取 state="none"）不会重新拾取；恢复时清除标志并置回 none。
        // 用带 getter 的 val 暴露（Java 侧是 getPausedMap() 静态方法）：调用方直接对返回的 map
        // add/remove（DownloadListActivity），必须仍是同一个 map 实例，不能在这里复制。
        @JvmStatic
        val pausedMap: java.util.concurrent.ConcurrentHashMap<Long, Boolean>
            get() = DownloadProgressStore.pausedMap

        // ---------- 以下为 DownloadProgressStore 的门面：实现全部搬到 service/download/ ----------

        @JvmStatic
        fun isPaused(id: Long): Boolean = DownloadProgressStore.isPaused(id)

        @JvmStatic
        fun pauseDownload(id: Long) = DownloadProgressStore.pauseDownload(id)

        @JvmStatic
        fun resumeDownload(id: Long) = DownloadProgressStore.resumeDownload(id)

        @JvmStatic
        fun getDownloadProgress(id: Long): DownloadProgressInfo? {
            return DownloadProgressStore.getDownloadProgress(id)
        }

        @JvmStatic
        fun getDownloadProgressMap(): java.util.concurrent.ConcurrentHashMap<Long, DownloadProgressInfo> {
            return DownloadProgressStore.getDownloadProgressMap()
        }

        @JvmStatic
        fun setDownloadProgress(id: Long, progress: Float, state: String) {
            DownloadProgressStore.setDownloadProgress(id, progress, state)
        }

        @JvmStatic
        fun setDownloadProgress(id: Long, progress: Float, state: String,
                                downloadedBytes: Long, totalBytes: Long) {
            DownloadProgressStore.setDownloadProgress(id, progress, state, downloadedBytes, totalBytes)
        }

        @JvmStatic
        fun removeDownloadProgress(id: Long) {
            DownloadProgressStore.removeDownloadProgress(id)
        }

        @JvmStatic
        fun getDownloadedBytes(): Long = DownloadProgressStore.getDownloadedBytes()

        @JvmStatic
        fun addDownloadedBytes(bytes: Long) {
            DownloadProgressStore.addDownloadedBytes(bytes)
        }

        @JvmStatic
        fun resetDownloadedBytes() {
            DownloadProgressStore.resetDownloadedBytes()
        }

        /**
         * 根据当前数据库中的下载项与进度映射，计算本次批次的总体进度。
         */
        @JvmStatic
        fun computeOverallProgress(sections: List<DownloadSection>): Float {
            return DownloadProgressStore.computeOverallProgress(sections)
        }

        /** 当前仍在下载中的项目剩余字节数合计（用于预估剩余时间） */
        @JvmStatic
        fun getActiveRemainingBytes(sections: List<DownloadSection>): Long {
            return DownloadProgressStore.getActiveRemainingBytes(sections)
        }

        // 下载结果码（含"任务被暂停不算失败"）统一见 DownloadPathSpec

        // ---------- 以下为 DownloadRepository 的门面：DB 访问全部搬到 service/download/ ----------

        @JvmStatic
        fun getFirst(): DownloadSection? = DownloadRepository.getFirst()

        @JvmStatic
        fun getAll(): ArrayList<DownloadSection>? = DownloadRepository.getAll()

        @JvmStatic
        fun deleteSection(id: Long) = DownloadRepository.deleteSection(id)

        @JvmStatic
        fun clear() = DownloadRepository.clear()

        @JvmStatic
        fun setState(id: Long, state: String) = DownloadRepository.setState(id, state)

        @JvmStatic
        fun startDownload(title: String, aid: Long, cid: Long, cover: String, qn: Int, downloadType: String,
                          audioUrl: String) {
            CenterThreadPool.run {
                var database: SQLiteDatabase? = null
                var cursor: Cursor? = null
                try {
                    // 目录写不进去的下载注定失败：先解析根目录（必要时降级到应用专属目录并提示），
                    // 连降级目录都不可写就直接放弃，不往数据库里塞一条永远下不完的任务。
                    if (FileUtil.resolveVideoDownloadDir(BiliTerminal.context) == null) return@run
                    val helper = DownloadSqlHelper(BiliTerminal.context)
                    database = helper.writableDatabase

                    cursor = database.rawQuery("select * from download where aid=? and cid=?",
                        arrayOf(aid.toString(), cid.toString()))
                    if (cursor != null && cursor.count > 0) {
                        MsgUtil.showMsg(BiliTerminal.context!!.getString(R.string.text_download_already_queued))
                        return@run
                    }
                    cursor?.close()

                    database.execSQL(
                        "insert into download(type,state,aid,cid,qn,title,child,cover,download_type,audio_url) values(?,?,?,?,?,?,?,?,?,?)",
                        arrayOf<Any>(DownloadPathSpec.TASK_VIDEO_SINGLE, "none", aid, cid, qn, title, "", GlideUtil.url(cover),
                            downloadType, audioUrl))

                    val path_single = FileUtil.getVideoDownloadPath(title, null)
                    path_single.mkdirs()

                    val file_sign = File(path_single, DownloadPathSpec.FILE_DOWNLOADING)
                    if (!file_sign.exists())
                        file_sign.createNewFile()

                    // 保存画质元数据
                    val qualityFile = File(path_single, DownloadPathSpec.FILE_QUALITY)
                    val qualityContent = if (DownloadPathSpec.TYPE_AUDIO_ONLY == downloadType) DownloadPathSpec.TYPE_AUDIO_ONLY else qn.toString()
                    qualityFile.writeText(qualityContent)

                    // 保存完整视频元数据到 .video_meta.json
                    DownloadRepository.saveVideoMeta(path_single, title, aid, cid, qn, downloadType)

                    val msg = if (DownloadPathSpec.TYPE_AUDIO_ONLY == downloadType) BiliTerminal.context!!.getString(R.string.text_download_added_audio) else BiliTerminal.context!!.getString(R.string.text_download_added)
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
                    // 同 startSingle：先解析可写的根目录，再决定要不要入队。
                    if (FileUtil.resolveVideoDownloadDir(BiliTerminal.context) == null) return@run
                    val helper = DownloadSqlHelper(BiliTerminal.context)
                    database = helper.writableDatabase

                    cursor = database.rawQuery("select * from download where aid=? and cid=?",
                        arrayOf(aid.toString(), cid.toString()))
                    if (cursor != null && cursor.count > 0) {
                        MsgUtil.showMsg(BiliTerminal.context!!.getString(R.string.text_download_already_queued))
                        return@run
                    }
                    cursor?.close()

                    database.execSQL(
                        "insert into download(type,state,aid,cid,qn,title,child,cover,download_type,audio_url) values(?,?,?,?,?,?,?,?,?,?)",
                        arrayOf<Any>(DownloadPathSpec.TASK_VIDEO_MULTI, "none", aid, cid, qn, parent, child, GlideUtil.url(cover),
                            downloadType, audioUrl))

                    val path_page = FileUtil.getVideoDownloadPath(parent, child)
                    path_page.mkdirs()

                    val file_sign = File(path_page, DownloadPathSpec.FILE_DOWNLOADING)
                    if (!file_sign.exists())
                        file_sign.createNewFile()

                    // 保存画质元数据
                    val qualityFile = File(path_page, DownloadPathSpec.FILE_QUALITY)
                    val qualityContent = if (DownloadPathSpec.TYPE_AUDIO_ONLY == downloadType) DownloadPathSpec.TYPE_AUDIO_ONLY else qn.toString()
                    qualityFile.writeText(qualityContent)

                    // 保存完整视频元数据到 .video_meta.json
                    DownloadRepository.saveVideoMeta(path_page, child, aid, cid, qn, downloadType)

                    val msg = if (DownloadPathSpec.TYPE_AUDIO_ONLY == downloadType) BiliTerminal.context!!.getString(R.string.text_download_added_audio) else BiliTerminal.context!!.getString(R.string.text_download_added)
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
        @Synchronized
        fun start(first: Long) {
            // 检查后置位（check-then-act）必须在同一把锁里完成：
            // start() 的入口不止一个（添加下载、恢复暂停、下载页手动继续），
            // 并发进入时两个线程会同时通过 if 检查、各自启动一个下载批次，最终并发写同一批文件。
            if (started) {
                // 不重复启动，但要给出可感知的反馈，避免用户点了"继续下载"以为没生效
                MsgUtil.showMsg(BiliTerminal.context!!.getString(R.string.text_download_queue_running))
                return
            }
            started = true
            Logu.d("start")
            DownloadRepository.setFirstDown(first)

            val context = BiliTerminal.context!!
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    context.startForegroundService(Intent(context, DownloadService::class.java))
                else
                    context.startService(Intent(context, DownloadService::class.java))
            } catch (e: Exception) {
                started = false
                Logu.e("start", e.message ?: "启动下载服务失败")
                CenterThreadPool.runOnUiThread {
                    MsgUtil.showMsg(BiliTerminal.context!!.getString(R.string.text_download_service_start_failed))
                }
            }
        }

        /** 在批次开始时重置速度采样，避免把上一批次的字节计入 */
        @JvmStatic
        fun resetSpeedSampling() {
            DownloadProgressStore.resetSpeedSampling()
        }

        /** 周期性采样全局已下载字节数，得到所有并行下载的聚合速度 */
        @JvmStatic
        fun sampleSpeed() {
            DownloadProgressStore.sampleSpeed()
        }
    }

    val NOTIFICATION_CHANNEL_ID = "biliterminal_download"
    val FOREGROUND_ID = 1027
    lateinit var statusBuilder: NotificationCompat.Builder
    lateinit var completionBuilder: NotificationCompat.Builder
    lateinit var notifyManager: NotificationManager

    /** 通知逻辑统一见 service/download/DownloadNotifier.kt。
     *  这里只放"持有者"：notifier 是 Service 的实例字段，随 Service 一起创建与回收。
     *  绝不能塞进 companion——object 会跨批次抓住第一个 Service 的 Context 与 Timer。
     *  上面几个 Builder / Manager / Timer 之所以还声明在这里，就是为了让状态只有一处。 */
    private lateinit var notifier: DownloadNotifier

    private var exitMessage: String? = null

    private var toastTimer: Timer? = null

    // 由 DownloadNotifier 读写（进度通知的幂等守卫），故不再是 private
    internal var notifyTimer: Timer? = null

    override fun onCreate() {
        super.onCreate()

        Logu.d("onCreate")

        notifier = DownloadNotifier(this)
        notifier.init()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @SuppressLint("MutatingSharedPrefs")
    override fun onStartCommand(serviceIntent: Intent?, flags: Int, startId: Int): Int {
        Logu.d("onStartCommand")
        // START_STICKY 被系统重启时 onStartCommand 收到的 Intent 为 null。这条路径同样受
        // 「5 秒内必须 startForeground」约束，不能再像修复前那样直接 return（findings/robust.md P1-2）。
        val isStickyRestart = serviceIntent == null
        // 幂等守卫（审计 S6）：服务已在一个批次里时，重复的 onStartCommand 必须直接返回。
        // 否则第二个批次会重新执行 recoverStuckSections()，把第一个批次正在下载的 section
        // 由 "downloading" 改回 "none"，调度器随即二次拾取同一文件，两个线程写同一路径。
        // 正常重复投递时首次调用已把服务拉成前台；系统重启路径没人替我们调过 startForeground，
        // 必须在这里补一次，否则服务会被系统按「前台服务没有进场」杀掉。
        if (batchRunning) {
            Logu.d("下载批次已在进行中，忽略重复的 onStartCommand")
            if (isStickyRestart) ensureForeground()
            return START_STICKY
        }
        batchRunning = true
        // 5 秒义务：正常启动与 START_STICKY 重启都先 startForeground 再做后续工作。
        ensureForeground()

        exitCode = DownloadPathSpec.ERR_UNKNOWN
        notifier.startNotifyProgress()

        CenterThreadPool.run {
            // 反馈 #91：这一整段代码才是真在下载的。屏幕熄灭后 CPU 会休眠，网络 read() 被挂起、
            // 下载原地停住，所以真正进下载分支前拿一把「下载期间不休眠」锁（只保 CPU，不亮屏）。
            // 只在别的下载入口也没持锁时才真正申请；批次结束在 finally 里放掉，空闲时不持有。
            var wakeLockHeld = false
            try {
                // 服务可能不与入口 Activity 同批被拉起（START_STICKY 恢复、下载页"继续"、批量重试），
                // 而「降级到应用专属目录」是进程级标记：新进程里它是空的。这里先解析一次根目录，
                // 保证恢复的任务与新任务看到同一个可写目录（写不进去时 resolve 会降级并提示）。
                FileUtil.resolveVideoDownloadDir(BiliTerminal.context)
                // 恢复上次崩溃/中断遗留的"下载中"记录，避免卡死
                recoverStuckSections()

                // START_STICKY 重启的续跑决策（修复前 null Intent 直接 return，队列永远不会续跑）。
                // recoverStuckSections() 已把新进程里遗留的 "downloading" 重置为 "none"，
                // 所以这里统计到的就是全部可恢复任务；paused/error 不算（见 DownloadServiceRecovery）。
                val resumable = if (isStickyRestart)
                    DownloadServiceRecovery.countResumable(DownloadRepository.getAll()?.map { it.state })
                else
                    0
                val action = DownloadServiceRecovery.decide(isStickyRestart, resumable)

                if (action == DownloadServiceRecovery.Action.STOP) {
                    // 重启后队列已空：前台义务已在 onStartCommand 里兑现，这里直接收摊，
                    // 避免服务空转被系统反复重建。刻意不设 exitMessage、不走 notifyExit：
                    // 这不是用户可见的「下载结束」。
                    Logu.d("START_STICKY 重启后没有可恢复任务，停止下载服务")
                    section = null
                    refreshDownloadList()
                } else {
                    if (action == DownloadServiceRecovery.Action.RESUME) {
                        // 系统重启路径没有人调用 start()，started 在新进程里是 false；
                        // 不置位的话调度循环 while(started) 会立刻退出，等于没续跑。
                        Logu.d("START_STICKY 重启：续跑 $resumable 个可恢复任务")
                        started = true
                    }
                    // 开始新批次，重置统计
                    batchStats.reset()
                    resetDownloadedBytes()
                    resetSpeedSampling()
                    activeDownloadsCount = 0

                    // 反馈 #91：队列里确实有活要干，从这里开始持锁到本段 finally 释放。
                    // START_STICKY 重启后队列为空（Action.STOP）的路径不会走到这里，不碰锁；
                    // 用户暂停的任务 state 不是 "none"，getFirst() 取不到，批次会自然结束并放锁。
                    wakeLockHeld = DownloadWakeLock.acquire(applicationContext)

                    val parallelCount = Aria2Util.getParallelDownloadVideos().coerceIn(1, 10)
                    if (parallelCount <= 1)
                        sequentialDownload()
                    else
                        parallelDownload(parallelCount)

                    section = null
                    refreshDownloadList()

                    exitCode = DownloadPathSpec.NORMAL
                    exitMessage = if (batchStats.failed > 0)
                        getString(R.string.text_download_some_failed, batchStats.failed)
                    else
                        getString(R.string.text_download_all_complete)
                }
            } catch (e: Exception) {
                MsgUtil.err(e)
                exitCode = DownloadPathSpec.ERR_UNKNOWN
                exitMessage = getString(R.string.text_download_failed_unknown)
            } finally {
                // 下载批次结束（成功 / 失败 / 被停止服务都走这里）：立刻放锁。
                // 锁在空闲时必须释放，常驻会吞掉待机续航。
                if (wakeLockHeld) DownloadWakeLock.release()
            }

            stopSelf()
        }

        return Service.START_STICKY
    }

    /**
     * 兑现前台服务义务。
     *
     * Android 8+ 由 `startForegroundService()` 拉起的服务必须在 5 秒内调用 startForeground，
     * 否则系统抛 `RemoteServiceException` 并杀掉进程（26.10.10 线上崩溃的同类问题，
     * 详见 PlaybackService 的「前台义务」注释）。所有进入 onStartCommand 的路径都必须走到这里，
     * 包括 START_STICKY 系统重启（Intent == null）和重复投递的早退分支。
     */
    private fun ensureForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_ID, statusBuilder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(FOREGROUND_ID, statusBuilder.build())
        }
    }

    private fun sequentialDownload() {
        while (started) {
            val section_tmp = DownloadRepository.getFirst()
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

            val sectionToProcess = DownloadRepository.getFirst()
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
            DownloadRepository.setState(sectionToProcess.id, "downloading")
            setDownloadProgress(sectionToProcess.id, 0f, getString(R.string.text_download_preparing))

            activeCount.incrementAndGet()
            activeDownloadsCount = activeCount.get()

            section = sectionToProcess
            state = getString(R.string.text_download_preparing)
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
            // 用户暂停的任务：不记为失败，状态保持"paused"，等待用户恢复
            if (isPaused(downloadSection.id)) {
                removeDownloadProgress(downloadSection.id)
                return true // 让串行/并行批次继续，不中断其他任务
            }
            batchStats.recordFailure()
            removeDownloadProgress(downloadSection.id)
            DownloadRepository.setState(downloadSection.id, "error")
        }
        return success
    }

    /** 恢复上次会话遗留的"下载中"记录，并清理残留进度 */
    private fun recoverStuckSections() {
        val all = DownloadRepository.getAll() ?: return
        for (s in all) {
            // 只重置「本进程没有线程在下载」的遗留记录：downloadProgressMap 里有条目的，
            // 说明有活跃下载线程正在写这个 section，改回 "none" 会让调度器二次拾取、
            // 双线程写同一文件（审计 S6）。同时顺手清掉它的残留进度，避免列表显示假进度。
            // 注意判据不是恒真：进程被杀重启后映射必然为空，此时所有 "downloading" 记录都会被重置，
            // 这正是想要的（旧进程的线程已经没了）；同进程内重复进入则由 onStartCommand 的幂等守卫挡住。
            if (s.state == "downloading" && !DownloadProgressStore.hasProgress(s.id)) {
                DownloadRepository.setState(s.id, "none")
                removeDownloadProgress(s.id)
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
                    // DASH下载需要音视频流都存在；无音轨视频（audio=null）只下视频流，不走合并
                    useDash = url_video.isNotEmpty() && url_audio.isNotEmpty()
                }
            }
            url_danmaku = data.danmakuUrl

            // 保存画质列表到元数据文件
            val downloadPath = downloadSection.getPath()
            if (downloadPath != null && downloadPath.exists()) {
                DownloadRepository.updateVideoMetaQualityLists(downloadPath, data.qnStrList, data.qnValueList)
            }
        } catch (e: JSONException) {
            DownloadRepository.setState(downloadSection.id, "error")
            notifier.notifyCompletion(getString(R.string.text_download_link_failed, downloadSection.name_short), downloadSection.id.toInt())
            section = null
            refreshDownloadList()
            return false
        } catch (e: IOException) {
            exitCode = DownloadPathSpec.ERR_NETWORK
            DownloadRepository.setState(downloadSection.id, "none")
            return false
        }

        try {
            DownloadRepository.setState(downloadSection.id, "downloading")

            // 更新进度追踪
            setDownloadProgress(downloadSection.id, 0f, getString(R.string.text_download_start))

            // 更新当前显示的section
            section = downloadSection
            percent = 0f
            state = getString(R.string.text_download_start)
            refreshDownloadList()

            var file_sign: File? = null
            var result: Int

            when (downloadSection.type) {
                DownloadPathSpec.TASK_VIDEO_SINGLE -> {
                    val path_single = downloadSection.getPath()

                    file_sign = File(path_single, DownloadPathSpec.FILE_DOWNLOADING)
                    if (!file_sign.exists() && !file_sign.createNewFile()) {
                        exitCode = DownloadPathSpec.ERR_FILE
                        return false
                    }

                    if (!downloadAttachments(
                            downloadSection, url_danmaku,
                            File(path_single, DownloadPathSpec.FILE_COVER), path_single,
                            File(path_single, DownloadPathSpec.FILE_DANMAKU), useDash
                        )) {
                        return false
                    }

                    if (downloadSection.isAudioOnly) {
                        // 音频下载：先写临时文件，成功后替换，避免失败破坏旧文件
                        val audioTmp = File(path_single, DownloadPathSpec.FILE_AUDIO_TMP)
                        state = getString(R.string.text_download_audio)
                        setDownloadProgress(downloadSection.id, 0.2f, getString(R.string.text_download_audio))
                        result = downFile(url_audio, audioTmp, downloadSection.id, 0.2f)
                        if (result != DownloadPathSpec.NORMAL) {
                            exitCode = result
                            return false
                        }
                        if (!replaceTempOrFail(audioTmp, File(path_single, DownloadPathSpec.FILE_AUDIO))) return false
                    } else if (useDash) {
                        // DASH分段进度：视频 0.12–0.52 → 音频 0.52–0.88 → 合并 0.88–1.00，附件已占 0.00–0.12。
                        // 下载完成后把音视频重新封装成一个 MP4（同 720P 的单文件形态）；合并失败保留双文件，
                        // 不判定下载失败。整条进度条单调前进（不再各自 0→100%）。
                        // 先下载到临时文件，全部成功后再替换正式文件（重新下载/切换清晰度时不破坏旧视频）
                        val videoTmp = File(path_single, DownloadPathSpec.FILE_VIDEO_TMP)
                        val audioTmp = File(path_single, DownloadPathSpec.FILE_AUDIO_TMP)
                        state = getString(R.string.text_download_video)
                        setDownloadProgress(downloadSection.id, DownloadProgressMath.DASH_ATTACH_END, getString(R.string.text_download_video))
                        result = downFile(url_video, videoTmp, downloadSection.id, DownloadProgressMath.DASH_ATTACH_END, DownloadProgressMath.DASH_VIDEO_END)
                        if (result != DownloadPathSpec.NORMAL) {
                            exitCode = result
                            return false
                        }
                        state = getString(R.string.text_download_audio)
                        setDownloadProgress(downloadSection.id, DownloadProgressMath.DASH_VIDEO_END, getString(R.string.text_download_audio))
                        result = downFile(url_audio, audioTmp, downloadSection.id, DownloadProgressMath.DASH_VIDEO_END, DownloadProgressMath.DASH_AUDIO_END)
                        if (result != DownloadPathSpec.NORMAL) {
                            exitCode = result
                            return false
                        }
                        val videoFinal = File(path_single, DownloadPathSpec.FILE_VIDEO)
                        val audioFinal = File(path_single, DownloadPathSpec.FILE_AUDIO)
                        if (!replaceDashPairOrFail(videoTmp, videoFinal, audioTmp, audioFinal)) return false
                        // 合并阶段只做容器重封装，几秒内完成，期间不响应暂停；但服务停止（started=false）要中止。
                        // 合并失败/跳过时双文件仍可正常播放，下载照常算成功。
                        if (isPaused(downloadSection.id)) return false
                        if (mergeDashPair(downloadSection.id, videoFinal, audioFinal) == MediaMerger.RESULT_STOPPED) return false
                        setDownloadProgress(downloadSection.id, DownloadProgressMath.DASH_MERGE_END, getString(R.string.text_update_download_complete))
                    } else {
                        // MP4格式或无音轨DASH：直接下载单个文件（音视频已合并/无音轨）
                        val videoTmp = File(path_single, DownloadPathSpec.FILE_VIDEO_TMP)
                        state = getString(R.string.text_download_video)
                        setDownloadProgress(downloadSection.id, 0.2f, getString(R.string.text_download_video))
                        result = downFile(url_video, videoTmp, downloadSection.id, 0.2f)
                        if (result != DownloadPathSpec.NORMAL) {
                            exitCode = result
                            return false
                        }
                        if (!replaceTempOrFail(videoTmp, File(path_single, DownloadPathSpec.FILE_VIDEO))) return false
                        // 清理旧 DASH 音频残留：从 DASH（video.mp4+audio.m4a）切换到普通 MP4 时，
                        // 旧的 audio.m4a 不会随新下载被覆盖，残留会导致播放器误判双文件、旧音频继续播放
                        val staleAudio = File(path_single, DownloadPathSpec.FILE_AUDIO)
                        if (staleAudio.exists()) staleAudio.delete()
                    }
                }
                DownloadPathSpec.TASK_VIDEO_MULTI -> {
                    val path_page = downloadSection.getPath()
                    val path_parent = path_page.parentFile

                    if (!path_page.exists() && !path_page.mkdirs()) {
                        exitCode = DownloadPathSpec.ERR_FILE
                        return false
                    }

                    file_sign = File(path_page, DownloadPathSpec.FILE_DOWNLOADING)
                    if (!file_sign.exists() && !file_sign.createNewFile()) {
                        exitCode = DownloadPathSpec.ERR_FILE
                        return false
                    }

                    if (!downloadAttachments(
                            downloadSection, url_danmaku,
                            File(path_parent, DownloadPathSpec.FILE_COVER), path_page,
                            File(path_page, DownloadPathSpec.FILE_DANMAKU), useDash
                        )) {
                        return false
                    }

                    if (downloadSection.isAudioOnly) {
                        // 音频下载：先写临时文件，成功后替换，避免失败破坏旧文件
                        val audioTmp = File(path_page, DownloadPathSpec.FILE_AUDIO_TMP)
                        state = getString(R.string.text_download_audio)
                        setDownloadProgress(downloadSection.id, 0.2f, getString(R.string.text_download_audio))
                        result = downFile(url_audio, audioTmp, downloadSection.id, 0.2f)
                        if (result != DownloadPathSpec.NORMAL) {
                            exitCode = result
                            return false
                        }
                        if (!replaceTempOrFail(audioTmp, File(path_page, DownloadPathSpec.FILE_AUDIO))) return false
                    } else if (useDash) {
                        // DASH分段进度：视频 0.12–0.52 → 音频 0.52–0.88 → 合并 0.88–1.00，附件已占 0.00–0.12。
                        // 下载完成后把音视频重新封装成一个 MP4（同 720P 的单文件形态）；合并失败保留双文件，
                        // 不判定下载失败。整条进度条单调前进（不再各自 0→100%）。
                        // 先下载到临时文件，全部成功后再替换正式文件（重新下载/切换清晰度时不破坏旧视频）
                        val videoTmp = File(path_page, DownloadPathSpec.FILE_VIDEO_TMP)
                        val audioTmp = File(path_page, DownloadPathSpec.FILE_AUDIO_TMP)
                        state = getString(R.string.text_download_video)
                        setDownloadProgress(downloadSection.id, DownloadProgressMath.DASH_ATTACH_END, getString(R.string.text_download_video))
                        result = downFile(url_video, videoTmp, downloadSection.id, DownloadProgressMath.DASH_ATTACH_END, DownloadProgressMath.DASH_VIDEO_END)
                        if (result != DownloadPathSpec.NORMAL) {
                            exitCode = result
                            return false
                        }
                        state = getString(R.string.text_download_audio)
                        setDownloadProgress(downloadSection.id, DownloadProgressMath.DASH_VIDEO_END, getString(R.string.text_download_audio))
                        result = downFile(url_audio, audioTmp, downloadSection.id, DownloadProgressMath.DASH_VIDEO_END, DownloadProgressMath.DASH_AUDIO_END)
                        if (result != DownloadPathSpec.NORMAL) {
                            exitCode = result
                            return false
                        }
                        val videoFinal = File(path_page, DownloadPathSpec.FILE_VIDEO)
                        val audioFinal = File(path_page, DownloadPathSpec.FILE_AUDIO)
                        if (!replaceDashPairOrFail(videoTmp, videoFinal, audioTmp, audioFinal)) return false
                        // 合并阶段只做容器重封装，几秒内完成，期间不响应暂停；但服务停止（started=false）要中止。
                        // 合并失败/跳过时双文件仍可正常播放，下载照常算成功。
                        if (isPaused(downloadSection.id)) return false
                        if (mergeDashPair(downloadSection.id, videoFinal, audioFinal) == MediaMerger.RESULT_STOPPED) return false
                        setDownloadProgress(downloadSection.id, DownloadProgressMath.DASH_MERGE_END, getString(R.string.text_update_download_complete))
                    } else {
                        // MP4格式或无音轨DASH：直接下载单个文件（音视频已合并/无音轨）
                        val videoTmp = File(path_page, DownloadPathSpec.FILE_VIDEO_TMP)
                        state = getString(R.string.text_download_video)
                        setDownloadProgress(downloadSection.id, 0.2f, getString(R.string.text_download_video))
                        result = downFile(url_video, videoTmp, downloadSection.id, 0.2f)
                        if (result != DownloadPathSpec.NORMAL) {
                            exitCode = result
                            return false
                        }
                        if (!replaceTempOrFail(videoTmp, File(path_page, DownloadPathSpec.FILE_VIDEO))) return false
                        // 清理旧 DASH 音频残留：从 DASH（video.mp4+audio.m4a）切换到普通 MP4 时，
                        // 旧的 audio.m4a 不会随新下载被覆盖，残留会导致播放器误判双文件、旧音频继续播放
                        val staleAudio = File(path_page, DownloadPathSpec.FILE_AUDIO)
                        if (staleAudio.exists()) staleAudio.delete()
                    }
                }
            }

            notifier.notifyCompletion(getString(R.string.text_download_success, downloadSection.name_short), downloadSection.id.toInt())

            // 移除进度映射
            removeDownloadProgress(downloadSection.id)

            if (file_sign != null && file_sign.exists())
                file_sign.delete()

            DownloadRepository.deleteSection(downloadSection.id)
            refreshLocalList()

            return true
        } catch (e: IOException) {
            exitCode = DownloadPathSpec.ERR_FILE
            DownloadRepository.setState(downloadSection.id, "error")
            return false
        }
    }

    /**
     * 临时文件替换正式文件（备份→替换→恢复），返回是否成功。
     * 下载先写临时文件、全部成功后才替换，避免下载期间破坏旧视频；
     * 替换失败时恢复旧文件，保证重新下载（切换清晰度）不丢旧数据。
     *
     * 审计 M11-c：原来返回 void，替换失败只留一行日志，调用方照样
     * `notifyCompletion("下载成功")` 并删掉下载记录 —— 用户以为下好了，实际文件没换过去。
     *
     * @param keepBackup 替换成功后**保留** .bak（默认删掉）。DASH 的 video/audio 要成对替换，
     *                   video 先换、audio 换成功后才允许删 video 的备份；否则 audio 失败时无法
     *                   把 video 回滚到替换前 —— 见 [replaceDashPair]。
     */
    private fun safeReplaceTemp(tmpFile: File, finalFile: File, keepBackup: Boolean = false): Boolean {
        if (!tmpFile.exists()) {
            Logu.e("DownloadService", "替换失败：临时文件不存在 ${tmpFile.name}")
            return false
        }
        val bakFile = File(finalFile.parentFile, finalFile.name + ".bak")
        var ok = false
        try {
            // 无旧文件，或旧文件成功备份到 .bak，才走正常替换
            if (!finalFile.exists() || finalFile.renameTo(bakFile)) {
                if (tmpFile.renameTo(finalFile)) {
                    if (!keepBackup && bakFile.exists()) bakFile.delete()
                    ok = true
                } else {
                    // 替换失败：恢复旧文件，保留临时文件供排查
                    if (bakFile.exists()) bakFile.renameTo(finalFile)
                    Logu.e("DownloadService", "替换文件失败：${finalFile.name}，已保留旧文件")
                }
            } else {
                // 无法备份（罕见）：直接尝试覆盖（Android 上 rename 可覆盖目标）
                ok = tmpFile.renameTo(finalFile)
                if (ok) tmpFile.delete()
                else Logu.e("DownloadService", "备份与覆盖均失败：${finalFile.name}")
            }
        } catch (e: Exception) {
            Logu.e("DownloadService", "替换文件异常：${finalFile.name} ${e.message}")
            if (bakFile.exists() && !finalFile.exists()) {
                try { bakFile.renameTo(finalFile) } catch (_: Exception) {}
            }
        }
        return ok
    }

    /**
     * [safeReplaceTemp] 的调用方包装（审计 M11-c）。
     * 只负责置错误码并让调用方 return false —— 失败后的状态收敛
     * （recordFailure / setState "error" / exitMessage）统一由 runDownloadSection 处理，
     * 这里不要重复设置，也不要用共享的 section 字段去猜当前任务。
     */
    private fun replaceTempOrFail(tmpFile: File, finalFile: File): Boolean {
        if (safeReplaceTemp(tmpFile, finalFile)) return true
        exitCode = DownloadPathSpec.ERR_FILE
        return false
    }

    /**
     * DASH 的 video/audio 是一对配套文件，必须成对替换到**同一代**。
     *
     * 原来顺序调两次 safeReplaceTemp：video 换好、audio 失败时，磁盘上留下「新视频 + 旧音频」，
     * 播放器按旧音频时间轴播新视频，画面与声音对不上；用户看到的是"下载失败"，实际却已被污染。
     *
     * 做法：video 替换时保留旧成品备份 → audio 替换成功才删备份；audio 失败则把 video 从备份
     * 还原，整体判失败（两个临时文件都留着，下次重下不必从头来）。
     * 返回 false 时调用方直接结束本次下载，状态由 runDownloadSection 统一收敛。
     */
    private fun replaceDashPair(videoTmp: File, videoFinal: File, audioTmp: File, audioFinal: File): Boolean {
        if (!videoTmp.exists() || !audioTmp.exists()) {
            Logu.e("DownloadService", "DASH 替换失败：临时文件缺失 video=${videoTmp.exists()} audio=${audioTmp.exists()}")
            return false
        }
        val videoBak = File(videoFinal.parentFile, videoFinal.name + ".bak")
        // 先记住有没有旧成品：没有的话"回滚"就是删掉刚换上的新视频，回到下载前的状态
        val hadOldVideo = videoFinal.exists()
        if (!safeReplaceTemp(videoTmp, videoFinal, keepBackup = true)) return false
        if (safeReplaceTemp(audioTmp, audioFinal)) {
            if (videoBak.exists()) videoBak.delete()
            return true
        }
        // 音频替换失败：把视频回滚到替换前的旧成品，避免「新视频 + 旧音频」的混代
        if (hadOldVideo && videoBak.exists()) {
            // 先删掉刚换上的新视频再改名：renameTo 在目标已存在时并非所有实现都会覆盖，
            // 先删一次才能保证这一步是真正的"回滚"而不是又留一对混代文件
            videoFinal.delete()
            if (videoBak.renameTo(videoFinal)) {
                Logu.e("DownloadService", "DASH 音频替换失败，已把视频回滚到旧文件")
            } else {
                Logu.e("DownloadService", "DASH 音频替换失败，且视频回滚失败，旧视频保留在 ${videoBak.name}")
            }
        } else if (!hadOldVideo) {
            // 下载前本来没有视频（例如从「仅音频」改成 DASH）：删掉刚换上的新视频，
            // 回到下载前的状态，不留孤立的新视频配旧音频
            videoFinal.delete()
            Logu.e("DownloadService", "DASH 音频替换失败，已删除新视频回到下载前状态")
        } else {
            Logu.e("DownloadService", "DASH 音频替换失败，且视频回滚失败：${videoFinal.name}")
        }
        return false
    }

    /** [replaceDashPair] 的调用方包装，语义同 [replaceTempOrFail]。 */
    private fun replaceDashPairOrFail(videoTmp: File, videoFinal: File, audioTmp: File, audioFinal: File): Boolean {
        if (replaceDashPair(videoTmp, videoFinal, audioTmp, audioFinal)) return true
        exitCode = DownloadPathSpec.ERR_FILE
        return false
    }

    /**
     * DASH 音视频合并（双文件替换到位之后调用）。
     *
     * 把 `video.mp4` + `audio.m4a` 重新封装成一个 MP4：成功时用合并产物替换 `video.mp4` 并删掉
     * `audio.m4a`；失败/跳过时**原样保留双文件**（播放器仍能挂外部音轨正常播放），下载照常算成功。
     * 合并只换容器、不重新编码，所以画质无损、耗时通常只有几秒。
     *
     * @return [MediaMerger.RESULT_OK] 合并成功；[MediaMerger.RESULT_STOPPED] 服务停止导致中止，
     *         调用方应按"下载未完成"处理；其它值表示没合并，调用方继续走成功收尾
     */
    private fun mergeDashPair(sectionId: Long, videoFile: File, audioFile: File): Int {
        if (!videoFile.isFile || !audioFile.isFile) {
            Logu.w("DownloadService", "DASH 合并跳过：成品缺失（video=${videoFile.exists()} audio=${audioFile.exists()}）")
            return MediaMerger.RESULT_FAILED
        }
        val mergedTmp = File(videoFile.parentFile, DownloadPathSpec.FILE_VIDEO_MERGING)
        val mergeState = getString(R.string.text_download_merging)
        state = mergeState
        setDownloadProgress(sectionId, DownloadProgressMath.DASH_AUDIO_END, mergeState)
        val mergeStartAt = System.currentTimeMillis()
        val result = MediaMerger.mergeAv(
            videoFile = videoFile,
            audioFile = audioFile,
            mergedFile = mergedTmp,
            onProgress = { fraction ->
                setDownloadProgress(sectionId, DownloadProgressMath.dashMergeProgress(fraction), mergeState)
            },
            // 合并只做容器重封装，不响应暂停；服务被销毁（started=false）时必须停手
            shouldStop = { !started }
        )
        val cost = System.currentTimeMillis() - mergeStartAt
        if (result != MediaMerger.RESULT_OK) {
            Logu.w("DownloadService", "DASH 合并未完成（result=$result，耗时${cost}ms），保留双文件")
            return result
        }
        if (!safeReplaceTemp(mergedTmp, videoFile)) {
            Logu.e("DownloadService", "DASH 合并产物替换失败，保留双文件")
            return MediaMerger.RESULT_FAILED
        }
        audioFile.delete()
        Logu.i("DownloadService", "DASH 合并完成：${videoFile.name}（耗时${cost}ms，${videoFile.length()} 字节）")
        return MediaMerger.RESULT_OK
    }

    /**
     * 附件阶段（封面 + 字幕 + 弹幕）统一处理。
     * DASH 流程：附件属于视频的前置开销，只占进度条 0.00–0.12（封面 0-0.04，字幕 0.05，
     * 弹幕 0.06-0.12），随后视频段 0.12–0.52、音频段 0.52–0.88、合并段 0.88–1.00，整条进度条**单调前进**。
     * （旧实现让每段各自 0→100%，DASH 下载一次要走满三遍，用户看到的就是"进度条来回满三次"。）
     * 其他流程（MP4 单文件/纯音频）：保持旧的比例区间（封面 0.05-0.1，字幕 0.1，弹幕 0.15）。
     * @return 是否成功；false 时调用方应直接结束下载
     */
    private fun downloadAttachments(
        downloadSection: DownloadSection,
        url_danmaku: String,
        coverFile: File,
        subtitleFolder: File,
        danmakuFile: File,
        useDash: Boolean
    ): Boolean {
        // 封面
        state = getString(R.string.text_download_cover)
        setDownloadProgress(downloadSection.id, if (useDash) 0f else 0.05f, getString(R.string.text_download_cover))
        if (!coverFile.exists() && downloadSection.url_cover.isNotEmpty()) {
            val result = if (useDash)
                downFile(downloadSection.url_cover, coverFile, downloadSection.id, 0f, 0.04f)
            else
                downFile(downloadSection.url_cover, coverFile, downloadSection.id, 0.05f, 0.1f)
            if (result != DownloadPathSpec.NORMAL) {
                exitCode = result
                return false
            }
        }

        if (!downloadSection.isAudioOnly) {
            // 字幕
            state = getString(R.string.text_download_subtitle)
            setDownloadProgress(downloadSection.id, if (useDash) 0.05f else 0.1f, getString(R.string.text_download_subtitle))
            downSubtitles(downloadSection.aid, downloadSection.cid, subtitleFolder)

            // 弹幕：DASH 传 0.07 使完成后进度为 0.12（与视频段的 0.12 起点接上）
            state = getString(R.string.text_download_danmaku)
            setDownloadProgress(downloadSection.id, if (useDash) 0.06f else 0.15f, getString(R.string.text_download_danmaku))
            val result = if (useDash)
                downDanmaku(url_danmaku, danmakuFile, downloadSection.id, 0.07f)
            else
                downDanmaku(url_danmaku, danmakuFile, downloadSection.id, 0.15f)
            if (result != DownloadPathSpec.NORMAL) {
                exitCode = result
                return false
            }
        }
        return true
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
                return DownloadPathSpec.NORMAL

            val subtitleFolder = File(folder, DownloadPathSpec.DIR_SUBTITLES)
            // 审计 M11-e：mkdirs() / createNewFile() 在"目标已存在"时返回 false，
            // 原来用它判失败，导致目录或同名 JSON 已存在（重下、续传）时直接报 DownloadPathSpec.ERR_FILE。
            if (!subtitleFolder.exists() && !subtitleFolder.mkdirs())
                return DownloadPathSpec.ERR_FILE
            for (subtitleLink in subtitleLinks) {
                if (subtitleLink.id != -1L) {
                    val subtitleFile = File(subtitleFolder, subtitleLink.lang + ".json")
                    if (!resetFile(subtitleFile))
                        return DownloadPathSpec.ERR_FILE
                    val result = downFile(subtitleLink.url, subtitleFile)
                    if (result != DownloadPathSpec.NORMAL)
                        return result
                }
            }
        } catch (e: IOException) {
            return DownloadPathSpec.ERR_NETWORK
        } catch (e: JSONException) {
            return DownloadPathSpec.ERR_JSON
        }
        return DownloadPathSpec.NORMAL
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
            return DownloadPathSpec.ERR_NETWORK
        }
        // 校验响应码：防盗链失败(403)或URL过期时会返回非2xx，错误页不能当文件写入
        if (!response.isSuccessful) {
            Logu.e("DownloadService", "下载失败，HTTP ${response.code}: ${file.name}")
            response.close()
            return DownloadPathSpec.ERR_NETWORK
        }
        var inputStream: InputStream? = null
        var fileOutputStream: FileOutputStream? = null
        var result = DownloadPathSpec.NORMAL
        var fileIncomplete = false
        try {
            if (!resetFile(file))
                return DownloadPathSpec.ERR_FILE

            val body = response.body
            if (body == null) return DownloadPathSpec.ERR_NETWORK

            inputStream = body.byteStream()
            fileOutputStream = FileOutputStream(file)
            var len: Int
            val bytes = ByteArray(256 * 1024)
            val TotalFileSize = body.contentLength()
            var totalDown: Long = 0
            var lastProgressUpdate = 0L
            // 未知总大小时按 512KB 节流推进伪进度（封顶 90%），避免进度条卡死不动
            val progressUpdateInterval = if (TotalFileSize > 0) Math.max(TotalFileSize / 1000, 65536) else 512 * 1024
            var pseudoProgress = 0f
            while ((inputStream.read(bytes).also { len = it }) != -1 && started && !isPaused(sectionId)) {
                fileOutputStream.write(bytes, 0, len)
                totalDown += len
                addDownloadedBytes(len.toLong())
                if (totalDown - lastProgressUpdate >= progressUpdateInterval) {
                    lastProgressUpdate = totalDown
                    if (TotalFileSize > 0) {
                        percent = DownloadProgressMath.progressForBytes(totalDown, TotalFileSize, baseProgress, endProgress)
                    }

                    // 更新进度到进度映射表
                    if (sectionId > 0) {
                        val actualProgress = if (TotalFileSize > 0) {
                            DownloadProgressMath.progressForBytes(totalDown, TotalFileSize, baseProgress, endProgress)
                        } else {
                            pseudoProgress = DownloadProgressMath.pseudoProgressStep(pseudoProgress)
                            DownloadProgressMath.progressForPseudo(pseudoProgress, baseProgress, endProgress)
                        }
                        setDownloadProgress(sectionId, actualProgress, state ?: getString(R.string.text_download_in_progress), totalDown, TotalFileSize)
                    }
                }
            }
            if (TotalFileSize <= 0) {
                percent = endProgress
            } else {
                percent = DownloadProgressMath.progressForBytes(totalDown, TotalFileSize, baseProgress, endProgress)
            }
            // 用户暂停：优先返回暂停信号（不视为错误，不清理半成品，恢复后重新下载覆盖）
            if (isPaused(sectionId)) {
                result = DownloadPathSpec.ERR_PAUSED
            } else if (TotalFileSize > 0 && totalDown < TotalFileSize) {
                fileIncomplete = true
                Logu.e("DownloadService", "下载不完整：${file.name} ${totalDown}/${TotalFileSize}")
                result = DownloadPathSpec.ERR_NETWORK
            } else if (!started) {
                result = DownloadPathSpec.ERR_UNKNOWN
            }
        } catch (e: IOException) {
            fileIncomplete = true
            result = DownloadPathSpec.ERR_FILE
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { fileOutputStream?.close() } catch (_: Exception) {}
            try { response.body?.close() } catch (_: Exception) {}
            response.close()
        }
        // 失败时清理半成品文件，避免残留损坏文件导致合并/播放异常
        if (result != DownloadPathSpec.NORMAL && fileIncomplete) {
            try { if (file.exists()) file.delete() } catch (_: Exception) {}
        }
        // 下载成功后把进度推进到阶段终点，保证阶段进度条走满（分段进度依赖）
        if (result == DownloadPathSpec.NORMAL && sectionId > 0) {
            setDownloadProgress(sectionId, endProgress, state ?: getString(R.string.text_download_in_progress))
        }
        return result
    }

    /** 下载专用 OkHttpClient 的缓存（每个 Service 实例一份，随批创建随批回收）。 */
    @Volatile
    private var downloadOkHttpClient: okhttp3.OkHttpClient? = null

    /**
     * 下载专用的 OkHttpClient：在 App 统一客户端（带 Cookie / 手动跟跳 / 登录态保存拦截器）基础上
     * 强制 HTTP/1.1。
     *
     * 原因：分片下载是"多条并发请求"，而 h2 会把它们复用进同一条 TCP 连接、共享一个连接窗口，
     * 单条连接的吞吐成为上限。实测同一个 B 站 CDN 直链（51.97MB）：
     * 5 条 h1.1 连接聚合约 10.4MB/s，5 条 h2 流共用一条连接只有约 4.9MB/s，差一倍。
     * 下载只关心吞吐，不需要 h2 的多路复用；多个 TCP 连接也避免了"一条流卡住拖累整条连接"。
     */
    private fun downloadClient(): okhttp3.OkHttpClient {
        downloadOkHttpClient?.let { return it }
        val built = NetWorkUtil.getOkHttpInstance().newBuilder()
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .build()
        downloadOkHttpClient = built
        return built
    }

    @Throws(IOException::class)
    private fun downFileSpeed(url: String, file: File, sectionId: Long = -1, baseProgress: Float = 0f, endProgress: Float = 1.0f): Int {
        if (!resetFile(file))
            return DownloadPathSpec.ERR_FILE

        val client = downloadClient()
        val headers = NetWorkUtil.webHeaders

        // 本次运行已判定分片不可用：直接单连接下载。单连接不需要预知文件大小，
        // 少发一次 HEAD 也就少一次可能卡住的往返。
        if (segmentModeDisabled) {
            return downFileSpeedSingle(url, file, client, headers, -1L, sectionId, baseProgress, endProgress)
        }

        val headBuilder = okhttp3.Request.Builder().url(url).head()
        var i = 0
        while (i < headers.size) {
            headBuilder.addHeader(headers[i], headers[i + 1])
            i += 2
        }
        val headReq = headBuilder.build()

        // HEAD 单独加 callTimeout：OkHttp 的 connect/read 超时管不住"连上了但一直不给响应"的情况，
        // 而分片阶段要等 HEAD 回来才知道能不能分片；HEAD 挂住 = 用户在 20% 上干等。
        // 一次只要响应头的 HEAD 给 15s 足够宽裕，超时就当"不知道长度"，直接走单连接。
        val headClient = client.newBuilder()
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val (totalSize, supportsRange) = try {
            headClient.newCall(headReq).execute().use { headRes ->
                // HEAD 被拒绝时，Content-Length 是错误页长度，不能拿来校验媒体文件。
                val len = if (headRes.isSuccessful) headRes.header("Content-Length") else null
                val size = len?.toLongOrNull()?.takeIf { it > 0 } ?: -1L
                val range = headRes.isSuccessful && "bytes" == headRes.header("Accept-Ranges")
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

        var effectiveTotalSize = -1L
        var downloaded = 0L
        var singleResult = DownloadPathSpec.ERR_NETWORK
        try {
            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                Logu.e("DownloadService", "下载连接失败：${file.name}")
                singleResult = DownloadStreamTransfer.failureCode(started, isPaused(sectionId), DownloadPathSpec.ERR_NETWORK)
                return singleResult
            }
            response.use {
                if (!response.isSuccessful || response.body == null) {
                    Logu.e("DownloadService", "下载失败，HTTP ${response.code}：${file.name}")
                    singleResult = DownloadStreamTransfer.failureCode(started, isPaused(sectionId), DownloadPathSpec.ERR_NETWORK)
                    return singleResult
                }
                val body = response.body!!
                effectiveTotalSize = DownloadStreamTransfer.expectedBytes(totalSize, body.contentLength())
                var lastProgressUpdate = 0L
                val progressUpdateInterval = if (effectiveTotalSize > 0)
                    Math.max(effectiveTotalSize / 1000, 65536) else 512 * 1024L
                var pseudoProgress = 0f
                singleResult = DownloadPathSpec.ERR_FILE // 在 copy() 返回前失败，说明开流/开文件未完成。
                body.byteStream().use { inputStream ->
                    FileOutputStream(file).use { fos ->
                        val transfer = DownloadStreamTransfer.copy(inputStream, fos, effectiveTotalSize,
                            isRunning = { started }, isPaused = { isPaused(sectionId) }) { total, delta ->
                            downloaded = total
                            addDownloadedBytes(delta.toLong())
                            if (total - lastProgressUpdate >= progressUpdateInterval) {
                                lastProgressUpdate = total
                                val actualProgress = if (effectiveTotalSize > 0) {
                                    DownloadProgressMath.progressForBytes(total, effectiveTotalSize, baseProgress, endProgress)
                                } else {
                                    pseudoProgress = DownloadProgressMath.pseudoProgressStep(pseudoProgress)
                                    DownloadProgressMath.progressForPseudo(pseudoProgress, baseProgress, endProgress)
                                }
                                percent = actualProgress
                                if (sectionId > 0) {
                                    setDownloadProgress(sectionId, actualProgress, state ?: getString(R.string.text_download_in_progress), total, effectiveTotalSize)
                                }
                            }
                        }
                        downloaded = transfer.downloadedBytes
                        singleResult = transfer.code
                    }
                }
            }
        } catch (e: IOException) {
            // 网络读取失败由 copy() 返回 ERR_NETWORK；开文件/关闭异常保留已有失败及暂停语义。
            val errorCode = if (singleResult == DownloadPathSpec.NORMAL) DownloadPathSpec.ERR_FILE else singleResult
            singleResult = DownloadStreamTransfer.failureCode(started, isPaused(sectionId), errorCode)
            Logu.e("DownloadService", "下载文件写入或关闭失败：${file.name}")
        } finally {
            // 资源关闭后仅清理本次下载目标，绝不删除旧成品所在的任务目录；暂停保留临时文件。
            if (singleResult != DownloadPathSpec.NORMAL && singleResult != DownloadPathSpec.ERR_PAUSED) {
                try {
                    if (file.exists() && !file.delete()) {
                        Logu.e("DownloadService", "清理未完成下载失败：${file.name}")
                    }
                } catch (e: Exception) {
                    Logu.e("DownloadService", "清理未完成下载异常：${file.name}")
                }
            }
        }
        if (singleResult == DownloadPathSpec.ERR_NETWORK) {
            Logu.e("DownloadService", "下载未完成：${file.name} $downloaded/$effectiveTotalSize")
        }
        // 只有读到 EOF 且字节校验通过、文件关闭成功，才推进阶段终点并允许调用方替换成品。
        if (singleResult == DownloadPathSpec.NORMAL) {
            percent = endProgress
            if (sectionId > 0) {
                setDownloadProgress(sectionId, endProgress, state ?: getString(R.string.text_download_in_progress), downloaded, effectiveTotalSize)
            }
        }
        return singleResult
    }

    @Throws(IOException::class)
    private fun downFileSpeedSeg(url: String, file: File, client: okhttp3.OkHttpClient,
                                  headers: ArrayList<String>, totalSize: Long,
                                  sectionId: Long = -1, baseProgress: Float = 0f, endProgress: Float = 1.0f): Int {
        // 预分配整个文件长度：分片线程各自 seek 到自己的区间写入，不预分配就无从随机写。
        java.io.RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(totalSize)
        }

        // 动态分片：约每 2MB 一片，上限受用户配置的分片数约束
        val segments = DownloadProgressMath.segmentCount(totalSize, Aria2Util.getSplit())
        val segmentLen = DownloadProgressMath.segmentLen(totalSize, segments)
        val shared = SegmentSharedState(segments)

        val threads = java.util.ArrayList<Thread>(segments)

        for (i in 0 until segments) {
            val segmentRange = DownloadProgressMath.segmentRange(i, segments, totalSize, segmentLen)
            val start = segmentRange.first
            val end = segmentRange.last
            val idx = i

            val thread = Thread({
                downloadSegment(url, file, client, headers, start, end, idx, sectionId, shared)
            }, "DL-Segment-$idx")
            threads.add(thread)
            thread.start()
        }
        Logu.d("DownloadService", "分片下载开始：$segments 片 / $totalSize 字节：${file.name}")

        // 轮询进度，直到全部分片结束、失败、暂停、停滞或取消
        var stalled = false
        while (completedSegments(threads) < segments && started && !shared.anyFailed.get() && !isPaused(sectionId)) {
            // 看门狗（"卡在 20%"的核心修复）：没有它，一个卡住的分片要等 OkHttp 16s 读超时
            // × 3 次重试（最坏 48s）才失败，这期间进度一个字节都不动，用户看到的就是"卡住"。
            // 触发后不再干等：取消在飞 Call 并退回整文件单连接下载（见下面的回退分支）。
            if (shared.stalled(threads)) {
                stalled = true
                Logu.e("DownloadService", "分片下载停滞 ${SegmentDownloadPolicy.STALL_TIMEOUT_MS}ms，改用单连接：${file.name}")
                break
            }
            val actualProgress = DownloadProgressMath.progressForBytes(shared.totalDownloaded.get(), totalSize, baseProgress, endProgress)
            percent = actualProgress

            // 更新进度到进度映射表
            if (sectionId > 0) {
                setDownloadProgress(sectionId, actualProgress, state ?: getString(R.string.text_download_in_progress), shared.totalDownloaded.get(), totalSize)
            }

            try {
                Thread.sleep(200)
            } catch (ignored: InterruptedException) {
            }
        }

        // 没收齐就退出轮询（停滞 / 失败 / 暂停 / 服务销毁）：先把分片线程彻底停掉。
        // 用 cancel() 而不是干等：阻塞中的 socket 读只有取消能立刻打断。
        val needAbort = stalled || shared.anyFailed.get() || !started || isPaused(sectionId)
        if (needAbort) shared.abort()

        // 等待线程结束（带超时），避免遗留写盘。
        // 超时是"整批分片"的总预算而不是每个线程各 30s（审计 M11-g）：
        // 原来逐个 t.join(30000)，32 个分片最坏会阻塞 16 分钟。
        // 取消场景只给 3s（线程还在写就不能重下），正常收尾仍是 30s。
        val joinBudget = if (needAbort) SegmentDownloadPolicy.ABORT_JOIN_TIMEOUT_MS
                         else SegmentDownloadPolicy.FINISH_JOIN_TIMEOUT_MS
        val joinDeadline = System.currentTimeMillis() + joinBudget
        for (t in threads) {
            val remain = joinDeadline - System.currentTimeMillis()
            if (remain <= 0) break
            try {
                t.join(remain)
            } catch (ignored: InterruptedException) {
            }
        }

        // 用户暂停：直接返回暂停信号，不回退重下
        if (isPaused(sectionId)) return DownloadPathSpec.ERR_PAUSED
        // 服务销毁（onDestroy 置 started=false）时不必再走单连接重下
        if (!started) return DownloadPathSpec.ERR_UNKNOWN
        // 分片线程没能在预算内退出：绝不能起单线程重下，两拨线程写同一个文件必然损坏。
        // 宁可这次任务失败（上层会清掉进度映射并置 error），也不要写出一份坏文件。
        if (threads.any { it.isAlive }) {
            Logu.e("DownloadService", "分片线程 ${joinBudget}ms 内未退出，放弃本次下载：${file.name}")
            return DownloadPathSpec.ERR_NETWORK
        }

        // 结构性原因（服务端忽略 Range / 链路停滞）说明分片在当前网络下不可用，
        // 本次进程内直接退化为单连接，避免用户每下一个文件都先卡一轮。
        if (SegmentDownloadPolicy.shouldDisableSegmentMode(shared.rangeUnsupported.get(), stalled)) {
            segmentModeDisabled = true
            Logu.e("DownloadService", "本次运行不再使用分片下载（Range 不可用或链路停滞）")
        }

        // 完整性校验：任一失败、Range 不可用、停滞、下载字节不足、或落盘尺寸与总长不符都视为
        // 失败，回退整文件单线程重下。最后那项（file.length()）是兜底：`setLength` 在某些
        // FUSE/exFAT 存储上可能静默失败，此时即使每片都写满 expectLen，文件的实际尺寸也未必对。
        if (stalled || shared.rangeUnsupported.get() || shared.anyFailed.get() ||
            shared.totalDownloaded.get() < totalSize || file.length() != totalSize
        ) {
            // 回退前必须把分片阶段已经计入的字节从全局计数里扣掉（审计 S7）：
            // downFileSpeedSingle 会用 FileOutputStream 截断重写整个文件，并再次
            // addDownloadedBytes()，不回滚就让 totalBytesDownloaded 把同一批字节算两遍，
            // sampleSpeed() 得出的速度与批次统计虚高。
            val segBytes = shared.totalDownloaded.get()
            if (segBytes > 0) addDownloadedBytes(-segBytes)
            // 进度同步退回阶段起点：单线程重下会从 0 重新推进 percent，
            // 若保留分片阶段的高位进度，进度条会先满后退。
            if (sectionId > 0) {
                setDownloadProgress(sectionId, baseProgress, state ?: getString(R.string.text_download_in_progress), 0L, totalSize)
            }
            return downFileSpeedSingle(url, file, client, headers, totalSize, sectionId, baseProgress, endProgress)
        }

        val segResult = if (started) DownloadPathSpec.NORMAL else DownloadPathSpec.ERR_UNKNOWN
        // 下载成功后把进度推进到阶段终点，保证阶段进度条走满（分段进度依赖）
        if (segResult == DownloadPathSpec.NORMAL && sectionId > 0) {
            setDownloadProgress(sectionId, endProgress, state ?: getString(R.string.text_download_in_progress))
        }
        return segResult
    }

    /**
     * 一次分片下载的线程间共享状态。
     * 只放必须跨线程共享的原子量与并发容器，避免把 Service 的实例字段牵进分片线程。
     */
    private class SegmentSharedState(val segments: Int) {
        /** 全部分片累计写入的字节（某片失败重试时会扣回已写入的部分）。 */
        val totalDownloaded = java.util.concurrent.atomic.AtomicLong(0)

        /** 任一分片 3 次重试都拿不到完整数据。 */
        val anyFailed = java.util.concurrent.atomic.AtomicBoolean(false)

        /** 服务端忽略 Range（返回 200 全量，或 Content-Range 与请求分片对不上）。 */
        val rangeUnsupported = java.util.concurrent.atomic.AtomicBoolean(false)

        /** 看门狗判定停滞、或用户暂停 / 服务销毁：分片线程据此尽快退出。 */
        val aborted = java.util.concurrent.atomic.AtomicBoolean(false)

        /** 每个分片当前在飞的 Call，卡住时 cancel() 能立刻打断阻塞中的 socket 读。 */
        val calls = java.util.concurrent.CopyOnWriteArrayList<okhttp3.Call>()

        /** 每个分片最后一次收到字节的时刻（初值是起跑时刻），看门狗据此判停滞。 */
        val lastByteAt = java.util.concurrent.atomic.AtomicLongArray(segments)

        init {
            val now = System.currentTimeMillis()
            for (i in 0 until segments) lastByteAt.set(i, now)
        }

        fun markByte(idx: Int) {
            lastByteAt.set(idx, System.currentTimeMillis())
        }

        /** 先置 aborted 再取消在飞请求：分片线程看到 aborted 就不会把取消当成"该重试"。 */
        fun abort() {
            aborted.set(true)
            for (call in calls) {
                try {
                    call.cancel()
                } catch (_: Exception) {
                }
            }
        }

        /** 有分片线程存活，且超过看门狗预算没收到任何新字节。 */
        fun stalled(threads: List<Thread>): Boolean {
            val now = System.currentTimeMillis()
            for (i in 0 until segments) {
                val thread = threads.getOrNull(i) ?: continue
                if (thread.isAlive && SegmentDownloadPolicy.hasStalled(lastByteAt.get(i), now)) return true
            }
            return false
        }
    }

    /** 下载单个分片（最多重试 3 次），成功后累加已下载字节。 */
    private fun downloadSegment(url: String, file: File, client: okhttp3.OkHttpClient,
                                headers: ArrayList<String>, start: Long, end: Long, idx: Int,
                                sectionId: Long,
                                shared: SegmentSharedState) {
        val expectLen = end - start + 1
        var attempts = 0
        while (attempts < 3 && started && !shared.anyFailed.get() && !isPaused(sectionId) && !shared.aborted.get()) {
            var written = 0L
            var ok = false
            var call: okhttp3.Call? = null
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
                val newCall = client.newCall(reqBuilder.build())
                call = newCall
                shared.calls.add(newCall)
                // 加入在飞列表后再查一次 aborted：取消方可能刚好在上一步扫过列表，
                // 漏掉这条请求就会白等到读超时。
                if (shared.aborted.get()) newCall.cancel()
                newCall.execute().use { resp ->
                    // 服务端忽略 Range 会回 200 + 整个文件：此时绝不能按分片读，
                    // 否则每片都把整个文件读一遍（5 片 = 5 倍流量），慢网下看起来就是下载不动。
                    if (SegmentDownloadPolicy.isRangeIgnored(resp.code)) {
                        shared.rangeUnsupported.set(true)
                        throw IOException("服务器忽略 Range（HTTP 200）")
                    }
                    if (!resp.isSuccessful || resp.body == null) throw IOException("HTTP ${resp.code}")
                    // 分片响应必须是 206：不是 206 就说明拿到的不是我们要的那一段。
                    if (!SegmentDownloadPolicy.isPartialContent(resp.code)) throw IOException("HTTP ${resp.code}")
                    // Content-Range 带了起点就必须和请求对上（部分镜像不带这个头，此时不强判）
                    if (SegmentDownloadPolicy.isContentRangeMismatch(resp.header("Content-Range"), start)) {
                        shared.rangeUnsupported.set(true)
                        throw IOException("Content-Range 与请求的分片不符")
                    }
                    val buffer = ByteArray(65536)
                    resp.body!!.byteStream().use { inputStream ->
                        java.io.RandomAccessFile(file, "rw").use { rafInner ->
                            rafInner.seek(start)
                            while (written < expectLen && started && !isPaused(sectionId) && !shared.aborted.get()) {
                                val read = inputStream.read(buffer)
                                if (read == -1) break
                                // 只写本片范围内的字节：服务端若超出 Range 多发了数据，
                                // 多出来的部分绝不能覆盖下一片已经写好的内容。
                                val toWrite = if (read.toLong() > expectLen - written) (expectLen - written).toInt() else read
                                if (toWrite <= 0) break
                                rafInner.write(buffer, 0, toWrite)
                                written += toWrite
                                // 分片内**逐块**计入进度，而不是等整片写完才加一次（审计 A2）：
                                // 2MB 一片时整片完成前进度纹丝不动，进度条就是 2MB 一跳，
                                // 剩余时间也跟着虚高；失败重试时下面的回滚负责扣回。
                                shared.totalDownloaded.addAndGet(toWrite.toLong())
                                addDownloadedBytes(toWrite.toLong())
                                shared.markByte(idx)
                            }
                        }
                    }
                }
                ok = started && written == expectLen && !shared.aborted.get()
            } catch (e: Exception) {
                ok = false
                // 分片失败原因必须留痕：不看日志的话，"服务端忽略 Range / 区间对不上"
                // 只表现为"多线程慢、退回单连接"，根本查不出来。
                if (attempts == 2) {
                    Logu.e("DownloadService", "分片 $idx（$start-$end）3 次尝试均失败：${e.message}")
                }
            } finally {
                call?.let { shared.calls.remove(it) }
            }
            if (ok) {
                // 字节已在循环里逐块计入 totalDownloaded，这里不能再加一次
                return
            }
            // 本次尝试没拿到完整分片（EOF 早停 / 异常 / 被暂停）：把这段已计入全局计数器的字节扣回。
            // 重试成功后会从分片起点整片重算，不扣就把同一段字节算成两次，sampleSpeed() 与批次统计虚高；
            // 分片阶段最终失败时 downFileSpeedSeg 还会按 totalDownloaded 回滚成功分片，两者口径保持一致。
            if (written > 0) {
                shared.totalDownloaded.addAndGet(-written)
                addDownloadedBytes(-written)
            }
            attempts++
            // Range 被判不可用或整体已中止时，重试没有任何意义
            if (shared.rangeUnsupported.get() || shared.aborted.get()) break
            if (attempts < 3) {
                try {
                    Thread.sleep(500L * attempts)
                } catch (ignored: InterruptedException) {
                }
            }
        }
        // 暂停 / 整体中止直接返回，不标记失败（避免整体回退重下被误判为"分片失败"）
        if (isPaused(sectionId) || shared.aborted.get()) return
        shared.anyFailed.set(true)
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
            return DownloadPathSpec.ERR_NETWORK
        }
        var bufferedSink: BufferedSink? = null
        try {
            // 审计 M11-f：原实现不校验 HTTP 状态码，404/412 的错误页会被当成弹幕正文写进
            // danmaku.xml，播放时表现为"弹幕全空"却又是成功状态。
            if (!response.isSuccessful)
                return DownloadPathSpec.ERR_NETWORK
            if (!resetFile(danmakuFile))
                return DownloadPathSpec.ERR_FILE

            val sink: Sink = danmakuFile.sink()
            val decompressBytes = NetWorkUtil.decompress(response.body!!.bytes())
            bufferedSink = sink.buffer()
            bufferedSink.write(decompressBytes)
            bufferedSink.close()
            
            // 更新进度
            if (sectionId > 0) {
                setDownloadProgress(sectionId, baseProgress + 0.05f, getString(R.string.text_download_danmaku))
            }
        } catch (e: IOException) {
            return DownloadPathSpec.ERR_FILE
        } finally {
            bufferedSink?.close()
            response.body?.close()
            response.close()
        }
        return DownloadPathSpec.NORMAL
    }

    override fun onDestroy() {
        Logu.d("结束")

        started = false
        batchRunning = false
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
            exitMessage = getString(R.string.text_download_service_exit)

        Logu.d("退出下载服务")
        if (section != null) {
            val id = section!!.id
            val folder = section!!.getPath()
            section = null

            CenterThreadPool.run {
                notifier.notifyExit(exitMessage!!)
                if (exitCode != DownloadPathSpec.NORMAL) {
                    DownloadRepository.setState(id, "none")
                    // 注意：这里绝不能删整个任务目录。单 P 任务的目录（FileUtil.getVideoDownloadPath(title, null)）
                    // 就是 <下载根>/<标题> 本身，递归删除会把上一次成功下载好的视频/音频/封面/弹幕一起清掉，
                    // 属于真实数据丢失。批次非正常结束（服务被回收、用户停止、中途出错）时只清理本次下载的
                    // 临时文件与 .DOWNLOADING 标记，成品一律保留。
                    FileUtil.cleanDownloadTempFiles(folder)
                }
                refreshDownloadList()
            }
        }

        super.onDestroy()
    }
}
