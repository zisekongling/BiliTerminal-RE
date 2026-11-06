package com.RobinNotBad.BiliClient.ui.appearance

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import androidx.core.content.ContextCompat
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 外观模块五：**玻璃质感组件**（卡片 / 按钮半透明化，让弥散光晕透进来）。
 *
 * 与 [AmbientGlow] 是两个独立开关：单开本模块时卡片 / 按钮在原主题纯色底上同样成立
 * （只是更通透、边缘带一道主题高亮色）；两者同开效果最好，光晕设置项的说明里会推荐。
 *
 * ## 机制（零运行时开销）
 * 每套主题一份 `GlassOverlay.<X>`（`res/values/themes_glass.xml`），只替换
 * `materialCardViewStyle` / `materialButtonStyle` 两个**具体样式引用**；
 * [applyTo] 在 `BaseActivity.onCreate` 里（`super.onCreate` 之后、子类 `setContentView` 之前）
 * 对 Activity 主题 `applyStyle(force = true)` 一次。不遍历视图树、不在列表绑定路径上做任何事。
 * 关闭时 [applyTo] 直接 return。
 *
 * > 注意与 §8.7.1 圆角走过的弯路区分：那次失败的是「维度属性的 `?attr/` 间接层」；
 * > 这里覆盖层里写的都是 `@style/` / `@color/` 具体值，没有 `?attr/` 间接层。
 *
 * ## 可读性（手表字小）
 * 卡片保持约 78% 不透明，光晕只是「隐约透出」；卡片与按钮 elevation 一律 0——
 * 半透明底下的阴影会透出来，看起来像脏边。
 */
object GlassStyle {

    const val KEY = SettingsKeys.UI_GLASS_COMPONENTS

    /** 默认关闭：不改老用户观感。 */
    const val DEFAULT = false

    fun isEnabled(): Boolean = SharedPreferencesUtil.getBoolean(KEY, DEFAULT)

    /** 主题 key → 玻璃覆盖层（纯函数，供 JVM 单测）。未知 key 与 [ColorScheme.themeResId] 一样回落到 B站粉。 */
    fun overlayResId(themeKey: String): Int = when (themeKey) {
        ColorScheme.THEME_ZHIHU_BLUE -> R.style.GlassOverlay_Zhihu
        ColorScheme.THEME_IQIYI_GREEN -> R.style.GlassOverlay_IQIYI
        ColorScheme.THEME_PURPLE_FANTASY -> R.style.GlassOverlay_PurpleFantasy
        ColorScheme.THEME_RAINBOW_FANTASY -> R.style.GlassOverlay_Rainbow
        ColorScheme.THEME_CLASSIC_GRAY -> R.style.GlassOverlay_ClassicGray
        ColorScheme.THEME_CLASSIC_TERMINAL -> R.style.GlassOverlay_Terminal
        else -> R.style.GlassOverlay_Pink
    }

    /** 开启时把当前主题的玻璃覆盖层叠到 [activity] 的主题上。必须在 `setContentView` 之前调用。 */
    fun applyTo(activity: Activity) {
        if (!isEnabled()) return
        activity.theme.applyStyle(overlayResId(ColorScheme.getCurrentThemeName()), true)
    }

    // ==================== 代码侧的玻璃背景 ====================
    //
    // 覆盖层只管得到「走 materialCardViewStyle / materialButtonStyle 的控件」。下面这些地方的背景是
    // 写死的 shape drawable 或在代码里 setBackgroundColor，覆盖层够不着，只能在设置背景时换成玻璃版：
    // - 播放器里的分P / 字幕选择、互动视频选项（PlayerActivity 不继承 BaseActivity，本就不叠覆盖层）
    // - 评论区的楼中楼预览框、「UP主觉得很赞」标签
    // - 用户列表行 cell_user_list（搜索结果「用户」页、关注分组）：单层 FrameLayout，见 applyCardBackground
    // 色值与 themes_glass.xml 同源（colors_glass.xml），**不另抄一份**。

    /** 一套主题的玻璃色（已解析成 ARGB）。 */
    class Palette(val card: Int, val stroke: Int, val fill: Int, val fillStroke: Int)

