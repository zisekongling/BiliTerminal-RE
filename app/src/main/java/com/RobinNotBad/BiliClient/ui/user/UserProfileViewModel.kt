package com.RobinNotBad.BiliClient.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.RobinNotBad.BiliClient.data.repository.UserRepository
import com.RobinNotBad.BiliClient.network.model.NetworkResult
import com.RobinNotBad.BiliClient.network.model.UserCardInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserProfileState(
    val userInfo: UserCardInfo? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isOwnProfile: Boolean = false
)

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(UserProfileState())
    val state: StateFlow<UserProfileState> = _state.asStateFlow()

    fun loadUserInfo(mid: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            when (val result = userRepository.getUserInfo(mid)) {
                is NetworkResult.Success -> {
                    _state.update { state ->
                        state.copy(
                            userInfo = result.data,
                            isLoading = false,
                            error = null
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _state.update { state ->
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