package com.RobinNotBad.BiliClient.adapter.video

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.search.SearchActivity
import com.RobinNotBad.BiliClient.model.HotSearchCard
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.util.GlideUtil

class HotSearchAdapter(
    val context: Context,
    val list: List<HotSearchCard>
) : RecyclerView.Adapter<HotSearchAdapter.Holder>() {

    companion object {
        @JvmStatic
        fun formatHeat(heat: Long): String {
            if (heat < 10000) return heat.toString()
            return String.format(UiText.of("%.1f万"), heat / 10000.0)
        }
    }

    @NonNull
    override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_hot_search, parent, false)
        return Holder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(@NonNull holder: Holder, position: Int) {
        val card = list[position]
        val rank = position + 1
        holder.rankText.text = rank.toString()
        // 前三名用当前主题主色，其余用主题三级文字色（此前写死 B站官方粉 #FB7299 / 灰 #999999）
        holder.rankText.setTextColor(if (rank <= 3) ColorScheme.PRIMARY else ColorScheme.TEXT_TERTIARY)
        // 反馈 #92：keywordText 是 MarqueeTextView，init 里已经置好 isSelected + MARQUEE。
        // RecyclerView 复用的是同一个 View 实例，选中态不会因为 bind/滚出屏幕/从搜索页返回而被清，
        // 所以这里（以及全仓库其它用 MarqueeTextView 的列表）都不需要重新 setMarquee()/setSelected()；
        // setText 本身会让 TextView 重新排版并重开跑马灯。
        holder.keywordText.text = card.showName
        if (card.heatScore > 0) {
            holder.heatText.text = formatHeat(card.heatScore)
        } else {
            holder.heatText.text = ""
        }
        if (card.icon.isNullOrEmpty()) {
            holder.iconView.visibility = View.GONE
        } else {
            holder.iconView.visibility = View.VISIBLE
            GlideUtil.request(holder.iconView, card.icon, 2, 0)
        }
        holder.itemView.setOnClickListener {
            val intent = Intent(context, SearchActivity::class.java)
            intent.putExtra("keyword", card.keyword)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = list.size

    class Holder(@NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rankText: TextView = itemView.findViewById(R.id.rankText)
        val iconView: ImageView = itemView.findViewById(R.id.iconView)
        val keywordText: TextView = itemView.findViewById(R.id.keywordText)
        val heatText: TextView = itemView.findViewById(R.id.heatText)
    }
}