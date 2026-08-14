package com.RobinNotBad.BiliClient.adapter.video

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.listener.OnItemClickListener
import com.RobinNotBad.BiliClient.listener.OnItemLongClickListener

class PageChooseAdapter(
    val context: Context,
    val nameList: ArrayList<String>
) : RecyclerView.Adapter<PageChooseAdapter.Holder>() {

    var onItemClickListener: OnItemClickListener? = null
    var onItemLongClickListener: OnItemLongClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(this.context).inflate(R.layout.cell_choose, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (position < 0 || position >= nameList.size)
            return

        holder.folder_name.text = nameList[position]

        holder.itemView.setOnClickListener {
            onItemClickListener?.onItemClick(position)
        }

        holder.itemView.setOnLongClickListener {
            if (onItemLongClickListener != null) {
                onItemLongClickListener!!.onItemLongClick(position)
                true
            } else
                false
        }
    }

    override fun getItemCount(): Int {
        return if (nameList != null) nameList.size else 0
    }

    class Holder(@androidx.annotation.NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val folder_name: TextView = itemView.findViewById(R.id.text)
    }
}