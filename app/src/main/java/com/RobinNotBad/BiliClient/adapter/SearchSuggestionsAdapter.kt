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

        holder.itemView.setOnClickListener {
            if (clickListener != null) {
                clickListener!!.onItemClick(position)
            }
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