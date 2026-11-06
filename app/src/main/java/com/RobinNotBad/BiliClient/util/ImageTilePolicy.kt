package com.RobinNotBad.BiliClient.util

import kotlin.math.ceil
import kotlin.math.floor

/**
 * 大图分块解码的**纯策略**（不碰 Android API，全部可 JVM 单测，见 `ImageTilePolicyTest`）。
 *
 * <h3>为什么要分块</h3>
 * 看图页（[com.RobinNotBad.BiliClient.activity.ImageViewerActivity]）原来用 PhotoView + Glide
 * 整图解码：一张 1024×20000 的长图按 ARGB_8888 全解就是 82MB，手表（heap 通常 128～256MB）
 * 必然 OOM——而且 Glide 的异步 OOM 不会落到调用方的 `catch (OutOfMemoryError)` 里，表现是
 * 「图片空白/闪退」而不是可读的提示。分块解码只把**当前可见区域**按块解成小位图，内存上限由
 * 「块数 × 块大小」决定，与图片总尺寸无关。
 *
 * <h3>坐标约定（本文件所有函数统一）</h3>
 * - 长度单位都是**图片源像素**（source px），与位图原始分辨率一一对应；
 * - `displayScale` 是「1 个源像素在屏幕上占多少像素」，等于 `fitScale × 用户放大倍率`；
 *   屏幕坐标 → 源坐标：`source = (view - offset) / displayScale`；
 * - `offsetX/offsetY` 是图片左上角在 View 坐标系里的位置（px，可以为负）；
 * - 矩形一律「左闭右开」，即 `right/bottom` 是不包含的边界。
 *
 * <h3>块大小与采样率的关系（关键）</h3>
 * 块位图的边长恒为 [DEFAULT_TILE_BITMAP_PX]（256px），**不随图片变大而变大**；
 * 放大后要更清晰，缩小的不是块位图，而是「一块覆盖多少源像素」——即
 * `块源像素 = tileBitmapPx × sampleSize`。采样率 [sampleSizeFor] 取
 * 「1/displayScale」向下取整到 2 的幂，保证解码出的像素数 ≥ 屏幕像素数（不糊），
 * 而块位图大小恒定 ≤ 256×256×4B = 256KB。
 *
 * 于是长图缩放时的块数也是常数级：一块源像素 `tileSourcePx` 在屏幕上占
 * `tileSourcePx × displayScale ≈ tileBitmapPx`（256px）——手表一屏 400px 上下，
 * 可见区最多 2×2 块，加上预取也不过十来个块。
 */
object ImageTilePolicy {

    /** 块位图的边长（px）。内存上限 = 该值² × 4B × 缓存块数。 */
    const val DEFAULT_TILE_BITMAP_PX: Int = 256

    /** 可见区四周各预取的比例（相对可见区的宽/高）。0.5 = 左右上下各多解半屏。 */
    const val DEFAULT_PREFETCH_FRACTION: Float = 0.5f

    /** 采样率下限：1 = 原分辨率。 */
    const val MIN_SAMPLE_SIZE: Int = 1

    /** 采样率上限：2^5。再降级块位图就只剩 8px，没有意义。 */
    const val MAX_SAMPLE_SIZE: Int = 32

    /** 单边超过该值就走分块：[LARGE_SIDE_PX]² 的 ARGB_8888 已是 64MB。 */
    const val LARGE_SIDE_PX: Int = 4096

    /**
     * 面积超过该值就走分块（16M px ≈ ARGB_8888 整图 64MB）。
     *
     * 与单边阈值是「或」的关系：1024×20000 的长图单边都没超 4096，但面积 20.5M 已经解不动。
     */
    const val LARGE_AREA_PX: Long = 16_000_000L

    /** 一块矩形，坐标是图片源像素，左闭右开。 */
    data class TileRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {

        val width: Int get() = right - left

        val height: Int get() = bottom - top

        val isEmpty: Boolean get() = width <= 0 || height <= 0

        /** 裁到 `[0, imageWidth) × [0, imageHeight)` 内；越界的空矩形返回空矩形。 */
        fun clampTo(imageWidth: Int, imageHeight: Int): TileRect {
            if (imageWidth <= 0 || imageHeight <= 0) return TileRect(0, 0, 0, 0)
            val clampedLeft = left.coerceIn(0, imageWidth)
            val clampedTop = top.coerceIn(0, imageHeight)
            val clampedRight = right.coerceIn(clampedLeft, imageWidth)
            val clampedBottom = bottom.coerceIn(clampedTop, imageHeight)
            return TileRect(clampedLeft, clampedTop, clampedRight, clampedBottom)
        }

