package com.RobinNotBad.BiliClient.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
class DragAdapter(
    private val mContext: Context,
    private val mList: MutableList<String>
) : RecyclerView.Adapter<DragAdapter.ViewHolder>() {

    companion object {
        private const val fixedPosition = -1
    }

    interface OnItemClickListener {
        fun onItemClick(position: Int)
        fun onItemLongClick(holder: ViewHolder)
    }

    private var mListener: OnItemClickListener? = null

    fun setOnItemClickListener(listener: OnItemClickListener) {
        mListener = listener
    }

    @NonNull
    override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(mContext).inflate(R.layout.item_drag_grid, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(@NonNull holder: ViewHolder, position: Int) {
        if (position < 0 || position >= mList.size)
            return
        holder.mItemTextView.text = mList[position]

        holder.mItemTextView.setOnClickListener {
            if (mListener != null) {
                val adapterPosition = holder.adapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    mListener!!.onItemClick(adapterPosition)
                }
            }
        }

        holder.mItemTextView.setOnLongClickListener {
            if (mListener != null) {
                mListener!!.onItemLongClick(holder)
                return@setOnLongClickListener true
            }
            false
        }
    }

    override fun getItemCount(): Int {
        return if (mList != null) mList.size else 0
    }

    fun getFixedPosition(): Int {
        return fixedPosition
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val mItemTextView: TextView = itemView.findViewById(R.id.item)
    }
}