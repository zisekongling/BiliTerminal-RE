package com.RobinNotBad.BiliClient.service.download

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 单连接下载的流写入与完整性判断，无 Android 依赖。
 *
 * 长度以本次 GET 响应为准，只有 GET 未提供长度时才使用 HEAD 的预估值。
 * 未知长度必须真正读到 EOF 才能成功；不能把未知长度伪造为 1 字节。
 * 这里只处理流，不负责开关资源、删除文件或替换成品。
 */
internal object DownloadStreamTransfer {
    data class Result(val code: Int, val downloadedBytes: Long)

    fun expectedBytes(headBytes: Long, responseBytes: Long): Long = when {
        responseBytes >= 0 -> responseBytes
        headBytes > 0 -> headBytes
        else -> -1L
    }

    fun completionCode(downloadedBytes: Long, expectedBytes: Long, reachedEof: Boolean,
                       running: Boolean, paused: Boolean): Int = when {
        paused -> DownloadPathSpec.ERR_PAUSED
        !running -> DownloadPathSpec.ERR_UNKNOWN
        !reachedEof || downloadedBytes <= 0 -> DownloadPathSpec.ERR_NETWORK
        expectedBytes >= 0 && downloadedBytes != expectedBytes -> DownloadPathSpec.ERR_NETWORK
        else -> DownloadPathSpec.NORMAL
    }

    /** 建连、关闭流等异常也要保留暂停/取消语义，不能把暂停文件当失败残片删除。 */
    fun failureCode(running: Boolean, paused: Boolean, errorCode: Int): Int = when {
        paused -> DownloadPathSpec.ERR_PAUSED
        !running -> DownloadPathSpec.ERR_UNKNOWN
        else -> errorCode
    }

    /**
     * 把 [input] 的内容写进 [output]，直到写完/暂停/取消/出错。
     *
     * [startBytes] 是"这个文件在本次续传之前已经落盘的字节数"：调方在续传时传文件已有长度，
     * 于是 [Result.downloadedBytes]、[onBytesWritten] 报的都是**整份文件的累计量**（含断点之前），
     * 进度条才是接着走的，而不是从 0 再涨一遍。它同时参与完整性判断：
     * `downloaded != expectedBytes` 里的 expectedBytes 是服务器给的文件总长。
     *
     * 不续传时保持默认 0，行为与原来完全一致。
     */
    fun copy(input: InputStream, output: OutputStream, expectedBytes: Long,
             isRunning: () -> Boolean, isPaused: () -> Boolean,
             startBytes: Long = 0L,
             onBytesWritten: (downloadedBytes: Long, deltaBytes: Int) -> Unit): Result {
        val buffer = ByteArray(65536)
        var downloaded = startBytes.coerceAtLeast(0L)
        var reachedEof = false
        while (isRunning() && !isPaused()) {
            val read = try {
                input.read(buffer)
            } catch (_: IOException) {
                return Result(failureCode(isRunning(), isPaused(), DownloadPathSpec.ERR_NETWORK), downloaded)
            }
            if (read == -1) {
                reachedEof = true
                break
            }
            if (read == 0) continue
            // 阻塞读取返回时再检查取消标记，不在用户暂停后继续写下一块。
            if (!isRunning() || isPaused()) break
            if (expectedBytes >= 0 && read.toLong() > expectedBytes - downloaded) {
                return Result(DownloadPathSpec.ERR_NETWORK, downloaded)
            }
            try {
                output.write(buffer, 0, read)
            } catch (_: IOException) {
                return Result(failureCode(isRunning(), isPaused(), DownloadPathSpec.ERR_FILE), downloaded)
            }
            downloaded += read
            onBytesWritten(downloaded, read)
        }
        try {
            output.flush()
        } catch (_: IOException) {
            return Result(failureCode(isRunning(), isPaused(), DownloadPathSpec.ERR_FILE), downloaded)
        }
        return Result(completionCode(downloaded, expectedBytes, reachedEof,
            isRunning(), isPaused()), downloaded)
    }
}
