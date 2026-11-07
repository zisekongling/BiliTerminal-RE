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
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.card.MaterialCardView

class AccountSwitchActivity : BaseActivity() {

    private lateinit var accountList: LinearLayout

    /**
     * 本页共用的 Glide 请求管理器。
     *
     * 不再每张卡片各写一次 `Glide.with(this)`：那个调用在页面已销毁 / 正在 finish 时会抛
     * `IllegalArgumentException: You cannot start a load for a destroyed activity`，
     * 而本页的头像加载全部发生在「网络回来之后」，正好落在这个窗口里。
     * 存成字段还能让 [clearAvatarRequests] 有东西可清。
     */
    private var requestManager: RequestManager? = null

    /** 本页加载过头像的 ImageView，用于在重建列表 / 销毁页面时逐个 clear。 */
    private val avatarViews = ArrayList<ImageView>()

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

    /**
     * 取消本页所有头像请求并断开引用。
     *
     * 调用点两处：[refreshAccountList] 重建列表之前、[onDestroy] 收尾时。
     *
     * 为什么必须显式清：Glide 只在 **Activity 销毁** 时清理「已经登记到它的 RequestManager 上」的请求，
     * 而 `accountList.removeAllViews()` 只是把 View 从父容器摘下来——还在加载中的 ViewTarget
     * 依然吊着那个 ImageView。本页每次 `onResume` 都会重建一遍列表（还有删除账号后的一次），
     * 不清理就会一轮轮往上堆，低性能设备上先表现为卡顿、再表现为 OOM。
     */
    private fun clearAvatarRequests() {
        val manager = requestManager ?: return
        for (avatarView in avatarViews) {
            try {
                manager.clear(avatarView)
            } catch (e: Exception) {
                Logu.e("AccountSwitchActivity", "取消头像请求失败：${e.message}")
            }
        }
        avatarViews.clear()
    }

    /**
     * 统一的头像加载入口。
     *
     * 两道防线：
     * 1. 页面已销毁 / 正在 finish 时直接放弃——这是本次崩溃的直接修复点；
     * 2. 请求走本页共用的 [requestManager]，而不是每次 `Glide.with(this)`，
     *    这样 [clearAvatarRequests] 才清得掉。
     */
    private fun loadAvatar(avatarView: ImageView, url: String) {
        // 本方法只在主线程被调用（onCreate/onResume 的同步路径与 runOnUiThread 内），
        // 而 onDestroy 也在主线程，因此「判断 → 调用」之间不存在被销毁的竞态。
        if (isDestroyed) return
        val manager = requestManager ?: Glide.with(this).also { requestManager = it }
        if (!avatarViews.contains(avatarView)) avatarViews.add(avatarView)
        manager.load(url)
            .transition(GlideUtil.getTransitionOptions())
            .placeholder(R.mipmap.akari)
            .error(R.mipmap.akari)
            .apply(RequestOptions.circleCropTransform())
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .into(avatarView)
    }

