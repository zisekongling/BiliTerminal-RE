package com.RobinNotBad.BiliClient.util

import okhttp3.Response
import java.io.File
import java.io.IOException

/**
 * 弹幕文件下载的**唯一落盘实现**：取 URL → 校验状态码 → 解压 → 判空 → 写盘。
 *
 * ## 为什么要收成一份
 *
 * 历史上「取 URL → 下载 → 解压 → 写盘」有 5 份逐字重复的副本
 * （`PlayerActivity.downdanmuOld`、`ShortVideoPlayerActivity.loadDanmakuFromXml`、
 * `LocalListActivity.downloadDanmakuFile`、`DownloadActivity.downdanmu`，
 * 以及 `DownloadService.downDanmaku`——它是 26.11.07 收口时漏掉的第 5 份），
 * 其中三份漏判 HTTP 状态码：B 站 CDN 对失效弹幕地址会回 404/412 的 HTML 错误页，
 * 旧代码照样 `decompress` 后写进 `danmaku.xml`，播放器解析出来是空弹幕，
 * 用户侧只看到「这个视频没弹幕」，且下载状态还是成功 —— 极难定位。
 *
 * 另外两份（`LocalListActivity` 与 `DownloadActivity`）已经各自补过 `isSuccessful`，
 * 两份修、两份没修，正好说明这是漏改而不是取舍。统一到这里以后，
 * 新调用方不可能再漏，也不用再判断「哪个副本才是权威实现」。
 *
 * 上层的错误处理各不相同（`DownloadService` 要返回错误码、`DownloadActivity` 要弹 Snackbar
 * 并结束页面、播放器只要把异常抛给 `MsgUtil.err`），所以这里只负责「非 2xx / 空正文一律
 * 抛 [IOException]」，提示与收尾仍留给调用方。`DownloadService` 用 [fetch] 拿字节、自己
 * `writeBytes` 落盘，只为把网络侧失败与写盘失败映射成两个不同的结果码。
 */
object DanmakuFileFetcher {

    /**
     * 纯函数：只有 2xx 才允许把响应体当作弹幕数据。
     *
     * 301/302 由 OkHttp 自动跟随，走到这里时 code 已是最终响应码，所以不需要额外放行 3xx。
     */
    @JvmStatic
    fun isAcceptableStatus(code: Int): Boolean = code in 200..299

    /**
     * 纯函数：从「状态码 + 解压后正文」判断能不能落盘。
     *
     * @param decompressedBody 解压后的弹幕正文；非 2xx 时调用方可以传 null（不必解压错误页）
     * @return 失败原因，null 表示可以落盘
     */
    @JvmStatic
    fun describeFailure(statusCode: Int, decompressedBody: ByteArray?): String? = when {
        !isAcceptableStatus(statusCode) -> "HTTP " + statusCode
        decompressedBody == null || decompressedBody.isEmpty() -> "empty danmaku body"
        else -> null
    }

    /**
     * 下载并解压弹幕 XML，返回正文。
     *
     * @param headers 与请求一起发送的 header（成对出现：name, value）。传 null 用 [NetWorkUtil.get] 的默认头；
     *                短视频等其他站点源需要用 [com.RobinNotBad.BiliClient.api.AppInfoApi.customHeaders]
     * @throws IOException 非 2xx、正文为空、网络或解压失败
     */
    @JvmStatic
    @Throws(IOException::class)
    fun fetch(url: String, headers: ArrayList<String>? = null): ByteArray {
        val response: Response = if (headers == null) NetWorkUtil.get(url) else NetWorkUtil.get(url, headers)
        response.use {
            val body = it.body
            // 非 2xx 时 body 是错误页/JSON：不解压（省一次无意义的 inflate），直接交给失败判定
            val data = if (body != null && isAcceptableStatus(it.code)) NetWorkUtil.decompress(body.bytes()) else null
            val failure = describeFailure(it.code, data)
            if (failure != null) throw IOException("弹幕下载失败：" + failure)
            // describeFailure 返回 null 时 data 必非空（唯一来源是上面那个三元表达式）
            return data!!
        }
    }

    /**
     * 下载弹幕后整体覆盖写入 [target]。
     *
     * 用 `writeBytes` 而不是先把文件 `createNewFile()` 再写：前者一次 truncate + 写完，
     * 失败时抛出 IOException 由调用方处理；旧副本里 `createNewFile()` 留下的空壳文件
     * 正是「写失败但文件存在」时播放器读到空弹幕的来源。
     */
    @JvmStatic
    @Throws(IOException::class)
    fun fetchToFile(url: String, target: File, headers: ArrayList<String>? = null) {
        target.writeBytes(fetch(url, headers))
    }
}
