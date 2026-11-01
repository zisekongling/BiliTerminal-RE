package com.RobinNotBad.BiliClient.activity.player

import kotlin.math.abs

/**
 * 「外挂音轨在静音/seek 时该不该出声」的纯判定（零 Android 依赖，可直接 JVM 单测）。
 *
 * **为什么需要它**（反馈 #41）：本地 DASH 双文件视频（纯视频 `video.mp4` + 外挂 `audio.m4a`）
 * 由一条 `android.media.MediaPlayer` 单独放声音。用户在**静音状态**下拖进度条，拖完会短促地
 * 出声；只有用到外挂音轨时才出现（合一 MP4 没有这条音轨，所以正常）。
 *
 * 定位到的两条**代码层**成因（与 ROM 无关，任何设备都可能中）：
 * 1. `initAudioTrack` 里 `player.start()` 之后才由 `applyVolumeGain()` 打增益——中间那一小段
 *    外挂音轨按 `MediaPlayer` 的默认音量 1.0 在放，静音用户也会听到一下；
 * 2. `seekTo` 是这条音轨唯一会「重新起播/重建输出缓冲」的动作，而全代码没有任何一处
 *    「seek 期间把静音补回去」的策略：静音只靠一次性的 `setVolume(0,0)`，中途被重置就只能等
 *    下一次 `applyVolumeGain()`（换清晰度/切P/重试才调），于是"拖完响一小会"。
 *
 * 因此把口径定成一条不变式（由 `PlayerActivity.syncExternalAudioPlayback()` 落地）：
 *
 * > **外挂音轨只在「真的能听见」且「正在播」时运行；静音（软件增益 0% 或系统媒体音量 0）时直接暂停。**
 *
 * 暂停之后 seek 不可能出声，解静音时再对齐位置起播——既修掉瞬时出声，又不影响倍速同步
 * （倍速只作用于主播放器与弹幕，外挂音轨本来就按位置漂移校正）。
 */
object AudioSeekVolumePolicy {

    /** 外挂音轨下一步要做的动作。 */
    enum class AudioAction {
        /** 维持现状。 */
        NONE,

        /** 该起播（从静音恢复/刚进入播放态）。 */
        START,

        /** 该暂停（静音了，继续跑只会在 seek 时漏出一下声音）。 */
        PAUSE
    }

    /**
     * 允许的音画偏差（毫秒）。与 `PlayerActivity.progressChange` 里既有的 800ms 漂移校正阈值一致，
     * 不要在两处各写一个数。
     */
    const val RESYNC_TOLERANCE_MS = 800L

    /**
     * 用户此刻是否**听不见**。
     *
     * 两个来源都算静音：播放器的「无极调音」增益被拉到 0%（采样乘 0），
     * 或系统媒体音量为 0（外挂 `MediaPlayer` 走的就是 STREAM_MUSIC）。
     *
     * @param gainPercent 软件增益 0~100（钳制由 `PlayerVolumePolicy` 负责，这里只判 0）
     * @param systemVolume 系统媒体音量档位；拿不到时调用方应传 `Int.MAX_VALUE`（= 按"不静音"处理）
     */
    fun isSilent(gainPercent: Int, systemVolume: Int): Boolean =
        gainPercent <= 0 || systemVolume <= 0

    /**
     * 决定外挂音轨下一步动作（幂等：一致时返回 [AudioAction.NONE]）。
     *
     * @param silent [isSilent] 的结果
     * @param playbackWanted 视频此刻是否在播（`isPlaying && isPrepared`）
     * @param audioRunning 外挂音轨此刻是否真的在跑（由播放页维护的标志，不去问 MediaPlayer）
     */
    fun actionFor(silent: Boolean, playbackWanted: Boolean, audioRunning: Boolean): AudioAction = when {
        // 静音优先：哪怕视频还在播，也先把这条音轨停掉
        silent && audioRunning -> AudioAction.PAUSE
        // 只有能听见且该播时才起播；暂停态起播会让声音与画面脱节
        !silent && playbackWanted && !audioRunning -> AudioAction.START
        else -> AudioAction.NONE
    }

    /**
     * 起播前是否需要先把音轨位置对齐到视频位置。
     *
     * 静音期间音轨是停着的，视频却继续走；直接从那儿 `start()` 会先放出几秒"旧声音"，
     * 再被 `progressChange` 的 800ms 漂移校正拽回去——听感是"解静音后先乱一下"。
     */
    fun shouldResyncBeforeStart(audioPosMs: Int, videoPosMs: Int): Boolean =
        abs(audioPosMs.toLong() - videoPosMs.toLong()) > RESYNC_TOLERANCE_MS
}
