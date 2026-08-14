package com.RobinNotBad.BiliClient.ui.widget.recycler

import android.content.Context
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

open class BaseHolder : RecyclerView.ViewHolder {

    private val viewArray: SparseArray<View> = SparseArray()

    constructor(viewGroup: ViewGroup, layoutId: Int) : super(
        LayoutInflater.from(viewGroup.context).inflate(layoutId, viewGroup, false)
    )

    constructor(view: View) : super(view)

    @Suppress("UNCHECKED_CAST")
    protected fun <T : View> getView(id: Int): T {
        var t = this.viewArray[id] as T?
        if (t == null) {
            val t2 = this.itemView.findViewById<T>(id)
            this.viewArray.put(id, t2)
            return t2
        }
        return t
    }

    protected fun getContext(): Context {
        return this.itemView.context
    }
}