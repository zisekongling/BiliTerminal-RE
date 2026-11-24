package com.RobinNotBad.BiliClient.util

/**
 * #236：点评论正文该去哪儿。
 *
 * 反馈里三处缺口是同一件事的三个面：
 * 1. 详情页（ReplyInfoActivity）里点正文完全没监听，点一条有楼中楼的评论毫无反应；
 * 2. 详情页里没有子回复的评论点了也没反应，用户分不清「没回复」和「按钮坏了」；
 * 3. 详情页里再点又叠一层同 rpid 的楼层页，返回要按好几下。
 *
 * 纯函数，方便单测；判「是不是同一个楼层页」需要当前页的 rpid，由调用方传进来。
 */
object ReplyTapPolicy {
    enum class TapResult {
        /** 进楼层页（ReplyInfoActivity） */
        OPEN_FLOOR,

        /** 这条评论本来就没有楼中楼，给个提示 */
        TOAST_NO_CHILD,

        /** 已经在同一条评论的楼层页里，什么都不做，避免叠层 */
        IGNORE
    }

    /**
     * @param childCount 这条评论的子回复数（Reply.childCount）
     * @param inDetailPage 当前是否在楼层页里（ReplyAdapter.isDetail）
     * @param sameRpid 当前楼层页的根评论 rpid 是否就是这条（不在楼层页时传 false）
     *
     * 列表页里点没有子回复的评论仍然进评论详情页（打开后能看到这条评论本身），保持原有行为；
     * 只有在楼层页里点「没有子回复的评论」才提示，因为那里再进一层没有任何新内容。
     */
    @JvmStatic
    fun forBodyTap(childCount: Int, inDetailPage: Boolean, sameRpid: Boolean): TapResult {
        if (sameRpid) return TapResult.IGNORE
        if (childCount > 0) return TapResult.OPEN_FLOOR
        return if (inDetailPage) TapResult.TOAST_NO_CHILD else TapResult.OPEN_FLOOR
    }
}
