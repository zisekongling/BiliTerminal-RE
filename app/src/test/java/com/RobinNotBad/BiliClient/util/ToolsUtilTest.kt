package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ToolsUtil.getRgb888 纯 JVM 单测。
 * 旧实现把三个通道的十进制数字符串拼接再 parseInt（白色得到 255255255 而非 16777215），
 * 导致发送弹幕颜色全错；修复后应为 color & 0xFFFFFF 的十进制值。
 */
class ToolsUtilTest {

    @Test
    fun getRgb888_white() {
        // 白色 0xFFFFFF = 16777215（旧实现错误地返回 255255255）
        assertEquals(16777215, ToolsUtil.getRgb888(0xFFFFFFFF.toInt()))
    }

    @Test
    fun getRgb888_blue() {
        assertEquals(255, ToolsUtil.getRgb888(0xFF0000FF.toInt()))
    }

    @Test
    fun getRgb888_mixedChannels() {
        // 0x102030 = 1056816（旧实现拼成 "162048" = 162048，错误）
        assertEquals(0x102030, ToolsUtil.getRgb888(0xFF102030.toInt()))
    }

    @Test
    fun getRgb888_stripsAlpha() {
        // 透明度通道被剥离，只保留 RGB
        assertEquals(0x000000, ToolsUtil.getRgb888(0x00123456))
        assertEquals(0x804020, ToolsUtil.getRgb888(0xFF804020.toInt()))
    }
}
