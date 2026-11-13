package com.RobinNotBad.BiliClient.activity.message

import com.RobinNotBad.BiliClient.util.UiText

import android.os.Bundle
import android.util.Pair

import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.message.NoticeAdapter
import com.RobinNotBad.BiliClient.api.MessageApi
import com.RobinNotBad.BiliClient.model.MessageCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil

class NoticeActivity : RefreshListActivity() {
    private var messageList: MutableList<MessageCard> = mutableListOf()
    private var noticeAdapter: NoticeAdapter? = null
    private var cursor: MessageCard.Cursor? = null
    private var pageType: String? = null

    /**
     * 代次 token：下拉刷新 / 回到前台重拉都会 ++，让在途的旧请求作废，
     * 免得旧响应晚到后把刚刷出来的新列表又覆盖回去。
     */
    @Volatile
    private var loadGeneration = 0

    /** onCreate 之后 onResume 会紧跟一次，第一次不算「回到前台」 */
    private var resumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setPageName(UiText.of("详情"))

        // 跳转方未传 type 时必须直接结束：否则 when 全不匹配、cursor 保持 null，
        // 翻页时 cursor!! 必然 NPE（此前闪退的一个来源）。
        val type = intent.getStringExtra("type")
        if (type == null) {
            finish()
            return
        }
        pageType = type
        messageList = ArrayList()

        setOnLoadMoreListener { i -> continueLoading(i) }
        // 下拉刷新：新点赞 / 新回复只能靠重新拉第一页才看得到（26.11.11 反馈 #150）
        setOnRefreshListener { loadFirstPage() }
        // 首屏失败时点空态文案重试
        setOnEmptyRetry(Runnable { loadFirstPage() })
        loadFirstPage()
    }

    /**
     * 回到前台重拉第一页。原来只在 onCreate 拉一次：切出去再回来、或从通知点进来，
     * 看到的还是进页面那一刻的旧列表，新回复 / 新点赞永远不出现（26.11.11 反馈 #150）。
     */
    override fun onResume() {
        super.onResume()
        if (!resumedOnce) {
            resumedOnce = true
            return
        }
        if (pageType != null) loadFirstPage()
    }

    /**
     * 首屏加载 / 下拉刷新 / 回到前台重拉都走这里。
     *
     * 两个关键点：
     * 1. 无论成功失败都必须调用 setRefreshing(false) 复位刷新状态 —— 基类 RefreshListActivity
     *    的翻页入口守卫含 swipeRefreshLayout.isRefreshing，首屏失败时若不复位，列表会永久卡死；
     * 2. **失败不等于到底**：旧代码在 catch 里 bottom = true，一次请求失败之后就再也不能翻页，
     *    断网进页面就是永久空白（26.11.11 反馈 #150）。这里失败只回 bottom = false 并给可重试空态。
     */
    private fun loadFirstPage() {
        val gen = ++loadGeneration
        setRefreshing(true)
        CenterThreadPool.run {
            try {
                var newCursor: MessageCard.Cursor? = null
                val fresh = ArrayList<MessageCard>()
                when (pageType) {
                    "like" -> {
                        val pair = MessageApi.getLikeMsg(0, 0)
                        newCursor = pair.first
                        fresh.addAll(pair.second)
                    }
                    "reply" -> {
                        val pair = MessageApi.getReplyMsg(0, 0)
                        newCursor = pair.first
                        fresh.addAll(pair.second)
                    }
                    "at" -> {
                        val pair = MessageApi.getAtMsg(0, 0)
                        newCursor = pair.first
                        fresh.addAll(pair.second)
                    }
                    // 系统通知接口没有游标：一次性列表
                    "system" -> fresh.addAll(MessageApi.getSystemMsg())
                }

                runOnUiThread {
                    // Activity 已销毁、或这次请求已被更新的请求作废时直接丢弃
                    if (isDestroyed || gen != loadGeneration) return@runOnUiThread
                    cursor = newCursor
                    // 就地替换内容：NoticeAdapter 持有的是同一个 list 引用，直接换引用会继续显示旧数据
                    messageList.clear()
                    messageList.addAll(fresh)
                    page = 1
                    bottom = "system" == pageType || (newCursor?.is_end ?: false)
                    if (noticeAdapter == null) {
                        noticeAdapter = NoticeAdapter(this, messageList)
                        setAdapter(noticeAdapter!!)
                    } else {
                        noticeAdapter!!.notifyDataSetChanged()
                    }
                    if (messageList.isEmpty()) showEmptyView() else hideEmptyView()
                    setRefreshing(false)
                }
            } catch (e: Exception) {
                MsgUtil.err(e)
                runOnUiThread {
                    if (isDestroyed || gen != loadGeneration) return@runOnUiThread
                    bottom = false
                    if (messageList.isEmpty()) showEmptyView()
                    setRefreshing(false)
                }
            }
        }
    }

    private fun continueLoading(i: Int) {
        val gen = loadGeneration
        CenterThreadPool.run {
            try {
                val cur = cursor
                // 系统通知无游标、首屏失败也已置 bottom=true：直接复位返回，
                // 否则走到下面的 cursor!!（旧代码）必然 NPE
                if ("system" == pageType || cur == null) {
                    runOnUiThread {
                        if (isDestroyed) return@runOnUiThread
                        bottom = true
                        setRefreshing(false)
                    }
                    return@run
                }

                val lastSize = messageList.size
                var pair: Pair<MessageCard.Cursor, List<MessageCard>>?
                when (pageType) {
                    "like" -> {
                        pair = MessageApi.getLikeMsg(cur.id, cur.time)
                        cursor = pair!!.first
                        messageList.addAll(pair.second)
                    }
                    "reply" -> {
                        pair = MessageApi.getReplyMsg(cur.id, cur.time)
                        cursor = pair!!.first
                        messageList.addAll(pair.second)
                    }
                    "at" -> {
                        pair = MessageApi.getAtMsg(cur.id, cur.time)
                        cursor = pair!!.first
                        messageList.addAll(pair.second)
                    }
                    else -> {
                        // 未知 type（理论上不会发生）：不要继续翻页，直接收尾
                        runOnUiThread {
                            if (isDestroyed) return@runOnUiThread
                            bottom = true
                            setRefreshing(false)
                        }
                        return@run
                    }
                }

                // 列表变更与状态写入统一收敛到主线程，避免后台线程改 UI 数据。
                // bottom 本身已是基类的 @Volatile 字段（见 RefreshListActivity.bottom），
                // 但 notifyItemRangeInserted 只能在主线程调，所以这里仍然整体切主线程
                if (gen != loadGeneration) return@run
                val newCursor = cursor
                runOnUiThread {
                    if (isDestroyed || gen != loadGeneration) return@runOnUiThread
                    noticeAdapter?.notifyItemRangeInserted(lastSize, messageList.size - lastSize)
                    bottom = newCursor?.is_end ?: true
                    setRefreshing(false)
                }
            } catch (e: Exception) {
                MsgUtil.err(e)
                runOnUiThread {
                    if (isDestroyed || gen != loadGeneration) return@runOnUiThread
                    // 失败只是这一页没拿到，**不是到底**：只回退页码并复位刷新，
                    // bottom 保持 false，用户滑到底还能再试（旧代码在这里 bottom = true，
                    // 一次失败就永久锁死翻页）
                    page--
                    setRefreshing(false)
                }
            }
        }
    }
}
