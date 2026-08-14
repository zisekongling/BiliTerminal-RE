package com.RobinNotBad.BiliClient.adapter

import android.annotation.SuppressLint
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
import java.util.ArrayList

class QualityChooseAdapter(
    val context: Context
) : RecyclerView.Adapter<QualityChooseAdapter.Holder>() {

    var nameList: List<String> = ArrayList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var onItemClickListener: OnItemClickListener? = null
    var onItemLongClickListener: OnItemLongClickListener? = null

    fun getName(index: Int): String? {
        return if (nameList == null) null else nameList[index]
    }

    @NonNull
    override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(this.context).inflate(R.layout.cell_choose, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(@NonNull holder: Holder, position: Int) {
        holder.folder_name.text = nameList[position]

        holder.itemView.setOnClickListener {
            if (onItemClickListener != null) onItemClickListener!!.onItemClick(position)
        }

        holder.itemView.setOnLongClickListener {
            if (onItemLongClickListener != null) {
                onItemLongClickListener!!.onItemLongClick(position)
                return@setOnLongClickListener true
            } else false
        }
    }

    override fun getItemCount(): Int {
        return nameList.size
    }

    class Holder(@NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val folder_name: TextView = itemView.findViewById(R.id.text)
    }
}