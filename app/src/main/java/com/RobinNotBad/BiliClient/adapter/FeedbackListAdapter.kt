package com.RobinNotBad.BiliClient.adapter

import com.RobinNotBad.BiliClient.util.UiText

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.api.TerminalApi
import com.RobinNotBad.BiliClient.model.FeedbackItem
import com.RobinNotBad.BiliClient.util.FeedbackUnread
import com.google.android.material.card.MaterialCardView

/**
 * 「我的反馈」列表适配器（26.10.16 新增）。列表本身只负责展示与点击进详情；
 * 追加说明在详情页（`FeedbackDetailActivity`）。
 *
 * 手表端屏小，信息密度要低：一行只放「分类 + 状态」，正文压成两行摘要，时间单独一行弱化。
 * 写法照 `AnnouncementAdapter`（直接继承 `RecyclerView.Adapter`，Holder 继承 `RecyclerView.ViewHolder`）。
 */
class FeedbackListAdapter(
    private val context: Context,
    private val list: ArrayList<FeedbackItem>,
    private val onClick: (FeedbackItem) -> Unit
) : RecyclerView.Adapter<FeedbackListAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(context).inflate(R.layout.cell_feedback_list, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (position < 0 || position >= list.size) return
        val item = list[position]

        holder.category.text = categoryLabel(item.category)
        // 状态只用「待受理 / 已受理」——绝不要写「已处理」，见 FeedbackItem.handled 的说明
        holder.status.text = if (item.handled) UiText.of("已受理") else UiText.of("待受理")
        holder.content.text = item.content
        holder.time.text = item.ctime
        // 26.11.16：开发者回复过、且用户还没看过这条之后的新回复 —— 挂一个「新回复」标签。
        // 判断只读本地水位线，不发请求（见 FeedbackUnread）。
        holder.newReply.visibility = if (FeedbackUnread.isUnread(item)) View.VISIBLE else View.GONE
        holder.cardView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = list.size

    /**
     * 分类显示名。
     *
     * 文案复用 `strings.xml` 里既有的 `feedback_category_*`（与发送页 FeedbackActivity 同一份，
     * 避免同一批中文标签出现两处真相）。FeedbackActivity 在阶段 1-3 的不可改清单里，
     * 故这里只重复「wire 值 → 资源 id」这一步映射，不改动它。
     */
    private fun categoryLabel(value: String): String = when (value) {
        TerminalApi.CATEGORY_BUG -> context.getString(R.string.feedback_category_bug)
        TerminalApi.CATEGORY_SUGGESTION -> context.getString(R.string.feedback_category_suggestion)
        TerminalApi.CATEGORY_CONTENT -> context.getString(R.string.feedback_category_content)
        TerminalApi.CATEGORY_PERFORMANCE -> context.getString(R.string.feedback_category_performance)
        else -> context.getString(R.string.feedback_category_other)
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        val category: TextView = itemView.findViewById(R.id.category)
        val status: TextView = itemView.findViewById(R.id.status)
        val newReply: TextView = itemView.findViewById(R.id.newreply)
        val content: TextView = itemView.findViewById(R.id.content)
        val time: TextView = itemView.findViewById(R.id.time)
    }
}