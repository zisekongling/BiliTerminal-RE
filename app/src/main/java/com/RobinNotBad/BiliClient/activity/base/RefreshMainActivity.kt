package com.RobinNotBad.BiliClient.activity.base

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.listener.OnLoadMoreListener
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.view.BarAutoHideController
import com.RobinNotBad.BiliClient.util.view.ImageAutoLoadScrollListener
import com.RobinNotBad.BiliClient.util.view.LoadMoreTipController

open class RefreshMainActivity : InstanceActivity() {
    lateinit var swipeRefreshLayout: SwipeRefreshLayout
    lateinit var recyclerView: RecyclerView
    var emptyView: TextView? = null

    /**
     * 列表底部"正在加载…／没有更多了"状态条（布局里的 loadMoreTip，不在列表项内）。
     *
     * 「没有更多了」是一次性提示（2 秒后自动收起），不是常驻横幅——它是列表外的兄弟节点，
     * 钉在屏幕底部不随列表滚走，常驻就变成用户反馈的「横幅一直固定在那里不会隐藏」。
     * 显示策略见 [com.RobinNotBad.BiliClient.util.view.LoadMoreTipPolicy]。
     */
    private var loadMoreTip: LoadMoreTipController? = null
    var listener: OnLoadMoreListener? = null
    // @Volatile：子类在 CenterThreadPool 后台线程写（如 DynamicActivity 的翻页回调），
    // 滚动监听却在主线程读，两者没有 happens-before 边，主线程可能长期读到陈旧的 false，
    // 表现为"到底后仍继续翻页"或"不显示『没有更多了』"。
    @Volatile
    var bottom: Boolean = false
    var page: Int = 1
    var lastLoadTimestamp: Long = 0
    protected var isRefreshing: Boolean = false

    /**
     * 滚动自动收回的实现（状态与动画）。
     *
     * 与 RefreshListActivity 共用同一个 [BarAutoHideController]：两个基类没有继承关系，
     * 逻辑只写一份才不会各自漂移。默认不启用，子类调 [setupAutoHideBars] 才生效。
     */
    private val barAutoHide: BarAutoHideController by lazy { BarAutoHideController(recyclerView) }

