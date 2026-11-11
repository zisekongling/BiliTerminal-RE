package com.RobinNotBad.BiliClient.util

/**
 * 「布局适配」页三个输入框的解析与范围校验。
 *
 * <p>为什么要单独抽出来：这三个框都是 `EditText`，`inputType=numberDecimal` / `number`
 * **只约束软键盘**，粘贴、IME 直接提交、无障碍服务都能把任意文字塞进去。旧代码直接
 * `text.toFloat()` / `toInt()`，于是线上真出现过两次 `NumberFormatException`
 * （`For input string: "界面大小"` 和 `"确认设置"`——都是页面自己的文案，疑似被粘贴进去的），
 * 每次都在点「确认设置」时整页崩掉。
 *
 * <p>这里用 `toFloatOrNull` / `toIntOrNull`：非法内容返回 `null`，调用方跳过写盘，
 * 页面照常关闭。范围校验与旧代码完全一致（DPI 0.1~10，边距 ≤30），不做额外收紧。
 */
object UiScaleInput {

    const val DPI_MIN = 0.1f
    const val DPI_MAX = 10.0f

    /** 边距只卡上限，与旧代码一致（负值照旧放行，避免顺手改行为）。 */
    const val PADDING_MAX = 30

    /** 解析界面缩放倍数；非数字或超出 0.1~10 都返回 `null`。 */
    @JvmStatic
    fun parseDpi(text: CharSequence?): Float? =
        text?.toString()?.trim()?.toFloatOrNull()?.takeIf { it >= DPI_MIN && it <= DPI_MAX }

    /** 解析边距百分比；非数字或大于 30 返回 `null`。 */
    @JvmStatic
    fun parsePadding(text: CharSequence?): Int? =
        text?.toString()?.trim()?.toIntOrNull()?.takeIf { it <= PADDING_MAX }

    /**
     * 解析「顶栏高度」（dp）；非数字返回 `null`。
     *
     * 这里**只判能不能解析**，上下限交给 `ui/widget/TopbarHeightPolicy.clampDp`：
     * 读侧（`BaseActivity`）本来就要再钳一次，上限只该有一个定义处，否则两处漂移。
     */
    @JvmStatic
    fun parseTopbarHeight(text: CharSequence?): Int? =
        text?.toString()?.trim()?.toIntOrNull()
}
