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
import com.RobinNotBad.BiliClient.ui.shortvideo.ShortVideoNewUi
import com.RobinNotBad.BiliClient.model.ShortVideoItem
import com.RobinNotBad.BiliClient.ui.widget.HighEnergyProgressBar
import com.RobinNotBad.BiliClient.ui.widget.SwipePageLayout
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.DanmakuFileFetcher
import com.RobinNotBad.BiliClient.util.DisguiseGesture
import com.RobinNotBad.BiliClient.util.DisguisePrefs
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.player.DanmakuManager
import com.RobinNotBad.BiliClient.player.PlayerDefaults
import com.RobinNotBad.BiliClient.player.IjkOption
import com.RobinNotBad.BiliClient.player.IjkPlayerBridge
import com.RobinNotBad.BiliClient.player.PlayerState
import com.RobinNotBad.BiliClient.player.PlayerVolumePolicy
import com.RobinNotBad.BiliClient.player.ShortVideoDanmakuPolicy
import com.RobinNotBad.BiliClient.player.ShortVideoSurfaceAction
import com.RobinNotBad.BiliClient.player.ShortVideoSurfaceRebindPolicy
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
        if (closeCurrentNewPanel()) return
        // 在右侧详情/评论/相关推荐页时，返回先回到短视频首页，而不是直接退出本页
        if (::pager.isInitialized && pager.currentItem > 0) {
            pager.setCurrentItem(0, true)
            return
        }
        super.onBackPressed()
    }

    /** 外壳当前页号（0 = 信息流，1~3 = 详情/评论/相关推荐）；页面还没建好时按 0。 */
    internal fun currentShellPage(): Int = if (::pager.isInitialized) pager.currentItem else 0

    /** 新版面板优先消费返回键；老版不挂控制器，行为完全不变。 */
    internal fun closeCurrentNewPanel(): Boolean {
        if (currentShellPage() != 0) return false
        return (pager.adapter as? ShortVideoShellAdapter)?.activeFeedFragment?.closeCurrentPanel() == true
    }

    /** 新版表冠换条：仅信息流页生效，复用竖向 pager 的既有翻页链路；边界返回 false。 */
    internal fun switchCurrentVideo(offset: Int): Boolean {
        if (!::pager.isInitialized || currentShellPage() != 0) return false
        return (pager.adapter as? ShortVideoShellAdapter)?.activeFeedFragment?.switchVideo(offset) == true
    }

    /** 「只看 TA」进入：仅信息流页 + 新版 UI（老布局在 Fragment 里被 activeNewUi 闸住）。 */
    internal fun enterAuthorMode(startItem: ShortVideoItem?) {
        if (!::pager.isInitialized || currentShellPage() != 0) return
        (pager.adapter as? ShortVideoShellAdapter)?.activeFeedFragment?.enterAuthorMode(startItem)
    }

    /** 「只看 TA」退出：回推荐流记住位置的下一条。 */
    internal fun exitAuthorMode() {
        if (!::pager.isInitialized || currentShellPage() != 0) return
        (pager.adapter as? ShortVideoShellAdapter)?.activeFeedFragment?.exitAuthorMode()
    }

    /** 当前是否「只看 TA」；新版 UI 据此把 UP 行渲染成 chip。 */
    internal fun isAuthorModeActive(): Boolean {
        if (!::pager.isInitialized) return false
        return (pager.adapter as? ShortVideoShellAdapter)?.activeFeedFragment?.isAuthorMode() == true
    }

    /**
     * 表冠换条只在「新版 UI 激活」时接管，且抢在视图树分发之前——编码器事件沿焦点链下钻，
     * 挂在覆盖层上未必收得到；DecorView 兜底又只会滚容器不会换条。老布局 / 非信息流页 /
     * 用户关闭表冠时一律走 super，原分发链逐字节不变。
     */
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (::pager.isInitialized && currentShellPage() == 0) {
            val ui = (pager.adapter as? ShortVideoShellAdapter)?.activeFeedFragment?.activeNewUi()
            if (ui != null && ui.onRotaryEvent(ev)) return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

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
    private val feedSource: ShortVideoFeedSource,
    private val audioManager: AudioManager
) : androidx.recyclerview.widget.RecyclerView.Adapter<ShortVideoPagerAdapter.PageHolder>() {

    private val holders = mutableMapOf<Int, PageHolder>()
    private var activeHolder: PageHolder? = null

    var lastVisiblePosition = -1

    /** 首帧回调：信息流据此收加载层，不依赖列表回调是否准时到达。 */
    var onFirstFrame: (() -> Unit)? = null

    // 「只看 TA」续播：换 adapter 会重建播放器，进入时记下旧播放器的毫秒进度，
    // 对应页 onPrepared 后一次性 seek 回去（消费即清，不影响后续正常播放）
    private var initialSeekPosition = -1
    private var initialSeekMs = 0L

    fun requestInitialSeek(position: Int, ms: Long) {
        initialSeekPosition = position
        initialSeekMs = ms
    }

    private fun consumeInitialSeek(position: Int): Long {
        if (position != initialSeekPosition || initialSeekMs <= 0) return 0L
        val ms = initialSeekMs
        initialSeekPosition = -1
        initialSeekMs = 0L
        return ms
    }

    /** 当前活跃页的播放进度（毫秒）；进入「只看 TA」续播时读取。 */
    fun currentPositionMs(): Long = activeHolder?.positionMs() ?: 0L

    fun closeCurrentPanel(): Boolean = holders[lastVisiblePosition]?.newUi?.closePanel() == true

    /** 当前活跃页的新版 UI；老布局返回 null，表冠换条据此不生效。 */
    fun activeNewUi(): ShortVideoNewUi? = holders[lastVisiblePosition]?.newUi

    override fun getItemCount(): Int = feedSource.getItemCount()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(if (SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_UI_NEW, false)) R.layout.fragment_short_video_page_new else R.layout.fragment_short_video_page, parent, false)
        return PageHolder(view, audioManager, activity)
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        val item = feedSource.getItem(position) ?: return
        holder.boundPosition = position
        holder.bind(item, position)
        holders[position] = holder
        // 顶栏在分页 item 里（不在 Activity 的视图树上），BaseActivity 那边装不到。
        // 每次绑定时重算一次：用户可能在设置里把开关关掉了，已装上的触摸层不会自己消失。
        if (!holder.isNewUi()) holder.syncDisguiseGesture()
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
        // 反馈 #191：开关打开时音量键改调系统媒体音量，这个 AudioManager 又要用上了。
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
        // 下面这 8 个控件**只有老版布局**有；新版布局（fragment_short_video_page_new）
        // 用 shortNew* 那一套 id，取不到。写成非空类型会在 inflate 时直接抛
        // `findViewById(...) must not be null`（用户反馈「短视频播放器依旧报错」）。
        // 一律改成可空，调用点都在 newUi == null 分支里，安全调用即可。
        private val bottomControl: View? = itemView.findViewById(R.id.bottom_control)
        private val videoProgress: SeekBar? = itemView.findViewById(R.id.videoprogress)
        private val textProgress: TextView? = itemView.findViewById(R.id.text_progress)
        private val buttonVideo: ImageButton? = itemView.findViewById(R.id.button_video)
        private val buttonSoundCut: ImageButton? = itemView.findViewById(R.id.button_sound_cut)
        private val buttonSoundAdd: ImageButton? = itemView.findViewById(R.id.button_sound_add)
        private val buttonSpeed: TextView? = itemView.findViewById(R.id.button_speed)
        private val buttonDanmaku: ImageButton? = itemView.findViewById(R.id.button_danmaku)
        private val showSound: TextView? = itemView.findViewById(R.id.showsound)
        private val speedBoostHint: TextView? = itemView.findViewById(R.id.text_speed_boost)
        internal var newUi: ShortVideoNewUi? = null

        // 圆屏适配前的原始 padding：圆屏下要在布局原有留白基础上再收进来，
        // 关掉开关时按这份快照还原（[applyRoundInsets] 可重复调用）
        private val baseTopPadding =
            intArrayOf(top.paddingLeft, top.paddingTop, top.paddingRight, top.paddingBottom)
        private val baseProgressPadding = intArrayOf(
            videoProgress?.paddingLeft ?: 0, videoProgress?.paddingTop ?: 0,
            videoProgress?.paddingRight ?: 0, videoProgress?.paddingBottom ?: 0
        )
        private val baseProgressTextPadding = intArrayOf(
            textProgress?.paddingLeft ?: 0, textProgress?.paddingTop ?: 0,
            textProgress?.paddingRight ?: 0, textProgress?.paddingBottom ?: 0
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
                buttonVideo?.setImageResource(R.drawable.btn_player_play)
                newUi?.syncPlayback()
            }
            MsgUtil.showMsg(UiText.of("播放错误"))
        })
        private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private var stateJob: Job? = null
        private var playerReady = false
        private var textureView: TextureView? = null

        /**
         * 当前 [textureView] 的 SurfaceTexture 包出来的 Surface。
         *
         * 同一个 SurfaceTexture 只建一个：`Surface(st)` 是 native 句柄的包装，对象被 GC 不会
         * 归还 native 资源，每次 surface 回调都新建一个就会持续泄漏（`PlayerSurfaceBinder`
         * 的 `obtainTextureSurface` 记的是同一个坑）。换纹理或解绑时 release。
         */
        private var textureSurface: Surface? = null
        private var textureSurfaceToken: SurfaceTexture? = null

        /**
         * 这个 holder 的 surface 丢了还没补上过一帧。
         *
         * 页面滑走时 TextureView 会摘掉 surface（[TextureView.SurfaceTextureListener.onSurfaceTextureDestroyed]
         * → `setSurface(null)`），滑回来是一个新的 SurfaceTexture。此时播放器往往已经是
         * prepared 的**暂停**状态：mediacodec 拿到新 surface 不会主动重绘上一帧，暂停中也不再
         * 产新帧，于是整页停在纯黑（封面在 prepare 时已隐藏，页底是黑色）——反馈 #167
         * 「回滑之后视频就黑了」。这个标记就是「该页需要重新出帧」的判据。
         */
        private var needsRepaint = false
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
        internal var isPlaying = false
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

        /** 双指拖动后的画面位移（px）。只有放大到 1 倍以上才有意义（反馈 #129）。 */
        private var translateX = 0f
        private var translateY = 0f

        /** 双指拖动时上一帧的手指重心；[panning] 为 false 表示当前没在拖。 */
        private var lastFocusX = 0f
        private var lastFocusY = 0f
        private var panning = false

        var isInitialized = false
            private set

        /** 当前绑定的 position，NO_POSITION 表示未绑定或被回收 */
        var boundPosition = androidx.recyclerview.widget.RecyclerView.NO_POSITION

        private val mainHandler = Handler(Looper.getMainLooper())
        private var hideBottomRunnable: Runnable? = null
        private var hideVolumeRunnable: Runnable? = null

        /**
         * 「无极调音」的软件增益百分比（0–100），与长视频共用同一个存档键。
         *
         * 短视频原先调的是**系统媒体音量**（`setStreamVolume` ±1 档），手表上只有很少几档，
         * 20% 一下跳到 33% 调不出细档；这里改走 ijk 的采样级增益，1% 一档，且与长视频手感一致。
         */
        private var volumeGainPercent: Int = PlayerVolumePolicy.sanitize(
            SharedPreferencesUtil.getInt(
                PlayerVolumePolicy.KEY_GAIN_PERCENT,
                PlayerVolumePolicy.DEFAULT_PERCENT
            )
        )

        private var gestureDetector: GestureDetector? = null
        private var scaleGestureDetector: ScaleGestureDetector? = null

        fun bind(item: ShortVideoItem, position: Int) {
            // bind 只保留与条目数据相关的内容：监听器、手势检测器已在 init 里随 holder 只建一次
            // holder 被复用到另一条视频时，把上一条的缩放 / 位移一起还原（反馈 #129）
            if (currentItem !== item) resetScale()
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
                        .apply(GlideUtil.thumbOptions(coverImage))
                        .into(coverImage)
                }
            }

            textTitle.text = item.title

            isInitialized = true

            // 复用 holder 的 newUi 可能已被 releasePlayer 置 null：按布局重建，别落回老链路
            if (newUi == null) newUi = createNewUi()
            if (newUi == null) applyRoundInsets()
            newUi?.bind(item)

            if (newUi == null) {
                bottomControl?.visibility = if (activity.isBottomControlVisible) View.VISIBLE else View.GONE
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
                videoProgress?.setPadding(
                    padding * 4, baseProgressPadding[1], padding * 4, baseProgressPadding[3]
                )
                textProgress?.setPadding(
                    padding * 3, baseProgressTextPadding[1],
                    baseProgressTextPadding[2], baseProgressTextPadding[3]
                )
            } else {
                top.setPadding(baseTopPadding[0], baseTopPadding[1], baseTopPadding[2], baseTopPadding[3])
                videoProgress?.setPadding(
                    baseProgressPadding[0], baseProgressPadding[1],
                    baseProgressPadding[2], baseProgressPadding[3]
                )
                textProgress?.setPadding(
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
                    if (newUi != null) newUi?.onPageTap(e.x, e.y) else toggleBottomControl()
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

            newUi = createNewUi()

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
                    // 缩回原始比例时位移一起归零，否则画面会偏在一角；否则再按边界收一次，
                    // 免得松手时停在「画面被拖出边缘」的位置上（反馈 #129）
                    if (currentScale <= minScale) {
                        translateX = 0f
                        translateY = 0f
                    }
                    applyScale()
                    itemView.parent?.requestDisallowInterceptTouchEvent(false)
                }
            })

            itemView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    isScaling = false
                }

                scaleGestureDetector?.onTouchEvent(event)
                gestureDetector?.onTouchEvent(event)
                // 双指拖动平移（反馈 #129）：放大后用两根手指搓动画面看边角。
                // 只认 ≥2 根手指，单指上下滑照旧交给竖向 pager 翻视频，两者不抢手势。
                handleTwoFingerPan(event)
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

            if (newUi == null) top.setOnClickListener {
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
            if (newUi == null) syncDisguiseGesture()

            // 可用区域尺寸变化后重摆画面：`adjustVideoSize()` 在区域还是 0 时会早退，
            // 而 prepare 往往早于本页完成布局（尤其是慢机型），只在 onPrepared 算一次会
            // 让 TextureView 留在 MATCH_PARENT（满屏拉伸）。
            itemView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                    adjustVideoSize()
                    // 画面尺寸变了，缩放/位移的可拖范围跟着变，按新尺寸重新夹一次（反馈 #129）
                    applyScale()
                }
            }

            if (newUi == null) initBottomButtons()
            // 中心播放键属于画面层，不属于底栏按钮组：新版布局下 initBottomButtons() 不调用，
            // 少了这一句暂停后就再也点不回来（点击会穿透到 itemView 被当成「切常驻层」）。
            bindCenterPlayButton()
        }

        /**
         * 新版 UI 门面：布局里有 shortNewControls 才建，老布局返回 null（老链路一字不动）。
         *
         * 回收/释放会把 newUi 置 null（[releasePlayer]），holder 复用到新一条时必须重建，
         * 否则复用页的手势会落回老底栏链路（新布局里那些按钮不存在）——表现为
         * 「单击没反应、静置不沉浸」。重建前 [ShortVideoNewUi.release] 已摘掉它插的画面层子 View。
         */
        private fun createNewUi(): ShortVideoNewUi? {
            if (itemView.findViewById<View>(R.id.shortNewControls) == null) return null
            return ShortVideoNewUi(itemView, activity, object : ShortVideoNewUi.Host {
                override fun isPlaying(): Boolean = this@PageHolder.isPlaying
                override fun pause() { this@PageHolder.pause() }
                override fun resume() { this@PageHolder.resume() }
                override fun togglePlayPause() { this@PageHolder.togglePlayPause() }
                override fun seekTo(positionMs: Long) {
                    playerBridge.seekTo(positionMs)
                    danmakuManager?.seekTo(positionMs)
                }
                override fun cycleSpeed() { this@PageHolder.cycleSpeed() }
                override fun setSpeed(speed: Float) {
                    currentSpeed = speed
                    applySpeedToPlayers(speed)
                    currentSpeedIndex = speedOptions.indexOf(speed).coerceAtLeast(0)
                }
                override fun toggleDanmaku(): Boolean {
                    this@PageHolder.toggleDanmaku()
                    return activity.isDanmakuVisible
                }
                override fun changeVolume(add: Boolean) { this@PageHolder.changeVolume(add) }
                override fun currentSpeed(): Float = this@PageHolder.currentSpeed
                override fun currentVolumePercent(): Int = this@PageHolder.volumePercent()
                override fun changeQuality(quality: Int) {
                    val video = currentItem ?: return
                    CenterThreadPool.run {
                        try {
                            video.videoUrl = ""
                            video.currentQuality = quality
                            // 走数据源：推荐流等价于原 fetchVideoUrl；作者流会补 cid 并返回富化副本
                            val playable = feedSource.resolvePlayable(video, quality)
                            mainHandler.post {
                                if (currentItem?.aid == video.aid) {
                                    playerBridge.release()
                                    playerReady = false
                                    isPrepared = false
                                    if (playable != null && playable.videoUrl.isNotEmpty()) {
                                        if (playable !== video) currentItem = playable
                                        initPlayer(playable)
                                    } else {
                                        MsgUtil.showMsg(activity.getString(R.string.uitext_shortvideoplayeractivity_002))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            MsgUtil.err(UiText.of("切换清晰度："), e)
                        }
                    }
                }
                override fun switchVideo(offset: Int): Boolean = activity.switchCurrentVideo(offset)
                override fun enterAuthorMode(startItem: ShortVideoItem?) {
                    activity.enterAuthorMode(startItem)
                }
                override fun exitAuthorMode() { activity.exitAuthorMode() }
                override fun isAuthorMode(): Boolean = activity.isAuthorModeActive()
            })
        }

        /**
         * 中心播放键的点击。
         *
         * 只给这个 56dp 图标装监听：页面级 GestureDetector 那条链（单击切底栏、
         * 双击播放暂停、长按三倍速、双指缩放）一律不动。
         */
        private fun bindCenterPlayButton() {
            // 三角只在暂停/播完时可见，此时点它就是「继续播」；[resume] 里已经处理了
            // 「播到结尾」的情形（先 seekTo(0) 再 start），所以直接复用 togglePlayPause。
            playIcon.setOnClickListener {
                togglePlayPause()
            }
        }

        private fun initBottomButtons() {
            buttonVideo?.setOnClickListener {
                controlVideo()
            }

            buttonDanmaku?.setImageResource(if (isDanmakuVisible) R.mipmap.danmakuon else R.mipmap.danmakuoff)
            buttonDanmaku?.setOnClickListener {
                toggleDanmaku()
            }

            buttonSoundAdd?.setOnClickListener {
                changeVolume(true)
            }

            buttonSoundCut?.setOnClickListener {
                changeVolume(false)
            }

            buttonSpeed?.setOnClickListener {
                cycleSpeed()
            }

            videoProgress?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, position: Int, fromUser: Boolean) {
                    if (fromUser) {
                        textProgress?.text = StringUtil.toTime(position / 1000) + "/" + progressStr
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

        internal fun cycleSpeed() {
            currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
            currentSpeed = speedOptions[currentSpeedIndex]
            buttonSpeed?.text = "${currentSpeed}x"

            applySpeedToPlayers(currentSpeed)
        }

        /**
         * 同时把倍速交给播放器与弹幕。
         *
         * **弹幕必须一起设**：`MediaClockSync` 用 `DrawHandler.mSpeed` 做时间外推与步进放大
         * （见 `docs/architecture-map.md` §7.51）。只设播放器的话，2x 下媒体时间每秒推进 2000ms
         * 而弹幕时钟按 1x 追，越播越落后；3x 更是完全追不上。
         */
        internal fun applySpeedToPlayers(speed: Float) {
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
            buttonSpeed?.text = "${boostSpeed}x"

            hideVolumeRunnable?.let { mainHandler.removeCallbacks(it) }
            speedBoostHint?.text = activity.getString(R.string.text_shortvideoplayer_speed_boost)
            speedBoostHint?.visibility = View.VISIBLE
        }

        private fun endSpeedBoost() {
            if (!speedBoosted) return
            speedBoosted = false
            applySpeedToPlayers(currentSpeed)
            buttonSpeed?.text = "${currentSpeed}x"
            speedBoostHint?.visibility = View.GONE
        }

        /**
         * 切换弹幕显示/隐藏
         */
        internal fun toggleDanmaku() {
            isDanmakuVisible = !isDanmakuVisible
            if (isDanmakuVisible) {
                danmakuManager?.show()
                buttonDanmaku?.setImageResource(R.mipmap.danmakuon)
            } else {
                danmakuManager?.hide()
                buttonDanmaku?.setImageResource(R.mipmap.danmakuoff)
            }
            // 「播放默认值 → 弹幕 = 沿用上次」的记录位，与长视频共用
            SharedPreferencesUtil.putBoolean(SettingsKeys.PLAYER_LAST_DANMAKU, isDanmakuVisible)
        }

        internal fun togglePlayPause() {
            if (playerBridge.isPlaying) {
                pause()
            } else {
                resume()
            }
        }

        internal fun controlVideo() {
            if (isPlaying) {
                pause()
            } else {
                if (videoNow >= videoAll - 250) {
                    playerBridge.seekTo(0)
                }
                resume()
            }
        }

        /**
         * 音量键/按钮。
         *
         * 默认（反馈 #191 的开关关着）调「无极调音」的软件增益（0–100%），不动系统媒体音量：
         * 0% 是真静音（采样乘 0），再按 + 从 0 恢复；静音/恢复语义与长视频播放器完全一致。
         * 手表系统媒体音量只有很少几档，软件增益是 1% 一档，才调得出 30% 这种档位。
         *
         * 开关打开后改成调手表的系统媒体音量——这才是 #191 要的：在本 App 里调完，退出后
         * 别的应用也一起变。每次按下都现读开关，改完即生效，不用重进页面。
         */
        internal fun changeVolume(addOrCut: Boolean) {
            if (systemVolumeEnabled()) {
                changeSystemVolume(addOrCut)
                return
            }
            val next = PlayerVolumePolicy.step(
                volumeGainPercent,
                if (addOrCut) 1 else -1,
                PlayerVolumePolicy.COARSE_STEP_PERCENT
            )
            applyVolumeGain(next)
            // 与长视频共用存档键：在短视频里调完，回到长视频播放器也是同一档
            SharedPreferencesUtil.putInt(PlayerVolumePolicy.KEY_GAIN_PERCENT, next)
            showVolumePercent(next)
        }

        /** 开关打开时走这条路：直接改系统媒体音量，一档就是系统的一档。 */
        private fun changeSystemVolume(addOrCut: Boolean) {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max <= 0) return
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val next = PlayerVolumePolicy.stepStream(current, max, if (addOrCut) 1 else -1)
            if (next != current) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
            }
            showVolumePercent(PlayerVolumePolicy.streamPercent(next, max))
        }

        /** 音量提示条：显示 3 秒后自动收起（两种模式共用）。 */
        private fun showVolumePercent(percent: Int) {
            showSound?.visibility = View.VISIBLE
            showSound?.text = activity.getString(R.string.player_port_volume_percent, percent)
            hideVolumeRunnable?.let { mainHandler.removeCallbacks(it) }
            hideVolumeRunnable = Runnable { showSound?.visibility = View.GONE }
            mainHandler.postDelayed(hideVolumeRunnable!!, 3000)
        }

        /** 反馈 #191 的开关（每次现读，改完即生效）。 */
        private fun systemVolumeEnabled(): Boolean =
            SharedPreferencesUtil.getBoolean(SettingsKeys.SHORTVIDEO_SYSTEM_VOLUME, false)

        /**
         * 当前音量百分比，给「更多」网格的底注显示。
         *
         * 两种音量模式下的「当前音量」不是同一个东西：系统音量模式读的是系统档位，
         * App 增益模式读的是存档的增益值。显示时按当前模式取，与按下音量键的实际效果一致。
         */
        internal fun volumePercent(): Int {
            if (systemVolumeEnabled()) {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (max <= 0) return 0
                return PlayerVolumePolicy.streamPercent(
                    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC), max
                )
            }
            return volumeGainPercent
        }

        /** 把 [percent] 打到当前播放器的软件增益上，并记到本页字段。 */
        private fun applyVolumeGain(percent: Int) {
            volumeGainPercent = PlayerVolumePolicy.clampPercent(percent)
            val gain = PlayerVolumePolicy.percentToGain(volumeGainPercent)
            playerBridge.setVolume(gain, gain)
        }

        /** 起播时把存档增益打到新播放器上（每个分页 holder 各自一份播放器）。 */
        private fun applyStoredVolumeGain() {
            if (systemVolumeEnabled()) {
                // 系统音量模式下 App 增益固定满格：否则「系统 100% × 上次在 App 里调到 40%」
                // 会变成双重衰减，用户把系统音量拉满还是小声。存档里的增益不动——切回
                // 「App 内增益」模式时照旧按用户上次填的值走。
                applyVolumeGain(PlayerVolumePolicy.MAX_PERCENT)
                return
            }
            volumeGainPercent = PlayerVolumePolicy.sanitize(
                SharedPreferencesUtil.getInt(
                    PlayerVolumePolicy.KEY_GAIN_PERCENT,
                    PlayerVolumePolicy.DEFAULT_PERCENT
                )
            )
            applyVolumeGain(volumeGainPercent)
        }

        /**
         * 双指拖动平移（反馈 #129）。
         *
         * 直接把「两根手指重心」的位移累加到画面上：捏合改比例、这里改位移，两条路互不干扰。
         * 单指一律不参与——竖向 pager 的「上下滑切视频」必须保持原样，
         * 所以只在第二根手指按下的那一刻起接管，手指一少就立刻放手。
         */
        private fun handleTwoFingerPan(event: MotionEvent) {
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    lastFocusX = focusOf(event) { i -> event.getX(i) }
                    lastFocusY = focusOf(event) { i -> event.getY(i) }
                    // 先认下「两根手指」这件事；能不能真的平移看当前的缩放倍数，
                    // 缩放往往在按下之后才发生，所以这里不能只在捏合前放行
                    panning = true
                }

                MotionEvent.ACTION_MOVE -> {
                    val fx = focusOf(event) { i -> event.getX(i) }
                    val fy = focusOf(event) { i -> event.getY(i) }
                    if (panning && event.pointerCount >= 2 && currentScale > minScale) {
                        // 先抢在 pager 之前吃掉这次拖动，否则画面没动、视频先翻页了
                        itemView.parent?.requestDisallowInterceptTouchEvent(true)
                        translateX += fx - lastFocusX
                        translateY += fy - lastFocusY
                        applyScale()
                    }
                    lastFocusX = fx
                    lastFocusY = fy
                }

                // 少一根手指就不再平移：剩下的那根要还给「上下滑切视频」
                MotionEvent.ACTION_POINTER_UP -> panning = false
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> panning = false
            }
        }

        private inline fun focusOf(event: MotionEvent, axis: (Int) -> Float): Float {
            var sum = 0f
            for (i in 0 until event.pointerCount) sum += axis(i)
            return sum / event.pointerCount.coerceAtLeast(1)
        }

        /** 画面回到原始比例、居中（holder 复用换条目时调用）。 */
        private fun resetScale() {
            currentScale = minScale
            translateX = 0f
            translateY = 0f
            panning = false
            applyScale()
        }

        /**
         * 把缩放与位移写进 TextureView 的变换矩阵。
         *
         * 缩放绕画面中心做，位移是屏幕像素；放大后内容比可视区多出来的那一半
         * （[(currentScale] - 1) × 尺寸 / 2）就是能拖动的上限，越界直接夹住，
         * 保证拖到头时画面边缘不会离开表盘（反馈 #129）。
         */
        private fun applyScale() {
            textureView?.let { view ->
                val w = view.width.toFloat()
                val h = view.height.toFloat()
                if (w <= 0f || h <= 0f) return@let
                // coerceAtLeast 兜底：currentScale 理论上不小于 1，真缩到 1 以下时上限会是负数，
                // 而 coerceIn(min, max) 在 min > max 时会直接抛 IllegalArgumentException
                val maxTx = (currentScale - 1f).coerceAtLeast(0f) * w / 2f
                val maxTy = (currentScale - 1f).coerceAtLeast(0f) * h / 2f
                translateX = translateX.coerceIn(-maxTx, maxTx)
                translateY = translateY.coerceIn(-maxTy, maxTy)
                val matrix = Matrix()
                matrix.setScale(currentScale, currentScale, w / 2f, h / 2f)
                matrix.postTranslate(translateX, translateY)
                view.setTransform(matrix)
            }
        }

        fun updateTitle() {
            currentItem?.let { item ->
                textTitle.text = item.title
            }
        }

        private fun toggleBottomControl() {
            if (bottomControl?.visibility == View.VISIBLE) {
                hideBottomControl()
            } else {
                showBottomControl()
            }
        }

        private fun showBottomControl() {
            bottomControl?.visibility = View.VISIBLE
            activity.isBottomControlVisible = true

            hideBottomRunnable?.let { mainHandler.removeCallbacks(it) }
            hideBottomRunnable = Runnable {
                bottomControl?.visibility = View.GONE
                activity.isBottomControlVisible = false
            }
            mainHandler.postDelayed(hideBottomRunnable!!, 10000)
        }

        private fun hideBottomControl() {
            bottomControl?.visibility = View.GONE
            activity.isBottomControlVisible = false
            hideBottomRunnable?.let { mainHandler.removeCallbacks(it) }
            hideBottomRunnable = null
        }

        fun setupPlayer() {
            val item = currentItem ?: return
            if (item.videoUrl.isEmpty()) {
                CenterThreadPool.run {
                    // 走数据源：推荐流就是原来的 fetchVideoUrl（返回原实例）；
                    // 作者流条目没有 cid，先补 cid 再取流，可能返回富化副本
                    val playable = try {
                        feedSource.resolvePlayable(item)
                    } catch (e: Exception) {
                        Logu.e("ShortVideo", "取流异常 aid=${item.aid}: ${e.message}")
                        null
                    }
                    mainHandler.post {
                        if (playable != null && playable.videoUrl.isNotEmpty()) {
                            if (playable !== item && currentItem?.aid == item.aid) currentItem = playable
                            initPlayer(playable)
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

        /** 当前播放进度（毫秒）；「只看 TA」续播保存位置用。播放器没起来时返回 0。 */
        fun positionMs(): Long {
            if (!playerReady) return 0L
            return try {
                playerBridge.currentPosition
            } catch (e: Exception) {
                videoNow.toLong()
            }
        }

        private fun initPlayer(item: ShortVideoItem) {
            if (playerReady) return

            // 新建播放器 = 新的 surface 生命周期：上一条（或上一次挂载）丢过的 surface 不算数，
            // 否则第一次挂载就会被判成「丢过 surface」而多 seek 一次
            needsRepaint = false

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
                            bindTextureSurface(st)
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            // 先让播放器松手，再放掉我们自己包出来的 Surface
                            playerBridge.setSurface(null)
                            needsRepaint = true
                            releaseTextureSurface()
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
                // 短视频弹幕默认不跟长视频共用「15 行 + 允许重叠」，改用独立的一份保守档
                // （开关与取值都在 player/ShortVideoDanmakuPolicy；关掉开关这里就是 null = 照旧）
                danmakuManager?.init(ShortVideoDanmakuPolicy.overrideOrNull())
                // 每条视频都会新建 DanmakuManager（默认可见），要把当前开关带过去；
                // prepare 前的 hide() 只记状态，真正隐藏由 DanmakuManager.prepared() 补上
                if (!isDanmakuVisible) danmakuManager?.hide()
                // 相邻页可能在用户切换开关之前就 bind 过，图标以开播这一刻为准
                buttonDanmaku?.setImageResource(if (isDanmakuVisible) R.mipmap.danmakuon else R.mipmap.danmakuoff)
                // 弹幕 View 是 initPlayer 里才取到的，此时补一次圆屏留白（bind 时它还可能是 null）
                if (newUi == null) applyRoundInsets()

                playerBridge.setOnPrepared {
                    isPrepared = true
                    // prepare 完成时还没有 surface（页面还没上屏，或 surface 已被摘掉）：
                    // 等它真的挂上来时要强制出一帧，否则 mediacodec 没有输出目标，起来就是黑的
                    if (textureSurface == null) needsRepaint = true
                    val st = playerBridge.state.value
                    videoAll = st.duration.toInt()
                    progressStr = StringUtil.toTime(videoAll / 1000)
                    // 换了视频要复位进度去重标记，否则新视频的文本要等整秒变化才刷新
                    videoNowLast = -1
                    videoNowLastSec = -1
                    if (newUi == null) videoProgress?.max = videoAll

                    lastBufferingVisible = false
                    bufferingIndicator.visibility = View.GONE
                    coverImage.visibility = View.GONE
                    playIcon.visibility = View.GONE

                    // 尺寸从 state 现取（含旋转角）：prepare 时容器元数据没带分辨率的话，
                    // 这里会先按退化比例摆好，等 setOnVideoSizeChanged 补上尺寸再纠正。
                    applyVideoSizeFromState(st)
                    // 长按三倍速可能在本条 prepare 之前就按下了：这时直接下发 currentSpeed
                    // 会把正在生效的 boost 压回 1x，按钮还停在 3.0x（假倍速）。所以按
                    // speedBoosted 现状决定下发哪个值。
                    applySpeedToPlayers(if (speedBoosted) boostSpeed else currentSpeed)
                    // 无极调音：新播放器要套用存档增益（换页/回收重建都走这里）
                    applyStoredVolumeGain()

                    // 「只看 TA」续播：换源重建播放器后回到进入前的进度（一次性消费）。
                    // 弹幕不用单独 seek：它跟的是媒体时钟（videoNow），状态收集会采到 seek 后的位置
                    val initialSeek = consumeInitialSeek(boundPosition)
                    if (initialSeek > 0) {
                        try {
                            playerBridge.seekTo(initialSeek)
                        } catch (e: Exception) {
                            Logu.e("ShortVideo", "只看 TA 续播定位失败: ${e.message}")
                        }
                    }

                    // 只有当前页面活跃时才自动播放，防止弱网延迟准备导致两个视频同时播放
                    if (isActive) {
                        playerBridge.start()
                        isPlaying = true
                        buttonVideo?.setImageResource(R.drawable.btn_player_pause)
                        // 不在这里 resume 弹幕：此刻 `loadDanmaku(item)` 还没跑，
                        // `DanmakuManager.resume()` 会因 isPrepared=false 直接返回（空操作）。
                        // 弹幕真正的起播由 `DanmakuManager.prepared()` 回调负责。
                    } else {
                        // 页面不活跃，暂停准备就绪
                        isPlaying = false
                        buttonVideo?.setImageResource(R.drawable.btn_player_play)
                        playIcon.visibility = View.VISIBLE
                    }

                    startStateCollection()
                    // 出帧即通知信息流收加载层；播放态同步进新版状态机（否则中心键/静置计时都按旧态走）
                    onFirstFrame?.invoke()
                    newUi?.syncPlayback()
                    // 异步加载弹幕
                    loadDanmaku(item)
                }

                playerBridge.setOnCompletion {
                    // 不再循环当前条：交给 Activity 决定「切下一条 / 相关推荐续接 / 停下」
                    // （旧实现是 seekTo(0) 无限循环同一条，与手机版行为不一致）
                    isPlaying = false
                    buttonVideo?.setImageResource(R.drawable.btn_player_play)
                    playIcon.visibility = View.VISIBLE
                    newUi?.syncPlayback()
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
         * 把 [st] 包出来的 Surface 挂到播放器上，再按 [ShortVideoSurfaceRebindPolicy] 决定要不要
         * 「强制出帧 / 接着播」。
         *
         * 幂等：页面滑回来时「surface 回调」与「onPageSelected → setActive(true)」的先后顺序
         * 并不确定，两条路径最终都落到这里。重复调用不会重复建 Surface，也不会重复 seek。
         */
        private fun bindTextureSurface(st: SurfaceTexture) {
            if (textureSurfaceToken !== st) {
                // 换了纹理：先把旧包装从播放器上摘下来再释放，
                // 否则播放器可能还在持有一个已被 release 的 Surface
                playerBridge.setSurface(null)
                releaseTextureSurface()
                textureSurface = Surface(st)
                textureSurfaceToken = st
            }
            val surface = textureSurface ?: return
            playerBridge.setSurface(surface)

            val action = ShortVideoSurfaceRebindPolicy.decide(
                hasTexture = true,
                needsRepaint = needsRepaint,
                isPrepared = isPrepared,
                isActive = isActive,
                // 横滑到详情页时宿主不在第 0 页：这一页就算重新拿到 surface 也不许自己起播
                // （否则详情页背景音会响，破坏「滑去详情页暂停」这个既有行为）
                feedVisible = activity.currentShellPage() == 0
            )
            // 先落标记再执行动作：repaint 里的 play() 不能再走一次「补 surface」
            needsRepaint = false

            when (action) {
                ShortVideoSurfaceAction.ATTACH_REPAINT ->
                    repaintAfterSurfaceRebind(startAfter = false)

                ShortVideoSurfaceAction.ATTACH_REPAINT_AND_START ->
                    repaintAfterSurfaceRebind(startAfter = true)

                ShortVideoSurfaceAction.ATTACH_ONLY,
                ShortVideoSurfaceAction.NONE -> Unit
            }
        }

        /**
         * 幂等入口：确保「该往哪画」这件事当前是接好的。
         *
         * 由 [setActive] 在页面变活跃时调用。正常情况下 surface 回调已经处理过，这里直接返回；
         * 只有回调顺序被打乱（surface 就绪早于 / 晚于页面活跃）时才真去补一次，
         * 避免「播放器在播、画面却全黑、也没有任何回调能救回来」这种状态。
         */
        fun ensureSurfaceReady() {
            val st = textureView?.surfaceTexture ?: return
            val bound = textureSurface
            // 已经挂在同一个 SurfaceTexture 上且没丢过：不重复 setSurface（重复会触发一次 flush）
            if (bound != null && textureSurfaceToken === st && !needsRepaint) return
            bindTextureSurface(st)
        }

        /**
         * 换过 surface 之后强制出一帧。
         *
         * 只 `setSurface` 是不够的：mediacodec 的输出链路在换 surface 后不会主动重绘上一帧，
         * 而暂停中的播放器更是一个新帧都不解 —— 页面就停在纯黑（封面在 prepare 时已隐藏，
         * 页底是 `@android:color/black`）。长视频那边同样是重挂后 seek 回当前位置强制出画面
         * （`PlayerActivity` 的 `onSurfaceReattached`）。
         */
        private fun repaintAfterSurfaceRebind(startAfter: Boolean) {
            if (!isPrepared) return
            // 以播放器真实位置为准，拿不到时才退回最近一次采样值
            val target = playerBridge.currentPosition.takeIf { it > 0L } ?: videoNow.toLong()
            try {
                playerBridge.seekTo(target)
                danmakuManager?.seekTo(target)
            } catch (e: Exception) {
                Logu.e("ShortVideo", "重挂 surface 后强制出帧失败: ${e.message}")
            }
            if (startAfter) play()
        }

        /** 放掉当前 Surface 包装；SurfaceTexture 本身归 TextureView 管，这里不碰。 */
        private fun releaseTextureSurface() {
            try {
                textureSurface?.release()
            } catch (e: Exception) {
                Logu.e("ShortVideo", "释放 Surface 异常: ${e.message}")
            }
            textureSurface = null
            textureSurfaceToken = null
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
            // 统一走 DanmakuFileFetcher：非 2xx / 空正文直接抛 IOException，由上层 catch 记日志；
            // 旧实现不判状态码，404/412 的 HTML 错误页会被解压后喂给解析器（调研清单第 1 条）
            val decompressed = DanmakuFileFetcher.fetch(danmakuUrl, NetWorkUtil.webHeaders)
            val inputStream = java.io.ByteArrayInputStream(decompressed)
            // 必须回主线程：DanmakuManager.createParser 内部用的是进程级单例
            // BiliDanmakuLoader（dataSource 是它的实例字段），并发调用会互相覆盖；
            // 真正的 XML 解析是懒执行的，在 DanmakuView 自己的渲染线程上做，不在这里
            mainHandler.post {
                danmakuManager?.loadFromXmlInput(inputStream)
            }
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
                            if (newUi != null) newUi?.onProgress(pos, videoAll, isPlaying)
                            else videoProgress?.progress = pos
                            val sec = pos / 1000
                            if (sec != videoNowLastSec) {
                                videoNowLastSec = sec
                                if (newUi == null) textProgress?.text = StringUtil.toTime(sec) + "/" + progressStr
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
                    if (realPlaying != isPlaying) {
                        isPlaying = realPlaying
                        // 缓冲中的短暂停摆不算「暂停」：不据此弹中心键/控件层，等缓冲结束再对齐
                        if (!st.isBuffering) newUi?.syncPlayback()
                    }
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
        fun isNewUi(): Boolean = newUi != null

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
            // 起播前把画面通道再确认一次：回滑回来时 surface 可能刚被摘掉或还没挂上，
            // 直接 start() 只会播出声音、画面停在黑帧（反馈 #167）
            ensureSurfaceReady()
            playerBridge.start()
            isPlaying = true
            newUi?.syncPlayback()
            buttonVideo?.setImageResource(R.drawable.btn_player_pause)
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
            newUi?.syncPlayback()
            try {
                buttonVideo?.setImageResource(R.drawable.btn_player_play)
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
                // 手动点播放 / 回前台续播：surface 可能还没挂回来，先补好通道再 start，
                // 否则只有声音没有画面（反馈 #167）
                ensureSurfaceReady()
                // 已经播到结尾（自动接续失败停住的情况）：先回到开头，否则 start() 会立刻再次触发完成
                if (videoAll > 0 && videoNow >= videoAll - 250) {
                    playerBridge.seekTo(0)
                    danmakuManager?.seekTo(0)
                }
                playerBridge.start()
                isPlaying = true
                buttonVideo?.setImageResource(R.drawable.btn_player_pause)
                playIcon.visibility = View.GONE
                newUi?.syncPlayback()
                danmakuManager?.resume()
            }
        }

        /**
         * 设置页面活跃状态。当页面变为活跃时恢复播放，变为非活跃时暂停。
         */
        fun setActive(active: Boolean) {
            isActive = active
            if (active) {
                // 回滑回到这一页：它的 surface 可能已经随页面 detach 被摘掉，
                // 这里先把画面通道补好再决定起播，否则 start() 出的是黑帧（反馈 #167）
                ensureSurfaceReady()
                if (isPrepared && !isPlaying) {
                    playerBridge.start()
                    isPlaying = true
                    buttonVideo?.setImageResource(R.drawable.btn_player_pause)
                    playIcon.visibility = View.GONE
                    danmakuManager?.resume()
                }
            } else {
                if (isPlaying) {
                    playerBridge.pause()
                    isPlaying = false
                    buttonVideo?.setImageResource(R.drawable.btn_player_play)
                    playIcon.visibility = View.VISIBLE
                    danmakuManager?.pause()
                }
            }
            newUi?.syncPlayback()
        }

        fun isPlaying(): Boolean {
            return isPlaying
        }

        fun releasePlayer() {
            newUi?.release()
            newUi = null
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
            releaseTextureSurface()
            needsRepaint = false
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
            // 倍速必须开 soundtouch：ijk 的变速是靠 soundtouch 拉伸音轨、让音轨时钟加速，
            // 视频再跟着音轨时钟走；不开时 setSpeed 只改了 pf_playback_rate 这个属性，
            // 实际播放仍是 1x（长按三倍速失效、按钮却显示 3.0x 就是它）。stock ijkplayer
            // 里 `soundtouch` 默认 0（bilibili/ijkplayer `ff_ffplay_options.h`：
            // `{ "soundtouch", "SoundTouch: enable", OPTION_INT(0, 0, 1) }`），长视频
            // PlayerActivity 一直是显式设 1 的，短视频这份选项列表此前漏了。
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1),
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
