package com.RobinNotBad.BiliClient.ui.mobile

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.audio.PlaylistActivity
import com.RobinNotBad.BiliClient.activity.settings.login.LoginActivity
import com.RobinNotBad.BiliClient.activity.user.CoinLogActivity
import com.RobinNotBad.BiliClient.activity.user.CreativeCenterActivity
import com.RobinNotBad.BiliClient.activity.user.EditSignActivity
import com.RobinNotBad.BiliClient.activity.user.ExpLogActivity
import com.RobinNotBad.BiliClient.activity.user.FollowUsersActivity
import com.RobinNotBad.BiliClient.activity.user.FollowingBangumisActivity
import com.RobinNotBad.BiliClient.activity.user.HistoryActivity
import com.RobinNotBad.BiliClient.activity.user.LoginRecordActivity
import com.RobinNotBad.BiliClient.activity.user.VipActivity
import com.RobinNotBad.BiliClient.activity.user.WatchLaterActivity
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

/**
 * 移动端"我的"页面Fragment
 */
class MobileMySpaceFragment : Fragment() {

    private lateinit var userAvatar: ImageView
    private lateinit var userName: TextView
    private lateinit var userFans: TextView
    private lateinit var userExp: TextView
    private lateinit var myInfo: MaterialCardView
    private lateinit var follow: MaterialCardView
    private lateinit var watchLater: MaterialCardView
    private lateinit var favorite: MaterialCardView
    private lateinit var bangumi: MaterialCardView
    private lateinit var myPlaylist: MaterialCardView
    private lateinit var history: MaterialCardView
    private lateinit var creative: MaterialCardView
    private lateinit var vip: MaterialCardView
    private lateinit var loginRecord: MaterialCardView
    private lateinit var coinLog: MaterialCardView
    private lateinit var expLog: MaterialCardView
    private lateinit var editSign: MaterialCardView
    private lateinit var logout: MaterialCardView
    private lateinit var credentialsCard: MaterialCardView
    private lateinit var cookieStatus: TextView
    private lateinit var accessTokenStatus: TextView
    private lateinit var midStatus: TextView
    private lateinit var csrfStatus: TextView
    private lateinit var refreshTokenStatus: TextView

    private var confirmLogout = false
    private var currentUserInfo: UserInfo? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_mobile_myspace, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userAvatar = view.findViewById(R.id.userAvatar)
        userName = view.findViewById(R.id.userName)
        userFans = view.findViewById(R.id.userFans)
        userExp = view.findViewById(R.id.userExp)

        myInfo = view.findViewById(R.id.myinfo)
        follow = view.findViewById(R.id.follow)
        watchLater = view.findViewById(R.id.watchlater)
        favorite = view.findViewById(R.id.favorite)
        bangumi = view.findViewById(R.id.bangumi)
        myPlaylist = view.findViewById(R.id.my_playlist)
        history = view.findViewById(R.id.history)
        creative = view.findViewById(R.id.creative)
        vip = view.findViewById(R.id.vip)
        loginRecord = view.findViewById(R.id.login_record)
        coinLog = view.findViewById(R.id.coin_log)
        expLog = view.findViewById(R.id.exp_log)
        editSign = view.findViewById(R.id.edit_sign)
        logout = view.findViewById(R.id.logout)
        credentialsCard = view.findViewById(R.id.credentialsCard)
        cookieStatus = view.findViewById(R.id.cookieStatus)
        accessTokenStatus = view.findViewById(R.id.accessTokenStatus)
        midStatus = view.findViewById(R.id.midStatus)
        csrfStatus = view.findViewById(R.id.csrfStatus)
        refreshTokenStatus = view.findViewById(R.id.refreshTokenStatus)

        updateCredentialDisplay()

