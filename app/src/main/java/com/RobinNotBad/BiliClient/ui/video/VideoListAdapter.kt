package com.RobinNotBad.BiliClient.ui.video

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.network.model.PopularVideoItem
import com.RobinNotBad.BiliClient.ui.theme.BiliColors
import com.RobinNotBad.BiliClient.ui.theme.BiliDimens
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager

class VideoListAdapter(
    private val context: Context
) : ListAdapter<PopularVideoItem, VideoListAdapter.VideoViewHolder>(DiffCallback()) {

    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewWithTag("title")
        val authorText: TextView = itemView.findViewWithTag("author")
        val statText: TextView = itemView.findViewWithTag("stats")
        val durationText: TextView = itemView.findViewWithTag("duration")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val itemView = createVideoItemView(parent.context)
        return VideoViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val item = getItem(position)
        val density = context.resources.displayMetrics.density

        holder.titleText.text = item.title
        holder.authorText.text = item.owner.name
        holder.durationText.text = formatDuration(item.duration)
        holder.statText.text = "${formatCount(item.stat_view)}播放  |  ${formatCount(item.stat_danmaku)}弹幕"

        holder.titleText.setTextColor(ThemeManager.TEXT_PRIMARY)
        holder.authorText.setTextColor(ThemeManager.TEXT_SECONDARY)
        holder.statText.setTextColor(ThemeManager.TEXT_TERTIARY)
        holder.durationText.setTextColor(ThemeManager.TEXT_PRIMARY)

        holder.itemView.setOnClickListener {
            BiliTerminal.jumpToVideo(context, item.aid)
        }
    }

    private fun createVideoItemView(context: Context): View {
        val density = context.resources.displayMetrics.density

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padding = (BiliDimens.SPACING_MD * density).toInt()
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(ThemeManager.CARD)
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )

            val coverSize = (BiliDimens.WATCH_COVER_SIZE * density).toInt()
            val coverPlaceholder = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(coverSize, coverSize).apply {
                    rightMargin = (BiliDimens.SPACING_MD * density).toInt()
                }
                setBackgroundColor(ThemeManager.PRIMARY_LIGHT)
            }

            val textColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val titleText = TextView(context).apply {
                tag = "title"
                textSize = BiliDimens.BODY_LARGE
                setTextColor(ThemeManager.TEXT_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val authorText = TextView(context).apply {
                tag = "author"
                textSize = BiliDimens.BODY_SMALL
                setTextColor(ThemeManager.TEXT_SECONDARY)
                maxLines = 1
                val topPad = (2 * density).toInt()
                setPadding(0, topPad, 0, 0)
            }

            val statText = TextView(context).apply {
                tag = "stats"
                textSize = BiliDimens.CAPTION
                setTextColor(ThemeManager.TEXT_TERTIARY)
                maxLines = 1
                val topPad = (2 * density).toInt()
                setPadding(0, topPad, 0, 0)
            }

            val durationText = TextView(context).apply {
                tag = "duration"
                textSize = BiliDimens.CAPTION
                alpha = 0.7f
            }

            textColumn.addView(titleText)
            textColumn.addView(authorText)
            textColumn.addView(statText)
            addView(coverPlaceholder)
            addView(textColumn)
            addView(durationText)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PopularVideoItem>() {
        override fun areItemsTheSame(oldItem: PopularVideoItem, newItem: PopularVideoItem): Boolean {
            return oldItem.aid == newItem.aid
        }

        override fun areContentsTheSame(oldItem: PopularVideoItem, newItem: PopularVideoItem): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        fun formatCount(count: Int): String = when {
            count >= 10000 -> "${"%.1f".format(count / 10000f)}万"
            else -> count.toString()
        }

        fun formatDuration(seconds: Long): String {
            val min = seconds / 60
            val sec = seconds % 60
            return "${"%02d".format(min)}:${"%02d".format(sec)}"
        }
    }
}