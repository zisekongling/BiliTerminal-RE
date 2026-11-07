package com.RobinNotBad.BiliClient.ui.appearance

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 外观模块四：**弥散光晕**（页面背景的边缘光晕 + 由光源向外的渐变底色），可在「外观设置」开关。
 *
 * 每套主题一份 [Spec]：光源处底色 + 1～2 团径向光晕（颜色 / 位置 / 半径各不相同），
 * 远离光源的一侧渐变回主题本来的背景色——光源在顶部（默认）时，列表下半部分、导航栏与关闭光晕时完全一致。
 * 光源位置（上 / 下 / 左 / 右 / 中）可在设置里选，[Spec] 只按「顶部」写一份，由 [placeGlow] / [baseGradient] 变换。
 *
 * ## 性能（手表优先，改这里前必读）
 * - **没有 blur**：CSS 里的 `filter: blur()` 在手表 GPU 上是整屏多次采样，代价最高的一步。
 *   这里用 4 段色标的径向渐变直接画出「已经模糊过」的柔和衰减，视觉等价、代价是一次普通填充。
 * - **没有噪点层**：噪点要再叠一层整屏纹理；改为 `Paint.isDither = true` 抑制渐变色带。
 * - Shader 只在 [AmbientGlowDrawable.onBoundsChange]（窗口尺寸变化）时创建，
 *   [AmbientGlowDrawable.draw] 里只有 1 次 `drawRect` + 1～2 次 `drawCircle`，无分配。
 * - 光晕只画在自己的圆内（不是整屏），声明 `OPAQUE` 让系统跳过其下方的绘制。
 * - **关闭时零开销**：[ColorScheme.applyWindowTheme] 走原来的纯色背景，本模块一行都不执行。
 *
 * 写入同其它外观模块一样只走 [AppearanceManager.setAmbientGlow]（递增外观版本号）。
 */
object AmbientGlow {

    const val KEY = SettingsKeys.UI_AMBIENT_GLOW

    /** 默认关闭：保持老用户观感不变，也不让低端表默认多付这点填充率。 */
    const val DEFAULT = false

    /** 线性渐变在高度的这个比例处回到主题背景色（对应参考稿的 `bottom-color 50%`）。 */
    const val FADE_END = 0.5f

    /** 亮色模式的渐变终点：浅底上色差更难察觉，顶部晕染多铺一段。 */
    const val LIGHT_FADE_END = 0.62f

    // ==================== 光源位置 ====================

    /** 光源位置的 SharedPreferences key。 */
    const val KEY_SOURCE = SettingsKeys.UI_AMBIENT_GLOW_SOURCE

    const val SOURCE_TOP = "top"
    const val SOURCE_BOTTOM = "bottom"
    const val SOURCE_LEFT = "left"
    const val SOURCE_RIGHT = "right"
    const val SOURCE_CENTER = "center"

    /** 默认贴顶：与加入本设置前的观感完全一致。 */
    const val DEFAULT_SOURCE = SOURCE_TOP

    /** 供设置页 `listChoose` 使用的候选值；显示名在 `strings.xml` 的 `ambient_glow_source_names`，顺序一一对应。 */
    val SOURCE_VALUES = listOf(SOURCE_TOP, SOURCE_BOTTOM, SOURCE_LEFT, SOURCE_RIGHT, SOURCE_CENTER)

    /**
     * 居中光源时半径的缩放：贴边时光晕有一半在屏外，居中时整团都在屏内，
     * 同样半径会显得「亮了一倍」；缩一档让观感接近贴边。
     */
    const val CENTER_RADIUS_SCALE = 0.8f

    /** 未知值一律回落到 [DEFAULT_SOURCE]。 */
    fun normalizeSource(saved: String?): String =
        if (saved != null && saved in SOURCE_VALUES) saved else DEFAULT_SOURCE

    fun currentSource(): String = normalizeSource(SharedPreferencesUtil.getString(KEY_SOURCE, DEFAULT_SOURCE))

