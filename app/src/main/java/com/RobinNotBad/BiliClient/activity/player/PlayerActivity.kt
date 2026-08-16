package com.RobinNotBad.BiliClient.activity.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimationDrawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.InteractionDebugActivity
import com.RobinNotBad.BiliClient.activity.video.info.VideoInfoActivity
import com.RobinNotBad.BiliClient.adapter.QualitySelectorAdapter
import com.RobinNotBad.BiliClient.adapter.ViewPointAdapter
import com.RobinNotBad.BiliClient.api.ConfInfoApi
import com.RobinNotBad.BiliClient.api.DanmakuApi
import com.RobinNotBad.BiliClient.api.InteractionVideoApi
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.api.VideoInfoApi
import com.RobinNotBad.BiliClient.event.SnackEvent
import com.RobinNotBad.BiliClient.model.DmSegMobileReply
import com.RobinNotBad.BiliClient.model.HighEnergyData
import com.RobinNotBad.BiliClient.model.InteractionVideoData
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.Subtitle
import com.RobinNotBad.BiliClient.model.SubtitleLink
import com.RobinNotBad.BiliClient.model.ViewPoint
import com.RobinNotBad.BiliClient.ui.widget.BatteryView
import com.RobinNotBad.BiliClient.ui.widget.HighEnergyProgressBar
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.google.android.material.snackbar.Snackbar
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.controller.IDanmakuView
import master.flame.danmaku.danmaku.loader.ILoader
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.model.android.Danmakus
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import master.flame.danmaku.danmaku.parser.IDataSource
import master.flame.danmaku.danmaku.parser.android.BiliDanmukuParser
import master.flame.danmaku.danmaku.parser.android.BiliProtobufDanmakuParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okio.BufferedSink
import okio.buffer
import okio.sink
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.regex.Pattern
import java.util.zip.Inflater
import kotlin.math.abs

class PlayerActivity : Activity(), IMediaPlayer.OnPreparedListener {
    private var destroyed = false

    private var ijkPlayer: IjkMediaPlayer? = null
    private var mDanmakuView: IDanmakuView? = null
    private var mContext: DanmakuContext? = null

    private var surfaceView: SurfaceView? = null
    private var textureView: TextureView? = null
    private var mSurfaceTexture: SurfaceTexture? = null

    private var subtitleLinks: Array<SubtitleLink>? = null
    private var subtitles: Array<Subtitle>? = null
    private var subtitle_curr_index: Int = 0
    private var subtitle_count: Int = 0
    private var subtitle_delta: Float = 0f

    private lateinit var layout_control: RelativeLayout
    private lateinit var layout_top: RelativeLayout
    private lateinit var layout_video: RelativeLayout
    private lateinit var layout_card_bg: RelativeLayout
    private lateinit var layout_audio_only: RelativeLayout
    private lateinit var layout_speed: LinearLayout
    private lateinit var right_control: LinearLayout
    private lateinit var loading_info: LinearLayout
    private lateinit var bottom_buttons: RelativeLayout
    private lateinit var right_second: HorizontalScrollView
    private lateinit var card_subtitle: LinearLayout
    private lateinit var card_danmaku_send: LinearLayout
    private lateinit var card_page_selector: LinearLayout
    private lateinit var card_quality_selector: LinearLayout
    private lateinit var card_viewpoint_selector: LinearLayout

    private lateinit var img_loading: ImageView
    private var anim_loading: AnimationDrawable? = null
    private lateinit var btn_control: ImageButton
    private lateinit var btn_danmaku: ImageButton
    private lateinit var btn_loop: ImageButton
    private lateinit var btn_rotate: ImageButton
    private lateinit var btn_menu: ImageButton
    private lateinit var btn_subtitle: ImageButton
    private lateinit var btn_danmaku_send: ImageButton
    private lateinit var btn_audio_only: ImageButton
    private lateinit var btn_page_selector: ImageButton
    private lateinit var btn_auto_next: ImageButton
    private lateinit var btn_quality: ImageButton
    private lateinit var btn_viewpoint: ImageButton
    private lateinit var btn_debug: ImageButton
    private lateinit var seekbar_progress: HighEnergyProgressBar
    private lateinit var seekbar_speed: SeekBar
    private lateinit var text_progress: TextView
    private lateinit var text_online: TextView
    private lateinit var text_volume: TextView
    private lateinit var loading_text0: TextView
    private lateinit var loading_text1: TextView
    private lateinit var text_speed: TextView
    private lateinit var text_newspeed: TextView
    @JvmField var text_title: TextView? = null
    private lateinit var text_subtitle: TextView
    private lateinit var text_audio_title: TextView
    private lateinit var text_audio_subtitle: TextView

    private var progressTimer: Timer? = null
    private var speedTimer: Timer? = null
    private var loadingTimer: Timer? = null
    private var onlineTimer: Timer? = null
    private var surfaceTimer: Timer? = null
    private var mainHandler: Handler? = null
    private var danmakuSyncRunnable: Runnable? = null
    private var video_url: String? = null
    private var danmaku_url: String = ""
    private var mediaSession: MediaSession? = null

    private var isPlaying: Boolean = false
    private var isPrepared: Boolean = false
    private var hasDanmaku: Boolean = false
    private var isOnlineVideo: Boolean = false
    private var isLiveMode: Boolean = false
    private var isSeeking: Boolean = false
    private var isDanmakuVisible: Boolean = false
    private var menu_opened = false
    private var isAudioOnlyMode = false
    private var isLocalAudioFile = false
    private var audioTrackUrl: String? = null // DASH外部音频轨道
    private var audioPlayer: android.media.MediaPlayer? = null // 外部音频播放器

    private var video_all: Int = 0
    private var video_now: Int = 0
    private var video_now_last: Int = 0
    private var progress_history: Long = 0
    private var progress_str: String = ""

    private var screen_width: Int = 0
    private var screen_height: Int = 0
    private var video_width: Int = 0
    private var video_height: Int = 0

    private var audioManager: AudioManager? = null

    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var scaleGestureListener: ViewScaleGestureListener? = null
    private var doubleTapGestureDetector: GestureDetector? = null
    private var previousX: Float = 0f
    private var previousY: Float = 0f
    private var gesture_moved: Boolean = false
    private var gesture_scaled: Boolean = false
    private var gesture_click_disabled: Boolean = false
    private var video_origX: Float = 0f
    private var video_origY: Float = 0f
    private var timestamp_click: Long = 0
    private var onLongClick: Boolean = false

    private val speed_values = floatArrayOf(0.5F, 0.75F, 1.0F, 1.25F, 1.5F, 1.75F, 2.0F, 3.0F)
    private val speed_strs = arrayOf("x 0.5", "x 0.75", "x 1.0", "x 1.25", "x 1.5", "x 1.75", "x 2.0", "x 3.0")

    private var finishWatching = false
    private var loop_enabled: Boolean = false
    private var auto_next_enabled = false

    private lateinit var batteryView: BatteryView
    private var batteryManager: BatteryManager? = null

    private var danmakuFile: File? = null

    private var screen_landscape: Boolean = false
    private var screen_round: Boolean = false

    @JvmField var online_number: String = "0"

    private var aid: Long = 0
    private var cid: Long = 0
    private var mid: Long = 0

    private var pagenames: ArrayList<String>? = null
    private var cids: ArrayList<Long>? = null
    private var currentPageIndex = 0
    private var videoTitle: String? = null

    private var qnStrList: Array<String>? = null
    private var qnValueList: IntArray? = null
    private var currentQuality = 0

    private var interactionData: InteractionVideoData? = null
    private var interactionGraphVersion: Long = 0
    private var currentEdgeId: Long = 0
    private var initialEdgeId: Long = 0
    private var currentQuestion: InteractionVideoData.InteractionQuestion? = null
    private var questionShown = false
    private var interactionChoiceLayout: LinearLayout? = null

    private var isShortVideoMode = false
    private lateinit var btn_video_info: TextView

    private var viewPoints: MutableList<ViewPoint>? = null
    private var viewPointAdapter: ViewPointAdapter? = null

    private var eventBusInit = false

    @JvmField var liveWebSocket: WebSocket? = null
    private var okHttpClient: OkHttpClient? = null
    private var subtitle_selected = -1

