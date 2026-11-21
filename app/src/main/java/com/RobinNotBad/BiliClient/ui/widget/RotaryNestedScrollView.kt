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
