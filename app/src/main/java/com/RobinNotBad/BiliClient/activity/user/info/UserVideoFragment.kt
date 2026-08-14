package com.RobinNotBad.BiliClient.activity.user.info

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View

import androidx.annotation.NonNull

import com.RobinNotBad.BiliClient.activity.base.RefreshListFragment
import com.RobinNotBad.BiliClient.adapter.video.UserVideoAdapter
import com.RobinNotBad.BiliClient.api.UserInfoApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool

class UserVideoFragment : RefreshListFragment() {

    private var mid: Long = 0
    private var videoList: ArrayList<VideoCard> = ArrayList()
    private var adapter: UserVideoAdapter? = null

    companion object {
        fun newInstance(mid: Long): UserVideoFragment {
            val fragment = UserVideoFragment()
            val args = Bundle()
            args.putLong("mid", mid)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            mid = arguments!!.getLong("mid")
        }
    }

    override fun onViewCreated(@NonNull view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        videoList = ArrayList()
        setOnLoadMoreListener { page -> continueLoading(page) }

        CenterThreadPool.run {
            try {
                bottom = (UserInfoApi.getUserVideos(mid, page, "", videoList) == 1)
                if (isAdded) {
                    setRefreshing(false)
                    adapter = UserVideoAdapter(requireContext(), mid, videoList)
                    setAdapter(adapter!!)
                    if (bottom && videoList.isEmpty()) showEmptyView()
                }
            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun continueLoading(page: Int) {
        CenterThreadPool.run {
            try {
                val list: MutableList<VideoCard> = ArrayList()
                val result = UserInfoApi.getUserVideos(mid, page, "", list)
                if (result != -1) {
                    Log.e("debug", "下一页")
                    runOnUiThread {
                        videoList.addAll(list)
                        adapter!!.notifyItemRangeInserted(videoList.size - list.size, list.size)
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