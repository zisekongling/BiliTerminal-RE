package com.RobinNotBad.BiliClient.activity.user

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.activity.audio.PlaylistActivity
import com.RobinNotBad.BiliClient.activity.settings.login.LoginActivity
import com.RobinNotBad.BiliClient.activity.user.favorite.FavoriteFolderListActivity
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity
import com.RobinNotBad.BiliClient.api.UserInfoApi
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.card.MaterialCardView

class MySpaceActivity : InstanceActivity() {

    private lateinit var userAvatar: ImageView
    private lateinit var userName: TextView
    private lateinit var userFans: TextView
    private lateinit var userExp: TextView
    private lateinit var myInfo: MaterialCardView
    private lateinit var menuContainer: LinearLayout

    private var confirmLogout = false
    private var currentUserInfo: UserInfo? = null

    /** 功能入口数据模型：图标 + 文字 + 点击行为，与用户信息 API 解耦。 */
    private class MySpaceItem(
        val iconRes: Int,
        val label: String,
        val isLogout: Boolean = false,
        val onClick: () -> Unit
    )

    @SuppressLint("SetTextI18n", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_myspace) { _, _ ->
            Log.e("debug", "进入个人页")

            userAvatar = findViewById(R.id.userAvatar)
            userName = findViewById(R.id.userName)
            userFans = findViewById(R.id.userFans)
            userExp = findViewById(R.id.userExp)
            myInfo = findViewById(R.id.myinfo)
            menuContainer = findViewById(R.id.menuContainer)

            // 功能入口与用户信息 API 解耦：先渲染入口，再异步加载用户信息
            addMenuItems()
            loadUserInfo()

            val scrollView = findViewById<View>(R.id.scrollView)
            scrollView.isFocusable = true
            scrollView.isFocusableInTouchMode = true
            scrollView.requestFocus()
        }
    }

    // ==================== 功能入口（数据驱动，点击即时生效） ====================

    private fun addMenuItems() {
        for (item in buildMenuItems()) {
            val cell = layoutInflater.inflate(R.layout.cell_myspace_item, menuContainer, false)
            cell.findViewById<ImageView>(R.id.item_icon).setImageResource(item.iconRes)
            val label = cell.findViewById<TextView>(R.id.item_label)
            label.text = item.label
            if (item.isLogout) label.setTextColor(0xFFF44336.toInt())
            cell.setOnClickListener { item.onClick() }
            menuContainer.addView(cell)
        }
    }

    private fun buildMenuItems(): List<MySpaceItem> {
        val items = ArrayList<MySpaceItem>()
        items += MySpaceItem(R.drawable.icon_info, "个人信息") { openMyInfo() }
        items += MySpaceItem(R.drawable.icon_followings, "关注") {
            startActivity(Intent(this, FollowUsersActivity::class.java)
                .putExtra("mid", currentMid())
                .putExtra("mode", 0))
        }
        items += MySpaceItem(R.drawable.icon_play_12, "稍后再看") { startActivity(Intent(this, WatchLaterActivity::class.java)) }
        items += MySpaceItem(R.drawable.icon_star, "收藏") { startActivity(Intent(this, FavoriteFolderListActivity::class.java)) }
        items += MySpaceItem(R.drawable.icon_bangumi, "追番列表") { startActivity(Intent(this, FollowingBangumisActivity::class.java)) }
        items += MySpaceItem(R.drawable.icon_player, "我的歌单") { startActivity(Intent(this, PlaylistActivity::class.java)) }
        items += MySpaceItem(R.drawable.icon_history, "历史记录") { startActivity(Intent(this, HistoryActivity::class.java)) }
        if (SharedPreferencesUtil.getBoolean("creative_enable", true)) {
            items += MySpaceItem(R.drawable.icon_creative_center, "创作中心") { startActivity(Intent(this, CreativeCenterActivity::class.java)) }
        }
        items += MySpaceItem(R.drawable.icon_info, "大会员") { startActivity(Intent(this, VipActivity::class.java)) }
        items += MySpaceItem(R.drawable.icon_time, "登录记录") { startActivity(Intent(this, LoginRecordActivity::class.java)) }
        items += MySpaceItem(R.drawable.icon_info, "硬币变化记录") { startActivity(Intent(this, CoinLogActivity::class.java)) }
        items += MySpaceItem(R.drawable.icon_info, "经验变化记录") { startActivity(Intent(this, ExpLogActivity::class.java)) }
        items += MySpaceItem(R.drawable.icon_info, "编辑个人资料") { startActivity(Intent(this, EditProfileActivity::class.java)) }
        items += MySpaceItem(R.drawable.icon_info, "修改个人描述") {
            val intent = Intent(this, EditSignActivity::class.java)
            intent.putExtra("currentSign", currentUserInfo?.sign ?: "")
            startActivity(intent)
        }
        items += MySpaceItem(R.drawable.icon_logout, "退出登录", isLogout = true) { handleLogout() }
        return items
    }

    private fun currentMid(): Long = currentUserInfo?.mid ?: SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)

    private fun openMyInfo() {
        val mid = currentMid()
        if (mid > 0) {
            startActivity(Intent(this, UserInfoActivity::class.java).putExtra("mid", mid))
        } else {
            jumpToLogin()
        }
    }

    private fun jumpToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
    }

    private fun handleLogout() {
        if (confirmLogout) {
            CenterThreadPool.run { UserInfoApi.exitLogin() }
            SharedPreferencesUtil.removeValue(SharedPreferencesUtil.cookies)
            SharedPreferencesUtil.removeValue(SharedPreferencesUtil.mid)
            SharedPreferencesUtil.removeValue(SharedPreferencesUtil.csrf)
            SharedPreferencesUtil.removeValue(SharedPreferencesUtil.refresh_token)
            SharedPreferencesUtil.removeValue(SharedPreferencesUtil.access_key)
            SharedPreferencesUtil.removeValue(SharedPreferencesUtil.cookie_refresh)
            MsgUtil.showMsg("账号已退出")
            jumpToLogin()
            finish()
        } else {
            MsgUtil.showMsg("再点一次退出登录！")
            confirmLogout = !confirmLogout
        }
    }

    // ==================== 用户信息（异步加载，失败不影响功能入口） ====================

    private fun loadUserInfo() {
        val mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
        if (mid == 0L) {
            // 未登录引导
            userName.text = "点击登录"
            userFans.text = ""
            userExp.text = ""
            myInfo.setOnClickListener { jumpToLogin() }
            return
        }
        CenterThreadPool.run {
            try {
                val userInfo = UserInfoApi.getCurrentUserInfo()
                val userCoin = UserInfoApi.getCurrentUserCoin()
                currentUserInfo = userInfo
                if (!this.isDestroyed) runOnUiThread {
                    Glide.with(this@MySpaceActivity).load(GlideUtil.url(userInfo.avatar))
                        .transition(GlideUtil.getTransitionOptions())
                        .placeholder(R.mipmap.akari).apply(RequestOptions.circleCropTransform())
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(userAvatar)
                    userName.text = userInfo.name
                    userFans.text = StringUtil.toWan(userInfo.fans.toLong()) + "粉丝 " + userCoin + "硬币"
                    userExp.text = "EXP:" + userInfo.current_exp + (if (userInfo.level >= 6) "" else "/" + userInfo.next_exp)
                    myInfo.setOnClickListener { openMyInfo() }
                }
            } catch (e: Exception) {
                report(e)
            }
        }
    }
}
