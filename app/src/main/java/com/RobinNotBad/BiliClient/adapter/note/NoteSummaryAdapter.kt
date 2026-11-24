package com.RobinNotBad.BiliClient.adapter.note

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.model.NoteSummary
import com.RobinNotBad.BiliClient.util.TimeUtil

/**
 * 笔记列表（26.11.23）：第一行是「我的笔记」（有私有笔记时），后面是公开笔记。
 *
 * 两行长得一样，差别只在信息行——私有笔记没有发布时间与点赞数，改用一句说明代替，
 * 免得出现一条什么都不写、看着像坏掉的行。
 */
class NoteSummaryAdapter(
    private val context: Context,
    private val list: ArrayList<NoteSummary>,
    private val onItemClick: (NoteSummary) -> Unit
) : RecyclerView.Adapter<NoteSummaryAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.cardView)
        val title: TextView = view.findViewById(R.id.noteTitle)
        val summary: TextView = view.findViewById(R.id.noteSummary)
        val info: TextView = view.findViewById(R.id.noteInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(context).inflate(R.layout.cell_note_summary, parent, false))

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (position < 0 || position >= list.size) return
        val item = list[position]

        holder.title.text = item.title.ifEmpty { context.getString(R.string.uitext_noteactivity_005) }

        if (item.own) {
            // 私有笔记不预取正文（多一次请求只为一行预览不划算），固定说明比空白清楚
            holder.summary.text = context.getString(R.string.note_list_own_info)
            holder.summary.visibility = View.VISIBLE
            holder.info.visibility = View.GONE
        } else {
            holder.summary.text = item.summary
            holder.summary.visibility = if (item.summary.isEmpty()) View.GONE else View.VISIBLE

            val parts = ArrayList<String>()
            if (item.authorName.isNotEmpty()) parts.add(item.authorName)
            if (item.pubtime > 0L) parts.add(TimeUtil.formatDate(item.pubtime * 1000L))
            if (item.likes > 0) parts.add(context.getString(R.string.note_list_likes, item.likes))
            holder.info.text = parts.joinToString(" · ")
            holder.info.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
        }

        holder.card.setOnClickListener { onItemClick(item) }
    }
}
