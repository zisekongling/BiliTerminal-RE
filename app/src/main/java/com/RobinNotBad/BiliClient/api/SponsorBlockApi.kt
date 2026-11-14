package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.util.Logu
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * BilibiliSponsorBlock（bsbsb.top）的**只读**接口封装。
 *
 * 这是一个**第三方社区服务**（B 站版 SponsorBlock，插件 hanydd/BilibiliSponsorBlock 的后端），
 * 由观众手工标注「哪一段是恰饭广告/三连提醒/片头」，客户端查回来自己跳。与本项目无隶属关系，
 * 服务随时可能挂或改口径，所以调用方必须按「拿不到就是没有」处理（本类失败一律返回空表）。
 *
 * ## 为什么用哈希接口而不是 `?videoID=BV…`
 *
 * 官方插件自己也走 `GET /api/skipSegments/<sha256前缀>`：把 BVID 做 SHA-256 取前几位当查询键，
 * 服务端因此**不知道你在看哪一个视频**（只能知道"某个前缀"）。直接传 BVID 等于是把观看记录明文交给第三方。
 * 代价是同一前缀会撞出**别的视频**的片段，必须按 `videoID` 过滤（见 [parseSegments]），这点已有单测。
 *
 * ## 网络客户端的硬约束
 *
 * **必须用本文件自己的 OkHttpClient**：`util/NetWorkUtil.getOkHttpInstance()` 挂着
 * `CookieSaveInterceptor` 和改 UA 的拦截器，拿它发第三方请求会把登录 Cookie 一起发给 bsbsb.top。
 * 同理 [CLIENT] 不挂任何拦截器、不跟随重定向，超时给得很短（4s/6s）——这是个锦上添花的服务，
 * 卡住播放起播是本末倒置。
 *
 * ## 纯解析 + 纯判定
 *
 * [parseSegments] 只做「JSON → 原始片段」的机械翻译（含按 videoID 过滤），
 * 「哪些类别要跳、cid 对不对、时长靠不靠谱」全在 `player/SponsorSkipPolicy` 里，
 * 两者都是零 Android 依赖的纯函数，可以直接 JVM 单测。
 */
object SponsorBlockApi {

    private const val TAG = "SponsorBlockApi"

    /** 正式服根地址。测试服用 `https://bsbsb.top/test/api`（数据与正式分开）。 */
    const val BASE_URL = "https://bsbsb.top/api"

    /**
     * 查询键的哈希前缀长度（十六进制字符数）。上游允许 4–32，插件取 4；
     * 这里取 8——碰撞更少、请求量不变（服务端按前缀扫，prefix 太长反而查不到更细的索引）。
     */
    private const val HASH_PREFIX_LENGTH = 8

    /** 上游要求带的调用方版本号，只用于统计来源，随便给个稳定值即可。 */
    private const val EXT_VERSION = "0.1.0"

    /**
     * `origin` 头。上游要求形如 `chrome-extension://<32 位>`，只做形态校验，
     * 所以这里用一个固定的伪扩展 ID，不暴露真实客户端信息。
     */
    private const val EXT_ORIGIN = "chrome-extension://rebiliclientsponsorblockwatch0"

    private val CLIENT by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    /**
     * 上游返回的一条原始片段（未做任何业务过滤）。
     *
     * 字段含义见 wiki 的 `API.md`：
     * @param cid 这条标注属于哪个 cid（分 P 标识；上游偶尔不给，此时为 0）
     * @param category 类别 id，如 `sponsor` / `selfpromo` / `intro`
     * @param actionType 动作类型：`skip`（跳过）/ `mute`（静音）/ `full`（整段视频标签）/ `poi` / `chapter`
     * @param fromSec 起点（秒）
     * @param toSec 终点（秒）；`full` 类型固定是 `[0,0]`
     * @param videoDuration 提交者当时看到的总时长（秒），用来判断"这条标注还是不是同一个版本"
     * @param votes 社区票数（正数=赞，负数=踩）
     * @param uuid 这条标注的**全局唯一 id**（上游 40 位十六进制）。投票、记录跳过、单条详情
     *   全要它，本地「这条不准」的忽略集也按它记——按区间记会在换源后错位。上游缺字段时为空串。
     * @param locked 是否被 VIP 锁定（锁定后普通用户不能投票/改动它自己的类别）
     * @param hidden 是否已被隐藏（票数太差被折叠，`GET /searchSegments` 之外拿不到，留作诊断）
     */
    data class RawSegment(
        val cid: Long,
        val category: String,
        val actionType: String,
        val fromSec: Double,
        val toSec: Double,
        val videoDuration: Double,
        val votes: Int,
        val uuid: String = "",
        val locked: Boolean = false,
        val hidden: Boolean = false,
    )

