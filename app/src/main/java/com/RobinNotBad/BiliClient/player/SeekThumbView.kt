package com.RobinNotBad.BiliClient.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import com.RobinNotBad.BiliClient.util.player.VideoShotGrid

/**
 * 拖动进度条时的缩略图预览。
 *
 * 服务端给的是一张拼版（10×10 格），每次拖动只需要其中**一格**，所以这里不做任何
 * Bitmap 分配：把拼版整张留在内存里，`onDraw` 里用 `drawBitmap(src→dst)` 直接画那一格。
 * 拖动是高频事件（每帧都可能变），若每帧 `Bitmap.createBitmap` 裁一张，
 * 2GB 内存的表上很快就 GC 抖动。
 *
 * <p>图没到时画一块半透明底衬而不是留白——否则遮罩上会出现一个「洞」，
 * 看着像渲染错而不是「正在加载」。
 */
class SeekThumbView(context: Context) : View(context) {

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 }
    private val src = Rect()
    private val dst = RectF()
    private val clipPath = Path()

    private var sheet: Bitmap? = null

    /** 圆角半径（px）；由控制器按屏幕尺寸设进来。 */
    var cornerRadius: Float = 0f

    /**
     * 换成「拼版里的这一格」。任一项为 null 时退化成底衬。
     *
     * 会做一次越界保护：拼版实际像素可能比 `img_x_size × img_x_len` 算出来的小
     * （服务端缩放过的老视频见过），越界时按实际范围裁，不抛也不画黑边。
     */
    fun show(source: Bitmap?, crop: VideoShotGrid.CropRect?) {
        if (source == null || crop == null) {
            if (sheet != null) {
                sheet = null
                invalidate()
            }
            return
        }
        src.set(crop.left, crop.top, crop.right, crop.bottom)
        if (!src.intersect(0, 0, source.width, source.height) || src.isEmpty) {
            if (sheet != null) {
                sheet = null
                invalidate()
            }
            return
        }
        sheet = source
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        clipPath.reset()
        clipPath.addRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        val source = sheet
        if (source == null) {
            canvas.drawRect(0f, 0f, w, h, backPaint)
        } else {
            dst.set(0f, 0f, w, h)
            canvas.drawBitmap(source, src, dst, bitmapPaint)
        }
        canvas.restore()
    }
}
