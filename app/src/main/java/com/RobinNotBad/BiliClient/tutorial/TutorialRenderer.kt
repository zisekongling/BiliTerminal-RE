package com.RobinNotBad.BiliClient.tutorial

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan

/**
 * 把 [TutorialSpan] 列表渲染成带样式的文本。
 *
 * 等价于旧 `TutorialHelper.loadText()`，区别是数据来源从 XML 换成了 DSL，
 * 并且颜色解析失败时回退白色（旧实现直接 `Color.parseColor` 抛异常）。
 */
object TutorialRenderer {

    fun toSpannable(spans: List<TutorialSpan>): CharSequence {
        val builder = SpannableStringBuilder()
        for (span in spans) {
            val text = span.text ?: continue
            val start = builder.length
            builder.append(text)
            // 纯换行片段不需要挂样式
            if (text == "\n") continue

            builder.setSpan(
                ForegroundColorSpan(parseColor(span.color)),
                start,
                builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            val styleSpan = when (span.style) {
                TutorialStyle.BOLD -> StyleSpan(Typeface.BOLD)
                TutorialStyle.ITALIC -> StyleSpan(Typeface.ITALIC)
                TutorialStyle.UNDERLINE -> UnderlineSpan()
                TutorialStyle.STRIKE -> StrikethroughSpan()
                TutorialStyle.NORMAL -> null
            }
            if (styleSpan != null) {
                builder.setSpan(styleSpan, start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return builder
    }

    private fun parseColor(color: String): Int = try {
        Color.parseColor(color)
    } catch (e: IllegalArgumentException) {
        Color.WHITE
    }
}
