package com.RobinNotBad.BiliClient.util

import java.io.RandomAccessFile
import java.io.File

/**
 * MP4 faststart：把 moov box 从文件末尾移到文件开头（ftyp 之后），并修正 stco/co64
 * 中的 chunk 绝对偏移。
 *
 * 背景：Android MediaMuxer 生成的 MP4 的 moov 在文件末尾（无 faststart），播放器需要
 * 先读文件末尾才能拿到轨道/索引信息，弱设备（手表端第三方播放器等）解析慢导致播放卡顿。
 * moov 前移后播放器可秒开，兼容性更好。
 */
object Mp4FastStart {

    private const val TAG = "Mp4FastStart"
    private const val BUFFER_SIZE = 512 * 1024

    private class BoxInfo(val type: String, val offset: Long, val size: Long)

    /**
     * 将 moov 前移并重写文件。
     * @return 是否成功；失败时调用方应保留原文件
     */
    fun process(input: File, output: File): Boolean {
        return try {
            if (!input.exists() || input.length() < 64) return false
            RandomAccessFile(input, "r").use { raf ->
                val boxes = scanTopBoxes(raf)
                val ftyp = boxes.firstOrNull { it.type == "ftyp" }
                val moov = boxes.firstOrNull { it.type == "moov" }
                val mdat = boxes.firstOrNull { it.type == "mdat" }
                if (moov == null || mdat == null) return false
                // moov 已经位于文件开头（ftyp 之后）时无需处理
                val moovNewOffset = if (ftyp != null) ftyp.offset + ftyp.size else 0L
                if (moov.offset == moovNewOffset) return true

                // mdat 移动量：重排后 mdat 位于 moov 之后
                val mdatNewOffset = moovNewOffset + moov.size
                val delta = mdatNewOffset - mdat.offset
                if (moov.size > Int.MAX_VALUE) return false

                // 读取 moov 并修正内部 stco/co64
                val moovData = ByteArray(moov.size.toInt())
                raf.seek(moov.offset)
                raf.readFully(moovData)
                fixStcoOffsets(moovData, delta)

                // 重写：ftyp → moov(修正) → 其余 box（mdat 等）
                RandomAccessFile(output, "rw").use { out ->
                    out.setLength(0)
                    if (ftyp != null) copyBox(raf, out, ftyp.offset, ftyp.size)
                    out.write(moovData)
                    for (b in boxes) {
                        if (b === ftyp || b === moov) continue
                        copyBox(raf, out, b.offset, b.size)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Logu.e(TAG, "faststart 失败: ${e.message}")
            false
        }
    }

    /** 扫描文件顶层 box 列表 */
    private fun scanTopBoxes(raf: RandomAccessFile): List<BoxInfo> {
        val result = ArrayList<BoxInfo>()
        val fileLen = raf.length()
        var pos = 0L
        while (pos + 8 <= fileLen) {
            raf.seek(pos)
            val size32 = raf.readInt().toLong() and 0xFFFFFFFFL
            val type = String(byteArrayOf(raf.readByte(), raf.readByte(), raf.readByte(), raf.readByte()))
            var size = size32
            if (size32 == 1L) {
                // 64 位 largesize
                size = raf.readLong()
            } else if (size32 == 0L) {
                // 直到文件末尾
                size = fileLen - pos
            }
            if (size < 8 || pos + size > fileLen) break
            result.add(BoxInfo(type, pos, size))
            pos += size
        }
        return result
    }

    /** 递归修正 moov 内 stco/co64 的 chunk 偏移 */
    private fun fixStcoOffsets(moov: ByteArray, delta: Long) {
        fixBoxes(moov, 0, moov.size, delta)
    }

    /** 仅递归已知的容器 box，避免误解析 stsd 等 payload 中的二进制数据 */
    private val CONTAINER_TYPES = setOf("moov", "trak", "mdia", "minf", "stbl")

    private fun fixBoxes(data: ByteArray, offset: Int, end: Int, delta: Long) {
        var pos = offset
        while (pos + 8 <= end) {
            var size = readUInt32(data, pos)
            val type = String(data, pos + 4, 4)
            var headerSize = 8
            if (size == 1L) {
                size = readUInt64(data, pos + 8)
                headerSize = 16
            } else if (size == 0L) {
                size = (end - pos).toLong()
            }
            if (size < headerSize || pos + size > end) break

            when (type) {
                "stco" -> fixChunkOffsets(data, pos + headerSize, (size - headerSize).toInt(), delta, false)
                "co64" -> fixChunkOffsets(data, pos + headerSize, (size - headerSize).toInt(), delta, true)
                else -> {
                    if (type in CONTAINER_TYPES && size > headerSize) {
                        fixBoxes(data, pos + headerSize, pos + size.toInt(), delta)
                    }
                }
            }
            pos += size.toInt()
        }
    }

    /**
     * 修正 stco/co64 的 chunk 偏移。
     * 结构：version/flags(4) + entry_count(4) + entry_count 个偏移
     */
    private fun fixChunkOffsets(data: ByteArray, payloadOffset: Int, payloadSize: Int, delta: Long, is64: Boolean) {
        if (payloadSize < 8) return
        val entryCount = readUInt32(data, payloadOffset + 4)
        val entrySize = if (is64) 8 else 4
        var p = payloadOffset + 8
        var i = 0
        while (i < entryCount && p + entrySize <= payloadOffset + payloadSize) {
            if (is64) {
                writeUInt64(data, p, readUInt64(data, p) + delta)
            } else {
                writeUInt32(data, p, readUInt32(data, p) + delta)
            }
            p += entrySize
            i++
        }
    }

    private fun copyBox(src: RandomAccessFile, dst: RandomAccessFile, offset: Long, size: Long) {
        if (size <= 0) return
        src.seek(offset)
        val buf = ByteArray(BUFFER_SIZE)
        var remaining = size
        while (remaining > 0) {
            val read = src.read(buf, 0, Math.min(buf.size.toLong(), remaining).toInt())
            if (read < 0) break
            dst.write(buf, 0, read)
            remaining -= read
        }
    }

    // ---- 大端序读写工具 ----

    private fun readUInt32(data: ByteArray, offset: Int): Long {
        return ((data[offset].toLong() and 0xFF) shl 24) or
                ((data[offset + 1].toLong() and 0xFF) shl 16) or
                ((data[offset + 2].toLong() and 0xFF) shl 8) or
                (data[offset + 3].toLong() and 0xFF)
    }

    private fun readUInt64(data: ByteArray, offset: Int): Long {
        var v = 0L
        for (i in 0 until 8) {
            v = (v shl 8) or (data[offset + i].toLong() and 0xFF)
        }
        return v
    }

    private fun writeUInt32(data: ByteArray, offset: Int, value: Long) {
        data[offset] = ((value shr 24) and 0xFF).toByte()
        data[offset + 1] = ((value shr 16) and 0xFF).toByte()
        data[offset + 2] = ((value shr 8) and 0xFF).toByte()
        data[offset + 3] = (value and 0xFF).toByte()
    }

    private fun writeUInt64(data: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 8) {
            data[offset + i] = ((value shr (8 * (7 - i))) and 0xFF).toByte()
        }
    }
}
