package com.RobinNotBad.BiliClient.player

import android.content.Context
import android.net.Uri
import android.view.Surface
import android.view.SurfaceHolder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer

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

enum class PlayerScaleMode {
    FIT_PARENT, FILL_PARENT, STRETCH_PARENT, FIT_16_9, FIT_4_3, ORIGINAL
}

class IjkPlayerBridge(
    private val onError: (Int, String) -> Unit = { _, _ -> }
) {
    private var mediaPlayer: IjkMediaPlayer? = null
    private var job: Job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var onPreparedCallback: (() -> Unit)? = null
    private var onCompletionCallback: (() -> Unit)? = null
    private var onBufferingUpdateCallback: ((Int) -> Unit)? = null

    fun createPlayer(context: Context) {
        release()

        val player = IjkMediaPlayer().apply {
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
                    _state.update { it.copy(currentPosition = player.currentPosition) }
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
        scope.cancel()
        progressJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _state.update { PlayerState() }
    }
}