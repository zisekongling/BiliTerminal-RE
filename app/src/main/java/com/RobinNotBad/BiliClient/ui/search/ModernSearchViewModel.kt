package com.RobinNotBad.BiliClient.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.RobinNotBad.BiliClient.api.SearchApi
import com.RobinNotBad.BiliClient.ui.video.viewmodel.PaginatedVideoListState
import com.RobinNotBad.BiliClient.ui.video.viewmodel.VideoCardItem
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.StringUtil
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
class ModernSearchViewModel @Inject constructor() : ViewModel() {

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
            val suggestions = SearchApi.getSearchSuggestions(keyword)
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
                val type = SEARCH_TYPES[_state.value.currentTab.coerceIn(0, SEARCH_TYPES.lastIndex)]
                val result = SearchApi.searchType(kw, 1, type)
                val items = parseSearchResults(result, type)
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
        val s = _state.value
        if (s.keyword.isNotEmpty() && !s.showHistory && !s.showSuggestions) {
            search(s.keyword)
        }
    }

    private fun parseSearchResults(result: Any?, type: String): List<VideoCardItem> {
        return when (type) {
            "media_bangumi" -> parseBangumiResults(result)
            "bili_user" -> parseUserResults(result)
            "live_room" -> parseLiveResults(result)
            else -> parseVideoResults(result)
        }
    }

    private fun parseVideoResults(result: Any?): List<VideoCardItem> {
        val list = mutableListOf<VideoCardItem>()
        if (result !is JSONArray) return list
        try {
            for (i in 0 until result.length()) {
                val item = result.optJSONObject(i) ?: continue
                if (item.optString("type") != "video") continue
                list.add(
                    VideoCardItem(
                        aid = item.optLong("aid", 0),
                        bvid = item.optString("bvid", ""),
                        title = stripSearchTitle(item.optString("title", "")),
                        cover = normalizeCover(item.optString("pic", "")),
                        author = item.optString("author", ""),
                        playCount = item.optInt("play", 0),
                        danmakuCount = item.optInt("video_review", 0),
                        duration = parseSearchDuration(item.optString("duration", ""))
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun parseBangumiResults(result: Any?): List<VideoCardItem> {
        val list = mutableListOf<VideoCardItem>()
        if (result !is JSONArray) return list
        try {
            for (i in 0 until result.length()) {
                val item = result.optJSONObject(i) ?: continue
                val areas = item.opt("areas")
                val author = when (areas) {
                    is JSONArray -> (0 until areas.length()).mapNotNull { areas.optString(it) }.joinToString("/")
                    is String -> areas
                    else -> ""
                }
                list.add(
                    VideoCardItem(
                        aid = item.optLong("media_id", 0),
                        bvid = item.optString("season_id", ""),
                        title = stripSearchTitle(item.optString("title", "")),
                        cover = normalizeCover(item.optString("cover", "")),
                        author = author,
                        itemType = "media_bangumi",
                        statLabel = item.optString("index_show", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun parseUserResults(result: Any?): List<VideoCardItem> {
        val list = mutableListOf<VideoCardItem>()
        if (result !is JSONArray) return list
        try {
            for (i in 0 until result.length()) {
                val item = result.optJSONObject(i) ?: continue
                val fans = item.optInt("fans", 0)
                list.add(
                    VideoCardItem(
                        mid = item.optLong("mid", 0),
                        title = item.optString("uname", ""),
                        cover = normalizeCover(item.optString("upic", "")),
                        author = item.optString("usign", ""),
                        itemType = "bili_user",
                        statLabel = "${fans}粉丝"
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun parseLiveResults(result: Any?): List<VideoCardItem> {
        val list = mutableListOf<VideoCardItem>()
        if (result !is JSONObject) return list
        try {
            val rooms = result.optJSONArray("live_room") ?: return list
            for (i in 0 until rooms.length()) {
                val item = rooms.optJSONObject(i) ?: continue
                val online = item.optInt("online", 0)
                list.add(
                    VideoCardItem(
                        roomId = item.optLong("roomid", 0),
                        title = stripSearchTitle(item.optString("title", "")),
                        cover = normalizeCover(item.optString("user_cover", "")),
                        author = item.optString("uname", ""),
                        itemType = "live_room",
                        statLabel = "${online}观看"
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun stripSearchTitle(title: String): String =
        StringUtil.htmlToString(
            title.replace("<em class=\"keyword\">", "").replace("</em>", "")
        )

    private fun normalizeCover(url: String): String =
        if (url.startsWith("//")) "http:$url" else url

    private fun parseSearchDuration(str: String): Long {
        if (str.isEmpty()) return 0L
        return try {
            val parts = str.split(":")
            when (parts.size) {
                2 -> parts[0].toLong() * 60 + parts[1].toLong()
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
                else -> str.toLong()
            }
        } catch (_: Exception) { 0L }
    }

    companion object {
        private val SEARCH_TYPES = listOf("video", "media_bangumi", "bili_user", "live_room")
    }
}