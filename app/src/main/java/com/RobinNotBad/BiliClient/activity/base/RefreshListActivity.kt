package com.RobinNotBad.BiliClient.activity.base

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter
import com.RobinNotBad.BiliClient.listener.OnLoadMoreListener
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.PerformanceManager
import com.RobinNotBad.BiliClient.util.view.BarAutoHideController
import com.RobinNotBad.BiliClient.util.view.ImageAutoLoadScrollListener
import com.RobinNotBad.BiliClient.util.view.LoadMoreTipController

open class RefreshListActivity : BaseActivity() {
    lateinit var swipeRefreshLayout: SwipeRefreshLayout
    lateinit var recyclerView: RecyclerView
    var emptyView: TextView? = null

    /**
     * 列表底部"正在加载…／没有更多了"状态条（布局里的 loadMoreTip，不在列表项内）。
     *
     * 「没有更多了」是一次性提示（2 秒后自动收起），不是常驻横幅——它是列表外的兄弟节点，
     * 钉在屏幕底部不随列表滚走，常驻就变成用户反馈的「横幅一直固定在那里不会隐藏」
     * （收藏夹条目不足一页、订阅列表为空这类「第一页就到底」的页面最明显）。
     * 显示策略见 [com.RobinNotBad.BiliClient.util.view.LoadMoreTipPolicy]。
     */
    private var loadMoreTip: LoadMoreTipController? = null
    var listener: OnLoadMoreListener? = null

    /**
     * 是否已到列表底部（没有更多数据）。
     *
     * 子类在网络回调（[com.RobinNotBad.BiliClient.util.CenterThreadPool] 的后台线程）里赋值，
     * 而 [onCreate] 注册的滚动监听在**主线程**读它来决定要不要继续翻页。普通 `Boolean` 字段
     * 没有 happens-before 边，主线程可能长期读到陈旧的 false，表现为"到底了还无限翻页"或
     * "到底了不提示没有更多了"。故必须 @Volatile 保证可见性。
     */
    @Volatile
    var bottom: Boolean = false
    var page: Int = 1
    var lastLoadTimestamp: Long = 0
    private var isLoading: Boolean = false

    // ---------- 顶部工具条的「滚动自动收回」 ----------

    /**
     * 滚动自动收回的实现（状态与动画）。抽成独立类是为了让一级列表页 RefreshMainActivity
     * 也有同一套能力——两个基类没有继承关系，逻辑只写一份才不会各自漂移。
     */
    private val barAutoHide: BarAutoHideController by lazy { BarAutoHideController(recyclerView) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_refresh)
        emptyView = findViewById(R.id.emptyTip)
        loadMoreTip = LoadMoreTipController(findViewById(R.id.loadMoreTip))
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        // 下拉刷新转圈此前用 SwipeRefreshLayout 默认色（与主题无关），统一到当前主题主色
        swipeRefreshLayout.setColorSchemeColors(ColorScheme.PRIMARY)
        swipeRefreshLayout.isEnabled = false
        swipeRefreshLayout.isRefreshing = true
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.setHasFixedSize(true)

        // 根据设备性能动态设置缓存大小，替代废弃的drawing cache
        val cacheSize = PerformanceManager.getRecyclerViewCacheSize()
        recyclerView.setItemViewCacheSize(cacheSize)

        // 使用RecycledViewPool共享ViewHolder池以减少内存分配
        val viewPool = androidx.recyclerview.widget.RecyclerView.RecycledViewPool()
        recyclerView.setRecycledViewPool(viewPool)

        recyclerView.layoutManager = getLayoutManager()
        ImageAutoLoadScrollListener.installIfEnabled(recyclerView)

