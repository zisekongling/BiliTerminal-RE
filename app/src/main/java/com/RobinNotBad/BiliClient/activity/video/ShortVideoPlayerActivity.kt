package com.RobinNotBad.BiliClient.activity.video

import com.RobinNotBad.BiliClient.util.UiText

import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.api.DanmakuApi
import com.RobinNotBad.BiliClient.api.ShortVideoFeedApi
import com.RobinNotBad.BiliClient.model.ShortVideoItem
import com.RobinNotBad.BiliClient.ui.widget.HighEnergyProgressBar
import com.RobinNotBad.BiliClient.ui.widget.SwipePageLayout
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.DisguiseGesture
import com.RobinNotBad.BiliClient.util.DisguisePrefs
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.VideoPreloadManager
import com.RobinNotBad.BiliClient.player.DanmakuManager
import com.RobinNotBad.BiliClient.player.PlayerDefaults
import com.RobinNotBad.BiliClient.player.IjkOption
import com.RobinNotBad.BiliClient.player.IjkPlayerBridge
import com.RobinNotBad.BiliClient.player.PlayerState
import com.bumptech.glide.Glide
import master.flame.danmaku.ui.widget.DanmakuView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tv.danmaku.ijk.media.player.IjkMediaPlayer

class ShortVideoPlayerActivity : InstanceActivity() {

    /**
     * 本页要自己处理横滑与「短视频页右滑退出」，必须关掉系统窗口层的右滑返回：
     * 那只手势在窗口层直接 finish()，不走 onBackPressed，应用根本收不到。
     */
    override val forceNoSwipeTheme: Boolean
        get() = true

    /**
     * 外壳横向 pager：第 0 页短视频流，第 1~3 页详情/评论/相关推荐（与手机版一致）。
     *
     * 用经典 `ViewPager` 而不是 `ViewPager2`：横向翻页交给它原生处理，滚动走
     * `scrollTo`，不经过「自定义手势 → fakeDrag → RecyclerView」这条链路。
     * 26.10.24 真机上 `ViewPager2 + fakeDrag` 的跟手/吸附始终有一顿一顿的残留，
     * 且 fakeDrag 状态不配对时会直接崩溃，故整体换回与普通视频详情页同款的
     * `ViewPager + ViewPagerFragmentAdapter` 方案（右滑退出由第 0 页根布局单独判）。
     */
    private lateinit var pager: ViewPager
    private lateinit var shellAdapter: ShortVideoShellAdapter

    var screenWidth = 0
    var screenHeight = 0
    internal var isBottomControlVisible = true

    /**
     * 本页当前是否在前台（start 之后、stop 之前）。
     *
     * 保亮屏判据之一：只有前台才允许挂 [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON]。
     * 少了这道闸，后台里任意一次 `isPlaying = true`（例如 prepare 完成时页面其实已经不可见）
     * 都会把标志加回已经 stop 的窗口，屏幕上长期「亮着」却没有任何画面。
     */
    private var isForeground = false

