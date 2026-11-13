package com.RobinNotBad.BiliClient.activity.message

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.message.PrivateMsgSessionsAdapter
import com.RobinNotBad.BiliClient.api.PrivateMsgApi
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SessionSorter

/**
 * 选好友页（反馈 #133「把视频发给好友」）。
 *
 * 复用私信会话列表当「好友选择器」：B 站没有给第三方用的好友接口，能列出来的、也最可能
 * 愿意收消息的人就是最近聊过的会话，所以直接拿 [PrivateMsgApi.getSessionsList] 的结果当名单，
 * 用 [PrivateMsgSessionsAdapter] 的选好友模式渲染——不另造一套列表 UI。
 *
 * 结果通过 `setResult(RESULT_OK)` 回传，调用方（[com.RobinNotBad.BiliClient.activity.video.info.VideoInfoFragment]）
 * 拿 uid 去发私信。
 */
class ChooseFriendActivity : RefreshListActivity() {

    companion object {
        /** 回传的接收者 mid */
        const val EXTRA_UID = "uid"

        /** 回传的接收者昵称（只用于「已发送给 xxx」提示，发送本身不依赖它） */
        const val EXTRA_NAME = "name"

        /**
         * 一次拉多少条会话。
         *
         * 会话接口只有 size、没有翻页参数（见 getSessionsList），所以这里直接要一个比消息页
         * 更大的数量；50 足够覆盖「最近聊过的人」，再大只是让首屏变慢。
         */
        private const val SESSION_SIZE = 50
    }

    /**
     * 代次 token：每次 [loadSessions] 自增，旧请求回来时值对不上就丢弃。
     *
     * 下拉刷新和空态重试都能重复触发加载，没有它时先发的慢响应会覆盖后发的快响应。
     */
    @Volatile
    private var loadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName(getString(R.string.pagename_choose_friend))
        setOnRefreshListener { loadSessions() }
        // 空态条同时是重试按钮：没网时点一下就重拉，不用退出去重进
        setOnEmptyRetry(Runnable { loadSessions() })
        loadSessions()
    }

    private fun loadSessions() {
        val generation = ++loadGeneration
        setRefreshing(true)
        CenterThreadPool.run {
            try {
                val sessionsList = PrivateMsgApi.getSessionsList(SESSION_SIZE)
                // 与消息页同一套排序：置顶优先 → 未读优先。用户找人的顺序和看消息一致
                SessionSorter.sort(sessionsList)
                val uidList = ArrayList<Long>()
                for (item in sessionsList) uidList.add(item.talkerUid)
                val userMap = PrivateMsgApi.getUsersInfo(uidList)
                runOnUiThread {
                    if (isDestroyed || generation != loadGeneration) return@runOnUiThread
                    val adapter = PrivateMsgSessionsAdapter(
                        this, sessionsList, userMap,
                        onSessionClick = { session ->
                            val user: UserInfo? = userMap[session.talkerUid]
                            pickFriend(session.talkerUid, user?.name.orEmpty())
                        }
                    )
                    recyclerView.layoutManager = CustomLinearManager(this)
                    setAdapter(adapter)
                    // 会话接口不分页，一次就是全部：直接标记到底，免得列表底部还在转「加载中」
                    bottom = true
                    if (sessionsList.isEmpty()) showEmptyView() else hideEmptyView()
                    setRefreshing(false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isDestroyed || generation != loadGeneration) return@runOnUiThread
                    setRefreshing(false)
                    // 列表还是空的就留出空态+重试入口，别让用户对着一片空白
                    if (recyclerView.adapter == null) showEmptyView()
                    MsgUtil.err(e)
                }
            }
        }
    }

    private fun pickFriend(uid: Long, name: String) {
        if (uid <= 0) return
        val data = Intent()
        data.putExtra(EXTRA_UID, uid)
        data.putExtra(EXTRA_NAME, name)
        setResult(Activity.RESULT_OK, data)
        finish()
    }
}
