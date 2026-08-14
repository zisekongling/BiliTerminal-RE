package com.RobinNotBad.BiliClient.ui.mobile

import android.annotation.SuppressLint
import com.RobinNotBad.BiliClient.adapter.LiveCardAdapter
import com.RobinNotBad.BiliClient.api.LiveApi
import com.RobinNotBad.BiliClient.model.LiveRoom
import com.RobinNotBad.BiliClient.util.CenterThreadPool

/**
 * 移动端直播频道Fragment
 */
class MobileLivePageFragment : MobilePageFragment() {

    private var roomList: MutableList<LiveRoom> = ArrayList()
    private var adapter: LiveCardAdapter? = null
    private var firstRefresh = true

    @SuppressLint("NotifyDataSetChanged")
    override fun loadData() {
        setRefreshing(true)

        CenterThreadPool.run {
            try {
                val list = LiveApi.getRecommend(page)
                if (list.isEmpty()) {
                    bottom = true
                    setRefreshing(false)
                    return@run
                }
                activity?.runOnUiThread {
                    roomList.addAll(list)
                    setRefreshing(false)
                    if (firstRefresh) {
                        firstRefresh = false
                        adapter = LiveCardAdapter(requireContext(), roomList)
                        recyclerView.adapter = adapter
                    } else {
                        adapter?.notifyItemRangeInserted(roomList.size - list.size, list.size)
                    }
                }
            } catch (e: Exception) {
                loadFail()
            }
        }
    }

    override fun refreshData() {
        if (firstRefresh) {
            roomList = ArrayList()
        } else {
            val last = roomList.size
            roomList.clear()
            adapter?.notifyItemRangeRemoved(0, last)
        }
        super.refreshData()
    }
}