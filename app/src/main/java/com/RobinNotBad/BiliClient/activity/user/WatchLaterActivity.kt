package com.RobinNotBad.BiliClient.activity.user

import android.annotation.SuppressLint
import android.os.Bundle

import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter
import com.RobinNotBad.BiliClient.api.WatchLaterApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil

import org.json.JSONException

import java.io.IOException

class WatchLaterActivity : RefreshListActivity() {

    private var longClickPosition = -1
    private var longClickTimestamp: Long = 0

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName("稍后再看")
        recyclerView.setHasFixedSize(true)

        CenterThreadPool.run {
            try {
                val videoCardList = WatchLaterApi.getWatchLaterList()
                val adapter = VideoCardAdapter(this, videoCardList)

                adapter.setOnLongClickListener { position ->
                    val timestamp = System.currentTimeMillis()
                    if (longClickPosition == position && timestamp - longClickTimestamp < 4000) {
                        CenterThreadPool.run {
                            try {
                                val result = WatchLaterApi.delete(videoCardList[position].aid)
                                longClickPosition = -1
                                if (result == 0) runOnUiThread {
                                    MsgUtil.showMsg("删除成功")
                                    videoCardList.removeAt(position)
                                    adapter.notifyItemRemoved(position)
                                    adapter.notifyItemRangeChanged(position, videoCardList.size - position)
                                }
                                else
                                    runOnUiThread { MsgUtil.showMsg("删除失败，错误码：" + result) }
                            } catch (e: IOException) {
                                e.printStackTrace()
                            } catch (e: JSONException) {
                                e.printStackTrace()
                            }
                        }
                    } else {
                        longClickPosition = position
                        longClickTimestamp = timestamp
                        MsgUtil.showMsg("再次长按删除")
                    }
                }

                setAdapter(adapter)
                setRefreshing(false)
            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }
}