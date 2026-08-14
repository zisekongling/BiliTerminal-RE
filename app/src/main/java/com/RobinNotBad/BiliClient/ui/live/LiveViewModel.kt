package com.RobinNotBad.BiliClient.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.RobinNotBad.BiliClient.network.api.VideoFeedApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class LiveRoomItem(
    val roomId: Long = 0,
    val uid: Long = 0,
    val title: String = "",
    val cover: String = "",
    val userName: String = "",
    val userFace: String = "",
    val online: Int = 0,
    val status: Int = 0,
    val areaName: String = "",
    val parentAreaName: String = ""
)

data class LiveListState(
    val rooms: List<LiveRoomItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val currentPage: Int = 1
)

@HiltViewModel
class LiveRecommendViewModel @Inject constructor(
    private val apiService: VideoFeedApiService
) : ViewModel() {

    private val _state = MutableStateFlow(LiveListState())
    val state: StateFlow<LiveListState> = _state.asStateFlow()

    init {
        loadRooms()
    }

    fun loadRooms(page: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    isLoading = page == 1 && it.rooms.isEmpty(),
                    isRefreshing = page == 1 && it.rooms.isNotEmpty()
                )
            }
            try {
                val response = apiService.getPopular(page)
                val list = parseLiveResponse(JSONObject(response.toString()))
                _state.update {
                    it.copy(
                        rooms = if (page == 1) list else it.rooms + list,
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        hasMore = list.size >= 20,
                        currentPage = page
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

    private fun parseLiveResponse(json: JSONObject): List<LiveRoomItem> {
        return emptyList()
    }

    fun refresh() = loadRooms(1)
    fun loadMore() = loadRooms(_state.value.currentPage + 1)
}