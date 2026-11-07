package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.Reply
import java.util.LinkedHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 一条评论的「跨页共享状态」快照。
 *
 * 只放会影响界面显示且可能被多个页面同时改动的字段：赞/踩、赞数、是否已删除。
 * 正文、作者等信息不进来——它们不会因为别处的操作而变化，放进来只会让合并规则变复杂。
 */
class ReplyState(
    val account: Long,
    val type: Int,
    val oid: Long,
    val rpid: Long,
    val root: Long,
    val liked: Boolean,
    val disliked: Boolean,
    val likeCount: Int,
    val deleted: Boolean,
    val revision: Long
) {
    /** 与另一份快照的可显示内容是否一致（revision 不参与比较）。 */
    fun hasSameContent(other: ReplyState?): Boolean =
        other != null &&
            other.liked == liked &&
            other.disliked == disliked &&
            other.likeCount == likeCount &&
            other.deleted == deleted
}

/**
 * 评论区状态的进程内共享存储。
 *
 * 要解决的问题：同一条评论可能同时显示在多个页面里——视频详情页的评论列表、评论详情页
 * （楼中楼）、动态详情页的评论列表。在详情页点了赞/踩，或者删掉了自己的一条回复，
 * 退回列表页时那条评论还是旧状态，要等下拉刷新才"突然变回来"，用户会以为操作没生效。
 *
 * 做法：
 * - 以 `account:type:oid:rpid` 为键保存最新状态（带账号维度——切号后不能把 A 的点赞状态给 B）；
 * - 点赞/点踩/删除成功后由 [ReplyAdapter] 写入（[recordReaction] / [markDeleted]），
 *   所有登记的 [Listener] 立即收到通知并刷新对应条目；
 * - 服务端列表刷新回来后用 [observe] 写入服务端快照，但**带 revision 保护**：
 *   本地动作比这次读更新时保留本地状态，避免「请求发出 → 用户点赞 → 响应回来把赞冲掉」；
 * - 容量有上限（LRU），防止长时间刷评论区把内存吃掉。
 *
 * 线程约束：[Listener] 在**调用方线程**上同步执行——本类不持有主线程 Handler，
 * 也不假设自己跑在主线程（[observe] 常由刷新回调在主线程调用）。
 * UI 侧必须在监听器里自己切主线程（见 `ReplyFragment` / `ReplyInfoActivity`）。
 */
class ReplyStateStore(private val capacity: Int = DEFAULT_CAPACITY) {

    /** 状态变化的监听器。实现方必须在页面销毁时 [removeListener]，否则会泄漏整个页面。 */
    interface Listener {
        fun onReplyStateChanged(state: ReplyState)
    }

    companion object {
        /** 默认容量：一次评论区浏览通常只有几十条，512 足够覆盖来回切换的几个页面。 */
        const val DEFAULT_CAPACITY = 512

        /** 全进程共用的实例。 */
        @JvmField
        val instance = ReplyStateStore()

        /** 拼 key。字段之间用冒号分隔，避免 oid/rpid 拼接后互相冒充。 */
        @JvmStatic
        fun keyOf(account: Long, type: Int, oid: Long, rpid: Long): String =
            "$account:$type:$oid:$rpid"
    }

    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<Listener>()

    private var revision: Long = 0L

    // 访问序 LinkedHashMap + 手动淘汰最老的 key = 最简 LRU：读也提到最近使用，长期不看的先淘汰。
    // （不用 removeEldestEntry 覆写：Kotlin 里覆写 java.util 的 protected 方法签名容易踩类型映射坑）
    private val states = LinkedHashMap<String, ReplyState>(capacity.coerceAtLeast(1), 0.75f, true)

    init {
        require(capacity > 0) { "ReplyStateStore capacity must be positive" }
    }

    /** 当前修订号。发请求前记下来，响应回来后作为 [observe] 的 read 参数。 */
    fun beginRead(): Long = synchronized(lock) { revision }

    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /** 只读取最新状态；没记录过返回 null。 */
    fun current(account: Long, type: Int, oid: Long, rpid: Long): ReplyState? =
        synchronized(lock) { states[keyOf(account, type, oid, rpid)] }

