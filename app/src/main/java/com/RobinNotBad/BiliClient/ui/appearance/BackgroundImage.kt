package com.RobinNotBad.BiliClient.ui.appearance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.UiText
import java.io.File

/**
 * 自定义背景图（外观体系里唯一新增的「看图」配置项）。
 *
 * 设计取舍：
 *
 * - **只存一份**：选中的图在 [install] 里拷进 `filesDir/appearance/background.img`，不留 URI。
 *   本类**不申请存储权限**：读图权限由内置选图页（`activity/ImagePickerActivity.kt`）按需申请，
 *   拷完就不再需要原 URI。不用 cacheDir 是因为「设置 → 存储空间」页允许清临时文件，
 *   把用户精挑的图放那儿会被一键清掉。
 * - **解码只做一次**：[drawable] 里有「路径+大小+修改时间」做键的内存缓存，并且用
 *   `inSampleSize` 按最长边 [DECODE_MAX_DIM] 降采样、`RGB_565` 省内存。没有这张缓存，
 *   每次进 Activity 都会把原图整张解进内存，手表端很容易 OOM。
 * - **画在窗口背景上**：见 [ColorScheme.applyWindowTheme] 的接入点。`android.R.id.content`
 *   容器上有用户自定义边距（[com.RobinNotBad.BiliClient.activity.base.BaseActivity.applyUserPadding]），
 *   背景必须铺在 DecorView 这一层，否则边距外圈会露出底色。
 *
 * 纯计算（尺寸校验、降采样倍数、覆盖矩形、蒙版色）都提成了 internal 纯函数，JVM 单测可覆盖；
 * Android 相关的只有安装（拷文件）与解码。
 */
object BackgroundImage {

    /** 背景图绝对路径。空串 = 未设置。键名统一收在 [SettingsKeys]，这里只做别名。 */
    const val KEY_PATH = SettingsKeys.UI_BACKGROUND_PATH

    /** 单张图上限 16MB。手表端再大就没意义了，也避免拷大文件把内部存储塞满。 */
    const val MAX_BYTES = 16L * 1024 * 1024

    /** 单边上限，超过直接拒（避免解码时整数溢出与离谱的内存申请）。 */
    const val MAX_DIMENSION = 8192

    /** 解码时最长边缩到这个值。手表屏幕通常不到 500px，1024 已经足够清晰。 */
    const val DECODE_MAX_DIM = 1024

    /** 适配方式：填满（等比放大、超出裁切）或适应（等比缩到完整可见、四周留底色）。 */
    const val KEY_FIT = SettingsKeys.UI_BACKGROUND_FIT
    const val FIT_COVER = "cover"
    const val FIT_CONTAIN = "contain"
    const val DEFAULT_FIT = FIT_COVER

    /**
     * 遮罩强度（百分比，0/25/50/75）。蒙版色在暗色下去压暗、亮色下去提亮。
     * 存字符串而不是整数：设置页的 `listChoose` 统一按字符串存值（见 SettingsAdapter.ListChooseHolder）。
     */
    const val KEY_SCRIM = SettingsKeys.UI_BACKGROUND_SCRIM
    const val DEFAULT_SCRIM = 50

    /** 设置页给出的档位，单测与设置页共用，避免两边各写一份。 */
    val SCRIM_PERCENTS = intArrayOf(0, 25, 50, 75)

    private const val DIR_NAME = "appearance"
    private const val FILE_NAME = "background.img"
    private const val TEMP_SUFFIX = ".tmp"
    private const val COPY_BUFFER = 64 * 1024

    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cachedBitmap: Bitmap? = null

    /** 当前是否设置了可用的背景图（只看文件在不在、非空；不校验图片能否解码，避免每次进页面都解码）。 */
    fun isEnabled(): Boolean {
        val path = currentPath()
        if (path.isEmpty()) return false
        val file = File(path)
        return file.isFile && file.length() > 0L
    }

    /** 已设置的绝对路径；未设置返回空串。 */
    fun currentPath(): String = SharedPreferencesUtil.getString(KEY_PATH, "")

    /** 当前适配方式（[FIT_COVER] / [FIT_CONTAIN]），非法值回落填满。 */
    fun currentFit(): String = normalizeFit(SharedPreferencesUtil.getString(KEY_FIT, DEFAULT_FIT))

    /** 当前遮罩强度（0..100），非法值回落 [DEFAULT_SCRIM]。 */
    fun currentScrimPercent(): Int = coerceScrimPercent(
        SharedPreferencesUtil.getString(KEY_SCRIM, DEFAULT_SCRIM.toString())
    )

    /** 落盘位置：`filesDir/appearance/background.img`。 */
    fun imageFile(context: Context): File = File(File(context.filesDir, DIR_NAME), FILE_NAME)

