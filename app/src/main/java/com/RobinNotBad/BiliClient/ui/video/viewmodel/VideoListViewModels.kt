package com.RobinNotBad.BiliClient.ui.video.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.RobinNotBad.BiliClient.network.api.VideoFeedApiService
import com.RobinNotBad.BiliClient.ui.theme.BiliColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class VideoCardItem(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val cover: String = "",
    val author: String = "",
    val mid: Long = 0,
    val playCount: Int = 0,
    val danmakuCount: Int = 0,
    val duration: Long = 0,
    val pubdate: Long = 0,
    val pic: String = "",
    val rank: Int = 0,
    val statView: Int = 0,
    val statDanmaku: Int = 0,
    val dateLabel: String = "",
    val viewAt: Long = 0,
    val itemType: String = "",
    val roomId: Long = 0,
    val statLabel: String = ""
)

data class PaginatedVideoListState(
    val items: List<VideoCardItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val currentPage: Int = 1
)

@HiltViewModel
class RecommandVideoViewModel @Inject constructor(
    private val apiService: VideoFeedApiService
) : ViewModel() {

    private val _state = MutableStateFlow(PaginatedVideoListState())
    val state: StateFlow<PaginatedVideoListState> = _state.asStateFlow()

    private var freshType = 3

    init {
        loadRecommend()
    }

    fun loadRecommend() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = it.items.isEmpty(), isRefreshing = it.items.isNotEmpty()) }
            try {
                val response = apiService.getRecommend(freshType)
                val list = parseRecommendResponse(JSONObject(response.toString()))
                _state.update {
                    it.copy(
                        items = list,
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        hasMore = list.size >= 20
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message ?: "加载失败"
                    )
                }
            }
        }
    }

    private fun parseRecommendResponse(json: JSONObject): List<VideoCardItem> {
        val list = mutableListOf<VideoCardItem>()
        try {
            val data = json.optJSONObject("data")
            val items = data?.optJSONArray("item")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    list.add(VideoCardItem(
                        aid = item.optLong("id", 0),
                        bvid = item.optString("bvid", ""),
                        title = item.optString("title", ""),
                        cover = item.optString("pic", ""),
                        author = item.optJSONObject("owner")?.optString("name", "") ?: "",
                        mid = item.optJSONObject("owner")?.optLong("mid", 0) ?: 0,
                        playCount = item.optJSONObject("stat")?.optInt("view", 0) ?: 0,
                        danmakuCount = item.optJSONObject("stat")?.optInt("danmaku", 0) ?: 0,
                        duration = item.optLong("duration", 0),
                        pubdate = item.optLong("pubdate", 0)
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun refresh() {
        freshType = 3
        loadRecommend()
    }
}

@HiltViewModel
class PopularVideoViewModel @Inject constructor(
    private val apiService: VideoFeedApiService
) : ViewModel() {

    private val _state = MutableStateFlow(PaginatedVideoListState())
    val state: StateFlow<PaginatedVideoListState> = _state.asStateFlow()

    fun loadPopular(page: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    isLoading = page == 1 && it.items.isEmpty(),
                    isLoadingMore = page > 1
                )
            }
            try {
                val response = apiService.getPopular(page)
                val list = parsePopularResponse(JSONObject(response.toString()))
                _state.update {
                    it.copy(
                        items = if (page == 1) list else it.items + list,
                        isLoading = false,
                        isLoadingMore = false,
                        error = null,
                        hasMore = list.size >= 20,
                        currentPage = page
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = e.message ?: "加载失败"
                    )
                }
            }
        }
    }

    private fun parsePopularResponse(json: JSONObject): List<VideoCardItem> {
        val list = mutableListOf<VideoCardItem>()
        try {
            val data = json.optJSONObject("data")
            val items = data?.optJSONArray("list")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    list.add(VideoCardItem(
                        aid = item.optLong("aid", 0),
                        bvid = item.optString("bvid", ""),
                        title = item.optString("title", ""),
                        cover = item.optString("pic", ""),
                        author = item.optJSONObject("owner")?.optString("name", "") ?: "",
                        mid = item.optJSONObject("owner")?.optLong("mid", 0) ?: 0,
                        playCount = item.optJSONObject("stat")?.optInt("view", 0) ?: 0,
                        danmakuCount = item.optJSONObject("stat")?.optInt("danmaku", 0) ?: 0,
                        duration = item.optLong("duration", 0),
                        pubdate = item.optLong("pubdate", 0)
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun refresh() = loadPopular(1)
    fun loadMore() = loadPopular(_state.value.currentPage + 1)
}

@HiltViewModel
class RankingVideoViewModel @Inject constructor(
    private val apiService: VideoFeedApiService
) : ViewModel() {

    private val _state = MutableStateFlow(PaginatedVideoListState())
    val state: StateFlow<PaginatedVideoListState> = _state.asStateFlow()

    private var rid = 0
    private var rankingType = "all"

    init {
        loadRanking()
    }

    fun loadRanking(rid: Int = 0, type: String = "all") {
        this.rid = rid
        this.rankingType = type
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            try {
                val response = apiService.getRanking(rid, type)
                val list = parseRankingResponse(JSONObject(response.toString()))
                _state.update {
                    it.copy(
                        items = list,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "加载失败"
                    )
                }
            }
        }
    }

    private fun parseRankingResponse(json: JSONObject): List<VideoCardItem> {
        val list = mutableListOf<VideoCardItem>()
        try {
            val data = json.optJSONObject("data")
            val items = data?.optJSONArray("list")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    list.add(VideoCardItem(
                        aid = item.optLong("aid", 0),
                        bvid = item.optString("bvid", ""),
                        title = item.optString("title", ""),
                        cover = item.optString("pic", ""),
                        author = item.optJSONObject("owner")?.optString("name", "") ?: "",
                        mid = item.optJSONObject("owner")?.optLong("mid", 0) ?: 0,
                        playCount = item.optJSONObject("stat")?.optInt("view", 0) ?: 0,
                        danmakuCount = item.optJSONObject("stat")?.optInt("danmaku", 0) ?: 0,
                        duration = item.optLong("duration", 0),
                        pubdate = item.optLong("pubdate", 0),
                        rank = i + 1
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun refresh() = loadRanking(rid, rankingType)
}