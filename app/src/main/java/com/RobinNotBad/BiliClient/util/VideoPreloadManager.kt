package com.RobinNotBad.BiliClient.util

import android.os.Handler
import android.os.Looper
import com.RobinNotBad.BiliClient.api.ShortVideoFeedApi
import com.RobinNotBad.BiliClient.model.ShortVideoItem
import java.util.concurrent.ConcurrentLinkedQueue

class VideoPreloadManager(private val preloadCount: Int = 2) {

    private val preloadedItems = ConcurrentLinkedQueue<ShortVideoItem>()
    private val allItems = mutableListOf<ShortVideoItem>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentIndex = 0
    private var isLoading = false

    var onItemsLoaded: ((List<ShortVideoItem>) -> Unit)? = null
    var onLoadError: ((String) -> Unit)? = null

    fun loadInitial() {
        CenterThreadPool.run {
            isLoading = true
            val items = ShortVideoFeedApi.fetchFeedPage()
            if (items.isNotEmpty()) {
                allItems.addAll(items)
                mainHandler.post { onItemsLoaded?.invoke(items) }
                preloadNext()
            } else {
                mainHandler.post { onLoadError?.invoke("获取视频列表失败") }
            }
            isLoading = false
        }
    }

    fun loadMore() {
        if (isLoading) return
        CenterThreadPool.run {
            isLoading = true
            val items = ShortVideoFeedApi.fetchFeedPage()
            if (items.isNotEmpty()) {
                allItems.addAll(items)
                mainHandler.post { onItemsLoaded?.invoke(items) }
            }
            isLoading = false
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

        CenterThreadPool.run {
            val item = allItems[index]
            if (item.videoUrl.isEmpty()) {
                Logu.d("PreloadManager", "Preloading video URL for index=$index, aid=${item.aid}")
                ShortVideoFeedApi.fetchVideoUrl(item)
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
        allItems.clear()
        preloadedItems.clear()
    }
}