package com.RobinNotBad.BiliClient.activity.video

import android.media.AudioManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.model.ShortVideoItem
import com.RobinNotBad.BiliClient.ui.shortvideo.ShortVideoAuthorPolicy
import com.RobinNotBad.BiliClient.ui.shortvideo.ShortVideoLoadingPolicy
import com.RobinNotBad.BiliClient.ui.shortvideo.ShortVideoNewUi
import com.RobinNotBad.BiliClient.ui.widget.SwipePageLayout
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.UiText
import com.RobinNotBad.BiliClient.util.VideoPreloadManager

/**
 * 短视频信息流（外壳横向 pager 的第 0 页）。
 *
 * 从 `ShortVideoPlayerActivity` 里拆出来，是为了让短视频流能作为横向 pager 的一页，
 * 和右侧的「视频详情 / 评论 / 相关推荐」连成一条线（见 `activity_short_video_player.xml`）。
 *
 * 这一页自己管全部播放逻辑：取流与预加载（[VideoPreloadManager]）、竖向翻页、
 * 播放器生命周期、播完自动接下一条。外壳只负责横向翻页与「当前这条视频是谁」的同步，
 * 需要时读 [currentShortVideo]。
 *
 * 片段生命周期与播放的关系：横向滑到详情页时，`FragmentStateAdapter` 会把这一页
 * 降到 STARTED，[onPause] 里暂停播放；滑回来 [onResume] 再续播。因此这里不需要
 * 让外壳额外通知「现在在第几页」。
 */
class ShortVideoFeedFragment : Fragment() {

    private lateinit var viewPager: ViewPager2
    private lateinit var loadingLayout: View
    private lateinit var errorLayout: View
    private lateinit var loadingText: TextView
    private lateinit var errorText: TextView
    private lateinit var audioManager: AudioManager

    // 推荐流数据源：整个 Fragment 生命周期保持存活。「只看 TA」期间也不释放——
    // 推荐列表与 StoryFeedPager 的 display_id / track_id / seenAids 原地保留，退出不重拉第一页。
    private val recommendSource = RecommendFeedSource(VideoPreloadManager(preloadCount = 3))

    // 当前生效的数据源：推荐 / 作者。翻页逻辑只认接口，换源 = 换实现 + 换 adapter。
    private var feedSource: ShortVideoFeedSource = recommendSource

    // 进入「只看 TA」前记住的推荐流位置，退出时落到它的下一条
    private var authorSavedIndex = 0

    private var currentPageIndex = 0
    private var wasPlayingWhenPaused = false

    /** 「播完且列表已到底」时等相关推荐回来再切页；用户中途自己滑走的作废。 */
    private var pendingAdvanceAfterRelated = false

    /** 作者流播完最后一条、但下一页还在飞：等它回来再自动接续。 */
    private var pendingAdvanceAfterMore = false

    /** 加载层判据的两份输入：本次加载是否失败、是否已经出过帧（见 [ShortVideoLoadingPolicy]）。 */
    private var feedLoadFailed = false
    private var firstFrameOut = false

    private val host: ShortVideoPlayerActivity
        get() = requireActivity() as ShortVideoPlayerActivity

    /** 当前这条短视频；右侧详情/评论/相关推荐按它渲染。 */
    val currentShortVideo: ShortVideoItem?
        get() = feedSource.getItem(currentPageIndex)

    private val feedAdapter: ShortVideoPagerAdapter?
        get() = viewPager.adapter as? ShortVideoPagerAdapter

    /** 当前活跃那页是否真的在播放；宿主据此决定要不要保持亮屏。 */
    fun isCurrentPlaying(): Boolean = feedAdapter?.isCurrentPlaying() == true

    fun closeCurrentPanel(): Boolean = feedAdapter?.closeCurrentPanel() == true

