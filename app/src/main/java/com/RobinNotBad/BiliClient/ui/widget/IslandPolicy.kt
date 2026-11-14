package com.RobinNotBad.BiliClient.ui.widget

import com.RobinNotBad.BiliClient.util.IslandState

/**
 * 灵动岛的分类开关（用户在外观 → 灵动岛设置里勾的 5 个选项）。
 *
 * 关掉某一类只是「不显示」，数据照常采集：再打开时不用等下一次事件。
 * 默认全开，与 26.11.11 之前的行为一致。
 */
data class IslandFilters(
    val playing: Boolean = true,
    val downloading: Boolean = true,
    val unread: Boolean = true,
    val battery: Boolean = true,
    val announcement: Boolean = true
)

/**
 * 灵动岛状态机输入（纯数据，JVM 单测用）。
 *
 * @param nowMs 当前 `SystemClock.uptimeMillis()`
 * @param playing 后台播放中
 * @param downloading 下载中
 * @param unreadBurst 刚收到新未读消息（由 `IslandStatus` 做增量检测，只弹一次）
 * @param burst 瞬时事件（低电量 / 充电 / 公告 / 更新），优先于一切
 * @param filters 分类开关
 * @param showBase 当前是否处于「让位给时间 + 电量」的窗口
 * @param phaseStartMs 当前阶段开始的时刻（[showBase] 为 true 时是基础窗口的起点，
 * 否则是常驻事件窗口的起点）
 * @param rotateMs 轮换间隔；`0` = 不轮换（常驻事件一直占着，默认）
 */
data class IslandInput(
    val nowMs: Long,
    val playing: Boolean,
    val downloading: Boolean,
    val unreadBurst: Boolean = false,
    val burst: IslandState? = null,
    val filters: IslandFilters = IslandFilters(),
    val showBase: Boolean = false,
    val phaseStartMs: Long = 0L,
    val rotateMs: Long = 0L
)

/**
 * 一次状态判断的结果。
 *
 * 调用方要把 [showBase] / [phaseStartMs] 原样存回绑定层，下一次 tick 再传进来；
 * 它就是一个只有两个字段的状态机，不需要额外的调度器对象。
 */
data class IslandDecision(
    val state: IslandState,
    val showBase: Boolean,
    val phaseStartMs: Long
)

/**
 * 顶栏灵动岛的显示策略（纯函数）。
 *
 * ## 三层优先级（26.11.14 用户拍板，取代 26.11.07 的「30 秒事件 + 5 秒时间」轮换）
 * 1. **瞬时事件**（低电量 / 充电 / 新公告 / 新版本 / 新未读）：短播，不参与轮换池。
 *    时长由 `IslandStatus.BURST_MS` 控制（5 秒），到点自己撤掉，回到之前的状态。
 * 2. **常驻事件**（播放中 > 下载中）：有就**一直显示**，除非用户开了轮换间隔，
 *    才每隔 N 秒让 N 秒给「时间 + 电量」。
 * 3. **基础状态**（时间，若开了电量显示则时间右侧并排电量图标）：没有事件时的兜底。
 *
 * 轮换间隔（[IslandInput.rotateMs]）只作用于「常驻事件 ↔ 基础状态」之间：
 * `0` = 不轮换（默认，符合「播放时一直显示播放状态」）；3 / 5 / 10 秒为对称切换。
 *
 * **不要在这里加 Android 依赖**：它必须能在 JVM 单测里直接跑。文案、颜色、动画都在
 * `TopbarIsland`。
 */
object IslandPolicy {

    /**
     * 功能总闸：26.11.08 曾暂时关闭并隐藏入口，26.11.09 用户要求恢复。
     *
     * 顶栏开关、设置项、搜索索引三处都读这个常量；关着时即使存档里残留 true 也不会挂载。
     */
    const val FEATURE_ENABLED = true

    /** 胶囊宽度按屏幕宽度封顶的比例（26.11.08：233dp 屏的 31% ≈ 72dp）。 */
    const val PILL_SCREEN_RATIO = 0.31f

    /** 轮换间隔允许的取值（毫秒）：0 = 不轮换。设置页只给这几个选项。 */
    val ROTATE_OPTIONS_MS = listOf(0L, 3_000L, 5_000L, 10_000L)

