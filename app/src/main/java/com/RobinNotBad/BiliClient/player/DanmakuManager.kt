package com.RobinNotBad.BiliClient.player

import android.content.Context
import android.graphics.Color
import android.view.View
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.controller.IDanmakuView
import master.flame.danmaku.danmaku.loader.ILoader
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import master.flame.danmaku.danmaku.parser.android.BiliDanmukuParser
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

data class DanmakuState(
    val isVisible: Boolean = true,
    val isPrepared: Boolean = false,
    val maxLines: Int = 15,
    val speedFactor: Float = 1.0f,
    val textSizeScale: Float = 0.7f,
    val transparency: Float = 0.5f,
    val mergeDuplicate: Boolean = false,
    val allowOverlap: Boolean = true
)

class DanmakuManager(
    private val danmakuView: IDanmakuView,
    private val onCurrentPositionMs: () -> Long
) {
    private var danmakuContext: DanmakuContext? = null
    private var danmakuParser: BaseDanmakuParser? = null

    private val _state = MutableStateFlow(DanmakuState())
    val state: StateFlow<DanmakuState> = _state.asStateFlow()

    fun init() {
        val prefs = SharedPreferencesUtil.getSharedPreferences()
        _state.update {
            DanmakuState(
                maxLines = SharedPreferencesUtil.getInt("player_danmaku_maxline", 15),
                speedFactor = SharedPreferencesUtil.getFloat("player_danmaku_speed", 1.0f),
                textSizeScale = SharedPreferencesUtil.getFloat("player_danmaku_size", 0.7f),
                transparency = SharedPreferencesUtil.getFloat("player_danmaku_transparency", 0.5f),
                mergeDuplicate = SharedPreferencesUtil.getBoolean("player_danmaku_mergeduplicate", false),
                allowOverlap = SharedPreferencesUtil.getBoolean("player_danmaku_allowoverlap", true)
            )
        }
    }

    fun loadFromXmlFile(xmlFilePath: String) {
        val file = File(xmlFilePath)
        if (!file.exists()) return

        try {
            val parser = createParser(file.inputStream())
            configureAndPrepare(parser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadFromXmlInput(inputStream: InputStream) {
        try {
            val parser = createParser(inputStream)
            configureAndPrepare(parser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadFromProtobuf(deflatedData: ByteArray) {
        try {
            val inflater = Inflater(true)
            inflater.setInput(deflatedData)
            val output = ByteArray(deflatedData.size * 20)
            val resultLength = inflater.inflate(output)
            inflater.end()

            val inflatedData = output.copyOf(resultLength)
            val inputStream = ByteArrayInputStream(inflatedData)

            val prefs = SharedPreferencesUtil.getSharedPreferences()
            val parser = com.RobinNotBad.BiliClient.util.safeCallOrDefault("danmaku_parse",
                BiliDanmukuParser().apply { sharedPreferences = prefs }
            ) {
                BiliDanmukuParser().apply { sharedPreferences = prefs }
            }

            configureAndPrepare(parser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createParser(inputStream: InputStream): BaseDanmakuParser {
        val loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI)
        loader.load(inputStream)
        val parser = BiliDanmukuParser()
        parser.sharedPreferences = SharedPreferencesUtil.getSharedPreferences()
        val dataSource = loader.dataSource
        parser.load(dataSource)
        return parser
    }

    private fun configureAndPrepare(parser: BaseDanmakuParser) {
        danmakuParser = parser
        val st = _state.value

        val maxLinesPair = hashMapOf(BaseDanmaku.TYPE_SCROLL_RL to st.maxLines)
        val overlap = hashMapOf(
            BaseDanmaku.TYPE_SCROLL_LR to st.allowOverlap,
            BaseDanmaku.TYPE_FIX_BOTTOM to st.allowOverlap
        )

        danmakuContext = DanmakuContext.create().apply {
            setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 1f)
            setDuplicateMergingEnabled(st.mergeDuplicate)
            setScrollSpeedFactor(st.speedFactor)
            setScaleTextSize(st.textSizeScale)
            setMaximumLines(maxLinesPair)
            setDanmakuTransparency(st.transparency)
            preventOverlapping(overlap)
        }

        danmakuView.setCallback(object : DrawHandler.Callback {
            override fun prepared() {
                _state.update { it.copy(isPrepared = true) }
                addDanmaku("弹幕准备完毕", Color.WHITE)
            }

            override fun updateTimer(timer: DanmakuTimer) {
                timer.update(onCurrentPositionMs())
            }

            override fun danmakuShown(danmaku: BaseDanmaku?) {}

            override fun drawingFinished() {}
        })

        danmakuView.enableDanmakuDrawingCache(true)
        danmakuView.prepare(parser, danmakuContext)
    }

    fun addDanmaku(text: String, color: Int = Color.WHITE) {
        val mContext = danmakuContext ?: return
        val danmaku = mContext.mDanmakuFactory.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL, danmakuContext)
        if (danmaku != null) {
            danmaku.text = text
            danmaku.padding = 5
            danmaku.priority = 0
            danmaku.isLive = false
            danmaku.time = onCurrentPositionMs() + 1200
            danmaku.textSize = 25f * (_state.value.textSizeScale)
            danmaku.textColor = color
            danmaku.textShadowColor = Color.BLACK
            danmakuView.addDanmaku(danmaku)
        }
    }

    fun toggleVisibility() {
        val newVisible = !_state.value.isVisible
        _state.update { it.copy(isVisible = newVisible) }
        if (!_state.value.isPrepared) return
        try {
            if (newVisible) {
                danmakuView.show()
            } else {
                danmakuView.hide()
            }
        } catch (_: Exception) {}
    }

    fun show() {
        _state.update { it.copy(isVisible = true) }
        if (!_state.value.isPrepared) return
        try { danmakuView.show() } catch (_: Exception) {}
    }

    fun hide() {
        _state.update { it.copy(isVisible = false) }
        if (!_state.value.isPrepared) return
        try { danmakuView.hide() } catch (_: Exception) {}
    }

    fun pause() {
        if (!_state.value.isPrepared) return
        try { danmakuView.pause() } catch (_: Exception) {}
    }
    fun resume() {
        if (!_state.value.isPrepared) return
        try { danmakuView.resume() } catch (_: Exception) {}
    }
    fun seekTo(ms: Long) {
        if (!_state.value.isPrepared) return
        try { danmakuView.seekTo(ms) } catch (_: Exception) {}
    }

    fun setSpeedFactor(factor: Float) {
        _state.update { it.copy(speedFactor = factor) }
    }

    fun setTextSizeScale(scale: Float) {
        _state.update { it.copy(textSizeScale = scale) }
    }

    fun setTransparency(alpha: Float) {
        _state.update { it.copy(transparency = alpha) }
    }

    fun release() {
        try { danmakuView.release() } catch (_: Exception) {}
    }
}