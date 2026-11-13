package com.RobinNotBad.BiliClient.activity.message

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.TranslateAnimation

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.reply.WriteReplyActivity
import com.RobinNotBad.BiliClient.adapter.message.PrivateMsgAdapter
import com.RobinNotBad.BiliClient.api.PrivateMsgApi
import com.RobinNotBad.BiliClient.model.PrivateMessage
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

import org.json.JSONArray
import org.json.JSONObject

import java.util.Collections
import java.util.Timer
import java.util.TimerTask

class PrivateMsgActivity : BaseActivity() {
    var allMsg = JSONObject()
    var list: MutableList<PrivateMessage> = Collections.synchronizedList(ArrayList())
    var emoteArray = JSONArray()
    lateinit var msgView: RecyclerView
    lateinit var writeMsgBtn: View
    var adapter: PrivateMsgAdapter? = null
    var uid: Long = 0
    var isLoadingMore = false
    var refreshTimer: Timer? = null
    var animTimer: Timer? = null

    var animVisible = true

    /**
     * 「写私信」页的结果回调：发出去就重拉一次消息列表，用户不用自己刷新。
     *
     * 发送本身整条复用写评论页的私信模式（[WriteReplyActivity] + EXTRA_PRIVATE_UID），
     * 这个页面只负责打开入口和收结果——输入框、表情、图片、换行都在那边。
     */
    private val writeMsgLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_private_msg)

        msgView = findViewById(R.id.msg_view)
        // 底部铺满的「写私信」按钮（原来是一整行输入框 + 表情/图片/发送三个小按钮）
        writeMsgBtn = findViewById(R.id.write_msg_btn)

        val intent = intent
        uid = intent.getLongExtra("uid", 114514)
        Log.e("", uid.toString())

        // 反馈 #121：这句提示可以关（设置 → 私信拦截提示，默认开）。老用户每次进会话都被弹一次太吵
        if (SharedPreferencesUtil.getBoolean(SettingsKeys.PRIVATE_MSG_INTERCEPT_TIP_ENABLE, true)) {
            MsgUtil.showMsg(UiText.of("私信有可能被拦截\n尽量不要用终端发私信喵"))
        }

        CenterThreadPool.run {
            try {
                allMsg = PrivateMsgApi.getPrivateMsg(uid, 50, 0, 0)
                list = PrivateMsgApi.getPrivateMsgList(allMsg)
                Collections.reverse(list)
                emoteArray = PrivateMsgApi.getEmoteJsonArray(allMsg)
                adapter = PrivateMsgAdapter(list, emoteArray, this)
                adapter!!.onRecall = { msg -> recallMessage(msg) }

                if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PRIVATE_MSG_AUTO_READ_ENABLE, true)) {
                    try {
                        PrivateMsgApi.updateAck(uid, 1, 0)
                    } catch (e: Exception) {
                        Log.e("PrivateMsgActivity", "自动已读失败", e)
                    }
                }

                runOnUiThread {
                    val manager = CustomLinearManager(this)
                    manager.stackFromEnd = true
                    msgView.layoutManager = manager
                    msgView.adapter = adapter
                    msgView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrollStateChanged(@NonNull recyclerView: RecyclerView, newState: Int) {
                            super.onScrollStateChanged(recyclerView, newState)
                            when (newState) {
                                RecyclerView.SCROLL_STATE_DRAGGING -> {
                                    if (!recyclerView.canScrollVertically(-1) && !isLoadingMore) {
                                        loadMore()
                                        Log.e("", "滑动到顶部，开始刷新")
                                    }
                                }
                                RecyclerView.SCROLL_STATE_IDLE -> {
                                    if (!animVisible) {
                                        if (animTimer != null) animTimer!!.cancel()
                                        animTimer = Timer()
                                        animTimer!!.schedule(object : TimerTask() {
                                            override fun run() {
                                                runOnUiThread { writeMsgBtn.startAnimation(getViewAnimation(writeMsgBtn, true, true)) }
                                                writeMsgBtn.postDelayed({ animVisible = true }, 200)
                                            }
                                        }, 500)
                                    }
                                }
                            }
                        }

                        override fun onScrolled(@NonNull recyclerView: RecyclerView, dx: Int, dy: Int) {
                            super.onScrolled(recyclerView, dx, dy)
                            if (animVisible && recyclerView.canScrollVertically(0) && dy != 0) {
                                animVisible = false
                                writeMsgBtn.startAnimation(getViewAnimation(writeMsgBtn, false, false))
                            }
                        }
                    })

                    refreshTimer = Timer()
                    refreshTimer!!.schedule(object : TimerTask() {
                        override fun run() {
                            refresh()
                        }
                    }, 15000, 15000)
                }
            } catch (e: Exception) {
                runOnUiThread { MsgUtil.err(e) }
            }
        }

        // 底部铺满的「写私信」按钮：点开写评论页的私信模式，输入框、表情、图片、换行、发送
        // 全部复用评论区那一套，这个页面只留入口——原来内联的输入行三个小按钮已经删掉
        writeMsgBtn.setOnClickListener {
            if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L) == 0L) {
                MsgUtil.showMsg(UiText.of("还没有登录喵~"))
            } else {
                writeMsgLauncher.launch(
                    Intent(this, WriteReplyActivity::class.java)
                        .putExtra(WriteReplyActivity.EXTRA_PRIVATE_UID, uid)
                )
            }
        }
    }

    /**
     * 撤回自己发的私信（反馈 #57）。入口在消息长按菜单，是否显示由 [PrivateMsgApi.canRecall] 决定；
     * 超时等失败以服务端返回的 message 为准（本地时间可能不准）。
     */
    private fun recallMessage(msg: PrivateMessage) {
        CenterThreadPool.run {
            try {
                val result = PrivateMsgApi.recallMsg(
                    SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L), uid, msg.msgId
                )
                runOnUiThread {
                    if (result.optInt("code", -1) == 0) {
                        MsgUtil.showMsg(getString(R.string.msg_private_msg_recalled))
                        adapter?.removeMessage(msg)
                        refresh()
                    } else {
                        val message = result.optString("message")
                        MsgUtil.showMsg(message.ifEmpty { getString(R.string.msg_private_msg_recall_failed) })
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { MsgUtil.err(e) }
            }
        }
    }

    private fun getViewAnimation(view: View, show_or_hide: Boolean, up_or_down: Boolean): TranslateAnimation {
        val height = view.measuredHeight + 2
        val anim: TranslateAnimation
        anim = TranslateAnimation(0f, 0f,
            (if (show_or_hide) (if (up_or_down) height else -height) else 0).toFloat(),
            (if (show_or_hide) 0 else (if (up_or_down) -height else height)).toFloat())
        anim.duration = 200
        val i = AccelerateDecelerateInterpolator()
        anim.interpolator = i
        anim.fillAfter = true
        anim.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                if (show_or_hide) view.visibility = View.VISIBLE
            }

            override fun onAnimationEnd(animation: Animation) {
                if (!show_or_hide) view.visibility = View.GONE
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        return anim
    }

    override fun onDestroy() {
        if (refreshTimer != null) refreshTimer!!.cancel()
        refreshTimer = null
        super.onDestroy()
    }

    private fun refresh() {
        CenterThreadPool.run {
            try {
                val oldListSize = list.size
                // 空会话时 list 为空，直接访问 list[list.size - 1] 会越界崩溃
                if (list.isNotEmpty()) {
                    val msgResult = PrivateMsgApi.getPrivateMsg(uid, 50, list[list.size - 1].msgSeqno, 0)
                    val newList = PrivateMsgApi.getPrivateMsgList(msgResult)
                    if (newList.size > 0) {
                        for (i in 0 until PrivateMsgApi.getEmoteJsonArray(msgResult).length()) {
                            val emote = PrivateMsgApi.getEmoteJsonArray(msgResult).getJSONObject(i)
                            emoteArray.put(emote)
                        }
                        Collections.reverse(newList)
                        runOnUiThread {
                            for (msg in newList) {
                                list.add(msg)
                                adapter!!.notifyItemInserted(list.size - 1)
                            }
                            adapter!!.notifyItemRangeChanged(oldListSize - 1, list.size)
                            msgView.smoothScrollToPosition(list.size - 1)
                        }
                    }
                } else {
                    isLoadingMore = false
                }
            } catch (e: Exception) {
                runOnUiThread { MsgUtil.err(e) }
            }
        }
    }

    @SuppressLint("SuspiciousIndentation")
    private fun loadMore() {
        isLoadingMore = true
        MsgUtil.showMsg(UiText.of("加载更多中..."))
        CenterThreadPool.run {
            try {
                if (allMsg.getInt("has_more") == 1) {
                    allMsg = PrivateMsgApi.getPrivateMsg(uid, 15, 0, list[0].msgSeqno)
                    Log.e("", allMsg.toString())
                    val newList = PrivateMsgApi.getPrivateMsgList(allMsg)
                    Collections.reverse(newList)

                    for (i in 0 until PrivateMsgApi.getEmoteJsonArray(allMsg).length()) {
                        val emote = PrivateMsgApi.getEmoteJsonArray(allMsg).getJSONObject(i)
                        emoteArray.put(emote)
                    }
                    for (a in list) {
                        Log.e("msgAll", a.msgSeqno.toString() + a.name + "." + a.uid + "." + a.msgId + "." + a.timestamp + "." + a.content + "." + a.type)
                    }

                    Log.e("loadMore", "loadMore")
                    runOnUiThread {
                        adapter!!.addItem(newList)
                        MsgUtil.showMsg(UiText.of("已加载更多消息！"))
                    }
                    isLoadingMore = false
                } else runOnUiThread {
                    // 没有更多消息：必须复位 isLoadingMore，否则它一直为 true，
                    // 之后每次滑到顶都会重复弹这条提示
                    isLoadingMore = false
                    MsgUtil.showMsg(UiText.of("没有更多消息了"))
                }
            } catch (e: Exception) {
                runOnUiThread { MsgUtil.err(e) }
            }
        }
    }
}