package com.RobinNotBad.BiliClient.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.model.Timeline
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import java.text.SimpleDateFormat
import java.util.Locale

class TimelineAdapter(
    private val context: Context,
    private val dayInfoList: List<Timeline.DayInfo>
) : RecyclerView.Adapter<TimelineAdapter.DayViewHolder>() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val requestManager = Glide.with(BiliTerminal.context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.cell_timeline_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val dayInfo = dayInfoList[position]

        var dateText = dayInfo.date
        if (dayInfo.is_today == 1) {
            dateText = dateText + " (今天)"
        }
        holder.dateText.text = dateText

        holder.episodesLayout.removeAllViews()

        if (dayInfo.episodes != null && dayInfo.episodes!!.isNotEmpty()) {
            for (i in dayInfo.episodes!!.indices) {
                val episode = dayInfo.episodes!![i]
                val episodeView = holder.obtainEpisodeView(i)

                val cover = episodeView.findViewById<ImageView>(R.id.img_cover)
                val title = episodeView.findViewById<TextView>(R.id.text_title)
                val episodeText = episodeView.findViewById<TextView>(R.id.text_episode)
                val timeText = episodeView.findViewById<TextView>(R.id.text_time)

                title.text = episode.title
                episodeText.text = episode.pub_index

                if (episode.pub_ts > 0) {
                    timeText.text = timeFormat.format(episode.pub_ts * 1000L)
                } else {
                    timeText.text = episode.pub_time
                }

                val coverUrl = GlideUtil.url(episode.cover)
                requestManager.asDrawable().load(coverUrl)
                    .placeholder(R.mipmap.placeholder)
                    .format(DecodeFormat.PREFER_RGB_565)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .apply(EPISODE_OPTIONS)
                    .into(cover)

                holder.episodesLayout.addView(episodeView)
            }
        }
    }

    override fun getItemCount(): Int {
        return if (dayInfoList != null) dayInfoList.size else 0
    }

    class DayViewHolder(@androidx.annotation.NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        var dateText: TextView = itemView.findViewById(R.id.text_date)
        var episodesLayout: LinearLayout = itemView.findViewById(R.id.episodes_layout)
        private val pooledViews = ArrayList<View>()

        fun obtainEpisodeView(index: Int): View {
            if (index < pooledViews.size) return pooledViews[index]
            val view = LayoutInflater.from(itemView.context)
                .inflate(R.layout.cell_timeline_episode, episodesLayout, false)
            pooledViews.add(view)
            return view
        }
    }

    companion object {
        private val ROUNDED_CORNERS = RoundedCorners(8)
        private val EPISODE_OPTIONS: RequestOptions =
            RequestOptions.bitmapTransform(ROUNDED_CORNERS).sizeMultiplier(0.85f)
    }
}