package com.RobinNotBad.BiliClient.activity.user

import android.os.Bundle

import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.video.HistoryVideoCardAdapter
import com.RobinNotBad.BiliClient.api.HistoryApi
import com.RobinNotBad.BiliClient.model.ApiResult
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil

class HistoryActivity : RefreshListActivity() {

    private var lastResult = ApiResult()
    private var videoList: ArrayList<VideoCard> = ArrayList()
    private var videoCardAdapter: HistoryVideoCardAdapter? = null

    private var longClickPosition = -1
    private var longClickTimestamp: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setPageName("历史记录")

        recyclerView.setHasFixedSize(true)

        videoList = ArrayList()

        CenterThreadPool.run {
            try {
                lastResult = HistoryApi.getHistory(lastResult, videoList)
                if (lastResult.code == 0) {
                    videoCardAdapter = HistoryVideoCardAdapter(this, videoList)

                    videoCardAdapter!!.setOnLongClickListener { position ->
                        val timestamp = System.currentTimeMillis()
                        
                        if (longClickPosition == position && timestamp - longClickTimestamp < 4000) {
                            CenterThreadPool.run {
                                try {
                                    val videoCard = videoList[position]
                                    val delResult = HistoryApi.deleteHistory(videoCard.aid, videoCard.bvid)
                                    longClickPosition = -1
                                    if (delResult == 0) {
                                        runOnUiThread {
                                            MsgUtil.showMsg("删除成功")
                                            videoList.removeAt(position)
                                            videoCardAdapter!!.updateList(videoList)
                                        }
                                    } else {
                                        runOnUiThread { MsgUtil.showMsg("删除失败，错误码：$delResult") }
                                    }
                                } catch (e: Exception) {
                                    report(e)
                                }
                            }
                        } else {
                            longClickPosition = position
                            longClickTimestamp = timestamp
                            MsgUtil.showMsg("再次长按删除")
                        }
                    }

                    setOnLoadMoreListener { page -> continueLoading(page) }
                    setRefreshing(false)
                    setAdapter(videoCardAdapter!!)

                    if (lastResult.isBottom) {
                        bottom = true
                    }
                } else MsgUtil.showMsg(lastResult.message)

            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }

    private fun continueLoading(page: Int) {
        CenterThreadPool.run {
            try {
                val list: MutableList<VideoCard> = ArrayList()
                lastResult = HistoryApi.getHistory(lastResult, list)
                if (lastResult.code == 0) {
                    runOnUiThread {
                        videoList.addAll(list)
                        videoCardAdapter!!.updateList(videoList)
                    }
                    if (lastResult.isBottom) {
                        bottom = true
                    }
                }
                setRefreshing(false)
                onLoadComplete()
            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }
}