package com.RobinNotBad.BiliClient.activity.live

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import com.RobinNotBad.BiliClient.activity.base.RefreshMainActivity
import com.RobinNotBad.BiliClient.adapter.LiveCardAdapter
import com.RobinNotBad.BiliClient.api.LiveApi
import com.RobinNotBad.BiliClient.model.LiveRoom
import com.RobinNotBad.BiliClient.util.CenterThreadPool

class RecommendLiveActivity : RefreshMainActivity() {
    private var roomList: MutableList<LiveRoom>? = null
    private var adapter: LiveCardAdapter? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setPageName("推荐直播")

        recyclerView.setHasFixedSize(true)

        roomList = ArrayList()

        setMenuClick()

        setOnRefreshListener { loadLiveRooms(refresh = true) }
        // 空数据时可点击重试
        setOnEmptyRetry { loadLiveRooms(refresh = true) }

        loadLiveRooms(refresh = false)
    }

    /** [refresh] = true 表示用户主动刷新/重试（要连监听器一起重建），false 为首次进入。 */
    private fun loadLiveRooms(refresh: Boolean) {
        CenterThreadPool.run {
            try {
                roomList = LiveApi.getRecommend(if (refresh) 1 else page)
                // 无内容时给出空态（一级页此前完全没有空态）
                if (roomList!!.isEmpty()) showEmptyView() else hideEmptyView()
                if (refresh) page = 1
                adapter = LiveCardAdapter(this, roomList!!)
                setOnLoadMoreListener { continueLoading(it) }
                setRefreshing(false)
                setAdapter(adapter!!)
            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }

    private fun continueLoading(page: Int) {
        CenterThreadPool.run {
            try {
                val list: List<LiveRoom>?
                list = LiveApi.getRecommend(page)
                Log.e("debug", "下一页")
                runOnUiThread {
                    if (list != null) {
                        roomList!!.addAll(list)
                        hideEmptyView()
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