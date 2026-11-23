package com.RobinNotBad.BiliClient.activity.dynamic

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.base.RefreshMainActivity
import com.RobinNotBad.BiliClient.activity.message.ChooseFriendActivity
import com.RobinNotBad.BiliClient.adapter.dynamic.DynamicAdapter
import com.RobinNotBad.BiliClient.adapter.dynamic.DynamicHolder
import com.RobinNotBad.BiliClient.api.DynamicApi
import com.RobinNotBad.BiliClient.api.EmoteApi
import com.RobinNotBad.BiliClient.api.VoteApi
import com.RobinNotBad.BiliClient.model.Dynamic
import com.RobinNotBad.BiliClient.model.VoteDraft
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import java.util.regex.Pattern

class DynamicActivity : RefreshMainActivity() {

    private var dynamicList: ArrayList<Dynamic>? = null
    private var dynamicAdapter: DynamicAdapter? = null
    var recentUpList: List<DynamicApi.UpInfo>? = null
    private var offset: Long = 0
    private var firstRefresh: Boolean = true
    private var type: String = "all"

    companion object {
        /** 动态页允许的筛选类型；取值是稳定的 key，与列表项显示文案无关 */
        private val VALID_TYPES = setOf("all", "video", "pgc", "article")

        /** 老的显示文案 → 取值映射；只作为没带 "value" 额外项的回传方兜底 */
        private val typeNameMap = mapOf(
            UiText.of("全部") to "all",
            UiText.of("视频投稿") to "video",
            UiText.of("追番") to "pgc",
            UiText.of("专栏") to "article"
        )

        fun getRelayDynamicLauncher(activity: BaseActivity): ActivityResultLauncher<Intent> {
            return activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val code = result.resultCode
                val data = result.data
                if (code == RESULT_OK && data != null) {
                    var text = data.getStringExtra("text")
                    if (TextUtils.isEmpty(text)) text = UiText.of("转发动态")
                    val dynamicId = data.getLongExtra("dynamicId", -1)
                    // 转发自动引用所需的信息：DynamicHolder 放进启动 intent，SendDynamicActivity 原样回传
                    val authorName = data.getStringExtra("forwardAuthorName")
                    val authorMid = data.getLongExtra("forwardAuthorMid", 0)
                    val authorContent = data.getStringExtra("forwardContentText")
                    val finalText = text!!
                    CenterThreadPool.run {
                        try {
                            val atUids = HashMap<String, Long>()
                            val pattern = Pattern.compile("@(\\S+)\\s")
                            val matcher = pattern.matcher(finalText)
                            while (matcher.find()) {
                                val matchedString = matcher.group(1)
                                val uid: Long
                                if (DynamicApi.mentionAtFindUser(matchedString).also { uid = it } != -1L) {
                                    atUids[matchedString] = uid
                                }
                            }
                            val emoteTexts = EmoteApi.getEmoteTexts(EmoteApi.BUSINESS_DYNAMIC)
                            val dynId = DynamicApi.relayDynamic(finalText, atUids.ifEmpty { null }, dynamicId,
                                authorName, authorMid, authorContent, emoteTexts)
                            if (dynId != -1L) {
                                activity.runOnUiThread { MsgUtil.showMsg(UiText.of("转发成功~")) }
                            } else {
                                activity.runOnUiThread { MsgUtil.showMsg(UiText.of("转发失败")) }
                            }
                        } catch (e: Exception) {
                            activity.runOnUiThread { MsgUtil.err(e) }
                        }
                    }
                }
            }
        }

