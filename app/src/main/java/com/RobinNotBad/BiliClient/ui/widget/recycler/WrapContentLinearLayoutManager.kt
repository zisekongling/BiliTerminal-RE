package com.RobinNotBad.BiliClient.ui.widget.recycler

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * 用于嵌套在ScrollView中的RecyclerView，正确测量所有子项高度，使wrap_content生效
 */
class WrapContentLinearLayoutManager : androidx.recyclerview.widget.LinearLayoutManager {

    constructor(context: Context) : super(context)

    constructor(context: Context, orientation: Int, reverseLayout: Boolean) : super(context, orientation, reverseLayout)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (e: Throwable) {
            // ignore
        }
    }
}