package com.RobinNotBad.BiliClient.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * DASH视频+音频合并工具，使用Android MediaMuxer将分离的视频流和音频流合并为单个MP4文件
 */
object MediaMerger {

    private const val TAG = "MediaMerger"
    private const val BUFFER_SIZE = 256 * 1024

    /**
     * 将DASH格式的视频流和音频流合并为单个MP4文件
     * @param videoFile 视频流文件（会被覆盖为合并后的文件）
     * @param audioFile 音频流文件（合并完成后会被删除）
     * @return 是否合并成功
     */
    fun mergeAv(videoFile: File, audioFile: File): Boolean {
        if (!videoFile.exists() || !audioFile.exists()) {
            Logu.e(TAG, "合并失败：文件不存在")
            return false
        }

        val tempFile = File(videoFile.parentFile, "video_merged_temp.mp4")
        var muxer: MediaMuxer? = null
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null

        try {
            videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(videoFile.absolutePath)

            audioExtractor = MediaExtractor()
            audioExtractor.setDataSource(audioFile.absolutePath)

            // 查找视频轨道
            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = format
                    break
                }
            }
            if (videoTrackIndex < 0) {
                Logu.e(TAG, "合并失败：找不到视频轨道")
                return false
            }

            // 查找音频轨道
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex < 0) {
                Logu.e(TAG, "合并失败：找不到音频轨道")
                return false
            }

            // 创建Muxer
            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            videoExtractor.selectTrack(videoTrackIndex)
            audioExtractor.selectTrack(audioTrackIndex)

            val videoOutTrackIndex = muxer.addTrack(videoFormat!!)
            val audioOutTrackIndex = muxer.addTrack(audioFormat!!)
            muxer.start()

            // 写入视频数据
            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val bufferInfo = MediaCodec.BufferInfo()

            var videoDone = false
            var audioDone = false
            var totalWritten = 0L

            while (!videoDone || !audioDone) {
                if (!videoDone) {
                    bufferInfo.set(0, 0, 0, 0)
                    val sampleSize = videoExtractor!!.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        videoDone = true
                    } else {
                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                        bufferInfo.flags = videoExtractor.sampleFlags
                        muxer!!.writeSampleData(videoOutTrackIndex, buffer, bufferInfo)
                        totalWritten += sampleSize
                        videoExtractor.advance()
                    }
                }

                if (!audioDone) {
                    bufferInfo.set(0, 0, 0, 0)
                    val sampleSize = audioExtractor!!.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        audioDone = true
                    } else {
                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                        bufferInfo.flags = audioExtractor.sampleFlags
                        muxer!!.writeSampleData(audioOutTrackIndex, buffer, bufferInfo)
                        totalWritten += sampleSize
                        audioExtractor.advance()
                    }
                }
            }

            muxer.stop()
            muxer.release()
            muxer = null

            videoExtractor.release()
            videoExtractor = null
            audioExtractor.release()
            audioExtractor = null

            // 验证临时文件有效
            if (!tempFile.exists() || tempFile.length() == 0L) {
                Logu.e(TAG, "合并失败：临时文件无效")
                tempFile.delete()
                return false
            }

            // 用临时文件替换原视频文件（同目录直接rename，避免整文件复制）
            videoFile.delete()
            if (!tempFile.renameTo(videoFile)) {
                Logu.e(TAG, "合并失败：无法替换文件，保留临时文件")
                // 不删除tempFile，保留作为备选
                return false
            }

            // 删除音频文件（合并成功后才删除）
            audioFile.delete()

            Logu.d(TAG, "合并成功：${videoFile.name} (${totalWritten / 1024 / 1024}MB)")
            return true        } catch (e: Exception) {
            Logu.e(TAG, "合并异常：${e.message}")
            try {
                tempFile.delete()
            } catch (_: Exception) {}
            return false
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            try { videoExtractor?.release() } catch (_: Exception) {}
            try { audioExtractor?.release() } catch (_: Exception) {}
        }
    }
}
