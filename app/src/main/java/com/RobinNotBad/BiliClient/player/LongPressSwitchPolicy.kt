package com.RobinNotBad.BiliClient.player

import kotlin.math.abs
import kotlin.math.max

/**
 * 播放页两组「长按」相关手势/提示的纯逻辑（**零 Android 依赖**，JVM 单测见 `LongPressSwitchPolicyTest`）。
 *
 * 一、长按后纵向划切视频：
 * 现有长按手势是「按住画面 = 倍速播放，松手恢复」（见 `PlayerActivity.onLongClick`）。
 * 本功能在同一手势上加一条**脱离路径**：按住期间**向上划 = 下一条、向下划 = 上一条**
 * （与短视频列表的手感一致：内容往上走 = 看下一个）。
 *
 * 判定必须够严，否则会把「按住时手抖」误判成切视频：
 * - 纵向位移要超过屏高的 [HEIGHT_FRACTION]（默认 18%）；
 * - 横向位移不能超过纵向的 1/1.8（斜着划不算，横滑留给调进度）；
 * - 必须在 [MIN_MOVE_MS]..[MAX_MOVE_MS] 内完成（快速一划；按住后慢慢拖不算）。
 *
 * 二、长按倍速提示尺寸：
 * 长按加速时屏幕上显示「x 3.0」的提示（`text_speed`）。手表屏幕小、字号固定，
 * 给 80%/100%/120%/150% 四档缩放（默认 100%），存档值同样用字符串。
 */
object LongPressSwitchPolicy {

    /** 没有触发切视频。 */
    const val NONE = 0

    /** 向上划：下一条。 */
    const val NEXT = 1

    /** 向下划：上一条。 */
    const val PREVIOUS = -1

    /** 纵向阈值占屏高的比例。 */
    const val HEIGHT_FRACTION = 0.18f

    /** 划动至少持续这么久才算「有意划动」。 */
    const val MIN_MOVE_MS = 30L

    /** 超过这个时长就当成按住后慢慢拖，不切视频。 */
    const val MAX_MOVE_MS = 500L

    /** 横向容差：横向位移不能超过纵向的 1/1.8（也至少给 2 倍 touchSlop 的余量）。 */
    const val HORIZONTAL_RATIO = 1.8f

    /** 纵向触发阈值（像素）。屏高未知/为 0 时返回 0，调用方应回退用 `resources.displayMetrics`。 */
    fun thresholdPx(screenHeightPx: Int): Float =
        if (screenHeightPx <= 0) 0f else max(1f, screenHeightPx * HEIGHT_FRACTION)

    /**
     * 判定一次划动是不是「长按后纵向切换视频」。
     *
     * @param dxPx 相对长按起点的横向位移（右为正）
     * @param dyPx 相对长按起点的纵向位移（**下为正**，所以向上划是负数）
     * @param screenHeightPx 播放区高度（像素）
     * @param durationMs 从长按起点开始的划动时长（毫秒）
     * @param touchSlopPx 系统 `ViewConfiguration.scaledTouchSlop`
     * @return [NEXT] 向上划 / [PREVIOUS] 向下划 / [NONE] 不切换
     */
    fun direction(
        dxPx: Float,
        dyPx: Float,
        screenHeightPx: Int,
        durationMs: Long,
        touchSlopPx: Float
    ): Int {
        val threshold = thresholdPx(screenHeightPx)
        if (threshold <= 0f) return NONE
        if (durationMs < MIN_MOVE_MS || durationMs > MAX_MOVE_MS) return NONE
        val dyAbs = abs(dyPx)
        if (dyAbs < threshold) return NONE
        // 斜划：横向位移明显大于纵向 → 用户想调进度/缩放，不切视频
        val maxDx = max(touchSlopPx * 2f, dyAbs / HORIZONTAL_RATIO)
        if (abs(dxPx) > maxDx) return NONE
        return if (dyPx < 0f) NEXT else PREVIOUS
    }

    // ==================== 长按倍速提示尺寸 ====================

    /** 默认缩放档（百分比）。 */
    const val HINT_DEFAULT_PERCENT = 100

    /** 默认档的存档形态（设置页 `list_choose` 的 defaultValue 用它）。 */
    const val HINT_DEFAULT_STORED = "100"

    /** 设置页下拉项的显示名，顺序与 [hintStoredValues] 一一对应。 */
    val hintDisplayNames: List<String> = listOf("80%", "100%", "120%", "150%")

    /** 设置页下拉项真正落盘的值（百分比字符串）。 */
    val hintStoredValues: List<String> = listOf("80", "100", "120", "150")

    /**
     * 把任意百分比收敛到可用范围 [80, 150]。
     *
     * 脏存档（0、负数、999）不能直接拿去 `scale`：0 会让提示消失、过大则糊满屏幕。
     */
    fun clampPercent(percent: Int): Int = when {
        percent < 80 -> 80
        percent > 150 -> 150
        else -> percent
    }

    /** 把存档原文解析成百分比；缺失/非数字回默认 100。 */
    fun parseHintPercent(raw: String?): Int =
        clampPercent(raw?.trim()?.toIntOrNull() ?: HINT_DEFAULT_PERCENT)

    /** 提示视图要用的缩放倍数（0.8 / 1.0 / 1.2 / 1.5）。 */
    fun hintScale(raw: String?): Float = parseHintPercent(raw) / 100f

    /** 「长按上下划切换视频」开关的默认值（默认开，与连播队列一起才有意义）。 */
    const val DEFAULT_SWITCH_ENABLED = true
}