    /**
     * 一团光晕。坐标按「光源在顶部」来写，其它光源位置由 [placeGlow] 旋转 / 镜像得到。
     * @param color 中心颜色（含 alpha，即参考稿的 `glow-color × glow-opacity`）
     * @param cx 沿光源所在边的位置，占该边长度的比例（光源在顶部时即横坐标占宽度的比例）
     * @param cy 离开光源所在边的深度，占 `min(宽, 高)` 的比例（手表方 / 圆屏宽高相等；手机竖屏保持贴边）
     * @param radius 半径，占 `min(宽, 高)` 的比例
     */
    class Glow(val color: Int, val cx: Float, val cy: Float, val radius: Float)

    /**
     * 一套主题的光晕方案：[top] 为顶部底色，底部颜色取主题背景色（见 [createDrawable]）；
     * [fadeEnd] 为线性渐变回到背景色的位置（占高度比例）。
     */
    class Spec(val top: Int, val glows: Array<Glow>, val fadeEnd: Float = FADE_END)

    fun isEnabled(): Boolean = SharedPreferencesUtil.getBoolean(KEY, DEFAULT)

    /**
     * 主题 key → 光晕方案（纯函数，供 JVM 单测）。
     *
     * 设计原则：顶部底色 = 背景色向主色偏 15% 左右；光晕色 = 主色压暗到不抢正文的亮度，
     * 对暖白 / 冷白正文保持足够对比度。
     */
    fun specFor(themeKey: String, light: Boolean = false): Spec =
        if (light) lightSpecFor(themeKey) else darkSpecFor(themeKey)

    /**
     * 亮色模式的光晕：浅底上「提亮」没有意义，改成**主色的彩色晕染**（像晨光透过彩色玻璃）。
     *
     * 26.10.26 加强：第一版沿用暗色的强度，实测在浅底上几乎看不出来（浅色区域里同样的色差，
     * 人眼感知更弱，而且晕染色比底色暗、读起来像「发灰」而不是「发光」）。现在按 CIELAB 色差对齐暗色：
     * 光晕中心对页面底色 ΔE ≈ 37～45（暗色 23～45），顶部底色 ΔE ≈ 13～15（暗色 8～24），
     * 半径放大一档、渐变回底色的位置下移到 62%。正文是深色，对比度不受影响。
     *
     * 亮色底色改米白后（见 `tools/appearance/gen_light_mode.py` 的 `CREAM`）：[Spec.top] = 米白底色向第一团光晕色
     * 混合到上述色差。**改了米白底色要重算这里的 top**，否则顶部会和底色接不上。
     */
    private fun lightSpecFor(themeKey: String): Spec = when (themeKey) {
        ColorScheme.THEME_ZHIHU_BLUE -> Spec(
            top = 0xFFCFDAE6.toInt(),
            glows = arrayOf(Glow(0x995AA2F8.toInt(), 0.5f, 0.04f, 0.70f)),
            fadeEnd = LIGHT_FADE_END
        )
        ColorScheme.THEME_IQIYI_GREEN -> Spec(
            top = 0xFFCBE5CA.toInt(),
            glows = arrayOf(Glow(0x8533CC7A.toInt(), 0.5f, 0.03f, 0.66f)),
            fadeEnd = LIGHT_FADE_END
        )
        ColorScheme.THEME_PURPLE_FANTASY -> Spec(
            top = 0xFFE5D0E1.toInt(),
            glows = arrayOf(
                Glow(0x76AD6BE3.toInt(), 0.5f, 0.05f, 0.72f),
                Glow(0x70C9A0F0.toInt(), 0.88f, 0.42f, 0.46f)
            ),
            fadeEnd = LIGHT_FADE_END
        )
        ColorScheme.THEME_RAINBOW_FANTASY -> Spec(
            top = 0xFFF8CDC3.toInt(),
            glows = arrayOf(
                Glow(0x99FF8080.toInt(), 0.22f, 0.03f, 0.62f),
                Glow(0x8C45C7BE.toInt(), 0.80f, 0.06f, 0.58f)
            ),
            fadeEnd = LIGHT_FADE_END
        )
        ColorScheme.THEME_CLASSIC_GRAY -> Spec(
            top = 0xFFDCD6E5.toInt(),
            glows = arrayOf(Glow(0x858C8CF5.toInt(), 0.5f, 0.04f, 0.68f)),
            fadeEnd = LIGHT_FADE_END
        )
        ColorScheme.THEME_CLASSIC_TERMINAL -> Spec(
            top = 0xFFF0C7C5.toInt(),
            glows = arrayOf(Glow(0x94FF7AA6.toInt(), 0.5f, 0.04f, 0.68f)),
            fadeEnd = LIGHT_FADE_END
        )
        else -> Spec(
            top = 0xFFF7CFD3.toInt(),
            glows = arrayOf(Glow(0x99FF85AD.toInt(), 0.5f, 0.04f, 0.70f)),
            fadeEnd = LIGHT_FADE_END
        )
    }

