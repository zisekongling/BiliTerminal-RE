package com.RobinNotBad.BiliClient.adapter.video

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.video.QualityChooserActivity
import com.RobinNotBad.BiliClient.activity.video.series.UserSeriesActivity
import com.RobinNotBad.BiliClient.adapter.dynamic.DynamicHolder
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.model.VideoInfo
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalContext

class UserVideoAdapter(
    val context: Context,
    val mid: Long,
    val videoCardList: List<VideoCard>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == 0) {
            val view = LayoutInflater.from(context).inflate(R.layout.cell_goto, parent, false)
            return object : RecyclerView.ViewHolder(view) {}
        } else {
            val view = LayoutInflater.from(this.context).inflate(R.layout.cell_video_list, parent, false)
            return VideoCardHolder(view)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (position == 0) {
            val textView = holder.itemView.findViewById<TextView>(R.id.text)
            textView.text = "视频系列"
            holder.itemView.setOnClickListener {
                val intent = Intent(context, UserSeriesActivity::class.java)
                intent.putExtra("mid", mid)
                context.startActivity(intent)
            }
        } else {
            val realPosition = position - 1
            if (realPosition < 0 || realPosition >= videoCardList.size)
                return
            val videoCardHolder = holder as VideoCardHolder
            val videoCard = videoCardList[realPosition]

            videoCardHolder.showVideoCard(videoCard, context)

            holder.itemView.setOnClickListener {
                TerminalContext.getInstance().enterVideoDetailPage(context, videoCard.aid, videoCard.bvid)
            }

            var longPressRunnable: Runnable? = null
            holder.itemView.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        longPressRunnable = Runnable {
                            val quickMode = SharedPreferencesUtil.getBoolean("cache_quick_mode", true)
                            if (quickMode && videoCard.type != "live") {
                                handleQuickCache(videoCard)
                            }
                        }
                        v.postDelayed(longPressRunnable, 200)
                        false
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { v.removeCallbacks(it) }
                        false
                    }
                    else -> false
                }
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is DynamicHolder)
            (holder as DynamicHolder).extraCard.removeAllViews()
        super.onViewRecycled(holder)
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
        return if (videoCardList != null) videoCardList.size + 1 else 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) 0 else 1
    }
}