package com.RobinNotBad.BiliClient.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * 圆屏贴表圈进度弧（对应设计稿 design/player-redesign-v2.html 的 `rArc`）。
 *
 * 设计稿口径：r=108、200°→340°（经过 270°，即屏幕下方那半圈）；描边 3、拖动 5；
 * 拇指半径 4（拖动 6），白填充 + #FF6699 描边；弧内侧 4–10dp 是弹幕热度刻度；
 * 沉浸态退化成 r=110 的白 16% 细线 + 粉色进度，不画刻度与拇指。
 * 角度按数学极坐标（3 点钟方向逆时针为正、y 轴向上）。
 *
 * Canvas.drawArc 的 0° 在正右、顺时针为正，所以数学角 θ 对应 Canvas 角 360-θ：
 * 起点 200° → 160°、终点 340° → 20°，只有 sweep 取负（-140°）才是屏幕下方那半圈。
 * 本类只把触点换算成进度，真正的 seek 仍由原 SeekBar 的回调执行。
 */
class PlayerArcProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var fraction = 0f
    private var dragging = false
    private var idle = true

    /** 弧半径（dp）与 1dp 对应的像素数，随控件尺寸等比缩放。 */
    private var unit = 1f
    private var centerX = 0f
    private var centerY = 0f

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0x4dffffff
    }
    private val buffer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0x61ffffff
    }
    private val progress = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        // 设计稿 rArc 的已播色写死 #FF6699，不跟主题主色走。
        color = 0xffff6699.toInt()
    }
    private val heat = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0x4dffffff
    }
    private val thumbFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xffffffff.toInt()
    }
    private val thumbStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xffff6699.toInt()
    }
    private val arc = RectF()

    fun bind(seek: (Float, Boolean) -> Unit) { onSeek = seek }

    private var onSeek: ((Float, Boolean) -> Unit)? = null

    /** 真实高能波形；null 时退回设计稿的示意刻度（[HEAT]）。 */
    private var heatData: FloatArray? = null

    fun setProgress(value: Int, max: Int) {
        fraction = if (max <= 0) 0f else (value.toFloat() / max).coerceIn(0f, 1f)
        invalidate()
    }

    fun setIdle(isIdle: Boolean) {
        idle = isIdle
        if (isIdle) dragging = false
        invalidate()
    }

    /** 接入真实高能数据；传 null 或空数组恢复设计稿的示意刻度。 */
    fun setHeat(data: FloatArray?) {
        heatData = if (data == null || data.isEmpty()) null else data
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 233dp 参考边长铺满控件时 1dp 对应多少像素；圆屏按实际边长等比缩放。
        unit = min(w, h) / PlayerNewUiGeometry.REFERENCE_SIZE_DP
        centerX = w / 2f
        centerY = h / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (unit <= 0f) return
        // 沉浸态半径 110、控制层显示时 108（设计稿 rArc 的 thin 分支与主分支）。
        val radius = (if (idle) 110f else 108f) * unit
        arc.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        val width = 1f * unit
        val stroke = if (idle) 2f else if (dragging) 5f else 3f
        track.strokeWidth = stroke * width
        buffer.strokeWidth = track.strokeWidth
        progress.strokeWidth = track.strokeWidth

        val start = 360f - START_DEGREE
        val sweep = -SWEEP_DEGREE
        if (idle) {
            // 沉浸态：一圈白 16% 细线 + 粉色进度，没有热度刻度，也没有拇指。
            track.color = 0x29ffffff
            canvas.drawArc(arc, start, sweep, false, track)
            canvas.drawArc(arc, start, sweep * fraction, false, progress)
            return
        }
        drawHeat(canvas)
        track.color = 0x4dffffff
        canvas.drawArc(arc, start, sweep, false, track)
        // 缓冲段至少 55%，并比已播多 8%（设计稿 buf = max(.55, p+.08)）。
        val buffered = (fraction + 0.08f).coerceAtLeast(0.55f).coerceAtMost(1f)
        canvas.drawArc(arc, start, sweep * buffered, false, buffer)
        canvas.drawArc(arc, start, sweep * fraction, false, progress)
        drawThumb(canvas, radius)
    }

    /** 弧内侧的热度刻度：外端固定 r=103，向内最多缩 5.5dp。优先用真实高能数据。 */
    private fun drawHeat(canvas: Canvas) {
        heat.strokeWidth = 1.6f * unit
        val outer = 103f * unit
        val data = heatData ?: HEAT
        var peak = 0f
        for (value in data) if (value > peak) peak = value
        if (peak <= 0f) return
        val last = data.size - 1
        for (index in data.indices) {
            val degree = START_DEGREE + SWEEP_DEGREE * index / last.coerceAtLeast(1)
            val radians = Math.toRadians(degree.toDouble())
            val length = (data[index] / peak).coerceIn(0f, 1f) * 5.5f * unit
            val cos = cos(radians).toFloat()
            val sin = sin(radians).toFloat()
            canvas.drawLine(
                centerX + outer * cos,
                centerY - outer * sin,
                centerX + (outer - length) * cos,
                centerY - (outer - length) * sin,
                heat
            )
        }
    }

    private fun drawThumb(canvas: Canvas, radius: Float) {
        val degree = START_DEGREE + SWEEP_DEGREE * fraction
        val radians = Math.toRadians(degree.toDouble())
        val x = centerX + radius * cos(radians).toFloat()
        val y = centerY - radius * sin(radians).toFloat()
        val size = (if (dragging) 6f else 4f) * unit
        canvas.drawCircle(x, y, size, thumbFill)
        thumbStroke.strokeWidth = 2f * unit
        canvas.drawCircle(x, y, size, thumbStroke)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 只有真正碰到表圈进度带才接管触摸；整层空白处必须留给中心播放键与双击手势。
                val band = 12f * unit
                val distance = kotlin.math.hypot(event.x - centerX, event.y - centerY)
                val radius = (if (idle) 110f else 108f) * unit
                if (radius <= 0f || kotlin.math.abs(distance - radius) > band) return false
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
        // atan2 的 y 分量取屏幕坐标（向下为正），得到的正是 Canvas 角（顺时针为正）。
        var angle = Math.toDegrees(atan2((y - centerY).toDouble(), (x - centerX).toDouble())).toFloat()
        if (angle < 0f) angle += 360f
        // 从起点（Canvas 160°）沿逆时针方向的距离；可点区是 0..140°，缺口按最近端点吸附。
        val distance = ((360f - START_DEGREE - angle) % 360f + 360f) % 360f
        val swept = when {
            distance <= SWEEP_DEGREE -> distance
            distance - SWEEP_DEGREE < 360f - distance -> SWEEP_DEGREE
            else -> 0f
        }
        fraction = (swept / SWEEP_DEGREE).coerceIn(0f, 1f)
        invalidate()
        onSeek?.invoke(fraction, finished)
    }
}

/** 设计稿的进度弧几何（数学角，度）：从 200° 逆时针扫到 340°。 */
private const val START_DEGREE = 200f
private const val SWEEP_DEGREE = 140f

/**
 * 48 条热度刻度的高度系数，与设计稿 312 行同一个公式（四段高斯峰）。
 * 设计稿用它当固定波形，这里先照抄保持画面一致，将来接真实热度数据只换这一处。
 */
private val HEAT = FloatArray(48) { index ->
    val t = index / 47.0
    fun peak(center: Double, width: Double, amplitude: Double) =
        amplitude * exp(-((t - center) * (t - center)) / width)
    (0.12 + peak(0.16, 0.004, 0.9) + peak(0.44, 0.006, 0.55) +
        peak(0.63, 0.003, 1.0) + peak(0.86, 0.01, 0.3)).toFloat()
}