        // 设置GAP Worker预加载（Android 5.0+）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            val prefetchCount = PerformanceManager.getRecyclerViewPrefetchCount()
            (recyclerView.layoutManager as? LinearLayoutManager)?.initialPrefetchItemCount = prefetchCount
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    checkLoadMore()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                barAutoHide.handleScroll(dy)
                if (dy > 0 && !swipeRefreshLayout.isRefreshing) {
                    checkLoadMore()
                }
            }

            private fun checkLoadMore() {
                if (listener == null || bottom || isLoading || swipeRefreshLayout.isRefreshing) {
                    return
                }
                val manager = recyclerView.layoutManager as LinearLayoutManager?
                if (manager == null) {
                    return
                }
                val lastVisiblePosition = manager.findLastVisibleItemPosition()
                val itemCount = manager.itemCount
                if (lastVisiblePosition >= (itemCount - 4)) {
                    goOnLoad()
                }
            }
        })
    }

    /**
     * 让顶部若干条工具条在列表滚动时自动收回，滚回顶部或往反方向滚时展开。
     *
     * 具体行为见 [BarAutoHideController]。**不调用就完全没有这套行为。** 多条可以一起传；
     * 注意别把「用户正在用的操作条」放进来（收藏夹的多选条就没放——正选着视频呢，
     * 条自己收走了就没法点删除了）。
     */
    fun setupAutoHideBars(vararg bars: View?) {
        barAutoHide.setup(*bars)
    }

    /**
     * 立即展开工具条（不带动画）。
     *
     * 用于「切换筛选/排序」这类会把列表重新拉回第一页的动作：列表回到顶部后，用户已经没有
     * 再往上滚的余地，靠滚动事件把条带回来是不可靠的（可能一直不产生滚动事件）。
     * 幂等，没收回时调用也无害。
     */
    fun expandAutoHideBars() {
        barAutoHide.expand()
    }

    fun setAdapter(adapter: RecyclerView.Adapter<*>) {
        // 不再包 ConcatAdapter：那会重映射业务 adapter 的 viewType，
        // 使用负数 viewType 的 adapter（如 SettingsAdapter）会错配 Holder 直接崩；
        // 翻页状态改由底部 loadMoreTip 呈现（见 updateLoadMoreTip）。
        runOnUiThread { recyclerView.adapter = adapter }
    }

    /**
     * 更新底部翻页状态提示；[loading]=true 显示"正在加载…"，[end]=true 显示"没有更多了"（2 秒后自动收起）。
     *
     * 文案与显示策略收在 [LoadMoreTipController] / [com.RobinNotBad.BiliClient.util.view.LoadMoreTipPolicy]：
     * 两个列表基类没有继承关系，以前各写一份同样的 `when`，改一处必漏一处。
     */
    private fun updateLoadMoreTip(loading: Boolean, end: Boolean) {
        val tip = loadMoreTip ?: return
        runOnUiThread { tip.render(loading, end) }
    }

    override fun onDestroy() {
        // 撤掉还没到点的「没有更多了」自动收起，别让定时器把 Activity 多留 2 秒
        loadMoreTip?.release()
        super.onDestroy()
    }

    fun setOnRefreshListener(listener: SwipeRefreshLayout.OnRefreshListener) {
        swipeRefreshLayout.setOnRefreshListener(listener)
        swipeRefreshLayout.isEnabled = true
    }

    fun showEmptyView() {
        emptyView?.let {
            runOnUiThread {
                recyclerView.visibility = View.GONE
                it.visibility = View.VISIBLE
            }
        }
    }

    fun hideEmptyView() {
        emptyView?.let {
            runOnUiThread {
                recyclerView.visibility = View.VISIBLE
                it.visibility = View.GONE
            }
        }
    }

    /**
     * 让空态可点击重试。
     *
     * 空态此前只是一行"啥都木有~"，用户无法区分"加载完了但没内容"和"网络挂了"，
     * 也没有任何恢复手段（只能退出重进）。调用本方法后空态会多一行提示并可点击重试。
     */
    fun setOnEmptyRetry(action: Runnable) {
        emptyView?.let { ev ->
            ev.isClickable = true
            ev.text = getString(R.string.text_list_empty_retry)
            ev.setOnClickListener {
                hideEmptyView()
                setRefreshing(true)
                action.run()
            }
        }
    }

    fun setRefreshing(bool: Boolean) {
        // 复位刷新状态时同步结束"加载更多"占用（isLoading）。
        // 子类加载完成的唯一统一信号就是 setRefreshing(false)，此前只有显式调
        // onLoadComplete() 的 3 个页面能恢复翻页，其余页面第一次加载更多后即永久卡死。
        if (!bool) {
            isLoading = false
            updateLoadMoreTip(loading = false, end = bottom)
        }
        runOnUiThread { swipeRefreshLayout.isRefreshing = bool }
    }

    /**
     * 全局搜索跳转时，滚动定位到名称为 [highlight] 的设置项，并让目标行闪一次高亮。
     *
     * **定位口径：目标行顶到列表顶端**（紧贴顶栏下沿）。不能直接用
     * [RecyclerView.scrollToPosition]：它只保证目标行「刚好看得见」，而跳转进来时列表都在
     * 最顶上，目标行永远在下方，于是每次都贴在**屏幕底部**，用户还得自己再往上找。
     * 这里改用 [LinearLayoutManager.scrollToPositionWithOffset]（offset = 0），让目标行的
     * 顶边对齐列表顶边。目标行后面剩余的内容不足一屏时滚不到那么远，会停在能滚的最大位置
     * （目标行尽可能靠上）——这是 RecyclerView 的固有边界，不是没生效。
     *
     * 高亮只在 [SettingsAdapter] 上有意义（别的列表没有「设置项」这个概念），所以拿不到
     * 该类型适配器时只滚动、不标红，不报错。
     */
    fun scrollToHighlight(list: List<SettingSection>, highlight: String?) {
        if (highlight.isNullOrEmpty()) return
        val index = list.indexOfFirst { it.name == highlight }
        if (index < 0) return
        // 子类都是「setAdapter 之后再调本方法」，post 里 recyclerView.adapter 一定已就位
        recyclerView.post {
            (recyclerView.adapter as? SettingsAdapter)?.highlight(index)
            // 设置页在竖屏/Landscape 下都是 LinearLayoutManager 子类（CustomLinearManager /
            // CustomGridManager），offset 0 = 目标行顶边贴着列表顶边；万一换了别的布局管理器
            // 就退回「刚好看得见」，至少还能定位到。
            val manager = recyclerView.layoutManager as? LinearLayoutManager
            if (manager != null) manager.scrollToPositionWithOffset(index, 0)
            else recyclerView.scrollToPosition(index)
        }
    }

    fun setOnLoadMoreListener(loadMore: OnLoadMoreListener) {
        listener = loadMore
    }

    private fun goOnLoad() {
        val loadMore = listener ?: return
        val timeCurrent = System.currentTimeMillis()
        if (timeCurrent - lastLoadTimestamp > 500) {
            isLoading = true
            updateLoadMoreTip(loading = true, end = false)
            swipeRefreshLayout.isRefreshing = true
            page++
            loadMore.onLoad(page)
            lastLoadTimestamp = timeCurrent
        }
    }

    fun onLoadComplete() {
        isLoading = false
    }

    fun loadFail() {
        isLoading = false
        page--
        MsgUtil.showMsgLong(getString(R.string.text_load_failed))
        setRefreshing(false)
    }

    fun loadFail(e: Exception) {
        isLoading = false
        page--
        report(e)
        setRefreshing(false)
    }
}