    /**
     * 把用户选中的图拷进私有目录并设为当前背景。
     *
     * @return null 表示成功；否则是给用户看的中文失败原因（走 [UiText] 原文回退）。
     * 与 [FontStyle.install] 一样是同步阻塞方法，调用方应放到后台线程。
     */
    fun install(context: Context, uri: Uri): String? {
        val target = imageFile(context)
        val dir = target.parentFile ?: return UiText.of("无法创建背景图目录")
        if (!dir.isDirectory && !dir.mkdirs()) return UiText.of("无法创建背景图目录")

        val temp = File(dir, FILE_NAME + TEMP_SUFFIX)
        temp.delete()

        var copied = 0L
        var oversize = false
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return UiText.of("无法读取该文件")
            input.use { ins ->
                val out = temp.outputStream()
                try {
                    val buffer = ByteArray(COPY_BUFFER)
                    while (true) {
                        val read = ins.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        copied += read
                        if (copied > MAX_BYTES) {
                            oversize = true
                            break
                        }
                        out.write(buffer, 0, read)
                    }
                } finally {
                    out.close()
                }
            }
        } catch (t: Throwable) {
            temp.delete()
            return UiText.fmt("复制图片失败：%1\$s", t.message ?: t.javaClass.simpleName)
        }

        if (oversize) {
            temp.delete()
            return UiText.fmt("图片不能超过 %1\$dMB", MAX_BYTES / 1024 / 1024)
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            BitmapFactory.decodeFile(temp.absolutePath, bounds)
        } catch (t: Throwable) {
            temp.delete()
            return UiText.of("无法识别该图片")
        }

        rejectReason(copied, bounds.outWidth, bounds.outHeight)?.let {
            temp.delete()
            return it
        }

        target.delete()
        if (!temp.renameTo(target)) {
            temp.delete()
            return UiText.of("保存背景图失败")
        }

