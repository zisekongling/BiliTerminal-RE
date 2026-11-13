package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.util.UiText

import android.os.Handler
import android.os.Looper
import com.RobinNotBad.BiliClient.api.ShortVideoFeedApi
import com.RobinNotBad.BiliClient.api.StoryFeedPager
import com.RobinNotBad.BiliClient.model.ShortVideoItem

class VideoPreloadManager(private val preloadCount: Int = 2) {

    // 分页状态（display_id / track_id / 已出现的 aid）跟着管理器的生命周期走：
    // 页面销毁、管理器被替换时一并丢弃，避免旧实现里 object 上的静态页码跨页面泄漏。
    private val pager = StoryFeedPager()

    // 只在主线程写（见 loadInitial/loadMore/loadRelated 里那个 mainHandler.post），
    // 但 preloadVideoUrl 会在 CenterThreadPool 线程按索引读，用写时复制列表避免并发读写竞争
    private val allItems = java.util.concurrent.CopyOnWriteArrayList<ShortVideoItem>()
    // 注意：不能用 ConcurrentHashMap.newKeySet()——它和它返回的 KeySetView 都是 API 24 才有的，
    // minSdk 21 上类初始化就 NoSuchMethodError。newSetFromMap 是 API 9，并发语义等价。
    private val inFlightPreloads =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Int, Boolean>())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentIndex = 0

    // 后台线程写、主线程读，必须 volatile，否则 loadMore() 可能重复拉取
    @Volatile
    private var isLoading = false

    // 释放后置位，用于拦住"在飞请求回来后继续写列表 / 回调 UI"的情况
    @Volatile
    private var released = false

    // 相关推荐请求进行中（与普通翻页互斥的意义不同：播完续接只允许一个在飞）
    @Volatile
    private var isLoadingRelated = false

    var onItemsLoaded: ((List<ShortVideoItem>) -> Unit)? = null
    var onLoadError: ((String) -> Unit)? = null

    /**
     * 相关推荐拉取结束：成功时参数是非空列表（已追加到列表末尾），失败/没续接上时是空列表。
     * 播完自动接续靠这个回调决定「切下一条」还是「停下」。
     */
    var onRelatedLoaded: ((List<ShortVideoItem>) -> Unit)? = null

    fun loadInitial() {
        // 检查并置位放在调用方线程（主线程）完成，避免 check-then-act 竞态
        if (isLoading) return
        isLoading = true
        CenterThreadPool.run {
            try {
                val items = pager.fetchPage()
                if (released) return@run
                if (items.isNotEmpty()) {
                    // 追加数据、通知 UI、预加载必须在同一个主线程 turn 里按这个顺序做完。
                    // adapter.getItemCount() 直接返回 allItems.size：原来在后台线程先 addAll、
                    // 主线程的通知后到，RecyclerView 拿到的 itemCount 与已通知的插入量对不上；
                    // 两批请求交错时「count - items.size」还会算出同一个 start，两次通知都指向同一段，
                    // 最终在 ViewPager2.onLayout 里炸成
                    // "Inconsistency detected. Invalid view holder adapter position"。
                    mainHandler.post {
                        if (released) return@post
                        allItems.addAll(items)
                        onItemsLoaded?.invoke(items)
                        preloadNext()
                    }
                } else {
                    mainHandler.post { onLoadError?.invoke(UiText.of("获取视频列表失败")) }
                }
            } finally {
                // 必须 finally：否则 fetch 抛异常会让 isLoading 永远为 true，之后再也拉不到数据
                isLoading = false
            }
        }
    }

    fun loadMore() {
        if (isLoading) return
        isLoading = true
        CenterThreadPool.run {
            try {
                val items = pager.fetchPage()
                if (released) return@run
                if (items.isNotEmpty()) {
                    // 与 loadInitial 同理：追加与通知同一主线程 turn，
                    // 保证 adapter 读到的 itemCount 与已通知的插入量始终一致
                    mainHandler.post {
                        if (released) return@post
                        allItems.addAll(items)
                        onItemsLoaded?.invoke(items)
                    }
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun getItem(index: Int): ShortVideoItem? {
        return allItems.getOrNull(index)
    }

    fun isLoadingRelated(): Boolean = isLoadingRelated

    /**
     * 列表已经刷到底时，以 [anchorAid]（通常是刚播完的那条）为锚点拉一批相关推荐。
     *
     * 与 [loadMore] 的区别：相关推荐不推进分页游标、也没有 track_id 语义，只是给
     * 「播完自动接下一条」续命。结果一律通过 [onRelatedLoaded] 回主线程，空列表表示没接上。
     */
    fun loadRelated(anchorAid: Long) {
        if (isLoadingRelated) return
        isLoadingRelated = true
        CenterThreadPool.run {
            try {
                val items = pager.fetchRelated(anchorAid)
                if (released) return@run
                if (items.isNotEmpty()) {
                    // 相关推荐也走主线程追加，且与 onRelatedLoaded 同一 turn：
                    // adapter.getItemCount() 直接读 allItems.size，后台先加会让下一次 layout
                    // 看到「数量已涨、插入还没通知」的中间态
                    mainHandler.post {
                        if (released) return@post
                        allItems.addAll(items)
                        onRelatedLoaded?.invoke(items)
                    }
                } else {
                    // 没续接上：只回调，不动列表
                    mainHandler.post {
                        if (released) return@post
                        onRelatedLoaded?.invoke(items)
                    }
                }
            } finally {
                isLoadingRelated = false
            }
        }
    }

    fun getCurrentIndex(): Int = currentIndex

    fun getItemCount(): Int = allItems.size

    fun moveToIndex(index: Int) {
        if (index >= 0 && index < allItems.size) {
            currentIndex = index
        }
    }

    fun preloadVideoUrl(index: Int) {
        if (index < 0 || index >= allItems.size) return
        if (!inFlightPreloads.add(index)) return

        CenterThreadPool.run {
            try {
                val item = allItems[index]
                if (item.videoUrl.isEmpty()) {
                    Logu.d("PreloadManager", "Preloading video URL for index=$index, aid=${item.aid}")
                    ShortVideoFeedApi.fetchVideoUrl(item)
                }
            } finally {
                inFlightPreloads.remove(index)
            }
        }
    }

    private fun preloadNext() {
        for (i in 1..preloadCount) {
            preloadVideoUrl(currentIndex + i)
        }
    }

    fun onSwipeToIndex(index: Int) {
        currentIndex = index
        preloadNext()

        if (index >= allItems.size - 3) {
            loadMore()
        }
    }

    fun release() {
        released = true
        allItems.clear()
        inFlightPreloads.clear()
    }
}