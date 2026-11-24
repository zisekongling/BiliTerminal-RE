package com.RobinNotBad.BiliClient.activity.message

import com.RobinNotBad.BiliClient.util.UiText

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.message.MessageSettingsAdapter
import com.RobinNotBad.BiliClient.api.PrivateMsgApi
import com.RobinNotBad.BiliClient.api.UserInfoApi
import com.RobinNotBad.BiliClient.model.PrivateMsgSession
import com.RobinNotBad.BiliClient.model.message.MessageSettingItem
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog

/**
 * 单会话的「聊天设置」页（对齐 PiliPlus 的 whisper_link_setting）。
 *
 * 五个开关：接收消息推送、置顶聊天、消息免打扰、拦截此会话消息、拉黑对方。
 * 其中置顶/免打扰/拦截的状态由会话列表直接带进来（会话对象里就有 top_ts / is_dnd / is_intercept），
 * 只有「接收推送」必须现拉 get_session_ss——它和「要不要显示这个开关」一起由服务端给。
 *
 * 只对用户会话有效：粉丝团会话没有置顶/免打扰/拉黑这些概念（接口明确「仅支持用户会话」），
 * 所以入口那边只在用户会话上给这一项。
 *
 * 改动成功就 setResult(RESULT_OK)，会话列表按服务端状态重拉一次；失败则把开关改回原状态，
 * 不给用户「以为改成功了」的错觉。
 */
class ChatSettingsActivity : BaseActivity() {
    companion object {
        const val EXTRA_UID = "uid"
        const val EXTRA_NAME = "name"
        const val EXTRA_TOP = "top"
        const val EXTRA_DND = "dnd"
        const val EXTRA_INTERCEPT = "intercept"

        /**
         * 构造打开本页的 Intent：会话列表里已有的状态直接带过去，省掉一次请求
         * （只有推送设置必须现拉）。
         */
        fun intentFor(context: Context, session: PrivateMsgSession): Intent {
            val intent = Intent(context, ChatSettingsActivity::class.java)
            intent.putExtra(EXTRA_UID, session.talkerUid)
            intent.putExtra(EXTRA_NAME, session.sessionName())
            intent.putExtra(EXTRA_TOP, session.isTop())
            intent.putExtra(EXTRA_DND, session.isDnd())
            intent.putExtra(EXTRA_INTERCEPT, session.intercept)
            return intent
        }

        /** 直接开页（拿不到返回结果时用；消息页走 launcher，见 intentFor）。 */
        fun start(context: Context, session: PrivateMsgSession) {
            val intent = intentFor(context, session)
            // 适配器的 context 是 Activity，但为了不让以后有人从别处调用时崩，加上这个标记
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var emptyView: TextView
    private var adapter: MessageSettingsAdapter? = null
    private val settingsList: MutableList<MessageSettingItem> = mutableListOf()

    private var uid: Long = 0
    private var sessionName: String = ""
    private var isTop = false
    private var isDnd = false
    private var isIntercept = false
    private var isBlocked = false
    private var pushEnabled = true
    private var showPushSetting = false
    private var changed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uid = intent.getLongExtra(EXTRA_UID, 0L)
        sessionName = intent.getStringExtra(EXTRA_NAME).orEmpty()
        isTop = intent.getBooleanExtra(EXTRA_TOP, false)
        isDnd = intent.getBooleanExtra(EXTRA_DND, false)
        isIntercept = intent.getBooleanExtra(EXTRA_INTERCEPT, false)

        setContentView(R.layout.activity_simple_refresh)
        setPageName(sessionName.ifEmpty { UiText.of("聊天设置") })

        emptyView = findViewById(R.id.emptyTip)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.isEnabled = false
        swipeRefreshLayout.isRefreshing = true
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        if (uid <= 0) {
            // 没有 uid 就没什么可设置的（理论上不会发生：入口只在真实会话上）
            swipeRefreshLayout.isRefreshing = false
            emptyView.text = UiText.of("加载失败，请重试")
            emptyView.visibility = View.VISIBLE
            return
        }

        load()
    }

    /**
     * 拉会话级的推送设置并铺出列表。
     *
     * get_session_ss 失败时照样铺列表，只是不显示「接收消息推送」这一项——
     * 其余四项的状态本来就随会话带进来了，没必要为一次失败的请求把整页变成错误页。
     */
    private fun load() {
        CenterThreadPool.run {
            var ss: PrivateMsgApi.SessionSs? = null
            try {
                ss = PrivateMsgApi.getSessionSs(uid)
            } catch (e: Exception) {
                Logu.w("ChatSettingsActivity", "会话推送设置加载失败：" + e.message)
            }
            CenterThreadPool.runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                applySessionSs(ss)
            }
        }
    }

