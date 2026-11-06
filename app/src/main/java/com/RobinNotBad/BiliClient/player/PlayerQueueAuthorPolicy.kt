package com.RobinNotBad.BiliClient.player

/**
 * 「按 UP 主投稿连播」用谁当作者 —— 纯逻辑，可在 JVM 单测里直接跑。
 *
 * <p>**背景（26.11.06 修的 bug）**：`PlayerData.mid` 是**当前登录账号**的 mid（直播弹幕 uid、
 * 登录态判据用它），`PlayerData.uploaderMid` 才是这条视频的作者。两者历史上共用一个字段，
 * 于是连播拉到的是登录账号自己的投稿、连播层标题也变成自己的昵称。
 *
 * <p>这里把「什么时候能点连播」「换到另一条视频后作者取谁」收成纯函数，
 * 免得这两条判据又散回 Activity 里、下次改坏时单测拦不住。
 */
object PlayerQueueAuthorPolicy {

    /**
     * 能不能点「连播」（按 UP 主投稿拉队列）。
     *
     * @param uploaderMid 已知的视频作者；0 = 入口没带（旧会话、只拿得到 aid 的入口），
     *                    此时靠 [aid] 现查，所以**不是**"必须已知作者"才能点。
     * @param aid         当前视频 aid；现查作者要用它，<= 0 表示连现查都无从查起。
     */
    fun canLoadUploader(
        isLive: Boolean,
        isPgc: Boolean,
        isOnline: Boolean,
        uploaderMid: Long,
        aid: Long
    ): Boolean = !isLive && !isPgc && isOnline && (uploaderMid > 0 || aid > 0)

    /**
     * 切到另一条视频之后应采用的作者 mid。
     *
     * @param newVideoAuthorMid 新条目的作者（取自它的 `videoInfo`）；**null 表示这次切页没有新条目**
     *        （真实多P 换P，还是同一条视频），沿用 [current]。
     *        非 null 时**原样采用，允许 0**：0 = "这条视频的作者未知"，之后点连播会按 aid 现查。
     *        **绝不能退回上一条视频的作者** —— 那与"连播拉到别人的投稿"是同一个 bug 的另一种长相
     *        （收藏夹里 UP 已注销的老视频 `staff` 为空就会走到这里）。
     */
    fun authorAfterSwitch(newVideoAuthorMid: Long?, current: Long): Long =
        newVideoAuthorMid ?: current
}
