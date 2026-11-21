package com.RobinNotBad.BiliClient.util

import org.json.JSONArray
import org.json.JSONException

/**
 * 搜索历史的唯一读写入口（26.11.21 反馈：希望能关掉搜索历史）。
 *
 * 开关是 [SettingsKeys.SEARCH_HISTORY_ENABLE]，默认开、保持老用户行为。关掉之后：
 * 1. 已有记录立刻清空（用户口径：关了就不留）；
 * 2. 之后不再写入（[save] 在关闭时只清空、不落盘）；
 * 3. 搜索页的历史浮层不再显示。
 *
 * 存储格式沿用原来的 JSON 数组字符串，键仍是 [SharedPreferencesUtil.search_history]——
 * 换存储格式会让老用户的记录凭空消失，没有这个必要。
 */
object SearchHistoryStore {

    /** 空历史在本地存的就是一个空 JSON 数组，与改造前写法一致。 */
    private const val EMPTY = "[]"

    fun isEnabled(): Boolean =
        SharedPreferencesUtil.getBoolean(SettingsKeys.SEARCH_HISTORY_ENABLE, true)

    /** 读历史。开关关着或存档损坏都返回空表（历史区因此不显示）。 */
    fun read(): ArrayList<String> {
        if (!isEnabled()) return ArrayList()
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
     * 开关关着时改成清空：调用方不必各自判一次开关，也就不会出现「某处漏判把记录又写回去」。
     */
    fun save(history: List<String>) {
        if (!isEnabled()) {
            clear()
            return
        }
        try {
            SharedPreferencesUtil.putString(SharedPreferencesUtil.search_history, JSONArray(history).toString())
        } catch (e: JSONException) {
            Logu.w("搜索历史", "写入失败：" + e.message)
        }
    }

    /** 清空本地记录（关闭开关时也走这里）。 */
    fun clear() {
        SharedPreferencesUtil.putString(SharedPreferencesUtil.search_history, EMPTY)
    }
}
