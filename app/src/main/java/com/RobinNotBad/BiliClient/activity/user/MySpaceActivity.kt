package com.RobinNotBad.BiliClient.activity.user

import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.util.UiText

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
import com.RobinNotBad.BiliClient.util.AccountManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.MySpaceConfig
import com.RobinNotBad.BiliClient.util.MySpaceLoadErrorText
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

    /** 账号信息是否正在加载。只在主线程读写：失败后卡片可点重试，这里挡住重复请求。 */
    private var userInfoLoading = false

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
                label.text = UiText.of("更多")
                cell.setOnClickListener { startActivity(Intent(this, MySpaceMoreActivity::class.java)) }
            }

            MySpaceConfig.KEY_LOGOUT -> {
                icon.setImageResource(R.drawable.icon_logout)
                label.text = UiText.of("退出登录")
                label.setTextColor(ColorScheme.ERROR) // 危险操作色随主题（与弹窗 colorError 一致），原写死 Material 红
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
            // 旧实现是「丢进线程池请求服务端注销」后立刻清本地 Cookie，两者构成竞态：
            // exitLogin 在后台线程读 csrf 时本地可能已经清空 → csrf 读成空串 → 服务端会话（含 refresh_token）压根没被注销。
            // 所以这里把「请求服务端注销 → 清本地登录态」放进同一个后台任务，保证先后顺序。
            MsgUtil.showMsg(UiText.of("正在退出登录…"))
            CenterThreadPool.run {
                val serverLogoutOk = try {
                    UserInfoApi.exitLogin()
                } catch (e: Exception) {
                    false
                }
                // 不论服务端成功与否都要清本地登录态：否则网络故障会把用户永久卡在「已登录」状态里退不出去。
                // 服务端失败的情况靠下面的提示告知用户，不阻塞本地退出。
                //
                // 走统一入口而不是逐个 removeValue：原实现只删了 5 个键，既不升账号代际
                // （在飞的旧请求回来还能把 Cookie 写回去，退完登录又"自己登上了"），
                // 也不清按账号走的本地缓存（大会员状态、未读红点会串到下一个账号）。
                AccountManager.clearCurrentLogin()
                runOnUiThread {
                    // 服务端注销失败时明确提示：用户/排查者才知道服务端会话可能仍然有效
                    MsgUtil.showMsg(if (serverLogoutOk) UiText.of("账号已退出") else UiText.of("服务端注销失败，已清除本地登录状态"))
                    jumpToLogin()
                    finish()
                }
            }
        } else {
            MsgUtil.showMsg(UiText.of("再点一次退出登录！"))
            confirmLogout = !confirmLogout
        }
    }

    // ==================== 用户信息（异步加载，失败不影响功能入口） ====================

    private fun loadUserInfo() {
        val mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
        if (mid == 0L) {
            // 未登录引导
            userInfoLoading = false
            userName.text = UiText.of("点击登录")
            userFans.text = ""
            userExp.text = ""
            myInfo.setOnClickListener { jumpToLogin() }
            return
        }
        if (userInfoLoading) return
        userInfoLoading = true
        renderUserInfoLoading()
        CenterThreadPool.run {
            try {
                val userInfo = UserInfoApi.getCurrentUserInfo()
                val userCoin = UserInfoApi.getCurrentUserCoin()
                if (userInfo.mid <= 0) {
                    // 接口没给账号数据（未登录 / code≠0 / 风控降级）时 UserInfoApi 返回 mid=0 的占位对象、
                    // 并不抛异常。以前这种响应被当成成功画了上去：昵称显示「加载失败」、点卡片还跳登录页，
                    // 用户看到的仍是一张没内容可点的卡片。现在和异常一样落到失败态，给出重试入口。
                    renderUserInfoFailure(null)
                } else {
                    currentUserInfo = userInfo
                    renderUserInfo(userInfo, userCoin)
                }
            } catch (e: Exception) {
                report(e)
                renderUserInfoFailure(e)
            }
        }
    }

    /** 加载态：卡片回到布局默认的「加载中...」，顺手摘掉点击，避免连点叠出多个请求。 */
    private fun renderUserInfoLoading() {
        userName.text = getString(R.string.text_activitymyspace_01)
        userFans.text = ""
        userExp.text = ""
        myInfo.setOnClickListener(null)
    }

    /**
     * 成功态：头像 + 昵称 + 粉丝/硬币 + 等级。
     *
     * **判活必须和用控件贴在同一个主线程 Runnable 里**（崩溃 #367，26.11.17-beta.2 / 8.1 /
     * alps Q5Plus / main 线程）。原来这里在后台线程先 `if (isDestroyed) return`，再
     * `runOnUiThread` 投递；而 runOnUiThread 只是把 Runnable 塞进主线程队列 ——
     * 投递到执行之间用户按返回键、系统回收页面都会走完 onDestroy。等到 Runnable 真的跑起来，
     * `Glide.with(this@MySpaceActivity)` 里 `RequestManagerRetriever.assertNotDestroyed`
     * 看到页面已销毁，直接抛 `IllegalArgumentException: You cannot start a load for a
     * destroyed activity`（Glide.java:574）。
     *
     * 放进 Runnable 就堵死了这条缝：onDestroy 也在主线程，两者之间插不进任何东西；
     * 这与 AccountSwitchActivity.loadAvatar（Activity 已销毁直接放弃）是同一套写法。
     * 这里判的 [isDestroyed] 是 BaseActivity 的重写（销毁**或**正在 finish 都算，
     * 见 BaseActivity.kt:833），恰好就是 Glide 那行 assertNotDestroyed 读的条件，所以够用。
     */
    private fun renderUserInfo(userInfo: UserInfo, userCoin: Int) {
        runOnUiThread {
            if (isDestroyed) return@runOnUiThread
            userInfoLoading = false
            Glide.with(this@MySpaceActivity).load(GlideUtil.url(userInfo.avatar))
                .transition(GlideUtil.getTransitionOptions())
                .apply(GlideUtil.withPlaceholder(userAvatar.context, R.mipmap.akari)).apply(RequestOptions.circleCropTransform())
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(userAvatar)
            userName.text = userInfo.name
            userFans.text = StringUtil.toWan(userInfo.fans.toLong()) + UiText.of("粉丝 ") + userCoin + UiText.of("硬币")
            userExp.text = "EXP:" + userInfo.current_exp + (if (userInfo.level >= 6) "" else "/" + userInfo.next_exp)
            myInfo.setOnClickListener { openMyInfo() }
        }
    }

    /**
     * 失败态（反馈 #163）：结束加载，把可读原因写在卡片上，卡片本身变成重试入口。
     *
     * 原来这里只有 `report(e)`——那个只是瞬时提示 + 打日志，卡片会一直停在「加载中...」，
     * 用户看到的就是「点我的显示正在加载」。
     *
     * @param e 加载异常；`null` 表示接口没给账号数据（见 [MySpaceLoadErrorText.reasonOf]）
     */
    private fun renderUserInfoFailure(e: Exception?) {
        // 原来这里先 `if (isDestroyed) return` 再投递——与 [renderUserInfo] 一样是
        // 「判断在后台线程、使用在主线程」的错位（崩溃 #367 的同一处病根），这里一并挪进
        // 下面的 runOnUiThread。纯计算（reasonOf / textOf / tapActionOf / titleOf）不碰控件，
        // 留在主线程外没有影响，先算好再投递。
        val reason = MySpaceLoadErrorText.reasonOf(e)
        val reasonText = MySpaceLoadErrorText.textOf(reason)
        // 掉登录（NO_DATA）时重试永远成功不了，直接给登录页；其余原因给重试。文案跟着动作走，
        // 免得卡片写着「重试」却跳登录页。
        val tapAction = MySpaceLoadErrorText.tapActionOf(reason)
        val titleText = MySpaceLoadErrorText.titleOf(tapAction)
        runOnUiThread {
            if (isDestroyed) return@runOnUiThread
            userInfoLoading = false
            userName.text = UiText.of(titleText)
            userFans.text = UiText.of(reasonText)
            userExp.text = ""
            myInfo.setOnClickListener {
                when (tapAction) {
                    MySpaceLoadErrorText.TapAction.LOGIN -> jumpToLogin()
                    MySpaceLoadErrorText.TapAction.RETRY -> loadUserInfo()
                }
            }
        }
    }
}