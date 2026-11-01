package com.RobinNotBad.BiliClient.activity.video

/**
 * 短视频画面的尺寸计算（纯逻辑，无 Android 依赖，有 JVM 单测）。
 *
 * 抽成纯函数的原因和 `ui/appearance/RoundListEffectMath` 一样：这类「算出来多大」的
 * 缺陷在模拟器上经常复现不出（默认 B 站合一 MP4 的 moov 带分辨率，`videoWidth` 不会为 0），
 * 只有把边界条件钉在单测里，才不至于下次改播放器又被静默带回去。
 *
 * 调用方（`ShortVideoPlayerActivity.PageHolder.adjustVideoSize`）只负责：
 * 取可用区域 → 调这里算宽高 → 写 `TextureView.layoutParams` 并 `gravity = CENTER`。
 */
object ShortVideoLayoutMath {

    /** 拿不到视频尺寸时的退化比例：最常见的横向视频。 */
    const val DEFAULT_RATIO = 16f / 9f

    /**
     * 旋转 90/270 时容器给的宽高与画面实际宽高是反的。
     *
     * ijk 的 `videoWidth/videoHeight` 来自容器 `codecpar`（未旋转），而开了
     * `mediacodec-auto-rotate` 之后解码器输出的是旋转后的帧，所以消费方要交换。
     */
    fun swapsWidthHeight(rotationDegrees: Int): Boolean =
        rotationDegrees == 90 || rotationDegrees == 270

    /** 把比例规整成合法值（非正数 / 非有限数一律按 [DEFAULT_RATIO]）。 */
    fun sanitizeRatio(ratio: Float): Float =
        if (ratio > 0f && ratio.isFinite()) ratio else DEFAULT_RATIO

    /**
     * 非圆屏：按视频比例等比 fit 进可用区域（留黑边，不裁切、不拉伸）。
     *
     * @return `intArrayOf(width, height)`；区域非法时返回 null
     */
    fun fit(ratio: Float, areaWidth: Int, areaHeight: Int): IntArray? {
        if (areaWidth <= 0 || areaHeight <= 0) return null
        val safeRatio = sanitizeRatio(ratio)
        val areaRatio = areaWidth.toFloat() / areaHeight.toFloat()
        return if (safeRatio > areaRatio) {
            intArrayOf(areaWidth, (areaWidth / safeRatio).toInt())
        } else {
            intArrayOf((areaHeight * safeRatio).toInt(), areaHeight)
        }.takeIf { it[0] > 0 && it[1] > 0 }
    }

    /**
     * 圆屏：取视频在圆形屏幕里的内接矩形（公式与 `PlayerActivity.changeVideoSize` 一致），
     * 再等比夹进可用区域，避免极端长宽比下内接矩形超出内容区。
     *
     * @param diameter 圆屏直径（调用方传屏幕短边）
     * @return `intArrayOf(width, height)`；参数非法时返回 null
     */
    fun fitRound(ratio: Float, diameter: Int, areaWidth: Int, areaHeight: Int): IntArray? {
        if (diameter <= 0 || areaWidth <= 0 || areaHeight <= 0) return null
        val safeRatio = sanitizeRatio(ratio)
        val heightPerWidth = 1.0 / safeRatio.toDouble()
        val side = Math.sqrt(diameter.toDouble() * diameter / (heightPerWidth * heightPerWidth + 1))
        var width = (side + 0.5).toInt()
        var height = (side * heightPerWidth + 0.5).toInt()
        if (width > areaWidth) {
            height = (height.toLong() * areaWidth / width).toInt()
            width = areaWidth
        }
        if (height > areaHeight) {
            width = (width.toLong() * areaHeight / height).toInt()
            height = areaHeight
        }
        return intArrayOf(width, height).takeIf { it[0] > 0 && it[1] > 0 }
    }
}