    private fun darkSpecFor(themeKey: String): Spec = when (themeKey) {
        // 知乎蓝：深海蓝，居中一团
        ColorScheme.THEME_ZHIHU_BLUE -> Spec(
            top = 0xFF142540.toInt(),
            glows = arrayOf(Glow(0xCC0F3D75.toInt(), 0.5f, 0.04f, 0.62f))
        )
        // 爱奇艺绿：主色很亮，光晕压得更暗、更小，避免整屏发绿
        ColorScheme.THEME_IQIYI_GREEN -> Spec(
            top = 0xFF10291F.toInt(),
            glows = arrayOf(Glow(0xB30D4D30.toInt(), 0.5f, 0.02f, 0.58f))
        )
        // 紫色空灵：顶部主光晕 + 右侧中部一团淡紫余晖，做出「空灵」的层次
        ColorScheme.THEME_PURPLE_FANTASY -> Spec(
            top = 0xFF26173D.toInt(),
            glows = arrayOf(
                Glow(0xCC4A2378.toInt(), 0.5f, 0.05f, 0.66f),
                Glow(0x80331C5C.toInt(), 0.88f, 0.42f, 0.42f)
            )
        )
        // 五彩斑斓：左上珊瑚红 + 右上青绿（取自 rainbow_red / rainbow_green），双色交叠
        ColorScheme.THEME_RAINBOW_FANTASY -> Spec(
            top = 0xFF2B1B22.toInt(),
            glows = arrayOf(
                Glow(0xB85E2C2C.toInt(), 0.22f, 0.03f, 0.55f),
                Glow(0xA0184A4D.toInt(), 0.80f, 0.06f, 0.52f)
            )
        )
        // 经典灰：低饱和的雾紫
        ColorScheme.THEME_CLASSIC_GRAY -> Spec(
            top = 0xFF20203A.toInt(),
            glows = arrayOf(Glow(0xB3363663.toInt(), 0.5f, 0.04f, 0.60f))
        )
        // 经典终端：即参考稿默认参数（#3b151f / #4f1b2c × 0.8），底部回到纯黑
        ColorScheme.THEME_CLASSIC_TERMINAL -> Spec(
            top = 0xFF3B151F.toInt(),
            glows = arrayOf(Glow(0xCC4F1B2C.toInt(), 0.5f, 0.04f, 0.60f))
        )
        // B站粉（及未知 key 的兜底）：玫瑰粉
        else -> Spec(
            top = 0xFF3A1A2B.toInt(),
            glows = arrayOf(Glow(0xCC5C2342.toInt(), 0.5f, 0.04f, 0.62f))
        )
    }

    /** 为当前主题创建**窗口背景** drawable（底色渐变 + 光晕团，声明 OPAQUE）。每个 Activity 创建一次（冷路径），不缓存。 */
    fun createDrawable(
        themeKey: String,
        background: Int,
        light: Boolean = false,
        source: String = DEFAULT_SOURCE
    ): Drawable = AmbientGlowDrawable(specFor(themeKey, light), background, normalizeSource(source))

    /**
     * 叠在自定义背景图**上面**的光晕层（只有光晕圆，没有底色渐变）。
     *
     * 为什么需要单独一份：正常光晕是整屏窗口背景（`drawRect` 铺满 + 声明 OPAQUE），
     * 直接叠在背景图上会把图整块盖住。这里改用 [AmbientGlowDrawable] 的叠加模式：只画那 1～2 团
     * 径向光晕、不画底色、声明 TRANSLUCENT，于是「背景图 + 光晕」能同时存在：图在下面，光晕像一层光洒在图上。
     *
     * [alphaFactor] 再压一次整体透明度（背景图本身有明暗细节，光晕照 1.0 会糊成一片）。
     */
    fun createOverlayDrawable(
        themeKey: String,
        light: Boolean = false,
        source: String = DEFAULT_SOURCE,
        alphaFactor: Float = OVERLAY_ALPHA
    ): Drawable = AmbientGlowDrawable(
        specFor(themeKey, light),
        null,
        normalizeSource(source),
        alphaFactor.coerceIn(0f, 1f)
    )

