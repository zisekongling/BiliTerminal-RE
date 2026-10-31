package com.RobinNotBad.BiliClient.player

import java.net.URI
import java.util.Locale

/**
 * 播放流地址策略：主机排序 + 主备地址选择（纯函数，可直接单测）。
 *
 * <p>B 站的播放地址会一次性下发「主地址 + backup_url 备用地址」，同一份流会落在好几个 CDN 上。
 * 这些 CDN 在国内的质量差别很大：
 * <ul>
 *   <li>`upos-sz-mirrorbd / mirrorali / mirrorcos / mirrorks / mirrorbda1` 这类自建镜像最快、最稳；</li>
 *   <li>其它 `*.bilivideo.com` 节点次之；</li>
 *   <li>`akamaized` / `mountaintoys` 是海外边缘节点，国内直连经常返回错误或直接超时，
 *       表现为「一换源就 -10000 硬解失败」；</li>
 *   <li>其余未知主机排在最后一批。</li>
 * </ul>
 *
 * <p>播放器（DASH 单时钟 + 失败换 CDN 重试）需要两样东西：一个「最可能成功」的主地址，
 * 以及「按成功率排好序」的完整地址表。本类只负责这两件事，不碰网络、不碰播放器。
 *
 * <p>注意：这里只处理 URL 排序，不判断地址是否过期、是否带鉴权参数——那些由服务端签名决定。
 */
object StreamUrlPolicy {
    /** 自建镜像（upos-sz-mirrorxx）：优先。 */
    private const val RANK_MIRROR = 0

    /** 其它 bilivideo.com 节点。 */
    private const val RANK_BILIVIDEO = 1

    /** 未知 CDN，排在已知海外边缘之前。 */
    private const val RANK_OTHER = 2

    /** 海外边缘（Akamai / mountaintoys）：国内大概率失败，最后才用。 */
    private const val RANK_OVERSEAS_EDGE = 3

    /** null / 空白地址（不会被选中，只是给个确定的名次便于测试）。 */
    private const val RANK_UNKNOWN = 9

    /** 自建镜像主机里的稳定标记（host 含任意一个即视为镜像）。 */
    private val MIRROR_MARKS = arrayOf(
        "upos-sz-mirrorbd",
        "upos-sz-mirrorali",
        "upos-sz-mirrorcos",
        "upos-sz-mirrorks",
        "upos-sz-mirrorbda1",
    )

    /** 国内常见不可用的海外边缘 CDN 标记。 */
    private val OVERSEAS_EDGE_MARKS = arrayOf("akamaized", "mountaintoys")

    /**
     * 给一个主机（或完整 URL）打名次，**数值越小越优先**。
     *
     * <p>参数兼容两种形态：调用方可能已经解析好 host，也可能直接丢整条 URL 进来；
     * 内部用 [URI] 再取一次 host，解析失败（地址里有未转义字符等）时退回原始字符串做 contains 判断，
     * 绝不向外抛异常——取流链路上任何一次抛异常都会变成「没有声音」。
     *
     * @param hostOrUrl 主机名或完整播放地址，允许 null
     * @return 0=自建镜像，1=bilivideo.com，2=其它，3=海外边缘，9=空地址
     */
    @JvmStatic
    fun rank(hostOrUrl: String?): Int {
        val raw = hostOrUrl?.trim().orEmpty()
        if (raw.isEmpty()) return RANK_UNKNOWN
        val host = extractHost(raw).lowercase(Locale.ROOT)
        // 顺序不能反：镜像域名本身也以 .bilivideo.com 结尾，先判镜像才能排到 0
        if (MIRROR_MARKS.any { host.contains(it) }) return RANK_MIRROR
        if (host.contains("bilivideo.com")) return RANK_BILIVIDEO
        if (OVERSEAS_EDGE_MARKS.any { host.contains(it) }) return RANK_OVERSEAS_EDGE
        return RANK_OTHER
    }

    /**
     * 从「主地址 + 备用地址列表」里挑一条最可能成功的地址。
     *
     * @return 排序后的第一条；全空时返回 null
     */
    @JvmStatic
    fun selectUrl(primary: String?, backups: List<String?>?): String? =
        orderedUrls(primary, backups).firstOrNull()

    /**
     * 返回排序后的完整地址表：先去空白、去重，再按 [rank] 从小到大稳定排序。
     *
     * <p>同 [rank] 的地址保持它们「主地址在前、backup_url 依次在后」的原始顺序，
     * 这样播放器换 CDN 重试时不会在同档位里乱跳。
     *
     * @return 不含 null / 空白项的地址表；没有可用地址时是空列表
     */
    @JvmStatic
    fun orderedUrls(primary: String?, backups: List<String?>?): List<String> {
        // LinkedHashSet 同时负责去重和保留首次出现的顺序；sortedBy 是稳定排序，不会打乱同 rank 的先后
        val seen = LinkedHashSet<String>()
        val all = ArrayList<String>()
        fun add(candidate: String?) {
            val url = candidate?.trim().orEmpty()
            if (url.isEmpty()) return
            if (seen.add(url)) all.add(url)
        }
        add(primary)
        backups?.forEach { add(it) }
        return all.sortedBy { rank(it) }
    }

    /** 取 URL 的 host；解析失败或没有 scheme 时返回原始字符串，让 contains 判断兜底。 */
    private fun extractHost(raw: String): String {
        return try {
            val host = URI(raw).host
            if (host.isNullOrEmpty()) raw else host
        } catch (e: Exception) {
            // URISyntaxException 等：地址里有中文/空格/未转义字符时 URI 会直接抛，
            // 这里不记录日志也不上抛，原始串一样能 contains 出 CDN 特征
            raw
        }
    }
}
