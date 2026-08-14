package com.RobinNotBad.BiliClient.ui.player

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.RobinNotBad.BiliClient.player.DanmakuManager
import com.RobinNotBad.BiliClient.player.PlayerControlDelegate
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.player.PlayerIntegrator
import com.RobinNotBad.BiliClient.player.QualityOption
import com.RobinNotBad.BiliClient.ui.theme.BiliColors
import com.RobinNotBad.BiliClient.ui.theme.BiliDimens
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ModernPlayerActivity : AppCompatActivity() {

    private lateinit var integrator: PlayerIntegrator
    private lateinit var playerContainer: FrameLayout
    private lateinit var controllerLayout: LinearLayout

    private lateinit var btnPlayPause: TextView
    private lateinit var btnDanmaku: TextView
    private lateinit var btnSendDanmaku: TextView
    private lateinit var btnQuality: TextView
    private lateinit var btnSpeed: TextView
    private lateinit var btnPage: TextView
    private lateinit var btnFullscreen: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var textCurrent: TextView
    private lateinit var textDuration: TextView
    private lateinit var textTitle: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var textBuffering: TextView
    private lateinit var textVolume: TextView
    private lateinit var textBrightness: TextView

    private lateinit var pageSelectorLayout: LinearLayout
    private lateinit var qualitySelectorLayout: LinearLayout
    private lateinit var speedSelectorLayout: LinearLayout
    private lateinit var danmakuSendLayout: LinearLayout

    private var videoUrl: String = ""
    private var danmakuUrl: String = ""
    private var pagenames: ArrayList<String> = ArrayList()
    private var cids: ArrayList<Long> = ArrayList()
    private var currentPage: Int = 0
    private var videoTitle: String = "播放中..."
    private var isLiveMode: Boolean = false
    private var isSeeking: Boolean = false

    private val density: Float get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = if ((SharedPreferencesUtil.getBoolean("player_autolandscape", false)
            || SharedPreferencesUtil.getBoolean("ui_landscape", false))
            && !SharedPreferencesUtil.getBoolean("ui_mobile_mode", false)
        ) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
        )

        parseIntent()
        setContentView(createRootLayout())
        setupPlayer()
        observePlayerState()
    }

    private fun parseIntent() {
        val intent = intent
        videoUrl = intent.getStringExtra("url") ?: ""
        danmakuUrl = intent.getStringExtra("danmaku") ?: ""
        videoTitle = intent.getStringExtra("title") ?: "播放中..."
        isLiveMode = intent.getBooleanExtra("live_mode", false)

        intent.getStringArrayListExtra("pagenames")?.let { pagenames = it }
        intent.getLongArrayExtra("cids")?.let { cids = it.toCollection(ArrayList()) }
        currentPage = intent.getIntExtra("currentPageIndex", 0)
    }

    private fun createRootLayout(): FrameLayout {
        return FrameLayout(this).apply {
            setBackgroundColor(BiliColors.PlayerBackground)
            id = View.generateViewId()

            playerContainer = FrameLayout(context).apply {
                id = View.generateViewId()
                setBackgroundColor(Color.BLACK)
            }
            addView(playerContainer, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))

            controllerLayout = createControllerOverlay()
            addView(controllerLayout, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM })

            loadingIndicator = ProgressBar(context).apply {
                isIndeterminate = true
                indeterminateTintList = android.content.res.ColorStateList.valueOf(BiliColors.Primary)
                visibility = View.VISIBLE
            }
            addView(loadingIndicator, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER })

            textBuffering = TextView(context).apply {
                text = "加载中..."
                textSize = BiliDimens.BODY_SMALL
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#66000000"))
                val p = (8 * density).toInt()
                setPadding(p, (4 * density).toInt(), p, (4 * density).toInt())
                visibility = View.GONE
            }
            addView(textBuffering, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL })

            textVolume = createOverlayIndicator()
            textBrightness = createOverlayIndicator()
            addView(textVolume, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
            addView(textBrightness, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        }
    }

    private fun createControllerOverlay(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BiliColors.PlayerControlBg)
            val p = (BiliDimens.SPACING_MD * density).toInt()
            val pb = (BiliDimens.SPACING_LG * density).toInt()
            setPadding(p, (BiliDimens.SPACING_SM * density).toInt(), p, pb)

            val topBar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            textTitle = TextView(context).apply {
                text = videoTitle
                textSize = BiliDimens.TITLE_SMALL
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            btnFullscreen = createCtrlBtn("⛶")
            btnFullscreen.setOnClickListener { integrator.controlDelegate.toggleFullscreen() }

            topBar.addView(textTitle)
            topBar.addView(btnFullscreen)
            addView(topBar)

            val progressRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (BiliDimens.SPACING_SM * density).toInt(), 0, 0)
            }

            textCurrent = createTimeLabel("00:00")
            textDuration = createTimeLabel("00:00")

            seekBar = SeekBar(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                max = 1000
                progressDrawable = null
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val pos = (p / 1000f * integrator.playerBridge.duration).toLong()
                            textCurrent.text = formatTime(pos)
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) { isSeeking = true }
                    override fun onStopTrackingTouch(sb: SeekBar?) {
                        isSeeking = false
                        val pos = (sb?.progress ?: 0) / 1000f * integrator.playerBridge.duration
                        integrator.controlDelegate.seekTo(pos.toLong())
                    }
                })
            }

            progressRow.addView(textCurrent)
            progressRow.addView(seekBar)
            progressRow.addView(textDuration)
            addView(progressRow)

            val ctrlRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, (BiliDimens.SPACING_SM * density).toInt(), 0, 0)
            }

            btnPlayPause = createCtrlBtn("▶")
            btnDanmaku = createCtrlBtn("弹")
            btnSendDanmaku = createCtrlBtn("✎")
            btnQuality = createCtrlBtn("画质")
            btnSpeed = createCtrlBtn("倍速")
            btnPage = createCtrlBtn("选集")

            btnPlayPause.setOnClickListener { integrator.controlDelegate.togglePlayPause() }
            btnDanmaku.setOnClickListener { integrator.danmakuManager?.toggleVisibility() }
            btnSendDanmaku.setOnClickListener { integrator.controlDelegate.toggleDanmakuSend() }
            btnQuality.setOnClickListener { integrator.controlDelegate.toggleQualitySelector() }
            btnSpeed.setOnClickListener { integrator.controlDelegate.toggleSpeedSelector() }
            btnPage.setOnClickListener { integrator.controlDelegate.togglePageSelector() }

            ctrlRow.addView(btnPlayPause)
            ctrlRow.addView(btnDanmaku)
            ctrlRow.addView(btnSendDanmaku)
            ctrlRow.addView(btnQuality)
            ctrlRow.addView(btnSpeed)
            if (pagenames.isNotEmpty()) ctrlRow.addView(btnPage)
            addView(ctrlRow)

            danmakuSendLayout = createDanmakuSendPanel()
            pageSelectorLayout = createPageSelectorPanel()
            qualitySelectorLayout = createQualitySelectorPanel()
            speedSelectorLayout = createSpeedSelectorPanel()

            addView(danmakuSendLayout)
            addView(pageSelectorLayout)
            addView(qualitySelectorLayout)
            addView(speedSelectorLayout)

            setOnClickListener { }
        }
    }

    private fun createCtrlBtn(label: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = BiliDimens.BODY_MEDIUM
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            val p = (BiliDimens.SPACING_MD * density).toInt()
            val ps = (BiliDimens.SPACING_SM * density).toInt()
            setPadding(p, ps, p, ps)
            background = makeRoundRect(0x33FFFFFF.toInt(), BiliDimens.BUTTON_CORNER * density)
        }
    }

    private fun createTimeLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = BiliDimens.CAPTION
            setTextColor(Color.WHITE)
            val p = (4 * density).toInt()
            setPadding(p, 0, p, 0)
        }
    }

    private fun createOverlayIndicator(): TextView {
        return TextView(this).apply {
            textSize = BiliDimens.TITLE_LARGE
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#66000000"))
            visibility = View.GONE
        }
    }

    private fun createDanmakuSendPanel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            val et = EditText(context).apply {
                hint = "发送弹幕..."
                setHintTextColor(Color.parseColor("#88FFFFFF"))
                setTextColor(Color.WHITE)
                textSize = BiliDimens.BODY_MEDIUM
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val send = TextView(context).apply {
                text = "发送"
                textSize = BiliDimens.BODY_MEDIUM
                setTextColor(BiliColors.Primary)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setOnClickListener {
                    integrator.danmakuManager?.addDanmaku(et.text.toString())
                    et.text?.clear()
                    integrator.controlDelegate.closeDanmakuSend()
                }
            }
            addView(et)
            addView(send)
        }
    }

    private fun createPageSelectorPanel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            pagenames.forEachIndexed { idx, name ->
                addView(TextView(context).apply {
                    text = name
                    textSize = BiliDimens.BODY_SMALL
                    setTextColor(if (idx == currentPage) BiliColors.Primary else Color.WHITE)
                    typeface = if (idx == currentPage) android.graphics.Typeface.DEFAULT_BOLD
                    else android.graphics.Typeface.DEFAULT
                    val p = (BiliDimens.SPACING_MD * density).toInt()
                    setPadding(p, (4 * density).toInt(), p, (4 * density).toInt())
                    setOnClickListener { switchPage(idx) }
                })
            }
        }
    }

    private fun createQualitySelectorPanel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }
    }

    private fun createSpeedSelectorPanel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            integrator.controlDelegate.speedOptions.forEach { opt ->
                addView(TextView(context).apply {
                    text = opt.label
                    textSize = BiliDimens.BODY_SMALL
                    setTextColor(Color.WHITE)
                    val p = (BiliDimens.SPACING_MD * density).toInt()
                    setPadding(p, (4 * density).toInt(), p, (4 * density).toInt())
                    setOnClickListener { integrator.controlDelegate.setSpeed(opt.value) }
                })
            }
        }
    }

    private fun setupPlayer() {
        integrator = PlayerIntegrator(
            this, playerContainer,
            onError = { code, msg -> runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() } },
            onToggleFullscreen = { toggleOrientation() },
            onRequestQualityChange = { qn -> switchQuality(qn) }
        )

        integrator.setupSurfaceView()
        integrator.setupDanmakuView()

        if (videoUrl.isNotEmpty()) {
            integrator.loadVideo(videoUrl)
            if (danmakuUrl.isNotEmpty()) {
                CenterThreadPool.run {
                    try { integrator.danmakuManager?.loadFromXmlFile(danmakuUrl) }
                    catch (_: Exception) {}
                }
            }
        }

        val gestureDetector = GestureDetector(this,
            PlayerControlDelegate.GestureHandler(integrator.controlDelegate, playerContainer))

        playerContainer.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun observePlayerState() {
        lifecycleScope.launch {
            integrator.playerBridge.state.collectLatest { ps ->
                loadingIndicator.visibility = if (!ps.isPrepared) View.VISIBLE else View.GONE

                textBuffering.visibility = if (ps.isBuffering) View.VISIBLE else View.GONE

                if (!isSeeking) {
                    textCurrent.text = formatTime(ps.currentPosition)
                    textDuration.text = formatTime(ps.duration)
                    if (ps.duration > 0) {
                        seekBar.progress = (ps.currentPosition.toFloat() / ps.duration * 1000).toInt()
                            .coerceIn(0, 1000)
                    }
                    seekBar.secondaryProgress = ps.bufferedPercent * 10
                }

                btnPlayPause.text = if (ps.isPlaying) "⏸" else "▶"

                if (ps.errorCode != 0) {
                    Toast.makeText(this@ModernPlayerActivity, ps.errorMessage ?: "播放错误", Toast.LENGTH_LONG).show()
                }
            }
        }

        lifecycleScope.launch {
            integrator.controlDelegate.controlState.collectLatest { cs ->
                controllerLayout.visibility = if (cs.isVisible) View.VISIBLE else View.GONE
                danmakuSendLayout.visibility = if (cs.isDanmakuSendOpen) View.VISIBLE else View.GONE
                pageSelectorLayout.visibility = if (cs.isPageSelectorOpen) View.VISIBLE else View.GONE
                qualitySelectorLayout.visibility = if (cs.isQualitySelectorOpen) View.VISIBLE else View.GONE
                speedSelectorLayout.visibility = if (cs.isSpeedSelectorOpen) View.VISIBLE else View.GONE

                if (cs.currentVolume >= 0) {
                    val volPercent = (cs.currentVolume * 100).toInt()
                    textVolume.text = if (volPercent == 0) "🔇" else "🔊 $volPercent%"
                    textVolume.visibility = View.VISIBLE
                    textVolume.postDelayed({ textVolume.visibility = View.GONE }, 1500)
                }
                if (cs.currentBrightness >= 0) {
                    val briPercent = (cs.currentBrightness * 100).toInt()
                    textBrightness.text = "☀ $briPercent%"
                    textBrightness.visibility = View.VISIBLE
                    textBrightness.postDelayed({ textBrightness.visibility = View.GONE }, 1500)
                }
            }
        }
    }

    private fun switchPage(index: Int) {
        if (index in cids.indices) {
            currentPage = index
            pageSelectorLayout.visibility = View.GONE
            CenterThreadPool.run {
                try {
                    val cid = cids[index]
                    val playerData = PlayerData()
                    playerData.aid = if (intent.getLongExtra("aid", 0) > 0) intent.getLongExtra("aid", 0) else 0
                    playerData.cid = cid
                    playerData.qn = SharedPreferencesUtil.getInt("play_qn", 16)
                    com.RobinNotBad.BiliClient.api.PlayerApi.getVideo(playerData, false)
                    runOnUiThread { integrator.loadVideo(playerData.videoUrl) }
                } catch (_: Exception) {}
            }
        }
    }

    private fun switchQuality(qn: Int) {
        CenterThreadPool.run {
            try {
                val aid = intent.getLongExtra("aid", 0)
                val cid = cids.getOrElse(currentPage) { intent.getLongExtra("cid", 0) }
                val playerData = PlayerData()
                playerData.aid = aid
                playerData.cid = cid
                playerData.qn = qn
                com.RobinNotBad.BiliClient.api.PlayerApi.getVideo(playerData, false)
                val currentPos = integrator.playerBridge.state.value.currentPosition
                runOnUiThread {
                    integrator.loadVideo(playerData.videoUrl)
                    integrator.playerBridge.setOnPrepared {
                        integrator.seekTo(currentPos)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun toggleOrientation() {
        requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun onResume() {
        super.onResume()
        integrator.onResume()
    }

    override fun onPause() {
        super.onPause()
        integrator.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        integrator.release()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                integrator.controlDelegate.togglePlayPause(); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                integrator.controlDelegate.seekBackward(10); true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                integrator.controlDelegate.seekForward(10); true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                integrator.controlDelegate.adjustVolume(0.05f); true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                integrator.controlDelegate.adjustVolume(-0.05f); true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    companion object {
        private fun makeRoundRect(color: Int, radius: Float): android.graphics.drawable.Drawable {
            return android.graphics.drawable.GradientDrawable().apply {
                setColor(color); cornerRadius = radius
            }
        }
        private fun formatTime(ms: Long): String {
            val totalSec = ms / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val hour = min / 60
            return if (hour > 0) {
                "${"%d".format(hour)}:${"%02d".format(min % 60)}:${"%02d".format(sec)}"
            } else {
                "${"%02d".format(min)}:${"%02d".format(sec)}"
            }
        }
    }
}