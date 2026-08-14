package com.RobinNotBad.BiliClient.ui.mobile

import android.annotation.SuppressLint
import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter
import com.RobinNotBad.BiliClient.api.RecommendApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool

/**
 * 移动端热门频道Fragment
 */
class MobilePopularPageFragment : MobilePageFragment() {

    private var videoCardList: MutableList<VideoCard> = ArrayList()
    private var videoCardAdapter: VideoCardAdapter? = null
    private var firstRefresh = true

    @SuppressLint("NotifyDataSetChanged")
    override fun loadData() {
        setRefreshing(true)

        CenterThreadPool.run {
            try {
                val list = ArrayList<VideoCard>()
                RecommendApi.getPopular(list, page)
                if (list.isEmpty()) {
                    bottom = true
                    setRefreshing(false)
                    return@run
                }
                activity?.runOnUiThread {
                    videoCardList.addAll(list)
                    setRefreshing(false)
                    if (firstRefresh) {
                        firstRefresh = false
                        videoCardAdapter = VideoCardAdapter(requireContext(), videoCardList)
                        recyclerView.adapter = videoCardAdapter
                    } else {
                        videoCardAdapter?.notifyItemRangeInserted(videoCardList.size - list.size, list.size)
                    }
                }
            } catch (e: Exception) {
                loadFail()
            }
        }
    }

    override fun refreshData() {
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