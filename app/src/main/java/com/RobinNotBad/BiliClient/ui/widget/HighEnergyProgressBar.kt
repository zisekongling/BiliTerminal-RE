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

    /**
     * 进度条上的一段标记：`[startMs, endMs]` 用 [color] 画一条带（26.11.14）。
     *
     * 用途是「把会被自动跳过的片段标出来」（SponsorBlock 广告片段）：观众能一眼看出
     * 这一 P 哪几段会被跳、跳多长，而不是进度条莫名其妙往前窜。区间单位是**毫秒**，
     * 与 [AppCompatSeekBar.getMax] 同一口径（播放页把 max 设成视频总时长毫秒）。
     */
    data class Mark(val startMs: Int, val endMs: Int, @ColorInt val color: Int)

    /**
     * 进度条上的一个**点**标记（26.11.14）：精彩时刻这类「跳转点」只有时刻、没有区间。
     *
     * 与 [Mark] 分开是因为画法不同：区间是一条带（看得出"要跳多长"），点是一个小三角
     * （只表示"这里值得看"）。单位同样是与 [AppCompatSeekBar.getMax] 对齐的毫秒。
     */
    data class PointMark(val posMs: Int, @ColorInt val color: Int)

    private var highEnergyData: FloatArray? = null
    private var linePaint: Paint
    private var fillPaint: Paint
    private var stepSec = 10
    private var showHighEnergy = true

    /** 片段标记（可为空）。改动只走 [setMarks] / [clearMarks]，两者都会 invalidate。 */
    private var marks: List<Mark> = emptyList()
    private val markPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

    /** 点标记（可为空）。改动只走 [setPointMarks] / [clearPointMarks]，两者都会 invalidate。 */
    private var pointMarks: List<PointMark> = emptyList()
    private val pointMarkPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

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

    /**
     * 设置进度条上的片段标记（会被自动跳过的广告片段等）。传空表等于 [clearMarks]。
     *
     * 标记画在**最上层**（super.onDraw 之后）：进度条的底轨是 drawable、进度填充也在 super 里画，
     * 画在之前会被整条盖住。代价是标记的半透明底会把经过的滑块稍微蒙一层——上游插件也是这么画，
     * 而且底部还有一条不透明的实心条，位置始终看得清。
     */
    fun setMarks(newMarks: List<Mark>?) {
        marks = newMarks ?: emptyList()
        invalidate()
    }

    fun clearMarks() = setMarks(null)

    /**
     * 设置进度条上的点标记（精彩时刻这类跳转点）。传空表等于 [clearPointMarks]。
     *
     * 与 [setMarks] 互不影响：同一 P 既可能有"会跳的广告区间"，也可能有"值得看的精彩点"。
     */
    fun setPointMarks(newMarks: List<PointMark>?) {
        pointMarks = newMarks ?: emptyList()
        invalidate()
    }

    fun clearPointMarks() = setPointMarks(null)

    @Synchronized
    override fun onDraw(canvas: Canvas) {
        if (showHighEnergy && highEnergyData != null && highEnergyData!!.isNotEmpty()) {
            drawHighEnergy(canvas)
        }

        super.onDraw(canvas)

        if (marks.isNotEmpty()) {
            drawMarks(canvas)
        }
        if (pointMarks.isNotEmpty()) {
            drawPointMarks(canvas)
        }
    }

    /**
     * 画片段标记：一段半透明铺底 + 一条贴底的实心条。
     *
     * 只铺底不贴条时，浅色主题下与底轨几乎分不开；只贴条又太细看不清。两者叠加在 20dp 高的
     * 进度条上都还留得住高能波形（见类注释里的尺寸约束）。
     *
     * 起点/终点都按 [getMax] 归一化并钳到 `[0, max]`：上游偶尔给出超过总长的秒数（换源前的旧标注），
     * 不钳会画到控件外面去。
     */
    private fun drawMarks(canvas: Canvas) {
        val total = max
        if (total <= 0) return

        val usableWidth = width - paddingLeft - paddingRight
        val usableHeight = height - paddingTop - paddingBottom
        if (usableWidth <= 0 || usableHeight <= 0) return

        val startX = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        val bottom = top + usableHeight
        val density = resources.displayMetrics.density
        val minWidth = 2f * density
        val stripHeight = 3f * density
        val radius = stripHeight / 2f

        for (mark in marks) {
            val fromMs = mark.startMs.coerceIn(0, total)
            val toMs = mark.endMs.coerceIn(0, total)
            if (toMs <= fromMs) continue

            val x1 = startX + fromMs.toFloat() / total * usableWidth
            val rawRight = startX + toMs.toFloat() / total * usableWidth
            // 极短的片段（1 秒的片头在 1 小时的视频里不到 1 像素）至少给一条看得见的宽度
            val x2 = if (rawRight - x1 < minWidth) x1 + minWidth else rawRight

            markPaint.color = withAlpha(mark.color, 0x26)
            canvas.drawRect(x1, top, x2, bottom, markPaint)

            markPaint.color = withAlpha(mark.color, 0xE6)
            canvas.drawRoundRect(x1, bottom - stripHeight, x2, bottom, radius, radius, markPaint)
        }
    }

    /**
     * 画点标记：贴底一个小三角，尖朝下。
     *
     * 用三角而不是竖线：进度条只有 20dp 高，竖线在浅色主题下与底轨混在一起；三角有方向感，
     * 而且压在进度填充上也认得出。左右各留半个三角宽，避免首个/末个点被裁掉一半。
     */
    private fun drawPointMarks(canvas: Canvas) {
        val total = max
        if (total <= 0) return

        val usableWidth = width - paddingLeft - paddingRight
        val usableHeight = height - paddingTop - paddingBottom
        if (usableWidth <= 0 || usableHeight <= 0) return

        val density = resources.displayMetrics.density
        val halfWidth = 4f * density
        val tipHeight = 5f * density
        val startX = paddingLeft.toFloat()
        val bottom = (paddingTop + usableHeight).toFloat()
        val path = Path()

        for (point in pointMarks) {
            val posMs = point.posMs.coerceIn(0, total)
            val centerX = (startX + posMs.toFloat() / total * usableWidth)
                .coerceIn(startX + halfWidth, startX + usableWidth - halfWidth)

            path.reset()
            path.moveTo(centerX, bottom)
            path.lineTo(centerX - halfWidth, bottom - tipHeight)
            path.lineTo(centerX + halfWidth, bottom - tipHeight)
            path.close()

            pointMarkPaint.color = point.color
            canvas.drawPath(path, pointMarkPaint)
        }
    }

    /** 保留颜色 RGB、只换透明度（0~0xFF）。 */
    private fun withAlpha(color: Int, alpha: Int): Int = (alpha shl 24) or (color and 0x00FFFFFF)

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