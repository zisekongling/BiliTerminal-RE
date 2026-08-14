package com.RobinNotBad.BiliClient.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSeekBar
import kotlin.math.min
import kotlin.math.pow

class HighEnergyProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatSeekBar(context, attrs, defStyleAttr) {

    private var highEnergyData: FloatArray? = null
    private var linePaint: Paint
    private var fillPaint: Paint
    private var stepSec = 10
    private var showHighEnergy = true

    init {
        linePaint = Paint()
        linePaint.isAntiAlias = true
        linePaint.color = 0xA8FB7299.toInt()
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = 2f * context.resources.displayMetrics.density
        linePaint.strokeCap = Paint.Cap.ROUND
        linePaint.strokeJoin = Paint.Join.ROUND

        fillPaint = Paint()
        fillPaint.isAntiAlias = true
        fillPaint.color = 0x33FB7299
        fillPaint.style = Paint.Style.FILL
    }

    fun setHighEnergyData(data: FloatArray?, stepSec: Int) {
        this.highEnergyData = data
        this.stepSec = stepSec
        invalidate()
    }

    fun setShowHighEnergy(show: Boolean) {
        this.showHighEnergy = show
        invalidate()
    }

    fun clearHighEnergyData() {
        this.highEnergyData = null
        invalidate()
    }

    @Synchronized
    override fun onDraw(canvas: Canvas) {
        if (showHighEnergy && highEnergyData != null && highEnergyData!!.isNotEmpty()) {
            drawHighEnergy(canvas)
        }

        super.onDraw(canvas)
    }

    private fun drawHighEnergy(canvas: Canvas) {
        val data = highEnergyData ?: return
        val width = width - paddingLeft - paddingRight
        val height = height - paddingTop - paddingBottom
        val max = max

        if (max <= 0 || width <= 0 || height <= 0) {
            return
        }

        var maxValue = 0f
        for (value in data) {
            if (value > maxValue) {
                maxValue = value
            }
        }

        if (maxValue <= 0) {
            return
        }

        val startX = paddingLeft.toFloat()
        val baselineY = (paddingTop + height).toFloat()
        val maxWaveHeight = height * 0.8f

        val linePath = Path()
        val fillPath = Path()
        var pathStarted = false

        for (i in data.indices) {
            val time = i * stepSec * 1000
            if (time > max)
                break

            val x = startX + time.toFloat() / max * width

            var density = data[i] / maxValue
            density = density.toDouble().pow(0.7).toFloat()
            val y = baselineY - maxWaveHeight * density

            if (!pathStarted) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, baselineY)
                fillPath.lineTo(x, y)
                pathStarted = true
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        if (pathStarted) {
            val lastIndex = min(data.size - 1, (max / (stepSec * 1000f)).toInt())
            val lastX = startX + min(lastIndex * stepSec * 1000, max).toFloat() / max * width
            fillPath.lineTo(lastX, baselineY)
            fillPath.close()

            canvas.drawPath(fillPath, fillPaint)
            canvas.drawPath(linePath, linePaint)
        }
    }
}