    private fun refreshAccountList() {
        clearAvatarRequests()
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
        addImportCard()
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
        text.text = getString(R.string.text_account_empty)
        text.textSize = 13f
        card.addView(text)

        accountList.addView(card)
        addNewAccountCard()
        addImportCard()
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

        if (!savedAvatar.isNullOrEmpty() && !savedName.isNullOrEmpty()) {
            nameView.text = savedName + (if (isCurrent) "（当前）" else "")
            loadAvatar(avatarView, savedAvatar)
        } else {
            nameView.text = "UID:" + account.mid + (if (isCurrent) "（当前）" else "")

            CenterThreadPool.run {
                // 起跑前先判一次：页面已经走了就别再白跑一次网络
                // （弱网下这次请求要挂好几秒，白占着连接与线程）。
                if (isDestroyed) return@run
                try {
                    val userInfo = UserInfoApi.getUserInfo(account.mid) ?: return@run
                    runOnUiThread {
                        // 【崩溃修复点】请求在后台跑的这几秒里，页面完全可能已经被 finish 或销毁：
                        // 点左上角标题退出、点另一个账号切走（startActivity + finish）、
                        // 或低内存设备上被系统回收。此时 Glide.with(activity) 会抛
                        // IllegalArgumentException: You cannot start a load for a destroyed activity
                        // （即混淆栈里的 R1.l.d），同时 nameView 也已脱离窗口，不该再写。
                        if (isDestroyed) return@runOnUiThread
                        nameView.text = userInfo.name + (if (isCurrent) "（当前）" else "")
                        if (!userInfo.avatar.isNullOrEmpty()) {
                            loadAvatar(avatarView, userInfo.avatar)
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
                MsgUtil.showMsg(getString(R.string.msg_accountswitchactivity_001))
            } else if (!AccountManager.switchToAccount(account)) {
                // 26.11.06 起 switchToAccount 走 applyCredentials：mid 与 Cookie 里的 DedeUserID
                // 对不上、或快照 Cookie 为空时**整笔拒绝**（防止 A 的 Cookie 配 B 的 mid）。
                // 原实现忽略返回值，照样提示「已切换到 X」再跳启动页 —— 用户看到的是「切了等于没切、
                // 一直加载」，也就是升级后被报成「掉登录 / 所有账号都登不上」的那一类。
                MsgUtil.showMsg(getString(R.string.msg_accountswitchactivity_004))
            } else {
                val displayName = if (savedName != null && savedName.isNotEmpty()) savedName else "UID:" + account.mid
                MsgUtil.showMsg(getString(R.string.msg_account_switched, displayName))
                val intent = Intent(this@AccountSwitchActivity, SplashActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
        }

        card.setOnLongClickListener {
            // 当前账号不能删：它的凭证还在 SP 里，删了下次 saveCurrentAccount() 又会把它加回来
            // （表现就是「删了又出现」），而且会把用户直接踢成未登录却没有任何提示。
            if (isCurrent) {
                MsgUtil.showMsg(getString(R.string.msg_accountswitchactivity_003))
            } else if (accountsCanBeRemoved()) {
                val displayName = if (savedName != null && savedName.isNotEmpty()) savedName else "UID:" + account.mid
                TerminalDialog.confirm(
                    context = this,
                    title = getString(R.string.accountswitch_delete_title),
                    message = getString(R.string.accountswitch_delete_message, displayName),
                    confirmText = getString(R.string.accountswitch_delete_confirm)
                ) {
                    if (AccountManager.removeAccount(account.mid)) {
                        MsgUtil.showMsg(getString(R.string.msg_accountswitchactivity_002))
                    } else {
                        MsgUtil.showMsg(getString(R.string.msg_accountswitchactivity_005))
                    }
                    refreshAccountList()
                }.show()
            }
            true
        }

        return view
    }

    /**
     * 页面销毁时收尾。
     *
     * 清理必须放在 `super.onDestroy()` **之前**：`super` 一跑，Glide 挂在 Activity 生命周期上的
     * RequestManager 就已经进入 destroyed 状态，那时候再 clear 属于事后补救；
     * 先清掉才能确定地把 ViewTarget 与 ImageView 的引用断开（防泄漏）。
     */
    override fun onDestroy() {
        clearAvatarRequests()
        requestManager = null
        super.onDestroy()
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
        text.text = getString(R.string.text_account_add_new)
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

    /**
     * 「导入账号数据」入口。
     *
     * <p>特殊登录页在设置里只在「本机未登录且 SDK 太低」时才可达，换机搬家的人根本找不到；
     * 这里给一条固定入口，粘贴一份多账号 JSON 就能把别的设备的账号全搬过来。
     * 导入只写账号列表、不切当前账号，导完回到本页点卡片切换。
     */
    private fun addImportCard() {
        val card = MaterialCardView(this)
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val text = TextView(this)
        text.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (35 * resources.displayMetrics.density).toInt()
        )
        text.setPadding(16, 0, 16, 0)
        text.gravity = Gravity.CENTER_VERTICAL
        text.text = getString(R.string.text_account_import)
        text.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
        text.textSize = 13f
        text.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.arrow_forward, 0)
        card.addView(text)

        card.setOnClickListener {
            startActivity(Intent(this, SpecialLoginActivity::class.java))
        }

        accountList.addView(card)
    }
}