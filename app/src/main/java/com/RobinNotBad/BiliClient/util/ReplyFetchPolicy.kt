package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.api.ReplyApi
import com.RobinNotBad.BiliClient.model.Reply

/**
 * 评论区「三层兜底」的纯策略。
 *
 * 背景：服务端评论区有三个已知的坑，界面层原先都没兜住——
 *
 * 1. **跨页重复**：翻页/下拉刷新时同一 rpid 可能被返回两次（置顶、游标回退、事件插入后再拉到），
 *    表现为列表里同一条评论出现两遍、或者通知位号算错后整行错位；
 * 2. **首屏空列表**：服务端偶发在当前排序档位回一页空 replies，而累计评论数明明大于 0，
 *    此时页面直接空掉，用户以为这条视频没评论；
 * 3. **数据源类型不符**：`type` 传错（比如把动态的评论区按视频类型请求）时接口不报错、只回空，
 *    以前只能看到空白评论区。
 *
 * 这里集中放「取哪一页/要不要换排序/要不要换类型」的判定，以及自己刚发评论的本地补回缓存。
 * 全部不碰网络、不碰 Android，可以直接在 JVM 单测里钉死（见 `ReplyFetchPolicyTest`）。
 *
 * 真正的请求仍在 [ReplyApi] 里发；[com.RobinNotBad.BiliClient.activity.reply.ReplyFragment]
 * 只负责按这里给出的顺序去试。
 */
object ReplyFetchPolicy {

    /** 自己刚发的评论最多补回多久：过了这个窗口就不该再强行塞回列表（服务端通常早已同步）。 */
    const val LOCAL_REPLY_WINDOW_MS = 5 * 60 * 1000L

    /** 本地补回缓存最多留多少条，防止长时间刷评论区把内存吃掉。 */
    const val MAX_LOCAL_REPLIES = 20

    /**
     * 首屏拿不到内容时依次尝试的评论区类型。
     *
     * 顺序有讲究：动态（17）与专栏（12）的接口形状与视频一致，最常被误传；
     * 最后才回到视频（1），避免对真正的视频评论区反复重试。
     */
    private val FALLBACK_REPLY_TYPES = intArrayOf(
        ReplyApi.REPLY_TYPE_DYNAMIC,
        ReplyApi.REPLY_TYPE_ARTICLE,
        ReplyApi.REPLY_TYPE_VIDEO
    )

    /**
     * 一次「首屏兜底」尝试：用哪种评论区类型、哪种排序去重拉。
     *
     * @property type 评论区类型（见 [ReplyApi.REPLY_TYPE_VIDEO] 等常量）
     * @property sort 排序档位（[Reply.SORT_TIME] / [Reply.SORT_LIKE]）
     */
    data class FirstPageAttempt(val type: Int, val sort: Int)

    /**
     * 除主类型外还可以试的评论区类型（保持 [FALLBACK_REPLY_TYPES] 的优先顺序）。
     *
     * @param primaryType 主路径已经用过的类型，会被排除，避免原样重试
     */
    @JvmStatic
    fun alternativeReplyTypes(primaryType: Int): List<Int> =
        FALLBACK_REPLY_TYPES.filter { it != primaryType }

    /**
     * 首屏兜底计划：空列表且服务端确实有评论时按这个顺序逐个重拉。
     *
     * 第一步先换「按时间」排序——服务端在「热度/回复数」档位下对冷门内容常回空页，
     * 而按时间永远有数据；之后才逐个换评论区类型。返回空列表表示没有可试的档位。
     *
     * @param primaryType 首次请求用的评论区类型
     * @param primarySort 首次请求用的排序
     */
    @JvmStatic
    fun firstPageFallbackPlan(primaryType: Int, primarySort: Int): List<FirstPageAttempt> {
        val attempts = ArrayList<FirstPageAttempt>(FALLBACK_REPLY_TYPES.size)
        if (primarySort != Reply.SORT_TIME) {
            attempts.add(FirstPageAttempt(primaryType, Reply.SORT_TIME))
        }
        for (type in alternativeReplyTypes(primaryType)) {
            attempts.add(FirstPageAttempt(type, Reply.SORT_TIME))
        }
        return attempts
    }

