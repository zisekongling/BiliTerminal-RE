package com.RobinNotBad.BiliClient.player

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.RobinNotBad.BiliClient.ui.widget.HighEnergyProgressBar
import kotlin.math.max

/**
 * 新版播放器方屏进度行的绘制（对应设计稿 design/player-redesign-v2.html 的 `sProg`）。
 *
 * 设计稿口径（dp，本类以控件左上角为原点换算）：
 * - 控件顶在 y=208、轨道中线 y=222，所以本类把「波形基线」定在 8dp、「轨道中线」定在 14dp；
 * - 弹幕热度波形：48 段折线，白色 26% 填充，最高 7dp，画在轨道**上方**；
 * - 轨道：3dp 圆角、白 22%；缓冲白 38%；已播 `#FF6699`；
 * - 滑块：4dp 白心 + 2dp `#FF6699` 描边（中心正好落在已播段末端）。
 *
 * 真实高能数据是按 [HighEnergyProgressBar.highEnergyStepSeconds] 切片的一串浮点数，
 * 设计稿那 48 个点是示意值；这里把真实数据点等距铺满轨道宽度，观感与设计稿同构。
 * 缓冲段优先用播放器给的真实值（拖动/加载时才有意义），没有真实值时退回设计稿的
 * `max(.55, p+.08)`，避免进度条大片空白。
 */
class PlayerNewProgressRenderer(private val bar: HighEnergyProgressBar) {

    private val density = bar.resources.displayMetrics.density

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x42ffffff
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x38ffffff
    }
    private val bufferPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x61ffffff
    }
    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xffff6699.toInt()
    }
    private val thumbFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xffffffff.toInt()
    }
    private val thumbStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xffff6699.toInt()
    }
    private val path = Path()
    private val rect = RectF()

    /** 诊断用：前几次绘制打一条现场日志，确认控件尺寸与进度真的进来了。 */
    private var drawCount = 0

    fun draw(canvas: Canvas) {
        val width = bar.width.toFloat()
        if (drawCount < 3) {
            drawCount++
            com.RobinNotBad.BiliClient.util.Logu.d(
                "进度行绘制>w=" + width + " h=" + bar.height + " max=" + bar.max +
                    " p=" + bar.progress + " sp=" + bar.secondaryProgress +
                    " vis=" + bar.visibility + " wave=" + (bar.highEnergyWave?.size ?: -1)
            )
        }
        if (width <= 0f) return
        val fraction = if (bar.max <= 0) 0f else (bar.progress.toFloat() / bar.max).coerceIn(0f, 1f)
        // 拖动时设计稿把轨道加粗到 5dp、滑块放大到 6dp。
        val dragging = bar.isPressed || bar.isFocused
        val barHeight = (if (dragging) 5f else 3f) * density

        drawWave(canvas, width)

        val centerY = 14f * density
        val half = barHeight / 2f
        // 圆角半径取条高的一半 —— 设计稿的 bar 是 height=w、border-radius=w。
        val radius = barHeight

        rect.set(0f, centerY - half, width, centerY + half)
        canvas.drawRoundRect(rect, radius, radius, trackPaint)

        val buffered = bufferedFraction(fraction)
        if (buffered > 0f) {
            rect.set(0f, centerY - half, width * buffered, centerY + half)
            canvas.drawRoundRect(rect, radius, radius, bufferPaint)
        }
        if (fraction > 0f) {
            rect.set(0f, centerY - half, width * fraction, centerY + half)
            canvas.drawRoundRect(rect, radius, radius, playedPaint)
        }

        val thumbRadius = (if (dragging) 6f else 4f) * density
        val x = width * fraction
        canvas.drawCircle(x, centerY, thumbRadius, thumbFill)
        thumbStroke.strokeWidth = 2f * density
        canvas.drawCircle(x, centerY, thumbRadius, thumbStroke)
    }

    /** 缓冲段：真实 secondaryProgress 比已播更靠前时才用它，否则照设计稿的 `max(.55, p+.08)`。 */
    private fun bufferedFraction(fraction: Float): Float {
        val real = if (bar.max <= 0) 0f else bar.secondaryProgress.toFloat() / bar.max
        if (real > fraction) return real.coerceAtMost(1f)
        return max(0.55f, fraction + 0.08f).coerceAtMost(1f)
    }

    /**
     * 弹幕热度波形：等距铺满轨道宽度，0 → 基线，1 → 基线以上 7dp。
     *
     * 数据点很多时逐点画（折线自然变成密排的柱状），数据点很少时也不会拉成锯齿——
     * 起止点都对齐到轨道两端，与设计稿 `M0 8 L… L${W} 8Z` 的形状同构。
     */
    private fun drawWave(canvas: Canvas, width: Float) {
        val data = bar.highEnergyWave
        if (data == null || data.isEmpty()) return
        var peak = 0f
        for (value in data) if (value > peak) peak = value
        if (peak <= 0f) return

        val baseline = 8f * density
        val height = 7f * density
        val lastIndex = data.size - 1
        path.reset()
        path.moveTo(0f, baseline)
        for (index in data.indices) {
            val x = if (lastIndex == 0) 0f else width * index / lastIndex
            val value = (data[index] / peak).coerceIn(0f, 1f)
            path.lineTo(x, baseline - height * value)
        }
        path.lineTo(width, baseline)
        path.close()
        canvas.drawPath(path, wavePaint)
    }
}
