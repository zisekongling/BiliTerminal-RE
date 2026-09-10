package com.RobinNotBad.BiliClient.ui.widget

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.util.SettingsKeys

/**
 * 支持表冠滚动的 `RecyclerView`。
 * 表冠判定与滚动逻辑见 [RotaryEncoderSupport]（三份重复实现已合并到那里）。
 */
class RotaryRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var scrollMultiple = 0f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scrollMultiple = RotaryEncoderSupport.multipleOf(SettingsKeys.UI_ROTATORY_RECYCLER)
        if (scrollMultiple > 0f) {
            setOnGenericMotionListener { _, event ->
                RotaryEncoderSupport.handle(this, event, scrollMultiple, requestFocus = true) {
                    smoothScrollBy(0, it)
                }
            }
        }
    }
}