    /** 轮换间隔的默认值：不轮换。 */
    const val ROTATE_DEFAULT_MS = 0L

    /**
     * 胶囊宽度（px）：**全局固定**，同一设备上所有页面、所有状态一致。
     *
     * 26.11.10 用户拍板的口径。此前宽度按「标题还剩多少空档」自适应，同一设备上不同页面
     * 会得到不同宽度（用户报的 bug），这里改成只由「理想宽度」和「屏幕宽度」决定：
     * - 宽度与当前状态、标题长短、是否圆屏都无关；
     * - 小屏（如 233dp 圆表）按屏宽 [PILL_SCREEN_RATIO] 封顶，避免胶囊被圆盘切角；
     * - 屏宽拿不到（<= 0）时退回理想宽度。
     *
     * 纯函数，JVM 单测直接跑（见 `IslandPolicyTest.islandWidthIsTheSameEverywhere`）。
     */
    fun pillWidthPx(wantedPx: Int, screenWidthPx: Int): Int {
        if (wantedPx <= 0) return 0
        if (screenWidthPx <= 0) return wantedPx
        val byScreen = (screenWidthPx * PILL_SCREEN_RATIO).toInt().coerceAtLeast(1)
        return minOf(wantedPx, byScreen)
    }

    /**
     * 轮换间隔的解析：设置页按字符串存值（listChoose 的统一口径），这里兜回允许的取值。
     *
     * 非法值 / 空值 → [ROTATE_DEFAULT_MS]（不轮换），与「用户没动过这一项」等价。
     */
    fun rotateMsOf(raw: String?): Long {
        val ms = raw?.trim()?.toLongOrNull() ?: return ROTATE_DEFAULT_MS
        return if (ms in ROTATE_OPTIONS_MS) ms else ROTATE_DEFAULT_MS
    }

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

    /**
     * 当前常驻事件的最高优先级；没有事件时为 null。
     *
     * 未读**不在**这里：它已改成「有新消息时短播 5 秒」的瞬时事件（见 [IslandInput.unreadBurst]），
     * 不再常驻占着胶囊。
     */
    fun ongoingState(playing: Boolean, downloading: Boolean): IslandState? = when {
        playing -> IslandState.PLAYING
        downloading -> IslandState.DOWNLOADING
        else -> null
    }

    /** 瞬时事件是否被用户关掉了。 */
    private fun burstAllowed(burst: IslandState, filters: IslandFilters): Boolean = when (burst) {
        IslandState.BATTERY -> filters.battery
        IslandState.ANNOUNCEMENT -> filters.announcement
        else -> true
    }

    fun decide(input: IslandInput): IslandDecision {
        // 1. 瞬时事件打断一切，包括正在轮换的基础窗口。
        val burst = input.burst?.takeIf { burstAllowed(it, input.filters) }
        if (burst != null) {
            return IslandDecision(burst, input.showBase, input.phaseStartMs)
        }

        // 2. 新未读消息：短播，同样不进入轮换池。
        if (input.unreadBurst && input.filters.unread) {
            return IslandDecision(IslandState.UNREAD, input.showBase, input.phaseStartMs)
        }

        // 3. 常驻事件（按开关过滤后按优先级取一个）。
        val ongoing = ongoingState(
            playing = input.playing && input.filters.playing,
            downloading = input.downloading && input.filters.downloading
        ) ?: return IslandDecision(IslandState.TIME, false, input.nowMs)

        // 不轮换：事件一直占着，直到它自己结束。
        if (input.rotateMs <= 0L) {
            return IslandDecision(ongoing, false, input.phaseStartMs)
        }

        // 对称轮换：事件 N 秒 → 时间 + 电量 N 秒 → 事件 N 秒……
        if (input.showBase) {
            return if (input.nowMs - input.phaseStartMs < input.rotateMs) {
                IslandDecision(IslandState.TIME, true, input.phaseStartMs)
            } else {
                IslandDecision(ongoing, false, input.nowMs)
            }
        }

        return if (input.nowMs - input.phaseStartMs >= input.rotateMs) {
            IslandDecision(IslandState.TIME, true, input.nowMs)
        } else {
            IslandDecision(ongoing, false, input.phaseStartMs)
        }
    }
}
