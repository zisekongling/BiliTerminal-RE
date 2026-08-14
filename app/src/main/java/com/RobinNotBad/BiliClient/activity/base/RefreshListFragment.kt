package com.RobinNotBad.BiliClient.activity.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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

open class RefreshListFragment : BaseFragment() {
    lateinit var swipeRefreshLayout: SwipeRefreshLayout
    lateinit var recyclerView: RecyclerView
    var emptyView: TextView? = null
    var listener: OnLoadMoreListener? = null
    var bottom: Boolean = false
    var page: Int = 1
    var lastLoadTimestamp: Long = 0
    var forceSingleColumn: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_simple_refresh, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        emptyView = view.findViewById(R.id.emptyTip)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.isEnabled = false
        swipeRefreshLayout.isRefreshing = true
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(10)
        recyclerView.recycledViewPool.setMaxRecycledViews(0, 20)
        recyclerView.layoutManager = getLayoutManager()
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (listener != null && !recyclerView.canScrollVertically(1) && !isRefreshing() && newState == RecyclerView.SCROLL_STATE_DRAGGING && !bottom) {
                    goOnLoad()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (listener != null) {
                    val manager = recyclerView.layoutManager as LinearLayoutManager
                    val lastItemPosition = manager.findLastVisibleItemPosition()
                    val itemCount = manager.itemCount
                    if (lastItemPosition >= (itemCount - 3) && dy > 0 && !isRefreshing() && !bottom) {
                        goOnLoad()
                    }
                }
            }
        })
        ImageAutoLoadScrollListener.install(recyclerView)
    }

    fun setAdapter(adapter: RecyclerView.Adapter<*>) {
        runOnUiThread { recyclerView.adapter = adapter }
    }

    fun setOnRefreshListener(listener: SwipeRefreshLayout.OnRefreshListener) {
        swipeRefreshLayout.setOnRefreshListener(listener)
        swipeRefreshLayout.isEnabled = true
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

    fun showEmptyView() {
        emptyView?.let {
            runOnUiThread {
                recyclerView.visibility = View.GONE
                it.visibility = View.VISIBLE
            }
        }
    }

    fun isRefreshing(): Boolean {
        return swipeRefreshLayout.isRefreshing
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
        return if (SharedPreferencesUtil.getBoolean("ui_landscape", false) && !SharedPreferencesUtil.getBoolean("ui_mobile_mode", false) && !forceSingleColumn)
            CustomGridManager(requireContext(), 3)
        else
            CustomLinearManager(requireContext())
    }

    fun setForceSingleColumn() {
        forceSingleColumn = true
    }
}