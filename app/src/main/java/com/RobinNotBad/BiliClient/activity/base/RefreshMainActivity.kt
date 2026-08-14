package com.RobinNotBad.BiliClient.activity.base

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.listener.OnLoadMoreListener
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.view.ImageAutoLoadScrollListener

open class RefreshMainActivity : InstanceActivity() {
    lateinit var swipeRefreshLayout: SwipeRefreshLayout
    lateinit var recyclerView: RecyclerView
    var listener: OnLoadMoreListener? = null
    var bottom: Boolean = false
    var page: Int = 1
    var lastLoadTimestamp: Long = 0
    protected var isRefreshing: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_main_refresh)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
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
        runOnUiThread { recyclerView.adapter = adapter }
    }

    fun setOnRefreshListener(listener: SwipeRefreshLayout.OnRefreshListener) {
        swipeRefreshLayout.setOnRefreshListener(listener)
        swipeRefreshLayout.isEnabled = true
    }

    @JvmName("setRefreshingState")
    fun setRefreshing(bool: Boolean) {
        runOnUiThread { swipeRefreshLayout.isRefreshing = bool }
        isRefreshing = bool
    }

    fun setOnLoadMoreListener(loadMore: OnLoadMoreListener) {
        listener = loadMore
    }

    private fun goOnLoad() {
        synchronized(this) {
            val timeCurrent = System.currentTimeMillis()
            if (timeCurrent - lastLoadTimestamp > 100) {
                swipeRefreshLayout.isRefreshing = true
                page++
                listener!!.onLoad(page)
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