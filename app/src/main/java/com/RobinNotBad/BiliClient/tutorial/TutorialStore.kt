package com.RobinNotBad.BiliClient.tutorial

import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 教程已读状态。
 *
 * 存储键：`tutorial_ver_<id>`（int），值为用户已读到的版本号。
 * **全仓库只有这里能拼教程键名**——页面和管理页一律走本对象的方法，别再自己拼字符串：
 * 旧管理页就是自己拼 `tutorial_ver_<旧 tag>`，而教程系统按 id 读，两个键对不上，
 * 于是「通过教程」之后教程照样弹、「清除进度」也清不掉（审计 P1-1）。
 *
 * 页面滑动提示（[TutorialKind.HINT]）沿用旧实现的键 `tutorial_pager_<目标页类名>`，
 * 值取反（`false` = 看过），历史格式不能改，否则老用户会重新看到提示；
 * 故这里保留原键值语义，只包一层 [isHintShown]，免得每个调用方都要自己记「取反」这件事。
 *
 * 对调用方来说不再需要区分两种教程：HINT 在 [isRead] / [markRead] / [clear] 里按自己的键读写，
 * 全屏教程走 `tutorial_ver_<id>`。管理页与触发链路都只认这套 API。
 */
object TutorialStore {

    /** 版本键前缀。 */
    private const val VERSION_PREFIX = "tutorial_ver_"

    /** 页面滑动提示键前缀（旧格式，见类注释）。 */
    private const val HINT_PREFIX = "tutorial_pager_"

    /** 旧管理页写的「历史记录」布尔值；新版不再读写它，只在清除进度时顺手清掉。 */
    private const val LEGACY_HISTORY_PREFIX = "tutorial_history_"

    /** 旧键迁移的完成标记，语义见 [migrateLegacyKeys]。 */
    private const val LEGACY_MIGRATED_FLAG = "tutorial_legacy_migrated"

    /** 是否已读过该教程的当前版本（HINT 看「提示是否展示过」）。 */
    fun isRead(tutorial: Tutorial): Boolean = when (tutorial.kind) {
        TutorialKind.HINT -> isHintShown(hintKeyPageOf(tutorial))
        else -> readVersion(tutorial.id) >= tutorial.version
    }

    /** 已读版本号；没读过返回 -1。 */
    fun readVersion(id: String): Int = SharedPreferencesUtil.getInt(keyOf(id), -1)

    /** 标记为已读（全屏教程写当前版本号，HINT 写「提示已展示」）。 */
    fun markRead(tutorial: Tutorial) {
        when (tutorial.kind) {
            TutorialKind.HINT -> markHintShown(hintKeyPageOf(tutorial))
            else -> SharedPreferencesUtil.putInt(keyOf(tutorial.id), tutorial.version)
        }
    }

    /** 清除某篇教程的进度，下次进到对应页面会重新展示（HINT 会重新出现那层提示）。 */
    fun clear(tutorial: Tutorial) {
        when (tutorial.kind) {
            TutorialKind.HINT -> clearHint(hintKeyPageOf(tutorial))
            else -> clear(tutorial.id)
        }
    }

    /**
     * 清除某篇的进度，下次进到对应页面会重新展示。
     *
     * 这里**不清旧版本键**：新旧键共用同一批字符串（见 [NEW_ONLY_IDS] 的注释），
     * 删 `tutorial_ver_dynamic` 会连动态页教程自己的进度一起删掉。
     * 「重置后有没有被迁移复活」由 [migrateLegacyKeys] 的一次性标记保证，不靠这里兜。
     * 旧管理页额外写的 `tutorial_history_<tag>` 布尔值已无读者，顺手清掉免得留垃圾。
     */
    fun clear(id: String) {
        SharedPreferencesUtil.removeValue(keyOf(id))
        SharedPreferencesUtil.removeValue(LEGACY_HISTORY_PREFIX + id)
        reverseLegacyIdOf(id)?.let { SharedPreferencesUtil.removeValue(LEGACY_HISTORY_PREFIX + it) }
    }

    /** 教程 id → 存储键。**唯一**允许拼版本键的地方。 */
    fun keyOf(id: String): String = VERSION_PREFIX + id

    // ==================== 页面滑动提示（HINT） ====================

    /**
     * HINT 教程的存储键用「目标页类名」而不是教程 id——这是旧实现的存档格式
     * （`tutorial_pager_<页类名>`），改了老用户会重新看到提示。
     * 因此宿主页由注册表里的 `target` 决定，这里只负责取它的类名。
     */
    private fun hintKeyPageOf(tutorial: Tutorial): String =
        tutorial.target.simpleName ?: tutorial.id

    /** 页面滑动提示的存储键。 */
    fun hintKeyOf(page: String): String = HINT_PREFIX + page

