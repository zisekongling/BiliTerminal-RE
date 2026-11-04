package com.RobinNotBad.BiliClient.activity.player

import com.RobinNotBad.BiliClient.player.SkipCancelPolicy
import com.RobinNotBad.BiliClient.player.ViewPointSkip
import java.util.concurrent.atomic.AtomicLong

/**
 * 自动跳过片头片尾的**纯状态机**（零 Android 依赖，可直接 JVM 单测）。
 *
 * **为什么要有它**（反馈 #34「跳过片头片尾会因为视频分段也跳过」）：判定逻辑本身
 * （`ViewPointSkip` / `SkipCancelPolicy`）没问题，问题出在**片段表与已处理集合的生命周期**上——
 * 它们原先只是 `PlayerActivity` 里三个散装字段，谁都能改，于是有三条会让片段落到**别的集/别的P**
 * 上的路径：
 *
 * 1. **切 P/换集时没有立刻清空旧片段表**：`doSwitchPage()` 只把 `viewPoints` 置空，
 *    `skipSegments` 还是上一P 的。新一P 起播到上一P 的片头秒数时，250ms 定时器照样命中
 *    → **在新的一P 里凭空跳一下**。用户看到的就是"因为视频分段（分P）也跳过"。
 * 2. **迟到的 `view_points` 响应会覆盖当前集**：`loadViewPoints()` 在后台线程请求、
 *    回主线程**无条件**写片段表，换 P 期间上一P 的响应一到就把新表的语境换掉。
 * 3. **重复拉取同一批片段不得清空已处理集合**（26.10.22「乱跳」的成因，判据在
 *    [SkipCancelPolicy.segmentsChanged]）——这条语义要保住，不能因为重构又变成无条件清空。
 *
 * 本类把「片段表 + 已处理集合 + 在途请求代次」收进一处，只暴露四个动作：
 * [reset]（换P/换集）、[beginLoad]/[isStale]（迟到响应隔离）、[applyLoaded]（应用结果）、
 * [decide]/[onUserSeek]（判定与用户反悔）。判定与取样仍然分离：本类只回答"该不该跳"，
 * 真正的 `seekTo` 由播放页完成。
 */
class SkipDecider {

    /**
     * 一次 `view_points` 拉取的令牌。
     *
     * @param aid 发起拉取时的 aid
     * @param cid 发起拉取时的 cid（**分P 的唯一标识**，换P 必然变）
     * @param generation 发起拉取时的代次（[reset] 与新的拉取都会让它 +1）
     */
    data class LoadToken(val aid: Long, val cid: Long, val generation: Long)

    /** 代次：主线程 [reset] 与后台线程 [beginLoad] 都会写，必须原子。 */
    private val generation = AtomicLong(0L)

    /** 当前这一集/这一P 的可跳片段（由 `ViewPointSkip.buildSegments` 排好序）。 */
    var segments: List<ViewPointSkip.Segment> = emptyList()
        private set

    /** 已处理过的片段 key：自动跳过一次、或用户手动拖进去过，都不再重复跳。 */
    private val handled = HashSet<String>()

    /** 最近一次自动跳过的片段，供「撤回」回到原位；跳完即置位，撤回/换P 后清空。 */
    var lastSkipped: ViewPointSkip.Segment? = null
        private set

    /**
     * 换 P / 换集：**立刻**丢弃旧片段表与已处理集合，并让所有在途请求作废。
     *
     * 必须在新一P 开始播之前调用（`doSwitchPage`）。只置空 `viewPoints` 不置空片段表是最初的 bug。
     */
    fun reset() {
        generation.incrementAndGet()
        segments = emptyList()
        handled.clear()
        lastSkipped = null
    }

    /** 发起一次拉取：记下发起时属于哪个 aid/cid，并占一个新的代次。 */
    fun beginLoad(aid: Long, cid: Long): LoadToken =
        LoadToken(aid, cid, generation.incrementAndGet())

    /**
     * 响应是否已经过期（期间换过 P/集、或又发起了新的拉取）。
     *
     * 过期就**整条丢弃**：既不能写片段表，也不能清空已处理集合。
     */
    fun isStale(token: LoadToken, currentAid: Long, currentCid: Long): Boolean =
        token.generation != generation.get() || token.aid != currentAid || token.cid != currentCid

    /**
     * 应用一次**未过期**的拉取结果。
     *
     * 只有片段内容真的变了才清空已处理集合（换集/换P/上游改了片段）；
     * 同一次播放里重复拉到同一批片段（迟到的重复响应、投屏恢复）必须保住"已处理"，
     * 否则片头会被再跳一次——判据复用 [SkipCancelPolicy.segmentsChanged]。
     */
    fun applyLoaded(newSegments: List<ViewPointSkip.Segment>) {
        if (SkipCancelPolicy.segmentsChanged(segments, newSegments)) {
            handled.clear()
            lastSkipped = null
        }
        segments = newSegments
    }

    /**
     * 当前位置是否应当自动跳过；返回需要跳过的片段并**立刻标记为已处理**，不需要则返回 null。
     *
     * 开关判定留给调用方（`PlayerActivity.skipOpEdEnabled()`）：本类不读存档。
     */
    fun decide(positionSec: Double): ViewPointSkip.Segment? {
        val segment = ViewPointSkip.shouldSkip(positionSec, segments, handled) ?: return null
        handled.add(ViewPointSkip.keyOf(segment))
        lastSkipped = segment
        return segment
    }

    /**
     * 用户主动跳转（拖进度条 / 快进快退 / 点看点）落在片段内时，把该片段记为已处理。
     *
     * 用户主动拖进片头说明他就是想看看，不能被下一轮定时器又弹走；
     * 自动跳过自己调用时**不要**走这里（落点在区间右端点之外，本来也不会命中）。
     *
     * @return 本次是否真的标记了（false = 落在片段外或已处理过）
     */
    fun onUserSeek(positionMs: Long): Boolean {
        val key = ViewPointSkip.keyForManualSeek(positionMs / 1000.0, segments, handled) ?: return false
        handled.add(key)
        // 用户自己动过进度，之前那条「撤回」就过期了：别点一下把人送回更早的位置
        lastSkipped = null
        return true
    }

    /** [segment] 是否仍然是"可撤回"的那一次跳过。 */
    fun isUndoValid(segment: ViewPointSkip.Segment): Boolean = lastSkipped === segment

    /** 撤回后清掉撤回位（片段仍在已处理集合里，不会立刻又被跳）。 */
    fun clearUndo() {
        lastSkipped = null
    }
}
