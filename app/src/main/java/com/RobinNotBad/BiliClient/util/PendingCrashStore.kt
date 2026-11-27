package com.RobinNotBad.BiliClient.util

import android.content.Context
import java.io.File
import kotlin.random.Random

/**
 * 崩溃报告的**本地暂存区**。
 *
 * <p>**为什么需要它**：原先崩溃报告只有「采完就发」一条路——[com.RobinNotBad.BiliClient.api.TerminalApi.uploadCrash]
 * 一旦撞上 IOException 就返回 -2，payload 只活在内存里，**失败即丢**；用户如果在崩溃页的 3 秒窗口里点了
 * 「退出」（`CatchActivity` 里直接 killProcess），这条报告连长出来的机会都没有。这两条路合起来的结果就是
 * 用户嘴里那句「崩溃了，但后台一条记录都看不到」。
 *
 * <p>所以改成「先落盘 → 再上传 → 成功才删」：磁盘是崩溃现场唯一可靠的存储介质，网络和进程都不算数。
 *
 * <p>**边界**：最多 [MAX_FILES] 条、单条不超过 [MAX_FILE_BYTES]，超了删最旧的。一条崩溃的 payload 上限是
 * 内存日志 [Logu] 的 64K 字符 + 堆栈，正常在 100KB 以内，于是最坏情况占 ~2MB，手表上也扛得住；
 * 又因为「每次启动最多补传 3 条」，堆积的东西总会被翻到，不会永远躺在那里。
 *
 * <p>**存的是最终报文**（就是发给 `/upload/stack` 的那个 JSON 字符串），不是原始异常：补传时不需要
 * 重新采集机型、内存、版本这些东西，而这些信息在下次启动时已经不对了。
 */
object PendingCrashStore {

    /** 暂存目录名（在 `filesDir` 下）。 */
    const val DIR_NAME = "crash_pending"

    /** 最多保留的崩溃报告条数，超了删最旧的。 */
    const val MAX_FILES = 20

    /** 单条报告的体积上限：超了当作坏文件丢掉，避免一个巨大的堆栈把整个目录撑爆。 */
    const val MAX_FILE_BYTES = 512 * 1024

    private const val SUFFIX = ".json"
    private const val TMP_SUFFIX = ".tmp"

    /**
     * 认领中的后缀：`x.json` 被认领后改名为 `x.json.inflight`。
     *
     * <p>**为什么需要认领**：同一条报告可能被好几条路径同时发——崩溃页（`:error_activity` 进程）的
     * [CrashReporter.upload]、主进程冷启动的 [CrashReporter.retryPending]。它们各自 [list] 出同一批文件，
     * 于是同一条报告会 POST 好几次。
     *
     * <p>办法是**改名**而不是加锁：同目录 rename 是原子的，谁先把名字抢走谁发，另一个 rename 直接失败、
     * 跳过这条。[list] 只收 [SUFFIX] 结尾，所以认领中的文件自然不在待传队列里。
     */
    const val INFLIGHT_SUFFIX = ".inflight"

    /** 认领超过这么久还没结果，就当认领它的进程死在半路（被系统杀掉 / 断电），回收重发。 */
    const val STALE_INFLIGHT_MS = 10 * 60 * 1000L

    /**
     * 取暂存目录。**不在这里 mkdirs**——写的时候才建，读的时候目录不存在就是「没有待传报告」，
     * 免得每次冷启动都白建一个空目录。取不到（context 为空 / filesDir 异常）返回 null。
     */
    fun dirOf(context: Context?): File? = try {
        context?.let { File(it.filesDir, DIR_NAME) }
    } catch (t: Throwable) {
        null
    }

