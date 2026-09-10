package com.RobinNotBad.BiliClient.adapter.video

import android.content.Context
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TimeUtil
import java.util.Locale

class HistoryVideoCardAdapter(
    private val context: Context,
    private val videoCardList: List<VideoCard>
) : RecyclerView.Adapter<VideoCardHolder>() {

    companion object {
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
            val timeStr = TimeUtil.formatTime(videoCard.viewAt * 1000, Locale.CHINESE)
            holder.viewCount.text = "${videoCard.view}  $timeStr"
        }

        var longPressRunnable: Runnable? = null

        holder.itemView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressRunnable = Runnable {
                        val quickMode = SharedPreferencesUtil.getBoolean("cache_quick_mode", true)
                        if (quickMode && videoCard.type != "live") {
                            VideoQuickCache.handle(context, videoCard)
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

    override fun getItemCount(): Int = videoList.size

    fun updateList(newList: List<VideoCard>) {
        videoList = newList
        notifyDataSetChanged()
    }
}
