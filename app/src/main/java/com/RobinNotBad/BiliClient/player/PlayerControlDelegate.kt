package com.RobinNotBad.BiliClient.player

import android.content.Context
import android.media.AudioManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

data class ControlState(
    val isVisible: Boolean = true,
    val isMenuOpen: Boolean = false,
    val isPageSelectorOpen: Boolean = false,
    val isQualitySelectorOpen: Boolean = false,
    val isSpeedSelectorOpen: Boolean = false,
    val isDanmakuSendOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val currentVolume: Float = 0.5f,
    val currentBrightness: Float = 0.5f,
    val isLiveMode: Boolean = false,
    val onlineCount: String = "0",
    val isAudioOnlyMode: Boolean = false
)

data class QualityOption(val name: String, val qn: Int)
data class SpeedOption(val label: String, val value: Float)

class PlayerControlDelegate(
    private val context: Context,
    private val playerBridge: IjkPlayerBridge,
    private val autoHideDelay: Long = 3000L,
    private val onToggleFullscreen: () -> Unit = {},
    private val onRequestQualityChange: (Int) -> Unit = {}
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    private val _controlState = MutableStateFlow(ControlState())
    val controlState: StateFlow<ControlState> = _controlState.asStateFlow()

    private val _availableQualities = MutableStateFlow(emptyList<QualityOption>())
    val availableQualities: StateFlow<List<QualityOption>> = _availableQualities.asStateFlow()

    private val _currentQuality = MutableStateFlow(QualityOption("自动", 0))
    val currentQuality: StateFlow<QualityOption> = _currentQuality.asStateFlow()

    private var autoHideJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f)
    private val speedLabels = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x", "3.0x")

    val speedOptions: List<SpeedOption>
        get() = speedValues.indices.map { SpeedOption(speedLabels[it], speedValues[it]) }

    fun showController() {
        _controlState.update { it.copy(isVisible = true) }
        scheduleAutoHide()
    }

    fun hideController() {
        _controlState.update { it.copy(
            isVisible = false,
            isMenuOpen = false,
            isPageSelectorOpen = false,
            isQualitySelectorOpen = false,
            isSpeedSelectorOpen = false,
            isDanmakuSendOpen = false,
            isSettingsOpen = false
        )}
    }

    fun toggleController() {
        if (_controlState.value.isVisible) hideController() else showController()
    }

    private fun scheduleAutoHide() {
        autoHideJob?.cancel()
        autoHideJob = scope.launch {
            delay(autoHideDelay)
            if (playerBridge.isPlaying) hideController()
        }
    }

    fun togglePlayPause() {
        playerBridge.togglePlayPause()
        scheduleAutoHide()
    }

    fun seekTo(positionMs: Long) {
        playerBridge.seekTo(positionMs)
        scheduleAutoHide()
    }

    fun seekForward(seconds: Int = 10) {
        val pos = playerBridge.currentPosition + (seconds * 1000L)
        playerBridge.seekTo(pos.coerceAtMost(playerBridge.duration))
    }

    fun seekBackward(seconds: Int = 10) {
        val pos = playerBridge.currentPosition - (seconds * 1000L)
        playerBridge.seekTo(pos.coerceAtLeast(0))
    }

    fun setSpeed(speed: Float) {
        playerBridge.setSpeed(speed)
        _controlState.update { it.copy(isSpeedSelectorOpen = false) }
        scheduleAutoHide()
    }

    fun toggleSpeedSelector() {
        _controlState.update {
            it.copy(isSpeedSelectorOpen = !it.isSpeedSelectorOpen)
        }
    }

    fun setQuality(qn: Int) {
        onRequestQualityChange(qn)
        _controlState.update { it.copy(isQualitySelectorOpen = false) }
    }

    fun toggleQualitySelector() {
        _controlState.update {
            it.copy(isQualitySelectorOpen = !it.isQualitySelectorOpen)
        }
    }

    fun setAvailableQualities(qualities: List<QualityOption>, currentQn: Int) {
        _availableQualities.value = qualities
        val current = qualities.find { it.qn == currentQn } ?: QualityOption("自动", 0)
        _currentQuality.value = current
    }

    fun togglePageSelector() {
        _controlState.update {
            it.copy(isPageSelectorOpen = !it.isPageSelectorOpen)
        }
    }

    fun toggleSettings() {
        _controlState.update {
            it.copy(isSettingsOpen = !it.isSettingsOpen)
        }
    }

    fun toggleDanmakuSend() {
        _controlState.update {
            it.copy(isDanmakuSendOpen = !it.isDanmakuSendOpen)
        }
    }

    fun closeDanmakuSend() {
        _controlState.update { it.copy(isDanmakuSendOpen = false) }
    }

    fun toggleFullscreen() {
        onToggleFullscreen()
    }

    fun adjustVolume(delta: Float) {
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
        val newVol = (currentVol + delta).coerceIn(0f, 1f)
        val newStreamVol = (newVol * maxVolume).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newStreamVol, 0)
        _controlState.update { it.copy(currentVolume = newVol) }
    }

    fun setVolume(volume: Float) {
        val newStreamVol = (volume * maxVolume).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newStreamVol, 0)
        _controlState.update { it.copy(currentVolume = volume) }
    }

    fun setBrightness(brightness: Float) {
        _controlState.update { it.copy(currentBrightness = brightness.coerceIn(0f, 1f)) }
    }

    fun setLiveMode(live: Boolean) {
        _controlState.update { it.copy(isLiveMode = live) }
    }

    fun updateOnlineCount(count: String) {
        _controlState.update { it.copy(onlineCount = count) }
    }

    fun toggleAudioOnly() {
        val newMode = !_controlState.value.isAudioOnlyMode
        _controlState.update { it.copy(isAudioOnlyMode = newMode) }
    }

    class GestureHandler(
        private val delegate: PlayerControlDelegate,
        private val playerView: View
    ) : GestureDetector.SimpleOnGestureListener() {

        private val doubleTapSeekEnabled = SharedPreferencesUtil.getBoolean("player_doubletap_seek", false)
        private val doubleTapSeekSeconds = SharedPreferencesUtil.getInt("player_doubletap_seek_seconds", 10)

        private var isLongPressing = false
        private var gestureStartX = 0f
        private var gestureStartY = 0f
        private var gestureStartVol = 0f
        private var gestureStartBri = 0f
        private var gestureStartPos = 0L
        private var isVolumeGesture = false
        private var isBrightnessGesture = false
        private var isSeekGesture = false

        override fun onDown(e: MotionEvent): Boolean {
            gestureStartX = e.x
            gestureStartY = e.y
            gestureStartVol = delegate.controlState.value.currentVolume
            gestureStartBri = delegate.controlState.value.currentBrightness
            gestureStartPos = delegate.playerBridge.currentPosition
            isVolumeGesture = false
            isBrightnessGesture = false
            isSeekGesture = false
            return true
        }

       override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (isLongPressing) return false

            val screenW = playerView.width
            val deltaY = distanceY / playerView.height
            val deltaX = distanceX / playerView.width

            if (!isVolumeGesture && !isBrightnessGesture && !isSeekGesture) {
                when {
                    abs(distanceY).toFloat() > abs(distanceX).toFloat() && gestureStartX < screenW / 2 -> {
                        isVolumeGesture = true
                    }
                    abs(distanceY).toFloat() > abs(distanceX).toFloat() && gestureStartX >= screenW / 2 -> {
                        isBrightnessGesture = true
                    }
                    abs(distanceX).toFloat() > abs(distanceY).toFloat() -> {
                        isSeekGesture = true
                    }
                }
            }

            when {
                isVolumeGesture -> delegate.setVolume((gestureStartVol - deltaY).coerceIn(0f, 1f))
                isBrightnessGesture -> delegate.setBrightness((gestureStartBri - deltaY).coerceIn(0f, 1f))
                isSeekGesture -> {
                    val seekDelta = (-deltaX * delegate.playerBridge.duration * 0.1f).toLong()
                    delegate.seekTo((gestureStartPos + seekDelta).coerceIn(0, delegate.playerBridge.duration))
                }
            }
            return true
        }

        fun onSingleTapUp(): Boolean {
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            delegate.toggleController()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (doubleTapSeekEnabled && delegate.playerBridge.state.value.isPrepared
                && !delegate.controlState.value.isLiveMode
            ) {
                if (e.x > playerView.width / 2f) {
                    delegate.seekForward(doubleTapSeekSeconds)
                } else {
                    delegate.seekBackward(doubleTapSeekSeconds)
                }
                return true
            }
            return false
        }

        override fun onLongPress(e: MotionEvent) {
            isLongPressing = true
        }
    }

    fun release() {
        scope.cancel()
        autoHideJob?.cancel()
    }
}