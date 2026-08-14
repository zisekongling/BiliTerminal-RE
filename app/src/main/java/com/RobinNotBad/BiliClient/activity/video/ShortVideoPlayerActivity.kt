package com.RobinNotBad.BiliClient.activity.video

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.viewpager2.widget.ViewPager2
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.MenuActivity
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.activity.video.info.VideoInfoActivity
import com.RobinNotBad.BiliClient.api.ShortVideoFeedApi
import com.RobinNotBad.BiliClient.model.ShortVideoItem
import com.RobinNotBad.BiliClient.service.DownloadService
import com.RobinNotBad.BiliClient.ui.widget.HighEnergyProgressBar
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.VideoPreloadManager
import com.RobinNotBad.BiliClient.helper.TutorialHelper
import com.RobinNotBad.BiliClient.player.DanmakuManager
import com.bumptech.glide.Glide
import master.flame.danmaku.ui.widget.DanmakuView
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.util.Timer
import java.util.TimerTask

class ShortVideoPlayerActivity : InstanceActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var loadingLayout: View
    private lateinit var errorLayout: View
    private lateinit var loadingText: TextView
    private lateinit var errorText: TextView

    private val preloadManager = VideoPreloadManager(preloadCount = 3)
    var screenWidth = 0
    var screenHeight = 0
    private var currentPageIndex = 0
    private var wasPlayingWhenPaused = false
    internal var isBottomControlVisible = true
    
    private lateinit var audioManager: AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_short_video_player)

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        viewPager = findViewById(R.id.viewPager)
        loadingLayout = findViewById(R.id.loadingLayout)
        errorLayout = findViewById(R.id.errorLayout)
        loadingText = findViewById(R.id.loadingText)
        errorText = findViewById(R.id.errorText)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        findViewById<TextView>(R.id.retryButton).setOnClickListener { loadFeed() }

        preloadManager.onItemsLoaded = { items ->
            if (items.isNotEmpty()) {
                loadingLayout.visibility = View.GONE
                errorLayout.visibility = View.GONE

                val adapter = viewPager.adapter as? ShortVideoPagerAdapter
                if (adapter == null) {
                    val newAdapter = ShortVideoPagerAdapter(this, preloadManager, audioManager)
                    viewPager.adapter = newAdapter
                } else {
                    adapter.notifyDataSetChanged()
                }
            }
        }

        preloadManager.onLoadError = { msg ->
            if (preloadManager.getItemCount() == 0) {
                loadingLayout.visibility = View.GONE
                errorLayout.visibility = View.VISIBLE
                errorText.text = msg
            } else {
                MsgUtil.showMsg(msg)
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPageIndex = position
                preloadManager.onSwipeToIndex(position)

                val adapter = viewPager.adapter as? ShortVideoPagerAdapter
                adapter?.let {
                    val prevPos = it.lastVisiblePosition
                    if (prevPos >= 0 && prevPos != position) {
                        it.pausePlayer(prevPos)
                    }
                    it.lastVisiblePosition = position
                    it.setupPlayerAtPosition(position)
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    val adapter = viewPager.adapter as? ShortVideoPagerAdapter
                    adapter?.playAtPosition(currentPageIndex)
                }
            }
        })

        TutorialHelper.show(R.xml.tutorial_short_video, this, "short_video", 1)

        loadFeed()
    }

    override fun onResume() {
        super.onResume()
        val adapter = viewPager.adapter as? ShortVideoPagerAdapter
        if (wasPlayingWhenPaused) {
            adapter?.resumeCurrent()
        }
    }

    override fun onPause() {
        super.onPause()
        val adapter = viewPager.adapter as? ShortVideoPagerAdapter
        wasPlayingWhenPaused = adapter?.isCurrentPlaying() == true
        adapter?.pauseCurrent()
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            val adapter = viewPager.adapter as? ShortVideoPagerAdapter
            adapter?.releaseAll()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        preloadManager.release()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        (viewPager.adapter as? ShortVideoPagerAdapter)?.notifyScreenSizeChanged(screenWidth, screenHeight)
    }

    override fun onBackPressed() {
        // 返回时暂停当前视频和弹幕，增加异常捕获防止闪退
        try {
            val adapter = viewPager.adapter as? ShortVideoPagerAdapter
            adapter?.pauseCurrent()
        } catch (e: Exception) {
            Logu.e("ShortVideo", "返回暂停异常: ${e.message}")
        }
        finish()
    }

    private fun loadFeed() {
        loadingLayout.visibility = View.VISIBLE
        errorLayout.visibility = View.GONE
        loadingText.text = "加载短视频..."
        preloadManager.loadInitial()
    }
}

