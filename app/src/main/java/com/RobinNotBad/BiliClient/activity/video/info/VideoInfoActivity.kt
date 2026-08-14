package com.RobinNotBad.BiliClient.activity.video.info

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.view.SurfaceView
import android.view.ViewTreeObserver
import android.view.MotionEvent
import android.graphics.Rect
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.VideoInfo
import com.RobinNotBad.BiliClient.player.InlinePlayerManager
import master.flame.danmaku.ui.widget.DanmakuView
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.viewpager.widget.ViewPager
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.EmoteActivity
import com.RobinNotBad.BiliClient.activity.MenuActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.video.info.BangumiInfoFragment
import com.RobinNotBad.BiliClient.activity.player.PlayerActivity
import com.RobinNotBad.BiliClient.activity.reply.ReplyFragment
import com.RobinNotBad.BiliClient.adapter.viewpager.ViewPagerFragmentAdapter
import com.RobinNotBad.BiliClient.api.EmoteApi
import com.RobinNotBad.BiliClient.api.DanmakuApi
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.api.ReplyApi
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.RobinNotBad.BiliClient.event.CloseAllVideoPagesEvent
import com.RobinNotBad.BiliClient.event.ReplyEvent
import com.RobinNotBad.BiliClient.helper.TutorialHelper
import com.RobinNotBad.BiliClient.ui.theme.ThemeUtils
import com.RobinNotBad.BiliClient.util.AnimationUtils
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.concurrent.TimeUnit

class VideoInfoActivity : BaseActivity() {

    private var aid: Long = 0
    private var bvid: String? = null

    private var fragmentList: MutableList<Fragment>? = null
    var replyFragment: ReplyFragment? = null
    var contentFragment: Fragment? = null
    private var seek_reply: Long = -1
    private lateinit var loading: ImageView
    private lateinit var viewPager: ViewPager

    private var currentTab = 0
    private lateinit var tabIntro: TextView
    private lateinit var tabComment: TextView
    private lateinit var contentContainer: FrameLayout

    private var inlinePlayerManager: InlinePlayerManager? = null
    private var playerData: PlayerData? = null

    private lateinit var danmakuInputPanel: LinearLayout
    private lateinit var danmakuEdit: EditText
    private var wasPlayingBeforeDanmaku = false
    private var playerContainer: FrameLayout? = null
    private var playerContainerOriginalHeight = 0
    private var savedPlayerMaxHeight = 0
    private var savedPlayerMinHeight = 0
    private var videoAspectRatio = 16f / 9f
    private var scrollView: ScrollView? = null
    private var btnBackToTop: FloatingActionButton? = null
    private var lastDanmakuClickTime = 0L
    private var keyboardListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var heightAnimator: ValueAnimator? = null

    private val fullscreenResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                val progress = data.getLongExtra("progress", 0)
                val isPlaying = data.getBooleanExtra("isPlaying", false)
                val isDanmakuEnabled = data.getBooleanExtra("isDanmakuEnabled", true)
                val quality = data.getIntExtra("quality", 0)

