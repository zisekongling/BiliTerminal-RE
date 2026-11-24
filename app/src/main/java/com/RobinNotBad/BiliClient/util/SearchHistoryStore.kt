package com.RobinNotBad.BiliClient.util

import org.json.JSONArray
import org.json.JSONException

/**
 * 搜索历史的唯一读写入口（26.11.21 反馈：希望能关掉搜索历史）。
 *
 * 开关是 [SettingsKeys.SEARCH_HISTORY_ENABLE]，默认开、保持老用户行为。**26.11.23 起改为只影响显示**
 * （用户口径：关掉仅隐藏，该记录的还是记录）：
 * 1. 关掉只是搜索页不显示历史区（判断在 `SearchActivity.showHistoryDropdown()`），记录照常写入；
 * 2. [read] / [save] 不再看开关——早先「关闭即清空已有记录、之后不再写入」的做法已经撤掉，
 *    关掉期间的搜索也会进历史，重新打开开关就原样回来；
 * 3. 想真删记录只有一条路：搜索页的「清空」（[clear]）。
 *
 * 存储格式沿用原来的 JSON 数组字符串，键仍是 [SharedPreferencesUtil.search_history]——
 * 换存储格式会让老用户的记录凭空消失，没有这个必要。
 */
object SearchHistoryStore {

    /** 空历史在本地存的就是一个空 JSON 数组，与改造前写法一致。 */
    private const val EMPTY = "[]"

    fun isEnabled(): Boolean =
        SharedPreferencesUtil.getBoolean(SettingsKeys.SEARCH_HISTORY_ENABLE, true)

    /** 读历史。与开关无关：开关只管显示，记录本身就是记录；存档损坏时返回空表。 */
    fun read(): ArrayList<String> {
        return try {
            JsonUtil.jsonToArrayList(
                JSONArray(SharedPreferencesUtil.getString(SharedPreferencesUtil.search_history, EMPTY)),
                false
            )
        } catch (e: JSONException) {
            Logu.w("搜索历史", "本地记录解析失败，按空历史处理：" + e.message)
            ArrayList()
        }
    }

    /**
     * 落盘。[history] 是内存里的当前列表。
     *
     * 同样与开关无关：开关关着也照常写，否则「关了再开」会丢掉这段时间的记录。
     */
    fun save(history: List<String>) {
        try {
            SharedPreferencesUtil.putString(SharedPreferencesUtil.search_history, JSONArray(history).toString())
        } catch (e: JSONException) {
            Logu.w("搜索历史", "写入失败：" + e.message)
        }
    }

    /** 清空本地记录（搜索页「清空」与二次确认走这里）。 */
    fun clear() {
        SharedPreferencesUtil.putString(SharedPreferencesUtil.search_history, EMPTY)
    }
}
