package com.RobinNotBad.BiliClient.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.RobinNotBad.BiliClient.ui.widget.HighEnergyProgressBar
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
    /** 进度最大值（毫秒），片段标记按它换算到弧上的角度；由 [setProgress] 更新。 */
    private var max = 0
    private val path = Path()

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

    /**
     * SponsorBlock 片段标记与精彩时刻三角。
     *
     * 圆屏下 [HighEnergyProgressBar] 是 GONE 的，旧实现只把标记设给它，于是圆屏用户
     * **一条条子、一个三角都看不到**。这里按弧上的角度重新画一遍：条子贴在弧外侧，
     * 三角挂在弧内——与方屏「贴底」的位置语义一致（一个说「别看这里」，一个说「这里值得看」）。
     */
    private var segments: List<HighEnergyProgressBar.Mark> = emptyList()
    private var jumpPoints: List<HighEnergyProgressBar.PointMark> = emptyList()

    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun setMarks(marks: List<HighEnergyProgressBar.Mark>?) {
        segments = marks.orEmpty()
        invalidate()
    }

    fun setPointMarks(marks: List<HighEnergyProgressBar.PointMark>?) {
        jumpPoints = marks.orEmpty()
        invalidate()
    }

    fun setProgress(value: Int, max: Int) {
        this.max = max.coerceAtLeast(0)
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
        // 片段条与三角画在最上层：它们要压在轨道上，否则会被进度色盖住。
        drawSegmentMarks(canvas, radius)
        drawJumpPoints(canvas, radius)
    }

    /**
     * 弧内侧的热度刻度：外端固定 r=103，向内最多缩 5.5dp。
     *
     * **没有真实高能数据就不画**。此前这里退回设计稿的示意波形 [HEAT]，
     * 造成「方屏关着高能条什么都没有、圆屏却永远有一条形状一样的假曲线」——
     * 两个屏形对同一条视频表现相反，而且那条曲线对用户没有任何信息量。
     * 高度同样不做峰值归一化：真实数据是 0..1 的相对量，整体平的地方就该看起来平。
     */
    private fun drawHeat(canvas: Canvas) {
        val data = heatData ?: return
        if (data.isEmpty()) return
        heat.strokeWidth = 1.6f * unit
        val outer = 103f * unit
        val last = data.size - 1
        for (index in data.indices) {
            val degree = START_DEGREE + SWEEP_DEGREE * index / last.coerceAtLeast(1)
            val radians = Math.toRadians(degree.toDouble())
            val length = data[index].coerceIn(0f, 1f) * 5.5f * unit
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

    /**
     * SponsorBlock 片段条：沿弧画一段加粗的弧线，贴在轨道**外侧**（方屏是贴轨道下沿）。
     *
     * 色值与透明度沿用旧进度条：[mark.color] 的 0xE6 不透明度直接画。
     * 极短片段（1 秒的片头在 1 小时的视频里不到 1°）至少给 1.5°，否则肉眼看不见。
     */
    private fun drawSegmentMarks(canvas: Canvas, radius: Float) {
        if (segments.isEmpty() || max <= 0) return
        val total = max.toFloat()
        val stroke = 3f * unit
        markPaint.style = Paint.Style.STROKE
        markPaint.strokeWidth = stroke
        markPaint.strokeCap = Paint.Cap.BUTT
        // 条子贴在弧外侧一点点，避免和 3dp 轨道糊成一条。
        val markRadius = radius + (stroke + 1.5f * unit)
        val minSweep = 1.5f
        for (mark in segments) {
            val from = mark.startMs.coerceIn(0, max).toFloat() / total
            val to = mark.endMs.coerceIn(0, max).toFloat() / total
            if (to <= from) continue
            val fromDegree = START_DEGREE + SWEEP_DEGREE * from
            val sweep = (SWEEP_DEGREE * (to - from)).coerceAtLeast(minSweep)
            markPaint.color = mark.color
            // Canvas 角 = 360 - 数学角，且沿屏幕下方逆时针走，所以扫角取负。
            canvas.drawArc(
                centerX - markRadius, centerY - markRadius,
                centerX + markRadius, centerY + markRadius,
                360f - fromDegree, -sweep, false, markPaint
            )
        }
    }

    /** 精彩时刻：弧**内侧**的小三角，尖端朝圆心（方屏是尖端朝下）。 */
    private fun drawJumpPoints(canvas: Canvas, radius: Float) {
        if (jumpPoints.isEmpty() || max <= 0) return
        val total = max.toFloat()
        markPaint.style = Paint.Style.FILL
        val inner = radius - 1.5f * unit
        val half = 3.5f * unit
        val tip = 4.5f * unit
        for (point in jumpPoints) {
            val fraction = point.posMs.coerceIn(0, max).toFloat() / total
            val degree = START_DEGREE + SWEEP_DEGREE * fraction
            val radians = Math.toRadians(degree.toDouble())
            val cos = cos(radians).toFloat()
            val sin = sin(radians).toFloat()
            // 以弧上一点为基准，沿「径向」往里推 tip 得到尖端，两侧沿切向各展开 half。
            val baseX = centerX + inner * cos
            val baseY = centerY - inner * sin
            val tipX = baseX - tip * cos
            val tipY = baseY + tip * sin
            // 切向量：把径向旋转 90°。
            val tangentX = -sin
            val tangentY = -cos
            path.reset()
            path.moveTo(tipX, tipY)
            path.lineTo(baseX + half * tangentX, baseY + half * tangentY)
            path.lineTo(baseX - half * tangentX, baseY - half * tangentY)
            path.close()
            markPaint.color = point.color
            canvas.drawPath(path, markPaint)
        }
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
