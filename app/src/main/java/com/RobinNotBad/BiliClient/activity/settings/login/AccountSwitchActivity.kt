package com.RobinNotBad.BiliClient.activity.settings.login

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.SplashActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.api.UserInfoApi
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.util.AccountManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.card.MaterialCardView

class AccountSwitchActivity : BaseActivity() {

    private lateinit var accountList: LinearLayout

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_switch)

        findViewById<View>(R.id.pageName).setOnClickListener { finish() }

        accountList = findViewById(R.id.accountList)

        findViewById<View>(R.id.scrollView).requestFocus()

        refreshAccountList()
    }

    override fun onResume() {
        super.onResume()
        refreshAccountList()
    }

    private fun refreshAccountList() {
        accountList.removeAllViews()
        val accounts = AccountManager.getAccounts()

        if (accounts.isEmpty()) {
            addEmptyHint()
            return
        }

        for (account in accounts) {
            accountList.addView(createAccountCard(account))
        }

        addNewAccountCard()
    }

    private fun addEmptyHint() {
        val card = MaterialCardView(this)
        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        card.layoutParams = cardParams

        val text = TextView(this)
        text.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        text.gravity = Gravity.CENTER
        text.setPadding(32, 48, 32, 48)
        text.text = "暂无已保存的账号\n登录后会自动保存账号凭证"
        text.textSize = 13f
        card.addView(text)

        accountList.addView(card)
        addNewAccountCard()
    }

    private fun createAccountCard(account: AccountManager.AccountInfo): View {
        val view = LayoutInflater.from(this).inflate(R.layout.item_account, null)

        val isCurrent = AccountManager.isCurrentAccount(account.mid)

        val avatarView = view.findViewById<ImageView>(R.id.account_avatar)
        val nameView = view.findViewById<TextView>(R.id.account_name)
        val uidView = view.findViewById<TextView>(R.id.account_uid)

        uidView.text = "UID: " + account.mid

        val savedName = account.name
        val savedAvatar = account.avatar

        if (savedAvatar != null && savedAvatar.isNotEmpty() && savedName != null && savedName.isNotEmpty()) {
            nameView.text = savedName + (if (isCurrent) "（当前）" else "")
            Glide.with(this)
                .load(savedAvatar)
                .transition(GlideUtil.getTransitionOptions())
                .placeholder(R.mipmap.akari)
                .apply(RequestOptions.circleCropTransform())
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(avatarView)
        } else {
            nameView.text = "UID:" + account.mid + (if (isCurrent) "（当前）" else "")

            CenterThreadPool.run {
                try {
                    val userInfo = UserInfoApi.getUserInfo(account.mid)
                    if (userInfo != null) {
                        runOnUiThread {
                            nameView.text = userInfo.name + (if (isCurrent) "（当前）" else "")
                            if (userInfo.avatar != null && userInfo.avatar.isNotEmpty()) {
                                Glide.with(this)
                                    .load(userInfo.avatar)
                                    .transition(GlideUtil.getTransitionOptions())
                                    .placeholder(R.mipmap.akari)
                                    .apply(RequestOptions.circleCropTransform())
                                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                                    .into(avatarView)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val card = view as MaterialCardView
        card.setOnClickListener {
            if (isCurrent) {
                MsgUtil.showMsg("这是当前登录的账号")
            } else {
                AccountManager.switchToAccount(account)
                val displayName = if (savedName != null && savedName.isNotEmpty()) savedName else "UID:" + account.mid
                MsgUtil.showMsg("已切换至 $displayName")
                val intent = Intent(this@AccountSwitchActivity, SplashActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
        }

        card.setOnLongClickListener {
            if (accountsCanBeRemoved()) {
                val displayName = if (savedName != null && savedName.isNotEmpty()) savedName else "UID:" + account.mid
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("删除账号")
                    .setMessage("确定要删除账号 $displayName 吗？\n删除后需重新登录才能恢复。")
                    .setPositiveButton("删除") { _, _ ->
                        AccountManager.removeAccount(account.mid)
                        MsgUtil.showMsg("已删除账号")
                        refreshAccountList()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            true
        }

        return view
    }

    private fun accountsCanBeRemoved(): Boolean {
        return AccountManager.getAccounts().size > 1
                || SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L
    }

    private fun addNewAccountCard() {
        val card = MaterialCardView(this)
        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        card.layoutParams = cardParams

        val text = TextView(this)
        text.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (35 * resources.displayMetrics.density).toInt()
        )
        text.setPadding(16, 0, 16, 0)
        text.gravity = Gravity.CENTER_VERTICAL
        text.text = "添加新账号"
        text.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
        text.textSize = 13f
        text.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.arrow_forward, 0)
        card.addView(text)

        card.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        accountList.addView(card)
    }
}