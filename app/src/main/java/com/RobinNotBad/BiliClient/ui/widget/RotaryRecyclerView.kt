package com.RobinNotBad.BiliClient.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.util.SettingsKeys

/**
 * 支持表冠滚动的 `RecyclerView`。
 * 表冠判定与滚动逻辑见 [RotaryEncoderSupport]（三份重复实现已合并到那里）。
 *
 * 开关与灵敏度每次事件现读（26.10.17），理由同 [RotaryScrollView]。
 *
 * 覆写 `onGenericMotionEvent` 而不是用 `setOnGenericMotionListener`（26.11.07.1）：总开关关闭时
 * 走「系统优先、App 补位」那条路，需要先跑 `super` 看框架滚没滚，而监听器拿不到 `super`。
 * 框架的 `RecyclerView.onGenericMotionEvent` 滚完**仍然 return false**（只认 `AXIS_SCROLL`），
 * 所以「滚了却说自己没处理」必须靠滚动位置判断，否则事件会冒泡到 DecorView 兜底再滚一遍。
 */
class RotaryRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (RotaryEncoderSupport.isSystemFirstMode()) {
            return RotaryEncoderSupport.handleSystemFirst(
                this, event,
                RotaryEncoderSupport.sensitivityOf(SettingsKeys.UI_ROTATORY_RECYCLER),
                // 横竖两个方向都算「框架动过」：横向列表（直播、播放器分组条等）在框架路径里
                // 走的是 canScrollHorizontally 分支，只看竖直偏移会把「已经滚过」误判成「没动」。
                // 这个带符号差值是框架实际滚了多少像素，26.11.09 起还要拿来抵扣补位距离
                // （框架滚不够时补足差额，修反馈 #132）。
                { computeVerticalScrollOffset() + computeHorizontalScrollOffset() },
                { super.onGenericMotionEvent(event) }
            ) { smoothScrollBy(0, it) }
        }
        if (RotaryEncoderSupport.handle(
                this, event,
                RotaryEncoderSupport.multipleOf(SettingsKeys.UI_ROTATORY_RECYCLER),
                requestFocus = true
            ) { smoothScrollBy(0, it) }
        ) return true
        return super.onGenericMotionEvent(event)
    }
}
