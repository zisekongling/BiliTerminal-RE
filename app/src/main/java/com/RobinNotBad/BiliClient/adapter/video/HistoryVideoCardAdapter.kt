package com.RobinNotBad.BiliClient.adapter.video

import android.content.Context
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TimeUtil
import com.RobinNotBad.BiliClient.util.view.LongPressDelay
import java.util.Locale

class HistoryVideoCardAdapter(
    private val context: Context,
    private val videoCardList: List<VideoCard>
) : RecyclerView.Adapter<VideoCardHolder>() {

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
            val timeStr = TimeUtil.formatTime(videoCard.viewAt * 1000, Locale.CHINESE)
            holder.viewCount.text = "${videoCard.view}  $timeStr"
        }

        var longPressRunnable: Runnable? = null

        holder.itemView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressRunnable = Runnable {
                        // 与 VideoCardAdapter 一致：页面显式注册的长按（历史记录的「长按两次删除」）
                        // 优先于「长按快捷操作」，没有自定义长按的列表才回落到操作菜单
                        if (onLongClickListener != null) {
                            onLongClickListener!!.invoke(position)
                        } else if (SharedPreferencesUtil.getBoolean(SettingsKeys.CACHE_QUICK_MODE, true) && videoCard.type != "live") {
                            VideoCardMenu.show(context, videoCard)
                        }
                    }
                    v.postDelayed(longPressRunnable, LongPressDelay.CARD_MS)
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

    override fun getItemCount(): Int = videoList.size

    fun updateList(newList: List<VideoCard>) {
        videoList = newList
        notifyDataSetChanged()
    }
}
