package com.RobinNotBad.BiliClient.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.RobinNotBad.BiliClient.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 崩溃前持续落盘的全量诊断日志（仅 Debug + -PdiagLog=true 时启用）。
 *
 * 与 [Logu] 的应用专属日志不同，这份日志刻意写到公共 Download 根目录：
 * 安卓 5 手表上没有 adb、也不一定能装文件管理器，出问题后用户直接把
 * `Download/rebili-full.log` 取出来就能看。代价是公共目录可被其他应用读取，
 * 所以只在诊断包里开，且沿用 [LogSanitizer] 的脱敏结果。
 *
 * 写入策略：
 * - 业务线程只做一次 O(1) 入队，I/O 全在 `rebili-full-log` 线程；
 * - 单文件上限 [MAX_BYTES]（5MB），超限把末尾 [MAX_BYTES] / 2 原子换到文件头，
 *   先写 `.tmp`、fsync、再 rename，避免掉电/崩溃留下半个文件；
 * - 每 0.5 秒或累计 64KB fsync 一次，崩溃时最多丢最后约半秒的日志；
 * - 公共 Download 不可写（Android 10+ 分区存储）时自动退回应用专属目录，
 *   路径会写进日志头，取不到公共文件时按日志里的路径找。
 */
object PublicLogRecorder {

    /** 公共目录里的文件名。 */
    const val FILE_NAME = "rebili-full.log"

    /** 单文件上限 5MB；超限保留末尾 2.5MB。 */
    private const val MAX_BYTES = 5L * 1024 * 1024

    /** 队列容量。诊断包宁可丢几行也不能阻塞业务线程。 */
    private const val QUEUE_CAPACITY = 8192

    /** 诊断包要尽量贴住崩溃瞬间：每 0.5 秒 fsync 一次，最坏只丢最后半秒。 */
    private const val SYNC_INTERVAL_MS = 500L

    private val queue = LinkedBlockingQueue<String>(QUEUE_CAPACITY)
    private val dropped = AtomicLong(0)
    private val writeLock = Any()

    @Volatile
    private var started = false

    @Volatile
    private var currentFile: File? = null

    @Volatile
    private var output: FileOutputStream? = null

    /** 当前已写入字节数（只允许在 [writeLock] 内读写）。 */
    private var written = 0L

    /** 上次 fsync 的时间（只允许在 [writeLock] 内读写）。 */
    private var lastSync = 0L

    /** 上一次滚动失败的时间；失败后 30 秒内不再重试，避免每个批次都做一次失败 I/O。 */
    private var lastRotateFailure = 0L

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * 启动写入器。重复调用无副作用；[Context] 用 applicationContext，避免持有 Activity。
     *
     * 必须在主进程调用：崩溃页跑在 `:error_activity`，那边只用 [appendCrash] 直接追加。
     */
    @JvmStatic
    fun start(context: Context) {
        if (started) return
        synchronized(writeLock) {
            if (started) return
            val app = context.applicationContext
            val file = resolveFile(app)
            if (file == null) {
                Log.e("PublicLogRecorder", "找不到可写的诊断日志目录，全量日志未开启")
                return
            }
            currentFile = file
            try {
                openLocked(file, append = true)
                if (file.length() == 0L) {
                    output?.write(header(app, file).toByteArray(Charsets.UTF_8))
                    output?.flush()
                }
                started = true
            } catch (t: Throwable) {
                Log.e("PublicLogRecorder", "打开诊断日志失败: " + t)
                closeLocked()
                return
            }
            Thread(::writerLoop, "rebili-full-log").apply { isDaemon = true }.start()
        }
    }

    /** 业务线程调用：只入队，不碰磁盘。未启动时静默丢弃。 */
    @JvmStatic
    fun record(line: String) {
        if (!started) return
        if (!queue.offer(line)) dropped.incrementAndGet()
    }