    /** 当前活跃页的新版 UI；老布局返回 null，表冠换条据此不生效。 */
    fun activeNewUi(): ShortVideoNewUi? = feedAdapter?.activeNewUi()

    /** 新版表冠换条：±1 页，复用 onPageSelected 的暂停 / 备播链路；到边界返回 false。 */
    fun switchVideo(offset: Int): Boolean {
        if (!::viewPager.isInitialized) return false
        val next = currentPageIndex + offset
        if (next < 0 || next >= feedSource.getItemCount()) {
            // 「只看 TA」到底：提示一下，不自动回推荐流（设计稿口径）
            if (offset > 0 && isAuthorMode() && feedSource.isExhausted()) {
                MsgUtil.showMsg(getString(R.string.msg_sv_author_end))
            }
            return false
        }
        viewPager.setCurrentItem(next, true)
        return true
    }

    /** 当前是否处于「只看 TA」作者流。 */
    fun isAuthorMode(): Boolean = feedSource !== recommendSource

    /**
     * 进入「只看 TA」：原地换数据源，不开新页面、不动 ViewPager2 结构。
     *
     * @param startItem null 或就是当前这条 → 当前条钉在第 0 位、记住播放进度继续播；
     *                  作者卡缩略图点进来的某条 → 从那条开始。
     *
     * 只在**新版 UI** 生效（老布局 [activeNewUi] 为 null，直接返回，老链路逐字节不变）。
     */
    fun enterAuthorMode(startItem: ShortVideoItem?) {
        if (!::viewPager.isInitialized || !isAdded) return
        if (activeNewUi() == null) return
        if (isAuthorMode()) return
        val current = currentShortVideo ?: return
        if (current.authorMid <= 0) return
        val continuing = startItem == null || startItem.aid == current.aid
        authorSavedIndex = currentPageIndex
        // 续播进度：换 adapter 会重建播放器，用保存的毫秒数在 onPrepared 里 seek 回去
        val seekMs = if (continuing) feedAdapter?.currentPositionMs() ?: 0L else 0L
        val seed = if (continuing) current else startItem!!
        bindSource(AuthorFeedSource(current.authorMid, seed), 0, seekMs)
        MsgUtil.showMsg(
            getString(
                if (continuing) R.string.msg_sv_author_only_current else R.string.msg_sv_author_only_from,
                current.authorName
            )
        )
    }

    /** 退出「只看 TA」：回推荐流记住位置的下一条，推荐列表原地还在、不重拉。 */
    fun exitAuthorMode() {
        if (!::viewPager.isInitialized || !isAdded) return
        if (!isAuthorMode()) return
        val old = feedSource
        val target = ShortVideoAuthorPolicy.exitRestoreIndex(authorSavedIndex, recommendSource.getItemCount())
        bindSource(recommendSource, target, 0L)
        old.release()
        MsgUtil.showMsg(getString(R.string.msg_sv_author_exit))
    }

    /**
     * 换数据源并重建 adapter（整列表替换必须换新 adapter，不能 notify 旧实例）。
     * 推荐流期间 [recommendSource] 的列表与分页游标一直活着，这里只是重新挂上去。
     */
    private fun bindSource(source: ShortVideoFeedSource, initialIndex: Int, initialSeekMs: Long) {
        pendingAdvanceAfterRelated = false
        pendingAdvanceAfterMore = false
        feedSource = source
        attachSourceCallbacks(source)
        feedLoadFailed = false
        firstFrameOut = false
        renderLoadingOverlay()
        errorLayout.visibility = View.GONE
        val adapter = newFeedAdapter(source)
        if (initialSeekMs > 0) adapter.requestInitialSeek(initialIndex, initialSeekMs)
        viewPager.adapter = adapter
        currentPageIndex = initialIndex
        if (initialIndex > 0) viewPager.setCurrentItem(initialIndex, false)
        viewPager.post {
            if (!isAdded || feedSource !== source) return@post
            val a = feedAdapter ?: return@post
            // layout 期间 onPageSelected 可能已经跑过（备播已完成）；没跑到的在这里兜底
            if (a.lastVisiblePosition != initialIndex) {
                source.onSwipeToIndex(initialIndex)
                host.onFeedAnchorChanged()
                a.lastVisiblePosition = initialIndex
                a.setupPlayerAtPosition(initialIndex)
            }
        }
    }

