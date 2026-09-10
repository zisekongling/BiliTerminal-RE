package com.RobinNotBad.BiliClient.ui.appearance

import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 外观模块三：**字体**。
 *
 * 两个独立维度，各有自己的 key：
 * - **字号**（[SCALE_VALUES] 四档）：手表上真正高频的痛点是「字太小看不清」。
 * - **字族**（[FAMILY_VALUES] 二选）：`monospace` 对**数字与拉丁字母**生效，中文大概率仍回落
 *   系统字体。即便中文不变，播放量/时长/弹幕数字变等宽在手表上是可感知的，且贴合「经典终端」调性。
 *
 * ## 与已有「界面大小 / DPI」设置的分工
 * `SettingsKeys.DPI` / `DENSITY` 管**整体界面密度**（含布局尺寸），本模块只管**文字**。
 * [scaleFactor] 是**乘在 DPI 结果之上**的独立系数，不写 `fontScale`，二者不会互相覆盖。
 *
 * ## 性能约定（手表优先）
 * - [SCALE_DEFAULT] 的系数**恒为 1.0f**，下游据此短路——默认档位必须做到**零运行时开销**
 *   （不遍历视图树、不调 `setTextSize`）。
 * - 字族只通过主题 style 的 `android:fontFamily` 落地，**不做任何代码遍历**。
 *
 * ## 真源是 dimen（字号）
 * [scaleFactor] 只是「档位 → 系数」，实际 sp 值来自 `@dimen/text_*` 五档 token 乘本系数。
 * **本对象不做几何计算**，也不缓存。
 */
object FontStyle {

    // ==================== 字号 ====================

    /** 字号档位的 SharedPreferences key。 */
    const val KEY_SCALE = SettingsKeys.UI_FONT_SCALE

    const val SCALE_SMALL = "small"
    const val SCALE_STANDARD = "standard"
    const val SCALE_LARGE = "large"
    const val SCALE_XLARGE = "xlarge"

    /** 默认字号档位。 */
    const val SCALE_DEFAULT = SCALE_STANDARD

    /** 供设置页 `listChoose` 使用的候选值（与 [SCALE_DISPLAY_NAMES] 一一对应）。 */
    val SCALE_VALUES = listOf(SCALE_SMALL, SCALE_STANDARD, SCALE_LARGE, SCALE_XLARGE)

    /** 供设置页展示的中文名（与 [SCALE_VALUES] 一一对应）。 */
    val SCALE_DISPLAY_NAMES = listOf("小", "标准", "大", "特大")

    // ==================== 字族 ====================

    /** 字族的 SharedPreferences key。 */
    const val KEY_FAMILY = SettingsKeys.UI_FONT_FAMILY

    const val FAMILY_SYSTEM = "system"
    const val FAMILY_MONOSPACE = "monospace"

    /** 默认字族。 */
    const val FAMILY_DEFAULT = FAMILY_SYSTEM

    /** 供设置页 `listChoose` 使用的候选值（与 [FAMILY_DISPLAY_NAMES] 一一对应）。 */
    val FAMILY_VALUES = listOf(FAMILY_SYSTEM, FAMILY_MONOSPACE)

    /** 供设置页展示的中文名（与 [FAMILY_VALUES] 一一对应）。 */
    val FAMILY_DISPLAY_NAMES = listOf("系统默认", "等宽")

    // ==================== 规整 ====================

    /** 把字号存档值规整到合法档位，未知值回落到 [SCALE_DEFAULT]。 */
    fun normalizeScale(saved: String?): String =
        if (saved != null && saved in SCALE_VALUES) saved else SCALE_DEFAULT

    /** 把字族存档值规整到合法值，未知值回落到 [FAMILY_DEFAULT]。 */
    fun normalizeFamily(saved: String?): String =
        if (saved != null && saved in FAMILY_VALUES) saved else FAMILY_DEFAULT

    // ==================== 读取 ====================

    /** 读取当前字号档位（已规整）。 */
    fun currentScale(): String =
        normalizeScale(SharedPreferencesUtil.getString(KEY_SCALE, SCALE_DEFAULT))

    /** 读取当前字族（已规整）。 */
    fun currentFamily(): String =
        normalizeFamily(SharedPreferencesUtil.getString(KEY_FAMILY, FAMILY_DEFAULT))

    // ==================== 档位 → 实际取值 ====================

    /**
     * 字号档位 → 文字缩放系数。
     *
     * **不变量**：[SCALE_DEFAULT] 必须恒返回 `1.0f`——下游据此短路，保证默认档位零运行时开销。
     * 有任何改档位数值的念头，先看 `FontStyleTest.scaleFactor_standardIsExactlyOne`。
     */
    fun scaleFactor(scale: String): Float = when (normalizeScale(scale)) {
        SCALE_SMALL -> 0.85f
        SCALE_LARGE -> 1.15f
        SCALE_XLARGE -> 1.30f
        else -> 1.0f
    }

    /**
     * 字族 → 可直接赋给 `android:fontFamily` / `setTypeface` 的值。
     *
     * 返回 `null` 表示**不覆盖**，沿用系统默认——这样 [FAMILY_SYSTEM] 档不会改变任何既有渲染。
     */
    fun fontFamilyValue(family: String): String? = when (normalizeFamily(family)) {
        FAMILY_MONOSPACE -> "monospace"
        else -> null
    }
}
