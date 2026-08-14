package com.RobinNotBad.BiliClient.ui.widget.recycler

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter

abstract class AbstractAdapter<VH : BaseHolder>(
    protected val mContext: Context
) : Adapter<BaseHolder>() {

    companion object {
        const val VIEW_TYPE_FOOTER = 1025
        const val VIEW_TYPE_HEADER = 1024
    }

    protected var footerView: View? = null
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            if (field !== value) {
                field = value
                notifyDataSetChanged()
            }
        }
    protected var headerView: View? = null
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            if (field !== value) {
                field = value
                notifyDataSetChanged()
            }
        }

    abstract fun doBindViewHolder(viewHolder: VH, position: Int)

    abstract fun doCreateViewHolder(parent: ViewGroup, viewType: Int): VH

    open fun bindHeaderView(viewHolder: BaseHolder) {}

    open fun bindFooterView(viewHolder: BaseHolder) {}

    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> BaseHolder(this.headerView!!)
            VIEW_TYPE_FOOTER -> BaseHolder(this.footerView!!)
            else -> doCreateViewHolder(parent, viewType)
        }
    }

    @Suppress("UNCHECKED_CAST")
    final override fun onBindViewHolder(holder: BaseHolder, position: Int) {
        val viewType = holder.itemViewType
        when (viewType) {
            VIEW_TYPE_HEADER -> {
                bindHeaderView(holder)
                return
            }

            VIEW_TYPE_FOOTER -> {
                bindFooterView(holder)
                return
            }
        }
        val realPosition = position - getHeaderViewCount()
        if (realPosition < 0)
            return
        doBindViewHolder(holder as VH, realPosition)
    }

    fun getExtraViewCount(): Int {
        val i = if (this.headerView != null) 1 else 0
        return if (this.footerView != null) i + 1 else i
    }

    fun getHeaderViewCount(): Int {
        return if (this.headerView == null) 0 else 1
    }

    fun getFooterViewCount(): Int {
        return if (this.footerView == null) 0 else 1
    }
}