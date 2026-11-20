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