    /** 叠在背景图上时的整体透明度：既看得出光晕，又不至于把图糊掉。 */
    const val OVERLAY_ALPHA = 0.55f

    /**
     * 把一团光晕（按「光源在顶部」写的坐标）放到 [source] 指定的位置，返回 `[cx, cy, r]`（像素，相对 bounds 左上角）。
     * 纯函数，供 JVM 单测。只在 `onBoundsChange` 调用，不在 draw 热路径上。
     *
     * - 上 / 下：沿宽度铺开，深度从上 / 下边量起；
     * - 左 / 右：把顶部方案顺时针 / 逆时针转 90°，沿高度铺开，深度从左 / 右边量起；
     * - 中：方案里各团光晕的**重心**移到屏幕中心、相对位置不变，半径乘 [CENTER_RADIUS_SCALE]。
     *   用重心而不是第一团，是为了让「左红右青」这种对称双色居中后仍然左右对称。
     */
    internal fun placeGlow(spec: Spec, index: Int, source: String, w: Float, h: Float): FloatArray {
        val g = spec.glows[index]
        val unit = min(w, h)
        val r = max(1f, unit * g.radius)
        return when (source) {
            SOURCE_BOTTOM -> floatArrayOf(w * g.cx, h - unit * g.cy, r)
            SOURCE_LEFT -> floatArrayOf(unit * g.cy, h * g.cx, r)
            SOURCE_RIGHT -> floatArrayOf(w - unit * g.cy, h * g.cx, r)
            SOURCE_CENTER -> {
                val meanX = spec.glows.map { it.cx }.average().toFloat()
                val meanY = spec.glows.map { it.cy }.average().toFloat()
                floatArrayOf(
                    w / 2f + w * (g.cx - meanX),
                    h / 2f + unit * (g.cy - meanY),
                    max(1f, r * CENTER_RADIUS_SCALE)
                )
            }
            else -> floatArrayOf(w * g.cx, unit * g.cy, r)
        }
    }

    /**
     * 底色渐变的几何，返回 `[x0, y0, x1, y1]`（像素，相对 bounds 左上角）：
     * 从光源处的 [Spec.top] 渐变到 `x1, y1` 处的背景色。
     * 上 / 下 / 左 / 右为线性渐变（终点在 [Spec.fadeEnd] 处）；
     * 中为径向渐变，`x0, y0` 是圆心，`x1` 是半径（半对角线 × fadeEnd，`y1` 无意义填 0）。
     */
    internal fun baseGradient(spec: Spec, source: String, w: Float, h: Float): FloatArray = when (source) {
        SOURCE_BOTTOM -> floatArrayOf(0f, h, 0f, h * (1f - spec.fadeEnd))
        SOURCE_LEFT -> floatArrayOf(0f, 0f, w * spec.fadeEnd, 0f)
        SOURCE_RIGHT -> floatArrayOf(w, 0f, w * (1f - spec.fadeEnd), 0f)
        SOURCE_CENTER -> floatArrayOf(
            w / 2f, h / 2f,
            max(1f, sqrt(w * w + h * h) / 2f * spec.fadeEnd), 0f
        )
        else -> floatArrayOf(0f, 0f, 0f, h * spec.fadeEnd)
    }

