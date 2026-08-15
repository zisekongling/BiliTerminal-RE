package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.model.HotSearchCard
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import org.json.JSONArray
import org.json.JSONObject

object HotSearchApi {

    /**
     * 获取热搜列表（经WBI签名），失败返回false
     */
    fun getHotSearch(list: MutableList<HotSearchCard>): Boolean {
        return try {
            val url = ConfInfoApi.signWBI("https://api.bilibili.com/x/web-interface/wbi/search/square?limit=50")
            parseHotSearch(NetWorkUtil.getJson(url), list)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 解析热搜接口响应，纯解析逻辑（便于单元测试）
     *
     * @param json 热搜接口返回的JSON
     * @param list 解析结果填充至此列表
     * @return 是否解析成功且非空
     */
    fun parseHotSearch(json: JSONObject?, list: MutableList<HotSearchCard>): Boolean {
        if (json == null || json.optInt("code", -1) != 0) return false
        val data = json.optJSONObject("data") ?: return false
        val trending = data.optJSONObject("trending") ?: return false
        val trendingList = trending.optJSONArray("list") ?: return false
        for (i in 0 until trendingList.length()) {
            val item = trendingList.optJSONObject(i) ?: continue
            val keyword = item.optString("keyword", "")
            val card = HotSearchCard().apply {
                this.keyword = keyword
                showName = item.optString("show_name", keyword)
                icon = item.optString("icon", "")
                position = item.optInt("position", i + 1)
                heatScore = item.optLong("heat_score", 0)
            }
            list.add(card)
        }
        return list.isNotEmpty()
    }
}
