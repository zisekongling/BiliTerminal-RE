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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.activity.video.local.DownloadListActivity
import com.RobinNotBad.BiliClient.activity.video.local.LocalListActivity
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.helper.sql.DownloadSqlHelper
import com.RobinNotBad.BiliClient.service.download.BangumiCacheSeason
import com.RobinNotBad.BiliClient.service.download.DownloadNotifier
import com.RobinNotBad.BiliClient.service.download.DownloadPathSpec
import com.RobinNotBad.BiliClient.service.download.DownloadProgressMath
import com.RobinNotBad.BiliClient.service.download.DownloadStreamTransfer
import com.RobinNotBad.BiliClient.service.download.DownloadUrlCandidates
import com.RobinNotBad.BiliClient.service.download.DownloadSegmentMerger
import com.RobinNotBad.BiliClient.service.download.DownloadSegmentPlan
import com.RobinNotBad.BiliClient.service.download.DownloadProgressStore
import com.RobinNotBad.BiliClient.service.download.DownloadProgressInfo
import com.RobinNotBad.BiliClient.service.download.DownloadRepository
import com.RobinNotBad.BiliClient.service.download.DownloadResiduePolicy
import com.RobinNotBad.BiliClient.service.download.DownloadResumePolicy
import com.RobinNotBad.BiliClient.service.download.SegmentDownloadPolicy
import com.RobinNotBad.BiliClient.model.Bangumi
import com.RobinNotBad.BiliClient.model.DownloadSection
import com.RobinNotBad.BiliClient.model.DownloadSegment
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.SubtitleLink
import com.RobinNotBad.BiliClient.util.Aria2Util
import com.RobinNotBad.BiliClient.util.CacheMediaPolicy
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.DownloadWakeLock
import com.RobinNotBad.BiliClient.util.DanmakuFileFetcher
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MediaMerger
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import okhttp3.Response
import org.json.JSONException
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Timer

/** release 包里 Logu 是关死的（BuildConfig.DEBUG 编译期折叠），真正出事的现场只有这条 logcat 能看到。 */
private const val TAG = "DownloadService"

class DownloadService : Service() {