    /**
     * 弹幕开关（反馈 #55，原先每个 PageHolder 写死 true，设置里的「播放默认值 → 弹幕」不生效）。
     *
     * 放在 Activity 而不是 PageHolder：刷到下一条时用户刚关掉的弹幕要保持关闭，
     * 每条各存一份就会「关了这条、下一条又开了」。开播值与长视频同一套三态 + 上次记录。
     */
    internal var isDanmakuVisible = PlayerDefaults.resolveTriState(
        SharedPreferencesUtil.getString(SettingsKeys.PLAYER_DEFAULT_DANMAKU, SettingsKeys.PLAYER_DEFAULT_MODE_LAST),
        SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_LAST_DANMAKU, true)
    )

    /**
     * 圆屏适配开关（与 `BaseActivity.setRound()`、`PlayerActivity.initUI()` 读同一个 key）。
     *
     * 本页顶栏在 ViewPager item 里，`BaseActivity.setRound()` 找不到 `R.id.pageName` 会整体跳过，
     * 所以圆屏留白要由 [PageHolder.applyRoundInsets] 自己加。
     */
    internal val roundAdapted: Boolean
        get() = SharedPreferencesUtil.getBoolean(SettingsKeys.UI_ROUND, false)

    /** 圆屏适配的基准边距，与 `PlayerActivity.initUI()` 同口径（屏宽 3%）。 */
    internal val roundPadding: Int
        get() = (screenWidth * 0.03f).toInt()

    /**
     * 内容区尺寸（已扣掉 BaseActivity 给 decorView 根布局加的「界面边距 / 圆屏留白」）。
     *
     * 视频实际可用的区域比整屏小，用整屏尺寸算画面大小会被裁掉；两个字段由
     * `BaseActivity.onCreate` 算好，异常情况下为 0 时退回整屏尺寸。
     */
    internal val contentWidth: Int
        get() = if (window_width > 0) window_width else screenWidth

    internal val contentHeight: Int
        get() = if (window_height > 0) window_height else screenHeight

    /** 右侧详情三页当前对应的 aid；变化时调用 [onFeedAnchorChanged] 重绑。 */
    private var detailAnchorAid = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_short_video_player)

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        pager = findViewById(R.id.pager)
        shellAdapter = ShortVideoShellAdapter(
            this,
            // 复用「视频详情页相关推荐」的既有开关：关掉的人，短视频第 4 页也不出现
            relatedEnabled = SharedPreferencesUtil.getBoolean(SettingsKeys.RELATED_ENABLE, true)
        )
        pager.adapter = shellAdapter
        // 详情/评论/相关推荐三页常驻：滑过去是纯横向动画，不用等它们现建
        pager.offscreenPageLimit = shellAdapter.count
        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                if (position == 0) {
                    // 从详情页滑回短视频时，当前条可能已经变了（例如播完自动接下一条）
                    onFeedAnchorChanged()
                    // 横滑去详情页时暂停了信息流播放，滑回第 0 页（含中途取消）恢复
                    shellAdapter.activeFeedFragment?.resumeAfterShellSwipe()
                } else {
                    // 滑去详情页时信息流整页还在屏幕上，视频继续解码会让两页同时出帧，
                    // 真机上明显加重横滑负担；先暂停，滑回来再恢复。
                    shellAdapter.activeFeedFragment?.pauseForShellSwipe()
                }
            }
        })

        // 教程改由 BaseActivity 按 Tutorials 注册表集中触发
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        // 可用区域变了：各页按新尺寸重算画面大小与亮屏判据。
        // 原先 `notifyScreenSizeChanged()` 全仓无调用点，旋转/边距变化后画面会停在旧尺寸。
        if (::shellAdapter.isInitialized) {
            shellAdapter.activeFeedFragment?.onHostScreenSizeChanged()
        }
        syncKeepScreenOn()
    }

    override fun onStart() {
        super.onStart()
        isForeground = true
        // 回到前台重算一次：后台期间可能已经暂停过，别把上一次的亮屏状态带回来
        syncKeepScreenOn()
    }

    override fun onResume() {
        super.onResume()
        // Fragment 的 onResume（→ resumeCurrent）在 super.onResume() 里已经跑完，
        // 此时「回前台自动续播」已经把 isPlaying 置真；onStart 里那次算早一步，
        // 单靠它会出现「回到前台画面已经在播、亮屏标志却没加回来」的窗口。
        syncKeepScreenOn()
    }

    override fun onStop() {
        // 进后台一律交还亮屏权：Fragment 的 onPause 走 pauseCurrent() 也会清，
        // 但那条路依赖「活跃页引用还在」，这里再加一道兜底，避免窗口不可见却挂着标志。
        isForeground = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    override fun onBackPressed() {
        // 在右侧详情/评论/相关推荐页时，返回先回到短视频首页，而不是直接退出本页
        if (::pager.isInitialized && pager.currentItem > 0) {
            pager.setCurrentItem(0, true)
            return
        }
        super.onBackPressed()
    }

    /** 外壳当前页号（0 = 信息流，1~3 = 详情/评论/相关推荐）；页面还没建好时按 0。 */
    internal fun currentShellPage(): Int = if (::pager.isInitialized) pager.currentItem else 0

    /**
     * 短视频首页根布局判出「右滑越过阈值」后的回调（系统右滑返回已在本页关闭，
     * 由 [SwipePageLayout] 自己跟手预览并触发退出）。
     */
    internal fun handleSwipe(direction: Int) {
        if (isFinishing) return
        // 只有第 0 页会触发；右滑即退出本页
        if (direction > 0) finish()
    }

    /**
     * 短视频页换了当前条：把右侧详情/评论/相关推荐热更新到新视频。
     *
     * 注意不能去 `notifyItemRangeChanged`：那会让 ViewPager2 把已经显示出来的页面
     * 移除再重建，真机上出现过整页空白（详见 [ShortVideoShellAdapter] 注释）。
     */
    internal fun onFeedAnchorChanged() {
        if (!::pager.isInitialized) return
        val item = currentShortVideo()
        val aid = item?.aid ?: 0L
        if (aid == detailAnchorAid) return
        detailAnchorAid = aid
        shellAdapter.refreshDetailPages(aid, item?.bvid)
    }

    /** 当前这条短视频；右侧详情页据此渲染。 */
    fun currentShortVideo(): ShortVideoItem? {
        if (!::pager.isInitialized) return null
        val item = shellAdapter.activeFeedFragment?.currentShortVideo
        return item
    }

    /** 播放完成的回调转交（播放逻辑在短视频页里；外壳只做转发）。 */
    internal fun onVideoCompleted(position: Int) {
        shellAdapter.activeFeedFragment?.handleVideoCompleted(position)
    }

    /**
     * 重算「保持亮屏」。
     *
     * 短视频页原先**没有任何 keep-screen-on**（长视频页靠 `activity_player.xml` 的
     * `android:keepScreenOn="true"`），看短视频到点就熄屏。
     *
     * 走**窗口标志**而不是某个 View 的 `keepScreenOn`：View 级标志挂在
     * RecyclerView 的 item 上并不可靠（26.10.22 实测：暂停后窗口 `fl` 里仍留着
     * `KEEP_SCREEN_ON`）。判据取「当前活跃那一页是否真的在播」，所以暂停、滑走、
     * 进后台都会清掉，不会出现「停在暂停画面上也一直亮」。
     *
     * 每次播放状态变化都**整体重算**而不是增量开关：增量写法在
     * `releasePlayer()` 这类「本来就没在播」的路径上会漏掉，残留一次就是永久亮屏。
     *
     * **必须再叠一道前台判据**：判据链要穿过 `shellAdapter → activeFeedFragment →
     * PageHolder` 四层，任何一层为空都会算成「没在播」；反过来，页面不可见时
     * prepare 完成也会把 `isPlaying` 置真（Fragment 的 onPause 只暂停当前条目，
     * 不保证晚到的 prepare 回调不会重新起播），那时若无条件 `addFlags`，
     * 就会给一个已经 stop 的窗口挂上永久亮屏。
     */
    internal fun syncKeepScreenOn() {
        if (isFinishing || isDestroyed) return
        if (!::shellAdapter.isInitialized) return
        val keep = isForeground && shellAdapter.activeFeedFragment?.isCurrentPlaying() == true
        if (keep) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/**
 * 外壳 pager 的适配器：第 0 页是短视频流（[ShortVideoFeedFragment]），
 * 第 1~3 页是视频详情 / 评论 / 相关推荐（[ShortVideoDetailPageFragment]）。
 *
 * **页码与 itemId 都不能跟着 aid 变**：26.10.21 真机上试过把 aid 编进 `getItemId`
 * 再 `notifyItemRangeChanged`，旧 Fragment 的视图被摘掉后新视图挂不回来，整页只剩背景色。
 * 现在 id 恒等于页码，换视频时由 [refreshDetailPages] 直接通知三个页面壳自己换内容。
 */
@Suppress("DEPRECATION")
class ShortVideoShellAdapter(
    activity: ShortVideoPlayerActivity,
    private val relatedEnabled: Boolean
) : FragmentPagerAdapter(
    activity.supportFragmentManager,
    BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT
) {

    /** 右侧详情三页当前的锚点 aid；0 表示短视频还没加载出来。 */
    var detailAid: Long = 0L
        private set

    /** 与 [detailAid] 配套的 bvid，详情页问接口时要用。 */
    var detailBvid: String? = null
        private set

    /** 页码 -> 页面壳。页面壳自己持有渲染节流，换视频只通知它换内容，不换 Fragment 实例。 */
    private val shells = HashMap<Int, Fragment>()

    /** 短视频流那页；外壳从这里取「当前这条视频」并转交播放完成回调。 */
    var feedFragment: ShortVideoFeedFragment? = null
        private set

    /** 只在还挂在页面里时可用：Fragment 被回收后引用会失效。 */
    val activeFeedFragment: ShortVideoFeedFragment?
        get() = feedFragment?.takeIf { it.isAdded }

    override fun getCount(): Int = if (relatedEnabled) 4 else 3

    override fun getItem(position: Int): Fragment = shells.getOrPut(position) {
        if (position == 0) {
            ShortVideoFeedFragment().also { feedFragment = it }
        } else {
            ShortVideoDetailPageFragment.newInstance(position, detailAid, detailBvid)
        }
    }

    /** 页码与实例都保持稳定：换视频不走重建，避免滑到一半整页被移除再挂回来。 */
    override fun getItemPosition(obj: Any): Int = POSITION_UNCHANGED

    /**
     * 当前短视频换了：把右侧三页的页面壳热更新到新 aid。
     *
     * 不调用 `notifyDataSetChanged`：页面壳自己会节流重建子 Fragment，屏幕上的页面
     * 不会经历「移除 → 重建」的中间态。
     */
    fun refreshDetailPages(aid: Long, bvid: String?) {
        if (aid == detailAid && bvid == detailBvid) return
        detailAid = aid
        detailBvid = bvid
        for ((position, fragment) in shells) {
            if (position == 0) continue
            if (fragment.isAdded) {
                (fragment as ShortVideoDetailPageFragment).updateItem(aid, bvid)
            }
        }
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
        // 顶栏在分页 item 里（不在 Activity 的视图树上），BaseActivity 那边装不到。
        // 每次绑定时重算一次：用户可能在设置里把开关关掉了，已装上的触摸层不会自己消失。
        holder.syncDisguiseGesture()
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
        holder.setupPlayer()
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

    /**
     * 可用区域尺寸变了（旋转 / 界面边距 / 圆屏留白）：让各页按新尺寸重摆画面。
     *
     * 原先这个方法没有任何调用方，转屏后画面会停在旧尺寸；现在由
     * `ShortVideoPlayerActivity.onConfigurationChanged` → `ShortVideoFeedFragment`
     * 转过来。尺寸本身从 `playerBridge.state` 现取，不需要参数。
     */
    fun notifyScreenSizeChanged() {
        holders.values.forEach { it.updateVideoSize() }
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
        private val speedBoostHint: TextView = itemView.findViewById(R.id.text_speed_boost)

        // 圆屏适配前的原始 padding：圆屏下要在布局原有留白基础上再收进来，
        // 关掉开关时按这份快照还原（[applyRoundInsets] 可重复调用）
        private val baseTopPadding =
            intArrayOf(top.paddingLeft, top.paddingTop, top.paddingRight, top.paddingBottom)
        private val baseProgressPadding = intArrayOf(
            videoProgress.paddingLeft, videoProgress.paddingTop,
            videoProgress.paddingRight, videoProgress.paddingBottom
        )
        private val baseProgressTextPadding = intArrayOf(
            textProgress.paddingLeft, textProgress.paddingTop,
            textProgress.paddingRight, textProgress.paddingBottom
        )

        private val playerBridge = IjkPlayerBridge(onError = { what, _ ->
            Logu.e("ShortVideo", "Player error: $what")
            mainHandler.post {
                lastBufferingVisible = false
                bufferingIndicator.visibility = View.GONE
                // 出错后必须清 isPlaying：它是「保持亮屏」的判据，留着 true 会让窗口长期挂着
                // KEEP_SCREEN_ON（画面已经不动了却一直不熄屏）
                isPlaying = false
                playIcon.visibility = View.VISIBLE
                buttonVideo.setImageResource(R.drawable.btn_player_play)
            }
            MsgUtil.showMsg(UiText.of("播放错误"))
        })
        private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private var stateJob: Job? = null
        private var playerReady = false
        private var textureView: TextureView? = null
        private var danmakuView: DanmakuView? = null
        private var danmakuManager: DanmakuManager? = null
        private var currentItem: ShortVideoItem? = null
        // 视频真实尺寸（已按旋转角交换宽高）。0 表示还没拿到。
        private var videoWidth = 0
        private var videoHeight = 0
        // 已经摆到 TextureView 上的尺寸（去重用）：state 每 250ms 都会更新一次，
        // 不去重就会在每个采样周期都改一遍 layoutParams。
        private var appliedVideoWidth = 0
        private var appliedVideoHeight = 0
        // 拿不到尺寸时的退化宽高比（优先沿用上一条有效比例，其次 16:9）。
        // 绝不能让 TextureView 停在 MATCH_PARENT：那等于把画面硬拉满整屏。
        private var fallbackRatio = ShortVideoLayoutMath.DEFAULT_RATIO
        private var videoAll = 0
        // 主线程在 state.collect 里写、弹幕渲染线程在位置回调里读，必须 volatile，
        // 否则弹幕线程可能读到撕裂或过期的毫秒值。这里是"内存值"，
        // 存在的意义就是让弹幕回调不再直接调 JNI（见下方 danmakuManager 的说明）。
        @Volatile
        private var videoNow = 0
        private var videoNowLast = 0
        // 已渲染到文本的整秒值，用于避免每 250ms 重复拼字符串与 setText
        private var videoNowLastSec = -1
        private var progressStr = "00:00"
        // 主线程写、弹幕渲染线程读（见 danmakuManager 的位置回调），必须 volatile，
        // 否则弹幕线程可能读到过期的 true，继续把重建窗口期的脏位置灌进 DanmakuTimer
        @Volatile
        private var isPrepared = false

        /**
         * 播放状态。
         *
         * setter 里顺带通知宿主重算「保持亮屏」：本页原先**没有任何 keep-screen-on**
         * （长视频页靠 `activity_player.xml` 根布局的 `android:keepScreenOn="true"`，
         * 短视频这套布局没有），看短视频到点就熄屏。
         *
         * `@Volatile`：主线程写、弹幕渲染线程（媒体时钟的 isPlaying 回调）读。
         */
        @Volatile
        private var isPlaying = false
            set(value) {
                field = value
                activity.syncKeepScreenOn()
            }
        private var isSeeking = false
        private var isActive = false         // 当前页面是否为活跃状态
        private var isLoadingDanmaku = false // 是否正在加载弹幕
        // 弹幕开关在 Activity 上（跨条保持），见 ShortVideoPlayerActivity.isDanmakuVisible
        private var isDanmakuVisible: Boolean
            get() = activity.isDanmakuVisible
            set(value) { activity.isDanmakuVisible = value }

        private val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        private var currentSpeedIndex = 2
        private var currentSpeed = 1.0f

        /** 长按临时加速用的倍速：松开后要还原成 [currentSpeed]，不能污染用户选的档位。 */
        private val boostSpeed = 3.0f
        private var speedBoosted = false

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
                // 页面已经销毁时 Glide.with(activity) 会直接抛
                // "You cannot start a load for a destroyed activity"（线上实测 2 例）：
                // 预加载线程回来、分页还没来得及回收时 onBindViewHolder 仍会被调用。
                if (GlideUtil.canLoad(coverImage)) {
                    Glide.with(activity)
                        .asDrawable()
                        .load(GlideUtil.url_hq(item.cover))
                        .into(coverImage)
                }
            }

            textTitle.text = item.title

            isInitialized = true

            applyRoundInsets()

            if (activity.isBottomControlVisible) {
                bottomControl.visibility = View.VISIBLE
            } else {
                bottomControl.visibility = View.GONE
            }
        }

        /**
         * 圆屏适配：把顶栏、进度条/进度文本与弹幕按圆形屏幕收进来。
         *
         * 本页的顶栏在 RecyclerView 的 item 里（不在 Activity 的视图树上），
         * `BaseActivity.setRound()` 只处理带 `R.id.pageName` 的整页布局，对它无效；
         * 此前表现为「开了圆屏适配，短视频页只有外层留白变了，顶栏返回键/左下进度文本
         * 和底部按钮仍贴在边缘，被圆盘切掉」。这里按 `PlayerActivity.initUI()` 的圆屏分支
         * 同口径重排（基准 margin = 屏宽 3%，顶栏横向 7 倍、进度条 4 倍、进度文本 3 倍）。
         *
         * 幂等：每次都按布局原始 padding 重算，开关关掉时还原。
         */
        fun applyRoundInsets() {
            val round = activity.roundAdapted
            if (round) {
                val padding = activity.roundPadding
                top.setPadding(padding * 7, padding, padding * 7, 0)
                videoProgress.setPadding(
                    padding * 4, baseProgressPadding[1], padding * 4, baseProgressPadding[3]
                )
                textProgress.setPadding(
                    padding * 3, baseProgressTextPadding[1],
                    baseProgressTextPadding[2], baseProgressTextPadding[3]
                )
            } else {
                top.setPadding(baseTopPadding[0], baseTopPadding[1], baseTopPadding[2], baseTopPadding[3])
                videoProgress.setPadding(
                    baseProgressPadding[0], baseProgressPadding[1],
                    baseProgressPadding[2], baseProgressPadding[3]
                )
                textProgress.setPadding(
                    baseProgressTextPadding[0], baseProgressTextPadding[1],
                    baseProgressTextPadding[2], baseProgressTextPadding[3]
                )
            }

            // 弹幕上下让开圆盘；横向滚动照旧（弹幕从圆边淡出是圆屏的正常表现）
            danmakuView?.let { view ->
                val params = view.layoutParams as? FrameLayout.LayoutParams ?: return@let
                val inset = if (round) activity.roundPadding * 3 else 0
                params.setMargins(params.leftMargin, inset, params.rightMargin, inset)
                view.layoutParams = params
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
                    // 长按不再跳视频详情页，改为「按住 3 倍速、松手还原」（与手机版短视频一致）。
                    // 详情页现在是横向第 2 页，右滑即可到达，不需要再用长按另开一个 Activity。
                    startSpeedBoost()
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
                // 长按加速是「按住生效」：抬手、被父容器打断（CANCEL）、或第二根手指按下
                // （开始捏合缩放）都要立刻还原倍速，否则会一直停在 3 倍速上。
                when (event.actionMasked) {
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL,
                    MotionEvent.ACTION_POINTER_DOWN -> endSpeedBoost()
                }
                // 必须返回 true：返回 false 时本 View 不会成为触摸目标，父容器（竖向 pager）
                // 接管后续事件后**不会**再给这里发 MOVE/UP/CANCEL，GestureDetector 收不到抬手，
                // 于是每次点按/滑动都会在 500ms 后误触发 onLongPress
                // （真机实测：点一下、上下滑一下都会跳详情页）。返回 true 不影响父级滚动：
                // pager 在手指真正纵向移动后仍会 onInterceptTouchEvent 抢走手势并给这里发 CANCEL。
                true
            }

            top.setOnClickListener {
                // 短视频是 InstanceActivity 的「菜单入口页」：顶栏点击的语义是「展开主菜单」，
                // 而不是 finish 掉本页。原来直接 finish() 会把整个页面关掉——短视频作为启动页时
                // 更是直接退出应用。改用基类统一的 menuClick（与推荐/热门/排行榜等页面一致）。
                // 展开菜单前先暂停当前短视频与弹幕，避免菜单在前台时视频仍在后台播放。
                try {
                    pause()
                } catch (e: Exception) {
                    Logu.e("ShortVideo", "顶栏回菜单暂停异常: ${e.message}")
                }
                activity.menuClick.run()
            }
            // 双击顶栏伪装：短视频的顶栏在分页 item 里（不在 Activity 的视图树上），
            // BaseActivity 那边找不到，必须在这里按 item 装。
            // 先装一次，之后每次 onBindViewHolder 由 [syncDisguiseGesture] 重算（开关可能被关掉）。
            syncDisguiseGesture()

            // 可用区域尺寸变化后重摆画面：`adjustVideoSize()` 在区域还是 0 时会早退，
            // 而 prepare 往往早于本页完成布局（尤其是慢机型），只在 onPrepared 算一次会
            // 让 TextureView 留在 MATCH_PARENT（满屏拉伸）。
            itemView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                    adjustVideoSize()
                }
            }

            initBottomButtons()
        }

        private fun initBottomButtons() {
            buttonVideo.setOnClickListener {
                controlVideo()
            }

            buttonDanmaku.setImageResource(if (isDanmakuVisible) R.mipmap.danmakuon else R.mipmap.danmakuoff)
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

            applySpeedToPlayers(currentSpeed)
        }

        /**
         * 同时把倍速交给播放器与弹幕。
         *
         * **弹幕必须一起设**：`MediaClockSync` 用 `DrawHandler.mSpeed` 做时间外推与步进放大
         * （见 `docs/architecture-map.md` §7.51）。只设播放器的话，2x 下媒体时间每秒推进 2000ms
         * 而弹幕时钟按 1x 追，越播越落后；3x 更是完全追不上。
         */
        private fun applySpeedToPlayers(speed: Float) {
            playerBridge.setSpeed(speed)
            danmakuManager?.setSpeed(speed)
        }

        /**
         * 长按临时切到 [boostSpeed]（3 倍速），松手由 [endSpeedBoost] 还原。
         *
         * 只改播放器倍速与按钮文案，不动 [currentSpeed]/[currentSpeedIndex]——用户选的档位要保持，
         * 否则长按一次就把底部按钮的倍速档位改掉了。
         */
        private fun startSpeedBoost() {
            if (speedBoosted || isScaling) return
            speedBoosted = true
            applySpeedToPlayers(boostSpeed)
            buttonSpeed.text = "${boostSpeed}x"

            hideVolumeRunnable?.let { mainHandler.removeCallbacks(it) }
            speedBoostHint.text = activity.getString(R.string.text_shortvideoplayer_speed_boost)
            speedBoostHint.visibility = View.VISIBLE
        }

        private fun endSpeedBoost() {
            if (!speedBoosted) return
            speedBoosted = false
            applySpeedToPlayers(currentSpeed)
            buttonSpeed.text = "${currentSpeed}x"
            speedBoostHint.visibility = View.GONE
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
            // 「播放默认值 → 弹幕 = 沿用上次」的记录位，与长视频共用
            SharedPreferencesUtil.putBoolean(SettingsKeys.PLAYER_LAST_DANMAKU, isDanmakuVisible)
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

        fun setupPlayer() {
            val item = currentItem ?: return
            if (item.videoUrl.isEmpty()) {
                CenterThreadPool.run {
                    val success = ShortVideoFeedApi.fetchVideoUrl(item)
                    mainHandler.post {
                        if (success && item.videoUrl.isNotEmpty()) {
                            initPlayer(item)
                        } else {
                            lastBufferingVisible = false
                            bufferingIndicator.visibility = View.GONE
                            // 这一页根本没起来：isPlaying 是「保持亮屏」的判据，
                            // 留着上一次的 true 会让窗口挂着 KEEP_SCREEN_ON 却什么都没有。
                            isPlaying = false
                            MsgUtil.showMsg(UiText.of("加载视频失败"))
                        }
                    }
                }
            } else {
                initPlayer(item)
            }
        }

        private fun initPlayer(item: ShortVideoItem) {
            if (playerReady) return

            // 与 lastBufferingVisible 同步，避免状态收集因“值相同”而不再纠正可见性
            lastBufferingVisible = true
            bufferingIndicator.visibility = View.VISIBLE

            try {
                // 原生库不可用时 createPlayer 返回 false 而不是抛异常（它抛的是
                // UnsatisfiedLinkError，属于 Error，下面这个 catch (e: Exception) 兜不住）：
                // 这里必须提前退出，否则后面 textureView / danmakuManager / setDataSource
                // 全是对着空播放器操作。
                if (!playerBridge.createPlayer(activity, shortVideoOptions)) {
                    lastBufferingVisible = false
                    bufferingIndicator.visibility = View.GONE
                    isPlaying = false
                    MsgUtil.showMsgLong(activity.getString(R.string.msg_ijk_native_load_failed))
                    return
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
                danmakuManager = DanmakuManager(danmakuView!!).also { manager ->
                    // 弹幕时间轴跟随播放器位置（而不是墙钟），见 DanmakuManager.bindMediaClock
                    // 与 DanmakuFlameMaster 的 MediaClockSync。两个 provider 跑在 DFM 渲染线程上，
                    // 只读主线程采样出来的内存值。
                    manager.bindMediaClock(
                        { if (isPrepared) videoNow.toLong() else -1L },
                        { isPrepared && isPlaying }
                    )
                }
                danmakuManager?.init()
                // 每条视频都会新建 DanmakuManager（默认可见），要把当前开关带过去；
                // prepare 前的 hide() 只记状态，真正隐藏由 DanmakuManager.prepared() 补上
                if (!isDanmakuVisible) danmakuManager?.hide()
                // 相邻页可能在用户切换开关之前就 bind 过，图标以开播这一刻为准
                buttonDanmaku.setImageResource(if (isDanmakuVisible) R.mipmap.danmakuon else R.mipmap.danmakuoff)
                // 弹幕 View 是 initPlayer 里才取到的，此时补一次圆屏留白（bind 时它还可能是 null）
                applyRoundInsets()

                playerBridge.setOnPrepared {
                    isPrepared = true
                    val st = playerBridge.state.value
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

                    // 尺寸从 state 现取（含旋转角）：prepare 时容器元数据没带分辨率的话，
                    // 这里会先按退化比例摆好，等 setOnVideoSizeChanged 补上尺寸再纠正。
                    applyVideoSizeFromState(st)
                    applySpeedToPlayers(currentSpeed)

                    // 只有当前页面活跃时才自动播放，防止弱网延迟准备导致两个视频同时播放
                    if (isActive) {
                        playerBridge.start()
                        isPlaying = true
                        buttonVideo.setImageResource(R.drawable.btn_player_pause)
                        // 不在这里 resume 弹幕：此刻 `loadDanmaku(item)` 还没跑，
                        // `DanmakuManager.resume()` 会因 isPrepared=false 直接返回（空操作）。
                        // 弹幕真正的起播由 `DanmakuManager.prepared()` 回调负责。
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
                    // 不再循环当前条：交给 Activity 决定「切下一条 / 相关推荐续接 / 停下」
                    // （旧实现是 seekTo(0) 无限循环同一条，与手机版行为不一致）
                    isPlaying = false
                    buttonVideo.setImageResource(R.drawable.btn_player_play)
                    playIcon.visibility = View.VISIBLE
                    danmakuManager?.pause()
                    activity.onVideoCompleted(boundPosition)
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
                isPlaying = false
                MsgUtil.showMsg(UiText.of("播放器初始化失败"))
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
                            // 弹幕时钟以 videoNow 为时间源（见上面的 bindMediaClock），
                            // 这里只需保证它是最新采样值，**不再做周期性 seekTo 吸附**
                            // —— 吸附会清空屏上弹幕，表现就是「弹幕闪一下又没了」。
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
                    // 尺寸晚到 / 中途变化（旋转信息后到、换分辨率、onPrepared 时为 0）：
                    // 把 state 当唯一数据源，真正变化时重摆画面。这是「画面被拉伸铺满整屏」
                    // 的兜底 —— 以前 state 里的尺寸更新没有任何消费者。
                    applyVideoSizeFromState(st)
                    // 真值兜底：isPlaying 是「保持亮屏」的判据，必须跟播放器实际状态对齐。
                    // 只靠各处显式赋值会漏（例如 start() 被调用但播放器已经自己停了）。
                    val realPlaying = playerBridge.isPlaying
                    if (realPlaying != isPlaying) isPlaying = realPlaying
                }
            }
        }

        /**
         * 按 `playerBridge.state` 里的视频尺寸重摆画面（幂等，可反复调用）。
         *
         * 以前只在 `onPrepared` 里读一次尺寸：容器元数据没带分辨率（`probesize` / `analyzeduration`
         * 压得很小、非 MP4 流）时 `videoWidth/videoHeight` 为 0，`adjustVideoSize()` 静默早退，
         * TextureView 就留在 `MATCH_PARENT` —— 用户看到的就是「画面被强行拉伸铺满整屏」。
         * 而 `IjkPlayerBridge` 的 `setOnVideoSizeChangedListener` 虽然会更新 state，
         * mediacodec 路径下却基本不会再补发尺寸，所以这里**必须**把 state 当唯一数据源，
         * 播放期间每轮采样都检查一次（真正变化才重摆）。
         *
         * 旋转 90/270 时交换宽高：容器给的是未旋转尺寸，解码器出的是旋转后的帧
         * （`mediacodec-auto-rotate=1` + `ffpipenode_android_mediacodec_vdec.c` 的
         * `frame_rotate_degrees` 分支），不交换同样表现为「比例被拉伸」。
         */
        private fun applyVideoSizeFromState(st: PlayerState) {
            val rotated = ShortVideoLayoutMath.swapsWidthHeight(st.rotationDegrees)
            val width = if (rotated) st.videoHeight else st.videoWidth
            val height = if (rotated) st.videoWidth else st.videoHeight
            val known = width > 0 && height > 0
            if (known) fallbackRatio = width.toFloat() / height.toFloat()

            if (known) {
                videoWidth = width
                videoHeight = height
                if (width == appliedVideoWidth && height == appliedVideoHeight) return
                appliedVideoWidth = width
                appliedVideoHeight = height
                Logu.v(
                    "ShortVideo",
                    "画面尺寸: ${width}x$height（旋转 ${st.rotationDegrees}°，" +
                        "区域 ${itemView.width}x${itemView.height}）"
                )
            } else {
                // 尺寸确实未知：先用退化比例占位，别把画面拉满整屏；
                // 尺寸晚到时下一轮采样会再进来纠正。
                videoWidth = 0
                videoHeight = 0
                appliedVideoWidth = 0
                appliedVideoHeight = 0
            }
            adjustVideoSize()
        }

        /**
         * 按当前 [videoWidth]/[videoHeight]（未知时按 [fallbackRatio]）在可用区域内等比居中。
         *
         * 可用区域优先取 `itemView` 实测尺寸（含界面边距/系统栏），还没量出来就用
         * BaseActivity 算好的内容区尺寸；不能用整屏尺寸，否则开了界面边距/圆屏后画面会被裁。
         */
        private fun adjustVideoSize() {
            val texture = textureView ?: return
            val params = texture.layoutParams as? FrameLayout.LayoutParams ?: return

            val areaW = if (itemView.width > 0) itemView.width else activity.contentWidth
            val areaH = if (itemView.height > 0) itemView.height else activity.contentHeight
            if (areaW <= 0 || areaH <= 0) return

            val ratio =
                if (videoWidth > 0 && videoHeight > 0) videoWidth.toFloat() / videoHeight.toFloat()
                else fallbackRatio

            // 尺寸计算是纯逻辑（`ShortVideoLayoutMath`，有 JVM 单测）：这里只负责取区域、
            // 写 layoutParams 并居中。圆屏直径取屏幕短边——圆屏手表是方形屏，短边即直径；
            // 非方形的手机/模拟器上取长边（例如 1440x2560 的 2560）会算出比屏幕还大的内接矩形。
            val size =
                if (activity.roundAdapted) {
                    ShortVideoLayoutMath.fitRound(
                        ratio, minOf(activity.screenWidth, activity.screenHeight), areaW, areaH
                    )
                } else {
                    ShortVideoLayoutMath.fit(ratio, areaW, areaH)
                } ?: return

            val width = size[0]
            val height = size[1]
            if (params.width == width && params.height == height) return

            params.width = width
            params.height = height
            params.gravity = Gravity.CENTER
            texture.layoutParams = params
            // 新的 layoutParams 要等下一帧布局才生效；缩放矩阵的 pivot 取 view 宽高，
            // 必须等布局完成后再重算，否则处于双指缩放状态时重摆会把画面推出中心。
            texture.post { applyScale() }
        }

        /**
         * 重算这一页顶栏的双击伪装手势：开着就装，关掉就摘。
         *
         * 短视频页的顶栏在 RecyclerView 的 item 里，`BaseActivity` 的 `findViewById(R.id.top)`
         * 找不到它，所以这一页必须自己接。`DisguiseGesture.install` 内部会判开关，
         * 但**已装上的触摸层不会自动消失**，所以关闭时还要显式 `detach`——
         * 否则单击顶栏会一直多等一个双击判定窗口。
         */
        fun syncDisguiseGesture() {
            if (DisguisePrefs.isEnabled()) DisguiseGesture.install(activity, top)
            else DisguiseGesture.detach(top)
        }

        fun play() {
            // 还没 prepare 时 `start()` 是空操作，但以前这里仍把 isPlaying 标成 true：
            // 它是「保持亮屏」的判据，于是取流 / prepare 失败或还没完成时窗口会一直挂着
            // KEEP_SCREEN_ON。真正起播交给 onPrepared 的 isActive 分支。
            if (!isPrepared) {
                isPlaying = false
                return
            }
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
                // 已经播到结尾（自动接续失败停住的情况）：先回到开头，否则 start() 会立刻再次触发完成
                if (videoAll > 0 && videoNow >= videoAll - 250) {
                    playerBridge.seekTo(0)
                    danmakuManager?.seekTo(0)
                }
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
            // 尺寸随播放器一起作废：holder 会被复用，留着上一条的尺寸会让下一次
            // updateVideoSize() 用错比例（去重字段也要一起清）。
            videoWidth = 0
            videoHeight = 0
            appliedVideoWidth = 0
            appliedVideoHeight = 0
            fallbackRatio = ShortVideoLayoutMath.DEFAULT_RATIO
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

        /** 可用区域尺寸变了：从 state 现取尺寸重摆一次（`onConfigurationChanged` 会转过来）。 */
        fun updateVideoSize() {
            applyVideoSizeFromState(playerBridge.state.value)
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
            // 原有一条 "max_cached_duration" = 3000 已删：实测本工程两个 .so（libijkplayer /
            // libijkffmpeg，arm64-v8a）里都找不到这个选项名，设了也不会生效，留着只会误导后来人
            // （依据见 docs/archive/superpowers/specs/2026-10-11-player-network-resilience.md 的 §2.4）。
            // 也不再设 infbuf=1：它是给"实时流"准备的（ff_ffplay_options.h:79-80），会短路
            // max-buffer-size 与 min-frames 两道闸门、让缓存无上限（同文档 §1 第 5 条）。短视频是
            // 点播语义，保留上面的 8MB 上限，宁可多等一点也不要内存随播放时长上涨。
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", NetWorkUtil.USER_AGENT_WEB),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48)
        )
    }
}
