package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.ShortVideoItem
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

object ShortVideoFeedApi {

    private const val FEED_STORY_URL = "https://app.bilibili.com/x/v2/feed/index/story"
    private const val FEED_INDEX_URL = "https://app.bilibili.com/x/v2/feed/index"

    private var currentPage = 1

    fun fetchFeedPage(): List<ShortVideoItem> {
        val items = fetchStoryFeed()
        if (items.isNotEmpty()) {
            currentPage++
            return items
        }
        val fallbackItems = fetchIndexFeed()
        if (fallbackItems.isNotEmpty()) {
            currentPage++
        }
        return fallbackItems
    }

    private fun fetchStoryFeed(): List<ShortVideoItem> {
        val params = buildString {
            append("?mobi_app=android")
            append("&platform=android")
            append("&display_id=").append(currentPage)
            append("&pull=true")
            append("&video_mode=2")
            append("&voice_balance=1")
            append("&s_locale=zh_CN")
            append("&inline_sound=2")
            append("&network=wifi")
            append("&fnval=272")
            append("&fnver=1")
            append("&force_host=2")
            append("&fourk=1")
            append("&qn=32")
            append("&story_mode=1")
            // 添加 access_key（如果存在）
            val accessKey = SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, "")
            if (accessKey.isNotEmpty()) {
                append("&access_key=").append(accessKey)
            }
        }

        return requestFeed(FEED_STORY_URL + params)
    }

    private fun fetchIndexFeed(): List<ShortVideoItem> {
        val params = buildString {
            append("?mobi_app=android")
            append("&platform=android")
            append("&fnval=272")
            append("&fnver=1")
            append("&force_host=2")
            append("&fourk=1")
            append("&qn=32")
            append("&network=wifi")
            append("&pull=true")
            append("&video_mode=1")
            append("&voice_balance=1")
            append("&s_locale=zh_CN")
            append("&inline_sound=1")
            append("&login_event=0")
            // 添加 access_key（如果存在）
            val accessKey = SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, "")
            if (accessKey.isNotEmpty()) {
                append("&access_key=").append(accessKey)
            }
        }

        return requestFeed(FEED_INDEX_URL + params)
    }

    private fun requestFeed(url: String): List<ShortVideoItem> {
        val headers = ArrayList<String>()
        headers.add("User-Agent")
        headers.add("BiliApp/8130300 (android)")
        headers.add("Referer")
        headers.add("https://www.bilibili.com/")
        headers.add("env")
        headers.add("prod")

        val cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")
        if (cookies.isNotEmpty()) {
            headers.add("Cookie")
            headers.add(cookies)
        }

        Logu.d("ShortVideoApi", "Fetching: $url")

        try {
            val response = NetWorkUtil.get(url, headers)
            val body = JSONObject(response.body!!.string())
            val code = body.optInt("code", -1)
            if (code != 0) {
                Logu.e("ShortVideoApi", "API error code=$code: ${body.optString("message")}")
                return emptyList()
            }

            val data = body.getJSONObject("data")
            val itemsJson = data.getJSONArray("items")
            val result = mutableListOf<ShortVideoItem>()

            for (i in 0 until itemsJson.length()) {
                val json = itemsJson.getJSONObject(i)
                val item = ShortVideoItem.fromFeedItem(json)
                if (item != null) {
                    result.add(item)
                }
            }

            Logu.d("ShortVideoApi", "Fetched ${result.size} items")
            return result
        } catch (e: JSONException) {
            Logu.e("ShortVideoApi", "JSON parse error: ${e.message}")
            return emptyList()
        } catch (e: IOException) {
            Logu.e("ShortVideoApi", "Network error: ${e.message}")
            return emptyList()
        }
    }

    fun fetchVideoUrl(item: ShortVideoItem, qn: Int = 16): Boolean {
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
            item.currentQuality = qn
            Logu.d("ShortVideoApi", "Fetched video URL for aid=${item.aid}")
            true
        } catch (e: Exception) {
            Logu.e("ShortVideoApi", "Failed to get video URL: ${e.message}")
            false
        }
    }
}