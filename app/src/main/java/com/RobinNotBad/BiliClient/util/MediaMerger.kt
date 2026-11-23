package com.RobinNotBad.BiliClient.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.StatFs
import java.io.File
import java.nio.ByteBuffer

/**
 * 把 DASH 分离的「纯视频流」与「纯音频流」重新封装成一个普通 MP4 单文件。
 *
 * 背景：B 站 1080P 及以上只提供 DASH，一条纯视频（video.m4s）+ 一条纯音频（audio.m4s），
 * 两者都是**分片 MP4**（moov 里的采样表是空的，真正的采样在 moof/mdat 里）。720P 及以下是
 * 音视频合一的普通 MP4，播放器直接能放；DASH 双文件则需要播放器额外挂一条外部音轨。
 * 这里在下载完成后把两条流 remux（只换容器、不重新编码）成一个标准 MP4：
 *
 * - 用 [MediaExtractor] 读两条流的采样：它内部会解析 moof，Android 4.4（API 19）起就支持分片 MP4；
 * - 用 [MediaMuxer] 按 `sampleTime` 归并写入，输出标准（非分片）MP4，moov 在文件尾；
 * - 不重新编码，画质无损、几秒内完成、几乎不占 CPU。
 *
 * 失败时（找不到轨道、编码不被 MediaMuxer 支持、空间不足、被中止）返回非 [RESULT_OK]，
 * 调用方应当保留原来的双文件继续用，**不要因此判定下载失败**。
 *
 * 注意：[MediaMuxer] 无法写入杜比全景声（ec-3/eac3）与无损 FLAC 音轨；本项目选流时已经优先
 * 选 AVC + AAC（见 `model/DashData.java` 的 codecPriority），正常路径不会撞上。
 *
 * 另一个硬边界：Android 7.1（API 25，Nougat MR1）之前 MediaMuxer 不支持 MP4 B 帧，
 * 带 B 帧的 H.264 会在 native 层直接崩溃；低于该版本时本方法直接返回失败，见 [MediaMuxerSupport]。
 */
object MediaMerger {

    /** 合并成功，产物可用 */
    const val RESULT_OK = 0

    /** 输入里找不到可用的视频轨或音频轨（容器异常、不是分片 MP4 等） */
    const val RESULT_NO_TRACK = 1

    /** 磁盘剩余空间不足以同时放下源文件与合并产物 */
    const val RESULT_NO_SPACE = 2

    /** 被 `shouldStop` 中止（服务停止等） */
    const val RESULT_STOPPED = 3

    /** 其他失败：IO 异常、MediaMuxer 不支持该编码、产物不完整等 */
    const val RESULT_FAILED = 4

    private const val TAG = "MediaMerger"

    /** 每次读写的缓冲区大小 */
    private const val BUFFER_SIZE = 1024 * 1024

    /** 合并产物在「源文件大小之和」之外额外需要的余量（moov 采样表、对齐开销） */
    private const val SPACE_MARGIN_BYTES = 4L * 1024 * 1024

    /** 进度回调节流：进度增量至少 1% */
    private const val PROGRESS_STEP = 0.01f

    /** 进度回调节流：两次回调至少间隔 200ms（避免每个样本都跨进程调 setDownloadProgress） */
    private const val PROGRESS_MIN_INTERVAL_MS = 200L

    /** `MediaFormat` 里旋转角度的键；常量 `MediaFormat.KEY_ROTATION` 在 API 23 才公开，这里用字面量 */
    private const val KEY_ROTATION = "rotation-degrees"

