package com.RobinNotBad.BiliClient.activity

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ImageButton
import android.widget.TextView
import androidx.viewpager.widget.ViewPager
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.viewpager.ViewPagerViewAdapter
import com.RobinNotBad.BiliClient.ui.widget.PhotoViewpager
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.Target
import com.github.chrisbanes.photoview.PhotoView
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

class ImageViewerActivity : BaseActivity() {

    /**
     * 看图时左右翻页、放大后拖动都是横向手势，手表的系统右滑返回会把它们抢成退出（反馈 #38）。
     * 清单里虽然写了 `Theme.NoSwipe`，但 `BaseActivity.onCreate` 会按用户主题重新 `setTheme`
     * 把它覆盖掉，所以必须走这个钩子。退出靠返回键 / 表冠键。
     */
    override val forceNoSwipeTheme: Boolean
        get() = true

    /** 与 ViewPager 的页序一一对应，双击时按 [PhotoViewpager.currentItem] 取当前页。 */
    private val photoViews = ArrayList<PhotoView>()

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

        for (i in imageList.indices) {
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
            photoViews.add(photoView)
        }

        // 26.10.27 性能：原来 onCreate 里一次性把**所有**图按原尺寸（SIZE_ORIGINAL）解码，
        // 九宫格动态就是 9 张 1024w ARGB 位图同时进内存，手表上容易 OOM（而且 Glide 的异步 OOM
        // 根本不会落到这里的 catch）。现在只加载当前页和左右各一页，翻页时再补。
        val loaded = BooleanArray(imageList.size)
        fun loadAround(position: Int) {
            for (i in (position - 1)..(position + 1)) {
                if (i !in imageList.indices || loaded[i]) continue
                loaded[i] = true
                try {
                    Glide.with(this).asDrawable()
                        .load(GlideUtil.url_zoom(imageList[i]))
                        .transition(GlideUtil.getTransitionOptions())
                        .override(Target.SIZE_ORIGINAL)
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(photoViews[i])
                } catch (e: OutOfMemoryError) {
                    MsgUtil.showMsg(UiText.of("超出内存，加载失败"))
                } catch (e: Exception) {
                    MsgUtil.err(UiText.of("图片查看"), e)
                }
            }
        }
        loadAround(0)

        val vpiAdapter = ViewPagerViewAdapter(photoViews)

        viewPager.adapter = vpiAdapter

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
            }

            override fun onPageScrollStateChanged(state: Int) {
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // 从下载页回来（它会自动结束）后重新允许点下载
        downloadLaunched = false
    }

    /**
     * 对第 [position] 张图做一次双击缩放，触点 ([x], [y]) 作为缩放中心。
     *
     * 缩放动画落地后再校验一次：部分 ROM 上图片刚好在双击动画期间加载完成，PhotoView 的
     * `updateBaseMatrix()` 会把缩放矩阵重置回原比例，观感就是「抖两下、还是原样」（反馈 #14 /
     * #54 的原话）。发现被重置就不再走动画、直接钉到目标倍率，避免反复抖动。
     */
    private fun zoomAt(position: Int, x: Float, y: Float) {
        val photoView = photoViews.getOrNull(position) ?: return
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
