package com.RobinNotBad.BiliClient.activity

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.viewpager.widget.ViewPager
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.viewpager.ViewPagerViewAdapter
import com.RobinNotBad.BiliClient.ui.widget.BigImageView
import com.RobinNotBad.BiliClient.ui.widget.PhotoViewpager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.ImageTilePolicy
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.FutureTarget
import com.bumptech.glide.request.target.Target
import com.github.chrisbanes.photoview.PhotoView
import java.io.File
import kotlin.math.abs

/**
 * 双击缩放的档位策略（纯逻辑，JVM 单测见 `ImageViewerZoomPolicyTest`）。
 *
 * 用户要的是「一次双击放大、再一次更大、第三次回原比例」（反馈 #14 的原话：
 * 「双击图片不能切换一次放大，二次放大和原比例」），与 PhotoView 自带的
 * min → mid → max → min 循环一致；这里把它抽成纯函数，方便脱离设备测边界。
 */
internal object ImageViewerZoomPolicy {

    /** 最小档 = 原比例。 */
    const val DEFAULT_MIN_SCALE = 1.0f

    /** 中间档 = 第一次双击放到的倍率；PhotoView 默认 1.75 在手表小屏上不够明显，取 2.0。 */
    const val DEFAULT_MID_SCALE = 2.0f

    /** 最大档；沿用原来的 `maximumScale = 6.25f`。 */
    const val DEFAULT_MAX_SCALE = 6.25f

    /** 浮点比较容差：缩放手势会留下 0.0001 级误差，直接 == 判断会把「已经在中间档」判错。 */
    const val EPSILON = 0.01f

    /**
     * 下一次双击应该落到哪个倍率。
     *
     * 判据按「当前值离哪一档近」放宽一个 [EPSILON]：
     * - 低于中间档（含少量误差） → 中间档；
     * - 中间档到最大档之间 → 最大档；
     * - 已在最大档（含误差） → 回原比例。
     *
     * 这样即使用户先双指捏到 3.2 倍，再双击也是「放到最大档」，不会出现点了没反应。
     */
    @JvmStatic
    fun nextScale(current: Float, minimum: Float, medium: Float, maximum: Float): Float {
        val low = sanitizeLevel(minimum, DEFAULT_MIN_SCALE)
        val mid = sanitizeLevel(medium, DEFAULT_MID_SCALE).coerceAtLeast(low + EPSILON)
        val high = sanitizeLevel(maximum, DEFAULT_MAX_SCALE).coerceAtLeast(mid + EPSILON)
        // 读不到当前倍率（NaN / Infinity）时按「还没放大的原比例」处理：双击一次就该放大
        if (current.isNaN() || current.isInfinite()) return mid
        return when {
            current < mid - EPSILON -> mid
            current < high - EPSILON -> high
            else -> low
        }
    }

    /** 档位参数兜底：非有限值 / 非正数时回落到给定默认值。 */
    private fun sanitizeLevel(value: Float, fallback: Float): Float =
        if (value.isNaN() || value.isInfinite() || value <= 0f) fallback else value
}

/**
 * 「这一页的整图 [android.widget.ImageView] 现在该不该让 Glide 清掉」的判据
 * （纯逻辑，JVM 单测见 `ImageViewerClearPolicyTest`）。
 *
 * 起因：线上崩溃 `IllegalStateException: Cannot recycle a resource while it is still acquired`
 * （`ResourceRecycler$ResourceRecyclerCallback.handleMessage` → `EngineResource.recycle`）。
 * 那条崩溃栈是**主线程消息队列里的延迟回收**，不是某次调用现场的异常；Glide 4.16 里
 * `EngineResource.recycle()` 只在 `acquired > 0` 时抛，所以只能从「少制造回收流量」这一侧收敛。
 *
 * 这里能收敛的是：「对同一个 View 反复 clear」。`RequestManager.clear(View)` 清的是**当前挂在该
 * View 上的那一次请求**，因此
 * - 这一页其实没发起过整图加载（探测失败直接走了分块 / 从没轮到这一页）时，clear 是空转；
 * - 已经清过一次之后再 clear，是又一次 `untrackOrDelegate` → `glide.removeFromManagers` 的空转
 *   （`RequestManager.untrack` 在 target 没有 request 时直接返回，见 RequestManager.java:670-684）。
 * 空转本身不致命，但它让「释放/淘汰/延迟回收」的节奏多跑了几遍，正好落在上面那条崩溃的概率窗口里。
 */
