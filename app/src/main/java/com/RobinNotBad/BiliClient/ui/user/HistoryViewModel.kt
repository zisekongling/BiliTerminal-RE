package com.RobinNotBad.BiliClient.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.RobinNotBad.BiliClient.network.api.VideoFeedApiService
import com.RobinNotBad.BiliClient.ui.video.viewmodel.PaginatedVideoListState
import com.RobinNotBad.BiliClient.ui.video.viewmodel.VideoCardItem
import com.RobinNotBad.BiliClient.util.TimeUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val apiService: VideoFeedApiService
) : ViewModel() {

    private val _state = MutableStateFlow(PaginatedVideoListState())
    val state: StateFlow<PaginatedVideoListState> = _state.asStateFlow()

    private var cursorMax = 0L
    private var cursorViewAt = 0L
    private var cursorBusiness = ""
    
    private var rawHistoryList = mutableListOf<VideoCardItem>()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, isRefreshing = true) }
            try {
                val response = apiService.getHistory()
                val json = JSONObject(response.toString())
                val (list, cursor) = parseHistoryResponse(json)
                cursorMax = cursor.first
                cursorViewAt = cursor.second
                cursorBusiness = cursor.third
                rawHistoryList.clear()
                rawHistoryList.addAll(list)
                val grouped = groupByDay(rawHistoryList)
                _state.update {
                    it.copy(items = grouped, isLoading = false, isRefreshing = false, error = null, hasMore = list.size >= 30)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "加载失败")
                }
            }
        }
    }

    fun loadMore() {
        if (!_state.value.hasMore || _state.value.isLoadingMore) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoadingMore = true) }
            try {
                val response = apiService.getHistory(
                    max = cursorMax,
                    viewAt = cursorViewAt,
                    business = cursorBusiness
                )
                val json = JSONObject(response.toString())
                val (list, cursor) = parseHistoryResponse(json)
                
                cursorMax = cursor.first
                cursorViewAt = cursor.second
                cursorBusiness = cursor.third
                
                if (list.isNotEmpty()) {
                    rawHistoryList.addAll(list)
                    val grouped = groupByDay(rawHistoryList)
                    _state.update { currentState ->
                        currentState.copy(
                            items = grouped,
                            isLoadingMore = false,
                            error = null,
                            hasMore = list.size >= 30
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isLoadingMore = false,
                            error = null,
                            hasMore = false
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoadingMore = false,
                        error = e.message ?: "加载失败",
                        hasMore = false
                    )
                }
            }
        }
    }

    fun refresh() {
        cursorMax = 0L
        cursorViewAt = 0L
        cursorBusiness = ""
        loadHistory()
    }

    /**
     * Parse cursor API response, returns (items list, cursor (max, view_at, business))
     */
    private fun parseHistoryResponse(json: JSONObject): Pair<List<VideoCardItem>, Triple<Long, Long, String>> {
        val list = mutableListOf<VideoCardItem>()
        var cMax = 0L
        var cViewAt = 0L
        var cBusiness = ""
        try {
            val data = json.optJSONObject("data") ?: return Pair(list, Triple(cMax, cViewAt, cBusiness))
            
            // Parse cursor
            val cursor = data.optJSONObject("cursor")
            if (cursor != null) {
                cMax = cursor.optLong("max", 0)
                cViewAt = cursor.optLong("view_at", 0)
                cBusiness = cursor.optString("business", "")
            }

            // Parse list
            val items = data.optJSONArray("list")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val history = item.optJSONObject("history")
                    val aid = history?.optLong("oid", 0) ?: 0
                    val bvid = history?.optString("bvid", "") ?: ""
                    val viewAt = item.optLong("view_at", 0)

                    list.add(VideoCardItem(
                        aid = aid,
                        bvid = bvid,
                        title = item.optString("title", ""),
                        author = item.optString("author_name", ""),
                        cover = item.optString("cover", ""),
                        duration = item.optLong("duration", 0),
                        pubdate = item.optLong("pubdate", 0),
                        viewAt = viewAt
                    ))
                }
            }
        } catch (_: Exception) {}
        return Pair(list, Triple(cMax, cViewAt, cBusiness))
    }

    /**
     * Group items by day (based on view_at), adding dateLabel on the first item of each day
     */
    private fun groupByDay(items: List<VideoCardItem>): List<VideoCardItem> {
        if (items.isEmpty()) return items
        
        val result = mutableListOf<VideoCardItem>()
        var lastDateKey = ""
        
        for (item in items) {
            val dateKey = TimeUtil.toDateKey(item.viewAt)
            
            if (dateKey.isNotEmpty() && dateKey != lastDateKey) {
                val smartDate = TimeUtil.toSmartDate(item.viewAt)
                result.add(item.copy(dateLabel = smartDate))
                lastDateKey = dateKey
            } else {
                result.add(item.copy(dateLabel = ""))
            }
        }
        return result
    }
}