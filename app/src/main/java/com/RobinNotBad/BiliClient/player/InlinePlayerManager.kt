package com.RobinNotBad.BiliClient.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.GestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.graphics.drawable.AnimationDrawable
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.controller.IDanmakuView
import master.flame.danmaku.danmaku.loader.ILoader
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import master.flame.danmaku.danmaku.parser.IDataSource
import master.flame.danmaku.danmaku.parser.android.BiliDanmukuParser
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import java.io.IOException
import java.util.Timer
import java.util.TimerTask

class InlinePlayerManager(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val danmakuView: IDanmakuView,
    private val playBtn: ImageView,
    private val progressText: TextView,
    private val durationText: TextView,
    private val seekBar: SeekBar,
    private val loadingLayout: android.view.View,
    private val loadingIcon: ImageView,
    private val loadingText0: TextView,
    private val loadingText1: TextView,
    private val controlsLayout: android.view.View? = null,
    private val onVideoSizeChanged: ((videoWidth: Int, videoHeight: Int) -> Unit)? = null
) {

    private var ijkPlayer: IjkMediaPlayer? = null
    private var mContext: DanmakuContext? = null
    private var isPlaying = false
    private var isPrepared = false
    private var hasDanmaku = false
    private var videoUrl: String? = null
    private var audioUrl: String? = null
    private var danmakuUrl: String = ""
    private var progressTimer: Timer? = null
    private var mainHandler = Handler(Looper.getMainLooper())
    private var animLoading: AnimationDrawable? = null
    private var isDanmakuEnabled = true
    private var isControlsVisible = true
    private var wasPlayingBeforeBackground = false
    private var gestureDetector: GestureDetector? = null
    private var longPressTimer: Timer? = null
    private var isLongPressActive = false
    private var lastNormalSpeed = 1.0f
    private var currentSpeed = 1.0f

    init {
        initDanmaku()
        setupSurfaceView()
        setupSeekBar()
        setupGestureDetector()
        startLoading()
    }

    private fun initDanmaku() {
        mContext = DanmakuContext.create()
        mContext!!.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3f)
            .setDuplicateMergingEnabled(false)
            .setScrollSpeedFactor(1.2f)
            .setScaleTextSize(1.2f)
    }

    private fun setupSurfaceView() {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (!isPrepared && videoUrl != null) {
                    ijkPlayer?.setDisplay(holder)
                    ijkPlayer?.prepareAsync()
                } else if (isPrepared) {
                    ijkPlayer?.setDisplay(holder)
                    if (wasPlayingBeforeBackground) {
                        ijkPlayer?.start()
                        if (isDanmakuEnabled) {
                            danmakuView.resume()
                        }
                        isPlaying = true
                        playBtn.setImageResource(com.RobinNotBad.BiliClient.R.drawable.btn_player_pause)
                        startProgressTimer()
                        wasPlayingBeforeBackground = false
                    }
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                ijkPlayer?.setDisplay(null)
            }
        })
    }

    private fun setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                ijkPlayer?.let {
                    val position = (seekBar.progress.toLong() * it.duration) / 100
                    it.seekTo(position)
                }
            }
        })
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                togglePlay()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                activateLongPressSpeed()
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleControls()
                return true
            }

            override fun onDown(e: MotionEvent): Boolean {
                return true
            }
        })
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector?.onTouchEvent(event) ?: false
    }

    private fun activateLongPressSpeed() {
        if (!isPlaying || isLongPressActive) return
        isLongPressActive = true
        lastNormalSpeed = currentSpeed
        currentSpeed = 3.0f
        ijkPlayer?.setSpeed(3.0f)
        danmakuView.setSpeed(3.0f)
    }

    fun deactivateLongPressSpeed() {
        if (!isLongPressActive) return
        isLongPressActive = false
        currentSpeed = lastNormalSpeed
        ijkPlayer?.setSpeed(lastNormalSpeed)
        danmakuView.setSpeed(lastNormalSpeed)
    }

    private fun startLoading() {
        loadingLayout.visibility = android.view.View.VISIBLE
        loadingIcon.setImageResource(com.RobinNotBad.BiliClient.R.drawable.loading_tv_shaking)
        animLoading = loadingIcon.drawable as AnimationDrawable
        animLoading?.start()
        loadingText0.text = "加载中"
        loadingText1.text = "OwO"
    }

    private fun showBuffering() {
        if (!isPrepared || !isPlaying) return
        loadingLayout.visibility = android.view.View.VISIBLE
        if (animLoading?.isRunning != true) {
            animLoading?.start()
        }
        loadingText0.text = "正在缓冲"
        loadingText1.text = ""
    }

    private fun stopLoading() {
        loadingLayout.visibility = android.view.View.GONE
        animLoading?.stop()
    }

    private fun showLoadError(message: String = "加载失败") {
        loadingLayout.visibility = android.view.View.VISIBLE
        animLoading?.stop()
        loadingIcon.setImageDrawable(null)
        loadingText0.text = message
        loadingText1.text = "点击重试"
        loadingLayout.setOnClickListener {
            loadingLayout.setOnClickListener(null)
            if (videoUrl != null) {
                startLoading()
                initPlayer()
            }
        }
    }

    fun setVideoUrl(url: String, danmakuUrl: String = "", audioUrl: String? = null) {
        this.videoUrl = url
        this.audioUrl = audioUrl
        this.danmakuUrl = danmakuUrl
        initPlayer()
    }

    private fun initPlayer() {
        if (ijkPlayer != null) {
            ijkPlayer!!.release()
            ijkPlayer = null
        }

        ijkPlayer = IjkMediaPlayer().apply {
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec",
                if (SharedPreferencesUtil.getBoolean("player_codec", true)) 1 else 0)
            setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles",
                if (SharedPreferencesUtil.getBoolean("player_audio", false)) 1 else 0)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 4)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 50)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 1024 * 10)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "flush_packets")
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 2 * 1024 * 1024)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-buffer-size", 512 * 1024)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "buffer-duration", 500)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-buffer-duration", 100)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", NetWorkUtil.USER_AGENT_WEB)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-seekable", 1)
        }

        ijkPlayer?.setOnPreparedListener {
            isPrepared = true
            isPlaying = true
            ijkPlayer?.start()
            playBtn.setImageResource(com.RobinNotBad.BiliClient.R.drawable.btn_player_pause)
            durationText.text = formatDuration(ijkPlayer!!.duration)
            seekBar.max = 100
            startProgressTimer()
            stopLoading()
            if (hasDanmaku && isDanmakuEnabled) {
                danmakuView.start()
            }
        }

        ijkPlayer?.setOnCompletionListener {
            isPlaying = false
            playBtn.setImageResource(com.RobinNotBad.BiliClient.R.drawable.btn_player_play)
            stopProgressTimer()
        }

        ijkPlayer?.setOnErrorListener { _, what, extra ->
            Logu.e("InlinePlayer", "Error: $what, $extra")
            isPlaying = false
            playBtn.setImageResource(com.RobinNotBad.BiliClient.R.drawable.btn_player_play)
            stopProgressTimer()
            val errorMsg = when (what) {
                IMediaPlayer.MEDIA_ERROR_UNKNOWN -> "播放出错"
                IMediaPlayer.MEDIA_ERROR_SERVER_DIED -> "服务器连接失败"
                IMediaPlayer.MEDIA_ERROR_IO -> "网络连接失败"
                else -> "加载失败"
            }
            showLoadError(errorMsg)
            false
        }

        ijkPlayer?.setOnInfoListener { _, what, _ ->
            if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
                mainHandler.post {
                    showBuffering()
                    if (hasDanmaku && isPlaying) danmakuView.pause()
                }
            } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
                mainHandler.post {
                    stopLoading()
                    if (hasDanmaku && isDanmakuEnabled && isPlaying) danmakuView.resume()
                }
            }
            false
        }

        ijkPlayer?.setOnVideoSizeChangedListener { _, width, height, _, _ ->
            if (width > 0 && height > 0) {
                onVideoSizeChanged?.invoke(width, height)
            }
        }

        try {
            val headers = HashMap<String, String>()
            headers["Referer"] = "https://www.bilibili.com/"
            headers["Cookie"] = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")
            ijkPlayer?.setDataSource(videoUrl!!, headers)
            if (surfaceView.holder.surface.isValid) {
                ijkPlayer?.setDisplay(surfaceView.holder)
                ijkPlayer?.prepareAsync()
            }
        } catch (e: IOException) {
            Logu.e("InlinePlayer", "SetDataSource error: ${e.message}")
            showLoadError("视频地址无效")
        }

        if (danmakuUrl.isNotEmpty()) {
            loadDanmaku(danmakuUrl)
        }
    }

    private fun loadDanmaku(url: String) {
        com.RobinNotBad.BiliClient.util.CenterThreadPool.run {
            try {
                val loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI)
                loader.load(url)
                val parser = BiliDanmukuParser()
                val dataSource: IDataSource<*> = loader.dataSource
                parser.load(dataSource)
                mainHandler.post {
                    try {
                        val maxLinesPair = HashMap<Int, Int>()
                        maxLinesPair[BaseDanmaku.TYPE_SCROLL_RL] = 5
                        mContext?.setMaximumLines(maxLinesPair)
                        danmakuView.prepare(parser, mContext)
                        hasDanmaku = true
                    } catch (e: Exception) {
                        Logu.e("InlinePlayer", "Load danmaku error: ${e.message}")
                        hasDanmaku = false
                    }
                }
            } catch (e: Exception) {
                Logu.e("InlinePlayer", "Load danmaku error: ${e.message}")
                hasDanmaku = false
            }
        }
    }

    fun togglePlay() {
        ijkPlayer?.let { player ->
            if (isPlaying) {
                player.pause()
                danmakuView.pause()
                isPlaying = false
                playBtn.setImageResource(com.RobinNotBad.BiliClient.R.drawable.btn_player_play)
            } else {
                player.start()
                if (isDanmakuEnabled) {
                    danmakuView.resume()
                }
                isPlaying = true
                playBtn.setImageResource(com.RobinNotBad.BiliClient.R.drawable.btn_player_pause)
            }
        }
    }

    fun toggleDanmaku(): Boolean {
        isDanmakuEnabled = !isDanmakuEnabled
        if (isDanmakuEnabled && hasDanmaku && isPlaying) {
            danmakuView.resume()
        } else {
            danmakuView.pause()
        }
        return isDanmakuEnabled
    }

    fun isDanmakuEnabled(): Boolean = isDanmakuEnabled

    fun release() {
        stopProgressTimer()
        stopLoading()
        ijkPlayer?.let {
            it.release()
            ijkPlayer = null
        }
        danmakuView.release()
        isPlaying = false
        isPrepared = false
    }

    fun toggleControls() {
        isControlsVisible = !isControlsVisible
        controlsLayout?.visibility = if (isControlsVisible) android.view.View.VISIBLE else android.view.View.GONE
    }

    fun pause() {
        ijkPlayer?.let { player ->
            if (isPlaying) {
                wasPlayingBeforeBackground = true
                player.pause()
                danmakuView.pause()
                isPlaying = false
                playBtn.setImageResource(com.RobinNotBad.BiliClient.R.drawable.btn_player_play)
                stopProgressTimer()
            } else {
                wasPlayingBeforeBackground = false
            }
        }
    }

    fun resumePlayback() {
        if (wasPlayingBeforeBackground && isPrepared) {
            if (surfaceView.holder.surface.isValid) {
                ijkPlayer?.setDisplay(surfaceView.holder)
                ijkPlayer?.start()
                if (isDanmakuEnabled) {
                    danmakuView.resume()
                }
                isPlaying = true
                playBtn.setImageResource(com.RobinNotBad.BiliClient.R.drawable.btn_player_pause)
                startProgressTimer()
                wasPlayingBeforeBackground = false
            }
            // surface 无效时由 surfaceCreated 回调处理恢复
        }
    }

    private fun startProgressTimer() {
        stopProgressTimer()
        progressTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    mainHandler.post {
                        ijkPlayer?.let {
                            val current = it.currentPosition
                            val total = it.duration
                            if (total > 0) {
                                val progress = (current * 100 / total).toInt()
                                seekBar.progress = progress
                                progressText.text = formatDuration(current)
                            }
                        }
                    }
                }
            }, 0, 1000)
        }
    }

    private fun stopProgressTimer() {
        progressTimer?.cancel()
        progressTimer = null
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000).toInt()
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }

    fun isPlaying() = isPlaying

    fun isPrepared() = isPrepared

    fun getCurrentPosition(): Long {
        return ijkPlayer?.currentPosition ?: 0
    }

    fun seekTo(position: Long) {
        ijkPlayer?.seekTo(position)
    }

    fun addDanmaku(text: String) {
        if (!hasDanmaku) return
        val danmaku = mContext?.mDanmakuFactory?.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL) ?: return
        danmaku.text = text
        danmaku.padding = 5
        danmaku.priority = 1
        danmaku.textColor = android.graphics.Color.WHITE
        danmaku.textSize = 25f
        danmaku.time = danmakuView.currentTime + 100
        danmakuView.addDanmaku(danmaku)
    }
}