internal object ImageViewerClearPolicy {

    /**
     * 这一次是否真的要向 Glide 发起 clear。
     *
     * @param photoLoadStarted 这一页是否**真的**对整图 View 发起过加载（[ImageViewerActivity.attachPhoto]）
     * @param photoCleared 那一整图请求是否已经被清过
     * @return 只有「发起过、且还没清过」才返回 true，保证同一个请求最多 clear 一次
     */
    @JvmStatic
    fun shouldClearPhoto(photoLoadStarted: Boolean, photoCleared: Boolean): Boolean =
        photoLoadStarted && !photoCleared
}

/** 页状态：还没探测出这张图该走哪条路径。 */
private const val PAGE_PENDING = 0

/** 普通整图路径（PhotoView + Glide）：小图，以及分块不可用时的回退目标。 */
private const val PAGE_PHOTO = 1

/** 分块路径（[BigImageView]）：长图 / 超大图。 */
private const val PAGE_BIG = 2

/**
 * 一页的全部状态。
 *
 * [container] 交给 `PagerAdapter`；[photoView] 是原来的整图路径（逻辑一字未改，只是从
 * 「直接当页」变成「装在容器里」）；[bigView] 只在探测出「这张图太大」时才懒创建、叠在
 * PhotoView 上层。
 */
private class ImagePage(val container: FrameLayout, val photoView: PhotoView) {

    var bigView: BigImageView? = null

    var mode: Int = PAGE_PENDING

    /** 每页只探测一次尺寸（探测结果同时决定路径）。 */
    var probeStarted = false

    /** 探测到的磁盘原图路径：大图页被切走释放后，切回来用它重新打开。 */
    var filePath: String? = null

    var sourceWidth: Int = 0

    var sourceHeight: Int = 0

    /** 「清晰度已降低」只提示一次。 */
    var degradedNotified = false

    /** 进行中的尺寸探测请求，活动销毁时要取消（否则 Glide 会往已销毁的宿主里回调）。 */
    var probeRequest: FutureTarget<File>? = null

    /** 是否真的对 [photoView] 发起过整图加载：探测失败 / 只走分块路径时它是 false。 */
    var photoLoadStarted = false

    /** 那次整图加载是否已经被 clear 过：同一个请求只清一次（判据见 [ImageViewerClearPolicy]）。 */
    var photoCleared = false
}

/** 原图尺寸探测结果（`inJustDecodeBounds` 的解码结果，只读文件头）。 */
private data class ImageBounds(val width: Int, val height: Int, val mimeType: String?)

class ImageViewerActivity : BaseActivity() {

    /**
     * 看图时左右翻页、放大后拖动都是横向手势，手表的系统右滑返回会把它们抢成退出（反馈 #38）。
     * 清单里虽然写了 `Theme.NoSwipe`，但 `BaseActivity.onCreate` 会按用户主题重新 `setTheme`
     * 把它覆盖掉，所以必须走这个钩子。退出靠返回键 / 表冠键。
     */
    override val forceNoSwipeTheme: Boolean
        get() = true

    /** 与 ViewPager 的页序一一对应；每页一个容器，容器里可能同时有 PhotoView 与 BigImageView。 */
    private val pages = ArrayList<ImagePage>()

    private lateinit var viewPager: PhotoViewpager
    private lateinit var imageList: ArrayList<String>

    /**
     * 下载入口的一次性闸门。
     *
     * 下载改成**单击直接开始**之后，快速连点两下会连开两个下载页（两个页面都想写同一个文件）。
     * 这里在拉起下载页时置位、回到本页的 onResume 复位。
     */
    private var downloadLaunched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 主题由 BaseActivity 按用户选择设置，这里不再强制回 B站粉
        setContentView(R.layout.activity_image_viewer)
        val intent = intent
        // 本页面 exported=false，只被应用内入口拉起；但历史上有过空 Intent 进来，缺参数时退出而非崩溃
        val urls = intent.getStringArrayListExtra("imageList")
        if (urls.isNullOrEmpty()) {
            MsgUtil.showMsgLong(UiText.of("未收到图片数据"))
            finish()
            return
        }
        imageList = urls

        viewPager = findViewById(R.id.viewPager)
        val textView = findViewById<TextView>(R.id.text_page)

