package com.RobinNotBad.BiliClient.service.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** 实际执行流读写，覆盖短 EOF、网络异常、磁盘异常及暂停；不依赖 Android 桩。 */
class DownloadStreamTransferTest {
    @Test
    fun 本次响应长度优先于过期的HEAD长度() {
        assertEquals(10L, DownloadStreamTransfer.expectedBytes(20L, 10L))
        assertEquals(0L, DownloadStreamTransfer.expectedBytes(20L, 0L))
    }

    @Test
    fun 响应未知长度时使用HEAD提示() {
        assertEquals(20L, DownloadStreamTransfer.expectedBytes(20L, -1L))
    }

    @Test
    fun 两处长度都未知时保持负一而非伪造一字节() {
        assertEquals(-1L, DownloadStreamTransfer.expectedBytes(0L, -1L))
        assertEquals(-1L, DownloadStreamTransfer.expectedBytes(-1L, -1L))
    }

    @Test
    fun 已知长度完整读到EOF才成功() {
        val data = ByteArray(150000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        var reportedBytes = 0L
        var reportedDelta = 0L
        val result = DownloadStreamTransfer.copy(ByteArrayInputStream(data), output, data.size.toLong(),
            { true }, { false }) { total, delta ->
            reportedBytes = total
            reportedDelta += delta
        }
        assertEquals(DownloadPathSpec.NORMAL, result.code)
        assertEquals(data.size.toLong(), result.downloadedBytes)
        assertEquals(result.downloadedBytes, reportedBytes)
        assertEquals(result.downloadedBytes, reportedDelta)
        assertArrayEquals(data, output.toByteArray())
    }

    @Test
    fun 已知长度提前EOF判网络错误而非成功() {
        val result = copy(ByteArrayInputStream(ByteArray(128)), 1024L)
        assertEquals(DownloadPathSpec.ERR_NETWORK, result.code)
        assertEquals(128L, result.downloadedBytes)
    }

    @Test
    fun 少一字节仍必须失败() {
        val result = copy(ByteArrayInputStream(ByteArray(65535)), 65536L)
        assertEquals(DownloadPathSpec.ERR_NETWORK, result.code)
    }

    @Test
    fun 多读字节也不能写成合法文件() {
        val output = ByteArrayOutputStream()
        val result = DownloadStreamTransfer.copy(ByteArrayInputStream(ByteArray(10)), output, 9L,
            { true }, { false }) { _, _ -> }
        assertEquals(DownloadPathSpec.ERR_NETWORK, result.code)
        assertEquals(0, output.size())
    }

    @Test
    fun 未知长度读到EOF且非空才成功() {
        val result = copy(ByteArrayInputStream(ByteArray(7)), -1L)
        assertEquals(DownloadPathSpec.NORMAL, result.code)
        assertEquals(7L, result.downloadedBytes)
    }

    @Test
    fun 空响应不能当作视频音频或附件成品() {
        for (length in listOf(-1L, 0L, 1L)) {
            assertEquals(DownloadPathSpec.ERR_NETWORK,
                copy(ByteArrayInputStream(byteArrayOf()), length).code)
        }
    }

    @Test
    fun 一字节文件不是未知长度哨兵() {
        assertEquals(DownloadPathSpec.NORMAL, copy(ByteArrayInputStream(byteArrayOf(1)), 1L).code)
        assertEquals(DownloadPathSpec.ERR_NETWORK, copy(ByteArrayInputStream(byteArrayOf()), 1L).code)
    }

    @Test
    fun 长度相等但未读到EOF不能提前报成功() {
        assertEquals(DownloadPathSpec.ERR_NETWORK,
            DownloadStreamTransfer.completionCode(10L, 10L, false, true, false))
    }

    @Test
    fun 暂停优先于不完整判断() {
        assertEquals(DownloadPathSpec.ERR_PAUSED,
            DownloadStreamTransfer.completionCode(3L, 10L, false, true, true))
        assertEquals(DownloadPathSpec.ERR_PAUSED,
            DownloadStreamTransfer.completionCode(10L, 10L, true, false, true))
    }

    @Test
    fun 取消不能当作成功() {
        assertEquals(DownloadPathSpec.ERR_UNKNOWN,
            DownloadStreamTransfer.completionCode(10L, 10L, true, false, false))
    }

    @Test
    fun 网络读异常归为网络错误() {
        val input = object : InputStream() {
            override fun read(): Int = throw IOException("模拟网络断开")
        }
        assertEquals(DownloadPathSpec.ERR_NETWORK, copy(input, 10L).code)
    }

    @Test
    fun 输出写异常归为文件错误且未计入成功字节() {
        val output = object : OutputStream() {
            override fun write(b: Int) { throw IOException("模拟磁盘满") }
        }
        val result = DownloadStreamTransfer.copy(ByteArrayInputStream(byteArrayOf(1)), output, 1L,
            { true }, { false }) { _, _ -> throw AssertionError("写失败不能增加进度") }
        assertEquals(DownloadPathSpec.ERR_FILE, result.code)
        assertEquals(0L, result.downloadedBytes)
    }

    @Test
    fun flush失败不能报成功() {
        val output = object : ByteArrayOutputStream() {
            override fun flush() { throw IOException("模拟落盘失败") }
        }
        val result = DownloadStreamTransfer.copy(ByteArrayInputStream(byteArrayOf(1)), output, 1L,
            { true }, { false }) { _, _ -> }
        assertEquals(DownloadPathSpec.ERR_FILE, result.code)
    }

    @Test
    fun 阻塞读返回后发现暂停不能继续写() {
        var paused = false
        val input = object : ByteArrayInputStream(byteArrayOf(1)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                paused = true
                return super.read(b, off, len)
            }
        }
        val output = ByteArrayOutputStream()
        val result = DownloadStreamTransfer.copy(input, output, 1L,
            { true }, { paused }) { _, _ -> throw AssertionError("暂停后不得增加进度") }
        assertEquals(DownloadPathSpec.ERR_PAUSED, result.code)
        assertEquals(0, output.size())
    }

