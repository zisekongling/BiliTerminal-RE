package com.RobinNotBad.BiliClient.player

import kotlin.math.roundToInt

/**
 * 「无极调音」的纯逻辑：播放器软件增益（0~100%）与 ijk `setVolume` 采样级增益（0.0~1.0）互转。
 *
 * 为什么需要它：手表的系统媒体音量通常只有很少几档（`AudioManager.getStreamMaxVolume` 返回值很小），
 * 只靠 `setStreamVolume` 一档一档加减，会出现「20% 直接跳到 33%」的手感，调不出 30% 这种需求；
 * 而 ijk 的 `setVolume(left, right)` 是解码后在采样上做线性乘算，可以 1% 一档地细调，且与系统音量互不干扰。
 *
 * 这里只放纯计算与边界钳制，不碰 Android API，方便直接在 JVM 上单测（见 `PlayerVolumePolicyTest`）。
 */
object PlayerVolumePolicy {

    /** 增益百分比的持久化键（int，0~100）。缺失或脏数据都按 [DEFAULT_PERCENT] 处理。 */
    const val KEY_GAIN_PERCENT = "player_volume_gain_percent"

    /**
     * 默认增益：100%（满格）。
     *
     * 选 100 而不是 50 作为默认值，是为了让「没动过这个功能」的用户听感与改造前完全一致：
     * 增益 1.0 时 ijk 只做直通乘算，实际音量仍由系统媒体音量决定。
     */
    const val DEFAULT_PERCENT = 100

    /** 增益下限：0% 才是真静音（采样乘 0），不是把播放器暂停。 */
    const val MIN_PERCENT = 0

    /** 增益上限：100%，不放大，避免超过满刻度后削波失真。 */
    const val MAX_PERCENT = 100

    /** 滑条/细调时 1% 一档。 */
    const val FINE_STEP_PERCENT = 1

    /** 音量键短按粗调时 5% 一档。 */
    const val COARSE_STEP_PERCENT = 5

    /** 把任意整数百分比钳到 [MIN_PERCENT]..[MAX_PERCENT]。 */
    fun clampPercent(percent: Int): Int = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)

    /**
     * 读取存档时用：存档可能是旧版本写的负数、被手改过的 999，直接交给 `setVolume` 会静音或爆音，
     * 所以钳制放在读取侧（与 [PlayerDefaults.clampSubtitleSize] 同一套思路），不要信任落盘值。
     */
    fun sanitize(storedPercent: Int): Int = clampPercent(storedPercent)

    /** 百分比 → 采样级增益。0 → 0.0（静音），100 → 1.0（不放大）。 */
    fun percentToGain(percent: Int): Float = clampPercent(percent) / MAX_PERCENT.toFloat()

    /**
     * 采样级增益 → 百分比（四舍五入），用于把外部算出的增益回显到滑条。
     *
     * NaN（例如脏第三方库返回值）没有可比较语义，退回 [DEFAULT_PERCENT]，不要当成 0 静音。
     */
    fun gainToPercent(gain: Float): Int {
        if (gain.isNaN()) return DEFAULT_PERCENT
        return clampPercent((gain * MAX_PERCENT).roundToInt())
    }

    /** 步长钳制：0 或负数步长会退化成 [FINE_STEP_PERCENT]，避免「按了没反应」。 */
    fun coerceStep(step: Int): Int = step.coerceIn(FINE_STEP_PERCENT, MAX_PERCENT)

    /**
     * 按方向步进，并在边界处停住（不绕回对侧）。
     *
     * [direction] 大于 0 加、小于 0 减、等于 0 原样返回；[step] 走 [coerceStep]。
     */
    fun step(percent: Int, direction: Int, step: Int = COARSE_STEP_PERCENT): Int {
        val dir = when {
            direction > 0 -> 1
            direction < 0 -> -1
            else -> return clampPercent(percent)
        }
        return clampPercent(clampPercent(percent) + dir * coerceStep(step))
    }

    /** 只有 0% 才算静音。 */
    fun isMuted(percent: Int): Boolean = clampPercent(percent) == MIN_PERCENT

    /** 左右声道用同一个增益；返回 Pair 方便直接 `setVolume(pair.first, pair.second)`。 */
    fun gainPair(percent: Int): Pair<Float, Float> {
        val gain = percentToGain(percent)
        return gain to gain
    }

    /**
     * 系统媒体音量的档位步进（反馈 #191）。
     *
     * [current] 与 [max] 都来自 AudioManager 的 getStreamVolume / getStreamMaxVolume，
     * 方向语义与 [step] 一致：大于 0 加一档、小于 0 减一档、等于 0 原样返回。边界处停住，
     * 不绕回对侧——手表上系统音量常常只有十几档，绕回会变成「按到最大再按一下就静音」。
     *
     * [max] 非法（小于等于 0，个别设备的 STREAM_MUSIC 会返回 0）时按 0 处理，调用方据此跳过。
     */
    fun stepStream(current: Int, max: Int, direction: Int): Int {
        if (max <= 0) return 0
        val dir = when {
            direction > 0 -> 1
            direction < 0 -> -1
            else -> return current.coerceIn(0, max)
        }
        return (current + dir).coerceIn(0, max)
    }

    /** 系统媒体音量档位 → 百分比（0~100，四舍五入），只用于回显。 */
    fun streamPercent(current: Int, max: Int): Int {
        if (max <= 0) return MIN_PERCENT
        return clampPercent((current.toFloat() / max * MAX_PERCENT).roundToInt())
    }
}
