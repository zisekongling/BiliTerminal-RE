package com.RobinNotBad.BiliClient.ui.appearance

import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * **外观门面**：配色 / 卡片圆角 / 字体三模块的统一入口。
 *
 * ## 职责边界（手表性能优先）
 * 只做三件事，**绝不做几何计算**：
 * 1. 汇总当前外观为一份 [Appearance]（[snapshot]），供调用方一次读取；
 * 2. 提供唯一的写入入口（[setTheme] / [setCornerRadius] / [setFontScale] / [setFontFamily]），
 *    每次写入都递增「外观版本号」；
 * 3. 版本号（[version]）让 Activity **只比一个 Int** 就能判断外观是否变过，
 *    而不必随模块增加而增加比较项——现有 `BaseActivity` 只记一个 `appliedTheme` 字符串，
 *    加到第 4 个模块时那种写法必然要改 `BaseActivity`，这里从根上避免。
 *
 * ## 为什么不缓存
 * [snapshot] **刻意不缓存**。它是「每次 Activity 创建读一次」的冷路径，缓存收益为零；
 * 而缓存失效点会随模块增加而变多（配色已有 `setTheme`，圆角/字体还会有各自的写入点），
 * 是一类只会引入 bug 的复杂度。热路径的重复读取问题已由
 * [ColorScheme.getCurrentTheme] 的色表缓存单独解决，不需要在这里再来一层。
 *
 * ## 写入为什么要「同步」落盘
 * 每个写入点后面紧接着就是 `recreate()` 重新读外观，用异步写入存在读到旧值的窗口——
 * 与原先 `ThemeManager.setTheme`（已并入本包 `ColorScheme`）的做法保持一致。代价是每个用户动作一次同步写盘，可忽略。
 * （注：旧注释称「apply() 存在读到旧值的窗口」并不成立——`apply()` 会同步更新内存映射，
 *  只把落盘放到异步；这里保留同步写入仅为不改动既有行为。）
 */
object AppearanceManager {

    /**
     * 外观版本号的 SharedPreferences key。
     *
     * 任何外观设置的写入都必须让它 +1。Activity 记录自己创建时的版本号，
     * `onResume` 时比一次即可知道是否需要重建。
     */
    const val PREF_KEY_VERSION = "appearance_version"

    /** 没有过任何外观写入时的版本号。 */
    const val INITIAL_VERSION = 0

    /**
     * 当前外观的一份快照：三个模块的档位 + 版本号。
     *
     * 存的是**档位存档位名**而非具体数值（6dp / 1.15f 之类），
     * 这样以后调整档位对应的数值不需要迁移用户数据。
     */
    data class Appearance(
        val themeKey: String,
        val cornerRadius: String,
        val fontScale: String,
        val fontFamily: String,
        val version: Int
    )

    // ==================== 版本号 ====================

    /** 当前外观版本号。 */
    fun version(): Int = SharedPreferencesUtil.getInt(PREF_KEY_VERSION, INITIAL_VERSION)

    /** 外观写入后调用：版本号 +1，让所有已创建的页面在下次 `onResume` 时重建。 */
    private fun bumpVersion() {
        SharedPreferencesUtil.putIntSync(PREF_KEY_VERSION, version() + 1)
    }

    // ==================== 读取 ====================

    /** 汇总当前外观。每次调用都会真实读取，见类注释「为什么不缓存」。 */
    fun snapshot(): Appearance = Appearance(
        // 用配色模块的读取入口而不是自己拼默认值，避免默认主题出现第二份定义。
        themeKey = ColorScheme.getCurrentThemeName(),
        cornerRadius = CornerStyle.current(),
        fontScale = FontStyle.currentScale(),
        fontFamily = FontStyle.currentFamily(),
        version = version()
    )

    // ==================== 写入（唯一入口，统一递增版本号） ====================

    /** 写入主题配色。写完立即失效 [ColorScheme] 的色表缓存。 */
    fun setTheme(themeKey: String) {
        SharedPreferencesUtil.putStringSync(SettingsKeys.THEME, themeKey)
        ColorScheme.invalidateCache()
        bumpVersion()
    }

    /** 写入卡片圆角档位（已规整到合法值）。 */
    fun setCornerRadius(value: String) {
        SharedPreferencesUtil.putStringSync(CornerStyle.KEY, CornerStyle.normalize(value))
        bumpVersion()
    }

    /** 写入字号档位（已规整到合法值）。 */
    fun setFontScale(value: String) {
        SharedPreferencesUtil.putStringSync(FontStyle.KEY_SCALE, FontStyle.normalizeScale(value))
        bumpVersion()
    }

    /** 写入字族（已规整到合法值）。 */
    fun setFontFamily(value: String) {
        SharedPreferencesUtil.putStringSync(FontStyle.KEY_FAMILY, FontStyle.normalizeFamily(value))
        bumpVersion()
    }
}
