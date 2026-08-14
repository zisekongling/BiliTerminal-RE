package com.RobinNotBad.BiliClient.activity.video.series

import android.os.Bundle
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.video.SeriesCardAdapter
import com.RobinNotBad.BiliClient.api.SeriesApi
import com.RobinNotBad.BiliClient.model.Series
import com.RobinNotBad.BiliClient.util.CenterThreadPool

class UserSeriesActivity : RefreshListActivity() {

    private var mid: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mid = intent.getLongExtra("mid", 0)

        setPageName("投稿的系列")

        loadData(1)
        setOnRefreshListener { loadData(1) }
        setOnLoadMoreListener {
            loadData(it)
        }
    }

    private fun loadData(page: Int) {
        CenterThreadPool.run {
            try {
                val seasonList = ArrayList<Series>()
                val result = SeriesApi.getUserSeries(mid, page, seasonList)

                if (page == 1) {
                    if (seasonList.isEmpty()) {
                        runOnUiThread {
                            showEmptyView()
                            setRefreshing(false)
                        }
                        return@run
                    }

                    runOnUiThread {
                        val adapter = SeriesCardAdapter(this@UserSeriesActivity, seasonList)
                        setAdapter(adapter)
                        setRefreshing(false)
                        hideEmptyView()
                    }
                } else {
                    runOnUiThread {
                        val recyclerView = this@UserSeriesActivity.recyclerView
                        val adapter = recyclerView.adapter as? SeriesCardAdapter
                        if (adapter != null) {
                            val oldSize = adapter.itemCount
                            adapter.notifyItemRangeInserted(oldSize, seasonList.size)
                        }
                        onLoadComplete()
                        setRefreshing(false)
                    }

                    if (result != 0) {
                        bottom = true
                    }
                }
            } catch (e: Exception) {
                if (page == 1) {
                    runOnUiThread {
                        showEmptyView()
                        setRefreshing(false)
                    }
                } else {
                    loadFail(e)
                }
            }
        }
    }
}