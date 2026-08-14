package com.RobinNotBad.BiliClient.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.RobinNotBad.BiliClient.network.model.NetworkResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class BaseViewModel<UiState>(initialState: UiState) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    protected fun updateState(transform: UiState.() -> UiState) {
        _uiState.update(transform)
    }

    protected fun launch(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        block: suspend () -> Unit
    ) {
        viewModelScope.launch(dispatcher) {
            block()
        }
    }

    protected fun launchOnMain(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.Main) {
            block()
        }
    }
}

data class LoadableListState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val currentPage: Int = 1
)

data class DetailPageState<T>(
    val data: T? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

inline fun <T> networkResultToState(
    state: LoadableListState<T>,
    result: NetworkResult<List<T>>,
    isRefresh: Boolean = false
): LoadableListState<T> {
    return when (result) {
        is NetworkResult.Success -> state.copy(
            items = if (isRefresh) result.data else state.items + result.data,
            isLoading = false,
            isLoadingMore = false,
            isRefreshing = false,
            error = null,
            hasMore = result.data.isNotEmpty(),
            currentPage = if (isRefresh) 1 else state.currentPage + 1
        )
        is NetworkResult.Error -> state.copy(
            isLoading = false,
            isLoadingMore = false,
            isRefreshing = false,
            error = result.message
        )
        is NetworkResult.Loading -> state.copy(
            isLoading = !isRefresh && state.items.isEmpty(),
            isRefreshing = isRefresh && state.items.isNotEmpty(),
            isLoadingMore = !isRefresh && state.items.isNotEmpty()
        )
    }
}