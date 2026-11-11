package com.RobinNotBad.BiliClient.util.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.LinearInterpolator
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlin.math.roundToInt

/**
 * 设置页「全局搜索命中行」的高亮：满亮保持 [HOLD_MS]，随后用 [FADE_MS] 淡出恢复原样。
 *
 * 定位（滚动到哪一行）由 [com.RobinNotBad.BiliClient.activity.base.RefreshListActivity.scrollToHighlight]
 * 负责，这里只管视觉。
 *
 * **为什么不用 View 的前景层**：任意 View 的 `setForeground` 要 API 23+，本项目 minSdk 19，
 * 所以按行的根视图类型分别下手：
 * - `MaterialCardView`（开关/选择/输入/列表选择/导航行）：改描边 + 卡片底色。仓库里「选中卡片」
 *   就是这个写法（见 `adapter/favorite/FolderChooseAdapter`、`SettingPlayerChooseActivity`）。
 * - `MaterialButton`（button 行）：改背景 tint。
 * - 其余（title 行的 `LinearLayout`）：套一层主色淡底的可绘制背景。
 *
 * **为什么用 keyed tag 存原始值**：RecyclerView 会把同一个 itemView 复用给别的行，绑定时必须能
 * 把上一行的样式原样还回去。原始描边宽度/颜色、卡片底色、背景 Drawable 都存在
 * [R.id.tag_setting_highlight] 里，高亮结束后即清空。
 */
object SettingsHighlight {

    /** 满亮保持多久（毫秒）。 */
    private const val HOLD_MS = 1500L

    /** 淡出用多久（毫秒）。 */
    private const val FADE_MS = 450L

    /** 卡片描边在满亮时的宽度。 */
    private const val STROKE_DP = 2f

    /** 卡片底色向主色靠的比例（只是淡淡一层，别盖掉卡片自己的底色/玻璃效果）。 */
    private const val CARD_TINT = 0.18f

    /** 无卡片根的行的底色向主色靠的比例。 */
    private const val PLATE_TINT = 0.55f

    /** 标题行淡底的不透明度（0..255）。 */
    private const val PLATE_ALPHA = 0x3D

    /** 标题行淡底的圆角。 */
    private const val PLATE_CORNER_DP = 6f

    /** 全透明 ARGB（不用 `android.graphics.Color.TRANSPARENT`，好让配色函数能上纯 JVM 单测）。 */
    private const val TRANSPARENT = 0

    /**
     * 绑定一行的当前状态。[highlighted]=true 播一次高亮，false 把可能残留的高亮样式还原。
     *
     * 每次 `onBindViewHolder` 都要调（包括非目标行）：复用过来的 itemView 上可能还带着
     * 上一行的高亮。
     */
    fun bind(itemView: View, highlighted: Boolean) {
        val attached = itemView.getTag(R.id.tag_setting_highlight) as? Skin
        if (!highlighted) {
            attached?.stopAndRestore()
            return
        }
        val skin = attached ?: createSkin(itemView)?.also {
            itemView.setTag(R.id.tag_setting_highlight, it)
        } ?: return
        skin.start()
    }

    private fun createSkin(itemView: View): Skin? = when (itemView) {
        is MaterialCardView -> CardSkin(itemView)
        is MaterialButton -> ButtonSkin(itemView)
        // 标题行是 LinearLayout，分割线也是别的 View，统一走「淡底」这条路
        else -> PlateSkin(itemView)
    }

    /**
     * 高亮强度曲线：前 [HOLD_MS] 保持 1，之后线性降到 0。
     *
     * [fraction] 是整段动画的进度 0..1（动画用线性插值器，与时间成正比）。
     */
    internal fun fadeFactor(fraction: Float): Float {
        val hold = HOLD_MS.toFloat() / (HOLD_MS + FADE_MS).toFloat()
        if (fraction <= hold) return 1f
        val faded = 1f - (fraction - hold) / (1f - hold)
        return faded.coerceIn(0f, 1f)
    }

