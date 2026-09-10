package com.RobinNotBad.BiliClient.activity.base

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.listener.OnLoadMoreListener
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.PerformanceManager
import com.RobinNotBad.BiliClient.util.view.ImageAutoLoadScrollListener

open class RefreshListActivity : BaseActivity() {
    companion object {
        /** 空态文案（可重试时多一行提示） */
        const val EMPTY_TEXT = "啥都木有~"
        const val EMPTY_TEXT_WITH_RETRY = "啥都木有~\n点我重试"
    }
    lateinit var swipeRefreshLayout: SwipeRefreshLayout
    lateinit var recyclerView: RecyclerView
    var emptyView: TextView? = null

    /** 列表底部"正在加载…／没有更多了"状态条（布局里的 loadMoreTip，不在列表项内） */
    private var loadMoreTip: TextView? = null
    var listener: OnLoadMoreListener? = null
    var bottom: Boolean = false
    var page: Int = 1
    var lastLoadTimestamp: Long = 0
    private var isLoading: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_refresh)
        emptyView = findViewById(R.id.emptyTip)
        loadMoreTip = findViewById(R.id.loadMoreTip)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        // 下拉刷新转圈此前用 SwipeRefreshLayout 默认色（与主题无关），统一到当前主题主色
        swipeRefreshLayout.setColorSchemeColors(ThemeManager.PRIMARY)
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
        ImageAutoLoadScrollListener.install(recyclerView)

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

    fun setAdapter(adapter: RecyclerView.Adapter<*>) {
        // 不再包 ConcatAdapter：那会重映射业务 adapter 的 viewType，
        // 使用负数 viewType 的 adapter（如 SettingsAdapter）会错配 Holder 直接崩；
        // 翻页状态改由底部 loadMoreTip 呈现（见 updateLoadMoreTip）。
        runOnUiThread { recyclerView.adapter = adapter }
    }

    /** 更新底部翻页状态提示；[loading]=true 显示"正在加载…"，[end]=true 显示"没有更多了"。 */
    private fun updateLoadMoreTip(loading: Boolean, end: Boolean) {
        val tip = loadMoreTip ?: return
        runOnUiThread {
            tip.text = when {
                end -> "没有更多了"
                loading -> "正在加载…"
                else -> ""
            }
            tip.visibility = if (end || loading) View.VISIBLE else View.GONE
        }
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
            ev.text = EMPTY_TEXT_WITH_RETRY
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

    /** 全局搜索跳转时，滚动定位到名称为 [highlight] 的设置项。 */
    fun scrollToHighlight(list: List<SettingSection>, highlight: String?) {
        if (highlight.isNullOrEmpty()) return
        val index = list.indexOfFirst { it.name == highlight }
        if (index >= 0) {
            recyclerView.post { recyclerView.scrollToPosition(index) }
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
        MsgUtil.showMsgLong("加载失败")
        setRefreshing(false)
    }

    fun loadFail(e: Exception) {
        isLoading = false
        page--
        report(e)
        setRefreshing(false)
    }
}