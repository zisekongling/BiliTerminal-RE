package com.RobinNotBad.BiliClient.activity.video

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter
import com.RobinNotBad.BiliClient.api.RecommendApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.view.ImageAutoLoadScrollListener

class PreciousActivity : InstanceActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var videoCardList: ArrayList<VideoCard>? = null
    private var videoCardAdapter: VideoCardAdapter? = null
    private var firstRefresh = true
    private var refreshing = false

    private var page = 1

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_main_refresh)
        setMenuClick()
        Log.e("debug", "进入入站必刷页")

        recyclerView = findViewById(R.id.recyclerView)
        ImageAutoLoadScrollListener.install(recyclerView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener { loadPrecious() }

        val title = findViewById<TextView>(R.id.pageName)
        title.text = "入站必刷"

        loadPrecious()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadPrecious() {
        Log.e("debug", "刷新")
        page = 1
        if (firstRefresh) {
            recyclerView.layoutManager = CustomLinearManager(this)
            videoCardList = ArrayList()
        } else {
            val last = videoCardList!!.size
            videoCardList!!.clear()
            videoCardAdapter!!.notifyItemRangeRemoved(0, last)
        }
        swipeRefreshLayout.setRefreshing(true)

        refreshing = true
        CenterThreadPool.run { addPrecious() }
    }

    private fun addPrecious() {
        Log.e("debug", "加载下一页")
        runOnUiThread { swipeRefreshLayout.setRefreshing(true) }
        try {
            val list = ArrayList<VideoCard>()
            RecommendApi.getPrecious(list, page)
            page++
            runOnUiThread {
                videoCardList!!.addAll(list)
                swipeRefreshLayout.setRefreshing(false)
                refreshing = false
                if (firstRefresh) {
                    firstRefresh = false
                    videoCardAdapter = VideoCardAdapter(this, videoCardList!!)
                    recyclerView.adapter = videoCardAdapter

                    recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                            super.onScrollStateChanged(recyclerView, newState)
                        }

                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            super.onScrolled(recyclerView, dx, dy)
                            val manager = recyclerView.layoutManager as LinearLayoutManager
                            val lastItemPosition = manager.findLastCompletelyVisibleItemPosition()
                            val itemCount = manager.itemCount
                            if (lastItemPosition >= (itemCount - 3) && dy > 0 && !refreshing) {
                                refreshing = true
                                CenterThreadPool.run { addPrecious() }
                            }
                        }
                    })
                } else {
                    videoCardAdapter!!.notifyItemRangeInserted(videoCardList!!.size - list.size, list.size)
                }
            }
        } catch (e: Exception) {
            runOnUiThread { MsgUtil.err(e) }
        }
    }
}