package com.RobinNotBad.BiliClient.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

class PhotoViewpager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewPager(context, attrs) {

    private var mIsDisallowIntercept = false

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        mIsDisallowIntercept = disallowIntercept
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount > 1 && mIsDisallowIntercept) {
            requestDisallowInterceptTouchEvent(false)
            val handled = super.dispatchTouchEvent(ev)
            requestDisallowInterceptTouchEvent(true)
            return handled
        } else {
            return super.dispatchTouchEvent(ev)
        }
    }
}