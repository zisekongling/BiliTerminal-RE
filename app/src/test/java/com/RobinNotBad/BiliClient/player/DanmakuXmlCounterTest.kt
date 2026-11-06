package com.RobinNotBad.BiliClient.player

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 弹幕 XML 流式计数的单测。
 *
 * 重点在「提前收工」与「跨缓冲区边界」：计数只是为了判超限，读满整个几十 MB 的 XML
 * 等于把保护要省下的内存又花回去；而分块读取会漏掉跨块出现的 `<d p=`。
 */
class DanmakuXmlCounterTest {

    private lateinit var file: File

    @Before
    fun setUp() {
        file = File.createTempFile("danmaku-count", ".xml")
    }

    @After
    fun tearDown() {
        file.delete()
    }

    private fun writeEntries(count: Int) {
        val builder = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><i>")
        for (i in 0 until count) builder.append("<d p=\"").append(i).append(",1,25\">测试</d>")
        builder.append("</i>")
        file.writeText(builder.toString())
    }

    @Test
    fun `空元素也算一条弹幕`() {
        file.writeText("<i><d p=\"1\"/></i>")
        assertEquals(1L, DanmakuXmlCounter.count(file))
    }

    @Test
    fun `数出全部弹幕条数`() {
        writeEntries(10)
        assertEquals(10L, DanmakuXmlCounter.count(file))
    }

    @Test
    fun `达到stopAt立刻返回`() {
        writeEntries(10)
        assertEquals(3L, DanmakuXmlCounter.count(file, 3))
    }

    @Test
    fun `空文件返回0`() {
        file.writeText("")
        assertEquals(0L, DanmakuXmlCounter.count(file))
    }

    @Test
    fun `文件不存在返回未知`() {
        assertEquals(-1L, DanmakuXmlCounter.count(null))
        assertEquals(-1L, DanmakuXmlCounter.count(File(file.parentFile, "not-exists-${System.nanoTime()}.xml")))
    }

    @Test
    fun `跨缓冲区边界的标签也能数到`() {
        // 16KB 缓冲区：把三分之一标签挤在块边界上，覆盖「块尾只匹配到半个 needle」的情况
        val prefix = "x".repeat(16 * 1024 - 3)
        file.writeText("$prefix<d p=\"1\">后续</d>")
        assertEquals(1L, DanmakuXmlCounter.count(file))
    }

    @Test
    fun `非弹幕标签不计数`() {
        file.writeText("<i><a p=\"1\"/><d q=\"2\"/></i>")
        assertEquals(0L, DanmakuXmlCounter.count(file))
    }
}
