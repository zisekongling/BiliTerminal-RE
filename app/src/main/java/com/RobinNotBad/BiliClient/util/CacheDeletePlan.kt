package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.R
import java.io.File

/**
 * 一条缓存视频的「删除计划」：定位目录 → 真的删 → 如实给出结果 → 对应提示文案。
 *
 * 为什么要有这个类（反馈 #162）：反馈 #58 只修了缓存列表页的删除，视频详情页「长按缓存按钮」
 * 那条路还是老写法——按「根目录 + 视频标题」硬拼一个路径、`FileUtil.deleteFolder()` 又吞掉
 * 删除结果，于是三件事一起发生：
 *
 * 1. **删错 / 删不到**：目录名 ≠ 标题时（下载侧清洗过标题、多 P 合集、用户改过缓存根），
 *    删的是一个不存在的路径，界面却照样提示「已清除」；
 * 2. **删了列表不认**：不通知缓存列表，条目还在、计数（[com.RobinNotBad.BiliClient.model.VideoFolder.getVideoCount]）
 *    还是旧的；
 * 3. **黑封面**：目录里的 `cover.png` 已经没了，缓存页那条记录还在，封面就渲染成一块黑。
 *
 * 所以「删哪些目录、删成没删成、该提示什么」这套判断不能每处各写一份，统一收在这里。
 * 本类纯 JDK（[File] + [CachePathResolver]），不碰 Android API，可以直接跑 JVM 单测；
 * 调用方只负责切线程、取文案、刷新列表。
 */
object CacheDeletePlan {

    /**
     * 删除结局。三档各对应一条文案，不再把「没删掉」说成「已清除」（旧实现只有「成功」这一种说法）。
     */
    enum class Outcome {
        /** 找到的候选目录全部删干净了 */
        SUCCESS,

        /** 找到了目录、也删掉了一部分，但还有目录没删干净（被占用 / 没写权限 / SD 卡只读） */
        PARTIAL_FAILED,

        /** 一个候选目录都不存在：这条视频本来就没什么可删，或已经被删过了 */
        NOT_FOUND
    }

    /**
     * [execute] / [judge] 的结果。
     *
     * [existed] 与 [deleted] 都按「候选目录」计数：同一部视频可能有多个候选目录（媒体文件反推的
     * 一个 + 按标题拼的一个），全部删掉才算真的清干净。
     */
    data class Result(
        val outcome: Outcome,
        val existed: Int,
        val deleted: Int,
        val failedDirs: List<File> = emptyList()
    ) {
        val ok: Boolean get() = outcome == Outcome.SUCCESS

        /** 第一个没删掉的目录（手表屏窄，提示里只报目录名）。 */
        val firstFailedDir: File? get() = failedDirs.firstOrNull()

        val firstFailedName: String? get() = firstFailedDir?.name
    }

    /** 一条待显示的提示：字符串资源 + 一个可选格式化参数（目录名）。 */
    data class Message(val resId: Int, val arg: String? = null)

    /**
     * 这部视频可能的落盘目录（按可信度排序、已去重），见 [CachePathResolver.videoDirCandidates]。
     *
     * 只做字符串层面的候选，不判断是否真的存在——要不要删由 [execute] 看 `isDirectory` 决定。
     */
    @JvmStatic
    @JvmOverloads
    fun candidateDirs(
        rootPath: String?,
        title: String?,
        mediaFilePath: String? = null
    ): List<File> = CachePathResolver
        .videoDirCandidates(rootPath, title, mediaFilePath)
        .map { File(it) }

    /**
     * 按真实情况判定结局（纯函数，不碰文件系统，单测直接喂数字）。
     *
     * @param existed     找到的候选目录个数
     * @param deleted     删成功的个数
     * @param failedDirs  没删掉的目录
     */
    @JvmStatic
    @JvmOverloads
    fun judge(existed: Int, deleted: Int, failedDirs: List<File> = emptyList()): Result {
        val outcome = when {
            existed <= 0 -> Outcome.NOT_FOUND
            failedDirs.isNotEmpty() -> Outcome.PARTIAL_FAILED
            else -> Outcome.SUCCESS
        }
        return Result(outcome, existed, deleted, failedDirs.toList())
    }

    /**
     * 真的把这部视频的缓存目录删掉，并把结果**如实**返回。
     *
     * 不切线程、不弹提示、不碰列表：这些由调用方按自己的节奏做。删一个候选失败也继续删其它候选
     * ——同一部视频常常同时存在清洗前 / 后的两份老目录，剩一份没删干净就提示没删干净。
     *
     * @param rootPath      缓存根目录（`FileUtil.getVideoDownloadPath().path`；必须与扫描条目时用的同一个根）
     * @param title         标题（缓存列表里就是磁盘目录名）
     * @param mediaFilePath 模型里记录的真实媒体文件绝对路径，可为 null（详情页拿不到，多 P 靠标题候选命中合集根）
     * @param deleter       真正执行删除的函数，默认 [FileUtil.deleteFolderRecursively]；单测里注入以模拟失败
     */
    @JvmStatic
    @JvmOverloads
    fun execute(
        rootPath: String?,
        title: String?,
        mediaFilePath: String? = null,
        deleter: (File) -> Boolean = { FileUtil.deleteFolderRecursively(it) }
    ): Result {
        var existed = 0
        var deleted = 0
        val failedDirs = ArrayList<File>()

        for (dir in candidateDirs(rootPath, title, mediaFilePath)) {
            // 只删真实存在的目录：候选里通常有几条是猜的（老数据的清洗前标题），
            // 跳过后「一个都没找到」就是 NOT_FOUND，而不是把「本来就没有」说成「已清除」
            if (!dir.isDirectory) continue
            existed++
            if (deleter(dir)) deleted++ else failedDirs.add(dir)
        }

        return judge(existed, deleted, failedDirs)
    }

    /**
     * 结局 → 提示文案（资源 id + 参数）。放进本类是为了「分级提示」只有一份，详情页与列表页共用。
     */
    @JvmStatic
    fun messageOf(result: Result): Message = when (result.outcome) {
        Outcome.SUCCESS -> Message(R.string.msg_cache_delete_success)
        // 有目录没删掉：报第一个目录名，让用户自己去文件管理器动手
        Outcome.PARTIAL_FAILED -> Message(R.string.msg_cache_delete_partial_failed, result.firstFailedName ?: "")
        Outcome.NOT_FOUND -> Message(R.string.msg_cache_delete_not_found)
    }
}
