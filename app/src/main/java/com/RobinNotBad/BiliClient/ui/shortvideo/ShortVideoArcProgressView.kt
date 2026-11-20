package com.RobinNotBad.BiliClient.ui.shortvideo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 短视频圆屏的贴表圈进度弧（设计稿 design/short-video-redesign.html 圆屏规格：
 * r=111 @ R=116.5，225° → 315° 经过正下方，描边 2 / 拖动 4 / 沉浸 1.5，沉浸不透明度 0.75）。
 *
 * **为什么不直接复用长视频的 PlayerArcProgressView**：那份把 200°/140° 起止角、
 * r=108/110、高能刻度、SponsorBlock 片段条与写死的 #FF6699 都焊在类里，且属于
 * `player/`（长视频新版并行改动区）。短视频弧只有 90°、无刻度无片段、颜色跟主题，
 * 所以这里单写一个轻量版；**角度 ↔ 触点的换算没有重造**——同一套公式抽成了
 * [ShortVideoLayoutSpec.arcFraction] 纯函数（带单测），两边语义一致。
 *
 * 几何全部由 [ShortVideoLayoutSpec] 按控件实际尺寸推出来，不写死 233 / 116.5：
 * 466px@320dpi、384px@320dpi、454px@320dpi 等表盘都成立。
 *
 * 本类只把触点换算成进度并回调，真正的 seek 由 [bind] 进来的回调执行。
 */
class ShortVideoArcProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var fraction = 0f
    private var dragging = false
    private var idle = false

    /** 以下像素量都在 onSizeChanged 里按规格重算，换分辨率只换尺寸。 */
    private var radius = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var strokeNormal = 0f
    private var strokeDragging = 0f
    private var strokeIdle = 0f
    private var touchBand = 0f

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val progress = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arc = RectF()

    private var onSeek: ((Float, Boolean) -> Unit)? = null

    /** 接入 seek 回调：(进度 0..1, 是否松手)。拖动中只刷新画面，松手才真正 seek。 */
    fun bind(seek: (Float, Boolean) -> Unit) {
        onSeek = seek
    }

    /** 已播色与轨道色由调用方按主题算好传入（ColorScheme.PLAYER_PROGRESS_FILL + 对比度退档）。 */
    fun setColors(fill: Int, trackColor: Int) {
        progress.color = fill
        track.color = trackColor
        invalidate()
    }

    fun setProgress(value: Int, max: Int) {
        // 拖动中不被播放回调拉回：松手前画面以手指为准（与 SeekBar 的 isPressed 判据同义）。
        if (dragging) return
        fraction = if (max <= 0) 0f else (value.toFloat() / max).coerceIn(0f, 1f)
        invalidate()
    }

    /** 沉浸态：细弧（1.5dp、75% 不透明度），并强制结束拖动。 */
    fun setIdle(isIdle: Boolean) {
        idle = isIdle
        if (isIdle) dragging = false
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val density = resources.displayMetrics.density
        if (w <= 0 || h <= 0 || density <= 0f) return
        val m = ShortVideoLayoutSpec.Metrics(w / density, h / density, round = true)
        centerX = w / 2f
        centerY = h / 2f
        radius = ShortVideoLayoutSpec.roundArcRadius(m) * density
        strokeNormal = ShortVideoLayoutSpec.progressTrackHeight(m) * density
        strokeDragging = ShortVideoLayoutSpec.progressTrackHeightDragging(m) * density
        strokeIdle = ShortVideoLayoutSpec.progressTrackHeightIdle(m) * density
        touchBand = ShortVideoLayoutSpec.roundArcTouchBand(m) * density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (radius <= 0f) return
        arc.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        val stroke = if (idle) strokeIdle else if (dragging) strokeDragging else strokeNormal
        track.strokeWidth = stroke
        progress.strokeWidth = stroke
        // 设计稿 thin 分支 opacity 0.75：沉浸时弧退成一条不抢画面的细线。
        val alpha = if (idle) 191 else 255
        track.alpha = alpha
        progress.alpha = alpha
        // Canvas 角 0° 在正右、顺时针为正；数学角 θ 对应 Canvas 角 360-θ，扫角取负。
        val start = 360f - ShortVideoLayoutSpec.ROUND_ARC_START_DEGREE
        val sweep = -ShortVideoLayoutSpec.ROUND_ARC_SWEEP_DEGREE
        canvas.drawArc(arc, start, sweep, false, track)
        if (fraction > 0f) canvas.drawArc(arc, start, sweep * fraction, false, progress)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 只有真正碰到表圈进度带才接管；其余空白留给单击 / 双击 / 长按手势。
                val distance = hypot(event.x - centerX, event.y - centerY)
                if (radius <= 0f || abs(distance - radius) > touchBand) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                dragging = true
                update(event.x, event.y, false)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) update(event.x, event.y, false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) update(event.x, event.y, true)
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun update(x: Float, y: Float, finished: Boolean) {
        fraction = ShortVideoLayoutSpec.arcFraction(centerX, centerY, x, y)
        invalidate()
        onSeek?.invoke(fraction, finished)
    }
}