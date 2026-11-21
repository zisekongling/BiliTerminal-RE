package com.RobinNotBad.BiliClient.activity.video

import android.os.Handler
import android.os.Looper
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.api.ShortVideoFeedApi
import com.RobinNotBad.BiliClient.api.UserInfoApi
import com.RobinNotBad.BiliClient.api.VideoInfoApi
import com.RobinNotBad.BiliClient.model.ShortVideoItem
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.ui.shortvideo.ShortVideoAuthorPolicy
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.VideoPreloadManager
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 短视频信息流数据源（26.11「只看 TA」原地换源）。
 *
 * 抽象在 [VideoPreloadManager] **之上**：推荐流原样委托给管理器（行为逐字节不变），
 * 作者流是独立实现。ViewPager2 / PageHolder / Fragment 只认这个接口，
 * 换源 = 换实现 + 换 adapter，翻页逻辑里不散落 if-else。
 *
 * 线程约定与 [VideoPreloadManager] 一致：
 * - 列表用写时复制；**追加数据 + 通知 UI + 预加载必须在同一个主线程 turn 里按序做完**
 *   （否则 ViewPager2 会炸 "Inconsistency detected"，见管理器里的注释）。
 * - [resolvePlayable] 在后台线程调用（CenterThreadPool），可能发同步网络请求。
 */
interface ShortVideoFeedSource {

    /** 是否支持「播完拉相关推荐续接」。作者流不支持：到底就停，不自动回推荐。 */
    val supportsRelated: Boolean

    var onItemsLoaded: ((List<ShortVideoItem>) -> Unit)?
    var onLoadError: ((String) -> Unit)?
    var onRelatedLoaded: ((List<ShortVideoItem>) -> Unit)?

    fun getItemCount(): Int
    fun getItem(index: Int): ShortVideoItem?
    fun loadInitial()
    fun loadMore()
    fun loadRelated(anchorAid: Long)
    fun onSwipeToIndex(index: Int)
    fun preloadVideoUrl(index: Int)

    /**
     * 把条目补成「可播放」：返回非 null 即 [ShortVideoItem.videoUrl] 非空。
     *
     * 推荐流条目自带 cid，直接取流、返回原实例；作者流条目（arc/search 的 vlist）
     * **没有 cid / duration**，而 `PlayerApi.getVideo` 把 cid 原样拼进 playurl、
     * 不会替 cid=0 解析——所以这里先经 `VideoInfoApi.getVideoInfo(aid)` 拿首 P cid，
     * `copy(cid=…)` 出富化副本再取流，并把可变字段写回原实例。
     *
     * @param qn 目标清晰度；-1 表示跟随设置默认值。
     */
    fun resolvePlayable(item: ShortVideoItem, qn: Int = -1): ShortVideoItem?

    /** 列表是否已确定拉空（推荐流永远 false）。用于「到底」提示与播完处理。 */
    fun isExhausted(): Boolean

    fun release()
}

/**
 * 推荐流：纯委托 [VideoPreloadManager]，不改它一行（util/ 不在本轮改动范围）。
 * 进入「只看 TA」期间本实例**保持存活**：推荐列表、StoryFeedPager 的
 * display_id / track_id / seenAids 全部原地保留，退出时不重拉第一页。
 */
class RecommendFeedSource(private val manager: VideoPreloadManager) : ShortVideoFeedSource {

    override val supportsRelated: Boolean = true

    override var onItemsLoaded: ((List<ShortVideoItem>) -> Unit)?
        get() = manager.onItemsLoaded
        set(value) { manager.onItemsLoaded = value }

    override var onLoadError: ((String) -> Unit)?
        get() = manager.onLoadError
        set(value) { manager.onLoadError = value }

    override var onRelatedLoaded: ((List<ShortVideoItem>) -> Unit)?
        get() = manager.onRelatedLoaded
        set(value) { manager.onRelatedLoaded = value }

    override fun getItemCount(): Int = manager.getItemCount()
    override fun getItem(index: Int): ShortVideoItem? = manager.getItem(index)
    override fun loadInitial() = manager.loadInitial()
    override fun loadMore() = manager.loadMore()
    override fun loadRelated(anchorAid: Long) = manager.loadRelated(anchorAid)
    override fun onSwipeToIndex(index: Int) = manager.onSwipeToIndex(index)
    override fun preloadVideoUrl(index: Int) = manager.preloadVideoUrl(index)