    /**
     * 宿主的可用区域尺寸变了（旋转 / 界面边距 / 圆屏留白）：让各页按新尺寸重摆画面。
     *
     * 之前 `ShortVideoPagerAdapter.notifyScreenSizeChanged()` 全仓没有任何调用方，
     * 转屏后画面会停在旧尺寸；现在由 `ShortVideoPlayerActivity.onConfigurationChanged` 转过来。
     */
    fun onHostScreenSizeChanged() {
        feedAdapter?.notifyScreenSizeChanged()
    }

    /** 本次暂停是否由「外壳横滑」触发，避免把用户自己按的暂停又恢复回来。 */
    private var pausedByShellSwipe = false

    /**
     * 外壳 pager 判出横向、开始横滑时调用。
     *
     * 横滑期间本页与右侧详情页会同时绘制，视频继续解码/出帧会把每帧成本抬高；
     * 普通视频详情页翻页时没有视频在播，这里对齐该状态。
     */
    fun pauseForShellSwipe() {
        val adapter = feedAdapter ?: return
        if (adapter.isCurrentPlaying()) {
            pausedByShellSwipe = true
            adapter.pauseCurrent()
        }
    }

    /** 横滑结束仍停在第 0 页（或手势取消）时恢复；翻到详情页则交给生命周期暂停。 */
    fun resumeAfterShellSwipe() {
        if (!pausedByShellSwipe) return
        pausedByShellSwipe = false
        feedAdapter?.resumeCurrent()
        // resumeCurrent 在「本来就没暂停」时是空操作，这里补一次重算，
        // 避免出现「画面在播、窗口却没有 KEEP_SCREEN_ON」的中间态。
        host.syncKeepScreenOn()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = requireContext().getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_short_video_feed, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.viewPager)
        loadingLayout = view.findViewById(R.id.loadingLayout)
        errorLayout = view.findViewById(R.id.errorLayout)
        loadingText = view.findViewById(R.id.loadingText)
        errorText = view.findViewById(R.id.errorText)

        // 本页是外壳第 0 页：横向翻页交给外壳的经典 ViewPager 自己处理，
        // 这里根布局只负责「停在第 0 页时右滑退出本页」这一条边缘手势。
        (view as? SwipePageLayout)?.apply {
            edgeExitCheck = { host.currentShellPage() == 0 }
            onSwipe = { direction -> host.handleSwipe(direction) }
        }

        view.findViewById<TextView>(R.id.retryButton).setOnClickListener { loadFeed() }

        attachSourceCallbacks(recommendSource)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // 用户在等续接回来时自己滑走了：取消自动接续，免得稍后又被强制跳页
                if (pendingAdvanceAfterRelated && position != currentPageIndex) {
                    pendingAdvanceAfterRelated = false
                }
                if (pendingAdvanceAfterMore && position != currentPageIndex) {
                    pendingAdvanceAfterMore = false
                }
                currentPageIndex = position
                feedSource.onSwipeToIndex(position)
                host.onFeedAnchorChanged()

