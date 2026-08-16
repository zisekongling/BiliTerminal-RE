package com.RobinNotBad.BiliClient.adapter.video

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.video.QualityChooserActivity
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.listener.OnItemLongClickListener
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.model.VideoInfo
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

class VideoCardAdapter(
    val context: Context,
    val videoCardList: List<VideoCard>
) : RecyclerView.Adapter<VideoCardHolder>() {

    var longClickListener: OnItemLongClickListener? = null
    /** 自定义点击监听器，如果设置了则覆盖默认的视频详情页跳转行为 */
    var onItemClickListener: ((Int, VideoCard) -> Unit)? = null

    init {
        setHasStableIds(true)
    }

    fun setOnLongClickListener(listener: OnItemLongClickListener) {
        this.longClickListener = listener
    }

    override fun getItemId(position: Int): Long {
        val card = videoCardList[position]
        return if (card.aid != 0L) card.aid else card.bvid.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoCardHolder {
        val view = LayoutInflater.from(this.context).inflate(R.layout.cell_video_list, parent, false)
        return VideoCardHolder(view)
    }

    override fun onBindViewHolder(holder: VideoCardHolder, position: Int) {
        if (position < 0 || position >= videoCardList.size)
            return
        val videoCard = videoCardList[position]

        holder.showVideoCard(videoCard, context)
        holder.bindClick(videoCard, context, position, object : View.OnLongClickListener {
            override fun onLongClick(v: View): Boolean {
                val quickMode = SharedPreferencesUtil.getBoolean("cache_quick_mode", true)
                if (quickMode && videoCard.type != "live") {
                    handleQuickCache(videoCard)
                    return true
                }
                if (longClickListener != null) {
                    longClickListener!!.onItemLongClick(position)
                    return true
                }
                return false
            }
        })
        // 如果设置了自定义点击监听器，覆盖默认行为
        if (onItemClickListener != null) {
            holder.setCustomClickCallback { onItemClickListener!!.invoke(position, videoCard) }
        }
    }

    private fun handleQuickCache(videoCard: VideoCard) {
        val qualitySetting = SharedPreferencesUtil.getString("cache_default_quality", "dialog")
        when (qualitySetting) {
            "dialog" -> {
                CenterThreadPool.run {
                    try {
                        val videoInfo = fetchVideoInfo(videoCard)
                        if (FileUtil.isVideoCached(videoInfo.title)) {
                            CenterThreadPool.runOnUiThread { MsgUtil.showMsg("该视频已缓存，请先删除原缓存文件") }
                            return@run
                        }
                        CenterThreadPool.runOnUiThread {
                            val intent = Intent(context, QualityChooserActivity::class.java)
                            intent.putExtra("aid", videoCard.aid)
                            intent.putExtra("bvid", videoCard.bvid)
                            intent.putExtra("page", 0)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    } catch (e: Exception) {
                        CenterThreadPool.runOnUiThread { MsgUtil.showMsg("获取视频信息失败") }
                        e.printStackTrace()
                    }
                }
            }
            "highest" -> {
                CenterThreadPool.run {
                    try {
                        val videoInfo = fetchVideoInfo(videoCard)
                        if (FileUtil.isVideoCached(videoInfo.title)) {
                            CenterThreadPool.runOnUiThread { MsgUtil.showMsg("该视频已缓存，请先删除原缓存文件") }
                            return@run
                        }
                        val playerData = videoInfo.toPlayerData(0)
                        PlayerApi.getVideo(playerData, true)
                        val qnValues = playerData.qnValueList
                        if (qnValues != null && qnValues.isNotEmpty()) {
                            val highestQn = qnValues.maxOrNull() ?: qnValues[0]
                            PlayerApi.startDownloading(videoInfo, 0, highestQn)
                            CenterThreadPool.runOnUiThread { MsgUtil.showMsg("已开始缓存") }
                        }
                    } catch (e: Exception) {
                        CenterThreadPool.runOnUiThread { MsgUtil.showMsg("获取视频信息失败") }
                        e.printStackTrace()
                    }
                }
            }
            "audio_only" -> {
                CenterThreadPool.run {
                    try {
                        val videoInfo = fetchVideoInfo(videoCard)
                        if (FileUtil.isVideoCached(videoInfo.title)) {
                            CenterThreadPool.runOnUiThread { MsgUtil.showMsg("该视频已缓存，请先删除原缓存文件") }
                            return@run
                        }
                        val playerData = videoInfo.toPlayerData(0)
                        PlayerApi.getVideo(playerData, true)
                        val qnValues = playerData.qnValueList
                        if (qnValues != null && qnValues.isNotEmpty()) {
                            PlayerApi.getVideoDash(playerData)
                            if (playerData.audioUrl == null || playerData.audioUrl!!.isEmpty()) {
                                CenterThreadPool.runOnUiThread { MsgUtil.showMsg("该视频没有可用的音频流") }
                                return@run
                            }
                            PlayerApi.startDownloadingAudioOnly(videoInfo, 0, qnValues[0], playerData.audioUrl!!)
                            CenterThreadPool.runOnUiThread { MsgUtil.showMsg("已开始缓存音频") }
                        }
                    } catch (e: Exception) {
                        CenterThreadPool.runOnUiThread { MsgUtil.showMsg("获取视频信息失败") }
                        e.printStackTrace()
                    }
                }
            }
            else -> {
                val qn = try { qualitySetting.toInt() } catch (e: Exception) { 64 }
                CenterThreadPool.run {
                    try {
                        val videoInfo = fetchVideoInfo(videoCard)
                        if (FileUtil.isVideoCached(videoInfo.title)) {
                            CenterThreadPool.runOnUiThread { MsgUtil.showMsg("该视频已缓存，请先删除原缓存文件") }
                            return@run
                        }
                        PlayerApi.startDownloading(videoInfo, 0, qn)
                        CenterThreadPool.runOnUiThread { MsgUtil.showMsg("已开始缓存") }
                    } catch (e: Exception) {
                        CenterThreadPool.runOnUiThread { MsgUtil.showMsg("获取视频信息失败") }
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun fetchVideoInfo(videoCard: VideoCard): VideoInfo {
        return if (videoCard.aid != 0L) {
            com.RobinNotBad.BiliClient.api.VideoInfoApi.getVideoInfo(videoCard.aid)
        } else {
            com.RobinNotBad.BiliClient.api.VideoInfoApi.getVideoInfo(videoCard.bvid)
        } ?: throw Exception("无法获取视频信息")
    }

    override fun getItemCount(): Int {
        return if (videoCardList != null) videoCardList.size else 0
    }
}