    override fun resolvePlayable(item: ShortVideoItem, qn: Int): ShortVideoItem? {
        // 与旧 setupPlayer 完全同语义：fetchVideoUrl 内部已捕获异常并填可变字段
        val ok = if (qn < 0) ShortVideoFeedApi.fetchVideoUrl(item)
        else ShortVideoFeedApi.fetchVideoUrl(item, qn)
        return if (ok && item.videoUrl.isNotEmpty()) item else null
    }

    override fun isExhausted(): Boolean = false
    override fun release() = manager.release()
}

/**
 * 作者流（「只看 TA」）：`x/space/wbi/arc/search` 按发布时间倒序分页。
 *
 * 构造时把当前播放的这条钉在第 0 位（同步入列，count 立刻 ≥ 1，换源后马上能播），
 * 其余投稿后台分页补齐；按 aid 去重。到底（接口返回空 vlist）置 [exhausted]，
 * 不拉相关推荐、不自动回推荐流。
 */
class AuthorFeedSource(
    private val mid: Long,
    seed: ShortVideoItem
) : ShortVideoFeedSource {

    override val supportsRelated: Boolean = false

    override var onItemsLoaded: ((List<ShortVideoItem>) -> Unit)? = null
    override var onLoadError: ((String) -> Unit)? = null
    override var onRelatedLoaded: ((List<ShortVideoItem>) -> Unit)? = null

    private val allItems = CopyOnWriteArrayList<ShortVideoItem>()
    private val seenAids = HashSet<Long>()
    private val authorFace = seed.authorFace

    // 只在 isLoading 串行的后台取页里读写；exhausted 主线程也要读，volatile
    private var nextPage = 1
    @Volatile
    private var exhausted = false

    @Volatile
    private var isLoading = false
    @Volatile
    private var released = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentIndex = 0
    private var trimHighWater = 0
    private val preloadCount = 3

    // 与 VideoPreloadManager 同款：newKeySet 是 API 24，minSdk 21 用 newSetFromMap
    private val inFlightPreloads =
        Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())

    // aid → 首 P cid 缓存。resolvePlayable / preload 都在后台线程调用，用并发容器
    private val cidCache = ConcurrentHashMap<Long, Long>()

    init {
        allItems.add(seed)
        if (seed.aid > 0) seenAids.add(seed.aid)
        if (seed.cid > 0) cidCache[seed.aid] = seed.cid
    }

    override fun getItemCount(): Int = allItems.size
    override fun getItem(index: Int): ShortVideoItem? = allItems.getOrNull(index)
    override fun isExhausted(): Boolean = exhausted

    override fun loadInitial() = fetchPage(notifyError = true)

    override fun loadMore() = fetchPage(notifyError = false)

    /** 作者流没有相关推荐，空实现（[supportsRelated] = false，调用方不会走这里）。 */
    override fun loadRelated(anchorAid: Long) = Unit

    private fun fetchPage(notifyError: Boolean) {
        if (isLoading || released || exhausted) return
        isLoading = true
        CenterThreadPool.run {
            try {
                val items = fetchNextPage()
                if (released) return@run
                if (items.isNotEmpty()) {
                    // 追加 + 通知 + 预加载同一主线程 turn，理由见 VideoPreloadManager.loadInitial
                    mainHandler.post {
                        if (released) return@post
                        allItems.addAll(items)
                        onItemsLoaded?.invoke(items)
                        preloadNext()
                    }
                }
            } catch (e: Exception) {
                Logu.e(TAG, "拉取作者投稿失败 mid=$mid: ${e.message}")
                if (notifyError && !released) {
                    val msg = BiliTerminal.context?.getString(R.string.msg_sv_author_list_failed)
                        ?: "author feed load failed"
                    mainHandler.post { if (!released) onLoadError?.invoke(msg) }
                }
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 同步取下一页（后台线程）。返回码口径见 `UserInfoApi.getUserVideos`：
     * 0 成功、1 空 vlist（到底）、-1 结构异常。成功才推进页码。
     */
    @Synchronized
    @Throws(IOException::class)
    private fun fetchNextPage(): List<ShortVideoItem> {
        val cards = ArrayList<VideoCard>()
        val code = UserInfoApi.getUserVideos(mid, nextPage, null, UserInfoApi.ORDER_PUBDATE, cards)
        if (code == 1) {
            exhausted = true
            return emptyList()
        }
        if (code != 0) throw IOException("space arc/search code=$code")
        nextPage++
        val result = ArrayList<ShortVideoItem>(cards.size)
        for (card in cards) {
            if (card.aid > 0 && seenAids.add(card.aid)) {
                result.add(ShortVideoAuthorPolicy.itemFromCard(card, mid, authorFace))
            }
        }
        return result
    }

    override fun onSwipeToIndex(index: Int) {
        currentIndex = index
        trimPlayedWindow()
        preloadNext()
        if (index >= allItems.size - 3) loadMore()
    }

    override fun preloadVideoUrl(index: Int) {
        if (index < 0 || index >= allItems.size) return
        if (!inFlightPreloads.add(index)) return
        CenterThreadPool.run {
            try {
                val item = allItems.getOrNull(index) ?: return@run
                if (item.videoUrl.isEmpty()) {
                    try {
                        resolvePlayable(item)
                    } catch (e: Exception) {
                        Logu.w(TAG, "预加载作者视频失败 aid=${item.aid}: ${e.message}")
                    }
                }
            } finally {
                inFlightPreloads.remove(index)
            }
        }
    }

    private fun preloadNext() {
        for (i in 1..preloadCount) preloadVideoUrl(currentIndex + i)
    }

    override fun resolvePlayable(item: ShortVideoItem, qn: Int): ShortVideoItem? {
        if (released) return null
        if (item.videoUrl.isNotEmpty()) return item
        var playable = item
        if (playable.cid <= 0) {
            val cid = resolveCid(item.aid) ?: return null
            playable = item.copy(cid = cid)
        }
        val ok = if (qn < 0) ShortVideoFeedApi.fetchVideoUrl(playable)
        else ShortVideoFeedApi.fetchVideoUrl(playable, qn)
        if (!ok || playable.videoUrl.isEmpty()) return null
        if (playable !== item) {
            // 可变字段写回原实例：列表里其他引用（预加载、瘦身）看到的也是已取流状态
            item.videoUrl = playable.videoUrl
            item.danmakuUrl = playable.danmakuUrl
            item.qnStrList = playable.qnStrList
            item.qnValueList = playable.qnValueList
            item.currentQuality = playable.currentQuality
            // 列表槽位换成带 cid 的副本，后续 bind / 弹幕 / 换清晰度都拿到完整条目。
            // 只换引用不动数量，无需 notify；正在播的 holder 由 setupPlayer 直接拿到副本。
            mainHandler.post { replaceSlot(item.aid, playable) }
        }
        return playable
    }

    /** 首 P cid：缓存优先，未命中走视频详情接口；失败返回 null（调用方按取流失败处理）。 */
    private fun resolveCid(aid: Long): Long? {
        cidCache[aid]?.let { return it }
        return try {
            val info = VideoInfoApi.getVideoInfo(aid)
            val cid = info.cids.firstOrNull() ?: 0L
            if (cid > 0) {
                cidCache[aid] = cid
                cid
            } else null
        } catch (e: Exception) {
            Logu.w(TAG, "解析 cid 失败 aid=$aid: ${e.message}")
            null
        }
    }

    private fun replaceSlot(aid: Long, enriched: ShortVideoItem) {
        if (released) return
        for (i in allItems.indices) {
            val old = allItems[i]
            if (old.aid == aid && old.cid <= 0) {
                allItems[i] = enriched
                return
            }
        }
    }

    /** 与 VideoPreloadManager.trimPlayedWindow 同口径：只瘦字段不裁列表，绝对下标不变。 */
    private fun trimPlayedWindow() {
        val trimBefore = currentIndex - VideoPreloadManager.KEEP_BEHIND
        if (trimBefore <= trimHighWater) return
        for (i in trimHighWater until trimBefore) {
            if (i < allItems.size) VideoPreloadManager.trimPlayedItemFields(allItems[i])
        }
        trimHighWater = trimBefore.coerceAtMost(allItems.size)
    }

    override fun release() {
        released = true
        allItems.clear()
        inFlightPreloads.clear()
        cidCache.clear()
        trimHighWater = 0
    }

    companion object {
        private const val TAG = "AuthorFeedSource"
    }
}