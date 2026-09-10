package com.RobinNotBad.BiliClient.util

import android.os.Handler
import android.os.Looper
import com.RobinNotBad.BiliClient.api.ShortVideoFeedApi
import com.RobinNotBad.BiliClient.model.ShortVideoItem

class VideoPreloadManager(private val preloadCount: Int = 2) {

    // 后台线程写入、主线程读取，用写时复制列表避免并发读写的数据竞争
    private val allItems = java.util.concurrent.CopyOnWriteArrayList<ShortVideoItem>()
    private val inFlightPreloads = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentIndex = 0

    // 后台线程写、主线程读，必须 volatile，否则 loadMore() 可能重复拉取
    @Volatile
    private var isLoading = false

    // 释放后置位，用于拦住"在飞请求回来后继续写列表 / 回调 UI"的情况
    @Volatile
    private var released = false

    var onItemsLoaded: ((List<ShortVideoItem>) -> Unit)? = null
    var onLoadError: ((String) -> Unit)? = null

    fun loadInitial() {
        // 检查并置位放在调用方线程（主线程）完成，避免 check-then-act 竞态
        if (isLoading) return
        isLoading = true
        CenterThreadPool.run {
            try {
                val items = ShortVideoFeedApi.fetchFeedPage()
                if (released) return@run
                if (items.isNotEmpty()) {
                    allItems.addAll(items)
                    mainHandler.post { onItemsLoaded?.invoke(items) }
                    preloadNext()
                } else {
                    mainHandler.post { onLoadError?.invoke("获取视频列表失败") }
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
                val items = ShortVideoFeedApi.fetchFeedPage()
                if (released) return@run
                if (items.isNotEmpty()) {
                    allItems.addAll(items)
                    mainHandler.post { onItemsLoaded?.invoke(items) }
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun getItem(index: Int): ShortVideoItem? {
        return allItems.getOrNull(index)
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