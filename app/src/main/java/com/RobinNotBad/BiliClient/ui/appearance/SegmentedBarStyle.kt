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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
 * 一条**玻璃轨道**（10dp 圆角、文字色 11% 填充 + 17% 描边）把整排格子框成一组；
 * 选中那一格浮一块**玻璃胶囊**：主色→浅主色的水平渐变（29% → 8%）、1dp 浅主色内描边，
 * **不做外发光**（试过 3dp 光晕，在手表小屏上只会把边缘糊掉）；
 * 胶囊里靠边的一侧露一小段 2dp 主色竖条（首格在左、末格在右，夹在中间时不画
 * ——夹在中间会变成横在格子之间的分隔线），文字提到 `TEXT_PRIMARY` 并加假粗体。
 * 未选中格子的文字压到 `TEXT_SECONDARY`，仍然可以点，只是明确「不是当前项」。
 *
 * ## 三条不能违反的约束
 * 1. **整条一起拖**：这条横条是**一个**可拖动控件（[installDrag]），手指扫到哪格就选哪格，
 *    所以每个格子不需要各自 48dp 的点按热区——行高压到 40dp 也不会点不准，
 *    比原来「48dp 格子 + 上下 padding」省 12dp 版面。
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

    /**
     * 胶囊与轨道的视觉高度。
     *
     * 从 40dp 压到 32dp：整条横条是**一个可拖动控件**（见 [installDrag]），不需要每个格子各自
     * 撑到 48dp 才点得准，所以行高跟着压到 `@dimen/segmented_bar_min`（40dp），
     * 比原来的 48dp + 上下 padding 省掉 12dp 版面。
     */
    private const val BAR_HEIGHT_DP = 32f

    /** 轨道与胶囊的圆角。压缩高度后 10dp 才收得住，14dp 会显得两头圆得发肿。 */
    private const val RADIUS_DP = 10f

    /** 胶囊左右各留 2dp，格子之间才不会连成一片。 */
    private const val EDGE_INSET_DP = 2f

    /** 轨道离格子左右各 2dp，和胶囊的左右内缩对齐（见 [TrackDrawable]）。 */
    private const val TRACK_EDGE_DP = 2f

    // 设计稿 E 量出来的透明度。轨道用文字色派生 → 亮暗主题自适应；胶囊用主色系。
    private const val TRACK_FILL_ALPHA = 0x1C
    private const val TRACK_STROKE_ALPHA = 0x2C
    private const val PILL_START_ALPHA = 0x4A
    private const val PILL_END_ALPHA = 0x14
    private const val PILL_STROKE_ALPHA = 0x5A
    private const val PILL_GLOW_ALPHA = 0x1A
    private const val PRESSED_ALPHA = 0x0F

    /** 选中胶囊按下时的提亮倍率。 */
    private const val PRESSED_BRIGHTNESS = 1.35f

    /** 主色竖条：只有 18dp 高的一小段，垂直居中，不再贯穿整条轨道。 */
    private const val ACCENT_HEIGHT_DP = 18f

    /** 竖条宽 2dp，细到不会读成「又一层描边」。 */
    private const val ACCENT_WIDTH_DP = 2f

    /** 竖条离胶囊内缘 3dp：贴边画会和 1dp 描边叠成两道线，往外露又会跑出胶囊。 */
    private const val ACCENT_INSET_DP = 3f

    /** 竖条画在哪一侧：只画在胶囊朝外的那一边（贴轨道的那边），两侧有邻居就不画。 */
    const val ACCENT_NONE = 0
    const val ACCENT_START = 1
    const val ACCENT_END = 2

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
    @JvmOverloads
    fun itemBackground(context: Context, selected: Boolean, accentSides: Int = ACCENT_NONE): Drawable {
        val colors = barColors()
        val density = context.resources.displayMetrics.density
        val normal: Drawable = if (selected) {
            PillDrawable(colors, density, 1f, accentSides)
        } else {
            ColorDrawable(Color.TRANSPARENT)
        }
        val pressed: Drawable = if (selected) {
            PillDrawable(colors, density, PRESSED_BRIGHTNESS, accentSides)
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
        val sides = accentSidesOf(item)
        ViewCompat.setBackground(item, itemBackground(item.context, selected, sides))
        item.setTextColor(if (selected) ColorScheme.TEXT_PRIMARY else ColorScheme.TEXT_SECONDARY)
        // 不换 typeface：工程里 FontStyle 给全局换过字体，Typeface.BOLD 会把它顶掉
        item.paint.isFakeBoldText = selected
        item.invalidate()
    }

    /**
     * 这个格子的主色竖条该画在哪一侧。
     *
     * 只画在**贴轨道外边**的那一侧：最左格画左边、最右格画右边；
     * 夹在中间时两边都是邻居，竖条会变成一条「分隔线」横在格与格之间（用户反馈「这道实线贯穿整条轨道」），
     * 所以中间格干脆不画。单格（比如收藏夹页的搜索框）也不画，没有比较对象就没有意义。
     */
    private fun accentSidesOf(item: View): Int {
        val parent = item.parent as? ViewGroup ?: return ACCENT_NONE
        var index = -1
        var visible = 0
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (child === item) index = visible
            visible++
        }
        if (index < 0 || visible <= 1) return ACCENT_NONE
        return accentSidesAt(index, visible)
    }

    /**
     * 让整条分段条可以**按住横向拖动**来切换选项（像滑条一样），手指扫过哪一格就选中哪一格。
     *
     * 为什么要加：胶囊长得就像个能拖的滑块，点一下才动反而「不像那么回事」；
     * 手表屏窄，三格挤在一起时拖动比逐格点更省事。
     *
     * 实现上不接管容器，而是给**每个格子**挂同一个 OnTouchListener：Android 里消耗掉 DOWN 的那个
     * View 会一路收到后续 MOVE/UP（哪怕手指已经滑出它的边界），所以跨格拖动天然成立；
     * 命中哪一格用 `event.rawX` 和每个格子的屏幕坐标比，不依赖父容器坐标系（TabLayout 的页签
     * 在内部 ScrollView 里，父坐标会被滚动偏移带歪）。
     *
     * 代价：格子自己的点击/涟漪被我们吃掉，所以按 DOWN 就立刻选中（滑条的手感），
     * 不需要再等 UP；`performClick()` 只是为了让无障碍服务还能读到「这是一次点击」。
     *
     * @param excluded 不参与拖动的格子——同一行里混着的**动作**（比如稍后再看的「清除已看完」）
     *   要排除掉，否则手指扫过去会顺手弹出清空确认框。
     */
    @JvmStatic
    @JvmOverloads
    fun installDrag(bar: ViewGroup, excluded: View? = null) {
        val items = ArrayList<View>(bar.childCount)
        for (i in 0 until bar.childCount) {
            val child = bar.getChildAt(i)
            if (child.visibility == View.VISIBLE && child !== excluded) items.add(child)
        }
        attachDrag(items) { index -> items.getOrNull(index)?.performClick() }
    }

    /**
     * TabLayout 版的拖动：只有 `tabMode="fixed"` 才装。
     *
     * 可滚动页签（表情面板、更新日志）里横向拖动和「滑动看更多页签」是同一个手势，
     * 装上去会让后面几页永远滑不出来——宁可那里只保留点击。
     */
    @JvmStatic
    fun installDrag(tabs: TabLayout) {
        if (tabs.tabMode != TabLayout.MODE_FIXED) return
        val items = ArrayList<View>(tabs.tabCount)
        for (i in 0 until tabs.tabCount) {
            tabs.getTabAt(i)?.view?.let { items.add(it) }
        }
        attachDrag(items) { index -> tabs.getTabAt(index)?.select() }
    }

    /** 把同一个「按下即选、滑过即选」的监听挂到一组格子上。单格没必要拖，直接跳过。 */
    private fun attachDrag(items: List<View>, select: (Int) -> Unit) {
        if (items.size <= 1) return
        var current = -1
        val listener = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> current = -1
                MotionEvent.ACTION_CANCEL -> return@OnTouchListener true
                else -> Unit
            }
            val index = indexAtX(items, event.rawX)
            if (index != -1 && index != current) {
                current = index
                select(index)
            }
            true
        }
        for (item in items) item.setOnTouchListener(listener)
    }

    /** 屏幕横坐标落在第几格；落在整条两端外面就贴到首/末格，中间空隙按最近的格子算。 */
    private fun indexAtX(items: List<View>, rawX: Float): Int {
        if (items.isEmpty()) return -1
        val loc = IntArray(2)
        for ((index, item) in items.withIndex()) {
            item.getLocationOnScreen(loc)
            if (rawX >= loc[0] && rawX < loc[0] + item.width) return index
        }
        items.first().getLocationOnScreen(loc)
        if (rawX < loc[0]) return 0
        items.last().getLocationOnScreen(loc)
        return if (rawX >= loc[0] + items.last().width) items.lastIndex else -1
    }

    private fun accentSidesAt(index: Int, count: Int): Int {
        if (count <= 1) return ACCENT_NONE
        return when (index) {
            0 -> ACCENT_START
            count - 1 -> ACCENT_END
            else -> ACCENT_NONE
        }
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
            ViewCompat.setBackground(tabView, itemBackground(tabView.context, selected, accentSidesAt(i, tabs.tabCount)))
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
     * 高度**自己算**而不是靠 inset：和 [PillDrawable] 用同一条 `min(BAR_HEIGHT_DP, h - 2*sidelen)` 公式，
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
     * 选中胶囊：渐变填充 + 内描边 + 靠边的一段主色竖条。
     *
     * 不用 `GradientDrawable` 是因为它一个 Drawable 只能带**一条**描边，凑不出「内描边 + 外光晕」
     * 两层轮廓；手绘另一个好处是高度可以固定成 32dp 居中对齐（不随格子高度拉伸）。
     * 一个页面最多三五个格子，且只在状态切换时重建，不进绘制热路径。
     */
    private class PillDrawable(
        private val colors: BarColors,
        private val density: Float,
        private val brightness: Float,
        private val accentSides: Int
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
        private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = colors.accent
        }

        private val pill = RectF()
        private val accentStart = RectF()
        private val accentEnd = RectF()
        private val radius = RADIUS_DP * density

        override fun onBoundsChange(bounds: Rect) {
            val side = EDGE_INSET_DP * density
            val pillHeight = minOf(BAR_HEIGHT_DP * density, bounds.height() - 2 * side)
            val vInset = (bounds.height() - pillHeight) / 2f
            pill.set(bounds.left + side, bounds.top + vInset, bounds.right - side, bounds.bottom - vInset)

            // 主色竖条：16dp 高、2dp 宽、垂直居中，只在胶囊朝外（贴轨道）的那一侧露一小段。
            // 不做成「贯穿轨道」的长条——那会盖住格子边界，读起来像分隔线而不是选中指示。
            // 位置在胶囊内缘往里缩 3dp：贴边压线会和描边叠成两道线，往外露又会跑出胶囊。
            val barW = ACCENT_WIDTH_DP * density
            val barHalf = minOf(ACCENT_HEIGHT_DP * density, pill.height()) / 2f
            val barInset = ACCENT_INSET_DP * density
            val cy = pill.centerY()
            accentStart.set(
                pill.left + barInset, cy - barHalf,
                pill.left + barInset + barW, cy + barHalf
            )
            accentEnd.set(
                pill.right - barInset - barW, cy - barHalf,
                pill.right - barInset, cy + barHalf
            )
            if (accentSides and ACCENT_START == 0) accentStart.setEmpty()
            if (accentSides and ACCENT_END == 0) accentEnd.setEmpty()

            fillPaint.shader = if (pill.width() > 0f) LinearGradient(
                pill.left, 0f, pill.right, 0f,
                brighten(colors.pillStart), brighten(colors.pillEnd),
                Shader.TileMode.CLAMP
            ) else null
            strokePaint.color = brighten(colors.pillStroke)
        }

        override fun draw(canvas: Canvas) {
            if (pill.width() <= 0f || pill.height() <= 0f) return
            canvas.drawRoundRect(pill, radius, radius, fillPaint)
            canvas.drawRoundRect(pill, radius, radius, strokePaint)
            val barRadius = ACCENT_WIDTH_DP * density / 2f
            if (!accentStart.isEmpty) canvas.drawRoundRect(accentStart, barRadius, barRadius, accentPaint)
            if (!accentEnd.isEmpty) canvas.drawRoundRect(accentEnd, barRadius, barRadius, accentPaint)
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
