package com.RobinNotBad.BiliClient.activity.search

import android.os.Bundle
import android.util.Log
import android.view.View

import androidx.annotation.NonNull

import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter
import com.RobinNotBad.BiliClient.api.SearchApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool

import org.json.JSONArray

class SearchVideoFragment : SearchFragment() {
    private var videoCardList = ArrayList<VideoCard>()
    private var videoCardAdapter: VideoCardAdapter? = null

    companion object {
        fun newInstance(): SearchVideoFragment {
            return SearchVideoFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(@NonNull view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        videoCardList = ArrayList()
        videoCardAdapter = VideoCardAdapter(requireContext(), videoCardList)
        setAdapter(videoCardAdapter!!)

        setOnRefreshListener { refreshInternal() }
        setOnLoadMoreListener { page -> continueLoading(page) }
    }

    private fun continueLoading(page: Int) {
        CenterThreadPool.run {
            Log.e("debug", "加载下一页")
            try {
                val result = SearchApi.search(keyword, page)
                if (result != null) {
                    if (page == 1) showEmptyView(false)
                    val list = ArrayList<VideoCard>()
                    SearchApi.getVideosFromSearchResult(result, list, page == 1)
                    Log.d("debug-size", list.size.toString())
                    if (list.size == 0) bottom = true
                    else CenterThreadPool.runOnUiThread {
                        val lastSize = videoCardList.size
                        videoCardList.addAll(list)
                        videoCardAdapter!!.notifyItemRangeInserted(lastSize + 1, videoCardList.size - lastSize)
                    }
                } else bottom = true
            } catch (e: Exception) {
                e.printStackTrace()
                loadFail(e)
            }
            setRefreshing(false)
        }
    }

    override fun refreshInternal() {
        CenterThreadPool.runOnUiThread {
            page = 1
            if (this.videoCardAdapter == null)
                this.videoCardAdapter = VideoCardAdapter(this.requireContext(), this.videoCardList)
            val size_old = this.videoCardList.size
            this.videoCardList.clear()
            if (size_old != 0) this.videoCardAdapter!!.notifyItemRangeRemoved(0, size_old)
            CenterThreadPool.run { continueLoading(page) }
        }
    }
}