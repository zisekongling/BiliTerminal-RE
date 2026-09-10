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
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.view.ImageAutoLoadScrollListener

open class RefreshMainActivity : InstanceActivity() {
    lateinit var swipeRefreshLayout: SwipeRefreshLayout
    lateinit var recyclerView: RecyclerView
    var emptyView: TextView? = null

    /** 列表底部"正在加载…／没有更多了"状态条（布局里的 loadMoreTip，不在列表项内） */
    private var loadMoreTip: TextView? = null
    var listener: OnLoadMoreListener? = null
    var bottom: Boolean = false
    var page: Int = 1
    var lastLoadTimestamp: Long = 0
    protected var isRefreshing: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_main_refresh)
        emptyView = findViewById(R.id.emptyTip)
        loadMoreTip = findViewById(R.id.loadMoreTip)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        // 下拉刷新转圈此前用 SwipeRefreshLayout 默认色（与主题无关），统一到当前主题主色
        swipeRefreshLayout.setColorSchemeColors(ThemeManager.PRIMARY)
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
        ImageAutoLoadScrollListener.install(recyclerView)
    }

    fun setAdapter(adapter: RecyclerView.Adapter<*>) {
        // 不再包 ConcatAdapter（理由见 RefreshListActivity.setAdapter）；翻页状态由底部 loadMoreTip 呈现
        runOnUiThread { recyclerView.adapter = adapter }
    }

    /** 更新底部翻页状态提示。 */
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
            ev.text = RefreshListActivity.EMPTY_TEXT_WITH_RETRY
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
        MsgUtil.showMsgLong("加载失败")
        setRefreshing(false)
    }

    fun loadFail(e: Exception) {
        page--
        report(e)
        setRefreshing(false)
    }
}