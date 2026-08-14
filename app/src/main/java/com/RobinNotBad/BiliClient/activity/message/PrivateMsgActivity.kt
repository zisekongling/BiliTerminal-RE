package com.RobinNotBad.BiliClient.activity.message

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.EditText
import android.widget.ImageButton

import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.message.PrivateMsgAdapter
import com.RobinNotBad.BiliClient.api.PrivateMsgApi
import com.RobinNotBad.BiliClient.model.PrivateMessage
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.util.Collections
import java.util.Timer
import java.util.TimerTask

class PrivateMsgActivity : BaseActivity() {
    var allMsg = JSONObject()
    var list: MutableList<PrivateMessage> = Collections.synchronizedList(ArrayList())
    var emoteArray = JSONArray()
    lateinit var msgView: RecyclerView
    lateinit var contentEt: EditText
    lateinit var sendBtn: ImageButton
    lateinit var layout_input: View
    var adapter: PrivateMsgAdapter? = null
    var uid: Long = 0
    var isLoadingMore = false
    var refreshTimer: Timer? = null
    var animTimer: Timer? = null

    var animVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_private_msg)

        msgView = findViewById(R.id.msg_view)
        contentEt = findViewById(R.id.msg_input_et)
        sendBtn = findViewById(R.id.send_btn)
        layout_input = findViewById(R.id.layout_input)

        val intent = intent
        uid = intent.getLongExtra("uid", 114514)
        Log.e("", uid.toString())

        MsgUtil.showMsg("私信有可能被拦截\n尽量不要用终端发私信喵")

        CenterThreadPool.run {
            try {
                allMsg = PrivateMsgApi.getPrivateMsg(uid, 50, 0, 0)
                list = PrivateMsgApi.getPrivateMsgList(allMsg)
                Collections.reverse(list)
                emoteArray = PrivateMsgApi.getEmoteJsonArray(allMsg)
                adapter = PrivateMsgAdapter(list, emoteArray, this)

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
                                                runOnUiThread { layout_input.startAnimation(getViewAnimation(layout_input, true, true)) }
                                                layout_input.postDelayed({ animVisible = true }, 200)
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
                                layout_input.startAnimation(getViewAnimation(layout_input, false, false))
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

        sendBtn.setOnClickListener {
            CenterThreadPool.run {
                try {
                    if (contentEt.text.toString() != "") {
                        val content = contentEt.text.toString()
                        runOnUiThread { contentEt.setText("") }
                        val result = PrivateMsgApi.sendMsg(SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 114514), uid, PrivateMessage.TYPE_TEXT, System.currentTimeMillis() / 1000, "{\"content\":\"$content\"}")
                        runOnUiThread {
                            try {
                                if (result.getInt("code") == 0) {
                                    MsgUtil.showMsg("发送成功")
                                    refresh()
                                } else {
                                    if (result.getInt("code") == 21047) {
                                        MsgUtil.showMsg(result.getString("message"))
                                    }
                                    MsgUtil.showMsg("发送失败")
                                }
                            } catch (e: JSONException) {
                                MsgUtil.showMsg("发送失败：\n$result")
                                e.printStackTrace()
                            }
                        }
                    } else {
                        runOnUiThread { MsgUtil.showMsg("你还木有输入喵~") }
                    }
                } catch (e: Exception) {
                    runOnUiThread { MsgUtil.err(e) }
                }
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
            } catch (e: Exception) {
                runOnUiThread { MsgUtil.err(e) }
            }
        }
    }

    @SuppressLint("SuspiciousIndentation")
    private fun loadMore() {
        isLoadingMore = true
        MsgUtil.showMsg("加载更多中...")
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
                        MsgUtil.showMsg("已加载更多消息！")
                    }
                    isLoadingMore = false
                } else runOnUiThread { MsgUtil.showMsg("没有更多消息了") }
            } catch (e: Exception) {
                runOnUiThread { MsgUtil.err(e) }
            }
        }
    }
}