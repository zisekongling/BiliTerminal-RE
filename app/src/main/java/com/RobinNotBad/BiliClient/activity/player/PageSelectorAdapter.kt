package com.RobinNotBad.BiliClient.activity.player

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.listener.OnItemClickListener

class PageSelectorAdapter : RecyclerView.Adapter<PageSelectorAdapter.Holder>() {
    private var pagenames: ArrayList<String>? = null
    private var selectedItemIndex = 0
    private var listener: OnItemClickListener? = null

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        this.listener = listener
    }

    fun setSelectedItemIndex(selectedItemIndex: Int) {
        val previousSelectedIndex = this.selectedItemIndex
        this.selectedItemIndex = selectedItemIndex
        notifyItemChanged(previousSelectedIndex)
        notifyItemChanged(selectedItemIndex)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setData(pagenames: ArrayList<String>, currentIndex: Int) {
        this.pagenames = pagenames
        this.selectedItemIndex = currentIndex
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.cell_page_item, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (position < 0 || pagenames == null || position >= pagenames!!.size)
            return
        holder.bind(position, selectedItemIndex == position)
    }

    override fun getItemCount(): Int {
        return pagenames?.size ?: 0
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val pageName: TextView = itemView.findViewById(R.id.page_name)

        fun bind(currentIndex: Int, isSelected: Boolean) {
            if (currentIndex < 0 || pagenames == null || currentIndex >= pagenames!!.size)
                return

            pageName.text = "P" + (currentIndex + 1) + " " + pagenames!![currentIndex]

            if (isSelected) {
                pageName.setTextColor(0xffff6699.toInt())
                itemView.setBackgroundResource(R.drawable.background_card)
            } else {
                pageName.setTextColor(0xffebe0e2.toInt())
                itemView.setBackgroundResource(R.drawable.background_card_borderless)
            }

            itemView.setOnClickListener {
                setSelectedItemIndex(currentIndex)
                listener?.onItemClick(currentIndex)
            }
        }
    }
}