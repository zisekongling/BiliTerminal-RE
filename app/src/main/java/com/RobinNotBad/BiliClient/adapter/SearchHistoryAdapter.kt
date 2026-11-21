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
import com.RobinNotBad.BiliClient.listener.OnItemLongClickListener
import com.RobinNotBad.BiliClient.util.StringUtil
import java.util.ArrayList

class SearchHistoryAdapter(
    val context: Context,
    val historyList: ArrayList<String>
) : RecyclerView.Adapter<SearchHistoryAdapter.BtnListHolder>() {

    var longClickListener: OnItemLongClickListener? = null
    var clickListener: OnItemClickListener? = null

    fun setOnLongClickListener(listener: OnItemLongClickListener) {
        this.longClickListener = listener
    }

    fun setOnClickListener(listener: OnItemClickListener) {
        this.clickListener = listener
    }

    @NonNull
    override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): BtnListHolder {
        val view = LayoutInflater.from(this.context).inflate(R.layout.cell_choose, parent, false)
        return BtnListHolder(view)
    }

    override fun onBindViewHolder(@NonNull holder: BtnListHolder, position: Int) {
        if (position < 0 || position >= historyList.size)
            return
        holder.show(historyList[position])

        // 位置一律在事件发生的那一刻重新取，不要用绑定时捕获的 position：
        // 历史列表增删之后旧下标就过期了，点一下就是 ArrayList 越界
        // （26.11.07 线上崩溃 #230：IndexOutOfBoundsException: Index: 1, Size: 1）。
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION || pos >= historyList.size)
                return@setOnClickListener
            clickListener?.onItemClick(pos)
        }

        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION || pos >= historyList.size)
                return@setOnLongClickListener false
            val listener = longClickListener ?: return@setOnLongClickListener false
            listener.onItemLongClick(pos)
            true
        }
    }

    override fun getItemCount(): Int {
        return if (historyList != null) historyList.size else 0
    }

    class BtnListHolder(@NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text_view: TextView = itemView.findViewById(R.id.text)

        fun show(text: String) {
            text_view.text = StringUtil.htmlToString(text)
        }
    }
}