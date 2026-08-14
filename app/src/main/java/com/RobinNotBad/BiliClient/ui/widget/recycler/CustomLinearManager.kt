package com.RobinNotBad.BiliClient.ui.widget.recycler

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.util.MsgUtil

class CustomLinearManager : LinearLayoutManager {
    constructor(context: Context) : super(context)

    constructor(context: Context, orientation: Int, reverseLayout: Boolean) : super(
        context,
        orientation,
        reverseLayout
    )

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes
    )

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (e: Throwable) {
            MsgUtil.err("列表报错：", e)
        }
    }
}