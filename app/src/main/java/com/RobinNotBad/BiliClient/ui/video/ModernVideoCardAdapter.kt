package com.RobinNotBad.BiliClient.ui.video

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.ui.theme.BiliDimens
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import com.RobinNotBad.BiliClient.ui.video.viewmodel.VideoCardItem

class ModernVideoCardAdapter(
    private val context: Context,
    private val onClick: (VideoCardItem) -> Unit
) : ListAdapter<VideoCardItem, ModernVideoCardAdapter.Holder>(DiffCallback()) {

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateLabelText: TextView = itemView.findViewWithTag("dateLabel")
        val titleText: TextView = itemView.findViewWithTag("title")
        val authorText: TextView = itemView.findViewWithTag("author")
        val statText: TextView = itemView.findViewWithTag("stats")
        val durationText: TextView = itemView.findViewWithTag("duration")
        val cardContent: View? = itemView.findViewWithTag("cardContent")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(createItemView(parent.context))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        holder.titleText.text = item.title
        holder.authorText.text = item.author
        holder.durationText.text = formatDuration(item.duration)
        holder.statText.text = "${formatCount(item.playCount)}播放  ${formatCount(item.danmakuCount)}弹幕"
        holder.titleText.setTextColor(ThemeManager.TEXT_PRIMARY)
        holder.authorText.setTextColor(ThemeManager.TEXT_SECONDARY)
        holder.statText.setTextColor(ThemeManager.TEXT_TERTIARY)
        holder.itemView.setOnClickListener { onClick(item) }

        // Show/hide date label
        if (item.dateLabel.isNotEmpty()) {
            holder.dateLabelText.text = item.dateLabel
            holder.dateLabelText.visibility = View.VISIBLE
        } else {
            holder.dateLabelText.visibility = View.GONE
        }
    }

    private fun createItemView(context: Context): View {
        val density = context.resources.displayMetrics.density

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            // Date label (shown only when dateLabel is not empty)
            val dateLabel = TextView(context).apply {
                tag = "dateLabel"
                textSize = BiliDimens.TITLE_SMALL
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ThemeManager.PRIMARY)
                visibility = View.GONE
                val padH = (BiliDimens.SPACING_LG * density).toInt()
                val padV = (BiliDimens.SPACING_MD * density).toInt()
                setPadding(padH, padV, padH, (BiliDimens.SPACING_SM * density).toInt())
            }
            addView(dateLabel)

            // Video card content
            val cardContent = LinearLayout(context).apply {
                tag = "cardContent"
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val pad = (BiliDimens.SPACING_MD * density).toInt()
                val padH = (BiliDimens.SPACING_LG * density).toInt()
                setPadding(padH, pad, padH, pad)
                setBackgroundColor(ThemeManager.CARD)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )

                val cover = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (BiliDimens.WATCH_COVER_SIZE * density).toInt(),
                        (BiliDimens.WATCH_COVER_SIZE * density).toInt()
                    ).apply { rightMargin = (BiliDimens.SPACING_MD * density).toInt() }
                    setBackgroundColor(ThemeManager.PRIMARY_LIGHT)
                }

                val textCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val title = TextView(context).apply {
                    tag = "title"
                    textSize = BiliDimens.BODY_LARGE
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }

                val author = TextView(context).apply {
                    tag = "author"
                    textSize = BiliDimens.BODY_SMALL
                    setPadding(0, (2 * density).toInt(), 0, 0)
                    maxLines = 1
                }

                val stats = TextView(context).apply {
                    tag = "stats"
                    textSize = BiliDimens.CAPTION
                    setPadding(0, (2 * density).toInt(), 0, 0)
                    maxLines = 1
                }

                val duration = TextView(context).apply {
                    tag = "duration"
                    textSize = BiliDimens.CAPTION
                    alpha = 0.7f
                }

                textCol.addView(title)
                textCol.addView(author)
                textCol.addView(stats)
                addView(cover)
                addView(textCol)
                addView(duration)
            }
            addView(cardContent)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<VideoCardItem>() {
        override fun areItemsTheSame(o: VideoCardItem, n: VideoCardItem) = o.aid == n.aid
        override fun areContentsTheSame(o: VideoCardItem, n: VideoCardItem) = o == n
    }

    companion object {
        fun formatCount(c: Int) = when {
            c >= 10000 -> "${"%.1f".format(c / 10000f)}万"
            else -> c.toString()
        }
        fun formatDuration(s: Long): String {
            val m = s / 60; val se = s % 60
            return "${"%02d".format(m)}:${"%02d".format(se)}"
        }
    }
}