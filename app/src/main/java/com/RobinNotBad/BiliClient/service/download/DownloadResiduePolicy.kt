package com.RobinNotBad.BiliClient.service.download

/**
 * 失败/中断的任务目录该怎么收拾（反馈 #136 与 #250 / #252 / #253 的交叉点）。
 *
 * #136 的教训：一次失败会在目录里留下 `.DOWNLOADING`，而视频详情页的下载入口看到它只弹一句
 * 「已在下载队列」、不再入队，于是同一个视频被永久挡住。当时的解法是"整目录删掉"，代价是
 * 用户下了几十分钟的半成品一起没了——#250「下到 99% 断网就得从 0 开始」正是这个代价。
 *
 * 现在改成「只删挡住重试的东西，半成品留着续传」：
 * - 删 `.DOWNLOADING`（否则详情页以为还在队列里，进不去）；
 * - 删 `video_merging.tmp`（两次 rename 之间留下的坏合并件，续不了，下次重下后合并就会覆盖）；
 * - 删"孤儿" 续传存档（`*.resume` 而对应的临时文件已经不在，存档留着只会在下次误命中）；
 * - 删多余/陈旧的 `.bak`；正式名缺失时把它改名回正式名（那是替换被打断后唯一的成品）。
 *
 * 留下：`video_new.mp4` / `audio_new.m4a`（续传的载体）、`*.resume`、`video_seg*.part`、
 * `.quality` / 封面 / 弹幕 / 字幕 / 元数据，以及用户自己放进去的任何文件。
 *
 * 纯文件名判定，不碰文件系统，可直接跑 JVM 单测。
 */
internal object DownloadResiduePolicy {

    /** 替换成品前的备份后缀，与 `DownloadService.safeReplaceTemp` 拼的 ".bak" 一致 */
    private const val BAK_SUFFIX = ".bak"

    /** 成品文件名：出现了就说明这个目录里已经有能播的东西 */
    private val FINAL_ARTIFACTS: Set<String> = setOf(
        DownloadPathSpec.FILE_VIDEO,
        DownloadPathSpec.FILE_AUDIO
    )

    /**
     * 收拾动作：哪些要改名恢复、哪些要删。
     *
     * [restoreFromBackup] 里的名字是 `.bak` 文件（调用方把它改名回去掉后缀的正式名）；
     * [deleteNames] 是直接删掉的名字。
     */
    data class CleanupPlan(
        val restoreFromBackup: List<String> = emptyList(),
        val deleteNames: List<String> = emptyList()
    ) {
        val isNoop: Boolean get() = restoreFromBackup.isEmpty() && deleteNames.isEmpty()
    }

    /** 无事可做的计划 */
    val NOOP = CleanupPlan()

    /** `.bak` 对应的正式文件名（"video.mp4.bak" → "video.mp4"） */
    fun backupTargetName(backupName: String): String {
        if (!backupName.endsWith(BAK_SUFFIX)) return backupName
        return backupName.dropLast(BAK_SUFFIX.length)
    }

    /** 是不是一份续传存档文件名 */
    fun isResumeName(name: String): Boolean = name.endsWith(DownloadPathSpec.SUFFIX_RESUME)

    /** 续传存档对应的临时文件名（"video_new.mp4.resume" → "video_new.mp4"） */
    fun resumeBaseName(name: String): String {
        if (!isResumeName(name)) return name
        return name.dropLast(DownloadPathSpec.SUFFIX_RESUME.length)
    }

    /**
     * 根据目录里的文件名算一份收拾计划。
     *
     * 传 null（目录不存在/不可读，`folder.list()` 返回 null）或空列表都返回 [NOOP]。
     */
    fun cleanupPlan(names: Collection<String>?): CleanupPlan {
        if (names.isNullOrEmpty()) return NOOP
        val present = names.toSet()

        val restore = ArrayList<String>()
        val delete = ArrayList<String>()

        // .bak：正式名不在就改名回去（替换在两次 rename 之间被打断），在就删掉这份陈旧备份
        for (name in present) {
            if (!name.endsWith(BAK_SUFFIX)) continue
            val base = name.dropLast(BAK_SUFFIX.length)
            if (base !in FINAL_ARTIFACTS) continue
            if (base in present) delete.add(name) else restore.add(name)
        }

        // 挡住重新入队的标记：必须摘掉，否则详情页一直以为这条还在下载队列里（#136）
        if (DownloadPathSpec.FILE_DOWNLOADING in present) delete.add(DownloadPathSpec.FILE_DOWNLOADING)

        // 坏合并件：续不了，也不该让下次合并读到它
        if (DownloadPathSpec.FILE_VIDEO_MERGING in present) delete.add(DownloadPathSpec.FILE_VIDEO_MERGING)

        // 孤儿续传存档：临时文件没了，存档留着只会在下次误命中
        for (name in present) {
            if (!isResumeName(name)) continue
            if (resumeBaseName(name) !in present) delete.add(name)
        }

        return CleanupPlan(restore, delete)
    }
}
