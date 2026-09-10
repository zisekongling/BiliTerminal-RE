package com.RobinNotBad.BiliClient.player

import android.content.Context
import android.view.SurfaceView
import android.view.TextureView
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer

/**
 * 播放内核：两个播放器（普通视频 / 短视频）共用的唯一播放层。
 *
 * 设计约束（合并两个播放器时必须同时满足）：
 *  1. **可多实例**：短视频的 ViewPager2 会同时持有多个播放器，所以本类不是单例；
 *  2. **不依赖 Activity**：只接受 `Context`（用 applicationContext 之外的也只用于 setDataSource），
 *     不持有 Activity 引用；View 通过 [attachTextureView]/[attachSurfaceView] 传入，[release] 时解绑；
 *  3. **回调统一在主线程**：surface 回调由 [PlayerSurfaceBinder] 保证，进度轮询跑在 Main 协程，
 *     IJK 的事件回调则运行在创建 [IjkMediaPlayer] 的线程（本类要求在有 Looper 的线程创建，
 *     见 [createPlayer] 的注释），调用方不需要自己 runOnUiThread；
 *  4. **幂等释放**：[release] 可重复调用。
 *
 * 与 [IjkPlayerBridge] 的区别：本类额外支持 SurfaceView 模式、外部音频轨道所在页面的
 * "重载保留进度"（[reload]），并对外提供 [onPosition] 高频回调，避免调用方为了拿一个
 * 播放位置去订阅整个 state。
 */
