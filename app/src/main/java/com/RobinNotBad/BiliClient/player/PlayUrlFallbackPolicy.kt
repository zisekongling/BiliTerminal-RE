package com.RobinNotBad.BiliClient.player

import org.json.JSONArray
import org.json.JSONObject

/**
 * 「取流接口回退」的纯策略。
 *
 * 背景：B 站视频取流有两套端点——WBI 版（需要签名，密钥/风控变量多）与旧版
 * `x/player/playurl`（无需签名）。线上偶发 WBI 返回 -352/-412 或空 data，而旧接口
 * 对同一条稿件还能返回地址；番剧侧则是 `pgc/player/web/v2/playurl` 与 v1 并存，
 * v2 的播放数据在 `result.video_info` 里、v1 在 `result` 里。
 *
 * 这里只放**判据**：哪个端点当主、响应是否可用、v2 的 payload 在哪。网络请求、WBI 签名、
 * 异常类型仍留在 [com.RobinNotBad.BiliClient.api.PlayerApi]；判据抽成纯函数才能在 JVM 单测里
 * 覆盖（见 `PlayUrlFallbackPolicyTest`）。
 *
 * 请求头规则不在这里：所有请求仍走 `NetWorkUtil.webHeaders`，Cookie/Referer 的发放边界见
 * [PlayerRequestPolicy]。
 */
object PlayUrlFallbackPolicy {

    /** 视频取流主端点（需要 WBI 签名）。 */
    const val VIDEO_WBI = "https://api.bilibili.com/x/player/wbi/playurl"

    /** 视频取流旧端点（不需要签名），WBI 不可用时的回退。 */
    const val VIDEO_LEGACY = "https://api.bilibili.com/x/player/playurl"

    /** 番剧取流 v2（播放数据在 `result.video_info`）。 */
    const val PGC_V2 = "https://api.bilibili.com/pgc/player/web/v2/playurl"

    /** 番剧取流 v1（播放数据在 `result`），v2 不可用时的回退。 */
    const val PGC_V1 = "https://api.bilibili.com/pgc/player/web/playurl"

    /** 一次取流尝试的失败类别。 */
    enum class Failure {
        /** 接口回了非 0 code：风控、登录、地区限制等。 */
        API_ERROR,

        /** 本次要 DASH，但响应里没有 dash。 */
        NO_DASH,

        /** 本次要 durl，但响应里没有 durl 或 durl 为空。 */
        NO_DURL
    }

    /** 视频取流主 URL（调用方负责再加 [com.RobinNotBad.BiliClient.api.ConfInfoApi.signWBI]）。 */
    @JvmStatic
    fun videoWbiUrl(query: String): String = VIDEO_WBI + query

    /** 视频取流回退 URL（旧接口不需要 WBI 签名）。 */
    @JvmStatic
    fun videoLegacyUrl(query: String): String = VIDEO_LEGACY + query

    /** 番剧 v2 URL。 */
    @JvmStatic
    fun bangumiV2Url(query: String): String = PGC_V2 + query

    /** 番剧 v1（回退）URL。 */
    @JvmStatic
    fun bangumiV1Url(query: String): String = PGC_V1 + query

    /**
     * 视频取流响应是否可用于当前请求。
     *
     * @param requireDash true = 调用方要 DASH（1080P 及以上）；false = 调用方要 durl 合一 MP4
     * @return null 表示可用；否则是失败类别，调用方据此决定「回退旧接口」还是「降级到合一 MP4」
     */
    @JvmStatic
    fun classifyVideoBody(body: JSONObject?, requireDash: Boolean): Failure? {
        if (body == null) return Failure.API_ERROR
        if (body.optInt("code", -1) != 0) return Failure.API_ERROR
        val data = body.optJSONObject("data")
            ?: return if (requireDash) Failure.NO_DASH else Failure.NO_DURL
        if (requireDash) {
            return if (data.optJSONObject("dash") != null) null else Failure.NO_DASH
        }
        return if (hasDurl(data)) null else Failure.NO_DURL
    }

    /** 视频响应里的 `data`；只在 [classifyVideoBody] 返回 null 后才保证非空。 */
    @JvmStatic
    fun videoData(body: JSONObject?): JSONObject? = body?.optJSONObject("data")

    /**
     * 番剧取流的播放数据体：v2 在 `result.video_info`，v1 在 `result`。
     *
     * @return 可解析的数据体；null 表示响应里没有 result
     */
    @JvmStatic
    fun bangumiPayload(body: JSONObject?): JSONObject? {
        val result = body?.optJSONObject("result") ?: return null
        return result.optJSONObject("video_info") ?: result
    }

    /** 番剧取流响应是否可用（v2/v1 同一判据）。null = 可用。 */
    @JvmStatic
    fun classifyBangumiBody(body: JSONObject?): Failure? {
        if (body == null) return Failure.API_ERROR
        if (body.optInt("code", -1) != 0) return Failure.API_ERROR
        val payload = bangumiPayload(body) ?: return Failure.NO_DURL
        return if (hasDurl(payload)) null else Failure.NO_DURL
    }

    private fun hasDurl(payload: JSONObject?): Boolean {
        val durl: JSONArray = payload?.optJSONArray("durl") ?: return false
        return durl.length() > 0
    }
}