    /** 保留 RGB、把 alpha 乘以 [factor]。渐变终点用「同色 alpha=0」而不是透明黑，避免边缘发灰。 */
    internal fun scaleAlpha(color: Int, factor: Float): Int {
        val a = ((color ushr 24) * factor + 0.5f).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    /**
     * 径向光晕的色标：模拟「实心中心 → 65% 处透明」再经 blur 后的平滑衰减（近似 smoothstep）。
     * 位置与 alpha 系数一一对应。
     */
    internal val GLOW_STOPS = floatArrayOf(0f, 0.3f, 0.62f, 1f)
    internal val GLOW_ALPHAS = floatArrayOf(1f, 0.75f, 0.32f, 0f)

    /**
     * 唯一一份光晕 Drawable，两种用法靠 [bottom] 区分：
     *
     * - **窗口背景**（[bottom] 非空）：先铺一层「光源处 [Spec.top] → [bottom]」的底色渐变，再叠光晕团，
     *   声明 [PixelFormat.OPAQUE] 让系统跳过它下方内容的绘制（见 [createDrawable]）；
     * - **背景图上的叠加层**（[bottom] 为 null）：只画光晕团、不画底色，声明 [PixelFormat.TRANSLUCENT]，
     *   于是「背景图 + 光晕」能同时存在（见 [createOverlayDrawable]）。
     *
     * 26.11.07 合并：此前这里是两份几乎相同的实现——本类（窗口背景）与 AmbientGlowOverlayDrawable
     * （叠加层），几何计算、色标、Drawable 三件套契约全是重复代码，只差「画不画底色」与整体透明度。
     * 合并成一份，免得住一处忘另一处。
     *
     * @param bottom 底色渐变的终点色；null 表示叠加层（不画底色）
     * @param alphaFactor 光晕整体的 alpha 系数：窗口背景 1.0，叠在背景图上用 [OVERLAY_ALPHA] 压一档
     */
    private class AmbientGlowDrawable(
        private val spec: Spec,
        private val bottom: Int?,
        private val source: String,
        private val alphaFactor: Float = 1f
    ) : Drawable() {

        /** 底色渐变的画笔；叠加层没有底色，保持 null。 */
        private val basePaint = if (bottom != null) Paint().apply { isDither = true } else null
        private val glowPaints = Array(spec.glows.size) { Paint().apply { isDither = true } }
        /** 每团光晕的 (cx, cy, r)，在 onBoundsChange 里算好，draw 时直接用。 */
        private val geometry = FloatArray(spec.glows.size * 3)
        private var ready = false

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            if (w <= 0f || h <= 0f) {
                ready = false
                return
            }
            val left = bounds.left.toFloat()
            val top = bounds.top.toFloat()

            val bg = bottom
            if (bg != null) {
                val base = baseGradient(spec, source, w, h)
                basePaint?.shader = if (source == SOURCE_CENTER) {
                    RadialGradient(left + base[0], top + base[1], base[2], spec.top, bg, Shader.TileMode.CLAMP)
                } else {
                    LinearGradient(
                        left + base[0], top + base[1], left + base[2], top + base[3],
                        spec.top, bg, Shader.TileMode.CLAMP
                    )
                }
            }
            for (i in spec.glows.indices) {
                val g = spec.glows[i]
                val p = placeGlow(spec, i, source, w, h)
                val cx = left + p[0]
                val cy = top + p[1]
                val r = p[2]
                val colors = IntArray(GLOW_ALPHAS.size) {
                    scaleAlpha(g.color, GLOW_ALPHAS[it] * alphaFactor)
                }
                glowPaints[i].shader = RadialGradient(cx, cy, r, colors, GLOW_STOPS, Shader.TileMode.CLAMP)
                geometry[i * 3] = cx
                geometry[i * 3 + 1] = cy
                geometry[i * 3 + 2] = r
            }
            ready = true
        }

        override fun draw(canvas: Canvas) {
            val bg = bottom
            if (!ready) {
                // 尺寸还没量出来：窗口背景先把底色铺满（绝不能透出后画的内容），叠加层什么都不画。
                if (bg != null) canvas.drawColor(bg)
                return
            }
            if (bg != null) basePaint?.let { canvas.drawRect(bounds, it) }
            for (i in glowPaints.indices) {
                canvas.drawCircle(geometry[i * 3], geometry[i * 3 + 1], geometry[i * 3 + 2], glowPaints[i])
            }
        }

        // 不做整体透明度 / 滤色：叠加层要压暗走构造参数的 alphaFactor，窗口背景本就不该半透明。
        override fun setAlpha(alpha: Int) {}

        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int =
            if (bottom != null) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT
    }

}
