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
import com.RobinNotBad.BiliClient.api.DanmakuApi
import com.RobinNotBad.BiliClient.api.ShortVideoFeedApi
import com.RobinNotBad.BiliClient.model.ShortVideoItem
import com.RobinNotBad.BiliClient.ui.widget.HighEnergyProgressBar
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.VideoPreloadManager
import com.RobinNotBad.BiliClient.player.DanmakuManager
import com.RobinNotBad.BiliClient.player.IjkOption
import com.RobinNotBad.BiliClient.player.IjkPlayerBridge
import com.bumptech.glide.Glide
import master.flame.danmaku.ui.widget.DanmakuView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tv.danmaku.ijk.media.player.IjkMediaPlayer

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
                    // 用范围插入代替 notifyDataSetChanged：避免 ViewPager2 重建全部页面并跳回第一页
                    val start = preloadManager.getItemCount() - items.size
                    if (start >= 0) adapter.notifyItemRangeInserted(start, items.size)
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

        // 教程改由 BaseActivity 按 Tutorials 注册表集中触发

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
        // 兜底：onStop 里只在 isFinishing 时释放，系统回收页面时也要释放，否则播放器泄漏
        (viewPager.adapter as? ShortVideoPagerAdapter)?.releaseAll()
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
        holder.boundPosition = position
        holder.bind(item, position)
        holders[position] = holder
    }

    override fun onViewRecycled(holder: PageHolder) {
        super.onViewRecycled(holder)
        // 回收时必须摘掉 map 里的条目，否则 holder 被复用到新 position 后，
        // 旧 position 的 key 仍指向它，pausePlayer(旧pos) 会暂停错对象
        val pos = holder.boundPosition
        if (pos != androidx.recyclerview.widget.RecyclerView.NO_POSITION && holders[pos] === holder) {
            holders.remove(pos)
        }
        holder.boundPosition = androidx.recyclerview.widget.RecyclerView.NO_POSITION
        // 被回收的若正好是当前活跃页，同步清掉，避免 activeHolder 悬空指向已释放的播放器
        if (activeHolder === holder) activeHolder = null
        holder.releasePlayer()
    }

    fun setupPlayerAtPosition(position: Int) {
        val holder = holders[position] ?: return
        // 先停掉旧的活跃页，避免拖动过程中新旧两页同时 isActive 而各自自动播放
        activeHolder?.let { if (it !== holder) it.setActive(false) }
        activeHolder = holder
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
        // 释放后不能继续指向已释放的 holder，否则 releaseAll 之后
        // pauseCurrent/resumeCurrent/isCurrentPlaying 会操作到已释放的播放器
        activeHolder = null
        lastVisiblePosition = -1
    }

    fun notifyScreenSizeChanged(width: Int, height: Int) {
        holders.values.forEach { it.updateVideoSize(width, height) }
    }

    // itemView.setOnTouchListener 改为在 init 里只设置一次，抑制注解随之上移到类上
    @Suppress("ClickableViewAccessibility")
    inner class PageHolder(
        itemView: View,
        private val audioManager: AudioManager,
        private val activity: ShortVideoPlayerActivity
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {

        private val videoContainer: FrameLayout = itemView.findViewById(R.id.videoContainer)
        private val bufferingIndicator: ProgressBar = itemView.findViewById(R.id.bufferingIndicator)

        // 上一次应用到 bufferingIndicator 的可见性；状态收集每 250ms 触发一次，
        // 记下来才能只在目标值变化时才动 View，省掉多余的 setVisibility 与布局失效
        private var lastBufferingVisible = false

        private val coverImage: ImageView = itemView.findViewById(R.id.coverImage)
        private val playIcon: ImageView = itemView.findViewById(R.id.playIcon)
        private val top: View = itemView.findViewById(R.id.top)
        private val textTitle: TextView = itemView.findViewById(R.id.text_title)
        private val bottomControl: View = itemView.findViewById(R.id.bottom_control)
        private val videoProgress: HighEnergyProgressBar = itemView.findViewById(R.id.videoprogress)
        private val textProgress: TextView = itemView.findViewById(R.id.text_progress)
        private val buttonVideo: ImageButton = itemView.findViewById(R.id.button_video)
        private val buttonSoundCut: ImageButton = itemView.findViewById(R.id.button_sound_cut)
        private val buttonSoundAdd: ImageButton = itemView.findViewById(R.id.button_sound_add)
        private val buttonSpeed: TextView = itemView.findViewById(R.id.button_speed)
        private val buttonDanmaku: ImageButton = itemView.findViewById(R.id.button_danmaku)
        private val showSound: TextView = itemView.findViewById(R.id.showsound)

        private val playerBridge = IjkPlayerBridge(onError = { what, _ ->
            Logu.e("ShortVideo", "Player error: $what")
            mainHandler.post {
                lastBufferingVisible = false
                bufferingIndicator.visibility = View.GONE
            }
            MsgUtil.showMsg("播放错误")
        })
        private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private var stateJob: Job? = null
        private var playerReady = false
        private var textureView: TextureView? = null
        private var danmakuView: DanmakuView? = null
        private var danmakuManager: DanmakuManager? = null
        private var currentItem: ShortVideoItem? = null
        private var videoWidth = 0
        private var videoHeight = 0
        private var videoAll = 0
        private var videoNow = 0
        private var videoNowLast = 0
        // 已渲染到文本的整秒值，用于避免每 250ms 重复拼字符串与 setText
        private var videoNowLastSec = -1
        private var progressStr = "00:00"
        // 主线程写、弹幕渲染线程读（见 danmakuManager 的位置回调），必须 volatile，
        // 否则弹幕线程可能读到过期的 true，继续把重建窗口期的脏位置灌进 DanmakuTimer
        @Volatile
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

        /** 当前绑定的 position，NO_POSITION 表示未绑定或被回收 */
        var boundPosition = androidx.recyclerview.widget.RecyclerView.NO_POSITION

        private val mainHandler = Handler(Looper.getMainLooper())
        private var hideBottomRunnable: Runnable? = null
        private var hideVolumeRunnable: Runnable? = null

        private var gestureDetector: GestureDetector? = null
        private var scaleGestureDetector: ScaleGestureDetector? = null

        fun bind(item: ShortVideoItem, position: Int) {
            // bind 只保留与条目数据相关的内容：监听器、手势检测器已在 init 里随 holder 只建一次
            currentItem = item

            if (item.cover.isNotEmpty()) {
                coverImage.visibility = View.VISIBLE
                Glide.with(activity)
                    .asDrawable()
                    .load(GlideUtil.url_hq(item.cover))
                    .into(coverImage)
            }

            textTitle.text = item.title

            isInitialized = true

            if (activity.isBottomControlVisible) {
                bottomControl.visibility = View.VISIBLE
            } else {
                bottomControl.visibility = View.GONE
            }
        }

        /**
         * 一次性初始化：手势检测器、触摸监听与底部按钮监听都只随 holder 创建一次。
         *
         * 该 init 块写在 [itemView]、[top]、各按钮等所有被引用字段的声明之后，
         * 按 Kotlin 的声明顺序初始化，执行到这里时它们都已赋值；构造时 itemView 已传入，
         * 故 findViewById 也可用。回调体内引用的 currentItem 等字段同样是延迟读取，不受影响。
         */
        init {
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
                    // parent 在 init 阶段可能还没挂到 ViewPager2 上，这里保持回调内按需取，并容错 null
                    itemView.parent?.requestDisallowInterceptTouchEvent(true)
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
                    itemView.parent?.requestDisallowInterceptTouchEvent(false)
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

            top.setOnClickListener {
                // 返回时暂停视频和弹幕，增加异常捕获防止闪退
                try {
                    pause()
                } catch (e: Exception) {
                    Logu.e("ShortVideo", "顶栏返回暂停异常: ${e.message}")
                }
                activity.finish()
            }

            initBottomButtons()
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
                        playerBridge.seekTo(seekPos)
                        danmakuManager?.seekTo(seekPos)
                    }
                }
            })
        }

        private fun cycleSpeed() {
            currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
            currentSpeed = speedOptions[currentSpeedIndex]
            buttonSpeed.text = "${currentSpeed}x"
            
            playerBridge.setSpeed(currentSpeed)
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
            if (playerBridge.isPlaying) {
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
                    playerBridge.seekTo(0)
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
                            lastBufferingVisible = false
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
            if (playerReady) return

            // 与 lastBufferingVisible 同步，避免状态收集因“值相同”而不再纠正可见性
            lastBufferingVisible = true
            bufferingIndicator.visibility = View.VISIBLE

            try {
                playerBridge.createPlayer(activity, shortVideoOptions)

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
                            playerBridge.setSurface(Surface(st))
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            playerBridge.setSurface(null)
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
                    // 播放器未就绪时返回 -1，让 DanmakuManager 跳过本次 timer 更新。
                    // 本回调在 DanmakuView 的渲染线程上，与主线程重建播放器并发，
                    // 窗口期读到脏位置会让弹幕整批不显示（间歇性"弹幕没了"）。
                    if (isPrepared) playerBridge.currentPosition else -1L
                }
                danmakuManager?.init()

                playerBridge.setOnPrepared {
                    isPrepared = true
                    val st = playerBridge.state.value
                    videoWidth = st.videoWidth
                    videoHeight = st.videoHeight
                    videoAll = st.duration.toInt()
                    progressStr = StringUtil.toTime(videoAll / 1000)
                    // 换了视频要复位进度去重标记，否则新视频的文本要等整秒变化才刷新
                    videoNowLast = -1
                    videoNowLastSec = -1
                    videoProgress.max = videoAll

                    lastBufferingVisible = false
                    bufferingIndicator.visibility = View.GONE
                    coverImage.visibility = View.GONE
                    playIcon.visibility = View.GONE

                    adjustVideoSize(screenW, screenH)
                    playerBridge.setSpeed(currentSpeed)

                    // 只有当前页面活跃时才自动播放，防止弱网延迟准备导致两个视频同时播放
                    if (isActive) {
                        playerBridge.start()
                        isPlaying = true
                        buttonVideo.setImageResource(R.drawable.btn_player_pause)
                        danmakuManager?.resume()
                    } else {
                        // 页面不活跃，暂停准备就绪
                        isPlaying = false
                        buttonVideo.setImageResource(R.drawable.btn_player_play)
                        playIcon.visibility = View.VISIBLE
                    }

                    startStateCollection()
                    // 异步加载弹幕
                    loadDanmaku(item)
                }

                playerBridge.setOnCompletion {
                    // 循环播放
                    playerBridge.seekTo(0)
                    danmakuManager?.seekTo(0)
                    if (isActive) {
                        playerBridge.start()
                        danmakuManager?.resume()
                    }
                }

                playerBridge.setScreenOnWhilePlaying(true)

                val headers = HashMap<String, String>()
                headers["Referer"] = "https://www.bilibili.com/"
                headers["Cookie"] = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")
                playerBridge.setDataSource(item.videoUrl, headers)
                playerBridge.prepareAsync()

                playerReady = true

            } catch (e: Exception) {
                Logu.e("ShortVideo", "Player init error: ${e.message}")
                lastBufferingVisible = false
                bufferingIndicator.visibility = View.GONE
                MsgUtil.showMsg("播放器初始化失败")
            }
        }

        /**
         * 异步加载弹幕数据。
         *
         * **优先走新版 protobuf 分段接口**：`PlayerApi` 给短视频设的弹幕地址是
         * `https://comment.bilibili.com/{cid}.xml`（旧版 XML 接口），B站已基本停用该接口，
         * 这正是短视频"一直没有弹幕"的根因——普通播放器早已切到 `downdanmuNew()` 的分段接口，
         * 短视频没跟上。分段接口确实拿不到数据时才回退旧版 XML。
         */
        private fun loadDanmaku(item: ShortVideoItem) {
            if (isLoadingDanmaku) return
            if (item.aid <= 0 || item.cid <= 0) return

            isLoadingDanmaku = true
            CenterThreadPool.run {
                try {
                    // 短视频时长通常几十秒到几分钟；取不到时长时按 10 分钟算，只会多请求 1 个分段
                    val durationSec = (playerBridge.state.value.duration / 1000).toInt()
                    val segments = DanmakuApi.getAllVideoDanmaku(
                        item.aid, item.cid, if (durationSec > 0) durationSec else 600
                    )
                    if (segments.isNotEmpty()) {
                        mainHandler.post { danmakuManager?.loadFromProtobufSegments(segments) }
                        return@run
                    }
                    loadDanmakuFromXml(item.danmakuUrl)
                } catch (e: Exception) {
                    Logu.e("ShortVideo", "弹幕分段加载失败，回退旧版: ${e.message}")
                    try {
                        loadDanmakuFromXml(item.danmakuUrl)
                    } catch (e2: Exception) {
                        Logu.e("ShortVideo", "弹幕加载失败: ${e2.message}")
                    }
                } finally {
                    isLoadingDanmaku = false
                }
            }
        }

        /** 旧版 XML 弹幕回退路径。 */
        private fun loadDanmakuFromXml(danmakuUrl: String) {
            if (danmakuUrl.isEmpty()) return
            val response = NetWorkUtil.get(danmakuUrl, NetWorkUtil.webHeaders)
            val body = response.body
            if (body != null) {
                // 解压弹幕数据
                val decompressed = NetWorkUtil.decompress(body.bytes())
                val inputStream = java.io.ByteArrayInputStream(decompressed)
                // 必须回主线程：DanmakuManager.createParser 内部用的是进程级单例
                // BiliDanmakuLoader（dataSource 是它的实例字段），并发调用会互相覆盖；
                // 真正的 XML 解析是懒执行的，在 DanmakuView 自己的渲染线程上做，不在这里
                mainHandler.post {
                    danmakuManager?.loadFromXmlInput(inputStream)
                }
            }
            response.close()
        }

        private fun startStateCollection() {
            stateJob?.cancel()
            stateJob = playerScope.launch {
                playerBridge.state.collect { st ->
                    // 播放准备就绪后，用桥接层的缓冲/播放状态驱动缓冲指示器
                    if (st.isPrepared) {
                        val bufferingVisible = st.isBuffering && !st.isPlaying
                        // 只有目标可见性变化时才动 View，避免每 250ms 一次多余的调用与布局失效
                        if (bufferingVisible != lastBufferingVisible) {
                            lastBufferingVisible = bufferingVisible
                            bufferingIndicator.visibility =
                                if (bufferingVisible) View.VISIBLE else View.GONE
                        }
                    }
                    // 进度更新（替代原来的 Timer 轮询）
                    if (isPrepared && isPlaying && !isSeeking) {
                        val pos = st.currentPosition.toInt()
                        if (pos != videoNowLast) {
                            videoNowLast = pos
                            videoNow = pos
                            // 进度条每 250ms 刷（要平滑），但文本一秒才变一次，
                            // 所以只在整秒变化时才做字符串拼接与 setText
                            videoProgress.progress = pos
                            val sec = pos / 1000
                            if (sec != videoNowLastSec) {
                                videoNowLastSec = sec
                                textProgress.text = StringUtil.toTime(sec) + "/" + progressStr
                            }
                        }
                    }
                }
            }
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
            playerBridge.start()
            isPlaying = true
            buttonVideo.setImageResource(R.drawable.btn_player_pause)
            playIcon.visibility = View.GONE
            danmakuManager?.resume()
        }

        fun pause() {
            try {
                playerBridge.pause()
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
                playerBridge.start()
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
                    playerBridge.start()
                    isPlaying = true
                    buttonVideo.setImageResource(R.drawable.btn_player_pause)
                    playIcon.visibility = View.GONE
                    danmakuManager?.resume()
                }
            } else {
                if (isPlaying) {
                    playerBridge.pause()
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
            stateJob?.cancel()
            stateJob = null

            try {
                danmakuManager?.release()
            } catch (e: Exception) {
                Logu.e("ShortVideo", "释放弹幕异常: ${e.message}")
            }
            danmakuManager = null
            danmakuView = null

            try {
                playerBridge.release()
            } catch (e: Exception) {
                Logu.e("ShortVideo", "释放播放器异常: ${e.message}")
            }
            playerReady = false
            textureView = null
            isPrepared = false
            isPlaying = false
            isActive = false
            currentScale = 1.0f
            // 若在"转圈可见"状态下被回收，必须同步复位，否则指示器会一直转到下次 initPlayer
            lastBufferingVisible = false
            bufferingIndicator.visibility = View.GONE
            videoNowLast = -1
            videoNowLastSec = -1
            
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

        // 短视频专属播放器选项：加速加载、减少缓冲延迟（与迁移前 setOption 块保持一致）
        private val shortVideoOptions = listOf(
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-all-videos", 1),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 5),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 1),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 1024),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "fastseek"),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 10L * 1000 * 1000),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "addrinfo_timeout", 5L * 1000 * 1000),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 8 * 1024 * 1024),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 5),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 3000),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", NetWorkUtil.USER_AGENT_WEB),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48)
        )
    }
}