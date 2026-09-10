package com.RobinNotBad.BiliClient.activity.user

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.activity.settings.login.LoginActivity
import com.RobinNotBad.BiliClient.api.UserInfoApi
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.MySpaceConfig
import com.RobinNotBad.BiliClient.util.SettingsKeys
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

    // ==================== 功能入口（顺序与分区由「我的页面设置」决定） ====================

    private fun addMenuItems() {
        val layout = SharedPreferencesUtil.loadMySpaceLayout()
        val keys = ArrayList<String>()
        keys += layout.main
        // 「更多」只在实际有功能被移入时出现；「退出登录」永远在最后一位
        if (layout.more.isNotEmpty()) keys += MySpaceConfig.KEY_MORE_BUTTON
        keys += MySpaceConfig.KEY_LOGOUT

        for (key in keys) addMenuCell(key)
    }

    private fun addMenuCell(key: String) {
        // 创作中心受通用偏好开关控制，关闭时整行不渲染（设置页里仍可排序）
        if (key == "creative" && !SharedPreferencesUtil.getBoolean(SettingsKeys.CREATIVE_ENABLE, true)) return

        val cell = layoutInflater.inflate(R.layout.cell_myspace_item, menuContainer, false)
        val icon = cell.findViewById<ImageView>(R.id.item_icon)
        val label = cell.findViewById<TextView>(R.id.item_label)

        when (key) {
            MySpaceConfig.KEY_MORE_BUTTON -> {
                icon.setImageResource(R.drawable.icon_menu)
                label.text = "更多"
                cell.setOnClickListener { startActivity(Intent(this, MySpaceMoreActivity::class.java)) }
            }

            MySpaceConfig.KEY_LOGOUT -> {
                icon.setImageResource(R.drawable.icon_logout)
                label.text = "退出登录"
                label.setTextColor(0xFFF44336.toInt())
                cell.setOnClickListener { handleLogout() }
            }

            else -> {
                val item = MySpaceMenu.itemOf(key) ?: return
                icon.setImageResource(item.iconRes)
                label.text = item.label
                cell.setOnClickListener { MySpaceMenu.open(this, key, currentMid()) }
            }
        }
        menuContainer.addView(cell)
    }

    private fun currentMid(): Long = currentUserInfo?.mid ?: SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)

    private fun openMyInfo() {
        val mid = currentMid()
        if (mid > 0) {
            BiliTerminal.jumpToUser(this, mid)
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
                        .error(R.mipmap.akari).apply(RequestOptions.circleCropTransform())
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
