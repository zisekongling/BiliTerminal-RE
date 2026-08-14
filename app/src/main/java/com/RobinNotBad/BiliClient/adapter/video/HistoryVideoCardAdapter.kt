package com.RobinNotBad.BiliClient.adapter.video

import android.content.Context
import android.content.Intent
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.video.QualityChooserActivity
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.model.VideoInfo
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import java.text.SimpleDateFormat
import java.util.*

class HistoryVideoCardAdapter(
    private val context: Context,
    private val videoCardList: List<VideoCard>
) : RecyclerView.Adapter<VideoCardHolder>() {

    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINESE)
        private const val LONG_PRESS_DELAY = 200L
    }

    private var videoList: List<VideoCard> = videoCardList
    private var onLongClickListener: ((Int) -> Unit)? = null

    init {
        setHasStableIds(true)
    }

    fun setOnLongClickListener(listener: (Int) -> Unit) {
        onLongClickListener = listener
    }

    override fun getItemViewType(position: Int): Int = 0

    override fun getItemId(position: Int): Long {
        val item = videoList[position]
        return if (item.aid != 0L) item.aid else item.bvid.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoCardHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.cell_video_list, parent, false)
        return VideoCardHolder(view)
    }

    override fun onBindViewHolder(holder: VideoCardHolder, position: Int) {
        val videoCard = videoList[position]
        holder.showVideoCard(videoCard, context)
        holder.bindClick(videoCard, context, position, null)

        // 在进度文字后追加观看时间
        if (videoCard.viewAt > 0) {
            val timeStr = timeFormat.format(Date(videoCard.viewAt * 1000))
            holder.viewCount.text = "${videoCard.view}  $timeStr"
        }

        var longPressRunnable: Runnable? = null

        holder.itemView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressRunnable = Runnable {
                        val quickMode = SharedPreferencesUtil.getBoolean("cache_quick_mode", false)
                        if (quickMode && videoCard.type != "live") {
                            handleQuickCache(videoCard)
                        } else {
                            onLongClickListener?.invoke(position)
                        }
                    }
                    v.postDelayed(longPressRunnable, LONG_PRESS_DELAY)
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

    override fun getItemCount(): Int = videoList.size

    fun updateList(newList: List<VideoCard>) {
        videoList = newList
        notifyDataSetChanged()
    }
}