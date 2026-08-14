package com.RobinNotBad.BiliClient.ui.mobile

import android.annotation.SuppressLint
import com.RobinNotBad.BiliClient.adapter.dynamic.DynamicAdapter
import com.RobinNotBad.BiliClient.api.DynamicApi
import com.RobinNotBad.BiliClient.model.Dynamic
import com.RobinNotBad.BiliClient.util.CenterThreadPool

/**
 * 移动端动态频道Fragment
 */
class MobileDynamicPageFragment : MobilePageFragment() {

    private var dynamicList: MutableList<Dynamic> = ArrayList()
    private var dynamicAdapter: DynamicAdapter? = null
    private var firstRefresh = true
    private var offset: Long = 0
    private var refreshing = false

    @SuppressLint("NotifyDataSetChanged")
    override fun loadData() {
        if (refreshing) return
        refreshing = true
        setRefreshing(true)

        CenterThreadPool.run {
            try {
                val list = ArrayList<Dynamic>()
                offset = DynamicApi.getDynamicList(list, offset, 0, "all")
                if (offset == -1L) {
                    bottom = true
                }
                activity?.runOnUiThread {
                    dynamicList.addAll(list)
                    setRefreshing(false)
                    refreshing = false
                    if (firstRefresh) {
                        firstRefresh = false
                        dynamicAdapter = DynamicAdapter(requireContext(), dynamicList, recyclerView, null)
                        recyclerView.adapter = dynamicAdapter
                    } else {
                        dynamicAdapter?.notifyDataSetChanged()
                    }
                }
            } catch (e: Exception) {
                refreshing = false
                loadFail()
            }
        }
    }

    override fun refreshData() {
        offset = 0
        if (firstRefresh) {
            dynamicList = ArrayList()
        } else {
            dynamicList.clear()
            dynamicAdapter?.notifyDataSetChanged()
        }
        super.refreshData()
    }
}