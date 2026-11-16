package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.model.FeedbackItem
import com.RobinNotBad.BiliClient.model.FeedbackReply

/**
 * 「我的反馈」的新回复提醒（26.11.16 新增）。
 *
 * <h3>要解决的事</h3>
 * 用户发了反馈之后，开发者的回复是**服务端写进去的**，用户这边不会有任何通知：
 * 不开 App 就永远不知道有人回了他。App 也没有通知权限的既有链路
 * （手表端通知本就不可靠），所以提醒只做在**站内**：
 * 1. 启动时拉一次「我的反馈」，有没看过的新回复就在首屏弹一次提示（[BiliTerminal]）；
 * 2. 「我的反馈」列表项上挂一个「新回复」标签；
 * 3. 设置里的「我的反馈」入口副标题带「· N 条新回复」。
 *
 * <h3>「看过」怎么判定（水位线）</h3>
 * 不存「已读/未读」布尔量：那需要服务端配合（或每次把整个列表状态回传），而这条路
 * 跟服务端没有任何新协议。改成**按开发者回复的 id 存水位线**——服务端下发的开发者
 * 回复 id 是「公告 id + 1e9」，单调递增，所以「这条反馈我看过的最大开发者回复 id」
 * 就足以判断有没有更新的回复。水位线只增不减。
 *
 * <h3>为什么不引第三方</h3>
 * 这里没有推送、没有长连接，只有 SharedPreferences 里的一批键 + 一次列表请求。
 */
object FeedbackUnread {

    /** 「我的反馈」入口上的未读数缓存（跨页面读，避免每次进设置都重算一遍列表）。 */
    const val KEY_UNREAD_COUNT = "terminal_feedback_unread_count"

    /** 每条反馈各自的水位线键前缀，后面拼 feedback.id。 */
    const val KEY_SEEN_PREFIX = "terminal_feedback_seen_"

    /**
     * 这一串回复里最新的**开发者**回复 id；没有开发者回复返回 0。
     *
     * 纯函数（不读 Context、不碰存档），单测直接调。
     */
    @JvmStatic
    fun latestDevReplyId(replies: List<FeedbackReply>?): Int {
        if (replies.isNullOrEmpty()) return 0
        var latest = 0
        for (reply in replies) {
            if (reply == null || reply.isFromUser()) continue
            if (reply.id > latest) latest = reply.id
        }
        return latest
    }

    /** 这条反馈在给定水位线下是否算「有新回复」。纯函数，单测直接调。 */
    @JvmStatic
    fun isUnread(item: FeedbackItem?, seenId: Int): Boolean =
        latestDevReplyId(item?.replies) > seenId

    /**
     * 数一数有几条有新回复。[seenOf] 取某条反馈的水位线，
     * 注入进来是为了让单测不碰存档。
     */
    @JvmStatic
    fun countUnread(items: List<FeedbackItem>?, seenOf: (Int) -> Int): Int {
        if (items.isNullOrEmpty()) return 0
        var count = 0
        for (item in items) {
            if (item != null && isUnread(item, seenOf(item.id))) count++
        }
        return count
    }

    /** 某条反馈已看到的最大开发者回复 id（0 = 一条都没看过）。 */
    fun seenId(feedbackId: Int): Int = SharedPreferencesUtil.getInt(KEY_SEEN_PREFIX + feedbackId, 0)

    /** 这条反馈现在有没有没看过的新回复（读存档版）。 */
    fun isUnread(item: FeedbackItem?): Boolean {
        val feedbackId = item?.id ?: return false
        return isUnread(item, seenId(feedbackId))
    }

    /**
     * 标记「这条我看过了」：把水位线推到它当前最新的开发者回复。
     *
     * 没有开发者回复时不写：写 0 只会让存档里多一堆没意义的键。
     */
    fun markSeen(item: FeedbackItem?) {
        val feedbackId = item?.id ?: return
        val latest = latestDevReplyId(item.replies)
        if (latest <= 0) return
        if (latest <= seenId(feedbackId)) return
        SharedPreferencesUtil.putInt(KEY_SEEN_PREFIX + feedbackId, latest)
    }

    /**
     * 用刚拉到的一份列表重算未读数并写进缓存，返回未读数。
     *
     * 每次拉列表都调它（列表页 / 启动检查），这样入口上的「· N 条新回复」不会过期。
     */
    fun refreshCount(items: List<FeedbackItem>?): Int {
        val count = countUnread(items) { seenId(it) }
        SharedPreferencesUtil.putInt(KEY_UNREAD_COUNT, count)
        return count
    }

    /** 缓存的未读数（不发起任何请求）。 */
    fun cachedCount(): Int = SharedPreferencesUtil.getInt(KEY_UNREAD_COUNT, 0)

    /**
     * 拼在「我的反馈」入口副标题后面的后缀：有新回复时是「 · N 条新回复」，否则空串。
     *
     * 为什么不单独做一个红点控件：设置页的行是纯文本行（见 SettingsIndex.Entry 只有
     * name/desc），没有徽标位。塞进副标题是这套 UI 里唯一不改布局就能表达「有新东西」的位置。
     */
    fun badgeSuffix(): String {
        val count = cachedCount()
        if (count <= 0) return ""
        val context = BiliTerminal.context ?: return ""
        return " · " + context.getString(R.string.text_feedback_unread_badge, count)
    }
}
