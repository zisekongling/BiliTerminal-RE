package com.RobinNotBad.BiliClient.activity.video

import android.annotation.SuppressLint
import android.os.Bundle
import com.RobinNotBad.BiliClient.activity.base.RefreshMainActivity
import com.RobinNotBad.BiliClient.adapter.video.HotSearchAdapter
import com.RobinNotBad.BiliClient.api.HotSearchApi
import com.RobinNotBad.BiliClient.model.HotSearchCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import java.util.ArrayList

class HotSearchActivity : RefreshMainActivity() {

    private var adapter: HotSearchAdapter? = null
    private var hotList: ArrayList<HotSearchCard> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName("热搜")
        setMenuClick()
        setOnRefreshListener { loadHotSearch() }
        loadHotSearch()
    }

    private fun loadHotSearch() {
        swipeRefreshLayout.isRefreshing = true
        CenterThreadPool.run {
            try {
                val newList = ArrayList<HotSearchCard>()
                if (HotSearchApi.getHotSearch(newList)) {
                    hotList = newList
                    runOnUiThread { applyResult() }
                } else {
                    runOnUiThread {
                        swipeRefreshLayout.isRefreshing = false
                        MsgUtil.showMsgLong("获取热搜失败，请稍后重试")
                    }
                }
            } catch (e: Exception) {
                report(e)
                runOnUiThread {
                    swipeRefreshLayout.isRefreshing = false
                    MsgUtil.showMsgLong("网络异常，请稍后重试")
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun applyResult() {
        if (adapter == null) {
            adapter = HotSearchAdapter(this, hotList)
            recyclerView.adapter = adapter
        } else {
            adapter?.notifyDataSetChanged()
        }
        swipeRefreshLayout.isRefreshing = false
    }
}