    companion object {
        // 合一 MP4/flv 下载的进度起点：附件（封面/字幕/弹幕）已经占掉前 20%，
        // 与原来单文件分支里 `downFile(..., 0.2f)` 的写法一致（反馈 #87/#90 的分段下载沿用同一口径）。
        private const val START_BYTES_BASE = 0.2f

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
        // 速度不再是一个全局字符串：每条任务各自一份速度（DownloadProgressInfo.speedBps），
        // 总速度由 DownloadProgressStore.getTotalSpeedBps() 现算。
        // 这里只留"是否高速模式"的门面，Java/Kotlin 侧照旧写 DownloadService.isSpeedMode。
        // 刻意保持非 volatile：UI 线程直接读、下载线程直接写是既有数据竞争，加 volatile 属于行为变更。
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
        /** 只换状态文案，进度与字节数不动（阶段切换：视频→音频→合并） */
        @JvmStatic
        fun setDownloadState(id: Long, state: String) {
            DownloadProgressStore.setDownloadState(id, state)
        }

        /** 给一条任务建立字节预算（视频流 + 音频流的大小）；<=0 表示探测失败、走阶段权重兜底 */
        @JvmStatic
        fun beginSectionBytes(id: Long, budgetBytes: Long) {
            DownloadProgressStore.beginSectionBytes(id, budgetBytes)
        }

        /**
         * 上报当前文件已下字节数（进度与速度都由此推进，见 DownloadSectionBytes）。
         *
         * [fallbackState] 只在这条任务还没有进度记录时兜底：状态文案以 setDownloadState 写进去的为准，
         * 别再传全局 state，否则并行下载时会把别的任务的文案显示到这一行。
         */
        @JvmStatic
        fun reportFileProgress(id: Long, fallbackState: String, downloadedInFile: Long, fileTotal: Long,
                               baseProgress: Float, endProgress: Float, pseudoProgress: Float) {
            DownloadProgressStore.reportFileProgress(
                id, fallbackState, downloadedInFile, fileTotal, baseProgress, endProgress, pseudoProgress
            )
        }

        /** 一个文件下完了：把它实际的字节数并进该任务的累计量 */
        @JvmStatic
        fun finishFileProgress(id: Long, actualBytes: Long) {
            DownloadProgressStore.finishFileProgress(id, actualBytes)
        }

        /** 数据部分全部下完（只剩合并）：进度顶到 100% */
        @JvmStatic
        fun finishAllProgress(id: Long, state: String) {
            DownloadProgressStore.finishAllProgress(id, state)
        }

        /** 所有活跃任务的速度之和（字节/秒），0 表示暂时测不出来 */
        @JvmStatic
        fun getTotalSpeedBps(): Long = DownloadProgressStore.getTotalSpeedBps()

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
                        // 失败过的任务不该再挡路（反馈 #136）：留着 state="error" 的旧行会让同一个视频
                        // 永远弹「已在下载队列」，这里把失败行删掉、按新任务重新入队。
                        val oldState = if (cursor.moveToFirst())
                            cursor.getString(cursor.getColumnIndexOrThrow("state")) else ""
                        if (oldState != "error") {
                            MsgUtil.showMsg(BiliTerminal.context!!.getString(R.string.text_download_already_queued))
                            return@run
                        }
                        database.execSQL("delete from download where aid=? and cid=?",
                            arrayOf<Any>(aid, cid))
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
                        // 同单 P：失败行不再挡路，删掉旧行重新入队（反馈 #136）。
                        val oldState = if (cursor.moveToFirst())
                            cursor.getString(cursor.getColumnIndexOrThrow("state")) else ""
                        if (oldState != "error") {
                            MsgUtil.showMsg(BiliTerminal.context!!.getString(R.string.text_download_already_queued))
                            return@run
                        }
                        database.execSQL("delete from download where aid=? and cid=?",
                            arrayOf<Any>(aid, cid))
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

        /**
         * 番剧缓存入队：一次把用户在缓存页勾选的那些集写进下载队列。
         *
         * <p>为什么不像投稿视频那样一集一个入口：番剧侧是一次勾一批（还可能跨季勾），
         * 逐集调用会让每一集都弹一条「已在下载队列」的提示，勾十集就是十条提示；
         * 这里在同一个后台线程里做完去重与插入，最后只给一条汇总提示。
         *
         * <p>落盘结构与多 P 视频一致（番剧名-季名当父目录、集名当子目录），所以
         * 缓存页/播放器/删除逻辑一行都不用改；区别只有 download_type 与
         * epid / season_id / season_type 三列 —— 下载时靠它们走番剧取流接口，
         * 见 [processDownloadSection] 与 [DownloadSection.isBangumi]。
         *
         * @param qn      统一清晰度：缓存页只问一次，所有集用同一个值
         * @param seasons 按季分组的剧集；允许一次传多季（缓存页支持跨季混选）
         */
        @JvmStatic
        fun startDownloadBangumi(qn: Int, seasons: List<BangumiCacheSeason>) {
            if (seasons.none { it.episodes.isNotEmpty() }) return
            CenterThreadPool.run {
                var database: SQLiteDatabase? = null
                var cursor: Cursor? = null
                try {
                    // 同投稿视频：目录不可写就一条都别入队，否则只会留下一堆注定的失败任务
                    if (FileUtil.resolveVideoDownloadDir(BiliTerminal.context) == null) return@run
                    val context = BiliTerminal.context!!
                    database = DownloadSqlHelper(context).writableDatabase

                    var added = 0
                    var skipped = 0
                    for (season in seasons) {
                        // 父目录必须带番剧名：季名（「TV」「第二季」）是通用的，只用季名当目录时
                        // 两部番的同名季会落进同一个目录，缓存状态互相串台、成品互相覆盖（26.11.18）
                        val folderName = BangumiCacheSeason.seasonFolderName(
                            season.bangumiTitle, season.seasonTitle
                        )
                        val seasonCover = season.seasonCover
                        val seasonId = season.seasonId
                        val seasonType = season.seasonType
                        for (episode in season.episodes) {
                            // 命名规则与缓存页共用同一个函数：那边要靠它推算目录判断「已下载」
                            val child = BangumiCacheSeason.childNameOf(
                                episode,
                                context.getString(R.string.text_download_bangumi_episode_unknown)
                            )
                            cursor = database.rawQuery(
                                "select state from download where aid=? and cid=?",
                                arrayOf(episode.aid.toString(), episode.cid.toString())
                            )
                            if (cursor != null && cursor.count > 0) {
                                // 与投稿视频同口径：失败过的行不挡路（反馈 #136），删掉按新任务重排
                                val oldState = if (cursor.moveToFirst()) cursor.getString(0) else ""
                                cursor.close()
                                cursor = null
                                if (oldState != "error") {
                                    skipped++
                                    continue
                                }
                                database.execSQL(
                                    "delete from download where aid=? and cid=?",
                                    arrayOf<Any>(episode.aid, episode.cid)
                                )
                            }
                            cursor?.close()
                            cursor = null

                            database.execSQL(
                                "insert into download(type,state,aid,cid,qn,title,child,cover,download_type,audio_url," +
                                        "epid,season_id,season_type) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                                arrayOf<Any>(
                                    DownloadPathSpec.TASK_VIDEO_MULTI, "none", episode.aid, episode.cid, qn,
                                    folderName, child,
                                    GlideUtil.url(episode.cover?.takeIf { it.isNotEmpty() } ?: seasonCover),
                                    DownloadPathSpec.TYPE_VIDEO_PGC, "",
                                    episode.id, seasonId, seasonType
                                )
                            )

                            val path_page = FileUtil.getVideoDownloadPath(folderName, child)
                            path_page.mkdirs()

                            val file_sign = File(path_page, DownloadPathSpec.FILE_DOWNLOADING)
                            if (!file_sign.exists()) file_sign.createNewFile()

                            // 番剧只有 durl（音视频合一的 mp4）一条链路，画质标记就是 qn 本身
                            File(path_page, DownloadPathSpec.FILE_QUALITY).writeText(qn.toString())
                            DownloadRepository.saveVideoMeta(
                                path_page, child, episode.aid, episode.cid, qn,
                                DownloadPathSpec.TYPE_VIDEO_PGC
                            )
                            added++
                        }
                    }

                    val msg = when {
                        added == 0 -> context.getString(R.string.text_download_bangumi_all_queued)
                        skipped == 0 -> context.getString(R.string.text_download_bangumi_added, added)
                        else -> context.getString(R.string.text_download_bangumi_added_partial, added, skipped)
                    }
                    MsgUtil.showMsg(msg)
                    if (added > 0) start(-1)
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
                // 26.11.17（崩溃 #354 / #212 / #365）：优先走普通 startService，不再给这次启动
                // 记「5 秒内必须 startForeground」的义务——低配手表上主线程被占满时，
                // 服务还没轮到 onCreate 就超时崩进程。前台通知仍由 ensureForeground() 在
                // onStartCommand 开头挂上，行为不变。
                // 若被判定为后台启动（IllegalStateException：系统重建、通知栏“继续”等无可见界面
                // 的入口），退回 startForegroundService——那种情形下义务重新出现，同样由
                // onStartCommand 第一行的 ensureForeground() 立刻兑现。
                try {
                    context.startService(Intent(context, DownloadService::class.java))
                } catch (e: IllegalStateException) {
                    Logu.w("DownloadService.start", "startService 被拒（判成后台启动），退回 startForegroundService：" + (e.message ?: ""))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        context.startForegroundService(Intent(context, DownloadService::class.java))
                    else
                        throw e
                }
            } catch (e: Exception) {
                started = false
                Logu.e("start", e.message ?: "启动下载服务失败")
                CenterThreadPool.runOnUiThread {
                    MsgUtil.showMsg(BiliTerminal.context!!.getString(R.string.text_download_service_start_failed))
                }
            }
        }

        /**
         * 收拾失败任务的目录，让它下次既能重新入队、又能接着下（反馈 #136 + #250/#252/#253）。
         *
         * 以前这里分两种结局：有成品就只清临时产物，一个成品都没有就整目录删掉。整目录删确实解决了
         * #136（`.DOWNLOADING` 还在，详情页于是永远弹「已在下载队列」），但连用户下了十几分钟的
         * `video_new.mp4` 一起删了——「下到 99% 断网就得从 0 开始」（#250）就是这么来的。
         *
         * 现在只动 [DownloadResiduePolicy] 算出来的那几样：
         * - 摘掉 `.DOWNLOADING`：详情页/多P页据此判定"正在下载"，摘掉就能重新入队（#136 不回退）；
         * - 删坏合并件 `video_merging.tmp`、删没有对应临时文件的孤儿 `*.resume`；
         * - 被打断的 `.bak`（替换正式文件时留下的备份）按"正式名缺失就改名回去、存在就删备份"处理，
         *   与 FileUtil.cleanDownloadTempFiles 的老规则一致。
         * 半成品、多段分片、封面、弹幕、字幕、元数据、成品，以及用户自己放进去的文件一律不动。
         */
        private fun cleanFailedResidue(folder: File?) {
            if (folder == null || !folder.isDirectory) return
            val plan = DownloadResiduePolicy.cleanupPlan(folder.list()?.toList())
            if (plan.isNoop) return
            for (name in plan.restoreFromBackup) {
                val target = DownloadResiduePolicy.backupTargetName(name)
                try {
                    if (File(folder, name).renameTo(File(folder, target)))
                        Logu.i("DownloadService", "失败任务 ${folder.name} 的成品备份已恢复：$target")
                } catch (e: Exception) {
                    Logu.w("DownloadService", "恢复成品备份失败：$name ${e.message}")
                }
            }
            for (name in plan.deleteNames) {
                try {
                    if (File(folder, name).delete())
                        Logu.d("DownloadService", "已清理失败任务的 $name（${folder.name}）")
                } catch (e: Exception) {
                    Logu.w("DownloadService", "清理失败任务文件失败：$name ${e.message}")
                }
            }
        }

        /**
         * 扫一遍数据库里所有失败任务，收拾它们的目录残留（反馈 #136）。
         *
         * 失败收尾现在会当场清残留，但**升级前就已经失败**的任务还留着 `.DOWNLOADING`：视频详情页的
         * 下载入口只看「任务目录在不在」，看到就只弹一句「已在下载队列，长按可清空文件」，
         * 于是同一个视频点多少次都进不了下载流程。进下载列表时扫一遍，让这些老任务重新可下。
         */
        @JvmStatic
        fun cleanupFailedResidues() {
            val all = DownloadRepository.getAll() ?: return
            for (section in all) {
                if (section.state != "error") continue
                cleanFailedResidue(section.getPath())
            }
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

    /**
     * 每个任务这次失败的**可读原因**（反馈 #87/#90：「下载出错」旁边要写清为什么）。
     *
     * 为什么按 id 存而不是一个字段：并行下载（[parallelDownload]）时多个任务在同一个 Service
     * 实例里同时跑，单个 `lastFailReason` 会让 A 的失败原因被 B 覆盖。由
     * [processDownloadSection] 的失败点写入，[runDownloadSection] 落库后取走并移除。
     */
    private val failReasons: java.util.concurrent.ConcurrentHashMap<Long, String> =
        java.util.concurrent.ConcurrentHashMap()

    /** 记下这次失败的原因（同时打日志），列表里会直接显示它。 */
    private fun recordFailReason(sectionId: Long, reason: String) {
        if (sectionId <= 0) return
        failReasons[sectionId] = reason
        Logu.e("DownloadService", "任务 $sectionId 失败：$reason")
    }

    /** 取走这个任务的失败原因（取一次就没了，避免下次失败还显示旧原因）。 */
    private fun takeFailReason(sectionId: Long): String =
        failReasons.remove(sectionId) ?: getString(R.string.text_download_failed_unknown)

    /** 把异常压成一行可读原因：类名 + message（Error 常常没有 message，只留类名）。 */
    private fun describeThrowable(t: Throwable): String {
        val name = t.javaClass.name
        val message = t.message
        return if (message.isNullOrBlank()) name else "$name: $message"
    }

    /**
     * 把任务当场标成失败并落库原因。
     *
     * 给 [parallelDownload] 的兜底用：那一层只负责「这个任务完了」，不写数据库；
     * 一旦异常越过 [runDownloadSection]（Error 就会），任务会永远留在「下载中 0.0%」。
     */
    private fun failSection(sectionId: Long, t: Throwable) {
        try {
            DownloadProgressStore.retireSection(sectionId, false)
            removeDownloadProgress(sectionId)
            DownloadRepository.setState(sectionId, "error")
            DownloadRepository.setFailReason(sectionId, describeThrowable(t))
        } catch (e: Throwable) {
            Log.e(TAG, "标记任务 $sectionId 失败时又出错", e)
        }
    }

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
                    DownloadProgressStore.resetBatchState()
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
            } catch (e: Throwable) {
                // 必须捕 Throwable：NoClassDefFoundError / OutOfMemoryError 这类 Error 不是
                // Exception，修复前从这里逃出去会让批次线程直接死掉——stopSelf() 不执行、
                // 队列停在「准备中 0.0%」，用户只看到一行类名（反馈现场即如此）。
                Log.e(TAG, "下载批次异常终止", e)
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
                } catch (e: Throwable) {
                    // 同上：Error 不能让任务一直停在「准备中 0.0%」，当场标成失败。
                    Log.e(TAG, "任务 ${taskSection.id} 线程异常", e)
                    MsgUtil.err(e)
                    failSection(taskSection.id, e)
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
        } catch (e: Throwable) {
            Log.e(TAG, "下载异常: ${downloadSection.id}", e)
            MsgUtil.err(e)
            recordFailReason(downloadSection.id, getString(R.string.text_download_failed_exception, describeThrowable(e)))
            removeDownloadProgress(downloadSection.id)
            false
        }

        if (success) {
            // 字节预算在这里交给批次统计：成功任务随后会被从数据库删掉，
            // 不在这时记账的话总进度会在它出队的瞬间往回掉一格。
            DownloadProgressStore.retireSection(downloadSection.id, true)
        } else {
            // 用户暂停的任务：不记为失败，状态保持"paused"，等待用户恢复
            if (isPaused(downloadSection.id)) {
                DownloadProgressStore.retireSection(downloadSection.id, false)
                removeDownloadProgress(downloadSection.id)
                return true // 让串行/并行批次继续，不中断其他任务
            }
            batchStats.recordFailure()
            DownloadProgressStore.retireSection(downloadSection.id, false)
            removeDownloadProgress(downloadSection.id)
            DownloadRepository.setState(downloadSection.id, "error")
            // 顺手把"为什么失败"落库（#87/#90）：列表不再只显示一句「下载出错」。
            // 原因由 processDownloadSection 的各个失败点写入（HTTP 码 / 第几段失败 / 校验不过）；
            // 没写过的回落到通用文案，与改动前的显示完全一致。
            DownloadRepository.setFailReason(downloadSection.id, takeFailReason(downloadSection.id))
            // 失败收尾必须收拾目录残留（反馈 #136）：留下来的 `.DOWNLOADING` 会让视频详情页
            // 一直以为"已在下载队列"，同一个视频从此再也点不进来。
            cleanFailedResidue(downloadSection.getPath())
        }
        return success
    }

