package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.LocalVideo
import com.RobinNotBad.BiliClient.model.VideoFolder

/**
 * 缓存页（[com.RobinNotBad.BiliClient.activity.video.local.LocalListActivity] +
 * [com.RobinNotBad.BiliClient.adapter.video.CacheListAdapter]）的排序与搜索规则。
 *
 * 为什么单独抽成纯函数：这些规则要能在没有 Robolectric 的纯 JVM 单测里跑，
 * 所以这里不读 strings.xml、不碰任何 Android 类，只处理 LocalVideo / VideoFolder
 * 这两个纯 Java model；菜单文案由调用方按档位下标自己去资源里取。
 *
 * 所有 order 方法返回的都是「原始列表下标」，不是排好序的副本：
 * 适配器仍然持有扫描原序的 folderList / videoList，回调里传给 Activity 的也是原始下标，
 * 多选 / 删除 / 刷新那套下标语义一行都不用改。默认档就是 0..n-1，扫描顺序不会丢。
 */
object LocalListSort {

    /** 排序的「键」；升序/降序用单独的 Boolean 表达（见 [optionIndex]）。 */
    enum class Key { DEFAULT, TITLE, TIME, SIZE }

    /**
     * 排序菜单的档位数量，与 [keyAt] / [ascendingAt] / [optionIndex] 一一对应。
     *
     * 为什么把方向也塞进同一个菜单：手表屏幕小，先选字段再弹第二个窗选方向要点两次，
     * 一屏 7 行能滚完，选一次就到位。
     */
    const val OPTION_COUNT = 7

    /** 档位 → 排序键：默认（扫描顺序）/ 标题升 / 标题降 / 时间新→旧 / 时间旧→新 / 大小大→小 / 大小小→大。 */
    private val OPTION_KEYS = arrayOf(Key.DEFAULT, Key.TITLE, Key.TITLE, Key.TIME, Key.TIME, Key.SIZE, Key.SIZE)

    /** 与 [OPTION_KEYS] 一一对应的方向；true = 升序（标题 A→Z / 时间旧→新 / 大小小→大）。 */
    private val OPTION_ASCENDING = booleanArrayOf(true, true, false, false, true, false, true)

    /** 越界的下标一律夹回合法范围，避免菜单回调传进脏值时抛下标越界。 */
    fun keyAt(option: Int): Key = OPTION_KEYS[option.coerceIn(0, OPTION_COUNT - 1)]

    fun ascendingAt(option: Int): Boolean = OPTION_ASCENDING[option.coerceIn(0, OPTION_COUNT - 1)]

    /**
     * 当前 (key, ascending) 在菜单里的档位下标。
     *
     * 默认档不看方向，恒为 0（「默认」只有一种：扫描顺序；升/降由用户自己再点一次标题等档位）。
     * 没找到精确组合时也回落 0，保证菜单永远能高亮一项。
     */
    fun optionIndex(key: Key, ascending: Boolean): Int {
        if (key == Key.DEFAULT) return 0
        for (i in 0 until OPTION_COUNT) {
            if (OPTION_KEYS[i] == key && OPTION_ASCENDING[i] == ascending) return i
        }
        return 0
    }

    /** 持久化用的短名（写进 SharedPreferences）。 */
    fun nameOf(key: Key): String = when (key) {
        Key.DEFAULT -> "default"
        Key.TITLE -> "title"
        Key.TIME -> "time"
        Key.SIZE -> "size"
    }

    /** 读回持久化的短名；没存过 / 值损坏（含大小写不符）时回落默认档，不让旧数据把页面搞乱。 */
    fun keyOf(name: String?): Key = when (name) {
        "title" -> Key.TITLE
        "time" -> Key.TIME
        "size" -> Key.SIZE
        else -> Key.DEFAULT
    }