        // ============ 下载入口（反馈 #17） ============
        // 原来要求「3 秒内点两次」：第一次点击的提示是页面底部的 Snackbar，**正好盖住左下角的下载
        // 按钮本身**，用户按提示去点中间的文字时落不到按钮上；3 秒窗口一过又要重来。评论区图片本来
        // 就是「看一眼 → 想存下来」，这个确认既不防误触（按钮在角落、下载也不是破坏性操作），
        // 又把唯一入口挡住了，所以改成单击直接开始下载（失败提示由 DownloadActivity 负责）。
        val download = findViewById<ImageButton>(R.id.btn_download)
        download.setOnClickListener {
            if (downloadLaunched) return@setOnClickListener
            val position = viewPager.currentItem
            if (position !in imageList.indices) return@setOnClickListener
            // 协议相对地址 / 明文 http 在这里先规范化（失败时的提示交给 DownloadActivity 统一给）
            val link = PictureDownloadPolicy.normalizeUrl(imageList[position]) ?: imageList[position]
            downloadLaunched = true
            startActivity(
                Intent(this, DownloadActivity::class.java)
                    .putExtra("link", link)
                    .putExtra("path", FileUtil.getPicturePath().absolutePath)
                    .putExtra("type", 0)
            )
        }

        // 每页：容器 + PhotoView（原来的整图路径）。BigImageView 等探测出「这是长图/超大图」再叠上去，
        // 小图完全走原逻辑，不产生任何额外开销。
        for (i in imageList.indices) {
            val container = FrameLayout(this)
            val photoView = PhotoView(this)
            // 显式给三档，别只改 maximumScale：PhotoView 的中间档默认 1.75，手表上双击后
            // 放大不明显，用户会觉得"没放大"。
            photoView.setScaleLevels(
                ImageViewerZoomPolicy.DEFAULT_MIN_SCALE,
                ImageViewerZoomPolicy.DEFAULT_MID_SCALE,
                ImageViewerZoomPolicy.DEFAULT_MAX_SCALE
            )
            // 关掉 PhotoView 自带的那套双击：双击统一由 PhotoViewpager 在最外层识别（原因见
            // PhotoViewpager 类注释），否则容器拦截时它收不到事件，没被拦截时又会和我们的
            // 缩放动画叠在一起。
            photoView.setOnDoubleTapListener(NOOP_DOUBLE_TAP_LISTENER)
            container.addView(
                photoView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            pages.add(ImagePage(container, photoView))
        }

        // 26.10.27 性能：原来 onCreate 里一次性把**所有**图按原尺寸（SIZE_ORIGINAL）解码，
        // 九宫格动态就是 9 张 1024w ARGB 位图同时进内存，手表上容易 OOM（而且 Glide 的异步 OOM
        // 根本不会落到这里的 catch）。现在只加载当前页和左右各一页，翻页时再补。
        // 26.11.06 大图：加载前先异步探一次「文件尺寸 + 格式」——单边 >4096 或面积 >16M 的图
        // 走 BigImageView 分块解码（见 ImageTilePolicy），否则仍走原来的 PhotoView 整图路径。
        val pageViews = ArrayList<View>(pages.size)
        for (page in pages) pageViews.add(page.container)
        viewPager.adapter = ViewPagerViewAdapter(pageViews)

        // 双击 → 当前页在触点位置放大 / 再放大 / 复原
        viewPager.onDoubleTap = { x, y -> zoomAt(viewPager.currentItem, x, y) }

        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            @SuppressLint("SetTextI18n")
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                if (positionOffset % 1 == 0f)
                    textView.text = "第${position + 1}/${imageList.size}张"
            }

