package com.RobinNotBad.BiliClient.player

import android.content.Context
import android.net.Uri
import android.view.Surface
import android.view.SurfaceHolder
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer

/** IjkMediaPlayer 单条选项，用于自定义播放器参数（category 见 IjkMediaPlayer.OPT_CATEGORY_*）。 */
data class IjkOption(
    val category: Int,
    val name: String,
    val value: Any // Long / Int / String
)

data class PlayerState(
    val isPrepared: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPercent: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val audioSessionId: Int = 0,
    val errorCode: Int = 0,
    val errorMessage: String? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0
)

class IjkPlayerBridge(
    private val onError: (Int, String) -> Unit = { _, _ -> }
) {
    private var mediaPlayer: IjkMediaPlayer? = null
    private var job: Job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var onPreparedCallback: (() -> Unit)? = null
    private var onCompletionCallback: (() -> Unit)? = null
    private var onBufferingUpdateCallback: ((Int) -> Unit)? = null

    /**
     * 创建播放器并设置选项。
     *
     * @param options 为 null 时使用默认选项集；传入非空列表时使用调用方提供的完整选项集
     *                （用于短视频等需要专门缓冲优化参数的场景，保证行为与调用方一致）。
     */
    fun createPlayer(context: Context, options: List<IjkOption>? = null) {
        release()
        IjkMediaPlayer.loadLibrariesOnce(null)

        val player = IjkMediaPlayer().apply {
            if (options == null) {
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1L)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0L)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", IjkMediaPlayer.SDL_FCC_RV32.toLong())
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0L)
                setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0L)
                setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "fastseek")
                setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48L)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1L)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "play-audio", 1L)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "play-video", 1L)
                // B 站 CDN 会拦截 FFmpeg 默认 UA（Lavf/x.x.x，被识别为下载工具特征，返回 403），
                // 必须伪装成浏览器 UA。注意 app 端音频流链接不能带 Referer，这里不设置 headers。
                setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", NetWorkUtil.USER_AGENT_WEB)
            } else {
                for (opt in options) {
                    when (val v = opt.value) {
                        is Long -> setOption(opt.category, opt.name, v)
                        is Int -> setOption(opt.category, opt.name, v.toLong())
                        is String -> setOption(opt.category, opt.name, v)
                    }
                }
            }

            setOnPreparedListener { mp ->
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
                onPreparedCallback?.invoke()
            }

            setOnCompletionListener {
                _state.update { it.copy(isPlaying = false) }
                onCompletionCallback?.invoke()
            }

            setOnBufferingUpdateListener { _, percent ->
                _state.update { it.copy(bufferedPercent = percent) }
                onBufferingUpdateCallback?.invoke(percent)
            }

            setOnInfoListener { _, what, extra ->
                when (what) {
                    IMediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                        _state.update { it.copy(isBuffering = true) }
                    }
                    IMediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                        _state.update { it.copy(isBuffering = false) }
                    }
                    IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                        _state.update { it.copy(isBuffering = false) }
                    }
                }
                true
            }

            setOnErrorListener { _, frameworkErr, implErr ->
                val msg = "播放错误 [framework:$frameworkErr, impl:$implErr]"
                _state.update { it.copy(errorCode = frameworkErr, errorMessage = msg, isPlaying = false) }
                onError(frameworkErr, msg)
                true
            }

            setOnVideoSizeChangedListener { _, width, height, _, _ ->
                if (width > 0 && height > 0) {
                    _state.update { it.copy(videoWidth = width, videoHeight = height) }
                }
            }
        }

        mediaPlayer = player
    }

    fun setOnPrepared(cb: () -> Unit) { onPreparedCallback = cb }
    fun setOnCompletion(cb: () -> Unit) { onCompletionCallback = cb }
    fun setOnBufferingUpdate(cb: (Int) -> Unit) { onBufferingUpdateCallback = cb }

    fun setDisplay(surfaceHolder: SurfaceHolder) {
        mediaPlayer?.setDisplay(surfaceHolder)
    }

    fun setSurface(surface: Surface?) {
        mediaPlayer?.setSurface(surface)
    }

    fun setDataSource(path: String) {
        mediaPlayer?.dataSource = path
    }

    fun setDataSource(path: String, headers: Map<String, String>) {
        mediaPlayer?.setDataSource(path, headers)
    }

    fun setDataSource(context: Context, uri: Uri) {
        mediaPlayer?.setDataSource(context, uri)
    }

    fun prepareAsync() {
        mediaPlayer?.prepareAsync()
    }

    fun start() {
        mediaPlayer?.start()
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
        mediaPlayer?.seekTo(positionMs)
        _state.update { it.copy(currentPosition = positionMs) }
    }

    fun setSpeed(speed: Float) {
        mediaPlayer?.setSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    val isPlaying: Boolean get() = _state.value.isPlaying && mediaPlayer?.isPlaying == true
    val currentPosition: Long get() = mediaPlayer?.currentPosition ?: 0L
    val duration: Long get() = _state.value.duration

    fun getAudioSessionId(): Int {
        return mediaPlayer?.audioSessionId ?: 0
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

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    val pos = player.currentPosition
                    if (pos != _state.value.currentPosition) {
                        _state.update { it.copy(currentPosition = pos) }
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        progressJob?.cancel()
        progressJob = null
        try {
            // reset() 已能把播放器退回未初始化状态；stop() 是多余的等待型调用，
            // 而本方法在 onViewRecycled 的主线程路径上被调用，去掉它可以减少每页滑动的阻塞
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _state.update { PlayerState() }
    }
}