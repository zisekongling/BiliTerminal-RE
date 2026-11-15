package com.RobinNotBad.BiliClient.service.download

/**
 * 下载实现用到的文件名、下载类型与结果码常量。
 *
 * 这些字面量原本散落在 DownloadService 内部：文件名写错会直接让"续传标记/画质标记/成品替换"
 * 三条链路对不上，结果码写错则会被 runDownloadSection 误判成成功或失败。集中到一处后，
 * 单测与下载实现共用同一份真相，改一处不会漏另一处。
 *
 * 纯 Kotlin 常量，无 Android 依赖，可直接跑 JVM 单测。
 */
internal object DownloadPathSpec {

    // ---------- 文件名 ----------

    /** 目录内的"下载中"标记文件；任务成功后删除，存在即表示该目录尚未完成 */
    const val FILE_DOWNLOADING = ".DOWNLOADING"

    /** 画质标记文件，内容为画质 qn 或 "audio_only" */
    const val FILE_QUALITY = ".quality"

    /** 封面文件名 */
    const val FILE_COVER = "cover.png"

    /** 弹幕文件名 */
    const val FILE_DANMAKU = "danmaku.xml"

    /** 字幕子目录名 */
    const val DIR_SUBTITLES = "subtitles"

    /** 视频成品文件名 */
    const val FILE_VIDEO = "video.mp4"

    /** 音频成品文件名 */
    const val FILE_AUDIO = "audio.m4a"

    /** 视频临时文件名：下载全部成功后才替换 [FILE_VIDEO] */
    const val FILE_VIDEO_TMP = "video_new.mp4"

    /** 音频临时文件名：下载全部成功后才替换 [FILE_AUDIO] */
    const val FILE_AUDIO_TMP = "audio_new.m4a"

    /**
     * DASH 音视频合并的临时文件名：合并成功才替换 [FILE_VIDEO]，中断留下的半成品由
     * `FileUtil.cleanDownloadTempFiles` 删除。
     *
     * 刻意不用 `.mp4` 后缀：它不是能直接播放的视频，也避免被"扫描旧合并残留"的兼容逻辑误认。
     */
    const val FILE_VIDEO_MERGING = "video_merging.tmp"

    // ---------- 下载类型与任务类型（写进数据库的字面量） ----------

    /** download_type：仅下载音频 */
    const val TYPE_AUDIO_ONLY = "audio_only"

    /**
     * download_type：番剧（pgc）剧集。
     *
     * 落盘结构与多 P 视频完全一致（季名当父目录、集名当子目录，成品是单个 video.mp4），
     * 这个类型只用于「取流走番剧接口」这一件事——真正的开关是下载表里的 epid 列
     * （见 [com.RobinNotBad.BiliClient.model.DownloadSection.isBangumi]）。
     */
    const val TYPE_VIDEO_PGC = "video_pgc"

    /** 下载表 type 列：单 P 视频 */
    const val TASK_VIDEO_SINGLE = "video_single"

    /** 下载表 type 列：多 P 视频的单个分 P */
    const val TASK_VIDEO_MULTI = "video_multi"

    // ---------- 下载结果码 ----------

    /** 成功 */
    const val NORMAL = 0

    /** 网络失败（含 HTTP 非 2xx、下载不完整） */
    const val ERR_NETWORK = -1

    /** 下载链接解析失败 */
    const val ERR_JSON = -2

    /** 本地文件操作失败（创建/替换失败） */
    const val ERR_FILE = -3

    /** 数据库操作失败 */
    const val ERR_DATABASE = -4

    /** 未知错误（例如批次被取消） */
    const val ERR_UNKNOWN = -7

    /** 任务被用户暂停，不视为失败 */
    const val ERR_PAUSED = -8
}
