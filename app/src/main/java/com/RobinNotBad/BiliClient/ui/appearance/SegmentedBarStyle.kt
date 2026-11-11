package com.RobinNotBad.BiliClient.ui.appearance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.view.ViewCompat
import com.google.android.material.tabs.TabLayout

/**
 * 「玻璃光晕分段条」——设计稿 E 方案的落地实现。
 *
 * ## 它解决什么问题
 * 排序条（收藏时间 / 播放量 / 投稿时间）、筛选条（全部 / 未看完）、表情面板与更新日志的页签，
 * 原来是三块**裸文字**：未选 `textColorSecondary`（在 #262626 底色上只有 2.5:1 左右的对比度），
 * 选中就是文字变粉——没有形体、没有层级，选中的那一格在窄屏上几乎看不出「被按下了」。
 *
 * ## 画成什么样
 * 一条**玻璃轨道**（14dp 圆角、文字色 7% 填充 + 13% 描边）把整排格子框成一组；
 * 选中那一格浮一块**玻璃胶囊**：主色→浅主色的水平渐变（34% → 10%）、1dp 浅主色内描边、
 * 外侧 3dp 主色微光晕、左缘一道 2dp 不透明主色竖条，文字提到 `TEXT_PRIMARY` 并加假粗体。
 * 未选中格子的文字压到 `TEXT_SECONDARY`，仍然可以点，只是明确「不是当前项」。
 *
 * ## 三条不能违反的约束
 * 1. **热区不让步**：视觉高度 40dp，但格子（TextView / TabView）本身仍是 48dp（`touch_min`），
 *    轨道和胶囊都画在格子内部、靠 [TrackDrawable] / [PillDrawable] 自己收边，不去改布局高度
 *    ——手表上点不准的按钮等于没有。
 * 2. **颜色只来自 [ColorScheme]**：轨道用 `TEXT_PRIMARY`（浅主题里是深色）加 alpha 派生，
 *    暗色/亮色主题都成立；胶囊用 `PRIMARY` / `PRIMARY_LIGHT`。这里**不写死任何颜色**。
 * 3. **不用自定义 typeface**：假粗体走 `Paint.isFakeBoldText`，不会顶掉主题字体
 *    （工程里 [FontStyle] 给全局换过字体，`Typeface.DEFAULT_BOLD` 会把它吃掉）。
 *
 * ## 颜色为什么要拆成纯函数
 * [colorsFor] 不碰 View、不碰 Context，只做 ARGB 派生，于是能被纯 JVM 单测钉住
 * （见 `SegmentedBarStyleTest`）——这套透明度的数值是设计稿量出来的，改错了肉眼要盯半天。
 */
object SegmentedBarStyle {

    /** 胶囊与轨道的视觉高度；格子本身的高度仍是 `@dimen/touch_min`（48dp）。 */
    private const val BAR_HEIGHT_DP = 40f

    /** 轨道与胶囊的圆角。 */
    private const val RADIUS_DP = 14f

    /** 胶囊左右各留 3dp，格子之间才不会连成一片。 */
    private const val EDGE_INSET_DP = 3f

    /** 轨道离格子左右各 3dp，和胶囊的左右内缩对齐（见 [TrackDrawable]）。 */
    private const val TRACK_EDGE_DP = 3f

    // 设计稿 E 量出来的透明度。轨道用文字色派生 → 亮暗主题自适应；胶囊用主色系。
    private const val TRACK_FILL_ALPHA = 0x12
    private const val TRACK_STROKE_ALPHA = 0x21
    private const val PILL_START_ALPHA = 0x57
    private const val PILL_END_ALPHA = 0x1A
    private const val PILL_STROKE_ALPHA = 0x6B
    private const val PILL_GLOW_ALPHA = 0x1A
    private const val PRESSED_ALPHA = 0x0F

    /** 选中胶囊按下时的提亮倍率。 */
    private const val PRESSED_BRIGHTNESS = 1.35f

    /** 一条分段条要用到的全部颜色，全部是 ARGB。 */
    data class BarColors(
        @ColorInt val trackFill: Int,
        @ColorInt val trackStroke: Int,
        @ColorInt val pillStart: Int,
        @ColorInt val pillEnd: Int,
        @ColorInt val pillStroke: Int,
        @ColorInt val pillGlow: Int,
        @ColorInt val accent: Int,
        @ColorInt val textSelected: Int,
        @ColorInt val textUnselected: Int,
        @ColorInt val pressed: Int
    )

