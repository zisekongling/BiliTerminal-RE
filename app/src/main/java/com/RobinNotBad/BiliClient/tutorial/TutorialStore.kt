package com.RobinNotBad.BiliClient.tutorial

import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 教程已读状态。
 *
 * 存储键：`tutorial_ver_<id>`（int），值为用户已读到的版本号。
 * 刻意沿用旧系统的键名格式，方便后续做一次性迁移（旧系统拿数组下标当 tag，存在错位）。
 */
object TutorialStore {

    /** 是否已读过该教程的当前版本。 */
    fun isRead(tutorial: Tutorial): Boolean = readVersion(tutorial.id) >= tutorial.version

    /**
     * 是否处于「读过旧版本、现在有新内容」的状态。
     *
     * 教程版本号变大后会重新弹出，此时要让用户知道「这是更新过的内容」，
     * 而不是疑惑「怎么又弹了一遍」。
     */
    fun isUpdated(tutorial: Tutorial): Boolean {
        val read = readVersion(tutorial.id)
        return read > 0 && read < tutorial.version
    }

    /** 已读版本号；没读过返回 -1。 */
    fun readVersion(id: String): Int = SharedPreferencesUtil.getInt(keyOf(id), -1)

    /** 标记为已读（写入当前版本号）。 */
    fun markRead(tutorial: Tutorial) {
        SharedPreferencesUtil.putInt(keyOf(tutorial.id), tutorial.version)
    }

    /** 清除某篇的进度，供管理页「重看」使用。 */
    fun clear(id: String) {
        SharedPreferencesUtil.removeValue(keyOf(id))
    }

    fun keyOf(id: String): String = "tutorial_ver_$id"

    /**
     * 旧键 → 新 id 的映射。
     *
     * 旧实现拿 `tutorial_list` 的数组下标当 tag，而调用点又错开一位：
     * 搜索教程写进了 `tutorial_ver_message`、消息教程写进了 `tutorial_ver_dynamic`、
     * 动态页教程写进了 `tutorial_ver_dynamic_info`。因此这里按「用户实际看过什么」重映射。
     */
    private val LEGACY_KEY_MAP = mapOf(
        "video" to "video_main",
        "message" to "search",
        "dynamic" to "message",
        "dynamic_info" to "dynamic",
    )

    /**
     * 一次性迁移旧键（幂等，每次启动调用无副作用）。
     *
     * 动态页与动态详情页在旧数据里**共用** `tutorial_ver_dynamic_info`，无法区分：
     * 这里只迁给 `dynamic`，`dynamic_info` 保持未读，宁可让用户重看一次也不要静默漏掉。
     * `article` 在旧系统里从未被写入（孤儿教程），无需迁移。
     */
    @JvmStatic
    fun migrateLegacyKeys() {
        for ((legacyId, newId) in LEGACY_KEY_MAP) {
            val legacyVersion = SharedPreferencesUtil.getInt("tutorial_ver_$legacyId", -1)
            if (legacyVersion < 0) continue
            if (readVersion(newId) < legacyVersion) {
                SharedPreferencesUtil.putInt(keyOf(newId), legacyVersion)
            }
        }
    }
}
