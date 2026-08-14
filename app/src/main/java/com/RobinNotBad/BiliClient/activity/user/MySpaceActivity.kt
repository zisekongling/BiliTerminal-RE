package com.RobinNotBad.BiliClient.activity.user

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
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
            follow = findViewById(R.id.follow)
            watchLater = findViewById(R.id.watchlater)
            favorite = findViewById(R.id.favorite)
            bangumi = findViewById(R.id.bangumi)
            myPlaylist = findViewById(R.id.my_playlist)
            history = findViewById(R.id.history)
            creative = findViewById(R.id.creative)
            vip = findViewById(R.id.vip)
            loginRecord = findViewById(R.id.login_record)
            coinLog = findViewById(R.id.coin_log)
            expLog = findViewById(R.id.exp_log)
            editSign = findViewById(R.id.edit_sign)
            logout = findViewById(R.id.logout)
            credentialsCard = findViewById(R.id.credentialsCard)
            cookieStatus = findViewById(R.id.cookieStatus)
            accessTokenStatus = findViewById(R.id.accessTokenStatus)
            midStatus = findViewById(R.id.midStatus)
            csrfStatus = findViewById(R.id.csrfStatus)
            refreshTokenStatus = findViewById(R.id.refreshTokenStatus)

            updateCredentialDisplay()


            CenterThreadPool.run {
                try {
                    val userInfo = UserInfoApi.getCurrentUserInfo()
                    currentUserInfo = userInfo
                    val userCoin = UserInfoApi.getCurrentUserCoin()
                    if (!this.isDestroyed) runOnUiThread {
                        Glide.with(this@MySpaceActivity).load(GlideUtil.url(userInfo.avatar))
                            .transition(GlideUtil.getTransitionOptions())
                            .placeholder(R.mipmap.akari).apply(RequestOptions.circleCropTransform())
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .into(userAvatar)
                        userName.text = userInfo.name
                        userFans.text = StringUtil.toWan(userInfo.fans.toLong()) + "粉丝 " + userCoin + "硬币"
                        userExp.text = "EXP:" + userInfo.current_exp + (if (userInfo.level >= 6) "" else "/" + userInfo.next_exp)

                        myInfo.setOnClickListener {
                            val intent = Intent()
                            intent.setClass(this@MySpaceActivity, UserInfoActivity::class.java)
                            intent.putExtra("mid", userInfo.mid)
                            startActivity(intent)
                        }

                        follow.setOnClickListener {
                            val intent = Intent()
                            intent.setClass(this@MySpaceActivity, FollowUsersActivity::class.java)
                            intent.putExtra("mid", userInfo.mid)
                            intent.putExtra("mode", 0)
                            startActivity(intent)
                        }

                        watchLater.setOnClickListener {
                            val intent = Intent()
                            intent.setClass(this@MySpaceActivity, WatchLaterActivity::class.java)
                            startActivity(intent)
                        }

                        favorite.setOnClickListener {
                            val intent = Intent()
                            intent.setClass(this@MySpaceActivity, FavoriteFolderListActivity::class.java)
                            startActivity(intent)
                        }

                        bangumi.setOnClickListener {
                            val intent = Intent()
                            intent.setClass(this@MySpaceActivity, FollowingBangumisActivity::class.java)
                            startActivity(intent)
                        }

                        myPlaylist.setOnClickListener {
                            val intent = Intent()
                            intent.setClass(this@MySpaceActivity, PlaylistActivity::class.java)
                            startActivity(intent)
                        }

                        history.setOnClickListener {
                            val intent = Intent()
                            intent.setClass(this@MySpaceActivity, HistoryActivity::class.java)
                            startActivity(intent)
                        }

                        creative.setOnClickListener {
                            val intent = Intent()
                            intent.setClass(this@MySpaceActivity, CreativeCenterActivity::class.java)
                            startActivity(intent)
                        }
                        if (!SharedPreferencesUtil.getBoolean("creative_enable", true))
                            creative.visibility = View.GONE

                        vip.setOnClickListener {
                            val intent = Intent()
                            intent.setClass(this@MySpaceActivity, VipActivity::class.java)
                            startActivity(intent)
                        }

                        loginRecord.setOnClickListener {
                            val intent = Intent()
                            intent.setClass(this@MySpaceActivity, LoginRecordActivity::class.java)
                            startActivity(intent)
                        }

                        coinLog.setOnClickListener {
                            val intent = Intent()
                            intent.setClass(this@MySpaceActivity, CoinLogActivity::class.java)
                            startActivity(intent)
                        }

                        expLog.setOnClickListener {
                            val intent = Intent()
                            intent.setClass(this@MySpaceActivity, ExpLogActivity::class.java)
                            startActivity(intent)
                        }

                        editSign.setOnClickListener {
                            val intent = Intent()
                            intent.setClass(this@MySpaceActivity, EditSignActivity::class.java)
                            intent.putExtra("currentSign", userInfo.sign)
                            startActivity(intent)
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
                                MsgUtil.showMsg("账号已退出")
                                val intent = Intent(this@MySpaceActivity, LoginActivity::class.java)
                                startActivity(intent)
                                finish()
                            } else MsgUtil.showMsg("再点一次退出登录！")
                            confirmLogout = !confirmLogout
                        }

                        val scrollView = findViewById<View>(R.id.scrollView)
                        scrollView.isFocusable = true
                        scrollView.isFocusableInTouchMode = true
                        scrollView.requestFocus()
                    }
                } catch (e: Exception) {
                    report(e)
                }
            }
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