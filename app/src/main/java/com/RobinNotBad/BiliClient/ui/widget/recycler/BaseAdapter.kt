package com.RobinNotBad.BiliClient.ui.widget.recycler

import android.annotation.SuppressLint
import android.content.Context
import androidx.recyclerview.widget.RecyclerView

abstract class BaseAdapter<M, VH : BaseHolder> : AbstractAdapter<VH> {

    private val dataList: MutableList<M>

    open fun getViewType(position: Int): Int {
        return 0
    }

    constructor(context: Context) : super(context) {
        this.dataList = ArrayList()
    }

    constructor(context: Context, dataList: List<M>) : super(context) {
        this.dataList = ArrayList()
        this.dataList.addAll(dataList)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun fillList(list: List<M>): Boolean {
        this.dataList.clear()
        val result = this.dataList.addAll(list)
        notifyDataSetChanged()
        return result
    }

    fun appendItem(item: M): Boolean {
        val size = this.dataList.size
        val result = this.dataList.add(item)
        if (result) {
            notifyItemInserted(size + getHeaderViewCount())
        }
        return result
    }

    fun appendList(list: List<M>): Boolean {
        if (list.isEmpty())
            return false
        val size = this.dataList.size
        val result = this.dataList.addAll(list)
        if (result) {
            notifyItemRangeInserted(size + getHeaderViewCount(), list.size)
        }
        return result
    }

    fun preposeItem(item: M) {
        this.dataList.add(0, item)
        notifyItemInserted(getHeaderViewCount())
        notifyItemRangeChanged(getHeaderViewCount(), itemCount)
    }

    fun preposeList(list: List<M>) {
        if (list.isEmpty())
            return
        this.dataList.addAll(0, list)
        notifyItemRangeInserted(getHeaderViewCount(), list.size)
    }

    fun updateItem(position: Int, item: M) {
        if (position < 0 || position >= this.dataList.size)
            return
        this.dataList[position] = item
        notifyItemChanged(getHeaderViewCount() + position)
    }

    fun updateItem(originalItem: M, newItem: M) {
        val index = this.dataList.indexOf(originalItem)
        if (index >= 0 && index < this.dataList.size) {
            this.dataList[index] = newItem
            notifyItemChanged(getHeaderViewCount() + index)
        }
    }

    fun removeItem(position: Int) {
        val realPosition = position - getHeaderViewCount()
        if (realPosition < 0 || realPosition >= this.dataList.size)
            return
        this.dataList.removeAt(realPosition)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, itemCount - position)
    }

    fun removeItem(item: M) {
        val index = this.dataList.indexOf(item)
        if (index >= 0 && index < this.dataList.size) {
            this.dataList.removeAt(index)
            val position = getHeaderViewCount() + index
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, itemCount - position)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearData() {
        this.dataList.clear()
        notifyDataSetChanged()
    }

    final override fun getItemViewType(position: Int): Int {
        if (this.headerView != null && position == 0) {
            return VIEW_TYPE_HEADER
        } else if (this.footerView != null && position == this.dataList.size + getHeaderViewCount()) {
            return VIEW_TYPE_FOOTER
        }
        return getViewType(position)
    }

    override fun getItemCount(): Int {
        return this.dataList.size + getExtraViewCount()
    }

    fun getItem(position: Int): M? {
        if (position < 0)
            return null
        val realPosition = position - getHeaderViewCount()
        if (realPosition < 0 || realPosition >= this.dataList.size) {
            return null
        }
        return this.dataList[realPosition]
    }

    fun getItem(vh: VH): M? {
        val position = vh.adapterPosition
        if (position == RecyclerView.NO_POSITION)
            return null
        return getItem(position)
    }

    fun getAllData(): List<M> {
        return this.dataList
    }
}