        /** 与另一矩形是否有重叠（用于「已解码的块是否覆盖可见区」的判断与绘制裁剪）。 */
        fun intersects(other: TileRect): Boolean =
            left < other.right && other.left < right && top < other.bottom && other.top < bottom

        override fun toString(): String = "($left,$top)-($right,$bottom)"
    }

    /** 尺寸是否是有效图片尺寸（`BitmapFactory` 探测失败时会给 -1 或 0）。 */
    fun isValidDimensions(width: Int, height: Int): Boolean = width > 0 && height > 0

    /**
     * 这张图是否该走分块解码：单边 > [LARGE_SIDE_PX] 或面积 > [LARGE_AREA_PX]。
     *
     * 阈值是**硬边界**：恰好等于阈值不算「超过」（4096×4096 = 16M px 仍走整图，
     * 因为此时整图解码 64MB 已经是这套判定能接受的上限）。
     */
    fun shouldTile(width: Int, height: Int): Boolean {
        if (!isValidDimensions(width, height)) return false
        if (width > LARGE_SIDE_PX || height > LARGE_SIDE_PX) return true
        return width.toLong() * height.toLong() > LARGE_AREA_PX
    }

    /**
     * 该图片格式能否用 [android.graphics.BitmapRegionDecoder] 分块解码。
     *
     * 仅 jpeg / png / webp 支持（BitmapRegionDecoder 的公开支持范围）；gif 等动图必须走整图，
     * 否则会得到一张静止的第一帧。MIME 未知（null）时按「可以试」处理——真解不了会由
     * `BigImageView` 的失败回退兜住，不会白屏。
     */
    fun isTileableMime(mimeType: String?): Boolean {
        if (mimeType.isNullOrEmpty()) return true
        return when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg", "image/png", "image/webp" -> true
            else -> false
        }
    }

    /**
     * 初始（未放大）时的显示倍率：让整张图刚好装进 View（取宽/高比例的较小者）。
     *
     * 与原先 PhotoView 路径的「整图适配」观感一致：长图（如 1024×20000）在 400×400 的
     * View 上按高适配成 0.02，先看到整张图的轮廓，双击/双指放大后再按块补清晰度——
     * 这条「先整体、再局部」的路径和整图解码时相同，只是不再为整图付内存。
     * 参数无效时返回 1f（= 不缩放），调用方据此仍能画出东西，不会除零。
     */
    fun fitScale(imageWidth: Int, imageHeight: Int, viewWidth: Int, viewHeight: Int): Float {
        if (!isValidDimensions(imageWidth, imageHeight) || viewWidth <= 0 || viewHeight <= 0) return 1f
        return minOf(viewWidth.toFloat() / imageWidth, viewHeight.toFloat() / imageHeight)
    }

    /**
     * 按显示倍率算解码采样率（2 的幂，[MIN_SAMPLE_SIZE]..[MAX_SAMPLE_SIZE]）。
     *
     * 取「1/displayScale」**向下取整**到 2 的幂：采样后解码像素数 ≥ 屏幕像素数，避免糊。
     * 例：displayScale=0.1（1 源像素占 0.1 屏像素）→ 需要 1/0.1=10 倍 → 采样 8。
     *
     * 倍率无效（NaN / ≤0）时取 [MAX_SAMPLE_SIZE]（最省内存，宁可先显示出来）；
     * 倍率无穷大（异常数据）时取 [MIN_SAMPLE_SIZE]（最清晰）。
     */
    fun sampleSizeFor(displayScale: Float): Int {
        if (displayScale.isNaN() || displayScale <= 0f) return MAX_SAMPLE_SIZE
        if (displayScale.isInfinite()) return MIN_SAMPLE_SIZE
        val required = 1f / displayScale
        if (required <= MIN_SAMPLE_SIZE.toFloat()) return MIN_SAMPLE_SIZE
        var sample = MIN_SAMPLE_SIZE
        while (sample < MAX_SAMPLE_SIZE && sample * 2 <= required) {
            sample *= 2
        }
        return sample
    }

    /** 一个块在**源图**上覆盖的边长 = 块位图边长 × 采样率。 */
    fun tileSourcePx(
        sampleSize: Int,
        tileBitmapPx: Int = DEFAULT_TILE_BITMAP_PX
    ): Int = tileBitmapPx.coerceAtLeast(1) * sampleSize.coerceAtLeast(MIN_SAMPLE_SIZE)

    /**
     * 当前屏幕可见区域对应的源图矩形（已裁到图片范围内）。
     *
     * View 坐标 → 源坐标：`source = (view - offset) / displayScale`。
     * 用 floor/ceil 各向外取整，保证「半像素」不会在边缘留下一条没被块覆盖的缝。
     * 图片滚出屏幕（矩形为空）时返回空矩形，调用方按「不需要解码」处理。
     */
    fun visibleRect(
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        displayScale: Float,
        offsetX: Float,
        offsetY: Float
    ): TileRect {
        if (!isValidDimensions(imageWidth, imageHeight) || viewWidth <= 0 || viewHeight <= 0) {
            return TileRect(0, 0, 0, 0)
        }
        if (displayScale.isNaN() || displayScale.isInfinite() || displayScale <= 0f) {
            return TileRect(0, 0, imageWidth, imageHeight)
        }
        val left = floor((0f - offsetX) / displayScale).toInt()
        val top = floor((0f - offsetY) / displayScale).toInt()
        val right = ceil((viewWidth - offsetX) / displayScale).toInt()
        val bottom = ceil((viewHeight - offsetY) / displayScale).toInt()
        return TileRect(left, top, right, bottom).clampTo(imageWidth, imageHeight)
    }

    /**
     * 在可见区四周各扩 [fraction] 倍宽/高作为预取区（给滑动留缓冲，减少「滑过去白一块」）。
     *
     * 预取区同样裁到图片范围内；`fraction <= 0` 或 NaN 时等于可见区本身。
     */
    fun prefetchRect(
        visible: TileRect,
        imageWidth: Int,
        imageHeight: Int,
        fraction: Float = DEFAULT_PREFETCH_FRACTION
    ): TileRect {
        if (visible.isEmpty) return visible
        val safeFraction = if (fraction.isNaN() || fraction < 0f) 0f else fraction
        val padX = ceil(visible.width * safeFraction).toInt()
        val padY = ceil(visible.height * safeFraction).toInt()
        return TileRect(
            visible.left - padX,
            visible.top - padY,
            visible.right + padX,
            visible.bottom + padY
        ).clampTo(imageWidth, imageHeight)
    }

    /**
     * 把 [area] 切成块的列表：**块起点对齐到 [tileSourcePx] 的整数倍**。
     *
     * 对齐（而不是从 area.left 开始切）是关键：可见区每滑动 1px 就换一套块坐标的话，
     * 缓存永远命中不了，等于每帧全部重解。对齐后滑动的只是「需要的块的集合」，已有块可复用。
     *
     * 返回的每个矩形都落在图片范围内、互不重叠，且并集覆盖 [area]（边缘块可能变小）。
     */
    fun tileRects(
        area: TileRect,
        imageWidth: Int,
        imageHeight: Int,
        tileSourcePx: Int
    ): List<TileRect> {
        val result = ArrayList<TileRect>()
        if (area.isEmpty || !isValidDimensions(imageWidth, imageHeight)) return result
        val tile = tileSourcePx.coerceAtLeast(1)
        val target = area.clampTo(imageWidth, imageHeight)
        if (target.isEmpty) return result
        val startX = (target.left / tile) * tile
        val startY = (target.top / tile) * tile
        var top = startY
        while (top < target.bottom) {
            var left = startX
            while (left < target.right) {
                val right = minOf(left + tile, target.right)
                val bottom = minOf(top + tile, target.bottom)
                result.add(TileRect(left, top, right, bottom))
                left = right
            }
            top = minOf(top + tile, target.bottom)
        }
        return result
    }

    /**
     * OOM 兜底：把采样率再降一半（乘 2）。已到 [MAX_SAMPLE_SIZE] 时原样返回——
     * 调用方据此判定「无法再降级，应该回退整图加载」，而不是死循环重试。
     */
    fun degradedSample(currentSampleSize: Int): Int {
        val base = currentSampleSize.coerceAtLeast(MIN_SAMPLE_SIZE)
        return minOf(base.toLong() * 2, MAX_SAMPLE_SIZE.toLong()).toInt()
    }
}