    /**
     * 把 [videoFile] 与 [audioFile] 合并成 [mergedFile] 单文件（remux，不重新编码）。
     *
     * @param onProgress 合并进度回调，参数为 0..1 的比例；在主下载线程回调，调用方自行节流展示
     * @param shouldStop 返回 true 时中止合并；每处理一个样本检查一次，开销可忽略
     * @return [RESULT_OK] 成功；[RESULT_STOPPED] 被中止；其它值为失败/跳过，此时 [mergedFile] 已被清理，
     *         调用方应保留源文件
     */
    fun mergeAv(
        videoFile: File,
        audioFile: File,
        mergedFile: File,
        onProgress: ((Float) -> Unit)? = null,
        shouldStop: (() -> Boolean)? = null,
        onStage: ((String) -> Unit)? = null
    ): Int {
        fun stage(name: String) {
            try {
                onStage?.invoke(name)
            } catch (_: Throwable) {
                // 诊断回调绝不能影响合并本身
            }
        }

        if (!MediaMuxerSupport.supportsBFrameMuxing(Build.VERSION.SDK_INT)) {
            // Android 7.1（API 25）之前 MediaMuxer 不支持 MP4 B 帧，带 B 帧的 H.264 会在 native 层直接崩溃。
            Logu.w(TAG, "合并跳过：Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）的 MediaMuxer 不支持 B 帧")
            return RESULT_FAILED
        }

        if (!videoFile.isFile || !audioFile.isFile) {
            Logu.e(TAG, "合并跳过：输入文件不存在（video=${videoFile.absolutePath} audio=${audioFile.absolutePath}）")
            return RESULT_FAILED
        }

        mergedFile.absoluteFile.parentFile?.mkdirs()
        if (mergedFile.exists() && !mergedFile.delete()) {
            Logu.e(TAG, "合并失败：上次残留的合并文件删不掉（${mergedFile.absolutePath}）")
            return RESULT_FAILED
        }

        val inputBytes = videoFile.length() + audioFile.length()
        if (!hasEnoughSpace(mergedFile, inputBytes)) {
            Logu.e(TAG, "合并跳过：剩余空间不足（至少需要 ${inputBytes + SPACE_MARGIN_BYTES} 字节）")
            return RESULT_NO_SPACE
        }

        var result = RESULT_FAILED
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            stage("extractor")
            videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor = MediaExtractor()
            audioExtractor.setDataSource(audioFile.absolutePath)

            stage("tracks")
            val videoTrackIndex = selectTrack(videoExtractor, "video/")
            val audioTrackIndex = selectTrack(audioExtractor, "audio/")
            if (videoTrackIndex < 0 || audioTrackIndex < 0) {
                Logu.e(TAG, "合并跳过：找不到可用轨道（videoTrack=$videoTrackIndex audioTrack=$audioTrackIndex）")
                return RESULT_NO_TRACK
            }
            videoExtractor.selectTrack(videoTrackIndex)
            audioExtractor.selectTrack(audioTrackIndex)

            val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
            val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)

