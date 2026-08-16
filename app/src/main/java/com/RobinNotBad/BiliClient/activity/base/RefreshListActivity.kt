package com.RobinNotBad.BiliClient.activity.base

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.listener.OnLoadMoreListener
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.PerformanceManager
import com.RobinNotBad.BiliClient.util.view.ImageAutoLoadScrollListener

open class RefreshListActivity : BaseActivity() {
    lateinit var swipeRefreshLayout: SwipeRefreshLayout
    lateinit var recyclerView: RecyclerView
    var emptyView: TextView? = null
    var listener: OnLoadMoreListener? = null
    var bottom: Boolean = false
    var page: Int = 1
    var lastLoadTimestamp: Long = 0
    private var isLoading: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_refresh)
        emptyView = findViewById(R.id.emptyTip)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
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
        runOnUiThread { recyclerView.adapter = adapter }
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

    fun setRefreshing(bool: Boolean) {
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
        val timeCurrent = System.currentTimeMillis()
        if (timeCurrent - lastLoadTimestamp > 500) {
            isLoading = true
            swipeRefreshLayout.isRefreshing = true
            page++
            listener!!.onLoad(page)
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