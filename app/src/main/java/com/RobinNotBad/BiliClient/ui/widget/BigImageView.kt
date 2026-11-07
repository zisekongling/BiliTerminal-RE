package com.RobinNotBad.BiliClient.ui.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.ImageViewerZoomPolicy
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.ImageTilePolicy
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 按块解码的大图 View，专治「长图/超大图整图解码 OOM」。
 *
 * <h3>它做的事</h3>
 * 打开图片文件后用一个 [BitmapRegionDecoder] 常驻，**只按当前可见区 + 预取区**解码若干
 * 256×256 的小块（块内源码像素 = 256 × 采样率，见 [ImageTilePolicy]），把它们摆到 View 上；
 * 滑动/缩放后重新算「需要哪些块」，缺的补解、已有的复用（块缓存 LRU，上限 6MB）。
 * 解码全在自带单线程池里（[BitmapRegionDecoder] 不是线程安全的），主线程只做
 * 「提交任务 + 画缓存块」。
 *
 * <h3>手势（与原 PhotoView 路径语义保持一致）</h3>
 * - 双击：**不在本 View 内识别**（[PhotoViewpager] 在最外层统一识别，见其类注释）；活动收到
 *   `onDoubleTap` 后调 [zoomNextAt]，档位同样走 [ImageViewerZoomPolicy.nextScale] 的
 *   「原比例 → 2x → 6.25x → 原比例」；
 * - 双指缩放 / 单指拖动滚图；
 * - 单击不消费（本 View 没有点击监听），下载入口仍是活动里那个 `btn_download` 的单击，
 *   「单击下载」这条交互不变；
 * - 未放大时 `requestDisallowInterceptTouchEvent(false)`，横向拖动照旧交给 ViewPager
 *   切上/下一张；放大后或双指时截获事件，避免把「看图」误判成「切图」。
 *
 * <h3>降级路径（绝不能白屏）</h3>
 * 1. 打不开文件 / 解不出任何块 → [onTilesUnavailable]（[REASON_DECODE]），活动把本 View 移除、
 *    显示 PhotoView 整图（按 View 尺寸降采样的 Glide 路径）；
 * 2. 解码 OOM → 采样率再降一半重试（[ImageTilePolicy.degradedSample]）。还能降就继续解（回调
 *    [onTilesDegraded] 只提示一次「清晰度已降低」）；降到 [ImageTilePolicy.MAX_SAMPLE_SIZE] 仍
 *    OOM → [onTilesUnavailable]（[REASON_OOM]）回退整图；
 * 3. 解码线程解失败单块（不常见）→ 记下来不再重试；连续失败且一块都没解出来时按第 1 条回退。
 *
 * 生命周期：活动切页/销毁时**必须**调 [releaseImage]/[destroy]——它们会让 [generation] 自增，
 * 在途解码结果直接丢弃，关流任务排在同一条解码线程后面（不会和解码并发），并回收全部块位图。
 */
class BigImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * 分块解码不可用时的原因码：[REASON_DECODE] 文件打不开/解不出任何块；[REASON_OOM] 内存不足且已无法再降采样。
     */
    companion object {
        const val REASON_DECODE = 1
        const val REASON_OOM = 2

        /** 双击缩放动画时长；动画期间不触发解码，结束后按新倍率重算。 */
        private const val ZOOM_ANIM_MS = 180L

        /** 块位图缓存上限 6MB（256KB/块 → 约 24 块）。 */
        private const val TILE_CACHE_BYTES = 6 * 1024 * 1024
        private const val TILE_BITMAP_BYTES =
            ImageTilePolicy.DEFAULT_TILE_BITMAP_PX * ImageTilePolicy.DEFAULT_TILE_BITMAP_PX * 4
        private const val MAX_TILE_COUNT = TILE_CACHE_BYTES / TILE_BITMAP_BYTES

        /** 每次重算最多补提交多少个块解码任务（避免一屏几十个任务排队导致滑动迟滞）。 */
        private const val MAX_DECODE_TASKS_PER_FRAME = 4

        /** 单块连续失败到这个数、且缓存里一块都没有，才判定「这张图分块解不了」。 */
        private const val MAX_FAILED_TILES_BEFORE_FALLBACK = 4
    }

    /** 块在「网格」上的身份：源图左上角 + 采样率。采样率不同就是不同的块（清晰度不同）。 */
    private data class TileKey(val left: Int, val top: Int, val sample: Int)

    /** 单块解码结果，用于区分「OOM（可降采样重试）」与「解不出（放弃这一块）」。 */
    private sealed class TileResult {
        class Success(val bitmap: Bitmap) : TileResult()
        object Oom : TileResult()
        object Failed : TileResult()
    }

    /** 分块解码失败/内存不足时回调，参数是 [REASON_DECODE] 或 [REASON_OOM]。只会回调一次。 */
    var onTilesUnavailable: ((reason: Int) -> Unit)? = null

    /** 已自动降低分块清晰度时回调（只会回调一次，用于弹一次提示，别每块都弹）。 */
    var onTilesDegraded: (() -> Unit)? = null

    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        isAntiAlias = true
        isFilterBitmap = true
    }

    /**
     * 「加载中 / 分块暂不可用」提示文字的画笔：半透明白，压在照片上任何底色都读得出来。
     * 颜色走语义色 [R.color.image_viewer_hint_text]（原来在这里写死 0xCCFFFFFF），
     * 它作用在图片之上、与主题无关，故亮暗两套同值；minSdk 19 用 ContextCompat 取色。
     */
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.image_viewer_hint_text)
        textAlign = Paint.Align.CENTER
        textSize = 14f * resources.displayMetrics.scaledDensity
    }

    /** 单线程解码池：BitmapRegionDecoder 非线程安全，且串行化才能保证「关流」排在解码之后。 */
    private val tileExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "big-image-tiles").apply { isDaemon = true }
    }

    private val cacheLock = Any()

    /** 块缓存：accessOrder=true 的 LinkedHashMap 自带 LRU 顺序，淘汰时手动 recycle。 */
    private val tileCache = LinkedHashMap<TileKey, Bitmap>(16, 0.75f, true)

    /** 已提交但还没回填的块（避免重复提交）。 */
    private val pendingTiles = HashSet<TileKey>()

    /** 解失败过的块，不再重试（坏图不必反复烧 CPU）。 */
    private val failedTiles = HashSet<TileKey>()

    private var decoder: BitmapRegionDecoder? = null

    /** 每次 setImageFile/releaseImage 自增；异步结果回来时对不上就丢弃。 */
    private var generation = 0

    private var imagePath: String? = null
    private var imageWidth = 0
    private var imageHeight = 0
    private var loadingText: CharSequence? = null

    /** 整图适配 View 的倍率（与 PhotoView 的整图适配一致，通常 < 1）。 */
    private var baseScale = 1f

    /** 用户相对倍率，1f = 整图适配；双击档位在 1f/2f/6.25f 间轮换。 */
    private var level = ImageViewerZoomPolicy.DEFAULT_MIN_SCALE

    private var offsetX = 0f
    private var offsetY = 0f

    /** OOM 后强制的最低采样率（越大越糊越省内存）。 */
    private var sampleFloor = ImageTilePolicy.MIN_SAMPLE_SIZE

    private var hasImageFlag = false
    private var destroyed = false
    private var degradedNotified = false
    private var unavailableReported = false

    private var animator: ValueAnimator? = null

    /** 最终显示倍率 = 适配倍率 × 相对倍率。 */
    private val renderScale: Float get() = baseScale * level

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean = scrollBy(distanceX, distanceY)
    }).apply { setIsLongpressEnabled(false) }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                animator?.cancel()
                animator = null
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                applyLevel(level * detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                scheduleTiles()
            }
        }
    )

    // ---------------------------------------------------------------- 对外 API

    /**
     * 打开一张图（[path] 是磁盘上的**原图文件**，尺寸由活动预先探测好后传进来）。
     *
     * 同一个文件重复调用会直接返回（ViewPager 翻回来复用同一页时不重开、不闪）。
     * 尺寸不合法（<=0）时按「探测失败」处理：打开 decoder 后用它的真实尺寸兜底。
     */
    fun setImageFile(path: String, sourceWidth: Int, sourceHeight: Int, loadingText: CharSequence?) {
        if (!destroyed &&
            path == imagePath &&
            sourceWidth == imageWidth &&
            sourceHeight == imageHeight &&
            decoder != null &&
            !unavailableReported
        ) {
            return
        }
        releaseImageInternal(clearText = false)
        imagePath = path
        imageWidth = sourceWidth
        imageHeight = sourceHeight
        this.loadingText = loadingText
        sampleFloor = ImageTilePolicy.MIN_SAMPLE_SIZE
        degradedNotified = false
        unavailableReported = false
        hasImageFlag = true
        generation++
        resetLayout()
        openDecoder(path, generation)
        invalidate()
    }

    /** 当前是否真的在显示一张图（活动据此决定双击走分块缩放还是原 PhotoView 逻辑）。 */
    fun hasImage(): Boolean = hasImageFlag && !unavailableReported && !destroyed

    /**
     * 双击三档缩放（与 PhotoView 路径的 `ImageViewerZoomPolicy.nextScale` 同一套档位）。
     * 返回 false 表示这次双击没被消费（没有图 / 还在动画中），调用方可忽略。
     */
    fun zoomNextAt(x: Float, y: Float): Boolean {
        if (!hasImageFlag || destroyed || unavailableReported) return false
        val target = ImageViewerZoomPolicy.nextScale(
            level,
            ImageViewerZoomPolicy.DEFAULT_MIN_SCALE,
            ImageViewerZoomPolicy.DEFAULT_MID_SCALE,
            ImageViewerZoomPolicy.DEFAULT_MAX_SCALE
        )
        return animateLevelTo(target, x, y)
    }

    /** 切页/不再需要这张图时调用：丢弃在途解码、回收块位图、关闭 decoder。 */
    fun releaseImage() {
        if (!destroyed) releaseImageInternal(clearText = true)
        invalidate()
    }

    /** 活动销毁时调用（之后再调 releaseImage 也无害）。 */
    fun destroy() {
        if (destroyed) return
        releaseImageInternal(clearText = true)
        destroyed = true
        tileExecutor.shutdown()
        invalidate()
    }

    // ---------------------------------------------------------------- 布局与手势

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (hasImageFlag) {
            resetLayout()
            scheduleTiles()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!hasImageFlag || unavailableReported) return false
        val pointerCount = event.pointerCount
        val zoomed = level > ImageViewerZoomPolicy.DEFAULT_MIN_SCALE + 0.001f
        val canPan = renderScale * imageWidth > width + 1f || renderScale * imageHeight > height + 1f
        // 只有「放大后确实能拖动」或「双指缩放」时才截获事件；默认比例下让 ViewPager
        // 照旧处理横向滑动（左右切图这条交互绝不能坏）。
        val captureTouch = pointerCount > 1 || (zoomed && canPan)
        parent?.requestDisallowInterceptTouchEvent(captureTouch)

        scaleDetector.onTouchEvent(event)
        if (pointerCount <= 1 && !scaleDetector.isInProgress) {
            // 只用来识别拖动（onScroll），不处理点击：单击下载由活动里的按钮负责。
            gestureDetector.onTouchEvent(event)
        }
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            scheduleTiles()
        }
        return true
    }

    /** 单指拖动：位移取反加到偏移上（手指往左拖，图往左走）。 */
    private fun scrollBy(distanceX: Float, distanceY: Float): Boolean {
        if (!hasImageFlag) return false
        val displayWidth = renderScale * imageWidth
        val displayHeight = renderScale * imageHeight
        if (displayWidth <= width && displayHeight <= height) return false
        offsetX -= distanceX
        offsetY -= distanceY
        clampOffsets()
        invalidate()
        return true
    }

    /**
     * 把相对倍率改到 `newLevel`，并保持 `(pivotX, pivotY)` 下面那个源像素不动。
     * 倍率被夹在 `[ImageViewerZoomPolicy.DEFAULT_MIN_SCALE]`～
     * `[ImageViewerZoomPolicy.DEFAULT_MAX_SCALE]` 之间；变化极小时不动，避免缩放检测抖动。
     */
    private fun applyLevel(newLevel: Float, pivotX: Float, pivotY: Float) {
        if (!hasImageFlag) return
        val clamped = newLevel.coerceIn(
            ImageViewerZoomPolicy.DEFAULT_MIN_SCALE,
            ImageViewerZoomPolicy.DEFAULT_MAX_SCALE
        )
        val oldScale = renderScale
        if (oldScale <= 0f || oldScale.isNaN()) return
        if (abs(clamped - level) < 0.0001f) return
        val safePivotX = if (pivotX.isFinite()) pivotX else width / 2f
        val safePivotY = if (pivotY.isFinite()) pivotY else height / 2f
        val sourceX = (safePivotX - offsetX) / oldScale
        val sourceY = (safePivotY - offsetY) / oldScale
        level = clamped
        val newScale = renderScale
        offsetX = safePivotX - sourceX * newScale
        offsetY = safePivotY - sourceY * newScale
        clampOffsets()
        invalidate()
    }

    /** 双击缩放动画：动画期间不提交解码任务（每帧重算采样率会把解码池打爆）。 */
    private fun animateLevelTo(targetLevel: Float, pivotX: Float, pivotY: Float): Boolean {
        val from = level
        val to = targetLevel.coerceIn(
            ImageViewerZoomPolicy.DEFAULT_MIN_SCALE,
            ImageViewerZoomPolicy.DEFAULT_MAX_SCALE
        )
        if (abs(to - from) < 0.01f) return false
        animator?.cancel()
        val safePivotX = if (pivotX.isFinite()) pivotX else width / 2f
        val safePivotY = if (pivotY.isFinite()) pivotY else height / 2f
        val animatorInstance = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ZOOM_ANIM_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                applyLevel(from + (to - from) * progress, safePivotX, safePivotY)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator === animation) animator = null
                    scheduleTiles()
                }
            })
        }
        animator = animatorInstance
        animatorInstance.start()
        return true
    }

    // ---------------------------------------------------------------- 绘制

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hasImageFlag || imageWidth <= 0 || imageHeight <= 0) {
            drawHint(canvas)
            return
        }
        val scale = renderScale
        if (scale <= 0f || scale.isNaN() || scale.isInfinite()) {
            drawHint(canvas)
            return
        }
        val drawn = drawCachedTiles(canvas, scale)
        if (!drawn) drawHint(canvas)
        // 绘制同时是很自然的「该补块了」时机：滑动/缩放后一帧内就把任务排上。
        if (animator == null && !unavailableReported) scheduleTiles()
    }

    /**
     * 画缓存里的块：**采样率粗的先画、细的后画**，于是先出模糊轮廓、再叠清晰块；
     * 块与块之间 dst 各外扩 0.5px，掩盖浮点取整留下的一像素缝。
     */
    private fun drawCachedTiles(canvas: Canvas, scale: Float): Boolean {
        val entries: List<Map.Entry<TileKey, Bitmap>> = synchronized(cacheLock) {
            if (tileCache.isEmpty()) return false
            ArrayList(tileCache.entries)
        }
        val allowed = allowedSampleSize(scale)
        var drawn = false
        for (entry in entries.sortedByDescending { it.key.sample }) {
            val key = entry.key
            if (key.sample > allowed) continue
            val bitmap = entry.value
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) continue
            // 一块实际覆盖的源像素宽度：位图宽 × 采样率，再被图片右/下边界裁掉。
            val spanX = min(bitmap.width.toLong() * key.sample, (imageWidth - key.left).toLong())
            val spanY = min(bitmap.height.toLong() * key.sample, (imageHeight - key.top).toLong())
            if (spanX <= 0L || spanY <= 0L) continue
            val left = offsetX + key.left * scale
            val top = offsetY + key.top * scale
            val right = offsetX + (key.left + spanX) * scale
            val bottom = offsetY + (key.top + spanY) * scale
            if (right < 0f || bottom < 0f || left > width.toFloat() || top > height.toFloat()) continue
            canvas.drawBitmap(bitmap, null, RectF(left, top, right + 0.5f, bottom + 0.5f), tilePaint)
            drawn = true
        }
        return drawn
    }

    /** 还没有任何块时画一行居中提示（可空）。 */
    private fun drawHint(canvas: Canvas) {
        val text = loadingText?.takeIf { it.isNotEmpty() } ?: return
        val centerY = height / 2f - (hintPaint.ascent() + hintPaint.descent()) / 2f
        canvas.drawText(text, 0, text.length, width / 2f, centerY, hintPaint)
    }

    // ---------------------------------------------------------------- 解码调度

    /** 重算可见/预取区域并补提交缺的块。只会在主线程调用。 */
    private fun scheduleTiles() {
        if (!hasImageFlag || destroyed || unavailableReported) return
        if (width <= 0 || height <= 0 || decoder == null) return
        ensureTiles()
    }

    private fun ensureTiles() {
        val scale = renderScale
        if (scale <= 0f || scale.isNaN() || scale.isInfinite()) return
        val visible = ImageTilePolicy.visibleRect(
            imageWidth,
            imageHeight,
            width,
            height,
            scale,
            offsetX,
            offsetY
        )
        if (visible.isEmpty) return
        val prefetch = ImageTilePolicy.prefetchRect(visible, imageWidth, imageHeight)
        val allowed = allowedSampleSize(scale)

        var submitted = 0
        var sample = allowed
        // 由细到粗：先保证当前倍率该有的清晰度，不够再加一层更糊的兜底，让用户先看到东西。
        while (sample >= ImageTilePolicy.MIN_SAMPLE_SIZE && submitted < MAX_DECODE_TASKS_PER_FRAME) {
            // 最清晰的那一层铺「预取区」，更粗的层只铺可见区（省内存）。
            val target = if (sample == allowed) prefetch else visible
            val tileSourcePx = ImageTilePolicy.tileSourcePx(sample)
            for (rect in ImageTilePolicy.tileRects(target, imageWidth, imageHeight, tileSourcePx)) {
                if (submitted >= MAX_DECODE_TASKS_PER_FRAME) break
                val key = TileKey(rect.left, rect.top, sample)
                if (tileCacheContains(key) || pendingTiles.contains(key) || failedTiles.contains(key)) {
                    continue
                }
                // 已经有更清晰的块完整盖住这块，就不用再解一份糊的。
                if (isRectCoveredByFiner(rect, sample)) continue
                submitTile(rect, sample)
                submitted++
            }
            if (sample == ImageTilePolicy.MIN_SAMPLE_SIZE) break
            sample = max(ImageTilePolicy.MIN_SAMPLE_SIZE, sample / 2)
        }
        if (submitted > 0) invalidate()
    }

    private fun tileCacheContains(key: TileKey): Boolean = synchronized(cacheLock) {
        tileCache.containsKey(key)
    }

    /** 缓存里是否有一块**采样率更小（更清晰）**的块完整覆盖 [rect]。 */
    private fun isRectCoveredByFiner(rect: ImageTilePolicy.TileRect, sample: Int): Boolean {
        synchronized(cacheLock) {
            for (entry in tileCache.entries) {
                val key = entry.key
                if (key.sample >= sample) continue
                val bitmap = entry.value
                if (bitmap.isRecycled) continue
                val right = key.left.toLong() + bitmap.width.toLong() * key.sample
                val bottom = key.top.toLong() + bitmap.height.toLong() * key.sample
                if (key.left <= rect.left && key.top <= rect.top &&
                    right >= rect.right && bottom >= rect.bottom
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun submitTile(rect: ImageTilePolicy.TileRect, sample: Int) {
        val key = TileKey(rect.left, rect.top, sample)
        if (!pendingTiles.add(key)) return
        val requestGeneration = generation
        tileExecutor.execute {
            val result = decodeTile(rect, sample)
            CenterThreadPool.runOnUiThread {
                if (destroyed || requestGeneration != generation) return@runOnUiThread
                pendingTiles.remove(key)
                when (result) {
                    is TileResult.Success -> {
                        putTile(key, result.bitmap)
                        invalidate()
                    }
                    TileResult.Oom -> handleTileOom()
                    TileResult.Failed -> handleTileFailed(key)
                }
            }
        }
    }

    /** 在解码线程里按块解码；OOM 与其它失败分开上报（前者可以降采样重试）。 */
    private fun decodeTile(rect: ImageTilePolicy.TileRect, sample: Int): TileResult {
        val activeDecoder = decoder ?: return TileResult.Failed
        return try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inJustDecodeBounds = false
            }
            val bitmap = activeDecoder.decodeRegion(
                Rect(rect.left, rect.top, rect.right, rect.bottom),
                options
            )
            if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                TileResult.Success(bitmap)
            } else {
                TileResult.Failed
            }
        } catch (e: OutOfMemoryError) {
            TileResult.Oom
        } catch (t: Throwable) {
            TileResult.Failed
        }
    }

    /** 单块试过两次（正常采样 + 降采样）还解不出，就拉倒，别反复烧 CPU。 */
    private fun handleTileFailed(key: TileKey) {
        failedTiles.add(key)
        val empty = synchronized(cacheLock) { tileCache.isEmpty() }
        if (empty && failedTiles.size >= MAX_FAILED_TILES_BEFORE_FALLBACK && !unavailableReported) {
            reportUnavailable(REASON_DECODE)
        }
        invalidate()
    }

    /**
     * 解码 OOM：把采样率再降一半（块位图不变大、只是每块盖的源域变大 → 更糊更省），
     * 清掉缓存重来；已经降到 [ImageTilePolicy.MAX_SAMPLE_SIZE] 还 OOM 才回退整图。
     */
    private fun handleTileOom() {
        val base = max(sampleFloor, ImageTilePolicy.sampleSizeFor(renderScale))
        val next = ImageTilePolicy.degradedSample(base)
        if (next <= base || base >= ImageTilePolicy.MAX_SAMPLE_SIZE) {
            reportUnavailable(REASON_OOM)
            return
        }
        sampleFloor = next
        clearCache()
        failedTiles.clear()
        if (!degradedNotified) {
            degradedNotified = true
            onTilesDegraded?.invoke()
        }
        invalidate()
    }

    private fun reportUnavailable(reason: Int) {
        if (unavailableReported) return
        unavailableReported = true
        clearCache()
        onTilesUnavailable?.invoke(reason)
    }

    // ---------------------------------------------------------------- 缓存与资源

    private fun allowedSampleSize(scale: Float): Int =
        max(ImageTilePolicy.sampleSizeFor(scale), sampleFloor)

    private fun putTile(key: TileKey, bitmap: Bitmap) {
        synchronized(cacheLock) {
            tileCache.remove(key)?.recycle()
            tileCache[key] = bitmap
            trimCacheLocked()
        }
    }

    /** LRU 淘汰：accessOrder=true 的迭代器先给最久没访问的块，淘汰即 recycle。 */
    private fun trimCacheLocked() {
        while (tileCache.size > MAX_TILE_COUNT) {
            val iterator = tileCache.entries.iterator()
            if (!iterator.hasNext()) break
            val eldest = iterator.next()
            eldest.value.recycle()
            iterator.remove()
        }
    }

    private fun clearCache() {
        synchronized(cacheLock) {
            for (bitmap in tileCache.values) bitmap.recycle()
            tileCache.clear()
        }
        pendingTiles.clear()
    }

    /**
     * 打开解码器（在解码线程做，避免主线程 IO）。打开成功才允许提交块任务，
     * 打开失败直接按「分块不可用」回退，绝不白屏。
     */
    private fun openDecoder(path: String, requestGeneration: Int) {
        tileExecutor.execute {
            val opened = try {
                BitmapRegionDecoder.newInstance(path, false)
            } catch (t: Throwable) {
                null
            }
            CenterThreadPool.runOnUiThread {
                if (destroyed || requestGeneration != generation) {
                    try {
                        opened?.recycle()
                    } catch (_: Throwable) {
                    }
                    return@runOnUiThread
                }
                decoder = opened
                if (opened == null) {
                    hasImageFlag = false
                    reportUnavailable(REASON_DECODE)
                } else {
                    // 活动探测的尺寸不可信时（<=0），用 decoder 的真实尺寸兜底。
                    if (!ImageTilePolicy.isValidDimensions(imageWidth, imageHeight)) {
                        imageWidth = opened.width
                        imageHeight = opened.height
                        resetLayout()
                    }
                    ensureTiles()
                }
                invalidate()
            }
        }
    }

    /** 丢弃当前图：代数自增让在途结果失效，块位图回收，decoder 关流排在解码任务之后。 */
    private fun releaseImageInternal(clearText: Boolean) {
        generation++
        hasImageFlag = false
        imagePath = null
        imageWidth = 0
        imageHeight = 0
        if (clearText) loadingText = null
        animator?.cancel()
        animator = null
        failedTiles.clear()
        clearCache()
        val closing = decoder
        decoder = null
        if (closing != null && !tileExecutor.isShutdown) {
            // 关流排在解码线程末尾：与「释放前已提交的解码任务」串行，不会一边解一边关。
            tileExecutor.execute {
                try {
                    closing.recycle()
                } catch (_: Throwable) {
                }
            }
        }
    }

    // ---------------------------------------------------------------- 偏移工具

    /** 按当前倍率重新算「整图适配倍率」，并把相对倍率复位到 1（= 双击档位的第一档）。 */
    private fun resetLayout() {
        baseScale = ImageTilePolicy.fitScale(imageWidth, imageHeight, width, height)
        level = ImageViewerZoomPolicy.DEFAULT_MIN_SCALE
        offsetX = 0f
        offsetY = 0f
        clampOffsets()
    }

    /**
     * 偏移夹紧：图比 View 大时限制在「不留白」范围内；图比 View 小时居中。
     * 这是「长图上下滑动、窄图不跑偏」的核心，缺了它拖动能把图拖出屏幕。
     */
    private fun clampOffsets() {
        if (imageWidth <= 0 || imageHeight <= 0) return
        val scale = renderScale
        if (scale <= 0f || scale.isNaN()) return
        offsetX = clampAxis(offsetX, imageWidth * scale, width.toFloat())
        offsetY = clampAxis(offsetY, imageHeight * scale, height.toFloat())
    }

    private fun clampAxis(offset: Float, displaySize: Float, viewSize: Float): Float {
        if (displaySize <= viewSize) return (viewSize - displaySize) / 2f
        val minOffset = viewSize - displaySize
        return offset.coerceIn(minOffset, 0f)
    }
}