    /**
     * BVID → 查询键：SHA-256 十六进制小写，取前 [HASH_PREFIX_LENGTH] 位。
     *
     * 不是安全用途，只是"让服务端不知道视频号"，所以不做任何加盐/迭代（上游也是这么算的）。
     */
    fun hashPrefix(bvid: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bvid.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.substring(0, HASH_PREFIX_LENGTH)
    }

    private val HEX = "0123456789abcdef".toCharArray()

    /**
     * 查一个视频的片段表（**阻塞**，必须在后台线程调用）。
     *
     * 失败（网络错误、非 2xx、JSON 坏掉）一律返回空表：对播放器来说「没有标注」和「查不到」
     * 应当是同一种结果——不跳过、不打扰。
     *
     * @param bvid B 站视频号，为空直接返回空表
     * @param cid 可选，用来在 [parseSegments] 之外再挡一层；传 0 表示不过滤
     */
    fun fetchSegments(bvid: String, cid: Long = 0L): List<RawSegment> {
        if (bvid.isEmpty()) return emptyList()
        val url = "$BASE_URL/skipSegments/" + hashPrefix(bvid)
        val request = Request.Builder()
            .url(url)
            .header("origin", EXT_ORIGIN)
            .header("x-ext-version", EXT_VERSION)
            .get()
            .build()
        return try {
            CLIENT.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Logu.w(TAG, "查询失败 HTTP " + resp.code)
                    emptyList()
                } else {
                    val body = resp.body?.string()
                    if (body.isNullOrBlank()) emptyList()
                    else parseSegments(body, bvid, cid)
                }
            }
        } catch (e: Exception) {
            // 第三方服务，任何异常都不该影响播放（超时、证书、JSON 格式变更……）
            Logu.w(TAG, "查询异常: " + e.message)
            emptyList()
        }
    }

    /**
     * 把哈希接口的响应体解析成原始片段表（**纯函数**，可直接单测）。
     *
     * 响应形状是 `[{videoID, segments:[{cid, category, actionType, segment:[from,to], …}]}, …]`。
     * **同一前缀必然带回别的视频**（实测 `d5c2` 前缀一次回了 7 个 BVID），所以第一步就按
     * [bvid] 精确过滤（大小写不敏感，BVID 理论上只大写）。
     *
     * 这里只做结构翻译：不看类别是不是用户想跳的、不看 [RawSegment.actionType]、
     * 不看区间是否合理——那些是 `player/SponsorSkipPolicy.buildSegments` 的事。
     * 唯一顺手做的过滤是 cid（传了才做）：同一前缀里还混着同视频**别的分 P** 的片段。
     */
    fun parseSegments(body: String, bvid: String, cid: Long = 0L): List<RawSegment> {
        val array = try {
            JSONArray(body)
        } catch (e: Exception) {
            Logu.w(TAG, "响应不是 JSON 数组: " + e.message)
            return emptyList()
        }
        val out = ArrayList<RawSegment>()
        for (i in 0 until array.length()) {
            val video = array.optJSONObject(i) ?: continue
            if (!video.optString("videoID").equals(bvid, ignoreCase = true)) continue
            val segments = video.optJSONArray("segments") ?: continue
            for (j in 0 until segments.length()) {
                val obj = segments.optJSONObject(j) ?: continue
                val segment = parseSegment(obj) ?: continue
                if (cid > 0L && segment.cid > 0L && segment.cid != cid) continue
                out.add(segment)
            }
        }
        return out
    }

    /** 单条片段的结构翻译；字段缺失到无法还原区间时返回 null（跳过而不是造一条 0-0）。 */
    private fun parseSegment(obj: JSONObject): RawSegment? {
        val range = obj.optJSONArray("segment")
        if (range == null || range.length() < 2) return null
        val from = range.optDouble(0, Double.NaN)
        val to = range.optDouble(1, Double.NaN)
        if (from.isNaN() || to.isNaN()) return null
        return RawSegment(
            cid = obj.optString("cid").toLongOrNull() ?: 0L,
            category = obj.optString("category"),
            actionType = obj.optString("actionType", "skip"),
            fromSec = from,
            toSec = to,
            videoDuration = obj.optDouble("videoDuration", 0.0),
            votes = obj.optInt("votes", 0),
            // 上游这三种字段的大小写不统一（`UUID` 全大写、`locked`/`hidden` 小写），
            // 且 `optString` 对 null 会返回 "null" 字符串，所以都只在真给了的时候才取
            uuid = obj.optString("UUID").takeIf { it.isNotEmpty() && it != "null" }.orEmpty(),
            locked = flagOf(obj, "locked"),
            hidden = flagOf(obj, "hidden"),
        )
    }

    /** 上游的布尔位是 0/1 数字，个别版本给的是 `true`/`false`，两种都认。 */
    private fun flagOf(obj: JSONObject, key: String): Boolean =
        obj.optBoolean(key, false) || obj.optInt(key, 0) == 1
}