class VideoPlayerCore(
    /** 播放出错回调（主线程）。frameworkErr / 描述信息 */
    private val onError: (Int, String) -> Unit = { _, _ -> },
    /** 准备完成回调（主线程） */
    private val onPrepared: () -> Unit = {},
    /** 播放结束回调（主线程） */
    private val onCompletion: () -> Unit = {},
    /** 缓冲开始/结束回调（主线程） */
    private val onBufferingChange: (Boolean) -> Unit = {},
    /** 播放进度回调（主线程，约 250ms 一次，只在与上次不同的整毫秒值上回调）。(位置ms, 时长ms) */
    private val onPosition: ((Long, Long) -> Unit)? = null
) {
    private var mediaPlayer: IjkMediaPlayer? = null
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** surface 就绪分发器：已就绪同步回调，未就绪等回调，不使用轮询 */
    private val surfaceBinder = PlayerSurfaceBinder(
        onReadyForPrepare = { target ->
            attachSurface(target)
            // surface 到位后才起播，避免 IJK 先准备好却无处可画
            if (_state.value.isPrepared) start() else prepare()
        },
        onSurfaceReattached = { target ->
            // surface 重建（退后台再回来）：播放器已 prepare 过，只重新挂上并回到原位，绝不重复 prepare
            attachSurface(target)
            val pos = lastKnownPosition
            if (_state.value.isPrepared && pos > 0) mediaPlayer?.seekTo(pos)
        },
        onSurfaceLost = { detachSurface() }
    )

    /** 最近一次已知位置，用于 surface 重建后回到原处 */
    private var lastKnownPosition: Long = 0L

    /** 当前要播放的地址；surface 就绪时用它起播 */
    private var pendingUrl: String? = null
    private var pendingHeaders: Map<String, String>? = null
    private var pendingPosition: Long = 0L

    /** 是否已释放 */
    private var released = false

    // ---------------------------------------------------------------- 创建与释放

    /**
     * 创建（或复用）底层播放器。
     *
     * **必须在有 Looper 的线程调用**（通常是主线程）：`IjkMediaPlayer` 的事件会分发到
     * 创建它的线程的 Looper，若在 IO 线程创建，回调就会在 IO 线程触发。
     *
     * @param reuse 为 true 时复用已有实例（只 `reset()`），省掉一次 native 播放器创建，
     *              用于切清晰度/切分页这类高频重建场景。
     */
    fun createPlayer(context: Context, options: List<IjkOption>? = null, reuse: Boolean = false) {
        val exist = mediaPlayer
        if (reuse && exist != null) {
            try {
                exist.reset()
            } catch (e: Exception) {
                Logu.e(TAG, "复用播放器 reset 失败，改为重建: ${e.message}")
                try {
                    exist.release()
                } catch (_: Exception) {}
                mediaPlayer = null
            }
        } else {
            releasePlayerOnly()
        }

        if (mediaPlayer == null) {
            IjkMediaPlayer.loadLibrariesOnce(null)
            mediaPlayer = IjkMediaPlayer()
        }

        val player = mediaPlayer!!
        applyOptions(player, options)
        bindListeners(player)
        _state.update { it.copy(isPrepared = false, isPlaying = false, errorCode = 0, errorMessage = null) }
    }

    private fun applyOptions(player: IjkMediaPlayer, options: List<IjkOption>?) {
        if (options == null) {
            setOption(player, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1L)
            setOption(player, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0L)
            setOption(player, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", IjkMediaPlayer.SDL_FCC_RV32.toLong())
            setOption(player, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L)
            setOption(player, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0L)
            setOption(player, IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0L)
            setOption(player, IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "fastseek")
            setOption(player, IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48L)
            setOption(player, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1L)
            setOption(player, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "play-audio", 1L)
            setOption(player, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "play-video", 1L)
            // B 站 CDN 会拦截 FFmpeg 默认 UA（Lavf/x.x.x，被识别为下载工具特征，返回 403），
            // 必须伪装成浏览器 UA。注意 app 端音频流链接不能带 Referer，这里不设置 headers。
            setOption(player, IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", NetWorkUtil.USER_AGENT_WEB)
        } else {
            for (opt in options) {
                when (val v = opt.value) {
                    is Long -> player.setOption(opt.category, opt.name, v)
                    is Int -> player.setOption(opt.category, opt.name, v.toLong())
                    is String -> player.setOption(opt.category, opt.name, v)
                }
            }
        }
    }

    private fun setOption(player: IjkMediaPlayer, category: Int, name: String, value: Any) {
        when (value) {
            is Long -> player.setOption(category, name, value)
            is Int -> player.setOption(category, name, value.toLong())
            is String -> player.setOption(category, name, value)
        }
    }

    private fun bindListeners(player: IjkMediaPlayer) {
        player.setOnPreparedListener { mp ->
            val duration = mp.duration
            val vidW = mp.videoWidth
            val vidH = mp.videoHeight
            _state.update {
                it.copy(
                    isPrepared = true,
                    duration = duration,
                    videoWidth = vidW,
                    videoHeight = vidH,
                    errorCode = 0,
                    errorMessage = null
                )
            }
            onPrepared()
        }

        player.setOnCompletionListener {
            stopProgressTracking()
            _state.update { it.copy(isPlaying = false) }
            onCompletion()
        }

        player.setOnBufferingUpdateListener { _, percent ->
            _state.update { it.copy(bufferedPercent = percent) }
        }

        player.setOnInfoListener { _, what, _ ->
            when (what) {
                IMediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                    _state.update { it.copy(isBuffering = true) }
                    onBufferingChange(true)
                }
                IMediaPlayer.MEDIA_INFO_BUFFERING_END,
                IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                    _state.update { it.copy(isBuffering = false) }
                    onBufferingChange(false)
                }
            }
            true
        }

        player.setOnErrorListener { _, frameworkErr, implErr ->
            val msg = "播放错误 [framework:$frameworkErr, impl:$implErr]"
            stopProgressTracking()
            _state.update { it.copy(errorCode = frameworkErr, errorMessage = msg, isPlaying = false) }
            onError(frameworkErr, msg)
            true
        }

        player.setOnVideoSizeChangedListener { _, width, height, _, _ ->
            if (width > 0 && height > 0) {
                _state.update { it.copy(videoWidth = width, videoHeight = height) }
            }
        }
    }

    /** 只释放底层播放器，保留 View 绑定与状态对象。 */
    private fun releasePlayerOnly() {
        stopProgressTracking()
        try {
            // reset() 已能把播放器退回未初始化状态；stop() 是多余的等待型调用
            mediaPlayer?.reset()
        } catch (_: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    /** 幂等释放：解绑 surface、停掉轮询、释放播放器。重复调用是 no-op。 */
    fun release() {
        if (released) return
        released = true
        surfaceBinder.release()
        releasePlayerOnly()
        job.cancel()
        _state.update { PlayerState() }
    }

    // ---------------------------------------------------------------- Surface 绑定

    /** 绑定 TextureView 作为渲染目标。 */
    fun attachTextureView(view: TextureView) {
        surfaceBinder.bindTextureView(view)
    }

    /** 绑定 SurfaceView 作为渲染目标。 */
    fun attachSurfaceView(view: SurfaceView) {
        surfaceBinder.bindSurfaceView(view)
    }

    private fun attachSurface(target: SurfaceTarget) {
        val player = mediaPlayer ?: return
        when (target) {
            is SurfaceTarget.Holder -> player.setDisplay(target.holder)
            is SurfaceTarget.Texture -> player.setSurface(target.surface)
        }
    }

    private fun detachSurface() {
        val player = mediaPlayer ?: return
        player.setDisplay(null)
        player.setSurface(null)
    }

    // ---------------------------------------------------------------- 数据源与播放控制

    /**
     * 设置数据源。http(s) 走带 headers 的重载，其余（本地文件/缓存路径）按路径处理。
     * 是否自动起播由调用方在 [onPrepared] 回调里决定（普通播放器要按用户上一状态决定，
     * 短视频要按页面是否活跃决定），内核不替调用方做这个决定。
     */
    fun setSource(url: String, headers: Map<String, String>? = null, positionMs: Long = 0L) {
        pendingUrl = url
        pendingHeaders = headers
        pendingPosition = positionMs
        lastKnownPosition = positionMs
    }

    /**
     * 开始准备：若 surface 已就绪则立即执行，否则等 surface 回调后自动执行。
     * 与 [PlayerSurfaceBinder.await] 配合，取代"轮询等 surface"的写法。
     */
    fun prepare() {
        if (surfaceBinder.isReady()) {
            doPrepare()
        } else {
            surfaceBinder.await()
        }
    }

    private fun doPrepare() {
        val player = mediaPlayer ?: return
        val url = pendingUrl ?: return

        try {
            val headers = pendingHeaders
            if (headers.isNullOrEmpty()) player.setDataSource(url) else player.setDataSource(url, headers)
        } catch (e: Exception) {
            Logu.e(TAG, "setDataSource 失败: ${e.message}")
            onError(-1, "setDataSource 失败: ${e.message}")
            return
        }

        if (pendingPosition > 0) {
            player.seekTo(pendingPosition)
        }
        player.prepareAsync()
    }

    fun start() {
        val player = mediaPlayer ?: return
        player.start()
        _state.update { it.copy(isPlaying = true) }
        startProgressTracking()
    }

    fun pause() {
        mediaPlayer?.pause()
        _state.update { it.copy(isPlaying = false) }
        stopProgressTracking()
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else start()
    }

    fun seekTo(positionMs: Long) {
        lastKnownPosition = positionMs
        mediaPlayer?.seekTo(positionMs)
        _state.update { it.copy(currentPosition = positionMs) }
    }

    fun setSpeed(speed: Float) {
        mediaPlayer?.setSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    fun setVolume(left: Float, right: Float) {
        mediaPlayer?.setVolume(left, right)
    }

    fun setLooping(looping: Boolean) {
        mediaPlayer?.isLooping = looping
    }

    fun setScreenOnWhilePlaying(screenOn: Boolean) {
        mediaPlayer?.setScreenOnWhilePlaying(screenOn)
    }

    /** 当前播放位置（直接读底层，不进 state，供手势等即时判断使用）。 */
    fun currentPosition(): Long = mediaPlayer?.currentPosition ?: lastKnownPosition

    /** 时长（来自 state，prepare 完成后有效）。 */
    fun duration(): Long = _state.value.duration

    /** 音频会话 id，供音频可视化/音效使用。 */
    fun audioSessionId(): Int = mediaPlayer?.audioSessionId ?: 0

    /**
     * 换源重载（切清晰度、切分页、切本地分P都走这里）：
     * 复用同一个 [IjkMediaPlayer] 实例，只 `reset()` + 重新设 options + 重新设数据源，
     * 省掉一次 native 播放器创建与解码器初始化，直接缩短切换后的起播时间。
     *
     * @param keepPosition 是否把当前位置带到新源上（切清晰度需要，切分页不需要）
     */
    fun reload(
        context: Context,
        url: String,
        headers: Map<String, String>? = null,
        keepPosition: Boolean = false,
        options: List<IjkOption>? = null
    ) {
        val position = if (keepPosition) currentPosition() else 0L
        _state.update { it.copy(isPrepared = false, isPlaying = false) }
        stopProgressTracking()
        createPlayer(context, options, reuse = true)
        setSource(url, headers, position)
        prepare()
    }

    // ---------------------------------------------------------------- 进度轮询

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    val pos = player.currentPosition
                    val dur = _state.value.duration
                    lastKnownPosition = pos
                    if (pos != _state.value.currentPosition) {
                        _state.update { it.copy(currentPosition = pos) }
                        onPosition?.invoke(pos, dur)
                    }
                }
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    companion object {
        private const val TAG = "VideoPlayerCore"
        private const val PROGRESS_INTERVAL_MS = 250L
    }
}
