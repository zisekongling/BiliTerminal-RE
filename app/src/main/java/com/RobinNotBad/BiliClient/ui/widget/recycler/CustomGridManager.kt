package com.RobinNotBad.BiliClient.ui.widget.recycler

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.util.MsgUtil

class CustomGridManager : GridLayoutManager {
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes
    )

    constructor(context: Context, spanCount: Int) : super(context, spanCount)

    constructor(context: Context, spanCount: Int, orientation: Int, reverseLayout: Boolean) : super(
        context,
        spanCount,
        orientation,
        reverseLayout
    )

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (e: Throwable) {
            MsgUtil.err("列表报错：", e)
        }
    }
}