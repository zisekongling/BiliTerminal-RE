package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import java.util.Locale

/**
 * G02「±N 秒快退 / 快进」的步长判据（26.11.13，来自兄弟分支 cyq 的 `player_seek_buttons`）。
 *
 * 三件事：
 * - 步长读设置并夹进 [MIN_SECONDS]..[MAX_SECONDS]，脏数据（0、负数、手改的 9999）一律兜回来；
 * - 目标位置 = 当前位置 + 步长，越界交给 `PlayerJumpTimePolicy.clampMs`（那边已有「时长无效返回 null」
 *   的语义，直播/未 prepare 不跳）；
 * - 按钮文字里带步长（`«10s` / `10s»`），格式化统一走 [label]，避免各调用点自己拼 string。
 *
 * 为什么单独一个设置键而不是复用双击快进步长：双击是手势、用户可能已经调成 30 秒，
 * 而按钮想要的是「轻点小步、双击大步」，两者不该互相拖累。
 */
object PlayerSeekStepPolicy {

    /** 默认步长（秒）：与双击快进的默认值一致，用户不用重新学。 */
    const val DEFAULT_SECONDS = 10

    /** 步长下限：再小就与「按一下几乎没动」无异，手表上还容易被误触成连点。 */
    const val MIN_SECONDS = 3

    /** 步长上限：120 秒已经能跨过绝大多数片头，再大就该用进度条或「跳转时间」了。 */
    const val MAX_SECONDS = 120

    /** 把任意输入夹成合法步长；`<= 0` 视为没设过，回落到默认值。 */
    fun sanitize(raw: Int): Int =
        if (raw <= 0) DEFAULT_SECONDS else raw.coerceIn(MIN_SECONDS, MAX_SECONDS)

    /** 读当前设置（设置页写的是 Int）。 */
    fun load(): Int = sanitize(
        SharedPreferencesUtil.getInt(SettingsKeys.PLAYER_SEEK_STEP_SECONDS, DEFAULT_SECONDS)
    )

    /**
     * 从 [positionMs] 出发走 [deltaSeconds] 秒后的位置（毫秒）。
     *
     * 只做加法并夹住步长本身，**不夹到 `[0, duration]`**——那是 `PlayerJumpTimePolicy.clampMs` 的活，
     * 这里夹了会掩盖「时长未知」的情况（比如直播）。
     */
    fun offsetMs(positionMs: Long, deltaSeconds: Int): Long {
        val safeDelta = deltaSeconds.coerceIn(-MAX_SECONDS, MAX_SECONDS)
        return positionMs + safeDelta * 1000L
    }

    /** 按钮文字：`format` 由调用方从资源取（带 `%1$d`），本类不持 Context。 */
    fun label(format: String, seconds: Int): String =
        String.format(Locale.US, format, sanitize(seconds))
}
