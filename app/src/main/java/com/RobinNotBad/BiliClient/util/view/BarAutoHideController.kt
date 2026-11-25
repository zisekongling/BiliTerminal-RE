package com.RobinNotBad.BiliClient.util.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * 顶部工具条的「滚动自动收回」。
 *
 * <h3>为什么不能靠 RecyclerView 自己把条带走</h3>
 * activity_simple_refresh / activity_simple_main_refresh 里的 filterBar / sortBar / manageBar
 * 都是列表的**兄弟节点**、位于 SwipeRefreshLayout 之上，不是列表项（做成列表项会重映射业务
 * adapter 的 viewType 并改变 adapterPosition 语义）。所以它们不会随列表滚动，只能在滚动回调里
 * 手动把 `layoutParams.height` 收到 0。手表屏本来就小，收起来能多露出一整行。
 *
 * <h3>为什么抽成独立类</h3>
 * 这套逻辑原先只写在 RefreshListActivity（二级列表页）里，而一级列表页走的是
 * RefreshMainActivity，两者没有继承关系，于是「菜单一级页 + 工具条」这种页面（如稍后再看）
 * 拿不到该能力。把状态与动画搬到这里，两个基类各持一个实例即可，行为完全一致，
 * 也避免以后两份实现各自漂移。
 */
class BarAutoHideController(private val recyclerView: RecyclerView) {
    companion object {
        /** 工具条收回/展开的动画时长（毫秒）。手表屏小，收放要快，拖久了像卡顿 */
        private const val BAR_ANIM_MS = 180L
    }

    /**
     * 参与自动收回的工具条，由 [setup] 指定。
     *
     * 默认是空表，也就是**不启用**：这两个布局被大量列表页共用（设置页、历史、下载、关注……），
     * 默认给所有页面加滚动收起是不合适的。
     */
    private val bars = ArrayList<View>()

    /**
     * 各条的自然高度。
     *
     * 这些条都是 `wrap_content`，收回时被压成 0，而 0 高度量不出「原本多高」，
     * 所以必须在它们还正常显示的时候把高度记下来，否则收得回去、展不回来。
     */
    private val barHeight = HashMap<View, Int>()

    /** 工具条当前是否处于收回状态。 */
    private var collapsed = false

    /** 正在跑的收回/展开动画；新手势到来时先取消，避免两条动画抢同一个 height。 */
    private var animator: ValueAnimator? = null

    /** 被折叠掉的条。只有这些需要展开——其它条可能是自己 GONE 的，不能替它们做主。 */
    private val collapsedBars = ArrayList<View>()

    /** 同向累计的滚动量，交给 [ScrollRetractDecider] 判断，避免手抖就来回闪。 */
    private var scrollAccum = 0

    /**
     * 指定参与自动收回的条。
     *
     * 在 onCreate 里把目标条设成 VISIBLE 之后调用，例如 `setup(findViewById(R.id.filterBar))`。
     * **不调用就完全没有这套行为。** 多条可以一起传；注意别把「用户正在用的操作条」放进来
     * （收藏夹的多选条就没放——正选着视频呢，条自己收走了就没法点删除了）。
     */
    fun setup(vararg targets: View?) {
        bars.clear()
        bars.addAll(targets.filterNotNull())
    }

    /**
     * 立即展开工具条（不带动画）。
     *
     * 用于「切换筛选/排序」这类会把列表重新拉回第一页的动作：列表回到顶部后，用户已经没有
     * 再往上滚的余地，靠滚动事件把条带回来是不可靠的（可能一直不产生滚动事件）。
     * 幂等，没收回时调用也无害。
     */
    fun expand() {
        animator?.cancel()
        animator = null
        bars.forEach { resetHeight(it) }
        collapsedBars.clear()
        collapsed = false
        scrollAccum = 0
    }

