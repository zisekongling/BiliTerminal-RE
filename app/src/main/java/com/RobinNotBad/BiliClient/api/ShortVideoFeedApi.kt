package com.RobinNotBad.BiliClient.api

import android.os.Build
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.ShortVideoItem
import com.RobinNotBad.BiliClient.util.DeviceIdentity
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.NetworkPlaybackQuality
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * 竖屏短视频取流。
 *
 * 参数拼装、降级判定、响应解析全部在纯函数 [StoryFeedQuery] 里（有 JVM 单测）；
 * 本类只负责「带登录态发请求」与「把分页状态绑在一次会话上」。
 */
object ShortVideoFeedApi {

    private const val TAG = "ShortVideoApi"
    private const val USER_AGENT = "BiliApp/8130300 (android)"

    /**
     * 取视频播放地址。与分页无关，仍做成静态方法供播放页调用。
     *
     * @param qn 目标清晰度，默认跟随「视频设置 → 分辨率」（[SettingsKeys.PLAY_QN]）。
     *           以前这里硬编码 16（360P），改设置后短视频页分辨率不变，就是这个原因。
     */
    fun fetchVideoUrl(item: ShortVideoItem, qn: Int = defaultPlayQuality()): Boolean {
        if (item.videoUrl.isNotEmpty()) {
            return true
        }

        val playerData = PlayerData()
        playerData.aid = item.aid
        playerData.cid = item.cid
        playerData.qn = qn

        return try {
            PlayerApi.getVideo(playerData, false)
            item.videoUrl = playerData.videoUrl
            item.danmakuUrl = playerData.danmakuUrl
            item.qnStrList = playerData.qnStrList
            item.qnValueList = playerData.qnValueList
            item.currentQuality = StoryFeedQuery.pickQuality(qn, playerData.qnValueList)
            if (item.currentQuality != qn) {
                Logu.d(TAG, "qn=$qn 不可用，实际取到 ${item.currentQuality}")
            }
            Logu.d(TAG, "Fetched video URL for aid=${item.aid}, qn=${item.currentQuality}")
            true
        } catch (e: Exception) {
            Logu.e(TAG, "Failed to get video URL: ${e.message}")
            false
        }
    }

    /**
     * 「视频设置 → 分辨率」里保存的目标清晰度；老用户没有该键时沿用 16（360P）。
     *
     * 26.11.06 起改为按网络类型取值（Wi-Fi / 移动数据可分别设置），判定与播放页同源，
     * 见 [NetworkPlaybackQuality.defaultQn]。`BiliTerminal.context` 在极早期可能为 null
     * （API 层单测/初始化前），此时退回「只读 PLAY_QN」的老行为。
     */
    fun defaultPlayQuality(): Int {
        val context = BiliTerminal.context
        return if (context != null) NetworkPlaybackQuality.defaultQn(context)
        else SharedPreferencesUtil.getInt(SettingsKeys.PLAY_QN, NetworkPlaybackQuality.DEFAULT_QN)
    }

    /** 供 [StoryFeedPager] 复用的请求头。 */
    internal fun buildHeaders(): ArrayList<String> {
        val headers = ArrayList<String>(14)
        headers.add("User-Agent")
        headers.add(USER_AGENT)
        headers.add("Referer")
        headers.add("https://www.bilibili.com/")
        headers.add("env")
        headers.add("prod")

        val cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")
        if (cookies.isNotEmpty()) {
            headers.add("Cookie")
            headers.add(cookies)
        }

        // 设备指纹：官方 App 会带 Buvid（buvid3）与稳定的 Display-ID。服务端据此判断请求来自
        // 「可识别的真实设备」；缺这两个头时，即使已登录，内容也更容易被下发到
        // gateway_fallback_ 降级池（推荐内容明显偏泛）。buvid3 由 CookiesApi 保证存在，
        // 这里只做读取，不主动补种（补种逻辑统一在 CookiesApi，避免两处写入打架）。
        val buvid3 = try {
            if (cookies.isNotEmpty()) NetWorkUtil.getInfoFromCookie("buvid3", cookies) else ""
        } catch (e: Exception) {
            ""
        }
        if (buvid3.isNotEmpty()) {
            headers.add("Buvid")
            headers.add(buvid3)
        }
        headers.add("Display-ID")
        headers.add(DeviceIdentity.displayId())
        return headers
    }

    /** 请求一个取流路径并解析成一批；任何失败都返回空批。 */
    internal fun requestBatch(
        path: String,
        params: List<Pair<String, String>>,
        alreadySeenAids: Set<Long>
    ): StoryFeedQuery.FeedBatch {
        val url = StoryFeedQuery.buildUrl(path, params)
        Logu.d(TAG, "Fetching: $url")

        return try {
            val response = NetWorkUtil.get(url, buildHeaders())
            val raw = response.body?.string()
            if (raw.isNullOrEmpty()) {
                Logu.e(TAG, "Empty response body")
                StoryFeedQuery.EMPTY_BATCH
            } else {
                val body = JSONObject(raw)
                val batch = StoryFeedQuery.parseFeed(body, alreadySeenAids)
                if (batch.items.isEmpty()) {
                    val code = body.optInt("code", -1)
                    if (code != 0) {
                        Logu.e(TAG, "API error code=$code: ${body.optString("message")}")
                    }
                } else {
                    Logu.d(TAG, "Fetched ${batch.items.size} items (degraded=${batch.degraded})")
                }
                batch
            }
        } catch (e: JSONException) {
            Logu.e(TAG, "JSON parse error: ${e.message}")
            StoryFeedQuery.EMPTY_BATCH
        } catch (e: IOException) {
            Logu.e(TAG, "Network error: ${e.message}")
            StoryFeedQuery.EMPTY_BATCH
        } catch (e: Exception) {
            // 兜底：NetWorkUtil 在异常响应下可能抛运行时异常，别让它把后台线程打穿
            Logu.e(TAG, "Request failed: ${e.javaClass.simpleName}: ${e.message}")
            StoryFeedQuery.EMPTY_BATCH
        }
    }

