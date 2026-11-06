package com.RobinNotBad.BiliClient.player

/**
 * 「播放器给在线流带什么请求头」的纯策略。
 *
 * 背景（26.11.04 修「部分视频播放时播放器报错」）：B 站 playurl 在高峰时段会把直链调度到自建
 * mcdn/PCDN 边缘节点（实测 `https://b-xxxx.edge.mountaintoys.cn:4483/upgcxcode/53/28/…-1-16.mp4?os=mcdn`），
 * 这些主机既不是 `*.bilivideo.com` 也不是 `*.hdslb.com`，落在
 * [com.RobinNotBad.BiliClient.util.NetWorkUtil.isBilibiliHost] 白名单之外。
 *
 * 26.10 那次「第三方片源不许收到 B 站 Cookie」的加固把「发不发 B 站 Referer」也绑到了同一份 host
 * 白名单上，于是这类视频一个请求头都不带。实测（设备端、应用进程内发同一 URL 的 GET）：
 * - 不带 Referer：`403 Forbidden`（openresty 防盗链页，150 字节）
 * - 带 `Referer: https://www.bilibili.com/`：`206`，正常 MP4 字节
 *
 * IJK 拿到 403 就回调 `MEDIA_ERROR_UNKNOWN(-10000)`，界面停在「载入视频中」并弹
 * 「播放器可能遇到错误！错误码：-10000」。见 `docs/bilibili-api-notes.md`：「流 URL 的防盗链校验
 * 要求 Referer 为 `.bilibili.com` 域且 UA 非空，否则 403 Forbidden」——这条要求本来就写在文档里，
 * 是这次加固把「Referer」和「Cookie」一起门控掉了。
 *
 * 结论：判据拆成两条，互不牵连。
 * - **Referer**：防盗链要它，不是凭证 ⇒ 只要这条流是我们自己的内容就发；host 白名单只作辅助判据。
 * - **Cookie**：登录态，是凭证 ⇒ 仍只发给白名单内的 B 站自有域（mcdn 直链靠 URL 里的 `upsig`
 *   签名，不需要 Cookie），第三方片源一个字节都不给。
 *
 * 纯函数，便于单测：真机上凑不齐「mcdn 直链 / 第三方直链 / 直播」这几种组合。
 */
object PlayerRequestPolicy {
    /** B 站 CDN 防盗链接受的 Referer。站点根即可，实测 mcdn 节点也认。 */
    const val BILIBILI_REFERER = "https://www.bilibili.com/"

    /**
     * 本次播放是不是第三方片源（私有功能的直链）。
     *
     * 判据：直播不算；B 站侧入口（`api/PlayerApi.java` 的 `buildTerminalPlayerIntent`）一定会带
     * `aid` + `cid`，而第三方直链只带 `url`/`title` ⇒ 两者恒为 0；再要求 host 不在 B 站白名单——
     * 万一私有功能给过来的其实是 B 站直链，仍按 B 站内容处理（行为与加固前一致，不会凭空 403）。
     */
    @JvmStatic
    fun isThirdPartySource(isLive: Boolean, aid: Long, cid: Long, bilibiliHost: Boolean): Boolean =
        !isLive && aid <= 0L && cid <= 0L && !bilibiliHost

    /**
     * 构造 `IJK setDataSource(url, headers)` 用的请求头。
     *
     * @param thirdParty [isThirdPartySource] 的结果
     * @param bilibiliHost 直链 host 是否命中 `NetWorkUtil.isBilibiliHost` 白名单（决定能不能带 Cookie）
     * @param cookies 已登录 Cookie；未登录/为空时不带该头
     * @param thirdPartyReferer 第三方片源规则给的 Referer（Intent extra `third_party_referer`）
     * @param thirdPartyUserAgent 第三方片源规则给的 UA（Intent extra `third_party_user_agent`）
     */
    @JvmStatic
    fun buildHeaders(
        thirdParty: Boolean,
        bilibiliHost: Boolean,
        cookies: String?,
        thirdPartyReferer: String?,
        thirdPartyUserAgent: String?
    ): HashMap<String, String> {
        val headers = HashMap<String, String>()
        if (thirdParty) {
            // 第三方片的 Referer/UA 只按片源规则给；缺了就不带，绝不退回 B 站 Referer/Cookie。
            thirdPartyReferer?.takeIf { it.isNotBlank() }?.let { headers["Referer"] = it }
            thirdPartyUserAgent?.takeIf { it.isNotBlank() }?.let { headers["User-Agent"] = it }
            return headers
        }
        headers["Referer"] = BILIBILI_REFERER
        // Cookie 是凭证：白名单外的 host（含 mcdn/PCDN 边缘节点）一律不给。
        if (bilibiliHost) cookies?.takeIf { it.isNotBlank() }?.let { headers["Cookie"] = it }
        return headers
    }
}
