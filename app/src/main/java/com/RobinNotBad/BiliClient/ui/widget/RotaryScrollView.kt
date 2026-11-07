package com.RobinNotBad.BiliClient.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ScrollView
import com.RobinNotBad.BiliClient.util.SettingsKeys

/**
 * 支持表冠滚动的 `ScrollView`。
 * 表冠判定与滚动逻辑见 [RotaryEncoderSupport]（三份重复实现已合并到那里）。
 *
 * 开关与灵敏度改为每次事件现读（26.10.17）：旧写法在 `onAttachedToWindow` 里判 `> 0` 才挂，
 * 用户在别的页面把灵敏度从 0 调上来后，已在返回栈里的页面永远不会再挂一次，
 * 表现就是「设置改了不生效」。
 *
 * 26.11.07.1 起覆写 `onGenericMotionEvent`：「系统优先、App 补位」需要先跑 `super`，
 * 再用 `scrollY` 的变化判断框架到底动没动（详情见 [RotaryEncoderSupport.handleSystemFirst]）。
 */
class RotaryScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (RotaryEncoderSupport.isSystemFirstMode()) {
            return RotaryEncoderSupport.handleSystemFirst(
                this, event,
                RotaryEncoderSupport.sensitivityOf(SettingsKeys.UI_ROTATORY_SCROLL),
                { scrollY },
                { super.onGenericMotionEvent(event) }
            ) { smoothScrollBy(0, it) }
        }
        if (RotaryEncoderSupport.handle(
                this, event,
                RotaryEncoderSupport.multipleOf(SettingsKeys.UI_ROTATORY_SCROLL),
                requestFocus = true
            ) { smoothScrollBy(0, it) }
        ) return true
        return super.onGenericMotionEvent(event)
    }
}