    private fun applySessionSs(ss: PrivateMsgApi.SessionSs?) {
        pushEnabled = ss?.pushEnabled ?: true
        showPushSetting = ss?.showPushSetting ?: false
        isBlocked = ss != null && ss.followStatus == PrivateMsgApi.FOLLOW_STATUS_BLOCKED

        settingsList.clear()

        // 服务端说别显示就别显示：官方客户端也是这么处理的（关推送对这类会话没有意义）
        if (showPushSetting) {
            settingsList.add(
                MessageSettingItem(
                    "push",
                    UiText.of("接收消息推送"),
                    UiText.of("图文消息与稿件推送；通知类消息不受影响"),
                    MessageSettingItem.TYPE_SWITCH,
                    pushEnabled,
                    null
                )
            )
        }

        settingsList.add(
            MessageSettingItem(
                "top",
                UiText.of("置顶聊天"),
                "",
                MessageSettingItem.TYPE_SWITCH,
                isTop,
                null
            )
        )

        settingsList.add(
            MessageSettingItem(
                "dnd",
                UiText.of("消息免打扰"),
                UiText.of("开启后不再提示这个人的新消息"),
                MessageSettingItem.TYPE_SWITCH,
                isDnd,
                null
            )
        )

        settingsList.add(
            MessageSettingItem(
                "intercept",
                UiText.of("拦截此会话消息"),
                UiText.of("拦截后，对方的私信会进入「被拦截」列表"),
                MessageSettingItem.TYPE_SWITCH,
                isIntercept,
                null
            )
        )

        settingsList.add(
            MessageSettingItem(
                "block",
                UiText.of("拉黑对方"),
                UiText.of("拉黑会同时解除关注关系，对方无法再给你发私信"),
                MessageSettingItem.TYPE_SWITCH,
                isBlocked,
                null
            )
        )

        adapter = MessageSettingsAdapter(this, settingsList) { key, value ->
            onSettingChanged(key, value)
        }
        recyclerView.adapter = adapter
        swipeRefreshLayout.isRefreshing = false
    }

    private fun onSettingChanged(key: String, value: Boolean) {
        when (key) {
            "push" -> setPush(value)
            "top" -> setTop(value)
            "dnd" -> setDnd(value)
            "intercept" -> setIntercept(value)
            "block" -> if (value) confirmBlock() else setBlock(false)
        }
    }

    private fun setPush(value: Boolean) {
        applyChange("push", { PrivateMsgApi.setPushSs(uid, value) }) { pushEnabled = value }
    }

    private fun setTop(value: Boolean) {
        applyChange("top", { PrivateMsgApi.setSessionTop(uid, PrivateMsgApi.SESSION_TYPE_USER, value) }) { isTop = value }
    }

    private fun setDnd(value: Boolean) {
        val ownUid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L)
        applyChange("dnd", { PrivateMsgApi.setMsgDnd(ownUid, uid, value) }) { isDnd = value }
    }

    private fun setIntercept(value: Boolean) {
        applyChange("intercept", { PrivateMsgApi.updateIntercept(uid, value) }) { isIntercept = value }
    }

    /**
     * 拉黑要二次确认：它会连带解除关注，是比较重的操作。
     */
    private fun confirmBlock() {
        TerminalDialog.confirm(
            this,
            title = UiText.of("拉黑对方"),
            message = UiText.of("拉黑会同时解除关注关系，对方无法再给你发私信"),
            confirmText = UiText.of("确定")
        ) { setBlock(true) }.show()
    }

    private fun setBlock(block: Boolean) {
        CenterThreadPool.run {
            try {
                val code = UserInfoApi.relationModify(
                    uid,
                    UserInfoApi.actForBlock(block),
                    UserInfoApi.RELATION_RE_SRC_IM
                )
                CenterThreadPool.runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (code == 0) {
                        isBlocked = block
                        markChanged()
                        MsgUtil.showMsg(if (block) UiText.of("已拉黑对方") else UiText.of("已解除拉黑"))
                    } else {
                        revert("block")
                        MsgUtil.showMsg(UiText.of("操作失败，请重试"))
                    }
                }
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    revert("block")
                    report(e)
                    MsgUtil.showMsg(UiText.of("操作失败，请重试"))
                }
            }
        }
    }

    /**
     * 跑一个会话设置接口，成功才把本地状态落下来。
     *
     * @param action 返回接口业务码，0 表示成功
     * @param onOk   成功时更新本地字段（开关本身已由适配器改成新位置）
     */
    private fun applyChange(key: String, action: () -> Int, onOk: () -> Unit) {
        CenterThreadPool.run {
            try {
                val code = action()
                CenterThreadPool.runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (code == 0) {
                        onOk()
                        markChanged()
                        MsgUtil.showMsg(UiText.of("设置已保存"))
                    } else {
                        revert(key)
                        val error = PrivateMsgApi.sessionErrorMsg(code)
                        MsgUtil.showMsg(error.ifEmpty { UiText.of("操作失败，请重试") })
                    }
                }
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    revert(key)
                    report(e)
                    MsgUtil.showMsg(UiText.of("操作失败，请重试"))
                }
            }
        }
    }

    /** 记下「改过」，返回会话列表时让它按服务端状态重拉一次。 */
    private fun markChanged() {
        if (changed) return
        changed = true
        setResult(RESULT_OK)
    }

    /** 接口失败时把开关拨回原状态：适配器在回调里已经先把开关画成新值了。 */
    private fun revert(key: String) {
        val item = settingsList.firstOrNull { it.key == key } ?: return
        item.value = when (key) {
            "push" -> pushEnabled
            "top" -> isTop
            "dnd" -> isDnd
            "intercept" -> isIntercept
            else -> isBlocked
        }
        val index = settingsList.indexOf(item)
        if (index >= 0) adapter?.notifyItemChanged(index)
    }
}