            override fun onPageSelected(position: Int) {
                loadAround(position)
                // 离当前页两页以上的分块页立刻释放块位图与 decoder：长图一张就可能占几十 MB，
                // 九宫格翻到后面时前面的分块页不能继续占着。
                for (i in pages.indices) {
                    if (abs(i - position) > 1) pages[i].bigView?.releaseImage()
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
            }
        })

        loadAround(0)
    }

    override fun onResume() {
        super.onResume()
        // 从下载页回来（它会自动结束）后重新允许点下载
        downloadLaunched = false
    }

    override fun onDestroy() {
        // 探测请求必须取消：Glide 的 FutureTarget 若在宿主销毁后才回调，会往死掉的活动里塞 View。
        for (page in pages) {
            page.probeRequest?.let { target ->
                try {
                    Glide.with(this).clear(target)
                } catch (e: Exception) {
                    Logu.d("image-viewer", "clear probe target failed: ${e.message}")
                }
            }
            page.probeRequest = null
            page.bigView?.destroy()
            page.bigView = null
        }
        super.onDestroy()
    }

    /** 加载当前页与左右各一页：已探测过的页（切走又切回）只做「大图重新挂上」。 */
    private fun loadAround(position: Int) {
        for (i in (position - 1)..(position + 1)) {
            if (i !in imageList.indices) continue
            val page = pages[i]
            if (page.probeStarted) {
                if (page.mode == PAGE_BIG) attachTiled(page)
                continue
            }
            page.probeStarted = true
            probePage(page, i)
        }
    }

    /**
     * 探测第 [index] 页的磁盘文件尺寸与格式，决定走分块还是整图。
     *
     * 用 Glide 的 `asFile()` 先拿到 disk cache 里的原图文件（与后面 `into(photoView)` 用的是
     * 同一个 URL、同一份缓存），再在主线程之外用 `inJustDecodeBounds` 只读文件头——不解码像素，
     * 开销是 O(1)。
     */
    private fun probePage(page: ImagePage, index: Int) {
        val request = try {
            Glide.with(this).asFile()
                .load(GlideUtil.url_zoom(imageList[index]))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .submit()
        } catch (e: Exception) {
            null
        }
        page.probeRequest = request
        if (request == null) {
            // 连请求都发不出去（网络/磁盘异常）：退回整图路径，让 Glide 自己报错
            attachPhoto(page, index, fallback = false)
            return
        }
        CenterThreadPool.run {
            val file = try {
                request.get()
            } catch (t: Throwable) {
                null
            }
            val bounds = if (file != null) readImageBounds(file) else ImageBounds(0, 0, null)
            CenterThreadPool.runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                onProbed(page, index, file, bounds)
            }
        }
    }

    /** 只读文件头拿尺寸与 MIME（`inJustDecodeBounds` 不会分配像素内存）。 */
    private fun readImageBounds(file: File): ImageBounds {
        return try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, options)
            ImageBounds(options.outWidth, options.outHeight, options.outMimeType)
        } catch (e: Exception) {
            ImageBounds(0, 0, null)
        }
    }

    /** 探测结束：大图且格式可分块 → 分块路径；否则（含探测失败）→ 原整图路径。 */
    private fun onProbed(page: ImagePage, index: Int, file: File?, bounds: ImageBounds) {
        if (file == null || !ImageTilePolicy.isTileableMime(bounds.mimeType) ||
            !ImageTilePolicy.shouldTile(bounds.width, bounds.height)
        ) {
            attachPhoto(page, index, fallback = false)
            return
        }
        page.mode = PAGE_BIG
        page.filePath = file.absolutePath
        page.sourceWidth = bounds.width
        page.sourceHeight = bounds.height
        attachTiled(page)
    }

    /** 把该页切到分块路径（幂等：ViewPager 来回翻时重复调用只会重挂已释放的图）。 */
    private fun attachTiled(page: ImagePage) {
        val path = page.filePath ?: return
        val big = page.bigView ?: BigImageView(this).also { created ->
            created.onTilesUnavailable = { reason -> onTilesUnavailable(page, reason) }
            created.onTilesDegraded = { onTilesDegraded(page) }
            page.container.addView(
                created,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            page.bigView = created
        }
        page.photoView.visibility = View.GONE
        // 底下那张整图不再需要：清掉它，别让「整图位图 + 分块位图」同时占内存（分块的初衷）。
        // 26.11.09：改成「只清这一页真发起过的那次整图请求，且只清一次」——原来是无条件 clear，
        // 而本方法在 ViewPager 来回翻时会被反复调用（注释已写明它必须幂等），于是同一个 photoView
        // 会被反复 clear。判据与单测见 [ImageViewerClearPolicy]。
        if (ImageViewerClearPolicy.shouldClearPhoto(page.photoLoadStarted, page.photoCleared)) {
            page.photoCleared = true
            try {
                Glide.with(this).clear(page.photoView)
            } catch (e: Exception) {
                Logu.d("image-viewer", "clear photo view failed: ${e.message}")
            }
        }
        if (!big.hasImage()) {
            big.setImageFile(
                path,
                page.sourceWidth,
                page.sourceHeight,
                getString(R.string.image_viewer_port_tile_loading)
            )
        }
    }

    /** 分块路径不可用（文件损坏 / 内存不足且已降到底）：回退整图，并提示一次。 */
    private fun onTilesUnavailable(page: ImagePage, reason: Int) {
        if (page.mode != PAGE_BIG) return
        val index = pages.indexOf(page)
        if (index < 0) return
        page.mode = PAGE_PHOTO
        MsgUtil.showMsg(
            getString(
                if (reason == BigImageView.REASON_OOM) {
                    R.string.image_viewer_port_tile_oom
                } else {
                    R.string.image_viewer_port_tile_failed
                }
            )
        )
        attachPhoto(page, index, fallback = true)
    }

    /** 分块中途自动降了清晰度：只提示一次，别每块都弹。 */
    private fun onTilesDegraded(page: ImagePage) {
        if (page.degradedNotified) return
        page.degradedNotified = true
        MsgUtil.showMsg(getString(R.string.image_viewer_port_tile_degraded))
    }

    /**
     * 回到原整图路径。
     *
     * [fallback] = true 表示「大图分块失败后回退」：此时**不能**再按 `SIZE_ORIGINAL` 解码
     * （那正是会 OOM 的路径），交给 Glide 按 View 尺寸降采样，先显示出来再说；
     * [fallback] = false 是普通小图，保持原来的 `SIZE_ORIGINAL` 行为不变。
     */
    private fun attachPhoto(page: ImagePage, index: Int, fallback: Boolean) {
        page.mode = PAGE_PHOTO
        page.bigView?.let { view ->
            page.container.removeView(view)
            view.destroy()
            page.bigView = null
        }
        page.photoView.visibility = View.VISIBLE
        // 记下「这一页真发起过整图请求」，并复位 clear 标记：新一次请求可以（也才需要）被清一次。
        page.photoLoadStarted = true
        page.photoCleared = false
        try {
            val builder = Glide.with(this).asDrawable()
                .load(GlideUtil.url_zoom(imageList[index]))
                .transition(GlideUtil.getTransitionOptions())
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            if (!fallback) {
                builder.override(Target.SIZE_ORIGINAL)
            }
            builder.into(page.photoView)
        } catch (e: OutOfMemoryError) {
            MsgUtil.showMsg(UiText.of("超出内存，加载失败"))
        } catch (e: Exception) {
            MsgUtil.err(UiText.of("图片查看"), e)
        }
    }

    /**
     * 对第 [position] 张图做一次双击缩放，触点 ([x], [y]) 作为缩放中心。
     *
     * 分块页交给 [BigImageView.zoomNextAt]（内部复用同一套 [ImageViewerZoomPolicy] 档位）；
     * 其余情况走原来的 PhotoView 逻辑，一个字没改。
     *
     * 缩放动画落地后再校验一次：部分 ROM 上图片刚好在双击动画期间加载完成，PhotoView 的
     * `updateBaseMatrix()` 会把缩放矩阵重置回原比例，观感就是「抖两下、还是原样」（反馈 #14 /
     * #54 的原话）。发现被重置就不再走动画、直接钉到目标倍率，避免反复抖动。
     */
    private fun zoomAt(position: Int, x: Float, y: Float) {
        val page = pages.getOrNull(position) ?: return
        val big = page.bigView
        if (page.mode == PAGE_BIG && big != null && big.hasImage() && big.zoomNextAt(x, y)) {
            return
        }
        if (page.mode != PAGE_PHOTO) return
        val photoView = page.photoView
        val target = ImageViewerZoomPolicy.nextScale(
            photoView.scale,
            photoView.minimumScale,
            photoView.mediumScale,
            photoView.maximumScale
        )
        photoView.setScale(target, x, y, true)
        photoView.postDelayed({
            if (photoView.isAttachedToWindow &&
                abs(photoView.scale - target) > ImageViewerZoomPolicy.EPSILON
            ) {
                Logu.d(
                    "image-zoom",
                    "双击缩放被重置（current=${photoView.scale}, target=$target），补钉一次"
                )
                photoView.setScale(target, x, y, false)
            }
        }, ZOOM_VERIFY_DELAY_MS)
    }

    private companion object {
        /** 空实现：双击已由 [PhotoViewpager] 统一处理，这里只把 PhotoView 自带的那套挡掉。 */
        val NOOP_DOUBLE_TAP_LISTENER = object : GestureDetector.OnDoubleTapListener {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean = false

            override fun onDoubleTap(e: MotionEvent): Boolean = true

            override fun onDoubleTapEvent(e: MotionEvent): Boolean = false
        }

        /** 缩放动画时长是 200ms（PhotoView 默认），校验点取动画结束后一点点。 */
        const val ZOOM_VERIFY_DELAY_MS = 240L
    }
}