    @Test
    fun 暂停保留已经写入的字节供调用方处理() {
        var paused = false
        val output = ByteArrayOutputStream()
        val result = DownloadStreamTransfer.copy(ByteArrayInputStream(ByteArray(131072)), output, 131072L,
            { true }, { paused }) { _, _ -> paused = true }
        assertEquals(DownloadPathSpec.ERR_PAUSED, result.code)
        assertEquals(65536L, result.downloadedBytes)
        assertEquals(65536, output.size())
    }

    @Test
    fun 超过Int上限的长度也不截断() {
        val total = Int.MAX_VALUE.toLong() + 100L
        assertEquals(DownloadPathSpec.NORMAL,
            DownloadStreamTransfer.completionCode(total, total, true, true, false))
        assertTrue(total > Int.MAX_VALUE)
        assertEquals(DownloadPathSpec.ERR_NETWORK,
            DownloadStreamTransfer.completionCode(total - 1, total, true, true, false))
    }

    @Test
    fun 建连或关闭异常期间暂停优先保留临时文件语义() {
        for (errorCode in listOf(DownloadPathSpec.ERR_NETWORK, DownloadPathSpec.ERR_FILE)) {
            assertEquals(DownloadPathSpec.ERR_PAUSED,
                DownloadStreamTransfer.failureCode(true, true, errorCode))
            assertEquals(DownloadPathSpec.ERR_UNKNOWN,
                DownloadStreamTransfer.failureCode(false, false, errorCode))
            assertEquals(errorCode, DownloadStreamTransfer.failureCode(true, false, errorCode))
        }
    }

    @Test
    fun 阻塞读取抛异常时若已暂停返回暂停而非网络错误() {
        var paused = false
        val input = object : InputStream() {
            override fun read(): Int {
                paused = true
                throw IOException("模拟暂停期间读失败")
            }
        }
        val result = DownloadStreamTransfer.copy(input, ByteArrayOutputStream(), 10L,
            { true }, { paused }) { _, _ -> }
        assertEquals(DownloadPathSpec.ERR_PAUSED, result.code)
    }

    @Test
    fun 尚未启动或已经暂停时不读取网络() {
        val input = object : InputStream() {
            override fun read(): Int = throw AssertionError("暂停或取消时不得发起读取")
        }
        assertEquals(DownloadPathSpec.ERR_UNKNOWN,
            DownloadStreamTransfer.copy(input, ByteArrayOutputStream(), -1L,
                { false }, { false }) { _, _ -> }.code)
        assertEquals(DownloadPathSpec.ERR_PAUSED,
            DownloadStreamTransfer.copy(input, ByteArrayOutputStream(), -1L,
                { true }, { true }) { _, _ -> }.code)
    }

    @Test
    fun 续传时累计字节含断点之前的量() {
        // 前 5 字节已经在磁盘上，这次只从第 6 字节往后写 5 字节
        val output = ByteArrayOutputStream()
        var reported = 0L
        val result = DownloadStreamTransfer.copy(ByteArrayInputStream(ByteArray(5)), output, 10L,
            { true }, { false }, startBytes = 5L, onBytesWritten = { total, _ -> reported = total })
        assertEquals(DownloadPathSpec.NORMAL, result.code)
        assertEquals(10L, result.downloadedBytes)
        assertEquals(10L, reported)
        assertEquals(5, output.size())
    }

    @Test
    fun 续传后仍少字节要判网络错误() {
        val result = DownloadStreamTransfer.copy(ByteArrayInputStream(ByteArray(3)), ByteArrayOutputStream(), 10L,
            { true }, { false }, startBytes = 5L, onBytesWritten = { _, _ -> })
        assertEquals(DownloadPathSpec.ERR_NETWORK, result.code)
        assertEquals(8L, result.downloadedBytes)
    }

    @Test
    fun 续传时服务器多给字节也不能写坏文件() {
        val output = ByteArrayOutputStream()
        val result = DownloadStreamTransfer.copy(ByteArrayInputStream(ByteArray(6)), output, 10L,
            { true }, { false }, startBytes = 5L, onBytesWritten = { _, _ -> })
        assertEquals(DownloadPathSpec.ERR_NETWORK, result.code)
        assertEquals(0, output.size())
    }

    private fun copy(input: InputStream, expected: Long): DownloadStreamTransfer.Result =
        DownloadStreamTransfer.copy(input, ByteArrayOutputStream(), expected,
            { true }, { false }) { _, _ -> }
}