    /**
     * 请求相关推荐（[StoryFeedQuery.PATH_RELATE]）并解析成短视频条目。
     *
     * 与 [requestBatch] 的区别：这里没有分页/track_id 语义，失败一律返回空列表，
     * 调用方据此「停下」而不是继续重试。
     */
    internal fun requestRelated(
        params: List<Pair<String, String>>,
        alreadySeenAids: Set<Long>
    ): List<ShortVideoItem> {
        val url = StoryFeedQuery.buildUrl(StoryFeedQuery.PATH_RELATE, params)
        Logu.d(TAG, "Fetching related: $url")

        return try {
            val response = NetWorkUtil.get(url, buildHeaders())
            val raw = response.body?.string()
            if (raw.isNullOrEmpty()) {
                Logu.e(TAG, "Empty related response body")
                emptyList()
            } else {
                val body = JSONObject(raw)
                val items = StoryFeedQuery.parseRelated(body, alreadySeenAids)
                if (items.isEmpty()) {
                    Logu.e(TAG, "Related empty, code=${body.optInt("code", -1)}")
                } else {
                    Logu.d(TAG, "Related fetched ${items.size} items")
                }
                items
            }
        } catch (e: Exception) {
            Logu.e(TAG, "Related request failed: ${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }
    }
}

/**
 * 一次「进入短视频页」的分页状态机。
 *
 * 每个 [com.RobinNotBad.BiliClient.util.VideoPreloadManager] 持有自己的实例，
 * 页面销毁即随实例丢弃——不再像旧实现那样把 `currentPage` 放在 object 上跨页面泄漏。
 *
 * 翻页协议要点：
 * - `display_id` 是批次序号（首屏 1，每成功一批 +1），不是页码；
 * - 首屏 `pull=0`，翻页 `pull=1`；
 * - 上一批服务端下发的 `track_id` 要回传（`trackid` 参数），降级通道的值不回传；
 * - 主信息流拿不到数据时改用竖屏动态流兜底。
 */
class StoryFeedPager {

    private var displayId = 1
    private var lastTrackId: String? = null
    private var firstBatchLoaded = false
    private val seenAids = HashSet<Long>()
    private var degradedLogged = false

    /**
     * 拉取下一批。返回空列表表示这一批彻底失败（调用方按「加载失败」处理）。
     */
    @Synchronized
    fun fetchPage(): List<ShortVideoItem> {
        val page = displayId
        val firstBatch = !firstBatchLoaded
        val accessKey = SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, "")
        val deviceName = try {
            Build.MODEL ?: ""
        } catch (e: Exception) {
            ""
        }
        val params = StoryFeedQuery.buildParams(page, firstBatch, lastTrackId, accessKey, deviceName)
        // 会话内已出现 + 跨会话看过的 aid 一起交给解析层过滤：
        // 服务端换批时偶尔会重复下发，退出再进页面更是会把刚看过的内容再推一遍。
        val seen = HashSet(seenAids)
        seen.addAll(StorySeenStore.load())

        var batch = ShortVideoFeedApi.requestBatch(StoryFeedQuery.PATH_STORY, params, seen)
        if (batch.items.isEmpty()) {
            Logu.d("ShortVideoApi", "主信息流无可用数据，改走动态流兜底")
            batch = ShortVideoFeedApi.requestBatch(StoryFeedQuery.PATH_STORY_DYNAMIC, params, seen)
        }
        if (batch.items.isEmpty()) {
            return emptyList()
        }

        displayId = StoryFeedQuery.nextDisplayId(page)
        firstBatchLoaded = true
        if (batch.trackId != null) {
            lastTrackId = batch.trackId
        }
        for (item in batch.items) {
            seenAids.add(item.aid)
        }
        StorySeenStore.record(batch.items.map { it.aid })
        if (batch.degraded && !degradedLogged) {
            degradedLogged = true
            Logu.d("ShortVideoApi", "本批来自网关降级池（track_id 以 ${StoryFeedQuery.DEGRADED_TRACK_PREFIX} 开头），推荐内容可能偏泛")
        }
        return batch.items
    }

    /**
     * 以某个视频为锚点拉一批相关推荐（列表刷到底时的续接通道）。
     *
     * 与 [fetchPage] 共用同一份「看过」集合：相关推荐里点开过的条目同样会被记进
     * [seenAids] 与跨会话的 [StorySeenStore]，避免续接后又把同一条推回来。
     */
    @Synchronized
    fun fetchRelated(anchorAid: Long): List<ShortVideoItem> {
        if (anchorAid <= 0) return emptyList()
        val accessKey = SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, "")
        val deviceName = try {
            Build.MODEL ?: ""
        } catch (e: Exception) {
            ""
        }
        val params = StoryFeedQuery.buildRelatedParams(anchorAid, accessKey, deviceName)
        val seen = HashSet(seenAids)
        seen.addAll(StorySeenStore.load())

        val items = ShortVideoFeedApi.requestRelated(params, seen)
        if (items.isEmpty()) return emptyList()

        for (item in items) {
            seenAids.add(item.aid)
        }
        StorySeenStore.record(items.map { it.aid })
        return items
    }
}
