package com.RobinNotBad.BiliClient.ui.mobile

import android.annotation.SuppressLint
import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter
import com.RobinNotBad.BiliClient.api.RecommendApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool

/**
 * 移动端推荐频道Fragment
 */
class MobileRecommendPageFragment : MobilePageFragment() {

    private var videoCardList: MutableList<VideoCard> = ArrayList()
    private var videoCardAdapter: VideoCardAdapter? = null
    private var firstRefresh = true
    private var freshType = 3
    private val loadedBvids = mutableSetOf<String>()

    @SuppressLint("NotifyDataSetChanged")
    override fun loadData() {
        val requestFreshType = freshType
        freshType = if (freshType == 3) 4 else 3
        setRefreshing(true)

        CenterThreadPool.run {
            try {
                val list = ArrayList<VideoCard>()
                RecommendApi.getRecommend(list, requestFreshType)
                setRefreshing(false)

                activity?.runOnUiThread {
                    val newItems = list.filter { loadedBvids.add(it.bvid) }
                    if (newItems.isEmpty()) {
                        setRefreshing(false)
                        return@runOnUiThread
                    }
                    videoCardList.addAll(newItems)
                    if (firstRefresh) {
                        firstRefresh = false
                        videoCardAdapter = VideoCardAdapter(requireContext(), videoCardList)
                        recyclerView.adapter = videoCardAdapter
                    } else {
                        videoCardAdapter?.notifyItemRangeInserted(videoCardList.size - newItems.size, newItems.size)
                    }
                }
            } catch (e: Exception) {
                loadFail()
            }
        }
    }

    override fun refreshData() {
        freshType = 3
        loadedBvids.clear()
        if (firstRefresh) {
            videoCardList = ArrayList()
        } else {
            val last = videoCardList.size
            videoCardList.clear()
            videoCardAdapter?.notifyItemRangeRemoved(0, last)
        }
        super.refreshData()
    }
}