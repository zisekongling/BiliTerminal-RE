package com.RobinNotBad.BiliClient.ui.widget

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

// 基类用 AppCompatTextView 而不是原生 TextView：布局里给跑马灯标题加的图标走
// app:drawableStartCompat（矢量），只有 AppCompatTextView 的构造函数会去读 Compat 属性；
// 换成原生 TextView 的话 app:* 属性被静默忽略，图标直接不显示（详见 docs/architecture-map.md 兼容清单）
class MarqueeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatTextView(context, attrs, defStyle) {

    init {
        setMarquee()
    }

    fun setMarquee() {
        if (!isInEditMode) {
            if (SharedPreferencesUtil.getBoolean("marquee_enable", true)) {
                isSelected = true
                ellipsize = TextUtils.TruncateAt.MARQUEE
                setSingleLine()
                marqueeRepeatLimit = -1
                isFocusable = true
                isFocusableInTouchMode = true
            } else {
                ellipsize = TextUtils.TruncateAt.END
                setSingleLine()
            }
        }
    }
}