    /**
     * 视频的显示顺序（原始下标列表）。
     *
     * 用稳定排序：同标题 / 同大小 / 同时间戳的条目保持扫描原序，刷新后不会来回乱跳。
     */
    fun videoOrder(videos: List<LocalVideo>, key: Key, ascending: Boolean): List<Int> {
        val order = ArrayList<Int>(videos.size)
        for (i in videos.indices) order.add(i)
        if (videos.size <= 1) return order

        if (key == Key.DEFAULT) {
            // 默认档就是扫描顺序；反序交给 reverse，跟其它档位语义一致
            if (!ascending) order.reverse()
            return order
        }

        order.sortWith(Comparator { a, b ->
            applyDirection(compareVideo(videos[a], videos[b], key), ascending)
        })
        return order
    }

    /**
     * 文件夹的显示顺序（原始下标列表），规则与 [videoOrder] 一致。
     *
     * @param videos 全量视频：文件夹的「更新时间 / 大小」是里面视频的聚合值。
     */
    fun folderOrder(
        folders: List<VideoFolder>,
        videos: List<LocalVideo>,
        key: Key,
        ascending: Boolean
    ): List<Int> {
        val order = ArrayList<Int>(folders.size)
        for (i in folders.indices) order.add(i)
        if (folders.size <= 1) return order

        if (key == Key.DEFAULT) {
            if (!ascending) order.reverse()
            return order
        }

        order.sortWith(Comparator { a, b ->
            applyDirection(compareFolder(folders[a], folders[b], videos, key), ascending)
        })
        return order
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

    private fun compareVideo(a: LocalVideo, b: LocalVideo, key: Key): Int = when (key) {
        Key.TITLE -> a.title.orEmpty().compareTo(b.title.orEmpty(), ignoreCase = true)
        Key.TIME -> compareLong(a.lastModified, b.lastModified)
        Key.SIZE -> compareLong(a.size, b.size)
        Key.DEFAULT -> 0
    }

    private fun compareFolder(
        a: VideoFolder,
        b: VideoFolder,
        videos: List<LocalVideo>,
        key: Key
    ): Int = when (key) {
        Key.TITLE -> a.name.orEmpty().compareTo(b.name.orEmpty(), ignoreCase = true)
        Key.TIME -> compareLong(folderTime(a, videos), folderTime(b, videos))
        Key.SIZE -> compareLong(folderSize(a, videos), folderSize(b, videos))
        Key.DEFAULT -> 0
    }

    /**
     * 文件夹的「更新时间」：VideoFolder 只记了创建时间，所以取「创建时间」和「夹内最新视频修改时间」
     * 的较大值——用户感知的「最近更新」通常是他最近往夹里放/换过的那个视频。
     */
    private fun folderTime(folder: VideoFolder, videos: List<LocalVideo>): Long {
        var latest = folder.createTime
        val name = folder.name
        if (name.isNullOrEmpty()) return latest
        for (video in videos) {
            if (video.folderName == name && video.lastModified > latest) latest = video.lastModified
        }
        return latest
    }

    /** 文件夹大小 = 夹内视频大小之和（文件夹本身不占空间）。 */
    private fun folderSize(folder: VideoFolder, videos: List<LocalVideo>): Long {
        var total = 0L
        val name = folder.name
        if (name.isNullOrEmpty()) return total
        for (video in videos) {
            if (video.folderName == name) total += video.size
        }
        return total
    }

    /**
     * 把「升序比较结果」翻成当前方向的结果。
     *
     * 为什么不用 Comparator.reversed() / thenComparing：那是 Java 8 的接口默认方法，
     * minSdk 19 上只有开了 desugaring 才可用，这里手工翻符号最稳。
     */
    private fun applyDirection(result: Int, ascending: Boolean): Int {
        if (result == 0 || ascending) return result
        return if (result < 0) 1 else -1
    }

    private fun compareLong(a: Long, b: Long): Int = when {
        a < b -> -1
        a > b -> 1
        else -> 0
    }
}
