package com.RobinNotBad.BiliClient.service.download

import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.json.JSONArray

/**
 * 「强制清除番剧缓存状态」的记号表（详情页「缓存」按钮长按，26.11.17）。
 *
 * 为什么不照投稿视频那样直接删缓存目录：
 * 番剧缓存页的「已下载」是**只看磁盘**算出来的（[BangumiCacheDownloadIndex.isDownloaded]），
 * 目录里有成品就画成「已下载」且勾不动。这条判定不看文件是不是下坏了、是不是用户想要的高清晰度，
 * 于是「下过一次就再也选不到」——用户想重下只能自己进文件管理器删。
 * 用户 26.11.17 要的是「清状态、不删文件」：长按记下这部番的所有季，缓存页遇到记过的季
 * 就当它没有已下载的集；重新勾上再缓存即可，下载服务本来就是先写 `video_new.mp4`、
 * 成功后再替换成品，"覆盖上次下载的" 不用额外做。
 *
 * 记号是**一次性的**：[restore] 在入队后撤销，清一次只解锁一次；
 * 不撤销的话用户下次进页面还会看到「明明下过了却还能勾」，而且旧记号会一直留着。
 */
object BangumiCacheClearStore {

    /** 键名沿用 [com.RobinNotBad.BiliClient.util.CacheListOrderStore] 的「模块前缀」习惯。 */
    private const val KEY = "bangumi_cache_cleared_seasons"

    private const val TAG = "BangumiCacheClear"

    /** 当前被强制清过状态的季；解析不了就按「没清过」处理，绝不让缓存页打不开。 */
    fun load(): Set<Long> {
        val raw = SharedPreferencesUtil.getString(KEY, "").orEmpty()
        if (raw.isEmpty()) return emptySet()
        return try {
            val array = JSONArray(raw)
            val result = LinkedHashSet<Long>()
            for (index in 0 until array.length()) {
                val seasonId = array.optLong(index, 0L)
                if (seasonId > 0) result.add(seasonId)
            }
            result
        } catch (e: Exception) {
            // 旧版本 / 手工改坏的数据只该让这一条功能失效，不该连累整页（同 CacheListOrderStore）
            Logu.w(TAG, "清除记号解析失败，按没清过处理：" + e.message)
            emptySet()
        }
    }

    fun isCleared(seasonId: Long): Boolean = seasonId > 0 && load().contains(seasonId)

    /** 标记这些季「缓存状态已强行清空」；多次清除会累加（记的是整部番的所有季）。 */
    fun clear(seasonIds: Collection<Long>) {
        val merged = load().toMutableSet()
        merged.addAll(seasonIds.filter { it > 0 })
        save(merged)
    }

    /** 入队后撤销记号：清一次只解锁一次，下回再进页面就按磁盘的真实状态画。 */
    fun restore(seasonIds: Collection<Long>) {
        val current = load()
        val remain = current.filterNot { it in seasonIds }
        if (remain.size == current.size) return
        save(remain.toSet())
    }

    private fun save(seasonIds: Set<Long>) {
        if (seasonIds.isEmpty()) {
            SharedPreferencesUtil.removeValue(KEY)
            return
        }
        val array = JSONArray()
        // 排序只是为了写入稳定，方便排查时肉眼比对
        for (seasonId in seasonIds.sorted()) array.put(seasonId)
        SharedPreferencesUtil.putString(KEY, array.toString())
    }
}
