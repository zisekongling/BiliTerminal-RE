package com.RobinNotBad.BiliClient.ui.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.RobinNotBad.BiliClient.data.repository.VideoRepository
import com.RobinNotBad.BiliClient.network.model.NetworkResult
import com.RobinNotBad.BiliClient.network.model.PopularVideoItem
import com.RobinNotBad.BiliClient.network.model.VideoDetailInfo
import com.RobinNotBad.BiliClient.ui.base.LoadableListState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VideoListViewModel @Inject constructor(
    private val videoRepository: VideoRepository
) : ViewModel() {

    private val _popularState = MutableStateFlow(LoadableListState<PopularVideoItem>())
    val popularState: StateFlow<LoadableListState<PopularVideoItem>> = _popularState.asStateFlow()

    private val _recommendState = MutableStateFlow(LoadableListState<PopularVideoItem>())
    val recommendState: StateFlow<LoadableListState<PopularVideoItem>> = _recommendState.asStateFlow()

    init {
        loadPopularVideos()
    }

    fun loadPopularVideos(isRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _popularState.update { it.copy(isLoading = true) }
            when (val result = videoRepository.getPopularVideos()) {
                is NetworkResult.Success -> {
                    _popularState.update { state ->
                        state.copy(
                            items = result.data,
                            isLoading = false,
                            error = null,
                            isRefreshing = false
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _popularState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = result.message,
                            isRefreshing = false
                        )
                    }
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    fun loadRecommendVideos(isRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _recommendState.update { it.copy(isLoading = _recommendState.value.items.isEmpty()) }
            when (val result = videoRepository.getRecommendVideos()) {
                is NetworkResult.Success -> {
                    _recommendState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = null,
                            isRefreshing = false
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _recommendState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = result.message,
                            isRefreshing = false
                        )
                    }
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    fun refreshPopular() = loadPopularVideos(true)
    fun refreshRecommend() = loadRecommendVideos(true)
}

@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    private val videoRepository: VideoRepository
) : ViewModel() {

    private val _detailState = MutableStateFlow<VideoDetailState>(VideoDetailState())
    val detailState: StateFlow<VideoDetailState> = _detailState.asStateFlow()

    fun loadVideoDetail(aid: Long? = null, bvid: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _detailState.update { it.copy(isLoading = true) }
            when (val result = videoRepository.getVideoDetail(aid, bvid)) {
                is NetworkResult.Success -> {
                    _detailState.update { state ->
                        state.copy(
                            videoInfo = result.data,
                            isLoading = false,
                            error = null
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _detailState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }
                is NetworkResult.Loading -> {}
            }
        }
    }
}

data class VideoDetailState(
    val videoInfo: VideoDetailInfo? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLiked: Boolean = false,
    val isCoinGiven: Boolean = false,
    val isFavored: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val videoRepository: VideoRepository
) : ViewModel() {

    private val _searchState = MutableStateFlow(LoadableListState<PopularVideoItem>())
    val searchState: StateFlow<LoadableListState<PopularVideoItem>> = _searchState.asStateFlow()

    fun search(keyword: String, page: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            _searchState.update { it.copy(isLoading = page == 1) }
            when (val result = videoRepository.getPopularVideos()) {
                is NetworkResult.Success -> {
                    _searchState.update { state ->
                        state.copy(
                            items = if (page == 1) result.data else state.items + result.data,
                            isLoading = false,
                            error = null
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _searchState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    fun clearSearch() {
        _searchState.update { LoadableListState() }
    }
}