                playerData?.progress = progress.toInt()
                if (quality > 0 && playerData != null) {
                    playerData!!.qn = quality
                }
                inlinePlayerManager?.let { manager ->
                    if (progress > 0) {
                        manager.seekTo(progress)
                    }
                    if (isPlaying) {
                        if (!manager.isPlaying()) {
                            manager.togglePlay()
                        }
                    } else {
                        if (manager.isPlaying()) {
                            manager.togglePlay()
                        }
                    }
                    if (isDanmakuEnabled != manager.isDanmakuEnabled()) {
                        manager.toggleDanmaku()
                    }
                }
            }
        }
    }

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isMobileMode()) {
            initMobileVideoInfoView()
            return
        }

        setContentView(R.layout.activity_simple_viewpager)

        Glide.get(BiliTerminal.context).clearMemory()
        val intent = intent
        var type = intent.getStringExtra("type")
        if (type == null) type = "video"
        this.aid = intent.getLongExtra("aid", 114514)
        this.bvid = intent.getStringExtra("bvid")
        this.seek_reply = intent.getLongExtra("seekReply", -1)

        viewPager = findViewById(R.id.viewPager)
        loading = findViewById(R.id.loading)
        setupLongPressToRoot()
        if (type == "media") initMediaInfoView()
        else initVideoInfoView()
    }

    fun initMediaInfoView() {
        setPageName("番剧详情")

        fragmentList = ArrayList(2)
        contentFragment = BangumiInfoFragment.newInstance(aid)
        fragmentList!!.add(contentFragment!!)
        replyFragment = ReplyFragment.newInstance(aid, 1, seek_reply == -1L, seek_reply)
        fragmentList!!.add(replyFragment!!)

        viewPager.offscreenPageLimit = fragmentList!!.size
        val vpfAdapter = ViewPagerFragmentAdapter(supportFragmentManager, fragmentList!!)
        viewPager.adapter = vpfAdapter
        if (seek_reply != -1L) viewPager.currentItem = 1
        if (SharedPreferencesUtil.getBoolean("first_videoinfo", true)) {
            MsgUtil.showMsgLong("提示：本页面可以左右滑动")
            SharedPreferencesUtil.putBoolean("first_videoinfo", false)
        }
    }

    protected fun initVideoInfoView() {
        TutorialHelper.showTutorialList(this, R.array.tutorial_video, 1)
        TutorialHelper.showPagerTutorial(this, 3)

        setPageName("视频详情")
        TerminalContext.getInstance().getVideoInfoByAidOrBvId(aid, bvid).observe(this) { result ->
            result.onSuccess { videoInfo ->
                aid = videoInfo.aid
                bvid = videoInfo.bvid
                fragmentList = ArrayList(3)
                contentFragment = VideoInfoFragment.newInstance(videoInfo.aid, bvid!!)
                fragmentList!!.add(contentFragment!!)
                replyFragment = ReplyFragment.newInstance(videoInfo.aid, 1, videoInfo.stats.reply, seek_reply, videoInfo.staff[0].mid)
                replyFragment!!.setManager(videoInfo.staff)
                fragmentList!!.add(replyFragment!!)
                if (SharedPreferencesUtil.getBoolean("related_enable", true)) {
                    val vrFragment = VideoRcmdFragment.newInstance(videoInfo.aid)
                    fragmentList!!.add(vrFragment)
                }
                viewPager.offscreenPageLimit = fragmentList!!.size
                val vpfAdapter = ViewPagerFragmentAdapter(supportFragmentManager, fragmentList!!)
                viewPager.adapter = vpfAdapter
                if (seek_reply != -1L) viewPager.currentItem = 1
            }.onFailure { error ->
                loading.setImageResource(R.mipmap.loading_2233_error)
                MsgUtil.showMsg("获取信息失败！\n可能是视频不存在？")
                CenterThreadPool.runOnUIThreadAfter(5L, TimeUnit.SECONDS) {
                    MsgUtil.err(error)
                }
            }
        }
    }

    fun setCurrentAid(aid: Long) {
        if (replyFragment != null) runOnUiThread { replyFragment!!.refresh(aid) }
    }

    fun crossFade(fragmentView: View?) {
        AnimationUtils.crossFade(loading, fragmentView)
        fragmentView?.post {
            val scrollView = fragmentView.findViewById<View>(R.id.scrollView)
            if (scrollView != null) {
                scrollView.isFocusable = true
                scrollView.isFocusableInTouchMode = true
                scrollView.requestFocus()
            }
        }
    }

    override fun eventBusEnabled(): Boolean {
        return true
    }

    @Subscribe(threadMode = ThreadMode.ASYNC, sticky = true, priority = 1)
    fun onEvent(event: ReplyEvent) {
        replyFragment!!.notifyReplyInserted(event)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onCloseAllVideoPages(event: CloseAllVideoPagesEvent) {
        if (!isFinishing && !isDestroyed) {
            finish()
        }
    }

    private fun setupLongPressToRoot() {
        val topBar = findViewById<View>(R.id.top)
        topBar?.setOnLongClickListener {
            MsgUtil.showMsg("已返回初始页面")
            EventBus.getDefault().post(CloseAllVideoPagesEvent())
            true
        }
    }

    private fun initMobileVideoInfoView() {
        setContentView(R.layout.activity_video_info_mobile)
        Glide.get(BiliTerminal.context).clearMemory()

        val intent = intent
        var type = intent.getStringExtra("type")
        if (type == null) type = "video"
        this.aid = intent.getLongExtra("aid", 114514)
        this.bvid = intent.getStringExtra("bvid")
        this.seek_reply = intent.getLongExtra("seekReply", -1)

        setPageName("视频详情")
        setTopbarExit()
        setupLongPressToRoot()

        tabIntro = findViewById(R.id.tab_intro)
        tabComment = findViewById(R.id.tab_comment)
        contentContainer = findViewById(R.id.content_container)
        loading = findViewById(R.id.loading)
        playerContainer = findViewById(R.id.player_container)
        playerContainerOriginalHeight = playerContainer!!.layoutParams.height

        danmakuInputPanel = findViewById(R.id.danmaku_input_panel)
        danmakuEdit = findViewById(R.id.danmaku_edit)

        setupTabSwitching()
        setupInlinePlayer()
        setupDanmakuControls()
        setupScrollListener()
        setupBackToTop()
        setupKeyboardListener()

        if (type == "media") {
            setPageName("番剧详情")
            initMobileMediaInfoView()
        } else {
            loadVideoDataForMobile()
        }
    }

    private fun initMobileMediaInfoView() {
        contentFragment = BangumiInfoFragment.newInstance(aid)
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_container, contentFragment!!)
            .commit()

        tabComment?.setOnClickListener {
            replyFragment = ReplyFragment.newInstance(aid, 1, seek_reply == -1L, seek_reply)
            supportFragmentManager.beginTransaction()
                .replace(R.id.content_container, replyFragment!!)
                .commit()
            tabIntro?.setTextColor(resources.getColor(android.R.color.darker_gray))
            tabComment?.setTextColor(resources.getColor(android.R.color.black))
        }

        tabIntro?.setOnClickListener {
            contentFragment = BangumiInfoFragment.newInstance(aid)
            supportFragmentManager.beginTransaction()
                .replace(R.id.content_container, contentFragment!!)
                .commit()
            tabIntro?.setTextColor(resources.getColor(android.R.color.black))
            tabComment?.setTextColor(resources.getColor(android.R.color.darker_gray))
        }
    }

    private fun setupInlinePlayer() {
        val surfaceView = findViewById<android.view.SurfaceView>(R.id.player_surface)
        val danmakuView = findViewById<DanmakuView>(R.id.player_danmaku)
        val playBtn = findViewById<ImageView>(R.id.player_play_btn)
        val progressText = findViewById<TextView>(R.id.player_progress)
        val durationText = findViewById<TextView>(R.id.player_duration)
        val seekBar = findViewById<SeekBar>(R.id.player_seekbar)
        val playerTitle = findViewById<TextView>(R.id.player_title)
        val loadingLayout = findViewById<View>(R.id.player_loading)
        val loadingIcon = findViewById<ImageView>(R.id.player_loading_icon)
        val loadingText0 = findViewById<TextView>(R.id.player_loading_text0)
        val loadingText1 = findViewById<TextView>(R.id.player_loading_text1)
        val fullscreenBtn = findViewById<ImageView>(R.id.player_fullscreen_btn)

        val controlsLayout = findViewById<View>(R.id.player_controls)
        inlinePlayerManager = InlinePlayerManager(
            this, surfaceView, danmakuView, playBtn, progressText, durationText, seekBar,
            loadingLayout, loadingIcon, loadingText0, loadingText1, controlsLayout
        ) { videoWidth, videoHeight ->
            adjustPlayerSize(videoWidth, videoHeight)
        }

        val playerContainer = findViewById<View>(R.id.player_container)
        playerContainer.setOnTouchListener { _, event ->
            if (danmakuInputPanel.visibility == View.VISIBLE) {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    hideDanmakuPanel()
                }
                true
            } else {
                inlinePlayerManager?.onTouchEvent(event)
                if (event.action == MotionEvent.ACTION_UP) {
                    inlinePlayerManager?.deactivateLongPressSpeed()
                }
                true
            }
        }

        playBtn.setOnClickListener {
            inlinePlayerManager?.togglePlay()
        }

        fullscreenBtn.setOnClickListener {
            openFullscreenPlayer()
        }

        playerTitle.text = "加载中..."
    }

    private fun adjustPlayerSize(videoWidth: Int, videoHeight: Int) {
        if (videoWidth <= 0 || videoHeight <= 0) return
        playerContainer?.let { container ->
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels
            
            videoAspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
            val minHeight = screenWidth * 9 / 16

            val idealHeight = (screenWidth / videoAspectRatio).toInt()
            val maxHeight = (screenHeight * 2 / 3f).toInt()
            val containerHeight = idealHeight.coerceIn(minHeight, maxHeight)

            container.layoutParams = container.layoutParams.apply {
                width = screenWidth
                height = containerHeight
            }

            savedPlayerMaxHeight = containerHeight
            savedPlayerMinHeight = minHeight
        }
    }

    private fun openFullscreenPlayer() {
        playerData?.let { data ->
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("url", data.videoUrl)
                putExtra("danmaku", data.danmakuUrl)
                putExtra("title", data.title)
                putExtra("aid", data.aid)
                putExtra("cid", data.cid)
                putExtra("mid", data.mid)
                putExtra("progress", inlinePlayerManager?.getCurrentPosition() ?: data.progress)
                putExtra("from", "mobile")
                putExtra("isPlaying", inlinePlayerManager?.isPlaying() ?: false)
                putExtra("isDanmakuEnabled", inlinePlayerManager?.isDanmakuEnabled() ?: true)
                putExtra("quality", data.qn)
            }
            fullscreenResult.launch(intent)
        }
    }

    private fun setupDanmakuControls() {
        val danmakuToggleBtn = findViewById<ImageView>(R.id.danmaku_toggle_btn)
        val danmakuSendBtn = findViewById<TextView>(R.id.danmaku_send_btn)
        val danmakuSendOkBtn = findViewById<TextView>(R.id.danmaku_send_ok_btn)

        danmakuToggleBtn.setOnClickListener {
            inlinePlayerManager?.let { manager ->
                val enabled = manager.toggleDanmaku()
                danmakuToggleBtn.alpha = if (enabled) 1.0f else 0.3f
            }
        }

        danmakuSendBtn.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastDanmakuClickTime < 300) return@setOnClickListener
            lastDanmakuClickTime = now

            wasPlayingBeforeDanmaku = inlinePlayerManager?.isPlaying() ?: false
            if (wasPlayingBeforeDanmaku) {
                inlinePlayerManager?.pause()
            }
            danmakuInputPanel.visibility = View.VISIBLE
            danmakuEdit.requestFocus()
            Handler(Looper.getMainLooper()).postDelayed({
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(danmakuEdit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }, 100)
        }

        danmakuSendOkBtn.setOnClickListener {
            val text = danmakuEdit.text.toString().trim()
            if (text.isNotEmpty()) {
                sendDanmaku(text)
                danmakuEdit.setText("")
            }
            hideDanmakuPanel()
        }

        danmakuEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && danmakuInputPanel.visibility == View.VISIBLE) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!danmakuEdit.hasFocus()) {
                        hideDanmakuPanel()
                    }
                }, 200)
            }
        }
    }

    private fun hideDanmakuPanel() {
        danmakuInputPanel.visibility = View.GONE
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(danmakuEdit.windowToken, 0)
        if (wasPlayingBeforeDanmaku && inlinePlayerManager?.isPlaying() == false) {
            inlinePlayerManager?.togglePlay()
        }
    }

    private fun sendDanmaku(text: String) {
        playerData?.let { data ->
            CenterThreadPool.run {
                try {
                    val result = DanmakuApi.sendVideoDanmakuByAid(
                        data.cid, text, data.aid,
                        inlinePlayerManager?.getCurrentPosition() ?: 0,
                        ToolsUtil.getRgb888(android.graphics.Color.WHITE), 1
                    )
                    runOnUiThread {
                        if (result == 0) {
                            MsgUtil.showMsg("发送成功")
                            addDanmaku(text)
                        } else {
                            MsgUtil.showMsg("发送失败")
                        }
                    }
                } catch (e: Exception) {
                    Logu.e("Danmaku", "Send danmaku error: ${e.message}")
                    runOnUiThread {
                        MsgUtil.showMsg("弹幕发送失败")
                    }
                }
            }
        }
    }

    private fun addDanmaku(text: String) {
        inlinePlayerManager?.addDanmaku(text)
    }

    private fun loadVideoDataForMobile() {
        TerminalContext.getInstance().getVideoInfoByAidOrBvId(aid, bvid).observe(this) { result ->
            result.onSuccess { videoInfo ->
                aid = videoInfo.aid
                bvid = videoInfo.bvid

                tabComment.text = "评论(${videoInfo.stats.reply})"

                val playerTitle = findViewById<TextView>(R.id.player_title)
                playerTitle.text = videoInfo.title

                contentFragment = VideoInfoFragment.newInstance(videoInfo.aid, bvid!!)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.content_container, contentFragment!!)
                    .commit()

                replyFragment = ReplyFragment.newInstance(
                    videoInfo.aid, 1, videoInfo.stats.reply, seek_reply,
                    videoInfo.staff[0].mid
                )
                replyFragment!!.setManager(videoInfo.staff)

                startInlinePlayer(videoInfo)

                setupRelatedVideos()

                if (seek_reply != -1L) switchToTab(1)
            }.onFailure { error ->
                loading.setImageResource(R.mipmap.loading_2233_error)
                MsgUtil.showMsg("获取信息失败！\n可能是视频不存在？")
                CenterThreadPool.runOnUIThreadAfter(5L, TimeUnit.SECONDS) {
                    MsgUtil.err(error)
                }
            }
        }
    }

    private fun startInlinePlayer(videoInfo: VideoInfo) {
        CenterThreadPool.run {
            try {
                playerData = videoInfo.toPlayerData(0)
                PlayerApi.getVideo(playerData!!, false)

                if (playerData != null && playerData!!.videoUrl != null) {
                    runOnUiThread {
                        inlinePlayerManager?.setVideoUrl(
                            playerData!!.videoUrl!!,
                            playerData!!.danmakuUrl
                        )
                    }
                }
            } catch (e: Exception) {
                Logu.e("InlinePlayer", "Start player error: ${e.message}")
            }
        }
    }

    private fun setupTabSwitching() {
        tabIntro.setOnClickListener { if (currentTab != 0) switchToTab(0) }
        tabComment.setOnClickListener { if (currentTab != 1) switchToTab(1) }
    }

    private fun switchToTab(tab: Int) {
        currentTab = tab
        val selectedColor = ThemeUtils.getPrimaryColor()
        val unselectedColor = ThemeUtils.getGrayColor()

        if (tab == 0) {
            if (contentFragment != null && contentFragment!!.isAdded) {
                supportFragmentManager.beginTransaction().show(contentFragment!!).commit()
            }
            if (replyFragment != null && replyFragment!!.isAdded) {
                supportFragmentManager.beginTransaction().hide(replyFragment!!).commit()
            }
            tabIntro.setTextColor(selectedColor)
            tabComment.setTextColor(unselectedColor)
        } else {
            if (contentFragment != null && contentFragment!!.isAdded) {
                supportFragmentManager.beginTransaction().hide(contentFragment!!).commit()
            }
            if (replyFragment != null) {
                if (!replyFragment!!.isAdded) {
                    supportFragmentManager.beginTransaction()
                        .add(R.id.content_container, replyFragment!!)
                        .commit()
                } else {
                    supportFragmentManager.beginTransaction().show(replyFragment!!).commit()
                }
            }
            tabComment.setTextColor(selectedColor)
            tabIntro.setTextColor(unselectedColor)
        }
    }

    private fun setupRelatedVideos() {
        val relatedContainer = findViewById<FrameLayout>(R.id.related_container)

        if (SharedPreferencesUtil.getBoolean("related_enable", true)) {
            val vrFragment = VideoRcmdMobileFragment.newInstance(aid)
            supportFragmentManager.beginTransaction()
                .add(R.id.related_container, vrFragment)
                .commit()
            relatedContainer.visibility = View.VISIBLE
        }
    }

    private fun setupScrollListener() {
        scrollView = findViewById(R.id.scroll_view)
        scrollView?.viewTreeObserver?.addOnScrollChangedListener {
            val sv = scrollView ?: return@addOnScrollChangedListener
            val container = playerContainer ?: return@addOnScrollChangedListener
            if (savedPlayerMaxHeight <= 0 || savedPlayerMinHeight <= 0) return@addOnScrollChangedListener

            val scrollY = sv.scrollY
            val maxDelta = savedPlayerMaxHeight - savedPlayerMinHeight
            if (maxDelta <= 0) return@addOnScrollChangedListener

            val newHeight = (savedPlayerMaxHeight - scrollY).coerceIn(savedPlayerMinHeight, savedPlayerMaxHeight)
            if (newHeight != container.layoutParams.height) {
                val heightDelta = container.layoutParams.height - newHeight
                animatePlayerHeight(newHeight)
                container.requestLayout()
                if (heightDelta != 0) {
                    sv.scrollBy(0, -heightDelta)
                }
            }
        }
    }

    private fun animatePlayerHeight(targetHeight: Int) {
        val container = playerContainer ?: return
        heightAnimator?.cancel()
        val currentHeight = container.layoutParams.height
        if (currentHeight == targetHeight) return

        heightAnimator = ValueAnimator.ofInt(currentHeight, targetHeight).apply {
            duration = 100
            interpolator = android.view.animation.DecelerateInterpolator(1.5f)
            addUpdateListener { animator ->
                val height = animator.animatedValue as Int
                container.layoutParams.height = height
                container.requestLayout()
            }
            start()
        }
    }

    private fun setupKeyboardListener() {
        val rootView = findViewById<View>(android.R.id.content)
        keyboardListener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            if (keypadHeight > screenHeight * 0.15) {
                // 键盘弹出 - 确保弹幕面板可见
                if (danmakuInputPanel.visibility != View.VISIBLE) {
                    danmakuInputPanel.visibility = View.VISIBLE
                }
            } else {
                // 键盘收起 - 隐藏弹幕面板
                if (danmakuInputPanel.visibility == View.VISIBLE && !danmakuEdit.hasFocus()) {
                    hideDanmakuPanel()
                }
            }
        }
        rootView.viewTreeObserver.addOnGlobalLayoutListener(keyboardListener!!)
    }

    private fun setupBackToTop() {
        btnBackToTop = findViewById(R.id.btn_back_to_top)
        btnBackToTop?.setOnClickListener {
            scrollView?.let { sv ->
                sv.smoothScrollTo(0, 0)
                btnBackToTop?.visibility = View.GONE
            }
        }
        scrollView?.viewTreeObserver?.addOnScrollChangedListener {
            val sv = scrollView ?: return@addOnScrollChangedListener
            btnBackToTop?.visibility = if (sv.scrollY > sv.height / 2) View.VISIBLE else View.GONE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            inlinePlayerManager?.let { manager ->
                if (!manager.isPlaying() && manager.isPrepared()) {
                    manager.resumePlayback()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        inlinePlayerManager?.pause()
    }

    override fun onResume() {
        super.onResume()
        inlinePlayerManager?.resumePlayback()
    }

    override fun onDestroy() {
        Logu.d("onDestroy")
        keyboardListener?.let {
            findViewById<View>(android.R.id.content).viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
        heightAnimator?.cancel()
        Glide.get(BiliTerminal.context).clearMemory()
        TerminalContext.getInstance().leaveDetailPage()
        inlinePlayerManager?.release()
        super.onDestroy()
    }
}