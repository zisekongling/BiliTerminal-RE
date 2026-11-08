package com.RobinNotBad.BiliClient.ui.widget

import com.RobinNotBad.BiliClient.util.IslandState

/**
 * 灵动岛状态机输入（纯数据，JVM 单测用）。
 *
 * @param nowMs 当前 `SystemClock.uptimeMillis()`
 * @param playing 后台播放中
 * @param downloading 下载中
 * @param unread 未读消息数
 * @param burst 瞬时事件（低电量 / 公告 / 更新），优先于持续事件
 * @param showTime 当前是否处于「给时间让位」的 5 秒窗口
 * @param phaseStartMs 当前阶段开始的时刻（[showTime] 为 true 时是 5 秒窗口的起点，
 * 否则是 30 秒事件窗口的起点）
 */
data class IslandInput(
    val nowMs: Long,
    val playing: Boolean,
    val downloading: Boolean,
    val unread: Int,
    val burst: IslandState?,
    val showTime: Boolean,
    val phaseStartMs: Long
)

/**
 * 一次状态判断的结果。
 *
 * 调用方要把 [showTime] / [phaseStartMs] 原样存回绑定层，下一次 tick 再传进来；
 * 它就是一个只有两个字段的状态机，不需要额外的调度器对象。
 */
data class IslandDecision(
    val state: IslandState,
    val showTime: Boolean,
    val phaseStartMs: Long
)

/**
 * 顶栏灵动岛的显示策略（纯函数）。
 *
 * 规则（26.11.07 用户拍板）：
 * 1. 瞬时事件（低电量 / 新公告 / 新版本）优先，显示 10 秒后由 `IslandStatus` 自己撤掉；
 * 2. 持续事件按 `播放中 > 下载中 > 未读` 取最高优先级；
 * 3. 持续事件每显示 30 秒，让 5 秒给时间，然后回到当前最高优先级事件；
 * 4. 没有事件时一直显示时间。
 *
 * **不要在这里加 Android 依赖**：它必须能在 JVM 单测里直接跑。文案、颜色、动画都在
 * `TopbarIsland`。
 */
object IslandPolicy {

    /**
     * 功能总闸：26.11.08 起暂时关闭并隐藏入口（代码保留，恢复时改回 true）。
     *
     * 顶栏开关、设置项、搜索索引三处都读这个常量；关着时即使存档里残留 true 也不会挂载。
     */
    const val FEATURE_ENABLED = false

    /** 持续事件连续显示多久后让位给时间。 */
    const val EVENT_SLICE_MS = 30_000L

    /** 时间插播时长。 */
    const val TIME_SLICE_MS = 5_000L

    /**
     * 灵动岛开关的迁移口径（26.11.08 与「圆屏适配」解耦）。
     *
     * 旧实现把灵动岛**硬绑在「圆屏适配」上**，没有自己的开关（`TopbarIsland.attach` 里判
     * `SettingsKeys.UI_ROUND`）。独立成开关后要把老用户的现状继承过来，否则开着圆屏适配的人
     * 升级后功能会凭空消失：
     * - [explicit] 非 null（用户在新开关上设过）→ **完全听用户的**，与圆屏适配无关；
     * - [explicit] 为 null（没设过，包含新装用户）→ 继承旧行为 [legacyRound]。
     *
     * 纯函数：读存档与落盘迁移都在 `TopbarIsland` 里做，这里只决定取哪个值。
     */
    fun resolveEnabled(explicit: Boolean?, legacyRound: Boolean): Boolean = explicit ?: legacyRound

    /** 当前持续事件的最高优先级；没有事件时为 null。 */
    fun ongoingState(playing: Boolean, downloading: Boolean, unread: Int): IslandState? = when {
        playing -> IslandState.PLAYING
        downloading -> IslandState.DOWNLOADING
        unread > 0 -> IslandState.UNREAD
        else -> null
    }

    fun decide(input: IslandInput): IslandDecision {
        // 瞬时事件打断一切，包括正在插播的时间窗口。
        input.burst?.let {
            return IslandDecision(it, input.showTime, input.phaseStartMs)
        }

        val ongoing = ongoingState(input.playing, input.downloading, input.unread)
            ?: return IslandDecision(IslandState.TIME, false, input.nowMs)

        if (input.showTime) {
            return if (input.nowMs - input.phaseStartMs < TIME_SLICE_MS) {
                IslandDecision(IslandState.TIME, true, input.phaseStartMs)
            } else {
                // 5 秒时间结束：回到事件，重新开始计 30 秒。
                IslandDecision(ongoing, false, input.nowMs)
            }
        }

        return if (input.nowMs - input.phaseStartMs >= EVENT_SLICE_MS) {
            // 事件满 30 秒：让位 5 秒给时间。
            IslandDecision(IslandState.TIME, true, input.nowMs)
        } else {
            IslandDecision(ongoing, false, input.phaseStartMs)
        }
    }
}
