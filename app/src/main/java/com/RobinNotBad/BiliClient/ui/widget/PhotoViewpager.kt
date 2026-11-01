package com.RobinNotBad.BiliClient.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

/**
 * 图片查看器的双指缩放翻页容器。
 *
 * <p>与 [SafeViewPager] 同源的外包缺陷在这里也要兜一次：线上崩溃 159 的堆栈直接停在
 * `ViewPager.onInterceptTouchEvent`（**没有** SafeViewPager 的帧），说明图片页这个
 * 裸 `ViewPager` 子类一直是漏网路径。两个方法都走同一份 [guardPointerIndexCrash]。
 */
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

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean =
        guardPointerIndexCrash("PhotoViewpager") { super.onInterceptTouchEvent(ev) }

    override fun onTouchEvent(ev: MotionEvent): Boolean =
        guardPointerIndexCrash("PhotoViewpager") { super.onTouchEvent(ev) }
}