            stage("muxer")
            muxer = MediaMuxer(mergedFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val videoOutTrack = muxer.addTrack(videoFormat)
            val audioOutTrack = muxer.addTrack(audioFormat)
            val rotation = if (videoFormat.containsKey(KEY_ROTATION)) videoFormat.getInteger(KEY_ROTATION) else 0
            if (rotation != 0) muxer.setOrientationHint(rotation)
            stage("muxer-start")
            muxer.start()
            muxerStarted = true

            stage("write")
            result = copySamples(
                videoExtractor, audioExtractor, muxer,
                videoOutTrack, audioOutTrack, inputBytes, onProgress, shouldStop
            )
        } catch (e: Exception) {
            Logu.e(TAG, "合并失败：${videoFile.name} + ${audioFile.name}", e)
            result = RESULT_FAILED
        } finally {
            stage("stop")
            releaseExtractor(videoExtractor)
            releaseExtractor(audioExtractor)
            if (muxerStarted) {
                try {
                    muxer?.stop()
                } catch (e: Exception) {
                    // stop 失败说明 moov 没写完整，产物不可用
                    Logu.e(TAG, "合并失败：MediaMuxer 收尾失败，产物不完整", e)
                    result = RESULT_FAILED
                }
            }
            stage("release")
            try {
                muxer?.release()
            } catch (e: Exception) {
                Logu.e(TAG, "MediaMuxer 释放失败", e)
            }
            if (result == RESULT_OK && (!mergedFile.isFile || mergedFile.length() <= 0L)) {
                Logu.e(TAG, "合并失败：产物为空")
                result = RESULT_FAILED
            }
            if (result != RESULT_OK) mergedFile.delete()
        }
        return result
    }

    /**
     * 按 sampleTime 归并两条轨的采样写入 muxer。
     *
     * 每轮比较两条轨当前样本的时间戳，先写早的那个；某条轨读完就只写另一条（音频通常比视频长）。
     * 这样输出的 interleave 是单调的，播放器 seek 与 av sync 都正常。
     */
    private fun copySamples(
        videoExtractor: MediaExtractor,
        audioExtractor: MediaExtractor,
        muxer: MediaMuxer,
        videoOutTrack: Int,
        audioOutTrack: Int,
        totalBytes: Long,
        onProgress: ((Float) -> Unit)?,
        shouldStop: (() -> Boolean)?
    ): Int {
        val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
        val bufferInfo = MediaCodec.BufferInfo()
        var videoDone = false
        var audioDone = false
        var readBytes = 0L
        var lastReportedProgress = -1f
        var lastReportAt = 0L

        while (!videoDone || !audioDone) {
            if (shouldStop?.invoke() == true) return RESULT_STOPPED

            val videoTime = if (videoDone) -1L else videoExtractor.sampleTime
            val audioTime = if (audioDone) -1L else audioExtractor.sampleTime
            if (videoTime < 0L) videoDone = true
            if (audioTime < 0L) audioDone = true
            if (videoDone && audioDone) break

            val takeVideo = !videoDone && (audioDone || videoTime <= audioTime)
            val extractor = if (takeVideo) videoExtractor else audioExtractor
            val outTrack = if (takeVideo) videoOutTrack else audioOutTrack

            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) {
                // 与 sampleTime<0 等价，标记该轨结束，下一轮只处理另一条
                if (takeVideo) videoDone = true else audioDone = true
                continue
            }

            buffer.position(0)
            buffer.limit(size)
            // MediaMuxer 只把 flags 当"是否关键帧"看，非 0 即可；
            // SAMPLE_FLAG_SYNC(1) 与 BUFFER_FLAG_KEY_FRAME(1) 值相同，用前者可避开 API 21 常量
            val isSyncFrame = (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
            bufferInfo.set(0, size, extractor.sampleTime, if (isSyncFrame) MediaExtractor.SAMPLE_FLAG_SYNC else 0)
            muxer.writeSampleData(outTrack, buffer, bufferInfo)
            extractor.advance()
            readBytes += size

            if (onProgress != null && totalBytes > 0L) {
                val progress = (readBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                val now = System.currentTimeMillis()
                if (progress - lastReportedProgress >= PROGRESS_STEP && now - lastReportAt >= PROGRESS_MIN_INTERVAL_MS) {
                    lastReportedProgress = progress
                    lastReportAt = now
                    runCatching { onProgress.invoke(progress) }
                }
            }
        }
        return RESULT_OK
    }

    /** 找第一条 MIME 以 [mimePrefix] 开头的轨道，找不到返回 -1 */
    private fun selectTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return index
        }
        return -1
    }

    /**
     * 剩余空间是否够：源文件还在盘上，合并期间要额外再写一份同样大小的产物。
     * 读不到剩余空间时放行——真没空间时 MediaMuxer 会抛 IO 异常，走失败兜底。
     */
    private fun hasEnoughSpace(targetFile: File, inputBytes: Long): Boolean {
        return try {
            val dir = targetFile.absoluteFile.parentFile ?: return true
            val statFs = StatFs(dir.absolutePath)
            statFs.availableBytes >= inputBytes + SPACE_MARGIN_BYTES
        } catch (e: Exception) {
            Logu.w(TAG, "读取剩余空间失败，跳过空间检查：${e.message}")
            true
        }
    }

    /** 释放 extractor，释放失败不影响合并结果 */
    private fun releaseExtractor(extractor: MediaExtractor?) {
        try {
            extractor?.release()
        } catch (e: Exception) {
            Logu.e(TAG, "MediaExtractor 释放失败", e)
        }
    }
}
