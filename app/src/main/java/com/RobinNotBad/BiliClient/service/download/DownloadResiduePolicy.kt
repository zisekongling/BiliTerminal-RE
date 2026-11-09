package com.RobinNotBad.BiliClient.service.download

/**
 * 失败任务的目录残留该怎么收拾。
 *
 * 背景（反馈 #136，「有些视频一旦下载错误之后就再也无法下载」）：一次失败会在任务目录里留下
 * `.DOWNLOADING`（"下载中"标记）和 `video_new.mp4` 这类半成品，而视频详情页的下载入口只要看到
 * 任务目录存在就只弹一句「已在下载队列 / 已下载完成」、不再入队；失败收尾又只把数据库标成
 * "error"、不清目录。用户去清缓存、重启应用都碰不到那个目录，于是同一个视频被永久挡住。
 *
 * 这里的判定决定「收拾残留时该动到哪一步」：目录里已经有成品就只清临时产物，纯残留就整目录
 * 删掉（下次从零重下），出现陌生文件名则保守只清临时产物，绝不误删用户自己放的东西。
 *
 * 纯文件名判定，不碰文件系统，可直接跑 JVM 单测。
 */
internal object DownloadResiduePolicy {

    /** 元数据文件名，与 `util/VideoMetaManager` 里的私有常量 META_FILE 一致（那边不可跨类引用） */
    private const val META_FILE = ".video_meta.json"

    /** 替换成品前的备份后缀，与 `DownloadService.safeReplaceTemp` 拼的 ".bak" 一致 */
    private const val BAK_SUFFIX = ".bak"

    /** 成品文件名：出现了就说明这个目录里已经有能播的东西 */
    private val FINAL_ARTIFACTS: Set<String> = setOf(
        DownloadPathSpec.FILE_VIDEO,
        DownloadPathSpec.FILE_AUDIO
    )

    /** 本次下载自己生成的文件/目录名：删掉它们不会丢用户数据 */
    private val OWN_ARTIFACTS: Set<String> = setOf(
        DownloadPathSpec.FILE_DOWNLOADING,
        DownloadPathSpec.FILE_QUALITY,
        DownloadPathSpec.FILE_COVER,
        DownloadPathSpec.FILE_DANMAKU,
        DownloadPathSpec.DIR_SUBTITLES,
        DownloadPathSpec.FILE_VIDEO_TMP,
        DownloadPathSpec.FILE_AUDIO_TMP,
        DownloadPathSpec.FILE_VIDEO_MERGING,
        META_FILE
    )

    /** 收拾残留时该做到哪一步 */
    enum class Disposition {
        /** 无事可做：目录不存在、是空目录，或者里面只有用户自己放的文件 */
        NONE,

        /** 只清临时产物（有成品，或出现了陌生文件名要留着） */
        CLEAN_TEMPS,

        /** 整目录删掉：里面全是本次下载自己生成的残留，没有任何成品 */
        DELETE_FOLDER
    }

    /**
     * 目录里有没有成品（`video.mp4` / `audio.m4a`，或它们的 `.bak` 备份）。
     *
     * `.bak` 也算：`FileUtil.cleanDownloadTempFiles` 在正式文件缺失时会把 `.bak` 改名回正式名，
     * 那是「替换在两个 rename 之间被打断」后的唯一成品，不能当残留删掉。
     */
    fun hasFinishedArtifact(names: Collection<String>?): Boolean {
        if (names.isNullOrEmpty()) return false
        return names.any { name ->
            name in FINAL_ARTIFACTS ||
                    (name.endsWith(BAK_SUFFIX) && name.dropLast(BAK_SUFFIX.length) in FINAL_ARTIFACTS)
        }
    }

    /**
     * 判定残留的处理方式。
     *
     * 注意 [Disposition.DELETE_FOLDER] 只在「一个成品都没有，且目录内容全是自己生成的」时给出：
     * 重新入队时 `startDownload` 会把 `.DOWNLOADING` / `.quality` / `.video_meta.json` 重新写出来，
     * 所以整目录删掉不会丢任何东西。
     */
    fun disposition(names: Collection<String>?): Disposition {
        if (names.isNullOrEmpty()) return Disposition.NONE
        if (hasFinishedArtifact(names)) return Disposition.CLEAN_TEMPS
        val onlyOwnArtifacts = names.all { it in OWN_ARTIFACTS || it.endsWith(BAK_SUFFIX) }
        return if (onlyOwnArtifacts) Disposition.DELETE_FOLDER else Disposition.CLEAN_TEMPS
    }
}
