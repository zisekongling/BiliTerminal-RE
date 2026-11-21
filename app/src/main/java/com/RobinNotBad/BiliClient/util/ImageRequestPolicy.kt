package com.RobinNotBad.BiliClient.util

import java.net.URI
import java.util.Locale

/**
 * 图片请求的凭据策略（审计 P0-1）。
 *
 * 背景：Glide 的 OkHttpClient 曾经把 [NetWorkUtil.webHeaders] 全量追加到**每一个**图片请求上，
 * 而 webHeaders 的第一组就是完整登录 Cookie（SESSDATA / bili_jct / DedeUserID）。站外图床
 * （爱发电 afdiancdn.com、用户动态/简介里的任意外链图）拿到的图片 URL 也会走这个客户端，
 * 等于把账号会话交给第三方：拿到 SESSDATA 可接管会话，拿到 bili_jct 可代发点赞/评论/收藏。
 *
 * 本策略把「这条图片请求要不要带会话头」收敛成纯函数，供 CustomGlideModule 的拦截器使用：
 * - 只认 [NetWorkUtil.isBilibiliHost] 白名单内的域名（B 站主站/短链/视频与图床 CDN、akamai 镜像）；
 * - 站外域名一律不带 Cookie / Origin / Referer，最多保留 User-Agent；
 * - 无法解析、非 http(s)、协议相对的 URL 一律按站外处理（fail-closed：宁可少带一次头）。
 *
 * 纯 JVM 实现（只用 java.net.URI / java.util.Locale，不碰 Android API），便于单测。
 */
object ImageRequestPolicy {

    /** 会话相关头：站外域名必须剔除的名字。 */
    val SESSION_HEADER_NAMES: List<String> = listOf("Cookie", "Origin", "Referer")

    /** 站外域名唯一允许保留的头。越少越不暴露设备指纹，也避免触发非常规 CDN 的风控。 */
    const val USER_AGENT_HEADER = "User-Agent"

    /**
     * 从 URL 解析 host。
     *
     * 只接受 `http://` / `https://` 的绝对 URL：
     * - 协议相对 URL（`//i0.hdslb.com/x.jpg`）没有 scheme，无法确认实际会被拼成哪套协议，返回 null；
     * - 裸域名（`i0.hdslb.com/x.jpg`）、`content://`、`file://`、含空格的非法串一律返回 null。
     * 返回 null 的调用方（[shouldAttachSession]）按「站外」处理，即不带任何会话头。
     */
    fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            if (scheme != "http" && scheme != "https") return null
            uri.host?.lowercase(Locale.ROOT)
        } catch (e: Exception) {
            // URI 解析对空格、非法转义等会抛 URISyntaxException：图片 URL 什么脏数据都可能有，
            // 这里绝不能让一次解析失败崩掉 Glide 加载，按站外处理即可。
            null
        }
    }

    /** 该图片 URL 是否允许携带登录会话头（Cookie / Origin / Referer）。 */
    fun shouldAttachSession(url: String?): Boolean {
        val host = hostOf(url) ?: return false
        return NetWorkUtil.isBilibiliHost(host)
    }

    /**
     * 认 `@0e_75q_640w.webp`（`@…q_…w.jpeg`）这类缩放后缀的 B 站图床主机。
     * 与 [NetWorkUtil.isBilibiliHost] 分开写：主站域名（www/api/passport…）虽然属于 B 站，
     * 但同样不认这个后缀，拼上去一样 404。
     */
    private val RESIZE_CDN_HOST_SUFFIXES = listOf(".hdslb.com", ".biliimg.com", ".bilivideo.com")

    /**
     * 这条图片地址能不能拼 CDN 缩放后缀（[com.RobinNotBad.BiliClient.util.GlideUtil.url] /
     * [com.RobinNotBad.BiliClient.util.GlideUtil.url_zoom] 用）。
     *
     * 后缀是 B 站图床的路径约定，拼给站外地址一律 404。26.11.06 加「赞赏」入口时踩到：
     * gitee 上的收款码被拼成 `ma.jpg@0e_75q_640w.webp`，图直接加载不出来。所以按主机白名单判定，
     * 而不是「凡是 http 图片都拼」。
     *
     * 失败方向：解析不出 host（协议相对 `//i0.hdslb.com/x.jpg`、裸域名、坏串）一律返回 false ——
     * 保守地请求原图，最多多下几十 KB，不会像拼错后缀那样整张图空白。
     */
    @JvmStatic
    fun supportsResizeSuffix(url: String?): Boolean {
        val host = hostOf(url) ?: return false
        return RESIZE_CDN_HOST_SUFFIXES.any { host.endsWith(it) }
    }

    /**
     * 计算这条图片请求最终要写的头。
     *
     * @param webHeaders [NetWorkUtil.webHeaders] 的快照（扁平 key/value 对，构造处见 NetWorkUtil#buildWebHeaders）
     * @return 有序 Map：B 站域返回全部头（Cookie / Origin / Referer / User-Agent / Sec-Ch-Ua…）；
     *         站外域只返回 User-Agent（快照里没有 UA 时返回空 Map）
     */
    fun headersFor(url: String?, webHeaders: List<String>): Map<String, String> {
        val pairs = pairsOf(webHeaders)
        if (shouldAttachSession(url)) return pairs
        val userAgent = valueOf(pairs, USER_AGENT_HEADER) ?: return emptyMap()
        return linkedMapOf(USER_AGENT_HEADER to userAgent)
    }

    /**
     * 站外域名需要在请求上显式删除的头。
     *
     * 只加不删不够：OkHttp 跟跨主机重定向时会保留自定义请求头（它只摘 Authorization 之类），
     * 所以拦截器必须在「每一跳」上删一次，否则 B 站图 302 到站外时 Cookie 会跟着过去。
     */
    fun headersToRemove(url: String?): List<String> =
        if (shouldAttachSession(url)) emptyList() else SESSION_HEADER_NAMES

    /** 把扁平 key/value 列表转成有序 Map；落单的末尾键直接忽略（防御式，构造处永远是偶数个）。 */
    fun pairsOf(webHeaders: List<String>): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        var i = 0
        while (i + 1 < webHeaders.size) {
            map[webHeaders[i]] = webHeaders[i + 1]
            i += 2
        }
        return map
    }

    /** 大小写不敏感地取一个头（HTTP 头名大小写不敏感，webHeaders 的首字母大写只是约定）。 */
    private fun valueOf(pairs: Map<String, String>, name: String): String? {
        pairs[name]?.let { return it }
        for ((key, value) in pairs) {
            if (key.equals(name, ignoreCase = true)) return value
        }
        return null
    }
}
