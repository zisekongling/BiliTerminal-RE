package com.RobinNotBad.BiliClient.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.listener.OnItemClickListener
import com.RobinNotBad.BiliClient.util.StringUtil
import java.util.ArrayList

class SearchSuggestionsAdapter(
    val context: Context,
    val suggestionsList: ArrayList<String>
) : RecyclerView.Adapter<SearchSuggestionsAdapter.SuggestionHolder>() {

    var clickListener: OnItemClickListener? = null

    fun setOnClickListener(listener: OnItemClickListener) {
        this.clickListener = listener
    }

    @NonNull
    override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): SuggestionHolder {
        val view = LayoutInflater.from(this.context).inflate(R.layout.cell_choose, parent, false)
        return SuggestionHolder(view)
    }

    override fun onBindViewHolder(@NonNull holder: SuggestionHolder, position: Int) {
        if (position < 0 || position >= suggestionsList.size)
            return
        holder.show(suggestionsList[position])

        // 同 SearchHistoryAdapter：位置在点击那一刻重新取，不用绑定时的捕获值
        // （建议列表每轮请求都会 clear + addAll，旧下标很容易过期）
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION || pos >= suggestionsList.size)
                return@setOnClickListener
            clickListener?.onItemClick(pos)
        }
    }

    override fun getItemCount(): Int {
        return if (suggestionsList != null) suggestionsList.size else 0
    }

    class SuggestionHolder(@NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text_view: TextView = itemView.findViewById(R.id.text)

        fun show(text: String) {
            text_view.text = StringUtil.htmlToString(text)
        }
    }
}