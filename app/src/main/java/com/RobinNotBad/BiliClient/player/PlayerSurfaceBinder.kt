package com.RobinNotBad.BiliClient.player

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import com.RobinNotBad.BiliClient.util.Logu

/**
 * 可用的渲染目标，用于把 IjkMediaPlayer 挂到具体 surface 上。
 */
sealed class SurfaceTarget {
    /** SurfaceView 的 holder，对应 `IjkMediaPlayer.setDisplay(SurfaceHolder)`。 */
    class Holder(val holder: SurfaceHolder) : SurfaceTarget()

    /** TextureView 的 Surface，对应 `IjkMediaPlayer.setSurface(Surface)`。 */
    class Texture(val surface: Surface) : SurfaceTarget()
}

/**
 * Surface 就绪分发器：**事件驱动**，取代原来"每 200ms 轮询一次 surface 是否就绪"的 `Timer`。
 *
 * 原实现（`PlayerActivity.setDisplay()`）有两处实实在在的代价：
 *  1. 每次调用都要新建一个 `java.util.Timer` 线程 —— 而 `setDisplay()` 在首播、切清晰度、
 *     切分页、切听视频模式时都会走，手表端线程创建/销毁的开销不可忽略；
 *  2. surface 尚未就绪时要等满一个轮询周期（最长 200ms）才会调 `prepareAsync`，
 *     **直接拖慢首帧**。
 *
 * 本类改用 `TextureView.SurfaceTextureListener` / `SurfaceHolder.Callback`：surface 已就绪时
 * **同步立即**回调（不经过任何延时），未就绪时等回调，全程不创建线程。
 *
 * 两个"就绪"回调的语义必须区分清楚：
 *  - [onReadyForPrepare]：调用方 [await] 过，说明正等着开播，需要**挂载 surface 并开始 prepare**；
 *  - [onSurfaceReattached]：surface 在非等待状态下重建（例如退到后台再回来），
 *    播放器已经 prepare 过了，只需把 surface **重新挂上**，绝不能再 prepare 一次。
 *
 * 线程约定：所有回调与 [await] 都保证在主线程执行（[await] 若在子线程调用会自动投递到主线程），
 * 因为 View 状态只能在主线程读写 —— 而 `PlayerActivity.setDisplay()` 确实会从后台线程被调用。
 */
class PlayerSurfaceBinder(
    private val onReadyForPrepare: (SurfaceTarget) -> Unit,
    private val onSurfaceReattached: (SurfaceTarget) -> Unit,
    private val onSurfaceLost: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var textureView: TextureView? = null
    private var surfaceView: SurfaceView? = null

    /** 是否正在等 surface 就绪（即调用过 [await] 且当时未就绪）。只在主线程读写。 */
    private var awaiting = false

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
            deliver(SurfaceTarget.Texture(Surface(st)))
        }

        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
            Logu.v("surfacetexture", "sizechanged")
        }

        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
            onSurfaceLost()
            return true
        }

        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            deliver(SurfaceTarget.Holder(holder))
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Logu.v("surface", "Holder没了")
            onSurfaceLost()
        }
    }

    /** 绑定 TextureView（取代手工设置 `surfaceTextureListener`）。 */
    fun bindTextureView(view: TextureView) {
        textureView = view
        view.surfaceTextureListener = textureListener
    }

    /** 绑定 SurfaceView（取代手工 `holder.addCallback`）。 */
    fun bindSurfaceView(view: SurfaceView) {
        surfaceView = view
        view.holder.addCallback(surfaceCallback)
    }

    /**
     * 请求在 surface 就绪时回调 [onReadyForPrepare]。
     * surface 当前已就绪则**同步立即**回调；否则登记等待，由 surface 回调触发。
     */
    fun await() {
        if (Looper.myLooper() == Looper.getMainLooper()) awaitOnMain()
        else mainHandler.post { awaitOnMain() }
    }

    /** 当前 surface 是否可用。 */
    fun isReady(): Boolean = currentTarget() != null

    /** 解绑并停止回调，供 `onDestroy` 调用。 */
    fun release() {
        awaiting = false
        textureView?.surfaceTextureListener = null
        surfaceView?.holder?.removeCallback(surfaceCallback)
        mainHandler.removeCallbacksAndMessages(null)
        textureView = null
        surfaceView = null
    }

    private fun awaitOnMain() {
        val target = currentTarget()
        if (target != null) {
            awaiting = false
            onReadyForPrepare(target)
        } else {
            awaiting = true
        }
    }

    /** surface 变为可用时的统一出口，保证回调线程与语义一致。 */
    private fun deliver(target: SurfaceTarget) {
        if (awaiting) {
            awaiting = false
            onReadyForPrepare(target)
        } else {
            onSurfaceReattached(target)
        }
    }

    /** 取当前可用的渲染目标；不可用返回 null。只在主线程调用。 */
    private fun currentTarget(): SurfaceTarget? {
        textureView?.let { view ->
            val st = view.surfaceTexture
            return if (view.isAvailable && st != null) SurfaceTarget.Texture(Surface(st)) else null
        }
        surfaceView?.let { view ->
            val holder = view.holder
            if (holder.surface?.isValid == true) return SurfaceTarget.Holder(holder)
        }
        return null
    }
}
