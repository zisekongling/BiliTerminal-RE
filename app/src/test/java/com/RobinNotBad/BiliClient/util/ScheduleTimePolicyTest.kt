package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 动态「定时发布」窗口与文本解析策略的纯逻辑单测。
 *
 * 26.11.06 批次 3（task-16）：B 站只接受「当前时间 +6 分钟 ~ +7 天」，
 * 这里把闭区间的四个边界（+5m59s / +6m / +7d / +7d1s）与四种错误原因都钉死。
 */
class ScheduleTimePolicyTest {

    /** 固定"当前时间"，避免测试读系统时钟：任意一天的整分钟即可。 */
    private val now = 1_700_000_040L

    private val day = 24L * 60L * 60L

    // ---- 窗口边界 ----

    @Test
    fun validate_rejectsExactlyOneSecondUnderSixMinutes() {
        val result = ScheduleTimePolicy.validate(now + 6 * 60 - 1, now)
        assertFalse("+5m59s 不合法", result.valid)
        assertEquals("原因是太早", ScheduleTimePolicy.Error.TOO_SOON, result.error)
        assertEquals("目标时间原样带回", now + 6 * 60 - 1, result.targetSeconds)
    }

    @Test
    fun validate_acceptsExactlySixMinutes() {
        val result = ScheduleTimePolicy.validate(now + 6 * 60, now)
        assertTrue("+6m 整合法", result.valid)
        assertEquals("没有错误", ScheduleTimePolicy.Error.NONE, result.error)
    }

    @Test
    fun validate_acceptsExactlySevenDays() {
        val result = ScheduleTimePolicy.validate(now + 7 * day, now)
        assertTrue("+7d 整合法", result.valid)
        assertEquals("没有错误", ScheduleTimePolicy.Error.NONE, result.error)
    }

    @Test
    fun validate_rejectsOneSecondOverSevenDays() {
        val result = ScheduleTimePolicy.validate(now + 7 * day + 1, now)
        assertFalse("+7d1s 不合法", result.valid)
        assertEquals("原因是太晚", ScheduleTimePolicy.Error.TOO_LATE, result.error)
    }

    @Test
    fun validate_rejectsPastTime() {
        val result = ScheduleTimePolicy.validate(now - 1, now)
        assertFalse("已经过去的时间不合法", result.valid)
        assertEquals("原因是已过期", ScheduleTimePolicy.Error.PAST, result.error)
    }

    @Test
    fun validate_treatsZeroAndNegativeAsNotScheduled() {
        assertEquals(
            "0 等于没设定时",
            ScheduleTimePolicy.Error.NOT_SCHEDULED,
            ScheduleTimePolicy.validate(0L, now).error
        )
        assertEquals(
            "负数同样当没设置",
            ScheduleTimePolicy.Error.NOT_SCHEDULED,
            ScheduleTimePolicy.validate(-10L, now).error
        )
    }

    @Test
    fun isUnset_onlyTrueForZeroOrNegative() {
        assertTrue("0 是没设置", ScheduleTimePolicy.isUnset(0L))
        assertTrue("负数是没设置", ScheduleTimePolicy.isUnset(-1L))
        assertFalse("正数是设置了", ScheduleTimePolicy.isUnset(1L))
    }

    @Test
    fun defaultInputSeconds_landsInsideWindow() {
        val target = ScheduleTimePolicy.defaultInputSeconds(now)
        assertEquals("默认预填 10 分钟后", now + 10 * 60, target)
        assertTrue("预填值本身必须合法", ScheduleTimePolicy.validate(target, now).valid)
    }

    // ---- 文本解析 ----

    /** 用 Calendar 构造一个"本地时间"的秒级时间戳（策略的 format/parse 都走默认时区）。 */
    private fun localSeconds(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set(year, month - 1, dayOfMonth, hour, minute, 0)
        return calendar.timeInMillis / 1000
    }

    @Test
    fun parse_acceptsStandardDateTime() {
        val target = localSeconds(2026, 11, 7, 12, 34)
        val text = ScheduleTimePolicy.format(target)
        assertEquals("格式化结果", "2026-11-07 12:34", text)
        val result = ScheduleTimePolicy.parse(text, target - 3 * day)
        assertTrue("标准格式可解析且合法", result.valid)
        assertEquals("解析回同一个时间戳", target, result.targetSeconds)
    }

    @Test
    fun parse_acceptsSecondsAndSlashSeparators() {
        val target = localSeconds(2026, 11, 7, 12, 34)
        val withSeconds = ScheduleTimePolicy.parse("2026-11-07 12:34:00", target - day)
        assertTrue("带秒也认", withSeconds.valid)
        assertEquals("数值一致", target, withSeconds.targetSeconds)

        val slashed = ScheduleTimePolicy.parse("2026/11/07 12:34", target - day)
        assertTrue("斜杠日期也认", slashed.valid)
        assertEquals("数值一致", target, slashed.targetSeconds)
    }

    @Test
    fun parse_rejectsGarbage() {
        val samples = listOf("", "   ", "abc", "2026-11-07", "12:34", "2026-11-07 12", "2026年11月07日 12:34")
        for (sample in samples) {
            val result = ScheduleTimePolicy.parse(sample, now)
            assertFalse("『$sample』不是合法日期时间", result.valid)
            assertEquals("『$sample』归为格式错", ScheduleTimePolicy.Error.INVALID_FORMAT, result.error)
        }
    }

    @Test
    fun parse_rejectsOutOfRangeDates() {
        // Calendar 关掉 lenient 后，13 月 / 2 月 30 日都会抛 IllegalArgumentException，统一归为格式错
        val samples = listOf("2026-13-01 12:00", "2026-02-30 12:00", "2026-11-32 12:00")
        for (sample in samples) {
            val result = ScheduleTimePolicy.parse(sample, now)
            assertFalse("『$sample』越界", result.valid)
            assertEquals("『$sample』归为格式错", ScheduleTimePolicy.Error.INVALID_FORMAT, result.error)
        }
    }

    @Test
    fun parse_reportsPastAndWindowErrors() {
        val past = ScheduleTimePolicy.format(now - 3600L)
        assertEquals(
            "已经过去的时间",
            ScheduleTimePolicy.Error.PAST,
            ScheduleTimePolicy.parse(past, now).error
        )

        // +5 分钟（分钟粒度），低于 6 分钟的门槛
        val tooSoon = ScheduleTimePolicy.format(now + 5 * 60)
        assertEquals(
            "太早",
            ScheduleTimePolicy.Error.TOO_SOON,
            ScheduleTimePolicy.parse(tooSoon, now).error
        )

        val tooLate = ScheduleTimePolicy.format(now + 8 * day)
        assertEquals(
            "太晚",
            ScheduleTimePolicy.Error.TOO_LATE,
            ScheduleTimePolicy.parse(tooLate, now).error
        )
    }

    @Test
    fun format_matchesExpectedShape() {
        val text = ScheduleTimePolicy.format(now)
        assertTrue("形如 yyyy-MM-dd HH:mm", Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$""").matches(text))
    }
}
