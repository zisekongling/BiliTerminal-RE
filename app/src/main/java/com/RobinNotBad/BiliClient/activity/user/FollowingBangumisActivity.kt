package com.RobinNotBad.BiliClient.activity.user

import android.os.Bundle
import android.util.Log

import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter
import com.RobinNotBad.BiliClient.api.BangumiApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool

class FollowingBangumisActivity : RefreshListActivity() {

    private var videoList: ArrayList<VideoCard> = ArrayList()
    private var videoCardAdapter: VideoCardAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setPageName("追番列表")

        recyclerView.setHasFixedSize(true)

        videoList = ArrayList()

        CenterThreadPool.run {
            try {
                val result = BangumiApi.getFollowingList(page, videoList)
                if (result != -1) {
                    videoCardAdapter = VideoCardAdapter(this, videoList)
                    setOnLoadMoreListener { page -> continueLoading(page) }
                    setRefreshing(false)
                    setAdapter(videoCardAdapter!!)

                    if (result == 1) {
                        Log.e("debug", "到底了")
                        bottom = true
                    }
                }

            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }

    private fun continueLoading(page: Int) {
        CenterThreadPool.run {
            try {
                val list: MutableList<VideoCard> = ArrayList()
                val result = BangumiApi.getFollowingList(page, list)
                if (result != -1) {
                    Log.e("debug", "下一页")
                    runOnUiThread {
                        videoList.addAll(list)
                        videoCardAdapter!!.notifyItemRangeInserted(videoList.size - list.size, list.size)
                    }
                    if (result == 1) {
                        Log.e("debug", "到底了")
                        bottom = true
                    }
                }
                setRefreshing(false)
            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }
}