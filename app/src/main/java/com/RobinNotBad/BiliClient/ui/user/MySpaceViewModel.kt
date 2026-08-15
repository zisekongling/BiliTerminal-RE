package com.RobinNotBad.BiliClient.ui.user

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

data class MySpaceState(
    val userName: String = "未登录",
    val userAvatar: String = "",
    val userSign: String = "",
    val userId: Long = 0,
    val userLevel: Int = 0,
    val currentExp: Long = 0,
    val nextExp: Long = 0,
    val fansCount: Int = 0,
    val coinCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)

data class MenuAction(
    val id: String,
    val label: String,
    val iconRes: Int = 0,
    val targetClassName: String = ""
)

@HiltViewModel
class MySpaceViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(MySpaceState())
    val state: StateFlow<MySpaceState> = _state.asStateFlow()

    init {
        loadUser()
    }

    fun loadUser() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            try {
                _state.update {
                    it.copy(
                        userName = SharedPreferencesUtil.getString("user_name", "未登录"),
                        userAvatar = SharedPreferencesUtil.getString("user_face", ""),
                        userSign = SharedPreferencesUtil.getString("user_sign", ""),
                        userId = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0),
                        userLevel = SharedPreferencesUtil.getInt("user_level", 0),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun getMenuItems(): List<MenuAction> = listOf(
        MenuAction("myinfo", "个人信息", targetClassName = "com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity"),
        MenuAction("follow", "我的关注", targetClassName = "com.RobinNotBad.BiliClient.activity.user.FollowUsersActivity"),
        MenuAction("favorite", "收藏夹", targetClassName = "com.RobinNotBad.BiliClient.activity.user.favorite.FavoriteFolderListActivity"),
        MenuAction("history", "历史记录", targetClassName = "com.RobinNotBad.BiliClient.activity.user.HistoryActivity"),
        MenuAction("watchlater", "稍后再看", targetClassName = "com.RobinNotBad.BiliClient.activity.user.WatchLaterActivity"),
        MenuAction("bangumi", "追番", targetClassName = "com.RobinNotBad.BiliClient.activity.user.FollowingBangumisActivity"),
        MenuAction("login_record", "登录记录", targetClassName = "com.RobinNotBad.BiliClient.activity.user.LoginRecordActivity"),
        MenuAction("coin_log", "硬币记录", targetClassName = "com.RobinNotBad.BiliClient.activity.user.CoinLogActivity"),
        MenuAction("exp_log", "经验记录", targetClassName = "com.RobinNotBad.BiliClient.activity.user.ExpLogActivity"),
        MenuAction("vip", "大会员", targetClassName = "com.RobinNotBad.BiliClient.activity.user.VipActivity"),
        MenuAction("creative", "创作中心", targetClassName = "com.RobinNotBad.BiliClient.activity.user.CreativeCenterActivity"),
        MenuAction("edit_sign", "编辑签名", targetClassName = "com.RobinNotBad.BiliClient.activity.user.EditSignActivity"),
        MenuAction("edit_profile", "编辑资料", targetClassName = "com.RobinNotBad.BiliClient.activity.user.EditProfileActivity"),
        MenuAction("settings", "设置", targetClassName = "com.RobinNotBad.BiliClient.activity.settings.SettingMainActivity"),
        MenuAction("logout", "退出登录", targetClassName = "LOGOUT")
    )
}