package com.RobinNotBad.BiliClient.activity.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.annotation.NonNull
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.listener.OnLoadMoreListener
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomGridManager
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.view.ImageAutoLoadScrollListener

open class SearchFragment : Fragment() {
    lateinit var swipeRefreshLayout: SwipeRefreshLayout
    lateinit var recyclerView: RecyclerView
    var emptyView: TextView? = null
    var listener: OnLoadMoreListener? = null
    var refreshListener: SwipeRefreshLayout.OnRefreshListener? = null
    var keyword: String? = null
    var bottom: Boolean = false
        set(value) {
            field = value
            if (page == 1) showEmptyView(value)
            else if (value && isAdded) {
                MsgUtil.showMsg("已经到底啦OwO")
            }
        }
    var page = 1
    var lastLoadTimestamp: Long = 0
    var refreshable = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_simple_refresh, container, false)
    }

    override fun onViewCreated(@NonNull view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        emptyView = view.findViewById(R.id.emptyTip)

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.isEnabled = false

        recyclerView = view.findViewById(R.id.recyclerView)
        val layoutManager = getLayoutManager()
        recyclerView.layoutManager = layoutManager
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(20)
        recyclerView.isNestedScrollingEnabled = true

        // 启用预取优化，提前加载即将进入屏幕的 item，减少滑动时 onBindViewHolder 的等待
        (layoutManager as? LinearLayoutManager)?.let {
            it.initialPrefetchItemCount = 4
            it.isItemPrefetchEnabled = true
        }

        val searchActivity = if (requireActivity() is SearchActivity) requireActivity() as SearchActivity else null
        val linearLayoutManager = layoutManager as? LinearLayoutManager

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            override fun onScrollStateChanged(@NonNull recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    if (!recyclerView.canScrollVertically(-1)) {
                        searchActivity?.onScrolled(-114)
                    }
                }
            }

            override fun onScrolled(@NonNull recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (dy > 0 && !swipeRefreshLayout.isRefreshing && !bottom && listener != null) {
                    linearLayoutManager?.let { manager ->
                        val lastItemPosition = manager.findLastVisibleItemPosition()
                        val itemCount = manager.itemCount
                        if (lastItemPosition >= itemCount - 3) {
                            goOnLoad()
                        }
                    }
                }

                searchActivity?.onScrolled(dy)
            }
        })
        ImageAutoLoadScrollListener.installIfEnabled(recyclerView)
    }

    fun setAdapter(adapter: RecyclerView.Adapter<*>) {
        runOnUiThread { recyclerView.adapter = adapter }
    }

    fun setOnRefreshListener(listener: SwipeRefreshLayout.OnRefreshListener) {
        this.refreshListener = listener
    }

    fun setRefreshing(bool: Boolean) {
        runOnUiThread { swipeRefreshLayout.isRefreshing = bool }
    }

    fun setOnLoadMoreListener(loadMore: OnLoadMoreListener) {
        listener = loadMore
    }

    private fun goOnLoad() {
        val timeCurrent = System.currentTimeMillis()
        if (timeCurrent - lastLoadTimestamp > 100) {
            swipeRefreshLayout.isRefreshing = true
            page++
            listener!!.onLoad(page)
            lastLoadTimestamp = timeCurrent
        }
    }

    fun runOnUiThread(runnable: Runnable) {
        if (isAdded) requireActivity().runOnUiThread(runnable)
    }

    fun showEmptyView(empty: Boolean) {
        if (emptyView != null) {
            runOnUiThread { emptyView!!.visibility = if (empty) View.VISIBLE else View.GONE }
        }
    }

    fun isRefreshing(): Boolean {
        if (swipeRefreshLayout != null) return swipeRefreshLayout.isRefreshing
        return false
    }

    fun report(e: Throwable) {
        MsgUtil.err(e)
    }

    fun loadFail() {
        page--
        MsgUtil.showMsgLong("加载失败")
        setRefreshing(false)
    }

    fun loadFail(e: Throwable) {
        page--
        report(e)
        setRefreshing(false)
    }

    fun getLayoutManager(): RecyclerView.LayoutManager {
        return if (SharedPreferencesUtil.getBoolean("ui_landscape", false))
            CustomGridManager(requireContext(), 3)
        else
            CustomLinearManager(requireContext())
    }

    fun update(keyword: String) {
        this.page = 1
        this.keyword = keyword
        this.refreshable = true
        bottom = false
    }

    fun refresh() {
        if (!refreshable) return
        refreshable = false
        setRefreshing(true)
        refreshListener!!.onRefresh()
    }

    protected open fun refreshInternal() {
    }
}