package com.RobinNotBad.BiliClient.ui.shortvideo

import com.RobinNotBad.BiliClient.model.ShortVideoItem
import com.RobinNotBad.BiliClient.model.VideoCard

/**
 * 「只看 TA」与作者卡的纯逻辑（26.11 短视频新版）。
 *
 * 全部是纯函数：不碰 Android 资源、不发请求，JVM 单测直接跑
 * （见 `app/src/test/.../ShortVideoAuthorPolicyTest.kt`）。
 */
object ShortVideoAuthorPolicy {

    /**
     * 统计副行拼接：`粉丝 · 投稿 · 获赞`。
     * 传 null / 空串的段直接跳过（未登录拿不到获赞、navnum 失败拿不到投稿时优雅降级），
     * 不显示 0、不留多余分隔符。各段文案由调用方从资源取好后传入。
     */
    @JvmStatic
    fun statLine(vararg parts: String?): String {
        return parts.filter { !it.isNullOrEmpty() }.joinToString(" · ")
    }

    /**
     * 作者流排序：当前播放的这条钉在第 0 位继续播，其余按服务端返回的发布时间倒序原样接上。
     * 按 aid 去重（当前条可能与列表重复、服务端分页也可能重复），[head] 为 null 时只去重。
     */
    @JvmStatic
    fun orderAuthorItems(head: ShortVideoItem?, fetched: List<ShortVideoItem>): List<ShortVideoItem> {
        val result = ArrayList<ShortVideoItem>()
        val seen = HashSet<Long>()
        if (head != null && head.aid > 0) {
            result.add(head)
            seen.add(head.aid)
        }
        for (item in fetched) {
            if (item.aid > 0 && seen.add(item.aid)) result.add(item)
        }
        return result
    }

    /**
     * 退出「只看 TA」回推荐流的落点：记住的位置的**下一条**（设计稿口径），
     * 越界收到最后一条；推荐流为空时回 0。
     */
    @JvmStatic
    fun exitRestoreIndex(savedIndex: Int, recommendCount: Int): Int {
        if (recommendCount <= 0) return 0
        return (savedIndex + 1).coerceIn(0, recommendCount - 1)
    }

    /** 是否该提示「TA 的投稿到底了」：已拉空且停在最后一条。不自动回推荐流。 */
    @JvmStatic
    fun shouldNotifyEnd(index: Int, count: Int, exhausted: Boolean): Boolean {
        return exhausted && count > 0 && index >= count - 1
    }

    /**
     * 空间投稿 [VideoCard] → 短视频条目。
     *
     * 注意：这条链路**拿不到 cid / duration**（arc/search 的 vlist 不返回），
     * cid 由 `AuthorFeedSource.resolvePlayable` 在取流前经 `VideoInfoApi.getVideoInfo` 补齐；
     * duration 缺失只影响进度条总长显示，取流后由播放器实际时长兜底。
     */
    @JvmStatic
    fun itemFromCard(card: VideoCard, mid: Long, authorFace: String): ShortVideoItem {
        // getUserVideos 拼的 view 是「toWan(play) + "观看"」，这里剥掉接口侧后缀还原纯数字文案，
        // 属于协议字符串处理，不是用户可见文案。
        val view = if (card.view != null && card.view.endsWith("观看")) {
            card.view.substring(0, card.view.length - 2)
        } else {
            card.view ?: ""
        }
        return ShortVideoItem(
            aid = card.aid,
            bvid = card.bvid ?: "",
            cid = card.cid,
            title = card.title ?: "",
            cover = card.cover ?: "",
            duration = card.duration,
            authorName = card.upName ?: "",
            authorFace = authorFace,
            authorMid = mid,
            viewCount = view
        )
    }

    /**
     * 「只看 {名字}」chip 里名字的最大宽度：chip 总宽上限减去头像、关闭钮、间距与内边距，
     * 保证整颗 chip 不超设计稿上限（圆屏 141dp / 方屏 150dp，随屏缩放）。
     * [gap] 按两处计（头像-名字、名字-✕），[paddingH] 是左右内边距**合计**。
     */
    @JvmStatic
    fun chipTextMaxWidth(
        chipMax: Float,
        avatarSize: Float,
        closeSize: Float,
        gap: Float,
        paddingH: Float
    ): Float {
        return (chipMax - avatarSize - closeSize - gap * 2 - paddingH).coerceAtLeast(0f)
    }
}