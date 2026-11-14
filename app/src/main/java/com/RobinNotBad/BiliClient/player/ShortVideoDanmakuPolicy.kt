package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 短视频弹幕档位（26.11.14）。
 *
 * 背景：短视频页与长视频播放器共用 [DanmakuManager]，弹幕档位以前也共用同一份存档。
 * 手表屏只有 400~466px，长视频的默认档是「15 行 + 允许重叠」，放到短视频这种**弹幕极密**
 * 的场景上就糊成一团（用户反馈原话「鬼畜弹幕」），没有可调的地方。
 *
 * 做法：给短视频一份**独立存档**（开关 + 行数 + 是否重叠），默认取更保守的 6 行 / 不重叠。
 * 这里只负责「读档 + 钳制 + 决定要不要覆盖」，覆盖本身由 [DanmakuManager.init] 的
 * [DanmakuConfigOverride] 参数落地；**长视频那两项存档一个字节都不动**。
 *
 * 为什么不是「按设备档位自动压行数」：那条路 26.11.08 已经被用户明确否掉
 * （见 [DanmakuManager.init] 里的注释）——改的必须是用户看得见、自己填的值。
 */
object ShortVideoDanmakuPolicy {

    /** 默认最大行数：手表屏 6 行大约刚好铺满画面下 2/3，再多就互相压字。 */
    const val DEFAULT_MAX_LINES = 6

    /** 默认不允许重叠：重叠是「糊成一团」的另一半原因。 */
    const val DEFAULT_ALLOW_OVERLAP = false

    /** 行数允许范围，与设置项输入框的钳制口径一致。 */
    const val MIN_MAX_LINES = 1
    const val MAX_MAX_LINES = 30

    /** 用户是否要「短视频弹幕单独配置」（默认开：不跟长视频共用那套宽松档）。 */
    fun isSeparate(): Boolean =
        SharedPreferencesUtil.getBoolean(SettingsKeys.SHORTVIDEO_DANMAKU_SEPARATE, true)

    /** 短视频弹幕最大行数（已钳到 [MIN_MAX_LINES]~[MAX_MAX_LINES]）。 */
    fun maxLines(): Int =
        clampLines(
            SharedPreferencesUtil.getInt(SettingsKeys.SHORTVIDEO_DANMAKU_MAXLINE, DEFAULT_MAX_LINES)
        )

    /** 短视频弹幕是否允许重叠。 */
    fun allowOverlap(): Boolean =
        SharedPreferencesUtil.getBoolean(
            SettingsKeys.SHORTVIDEO_DANMAKU_ALLOW_OVERLAP,
            DEFAULT_ALLOW_OVERLAP
        )

    /**
     * 交给 [DanmakuManager.init] 的覆盖值。
     *
     * 返回 null 表示「开关关掉了」——此时短视频照旧读全局存档，与长视频完全一致。
     */
    fun overrideOrNull(): DanmakuConfigOverride? =
        if (!isSeparate()) null
        else DanmakuConfigOverride(maxLines = maxLines(), allowOverlap = allowOverlap())

    /** 纯函数：把行数钳进合法区间（设置项也能填 0 或 999）。 */
    fun clampLines(lines: Int): Int = lines.coerceIn(MIN_MAX_LINES, MAX_MAX_LINES)
}