    /** 主题 key → [card, stroke, fill, fillStroke] 的颜色资源 id（纯函数）。 */
    fun paletteRes(themeKey: String): IntArray = when (themeKey) {
        ColorScheme.THEME_ZHIHU_BLUE -> intArrayOf(
            R.color.glass_card_zhihu, R.color.glass_stroke_zhihu,
            R.color.glass_btn_bg_fill_zhihu, R.color.glass_btn_stroke_zhihu
        )
        ColorScheme.THEME_IQIYI_GREEN -> intArrayOf(
            R.color.glass_card_iqiyi, R.color.glass_stroke_iqiyi,
            R.color.glass_btn_bg_fill_iqiyi, R.color.glass_btn_stroke_iqiyi
        )
        ColorScheme.THEME_PURPLE_FANTASY -> intArrayOf(
            R.color.glass_card_purple_fantasy, R.color.glass_stroke_purple_fantasy,
            R.color.glass_btn_bg_fill_purple_fantasy, R.color.glass_btn_stroke_purple_fantasy
        )
        ColorScheme.THEME_RAINBOW_FANTASY -> intArrayOf(
            R.color.glass_card_rainbow, R.color.glass_stroke_rainbow,
            R.color.glass_btn_bg_fill_rainbow, R.color.glass_btn_stroke_rainbow
        )
        ColorScheme.THEME_CLASSIC_GRAY -> intArrayOf(
            R.color.glass_card_classic_gray, R.color.glass_stroke_classic_gray,
            R.color.glass_btn_bg_fill_classic_gray, R.color.glass_btn_stroke_classic_gray
        )
        ColorScheme.THEME_CLASSIC_TERMINAL -> intArrayOf(
            R.color.glass_card_terminal, R.color.glass_stroke_terminal,
            R.color.glass_btn_bg_fill_terminal, R.color.glass_btn_stroke_terminal
        )
        else -> intArrayOf(
            R.color.glass_card_pink, R.color.glass_stroke_pink,
            R.color.glass_btn_bg_fill_pink, R.color.glass_btn_stroke_pink
        )
    }

    fun palette(context: Context): Palette {
        val ids = paletteRes(ColorScheme.getCurrentThemeName())
        return Palette(
            ContextCompat.getColor(context, ids[0]),
            ContextCompat.getColor(context, ids[1]),
            ContextCompat.getColor(context, ids[2]),
            ContextCompat.getColor(context, ids[3])
        )
    }

    private fun box(fill: Int, stroke: Int, radiusPx: Float, strokePx: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusPx
        setColor(fill)
        setStroke(strokePx, stroke)
    }

    private fun strokePx(context: Context) =
        maxOf(1, (context.resources.displayMetrics.density + 0.5f).toInt())

    /** 卡片圆角的像素值（与 XML 里的 `@dimen/card_round` 一致）。 */
    fun cornerPx(context: Context): Float = context.resources.getDimension(R.dimen.card_round)

    /**
     * 不可点的玻璃面板：卡片色 + 高亮色细描边（楼中楼预览框、「UP主觉得很赞」标签）。
     */
    fun panelBackground(context: Context, radiusPx: Float = cornerPx(context)): Drawable {
        val p = palette(context)
        return box(p.card, p.stroke, radiusPx, strokePx(context))
    }

    /**
     * 把「写死 shape 底」的列表卡片就地换成玻璃面板，返回是否改动（关闭玻璃质感时什么都不做）。
     *
     * 现存唯一调用点是 `cell_user_list`（搜索结果「用户」页、关注分组里的用户行）：它是
     * 单层 `FrameLayout` + `@drawable/bg_card_rounded`（不透明 `@color/card_dark`），不读
     * `materialCardViewStyle`，主题覆盖层够不着，所以只能在建 holder 时自己换背景。
     *
     * 圆角取 [CornerStyle.radiusPx]：主题卡片（`CardStyle*`）的圆角是 `@dimen/card_round`，
     * 选「圆角」档时由 [AppearanceApplier] 运行时覆盖成 `card_round_large`；写死背景不经过主题样式，
     * 只能按同一档位自己取值，否则同一屏里会出现两种圆角。
     *
     * elevation 一并置 0：半透明底下的阴影会从下面透出来形成沿轮廓一圈的脏边，
     * 与 `themes_glass.xml` 里卡片 / 按钮 `cardElevation=0` 是同一条约定。
     */
    fun applyCardBackground(view: View): Boolean {
        if (!isEnabled()) return false
        view.background = panelBackground(view.context, CornerStyle.radiusPx(view.context))
        view.elevation = 0f
        return true
    }

    /**
     * 可点的玻璃选项：平时 = 面板样式，按下 / [selected] = 主色淡底 + 高亮色描边（与玻璃按钮一致）。
     * 每次调用新建，只在绑定时用。
     */
    fun choiceBackground(context: Context, selected: Boolean, radiusPx: Float = cornerPx(context)): Drawable {
        val p = palette(context)
        val sw = strokePx(context)
        val active = box(p.fill, p.fillStroke, radiusPx, sw)
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), active)
            addState(intArrayOf(), if (selected) box(p.fill, p.fillStroke, radiusPx, sw) else box(p.card, p.stroke, radiusPx, sw))
        }
    }
}