    /** 该页的滑动提示是否已经看过（看过就不再显示）。 */
    fun isHintShown(page: String): Boolean =
        !SharedPreferencesUtil.getBoolean(hintKeyOf(page), true)

    /** 标记该页的滑动提示已看过（旧实现是 `putBoolean(key, false)`，这里保持同一份存档）。 */
    fun markHintShown(page: String) {
        SharedPreferencesUtil.putBoolean(hintKeyOf(page), false)
    }

    /** 重置该页的滑动提示，下次进页面会重新显示。 */
    fun clearHint(page: String) {
        SharedPreferencesUtil.removeValue(hintKeyOf(page))
    }

    // ==================== 旧键迁移 ====================

    /**
     * 旧教程系统的 tag（= 旧 `tutorial_list` 数组的 9 项）。
     *
     * 旧管理页拿它们拼 `tutorial_ver_<tag>`，所以这 9 个字符串同时也是历史键的一部分。
     */
    private val LEGACY_TAGS = setOf(
        "recommend", "video", "space", "search", "message",
        "dynamic", "dynamic_info", "article", "short_video",
    )

    /**
     * 旧键 → 新 id 的映射，只列**对不上的**那四项。
     *
     * 旧实现拿 `tutorial_list` 的数组下标当 tag，而调用点又错开一位：
     * 搜索教程写进了 `tutorial_ver_message`、消息教程写进了 `tutorial_ver_dynamic`、
     * 动态页教程写进了 `tutorial_ver_dynamic_info`。其余 5 个旧 tag 与新 id 同名，无需迁移。
     */
    private val LEGACY_KEY_MAP = mapOf(
        "video" to "video_main",
        "message" to "search",
        "dynamic" to "message",
        "dynamic_info" to "dynamic",
    )

    /** 新 id → 旧 tag（[LEGACY_KEY_MAP] 的反查），供 [clear] 清旧管理页的残留键。 */
    private val REVERSE_LEGACY_KEY_MAP =
        LEGACY_KEY_MAP.entries.associate { (legacyId, newId) -> newId to legacyId }

    private fun reverseLegacyIdOf(newId: String): String? = REVERSE_LEGACY_KEY_MAP[newId]

    /**
     * 只在「新教程系统」里存在的 id。
     *
     * 新 id 里有 8 个与旧 tag 同名（recommend、space、search、message、dynamic、dynamic_info、
     * article、short_video），它们的存储键**与旧系统完全一样**，无法区分是哪一代写的；
     * 只有这 3 个（theme_notice、video_main、local）是新系统独有的——读到其中任意一篇，
     * 就说明这台机器已经在新系统上跑过，旧键不可能还是旧系统的键。
     */
    private val NEW_ONLY_IDS: List<String> by lazy {
        Tutorials.all.map { it.id }.filterNot { it in LEGACY_TAGS }
    }

    /**
     * 一次性迁移旧键。
     *
     * 旧键与新键共用同一个键空间（见 [NEW_ONLY_IDS]），所以迁移**只能做一次**：
     * 旧实现每次启动都跑，而新系统写入 `tutorial_ver_message` 之后，下一次启动的迁移会把它
     * 当成「旧系统看过消息教程」，再扇出成 `tutorial_ver_search`（消息→搜索）、
     * `tutorial_ver_dynamic`（动态→消息）……表现为「清掉进度重启又回来了」。
     *
     * 因此这里两道闸：
     * 1. 已经迁移过（[LEGACY_MIGRATED_FLAG]）直接返回；
     * 2. 机器上已有新系统独有的已读记录（[NEW_ONLY_IDS]）→ 认为升级早已完成，跳过并打标记。
     *
     * @return 本次是否真的执行了迁移（供单测断言，业务方无需关心）
     */
    @JvmStatic
    fun migrateLegacyKeys(): Boolean {
        if (SharedPreferencesUtil.getBoolean(LEGACY_MIGRATED_FLAG, false)) return false
        if (hasNewSystemRecord()) {
            SharedPreferencesUtil.putBooleanSync(LEGACY_MIGRATED_FLAG, true)
            return false
        }
        for ((legacyId, newId) in LEGACY_KEY_MAP) {
            val legacyVersion = SharedPreferencesUtil.getInt(VERSION_PREFIX + legacyId, -1)
            if (legacyVersion < 0) continue
            if (readVersion(newId) < legacyVersion) {
                SharedPreferencesUtil.putInt(keyOf(newId), legacyVersion)
            }
        }
        // 用同步写入：标记一旦丢了就会再迁一次，而这次迁移会扇出串键（见上面的注释）。
        SharedPreferencesUtil.putBooleanSync(LEGACY_MIGRATED_FLAG, true)
        return true
    }

    /** 是否存在只有新系统才会写的已读记录。 */
    private fun hasNewSystemRecord(): Boolean = NEW_ONLY_IDS.any { readVersion(it) >= 0 }
}
