package com.RobinNotBad.BiliClient.ui.appearance

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
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
 * ## 预设（26.11.21）
 * 「玻璃质感」下本来就有两种材质并存：卡片是**背景色形态**（表面色 + 一道淡描边），
 * 按钮 / 三连卡片 / 选中项是**主题色形态**（主色淡底 + 亮描边）。[GlassPreset] 把这个配比做成三档：
 * 默认「主次分明」= 现状；「稳扎稳打」把按钮那套也降成卡片色；「晶莹剔透」把卡片也升成主色淡底。
 *
 * 实现上是**第二层覆盖层**（`GlassPresetOverlay.<X>.<档位>`，见 [presetOverlayResId]）
 * 叠在基础覆盖层之上，仍然零运行时遍历；代码侧自己画背景的地方（见下面「代码侧的玻璃背景」）
 * 由 [surfaces] 一次性合并出「常态 / 选中」两种底色——**选中态任何档位都保留主色淡底**，
 * 否则「选了哪个」就看不出来了。
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

    /**
     * 主题 key + 预设 → 预设覆盖层（纯函数，供 JVM 单测）。
     *
     * [GlassPreset.BALANCED] 返回 `null`：基础覆盖层本身就是「主次分明」，不需要第二层。
     * 未知预设按 [GlassPreset.normalize] 回落成默认档，同样返回 `null`。
     */
    fun presetOverlayResId(
        themeKey: String,
        preset: String = GlassPreset.current()
    ): Int? = when (GlassPreset.normalize(preset)) {
        GlassPreset.STEADY -> when (themeKey) {
            ColorScheme.THEME_ZHIHU_BLUE -> R.style.GlassPresetOverlay_Zhihu_Steady
            ColorScheme.THEME_IQIYI_GREEN -> R.style.GlassPresetOverlay_IQIYI_Steady
            ColorScheme.THEME_PURPLE_FANTASY -> R.style.GlassPresetOverlay_PurpleFantasy_Steady
            ColorScheme.THEME_RAINBOW_FANTASY -> R.style.GlassPresetOverlay_Rainbow_Steady
            ColorScheme.THEME_CLASSIC_GRAY -> R.style.GlassPresetOverlay_ClassicGray_Steady
            ColorScheme.THEME_CLASSIC_TERMINAL -> R.style.GlassPresetOverlay_Terminal_Steady
            else -> R.style.GlassPresetOverlay_Pink_Steady
        }
        GlassPreset.CRYSTAL -> when (themeKey) {
            ColorScheme.THEME_ZHIHU_BLUE -> R.style.GlassPresetOverlay_Zhihu_Crystal
            ColorScheme.THEME_IQIYI_GREEN -> R.style.GlassPresetOverlay_IQIYI_Crystal
            ColorScheme.THEME_PURPLE_FANTASY -> R.style.GlassPresetOverlay_PurpleFantasy_Crystal
            ColorScheme.THEME_RAINBOW_FANTASY -> R.style.GlassPresetOverlay_Rainbow_Crystal
            ColorScheme.THEME_CLASSIC_GRAY -> R.style.GlassPresetOverlay_ClassicGray_Crystal
            ColorScheme.THEME_CLASSIC_TERMINAL -> R.style.GlassPresetOverlay_Terminal_Crystal
            else -> R.style.GlassPresetOverlay_Pink_Crystal
        }
        else -> null
    }

    /**
     * 开启时把当前主题的玻璃覆盖层叠到 [activity] 的主题上。必须在 `setContentView` 之前调用。
     *
     * 两层：基础层（[overlayResId]）给「主次分明」，第二层（[presetOverlayResId]）按预设改几个槽位。
     * 后面的 `applyStyle` 覆盖同名项，所以第二层只需要写它真正要改的东西。
     */
    fun applyTo(activity: Activity) {
        if (!isEnabled()) return
        val themeKey = ColorScheme.getCurrentThemeName()
        activity.theme.applyStyle(overlayResId(themeKey), true)
        presetOverlayResId(themeKey)?.let { activity.theme.applyStyle(it, true) }
    }

    // ==================== 代码侧的玻璃背景 ====================
    //
    // 覆盖层只管得到「走 materialCardViewStyle / materialButtonStyle 的控件」。下面这些地方的背景是
    // 写死的 shape drawable 或在代码里 setBackgroundColor，覆盖层够不着，只能在设置背景时换成玻璃版：
    // - 播放器里的分P / 字幕选择、互动视频选项（PlayerActivity 不继承 BaseActivity，本就不叠覆盖层）
    // - 评论区的楼中楼预览框、「UP主觉得很赞」标签
    // - 用户列表行 cell_user_list（搜索结果「用户」页、关注分组、消息页的私信列表）：单层 FrameLayout，
    //   见 applyCardBackground
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

    /**
     * 代码侧真正要用的两种形态：**常态**（面板 / 未选中）与**选中**各自的底色 + 描边。
     *
     * 引入预设后，「卡片色还是主色」不再写死，而是由 [surfaces] 先合并好再交给 [box]，
     * 免得每个调用点各判一次档位。
     */
    class Surfaces(
        val normalFill: Int,
        val normalStroke: Int,
        val selectedFill: Int,
        val selectedStroke: Int
    )

    /**
     * 把原始四色按预设合并成「常态 / 选中」（纯函数，供 JVM 单测）。
     *
     * - [GlassPreset.BALANCED] / [GlassPreset.STEADY]：常态 = 卡片色 + 淡描边，
     *   选中 = 主色淡底 + 亮描边。**「稳扎稳打」的选中态保持原样**——两档都只统一未选中的控件，
     *   把选中态也抹平就看不出选了哪个了。
     * - [GlassPreset.CRYSTAL]：常态也换成主色淡底（描边改用柔和的玻璃描边，大面积用亮描边会像被框住的按钮），
     *   选中再加浓一档（[accentFill]）+ 亮描边。
     */
    fun surfaces(preset: String, card: Int, stroke: Int, fill: Int, fillStroke: Int): Surfaces =
        when (GlassPreset.normalize(preset)) {
            GlassPreset.CRYSTAL -> Surfaces(fill, stroke, accentFill(fill), fillStroke)
            else -> Surfaces(card, stroke, fill, fillStroke)
        }

    /** 当前主题 + 当前预设下的两种形态（读 prefs 与资源色，只在建页面 / 绑定时调用）。 */
    fun surfaces(context: Context): Surfaces {
        val p = palette(context)
        return surfaces(GlassPreset.current(), p.card, p.stroke, p.fill, p.fillStroke)
    }

    /**
     * 主色淡底的「加浓一档」：alpha 翻倍、封顶不透明（暗色 20% → 40%，亮色 14% → 28%）。
     *
     * 晶莹剔透档里常态与选中是同一种颜色，只能靠浓度 + 描边区分；翻倍刚好是「一眼看得出、
     * 又不至于变成实心色块」的一档。RGB 不动，只改 alpha。
     */
    fun accentFill(fill: Int): Int {
        val alpha = (fill ushr 24) and 0xFF
        val boosted = minOf(0xFF, alpha * 2)
        return (fill and 0x00FFFFFF) or (boosted shl 24)
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
     * 不可点的玻璃面板：常态形态 + 细描边（楼中楼预览框、「UP主觉得很赞」标签）。
     *
     * 「常态形态」= 主次分明 / 稳扎稳打下的卡片色，晶莹剔透下的主色淡底，见 [surfaces]。
     */
    fun panelBackground(context: Context, radiusPx: Float = cornerPx(context)): Drawable {
        val s = surfaces(context)
        return box(s.normalFill, s.normalStroke, radiusPx, strokePx(context))
    }

    /**
     * 把「写死 shape 底」的列表卡片就地换成玻璃面板，返回是否改动（关闭玻璃质感时什么都不做）。
     *
     * 调用点都是 `cell_user_list`（搜索结果「用户」页、关注分组的成员行、消息页的「私信列表」）：它是
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
        // elevation 是 API 21 的接口；4.4 上本来就没有 Material 阴影，直接跳过即可。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) view.elevation = 0f
        return true
    }

    /**
     * 可点的玻璃选项：平时 = 常态形态，按下 / [selected] = 选中形态（主色淡底 + 亮描边，与玻璃按钮一致）。
     * 每次调用新建，只在绑定时用。
     */
    fun choiceBackground(context: Context, selected: Boolean, radiusPx: Float = cornerPx(context)): Drawable {
        val s = surfaces(context)
        val sw = strokePx(context)
        // 按下与选中各建一份：同一个 Drawable 实例塞进两个状态位，切状态时可能只换 index 不重画。
        fun activeBox() = box(s.selectedFill, s.selectedStroke, radiusPx, sw)
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), activeBox())
            addState(intArrayOf(), if (selected) activeBox() else box(s.normalFill, s.normalStroke, radiusPx, sw))
        }
    }
}
