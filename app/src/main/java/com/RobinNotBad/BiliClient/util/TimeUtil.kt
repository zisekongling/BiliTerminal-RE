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

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINESE)
    private val fullDisplayFormat = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINESE)
    private val weekdayFormat = SimpleDateFormat("EEEE", Locale.CHINESE)

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

        val targetDate = Date(timestampSec * 1000)
        val now = Calendar.getInstance(Locale.CHINESE)
        val target = Calendar.getInstance(Locale.CHINESE).apply { time = targetDate }

        // 重置时间部分，只比较日期
        val today = Calendar.getInstance(Locale.CHINESE).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val targetDayStart = Calendar.getInstance(Locale.CHINESE).apply {
            time = targetDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffMillis = today.timeInMillis - targetDayStart.timeInMillis
        val diffDays = diffMillis / (24 * 60 * 60 * 1000)

        return when (diffDays) {
            0L -> "今天"
            1L -> "昨天"
            2L -> "前天"
            else -> fullDisplayFormat.format(targetDate)
        }
    }

    /**
     * 将秒级时间戳转换为用于分组的日期键（yyyy-MM-dd）
     */
    fun toDateKey(timestampSec: Long): String {
        if (timestampSec <= 0) return ""
        return dateFormat.format(Date(timestampSec * 1000))
    }
}