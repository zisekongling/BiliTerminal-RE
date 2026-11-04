package com.RobinNotBad.BiliClient.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager
import com.RobinNotBad.BiliClient.util.Logu

/**
 * 图片查看器的双指缩放翻页容器。
 *
 * <p>与 [SafeViewPager] 同源的外包缺陷在这里也要兜一次：线上崩溃 159 的堆栈直接停在
 * `ViewPager.onInterceptTouchEvent`（**没有** SafeViewPager 的帧），说明图片页这个
 * 裸 `ViewPager` 子类一直是漏网路径。两个方法都走同一份 [guardPointerIndexCrash]。
 *
 * <h3>双击检测为什么放在这一层（26.11.02，反馈 #14 / #54）</h3>
 * 图片页原来靠 PhotoView（chrisbanes 2.3.0）自带的 `GestureDetector` 做双击放大，
 * 用户反馈「双击只会抖两下、切不到放大」。PhotoView 的检测器挂在**子 View** 上，
 * 事件要先穿过 [PhotoViewpager] 的 `onInterceptTouchEvent` 才轮到它；容器一拦截（或
 * 手指在抖动边缘被判成拖动）子 View 只会收到 ACTION_CANCEL，双击**识别**这一步就已经丢了，
 * 后面再怎么设 `setOnDoubleTapListener` 也救不回来。
 *
 * 这里在 [dispatchTouchEvent] 里**旁路**喂一个自己的 [GestureDetector]：
 * 它拿到的是**分发到容器的最原始事件**，不受子 View 是否被拦截影响；同时
 * **不消费事件、不改变返回值**，所以双指缩放 / 拖动 / 左右翻页的行为一点不变。
 * 判定出双击后用 [onDoubleTap] 通知宿主，由宿主决定缩放哪一张、放到多大。
 */
class PhotoViewpager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewPager(context, attrs) {

    private var mIsDisallowIntercept = false

    /**
     * 双击回调：参数是**本容器坐标系**里的触点位置（当前页铺满容器，可以直接当页面内坐标用）。
     *
     * 不设回调时本类行为与改动前完全一致。
     */
    var onDoubleTap: ((x: Float, y: Float) -> Unit)? = null

    /**
     * 只用来识别双击。`isLongpressEnabled = false`：图片页没有长按语义，
     * 关掉可以少一个挂起的延时回调（长按判定本身也会抢双击的第二次按下）。
     */
    private val doubleTapDetector: GestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val callback = onDoubleTap
                if (callback == null) return false
                Logu.d("image-zoom", "双击 (${e.x.toInt()}, ${e.y.toInt()})")
                callback(e.x, e.y)
                return true
            }
        }
    ).apply { setIsLongpressEnabled(false) }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        mIsDisallowIntercept = disallowIntercept
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // 先旁路跑一遍双击检测：必须在 super 之前，否则容器一旦拦截子 View 就收不到后续事件。
        // 这里**只看不吞**：检测器的返回值不参与派发决策，翻页 / 缩放 / 拖动完全照旧。
        if (onDoubleTap != null) {
            try {
                doubleTapDetector.onTouchEvent(ev)
            } catch (e: Exception) {
                // 手势识别失败不能影响翻页本身
                Logu.e("image-zoom", "双击检测异常：${e.message}")
            }
        }
        return if (ev.pointerCount > 1 && mIsDisallowIntercept) {
            requestDisallowInterceptTouchEvent(false)
            val handled = super.dispatchTouchEvent(ev)
            requestDisallowInterceptTouchEvent(true)
            handled
        } else {
            super.dispatchTouchEvent(ev)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean =
        guardPointerIndexCrash("PhotoViewpager") { super.onInterceptTouchEvent(ev) }

    override fun onTouchEvent(ev: MotionEvent): Boolean =
        guardPointerIndexCrash("PhotoViewpager") { super.onTouchEvent(ev) }
}
