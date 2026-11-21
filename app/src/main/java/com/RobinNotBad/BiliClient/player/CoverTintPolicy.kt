package com.RobinNotBad.BiliClient.player

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 封面取色（G06，26.11.13）。
 *
 * 目标：拿视频封面里「最有代表性的一个颜色」，把播放器的高能曲线 / 进度条染成它——
 * 兄弟分支 PianoEthan 用 material-color-utilities 的 HCT + Celebi 量化做了同一件事
 * （`theme/ContentTintHelper.java`），收益全在观感上，不值得为此引入一个新库
 * （`material-color-utilities` 会带进一大坨 Java 工具类，且要求 API 24+），
 * 这里用「12 色相 × 4 明度 × 4 饱和度 的加权投票」实现同一意图：
 *
 * 1. 跳过透明像素（视频封面几乎都是不透明，但圆角/占位图会透明）；
 * 2. 跳过接近纯白 / 纯黑 / 无彩的像素——它们数量最多但**没有色相信息**，
 *    不跳过的话「一张大面积留白的封面」会取出一块灰；
 * 3. 权重取 `饱和度 × 明度`：同样面积下，鲜艳的中等亮度区块胜过浅色和深色区块；
 * 4. 取票数最高的桶里所有像素的**平均值**（同色系的深浅变化平均后更接近观感色）；
 * 5. 最后按深色 UI 调一遍（[tuneForDarkUi]）：提饱和度保底、把明度夹在中间区间，
 *    否则封面里的深色/灰白色在黑色播放器背景上几乎看不出来。
 *
 * 全是纯函数、不碰 Android 类（HSV 换算手写），所以能直接进 JVM 单测——
 * 这也是不引 `android.graphics.Color.colorToHSV` 的原因。
 */
object CoverTintPolicy {

    /** 候选像素的最低明度：更低的基本是黑边 / 字幕条。 */
    private const val MIN_VALUE = 0.15f

    /** 候选像素的最高明度：更高的基本是白边 / 纯背景。 */
    private const val MAX_VALUE = 0.97f

    /** 候选像素的最低饱和度：更低的没有可用色相。 */
    private const val MIN_SATURATION = 0.15f

    /** 调色后允许的最低明度（深色背景上要亮得起来）。 */
    private const val UI_MIN_VALUE = 0.55f

    /** 调色后允许的最高明度（避免整条进度条发白刺眼）。 */
    private const val UI_MAX_VALUE = 0.95f

    /** 调色后允许的最低饱和度（避免取出一块灰）。 */
    private const val UI_MIN_SATURATION = 0.35f

    /** 调色后允许的最高饱和度（避免荧光色）。 */
    private const val UI_MAX_SATURATION = 0.90f

    /** 色相分桶数（每桶 30°）。 */
    private const val HUE_BUCKETS = 12

    /** 明度 / 饱和度各自的分桶数。 */
    private const val LEVEL_BUCKETS = 4

    /**
     * 从像素里取一个代表色。
     *
     * @param pixels ARGB 像素（`Bitmap.getPixels` 的输出）
     * @return 已按深色 UI 调过的颜色；没有可用像素时返回 null
     */
    fun fromPixels(pixels: IntArray?): Int? {
        if (pixels == null || pixels.isEmpty()) return null

        val bucketCount = HUE_BUCKETS * LEVEL_BUCKETS * LEVEL_BUCKETS
        val weights = DoubleArray(bucketCount)
        val reds = DoubleArray(bucketCount)
        val greens = DoubleArray(bucketCount)
        val blues = DoubleArray(bucketCount)

        for (pixel in pixels) {
            val alpha = pixel ushr 24 and 0xFF
            if (alpha < 0xC0) continue
            val r = pixel ushr 16 and 0xFF
            val g = pixel ushr 8 and 0xFF
            val b = pixel and 0xFF

            val hsv = rgbToHsv(r, g, b)
            val h = hsv[0]
            val s = hsv[1]
            val v = hsv[2]
            if (v < MIN_VALUE || v > MAX_VALUE) continue
            if (s < MIN_SATURATION) continue

            val hueBucket = (h / (360f / HUE_BUCKETS)).toInt().coerceIn(0, HUE_BUCKETS - 1)
            val valueBucket = (v * LEVEL_BUCKETS).toInt().coerceIn(0, LEVEL_BUCKETS - 1)
            val saturationBucket = (s * LEVEL_BUCKETS).toInt().coerceIn(0, LEVEL_BUCKETS - 1)
            val index = (hueBucket * LEVEL_BUCKETS + valueBucket) * LEVEL_BUCKETS + saturationBucket

            val weight = (s * v).toDouble()
            weights[index] += weight
            reds[index] += r * weight
            greens[index] += g * weight
            blues[index] += b * weight
        }

        var best = -1
        var bestWeight = 0.0
        for (i in weights.indices) {
            if (weights[i] > bestWeight) {
                bestWeight = weights[i]
                best = i
            }
        }
        if (best < 0 || bestWeight <= 0.0) return null

        val r = (reds[best] / bestWeight).toInt().coerceIn(0, 255)
        val g = (greens[best] / bestWeight).toInt().coerceIn(0, 255)
        val b = (blues[best] / bestWeight).toInt().coerceIn(0, 255)
        return tuneForDarkUi(0xFF shl 24 or (r shl 16) or (g shl 8) or b)
    }

    /**
     * 把一个颜色调成适合深色播放器 UI 的强调色（只保证亮度和饱和度区间，不改色相）。
     */
    fun tuneForDarkUi(color: Int): Int {
        val r = color ushr 16 and 0xFF
        val g = color ushr 8 and 0xFF
        val b = color and 0xFF

        val hsv = rgbToHsv(r, g, b)
        val h = hsv[0]
        val s = hsv[1].coerceIn(UI_MIN_SATURATION, UI_MAX_SATURATION)
        val v = hsv[2].coerceIn(UI_MIN_VALUE, UI_MAX_VALUE)
        return hsvToRgb(h, s, v)
    }

    /**
     * RGB（0..255）转 HSV：h 为 0..360，s / v 为 0..1。
     *
     * 与 `android.graphics.Color.RGBToHSV` 同算法；手写是为了让本文件保持纯 Kotlin。
     */
    internal fun rgbToHsv(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val maxValue = max(rf, max(gf, bf))
        val minValue = min(rf, min(gf, bf))
        val delta = maxValue - minValue

        val hue = when {
            delta < 1e-6f -> 0f
            maxValue == rf -> 60f * (((gf - bf) / delta) % 6f)
            maxValue == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }

        val saturation = if (maxValue <= 1e-6f) 0f else delta / maxValue
        return floatArrayOf(hue, saturation, maxValue)
    }

    /**
     * HSV 转 RGB（带 alpha 0xFF）。
     */
    internal fun hsvToRgb(hue: Float, saturation: Float, value: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)

        val chroma = v * s
        val x = chroma * (1f - abs((h / 60f) % 2f - 1f))
        val m = v - chroma

        val (r1, g1, b1) = when {
            h < 60f -> Triple(chroma, x, 0f)
            h < 120f -> Triple(x, chroma, 0f)
            h < 180f -> Triple(0f, chroma, x)
            h < 240f -> Triple(0f, x, chroma)
            h < 300f -> Triple(x, 0f, chroma)
            else -> Triple(chroma, 0f, x)
        }

        val r = ((r1 + m) * 255f).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).toInt().coerceIn(0, 255)
        return 0xFF shl 24 or (r shl 16) or (g shl 8) or b
    }
}
