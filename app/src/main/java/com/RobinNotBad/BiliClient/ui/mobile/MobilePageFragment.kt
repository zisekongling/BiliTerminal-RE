package com.RobinNotBad.BiliClient.ui.mobile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.util.view.ImageAutoLoadScrollListener

/**
 * 移动端通用频道内容页Fragment基类
 * 提供双列瀑布流RecyclerView + 下拉刷新 + 加载更多的基础设施
 */
abstract class MobilePageFragment : Fragment() {

    protected lateinit var recyclerView: RecyclerView
    protected lateinit var swipeRefreshLayout: SwipeRefreshLayout
    protected lateinit var emptyTip: TextView
    protected var isLoading = false
    protected var bottom = false
    protected var page = 1
    private var lastLoadTimestamp = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_mobile_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefresh)
        emptyTip = view.findViewById(R.id.emptyTip)

        // 双列网格布局
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(10)
        recyclerView.recycledViewPool.setMaxRecycledViews(0, 20)
        recyclerView.itemAnimator = null

        swipeRefreshLayout.setOnRefreshListener { refreshData() }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val manager = recyclerView.layoutManager as LinearLayoutManager
                val lastItemPosition = manager.findLastCompletelyVisibleItemPosition()
                val itemCount = manager.itemCount
                if (lastItemPosition >= (itemCount - 3) && dy > 0 && !swipeRefreshLayout.isRefreshing && !isLoading && !bottom) {
                    loadMore()
                }
            }
        })
        ImageAutoLoadScrollListener.install(recyclerView)

        refreshData()
    }

    /**
     * 下拉刷新，子类重写
     */
    protected open fun refreshData() {
        page = 1
        bottom = false
        loadData()
    }

    /**
     * 加载更多
     */
    private fun loadMore() {
        synchronized(this) {
            val timeCurrent = System.currentTimeMillis()
            if (timeCurrent - lastLoadTimestamp > 100) {
                page++
                loadData()
                lastLoadTimestamp = timeCurrent
            }
        }
    }

    /**
     * 加载数据，子类必须实现
     */
    protected abstract fun loadData()

    /**
     * 设置刷新状态
     */
    protected fun setRefreshing(refreshing: Boolean) {
        isLoading = refreshing
        swipeRefreshLayout.isRefreshing = refreshing
    }

    /**
     * 加载失败处理
     */
    protected fun loadFail() {
        page--
        setRefreshing(false)
    }
}