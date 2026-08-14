package com.RobinNotBad.BiliClient.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 时间格式化工具类
 * 提供智能日期显示功能：今天、昨天、前天、完整日期
 */
object TimeUtil {

    private val dateFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.CHINESE) }
    private val fullDisplayFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINESE) }

    private var cachedTodayStart = 0L
    private var cachedTodayStartMillis = 0L

    /**
     * 将秒级时间戳转换为智能日期字符串
     * - 今天 → "今天"
     * - 昨天 → "昨天"
     * - 前天 → "前天"
     * - 更早 → "yyyy年MM月dd日 EEEE"
     *
     * @param timestampSec 秒级时间戳
     * @return 智能日期字符串
     */
    fun toSmartDate(timestampSec: Long): String {
        if (timestampSec <= 0) return ""

        val targetMillis = timestampSec * 1000
        val targetDate = Date(targetMillis)

        val nowMillis = System.currentTimeMillis()
        if (nowMillis - cachedTodayStartMillis > 24 * 60 * 60 * 1000 || nowMillis < cachedTodayStartMillis) {
            cachedTodayStartMillis = nowMillis
            cachedTodayStart = todayStartMillis(nowMillis)
        }

        val targetDayStart = todayStartMillis(targetMillis)
        val diffDays = (cachedTodayStart - targetDayStart) / (24 * 60 * 60 * 1000)

        return when (diffDays) {
            0L -> "今天"
            1L -> "昨天"
            2L -> "前天"
            else -> fullDisplayFormat.get().format(targetDate)
        }
    }

    private fun todayStartMillis(millis: Long): Long {
        val cal = Calendar.getInstance(Locale.CHINESE)
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * 将秒级时间戳转换为用于分组的日期键（yyyy-MM-dd）
     */
    fun toDateKey(timestampSec: Long): String {
        if (timestampSec <= 0) return ""
        return dateFormat.get().format(Date(timestampSec * 1000))
    }
}