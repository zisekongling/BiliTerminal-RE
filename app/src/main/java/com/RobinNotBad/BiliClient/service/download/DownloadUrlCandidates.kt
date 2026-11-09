package com.RobinNotBad.BiliClient.service.download

/**
 * 一条视频/音频流的「可换线路」列表：主线路在前，playurl 给的备用线路（`backup_url`）
 * 按原顺序去重追加。
 *
 * 为什么要这份列表：upos 的某条 CDN 线在某些网络下会**下载途中掐断连接**——服务端声明的
 * `Content-Length` 还没传完就 EOF，OkHttp 报 `unexpected end of stream`。26.11.09 的反馈
 * #136（OPPO OW20W2）就是这样：分片与整文件两条链路都在同一偏移处失败，重试次数用尽后
 * 任务变成 error，用户再点下载还是走同一条线，于是「一旦下载出错就再也下不动」。
 * playurl 早就把备用线路一起给我们了，失败时换线就能绕开坏节点。
 *
 * 纯 Kotlin、不依赖 Android，可直接跑 JVM 单测（见 DownloadUrlCandidatesTest）。
 */
object DownloadUrlCandidates {

    /**
     * 组装候选线路。
     *
     * 规则：去掉空串与重复项；主线路排第一（它是 B 站给的默认节点）；**没有主线路时**
     * 备用线路仍然可用（音频用的是用户自选地址时，主线路可能为空）。
     */
    @JvmStatic
    fun of(primary: String?, backups: Collection<String>?): List<String> {
        val candidates = ArrayList<String>()
        if (!primary.isNullOrEmpty()) candidates.add(primary)
        if (backups != null) {
            for (backup in backups) {
                if (backup.isNullOrEmpty()) continue
                if (!candidates.contains(backup)) candidates.add(backup)
            }
        }
        return candidates
    }
}
