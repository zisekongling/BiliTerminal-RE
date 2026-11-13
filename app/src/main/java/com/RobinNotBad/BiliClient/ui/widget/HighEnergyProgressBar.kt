package com.RobinNotBad.BiliClient.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.content.res.ResourcesCompat
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
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
        // 高能进度条此前写死旧版 B站粉（#FB7299），切主题不跟着变；
        // 这里取当前主题主色，并保持原来的不透明度（线条 A8=66%、填充 33=20%）
        val accent = ColorScheme.PRIMARY
        val accentRgb = accent and 0x00FFFFFF

        linePaint = Paint()
        linePaint.isAntiAlias = true
        linePaint.color = 0xA8000000.toInt() or accentRgb
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = 2f * context.resources.displayMetrics.density
        linePaint.strokeCap = Paint.Cap.ROUND
        linePaint.strokeJoin = Paint.Join.ROUND

        fillPaint = Paint()
        fillPaint.isAntiAlias = true
        fillPaint.color = 0x33000000 or accentRgb
        fillPaint.style = Paint.Style.FILL

        // 滑块：原来写在布局的 android:thumb="@drawable/player_selector_progress" 里，而 ProgressBar
        // 构造函数是用 Resources.getDrawable 原生加载 thumb 的，那个 selector 里指向矢量，API<21
        // 解析不了 <vector> 会直接崩（Resources$NotFoundException: invalid drawable tag vector）。
        // 所以布局不再声明 thumb，改在这里按 SDK 取：>=21 直接加载 selector；<21 手搭
        // StateListDrawable——AppCompatResources 能解矢量，但解不了「含矢量的 selector」。
        thumb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ResourcesCompat.getDrawable(
                context.resources, R.drawable.player_selector_progress, context.theme
            )
        } else StateListDrawable().apply {
            val pressed = AppCompatResources.getDrawable(context, R.drawable.player_tv_white)
            val normal = AppCompatResources.getDrawable(context, R.drawable.player_tv_blue)
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(android.R.attr.state_focused), pressed)
            addState(intArrayOf(), normal)
        }
    }

    fun setHighEnergyData(data: FloatArray?, stepSec: Int) {
        this.highEnergyData = data
        this.stepSec = stepSec
        invalidate()
    }

    /**
     * 换强调色（26.11.13：封面取色）。
     *
     * 只改高能曲线 / 填充的色调，保留原来写死的不透明度（线条 A8=66%、填充 33=20%）——
     * 波形叠在进度条上，透明度一变就会盖住底下的进度与文字。
     */
    fun setAccentColor(@ColorInt color: Int) {
        val accentRgb = color and 0x00FFFFFF
        linePaint.color = 0xA8000000.toInt() or accentRgb
        fillPaint.color = 0x33000000 or accentRgb
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