    /**
     * 纯函数：从四项主题色派生出一条分段条的全部颜色。
     *
     * 轨道刻意**不用** `SURFACE` / `BACKGROUND`：这两者在亮色主题下与页面底色太接近，
     * 轨道会消失；用 `TEXT_PRIMARY` 加低透明度，等于「反相的一层雾」，两套底色都看得见。
     */
    @JvmStatic
    fun colorsFor(
        @ColorInt primary: Int,
        @ColorInt primaryLight: Int,
        @ColorInt textPrimary: Int,
        @ColorInt textSecondary: Int
    ): BarColors = BarColors(
        trackFill = withAlpha(textPrimary, TRACK_FILL_ALPHA),
        trackStroke = withAlpha(textPrimary, TRACK_STROKE_ALPHA),
        pillStart = withAlpha(primary, PILL_START_ALPHA),
        pillEnd = withAlpha(primaryLight, PILL_END_ALPHA),
        pillStroke = withAlpha(primaryLight, PILL_STROKE_ALPHA),
        pillGlow = withAlpha(primary, PILL_GLOW_ALPHA),
        accent = primary,
        textSelected = textPrimary,
        textUnselected = textSecondary,
        pressed = withAlpha(textPrimary, PRESSED_ALPHA)
    )

    /** 当前主题下的一条分段条颜色。 */
    @JvmStatic
    fun barColors(): BarColors = colorsFor(
        ColorScheme.PRIMARY,
        ColorScheme.PRIMARY_LIGHT,
        ColorScheme.TEXT_PRIMARY,
        ColorScheme.TEXT_SECONDARY
    )

    /**
     * 给分段条的容器（`filterBar` / `sortBar` / `tabBar` 这种 LinearLayout）铺玻璃轨道。
     *
     * 不需要算 inset：背景的绘制区域**天然就排除了容器 padding**，而 [TrackDrawable]
     * 会和 [PillDrawable] 用同一条高度公式，所以轨道内缘与胶囊外缘永远对齐——
     * 无论容器是 48dp 的 `touch_min` 格子 + 2dp 上下 padding，还是 TabLayout 的默认高度。
     */
    @JvmStatic
    fun applyTrack(bar: View) {
        val density = bar.resources.displayMetrics.density
        ViewCompat.setBackground(bar, TrackDrawable(barColors(), density))
    }

