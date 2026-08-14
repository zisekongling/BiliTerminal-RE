package com.RobinNotBad.BiliClient.activity.video

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshMainActivity
import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter
import com.RobinNotBad.BiliClient.api.RecommendApi
import com.RobinNotBad.BiliClient.helper.TutorialHelper
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool

//推荐页面
//2023-07-13

class RecommendActivity : RefreshMainActivity() {

    private var videoCardList: MutableList<VideoCard>? = null
    private var videoCardAdapter: VideoCardAdapter? = null
    private var firstRefresh = true
    private var freshType = 3
    private val loadedBvids = mutableSetOf<String>()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setMenuClick()
        Log.e("debug", "进入推荐页")

        setOnRefreshListener { refreshRecommend() }
        setOnLoadMoreListener { addRecommend() }

        setPageName("推荐")

        recyclerView.setHasFixedSize(true)

        TutorialHelper.showTutorialList(this, R.array.tutorial_recommend, 0)

        refreshRecommend()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun refreshRecommend() {
        Log.e("debug", "刷新")
        freshType = 3
        loadedBvids.clear()
        if (firstRefresh) {
            videoCardList = ArrayList()
        } else {
            val last = videoCardList!!.size
            videoCardList!!.clear()
            videoCardAdapter!!.notifyItemRangeRemoved(0, last)
        }

        addRecommend()
    }

    private fun addRecommend() {
        Log.e("debug", "加载下一页")
        val requestFreshType = freshType
        freshType = if (freshType == 3) 4 else 3
        CenterThreadPool.run {
            try {
                val list = ArrayList<VideoCard>()
                RecommendApi.getRecommend(list, requestFreshType)
                setRefreshing(false)

                runOnUiThread {
                    val newItems = list.filter { loadedBvids.add(it.bvid) }
                    if (newItems.isEmpty()) {
                        setRefreshing(false)
                        return@runOnUiThread
                    }
                    videoCardList!!.addAll(newItems)
                    if (firstRefresh) {
                        firstRefresh = false
                        videoCardAdapter = VideoCardAdapter(this, videoCardList!!)
                        setAdapter(videoCardAdapter!!)
                    } else {
                        videoCardAdapter!!.notifyItemRangeInserted(videoCardList!!.size - newItems.size, newItems.size)
                    }
                }
            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }
}