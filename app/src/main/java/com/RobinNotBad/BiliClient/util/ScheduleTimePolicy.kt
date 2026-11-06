package com.RobinNotBad.BiliClient.util

import java.util.Calendar

/**
 * 动态「定时发布」的时间范围策略（纯逻辑，便于 JVM 单测）。
 *
 * 26.11.06 批次 3（task-16）：B 站的 `option.timer_pub_time` 要求秒级时间戳，
 * 并且只接受「当前时间 +6 分钟 ~ +7 天」这个窗口；窗口外提交会被服务端拒绝，
 * 但 App 原来没有任何前置校验——用户选了不合法时间也会照发一次请求。
 *
 * 这里把「窗口判断」与「日期时间文本解析」都做成纯函数：
 * 时间来源由调用方传入（不在策略里读系统时钟），单测可以钉死边界。
 *
 * 边界取闭区间：+6 分钟整合法、+7 天整合法；+5m59s 与 +7d1s 不合法。
 */
object ScheduleTimePolicy {

    /** 定时发布最早的时间：当前时间 +6 分钟。 */
    const val MIN_LEAD_SECONDS: Long = 6L * 60L

    /** 定时发布时间最晚：当前时间 +7 天。 */
    const val MAX_LEAD_SECONDS: Long = 7L * 24L * 60L * 60L

    /** 校验/解析的失败原因。 */
    enum class Error {
        /** 合法。 */
        NONE,

        /** 没有设置定时（0 或负数），不需要校验。 */
        NOT_SCHEDULED,

        /** 时间已经过去。 */
        PAST,

        /** 太早：不足 6 分钟。 */
        TOO_SOON,

        /** 太晚：超过 7 天。 */
        TOO_LATE,

        /** 文本不是可识别的日期时间。 */
        INVALID_FORMAT
    }

    /**
     * 一次校验的结果。
     *
     * @param valid         是否合法
     * @param error         不合法时的原因；合法为 [Error.NONE]
     * @param targetSeconds 目标时间（秒级）；[Error.INVALID_FORMAT] 时为 0
     */
    data class Validation(val valid: Boolean, val error: Error, val targetSeconds: Long)

    /** 用户输入的日期时间：`yyyy-MM-dd HH:mm` 或 `yyyy-MM-dd HH:mm:ss`（日期分隔符也接受 `/`）。 */
    private val DATE_TIME_PATTERN =
        Regex("""^(\d{4})[-/](\d{1,2})[-/](\d{1,2})[ T](\d{1,2}):(\d{1,2})(?::(\d{1,2}))?$""")

    /** 是否根本没设定时（0 / 负数都当作不定时）。 */
    @JvmStatic
    fun isUnset(targetSeconds: Long): Boolean = targetSeconds <= 0L

    /**
     * 校验目标时间是否落在「现在 +6 分钟 ~ +7 天」窗口内。
     *
     * @param targetSeconds 目标时间（秒级）
     * @param nowSeconds    当前时间（秒级），由调用方传入
     */
    @JvmStatic
    fun validate(targetSeconds: Long, nowSeconds: Long): Validation {
        if (isUnset(targetSeconds)) return Validation(false, Error.NOT_SCHEDULED, targetSeconds)
        val delta = targetSeconds - nowSeconds
        return when {
            delta < 0L -> Validation(false, Error.PAST, targetSeconds)
            delta < MIN_LEAD_SECONDS -> Validation(false, Error.TOO_SOON, targetSeconds)
            delta > MAX_LEAD_SECONDS -> Validation(false, Error.TOO_LATE, targetSeconds)
            else -> Validation(true, Error.NONE, targetSeconds)
        }
    }

    /**
     * 解析用户输入的日期时间文本并校验。
     *
     * 解析用 `Calendar` 且 `isLenient = false`：`2026-13-40 12:00` 这类越界日期会抛
     * `IllegalArgumentException`，统一归为 [Error.INVALID_FORMAT]，而不是被静默"归位"到别的日期。
     */
    @JvmStatic
    fun parse(text: String?, nowSeconds: Long): Validation {
        val raw = text?.trim().orEmpty()
        val match = DATE_TIME_PATTERN.matchEntire(raw)
            ?: return Validation(false, Error.INVALID_FORMAT, 0L)
        val groups = match.destructured
        val year = groups.component1().toIntOrNull() ?: return invalid()
        val month = groups.component2().toIntOrNull() ?: return invalid()
        val day = groups.component3().toIntOrNull() ?: return invalid()
        val hour = groups.component4().toIntOrNull() ?: return invalid()
        val minute = groups.component5().toIntOrNull() ?: return invalid()
        val second = groups.component6().ifEmpty { "0" }.toIntOrNull() ?: return invalid()
        val seconds = toEpochSeconds(year, month, day, hour, minute, second) ?: return invalid()
        return validate(seconds, nowSeconds)
    }

    /** 把秒级时间戳格式化成 `yyyy-MM-dd HH:mm`（给输入框预填与提示用）。 */
    @JvmStatic
    fun format(targetSeconds: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = targetSeconds * 1000L
        return buildString(17) {
            append(pad(calendar.get(Calendar.YEAR), 4))
            append('-')
            append(pad(calendar.get(Calendar.MONTH) + 1, 2))
            append('-')
            append(pad(calendar.get(Calendar.DAY_OF_MONTH), 2))
            append(' ')
            append(pad(calendar.get(Calendar.HOUR_OF_DAY), 2))
            append(':')
            append(pad(calendar.get(Calendar.MINUTE), 2))
        }
    }

    /** 输入框的预填默认值：现在 + 10 分钟（在合法窗口内，且比 6 分钟留一点余量）。 */
    @JvmStatic
    fun defaultInputSeconds(nowSeconds: Long): Long = nowSeconds + 10L * 60L

    private fun invalid(): Validation = Validation(false, Error.INVALID_FORMAT, 0L)

    private fun toEpochSeconds(
        year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int
    ): Long? = try {
        val calendar = Calendar.getInstance()
        calendar.isLenient = false
        calendar.clear()
        calendar.set(year, month - 1, day, hour, minute, second)
        calendar.timeInMillis / 1000L
    } catch (e: IllegalArgumentException) {
        null
    }

    /** 取 2 位数字时做补零；年份按 4 位截断（超出部分不补）。 */
    private fun pad(value: Int, width: Int): String = value.toString().let {
        if (it.length >= width) it.substring(0, width) else "0".repeat(width - it.length) + it
    }
}
