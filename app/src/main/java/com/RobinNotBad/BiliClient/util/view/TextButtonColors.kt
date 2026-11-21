package com.RobinNotBad.BiliClient.util.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import com.google.android.material.button.MaterialButton

/**
 * Material 文字按钮（TextButton）老框架配色兼容。
 *
 * **坑**：Material 1.12 的文字按钮把文字色/底色写成「颜色状态列表里引用主题属性」——
 * `res/color/m3_text_button_foreground_color_selector.xml`、`m3_text_button_background_color_selector.xml`
 * （M3 样式）与 `mtrl_text_btn_text_color_selector.xml`、`mtrl_btn_text_btn_bg_color_selector.xml`（M2 样式）
 * 的每个 item 都是 `?attr/colorPrimary`、`?attr/colorOnContainer` 这种主题属性引用。
 *
 * 而**颜色状态列表里的主题属性引用要 API 23 才解析得了**：API 21/22 上框架取不到值，
 * 会退回 `android.content.res.ColorStateList` 内部的哨兵默认色 `DEFAULT_COLOR = Color.RED`，
 * 于是按钮变成「纯红实心底 + 同色文字（看不见）」——26.11.09 报障的 ZTE 手表
 * （Android 5.1 / API 22）就是这条路：实测诊断读数为
 * `字=#FFFF0000 实画=#FFFF0000 着色=#FFFF0000`，而 `colorPrimary` 本身解析正常（`#FFFF6699`）。
 *
 * 这里绕开状态列表：按当前主题取一次色，写成单色列表。
 * 老设备拿到正确颜色，新设备观感不变（透明底 + `colorPrimary` 文字）。
 * 颜色仍只读 `?attr/`，不写死色值。
 */
object TextButtonColors {

    /** 水波纹透明度：主色的 1/4，够看见按下反馈又不抢眼 */
    private const val RIPPLE_ALPHA = 0x40

    /**
     * 给文字按钮套上「透明底 + colorPrimary 文字 + 主色水波纹」。
     * 一次性处理整排按钮（同一屏的按钮共享同一个主题，取色只做一次）。
     */
    fun apply(vararg buttons: MaterialButton) {
        val primary = buttons.firstOrNull()?.let {
            colorOf(it.context, com.google.android.material.R.attr.colorPrimary)
        }
        for (button in buttons) {
            if (primary != null) {
                button.setTextColor(primary)
                button.rippleColor = ColorStateList.valueOf(withAlpha(primary, RIPPLE_ALPHA))
            }
            button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)

    /** 从主题取颜色属性；取不到返回 null（调用方保持样式原样，不做破坏性覆盖） */
    private fun colorOf(context: Context, attr: Int): Int? {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(attr, value, true)) return null
        val isColor = value.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
            value.type <= TypedValue.TYPE_LAST_COLOR_INT
        return if (isColor) value.data else null
    }
}
