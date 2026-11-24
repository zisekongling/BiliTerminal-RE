package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 锁住诊断日志的滚动行为：超过上限时只保留末尾一半，且替换是「先写 .tmp 再 rename」。
 *
 * 这是纯 java.io 的逻辑，能在 JVM 单测里跑；崩溃现场最怕的是滚动本身把文件写坏，
 * 所以这里既验内容（末尾字节原样保留），也验不留临时文件。
 */
class LogFileRotationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun 文件不超过保留量时不滚动() {
        val file = tempFolder.newFile("short.log")
        file.writeBytes(ByteArray(100) { it.toByte() })

        assertFalse(LogFileRotation.rotateKeepingTail(file, 1000L))
        assertArrayEquals(ByteArray(100) { it.toByte() }, file.readBytes())
    }

    @Test
    fun 超限时保留末尾一半并丢弃前半() {
        val file = tempFolder.newFile("full.log")
        val original = ByteArray(3000) { (it % 251).toByte() }
        file.writeBytes(original)

        assertTrue(LogFileRotation.rotateKeepingTail(file, 1000L))

        val expected = original.copyOfRange(2500, 3000)
        assertArrayEquals(expected, file.readBytes())
        assertFalse("滚动后不能留下 .tmp", File(file.parentFile, file.name + ".tmp").exists())
    }

    @Test
    fun 超过保留量即滚动到保留量() {
        val file = tempFolder.newFile("edge.log")
        val original = ByteArray(600) { it.toByte() }
        file.writeBytes(original)

        // 上限 1000 → 保留 500；600 > 500，所以会滚动成 500 字节
        assertTrue(LogFileRotation.rotateKeepingTail(file, 1000L))
        assertArrayEquals(original.copyOfRange(100, 600), file.readBytes())
    }

    @Test
    fun 文件不存在或上限非法时返回false() {
        val missing = File(tempFolder.root, "missing.log")
        assertFalse(LogFileRotation.rotateKeepingTail(missing, 1000L))
        assertFalse(LogFileRotation.rotateKeepingTail(missing, 0L))
    }

    @Test
    fun 滚动后文件仍可继续追加() {
        val file = tempFolder.newFile("append.log")
        file.writeBytes(ByteArray(2048) { 1 })
        assertTrue(LogFileRotation.rotateKeepingTail(file, 1024L))

        file.appendBytes(byteArrayOf(9, 8, 7))
        val bytes = file.readBytes()
        assertTrue(bytes.size > 512)
        assertArrayEquals(byteArrayOf(9, 8, 7), bytes.copyOfRange(bytes.size - 3, bytes.size))
    }
}