        CenterThreadPool.run {
            try {
                val userInfo = UserInfoApi.getCurrentUserInfo()
                currentUserInfo = userInfo
                val userCoin = UserInfoApi.getCurrentUserCoin()
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    Glide.with(this@MobileMySpaceFragment).load(GlideUtil.url(userInfo.avatar))
                        .transition(GlideUtil.getTransitionOptions())
                        .placeholder(R.mipmap.akari).apply(RequestOptions.circleCropTransform())
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(userAvatar)
                    userName.text = userInfo.name
                    userFans.text = StringUtil.toWan(userInfo.fans.toLong()) + "粉丝 " + userCoin + "硬币"
                    userExp.text = "EXP:" + userInfo.current_exp + (if (userInfo.level >= 6) "" else "/" + userInfo.next_exp)

                    setupClickListeners(userInfo)
                }
            } catch (e: Exception) {
                // 未登录或加载失败，保持默认状态
            }
        }
    }

    private fun setupClickListeners(userInfo: UserInfo) {
        myInfo.setOnClickListener {
            startActivity(Intent(requireContext(), UserInfoActivity::class.java).apply {
                putExtra("mid", userInfo.mid)
            })
        }

        follow.setOnClickListener {
            startActivity(Intent(requireContext(), FollowUsersActivity::class.java).apply {
                putExtra("mid", userInfo.mid)
                putExtra("mode", 0)
            })
        }

        watchLater.setOnClickListener {
            startActivity(Intent(requireContext(), WatchLaterActivity::class.java))
        }

        favorite.setOnClickListener {
            startActivity(Intent(requireContext(), FavoriteFolderListActivity::class.java))
        }

        bangumi.setOnClickListener {
            startActivity(Intent(requireContext(), FollowingBangumisActivity::class.java))
        }

        myPlaylist.setOnClickListener {
            startActivity(Intent(requireContext(), PlaylistActivity::class.java))
        }

        history.setOnClickListener {
            startActivity(Intent(requireContext(), HistoryActivity::class.java))
        }

        creative.setOnClickListener {
            startActivity(Intent(requireContext(), CreativeCenterActivity::class.java))
        }
        if (!SharedPreferencesUtil.getBoolean("creative_enable", true)) {
            creative.visibility = View.GONE
        }

        vip.setOnClickListener {
            startActivity(Intent(requireContext(), VipActivity::class.java))
        }

        loginRecord.setOnClickListener {
            startActivity(Intent(requireContext(), LoginRecordActivity::class.java))
        }

        coinLog.setOnClickListener {
            startActivity(Intent(requireContext(), CoinLogActivity::class.java))
        }

        expLog.setOnClickListener {
            startActivity(Intent(requireContext(), ExpLogActivity::class.java))
        }

        editSign.setOnClickListener {
            startActivity(Intent(requireContext(), EditSignActivity::class.java).apply {
                putExtra("currentSign", userInfo.sign)
            })
        }

        logout.setOnClickListener {
            if (confirmLogout) {
                CenterThreadPool.run { UserInfoApi.exitLogin() }
                SharedPreferencesUtil.removeValue(SharedPreferencesUtil.cookies)
                SharedPreferencesUtil.removeValue(SharedPreferencesUtil.mid)
                SharedPreferencesUtil.removeValue(SharedPreferencesUtil.csrf)
                SharedPreferencesUtil.removeValue(SharedPreferencesUtil.refresh_token)
                SharedPreferencesUtil.removeValue(SharedPreferencesUtil.access_key)
                SharedPreferencesUtil.removeValue(SharedPreferencesUtil.cookie_refresh)
                activity?.runOnUiThread {
                    MsgUtil.showMsg("账号已退出")
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                }
            } else {
                MsgUtil.showMsg("再点一次退出登录！")
            }
            confirmLogout = !confirmLogout
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateCredentialDisplay() {
        val cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")
        val accessToken = SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, "")
        val mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
        val csrf = SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, "")
        val refreshToken = SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "")

        if (cookies.isNotEmpty()) {
            val hasSESSDATA = cookies.contains("SESSDATA=")
            cookieStatus.text = if (hasSESSDATA) "Cookie：已获取（含SESSDATA）" else "Cookie：已获取"
            cookieStatus.setTextColor(if (hasSESSDATA) 0xFF4CAF50.toInt() else 0xFFFFA726.toInt())
        } else {
            cookieStatus.text = "Cookie：未获取"
            cookieStatus.setTextColor(0xFFF44336.toInt())
        }

        if (accessToken.isNotEmpty()) {
            accessTokenStatus.text = "Access Token：已获取（${accessToken.take(8)}...）"
            accessTokenStatus.setTextColor(0xFF4CAF50.toInt())
        } else {
            accessTokenStatus.text = "Access Token：未获取（请使用TV端扫码登录）"
            accessTokenStatus.setTextColor(0xFFF44336.toInt())
        }

        midStatus.text = if (mid > 0) "UID：$mid" else "UID：未登录"

        if (csrf.isNotEmpty()) {
            csrfStatus.text = "CSRF Token：已获取"
            csrfStatus.setTextColor(0xFF4CAF50.toInt())
        } else {
            csrfStatus.text = "CSRF Token：未获取"
            csrfStatus.setTextColor(0xFFF44336.toInt())
        }

        if (refreshToken.isNotEmpty()) {
            refreshTokenStatus.text = "Refresh Token：已获取（${refreshToken.take(8)}...）"
            refreshTokenStatus.setTextColor(0xFF4CAF50.toInt())
        } else {
            refreshTokenStatus.text = "Refresh Token：未获取"
            refreshTokenStatus.setTextColor(0xFFF44336.toInt())
        }
    }
}