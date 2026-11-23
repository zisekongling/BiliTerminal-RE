package com.RobinNotBad.BiliClient.util.view

/**
 * 各页面「分享 / 转发」面板用到的链接拼装与分享文案（纯函数，可 JVM 单测）。
 *
 * 为什么单独抽出来：
 * 1. 视频、番剧、直播、专栏、收藏夹的链接形态各不相同，散在各页面里各拼一遍，早晚拼出四五种写法；
 * 2. 拼装逻辑是纯字符串，能直接单测（见 ShareTextsTest），不用上模拟器。
 *
 * 口径来自 PiliPlus 各页面的分享面板（视频 ugc/pgc controller、live_room、article），
 * 只保留「链接长什么样」「分享文本怎么拼」这两件事，界面相关的一律放在 ShareSheet。
 *
 * 链接规则：
 * - 视频：https://www.bilibili.com/video/BV...
 * - 番剧：https://www.bilibili.com/bangumi/play/ep<epid>
 * - 直播：https://live.bilibili.com/<roomId>
 * - 专栏：https://www.bilibili.com/read/cv<cvId>
 * - 收藏夹：https://www.bilibili.com/medialist/detail/ml<mediaId>
 * - 动态 / 图文：https://www.bilibili.com/opus/<id>
 */
object ShareTexts {

    /** 站点根；所有页面链接都从它拼起，改域名只改这一处。 */
    const val BASE_URL = "https://www.bilibili.com"

    /** 标题在分享文本里的最大字数，超出截断加省略号（私信正文有 2000 字节上限，长标题会把上限顶穿）。 */
    const val TITLE_MAX = 60

    /** 视频链接；bvid 为空返回空串，调用方据此跳过分享。 */
    fun videoUrl(bvid: String?): String {
        val id = bvid?.trim().orEmpty()
        return if (id.isEmpty()) "" else "$BASE_URL/video/$id"
    }

    /** 番剧（PGC）链接，指向某一集。epId <= 0 返回空串。 */
    fun bangumiUrl(epId: Long): String =
        if (epId <= 0L) "" else "$BASE_URL/bangumi/play/ep$epId"

    /** 直播间链接。roomId <= 0 返回空串。 */
    fun liveUrl(roomId: Long): String =
        if (roomId <= 0L) "" else "https://live.bilibili.com/$roomId"

    /** 专栏链接。cvId <= 0 返回空串。 */
    fun articleUrl(cvId: Long): String =
        if (cvId <= 0L) "" else "$BASE_URL/read/cv$cvId"

    /** 收藏夹链接。mediaId <= 0 返回空串。 */
    fun favoriteUrl(mediaId: Long): String =
        if (mediaId <= 0L) "" else "$BASE_URL/medialist/detail/ml$mediaId"

    /** 动态 / 图文链接；id 通常是动态 idStr（数字串）。 */
    fun opusUrl(id: String?): String {
        val safe = id?.trim().orEmpty()
        return if (safe.isEmpty()) "" else "$BASE_URL/opus/$safe"
    }

    /**
     * 动态 / 图文的链接，参数是 [com.RobinNotBad.BiliClient.model.Dynamic.dynamicId]。
     *
     * 为什么要这一层而不是直接 [opusUrl]：动态模型上没有 idStr 字段，唯一的 id 就是
     * `dynamicId`——它来自接口的 `id_str`，缺这个字段时 DynamicApi 会把它落成 **0**，
     * 拼出来的 `opus/0` 是个死链。<= 0 一律返回空串，调用方据此整项不显示。
     */
    fun dynamicUrl(dynamicId: Long): String =
        if (dynamicId <= 0L) "" else opusUrl(dynamicId.toString())

    /**
     * 动态分享文本里排在链接上面的那一行。
     *
     * 动态多数没有标题（`title` 只有部分图文才有），所以优先用 `title`，
     * 实在没有才退回正文**第一行**：正文动辄好几行，整段塞进分享文本，接收方一眼看不到重点，
     * 私信还会顶到 2000 字节上限。
     */
    fun dynamicShareHead(title: String?, content: String?): String {
        val safeTitle = title?.trim().orEmpty()
        if (safeTitle.isNotEmpty()) return trimTitle(safeTitle)
        return trimTitle(content?.lineSequence()?.firstOrNull())
    }

    /** 「分享动态」的文本：`标题（或正文首行）\n链接`，与私信正文同一口径（[messageText]）。 */
    fun dynamicShareText(title: String?, content: String?, dynamicId: Long): String =
        messageText(dynamicShareHead(title, content), dynamicUrl(dynamicId))

    /**
     * 标题截断：去掉首尾空白，超过 [TITLE_MAX] 截断加省略号。
     * 分享文本和私信正文共用，免得两处各截一次、截出两种长度。
     */
    fun trimTitle(title: String?): String {
        val safe = title?.trim().orEmpty()
        return if (safe.length <= TITLE_MAX) safe else safe.substring(0, TITLE_MAX) + "…"
    }

    /**
     * 视频的「分享视频」文本：`标题 UP主: 昵称 - 链接`。
     *
     * 与 PiliPlus 视频页一致（ugc controller 的 shareText）：作者名拿不到时整段「UP主: xxx」去掉，
     * 而不是留下一个半截的「UP主: 」。
     */
    fun videoShareText(title: String?, bvid: String?, upName: String?): String {
        val url = videoUrl(bvid)
        val parts = ArrayList<String>(3)
        val safeTitle = trimTitle(title)
        if (safeTitle.isNotEmpty()) parts += safeTitle
        val up = upName?.trim().orEmpty()
        if (up.isNotEmpty()) parts += "UP主: $up"
        if (url.isNotEmpty()) parts += "- $url"
        return parts.joinToString(" ")
    }

    /**
     * 番剧的「分享视频」文本：`剧名 集标题 - 链接`。
     *
     * PiliPlus 番剧页没有「UP主:」段（番剧没有 UP 主这个概念），这里保持一致。
     */
    fun bangumiShareText(title: String?, epTitle: String?, epId: Long): String {
        val url = bangumiUrl(epId)
        val parts = ArrayList<String>(3)
        val safeTitle = trimTitle(title)
        if (safeTitle.isNotEmpty()) parts += safeTitle
        val safeEp = trimTitle(epTitle)
        if (safeEp.isNotEmpty() && safeEp != safeTitle) parts += safeEp
        if (url.isNotEmpty()) parts += "- $url"
        return parts.joinToString(" ")
    }

    /**
     * 「分享至消息」的私信正文：`标题\n链接`。
     *
     * 私信只能发 msg_type=1 的纯文本（卡片是 7，会被回 21035），所以正文就是标题 + 链接两行。
     * 标题或链接缺一个时只发另一个，绝不拼出「标题 + 空行」。
     */
    fun messageText(title: String?, url: String?): String {
        val safeTitle = trimTitle(title)
        val safeUrl = url?.trim().orEmpty()
        return when {
            safeTitle.isEmpty() -> safeUrl
            safeUrl.isEmpty() -> safeTitle
            else -> safeTitle + "\n" + safeUrl
        }
    }
}