    /**
     * 按 ARGB 四通道线性插值，[t] 夹到 0..1（0=[from]，1=[to]）。
     *
     * 只用位运算、不碰 `android.graphics.Color`，这样这条曲线能上纯 JVM 单测。
     */
    internal fun lerpColor(from: Int, to: Int, t: Float): Int {
        val ratio = t.coerceIn(0f, 1f)
        val a = lerpChannel(from ushr 24 and 0xFF, to ushr 24 and 0xFF, ratio)
        val r = lerpChannel(from ushr 16 and 0xFF, to ushr 16 and 0xFF, ratio)
        val g = lerpChannel(from ushr 8 and 0xFF, to ushr 8 and 0xFF, ratio)
        val b = lerpChannel(from and 0xFF, to and 0xFF, ratio)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** 从 [from] 向 [to] 混 [ratio]（等价于 [lerpColor]，名字用于「底色向主色靠」的语义）。 */
    internal fun blend(from: Int, to: Int, ratio: Float): Int = lerpColor(from, to, ratio)

    private fun lerpChannel(from: Int, to: Int, t: Float): Int =
        (from + (to - from) * t).roundToInt().coerceIn(0, 255)

    /**
     * 一行的原始样式 + 高亮动画。
     *
     * 原始值在构造时抓取——那一刻这一行还是原样的（上一次高亮结束即还原并摘掉 tag）。
     */
    private abstract class Skin(protected val view: View) {
        private var animator: ValueAnimator? = null

        /** [t]=1 满亮，0 完全原样。 */
        abstract fun apply(t: Float)

        /** 还原成构造时抓到的样子。 */
        abstract fun restore()

        fun start() {
            cancel()
            apply(1f)
            val animator = ValueAnimator.ofFloat(0f, 1f)
            animator.duration = HOLD_MS + FADE_MS
            animator.interpolator = LinearInterpolator()
            animator.addUpdateListener { animation ->
                apply(fadeFactor(animation.animatedValue as Float))
            }
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 被 cancel() 掉的旧动画不要收尾，否则会把新绑定的行改成旧样式
                    if (this@Skin.animator !== animation) return
                    this@Skin.animator = null
                    restore()
                    view.setTag(R.id.tag_setting_highlight, null)
                }
            })
            this.animator = animator
            animator.start()
        }

        /** 停掉动画并还原（复用给别的行、或高亮结束时用）。 */
        fun stopAndRestore() {
            cancel()
            restore()
            view.setTag(R.id.tag_setting_highlight, null)
        }

        private fun cancel() {
            val running = animator
            animator = null
            running?.cancel()
        }
    }

    private class CardSkin(private val card: MaterialCardView) : Skin(card) {
        private val baseStrokeWidth: Int = card.strokeWidth
        // 描边色在主题里是 ColorStateList，整条存下来才能原样还回去；
        // 注意别用 card.strokeColor（getStrokeColor 已废弃，返回的 Int 会丢掉状态）
        private val baseStrokeColors: ColorStateList? = card.strokeColorStateList
        private val baseStrokeColor: Int = baseStrokeColors?.defaultColor ?: TRANSPARENT
        private val baseCardColors: ColorStateList? = card.cardBackgroundColor
        private val baseCardColor: Int = baseCardColors?.defaultColor ?: TRANSPARENT

        private val glowStrokeWidth: Int = ToolsUtil.dp2px(STROKE_DP)
        private val glowStrokeColor: Int = ColorScheme.PRIMARY
        private val glowCardColor: Int = blend(baseCardColor, ColorScheme.PRIMARY, CARD_TINT)

        override fun apply(t: Float) {
            card.strokeWidth = (baseStrokeWidth + (glowStrokeWidth - baseStrokeWidth) * t).roundToInt()
            card.setStrokeColor(lerpColor(baseStrokeColor, glowStrokeColor, t))
            card.setCardBackgroundColor(lerpColor(baseCardColor, glowCardColor, t))
        }

        override fun restore() {
            card.strokeWidth = baseStrokeWidth
            baseStrokeColors?.let { card.setStrokeColor(it) }
            // 卡片底色是 ColorStateList（主题里可能有按压态），原样还回去
            baseCardColors?.let { card.setCardBackgroundColor(it) }
        }
    }

    private class ButtonSkin(private val button: MaterialButton) : Skin(button) {
        private val baseTint: ColorStateList? = button.backgroundTintList
        private val baseColor: Int = baseTint?.defaultColor ?: TRANSPARENT
        private val glowColor: Int = blend(baseColor, ColorScheme.PRIMARY, PLATE_TINT)

        override fun apply(t: Float) {
            button.backgroundTintList = ColorStateList.valueOf(lerpColor(baseColor, glowColor, t))
        }

        override fun restore() {
            // baseTint 为 null 时置回 null 就是 MaterialButton 的默认 tint
            button.backgroundTintList = baseTint
        }
    }

    private class PlateSkin(view: View) : Skin(view) {
        private val original: Drawable? = view.background
        private val plate: GradientDrawable = GradientDrawable().apply {
            cornerRadius = ToolsUtil.dp2px(PLATE_CORNER_DP).toFloat()
        }
        private val glowColor: Int = ColorScheme.withPrimaryAlpha(PLATE_ALPHA)

        init {
            // 只在这里换一次背景，动画里只改颜色：每帧 setBackground 会触发 requestLayout
            view.background = plate
        }

        override fun apply(t: Float) {
            plate.setColor(lerpColor(TRANSPARENT, glowColor, t))
        }

        override fun restore() {
            view.background = original
        }
    }
}