    /** 写入并维持 LRU 上限：超出容量时淘汰最久没被访问的那条。 */
    private fun putState(key: String, state: ReplyState) {
        states[key] = state
        if (states.size <= capacity) return
        val iterator = states.keys.iterator()
        if (iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }

    /**
     * 写入一份服务端快照（刷新/翻页拿到列表后逐条调用）。
     *
     * 合并规则（顺序即优先级）：
     * 1. 本地已经删除 → 保留已删除，服务端列表还没同步过来不算复活；
     * 2. 本地动作的 revision 比这次读更新 → 保留本地状态，别被晚到的响应冲掉；
     * 3. 否则以服务端快照覆盖，revision 记为这次读的修订号。
     *
     * **只在「已经存过、且内容真的变了」时通知监听器。**
     * 首屏/翻页第一次见到某条评论不算变化——那一行还没有任何监听者在显示它，
     * 通知只会让列表把刚绑好的行再重绑一遍（首屏 20 条 = 20 次无意义的重绑）。
     */
    fun observe(
        account: Long,
        type: Int,
        oid: Long,
        rpid: Long,
        root: Long,
        liked: Boolean,
        disliked: Boolean,
        likeCount: Int,
        read: Long
    ): ReplyState {
        val key = keyOf(account, type, oid, rpid)
        var changed: ReplyState? = null
        val result = synchronized(lock) {
            val old = states[key]
            if (old != null && (old.deleted || old.revision > read)) {
                old
            } else {
                val fresh = ReplyState(
                    account, type, oid, rpid, root, liked, disliked, likeCount, false, read
                )
                if (old != null && !fresh.hasSameContent(old)) changed = fresh
                putState(key, fresh)
                fresh
            }
        }
        changed?.let { notifyListeners(it) }
        return result
    }

    /** 对一整页评论（含楼中楼）逐条 [observe]。 */
    fun observeReplies(replies: List<Reply>?, account: Long, type: Int, oid: Long, read: Long) {
        if (replies == null) return
        for (reply in replies) {
            observe(
                account, type, oid, reply.rpid, reply.root,
                reply.liked, reply.disliked, reply.likeCount, read
            )
            val children = reply.childMsgList
            if (children != null && children.isNotEmpty()) {
                observeReplies(children, account, type, oid, read)
            }
        }
    }

    /**
     * 记录用户在本地完成的点赞/点踩结果（互斥已由调用方算好）。
     *
     * 这里不自己推算点赞数：+1/-1 的边界（取消赞、赞转踩、重复点击）在界面层已经算完，
     * 传进来的是**最终值**，避免两份计数逻辑各算各的。
     */
    fun recordReaction(
        account: Long,
        type: Int,
        oid: Long,
        rpid: Long,
        root: Long,
        liked: Boolean,
        disliked: Boolean,
        likeCount: Int
    ): ReplyState {
        val key = keyOf(account, type, oid, rpid)
        val state = synchronized(lock) {
            val next = ReplyState(
                account, type, oid, rpid, root, liked, disliked, likeCount, false, ++revision
            )
            putState(key, next)
            next
        }
        notifyListeners(state)
        return state
    }

    /** 记录一条评论已被删除（自己删的，或其他页面删的）。 */
    fun markDeleted(
        account: Long,
        type: Int,
        oid: Long,
        rpid: Long,
        root: Long
    ): ReplyState {
        val key = keyOf(account, type, oid, rpid)
        val state = synchronized(lock) {
            val old = states[key]
            val next = ReplyState(
                account, type, oid, rpid, root,
                old?.liked ?: false, old?.disliked ?: false, 0, true, ++revision
            )
            putState(key, next)
            next
        }
        notifyListeners(state)
        return state
    }

    /**
     * 把共享状态应用到一条评论上。
     *
     * @return true 表示这条评论已删除，调用方应当把它从列表里移除而不是继续显示
     */
    fun applyTo(reply: Reply, account: Long, type: Int, oid: Long): Boolean {
        val state = current(account, type, oid, reply.rpid) ?: return false
        reply.liked = state.liked
        reply.disliked = state.disliked
        reply.likeCount = state.likeCount
        return state.deleted
    }

    /**
     * 把共享状态应用到整页评论（含楼中楼），并把已删除的条目就地移除。
     *
     * 这是「在别的页面删了评论，回到这一页那一行自动消失」的落点。
     *
     * @return true 表示列表被改动过（调用方应当 notify）
     */
    fun applyLatest(replies: MutableList<Reply>?, account: Long, type: Int, oid: Long): Boolean {
        if (replies == null || replies.isEmpty()) return false
        var changed = false
        val iterator = replies.iterator()
        while (iterator.hasNext()) {
            val reply = iterator.next()
            if (applyTo(reply, account, type, oid)) {
                iterator.remove()
                changed = true
                continue
            }
            val children = reply.childMsgList
            if (children != null && children.isNotEmpty()) {
                val childIterator = children.iterator()
                var removedChildren = 0
                while (childIterator.hasNext()) {
                    val child = childIterator.next()
                    if (applyTo(child, account, type, oid)) {
                        childIterator.remove()
                        removedChildren++
                    }
                }
                if (removedChildren > 0) {
                    reply.childCount = (reply.childCount - removedChildren).coerceAtLeast(0)
                    changed = true
                }
            }
        }
        return changed
    }

    /** 清空全部状态（切号/退出登录时调用）。监听器不清，由各页面自己摘。 */
    fun clear() {
        synchronized(lock) { states.clear() }
    }

    private fun notifyListeners(state: ReplyState) {
        if (listeners.isEmpty()) return
        for (listener in listeners) {
            try {
                listener.onReplyStateChanged(state)
            } catch (e: Exception) {
                // 一个页面刷新失败不能拖累其它页面；监听器自己负责把异常挡在 UI 层
                Logu.e("评论状态", "监听器刷新失败：" + e.message)
            }
        }
    }
}
