package com.RobinNotBad.BiliClient.player

import android.content.Context
import android.view.SurfaceView
import android.view.TextureView
import android.widget.FrameLayout
import com.RobinNotBad.BiliClient.player.IjkPlayerBridge
import com.RobinNotBad.BiliClient.player.DanmakuManager
import com.RobinNotBad.BiliClient.player.PlayerControlDelegate
import com.RobinNotBad.BiliClient.player.PlayerState
import master.flame.danmaku.ui.widget.DanmakuView
import tv.danmaku.ijk.media.player.IjkMediaPlayer

class PlayerIntegrator(
    private val context: Context,
    private val container: FrameLayout,
    onError: (Int, String) -> Unit = { _, _ -> },
    onToggleFullscreen: () -> Unit = {},
    onRequestQualityChange: (Int) -> Unit = {}
) {
    val playerBridge = IjkPlayerBridge(onError)
    val controlDelegate = PlayerControlDelegate(
        context, playerBridge,
        onToggleFullscreen = onToggleFullscreen,
        onRequestQualityChange = onRequestQualityChange
    )

    private var surfaceView: SurfaceView? = null
    private var textureView: TextureView? = null
    private var danmakuView: DanmakuView? = null
    var danmakuManager: DanmakuManager? = null
        private set

    val playerState: PlayerState get() = playerBridge.state.value
    val isPlaying get() = playerBridge.isPlaying

    fun setupSurfaceView(): SurfaceView {
        val sv = SurfaceView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            holder.addCallback(object : android.view.SurfaceHolder.Callback {
                override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                    playerBridge.setDisplay(holder)
                }
                override fun surfaceChanged(holder: android.view.SurfaceHolder, fmt: Int, w: Int, h: Int) {}
                override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {}
            })
        }
        surfaceView = sv
        container.addView(sv, 0)
        return sv
    }

    fun setupTextureView(): TextureView {
        val tv = TextureView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                    playerBridge.setSurface(android.view.Surface(st))
                }
                override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                    playerBridge.setSurface(null)
                    return true
                }
                override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
            }
        }
        textureView = tv
        container.addView(tv, 0)
        return tv
    }

    fun setupDanmakuView(): DanmakuView {
        val dv = DanmakuView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            bringToFront()
        }
        danmakuView = dv
        container.addView(dv, 1)
        danmakuManager = DanmakuManager(dv) { playerBridge.state.value.currentPosition }
        danmakuManager?.init()
        return dv
    }

    fun loadVideo(url: String) {
        playerBridge.createPlayer(context)
        if (url.startsWith("http")) {
            playerBridge.setDataSource(url)
        } else {
            playerBridge.setDataSource(context, android.net.Uri.parse(url))
        }
        playerBridge.prepareAsync()
    }

    fun play() = playerBridge.start()
    fun pause() = playerBridge.pause()
    fun seekTo(pos: Long) = playerBridge.seekTo(pos)
    fun setSpeed(speed: Float) = playerBridge.setSpeed(speed)

    fun onResume() {
        danmakuManager?.resume()
        if (playerBridge.state.value.isPrepared) {
            playerBridge.start()
        }
    }

    fun onPause() {
        danmakuManager?.pause()
        playerBridge.pause()
    }

    fun release() {
        controlDelegate.release()
        danmakuManager?.release()
        playerBridge.release()
        surfaceView = null
        textureView = null
        danmakuView = null
        danmakuManager = null
    }
}