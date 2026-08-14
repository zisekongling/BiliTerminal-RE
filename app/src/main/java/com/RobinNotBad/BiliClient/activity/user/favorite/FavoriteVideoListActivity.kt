package com.RobinNotBad.BiliClient.activity.user.favorite

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log

import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter
import com.RobinNotBad.BiliClient.api.FavoriteApi
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

class FavoriteVideoListActivity : RefreshListActivity() {

    private var mid: Long = 0
    private var fid: Long = 0
    private var videoList: ArrayList<VideoCard> = ArrayList()
    private var videoCardAdapter: VideoCardAdapter? = null

    private var longClickPosition = -1
    private var longClickTimestamp: Long = 0

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        mid = intent.getLongExtra("mid", 0)
        fid = intent.getLongExtra("fid", 0)
        val name = intent.getStringExtra("name")

        setPageName(name!!)

        videoList = ArrayList()

        CenterThreadPool.run {
            try {
                val result = FavoriteApi.getFolderVideos(mid, fid, page, videoList)
                if (result != -1) {
                    videoCardAdapter = VideoCardAdapter(this, videoList)

                    // 虚拟合集模式：点击收藏夹内视频，将当前收藏夹所有视频组成合集播放
                    if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.VIRTUAL_COLLECTION_ENABLE, true)) {
                        videoCardAdapter!!.onItemClickListener = { position, videoCard ->
                            val currentAid = videoCard.aid
                            playFavoriteVirtualCollection(position, currentAid, fid, name ?: "")
                        }
                    }

                    videoCardAdapter!!.setOnLongClickListener { position ->
                        val timestamp = System.currentTimeMillis()
                        if (longClickPosition == position && timestamp - longClickTimestamp < 4000) {
                            CenterThreadPool.run {
                                try {
                                    val delResult = FavoriteApi.deleteFavorite(videoList[position].aid, fid)
                                    longClickPosition = -1
                                    if (delResult == 0) runOnUiThread {
                                        MsgUtil.showMsg("删除成功")
                                        videoList.removeAt(position)
                                        videoCardAdapter!!.notifyItemRemoved(position)
                                        videoCardAdapter!!.notifyItemRangeChanged(position, videoList.size - position)
                                    }
                                    else
                                        runOnUiThread { MsgUtil.showMsg("删除失败，错误码：$delResult") }
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
                    setAdapter(videoCardAdapter!!)
                    setRefreshing(false)

                    if (result == 1) {
                        Log.e("debug", "到底了")
                        bottom = true
                    }
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
                val lastSize = videoList.size
                val result = FavoriteApi.getFolderVideos(mid, fid, page, videoList)
                if (result != -1) {
                    Log.e("debug", "下一页")
                    runOnUiThread { videoCardAdapter!!.notifyItemRangeInserted(lastSize, videoList.size - lastSize) }
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

    /**
     * 播放收藏夹虚拟合集：将收藏夹内的所有视频组成合集，传入播放器实现连续播放
     * @param startPosition 点击的视频在列表中的位置
     * @param startAid 点击的视频aid（作为起始播放位置）
     * @param folderId 收藏夹ID
     * @param folderName 收藏夹名称
     */
    private fun playFavoriteVirtualCollection(startPosition: Int, startAid: Long, folderId: Long, folderName: String) {
        if (videoList.isEmpty()) {
            MsgUtil.showMsg("收藏夹为空")
            return
        }

        // 在后台线程获取起始视频的cid
        CenterThreadPool.run {
            try {
                // 构建合集的pagenames和cids
                val pagenames = ArrayList<String>()
                val cids = ArrayList<Long>()
                var startPageIndex = 0
                var firstCid: Long = 0

                for ((i, v) in videoList.withIndex()) {
                    pagenames.add(v.title)
                    cids.add(v.aid) // 使用aid作为标识，实际播放时会重新获取cid
                    if (v.aid == startAid) {
                        startPageIndex = i
                    }
                }

                // 获取起始视频的cid
                val firstAid = if (startPosition < videoList.size) videoList[startPosition].aid else startAid
                val videoInfo = com.RobinNotBad.BiliClient.api.VideoInfoApi.getVideoInfo(firstAid)
                if (videoInfo != null && videoInfo.cids.isNotEmpty()) {
                    firstCid = videoInfo.cids[0]
                }

                if (firstCid <= 0) {
                    runOnUiThread { MsgUtil.showMsg("获取视频信息失败") }
                    return@run
                }

                // 构建PlayerData
                val playerData = PlayerData(PlayerData.TYPE_VIDEO)
                playerData.title = "$folderName（虚拟合集）"
                playerData.aid = firstAid
                playerData.cid = firstCid
                playerData.pagenames = pagenames
                playerData.cids = cids
                playerData.currentPageIndex = startPageIndex

                // 在主线程启动播放
                runOnUiThread {
                    try {
                        PlayerApi.startGettingUrl(playerData)
                    } catch (e: Exception) {
                        MsgUtil.err(e)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { MsgUtil.err(e) }
            }
        }
    }
}