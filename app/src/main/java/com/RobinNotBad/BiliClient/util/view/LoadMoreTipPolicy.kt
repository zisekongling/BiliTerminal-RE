package com.RobinNotBad.BiliClient.util.view

/**
 * 列表底部状态条（布局里的 `loadMoreTip`）的显示策略。
 *
 * <h3>为什么要有这个东西</h3>
 * 这个条是**列表外的兄弟节点**（`activity_simple_refresh.xml` / `activity_simple_main_refresh.xml`
 * 里排在 `SwipeRefreshLayout` 下面），钉在屏幕底部、不随列表滚走，也不会被列表滚出去。
 * 以前「没有更多了」是**常驻**的：只要哪一页拿到「没有下一页」的信号（收藏夹条目不足一页、
 * 订阅列表为空、推荐流翻到头……），屏幕底下就永久多出一条，用户看到的就是
 * 「没有更多了 横幅一直固定在推荐和收藏夹底部，不会隐藏」（26.11.06 反馈）。
 *
 * 现在的口径：
 * - 「正在加载…」在加载期间保持，加载结束由基类的 `setRefreshing(false)` 收掉；
 * - 「没有更多了」改成**一次性提示**：显示 [END_VISIBLE_MS] 毫秒后自己收起，不占位。
 *
 * <h3>为什么判断单独抽成纯函数</h3>
 * View 那一层（`postDelayed` / `visibility`）在 JVM 单测里全是桩，断言写了也是假的。
 * 真正容易写错的是「哪个状态优先、哪个状态要自动收」，所以把那部分放在这里，
 * 由 `LoadMoreTipPolicyTest` 钉住；`LoadMoreTipController` 只做机械翻译。
 */
object LoadMoreTipPolicy {

    /** 状态条的三种状态。 */
    enum class State {
        /** 不显示、不占位。 */
        HIDDEN,

        /** 「正在加载…」：加载期间一直显示。 */
        LOADING,

        /** 「没有更多了」：显示一小会儿就收起。 */
        END
    }

    /** 「没有更多了」显示多久后自动收起（毫秒）。 */
    const val END_VISIBLE_MS = 2000L

    /**
     * 由调用方的两个标志算出该显示什么。
     *
     * `end` 优先于 `loading`：加载结束时调用方常同时传 `loading=false, end=bottom`，
     * 而「到底了」是比「正在加载」更具体的信息；两者不会同时为真的正常路径下，
     * 这个优先级只影响 `setRefreshing(true)` 期间 `bottom` 仍为真的边角情况。
     */
    @JvmStatic
    fun stateOf(loading: Boolean, end: Boolean): State = when {
        end -> State.END
        loading -> State.LOADING
        else -> State.HIDDEN
    }

    /** 该状态是否要占位（`View.VISIBLE`）；[State.HIDDEN] 不占位。 */
    @JvmStatic
    fun isVisible(state: State): Boolean = state != State.HIDDEN

    /**
     * 该状态是否要起一个「到点自动收起」的定时器。
     *
     * 只有 [State.END] 是；[State.LOADING] 的结束由加载回调驱动，不能按时间收掉，
     * 否则请求慢的时候用户会看到「正在加载…」闪一下就没，以为列表卡死。
     */
    @JvmStatic
    fun autoHides(state: State): Boolean = state == State.END
}
