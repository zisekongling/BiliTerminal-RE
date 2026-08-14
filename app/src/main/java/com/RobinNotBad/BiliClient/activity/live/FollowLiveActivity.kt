package com.RobinNotBad.BiliClient.activity.live

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.LiveCardAdapter
import com.RobinNotBad.BiliClient.api.LiveApi
import com.RobinNotBad.BiliClient.model.LiveRoom
import com.RobinNotBad.BiliClient.util.CenterThreadPool

class FollowLiveActivity : RefreshListActivity() {
    private var roomList: MutableList<LiveRoom>? = null
    private var adapter: LiveCardAdapter? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setPageName("我关注的直播")

        recyclerView.setHasFixedSize(true)

        roomList = ArrayList()

        CenterThreadPool.run {
            try {
                roomList = LiveApi.getFollowed(page)
                adapter = LiveCardAdapter(this, roomList!!)
                setOnLoadMoreListener { continueLoading(it) }
                setRefreshing(false)
                setAdapter(adapter!!)
                if (roomList!!.size < 1) showEmptyView()
            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }

    private fun continueLoading(page: Int) {
        CenterThreadPool.run {
            try {
                val list: List<LiveRoom>?
                list = LiveApi.getFollowed(page)
                Log.e("debug", "下一页")
                runOnUiThread {
                    if (list != null) {
                        roomList!!.addAll(list)
                        adapter!!.notifyItemRangeInserted(roomList!!.size - list.size, list.size)
                    }
                }
                if (list != null && list.size < 1) {
                    Log.e("debug", "到底了")
                    bottom = true
                }
                setRefreshing(false)
            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }
}