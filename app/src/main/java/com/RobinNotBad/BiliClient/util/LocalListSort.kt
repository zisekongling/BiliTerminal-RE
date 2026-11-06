package com.RobinNotBad.BiliClient.util

/**
 * 缓存页（[com.RobinNotBad.BiliClient.activity.video.local.LocalListActivity] +
 * [com.RobinNotBad.BiliClient.adapter.video.CacheListAdapter]）的搜索与手动排序规则。
 *
 * 为什么单独抽成纯函数：这些规则要能在没有 Robolectric 的纯 JVM 单测里跑，
 * 所以这里不读 strings.xml、不碰任何 Android 类，只处理标题字符串和下标。
 *
 * 排序是「用户自己拖出来的顺序」，不是按某个字段排：显示顺序只由持久化的标题序决定。
 * 所有 order 方法返回的都是「原始列表下标」，不是排好序的副本：
 * 适配器仍然持有扫描原序的 folderList / videoList，回调里传给 Activity 的也是原始下标，
 * 多选 / 删除 / 刷新那套下标语义一行都不用改。
 */
object LocalListSort {

    /**
     * 按持久化的标题顺序排列一组扫描下标。
     *
     * 规则：
     * 1. 在 [storedTitles] 里出现过的条目，按 [storedTitles] 的顺序排在前面（重复出现只算第一次）；
     * 2. 没出现过的条目（新下载的、扫描到但持久化记录里漏掉的）按 [scanIndices] 原顺序追加在末尾。
     *
     * 第 2 条是「不许丢条目」的兜底：夹内顺序存在 VideoFolder.videoTitles 里，
     * 而条目是否属于该夹由每条视频的 meta 决定，两者有可能不同步（旧版本只写 meta，
     * 或者文件被手工拷进来）。此时宁可把它排在最后显示，也不能让它从列表里消失。
     *
     * @param scanIndices 待排序的原始下标（调用方已按扫描顺序收集好）
     * @param storedTitles 持久化的标题顺序（可以含已不存在的标题，会被忽略）
     * @param titleAt 取下标对应的标题
     */
    fun orderByStoredTitles(
        scanIndices: List<Int>,
        storedTitles: List<String>,
        titleAt: (Int) -> String
    ): List<Int> {
        if (scanIndices.size <= 1) return ArrayList(scanIndices)

        val indexByTitle = HashMap<String, Int>(scanIndices.size * 2)
        for (index in scanIndices) {
            val title = titleAt(index)
            if (title.isNotEmpty() && !indexByTitle.containsKey(title)) indexByTitle[title] = index
        }

        val order = ArrayList<Int>(scanIndices.size)
        val used = HashSet<Int>(scanIndices.size * 2)
        for (title in storedTitles) {
            val index = indexByTitle[title] ?: continue
            if (used.add(index)) order.add(index)
        }
        // 持久化记录里没有的条目（新下载 / 记录不同步）按扫描序补齐，保证列表不丢东西
        for (index in scanIndices) {
            if (used.add(index)) order.add(index)
        }
        return order
    }

    /**
     * 合并「本次拖出来的新顺序」与「之前存着的旧顺序」。
     *
     * 拖动只覆盖当前显示出来的条目（搜索过滤后、或者夹内实际扫描到的视频），
     * 旧顺序里可能有这次没显示出来的标题；把它们按原来的相对顺序接在后面，
     * 等它们重新出现时位置还在。返回的列表去重，保证同一个标题只出现一次。
     */
    fun mergeOrder(newOrder: List<String>, previous: List<String>): List<String> {
        val merged = ArrayList<String>(newOrder.size + previous.size)
        val added = HashSet<String>(newOrder.size + previous.size)
        for (title in newOrder) {
            if (title.isNotEmpty() && added.add(title)) merged.add(title)
        }
        for (title in previous) {
            if (title.isNotEmpty() && added.add(title)) merged.add(title)
        }
        return merged
    }

    /**
     * 搜索词归一化：去掉首尾空白；null 当空串。
     *
     * 空串表示「不过滤」——清空搜索框就是恢复全量。
     */
    fun normalizeKeyword(raw: String?): String = raw?.trim().orEmpty()

    /**
     * 模糊匹配（忽略大小写）。中文没有大小写，这个参数只对标题里的英文/数字生效。
     *
     * @param text 条目显示的文字（视频标题 / 文件夹名）
     * @param keyword 已经过 [normalizeKeyword] 的词
     */
    fun matches(text: String?, keyword: String): Boolean {
        if (keyword.isEmpty()) return true
        if (text.isNullOrEmpty()) return false
        return text.contains(keyword, ignoreCase = true)
    }
}
