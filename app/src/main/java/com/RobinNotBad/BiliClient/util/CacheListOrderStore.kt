package com.RobinNotBad.BiliClient.util

import org.json.JSONArray

/**
 * 缓存页「未分类视频」的手动排序持久化。
 *
 * 缓存页的顺序有三种载体，为什么未分类要单独存：
 * 1. 文件夹之间的顺序 = `.folders_info.json` 的数组顺序（[FolderManager.saveFolderOrder] 落盘）；
 * 2. 文件夹内的顺序 = [com.RobinNotBad.BiliClient.model.VideoFolder.videoTitles]
 *    （[FolderManager.saveFolderVideoOrder] 落盘）；
 * 3. 未分类视频在磁盘上没有顺序载体——扫描顺序取决于文件系统，于是按「缓存根目录」分开
 *    存在 SharedPreferences 里。
 *
 * 按根目录分键是为了互不干扰：用户在设置里把缓存路径改到公共目录、之后又降级回应用专属目录，
 * 两套缓存各自的顺序都还在，不会互相覆盖。键里带路径，所以不放进 [SettingsKeys]
 * （那份常量表只收「全局唯一」的设置项）。
 */
object CacheListOrderStore {

    private const val KEY_PREFIX = "cache_list_order::"

    private fun keyOf(rootPath: String): String = KEY_PREFIX + rootPath

    /** 读回某个缓存根目录下的未分类视频标题顺序；没存过 / JSON 损坏时返回空表（= 扫描顺序）。 */
    fun load(rootPath: String): List<String> {
        val raw = SharedPreferencesUtil.getString(keyOf(rootPath), "")
        if (raw.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val order = ArrayList<String>(array.length())
            for (i in 0 until array.length()) {
                val title = array.optString(i, "")
                // 空标题写进去也没有意义，读的时候就滤掉，别让它占掉排序里的一个位置
                if (title.isNotEmpty()) order.add(title)
            }
            order
        } catch (_: Exception) {
            // 旧版本 / 手工改坏的数据不该让缓存页打不开，回落到扫描顺序即可
            emptyList()
        }
    }

    /** 保存某个缓存根目录下的未分类视频标题顺序；调用方保证传入的是已经 [LocalListSort.mergeOrder] 过的全量顺序。 */
    fun save(rootPath: String, order: List<String>) {
        val array = JSONArray()
        for (title in order) array.put(title)
        SharedPreferencesUtil.putString(keyOf(rootPath), array.toString())
    }
}
