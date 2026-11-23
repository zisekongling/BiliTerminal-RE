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

import com.RobinNotBad.BiliClient.BiliTerminal
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
    companion object {
        /**
         * 系统会话（UP主小助手 / 系统通知 / 客服…）的标题。
         *
         * 这些会话的 talker_id 是服务端合成的，user/cards 查不到昵称，只能用会话自带的
         * account_info.name，由消息页通过这个 extra 传进来（26.11.20 放开系统会话）。
         */
        const val EXTRA_SYSTEM_NAME = "system_name"

        /** 会话已读到哪一条（max_seqno），update_ack 的 ack_seqno；缺了它服务端不认这条已读（26.11.23）。 */
        const val EXTRA_MAX_SEQNO = "max_seqno"

        /**
         * 非系统会话的标题覆盖（26.11.23）：粉丝团会话的 talker_id 是粉丝团 id，
         * user/cards 查不到昵称，标题用团名，由消息页传进来。
         */
        const val EXTRA_SESSION_NAME = "session_name"

        /**
         * 会话类型（26.11.23）：1 用户会话、2 粉丝团。
         *
         * 拉消息（fetch_session_msgs）与标已读（update_ack）都要按它带 session_type——
         * 以前两处都写死 1，粉丝团会话一条消息都拉不出来、未读也消不掉。
         */
        const val EXTRA_SESSION_TYPE = "session_type"
    }

    var allMsg = JSONObject()
    var list: MutableList<PrivateMessage> = Collections.synchronizedList(ArrayList())
    var emoteArray = JSONArray()
    lateinit var msgView: RecyclerView
    lateinit var writeMsgBtn: View
    var adapter: PrivateMsgAdapter? = null
    var uid: Long = 0

    /** 已读游标，来自消息页的 [EXTRA_MAX_SEQNO]；为 0 说明没传（例如从别处进来），不做 ack */
    var maxSeqno: Long = 0

    /** 本会话的类型（[EXTRA_SESSION_TYPE]）：1 用户会话、2 粉丝团。拉消息与已读都按它走。 */
    var sessionType: Int = PrivateMsgApi.SESSION_TYPE_USER
    var isLoadingMore = false
    var animTimer: Timer? = null

    /**
     * 是不是系统会话（UP主小助手 / 系统通知 / 客服…）。
     * 26.11.23 从 onCreate 的局部变量提成字段：ack 现在有多个调用点（进页面、每次刷新拉到新消息），
     * 都要按它跳过——系统会话的 talker_id 是服务端合成的，ack 对它没有意义。
     */
    private var isSystemSession = false

    var animVisible = true

    /**
     * 「写私信」页的结果回调：发出去就重拉一次消息列表，用户不用自己刷新。
     *
     * 发送本身整条复用写评论页的私信模式（[WriteReplyActivity] + EXTRA_PRIVATE_UID），
     * 这个页面只负责打开入口和收结果——输入框、表情、图片、换行都在那边。
     *
     * 反馈 #166：这里还要把 RESULT_OK 往上传一层，消息页才知道「真的发过消息」，
     * 据此重拉会话列表、把刚聊过的人排到最上面。只有这一条路径会置结果——
     * 单纯点进会话看一眼再返回不会，所以消息页不会每次返回都整表刷新。
     */
    private val writeMsgLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                setResult(RESULT_OK)
                refresh()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_private_msg)

        msgView = findViewById(R.id.msg_view)
        // 底部铺满的「写私信」按钮（原来是一整行输入框 + 表情/图片/发送三个小按钮）
        writeMsgBtn = findViewById(R.id.write_msg_btn)

        val intent = intent
        uid = intent.getLongExtra("uid", 114514)
        maxSeqno = intent.getLongExtra(EXTRA_MAX_SEQNO, 0)
        sessionType = intent.getIntExtra(EXTRA_SESSION_TYPE, PrivateMsgApi.SESSION_TYPE_USER)
        Log.e("", uid.toString())

        // 系统会话只能读：没有真实 mid，私信发不出去；标题也用它自带的账号名（默认是「私信」）
        val systemName = intent.getStringExtra(EXTRA_SYSTEM_NAME).orEmpty()
        isSystemSession = systemName.isNotEmpty()
        // 标题：系统会话用 account_info 的名字，粉丝团会话用团名，其余留默认「私信」
        val titleOverride = systemName.ifEmpty { intent.getStringExtra(EXTRA_SESSION_NAME).orEmpty() }
        if (titleOverride.isNotEmpty()) setPageName(titleOverride)
        // 粉丝团会话同样不能从终端发消息（没有应援团发信的接口），只读，但**要** ack
        if (isSystemSession || sessionType == PrivateMsgApi.SESSION_TYPE_FAN_GROUP) {
            writeMsgBtn.visibility = View.GONE
        }

        // 反馈 #121：这句提示可以关（设置 → 私信拦截提示，默认开）。老用户每次进会话都被弹一次太吵
        // 26.11.19 debug 包不弹任何自动提示：这条每次进会话都弹的拦截提示直接免掉，release 照旧。
        // 系统会话没有发送入口，这条「发私信会被拦截」的提示对它没有意义，直接跳过。
        if (!isSystemSession && sessionType == PrivateMsgApi.SESSION_TYPE_USER
            && !BiliTerminal.isAutoPopupSuppressed()
            && SharedPreferencesUtil.getBoolean(SettingsKeys.PRIVATE_MSG_INTERCEPT_TIP_ENABLE, true)
        ) {
            MsgUtil.showMsg(UiText.of("私信有可能被拦截\n尽量不要用终端发私信喵"))
        }

        CenterThreadPool.run {
            try {
                allMsg = PrivateMsgApi.getPrivateMsg(uid, sessionType, 50, 0, 0)
                list = PrivateMsgApi.getPrivateMsgList(allMsg)
                Collections.reverse(list)
                emoteArray = PrivateMsgApi.getEmoteJsonArray(allMsg)
                adapter = PrivateMsgAdapter(list, emoteArray, this)
                adapter!!.onRecall = { msg -> recallMessage(msg) }

                // 进页面时按消息页传来的游标 ack 一次；系统会话 / 关掉自动已读时 ackTo 内部直接跳过
                if (maxSeqno > 0) ackTo(maxSeqno)
                else if (!isSystemSession) Log.w("PrivateMsgActivity", "缺 max_seqno，跳过自动已读")

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

        // 手动刷新（26.11.23）：15 秒定时器已经去掉，新消息只在这个按钮和「写完私信返回」时拉
        findViewById<View>(R.id.refresh_btn).setOnClickListener { refresh(manual = true) }
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
        // 26.11.23：定时刷新已去掉，这里只剩底部按钮的动画计时器要收
        animTimer?.cancel()
        animTimer = null
        super.onDestroy()
    }

    /**
     * 拉当前会话有没有新消息。
     *
     * 26.11.23：原来这个方法被一个 15 秒定时器反复调，切到后台也不停——手表上持续唤醒、持续发请求，
     * 电池吃不消，用户拍板去掉定时器。现在只有两个时机：点顶栏刷新按钮、写完私信返回。
     * 另外原来只有进页面那一次 ack，用户一直开着会话页时未读永远消不掉，所以拉到新消息后补一次 ack。
     *
     * @param manual 用户主动点刷新：没有新消息也要给一句提示，否则点下去像没反应
     */
    private fun refresh(manual: Boolean = false) {
        CenterThreadPool.run {
            try {
                val oldListSize = list.size
                if (oldListSize == 0) {
                    // 空会话（新会话、历史被清）刷新要能真拉出内容；原来这里只是复位标志，按了没反应
                    val firstMsg = PrivateMsgApi.getPrivateMsg(uid, sessionType, 50, 0, 0)
                    val firstList = PrivateMsgApi.getPrivateMsgList(firstMsg)
                    val emotes = PrivateMsgApi.getEmoteJsonArray(firstMsg)
                    Collections.reverse(firstList)
                    allMsg = firstMsg
                    runOnUiThread {
                        for (i in 0 until emotes.length()) {
                            emoteArray.put(emotes.getJSONObject(i))
                        }
                        list.clear()
                        list.addAll(firstList)
                        adapter?.notifyDataSetChanged()
                        if (firstList.isNotEmpty()) {
                            msgView.smoothScrollToPosition(list.size - 1)
                        } else if (manual) {
                            MsgUtil.showMsg(UiText.of("已是最新消息"))
                        }
                    }
                    // 用局部变量取 seqno：list 是在 runOnUiThread 里异步填的，这里读它可能还是空的
                    if (firstList.isNotEmpty()) ackTo(firstList.last().msgSeqno)
                    return@run
                }

                // 取游标也用 getOrNull：UI 线程可能刚把 list 换掉（空会话分支 clear+addAll），直接下标会越界
                val lastSeqno = list.getOrNull(oldListSize - 1)?.msgSeqno ?: return@run
                val msgResult = PrivateMsgApi.getPrivateMsg(uid, sessionType, 50, lastSeqno, 0)
                val newList = PrivateMsgApi.getPrivateMsgList(msgResult)
                if (newList.isEmpty()) {
                    if (manual) runOnUiThread { MsgUtil.showMsg(UiText.of("已是最新消息")) }
                    return@run
                }
                val emotes = PrivateMsgApi.getEmoteJsonArray(msgResult)
                Collections.reverse(newList)
                runOnUiThread {
                    for (i in 0 until emotes.length()) {
                        emoteArray.put(emotes.getJSONObject(i))
                    }
                    for (msg in newList) {
                        list.add(msg)
                        adapter?.notifyItemInserted(list.size - 1)
                    }
                    adapter?.notifyItemRangeChanged(oldListSize - 1, list.size)
                    msgView.smoothScrollToPosition(list.size - 1)
                }
                // 拉到新消息就把未读消到最新一条 —— 这是「停留期间未读也清得掉」的关键
                // newList 已 reverse，最后一条就是最新的；不用 list.last()（UI 线程还没跑完时它还是旧的）
                ackTo(newList.last().msgSeqno)
            } catch (e: Exception) {
                runOnUiThread {
                    if (manual) MsgUtil.err(e) else Log.e("PrivateMsgActivity", "刷新私信失败", e)
                }
            }
        }
    }

    /**
     * 把会话标为已读到 [seqno]（update_ack 的 ack_seqno）。
     *
     * 26.11.23：原来调用处恒传 0，而 updateAck 只有 ackSeqno > 0 才拼 &ack_seqno=，
     * 等于一次都没真正 ack 过——设置页那个「私信自动已读」一直空转，会话未读消不掉（#281）。
     * 系统会话的 talker_id 是服务端合成的，ack 对它没有意义，跳过。
     * 失败只打日志、不打扰用户：下次刷新还会再 ack 一次。
     */
    private fun ackTo(seqno: Long) {
        if (isSystemSession || seqno <= 0) return
        if (!SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PRIVATE_MSG_AUTO_READ_ENABLE, true)) return
        CenterThreadPool.run {
            try {
                // 把返回码打进日志：真机验证「ack 到底生效没」全靠这一行
                val ack = PrivateMsgApi.updateAck(uid, sessionType, seqno)
                if (ack.optInt("code", -1) != 0) {
                    Log.w("PrivateMsgActivity", "自动已读返回 " + ack)
                }
            } catch (e: Exception) {
                Log.e("PrivateMsgActivity", "自动已读失败", e)
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
                    allMsg = PrivateMsgApi.getPrivateMsg(uid, sessionType, 15, 0, list[0].msgSeqno)
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