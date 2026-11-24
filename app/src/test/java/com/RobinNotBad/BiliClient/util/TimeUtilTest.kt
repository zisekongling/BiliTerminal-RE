package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * [TimeUtil.formatChatTime] 的纯 JVM 单测（反馈 #267「私信显示时间」）。
 *
 * 三个分支（当天 / 同年 / 跨年）各自只比**选对了哪一档**：期望值由 [TimeUtil] 自己的对应格式
 * 生成，这样断言与时区无关，跑在 CI 的 UTC 机器上也一样绿。
 */
class TimeUtilTest {

    /** 按本地时区造一个毫秒时间戳。 */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun 当天只给时分() {
        val now = at(2026, Calendar.NOVEMBER, 23, 15, 30)
        val then = at(2026, Calendar.NOVEMBER, 23, 9, 5)
        assertEquals(TimeUtil.formatTime(then), TimeUtil.formatChatTime(then, now))
    }

    @Test
    fun 同年不同天给月日加分时() {
        val now = at(2026, Calendar.NOVEMBER, 23, 15, 30)
        val then = at(2026, Calendar.JANUARY, 8, 9, 5)
        assertEquals(
            TimeUtil.format(then, TimeUtil.PATTERN_MONTH_DAY_TIME),
            TimeUtil.formatChatTime(then, now)
        )
    }

    @Test
    fun 跨年带上年份() {
        val now = at(2026, Calendar.NOVEMBER, 23, 15, 30)
        val then = at(2025, Calendar.DECEMBER, 31, 23, 59)
        val text = TimeUtil.formatChatTime(then, now)
        assertEquals(TimeUtil.formatDateTime(then), text)
        assertTrue("跨年要能看出年份：$text", text.contains("2025"))
    }

    @Test
    fun 时间戳缺失时返回空串() {
        // 0 与负数都是「这条消息没有时间」，调用方据此隐藏整个时间标签（别显示 1970-01-01）。
        val now = at(2026, Calendar.NOVEMBER, 23, 15, 30)
        assertEquals("", TimeUtil.formatChatTime(0L, now))
        assertEquals("", TimeUtil.formatChatTime(-1L, now))
    }

    @Test
    fun 秒与毫秒不会被混淆() {
        // 私信接口给的是秒；传进本方法前必须 ×1000。传秒会落到 1970 年，这里守住「看出来不对」。
        val now = at(2026, Calendar.NOVEMBER, 23, 15, 30)
        val seconds = at(2026, Calendar.NOVEMBER, 23, 9, 5) / 1000
        val text = TimeUtil.formatChatTime(seconds, now)
        assertFalse("传秒应落到 1970，说明调用方漏了 ×1000：$text", text.startsWith("2026"))
    }
}
