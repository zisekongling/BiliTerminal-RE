package com.RobinNotBad.BiliClient.player

import java.io.File
import java.io.FileInputStream

/**
 * 弹幕 XML 的流式计数。
 *
 * 老弹幕接口（`comment.bilibili.com/<cid>.xml`）没有「先查总数」的轻量查询：数量就在正文里。
 * 所以对**已落盘的 XML** 做一次流式扫描，只数 `<d p="` 的出现次数，达到 [stopAt] 立即收工。
 *
 * 为什么不用 DOM/正则整读：热门视频的弹幕 XML 有几十 MB，整读进内存再数，
 * 刚好与本保护要解决的问题（低内存设备）相反。这里常数内存、可提前退出。
 *
 * 纯 JVM 实现（只用 `java.io`），单测见 `DanmakuXmlCounterTest`。
 */
object DanmakuXmlCounter {

    /** 每段 6 分钟弹幕 XML 里，一条弹幕就是一个 `<d p="...">` 元素。 */
    private val NEEDLE = "<d p=\"".toByteArray(Charsets.UTF_8)

    /**
     * 数 [file] 里的弹幕条数。
     *
     * @param file  弹幕 XML 文件（可为 null / 不存在）
     * @param stopAt 数到这个数量就提前返回（传阈值 +1 即可判断「是否超限」）
     * @return 条数；文件不存在或读取失败返回 **-1**（调用方按「数量未知」失败开放）
     */
    @JvmStatic
    fun count(file: File?, stopAt: Long = Long.MAX_VALUE): Long {
        if (file == null || !file.isFile) return -1L
        val limit = if (stopAt <= 0L) 1L else stopAt
        var total = 0L
        var matched = 0
        try {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    for (i in 0 until read) {
                        val byte = buffer[i]
                        if (byte == NEEDLE[matched]) {
                            matched++
                            if (matched == NEEDLE.size) {
                                total++
                                matched = 0
                                if (total >= limit) return total
                            }
                        } else {
                            matched = if (byte == NEEDLE[0]) 1 else 0
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return -1L
        }
        return total
    }
}
