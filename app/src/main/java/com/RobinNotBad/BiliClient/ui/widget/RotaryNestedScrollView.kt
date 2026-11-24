package com.RobinNotBad.BiliClient.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.widget.NestedScrollView
import com.RobinNotBad.BiliClient.util.SettingsKeys

/**
 * 支持表冠滚动的 `NestedScrollView`。
 *
 * 表冠判定与滚动逻辑见 [RotaryEncoderSupport]（三份重复实现已合并到那里）。
 * 与另外两个控件的两点差异原样保留：
 * 1. 走 `dispatchGenericMotionEvent` 覆写而不是 `onGenericMotionEvent`——它常常是页面根容器、
 *    里面还套着自己的 `RotaryRecyclerView`，在 dispatch 层先判一次能保证「页面滚」优先于「内层滚」；
 * 2. 滚动后**不**抢焦点。
 *
 * 开关与灵敏度改成每次事件现读（26.10.17），理由同 [RotaryScrollView]。
 * 26.11.07.1 起总开关关闭时先让框架/子控件处理，谁都没动才由 App 补位，见
 * [RotaryEncoderSupport.handleSystemFirst]。
 *
 * 反馈 #132（详情页关掉表冠适配后转半天不动）就出在这个控件上：框架的
 * `NestedScrollView.onGenericMotionEvent` 用主题属性 `android:listPreferredItemHeight`
 * 换算滚动量（Material/AppCompat 默认 48dp），比 App 口径（64dp × 2）小得多，
 * 26.11.09 起由 [RotaryEncoderSupport.handleSystemFirst] 按差额补足。
 */
class RotaryNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        // 26.11.23 修反馈 #279（同一台 OPPO：设置页能滚、视频详情-简介整页转不动）：
        // 兼容模式把滚轮源整段让给框架，可本容器的框架实现（androidx NestedScrollView）只读
        // AXIS_SCROLL，把表冠报成滚轮的 ROM 把量放在 AXIS_VSCROLL 上——交过去等于交给一个不处理的人。
        // 判据与「为什么不会与框架叠加」见 [RotaryEncoderSupport.handleCompatWheelFallback]；
        // 不在兼容模式、或框架这次真的会处理时它返回 false，下面两条老路径一点不变。
        if (RotaryEncoderSupport.handleCompatWheelFallback(
                this, event,
                RotaryEncoderSupport.sensitivityOf(SettingsKeys.UI_ROTATORY_SCROLL)
            ) { smoothScrollBy(0, it) }
        ) return true
        if (RotaryEncoderSupport.isSystemFirstMode()) {
            return RotaryEncoderSupport.handleSystemFirst(
                this, event,
                RotaryEncoderSupport.sensitivityOf(SettingsKeys.UI_ROTATORY_SCROLL),
                { scrollY },
                { super.dispatchGenericMotionEvent(event) }
            ) { smoothScrollBy(0, it) }
        }
        val handled = RotaryEncoderSupport.handle(
            this, event,
            RotaryEncoderSupport.multipleOf(SettingsKeys.UI_ROTATORY_SCROLL),
            requestFocus = false
        ) { smoothScrollBy(0, it) }
        if (handled) return true
        return super.dispatchGenericMotionEvent(event)
    }
}
