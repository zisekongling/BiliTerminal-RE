package com.RobinNotBad.BiliClient.ui.appearance

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 外观模块二：**卡片圆角**。
 *
 * ## 为什么只有两档
 * 手表性能优先：档位越少，需要预烤的资源与运行时分枝越少。两档刚好覆盖核心诉求
 * （「要方的」/「要圆的」），四档属于收益递减。
 *
 * ## 两档的来历（已对上游实测核实）
 * - [SQUARE] = **还原原项目**。上游 BiliClient（gitee `develop`，HEAD `f2b1aca`）全项目唯一的
 *   卡片圆角是 `@dimen/card_round`（`values/dimens.xml` 第 7 行 = 6dp），经主题的
 *   `materialCardViewStyle=@style/CardStyle` 全局下发，布局里几乎不写圆角。
 *   **上游并不存在「0dp 直角」外观，也没有任何圆角设置项**——所以本档位叫「方角」但值是
 *   `card_round`，语义是「还原原项目」，不是「做成直角」。
 * - [ROUNDED] = 本项目主题化改造时给 6 套主题硬编码的值（12dp），保留下来作为可选项。
 *
 * ## 默认档位的选择
 * 默认 [SQUARE]。因为当前默认主题是「经典终端」，它本来就走 `@dimen/card_round`，
 * 所以默认档位 = **现有用户观感零变化**，不会因为引入本模块而突然改掉所有人的卡片外观。
 *
 * ## 真源是 dimen，不是这里的数字
 * 本对象只保存「档位存档位名」。实际 dp 值来自 `@dimen/card_round` 与 `card_round_large`，
 * 这样手表（`values/`）与宽屏手机（`values-w300dp/`）可以各自给值，符合「手表优先、手机顺便适配」。
 * **本对象不做任何几何计算**，也不缓存——它不是热路径（只在读写设置时调用）。
 */
object CornerStyle {

    /** SharedPreferences key。 */
    const val KEY = SettingsKeys.UI_CORNER_RADIUS

    /** 方角：还原原项目 BiliClient 的 `card_round`。 */
    const val SQUARE = "square"

    /** 圆角：本项目主题化改造后各主题硬编码的 12dp。 */
    const val ROUNDED = "rounded"

    /** 默认档位。 */
    const val DEFAULT = SQUARE

    /** 供设置页 `listChoose` 使用的候选值（与 [DISPLAY_NAMES] 一一对应）。 */
    val VALUES = listOf(SQUARE, ROUNDED)

    /** 供设置页展示的中文名（与 [VALUES] 一一对应）。 */
    val DISPLAY_NAMES = listOf("方角", "圆角")

    /**
     * 把存档值规整到合法档位。
     *
     * 未知值（用户改坏了 prefs、或将来删掉了某档）一律回落到 [DEFAULT]，
     * 避免设置页显示空白、以及下游 `when` 走进未定义分支。
     */
    fun normalize(saved: String?): String = if (saved != null && saved in VALUES) saved else DEFAULT

    /** 读取当前档位（已规整）。 */
    fun current(): String = normalize(SharedPreferencesUtil.getString(KEY, DEFAULT))

    /**
     * 档位 → 需要 `theme.applyStyle()` 的**覆盖样式**（定义在 `res/values/styles.xml`）。
     *
     * 为什么是这套机制：`dimen` 编译期固定、`shape drawable` 读不到主题，
     * 只有**主题属性**（`?attr/appCornerRadius`）能在运行时被 `applyStyle` 覆盖。
     * 因此所有 `CardStyle*`/`ButtonStyle*` 都引用该属性，由 `BaseActivity`
     * 在 `setTheme` 之后、任何视图 inflate 之前叠加本样式。
     *
     * 好处：**零运行时遍历**，不碰 `RecyclerView` 绑定路径（手表性能优先）。
     */
    fun overlayStyleResId(value: String = current()): Int = when (normalize(value)) {
        ROUNDED -> R.style.Appearance_CornerRounded
        else -> R.style.Appearance_CornerSquare
    }
}