                val adapter = feedAdapter
                if (adapter != null) {
                    val prevPos = adapter.lastVisiblePosition
                    if (prevPos >= 0 && prevPos != position) {
                        adapter.pausePlayer(prevPos)
                    }
                    adapter.lastVisiblePosition = position
                    adapter.setupPlayerAtPosition(position)
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    feedAdapter?.playAtPosition(currentPageIndex)
                }
            }
        })

        // 作者流视图重建（横滑去详情页再回来）：列表还在就原样重挂，不重拉；
        // 推荐流保持旧行为走 loadFeed()
        if (isAuthorMode() && feedSource.getItemCount() > 0) {
            bindSource(feedSource, currentPageIndex.coerceIn(0, feedSource.getItemCount() - 1), 0L)
        } else {
            loadFeed()
        }
    }

    override fun onResume() {
        super.onResume()
        if (wasPlayingWhenPaused) {
            feedAdapter?.resumeCurrent()
        }
        // 回到前台无条件重算一次亮屏：`wasPlayingWhenPaused` 依赖 onPause 那一刻 activeHolder
        // 还在（被回收/换页时会取到 false），只靠它决定续播会出现「播放器其实在走、
        // 窗口却没有 KEEP_SCREEN_ON」的中间态，真机表现就是看着看着熄屏。
        host.syncKeepScreenOn()
    }

    override fun onPause() {
        super.onPause()
        feedAdapter?.let { adapter ->
            wasPlayingWhenPaused = adapter.isCurrentPlaying()
            adapter.pauseCurrent()
        }
    }

    override fun onDestroyView() {
        // 播放器是 native 资源，视图销毁必须释放；onDestroyView 之后不会再回到这一页，
        // 需要续播时会在新的视图里重新初始化
        feedAdapter?.releaseAll()
        viewPager.adapter = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        recommendSource.release()
        if (feedSource !== recommendSource) feedSource.release()
        super.onDestroy()
    }

    /**
     * 当前这条视频播完了（由 [ShortVideoPagerAdapter.PageHolder] 的完成回调转来）。
     *
     * 与 B 站手机版一致：**不再循环当前条**。
     * - 列表还有下一条 → 直接切过去；
     * - 已经到底 → 以刚播完的这条为锚点拉一批相关推荐（relate/story），回来后切过去；
     * - 相关推荐也拿不到 → 停住并提示，不反复重试。
     *
     * @param position 播完那页的 position，用于过滤「切换瞬间旧页的迟到回调」
     */
    fun handleVideoCompleted(position: Int) {
        if (position != currentPageIndex || !isAdded) return
        if (feedAdapter == null) return

        val next = currentPageIndex + 1
        if (next < feedSource.getItemCount()) {
            viewPager.setCurrentItem(next, true)
            return
        }

        // 「只看 TA」：不拉相关推荐、不自动回推荐流。到底提示；下一页还在飞就等它回来接续
        if (!feedSource.supportsRelated) {
            if (feedSource.isExhausted()) {
                MsgUtil.showMsg(getString(R.string.msg_sv_author_end))
            } else {
                pendingAdvanceAfterMore = true
                feedSource.loadMore()
            }
            return
        }

        val anchorAid = feedSource.getItem(currentPageIndex)?.aid ?: 0L
        if (anchorAid <= 0) {
            MsgUtil.showMsg(getString(R.string.text_shortvideoplayer_no_more))
            return
        }
        pendingAdvanceAfterRelated = true
        feedSource.loadRelated(anchorAid)
    }

    /**
     * 给数据源挂三条回调。闭包捕获的是**传入的 source**而不是 feedSource：
     * 换源后旧数据源的在飞请求回来时，`source !== feedSource` 直接丢弃通知
     * （数据仍追加进旧源自己的列表，推荐流状态因此原地保留）。
     */
    private fun attachSourceCallbacks(source: ShortVideoFeedSource) {
        source.onItemsLoaded = load@{ items ->
            // 预加载是后台线程拉数据、回主线程才回调的，这中间用户可能已经退出页面。
            // 不判 isAdded 时旧代码会在这里抛
            // "Fragment ... not attached to an activity"（线上实测 4 例：loadInitial/loadRelated
            // 的回调打在一个已 detach 的 Fragment 上），所以三条回调都先过这道闸。
            if (!isAdded || source !== feedSource) return@load
            if (items.isNotEmpty()) {
                feedLoadFailed = false
                renderLoadingOverlay()
                errorLayout.visibility = View.GONE

                // adapter 的 count 与本次插入通知必须落在同一个主线程 turn、同一个实例上：
                // 数据源已在同一 turn 里把 items 追加进列表后才回调，所以此刻
                // getItemCount() 已经包含本批数据，start 就是本批的起点。
                val adapter = feedAdapter
                if (adapter == null) {
                    viewPager.adapter = newFeedAdapter(source)
                } else {
                    // 用范围插入代替 notifyDataSetChanged：避免 ViewPager2 重建全部页面并跳回第一页
                    val start = source.getItemCount() - items.size
                    if (start >= 0) adapter.notifyItemRangeInserted(start, items.size)
                }
                // 第一批内容到位后，右侧详情页才有 aid 可用
                host.onFeedAnchorChanged()
                // 作者流播完最后一条时等的下一页到了：自动接续
                if (pendingAdvanceAfterMore) {
                    pendingAdvanceAfterMore = false
                    val next = currentPageIndex + 1
                    if (next < source.getItemCount()) viewPager.setCurrentItem(next, true)
                }
            }
        }

        source.onLoadError = err@{ msg ->
            if (!isAdded || source !== feedSource) return@err
            feedLoadFailed = true
            if (source.getItemCount() == 0) {
                errorLayout.visibility = View.VISIBLE
                errorText.text = msg
            } else {
                // 已有内容时一次失败的刷新不该把转圈盖回正在播的画面上
                MsgUtil.showMsg(msg)
            }
            renderLoadingOverlay()
        }

        // 播到列表末尾时用相关推荐续接：成功后追加并切到下一条，失败就停住并提示
        source.onRelatedLoaded = related@{ items ->
            if (!isAdded || source !== feedSource) return@related
            if (items.isEmpty()) {
                pendingAdvanceAfterRelated = false
                MsgUtil.showMsg(getString(R.string.text_shortvideoplayer_no_more))
            } else {
                // 先取 adapter 再算 start，并用同一个实例通知：原来直接用 feedAdapter?.xxx，
                // 取实例与通知之间若 adapter 被换过（如视图重建），插入通知会打到已废弃的旧 adapter 上，
                // 留下来的新 adapter 数量涨了却没收到通知，ViewPager2 下一次 layout 就会判不一致。
                // start 用「当前总数 - 本批条数」反推，因为数据源已在同一 turn 里追加完。
                val adapter = feedAdapter
                val start = source.getItemCount() - items.size
                if (adapter != null && start >= 0) adapter.notifyItemRangeInserted(start, items.size)
                if (pendingAdvanceAfterRelated) {
                    pendingAdvanceAfterRelated = false
                    val next = currentPageIndex + 1
                    if (next < source.getItemCount()) {
                        viewPager.setCurrentItem(next, true)
                    }
                }
            }
        }
    }

    private fun loadFeed() {
        feedLoadFailed = false
        firstFrameOut = false
        renderLoadingOverlay()
        errorLayout.visibility = View.GONE
        loadingText.text = UiText.of("加载短视频...")
        feedSource.loadInitial()
    }

    /** 加载层显隐唯一出口：有内容 / 已失败 / 已出帧，三者任一都不再盖转圈。 */
    private fun renderLoadingOverlay() {
        loadingLayout.visibility =
            if (ShortVideoLoadingPolicy.keepLoading(feedSource.getItemCount(), feedLoadFailed, firstFrameOut)) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    /** 建 adapter 时挂首帧回调：出帧即收加载层，不等列表回调。 */
    private fun newFeedAdapter(source: ShortVideoFeedSource): ShortVideoPagerAdapter =
        ShortVideoPagerAdapter(host, source, audioManager).also { adapter ->
            adapter.onFirstFrame = {
                firstFrameOut = true
                renderLoadingOverlay()
            }
        }
}
