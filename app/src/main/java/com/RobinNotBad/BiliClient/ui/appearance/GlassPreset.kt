package com.RobinNotBad.BiliClient.ui.appearance

import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.UiText

/**
 * 外观模块「玻璃质感」的形态子档位：**玻璃质感预设**。
 *
 * ## 为什么需要它
 * 玻璃质感下本来就有两种材质同时存在，各自都有存在的理由：
 * - **主题色形态**：按钮、三连卡片、选中项 —— 主色淡底 + 亮描边，用来表示「可点 / 已选」；
 * - **背景色形态**：普通卡片 —— 约 78% 不透明的表面色，用来「装内容、别抢眼」。
 *
 * 两者并存就是默认档 [BALANCED]（主次分明）。但摆在一起有人觉得「一片粉里夹着几块米白」太杂，
 * 于是给它两档统一的方向：
 * - [STEADY]（稳扎稳打）：全部统一成**背景色形态**，主题色只留在描边上；
 * - [CRYSTAL]（晶莹剔透）：全部统一成**主题色形态**，整屏都是一层主色淡彩。
 *
 * ## 只管形态，不碰「选中」的可辨识性
 * 两档都只统一**未选中**的控件；**选中态仍然保留主色淡底**（晶莹剔透下再加浓一档），
 * 否则「我选了哪个」就看不出来了 —— 26.11.21 用户拍板的口径。
 *
 * ## 真源在资源，不在这里
 * 本对象只保存「档位存档位名」，不给颜色判据——判据只有一处：[GlassStyle.surfaces]。
 * 具体色值在 `colors_glass.xml`（亮 / 暗各一份），具体样式在 `themes_glass.xml` 的
 * `CardStyle<X>.GlassVivid` / `ButtonStyle<X>.GlassQuiet` 与 `GlassPresetOverlay.<X>.<档位>`，
 * 由 [GlassStyle.applyTo] 叠在基础覆盖层之上；代码侧由 [GlassStyle.surfaces] 合并。
 * **本对象不做任何颜色 / 样式计算**，也不是热路径（只在读写设置与建页面时调用）。
 *
 * ## 与开关的关系
 * 预设常驻可改（设置页不随开关隐藏），但只有「玻璃质感」开关打开时才有视觉作用：
 * 关着的时候 [GlassStyle.applyTo] 直接 return，一层覆盖层都不会叠。
 */
object GlassPreset {

    /** SharedPreferences key。 */
    const val KEY = SettingsKeys.UI_GLASS_PRESET

    /** 主次分明：卡片 = 背景色形态，按钮 / 选中 = 主题色形态（引入本设置项之前的观感）。 */
    const val BALANCED = "balanced"

    /** 稳扎稳打：卡片与按钮全部统一成背景色形态。 */
    const val STEADY = "steady"

    /** 晶莹剔透：卡片与按钮全部统一成主题色形态。 */
    const val CRYSTAL = "crystal"

    /** 默认档位：老用户观感零变化。 */
    const val DEFAULT = BALANCED

    /** 供设置页 `listChoose` 使用的候选值（与 [DISPLAY_NAMES] 一一对应）。 */
    val VALUES = listOf(BALANCED, STEADY, CRYSTAL)

    /** 供设置页展示的中文名（与 [VALUES] 一一对应）。 */
    val DISPLAY_NAMES = listOf(UiText.of("主次分明"), UiText.of("稳扎稳打"), UiText.of("晶莹剔透"))

    /**
     * 把存档值规整到合法档位。
     *
     * 未知值（用户改坏了 prefs、或将来删掉了某档）一律回落到 [DEFAULT]，
     * 避免设置页显示空白、以及下游 `when` 走进未定义分支。
     */
    fun normalize(saved: String?): String = if (saved != null && saved in VALUES) saved else DEFAULT

    /** 读取当前档位（已规整）。 */
    fun current(): String = normalize(SharedPreferencesUtil.getString(KEY, DEFAULT))
}
