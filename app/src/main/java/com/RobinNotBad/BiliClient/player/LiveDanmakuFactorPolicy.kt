package com.RobinNotBad.BiliClient.player

/**
 * 直播滚动弹幕「停留时长倍率」的纯逻辑（D5）。
 *
 * 背景：直播弹幕是实时滚动聊天，普通录播按解析弹幕的节奏（`player_danmaku_speed`，默认 1.0）
 * 滚得偏快、来不及看；所以直播单独给一个倍率，默认 **2.0**（停留时间约为普通弹幕的两倍），
 * 可调范围 **1.0–3.0**。它与普通弹幕的 `speedFactor` **互相独立**：
 * 录播改「弹幕速度」不会影响直播，直播改倍率也不影响录播。
 *
 * 落到 DFM 上的通道是 `DanmakuContext.setScrollSpeedFactor(factor)`：DFM 的 Duration
 * 语义是 `value = initial * factor`，factor 越大停留越久，正好等于这里的「倍率」。
 *
 * 纯函数，单测见 `LiveDanmakuFactorPolicyTest`。
 */
object LiveDanmakuFactorPolicy {

    /** 存档键（与上游/兄弟分支同名，升级后用户已有的值直接生效）。 */
    const val KEY = "player_live_danmaku_duration_factor"

    /** 默认倍率：直播弹幕停留时间约为普通弹幕的两倍。 */
    const val DEFAULT = 2.0f

    /** 下限：1.0 = 与普通弹幕同速。 */
    const val MIN = 1.0f

    /** 上限：3.0，再长会明显拖慢挡位判断、弹幕容易叠在一起。 */
    const val MAX = 3.0f

    /** 设置页展示用的档位（与 [storedValues] 一一对应）。 */
    val displayNames = listOf("1.0x", "1.5x", "2.0x", "2.5x", "3.0x")

    /** 落盘用的档位值（list_choose 存字符串）。 */
    val storedValues = listOf("1.0", "1.5", "2.0", "2.5", "3.0")

    /** 把任意值钳到 [MIN]..[MAX]；NaN/无穷按 [DEFAULT] 处理。 */
    @JvmStatic
    fun sanitize(value: Float): Float {
        if (value.isNaN() || value.isInfinite()) return DEFAULT
        return value.coerceIn(MIN, MAX)
    }

    /** 解析存档字符串（list_choose 存的是字符串）：空/非法按 [DEFAULT]。 */
    @JvmStatic
    fun parseStored(raw: String?): Float {
        val parsed = raw?.trim()?.toFloatOrNull() ?: DEFAULT
        return sanitize(parsed)
    }

    /**
     * 本次弹幕渲染要用的 DFM `scrollSpeedFactor`。
     *
     * @param live            是否直播（直播用独立倍率）
     * @param normalSpeedFactor 录播的普通弹幕速度（`player_danmaku_speed`）
     * @param liveFactor      直播倍率存档值
     */
    @JvmStatic
    fun scrollSpeedFactor(live: Boolean, normalSpeedFactor: Float, liveFactor: Float): Float =
        if (live) sanitize(liveFactor) else normalSpeedFactor
}
