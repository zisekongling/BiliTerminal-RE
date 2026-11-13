package com.RobinNotBad.BiliClient.activity.message

import com.RobinNotBad.BiliClient.ui.appearance.ThemeTint
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.EmoteActivity
import com.RobinNotBad.BiliClient.activity.ImagePickerActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.message.PrivateMsgAdapter
import com.RobinNotBad.BiliClient.api.EmoteApi
import com.RobinNotBad.BiliClient.api.ImageApi
import com.RobinNotBad.BiliClient.api.PrivateMsgApi
import com.RobinNotBad.BiliClient.model.PrivateMessage
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SettingsKeys
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
    lateinit var imageBtn: TextView
    lateinit var emoteBtn: ImageButton
    lateinit var layout_input: View
    var adapter: PrivateMsgAdapter? = null
    var uid: Long = 0
    var isLoadingMore = false
    var refreshTimer: Timer? = null
    var animTimer: Timer? = null

    var animVisible = true

    /**
     * 选图结果回调。发图链路见 [sendImage]。
     */
    private val imageLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == android.app.Activity.RESULT_OK && data?.data != null) {
                sendImage(data.data!!)
            }
        }

    /**
     * 表情面板结果回调：面板选中后回传表情码（如 `[doge]`），追加到输入框光标处，
     * 随后照旧按 `msg_type=1` 的文本消息发送——服务端按文本里的表情码解析，
     * 收发双方都由 [PrivateMsgAdapter] 渲染成表情图（渲染侧不用改）。
     */
    private val emoteLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
                val emoteCode = data.getStringExtra("text")
                if (!emoteCode.isNullOrEmpty()) {
                    val cursor = contentEt.selectionStart.coerceAtLeast(0)
                    contentEt.text.insert(cursor, emoteCode)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_private_msg)

        msgView = findViewById(R.id.msg_view)
        contentEt = findViewById(R.id.msg_input_et)
        sendBtn = findViewById(R.id.send_btn)
        imageBtn = findViewById(R.id.image_btn)
        emoteBtn = findViewById(R.id.emote_btn)
        // background_privatemsg_send 写死老版粉 #fb8799，这里按主题主色着色（形状/圆角不变）
        ThemeTint.tintBackground(sendBtn, ColorScheme.PRIMARY)
        ThemeTint.tintBackground(imageBtn, ColorScheme.PRIMARY)
        ThemeTint.tintBackground(emoteBtn, ColorScheme.PRIMARY)
        // 图标本身是单色 #fff 矢量，底已被染成主题色，图标保持白；不染的话在浅色主题下会糊成一团
        emoteBtn.setImageDrawable(ThemeTint.tinted(this, R.drawable.icon_emote, Color.WHITE))
        layout_input = findViewById(R.id.layout_input)

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
                        // 避免 JSON 注入：用户输入含引号/反斜杠时不能直接拼 JSON 字符串
                        val contentJson = JSONObject()
                        contentJson.put("content", content)
                        val result = PrivateMsgApi.sendMsg(SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 114514), uid, PrivateMessage.TYPE_TEXT, System.currentTimeMillis() / 1000, contentJson.toString())
                        runOnUiThread {
                            try {
                                if (result.getInt("code") == 0) {
                                    MsgUtil.showMsg(UiText.of("发送成功"))
                                    refresh()
                                } else {
                                    if (result.getInt("code") == 21047) {
                                        MsgUtil.showMsg(result.getString("message"))
                                    }
                                    MsgUtil.showMsg(UiText.of("发送失败"))
                                }
                            } catch (e: JSONException) {
                                MsgUtil.showMsg("发送失败：\n$result")
                                e.printStackTrace()
                            }
                        }
                    } else {
                        runOnUiThread { MsgUtil.showMsg(UiText.of("你还木有输入喵~")) }
                    }
                } catch (e: Exception) {
                    runOnUiThread { MsgUtil.err(e) }
                }
            }
        }

        imageBtn.setOnClickListener {
            if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L) == 0L) {
                MsgUtil.showMsg(UiText.of("还没有登录喵~"))
            } else {
                pickImage()
            }
        }

        emoteBtn.setOnClickListener {
            if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L) == 0L) {
                MsgUtil.showMsg(UiText.of("还没有登录喵~"))
            } else {
                openEmotePanel()
            }
        }
    }

    /**
     * 打开表情面板：`business=im` 优先，API 层拉不到时自动回落到 reply 面板
     * （见 [EmoteApi.getEmotesWithFallback]）；选中结果由 [emoteLauncher] 回填输入框。
     */
    private fun openEmotePanel() {
        try {
            emoteLauncher.launch(
                Intent(this, EmoteActivity::class.java)
                    .putExtra("from", EmoteApi.BUSINESS_IM)
            )
        } catch (e: Exception) {
            MsgUtil.err(e)
        }
    }

    /**
     * 选图：走内置选图页（反馈 #53），页内可退回系统的 ACTION_GET_CONTENT；结果格式相同。
     */
    private fun pickImage() {
        try {
            imageLauncher.launch(ImagePickerActivity.intent(this))
        } catch (e: Exception) {
            MsgUtil.err(e)
        }
    }

    /**
     * 发图：本地图片 → [ImageApi.prepareImage]（按最长边 2048 压缩，避免手表 OOM）
     * → [ImageApi.uploadImage] 上传到 B 站图床 → 组装 `msg_type=2` 的 content → sendMsg。
     *
     * 必须走本项目图床（upload_bfs），否则接口回 21037「图片格式不合法，不要调戏接口啦」。
     */
    private fun sendImage(uri: Uri) {
        CenterThreadPool.run {
            try {
                runOnUiThread { MsgUtil.showMsg(UiText.of("正在上传图片喵…")) }
                val prepared = ImageApi.prepareImage(this, uri)
                val uploaded = ImageApi.uploadImage(
                    prepared.data,
                    prepared.fileName,
                    prepared.mimeType,
                    // 私信图走 IM 图床目录（biz=im）；26.11.02 前误用了评论图那套 biz
                    ImageApi.BIZ_MESSAGE
                ).getOrThrow()
                val content = PrivateMsgApi.buildImageContentOfKb(
                    uploaded.url,
                    uploaded.width,
                    uploaded.height,
                    // UploadedImage.size 是图床返回的 img_size，本身就是千字节（小数），
                    // 不能再按字节数走 sizeToKb，否则会被多除一次 1024
                    uploaded.size,
                    PrivateMsgApi.imageTypeOf(prepared.mimeType)
                )
                val result = PrivateMsgApi.sendMsg(
                    SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 114514),
                    uid,
                    PrivateMessage.TYPE_PIC,
                    System.currentTimeMillis() / 1000,
                    content.toString()
                )
                runOnUiThread {
                    try {
                        if (result.getInt("code") == 0) {
                            MsgUtil.showMsg(UiText.of("发送成功"))
                            refresh()
                        } else {
                            val message = result.optString("message")
                            MsgUtil.showMsg(message.ifEmpty { UiText.of("发送失败") })
                        }
                    } catch (e: JSONException) {
                        MsgUtil.showMsg("发送失败：\n$result")
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { MsgUtil.err(e) }
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