    /**
     * 存一条报告，返回落盘后的文件；失败返回 null。
     *
     * <p>写入是**先写 .tmp 再改名**：改名在同一分区内是原子的，这样即使写到一半断电 / 被杀，
     * 目录里也不会出现半截 JSON（半截文件会被补传逻辑当成坏文件删掉，等于白丢一条）。
     */
    fun save(dir: File?, json: String): File? {
        if (dir == null) return null
        return try {
            if (!dir.isDirectory && !dir.mkdirs()) return null
            val file = File(dir, newName())
            if (!writeAtomically(file, json)) return null
            // keep 传进 prune：同一毫秒内写的多条，修改时间可能并列，排序退化到文件名（含随机后缀），
            // 那就有极小概率把「刚存进去的这条」当成最旧的删掉。这里点名保住它。
            prune(dir, MAX_FILES, file)
            file
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * 用新的报文覆盖同一个文件（`CatchActivity` 3 秒后按用户最终勾选重写用）。
     * 文件为空或写失败返回 false，调用方应退回 [save] 存一份新的。
     */
    fun rewrite(file: File?, json: String): Boolean {
        if (file == null) return false
        return try {
            writeAtomically(file, json)
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * 待传报告，**最旧的在前**（按修改时间，同一时间按文件名）。
     *
     * <p>用修改时间而不是文件名排序，是为了让补传队列能轮转：一条总是传不上去的报告在失败后会被
     * [touch] 一下、挪到队尾，下次启动先试别人；否则队头那条坏报告会把后面的一起堵死。
     */
    fun list(dir: File?): List<File> {
        if (dir == null) return emptyList()
        val all = try {
            dir.listFiles()
        } catch (t: Throwable) {
            null
        } ?: return emptyList()
        return all.filter { it.isFile && it.name.endsWith(SUFFIX) }
            .sortedWith(compareBy({ it.lastModified() }, { it.name }))
    }

    /**
     * 认领一条待传报告：把 `x.json` 改名为 `x.json.inflight`。
     *
     * <p>认领成功后会把 [File.setLastModified] 打上**当前时间**——不这么做，[recoverStaleInflight]
     * 看到的是这条报告「崩溃时刻」的修改时间，一条半小时前崩的报告一被认领就会被当成超时回收，
     * 等于认领白做。改名成功但改时间失败时最多退回原行为（可能被提前回收），不影响正确性。
     *
     * @return 认领后的文件（发完要 [delete]，发失败要 [release]）；已被别的路径抢走 / 文件不存在返回 null。
     */
    fun claim(file: File?): File? {
        if (file == null) return null
        return try {
            if (!file.isFile) return null
            val inflight = File(file.parentFile, file.name + INFLIGHT_SUFFIX)
            if (inflight.exists()) return null
            if (!file.renameTo(inflight)) return null
            inflight.setLastModified(System.currentTimeMillis())
            inflight
        } catch (t: Throwable) {
            null
        }
    }

    /** 归还认领：把 `x.json.inflight` 改回 `x.json`（上传失败时调，让下次启动还能捡到它）。 */
    fun release(inflight: File?): Boolean {
        if (inflight == null) return false
        return try {
            val name = inflight.name
            if (!inflight.isFile || !name.endsWith(INFLIGHT_SUFFIX)) return false
            inflight.renameTo(File(inflight.parentFile, name.removeSuffix(INFLIGHT_SUFFIX)))
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * 回收「认领了但没结果」的文件：超过 [staleMs] 没动过的 `.inflight` 改回 `.json` 重新入队，返回回收条数。
     *
     * <p>不回收的话，认领它的进程若在 POST 途中被系统杀掉，这条报告就永远停在 `.inflight` 里：
     * 既不在待传队列（[list] 按后缀筛），也不在 [prune] 的视野里，等于静默丢失。
     */
    fun recoverStaleInflight(
        dir: File?,
        now: Long = System.currentTimeMillis(),
        staleMs: Long = STALE_INFLIGHT_MS
    ): Int {
        if (dir == null) return 0
        val all = try {
            dir.listFiles()
        } catch (t: Throwable) {
            null
        } ?: return 0
        var recovered = 0
        for (f in all) {
            if (!f.isFile || !f.name.endsWith(INFLIGHT_SUFFIX)) continue
            try {
                if (now - f.lastModified() < staleMs) continue
                if (release(f)) recovered++
            } catch (t: Throwable) {
                // 单条回收获失败不影响其它
            }
        }
        return recovered
    }

    fun delete(file: File?): Boolean = try {
        file?.delete() == true
    } catch (t: Throwable) {
        false
    }

    /** 把文件推到队尾（补传失败时调）。 */
    fun touch(file: File?) {
        if (file == null) return
        try {
            file.setLastModified(System.currentTimeMillis())
        } catch (t: Throwable) {
            // 推不动就算了，最多是这条下次还被先试一次
        }
    }

    /**
     * 只保留最新的 [max] 条。
     *
     * @param keep 无论排序如何都要保住的文件（刚写进去的那条），可以为 null。
     */
    fun prune(dir: File?, max: Int = MAX_FILES, keep: File? = null) {
        if (max <= 0) return
        val files = list(dir)
        val overflow = files.size - max
        if (overflow <= 0) return
        files.asSequence()
            .filter { keep == null || it.absolutePath != keep.absolutePath }
            .take(overflow)
            .forEach { delete(it) }
    }

    /** 读回报文；文件不见了 / 体积超限 / 读失败 / 内容是空的都返回 null（调用方据此删文件）。 */
    fun read(file: File?): String? {
        if (file == null) return null
        return try {
            if (!file.isFile || file.length() > MAX_FILE_BYTES) null
            else file.readText(Charsets.UTF_8).ifBlank { null }
        } catch (t: Throwable) {
            null
        }
    }

    private fun writeAtomically(file: File, json: String): Boolean {
        val tmp = File(file.parentFile, file.name + TMP_SUFFIX)
        return try {
            tmp.writeText(json, Charsets.UTF_8)
            if (tmp.renameTo(file)) {
                true
            } else {
                // 有些文件系统不让改已存在的文件：退回直接写（可能不原子，但总比丢掉强）
                tmp.delete()
                file.writeText(json, Charsets.UTF_8)
                true
            }
        } catch (t: Throwable) {
            tmp.delete()
            false
        }
    }

    /** 文件名 = 毫秒时间戳 + 随机后缀：时间戳让人肉一眼看出先后，随机后缀防同一毫秒两条撞名。 */
    private fun newName(): String =
        System.currentTimeMillis().toString() + "-" +
            Random.nextInt(0x10000).toString(16).padStart(4, '0') + SUFFIX
}