    override fun onBackPressed() {
        if (!SharedPreferencesUtil.getBoolean("back_disable", false)) {
            super.onBackPressed()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(BiliTerminal.getFitDisplayContext(newBase))
    }

    private fun getExtras(): Boolean {
        val intent = intent ?: return false

        video_url = intent.getStringExtra("url")
        danmaku_url = intent.getStringExtra("danmaku") ?: ""
        val title = intent.getStringExtra("title")

        if (video_url == null) return false
        if (danmaku_url.isNotEmpty()) Logu.v("弹幕", danmaku_url)
        Logu.v("视频", video_url)
        Logu.v("标题", title)
        text_title!!.text = title
        videoTitle = title

        aid = intent.getLongExtra("aid", 0)
        cid = intent.getLongExtra("cid", 0)
        mid = intent.getLongExtra("mid", 0)

        progress_history = intent.getIntExtra("progress", 0).toLong()
        Logu.d("history", progress_history.toString())

        isLiveMode = intent.getBooleanExtra("live_mode", false)
        isOnlineVideo = video_url!!.contains("http")
        hasDanmaku = danmaku_url.isNotEmpty()

        if (intent.hasExtra("pagenames")) {
            pagenames = intent.getStringArrayListExtra("pagenames")
            if (intent.hasExtra("cids")) {
                val cidList = ArrayList<Long>()
                val cidArray = intent.getLongArrayExtra("cids")
                if (cidArray != null) {
                    for (c in cidArray) cidList.add(c)
                }
                cids = cidList
            } else {
                cids = ArrayList()
            }
            currentPageIndex = intent.getIntExtra("currentPageIndex", 0)
        }

        initialEdgeId = intent.getLongExtra("edgeId", 0)
        if (initialEdgeId > 0) {
            currentEdgeId = initialEdgeId
        }

        if (intent.hasExtra("qnStrList") && intent.hasExtra("qnValueList")) {
            qnStrList = intent.getStringArrayExtra("qnStrList")
            qnValueList = intent.getIntArrayExtra("qnValueList")
            currentQuality = intent.getIntExtra("currentQuality", SharedPreferencesUtil.getInt("play_qn", 16))
        }

        isShortVideoMode = intent.getBooleanExtra("isShortVideoMode", false)

        return true
    }

    @SuppressLint("SimpleDateFormat")
    override fun onCreate(savedInstanceState: Bundle?) {
        Logu.v("加载", "加载")
        val theme = SharedPreferencesUtil.getString(ThemeManager.PREF_KEY_THEME, ThemeManager.THEME_BILIBILI_PINK)
        val themeResId = when (theme) {
            ThemeManager.THEME_ZHIHU_BLUE -> R.style.Theme_ZhihuBlue
            ThemeManager.THEME_IQIYI_GREEN -> R.style.Theme_IQIYIGreen
            ThemeManager.THEME_PURPLE_FANTASY -> R.style.Theme_PurpleFantasy
            ThemeManager.THEME_RAINBOW_FANTASY -> R.style.Theme_RainbowFantasy
            ThemeManager.THEME_CLASSIC_GRAY -> R.style.Theme_ClassicGray
            else -> R.style.Theme_BiliClient
        }
        setTheme(themeResId)
        super.onCreate(savedInstanceState)

        screen_landscape = SharedPreferencesUtil.getBoolean("player_autolandscape", false)
                || SharedPreferencesUtil.getBoolean("ui_landscape", false)
        if (SharedPreferencesUtil.getBoolean("dev_player_rotate_software", false) && screen_landscape) {
            MsgUtil.showMsg("不支持默认横屏！")
            screen_landscape = false
        } else {
            requestedOrientation = if (screen_landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        setContentView(R.layout.activity_player)
        findview()
        if (!getExtras()) {
            finish()
            return
        }

        initUI()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PLAYER_MEDIA_SESSION_ENABLE, false)) {
            initMediaSession()
        }

        IjkMediaPlayer.loadLibrariesOnce(null)

        ijkPlayer = IjkMediaPlayer()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
            batteryView.setPower(batteryManager!!.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
        } else
            batteryView.visibility = View.GONE

        loop_enabled = SharedPreferencesUtil.getBoolean("player_loop", false)
        isAudioOnlyMode = SharedPreferencesUtil.getBoolean("player_audio_only", false)
        isLocalAudioFile = intent.getBooleanExtra("audio_only", false)
        audioTrackUrl = intent.getStringExtra("audio_track_url")
        if (isLocalAudioFile) {
            isAudioOnlyMode = true
        }
        img_loading.setImageResource(R.drawable.loading_tv_shaking)
        anim_loading = img_loading.drawable as AnimationDrawable
        anim_loading!!.start()

        val cachepath = cacheDir
        if (!cachepath.exists()) cachepath.mkdirs()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mainHandler = Handler(Looper.getMainLooper())

        setVideoGestures()
        autohideReset()

        initSeekbars()

        if (isLiveMode) {
            btn_control.visibility = View.GONE
            seekbar_progress.visibility = View.GONE
            seekbar_progress.isEnabled = false
            streamDanmaku(null)
        }

        layout_control.postDelayed({
            CenterThreadPool.run {
                if (isLiveMode) {
                    runOnUiThread {
                        btn_menu.visibility = View.GONE
                        btn_quality.visibility = View.GONE
                        btn_loop.visibility = View.GONE
                        btn_audio_only.visibility = View.GONE
                        btn_auto_next.visibility = View.GONE
                        btn_page_selector.visibility = View.GONE
                    }
                    setDisplay()
                    return@run
                }

                runOnUiThread {
                    loading_text0.text = "装填弹幕中"
                    loading_text1.text = "(≧∇≦)"
                }
                if (isOnlineVideo) {
                    danmakuFile = File(cachepath, "danmaku.xml")
                    downdanmu()
                } else {
                    runOnUiThread { btn_danmaku_send.visibility = View.GONE }
                    danmakuFile = File(danmaku_url)
                    if (danmakuFile!!.exists())
                        streamDanmaku(danmakuFile!!.toString())
                    else
                        hasDanmaku = false
                }

                if (!destroyed && SharedPreferencesUtil.getBoolean("player_subtitle_autoshow", true))
                    downSubtitle(false)

                if (!destroyed && isOnlineVideo && aid > 0 && cid > 0) {
                    loadHighEnergyData()
                }

                if (!destroyed && isOnlineVideo && aid > 0 && cid > 0 && SharedPreferencesUtil.getBoolean("player_show_viewpoints", true)) {
                    loadViewPoints()
                }

                if (!destroyed && isOnlineVideo && aid > 0 && cid > 0) {
                    loadInteractionVideo()
                }

                if (!destroyed) setDisplay()
            }
        }, 60)
    }

    private fun findview() {
        layout_control = findViewById(R.id.control_layout)
        layout_top = findViewById(R.id.top)
        right_control = findViewById(R.id.right_control)
        right_second = findViewById(R.id.right_second)
        layout_card_bg = findViewById(R.id.card_bg)
        card_subtitle = findViewById(R.id.subtitle_card)
        card_danmaku_send = findViewById(R.id.danmaku_send_card)
        card_page_selector = findViewById(R.id.page_selector_card)
        card_quality_selector = findViewById(R.id.quality_selector_card)
        card_viewpoint_selector = findViewById(R.id.viewpoint_selector_card)
        layout_audio_only = findViewById(R.id.audio_only_layout)

        loading_info = findViewById(R.id.loading_info)

        img_loading = findViewById(R.id.circle)
        text_progress = findViewById(R.id.text_progress)
        text_online = findViewById(R.id.text_online)
        btn_danmaku = findViewById(R.id.danmaku_btn)
        btn_loop = findViewById(R.id.loop_btn)
        btn_rotate = findViewById(R.id.rotate_btn)
        btn_menu = findViewById(R.id.menu_btn)
        btn_danmaku_send = findViewById(R.id.danmaku_send_btn)
        btn_subtitle = findViewById(R.id.subtitle_btn)
        btn_audio_only = findViewById(R.id.audio_only_btn)
        btn_control = findViewById(R.id.button_video)
        btn_page_selector = findViewById(R.id.button_page_selector)
        btn_auto_next = findViewById(R.id.auto_next_btn)
        btn_quality = findViewById(R.id.button_quality)
        btn_viewpoint = findViewById(R.id.viewpoint_btn)
        seekbar_progress = findViewById(R.id.videoprogress)
        loading_text0 = findViewById(R.id.loading_text0)
        loading_text1 = findViewById(R.id.loading_text1)
        text_title = findViewById(R.id.text_title)
        text_volume = findViewById(R.id.showsound)
        layout_video = findViewById(R.id.videoArea)
        mDanmakuView = findViewById(R.id.sv_danmaku)
        batteryView = findViewById(R.id.battery)

        text_speed = findViewById(R.id.text_speed)
        layout_speed = findViewById(R.id.layout_speed)
        seekbar_speed = findViewById(R.id.seekbar_speed)
        text_newspeed = findViewById(R.id.text_newspeed)
        bottom_buttons = findViewById(R.id.bottom_buttons)
        btn_debug = findViewById(R.id.btn_debug)

        text_subtitle = findViewById(R.id.text_subtitle)
        text_audio_title = findViewById(R.id.audio_title)
        text_audio_subtitle = findViewById(R.id.audio_subtitle)

        btn_video_info = findViewById(R.id.video_info_btn)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setVideoGestures() {
        val doubleTapSeekEnabled = SharedPreferencesUtil.getBoolean("player_doubletap_seek", false)
        val doubleTapSeekSeconds = SharedPreferencesUtil.getInt("player_doubletap_seek_seconds", 10)

        if (doubleTapSeekEnabled) {
            doubleTapGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (ijkPlayer != null && isPrepared && !isLiveMode) {
                        val x = e.x
                        val viewWidth = layout_control.width.toFloat()
                        val currentPosition = ijkPlayer!!.currentPosition
                        val seekOffset = doubleTapSeekSeconds * 1000L

                        gesture_click_disabled = true

                        val third = viewWidth / 3.0f
                        when {
                            x > third * 2 -> {
                                var newPosition = currentPosition + seekOffset
                                val duration = ijkPlayer!!.duration
                                if (newPosition > duration) newPosition = duration
                                seekToPosition(newPosition)
                            }
                            x < third -> {
                                var newPosition = currentPosition - seekOffset
                                if (newPosition < 0) newPosition = 0
                                seekToPosition(newPosition)
                            }
                            else -> {
                                handleDoubleTapAction()
                            }
                        }
                        return true
                    }
                    return false
                }
            })
        }

        if (SharedPreferencesUtil.getBoolean("player_scale", true)) {
            scaleGestureListener = ViewScaleGestureListener(layout_video)
            scaleGestureDetector = ScaleGestureDetector(this, scaleGestureListener!!)

            val doublemove_enabled = SharedPreferencesUtil.getBoolean("player_doublemove", true)

            layout_control.setOnTouchListener { v, event ->
                if (doubleTapSeekEnabled && doubleTapGestureDetector != null) {
                    doubleTapGestureDetector!!.onTouchEvent(event)
                }
                val action = event.actionMasked
                val pointerCount = event.pointerCount
                val singleTouch = pointerCount == 1
                val doubleTouch = pointerCount == 2

                scaleGestureDetector!!.onTouchEvent(event)
                val gesture_scaling = scaleGestureListener!!.scaling

                if (!gesture_scaled && gesture_scaling) gesture_scaled = true

                when (action) {
                    MotionEvent.ACTION_MOVE -> {
                        if (singleTouch) {
                            if (gesture_scaling) {
                                videoMoveBy(0f, 0f)
                            } else if (!(gesture_scaled && !doublemove_enabled)) {
                                val currentX = event.getX(0)
                                val currentY = event.getY(0)
                                val deltaX = currentX - previousX
                                val deltaY = currentY - previousY
                                if (deltaX != 0f || deltaY != 0f) {
                                    videoMoveBy(deltaX, deltaY)
                                    previousX = currentX
                                    previousY = currentY
                                }
                            }
                        }
                        if (doubleTouch && doublemove_enabled) {
                            val currentX = (event.getX(0) + event.getX(1)) / 2
                            val currentY = (event.getY(0) + event.getY(1)) / 2
                            val deltaX = currentX - previousX
                            val deltaY = currentY - previousY
                            if (deltaX != 0f || deltaY != 0f) {
                                videoMoveBy(deltaX, deltaY)
                                previousX = currentX
                                previousY = currentY
                            }
                        }
                    }
                    MotionEvent.ACTION_DOWN -> {
                        if (singleTouch) {
                            previousX = event.getX(0)
                            previousY = event.getY(0)
                        }
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if (doubleTouch) {
                            previousX = (event.getX(0) + event.getX(1)) / 2
                            previousY = (event.getY(0) + event.getY(1)) / 2
                        }
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        if (doubleTouch) {
                            val index = event.actionIndex
                            previousX = event.getX(if (index == 0) 1 else 0)
                            previousY = event.getY(if (index == 0) 1 else 0)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (onLongClick) {
                            onLongClick = false
                            val normalSpeed = speed_values[seekbar_speed.progress]
                            ijkPlayer?.setSpeed(normalSpeed)
                            mDanmakuView?.setSpeed(normalSpeed)
                            text_speed.text = speed_strs[seekbar_speed.progress]
                        }
                        if (gesture_moved) gesture_moved = false
                        if (gesture_scaled) gesture_scaled = false
                    }
                }

                if (!gesture_click_disabled && (gesture_moved || gesture_scaled)) {
                    gesture_click_disabled = true
                    hidecon.run()
                }

                false
            }
        } else {
            layout_control.setOnTouchListener { _, motionEvent ->
                if (doubleTapSeekEnabled && doubleTapGestureDetector != null) {
                    doubleTapGestureDetector!!.onTouchEvent(motionEvent)
                }
                if (motionEvent.action == MotionEvent.ACTION_UP && onLongClick) {
                    onLongClick = false
                    val normalSpeed = speed_values[seekbar_speed.progress]
                    ijkPlayer?.setSpeed(normalSpeed)
                    mDanmakuView?.setSpeed(normalSpeed)
                    text_speed.text = speed_strs[seekbar_speed.progress]
                }
                false
            }
        }

        layout_control.setOnClickListener {
            if (gesture_click_disabled) gesture_click_disabled = false
            else clickUI()
        }
        layout_control.setOnLongClickListener {
            if (SharedPreferencesUtil.getBoolean("player_longclick", true) && ijkPlayer != null && isPlaying && !isLiveMode) {
                if (!onLongClick && !gesture_click_disabled) {
                    hidecon.run()
                    ijkPlayer?.setSpeed(3.0F)
                    mDanmakuView?.setSpeed(3.0f)
                    text_speed.text = "x 3.0"
                    onLongClick = true
                    Logu.v("gesture", "longclick_down")
                    return@setOnLongClickListener true
                }
                return@setOnLongClickListener false
            }
            false
        }
    }

    private fun autohideReset() {
        layout_control.removeCallbacks(hidecon)
        layout_control.postDelayed(hidecon, 4000)
    }

    private fun clickUI() {
        val nowTimestamp = System.currentTimeMillis()
        if (nowTimestamp - timestamp_click < 300) {
            if (SharedPreferencesUtil.getBoolean("player_scale", true) && scaleGestureListener!!.can_reset) {
                scaleGestureListener!!.can_reset = false
                layout_video.x = video_origX
                layout_video.y = video_origY
                layout_video.scaleX = 1.0f
                layout_video.scaleY = 1.0f
            } else {
                handleDoubleTapAction()
            }
        } else {
            timestamp_click = nowTimestamp
            if (layout_top.visibility == View.GONE) showcon()
            else hidecon.run()
        }
    }