    /** 恢复上次会话遗留的"下载中"记录、清理残留进度，并收拾历史失败任务的目录残留 */
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
                // 进程被杀（更新、被系统回收）时留下的 .DOWNLOADING 标记必须一起清：
                // 缓存页见到这个标记会整条跳过，于是「早就下好了、只是标记没删」的视频
                // 在更新后再也不出现在缓存列表里（反馈 #187）。
                clearStaleDownloadMarker(s.getPath())
            } else if (s.state == "error") {
                // 历史失败任务（反馈 #136）：视频详情页只看「任务目录还在不在」，目录不清掉就
                // 永远弹「已在下载队列」、再也进不了下载流程。这里趁批次启动把它们一并收拾了，
                // 有成品时只清临时产物，不会动已经下好的视频。
                cleanFailedResidue(s.getPath())
            }
        }
    }

    /**
     * 清掉「已经下好、标记却没清」的 `.DOWNLOADING`。
     *
     * 只按 [CacheMediaPolicy.isStaleDownloadMarker] 动手：目录里既有成品、又有下载标记，
     * 说明那一轮真正写完了（成品是下载的最后一步才被转正的），标记只是没来得及删。
     * 没有成品时保留标记，失败收尾还要照它做残留判定。
     *
     * 多 P 任务的 `.DOWNLOADING` 写在页目录里，所以任务目录的一级子目录也判一遍。
     */
    private fun clearStaleDownloadMarker(dir: File?) {
        if (dir == null) return
        val targets = ArrayList<File>(1 + 8)
        targets.add(dir)
        dir.listFiles()?.forEach { if (it.isDirectory) targets.add(it) }
        for (d in targets) {
            val names = d.list()?.toList() ?: continue
            if (!CacheMediaPolicy.isStaleDownloadMarker(names)) continue
            if (File(d, DownloadPathSpec.FILE_DOWNLOADING).delete()) {
                Logu.d("DownloadService", "清掉陈旧的下载标记：" + d.absolutePath)
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
        // 每条流的候选线路（主线路在前，备用线路在后）：主线路被 CDN 掐断时换线重试，
        // 见 DownloadUrlCandidates 与 downFile(List)。
        val videoUrls: List<String>
        val audioUrls: List<String>
        // 老接口（合一 MP4/flv）的多段 durl **完整分段清单**（反馈 #87/#90）。
        // 只有 qn<=64 的老接口链路会填；DASH / 纯音频链路恒为空。
        val videoSegments: List<DownloadSegment>
        try {
            val data = downloadSection.toPlayerData()

            if (downloadSection.isAudioOnly) {
                PlayerApi.getVideoDash(data)
                url_audio = if (!downloadSection.audioUrl.isNullOrEmpty())
                    downloadSection.audioUrl!!
                else
                    data.audioUrl
                // 用户自选的音频地址没有备用线路可言；从取流接口拿到的那条才有
                audioUrls = if (!downloadSection.audioUrl.isNullOrEmpty())
                    listOf(downloadSection.audioUrl!!)
                else
                    data.audioUrlCandidates.ifEmpty { listOf(url_audio) }
                videoUrls = emptyList()
                videoSegments = emptyList()
                url_video = ""
                useDash = false // 纯音频不需要合并
            } else if (downloadSection.isBangumi()) {
                // 番剧（pgc）：走 pgc/player/web/v2/playurl，且 fnval 只申请 durl ——
                // 拿到的是音视频合一的 mp4（服务端可能切成多段），没有 DASH、没有独立音轨，
                // 所以下面这一支和投稿视频 qn<=64 的老接口链路完全同构，落盘也复用同一段代码。
                PlayerApi.getBangumi(data)
                url_video = data.videoUrl
                videoUrls = data.videoUrlCandidates.ifEmpty { listOf(url_video) }
                videoSegments = data.videoSegments.orEmpty()
                audioUrls = emptyList()
                url_audio = ""
                useDash = false
            } else {
                // 720P及以下用旧方法(MP4单文件)，1080P及以上用DASH新方法
                if (downloadSection.qn <= 64) {
                    PlayerApi.getVideo(data, true)
                    url_video = data.videoUrl
                    videoUrls = data.videoUrlCandidates.ifEmpty { listOf(url_video) }
                    // #87/#90：服务端可能把这一份文件切成多段，清单照收，下载器逐段下再拼
                    videoSegments = data.videoSegments.orEmpty()
                    audioUrls = emptyList()
                    url_audio = ""
                    useDash = false
                } else {
                    PlayerApi.getVideoDash(data)
                    url_video = data.videoUrl
                    url_audio = data.audioUrl
                    videoUrls = data.videoUrlCandidates.ifEmpty { listOf(url_video) }
                    audioUrls = data.audioUrlCandidates.ifEmpty { listOf(url_audio) }
                    videoSegments = emptyList()
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
            // NoPlayUrlException 的消息本身就点明了原因（大会员专享 / 地区限制 / 需要登录），
            // 直接把它写进失败原因，比笼统的"取流失败"有用得多——番剧会员集就靠它说清楚为什么下不动
            val reason = (e as? PlayerApi.NoPlayUrlException)?.message?.takeIf { it.isNotEmpty() }
                ?: getString(R.string.text_download_fail_fetch_url)
            recordFailReason(downloadSection.id, reason)
            DownloadRepository.setState(downloadSection.id, "error")
            notifier.notifyCompletion(getString(R.string.text_download_link_failed, downloadSection.name_short), downloadSection.id.toInt())
            section = null
            refreshDownloadList()
            return false
        } catch (e: IOException) {
            // 取流阶段的网络失败也置 "error"（以前置 "none"，而调用方 runDownloadSection 紧接着
            // 又置 "error"，两个口径打架：列表一会儿是"等待下载"一会儿是"下载出错"）。
            // 统一成 "error"：这条任务这次失败了，需要用户/调度器重新排队才会再试。
            exitCode = DownloadPathSpec.ERR_NETWORK
            recordFailReason(downloadSection.id, getString(R.string.text_download_fail_network))
            DownloadRepository.setState(downloadSection.id, "error")
            return false
        }

        // 给这条任务建"字节预算"：先把视频流/音频流的大小探出来，之后整条进度条就按
        // 「已下字节 / 预算字节」走（见 DownloadSectionBytes）。探测失败（<=0）时不建预算，
        // 进度退回按阶段权重摊派的老算法 —— 探测只影响进度显示，不影响能不能下载。
        // 多段 durl 时清单自己就带总字节（各段 size 之和），比"只探第一段"准得多；
        // 服务端没给 size 才退回探测。
        val planTotalBytes = if (!useDash && !downloadSection.isAudioOnly)
            DownloadSegmentPlan.totalBytes(videoSegments) else -1L
        beginSectionBytes(
            downloadSection.id,
            if (planTotalBytes > 0) planTotalBytes
            else probeSectionBytes(videoUrls, audioUrls, useDash, downloadSection.isAudioOnly)
        )

        try {
            DownloadRepository.setState(downloadSection.id, "downloading")
            // 重新开始下载就清掉上一次的失败原因，否则这一轮还在下、列表却挂着旧原因（#87/#90）
            DownloadRepository.setFailReason(downloadSection.id, "")

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
                        setDownloadState(downloadSection.id, getString(R.string.text_download_audio))
                        result = downFile(audioUrls, audioTmp, downloadSection.id, 0.2f,
                            resumeTag = resumeTagFor(downloadSection, DownloadResumePolicy.STREAM_AUDIO))
                        if (result != DownloadPathSpec.NORMAL) {
                            exitCode = result
                            return false
                        }
                        if (!replaceTempOrFail(audioTmp, File(path_single, DownloadPathSpec.FILE_AUDIO))) return false
                    } else if (useDash) {
                        // DASH：视频流与音频流依次下完，进度按「已下字节 / 预算字节」走，
                        // 不再按阶段权重摊派（视频/音频大小在 processDownloadSection 开头探测）。
                        // 下载完成后把音视频重新封装成一个 MP4（同 720P 的单文件形态）；合并失败保留双文件，
                        // 不判定下载失败。合并阶段进度已到 100%，只把状态文案换成"合并中"。
                        // 先下载到临时文件，全部成功后再替换正式文件（重新下载/切换清晰度时不破坏旧视频）
                        val videoTmp = File(path_single, DownloadPathSpec.FILE_VIDEO_TMP)
                        val audioTmp = File(path_single, DownloadPathSpec.FILE_AUDIO_TMP)
                        state = getString(R.string.text_download_video)
                        setDownloadState(downloadSection.id, getString(R.string.text_download_video))
                        result = downFile(videoUrls, videoTmp, downloadSection.id, DownloadProgressMath.DASH_ATTACH_END, DownloadProgressMath.DASH_VIDEO_END,
                            resumeTagFor(downloadSection, DownloadResumePolicy.STREAM_VIDEO))
                        if (result != DownloadPathSpec.NORMAL) {
                            exitCode = result
                            return false
                        }
                        state = getString(R.string.text_download_audio)
                        setDownloadState(downloadSection.id, getString(R.string.text_download_audio))
                        result = downFile(audioUrls, audioTmp, downloadSection.id, DownloadProgressMath.DASH_VIDEO_END, DownloadProgressMath.DASH_AUDIO_END,
                            resumeTagFor(downloadSection, DownloadResumePolicy.STREAM_AUDIO))
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
                    } else {
                        // MP4格式或无音轨DASH：直接下载单个文件（音视频已合并/无音轨）
                        // 多段 durl 时由 downFileOrSegments 逐段下载再拼接（#87/#90）
                        val videoTmp = File(path_single, DownloadPathSpec.FILE_VIDEO_TMP)
                        state = getString(R.string.text_download_video)
                        setDownloadState(downloadSection.id, getString(R.string.text_download_video))
                        result = downFileOrSegments(downloadSection, videoSegments, videoUrls, videoTmp)
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
                        setDownloadState(downloadSection.id, getString(R.string.text_download_audio))
                        result = downFile(audioUrls, audioTmp, downloadSection.id, 0.2f,
                            resumeTag = resumeTagFor(downloadSection, DownloadResumePolicy.STREAM_AUDIO))
                        if (result != DownloadPathSpec.NORMAL) {
                            exitCode = result
                            return false
                        }
                        if (!replaceTempOrFail(audioTmp, File(path_page, DownloadPathSpec.FILE_AUDIO))) return false
                    } else if (useDash) {
                        // DASH：视频流与音频流依次下完，进度按「已下字节 / 预算字节」走，
                        // 不再按阶段权重摊派（视频/音频大小在 processDownloadSection 开头探测）。
                        // 下载完成后把音视频重新封装成一个 MP4（同 720P 的单文件形态）；合并失败保留双文件，
                        // 不判定下载失败。合并阶段进度已到 100%，只把状态文案换成"合并中"。
                        // 先下载到临时文件，全部成功后再替换正式文件（重新下载/切换清晰度时不破坏旧视频）
                        val videoTmp = File(path_page, DownloadPathSpec.FILE_VIDEO_TMP)
                        val audioTmp = File(path_page, DownloadPathSpec.FILE_AUDIO_TMP)
                        state = getString(R.string.text_download_video)
                        setDownloadState(downloadSection.id, getString(R.string.text_download_video))
                        result = downFile(videoUrls, videoTmp, downloadSection.id, DownloadProgressMath.DASH_ATTACH_END, DownloadProgressMath.DASH_VIDEO_END,
                            resumeTagFor(downloadSection, DownloadResumePolicy.STREAM_VIDEO))
                        if (result != DownloadPathSpec.NORMAL) {
                            exitCode = result
                            return false
                        }
                        state = getString(R.string.text_download_audio)
                        setDownloadState(downloadSection.id, getString(R.string.text_download_audio))
                        result = downFile(audioUrls, audioTmp, downloadSection.id, DownloadProgressMath.DASH_VIDEO_END, DownloadProgressMath.DASH_AUDIO_END,
                            resumeTagFor(downloadSection, DownloadResumePolicy.STREAM_AUDIO))
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
                    } else {
                        // MP4格式或无音轨DASH：直接下载单个文件（音视频已合并/无音轨）
                        // 多段 durl 时由 downFileOrSegments 逐段下载再拼接（#87/#90）
                        val videoTmp = File(path_page, DownloadPathSpec.FILE_VIDEO_TMP)
                        state = getString(R.string.text_download_video)
                        setDownloadState(downloadSection.id, getString(R.string.text_download_video))
                        result = downFileOrSegments(downloadSection, videoSegments, videoUrls, videoTmp)
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

            // 数据部分全部下完：进度顶到 100%（此后的合并/替换不再占进度区间）
            finishAllProgress(downloadSection.id, getString(R.string.text_update_download_complete))

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
        // 合并只换容器、不产生新字节：进合并就说明数据部分已下完，进度直接顶到 100%，
        // 期间只把状态文案保持为"合并中"（用户看到的是"100% + 合并中"）
        finishAllProgress(sectionId, mergeState)
        val mergeStartAt = System.currentTimeMillis()
        val result = MediaMerger.mergeAv(
            videoFile = videoFile,
            audioFile = audioFile,
            mergedFile = mergedTmp,
            // 进度已到 100%，合并阶段不再推进进度条
            onProgress = { _ -> },
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
     *
     * 进度不再由附件占区间：整条任务的进度是「已下字节 / 预算字节」（见 [DownloadSectionBytes]），
     * 附件不建预算，下载时进度基本不动、只换状态文案。真正的字节进度从视频流开始走。
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
        setDownloadState(downloadSection.id, getString(R.string.text_download_cover))
        if (!coverFile.exists() && downloadSection.url_cover.isNotEmpty()) {
            // 附件不参与任务的字节预算（预算只算视频流 + 音频流），所以不传 sectionId：
            // 传了的话封面/弹幕的字节会被算进进度分子，把进度条推过真实比例。
            // 代价只是暂停要等到下一个真正下载的流才会生效，附件本身只有几十 KB～几 MB。
            val result = downFile(downloadSection.url_cover, coverFile)
            if (result != DownloadPathSpec.NORMAL) {
                exitCode = result
                return false
            }
        }

        if (!downloadSection.isAudioOnly) {
            // 字幕
            state = getString(R.string.text_download_subtitle)
            setDownloadState(downloadSection.id, getString(R.string.text_download_subtitle))
            downSubtitles(downloadSection.aid, downloadSection.cid, subtitleFolder)

            // 弹幕
            state = getString(R.string.text_download_danmaku)
            setDownloadState(downloadSection.id, getString(R.string.text_download_danmaku))
            val result = downDanmaku(url_danmaku, danmakuFile)
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
            (instance as LocalListActivity).refreshAfterExternalChange()
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
    private fun downFile(
        url: String, file: File, sectionId: Long = -1,
        baseProgress: Float = 0f, endProgress: Float = 1.0f,
        resumeTag: String? = null
    ): Int = downFile(listOf(url), file, sectionId, baseProgress, endProgress, resumeTag)

    /**
     * 断点续传的身份标识：**由内容派生**，不能用 sectionId。
     *
     * 重新入队时 [startDownload] 会把 state=="error" 的旧行删掉再 insert 一条新行（id 就变了），
     * 于是"上次下到一半"的那份存档按 id 根本认不回来。aid+cid+qn 唯一确定一条流，
     * [stream] 再区分视频流 / 音频流 / 合一 MP4（见 [DownloadResumePolicy.STREAM_VIDEO] 等）。
     */
    private fun resumeTagFor(downloadSection: DownloadSection, stream: String): String =
        DownloadResumePolicy.tag(downloadSection.aid, downloadSection.cid, downloadSection.qn, stream)

    /**
     * 按候选线路顺序下载，一条线失败就换下一条。
     *
     * 背景见 [DownloadUrlCandidates]：upos 的某条线会在下载途中掐断连接（服务端声明的
     * Content-Length 没传完就 EOF），而 playurl 早已把备用线路一起给了我们。原实现只用主线路、
     * 重试也打同一条线，坏节点上就等于「这个视频永远下不完」（反馈 #136）。
     *
     * 只对网络类失败换线：本地写文件失败（ERR_FILE）、用户暂停、服务已停都不该再白打一遍。
     * 每条线路内部仍走原来的分片重试与退化逻辑（[downFileOnce] 语义不变）。
     */
    @Throws(IOException::class)
    private fun downFile(
        urls: List<String>, file: File, sectionId: Long = -1,
        baseProgress: Float = 0f, endProgress: Float = 1.0f,
        resumeTag: String? = null
    ): Int {
        val candidates = urls.filter { it.isNotEmpty() }.ifEmpty { listOf("") }
        var lastResult = DownloadPathSpec.ERR_NETWORK
        for ((index, url) in candidates.withIndex()) {
            // 换线前先看服务是否还活着、用户有没有暂停：这两件事换哪条线都救不回来
            if (!started) return DownloadPathSpec.ERR_UNKNOWN
            if (sectionId > 0 && isPaused(sectionId)) return DownloadPathSpec.ERR_PAUSED
            if (index > 0) {
                Logu.w("DownloadService", "下载失败，换备用线路重试（${index + 1}/${candidates.size}）：${file.name}")
            }
            lastResult = downFileOnce(url, file, sectionId, baseProgress, endProgress, resumeTag)
            if (lastResult != DownloadPathSpec.ERR_NETWORK) return lastResult
        }
        return lastResult
    }

    /**
     * 下载一条"合一 MP4/flv"的视频流：服务端把文件切成多段时**逐段下载再拼接**（反馈 #87/#90）。
     *
     * 背景：老接口（qn<=64）在文件较大时会在 `durl` 里回多段，以前只下第 0 段，落盘就是一个
     * 能打开、但只有开头的残片。现在按清单顺序逐段落到临时分片（[DownloadSegmentPlan.partFileName]），
     * 全部下完再交给 [DownloadSegmentMerger] 拼成整片。
     *
     * 单段/没有清单时**完全走原来的**[downFile] 路径，行为一个字不变。
     *
     * 分片复用的前提是"这次清单和上次一样"（[DownloadSegmentPlan.isSamePlan]）：服务端换了切法
     * 或地址后，旧分片一律作废——拿两套不同切法的字节拼起来只会得到一堆错位数据。
     *
     * @return [DownloadPathSpec.NORMAL] 或错误码（失败原因已由 [recordFailReason] 写好）
     */
    @Throws(IOException::class)
    private fun downFileOrSegments(
        downloadSection: DownloadSection,
        segments: List<DownloadSegment>,
        urls: List<String>,
        videoTmp: File
    ): Int {
        val sectionId = downloadSection.id
        // 单段（绝大多数高清视频就是一份整文件）：可以按续传存档接着下。
        // 多段分片走下面逐段下载，段内不续传——那套已经有 part+done 的整段复用，
        // 再做片内续传会让"这次清单和上次一样吗"的判断变复杂，收益也不大。
        if (segments.size <= 1) return downFile(urls, videoTmp, sectionId, START_BYTES_BASE, 1.0f,
            resumeTagFor(downloadSection, DownloadResumePolicy.STREAM_MERGED))

        val folder = videoTmp.parentFile ?: return DownloadPathSpec.ERR_FILE
        val stored = DownloadSegmentPlan.fromJson(DownloadRepository.getSegments(sectionId))
        if (!DownloadSegmentPlan.isSamePlan(stored, segments)) {
            DownloadSegmentPlan.clearTempParts(folder)
        }
        // 清单先落库：进程被系统杀掉后重启，才能确认"磁盘上这些分片还算不算数"
        DownloadRepository.setSegments(sectionId, DownloadSegmentPlan.toJson(segments))

        val parts = ArrayList<File>(segments.size)
        val span = 1.0f - START_BYTES_BASE
        segments.forEachIndexed { index, segment ->
            val partFile = File(folder, DownloadSegmentPlan.partFileName(index))
            val doneFile = File(folder, DownloadSegmentPlan.partDoneFileName(index))
            parts.add(partFile)
            if (DownloadSegmentPlan.isReusablePart(partFile, doneFile, segment)) {
                Logu.i("DownloadService", "第 ${index + 1}/${segments.size} 段上次已下完，跳过：${partFile.name}")
                return@forEachIndexed
            }
            if (doneFile.exists()) doneFile.delete()
            val segmentState = getString(R.string.text_download_segment, index + 1, segments.size)
            state = segmentState
            setDownloadState(sectionId, segmentState)
            val candidates = DownloadUrlCandidates.of(segment.url, segment.backupUrls)
            val result = downFile(
                candidates, partFile, sectionId,
                START_BYTES_BASE + span * index / segments.size,
                START_BYTES_BASE + span * (index + 1) / segments.size
            )
            if (result != DownloadPathSpec.NORMAL) {
                // 具体原因（HTTP 码等）可能已被 downFileNormal/downFileSpeedSingle 写得更细；
                // 这里只在没有更细原因时补一句"第几段失败"，不覆盖它。
                if (!failReasons.containsKey(sectionId)) {
                    recordFailReason(
                        sectionId,
                        getString(R.string.text_download_fail_segment, index + 1, segments.size)
                    )
                }
                return result
            }
            // 写完才落"完成标记"：Aria2 分片模式会先把文件 setLength 预分配满长度，
            // 只看文件长度会把"刚开头"误判成"已经下完"（见 DownloadSegmentPlan.isReusablePart）
            doneFile.createNewFile()
        }

        val joinState = getString(R.string.text_download_join_segments)
        state = joinState
        setDownloadState(sectionId, joinState)
        return try {
            DownloadSegmentMerger.merge(parts, videoTmp)
            DownloadSegmentPlan.clearTempParts(folder)
            // 清单用完即清：下次重新下载时这些地址/切法都已过期，留着只会误导"能不能复用分片"
            DownloadRepository.setSegments(sectionId, "")
            DownloadPathSpec.NORMAL
        } catch (e: IOException) {
            recordFailReason(sectionId, getString(R.string.text_download_fail_join, e.message ?: ""))
            DownloadPathSpec.ERR_FILE
        }
    }

    @Throws(IOException::class)
    private fun downFileOnce(
        url: String, file: File, sectionId: Long = -1,
        baseProgress: Float = 0f, endProgress: Float = 1.0f,
        resumeTag: String? = null
    ): Int {
        if (Aria2Util.isEnabled()) {
            isSpeedMode = true
            return downFileSpeed(url, file, sectionId, baseProgress, endProgress, resumeTag)
        }
        isSpeedMode = false
        return downFileNormal(url, file, sectionId, baseProgress, endProgress, resumeTag)
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

    /** 临时文件旁边的续传存档（`video_new.mp4` → `video_new.mp4.resume`）；目录不可用则 null。 */
    private fun resumeSidecar(file: File): File? {
        val parent = file.parentFile ?: return null
        return File(parent, DownloadPathSpec.resumeNameFor(file.name))
    }

    /** 读续传存档。没有、读坏、版本对不上都返回 null —— 语义就是"从头下"，不是错误。 */
    private fun readResumeRecord(file: File): DownloadResumePolicy.Record? {
        val sidecar = resumeSidecar(file) ?: return null
        if (!sidecar.isFile) return null
        return try {
            DownloadResumePolicy.fromJson(sidecar.readText())
        } catch (e: Exception) {
            Logu.w("DownloadService", "续传存档读取失败（当作从头下载）：${sidecar.name} ${e.message}")
            null
        }
    }

    /** 写续传存档。写不进去只记日志：最坏退化成从头下载，不能因此让下载失败。 */
    private fun writeResumeRecord(file: File, record: DownloadResumePolicy.Record) {
        val sidecar = resumeSidecar(file) ?: return
        try {
            sidecar.writeText(DownloadResumePolicy.toJson(record))
        } catch (e: Exception) {
            Logu.w("DownloadService", "续传存档写入失败：${sidecar.name} ${e.message}")
        }
    }

    /**
     * 把分片进度写进续传存档：**先 fsync 数据，再写存档**。
     *
     * 顺序反过来的话，进程被系统杀掉后存档会声称某些字节已经落好，而它们其实还在页缓存里，
     * 下次续传就会跳过这些字节，文件中间留下零字节窟窿——那比"从头下载"坏得多。
     */
    private fun flushSegmentResume(file: File, tag: String, totalSize: Long, shared: SegmentSharedState) {
        if (totalSize <= 0) return
        try {
            java.io.RandomAccessFile(file, "rw").use { it.fd.sync() }
        } catch (e: Exception) {
            Logu.w("DownloadService", "分片数据落盘失败：${file.name} ${e.message}")
        }
        val offsets = LongArray(shared.segments) { shared.progress.get(it) }
        writeResumeRecord(file, DownloadResumePolicy.Record(tag, totalSize, offsets.toList()))
    }

    /** 下载成功后清掉续传存档（临时文件马上会被替换成成品，存档留着只会误导下一次）。 */
    private fun clearResumeRecord(file: File) {
        val sidecar = resumeSidecar(file) ?: return
        try {
            if (sidecar.exists() && !sidecar.delete())
                Logu.w("DownloadService", "续传存档删除失败：${sidecar.name}")
        } catch (e: Exception) {
            Logu.w("DownloadService", "续传存档删除失败：${sidecar.name} ${e.message}")
        }
    }

    /**
     * 单连接下载（Aria2 关闭时的入口）。
     *
     * 实体实现已合并到 [downFileSpeedSingle]：这两条路径本来就是一回事（一个 GET 顺序写盘），
     * 分开写只会让断点续传的判定存在两份、迟早走偏。这里只做转发。
     */
    @Throws(IOException::class)
    private fun downFileNormal(url: String, file: File, sectionId: Long = -1,
                               baseProgress: Float = 0f, endProgress: Float = 1.0f,
                               resumeTag: String? = null): Int =
        downFileSpeedSingle(url, file, downloadClient(), NetWorkUtil.webHeaders,
            -1L, sectionId, baseProgress, endProgress, resumeTag)

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

    /**
     * 探测一条任务要下多少字节（视频流 + 音频流），作为整条进度条的分母。
     *
     * 探测只用于**显示**：失败返回 -1，进度退回按阶段权重摊派的老算法，不影响能不能下载。
     * DASH 要求两条流都探到才相加 —— 只探到一条会让分母偏小，进度条先冲到 100% 再卡住。
     */
    private fun probeSectionBytes(videoUrls: List<String>, audioUrls: List<String>,
                                  useDash: Boolean, isAudioOnly: Boolean): Long {
        val videoUrl = videoUrls.firstOrNull().orEmpty()
        val audioUrl = audioUrls.firstOrNull().orEmpty()
        return when {
            isAudioOnly -> probeContentLength(audioUrl)
            useDash -> {
                val videoSize = probeContentLength(videoUrl)
                val audioSize = probeContentLength(audioUrl)
                if (videoSize > 0 && audioSize > 0) videoSize + audioSize else -1L
            }
            else -> probeContentLength(videoUrl)
        }
    }

    /**
     * 探单个文件的总字节数：先发 HEAD（最省流量），HEAD 不给 Content-Length 时再发
     * `Range: bytes=0-0`，从 `Content-Range: bytes 0-0/总长` 里读总长。都失败返回 -1。
     */
    private fun probeContentLength(url: String): Long {
        if (url.isEmpty()) return -1L
        val headers = NetWorkUtil.webHeaders
        // 单独加 callTimeout：连上了却一直不给响应时，OkHttp 的 connect/read 超时管不住，
        // 而这一步挡在"开始下载"前面，挂住就是用户点了下载后一直没反应。
        val client = downloadClient().newBuilder()
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        fun buildRequest(isHead: Boolean): okhttp3.Request {
            val builder = okhttp3.Request.Builder().url(url)
            if (isHead) builder.head() else builder.get().header("Range", "bytes=0-0")
            var i = 0
            while (i < headers.size) {
                builder.addHeader(headers[i], headers[i + 1])
                i += 2
            }
            return builder.build()
        }

        try {
            client.newCall(buildRequest(true)).execute().use { res ->
                val len = if (res.isSuccessful) res.header("Content-Length") else null
                val size = len?.toLongOrNull() ?: 0L
                if (size > 0) return size
            }
        } catch (e: Exception) {
            Logu.d("DownloadService", "HEAD 探测文件大小失败：${e.message}")
        }
        try {
            client.newCall(buildRequest(false)).execute().use { res ->
                if (res.code == 206) {
                    val total = res.header("Content-Range")?.substringAfter('/', "")?.trim()?.toLongOrNull() ?: 0L
                    if (total > 0) return total
                }
            }
        } catch (e: Exception) {
            Logu.e("DownloadService", "探测文件大小失败：${e.message}")
        }
        return -1L
    }

    @Throws(IOException::class)
    private fun downFileSpeed(
        url: String, file: File, sectionId: Long = -1,
        baseProgress: Float = 0f, endProgress: Float = 1.0f,
        resumeTag: String? = null
    ): Int {
        // 这里不再 resetFile：能不能接着写由两个下游各自按续传存档判断（#250/#252/#253）。
        // 无条件清空正是"进度清零重下"的源头——暂停、断网、进程被杀之后，下次一进来就先删文件。
        val client = downloadClient()
        val headers = NetWorkUtil.webHeaders

        // 本次运行已判定分片不可用：直接单连接下载。单连接不需要预知文件大小，
        // 少发一次 HEAD 也就少一次可能卡住的往返。
        if (segmentModeDisabled) {
            return downFileSpeedSingle(url, file, client, headers, -1L, sectionId, baseProgress, endProgress, resumeTag)
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
            downFileSpeedSingle(url, file, client, headers, totalSize, sectionId, baseProgress, endProgress, resumeTag)
        } else {
            downFileSpeedSeg(url, file, client, headers, totalSize, sectionId, baseProgress, endProgress, resumeTag)
        }
    }

    /**
     * 发一次下载 GET；[rangeFrom] > 0 时带 `Range: bytes=<rangeFrom>-`（断点续传）。
     * 调用方负责关闭返回的响应。
     */
    @Throws(IOException::class)
    private fun executeGet(
        url: String, client: okhttp3.OkHttpClient, headers: ArrayList<String>, rangeFrom: Long
    ): Response {
        val builder = okhttp3.Request.Builder().url(url).get()
        var i = 0
        while (i < headers.size) {
            builder.addHeader(headers[i], headers[i + 1])
            i += 2
        }
        if (rangeFrom > 0L) builder.header("Range", "bytes=$rangeFrom-")
        return client.newCall(builder.build()).execute()
    }

    /** 从 `Content-Range: bytes 100-299/300` 里取整份文件的总长；取不到返回 -1。 */
    private fun contentRangeTotal(response: Response): Long {
        val header = response.header("Content-Range") ?: return -1L
        val slash = header.lastIndexOf('/')
        if (slash < 0 || slash == header.length - 1) return -1L
        return header.substring(slash + 1).trim().toLongOrNull() ?: -1L
    }

    /**
     * 单连接下载：Aria2 关闭时的唯一实现，也是分片模式失败后的退化路径。
     *
     * 断点续传（反馈 #250/#252/#253）：临时文件旁边有一份 [DownloadResumePolicy] 存档时，按文件长度
     * 算出断点，用 `Range: bytes=<断点>-` 接着下、以追加方式写盘。服务端忽略 Range（回 200 整份文件）
     * 或总长与存档对不上（换线路换了文件）时**立刻清空重下**，绝不把两段不同来源的字节拼在一起。
     *
     * 失败**不删文件**：半成品 + 存档一起留着，换线路重试、用户点继续、几天后重新入队都从断点接着下。
     * 只有不续传的调用（封面 / 字幕 / 多段分片，即 resumeTag == null）才保持老的"失败即删残片"语义。
     */
    @Throws(IOException::class)
    private fun downFileSpeedSingle(url: String, file: File, client: okhttp3.OkHttpClient,
                                     headers: ArrayList<String>, totalSize: Long,
                                     sectionId: Long = -1, baseProgress: Float = 0f, endProgress: Float = 1.0f,
                                     resumeTag: String? = null): Int {
        // 1) 续传判定（见 DownloadResumePolicy）：能接上断点就接着写，接不上才清空重来
        val plan = DownloadResumePolicy.singlePlan(readResumeRecord(file), resumeTag ?: "", file.length())
        if (plan.action == DownloadResumePolicy.Action.COMPLETE) {
            // 文件已经是完整长度：上次下完却停在收尾阶段（替换成品前被系统杀掉）时就是这个状态
            Logu.i("DownloadService", "临时文件已是完整长度，跳过下载：${file.name}（${plan.offset} 字节）")
            clearResumeRecord(file)
            if (sectionId > 0) {
                finishFileProgress(sectionId, plan.offset)
                percent = getDownloadProgress(sectionId)?.progress ?: endProgress
            } else {
                percent = endProgress
            }
            return DownloadPathSpec.NORMAL
        }
        var startBytes = if (plan.action == DownloadResumePolicy.Action.APPEND) plan.offset else 0L
        if (startBytes <= 0L) {
            if (!resetFile(file)) return DownloadPathSpec.ERR_FILE
        } else {
            Logu.i("DownloadService", "断点续传：${file.name} 从 $startBytes 字节接着下")
        }

        var effectiveTotalSize = -1L
        var downloaded = startBytes
        var singleResult = DownloadPathSpec.ERR_NETWORK
        try {
            var response: Response
            try {
                response = executeGet(url, client, headers, startBytes)
            } catch (e: IOException) {
                Logu.e("DownloadService", "下载连接失败：${file.name}")
                singleResult = DownloadStreamTransfer.failureCode(started, isPaused(sectionId), DownloadPathSpec.ERR_NETWORK)
                return singleResult
            }
            if (startBytes > 0L) {
                // 续传请求必须落在 206 且总长对得上：200 说明服务端忽略了 Range（整份文件重发），
                // 总长对不上说明这条线路给的是另一个文件。两种都不能接着写，关掉响应、清空、重发一次。
                val rangeTotal = contentRangeTotal(response)
                if (response.code != 206 || rangeTotal <= startBytes) {
                    Logu.w("DownloadService", "续传不可用（HTTP ${response.code}，总长 $rangeTotal），改为从头下载：${file.name}")
                    try { response.close() } catch (_: Exception) {}
                    if (!resetFile(file)) return DownloadPathSpec.ERR_FILE
                    startBytes = 0L
                    downloaded = 0L
                    try {
                        response = executeGet(url, client, headers, 0L)
                    } catch (e: IOException) {
                        Logu.e("DownloadService", "下载连接失败：${file.name}")
                        singleResult = DownloadStreamTransfer.failureCode(started, isPaused(sectionId), DownloadPathSpec.ERR_NETWORK)
                        return singleResult
                    }
                }
            }
            response.use {
                if (!response.isSuccessful || response.body == null) {
                    Logu.e("DownloadService", "下载失败，HTTP ${response.code}：${file.name}")
                    recordFailReason(sectionId, getString(R.string.text_download_fail_http, response.code))
                    singleResult = DownloadStreamTransfer.failureCode(started, isPaused(sectionId), DownloadPathSpec.ERR_NETWORK)
                    return singleResult
                }
                val body = response.body!!
                // 206 时 body.contentLength() 只是"剩余长度"，整份文件的总长只能从 Content-Range 里取；
                // 取不到就回落到 HEAD 给的 totalSize（HEAD 也没给就是 -1，走伪进度）。
                effectiveTotalSize = if (response.code == 206)
                    contentRangeTotal(response).takeIf { it > 0 } ?: totalSize
                else
                    DownloadStreamTransfer.expectedBytes(totalSize, body.contentLength())
                // 断点存档立刻写下来：进程被系统杀掉（onDestroy 都跑不到）时，恢复就靠它 + 文件长度
                if (resumeTag != null && effectiveTotalSize > 0) {
                    writeResumeRecord(file, DownloadResumePolicy.Record(resumeTag, effectiveTotalSize))
                }
                var lastProgressUpdate = startBytes
                val progressUpdateInterval = if (effectiveTotalSize > 0)
                    Math.max(effectiveTotalSize / 1000, 65536) else 512 * 1024L
                var pseudoProgress = 0f
                singleResult = DownloadPathSpec.ERR_FILE // 在 copy() 返回前失败，说明开流/开文件未完成。
                body.byteStream().use { inputStream ->
                    FileOutputStream(file, startBytes > 0L).use { fos ->
                        val transfer = DownloadStreamTransfer.copy(inputStream, fos, effectiveTotalSize,
                            isRunning = { started }, isPaused = { isPaused(sectionId) },
                            onBytesWritten = { total, _ ->
                                downloaded = total
                                if (total - lastProgressUpdate >= progressUpdateInterval) {
                                    lastProgressUpdate = total
                                    if (effectiveTotalSize <= 0) {
                                        pseudoProgress = DownloadProgressMath.pseudoProgressStep(pseudoProgress)
                                    }
                                    // 进度与速度都由 store 按真实字节算（见 DownloadSectionBytes）；
                                    // 续传时 total 从断点起算，所以进度接着上次的走，不归零
                                    if (sectionId > 0) {
                                        reportFileProgress(sectionId, getString(R.string.text_download_in_progress),
                                            total, effectiveTotalSize, baseProgress, endProgress, pseudoProgress)
                                        percent = getDownloadProgress(sectionId)?.progress ?: percent
                                    }
                                }
                            },
                            startBytes = startBytes)
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
        }
        if (singleResult != DownloadPathSpec.NORMAL) {
            if (resumeTag != null) {
                // 半成品留着续传：文件长度就是断点，存档只补上"整份应该多大"
                if (effectiveTotalSize > 0) {
                    writeResumeRecord(file, DownloadResumePolicy.Record(resumeTag, effectiveTotalSize))
                }
            } else if (singleResult != DownloadPathSpec.ERR_PAUSED) {
                // 不续传的调用（封面 / 字幕 / 多段分片）保持老语义：删掉残片，免得被当成完整文件
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
            // 把"收到/应得字节数"显式报出来：用户报问题时这个数字最有信息量（#87/#90/#250）
            if (effectiveTotalSize > 0 && downloaded < effectiveTotalSize) {
                recordFailReason(sectionId,
                    getString(R.string.text_download_fail_incomplete, downloaded, effectiveTotalSize))
            }
        }
        // 只有读到 EOF 且字节校验通过、文件关闭成功，才把本文件字节并进任务累计量、允许调用方替换成品。
        if (singleResult == DownloadPathSpec.NORMAL) {
            clearResumeRecord(file)
            if (sectionId > 0) {
                finishFileProgress(sectionId, downloaded)
                percent = getDownloadProgress(sectionId)?.progress ?: endProgress
            } else {
                percent = endProgress
            }
        }
        return singleResult
    }

    @Throws(IOException::class)
    private fun downFileSpeedSeg(url: String, file: File, client: okhttp3.OkHttpClient,
                                  headers: ArrayList<String>, totalSize: Long,
                                  sectionId: Long = -1, baseProgress: Float = 0f, endProgress: Float = 1.0f,
                                  resumeTag: String? = null): Int {
        // 动态分片：约每 2MB 一片，上限受用户配置的分片数约束
        val segments = DownloadProgressMath.segmentCount(totalSize, Aria2Util.getSplit())
        val segmentLen = DownloadProgressMath.segmentLen(totalSize, segments)
        // 续传：只有"文件已经是预分配好的整长"时才敢信存档里的逐片进度——
        // 文件被删/被清缓存后 setLength 只会补出一片空洞，照着旧进度写就会留下一段零字节窟窿。
        val resumeOffsets = if (resumeTag != null && file.length() == totalSize)
            DownloadResumePolicy.segmentOffsets(readResumeRecord(file), resumeTag, totalSize, segments)
        else null

        // 预分配整个文件长度：分片线程各自 seek 到自己的区间写入，不预分配就无从随机写。
        java.io.RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(totalSize)
        }

        val shared = SegmentSharedState(segments)
        if (resumeOffsets != null) {
            shared.seed(resumeOffsets)
            Logu.i("DownloadService", "分片断点续传：${file.name} 已有 ${resumeOffsets.sum()} / $totalSize 字节")
        }

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
        // 上次把逐片进度落进续传存档的时刻（约 1 秒一次；崩溃最多丢这 1 秒的进度）
        var lastResumeFlush = 0L
        while (completedSegments(threads) < segments && started && !shared.anyFailed.get() && !isPaused(sectionId)) {
            // 看门狗（"卡在 20%"的核心修复）：没有它，一个卡住的分片要等 OkHttp 16s 读超时
            // × 3 次重试（最坏 48s）才失败，这期间进度一个字节都不动，用户看到的就是"卡住"。
            // 触发后不再干等：取消在飞 Call 并退回整文件单连接下载（见下面的回退分支）。
            if (shared.stalled(threads)) {
                stalled = true
                Logu.e("DownloadService", "分片下载停滞 ${SegmentDownloadPolicy.STALL_TIMEOUT_MS}ms，改用单连接：${file.name}")
                break
            }
            val downloadedNow = shared.totalDownloaded.get()
            if (sectionId > 0) {
                reportFileProgress(sectionId, getString(R.string.text_download_in_progress),
                    downloadedNow, totalSize, baseProgress, endProgress, 0f)
                percent = getDownloadProgress(sectionId)?.progress ?: percent
            } else {
                percent = DownloadProgressMath.progressForBytes(downloadedNow, totalSize, baseProgress, endProgress)
            }

            // 每约 1 秒把逐片进度落进续传存档：进程被直接杀掉（连 onDestroy 都跑不到）时，
            // 恢复只能靠这份存档 + 文件本身（#250 的"下到 99% 断网/被杀"）。
            if (resumeTag != null && System.currentTimeMillis() - lastResumeFlush >= 1000L) {
                lastResumeFlush = System.currentTimeMillis()
                flushSegmentResume(file, resumeTag, totalSize, shared)
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

        // 用户暂停：把逐片进度落盘后返回暂停信号，下次从原处接着下、不回退重下（#252）
        if (isPaused(sectionId)) {
            if (resumeTag != null) flushSegmentResume(file, resumeTag, totalSize, shared)
            return DownloadPathSpec.ERR_PAUSED
        }
        // 服务销毁（onDestroy 置 started=false）时不必再走单连接重下
        if (!started) {
            if (resumeTag != null) flushSegmentResume(file, resumeTag, totalSize, shared)
            return DownloadPathSpec.ERR_UNKNOWN
        }
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
            // 回退前不需要回滚任何计数：分片阶段的字节只是"在飞量"，只有 finishFileProgress()
            // 才会把它并进任务累计量（见 DownloadSectionBytes），所以单连接重下不会把同一批
            // 字节算两遍。进度条走单调护栏，重下期间不会往回掉。
            // 存档必须清掉再回退：setLength 出来的稀疏文件不能当"已下字节"用——单连接是按顺序
            // 追加写的，拿它续传只会得到一份错位文件。回退仍然是整文件重下（与改这条链路前一致）。
            clearResumeRecord(file)
            return downFileSpeedSingle(url, file, client, headers, totalSize, sectionId, baseProgress, endProgress, null)
        }

        val segResult = if (started) DownloadPathSpec.NORMAL else DownloadPathSpec.ERR_UNKNOWN
        // 分片全部写完：把这个文件的实际字节并进任务累计量
        if (segResult == DownloadPathSpec.NORMAL) {
            clearResumeRecord(file)
            if (sectionId > 0) {
                finishFileProgress(sectionId, totalSize)
                percent = getDownloadProgress(sectionId)?.progress ?: endProgress
            }
        }
        return segResult
    }

    /**
     * 一次分片下载的线程间共享状态。
     * 只放必须跨线程共享的原子量与并发容器，避免把 Service 的实例字段牵进分片线程。
     */
    private class SegmentSharedState(val segments: Int) {
        /** 全部分片累计写入的字节（含续传起点；重试从断点接着写，不再回滚）。 */
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

        /**
         * 每个分片**已经落盘**的字节数。既是续传的起点，也是失败重试的起点：
         * 写进文件的字节是真的在文件里了，重试接着往下写，不再整片重下（#250/#252）。
         */
        val progress = java.util.concurrent.atomic.AtomicLongArray(segments)

        init {
            val now = System.currentTimeMillis()
            for (i in 0 until segments) lastByteAt.set(i, now)
        }

        /** 续传起点：把上次已经落盘的逐片字节装进来，并把它们算进总量（否则永远凑不满 totalSize）。 */
        fun seed(offsets: LongArray) {
            for (i in 0 until segments) {
                val value = if (i < offsets.size) offsets[i].coerceAtLeast(0L) else 0L
                progress.set(i, value)
                if (value > 0) totalDownloaded.addAndGet(value)
            }
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

    /**
     * 下载单个分片（最多重试 3 次）。
     *
     * 起点取 [SegmentSharedState.progress] 里"本片已落盘字节"，而不是固定从分片起点开始：
     * 续传（存档里记着的进度）与同一次运行内的失败重试都从那里接着写，
     * 已经写进文件的字节不再重下一遍。因此这里**不再**把失败尝试的字节回滚计数——
     * 那些字节确实在文件里，老实现回滚是因为它重试时从分片起点整片重算。
     */
    private fun downloadSegment(url: String, file: File, client: okhttp3.OkHttpClient,
                                headers: ArrayList<String>, start: Long, end: Long, idx: Int,
                                sectionId: Long,
                                shared: SegmentSharedState) {
        val segmentTotal = end - start + 1
        var attempts = 0
        while (attempts < 3 && started && !shared.anyFailed.get() && !isPaused(sectionId) && !shared.aborted.get()) {
            val begin = shared.progress.get(idx)
            // 本片已经满了（续传存档里就是完整的）：直接当成功返回，别白下一个字节
            if (begin >= segmentTotal) return
            val from = start + begin
            val expectLen = segmentTotal - begin
            var written = 0L
            var ok = false
            var call: okhttp3.Call? = null
            try {
                val reqBuilder = okhttp3.Request.Builder()
                    .url(url)
                    .header("Range", "bytes=$from-$end")
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
                    // Content-Range 带了起点就必须和请求对上（部分镜像不带这个头，此时不强判）。
                    // 续传时请求起点是 from 而不是 start，校验也跟着用 from。
                    if (SegmentDownloadPolicy.isContentRangeMismatch(resp.header("Content-Range"), from)) {
                        shared.rangeUnsupported.set(true)
                        throw IOException("Content-Range 与请求的分片不符")
                    }
                    val buffer = ByteArray(65536)
                    resp.body!!.byteStream().use { inputStream ->
                        java.io.RandomAccessFile(file, "rw").use { rafInner ->
                            rafInner.seek(from)
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
                                // 剩余时间也跟着虚高。
                                shared.totalDownloaded.addAndGet(toWrite.toLong())
                                shared.progress.addAndGet(idx, toWrite.toLong())
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
                    Logu.e("DownloadService", "分片 $idx（$from-$end）3 次尝试均失败：${e.message}")
                }
            } finally {
                call?.let { shared.calls.remove(it) }
            }
            if (ok) {
                // 字节已在循环里逐块计入 totalDownloaded / progress，这里不能再加一次
                return
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

    /**
     * 下载弹幕并落盘。
     *
     * 取 URL → 校验状态码 → 解压 → 判空这一段**不再自己写一遍**，统一走
     * [com.RobinNotBad.BiliClient.util.DanmakuFileFetcher]（26.11.08 验收：这里曾是第 5 份
     * 逐字重复的落盘副本，还是没补空正文校验的那份）。这里只保留本服务需要的东西：
     * 错误码映射（网络侧失败 → [DownloadPathSpec.ERR_NETWORK]，写盘失败 → [DownloadPathSpec.ERR_FILE]）。
     *
     * 写盘用 `writeBytes`（一次 truncate 后写完），不再像旧实现那样先 `resetFile()` 建一个空壳：
     * 写失败时留下的空文件正是「下载成功但播放时没弹幕」的来源。
     */
    @Throws(IOException::class)
    private fun downDanmaku(danmaku: String, danmakuFile: File, sectionId: Long = -1): Int {
        val decompressed = try {
            DanmakuFileFetcher.fetch(danmaku)
        } catch (e: IOException) {
            // 非 2xx / 空正文 / 网络失败 / 解压失败都归到「这份弹幕没拿到」——
            // ERR_NETWORK 的语义就是「含 HTTP 非 2xx、下载不完整」
            return DownloadPathSpec.ERR_NETWORK
        }
        return try {
            danmakuFile.writeBytes(decompressed)
            // 只换状态文案：弹幕字节不进任务的字节预算（见 downloadAttachments）
            if (sectionId > 0) {
                setDownloadState(sectionId, getString(R.string.text_download_danmaku))
            }
            DownloadPathSpec.NORMAL
        } catch (e: IOException) {
            DownloadPathSpec.ERR_FILE
        }
    }

    override fun onDestroy() {
        Logu.d("结束")

        started = false
        batchRunning = false
        percent = -1f
        state = null
        getDownloadProgressMap().let { it.clear() }
        batchStats.reset()
        DownloadProgressStore.resetBatchState()
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
                    // 属于真实数据丢失。批次非正常结束（服务被回收、用户停止、中途出错）时只摘掉
                    // .DOWNLOADING 标记（否则详情页会一直弹「已在下载队列」），成品一律保留。
                    // 半成品（video_new.mp4 / audio_new.m4a / *.resume / 多段分片）也保留，下次接着下（#250/#252/#253）。
                    cleanFailedResidue(folder)
                }
                refreshDownloadList()
            }
        }

        super.onDestroy()
    }
}