        /**
         * 编辑动态的结果接收器。
         *
         * <p>请求本身由 SendDynamicActivity 发（组 contents、判 @ 与表情它都现成），
         * 这里只把「改完的新正文」交还给发起方，用来刷新界面上那一条动态。
         * 收到的 extras 里没有 edit_dyn_id 时什么都不做，避免误伤其他返回路径。
         */
        fun getEditDynamicLauncher(activity: BaseActivity): ActivityResultLauncher<Intent> {
            return activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                // 不管成功失败都要清掉槽位，否则下一次编辑会调到上一次的回调
                val callback = activity.pendingDynamicEdit
                activity.pendingDynamicEdit = null
                if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
                val data = result.data
                if (data == null || data.getLongExtra("edit_dyn_id", -1L) <= 0L) return@registerForActivityResult
                val newText = data.getStringExtra("text") ?: return@registerForActivityResult
                callback?.invoke(newText)
            }
        }

        /**
         * 「分享至消息」选好友的结果接收器。
         *
         * <p>Holder 在 RecyclerView 里，不是 LifecycleOwner，自己注册不了 ActivityResultLauncher，
         * 所以统一借宿主 Activity 上的这一个：发起前把「选到人之后怎么发」放进
         * [BaseActivity.pendingShareToFriend]，结果回来时取走并清空。
         * 动态列表页与动态详情页共用这一份，不必各注册一个。
         *
         * <p>用户直接返回（没选人）时同样要清槽位，否则下一次分享会调到上一次的回调。
         */
        fun getShareFriendLauncher(activity: BaseActivity): ActivityResultLauncher<Intent> {
            return activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val callback = activity.pendingShareToFriend
                activity.pendingShareToFriend = null
                if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
                val data = result.data ?: return@registerForActivityResult
                val uid = data.getLongExtra(ChooseFriendActivity.EXTRA_UID, 0L)
                if (uid <= 0L) return@registerForActivityResult
                // 昵称只用于「已发送给 xxx」，拿不到时留空也不影响发送
                callback?.invoke(uid, data.getStringExtra(ChooseFriendActivity.EXTRA_NAME).orEmpty())
            }
        }
    }

    val selectTypeLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val code = result.resultCode
        val data = result.data
        if (code == RESULT_OK && data != null && data.getStringExtra("item") != null) {
            // 优先读 ListChooseActivity 回传的稳定取值；老的回传方只带显示文案时再退回查表
            val chosen = data.getStringExtra("value")
            val type = if (chosen.isNullOrEmpty()) typeNameMap[data.getStringExtra("item")] else chosen
            if (type != null && type in VALID_TYPES) {
                if (isRefreshing) {
                    MsgUtil.showMsg(UiText.of("还在加载中OvO"))
                } else {
                    this.type = type
                    setRefreshing(true)
                    refreshDynamic()
                }
            }
        }
    }

    val writeDynamicLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val code = result.resultCode
        val data = result.data
        if (code == RESULT_OK && data != null) {
            val text = data.getStringExtra("text") ?: ""
            val voteDraft = data.getSerializableExtra("voteDraft") as? VoteDraft
            val picsJson = data.getStringExtra("pics")
            // 定时发布：秒级时间戳，0 表示不定时（只有发布链路吃这个字段）
            val timerSeconds = data.getLongExtra("timerPubTime", 0L)
            // 发布选项开关：SendDynamicActivity 的普通发布链路会回传；
            // 转发（relayDynamic）与视频分享链路不带这两个字段 → 缺省即「不勾」
            val privatePub = data.getBooleanExtra("privatePub", false)
            val closeComment = data.getBooleanExtra("closeComment", false)
            CenterThreadPool.run {
                try {
                    val pics = if (!picsJson.isNullOrEmpty()) org.json.JSONArray(picsJson) else null
                    if (text.isEmpty() && pics == null) {
                        runOnUiThread { MsgUtil.showMsg(UiText.of("还没输入内容呢~")) }
                    } else {
                        // 如果有投票草稿，先创建投票
                        var voteId: Long = -1
                        if (voteDraft != null && voteDraft.isValid()) {
                            voteId = VoteApi.createVote(voteDraft)
                        }

                        val atUids = HashMap<String, Long>()
                        val pattern = Pattern.compile("@(\\S+)\\s")
                        val matcher = pattern.matcher(text)
                        while (matcher.find()) {
                            val matchedString = matcher.group(1)
                            val uid: Long
                            if (DynamicApi.mentionAtFindUser(matchedString).also { uid = it } != -1L) {
                                atUids[matchedString] = uid
                            }
                        }

                        // 表情文本用于把正文里的 [xxx] 拆成 type 9 表情节点
                        val emoteTexts = EmoteApi.getEmoteTexts(EmoteApi.BUSINESS_DYNAMIC)

                        // 发布选项：定时 / 仅自己可见 / 关闭评论任一开启才下发 option，
                        // 三个都不开时保持各条既有链路原样（不下发 option，服务端走默认值）
                        val option = if (timerSeconds > 0 || privatePub || closeComment) {
                            DynamicApi.buildPublishOption(
                                privatePub,
                                // close_comment：1 = 关闭评论；null = 不下发，服务端默认允许评论
                                if (closeComment) 1 else null,
                                // up_choose_comment（精选评论）暂未接线，null = 不下发
                                null,
                                // timer_pub_time 要秒级 int 时间戳，不是 "yyyy-MM-dd HH:mm" 字符串
                                if (timerSeconds > 0) timerSeconds.toInt() else null
                            )
                        } else null

                        val dynId: Long
                        if (voteId > 0) {
                            // 有投票，使用复杂动态发布并挂载投票
                            val attachCard = org.json.JSONObject().put("vote", org.json.JSONObject().put("vote_id", voteId))
                            val contents = DynamicApi.buildContents(text, atUids.ifEmpty { null }, emoteTexts)
                            dynId = DynamicApi.publishComplex(
                                contents, pics, option, null, if (pics != null) 2 else 1,
                                attachCard, null
                            )
                        } else if (pics != null) {
                            // 带图动态走 scene=2，图片已在 SendDynamicActivity 上传成 pics 节点
                            dynId = DynamicApi.publishImageContent(text, atUids.ifEmpty { null }, pics, option, emoteTexts)
                        } else if (atUids.isEmpty() && !DynamicApi.containsEmoteText(text, emoteTexts)) {
                            // 既没有 @ 也没有可用表情，继续走原来的纯文本接口，不无谓地换链路
                            dynId = if (option == null) DynamicApi.publishTextContent(text)
                            else DynamicApi.publishTextContent(text, null, option, null)
                        } else {
                            dynId = DynamicApi.publishTextContent(text, atUids.ifEmpty { null }, option, emoteTexts)
                        }
                        if (dynId != -1L) {
                            runOnUiThread { MsgUtil.showMsg(if (timerSeconds > 0) UiText.of("已设置定时发布~") else UiText.of("发送成功~")) }
                            // 定时发布的动态此刻还没真正发出去，getDynamic 拿回的状态不对，
                            // 插进列表只会显示一条"将来才发"的动态；跳过本地插入，由下拉刷新兜底
                            if (timerSeconds == 0L) CenterThreadPool.run {
                                try {
                                    val dynamic = DynamicApi.getDynamic(dynId)
                                    runOnUiThread {
                                        // list 突变与 Adapter 通知必须在同一线程：
                                        // 后台改 list、主线程通知的话，RecyclerView 下一次 layout 读到的
                                        // 可能是改了一半的 list，会偶发 IndexOutOfBoundsException
                                        dynamicList!!.add(0, dynamic)
                                        if (type == "all") {
                                            dynamicAdapter!!.notifyItemInserted(0)
                                            dynamicAdapter!!.notifyItemRangeChanged(0, dynamicList!!.size)
                                        }
                                    }
                                } catch (e: Exception) {
                                    MsgUtil.err(e)
                                }
                            }
                        } else {
                            runOnUiThread { MsgUtil.showMsg(UiText.of("发送失败")) }
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { MsgUtil.err(e) }
                }
            }
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMenuClick()
        Log.e("debug", "进入动态页")

        setOnRefreshListener { refreshDynamic() }
        setOnLoadMoreListener { page -> addDynamic(type) }
        // 空数据时可点击重试
        setOnEmptyRetry { refreshDynamic() }

        setPageName(UiText.of("动态"))

        // 教程改由 BaseActivity 按 Tutorials 注册表集中触发

        loadRecentUpList()
        refreshDynamic()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun refreshDynamic() {
        Log.e("debug", "刷新")
        if (firstRefresh) {
            dynamicList = ArrayList()
        } else {
            offset = 0
            bottom = false
            dynamicList!!.clear()
            dynamicAdapter!!.notifyDataSetChanged()
        }

        loadRecentUpList()
        addDynamic(type, true)
    }

    private fun addDynamic(type: String) {
        addDynamic(type, false)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun addDynamic(type: String, refresh: Boolean) {
        Log.e("debug", "加载下一页")
        CenterThreadPool.run {
            try {
                val list = ArrayList<Dynamic>()
                offset = DynamicApi.getDynamicList(list, offset, 0, type)
                bottom = (offset == -1L)
                setRefreshing(false)

                runOnUiThread {
                    dynamicList!!.addAll(list)
                    // 无内容时给出空态（一级页此前完全没有空态）
                    if (dynamicList!!.isEmpty()) showEmptyView() else hideEmptyView()
                    if (firstRefresh) {
                        firstRefresh = false
                        dynamicAdapter = DynamicAdapter(this, dynamicList!!, recyclerView, recentUpList)
                        setAdapter(dynamicAdapter!!)
                    } else {
                        if (refresh) {
                            dynamicAdapter!!.notifyDataSetChanged()
                        } else {
                            val offset = if (showRecentUp()) 2 else 1
                            dynamicAdapter!!.notifyItemRangeInserted(dynamicList!!.size - list.size + offset, list.size)
                        }
                    }
                    if (refresh) {
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.DYNAMIC_UPDATE_NUM, 0)
                    }
                }

            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }

    private fun loadRecentUpList() {
        CenterThreadPool.run {
            try {
                recentUpList = DynamicApi.getRecentUpList()
                runOnUiThread {
                    if (dynamicAdapter != null) {
                        dynamicAdapter!!.recentUpList = recentUpList
                        val shouldShow = showRecentUp()
                        val currentItemCount = dynamicAdapter!!.itemCount
                        val newItemCount = (if (dynamicList != null) dynamicList!!.size + 1 else 1) + (if (shouldShow) 1 else 0)
                        if (currentItemCount != newItemCount) {
                            if (shouldShow) {
                                dynamicAdapter!!.notifyItemInserted(1)
                            } else {
                                dynamicAdapter!!.notifyItemRemoved(1)
                            }
                        } else if (shouldShow) {
                            dynamicAdapter!!.notifyItemChanged(1)
                        }
                    }
                }
            } catch (e: Exception) {
                recentUpList = null
            }
        }
    }

    private fun showRecentUp(): Boolean {
        return SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.RECENT_UP_DISPLAY_ENABLE, true)
                && recentUpList != null && recentUpList!!.isNotEmpty()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DynamicHolder.GO_TO_INFO_REQUEST && resultCode == RESULT_OK) {
            try {
                if (data != null && !isRefreshing) {
                    val adapterPosition = data.getIntExtra("position", 0)
                    val offset = if (showRecentUp()) 2 else 1
                    val realPosition = adapterPosition - offset
                    if (realPosition >= 0 && realPosition < dynamicList!!.size) {
                        DynamicHolder.removeDynamicFromList(dynamicList!!, realPosition, dynamicAdapter!!, showRecentUp())
                    }
                }
            } catch (ignored: Throwable) {
            }
        }
    }
}