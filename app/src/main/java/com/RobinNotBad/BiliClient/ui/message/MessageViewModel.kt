package com.RobinNotBad.BiliClient.ui.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MessageSessionItem(
    val talkerUid: Long = 0,
    val talkerName: String = "",
    val talkerFace: String = "",
    val lastMessage: String = "",
    val unreadCount: Int = 0,
    val lastTime: Long = 0
)

data class MessageBadgeState(
    val replyUnread: Int = 0,
    val likeUnread: Int = 0,
    val atUnread: Int = 0,
    val systemUnread: Int = 0,
    val totalUnread: Int = 0
)

data class MessageUiState(
    val badge: MessageBadgeState = MessageBadgeState(),
    val sessions: List<MessageSessionItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class MessageViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(MessageUiState())
    val state: StateFlow<MessageUiState> = _state.asStateFlow()

    init {
        loadMessages()
    }

    fun loadMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            try {
                _state.update {
                    it.copy(
                        badge = MessageBadgeState(
                            replyUnread = SharedPreferencesUtil.getInt("message_reply_unread", 0),
                            likeUnread = SharedPreferencesUtil.getInt("message_like_unread", 0),
                            atUnread = SharedPreferencesUtil.getInt("message_at_unread", 0),
                            totalUnread = SharedPreferencesUtil.getInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, 0)
                        ),
                        isLoading = false
                    )
                }
                SharedPreferencesUtil.putInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, 0)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun refresh() = loadMessages()
}