    /**
     * 一个格子的背景：选中 → 玻璃胶囊（按下时提亮）；未选中 → 只有按下反馈。
     *
     * 必须连**未选中**也给一份背景：格子的 XML 背景是 `?attr/selectableItemBackground`
     * （按压涟漪），被替换掉之后如果给 null，未选中的格子就彻底没有点击反馈了。
     */
    @JvmStatic
    fun itemBackground(context: Context, selected: Boolean): Drawable {
        val colors = barColors()
        val density = context.resources.displayMetrics.density
        val normal: Drawable = if (selected) {
            PillDrawable(colors, density, 1f)
        } else {
            ColorDrawable(Color.TRANSPARENT)
        }
        val pressed: Drawable = if (selected) {
            PillDrawable(colors, density, PRESSED_BRIGHTNESS)
        } else {
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = RADIUS_DP * density
                setColor(colors.pressed)
            }
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    /** 把一个格子刷成选中/未选中（文字色 + 假粗体 + 胶囊背景）。 */
    @JvmStatic
    fun applyItem(item: TextView, selected: Boolean) {
        ViewCompat.setBackground(item, itemBackground(item.context, selected))
        item.setTextColor(if (selected) ColorScheme.TEXT_PRIMARY else ColorScheme.TEXT_SECONDARY)
        // 不换 typeface：工程里 FontStyle 给全局换过字体，Typeface.BOLD 会把它顶掉
        item.paint.isFakeBoldText = selected
        item.invalidate()
    }

    /**
     * 给 TabLayout（表情面板的分类页签、更新日志的版本页签）套同一套外观。
     *
     * 前提：布局里已经把 `app:tabIndicatorHeight` 设成 `0dp`（下划线由胶囊取代）。
     * 底色仍然**不设**——页签继承页面底色，这样才不会重演 #113（写死播放区纯黑导致浅色主题下一条黑带）。
     */
    @JvmStatic
    fun styleTabs(tabs: TabLayout) {
        val density = tabs.resources.displayMetrics.density
        val colors = barColors()
        ViewCompat.setBackground(tabs, TrackDrawable(colors, density))
        // 先按官方接口置色：TabView 内部 update() 会拿这份 ColorStateList 重刷文字，
        // 只改子 View 的话下一次选中/取消选中又会被覆盖回去。
        tabs.setTabTextColors(colors.textUnselected, colors.textSelected)
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = refreshTabs(tabs)

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) = refreshTabs(tabs)
        })
        refreshTabs(tabs)
    }

    /**
     * 重刷每个页签的胶囊与文字色。
     *
     * 页签内容（表情图标、版本号）是**异步**填进来的，填进来时不会通知监听器，
     * 所以 EmoteActivity 在图标载完之后还要再调一次这里。
     */
    @JvmStatic
    fun refreshTabs(tabs: TabLayout) {
        val colors = barColors()
        for (i in 0 until tabs.tabCount) {
            val tab = tabs.getTabAt(i) ?: continue
            val tabView = tab.view
            val selected = tab.isSelected
            ViewCompat.setBackground(tabView, itemBackground(tabView.context, selected))
            val color = if (selected) colors.textSelected else colors.textUnselected
            // TabView 是一个 LinearLayout：图标是 ImageView，文字是 TextView，只染后者
            for (j in 0 until tabView.childCount) {
                val child = tabView.getChildAt(j)
                if (child is TextView) {
                    child.setTextColor(color)
                    child.paint.isFakeBoldText = selected
                }
            }
        }
    }

    private fun withAlpha(@ColorInt color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)

    /**
     * 玻璃轨道：一格圆角矩形，填充 + 1dp 描边。
     *
     * 高度**自己算**而不是靠 inset：和 [PillDrawable] 用同一条 `min(40dp, h - 6dp)` 公式，
     * 于是不管容器多高（48dp 格子、TabLayout 默认高度、以后别人改的别的尺寸），
     * 轨道和胶囊都严丝合缝，不会出现「胶囊比轨道高 1dp」这种一眼假的错位。
     */
    private class TrackDrawable(
        private val colors: BarColors,
        private val density: Float
    ) : Drawable() {

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = colors.trackFill
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, density)
            color = colors.trackStroke
        }

        private val track = RectF()
        private var radius = 0f

        override fun onBoundsChange(bounds: Rect) {
            val side = TRACK_EDGE_DP * density
            val height = minOf(BAR_HEIGHT_DP * density, bounds.height() - 2 * side)
            val vInset = (bounds.height() - height) / 2f
            track.set(bounds.left + side, bounds.top + vInset, bounds.right - side, bounds.bottom - vInset)
            radius = minOf(RADIUS_DP * density, height / 2f)
        }

        override fun draw(canvas: Canvas) {
            if (track.width() <= 0f || track.height() <= 0f) return
            canvas.drawRoundRect(track, radius, radius, fillPaint)
            canvas.drawRoundRect(track, radius, radius, strokePaint)
        }

        // 背景的透明度由 View 自己控制（同 ThemeTint.GlowDotDrawable 的处理）
        override fun setAlpha(alpha: Int) {}

        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /**
     * 选中胶囊：渐变填充 + 内描边 + 外光晕 + 左侧主色竖条。
     *
     * 不用 `GradientDrawable` 是因为它一个 Drawable 只能带**一条**描边，凑不出「内描边 + 外光晕」
     * 两层轮廓；手绘另一个好处是高度可以固定成 40dp 居中对齐（不随格子高度拉伸）。
     * 一个页面最多三五个格子，且只在状态切换时重建，不进绘制热路径。
     */
    private class PillDrawable(
        private val colors: BarColors,
        private val density: Float,
        private val brightness: Float
    ) : Drawable() {

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            isDither = true
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            isDither = true
            strokeWidth = maxOf(1f, density)
        }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            isDither = true
            strokeWidth = 3f * density
            color = colors.pillGlow
        }
        private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = colors.accent
        }

        private val pill = RectF()
        private val glow = RectF()
        private val accent = RectF()
        private val radius = RADIUS_DP * density

        override fun onBoundsChange(bounds: Rect) {
            val side = EDGE_INSET_DP * density
            val pillHeight = minOf(BAR_HEIGHT_DP * density, bounds.height() - 2 * side)
            val vInset = (bounds.height() - pillHeight) / 2f
            pill.set(bounds.left + side, bounds.top + vInset, bounds.right - side, bounds.bottom - vInset)

            val glowPad = glowPaint.strokeWidth / 2f
            glow.set(pill.left - glowPad, pill.top - glowPad, pill.right + glowPad, pill.bottom + glowPad)

            // 左缘竖条：上下各缩 6dp，别顶到圆角上
            val barPad = 6f * density
            accent.set(pill.left + barPad, pill.top + barPad, pill.left + barPad + 2f * density, pill.bottom - barPad)

            fillPaint.shader = if (pill.width() > 0f) LinearGradient(
                pill.left, 0f, pill.right, 0f,
                brighten(colors.pillStart), brighten(colors.pillEnd),
                Shader.TileMode.CLAMP
            ) else null
            strokePaint.color = brighten(colors.pillStroke)
        }

        override fun draw(canvas: Canvas) {
            if (pill.width() <= 0f || pill.height() <= 0f) return
            val glowRadius = radius + glowPaint.strokeWidth / 2f
            canvas.drawRoundRect(glow, glowRadius, glowRadius, glowPaint)
            canvas.drawRoundRect(pill, radius, radius, fillPaint)
            canvas.drawRoundRect(pill, radius, radius, strokePaint)
            if (accent.height() > 0f) canvas.drawRoundRect(accent, density, density, accentPaint)
        }

        /** 按下时把三层颜色的透明度整体提亮，色相不变。 */
        private fun brighten(@ColorInt color: Int): Int {
            if (brightness == 1f) return color
            val alpha = ((color ushr 24) and 0xFF) * brightness
            return withAlpha(color, minOf(255f, alpha).toInt())
        }

        // 背景的透明度由 View 自己控制，这里不需要（同 ThemeTint.GlowDotDrawable 的处理）
        override fun setAlpha(alpha: Int) {}

        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