    /**
     * 首屏要不要进入兜底重试。
     *
     * 三个条件缺一不可：
     * - 拿回来的列表是空的（有内容就没必要重拉）；
     * - 已知评论总数大于 0（否则「这条视频本来就没人评论」会被无意义地重试三次）；
     * - 不是 seek 定位模式（`seek_rpid` 请求的是一段楼中楼，换排序/换类型都会破坏定位）。
     *
     * @param loadedSize    首次请求拿到的条数
     * @param declaredCount 已知的评论总数（`count` 字段或 `getReplyCount`）；<=0 表示未知/无评论
     * @param seekRpid      seek 定位的 rpid，0 表示普通列表
     */
    @JvmStatic
    fun shouldTryFirstPageFallback(loadedSize: Int, declaredCount: Int, seekRpid: Long): Boolean =
        loadedSize <= 0 && declaredCount > 0 && seekRpid <= 0
}

/**
 * 自己刚发的评论的本地缓存。
 *
 * 为什么需要：发完评论立刻下拉刷新时，服务端列表往往还没同步到这条（审核/缓存延迟），
 * 刷新会把刚回显的评论冲掉，用户以为发送失败了。这里把新评论记下来，
 * 刷新/分页拿到的服务端列表里没有它时补回列表顶部。
 *
 * 线程安全：`remember` 在 EventBus 回调（后台线程）、`mergeInto` 在刷新回调（主线程）里调用，
 * 所有方法都加了锁。不持 Context、不碰视图，可在 JVM 单测里直接构造。
 *
 * @param windowMs 超过这个时间的本地评论视为已同步，不再补回
 * @param maxSize  最多保留条数，超出后丢最早的
 */
class LocalReplyCache(
    private val windowMs: Long = ReplyFetchPolicy.LOCAL_REPLY_WINDOW_MS,
    private val maxSize: Int = ReplyFetchPolicy.MAX_LOCAL_REPLIES
) {

    private val replies = ArrayList<Reply>()
    private val postedAt = HashMap<Long, Long>()

    /**
     * 记住一条自己刚发的评论。
     *
     * rpid 无效（<=0，服务端没回 id 的脏数据）直接忽略：没有 id 就无法去重，补回列表只会制造重复。
     * 同一 rpid 重复回写（EventBus sticky 事件重投递）只刷新时间戳，不重复入队。
     */
    @Synchronized
    fun remember(reply: Reply, nowMs: Long) {
        if (reply.rpid <= 0) return
        prune(nowMs)
        val oldTime = postedAt[reply.rpid]
        if (oldTime != null) {
            postedAt[reply.rpid] = nowMs
            return
        }
        replies.add(reply)
        postedAt[reply.rpid] = nowMs
        while (replies.size > maxSize) {
            val removed = replies.removeAt(0)
            postedAt.remove(removed.rpid)
        }
    }

    /** 丢掉超过窗口的本地评论，返回丢掉的条数。 */
    @Synchronized
    fun prune(nowMs: Long): Int {
        if (replies.isEmpty()) return 0
        var removed = 0
        val iterator = replies.iterator()
        while (iterator.hasNext()) {
            val reply = iterator.next()
            val at = postedAt[reply.rpid] ?: 0L
            if (nowMs - at > windowMs) {
                iterator.remove()
                postedAt.remove(reply.rpid)
                removed++
            }
        }
        return removed
    }

    /**
     * 把还没过窗口、且 [target] 里还没有的本地评论补回列表顶部。
     *
     * 补入顺序：按发送先后插到下标 0，最终结果与发送顺序一致
     * （先发的在上、后发的在下），连续发两条时不会看起来像是被随机打乱。
     *
     * @return 真正补进去的评论（调用方据此把它们也登记进「已加载 rpid」，防止翻页再拉一遍）
     */
    @Synchronized
    fun mergeInto(target: MutableList<Reply>, nowMs: Long): List<Reply> {
        prune(nowMs)
        if (replies.isEmpty()) return emptyList()

        val existing = HashSet<Long>(target.size * 2 + 4)
        for (reply in target) {
            if (reply.rpid > 0) existing.add(reply.rpid)
            val children = reply.childMsgList
            if (children != null) {
                for (child in children) {
                    if (child.rpid > 0) existing.add(child.rpid)
                }
            }
        }

        val inserted = ArrayList<Reply>(replies.size)
        for (i in replies.indices.reversed()) {
            val reply = replies[i]
            if (reply.rpid <= 0 || existing.contains(reply.rpid)) continue
            target.add(0, reply)
            existing.add(reply.rpid)
            inserted.add(reply)
        }
        return inserted
    }

    /** 当前缓存条数（已过窗口的会先被清掉）。 */
    @Synchronized
    fun size(nowMs: Long): Int {
        prune(nowMs)
        return replies.size
    }

    /** 清空缓存（换视频/换评论区时调用）。 */
    @Synchronized
    fun clear() {
        replies.clear()
        postedAt.clear()
    }
}
