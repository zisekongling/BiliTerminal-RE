package com.RobinNotBad.BiliClient.adapter.note

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.ImageViewerActivity
import com.RobinNotBad.BiliClient.api.NoteApi
import com.RobinNotBad.BiliClient.model.Note
import com.RobinNotBad.BiliClient.model.NoteContentItem
import com.RobinNotBad.BiliClient.model.NoteTextRun
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.TimeUtil
import java.util.ArrayList

/**
 * 笔记详情页的正文适配器（26.11.23）。
 *
 * 正文在 `NoteApi.buildContent` 里已经合并成「一行一项」：position 0 是头部，
 * 之后每个元素是一段文字 / 一张图片 / 一个跳转标签。**不**用一个大 TextView 一次渲染整篇：
 * 那样图片只能显示成「[图片]」，长笔记也得整篇一次性排版。
 */
class NoteContentAdapter(
    private val context: Context,
    private val note: Note,
    private val items: List<NoteContentItem>,
    private val onTagClick: (NoteContentItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_TEXT = 1
        private const val TYPE_IMAGE = 2
        private const val TYPE_TAG = 3
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.noteTitle)
        val info: TextView = view.findViewById(R.id.noteInfo)
        val summary: TextView = view.findViewById(R.id.noteSummary)
        val video: TextView = view.findViewById(R.id.noteVideo)
    }

    class TextHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.noteText)
    }

    class ImageHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.noteImage)

        /** 上一次绑的地址：列表复用时会重复绑定同一个 holder，同地址不必再走一遍 Glide。 */
        var lastUrl: String? = null
    }

    class TagHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tag: TextView = view.findViewById(R.id.noteTag)
    }

    override fun getItemCount(): Int = items.size + 1

    override fun getItemViewType(position: Int): Int {
        if (position <= 0) return TYPE_HEADER
        val index = position - 1
        if (index >= items.size) return TYPE_HEADER
        return when (items[index].type) {
            NoteContentItem.TYPE_IMAGE -> TYPE_IMAGE
            NoteContentItem.TYPE_TAG -> TYPE_TAG
            else -> TYPE_TEXT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(context)
        return when (viewType) {
            TYPE_TEXT -> TextHolder(inflater.inflate(R.layout.cell_note_text, parent, false))
            TYPE_IMAGE -> ImageHolder(inflater.inflate(R.layout.cell_note_image, parent, false))
            TYPE_TAG -> TagHolder(inflater.inflate(R.layout.cell_note_tag, parent, false))
            else -> HeaderHolder(inflater.inflate(R.layout.cell_note_header, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderHolder) {
            bindHeader(holder)
            return
        }
        val index = position - 1
        if (index < 0 || index >= items.size) return
        val item = items[index]
        when (holder) {
            is TextHolder -> holder.text.text = styledText(item)
            is ImageHolder -> bindImage(holder, item)
            is TagHolder -> {
                holder.tag.text = tagText(item)
                holder.tag.setOnClickListener { onTagClick(item) }
            }
            else -> Unit
        }
    }

    private fun bindHeader(holder: HeaderHolder) {
        holder.title.text = note.title.ifEmpty { context.getString(R.string.uitext_noteactivity_005) }

        val parts = ArrayList<String>()
        if (note.authorName.isNotEmpty()) {
            parts.add(context.getString(R.string.note_detail_author, note.authorName))
        }
        if (note.pubtime > 0L) parts.add(TimeUtil.formatDate(note.pubtime * 1000L))
        if (note.likes > 0) parts.add(context.getString(R.string.note_detail_likes, note.likes))
        holder.info.text = parts.joinToString(" · ")
        holder.info.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE

        holder.summary.text = note.summary
        holder.summary.visibility = if (note.summary.isEmpty()) View.GONE else View.VISIBLE

        if (note.videoTitle.isEmpty()) {
            holder.video.visibility = View.GONE
        } else {
            holder.video.text = context.getString(R.string.note_detail_video, note.videoTitle)
            holder.video.visibility = View.VISIBLE
        }
    }

    /** 把段落文本与它带的样式段拼成 Spannable。没有样式段时直接返回纯文本，省一次对象分配。 */
    private fun styledText(item: NoteContentItem): CharSequence {
        if (item.runs.isEmpty()) return item.text
        val builder = SpannableStringBuilder(item.text)
        val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        for (run: NoteTextRun in item.runs) {
            val start = run.start.coerceIn(0, builder.length)
            val end = run.end.coerceIn(start, builder.length)
            if (end <= start) continue
            if (run.bold) builder.setSpan(StyleSpan(Typeface.BOLD), start, end, flags)
            if (run.underline) builder.setSpan(UnderlineSpan(), start, end, flags)
            if (run.strike) builder.setSpan(StrikethroughSpan(), start, end, flags)
            parseColor(run.color)?.let { builder.setSpan(ForegroundColorSpan(it), start, end, flags) }
            parseColor(run.background)?.let { builder.setSpan(BackgroundColorSpan(it), start, end, flags) }
        }
        return builder
    }

    /** 服务端给的颜色是 `#RRGGBB`；解析不出来就当没有，不能让一朵坏样式把整篇笔记渲染崩掉。 */
    private fun parseColor(value: String): Int? {
        if (value.isEmpty()) return null
        return try {
            android.graphics.Color.parseColor(value)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun bindImage(holder: ImageHolder, item: NoteContentItem) {
        val url = item.imageUrl
        if (url.isEmpty()) return
        if (url != holder.lastUrl) {
            holder.lastUrl = url
            GlideUtil.with(holder.image).asDrawable().load(url)
                .apply(GlideUtil.thumbOptions(holder.image))
                .apply(GlideUtil.withPlaceholder(holder.image.context, R.mipmap.placeholder))
                .transition(GlideUtil.getTransitionOptions())
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(holder.image)
        }
        // ImageViewerActivity 只认 imageList、没有起始下标的 extra，所以一张图一张票
        holder.image.setOnClickListener {
            val intent = Intent(context, ImageViewerActivity::class.java)
            intent.putExtra("imageList", ArrayList(listOf(url)))
            context.startActivity(intent)
        }
    }

    /** 标签文案与旧版一致：分P 带序号，没有分P 信息就只说进度。 */
    private fun tagText(item: NoteContentItem): String {
        val seconds = NoteApi.formatTagSeconds(item.tagSeconds)
        return if (item.tagIndex >= 0) {
            context.getString(R.string.uitext_noteactivity_009, item.tagIndex + 1, seconds)
        } else {
            context.getString(R.string.uitext_noteactivity_010, seconds)
        }
    }
}
