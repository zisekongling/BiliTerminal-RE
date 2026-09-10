package com.RobinNotBad.BiliClient.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.HashMap
import java.util.Locale

/**
 * 时间格式化的统一入口。
 *
 * 合并自原先 11 处各自 `new SimpleDateFormat(...)` 的实现（分布在 `api/`、`model/`、`adapter/`、
 * `ui/widget/`、`activity/`）；其中 `NoticeHolder`、`TextClock`、`HistoryVideoCardAdapter` 三处
 * 是**静态实例**，多线程共用同一个 `SimpleDateFormat` 本身就是线程不安全的。
 *
 * 这里按「模式 + Locale」把实例缓存在 **ThreadLocal** 里：
 * 既省掉重复构造的开销（`SimpleDateFormat` 构造要查 Locale 数据，在手表端弱 CPU 上尤其明显，
 * 而 `NoticeHolder` / `TimelineAdapter` / `HistoryVideoCardAdapter` 都是在
 * `onBindViewHolder` 这种会被滚动高频触发的路径上调用的），又保证每个线程各自持有实例。
 *
 * 所有入参统一为 **epoch 毫秒**；调用方原本传秒的，请自行 `* 1000`（保持各处原有换算不变）。
 */
object TimeUtil {

    const val PATTERN_DATE = "yyyy-MM-dd"
    const val PATTERN_TIME = "HH:mm"
    const val PATTERN_DATE_TIME = "yyyy-MM-dd HH:mm"
    const val PATTERN_DATE_TIME_SEC = "yyyy-MM-dd HH:mm:ss"

    /**
     * 12 小时制（`hh`）。
     *
     * 这是**历史遗留**：`AppInfoApi` 的赞助名单时间一直在用它，同文件另一处却用的是 24 小时制。
     * 改成 `HH` 会直接改变用户看到的文案，因此这里原样保留，不顺手"修正"。
     */
    const val PATTERN_DATE_TIME_12H = "yyyy-MM-dd hh:mm"

    // 不用 ThreadLocal.withInitial(...) —— 它依赖 java.util.function，minSdk 24 上不保险
    private val cache = object : ThreadLocal<HashMap<String, SimpleDateFormat>>() {
        override fun initialValue(): HashMap<String, SimpleDateFormat> = HashMap()
    }

    /**
     * 按 [pattern] 格式化 [millis]（epoch 毫秒）。
     *
     * [locale] 默认跟随系统；需要固定语言环境的调用方显式传入（原实现的 Locale 差异已逐处保留）。
     */
    @JvmStatic
    @JvmOverloads
    fun format(millis: Long, pattern: String, locale: Locale = Locale.getDefault()): String {
        val key = pattern + '|' + locale.toString()
        val map = cache.get()!!
        var simpleDateFormat = map[key]
        if (simpleDateFormat == null) {
            simpleDateFormat = SimpleDateFormat(pattern, locale)
            map[key] = simpleDateFormat
        }
        return simpleDateFormat.format(Date(millis))
    }

    /** `yyyy-MM-dd` */
    @JvmStatic
    @JvmOverloads
    fun formatDate(millis: Long, locale: Locale = Locale.getDefault()): String =
        format(millis, PATTERN_DATE, locale)

    /** `HH:mm` */
    @JvmStatic
    @JvmOverloads
    fun formatTime(millis: Long, locale: Locale = Locale.getDefault()): String =
        format(millis, PATTERN_TIME, locale)

    /** `yyyy-MM-dd HH:mm` */
    @JvmStatic
    @JvmOverloads
    fun formatDateTime(millis: Long, locale: Locale = Locale.getDefault()): String =
        format(millis, PATTERN_DATE_TIME, locale)

    /** `yyyy-MM-dd HH:mm:ss` */
    @JvmStatic
    @JvmOverloads
    fun formatDateTimeSec(millis: Long, locale: Locale = Locale.getDefault()): String =
        format(millis, PATTERN_DATE_TIME_SEC, locale)
}