    private fun handleDoubleTapAction() {
        if (SharedPreferencesUtil.getBoolean("player_doubletap_restore_screen", false) && screen_landscape) {
            screen_landscape = false
            if (SharedPreferencesUtil.getBoolean("dev_player_rotate_software", false)) softwareRotate()
            else requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else if (!isLiveMode) {
            if (isPlaying) playerPause()
            else playerResume()
            showcon()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showcon() {
        right_control.visibility = View.VISIBLE
        layout_top.visibility = View.VISIBLE
        bottom_buttons.visibility = View.VISIBLE
        seekbar_progress.visibility = View.VISIBLE
        seekbar_progress.isEnabled = false
        seekbar_progress.postDelayed(progressbarEnable, 200)
        if (isPrepared && !isLiveMode && !isAudioOnlyMode) {
            text_speed.visibility = View.VISIBLE
            updateDebugButtonVisibility()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryView.setPower(batteryManager!!.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
        }
        if (screen_round) {
            text_progress.gravity = Gravity.NO_GRAVITY
            text_progress.setPadding(ToolsUtil.dp2px(24f), 0, 0, 0)
            if (onlineTimer != null) text_online.visibility = View.VISIBLE
        }

        autohideReset()
    }

    private val progressbarEnable = Runnable { seekbar_progress.isEnabled = true }

    private val hidecon = Runnable {
        right_control.visibility = View.GONE
        layout_top.visibility = View.GONE
        bottom_buttons.visibility = View.GONE
        seekbar_progress.visibility = View.GONE
        if (isPrepared && !isAudioOnlyMode) {
            text_speed.visibility = View.GONE
            btn_debug.visibility = View.GONE
        }
        if (screen_round) {
            text_progress.gravity = Gravity.CENTER
            text_progress.setPadding(0, 0, 0, ToolsUtil.dp2px(8f))
            if (onlineTimer != null) text_online.visibility = View.GONE
        }
        if (menu_opened) btn_menu.performClick()
    }

    private fun setDisplay() {
        Logu.v("创建播放器")
        Logu.v("url", video_url)

        runOnUiThread { loading_text0.text = "初始化播放" }

        if (isAudioOnlyMode) {
            ijkPlayer!!.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "vn", 1)
            ijkPlayer!!.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_frame", 48)
            ijkPlayer!!.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48)
        } else {
            ijkPlayer!!.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec",
                if (SharedPreferencesUtil.getBoolean("player_codec", true)) 1 else 0)
            ijkPlayer!!.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48)
        }

        ijkPlayer!!.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles",
            if (SharedPreferencesUtil.getBoolean("player_audio", false)) 1 else 0)
        ijkPlayer!!.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1)
        ijkPlayer!!.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1)
        ijkPlayer!!.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 4)
        ijkPlayer!!.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1)
        ijkPlayer!!.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 100)
        ijkPlayer!!.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1)
        ijkPlayer!!.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1)
        ijkPlayer!!.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "flush_packets")
        ijkPlayer!!.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1)

        if (isOnlineVideo) {
            ijkPlayer!!.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1)
            ijkPlayer!!.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 15 * 1024 * 1024)
            ijkPlayer!!.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", NetWorkUtil.USER_AGENT_WEB)
            Logu.v("设置ua")
        }

        Logu.v("准备设置显示")
        if (SharedPreferencesUtil.getBoolean("player_display", Build.VERSION.SDK_INT < 26)) {
            Logu.v("使用texture模式")
            surfaceTimer = Timer()
            surfaceTimer!!.schedule(object : TimerTask() {
                override fun run() {
                    Logu.v("循环检测")
                    if (mSurfaceTexture != null) {
                        this.cancel()
                        val surface = Surface(mSurfaceTexture)
                        ijkPlayer!!.setSurface(surface)
                        MPPrepare(video_url!!)
                        Logu.v("设置surfaceTexture成功！")
                    }
                }
            }, 0, 200)
        } else {
            Logu.v("使用surface模式")
            val surfaceHolder = surfaceView!!.holder
            Logu.v("获取surfaceHolder成功！")
            surfaceTimer = Timer()
            surfaceTimer!!.schedule(object : TimerTask() {
                override fun run() {
                    Logu.v("循环检测")
                    if (!surfaceHolder.isCreating) {
                        this.cancel()
                        Logu.v("定时器结束！")
                        ijkPlayer!!.setDisplay(surfaceHolder)
                        Logu.v("设置surfaceHolder成功！")
                        surfaceHolder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                if (!destroyed) {
                                    Logu.v("surface", "重新设置Holder")
                                    ijkPlayer!!.setDisplay(holder)
                                    if (isPrepared) {
                                        ijkPlayer!!.seekTo(seekbar_progress.progress.toLong())
                                    }
                                }
                            }

                            override fun surfaceChanged(holder: SurfaceHolder, i: Int, i1: Int, i2: Int) {}
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                Logu.v("surface", "Holder没了")
                                if (isPrepared && !destroyed) ijkPlayer!!.setDisplay(null)
                            }
                        })
                        Logu.v("添加callback成功！")
                        MPPrepare(video_url!!)
                    }
                }
            }, 0, 200)
        }
    }

    private fun MPPrepare(nowurl: String) {
        ijkPlayer!!.setOnPreparedListener(this)

        if (isLiveMode) {
            runOnUiThread { loading_text0.text = "载入直播中" }
            danmuSocketConnect()
        } else
            runOnUiThread { loading_text0.text = "载入视频中" }
        try {
            if (isOnlineVideo) {
                val headers = HashMap<String, String>()
                headers["Referer"] = "https://www.bilibili.com/"
                headers["Cookie"] = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")
                ijkPlayer!!.setDataSource(nowurl, headers)
            } else
                ijkPlayer!!.setDataSource(nowurl)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        ijkPlayer!!.setOnCompletionListener {
            finishWatching = true

            if (interactionData != null && interactionData!!.edges != null &&
                interactionData!!.edges!!.questions != null && !questionShown) {
                checkEndInteractionQuestions()
                if (questionShown) {
                    isPlaying = false
                    if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.pause()
                    btn_control.setImageResource(R.drawable.btn_player_play)
                    return@setOnCompletionListener
                }
            }

            if (loop_enabled) {
                ijkPlayer!!.seekTo(0)
                if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.seekTo(0L)
                ijkPlayer!!.start()
            } else if (auto_next_enabled && hasMultiplePages() && currentPageIndex < pagenames!!.size - 1) {
                switchToPage(currentPageIndex + 1)
            } else {
                isPlaying = false
                if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.pause()
                btn_control.setImageResource(R.drawable.btn_player_play)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
                    updateMediaSessionPlaybackState()
                }
            }
        }

        ijkPlayer!!.setOnErrorListener { _, what, extra ->
            val EReport = "播放器可能遇到错误！\n错误码：" + what + "\n附加：" + extra
            Logu.e("ijk-err", EReport)
            false
        }

        ijkPlayer!!.setOnBufferingUpdateListener { _, percent ->
            seekbar_progress.secondaryProgress = percent * video_all / 100
        }

        if (isOnlineVideo || isLiveMode)
            ijkPlayer!!.setOnInfoListener { _, what, _ ->
                if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    runOnUiThread {
                        loading_info.visibility = View.VISIBLE
                        anim_loading!!.start()
                        loading_text0.text = "正在缓冲"
                        showLoadingSpeed()
                        if (hasDanmaku && mDanmakuView != null && isPlaying) mDanmakuView!!.pause()
                    }
                } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    runOnUiThread {
                        loadingTimer?.cancel()
                        loading_info.visibility = View.GONE
                        anim_loading!!.stop()
                        if (hasDanmaku && mDanmakuView != null && isPlaying) mDanmakuView!!.resume()
                    }
                }
                false
            }

        ijkPlayer!!.setScreenOnWhilePlaying(true)
        ijkPlayer!!.prepareAsync()
        Logu.v("开始准备")
    }

    private fun showLoadingSpeed() {
        loadingTimer = Timer()
        loadingTimer!!.schedule(object : TimerTask() {
            override fun run() {
                val text = String.format(Locale.CHINA, "%.1f", ijkPlayer!!.tcpSpeed / 1024f) + "KB/s"
                runOnUiThread { loading_text1.text = text }
            }
        }, 0, 500)
    }

    private fun changeVideoSize() {
        if (!isPrepared || ijkPlayer == null) return
        val width = ijkPlayer!!.videoWidth
        val height = ijkPlayer!!.videoHeight
        Logu.v("screen", screen_width.toString() + "x" + screen_height)
        Logu.v("video", width.toString() + "x" + height)

        if (width == 0 || height == 0) {
            Logu.v("视频尺寸", "视频宽高为0，跳过尺寸调整（可能处于听视频模式）")
            return
        }

        if (SharedPreferencesUtil.getBoolean("player_ui_round", false)) {
            val videoMul = height.toFloat() / width.toFloat()
            val sqrt = Math.sqrt((screen_width * screen_width / ((height.toDouble() * height / (width * width)) + 1)).toDouble())
            video_height = (sqrt * videoMul + 0.5).toInt()
            video_width = (sqrt + 0.5).toInt()
        } else {
            val widthCase1 = width * screen_height / height
            val heightCase2 = height * screen_width / width

            if (widthCase1 <= screen_width) {
                video_width = widthCase1
                video_height = screen_height
            } else {
                video_width = screen_width
                video_height = heightCase2
            }
        }

        runOnUiThread {
            layout_video.layoutParams = RelativeLayout.LayoutParams(video_width, video_height)
            Logu.v("改变视频区域大小", video_width.toString() + "x" + video_height)
            video_origX = (screen_width - video_width) / 2f
            video_origY = (screen_height - video_height) / 2f

            layout_video.postDelayed({
                layout_video.x = video_origX
                layout_video.y = video_origY
                Logu.v("改变视频位置", ((screen_width - video_width) / 2).toString() + "," + ((screen_height - video_height) / 2))
            }, 60)
        }
    }

    private fun progressChange() {
        progressTimer?.cancel()
        progressTimer = null
        progressTimer = Timer()
        val task = object : TimerTask() {
            @SuppressLint("SetTextI18n")
            override fun run() {
                if (isPrepared && isPlaying && !isSeeking) {
                    video_now = ijkPlayer!!.currentPosition.toInt()
                    if (video_now_last != video_now) {
                        video_now_last = video_now
                        val currSec = video_now / 1000f
                        runOnUiThread {
                            if (isLiveMode) {
                                text_progress.text = StringUtil.toTime(currSec.toInt())
                                text_online.text = online_number
                            } else {
                                seekbar_progress.progress = video_now
                            }
                            if (subtitles == null) text_subtitle.visibility = View.GONE

                            if (viewPointAdapter != null && viewPoints != null && viewPoints!!.isNotEmpty()) {
                                viewPointAdapter!!.updateCurrentPosition(currSec.toInt())
                            }
                        }
                        if (subtitles != null) showSubtitle(currSec + subtitle_delta)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
                            updateMediaSessionPlaybackState()
                        }
                    }
                }
            }
        }
        progressTimer!!.schedule(task, 0, 250)
    }

    private fun onlineChange() {
        if (!SharedPreferencesUtil.getBoolean("player_show_online", false) || isLiveMode || aid == 0L || cid == 0L)
            return

        onlineTimer?.cancel()
        onlineTimer = null
        onlineTimer = Timer()
        val task = object : TimerTask() {
            @SuppressLint("SetTextI18n")
            override fun run() {
                if (ijkPlayer != null) {
                    try {
                        online_number = VideoInfoApi.getWatching(aid, cid)
                        runOnUiThread {
                            if (online_number.isNotEmpty()) text_online.text = online_number + "人在看"
                            else text_online.text = ""
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            MsgUtil.err(e)
                            text_online.visibility = View.GONE
                        }
                        this.cancel()
                    }
                }
            }
        }
        onlineTimer!!.schedule(task, 0, 5000)
    }

    private fun getSubtitle(subtitleUrl: String) {
        if (subtitleUrl.isEmpty()) return
        try {
            subtitles = if (isOnlineVideo) PlayerApi.getSubtitle(subtitleUrl)
            else PlayerApi.getSubtitle(File(subtitleUrl))

            if (subtitles == null) return

            subtitle_count = subtitles!!.size
            subtitle_curr_index = 0
            runOnUiThread { btn_subtitle.setImageResource(R.mipmap.subtitle_on) }
        } catch (e: Exception) {
            MsgUtil.err(e)
        }
    }

    private fun showSubtitle(currSec: Float) {
        if (subtitles == null || subtitle_count == 0) {
            runOnUiThread { text_subtitle.visibility = View.GONE }
            return
        }

        var subtitleCurr = subtitles!![subtitle_curr_index]

        var needAdjust = true
        var needShow = true

        while (needAdjust) {
            if (currSec < subtitleCurr.from) {
                if (subtitle_curr_index != 0 && currSec < subtitles!![subtitle_curr_index - 1].to) {
                    subtitle_curr_index--
                } else {
                    needAdjust = false
                    needShow = false
                }
            } else if (currSec > subtitleCurr.to) {
                if (subtitle_curr_index + 1 < subtitle_count && currSec > subtitles!![subtitle_curr_index + 1].from) {
                    subtitle_curr_index++
                } else {
                    needAdjust = false
                    needShow = false
                }
            } else needAdjust = false
        }

        if (needShow)
            runOnUiThread {
                text_subtitle.text = subtitles!![subtitle_curr_index].content
                text_subtitle.visibility = View.VISIBLE
            }
        else runOnUiThread { text_subtitle.visibility = View.GONE }
    }

    private fun downSubtitle(fromBtn: Boolean) {
        try {
            if (subtitleLinks == null) {
                subtitleLinks = if (isOnlineVideo) PlayerApi.getSubtitleLinks(aid, cid)
                else PlayerApi.getSubtitleLinks(File(danmakuFile!!.parentFile, "subtitles"))
            }

            if (subtitleLinks!!.size == 1) {
                if (fromBtn) MsgUtil.showMsg("本视频无字幕")
                return
            }

            subtitle_delta = SharedPreferencesUtil.getFloat("player_subtitle_delta", 0.3f)

            val aiNotOnly = (subtitleLinks!!.size > 2 || (subtitleLinks!!.size == 2 && !subtitleLinks!![0].isAI))
            val aiAllowed = (fromBtn || SharedPreferencesUtil.getBoolean("player_subtitle_ai_allowed", false))

            if (aiNotOnly || aiAllowed) {
                if (subtitle_selected == -1) subtitle_selected = subtitleLinks!!.size

                runOnUiThread {
                    val subtitleRecycler = findViewById<RecyclerView>(R.id.subtitle_list)
                    val adapter = SubtitleAdapter()
                    adapter.setData(subtitleLinks!!)
                    adapter.selectedItemIndex = subtitle_selected
                    adapter.setOnItemClickListener { index ->
                        layout_card_bg.visibility = View.GONE
                        card_subtitle.visibility = View.GONE
                        subtitle_selected = index

                        if (subtitleLinks!![index].id == -1L) {
                            subtitles = null
                            btn_subtitle.setImageResource(R.mipmap.subtitle_off)
                        } else
                            CenterThreadPool.run { getSubtitle(subtitleLinks!![index].url) }
                    }
                    subtitleRecycler.layoutManager = CustomLinearManager(this, LinearLayoutManager.HORIZONTAL, false)
                    subtitleRecycler.setHasFixedSize(true)
                    subtitleRecycler.adapter = adapter
                    layout_card_bg.visibility = View.VISIBLE
                    card_subtitle.visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            MsgUtil.err(e)
        }
    }

    private fun downdanmu() {
        if (danmaku_url.isEmpty()) return

        val useNewApi = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NEW_DANMAKU_API, true)

        if (useNewApi) downdanmuNew()
        else downdanmuOld()
    }

    private fun downdanmuOld() {
        try {
            val response = NetWorkUtil.get(danmaku_url, NetWorkUtil.webHeaders)
            var bufferedSink: BufferedSink? = null
            try {
                if (!danmakuFile!!.exists()) danmakuFile!!.createNewFile()
                val sink = danmakuFile!!.sink()
                val decompressBytes = decompress(response.body!!.bytes())
                bufferedSink = sink.buffer()
                bufferedSink!!.write(decompressBytes)
                bufferedSink!!.close()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                bufferedSink?.close()
            }
            streamDanmaku(danmakuFile!!.toString(), null)
        } catch (e: Exception) {
            runOnUiThread { MsgUtil.err(e) }
        }
    }

    private fun downdanmuNew() {
        try {
            var estimatedDuration = 3600

            if (ijkPlayer != null) {
                val duration = ijkPlayer!!.duration
                if (duration > 0) estimatedDuration = (duration / 1000).toInt()
            }

            Logu.d("新版弹幕", "开始获取新版弹幕，aid=" + aid + ", cid=" + cid)

            val segments = DanmakuApi.getAllVideoDanmaku(aid, cid, estimatedDuration)

            if (segments.isEmpty()) {
                Logu.w("新版弹幕", "未获取到弹幕，尝试使用旧版接口")
                CenterThreadPool.run { downdanmuOld() }
                return
            }

            Logu.d("新版弹幕", "成功获取 " + segments.size + " 个弹幕分段")

            streamDanmaku(null, segments)
        } catch (e: Exception) {
            e.printStackTrace()
            Logu.e("新版弹幕", "获取失败: " + e.message + "，回退到旧版接口")
            runOnUiThread { MsgUtil.toast("新版弹幕获取失败，使用旧版接口") }
            CenterThreadPool.run { downdanmuOld() }
        }
    }

    private fun createParser(stream: String?): BaseDanmakuParser = createParser(stream, null)

    private fun createParser(stream: String?, protobufSegments: List<DmSegMobileReply>?): BaseDanmakuParser {
        if (protobufSegments != null && protobufSegments.isNotEmpty()) {
            val parser = BiliProtobufDanmakuParser()
            parser.sharedPreferences = SharedPreferencesUtil.getSharedPreferences()
            parser.setDanmakuSegments(protobufSegments)
            return parser
        }

        if (stream == null) {
            return object : BaseDanmakuParser() {
                override fun parse(): Danmakus = Danmakus()
            }
        }

        val loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI)!!
        loader.load(stream)
        val parser = BiliDanmukuParser()
        parser.sharedPreferences = SharedPreferencesUtil.getSharedPreferences()
        val dataSource: IDataSource<*> = loader.dataSource
        parser.load(dataSource)
        return parser
    }

    private fun streamDanmaku(danmakuFile: String?) = streamDanmaku(danmakuFile, null)

    private fun streamDanmaku(danmakuFile: String?, protobufSegments: List<DmSegMobileReply>?) {
        Logu.v("danmaku", "stream")

        mContext = DanmakuContext.create()
        val maxLinesPair = HashMap<Int, Int>()
        maxLinesPair[BaseDanmaku.TYPE_SCROLL_RL] = SharedPreferencesUtil.getInt("player_danmaku_maxline", 15)
        val overlap = HashMap<Int, Boolean>()
        overlap[BaseDanmaku.TYPE_SCROLL_LR] = SharedPreferencesUtil.getBoolean("player_danmaku_allowoverlap", true)
        overlap[BaseDanmaku.TYPE_FIX_BOTTOM] = SharedPreferencesUtil.getBoolean("player_danmaku_allowoverlap", true)
        mContext!!.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 1f)
            .setDuplicateMergingEnabled(SharedPreferencesUtil.getBoolean("player_danmaku_mergeduplicate", false))
            .setScrollSpeedFactor(SharedPreferencesUtil.getFloat("player_danmaku_speed", 1.0f))
            .setScaleTextSize(SharedPreferencesUtil.getFloat("player_danmaku_size", 0.7f))
            .setMaximumLines(maxLinesPair)
            .setDanmakuTransparency(SharedPreferencesUtil.getFloat("player_danmaku_transparency", 0.5f))
            .preventOverlapping(overlap)

        val mParser = createParser(danmakuFile, protobufSegments)

        mDanmakuView!!.setCallback(object : DrawHandler.Callback {
            override fun prepared() {
                Logu.v("danmaku", "prepared")
                val msg = if (protobufSegments != null) "弹幕君准备完毕～(是新来的哦～)" else "弹幕君准备完毕～(*≧ω≦)"
                addDanmaku(msg, Color.WHITE)
            }

            override fun updateTimer(timer: DanmakuTimer) {
                if (ijkPlayer != null && isPrepared) {
                    val currentPos = ijkPlayer!!.currentPosition
                    timer.update(currentPos)
                }
            }

            override fun danmakuShown(danmaku: BaseDanmaku) {}
            override fun drawingFinished() {}
        })
        mDanmakuView!!.enableDanmakuDrawingCache(true)
        mDanmakuView!!.prepare(mParser, mContext)
    }

    fun addDanmaku(text: String?, color: Int) = addDanmaku(text, color, 25, 1, 0)

    fun addDanmaku(text: String?, color: Int, textSize: Int, type: Int, backgroundColor: Int) {
        val danmaku = mContext!!.mDanmakuFactory.createDanmaku(type) ?: return
        if (text == null || ijkPlayer == null) return
        danmaku.text = text
        danmaku.padding = 5
        danmaku.priority = 1
        danmaku.textColor = color
        danmaku.backgroundColor = backgroundColor
        danmaku.textSize = textSize * (mContext!!.displayer.density - 0.6f)
        danmaku.time = mDanmakuView!!.currentTime + 100
        mDanmakuView!!.addDanmaku(danmaku)
    }

    fun controlVideo() {
        if (isPlaying) {
            playerPause()
        } else {
            if (video_now >= video_all - 250) {
                if (interactionData != null && interactionData!!.edges != null &&
                    interactionData!!.edges!!.questions != null && !questionShown) {
                    if (!questionShown) {
                        ijkPlayer!!.seekTo(0)
                        if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.seekTo(0L)
                        Logu.v("播完重播")
                    }
                } else {
                    ijkPlayer!!.seekTo(0)
                    if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.seekTo(0L)
                    Logu.v("播完重播")
                }
            }
            playerResume()
        }
        autohideReset()
    }

    @SuppressLint("SetTextI18n")
    fun changeVolume(addOrCut: Boolean) {
        var volumeNow = audioManager!!.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volumeMax = audioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumeNew = volumeNow + (if (addOrCut) 1 else -1)
        if (volumeNew in 0..volumeMax) {
            audioManager!!.setStreamVolume(AudioManager.STREAM_MUSIC, volumeNew, 0)
            volumeNow = volumeNew
        }
        val show = (volumeNow.toFloat() / volumeMax.toFloat() * 100).toInt()

        text_volume.visibility = View.VISIBLE
        text_volume.text = "音量：" + show + "%"

        text_volume.removeCallbacks(hideVolume)
        text_volume.postDelayed(hideVolume, 3000)
        autohideReset()
    }

    private val hideVolume = Runnable { text_volume.visibility = View.GONE }

    private fun softwareRotate() {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        screen_width = if (screen_landscape) displayMetrics.heightPixels else displayMetrics.widthPixels
        screen_height = if (screen_landscape) displayMetrics.widthPixels else displayMetrics.heightPixels

        val rootLayout = findViewById<ViewGroup>(R.id.root_layout)
        val params = rootLayout.layoutParams
        params.width = screen_width
        params.height = screen_height

        if (isPrepared && !destroyed)
            runOnUiThread {
                rootLayout.layoutParams = params
                rootLayout.pivotX = 0f
                rootLayout.pivotY = 0f
                rootLayout.x = if (screen_landscape) screen_height.toFloat() else 0f
                rootLayout.rotation = if (screen_landscape) 90f else 0f
                if (SharedPreferencesUtil.getBoolean("player_display", Build.VERSION.SDK_INT < 26)) {
                    if (textureView != null) {
                        val matrix = Matrix()
                        textureView!!.getTransform(matrix)
                        matrix.postRotate(0f)
                        textureView!!.setTransform(matrix)
                    }
                } else {
                    MsgUtil.showMsg("请切换为TextureView才能支持软件旋屏！")
                }
            }
        changeVideoSize()
    }

    private fun videoMoveBy(dx: Float, dy: Float) {
        var x = dx + layout_video.x
        var y = dy + layout_video.y

        val widthDelta = 0.5f * video_width * (layout_video.scaleX - 1f)
        val heightDelta = 0.5f * video_height * (layout_video.scaleY - 1f)
        val videoXMin = video_origX - widthDelta
        val videoXMax = video_origX + widthDelta
        val videoYMin = video_origY - heightDelta
        val videoYMax = video_origY + heightDelta

        if (x < videoXMin) x = videoXMin
        if (x > videoXMax) x = videoXMax
        if (y < videoYMin) y = videoYMin
        if (y > videoYMax) y = videoYMax

        if (layout_video.x != x || layout_video.y != y) {
            layout_video.x = x
            layout_video.y = y
            if (!gesture_moved && (abs(video_origX - x) > 5f || abs(video_origY - y) > 5f)) {
                gesture_moved = true
            }
        }
    }

    private fun playerPause() {
        isPlaying = false
        if (ijkPlayer != null && isPrepared) {
            ijkPlayer!!.pause()
            if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.pause()
        }
        audioPlayer?.pause()
        btn_control.setImageResource(R.drawable.btn_player_play)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
            updateMediaSessionPlaybackState()
        }
    }

    private fun playerResume() {
        isPlaying = true
        if (ijkPlayer != null && isPrepared) {
            ijkPlayer!!.start()
            if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.resume()
        }
        audioPlayer?.start()
        btn_control.setImageResource(R.drawable.btn_player_pause)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
            updateMediaSessionPlaybackState()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Logu.v("开始旋转屏幕")

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        screen_width = displayMetrics.widthPixels
        screen_height = displayMetrics.heightPixels
        changeVideoSize()

        Logu.v("旋转屏幕结束")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Logu.v("onNewIntent")
        finish()
    }

    override fun onPause() {
        super.onPause()
        Logu.v("onPause")
        if (!SharedPreferencesUtil.getBoolean("player_background", false)) {
            playerPause()
        }
    }

    override fun onResume() {
        super.onResume()
        Logu.v("onResume")
    }

    override fun onStop() {
        super.onStop()
        Logu.v("onStop")
    }

    override fun onDestroy() {
        if (!isFinishing) {
            super.onDestroy()
            return
        }

        Logu.v("结束")
        if (eventBusInit) {
            EventBus.getDefault().unregister(this)
            eventBusInit = false
        }
        destroyed = true

        cancelAllTimers()

        mDanmakuView?.release()
        mDanmakuView = null
        ijkPlayer?.release()
        ijkPlayer = null
        audioPlayer?.release()
        audioPlayer = null

        if (isOnlineVideo && danmakuFile != null && danmakuFile!!.exists())
            danmakuFile!!.delete()

        liveWebSocket?.close(1000, "")
        liveWebSocket = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
            mediaSession!!.release()
            mediaSession = null
        }

        requestedOrientation = if (SharedPreferencesUtil.getBoolean("ui_landscape", false))
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        super.onDestroy()
    }

    private fun cancelAllTimers() {
        surfaceTimer?.cancel()
        surfaceTimer = null
        progressTimer?.cancel()
        progressTimer = null
        onlineTimer?.cancel()
        onlineTimer = null
        loadingTimer?.cancel()
        loadingTimer = null
        mainHandler?.removeCallbacksAndMessages(null)
        layout_control.removeCallbacks(hidecon)
        text_volume.removeCallbacks(hideVolume)
        seekbar_progress.removeCallbacks(progressbarEnable)
    }

    private fun danmuSocketConnect() {
        CenterThreadPool.run {
            try {
                var url = "https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo?type=0&id=" + aid
                val mHeaders = ArrayList<String>().apply {
                    add("Cookie")
                    add(SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))
                    add("Referer")
                    add("https://live.bilibili.com/" + aid)
                    add("Origin")
                    add("https://live.bilibili.com")
                    add("User-Agent")
                    add(NetWorkUtil.USER_AGENT_WEB)
                }
                val response = NetWorkUtil.get(ConfInfoApi.signWBI(url), mHeaders)
                val data = JSONObject(response.body!!.string()).getJSONObject("data")
                val host = data.getJSONArray("host_list").getJSONObject(0)

                url = "wss://" + host.getString("host") + ":" + host.getInt("wss_port") + "/sub"
                Logu.v("连接WebSocket", url)

                okHttpClient = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .header("Cookie", SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))
                    .header("Origin", "https://live.bilibili.com")
                    .header("User-Agent", NetWorkUtil.USER_AGENT_WEB)
                    .build()

                val listener = PlayerDanmuClientListener()
                listener.mid = mid
                listener.roomid = aid
                listener.key = data.getString("token")
                listener.playerActivity = this

                liveWebSocket = okHttpClient!!.newWebSocket(request, listener)
            } catch (e: Exception) {
                MsgUtil.showMsg("直播弹幕连接失败")
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("WrongConstant")
    private fun initMediaSession() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        mediaSession = MediaSession(this, "BiliClientPlayer")
        mediaSession!!.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mediaSession!!.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                super.onPlay()
                runOnUiThread {
                    if (!isPlaying) {
                        playerResume()
                        updateMediaSessionPlaybackState()
                    }
                }
            }

            override fun onPause() {
                super.onPause()
                runOnUiThread {
                    if (isPlaying) {
                        playerPause()
                        updateMediaSessionPlaybackState()
                    }
                }
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                runOnUiThread {
                    if (hasMultiplePages() && currentPageIndex < pagenames!!.size - 1) {
                        switchToPage(currentPageIndex + 1)
                    }
                }
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                runOnUiThread {
                    if (hasMultiplePages() && currentPageIndex > 0) {
                        switchToPage(currentPageIndex - 1)
                    }
                }
            }

            override fun onSeekTo(pos: Long) {
                super.onSeekTo(pos)
                runOnUiThread {
                    seekToPosition(pos)
                    updateMediaSessionPlaybackState()
                }
            }
        })
        mediaSession!!.isActive = true
    }

    private fun updateMediaSessionMetadata() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || mediaSession == null) return
        val metadataBuilder = MediaMetadata.Builder()
        if (videoTitle != null) metadataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, videoTitle)
        if (video_all > 0) metadataBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, video_all.toLong())
        mediaSession!!.setMetadata(metadataBuilder.build())
    }

    private fun updateMediaSessionPlaybackState() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || mediaSession == null) return
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val position = if (isPrepared && ijkPlayer != null) ijkPlayer!!.currentPosition else 0
        var actions = (PlaybackState.ACTION_PLAY.toLong()
                or PlaybackState.ACTION_PAUSE.toLong()
                or PlaybackState.ACTION_SEEK_TO.toLong()
                or PlaybackState.ACTION_SKIP_TO_NEXT.toLong()
                or PlaybackState.ACTION_SKIP_TO_PREVIOUS.toLong())
        if (!hasMultiplePages() || currentPageIndex >= pagenames!!.size - 1) {
            actions = actions and PlaybackState.ACTION_SKIP_TO_NEXT.inv()
        }
        if (!hasMultiplePages() || currentPageIndex <= 0) {
            actions = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS.inv()
        }
        val stateBuilder = PlaybackState.Builder()
            .setState(state, position, 1.0f)
            .setActions(actions)
        mediaSession!!.setPlaybackState(stateBuilder.build())
    }

    private fun initUI() {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        screen_width = displayMetrics.widthPixels
        screen_height = displayMetrics.heightPixels

        if (SharedPreferencesUtil.getBoolean("player_ui_showRotateBtn", true))
            btn_rotate.visibility = View.VISIBLE
        else btn_rotate.visibility = View.GONE

        screen_round = SharedPreferencesUtil.getBoolean("player_ui_round", false)
        if (screen_round) {
            val padding = (screen_width * 0.03).toInt()

            val progressParams = seekbar_progress.layoutParams as LinearLayout.LayoutParams
            progressParams.leftMargin = padding * 4
            progressParams.rightMargin = padding * 4
            seekbar_progress.layoutParams = progressParams

            text_online.setPadding(0, 0, padding * 3, 0)
            text_progress.setPadding(padding * 3, 0, 0, 0)

            bottom_buttons.setPadding(padding, 0, padding, padding)

            right_control.setPadding(0, 0, padding, 0)

            val danmakuParams = mDanmakuView!!.layoutParams as RelativeLayout.LayoutParams
            danmakuParams.setMargins(0, padding * 3, 0, padding * 3)
            mDanmakuView!!.layoutParams = danmakuParams

            text_subtitle.maxWidth = (screen_width * 0.65).toInt()

            layout_top.setPadding(padding * 7, padding, padding * 7, 0)

            val clockLayout = findViewById<LinearLayout>(R.id.clock_layout)
            clockLayout.orientation = LinearLayout.HORIZONTAL
            val clockLayoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            clockLayoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
            clockLayout.layoutParams = clockLayoutParams

            val titleParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            titleParams.addRule(RelativeLayout.BELOW, R.id.clock_layout)
            titleParams.topMargin = padding / 2
            text_title!!.layoutParams = titleParams
            text_title!!.gravity = Gravity.CENTER

            val textClock = findViewById<TextView>(R.id.clock)
            val textClockParams = textClock.layoutParams as LinearLayout.LayoutParams
            textClockParams.leftMargin = padding / 2
            textClockParams.topMargin = padding / 4
            textClock.layoutParams = textClockParams
        }

        if (!SharedPreferencesUtil.getBoolean("player_show_online", false) || aid == 0L || cid == 0L)
            text_online.visibility = View.GONE

        layout_top.setOnClickListener { finish() }

        val params = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        if (SharedPreferencesUtil.getBoolean("player_display", Build.VERSION.SDK_INT < 26)) {
            textureView = TextureView(this)
            textureView!!.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, i: Int, i1: Int) {
                    Logu.v("surfacetexture", "available")
                    mSurfaceTexture = surfaceTexture
                    if (isPrepared && ijkPlayer != null) ijkPlayer!!.setSurface(Surface(surfaceTexture))
                }

                override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, i: Int, i1: Int) {
                    Logu.v("surfacetexture", "sizechanged")
                }

                override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                    Logu.v("surfacetexture", "destroyed")
                    mSurfaceTexture = null
                    ijkPlayer?.setSurface(null)
                    return true
                }

                override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
            }
            layout_video.addView(textureView, params)
        } else {
            surfaceView = SurfaceView(this)
            layout_video.addView(surfaceView, params)
        }

        btn_rotate.setOnClickListener {
            Logu.v("点击旋转按钮")
            screen_landscape = !screen_landscape
            if (SharedPreferencesUtil.getBoolean("dev_player_rotate_software", false)) softwareRotate()
            else requestedOrientation = if (screen_landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        findViewById<View>(R.id.button_sound_add).setOnClickListener { changeVolume(true) }
        findViewById<View>(R.id.button_sound_cut).setOnClickListener { changeVolume(false) }

        btn_menu.setOnClickListener {
            if (menu_opened) {
                right_second.visibility = View.GONE
                btn_menu.setImageResource(R.mipmap.morehide)
            } else {
                right_second.visibility = View.VISIBLE
                btn_menu.setImageResource(R.mipmap.moreshow)
            }
            menu_opened = !menu_opened
        }

        layout_card_bg.setOnClickListener {
            layout_card_bg.visibility = View.GONE
            card_subtitle.visibility = View.GONE
            card_danmaku_send.visibility = View.GONE
            card_page_selector.visibility = View.GONE
            card_quality_selector.visibility = View.GONE
            card_viewpoint_selector.visibility = View.GONE
        }
        btn_danmaku_send.setOnClickListener {
            layout_card_bg.visibility = View.VISIBLE
            card_danmaku_send.visibility = View.VISIBLE
        }
        findViewById<View>(R.id.danmaku_send).setOnClickListener {
            val editText = findViewById<EditText>(R.id.danmaku_send_edit)
            if (editText.text.toString().isEmpty()) {
                MsgUtil.showMsg("不能发送空弹幕喵")
            } else {
                layout_card_bg.visibility = View.GONE
                card_danmaku_send.visibility = View.GONE

                CenterThreadPool.run {
                    try {
                        MsgUtil.showMsg("正在发送~")

                        val result = DanmakuApi.sendVideoDanmakuByAid(cid, editText.text.toString(), aid,
                            video_now.toLong(), ToolsUtil.getRgb888(Color.WHITE), 1)

                        if (result == 0) {
                            MsgUtil.showMsg("发送成功喵~")
                            runOnUiThread {
                                addDanmaku(editText.text.toString(), Color.WHITE)
                                editText.setText("")
                            }
                        } else
                            MsgUtil.showMsg("发送失败：" + result)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        MsgUtil.err(e)
                    }
                }
            }
        }
    }

    private fun initSeekbars() {
        seekbar_progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            @SuppressLint("SetTextI18n")
            override fun onProgressChanged(seekBar: SeekBar, position: Int, fromUser: Boolean) {
                runOnUiThread {
                    if (!isLiveMode) text_progress.text = StringUtil.toTime(position / 1000) + "/" + progress_str
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isSeeking = false
                if (isPrepared && !destroyed) {
                    val seekPos = seekbar_progress.progress
                    ijkPlayer!!.seekTo(seekPos.toLong())
                    if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.seekTo(seekPos.toLong())
                    autohideReset()
                }
            }
        })

        seekbar_speed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, position: Int, fromUser: Boolean) {
                if (fromUser) {
                    text_newspeed.text = speed_strs[position]
                    text_speed.text = speed_strs[position]
                    ijkPlayer?.setSpeed(speed_values[position])
                    mDanmakuView?.setSpeed(speed_values[position])
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                speedTimer?.cancel()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                speedTimer = Timer()
                val timerTask = object : TimerTask() {
                    override fun run() {
                        runOnUiThread { layout_speed.visibility = View.GONE }
                    }
                }
                speedTimer!!.schedule(timerTask, 200)
            }
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isPrepared)
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> controlVideo()
                KeyEvent.KEYCODE_DPAD_LEFT -> seekToPosition(ijkPlayer!!.currentPosition - 10000L)
                KeyEvent.KEYCODE_DPAD_RIGHT -> seekToPosition(ijkPlayer!!.currentPosition + 10000L)
                KeyEvent.KEYCODE_DPAD_UP -> changeVolume(true)
                KeyEvent.KEYCODE_DPAD_DOWN -> changeVolume(false)
            }
        return super.onKeyDown(keyCode, event)
    }

    private fun seekToPosition(position: Long) {
        if (ijkPlayer != null && isPrepared) {
            ijkPlayer!!.seekTo(position)
            if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.seekTo(position)
            // 同步外部音频轨道
            if (audioPlayer != null) {
                try { audioPlayer!!.seekTo(position.toInt()) } catch (_: Exception) {}
            }
        }
    }

    /**
     * 初始化外部音频轨道（DASH分离文件合并失败时的fallback）
     * 使用Android MediaPlayer单独播放音频，与IJK视频播放器同步
     */
    private fun initAudioTrack() {
        val url = audioTrackUrl ?: return
        val audioFile = File(url)
        if (!audioFile.exists()) {
            Logu.e("AudioTrack", "音频文件不存在: $url")
            return
        }
        try {
            audioPlayer = android.media.MediaPlayer().apply {
                setDataSource(url)
                prepare()
                // 与视频播放器保持同步的初始位置
                if (ijkPlayer != null && isPrepared) {
                    seekTo(ijkPlayer!!.currentPosition.toInt())
                }
                isLooping = false
                start()
            }
            Logu.d("AudioTrack", "外部音频轨道已启动")
        } catch (e: Exception) {
            Logu.e("AudioTrack", "音频轨道初始化失败: ${e.message}")
            audioPlayer?.release()
            audioPlayer = null
        }
    }

    private fun toggleAudioOnlyMode() {
        val oldMode = isAudioOnlyMode
        isAudioOnlyMode = !isAudioOnlyMode

        if (isPrepared && ijkPlayer != null) {
            val currentPosition = ijkPlayer!!.currentPosition
            val wasPlaying = isPlaying

            MsgUtil.showMsg(if (isAudioOnlyMode) "正在切换到听视频模式..." else "正在切换到普通模式...")

            CenterThreadPool.run {
                try {
                    runOnUiThread {
                        if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.pause()
                        if (ijkPlayer != null) {
                            ijkPlayer!!.stop()
                            ijkPlayer!!.release()
                        }

                        loading_info.visibility = View.VISIBLE
                        anim_loading!!.start()
                        loading_text0.text = if (isAudioOnlyMode) "切换到听视频模式" else "切换到普通模式"
                        isPrepared = false
                        isPlaying = false

                        updateAudioOnlyButton()
                        updateAudioOnlyUI()
                    }

                    Thread.sleep(100)

                    runOnUiThread {
                        ijkPlayer = IjkMediaPlayer()
                        progress_history = currentPosition
                        setDisplay()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        MsgUtil.showMsg("切换失败，请重试")
                        isAudioOnlyMode = oldMode
                        updateAudioOnlyButton()
                        updateAudioOnlyUI()
                        loading_info.visibility = View.GONE
                        anim_loading!!.stop()
                    }
                }
            }
        } else {
            updateAudioOnlyButton()
            updateAudioOnlyUI()
            MsgUtil.showMsg(if (isAudioOnlyMode) "已切换到听视频模式" else "已切换到普通模式")
        }
    }

    private fun updateAudioOnlyButton() {
        btn_audio_only.setImageResource(if (isAudioOnlyMode) R.drawable.icon_audio_only_on else R.drawable.icon_audio_only_off)
    }

    private fun updateAudioOnlyUI() {
        runOnUiThread {
            if (isAudioOnlyMode) {
                text_speed.visibility = View.GONE
                btn_debug.visibility = View.GONE
                btn_danmaku.visibility = View.GONE
                layout_video.visibility = View.GONE
                layout_audio_only.visibility = View.VISIBLE
                mDanmakuView?.setVisibility(View.GONE)
                if (text_audio_title != null) {
                    val title = text_title!!.text.toString()
                    text_audio_title.text = if (title.isEmpty()) "听视频模式" else title
                }
            } else {
                text_speed.visibility = View.VISIBLE
                updateDebugButtonVisibility()
                btn_danmaku.visibility = View.VISIBLE
                layout_video.visibility = View.VISIBLE
                layout_audio_only.visibility = View.GONE
                if (mDanmakuView != null && isDanmakuVisible) mDanmakuView!!.setVisibility(View.VISIBLE)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (eventBusEnabled() && !eventBusInit) {
            EventBus.getDefault().register(this)
            Logu.v("event", "register")
            eventBusInit = true
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onEvent(snackEvent: SnackEvent) {
        if (isFinishing) return
        Logu.v("event", "onEvent")

        val currentTime = System.currentTimeMillis()

        val duration = when {
            snackEvent.duration > 0 -> snackEvent.duration
            snackEvent.duration == Snackbar.LENGTH_SHORT -> 1950
            snackEvent.duration == Snackbar.LENGTH_INDEFINITE -> Int.MAX_VALUE
            else -> 2750
        }

        val endTime = snackEvent.startTime + duration
        if (currentTime >= endTime) {
            EventBus.getDefault().removeStickyEvent(snackEvent)
        } else {
            MsgUtil.toast(snackEvent.message)
        }
    }

    protected fun eventBusEnabled(): Boolean {
        return SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.SNACKBAR_ENABLE, true)
    }

    private fun loadHighEnergyData() {
        if (!SharedPreferencesUtil.getBoolean("player_high_energy", false)) {
            Logu.d("高能进度条", "功能已禁用")
            return
        }

        CenterThreadPool.run {
            try {
                Logu.d("高能进度条", "开始加载数据 aid=" + aid + " cid=" + cid)
                val data = PlayerApi.getHighEnergyData(cid, aid)

                if (data != null && data.hasValidData()) {
                    runOnUiThread {
                        if (!destroyed) {
                            seekbar_progress.setHighEnergyData(data.events, data.stepSec)
                            Logu.d("高能进度条", "数据加载成功并设置到进度条")
                        }
                    }
                } else {
                    Logu.w("高能进度条", "未获取到有效数据")
                }
            } catch (e: Exception) {
                Logu.e("高能进度条", "加载失败: " + e.message)
                e.printStackTrace()
            }
        }
    }

    private fun hasMultiplePages(): Boolean = pagenames != null && pagenames!!.size > 1

    private fun showPageSelectorCard() {
        if (!hasMultiplePages()) return

        runOnUiThread {
            val pageSelectorRecycler = findViewById<RecyclerView>(R.id.page_selector_list)
            val adapter = PageSelectorAdapter()
            adapter.setData(pagenames!!, currentPageIndex)
            adapter.setOnItemClickListener { index ->
                layout_card_bg.visibility = View.GONE
                card_page_selector.visibility = View.GONE
                if (index != currentPageIndex) switchToPage(index)
            }
            pageSelectorRecycler.layoutManager = CustomLinearManager(this)
            pageSelectorRecycler.adapter = adapter
            layout_card_bg.visibility = View.VISIBLE
            card_page_selector.visibility = View.VISIBLE
        }
    }

    private fun switchToPage(pageIndex: Int) {
        if (!hasMultiplePages() || pageIndex < 0 || pageIndex >= pagenames!!.size) return
        if (pageIndex == currentPageIndex) return

        currentPageIndex = pageIndex
        val newTitle = pagenames!![pageIndex]

        MsgUtil.showMsg("切换到 P" + (pageIndex + 1))

        if (isOnlineVideo) {
            switchToOnlinePage(pageIndex, newTitle)
        } else {
            switchToLocalPage(pageIndex, newTitle)
        }
    }

    /**
     * 切换到在线视频的指定分页
     * 支持两种场景：
     * 1. 多P视频：cids中存储的是真实cid，直接使用
     * 2. 虚拟合集（收藏夹）：cids中存储的是aid，需要通过aid获取视频信息
     */
    private fun switchToOnlinePage(pageIndex: Int, newTitle: String) {
        CenterThreadPool.run {
            try {
                val playerData = PlayerData()
                val cidValue = cids!![pageIndex]

                // 判断是虚拟合集模式（aid != 当前页的cid值，说明cids存的是aid）
                val isVirtualCollection = aid != cidValue && cidValue > 0 && pagenames!!.size > 1

                if (isVirtualCollection) {
                    // 虚拟合集模式：通过aid获取视频信息
                    playerData.aid = cidValue
                    playerData.cid = 0 // 需要从API获取
                    val videoInfo = com.RobinNotBad.BiliClient.api.VideoInfoApi.getVideoInfo(cidValue)
                    if (videoInfo != null && videoInfo.cids.isNotEmpty()) {
                        playerData.cid = videoInfo.cids[0]
                    }
                } else {
                    playerData.aid = aid
                    playerData.cid = cidValue
                }

                playerData.title = newTitle
                playerData.mid = mid
                playerData.qn = SharedPreferencesUtil.getInt("play_qn", 16)
                playerData.pagenames = pagenames
                playerData.cids = cids
                playerData.currentPageIndex = currentPageIndex

                PlayerApi.getVideo(playerData, false)

                runOnUiThread {
                    if (destroyed) return@runOnUiThread
                    doSwitchPage(newTitle, playerData.videoUrl, playerData.danmakuUrl, pageIndex)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    MsgUtil.err(e)
                    MsgUtil.showMsg("切换失败")
                }
            }
        }
    }

    /**
     * 切换到本地虚拟合集的指定分页
     */
    private fun switchToLocalPage(pageIndex: Int, newTitle: String) {
        // 从 intent 中获取视频文件和弹幕文件列表
        val videoFileList = intent.getStringArrayListExtra("videoFileList") ?: return
        val danmakuFileList = intent.getStringArrayListExtra("danmakuFileList")

        if (pageIndex < 0 || pageIndex >= videoFileList.size) return

        val videoUrl = videoFileList[pageIndex]
        val danmakuUrl = danmakuFileList?.getOrElse(pageIndex) { "" } ?: ""

        runOnUiThread {
            doSwitchPage(newTitle, videoUrl, danmakuUrl, pageIndex)
        }
    }

    /**
     * 执行分页切换的通用UI操作
     */
    private fun doSwitchPage(newTitle: String, newVideoUrl: String, newDanmakuUrl: String, pageIndex: Int) {
        if (destroyed) return

        ijkPlayer?.stop()
        ijkPlayer?.release()
        mDanmakuView?.release()
        mDanmakuView = null

        video_url = newVideoUrl
        danmaku_url = newDanmakuUrl
        text_title!!.text = newTitle
        videoTitle = newTitle

        loading_info.visibility = View.VISIBLE
        anim_loading!!.start()
        loading_text0.text = "加载P" + (pageIndex + 1)
        isPrepared = false
        isPlaying = false
        finishWatching = false
        progress_history = 0
        subtitles = null
        subtitleLinks = null
        subtitle_selected = -1
        viewPoints = null
        viewPointAdapter = null
        btn_viewpoint.visibility = View.GONE

        interactionData = null
        currentEdgeId = 0
        currentQuestion = null
        questionShown = false
        interactionChoiceLayout?.visibility = View.GONE
        interactionChoiceLayout?.removeAllViews()

        ijkPlayer = IjkMediaPlayer()
        mDanmakuView = findViewById(R.id.sv_danmaku)

        setDisplay()

        layout_control.postDelayed({
            CenterThreadPool.run {
                if (destroyed) return@run

                runOnUiThread {
                    loading_text0.text = "装填弹幕中"
                    loading_text1.text = "(≧∇≦)"
                }

                if (isOnlineVideo) {
                    danmakuFile = File(cacheDir, "danmaku.xml")
                    if (danmakuFile!!.exists()) danmakuFile!!.delete()
                    downdanmu()
                }

                if (!destroyed && SharedPreferencesUtil.getBoolean("player_subtitle_autoshow", true)) {
                    downSubtitle(false)
                }

                if (!destroyed && isOnlineVideo && aid > 0 && cid > 0) {
                    loadHighEnergyData()
                }

                if (!destroyed && isOnlineVideo && aid > 0 && cid > 0 && SharedPreferencesUtil.getBoolean("player_show_viewpoints", true)) {
                    loadViewPoints()
                }

                if (!destroyed && isOnlineVideo && aid > 0 && cid > 0) {
                    loadInteractionVideo()
                }
            }
        }, 60)
    }

    private fun toggleAutoNext() {
        auto_next_enabled = !auto_next_enabled
        updateAutoNextButton()
        MsgUtil.showMsg(if (auto_next_enabled) "已开启自动连播" else "已关闭自动连播")
    }

    private fun updateAutoNextButton() {
        btn_auto_next.setImageResource(if (auto_next_enabled) R.drawable.icon_auto_next_on else R.drawable.icon_auto_next_off)
    }

    private fun showQualitySelectorCard() {
        if (qnStrList == null || qnValueList == null || qnStrList!!.isEmpty()) {
            MsgUtil.showMsg("清晰度列表未加载")
            return
        }

        runOnUiThread {
            val qualitySelectorRecycler = findViewById<RecyclerView>(R.id.quality_selector_list)
            val adapter = QualitySelectorAdapter()
            adapter.setData(qnStrList!!, qnValueList!!, currentQuality)
            adapter.setOnItemClickListener { index ->
                layout_card_bg.visibility = View.GONE
                card_quality_selector.visibility = View.GONE
                if (index >= 0 && index < qnValueList!!.size && qnValueList!![index] != currentQuality) {
                    switchQuality(qnValueList!![index])
                }
            }
            qualitySelectorRecycler.layoutManager = CustomLinearManager(this, LinearLayoutManager.HORIZONTAL, false)
            qualitySelectorRecycler.adapter = adapter
            layout_card_bg.visibility = View.VISIBLE
            card_quality_selector.visibility = View.VISIBLE
        }
    }

    private fun switchQuality(newQuality: Int) {
        if (!isOnlineVideo || newQuality == currentQuality) return

        MsgUtil.showMsg("正在切换清晰度...")

        CenterThreadPool.run {
            try {
                val playerData = PlayerData()
                playerData.aid = aid
                playerData.cid = cid
                playerData.title = text_title!!.text.toString()
                playerData.mid = mid
                playerData.qn = newQuality
                playerData.pagenames = pagenames
                playerData.cids = cids
                playerData.currentPageIndex = currentPageIndex

                PlayerApi.getVideo(playerData, false)

                runOnUiThread {
                    if (destroyed) return@runOnUiThread

                    val currentPosition = ijkPlayer?.currentPosition ?: 0
                    val wasPlaying = isPlaying

                    ijkPlayer?.stop()
                    ijkPlayer?.release()

                    video_url = playerData.videoUrl
                    currentQuality = newQuality

                    if (playerData.qnStrList != null && playerData.qnValueList != null) {
                        qnStrList = playerData.qnStrList
                        qnValueList = playerData.qnValueList
                    }

                    loading_info.visibility = View.VISIBLE
                    anim_loading!!.start()
                    loading_text0.text = "切换清晰度中"
                    isPrepared = false
                    isPlaying = false

                    ijkPlayer = IjkMediaPlayer()
                    progress_history = currentPosition

                    setDisplay()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    MsgUtil.err(e)
                    MsgUtil.showMsg("清晰度切换失败")
                }
            }
        }
    }

    private fun loadViewPoints() {
        CenterThreadPool.run {
            try {
                Logu.d("视频分段", "开始加载分段数据 aid=" + aid + " cid=" + cid)
                viewPoints = PlayerApi.getViewPoints(aid, cid)

                if (viewPoints != null && viewPoints!!.isNotEmpty()) {
                    runOnUiThread {
                        if (!destroyed) {
                            btn_viewpoint.visibility = View.VISIBLE
                            btn_viewpoint.setOnClickListener { showViewPointSelectorCard() }
                            Logu.d("视频分段", "成功加载 " + viewPoints!!.size + " 个分段")
                        }
                    }
                } else {
                    Logu.d("视频分段", "未获取到分段数据")
                }
            } catch (e: Exception) {
                Logu.e("视频分段", "加载失败: " + e.message)
                e.printStackTrace()
            }
        }
    }

    private fun showViewPointSelectorCard() {
        if (viewPoints == null || viewPoints!!.isEmpty()) return

        runOnUiThread {
            val viewPointRecycler = findViewById<RecyclerView>(R.id.viewpoint_selector_list)
            if (viewPointAdapter == null) {
                viewPointAdapter = ViewPointAdapter()
                viewPointAdapter!!.setData(viewPoints!!)
                viewPointAdapter!!.setOnItemClickListener { index ->
                    layout_card_bg.visibility = View.GONE
                    card_viewpoint_selector.visibility = View.GONE
                    if (index >= 0 && index < viewPoints!!.size) {
                        val vp = viewPoints!![index]
                        seekToPosition(vp.from * 1000L)
                        MsgUtil.showMsg("跳转到: " + vp.content)
                    }
                }
                viewPointRecycler.layoutManager = CustomLinearManager(this, LinearLayoutManager.HORIZONTAL, false)
                viewPointRecycler.adapter = viewPointAdapter
            }
            if (ijkPlayer != null && isPrepared) {
                val currentPos = (ijkPlayer!!.currentPosition / 1000).toInt()
                viewPointAdapter!!.updateCurrentPosition(currentPos)
            }
            layout_card_bg.visibility = View.VISIBLE
            card_viewpoint_selector.visibility = View.VISIBLE
        }
    }

    private fun loadInteractionVideo() {
        CenterThreadPool.run {
            try {
                val graphVersion = PlayerApi.getInteractionGraphVersion(aid, cid)
                if (graphVersion > 0) {
                    interactionGraphVersion = graphVersion
                    Logu.d("互动视频", "检测到互动视频，graph_version: " + interactionGraphVersion + ", cid: " + cid)

                    runOnUiThread {
                        questionShown = false
                        currentQuestion = null
                        interactionChoiceLayout?.visibility = View.GONE
                        interactionChoiceLayout?.removeAllViews()
                    }

                    var edgeId: Long = 0
                    if (initialEdgeId > 0) {
                        edgeId = initialEdgeId
                        initialEdgeId = 0
                    } else if (interactionData != null && currentEdgeId > 0) {
                        edgeId = currentEdgeId
                    }

                    interactionData = InteractionVideoApi.getEdgeInfo(aid, null, interactionGraphVersion, edgeId)
                    if (interactionData != null) {
                        currentEdgeId = interactionData!!.edgeId
                        Logu.d("互动视频", "成功加载互动视频数据，edge_id: " + currentEdgeId)
                        runOnUiThread { updateDebugButtonVisibility() }
                    }
                } else {
                    interactionData = null
                    currentEdgeId = 0
                    runOnUiThread {
                        questionShown = false
                        currentQuestion = null
                        updateDebugButtonVisibility()
                    }
                }
            } catch (e: Exception) {
                Logu.e("互动视频", "加载失败: " + e.message)
                e.printStackTrace()
                interactionData = null
                currentEdgeId = 0
                runOnUiThread {
                    questionShown = false
                    currentQuestion = null
                    updateDebugButtonVisibility()
                }
            }
        }
    }

    private fun updateDebugButtonVisibility() {
        val debugEnabled = SharedPreferencesUtil.getBoolean("player_interaction_debug", false)
        if (debugEnabled && interactionData != null && interactionData!!.hiddenVars != null && !interactionData!!.hiddenVars!!.isEmpty() && !isLiveMode && !isAudioOnlyMode) {
            btn_debug.visibility = layout_top.visibility
        } else {
            btn_debug.visibility = View.GONE
        }
    }

    private fun checkEndInteractionQuestions() {
        if (interactionData == null || interactionData!!.edges == null ||
            interactionData!!.edges!!.questions == null || questionShown) return

        for (question in interactionData!!.edges!!.questions!!) {
            if (question.type == 0) {
                if (question.choices != null && question.choices!!.isNotEmpty()) {
                    for (choice in question.choices!!) {
                        if (choice.isHidden == 1) continue

                        if (choice.condition != null && choice.condition!!.isNotEmpty()) {
                            if (!evaluateCondition(choice.condition!!)) continue
                        }

                        handleChoiceSelection(choice)
                        break
                    }
                }
                continue
            }
            showInteractionQuestion(question)
        }
    }

    private fun showInteractionQuestion(question: InteractionVideoData.InteractionQuestion) {
        if (questionShown || question.choices == null || question.choices!!.isEmpty()) return

        runOnUiThread {
            questionShown = true
            currentQuestion = question

            if (question.pauseVideo == 1 && isPlaying) {
                ijkPlayer!!.pause()
                isPlaying = false
                btn_control.setImageResource(R.drawable.btn_player_play)
            }

            if (interactionChoiceLayout == null) createInteractionChoiceLayout()

            interactionChoiceLayout!!.removeAllViews()

            for (choice in question.choices!!) {
                if (choice.isHidden == 1) continue

                if (choice.condition != null && choice.condition!!.isNotEmpty()) {
                    if (!evaluateCondition(choice.condition!!)) continue
                }

                val choiceView = createChoiceView(choice)
                interactionChoiceLayout!!.addView(choiceView)
            }

            if (interactionChoiceLayout!!.childCount > 0) {
                interactionChoiceLayout!!.visibility = View.VISIBLE
            }
        }
    }

    private fun createChoiceView(choice: InteractionVideoData.InteractionChoice): TextView {
        val choiceView = LayoutInflater.from(this).inflate(R.layout.cell_interaction_choice, null) as TextView
        choiceView.text = choice.option
        val fontSize = SharedPreferencesUtil.getFloat("player_interaction_choice_size", 17.0f)
        choiceView.textSize = fontSize
        choiceView.setOnClickListener { handleChoiceSelection(choice) }
        return choiceView
    }

    private fun createInteractionChoiceLayout() {
        val rootLayout = findViewById<RelativeLayout>(R.id.root_layout)
        interactionChoiceLayout = LinearLayout(this)
        interactionChoiceLayout!!.orientation = LinearLayout.VERTICAL
        interactionChoiceLayout!!.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL

        val params = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        params.setMargins(0, 0, 0, 100)

        interactionChoiceLayout!!.layoutParams = params
        interactionChoiceLayout!!.visibility = View.GONE
        rootLayout.addView(interactionChoiceLayout)
    }

    private fun evaluateCondition(condition: String): Boolean {
        if (interactionData == null || interactionData!!.hiddenVars == null || condition.isEmpty()) return true

        try {
            var result = condition
            for (v in interactionData!!.hiddenVars!!) {
                if (v.idV2 != null && v.idV2!!.isNotEmpty()) {
                    val pattern = "\\b" + Pattern.quote(v.idV2!!) + "\\b"
                    result = result.replace(pattern.toRegex(), v.value.toString())
                }
                if (v.id != null && v.id!!.isNotEmpty() && v.id != v.idV2) {
                    val pattern = "\\b" + Pattern.quote(v.id!!) + "\\b"
                    result = result.replace(pattern.toRegex(), v.value.toString())
                }
            }

            return evaluateExpression(result)
        } catch (e: Exception) {
            Logu.e("互动视频", "条件判断失败: " + e.message)
            return true
        }
    }

    private fun evaluateExpression(expr: String): Boolean {
        var stripped = expr.trim()
        try {
            stripped = expr.trim()
            if (stripped.contains(">=")) {
                val parts = stripped.split(">=")
                val left = parts[0].trim().toLong()
                val right = parts[1].trim().toLong()
                return left >= right
            } else if (stripped.contains("<=")) {
                val parts = stripped.split("<=")
                val left = parts[0].trim().toLong()
                val right = parts[1].trim().toLong()
                return left <= right
            } else if (stripped.contains(">")) {
                val parts = stripped.split(">")
                val left = parts[0].trim().toLong()
                val right = parts[1].trim().toLong()
                return left > right
            } else if (stripped.contains("<")) {
                val parts = stripped.split("<")
                val left = parts[0].trim().toLong()
                val right = parts[1].trim().toLong()
                return left < right
            } else if (stripped.contains("==")) {
                val parts = stripped.split("==")
                val left = parts[0].trim().toLong()
                val right = parts[1].trim().toLong()
                return left == right
            } else if (stripped.contains("!=")) {
                val parts = stripped.split("!=")
                val left = parts[0].trim().toLong()
                val right = parts[1].trim().toLong()
                return left != right
            }
        } catch (e: Exception) {
            Logu.e("互动视频", "表达式计算失败: " + stripped)
        }
        return true
    }

    private fun handleChoiceSelection(choice: InteractionVideoData.InteractionChoice) {
        hideInteractionChoices()

        if (choice.nativeAction != null && choice.nativeAction!!.isNotEmpty()) {
            executeNativeAction(choice.nativeAction!!)
        }

        CenterThreadPool.run {
            try {
                val targetEdgeId = choice.id
                val newData = InteractionVideoApi.getEdgeInfo(aid, null, interactionGraphVersion, targetEdgeId)

                if (newData == null) {
                    runOnUiThread { MsgUtil.showMsg("获取互动视频数据失败") }
                    return@run
                }

                interactionData = newData
                currentEdgeId = newData.edgeId

                val targetCid = choice.cid
                if (targetCid > 0 && targetCid != cid) {
                    jumpToInteractionPage(targetCid, newData)
                } else {
                    resumePlaybackIfPaused()
                }
            } catch (e: Exception) {
                Logu.e("互动视频", "处理选择失败: " + e.message)
                runOnUiThread { MsgUtil.showMsg("处理选择失败: " + e.message) }
            }
        }
    }

    private fun hideInteractionChoices() {
        runOnUiThread {
            interactionChoiceLayout?.visibility = View.GONE
            questionShown = false
            currentQuestion = null
        }
    }

    private fun jumpToInteractionPage(targetCid: Long, newData: InteractionVideoData) {
        CenterThreadPool.run {
            try {
                val playerData = PlayerData()
                playerData.aid = aid
                playerData.cid = targetCid
                playerData.title = newData.title
                playerData.mid = mid
                playerData.qn = getTargetQuality()

                if (pagenames != null && cids != null) {
                    playerData.pagenames = pagenames
                    playerData.cids = cids
                    val newPageIndex = cids!!.indexOf(targetCid)
                    if (newPageIndex >= 0) {
                        playerData.currentPageIndex = newPageIndex
                        currentPageIndex = newPageIndex
                    }
                }

                PlayerApi.getVideo(playerData, false)

                runOnUiThread {
                    if (destroyed) return@runOnUiThread

                    ijkPlayer?.stop()
                    ijkPlayer?.release()
                    mDanmakuView?.release()
                    mDanmakuView = null

                    cid = targetCid
                    video_url = playerData.videoUrl
                    danmaku_url = playerData.danmakuUrl
                    text_title!!.text = newData.title
                    videoTitle = newData.title
                    currentEdgeId = newData.edgeId

                    if (playerData.qnStrList != null && playerData.qnValueList != null) {
                        qnStrList = playerData.qnStrList
                        qnValueList = playerData.qnValueList
                        currentQuality = playerData.qn
                    }

                    loading_info.visibility = View.VISIBLE
                    anim_loading!!.start()
                    loading_text0.text = "加载互动分P"
                    isPrepared = false
                    isPlaying = false
                    finishWatching = false
                    progress_history = 0
                    subtitles = null
                    subtitleLinks = null
                    subtitle_selected = -1
                    viewPoints = null
                    viewPointAdapter = null
                    btn_viewpoint.visibility = View.GONE

                    interactionData = newData
                    currentQuestion = null
                    questionShown = false
                    interactionChoiceLayout?.visibility = View.GONE
                    interactionChoiceLayout?.removeAllViews()

                    ijkPlayer = IjkMediaPlayer()
                    mDanmakuView = findViewById(R.id.sv_danmaku)

                    setDisplay()

                    layout_control.postDelayed({
                        CenterThreadPool.run {
                            if (destroyed) return@run

                            runOnUiThread {
                                loading_text0.text = "装填弹幕中"
                                loading_text1.text = "(≧∇≦)"
                            }

                            if (isOnlineVideo) {
                                danmakuFile = File(cacheDir, "danmaku.xml")
                                if (danmakuFile!!.exists()) danmakuFile!!.delete()
                                downdanmu()
                            }

                            if (!destroyed && SharedPreferencesUtil.getBoolean("player_subtitle_autoshow", true)) {
                                downSubtitle(false)
                            }

                            if (!destroyed && isOnlineVideo && aid > 0 && cid > 0) {
                                loadHighEnergyData()
                            }

                            if (!destroyed && isOnlineVideo && aid > 0 && cid > 0 && SharedPreferencesUtil.getBoolean("player_show_viewpoints", true)) {
                                loadViewPoints()
                            }
                        }
                    }, 60)
                }
            } catch (e: Exception) {
                Logu.e("互动视频", "跳转失败: " + e.message)
                runOnUiThread { MsgUtil.showMsg("跳转失败: " + e.message) }
            }
        }
    }

    private fun resumePlaybackIfPaused() {
        runOnUiThread {
            if (currentQuestion != null && currentQuestion!!.pauseVideo == 1 && !isPlaying) {
                ijkPlayer!!.start()
                isPlaying = true
                btn_control.setImageResource(R.drawable.btn_player_pause)
            }
        }
    }

    private fun getTargetQuality(): Int {
        val defaultQn = SharedPreferencesUtil.getInt("play_qn", 16)
        if (qnValueList == null || qnValueList!!.isEmpty()) return if (currentQuality > 0) currentQuality else defaultQn

        for (qn in qnValueList!!) {
            if (qn == currentQuality) return currentQuality
        }

        return if (currentQuality > 0) currentQuality else defaultQn
    }

    private fun executeNativeAction(nativeAction: String) {
        if (interactionData == null || interactionData!!.hiddenVars == null || nativeAction.isEmpty()) return

        val actions = nativeAction.split(";")
        for (action in actions) {
            val trimmed = action.trim()
            if (trimmed.isEmpty()) continue

            try {
                if (trimmed.contains("=")) {
                    val parts = trimmed.split("=")
                    if (parts.size == 2) {
                        val varId = parts[0].trim()
                        val valueExpr = parts[1].trim()

                        val value = evaluateValueExpression(valueExpr)

                        for (v in interactionData!!.hiddenVars!!) {
                            if ((v.idV2 != null && v.idV2 == varId) ||
                                (v.id != null && v.id == varId)) {
                                v.value = value
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Logu.e("互动视频", "执行动作失败: " + trimmed)
            }
        }
    }

    private fun evaluateValueExpression(expr: String): Long {
        var stripped = expr.trim()
        try {
            stripped = expr.trim()
            if (stripped.contains("+")) {
                val parts = stripped.split("\\+".toRegex()).toTypedArray()
                var sum: Long = 0
                for (part in parts) sum += evaluateValueExpression(part.trim())
                return sum
            } else if (stripped.contains("-")) {
                val parts = stripped.split("-")
                var result = evaluateValueExpression(parts[0].trim())
                for (i in 1 until parts.size) result -= evaluateValueExpression(parts[i].trim())
                return result
            } else {
                if (interactionData != null && interactionData!!.hiddenVars != null) {
                    for (v in interactionData!!.hiddenVars!!) {
                        if ((v.idV2 != null && stripped == v.idV2) ||
                            (v.id != null && stripped == v.id)) {
                            return v.value
                        }
                    }
                }
                if (stripped.contains(".")) return stripped.toDouble().toLong()
                else return stripped.toLong()
            }
        } catch (e: Exception) {
            Logu.e("互动视频", "值表达式计算失败: " + stripped + ", 错误: " + e.message)
            return 0
        }
    }

    private fun showInteractionDebugDialog() {
        if (interactionData == null || interactionData!!.hiddenVars == null || interactionData!!.hiddenVars!!.isEmpty()) {
            MsgUtil.showMsg("当前没有互动视频变量")
            return
        }

        InteractionDebugActivity.setInteractionData(interactionData!!)
        val intent = Intent(this, InteractionDebugActivity::class.java)
        startActivity(intent)
    }

    override fun onPrepared(mediaPlayer: IMediaPlayer) {
        if (destroyed) {
            ijkPlayer!!.release()
            return
        }

        isPrepared = true
        video_all = ijkPlayer!!.duration.toInt()

        changeVideoSize()

        if ((isLiveMode || hasDanmaku) && mDanmakuView != null) mDanmakuView!!.start()
        if (SharedPreferencesUtil.getBoolean("player_ui_showDanmakuBtn", true)) {
            isDanmakuVisible = !SharedPreferencesUtil.getBoolean("pref_switch_danmaku", true)
            btn_danmaku.setOnClickListener {
                if (mDanmakuView == null) return@setOnClickListener
                if (isDanmakuVisible) mDanmakuView!!.hide()
                else mDanmakuView!!.show()
                btn_danmaku.setImageResource(if (isDanmakuVisible) R.mipmap.danmakuoff else R.mipmap.danmakuon)
                isDanmakuVisible = !isDanmakuVisible
                SharedPreferencesUtil.putBoolean("pref_switch_danmaku", isDanmakuVisible)
            }
            btn_danmaku.performClick()
            
            btn_danmaku.visibility = View.VISIBLE
        } else btn_danmaku.visibility = View.GONE

        if (!isLiveMode) {
            if (loop_enabled) btn_loop.setImageResource(R.mipmap.loopon)
            else btn_loop.setImageResource(R.mipmap.loopoff)
            btn_loop.setOnClickListener {
                btn_loop.setImageResource(if (loop_enabled) R.mipmap.loopoff else R.mipmap.loopon)
                loop_enabled = !loop_enabled
            }
            btn_loop.visibility = View.VISIBLE

            if (isLocalAudioFile) {
                btn_audio_only.visibility = View.GONE
            } else {
                updateAudioOnlyButton()
                btn_audio_only.setOnClickListener { toggleAudioOnlyMode() }
                btn_audio_only.visibility = View.VISIBLE
            }

            if (hasMultiplePages()) {
                btn_page_selector.visibility = View.VISIBLE
                btn_page_selector.setOnClickListener { showPageSelectorCard() }
                btn_auto_next.visibility = View.VISIBLE
                updateAutoNextButton()
                btn_auto_next.setOnClickListener { toggleAutoNext() }
            } else {
                btn_page_selector.visibility = View.GONE
                btn_auto_next.visibility = View.GONE
            }

            if (SharedPreferencesUtil.getBoolean("player_ui_showQualityBtn", true) && isOnlineVideo) {
                btn_quality.visibility = View.VISIBLE
                btn_quality.setOnClickListener { showQualitySelectorCard() }
            } else {
                btn_quality.visibility = View.GONE
            }

            if (!SharedPreferencesUtil.getBoolean("player_ui_showPageBtn", true))
                btn_page_selector.visibility = View.GONE
        } else {
            btn_loop.visibility = View.GONE
            btn_audio_only.visibility = View.GONE
            btn_page_selector.visibility = View.GONE
            btn_auto_next.visibility = View.GONE
            btn_quality.visibility = View.GONE
        }

        seekbar_progress.max = video_all
        progress_str = StringUtil.toTime(video_all / 1000)

        if (isAudioOnlyMode) updateAudioOnlyUI()

        if (SharedPreferencesUtil.getBoolean("player_from_last", true) && !isLiveMode) {
            if (progress_history > 5) {
                ijkPlayer!!.seekTo(progress_history)
                if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.seekTo(progress_history)
                Logu.d("进度跳转", progress_history.toString())
                runOnUiThread { MsgUtil.showMsg("已从上次的位置播放") }
            }
        }

        loading_info.visibility = View.GONE
        anim_loading!!.stop()

        isPlaying = true

        btn_control.setImageResource(if (isPlaying) R.drawable.btn_player_pause else R.drawable.btn_player_play)

        text_speed.visibility = layout_top.visibility
        if (isLiveMode) text_speed.visibility = View.GONE
        text_speed.setOnClickListener { layout_speed.visibility = View.VISIBLE }
        layout_speed.setOnClickListener { layout_speed.visibility = View.GONE }

        btn_debug.setOnClickListener { showInteractionDebugDialog() }
        updateDebugButtonVisibility()

        progressChange()
        onlineChange()

        // 初始化外部音频轨道（DASH分离文件的fallback）
        if (!audioTrackUrl.isNullOrEmpty()) {
            initAudioTrack()
        }

        if (isPlaying) {
            ijkPlayer!!.start()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
            updateMediaSessionMetadata()
            updateMediaSessionPlaybackState()
        }

        btn_control.setOnClickListener { controlVideo() }
        btn_subtitle.setOnClickListener { CenterThreadPool.run { downSubtitle(true) } }

        if (isShortVideoMode && ::btn_video_info.isInitialized) {
            btn_video_info.visibility = View.VISIBLE
            btn_video_info.setOnClickListener {
                val intent = Intent(this, VideoInfoActivity::class.java)
                intent.putExtra("aid", aid)
                startActivity(intent)
            }
        }
    }

    override fun finish() {
        if (isPlaying) playerPause()
        if (ijkPlayer != null) {
            val result = Intent()
            result.putExtra("progress", ijkPlayer!!.currentPosition.toInt())
            result.putExtra("isPlaying", isPlaying)
            result.putExtra("isDanmakuEnabled", !isDanmakuVisible)
            result.putExtra("quality", currentQuality)
            Logu.d("进度回传", ijkPlayer!!.currentPosition.toString())
            setResult(RESULT_OK, result)
        } else setResult(RESULT_CANCELED)
        super.finish()
    }

    companion object {
        @JvmStatic
        fun decompress(data: ByteArray): ByteArray {
            var output: ByteArray
            val decompresser = Inflater(true)
            decompresser.reset()
            decompresser.setInput(data)
            val o = ByteArrayOutputStream(data.size)
            try {
                val buf = ByteArray(2048)
                while (!decompresser.finished()) {
                    val i = decompresser.inflate(buf)
                    o.write(buf, 0, i)
                }
                output = o.toByteArray()
            } catch (e: Exception) {
                output = data
                e.printStackTrace()
            } finally {
                try {
                    o.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            decompresser.end()
            return output
        }
    }
}