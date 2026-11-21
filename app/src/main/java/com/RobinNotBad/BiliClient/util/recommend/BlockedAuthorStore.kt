package com.RobinNotBad.BiliClient.util.recommend

import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.json.JSONArray
import org.json.JSONObject

/** 一个被屏蔽的作者。名字只用于设置页展示，可以为空串（老数据 / 接口没给名字）。 */
data class BlockedAuthor(val mid: Long, val name: String)

/**
 * 「屏蔽作者」的本地名单（SharedPreferences + JSON）。
 *
 * 语义是**本地过滤**：命中的卡片在推荐页直接不显示（见 [RecommendFilter]），
 * 同时调用方还会给 B 站发一次「不想看此 UP 主」（`FeedbackApi.REASON_UP`），
 * 让服务端也少推——两者是互补的，本地名单保证立刻生效、服务端信号影响长期推荐。
 *
 * 上限 [MAX] 条，超出后丢弃最早加入的（按列表顺序，新加的排最后）。名单本身不设过期：
 * 用户屏蔽了谁就一直屏蔽，要取消得自己进设置页删。
 */
object BlockedAuthorStore {

    private const val KEY = "recommend_blocked_authors"

    /** 名单上限，防止 SP 无限膨胀。 */
    const val MAX: Int = 200

    /** 当前名单（按加入顺序，新的在最后）。 */
    @JvmStatic
    fun list(): List<BlockedAuthor> = decode(SharedPreferencesUtil.getString(KEY, null))

    /** 只取 mid 集合，用于过滤卡片。 */
    @JvmStatic
    fun mids(): Set<Long> {
        val blocked = list()
        if (blocked.isEmpty()) return emptySet()
        val set = HashSet<Long>(blocked.size)
        for (author in blocked) set.add(author.mid)
        return set
    }

    /** 这个作者是否已被屏蔽。 */
    @JvmStatic
    fun isBlocked(mid: Long): Boolean = mid != 0L && mids().contains(mid)

    /**
     * 加入名单。
     *
     * @return true = 这次是新加入；false = 本来就在名单里（或 mid 非法）
     */
    @JvmStatic
    fun block(mid: Long, name: String?): Boolean {
        if (mid == 0L) return false
        val current = list()
        for (author in current) if (author.mid == mid) return false
        val trimmed = (name ?: "").trim()
        val updated = ArrayList<BlockedAuthor>(current.size + 1)
        updated.addAll(current)
        updated.add(BlockedAuthor(mid, trimmed))
        while (updated.size > MAX) updated.removeAt(0)
        SharedPreferencesUtil.putString(KEY, encode(updated))
        return true
    }

    /**
     * 从名单移除。
     *
     * @return true = 确实删掉了一条
     */
    @JvmStatic
    fun unblock(mid: Long): Boolean {
        if (mid == 0L) return false
        val current = list()
        val updated = current.filter { it.mid != mid }
        if (updated.size == current.size) return false
        SharedPreferencesUtil.putString(KEY, encode(updated))
        return true
    }

    /** 清空名单。 */
    @JvmStatic
    fun clear() {
        SharedPreferencesUtil.putString(KEY, "")
    }

    /** 编码名单（纯函数，单测用）。 */
    @JvmStatic
    fun encode(authors: List<BlockedAuthor>?): String {
        val array = JSONArray()
        if (!authors.isNullOrEmpty()) {
            for (author in authors) {
                if (author.mid == 0L) continue
                array.put(
                    JSONObject()
                        .put("mid", author.mid)
                        .put("name", author.name)
                )
            }
        }
        return array.toString()
    }

    /** 解码名单；坏数据当空处理，单条坏记录跳过（不影响其余条目）。 */
    @JvmStatic
    fun decode(raw: String?): List<BlockedAuthor> {
        if (raw.isNullOrEmpty()) return emptyList()
        val array = try {
            JSONArray(raw)
        } catch (e: Exception) {
            return emptyList()
        }
        val result = ArrayList<BlockedAuthor>(array.length())
        val seen = HashSet<Long>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val mid = item.optLong("mid", 0L)
            if (mid == 0L || !seen.add(mid)) continue
            result.add(BlockedAuthor(mid, item.optString("name", "")))
        }
        return result
    }
}