    /**
     * 本页用哪个列表布局。
     *
     * 默认是 activity_simple_main_refresh（无筛选/排序条的一级页布局）。需要工具条的一级页
     * （如稍后再看要筛选条）把它覆写成 activity_simple_refresh —— 那边多出来的 sortBar /
     * manageBar 等节点不 findViewById 就不会参与布局逻辑，而本基类要的
     * emptyTip / loadMoreTip / top / pageName / timeText 两边都有。
     */
    protected open fun contentLayoutRes(): Int = R.layout.activity_simple_main_refresh

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(contentLayoutRes())
        emptyView = findViewById(R.id.emptyTip)
        loadMoreTip = LoadMoreTipController(findViewById(R.id.loadMoreTip))
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        // 下拉刷新转圈此前用 SwipeRefreshLayout 默认色（与主题无关），统一到当前主题主色
        swipeRefreshLayout.setColorSchemeColors(ColorScheme.PRIMARY)
        swipeRefreshLayout.isEnabled = false
        swipeRefreshLayout.isRefreshing = true
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = getLayoutManager()
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(10)
        recyclerView.recycledViewPool.setMaxRecycledViews(0, 20)
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (listener != null && !recyclerView.canScrollVertically(1) && !swipeRefreshLayout.isRefreshing && newState == RecyclerView.SCROLL_STATE_DRAGGING && !bottom) {
                    goOnLoad()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                barAutoHide.handleScroll(dy)
                if (listener != null) {
                    val manager = recyclerView.layoutManager as LinearLayoutManager
                    val lastItemPosition = manager.findLastCompletelyVisibleItemPosition()
                    val itemCount = manager.itemCount
                    if (lastItemPosition >= (itemCount - 3) && dy > 0 && !swipeRefreshLayout.isRefreshing && !isRefreshing && !bottom) {
                        goOnLoad()
                    }
                }
            }
        })
        ImageAutoLoadScrollListener.installIfEnabled(recyclerView)
    }

    /**
     * 让顶部若干条工具条在列表滚动时自动收回，滚回顶部或往反方向滚时展开。
     *
     * 具体行为见 [BarAutoHideController]。**不调用就完全没有这套行为。**
     */
    fun setupAutoHideBars(vararg bars: View?) {
        barAutoHide.setup(*bars)
    }

    /**
     * 立即展开工具条（不带动画）。
     *
     * 用于「切换筛选/排序」这类会把列表重新拉回第一页的动作：列表回到顶部后靠滚动事件
     * 把条带回来不可靠（可能一直不产生滚动事件）。幂等，没收回时调用也无害。
     */
    fun expandAutoHideBars() {
        barAutoHide.expand()
    }

    fun setAdapter(adapter: RecyclerView.Adapter<*>) {
        // 不再包 ConcatAdapter（理由见 RefreshListActivity.setAdapter）；翻页状态由底部 loadMoreTip 呈现
        runOnUiThread { recyclerView.adapter = adapter }
    }

    /** 更新底部翻页状态提示；[loading]=true 显示"正在加载…"，[end]=true 显示"没有更多了"（2 秒后自动收起）。 */
    private fun updateLoadMoreTip(loading: Boolean, end: Boolean) {
        val tip = loadMoreTip ?: return
        runOnUiThread { tip.render(loading, end) }
    }

    override fun onDestroy() {
        // 撤掉还没到点的「没有更多了」自动收起，别让定时器把 Activity 多留 2 秒
        loadMoreTip?.release()
        super.onDestroy()
    }

    /**
     * 空数据提示。
     *
     * 一级页此前完全没有空态：`activity_simple_main_refresh.xml` 里虽然有 `@+id/emptyTip`，
     * 但基类从不 `findViewById` 它，于是"加载完成但没有内容"时用户看到的是纯黑列表，
     * 无法与"卡死"区分。这里补齐与 `RefreshListActivity` 一致的能力。
     */
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
     * 让空态可点击重试（与 [RefreshListActivity.setOnEmptyRetry] 同一套约定）。
     * 一级页此前空数据只能退出重进，用户也分不清是"没内容"还是"网络挂了"。
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

    fun setOnRefreshListener(listener: SwipeRefreshLayout.OnRefreshListener) {
        swipeRefreshLayout.setOnRefreshListener(listener)
        swipeRefreshLayout.isEnabled = true
    }

    @JvmName("setRefreshingState")
    fun setRefreshing(bool: Boolean) {
        runOnUiThread { swipeRefreshLayout.isRefreshing = bool }
        isRefreshing = bool
        if (!bool) {
            updateLoadMoreTip(loading = false, end = bottom)
        }
    }

    fun setOnLoadMoreListener(loadMore: OnLoadMoreListener) {
        listener = loadMore
    }

    private fun goOnLoad() {
        val loadMore = listener ?: return
        synchronized(this) {
            val timeCurrent = System.currentTimeMillis()
            if (timeCurrent - lastLoadTimestamp > 100) {
                // 必须同时置成员 isRefreshing：onScrolled 用它做防重入，
                // 旧代码只置 UI 状态，滚动持续触发并发 onLoad 导致 offset/freshType 竞态
                isRefreshing = true
                updateLoadMoreTip(loading = true, end = false)
                swipeRefreshLayout.isRefreshing = true
                page++
                loadMore.onLoad(page)
                lastLoadTimestamp = timeCurrent
            }
        }
    }

    fun loadFail() {
        page--
        MsgUtil.showMsgLong(getString(R.string.text_load_failed))
        setRefreshing(false)
    }

    fun loadFail(e: Exception) {
        page--
        report(e)
        setRefreshing(false)
    }
}