        AppearanceManager.setBackgroundPath(target.absolutePath)
        invalidateCache()
        return null
    }

    /** 清除背景图：删文件 + 清偏好 + 丢解码缓存（由调用方负责重建界面）。 */
    fun clear(context: Context) {
        val file = imageFile(context)
        file.delete()
        File(file.parentFile, FILE_NAME + TEMP_SUFFIX).delete()
        invalidateCache()
        AppearanceManager.clearBackgroundPath()
    }

    /** 丢弃解码好的位图引用（不 recycle：窗口背景可能还拿着它画）。 */
    fun invalidateCache() {
        cachedKey = null
        cachedBitmap = null
    }

    /**
     * 给窗口背景用的 Drawable；没有可用背景图时返回 null（调用方走原来的纯色/光晕分支）。
     *
     * @param light 当前是亮色还是暗色，决定蒙版方向。
     * @param backgroundColor 主题背景色：`适应` 档位下图片没铺满的部分用它填。
     */
    fun drawable(light: Boolean, backgroundColor: Int): Drawable? {
        val path = currentPath()
        if (path.isEmpty()) return null
        val file = File(path)
        if (!file.isFile || file.length() <= 0L) return null
        val bitmap = loadBitmap(file) ?: return null
        return BackgroundDrawable(
            bitmap = bitmap,
            scrim = scrimColor(currentScrimPercent(), light),
            contain = currentFit() == FIT_CONTAIN,
            backgroundColor = backgroundColor
        )
    }

    private fun loadBitmap(file: File): Bitmap? {
        val key = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
        val cached = cachedBitmap
        if (cached != null && cachedKey == key && !cached.isRecycled) return cached

        return try {
            val sizeOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, sizeOptions)
            if (sizeOptions.outWidth <= 0 || sizeOptions.outHeight <= 0) return null

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(sizeOptions.outWidth, sizeOptions.outHeight, DECODE_MAX_DIM)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null
            cachedBitmap = bitmap
            cachedKey = key
            bitmap
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * 安装前的尺寸/大小校验；返回 null 表示通过，否则是拒绝原因。
     *
     * 纯函数（不碰 Android），原因文案走 [UiText] 原文回退。
     */
    fun rejectReason(sizeBytes: Long, width: Int, height: Int): String? {
        if (sizeBytes <= 0L) return UiText.of("文件为空")
        if (sizeBytes > MAX_BYTES) {
            return UiText.fmt("图片不能超过 %1\$dMB", MAX_BYTES / 1024 / 1024)
        }
        if (width <= 0 || height <= 0) return UiText.of("无法识别该图片")
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            return UiText.fmt("图片太大了（单边最多 %1\$d 像素）", MAX_DIMENSION)
        }
        return null
    }

    /**
     * `inSampleSize` 只能取 2 的幂：让解码后最长边尽量接近但不超过 [maxDim]。
     *
     * 例：4000×3000、maxDim=1024 → 4（解出 1000×750）；1024×768 → 1（原样）。
     */
    fun computeInSampleSize(srcWidth: Int, srcHeight: Int, maxDim: Int): Int {
        if (srcWidth <= 0 || srcHeight <= 0 || maxDim <= 0) return 1
        val longest = maxOf(srcWidth, srcHeight)
        var sample = 1
        while (longest / sample > maxDim && sample < 512) {
            sample *= 2
        }
        return sample
    }

    /**
     * 「覆盖」缩放的落点：等比放大到铺满 [viewWidth]×[viewHeight]，多余部分居中裁掉。
     * 返回相对 view 左上角的 `[left, top, width, height]`。
     */
    fun coverRect(bitmapWidth: Int, bitmapHeight: Int, viewWidth: Int, viewHeight: Int): FloatArray {
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return floatArrayOf(0f, 0f, 0f, 0f)
        }
        val scale = maxOf(viewWidth.toFloat() / bitmapWidth, viewHeight.toFloat() / bitmapHeight)
        return centeredRect(bitmapWidth, bitmapHeight, viewWidth, viewHeight, scale)
    }

    /**
     * 「适应」缩放的落点：等比缩到**完整可见**，四周留白由窗口背景色补。
     * 返回相对 view 左上角的 `[left, top, width, height]`。
     */
    fun containRect(bitmapWidth: Int, bitmapHeight: Int, viewWidth: Int, viewHeight: Int): FloatArray {
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return floatArrayOf(0f, 0f, 0f, 0f)
        }
        val scale = minOf(viewWidth.toFloat() / bitmapWidth, viewHeight.toFloat() / bitmapHeight)
        return centeredRect(bitmapWidth, bitmapHeight, viewWidth, viewHeight, scale)
    }

    /** 按适配方式取落点（内部入口，避免调用方各写一遍 if）。 */
    fun fitRect(
        bitmapWidth: Int,
        bitmapHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        fit: String
    ): FloatArray = if (normalizeFit(fit) == FIT_CONTAIN) {
        containRect(bitmapWidth, bitmapHeight, viewWidth, viewHeight)
    } else {
        coverRect(bitmapWidth, bitmapHeight, viewWidth, viewHeight)
    }

    private fun centeredRect(
        bitmapWidth: Int,
        bitmapHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        scale: Float
    ): FloatArray {
        val drawWidth = bitmapWidth * scale
        val drawHeight = bitmapHeight * scale
        return floatArrayOf(
            (viewWidth - drawWidth) / 2f,
            (viewHeight - drawHeight) / 2f,
            drawWidth,
            drawHeight
        )
    }

    /** 适配方式归一化：只认 [FIT_CONTAIN]，其余（含空/脏数据）一律回 [FIT_COVER]。 */
    fun normalizeFit(raw: String?): String = if (raw == FIT_CONTAIN) FIT_CONTAIN else FIT_COVER

    /** 遮罩字符串 → 档位百分比：非法值回落 [DEFAULT_SCRIM]，并夹到 0..100。 */
    fun coerceScrimPercent(raw: String?): Int {
        val parsed = raw?.trim()?.toIntOrNull() ?: DEFAULT_SCRIM
        return parsed.coerceIn(0, 100)
    }

    /** 遮罩不透明度：百分比 → 0..255。 */
    fun scrimAlpha(percent: Int): Int = percent.coerceIn(0, 100) * 255 / 100

    /** 半透明蒙版色：暗色压暗、亮色提亮，透明度由档位决定。 */
    fun scrimColor(percent: Int, light: Boolean): Int {
        val rgb = if (light) 0xFFFFFF else 0x000000
        return (scrimAlpha(percent) shl 24) or rgb
    }

    /**
     * 背景 Drawable：bitmap 按适配方式铺在 bounds 上，再盖一层 [scrim] 让前景文字可读。
     *
     * 不参与 padding / 触摸：窗口背景本身不接收触摸事件，内容容器在
     * [ColorScheme.applyWindowTheme] 里被设成透明，所以正文列表的点击不受影响。
     */
    private class BackgroundDrawable(
        private val bitmap: Bitmap,
        private val scrim: Int,
        private val contain: Boolean,
        private val backgroundColor: Int
    ) : Drawable() {

        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val dst = RectF()

        override fun draw(canvas: Canvas) {
            if (bitmap.isRecycled) return
            val viewBounds = bounds
            if (viewBounds.width() <= 0 || viewBounds.height() <= 0) return

            // 适应档位下图片不满屏，先铺主题底色，避免露出一条上下/左右的旧窗口背景。
            if (contain) canvas.drawColor(backgroundColor)

            val rect = BackgroundImage.fitRect(
                bitmap.width, bitmap.height,
                viewBounds.width(), viewBounds.height(),
                if (contain) BackgroundImage.FIT_CONTAIN else BackgroundImage.FIT_COVER
            )
            dst.set(
                viewBounds.left + rect[0],
                viewBounds.top + rect[1],
                viewBounds.left + rect[0] + rect[2],
                viewBounds.top + rect[1] + rect[3]
            )
            canvas.drawBitmap(bitmap, null, dst, paint)

            if (Color.alpha(scrim) > 0) canvas.drawColor(scrim)
        }

        @Deprecated("Drawable#setAlpha 对窗口背景无意义，这里保持不透明绘制")
        override fun setAlpha(alpha: Int) = Unit

        @Deprecated("Drawable#setColorFilter 对窗口背景无意义")
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("窗口背景总是半透明合成")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