    /**
     * 由列表的 onScrolled 转发垂直滚动量。
     *
     * 必须在主线程调用（RecyclerView 的滚动回调本来就在主线程）。
     */
    fun handleScroll(dy: Int) {
        if (bars.isEmpty()) return
        scrollAccum += dy
        when (ScrollRetractDecider.action(
            accumulated = scrollAccum,
            collapsed = collapsed,
            canScrollUp = recyclerView.canScrollVertically(-1)
        )) {
            ScrollRetractDecider.COLLAPSE -> {
                scrollAccum = 0
                animate(collapse = true)
            }

            ScrollRetractDecider.EXPAND -> {
                scrollAccum = 0
                animate(collapse = false)
            }
        }
    }

    /**
     * 收回或展开。
     *
     * 多条用一个 [ValueAnimator] 一起推：每条各自起一个动画的话，条与条之间会因为启动时刻
     * 微差而错位，看起来像抽搐。高度按同一条 0..1 的进度算，天然同步。
     */
    private fun animate(collapse: Boolean) {
        if (collapse == this.collapsed) return

        // 收回只动当前真正可见的条：不可见的（如只读收藏夹的多选条、非稍后再看页的
        // 清除按钮）高度是 0，收它没有意义，还会把它算进 collapsedBars 里，
        // 让展开时把本该 GONE 的条点亮。
        val targets = if (collapse) {
            bars.filter { it.visibility == View.VISIBLE && naturalHeight(it) > 0 }
        } else {
            ArrayList(collapsedBars)
        }
        if (targets.isEmpty()) return

        // 先取消再置空：cancel() 会同步回调旧动画的 onAnimationEnd，
        // 那时 animator 若还指着旧动画，就把 height 复位成 WRAP_CONTENT 了。
        animator?.cancel()
        animator = null

        if (!collapse) {
            // 展开时必须从「可见 + 0 高度」起步，否则条会从 GONE 直接跳到满高，没有动画
            targets.forEach { bar ->
                bar.visibility = View.VISIBLE
                bar.alpha = 0f
                setHeight(bar, 0)
            }
        }

        this.collapsed = collapse
        val valueAnimator = ValueAnimator.ofFloat(
            if (collapse) 0f else 1f,
            if (collapse) 1f else 0f
        )
        valueAnimator.duration = BAR_ANIM_MS
        valueAnimator.addUpdateListener { animation ->
            val fraction = animation.animatedValue as Float
            targets.forEach { bar ->
                setHeight(bar, (naturalHeight(bar) * (1f - fraction)).toInt())
                bar.alpha = 1f - fraction
            }
        }
        valueAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // 被新手势取消掉的旧动画不要收尾，否则会把新状态改成旧的
                if (animator !== animation) return
                animator = null
                targets.forEach { resetHeight(it) }
                if (collapse) {
                    // 收完才真正 GONE，让布局把那部分高度让给列表
                    targets.forEach { it.visibility = View.GONE }
                    collapsedBars.clear()
                    collapsedBars.addAll(targets)
                } else {
                    collapsedBars.clear()
                }
            }
        })
        animator = valueAnimator
        valueAnimator.start()
    }

    /** 把条的高度改成指定值（保留其它 LayoutParams）。 */
    private fun setHeight(bar: View, height: Int) {
        val lp = bar.layoutParams ?: return
        lp.height = height
        bar.layoutParams = lp
    }

    /** 把条还原成 `wrap_content` + 完全不透明。 */
    private fun resetHeight(bar: View) {
        bar.alpha = 1f
        val lp = bar.layoutParams ?: return
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        bar.layoutParams = lp
    }

    /**
     * 条的自然高度。
     *
     * 只在条正常显示时量得到，所以第一次量到就缓存——之后它可能正被压成 0 高度。
     */
    private fun naturalHeight(bar: View): Int {
        val cached = barHeight[bar]
        if (cached != null && cached > 0) return cached
        val measured = if (bar.height > 0) bar.height else bar.measuredHeight
        if (measured > 0) barHeight[bar] = measured
        return measured
    }
}