    /** 把队列里已经写出去的内容 fsync 到磁盘。 */
    @JvmStatic
    fun flush() {
        if (!started) return
        synchronized(writeLock) {
            try {
                output?.flush()
                output?.fd?.sync()
                lastSync = System.currentTimeMillis()
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * 同步追加一段带标题的日志并立即 fsync。
     *
     * 只给「进程可能马上被 native 崩溃带走」的路径用（例如重封装前后）：
     * 普通 [record] 是异步队列，native abort 时队列里的最后几行会丢。
     * 非诊断包（[BuildConfig.DIAG_FULL_LOG] 为 false）直接返回。
     */
    @JvmStatic
    fun appendSync(context: Context?, title: String, body: String) {
        if (!BuildConfig.DEBUG || !BuildConfig.DIAG_FULL_LOG) return
        val app = context?.applicationContext
        synchronized(writeLock) {
            val file = currentFile ?: app?.let { resolveFile(it) } ?: return
            try {
                if (file.length() > MAX_BYTES) {
                    // 先关掉 writer 的旧句柄再换文件，否则它醒来后仍往被 rename 掉的旧 inode 写
                    closeLocked()
                    LogFileRotation.rotateKeepingTail(file, MAX_BYTES)
                    openLocked(file, append = true)
                }
                FileOutputStream(file, true).use { out ->
                    if (file.length() == 0L && app != null) {
                        out.write(header(app, file).toByteArray(Charsets.UTF_8))
                    }
                    // 公共 Download 可被其他应用读取，目录名/异常文本也过一遍脱敏。
                    val safeBody = LogSanitizer.sanitize(body)
                    val block = buildString {
                        append("\n\n===== ").append(title).append(' ')
                            .append(timeFormat.format(Date())).append(" =====\n")
                        append(safeBody)
                        if (!body.endsWith("\n")) append('\n')
                        append("===== END ").append(title).append(" =====\n")
                    }
                    out.write(block.toByteArray(Charsets.UTF_8))
                    out.flush()
                    out.fd.sync()
                }
                // 同步追加绕过了 writer 的计数器，校正一下，避免后续滚动判断失真
                written = file.length()
            } catch (t: Throwable) {
                Log.e("PublicLogRecorder", "同步追加日志失败: " + t)
            }
        }
    }

    /** 因队列满丢弃的行数。 */
    @JvmStatic
    fun droppedCount(): Long = dropped.get()

    /** 当前日志文件路径；未启动时返回空串。 */
    @JvmStatic
    fun filePath(): String = currentFile?.absolutePath ?: ""

    /**
     * 崩溃页调用：把崩溃现场直接追加到同一个文件。
     *
     * 崩溃页在独立进程，拿不到主进程的 [currentFile] 与队列，所以这里同步打开文件追加；
     * 内容量有上限（堆栈可能很长，调用方负责截断），不会在崩溃页卡太久。
     */
    @JvmStatic
    fun appendCrash(context: Context, title: String, body: String) {
        // 崩溃页在独立进程，currentFile 为空，appendSync 会按 context 重新解析同一个路径。
        appendSync(context, title, body)
    }

    private fun writerLoop() {
        while (true) {
            val first = try {
                queue.poll(500, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
            if (first == null) {
                syncIfDue()
                continue
            }
            val batch = ArrayList<String>(64)
            batch.add(first)
            queue.drainTo(batch, 63)
            try {
                writeBatch(batch)
            } catch (t: Throwable) {
                // 写不进去（存储满、目录被删）不能让线程退出，下一批继续试
                Log.e("PublicLogRecorder", "写诊断日志失败: " + t)
                synchronized(writeLock) { closeLocked() }
            }
        }
    }

    private fun writeBatch(batch: List<String>) {
        val sb = StringBuilder(256 * batch.size)
        for (line in batch) {
            sb.append(line).append('\n')
        }
        val bytes = sb.toString().toByteArray(Charsets.UTF_8)
        synchronized(writeLock) {
            val file = currentFile ?: return
            if (output == null) openLocked(file, append = true)
            if (written + bytes.size > MAX_BYTES && canRotateNow()) {
                if (LogFileRotation.rotateKeepingTail(file, MAX_BYTES)) {
                    closeLocked()
                    openLocked(file, append = true)
                } else {
                    lastRotateFailure = System.currentTimeMillis()
                }
            }
            val out = output ?: return
            out.write(bytes)
            written += bytes.size
            if (bytes.size >= 64 * 1024) {
                out.flush()
                out.fd.sync()
                lastSync = System.currentTimeMillis()
            } else {
                syncIfDueLocked()
            }
        }
    }

    private fun canRotateNow(): Boolean =
        System.currentTimeMillis() - lastRotateFailure > 30_000L

    private fun syncIfDue() {
        synchronized(writeLock) { syncIfDueLocked() }
    }

    private fun syncIfDueLocked() {
        val out = output ?: return
        val now = System.currentTimeMillis()
        if (now - lastSync < SYNC_INTERVAL_MS) return
        try {
            out.flush()
            out.fd.sync()
            lastSync = now
        } catch (_: Throwable) {
        }
    }

    private fun openLocked(file: File, append: Boolean) {
        val out = FileOutputStream(file, append)
        output = out
        written = file.length()
        lastSync = System.currentTimeMillis()
    }

    private fun closeLocked() {
        try {
            output?.flush()
            output?.fd?.sync()
        } catch (_: Throwable) {
        }
        try {
            output?.close()
        } catch (_: Throwable) {
        }
        output = null
    }

    /**
     * 选一个可写目录：优先公共 Download 根目录，其次应用专属外部目录，最后内部 files。
     * 用临时文件探测真实可写性——分区存储下目录存在但 `canWrite()` 仍可能是 true。
     */
    private fun resolveFile(context: Context): File? {
        val candidates = ArrayList<File>(3)
        if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
            try {
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                candidates.add(File(downloads, FILE_NAME))
            } catch (_: Throwable) {
            }
        }
        context.getExternalFilesDir("logs")?.let { candidates.add(File(it, FILE_NAME)) }
        candidates.add(File(File(context.filesDir, "logs"), FILE_NAME))

        for (candidate in candidates) {
            val dir = candidate.parentFile ?: continue
            try {
                if (!dir.exists() && !dir.mkdirs()) continue
                val probe = File.createTempFile("rebili-probe", ".tmp", dir)
                if (!probe.delete()) {
                    probe.deleteOnExit()
                }
                return candidate
            } catch (_: Throwable) {
                // 试下一个候选
            }
        }
        return null
    }

    private fun header(context: Context, file: File): String {
        return buildString {
            append("===== ReBili 全量诊断日志 =====\n")
            append("开始: ").append(timeFormat.format(Date())).append('\n')
            append("版本: ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")")
                .append(" debug=").append(BuildConfig.DEBUG).append('\n')
            append("设备: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" / Android ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
            append("包名: ").append(context.packageName).append('\n')
            append("路径: ").append(file.absolutePath).append('\n')
            append("上限: ").append(MAX_BYTES / 1024 / 1024).append("MB，超限保留末 ")
                .append(MAX_BYTES / 2 / 1024 / 1024).append("MB\n")
            append("===============================\n")
        }
    }
}

/**
 * 文件尾部保留 + 原子替换。抽出来是为了能在纯 JVM 单测里验证（不依赖 Android）。
 */
internal object LogFileRotation {

    /**
     * 保留文件末尾 [maxBytes] / 2，丢弃前半。
     *
     * 先写同目录的 `.tmp` 并 fsync，再 rename 覆盖原文件；rename 在 Android/Linux 上是原子的，
     * 崩溃最坏只会留下一个完整的 `.tmp` 或完整的旧文件，不会出现半个日志。
     *
     * @return true = 发生了滚动；false = 文件不够长或滚动失败（原文件保持可读）
     */
    @JvmStatic
    fun rotateKeepingTail(file: File, maxBytes: Long): Boolean {
        if (!file.isFile || maxBytes <= 0) return false
        val length = file.length()
        val keep = maxBytes / 2
        if (length <= keep) return false
        val tail = ByteArray(keep.toInt())
        try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(length - keep)
                raf.readFully(tail)
            }
        } catch (_: Throwable) {
            return false
        }

        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            FileOutputStream(tmp, false).use { out ->
                out.write(tail)
                out.flush()
                out.fd.sync()
            }
        } catch (_: Throwable) {
            tmp.delete()
            return false
        }

        // Linux/Android 的 rename 会原子替换目标；Windows（本地单测）目标存在时会失败，走回退
        if (tmp.renameTo(file)) return true
        if (file.delete() && tmp.renameTo(file)) return true
        tmp.delete()
        return false
    }
}
