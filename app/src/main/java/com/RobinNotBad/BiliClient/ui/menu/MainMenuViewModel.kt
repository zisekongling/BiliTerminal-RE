package com.RobinNotBad.BiliClient.ui.menu

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

data class MainMenuState(
    val userName: String = "",
    val userFace: String = "",
    val userLevel: Int = 0,
    val isVip: Boolean = false,
    val dynamicBadge: Int = 0,
    val messageBadge: Int = 0,
    val isLoading: Boolean = true,
    val menuItems: List<MenuItemData> = buildDefaultMenuItems(),
    val currentTheme: String = "system"
)

data class MenuItemData(
    val id: String,
    val title: String,
    val iconName: String,
    val type: MenuItemType,
    val badge: Int = 0,
    val targetActivity: String = ""
)

enum class MenuItemType {
    VIDEO, LIVE, DYNAMIC, SEARCH, FAVORITE, HISTORY,
    DOWNLOAD, MESSAGE, USER, SETTINGS, DIVIDER, ABOUT
}

fun buildDefaultMenuItems(): List<MenuItemData> {
    return listOf(
        MenuItemData("recommend", "推荐", "icon_trending", MenuItemType.VIDEO,
            targetActivity = "video.RecommendActivity"),
        MenuItemData("short_video", "短视频", "icon_play", MenuItemType.VIDEO,
            targetActivity = "video.ShortVideoPlayerActivity"),
        MenuItemData("popular", "热门", "icon_fire", MenuItemType.VIDEO,
            targetActivity = "video.PopularActivity"),
        MenuItemData("ranking", "排行榜", "icon_ranking", MenuItemType.VIDEO,
            targetActivity = "video.RankingActivity"),
        MenuItemData("hotsearch", "热搜", "icon_ranking", MenuItemType.VIDEO,
            targetActivity = "video.HotSearchActivity"),
        MenuItemData("dynamic", "动态", "icon_dynamic", MenuItemType.DYNAMIC,
            targetActivity = "dynamic.DynamicActivity"),
        MenuItemData("live", "直播", "icon_live", MenuItemType.LIVE,
            targetActivity = "live.RecommendLiveActivity"),
        MenuItemData("search", "搜索", "icon_search", MenuItemType.SEARCH,
            targetActivity = "search.SearchActivity"),
        MenuItemData("favorite", "收藏", "icon_favorite", MenuItemType.FAVORITE,
            targetActivity = "user.favorite.FavoriteFolderListActivity"),
        MenuItemData("history", "历史", "icon_history", MenuItemType.HISTORY,
            targetActivity = "user.HistoryActivity"),
        MenuItemData("download", "缓存", "icon_download", MenuItemType.DOWNLOAD,
            targetActivity = "video.local.LocalListActivity"),
        MenuItemData("message", "消息", "icon_message", MenuItemType.MESSAGE,
            targetActivity = "message.MessageActivity"),
        MenuItemData("my", "我的", "icon_person", MenuItemType.USER,
            targetActivity = "user.MySpaceActivity"),
        MenuItemData("settings", "设置", "icon_settings", MenuItemType.SETTINGS,
            targetActivity = "settings.SettingMainActivity")
    )
}

@HiltViewModel
class MainMenuViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(MainMenuState())
    val state: StateFlow<MainMenuState> = _state.asStateFlow()

    init {
        loadUserData()
        loadBadgeCounts()
    }

    private fun loadUserData() {
        viewModelScope.launch(Dispatchers.IO) {
            val mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
            _state.update { it.copy(isLoading = true) }
            try {
                _state.update { current ->
                    current.copy(
                        userName = SharedPreferencesUtil.getString("user_name", "未登录"),
                        userFace = SharedPreferencesUtil.getString("user_face", ""),
                        userLevel = SharedPreferencesUtil.getInt("user_level", 0),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun loadBadgeCounts() {
        _state.update { current ->
            current.copy(
                dynamicBadge = SharedPreferencesUtil.getInt(SharedPreferencesUtil.DYNAMIC_UPDATE_NUM, 0),
                messageBadge = SharedPreferencesUtil.getInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, 0)
            )
        }
    }

    fun refreshBadges() {
        loadBadgeCounts()
    }

    fun getDynamicBadge(): Int = _state.value.dynamicBadge
    fun getMessageBadge(): Int = _state.value.messageBadge
}