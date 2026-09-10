package com.RobinNotBad.BiliClient.adapter.video

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.listener.OnItemLongClickListener
import com.RobinNotBad.BiliClient.model.VideoCard
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
                    VideoQuickCache.handle(context, videoCard)
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

    override fun getItemCount(): Int {
        return if (videoCardList != null) videoCardList.size else 0
    }
}
