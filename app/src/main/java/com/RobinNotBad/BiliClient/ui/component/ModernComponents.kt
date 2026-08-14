package com.RobinNotBad.BiliClient.ui.component

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.RobinNotBad.BiliClient.ui.theme.BiliColors
import com.RobinNotBad.BiliClient.ui.theme.BiliDimens
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager

class ModernLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        color = ThemeManager.PRIMARY
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ThemeManager.PRIMARY_LIGHT
    }

    private var rotation = 0f
    private var scale = 1f
    private var animator: ValueAnimator? = null
    private val dots = listOf(
        Dot(0.5f, 0.25f, 0f),
        Dot(0.75f, 0.4f, 0.15f),
        Dot(0.75f, 0.6f, 0.3f),
        Dot(0.5f, 0.75f, 0.45f),
        Dot(0.25f, 0.6f, 0.6f),
        Dot(0.25f, 0.4f, 0.75f)
    )

    private val rect = RectF()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = 8f
        rect.set(padding, padding, w - padding, h - padding)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.rotate(rotation, width / 2f, height / 2f)
        canvas.scale(scale, scale, width / 2f, height / 2f)

        canvas.drawArc(rect, 0f, 280f, false, paint)

        val cx = width / 2f
        val cy = height / 2f
        val radius = width * 0.3f

        dots.forEach { dot ->
            val angle = ((rotation + dot.phase * 360f) % 360f) * Math.PI.toFloat() / 180f
            val dx = cx + radius * kotlin.math.cos(angle.toDouble()).toFloat()
            val dy = cy + radius * kotlin.math.sin(angle.toDouble()).toFloat()
            val alpha = ((kotlin.math.cos(((rotation + dot.phase * 360f) % 360f) * Math.PI / 180.0) + 1) / 2 * 255).toInt()
            dotPaint.alpha = alpha.coerceIn(40, 200)
            canvas.drawCircle(dx, dy, 3f, dotPaint)
        }

        canvas.restore()
    }

    private fun startAnimation() {
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1500L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                rotation = animation.animatedValue as Float
                scale = 1f + 0.05f * kotlin.math.sin((rotation * Math.PI / 180f).toFloat())
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    private data class Dot(val xRatio: Float, val yRatio: Float, val phase: Float)
}

class BiliVideoCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemeManager.CARD
    }

    private val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemeManager.TEXT_PRIMARY
        textSize = BiliDimens.BODY_MEDIUM
        typeface = Typeface.DEFAULT_BOLD
    }

    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemeManager.TEXT_SECONDARY
        textSize = BiliDimens.BODY_MEDIUM
    }

    private val statPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemeManager.TEXT_TERTIARY
        textSize = BiliDimens.BODY_SMALL
    }

    private val cornerRadius = 12f
    private val coverRect = RectF()
    private var coverBitmap: Bitmap? = null

    var title: String = ""
        set(value) { field = value; invalidate() }
    var author: String = ""
        set(value) { field = value; invalidate() }
    var views: String = ""
        set(value) { field = value; invalidate() }
    var duration: String = ""
        set(value) { field = value; invalidate() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val coverSize = h - 16f
        coverRect.set(8f, 8f, 8f + coverSize, 8f + coverSize)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius, cornerRadius, bgPaint)

        if (coverBitmap != null) {
            canvas.drawBitmap(coverBitmap!!, null, coverRect, coverPaint)
        }

        val textX = coverRect.right + 12f
        val textWidth = width - textX - 12f

        canvas.drawText(title, textX, 24f, titlePaint)
        canvas.drawText(author, textX, 44f, subtitlePaint)
        canvas.drawText("$views  |  $duration", textX, 62f, statPaint)
    }

    fun setCoverBitmap(bitmap: Bitmap) {
        coverBitmap = bitmap
        invalidate()
    }
}