class ShortVideoPagerAdapter(
    private val activity: ShortVideoPlayerActivity,
    private val preloadManager: VideoPreloadManager,
    private val audioManager: AudioManager
) : androidx.recyclerview.widget.RecyclerView.Adapter<ShortVideoPagerAdapter.PageHolder>() {

    private val holders = mutableMapOf<Int, PageHolder>()
    private var activeHolder: PageHolder? = null

    var lastVisiblePosition = -1

    override fun getItemCount(): Int = preloadManager.getItemCount()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_short_video_page, parent, false)
        return PageHolder(view, audioManager, activity)
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        val item = preloadManager.getItem(position) ?: return
        holder.bind(item, position)
        holders[position] = holder
    }

    override fun onViewRecycled(holder: PageHolder) {
        super.onViewRecycled(holder)
        holder.releasePlayer()
    }

    fun setupPlayerAtPosition(position: Int) {
        val holder = holders[position] ?: return
        holder.setActive(true)
        holder.setupPlayer(activity.screenWidth, activity.screenHeight)
    }

    fun playAtPosition(position: Int) {
        val holder = holders[position] ?: return
        // 先停掉之前的活跃页面
        activeHolder?.let { if (it != holder) it.setActive(false) }
        activeHolder = holder
        holder.setActive(true)
        holder.play()
    }

    fun pausePlayer(position: Int) {
        holders[position]?.let {
            it.setActive(false)
            it.pause()
        }
    }

    fun pauseCurrent() {
        activeHolder?.pause()
    }

    fun resumeCurrent() {
        activeHolder?.resume()
    }

    fun isCurrentPlaying(): Boolean {
        return activeHolder?.isPlaying() == true
    }

    fun releaseAll() {
        holders.values.forEach { it.releasePlayer() }
        holders.clear()
    }

    fun notifyScreenSizeChanged(width: Int, height: Int) {
        holders.values.forEach { it.updateVideoSize(width, height) }
    }

    inner class PageHolder(
        itemView: View,
        private val audioManager: AudioManager,
        private val activity: ShortVideoPlayerActivity
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {

        private val videoContainer: FrameLayout = itemView.findViewById(R.id.videoContainer)
        private val bufferingIndicator: ProgressBar = itemView.findViewById(R.id.bufferingIndicator)
        private val coverImage: ImageView = itemView.findViewById(R.id.coverImage)
        private val playIcon: ImageView = itemView.findViewById(R.id.playIcon)
        private val top: View = itemView.findViewById(R.id.top)
        private val textTitle: TextView = itemView.findViewById(R.id.text_title)
        private val bottomControl: View = itemView.findViewById(R.id.bottom_control)
        private val videoProgress: HighEnergyProgressBar = itemView.findViewById(R.id.videoprogress)
        private val textProgress: TextView = itemView.findViewById(R.id.text_progress)
        private val bottomButtons: View = itemView.findViewById(R.id.bottom_buttons)
        private val buttonVideo: ImageButton = itemView.findViewById(R.id.button_video)
        private val buttonSoundCut: ImageButton = itemView.findViewById(R.id.button_sound_cut)
        private val buttonSoundAdd: ImageButton = itemView.findViewById(R.id.button_sound_add)
        private val buttonSpeed: TextView = itemView.findViewById(R.id.button_speed)
        private val buttonDanmaku: ImageButton = itemView.findViewById(R.id.button_danmaku)
        private val showSound: TextView = itemView.findViewById(R.id.showsound)

        private var ijkPlayer: IjkMediaPlayer? = null
        private var textureView: TextureView? = null
        private var danmakuView: DanmakuView? = null
        private var danmakuManager: DanmakuManager? = null
        private var currentItem: ShortVideoItem? = null
        private var videoWidth = 0
        private var videoHeight = 0
        private var videoAll = 0
        private var videoNow = 0
        private var videoNowLast = 0
        private var progressStr = "00:00"
        private var isPrepared = false
        private var isPlaying = false
        private var isSeeking = false
        private var isActive = false         // 当前页面是否为活跃状态
        private var isLoadingDanmaku = false // 是否正在加载弹幕
        private var isDanmakuVisible = true  // 弹幕是否可见

        private val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        private var currentSpeedIndex = 2
        private var currentSpeed = 1.0f

        private var currentScale = 1.0f
        private val minScale = 1.0f
        private val maxScale = 3.0f

        private var isScaling = false

        var isInitialized = false
            private set

        private val mainHandler = Handler(Looper.getMainLooper())
        private var hideBottomRunnable: Runnable? = null
        private var progressTimer: Timer? = null
        private var hideVolumeRunnable: Runnable? = null

        private var gestureDetector: GestureDetector? = null
        private var scaleGestureDetector: ScaleGestureDetector? = null

        @Suppress("ClickableViewAccessibility")
        fun bind(item: ShortVideoItem, position: Int) {
            currentItem = item

            if (item.cover.isNotEmpty()) {
                coverImage.visibility = View.VISIBLE
                Glide.with(activity)
                    .asDrawable()
                    .load(GlideUtil.url(item.cover))
                    .into(coverImage)
            }

            isInitialized = true

            top.setOnClickListener {
                // 返回时暂停视频和弹幕，增加异常捕获防止闪退
                try {
                    pause()
                } catch (e: Exception) {
                    Logu.e("ShortVideo", "顶栏返回暂停异常: ${e.message}")
                }
                activity.finish()
            }

            textTitle.text = item.title

            gestureDetector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    toggleBottomControl()
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    togglePlayPause()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    openVideoDetail()
                }
            })

            scaleGestureDetector = ScaleGestureDetector(activity, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    isScaling = true
                    itemView.parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val scaleFactor = detector.scaleFactor
                    currentScale *= scaleFactor
                    currentScale = currentScale.coerceIn(minScale, maxScale)
                    applyScale()
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    isScaling = false
                    itemView.parent.requestDisallowInterceptTouchEvent(false)
                }
            })

            itemView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    isScaling = false
                }
                
                scaleGestureDetector?.onTouchEvent(event)
                gestureDetector?.onTouchEvent(event)
                true
            }

            initBottomButtons()

            if (activity.isBottomControlVisible) {
                bottomControl.visibility = View.VISIBLE
            } else {
                bottomControl.visibility = View.GONE
            }
        }

        private fun initBottomButtons() {
            buttonVideo.setOnClickListener {
                controlVideo()
            }

            buttonDanmaku.setOnClickListener {
                toggleDanmaku()
            }

            buttonSoundAdd.setOnClickListener {
                changeVolume(true)
            }

            buttonSoundCut.setOnClickListener {
                changeVolume(false)
            }

            buttonSpeed.setOnClickListener {
                cycleSpeed()
            }

            videoProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, position: Int, fromUser: Boolean) {
                    if (fromUser) {
                        textProgress.text = StringUtil.toTime(position / 1000) + "/" + progressStr
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    isSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    isSeeking = false
                    if (isPrepared) {
                        val seekPos = seekBar.progress.toLong()
                        ijkPlayer?.seekTo(seekPos)
                        danmakuManager?.seekTo(seekPos)
                    }
                }
            })
        }

        private fun cycleSpeed() {
            currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
            currentSpeed = speedOptions[currentSpeedIndex]
            buttonSpeed.text = "${currentSpeed}x"
            
            ijkPlayer?.setSpeed(currentSpeed)
        }

        /**
         * 切换弹幕显示/隐藏
         */
        private fun toggleDanmaku() {
            isDanmakuVisible = !isDanmakuVisible
            if (isDanmakuVisible) {
                danmakuManager?.show()
                buttonDanmaku.setImageResource(R.mipmap.danmakuon)
            } else {
                danmakuManager?.hide()
                buttonDanmaku.setImageResource(R.mipmap.danmakuoff)
            }
        }

        private fun togglePlayPause() {
            if (ijkPlayer?.isPlaying == true) {
                pause()
            } else {
                resume()
            }
        }

        private fun controlVideo() {
            if (isPlaying) {
                pause()
            } else {
                if (videoNow >= videoAll - 250) {
                    ijkPlayer?.seekTo(0)
                }
                resume()
            }
        }

        private fun changeVolume(addOrCut: Boolean) {
            var volumeNow = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val volumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val volumeNew = volumeNow + if (addOrCut) 1 else -1
            if (volumeNew in 0..volumeMax) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeNew, 0)
                volumeNow = volumeNew
            }
            val show = (volumeNow.toFloat() / volumeMax.toFloat() * 100).toInt()

            showSound.visibility = View.VISIBLE
            showSound.text = "音量：$show%"

            hideVolumeRunnable?.let { mainHandler.removeCallbacks(it) }
            hideVolumeRunnable = Runnable { showSound.visibility = View.GONE }
            mainHandler.postDelayed(hideVolumeRunnable!!, 3000)
        }

        private fun openVideoDetail() {
            currentItem?.let { item ->
                if (item.aid > 0) {
                    val intent = Intent(activity, VideoInfoActivity::class.java)
                    intent.putExtra("aid", item.aid)
                    activity.startActivity(intent)
                }
            }
        }

        private fun applyScale() {
            textureView?.let { view ->
                val matrix = Matrix()
                val pivotX = view.width / 2f
                val pivotY = view.height / 2f
                matrix.setScale(currentScale, currentScale, pivotX, pivotY)
                view.setTransform(matrix)
            }
        }

        fun updateTitle() {
            currentItem?.let { item ->
                textTitle.text = item.title
            }
        }

        private fun toggleBottomControl() {
            if (bottomControl.visibility == View.VISIBLE) {
                hideBottomControl()
            } else {
                showBottomControl()
            }
        }

        private fun showBottomControl() {
            bottomControl.visibility = View.VISIBLE
            activity.isBottomControlVisible = true

            hideBottomRunnable?.let { mainHandler.removeCallbacks(it) }
            hideBottomRunnable = Runnable {
                bottomControl.visibility = View.GONE
                activity.isBottomControlVisible = false
            }
            mainHandler.postDelayed(hideBottomRunnable!!, 10000)
        }

        private fun hideBottomControl() {
            bottomControl.visibility = View.GONE
            activity.isBottomControlVisible = false
            hideBottomRunnable?.let { mainHandler.removeCallbacks(it) }
            hideBottomRunnable = null
        }

        fun setupPlayer(screenW: Int, screenH: Int) {
            val item = currentItem ?: return
            if (item.videoUrl.isEmpty()) {
                CenterThreadPool.run {
                    val success = ShortVideoFeedApi.fetchVideoUrl(item)
                    mainHandler.post {
                        if (success && item.videoUrl.isNotEmpty()) {
                            initPlayer(item, screenW, screenH)
                        } else {
                            bufferingIndicator.visibility = View.GONE
                            MsgUtil.showMsg("加载视频失败")
                        }
                    }
                }
            } else {
                initPlayer(item, screenW, screenH)
            }
        }

        private fun initPlayer(item: ShortVideoItem, screenW: Int, screenH: Int) {
            if (ijkPlayer != null) return

            bufferingIndicator.visibility = View.VISIBLE

            try {
                IjkMediaPlayer.loadLibrariesOnce(null)
                ijkPlayer = IjkMediaPlayer()

                // 优化播放器选项：加速加载、减少缓冲延迟
                ijkPlayer?.apply {
                    // 硬件解码
                    setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1)
                    setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-all-videos", 1)
                    setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1)
                    setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1)
                    // 禁用OpenSL ES（避免音频兼容问题）
                    setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0)
                    // 跳帧策略：适度跳帧保证流畅
                    setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 5)
                    // 关键：移除 start-on-prepared，改为手动控制播放，防止弱网下自动播放
                    setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0)
                    // 快速打开：减少分析时间
                    setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 1)
                    setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 1024)
                    setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "fastseek")
                    // 网络优化
                    setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1)
                    setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 10 * 1000 * 1000)
                    setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "addrinfo_timeout", 5 * 1000 * 1000)
                    // 缓冲策略：小缓冲快速启动，大缓冲防卡顿
                    setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0)
                    setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 8 * 1024 * 1024)
                    setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 5)
                    setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 3000)
                    // 无限缓冲
                    setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1)
                    // UA
                    setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", NetWorkUtil.USER_AGENT_WEB)
                    // 跳过环路滤波加速解码
                    setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48)
                }

                textureView = TextureView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    isOpaque = false

                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            st: SurfaceTexture, w: Int, h: Int
                        ) {
                            ijkPlayer?.setSurface(Surface(st))
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            ijkPlayer?.setSurface(null)
                            return true
                        }

                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
                videoContainer.removeAllViews()
                videoContainer.addView(textureView)

                // 初始化弹幕视图
                danmakuView = itemView.findViewById(R.id.danmakuView)
                danmakuManager = DanmakuManager(danmakuView!!) {
                    ijkPlayer?.currentPosition ?: 0L
                }
                danmakuManager?.init()

                ijkPlayer?.setOnPreparedListener {
                    isPrepared = true
                    videoWidth = it.videoWidth
                    videoHeight = it.videoHeight
                    videoAll = it.duration.toInt()
                    progressStr = StringUtil.toTime(videoAll / 1000)
                    videoProgress.max = videoAll

                    bufferingIndicator.visibility = View.GONE
                    coverImage.visibility = View.GONE
                    playIcon.visibility = View.GONE

                    adjustVideoSize(screenW, screenH)
                    ijkPlayer?.setSpeed(currentSpeed)

                    // 只有当前页面活跃时才自动播放，防止弱网延迟准备导致两个视频同时播放
                    if (isActive) {
                        ijkPlayer?.start()
                        isPlaying = true
                        buttonVideo.setImageResource(R.drawable.btn_player_pause)
                        danmakuManager?.resume()
                    } else {
                        // 页面不活跃，暂停准备就绪
                        isPlaying = false
                        buttonVideo.setImageResource(R.drawable.btn_player_play)
                        playIcon.visibility = View.VISIBLE
                    }

                    startProgressTimer()
                    // 异步加载弹幕
                    loadDanmaku(item)
                }

                ijkPlayer?.setOnErrorListener { _, what, _ ->
                    Logu.e("ShortVideo", "Player error: $what")
                    bufferingIndicator.visibility = View.GONE
                    MsgUtil.showMsg("播放错误")
                    false
                }

                ijkPlayer?.setOnInfoListener { _, what, _ ->
                    if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
                        mainHandler.post { bufferingIndicator.visibility = View.VISIBLE }
                    } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
                        mainHandler.post { bufferingIndicator.visibility = View.GONE }
                    }
                    false
                }

                ijkPlayer?.setOnCompletionListener {
                    // 循环播放
                    it.seekTo(0)
                    danmakuManager?.seekTo(0)
                    if (isActive) {
                        it.start()
                        danmakuManager?.resume()
                    }
                }

                ijkPlayer?.setScreenOnWhilePlaying(true)

                val headers = HashMap<String, String>()
                headers["Referer"] = "https://www.bilibili.com/"
                headers["Cookie"] = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")
                ijkPlayer?.setDataSource(item.videoUrl, headers)
                ijkPlayer?.prepareAsync()

            } catch (e: Exception) {
                Logu.e("ShortVideo", "Player init error: ${e.message}")
                bufferingIndicator.visibility = View.GONE
                MsgUtil.showMsg("播放器初始化失败")
            }
        }

        /**
         * 异步加载弹幕数据
         */
        private fun loadDanmaku(item: ShortVideoItem) {
            if (isLoadingDanmaku) return
            val danmakuUrl = item.danmakuUrl
            if (danmakuUrl.isEmpty()) return

            isLoadingDanmaku = true
            CenterThreadPool.run {
                try {
                    val response = NetWorkUtil.get(danmakuUrl, NetWorkUtil.webHeaders)
                    val body = response.body
                    if (body != null) {
                        // 解压弹幕数据
                        val decompressed = DownloadService.decompress(body.bytes())
                        val inputStream = java.io.ByteArrayInputStream(decompressed)
                        mainHandler.post {
                            danmakuManager?.loadFromXmlInput(inputStream)
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    Logu.e("ShortVideo", "Danmaku load error: ${e.message}")
                } finally {
                    isLoadingDanmaku = false
                }
            }
        }

        private fun startProgressTimer() {
            progressTimer?.cancel()
            progressTimer = Timer()
            progressTimer?.schedule(object : TimerTask() {
                override fun run() {
                    if (isPrepared && isPlaying && !isSeeking && ijkPlayer != null) {
                        videoNow = ijkPlayer!!.currentPosition.toInt()
                        if (videoNowLast != videoNow) {
                            videoNowLast = videoNow
                            mainHandler.post {
                                videoProgress.progress = videoNow
                                textProgress.text = StringUtil.toTime(videoNow / 1000) + "/" + progressStr
                            }
                        }
                    }
                }
            }, 0, 250)
        }

        private fun adjustVideoSize(screenW: Int, screenH: Int) {
            if (videoWidth == 0 || videoHeight == 0) return

            val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
            val screenRatio = screenW.toFloat() / screenH.toFloat()

            val params = textureView?.layoutParams as? FrameLayout.LayoutParams
            params?.let {
                if (videoRatio > screenRatio) {
                    it.width = screenW
                    it.height = (screenW / videoRatio).toInt()
                } else {
                    it.width = (screenH * videoRatio).toInt()
                    it.height = screenH
                }
                it.gravity = Gravity.CENTER
                textureView?.layoutParams = it
            }
        }

        fun play() {
            ijkPlayer?.start()
            isPlaying = true
            buttonVideo.setImageResource(R.drawable.btn_player_pause)
            playIcon.visibility = View.GONE
            danmakuManager?.resume()
        }

        fun pause() {
            try {
                ijkPlayer?.pause()
            } catch (e: Exception) {
                Logu.e("ShortVideo", "暂停播放器异常: ${e.message}")
            }
            isPlaying = false
            try {
                buttonVideo.setImageResource(R.drawable.btn_player_play)
                playIcon.visibility = View.VISIBLE
            } catch (e: Exception) {
                Logu.e("ShortVideo", "更新UI异常: ${e.message}")
            }
            try {
                danmakuManager?.pause()
            } catch (e: Exception) {
                Logu.e("ShortVideo", "暂停弹幕异常: ${e.message}")
            }
        }

        fun resume() {
            if (isPrepared) {
                ijkPlayer?.start()
                isPlaying = true
                buttonVideo.setImageResource(R.drawable.btn_player_pause)
                playIcon.visibility = View.GONE
                danmakuManager?.resume()
            }
        }

        /**
         * 设置页面活跃状态。当页面变为活跃时恢复播放，变为非活跃时暂停。
         */
        fun setActive(active: Boolean) {
            isActive = active
            if (active) {
                if (isPrepared && !isPlaying) {
                    ijkPlayer?.start()
                    isPlaying = true
                    buttonVideo.setImageResource(R.drawable.btn_player_pause)
                    playIcon.visibility = View.GONE
                    danmakuManager?.resume()
                }
            } else {
                if (isPlaying) {
                    ijkPlayer?.pause()
                    isPlaying = false
                    buttonVideo.setImageResource(R.drawable.btn_player_play)
                    playIcon.visibility = View.VISIBLE
                    danmakuManager?.pause()
                }
            }
        }

        fun isPlaying(): Boolean {
            return isPlaying
        }

        fun releasePlayer() {
            progressTimer?.cancel()
            progressTimer = null
            
            try {
                danmakuManager?.release()
            } catch (e: Exception) {
                Logu.e("ShortVideo", "释放弹幕异常: ${e.message}")
            }
            danmakuManager = null
            danmakuView = null

            try {
                ijkPlayer?.apply {
                    if (isPlaying) pause()
                    stop()
                    release()
                }
            } catch (e: Exception) {
                Logu.e("ShortVideo", "释放播放器异常: ${e.message}")
            }
            ijkPlayer = null
            textureView = null
            isPrepared = false
            isPlaying = false
            isActive = false
            currentScale = 1.0f
            
            hideBottomRunnable?.let { mainHandler.removeCallbacks(it) }
            hideBottomRunnable = null
            hideVolumeRunnable?.let { mainHandler.removeCallbacks(it) }
            hideVolumeRunnable = null
            
            videoContainer.removeAllViews()
        }

        fun updateVideoSize(screenW: Int, screenH: Int) {
            if (isPrepared) {
                adjustVideoSize(screenW, screenH)
            }
        }
    }

    companion object {
        private const val TAG = "ShortVideoAdapter"
    }
}