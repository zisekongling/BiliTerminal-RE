package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.util.NetWorkUtil
import org.json.JSONObject

/**
 * UP 主空间统计（26.11 短视频新版作者卡用）：投稿数 + 获赞数。
 *
 * <p>接口文档：`bilibili-API/docs/user/status_number.md`。
 * <ul>
 *   <li>`x/space/navnum`：无需登录，`data.video` 是投稿数。</li>
 *   <li>`x/space/upstat`：需要任意登录态（Cookie 由 [NetWorkUtil.getJson] 的 webHeaders 自带），
 *       `data.likes` 是获赞数；未登录时接口不返回 data，**必须优雅降级**。</li>
 * </ul>
 *
 * <p>两个方法都是阻塞请求，必须在 `CenterThreadPool.run { }` 里调用；
 * 任何失败（网络、风控、未登录、字段缺失）统一返回 -1，调用方据此隐藏对应文案段，
 * 不显示 0、不弹错、不阻塞其余数据。
 */
object SpaceStatApi {

    /** 投稿数；失败返回 -1。 */
    @JvmStatic
    fun getVideoCount(mid: Long): Int {
        return try {
            parseVideoCount(NetWorkUtil.getJson("https://api.bilibili.com/x/space/navnum?mid=$mid"))
        } catch (e: Exception) {
            -1
        }
    }

    /** 获赞数；失败或未登录返回 -1。 */
    @JvmStatic
    fun getLikes(mid: Long): Long {
        return try {
            parseLikes(NetWorkUtil.getJson("https://api.bilibili.com/x/space/upstat?mid=$mid"))
        } catch (e: Exception) {
            -1L
        }
    }

    /** 纯解析：code 非 0、缺 data/video 都返回 -1；0 是合法投稿数。 */
    @JvmStatic
    fun parseVideoCount(json: JSONObject?): Int {
        if (json == null || json.optInt("code", -1) != 0) return -1
        val data = json.optJSONObject("data") ?: return -1
        if (!data.has("video")) return -1
        return data.optInt("video", -1)
    }

    /** 纯解析：code 非 0、缺 data/likes（未登录即如此）都返回 -1；0 是合法获赞数。 */
    @JvmStatic
    fun parseLikes(json: JSONObject?): Long {
        if (json == null || json.optInt("code", -1) != 0) return -1L
        val data = json.optJSONObject("data") ?: return -1L
        if (!data.has("likes")) return -1L
        return data.optLong("likes", -1L)
    }
}