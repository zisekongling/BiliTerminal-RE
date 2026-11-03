package com.RobinNotBad.BiliClient.activity.player

import com.RobinNotBad.BiliClient.util.SettingsKeys
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 左右滑动调进度的**灵敏度档位**（反馈 #94a）。
 *
 * 为什么要有倍率：手表屏幕窄，部分设备 dpi 又低，同样的手指位移换算成进度会被放大，
 * 用户反馈「拖动一点点就快进很多」。原来这个比例写死 1:1，只能整个关掉手势。
 *
 * 约定（与 [PlayerLongPressPolicy] 一致）：
 * 1. 默认 1.0x —— 与改造前完全一致，老用户升级后手感不变；
 * 2. 只认列出来的档位，脏存档一律退回默认，绝不把非法倍率交给播放页；
 * 3. 改完即生效 —— 播放页每次横滑重新读存档，不在这里缓存。
 */
object PlayerSwipeSeekScalePolicy {
    /** 存档键，值形如 `"1.0"`。 */
    const val KEY = SettingsKeys.PLAYER_SWIPE_SEEK_SCALE

    /** 默认倍率：改造前就是 1:1，升级后行为不变。 */
    const val DEFAULT_SCALE = 1.0f

    /** 默认档位的存档形态，与 [storedValues] 里的写法保持一致。 */
    const val DEFAULT_STORED = "1.0"

    /** 档位：从「划半屏才动一点」到「划一点就跳很远」，覆盖低 dpi 与小屏两端。 */
    private val SCALES = listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f)

    /** 设置页显示名，顺序与 [storedValues] 一一对应。 */
    val displayNames: List<String> = SCALES.map { format(it) + "x" }

    /** 存档值，顺序与 [displayNames] 一一对应。 */
    val storedValues: List<String> = SCALES.map { format(it) }

    /**
     * 把存档读成倍率。空值、非数字、档位外的值都退回 [DEFAULT_SCALE]。
     *
     * 为什么要挡脏数据：倍率直接乘进 seek 目标，负数或 NaN 会让进度条跑到非法位置。
     */
    fun fromStored(raw: String?): Float {
        val scale = raw?.trim()?.toFloatOrNull() ?: return DEFAULT_SCALE
        return if (isSupported(scale)) scale else DEFAULT_SCALE
    }

    /** 是否是我们认识的档位。浮点相等统一走容差，避免存档 `0.5` 之类的表示误差。 */
    fun isSupported(scale: Float): Boolean = SCALES.any { abs(it - scale) < 0.005f }

    /** 档位格式化：整数补一位小数（`1` → `1.0`），`0.25` 这类保留两位。 */
    fun format(scale: Float): String {
        val rounded = (scale * 100f).roundToLong() / 100f
        return if (abs(rounded - rounded.toLong()) < 0.001f) String.format(Locale.US, "%.1f", rounded)
        else rounded.toString()
    }

    /**
     * 本次横滑的目标位置（毫秒）。
     *
     * 纯函数：把「起点 + 位移比例 × 总时长 × 倍率」算完并 clamp 进 `[0, duration]`，
     * 播放页只负责读事件、画浮层。`durationMs <= 0`（直播/时长未知）时不动位置。
     */
    fun targetMs(startMs: Long, deltaX: Float, widthPx: Float, durationMs: Long, scale: Float): Long {
        if (durationMs <= 0L) return startMs.coerceAtLeast(0L)
        val safeWidth = if (widthPx > 0f) widthPx else 1f
        val safeScale = if (isSupported(scale)) scale else DEFAULT_SCALE
        val offsetMs = (deltaX / safeWidth * durationMs * safeScale).toLong()
        return (startMs + offsetMs).coerceIn(0L, durationMs)
    }
}

/**
 * 「跳到指定时间」的输入解析（QQ 反馈 #9）。
 *
 * 玩家在手表上拖进度条很难停准，希望直接输入时间点。这里只做纯计算：
 * 支持纯秒数 / `mm:ss` / `hh:mm:ss`；非法输入返回 null，由播放页提示后不跳，
 * 绝不静默跳到错误位置。
 */
object PlayerJumpTimePolicy {
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3600L

    /**
     * 单个数字段的上限（约 277 小时）。手表上没人会输入更大的数；
     * 先限死位数再做乘法，就不会出现 Long 溢出后的「合法」结果。
     */
    private const val MAX_PART = 999_999L

    /**
     * 解析输入为秒数；非法返回 null。
     *
     * 规则：
     * - 允许前后空白与全角冒号（手表输入法容易打出「：」）；
     * - 除数字与冒号外不允许其他字符，避免 `1a:30` 被 `toLongOrNull` 部分吃掉；
     * - 冒号分段最多 3 段，空段非法（`1:`、`:30` 都不认）；
     * - `mm:ss` / `hh:mm:ss` 里的「分/秒」必须 < 60 —— `1:90` 是误输入，不是 150 秒。
     */
    fun parseToSeconds(input: String?): Long? {
        val text = input?.trim().orEmpty().replace('：', ':')
        if (text.isEmpty()) return null
        if (!text.all { it in '0'..'9' || it == ':' }) return null
        val parts = text.split(':')
        if (parts.size > 3) return null
        val numbers = parts.map { it.toLongOrNull() ?: return null }
        if (numbers.any { it > MAX_PART }) return null
        val total = when (numbers.size) {
            1 -> numbers[0]
            2 -> {
                if (numbers[1] >= SECONDS_PER_MINUTE) return null
                numbers[0] * SECONDS_PER_MINUTE + numbers[1]
            }
            3 -> {
                if (numbers[1] >= SECONDS_PER_MINUTE || numbers[2] >= SECONDS_PER_MINUTE) return null
                numbers[0] * SECONDS_PER_HOUR + numbers[1] * SECONDS_PER_MINUTE + numbers[2]
            }
            else -> return null
        }
        // 保险丝：任何意外溢出都不许把负数位置送出去
        return if (total < 0L) null else total
    }

    /**
     * 把目标毫秒限制进 `[0, durationMs]`；时长无效（直播、未 prepare）返回 null。
     *
     * 直播没有「时间点」，调用方据此不显示入口；播放页再兜一次，防止菜单隐藏与点击的时序缝隙。
     */
    fun clampMs(targetMs: Long, durationMs: Long): Long? {
        if (durationMs <= 0L) return null
        return targetMs.coerceIn(0L, durationMs)
    }
}
