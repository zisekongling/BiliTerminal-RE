package com.RobinNotBad.BiliClient.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.RobinNotBad.BiliClient.network.api.VideoFeedApiService
import com.RobinNotBad.BiliClient.ui.video.viewmodel.PaginatedVideoListState
import com.RobinNotBad.BiliClient.ui.video.viewmodel.VideoCardItem
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class SearchUiState(
    val keyword: String = "",
    val searchHistory: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val searchResult: PaginatedVideoListState = PaginatedVideoListState(),
    val showHistory: Boolean = true,
    val showSuggestions: Boolean = false,
    val currentTab: Int = 0,
    val defaultHint: String = "",
    val suggestionsEnabled: Boolean = true
)

@HiltViewModel
class ModernSearchViewModel @Inject constructor(
    private val apiService: VideoFeedApiService
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var suggestionJob: Job? = null

    init {
        loadHistory()
        loadSettings()
    }

    private fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val arr = JSONArray(
                    SharedPreferencesUtil.getString(SharedPreferencesUtil.search_history, "[]")
                )
                val list = (0 until arr.length()).mapNotNull { arr.optString(it) }
                _state.update { it.copy(searchHistory = list) }
            } catch (_: Exception) {}
        }
    }

    private fun saveHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            SharedPreferencesUtil.putString(
                SharedPreferencesUtil.search_history,
                JSONArray(_state.value.searchHistory).toString()
            )
        }
    }

    private fun loadSettings() {
        _state.update {
            it.copy(
                suggestionsEnabled = SharedPreferencesUtil.getBoolean("search_suggestions_enable", true)
            )
        }
    }

    fun onKeywordChanged(keyword: String) {
        _state.update {
            it.copy(
                keyword = keyword,
                showHistory = keyword.isEmpty(),
                showSuggestions = keyword.isNotEmpty() && it.suggestionsEnabled
            )
        }

        if (keyword.isNotEmpty() && _state.value.suggestionsEnabled) {
            suggestionJob?.cancel()
            suggestionJob = viewModelScope.launch(Dispatchers.IO) {
                delay(300)
                loadSuggestions(keyword)
            }
        }
    }

    private suspend fun loadSuggestions(keyword: String) {
        try {
            val response = JSONObject(apiService.getPopular(1).toString())
            val data = response.optJSONObject("data")
            val list = data?.optJSONArray("suggestions")
            val suggestions = mutableListOf<String>()
            if (list != null) {
                for (i in 0 until list.length()) {
                    suggestions.add(list.optString(i, ""))
                }
            }
            _state.update {
                if (it.keyword == keyword) {
                    it.copy(suggestions = suggestions)
                } else it
            }
        } catch (_: Exception) {}
    }

    fun search(keyword: String) {
        if (keyword.isBlank()) return

        val kw = keyword.trim()
        _state.update { s ->
            val updatedHistory = s.searchHistory.toMutableList().apply {
                remove(kw)
                add(0, kw)
            }
            s.copy(
                keyword = kw,
                searchHistory = updatedHistory.take(20),
                isSearching = true,
                showHistory = false,
                showSuggestions = false
            )
        }
        saveHistory()

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(searchResult = it.searchResult.copy(isLoading = true)) }
            try {
                val response = JSONObject(apiService.getPopular(1).toString())
                val items = parseSearchResults(response)
                _state.update {
                    it.copy(
                        searchResult = it.searchResult.copy(
                            items = items, isLoading = false, error = null
                        ),
                        isSearching = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        searchResult = it.searchResult.copy(
                            isLoading = false, error = e.message ?: "搜索失败"
                        ),
                        isSearching = false
                    )
                }
            }
        }
    }

    fun selectSuggestion(suggestion: String) {
        _state.update { it.copy(keyword = suggestion) }
        search(suggestion)
    }

    fun deleteHistoryItem(position: Int) {
        _state.update { s ->
            val h = s.searchHistory.toMutableList().apply { removeAt(position) }
            s.copy(searchHistory = h)
        }
        saveHistory()
    }

    fun selectTab(index: Int) {
        _state.update { it.copy(currentTab = index) }
    }

    private fun parseSearchResults(json: JSONObject): List<VideoCardItem> {
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
                        playCount = item.optJSONObject("stat")?.optInt("view", 0) ?: 0,
                        danmakuCount = item.optJSONObject("stat")?.optInt("danmaku", 0) ?: 0,
                        duration = item.optLong("duration", 0)
                    ))
                }
            }
        } catch (_: Exception) {}
        return list
    }
}