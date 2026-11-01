package com.RobinNotBad.BiliClient.activity.player

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 「长按倍速」档位的纯逻辑（零 Android 依赖，可直接 JVM 单测，见 `PlayerLongPressPolicyTest`）。
 *
 * **为什么需要它**（反馈 #61）：播放页的长按加速原先写死 `ijkPlayer.setSpeed(3.0F)`、
 * 提示文字写死 `"x 3.0"`，用户没法改成 2x/5x。设置页里那个同名的「长按倍速」开关只是
 * 「允不允许长按加速」，不是倍率本身，所以倍率另起一个存档键。
 *
 * 三条约定：
 * 1. **默认 3.0 与改造前完全一致**——没在设置页动过这一项的用户，长按仍是 3 倍速；
 * 2. **只认这四个档位**，存档里的脏值（手改的 `2.5`、旧版本残留的空串）一律退回默认，
 *    不把任意浮点直接交给 `setSpeed`（0 或负数会让播放器卡住）；
 * 3. **改完即生效**：读值的一方每次长按时重读存档，不在 Activity 启动时缓存。
 */
object PlayerLongPressPolicy {

    /**
     * 存档键（`SharedPreferences` 里存 String，与设置页 `list_choose` 的存储方式一致）。
     *
     * 刻意不放进 `util/SettingsKeys`：那是本次修复作用域之外的文件，而这里的键只有
     * 「长按倍速档位」一个消费方（设置页 + 播放页），放在逻辑旁边反而更容易保持一致。
     */
    const val KEY = "player_longclick_speed"

    /** 默认倍率：与改造前写死的 3.0F 一致。 */
    const val DEFAULT_SPEED = 3.0f

    /** 默认档位的存档形态（设置页 `list_choose` 的 defaultValue 用它）。 */
    const val DEFAULT_STORED = "3.0"

    /** 允许的档位。顺序即设置页里的展示顺序。 */
    private val SPEEDS = listOf(1.5f, 2.0f, 3.0f, 5.0f)

    /** 设置页下拉项的显示名，如 `1.5x`。 */
    val displayNames: List<String> = SPEEDS.map { format(it) + "x" }

    /** 设置页下拉项真正落盘的值，如 `1.5`；顺序与 [displayNames] 一一对应。 */
    val storedValues: List<String> = SPEEDS.map { format(it) }

    /**
     * 把存档原文解析成可用倍率。
     *
     * 缺失、空串、非数字、以及**不在档位表里**的值都退回 [DEFAULT_SPEED]：
     * 后者是刻意收紧的——`setSpeed(0.0f)` / 负值在 IJK 里不是「慢放」，而是直接不动甚至报错。
     */
    fun fromStored(raw: String?): Float {
        val parsed = raw?.trim()?.toFloatOrNull() ?: return DEFAULT_SPEED
        return SPEEDS.firstOrNull { abs(it - parsed) < 0.01f } ?: DEFAULT_SPEED
    }

    /** 倍率 → 落盘/显示用的小数字符串：`1.5`、`2.0`、`3.0`、`5.0`。 */
    fun format(speed: Float): String {
        val rounded = speed.roundToInt()
        return if (abs(speed - rounded) < 0.01f) "$rounded.0" else speed.toString()
    }

    /** 播放页倍速提示文字：`x 3.0`（与既有 `speed_strs` 的写法一致）。 */
    fun labelOf(speed: Float): String = "x " + format(speed)

    /** 该倍率是否在允许的档位表里（设置页/单测用）。 */
    fun isSupported(speed: Float): Boolean = SPEEDS.any { abs(it - speed) < 0.01f }
}
