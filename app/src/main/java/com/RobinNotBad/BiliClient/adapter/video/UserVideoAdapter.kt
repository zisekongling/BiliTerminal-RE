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
import com.RobinNotBad.BiliClient.activity.video.series.UserSeriesActivity
import com.RobinNotBad.BiliClient.adapter.dynamic.DynamicHolder
import com.RobinNotBad.BiliClient.model.VideoCard
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
                                VideoQuickCache.handle(context, videoCard)
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

    override fun getItemCount(): Int {
        return if (videoCardList != null) videoCardList.size + 1 else 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) 0 else 1
    }
}
