package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 「SponsorBlock 广告片段跳过」的设置位**默认值与读写**。
 *
 * 与 `SkipOpEdPrefs` 同构：默认值只在这里写一处，设置页的默认值、播放页的判据都引用它，
 * 免得改个默认值要四处同步（漏一处就是"设置页开着、播放器不跳"这种不报错的错位）。
 *
 * **默认关闭**：这是第三方社区数据，且半数类别带主观性（`filler` / `preview` 等），
 * 不该让没主动开启的人被动跳内容；设置入口是「选择播放器」页里的**跳过设置**二级页
 * （26.11.14 起与「片头片尾」同页，之前是播放器设置里的六档；更早混在「画面与显示」里）。
 *
 * 与片头片尾的区别：那边是 14 天不活跃就自动关的"锦上添花"，这里没有自动关逻辑，
 * 用户选了什么就一直是什么。
 */
object SponsorSkipPrefs {

    /**
     * 功能总开关（与用户存档无关的否决位）。
     *
     * 第三方服务一旦长时间不可用/政策有变，改 `false` 即可整体下线，
     * 不必去动 [DEFAULT_LEVEL]（改默认值对存档里已经选过档的用户毫无作用）。
     */
    const val FEATURE_ENABLED = true

    /** 默认档位：关闭（见类注释）。**老档位**，26.11.14 起只在迁移时参考。 */
    const val DEFAULT_LEVEL = SponsorSkipPolicy.LEVEL_OFF

    /** 用户存档里的**老**总档位（不看 [FEATURE_ENABLED]）；26.11.14 起只用于迁移。 */
    fun currentLevel(): Int =
        SponsorSkipPolicy.fromStored(SharedPreferencesUtil.getString(SettingsKeys.PLAYER_SPONSOR_BLOCK_MODE, null))

    /** 写入老总档位。26.11.14 起设置页已不再调用，留着给迁移与回滚用。 */
    fun storeLevel(level: Int) {
        SharedPreferencesUtil.putString(SettingsKeys.PLAYER_SPONSOR_BLOCK_MODE, level.toString())
    }

    // ---- 逐类别三档（26.11.14 起，取代上面那个总档位） ----
    //
    // 一个类别一个键（`player_sponsor_block_skip_mode_<类别>`），值 0=关闭 / 1=询问我 /
    // 2=自动跳转。**新装用户全部关闭**——这和老的「默认关闭」口径一致，不会因为改版让
    // 没主动开过的人突然开始被跳内容。

    /** 某个类别的三档存档键。 */
    fun modeKey(category: String): String =
        SettingsKeys.PLAYER_SPONSOR_BLOCK_SKIP_MODE_PREFIX + category

    /** 某个类别的三档（默认关闭）。 */
    fun storedMode(category: String): Int =
        SponsorSkipPolicy.fromStoredMode(SharedPreferencesUtil.getString(modeKey(category), null))

    /** 写入某个类别的三档（设置页选完走这里）。 */
    fun storeMode(category: String, mode: Int) {
        val safe = mode.coerceIn(SponsorSkipPolicy.MODE_OFF, SponsorSkipPolicy.MODE_AUTO)
        SharedPreferencesUtil.putString(modeKey(category), safe.toString())
    }

    /**
     * 全部类别的三档；**第一次读**时把老的六档存档折算过来并写回（只折算一次，见
     * [SettingsKeys.PLAYER_SPONSOR_BLOCK_SKIP_MODE_MIGRATED]）。
     *
     * 折算口径在 [SponsorSkipPolicy.migrateLevel]：老用户升级后跳的类别与升级前完全一致。
     */
    fun modes(): Map<String, Int> {
        val migrated = SharedPreferencesUtil.getBoolean(
            SettingsKeys.PLAYER_SPONSOR_BLOCK_SKIP_MODE_MIGRATED, false
        )
        if (!migrated) {
            val legacy = SponsorSkipPolicy.migrateLevel(currentLevel(), customCategories())
            for ((category, mode) in legacy) {
                SharedPreferencesUtil.putString(modeKey(category), mode.toString())
            }
            SharedPreferencesUtil.putBoolean(SettingsKeys.PLAYER_SPONSOR_BLOCK_SKIP_MODE_MIGRATED, true)
            return legacy
        }
        return SponsorSkipPolicy.MODE_CATEGORIES.associateWith { storedMode(it) }
    }

    /** 「自动跳转」的类别集合（播放页据此直接跳）。 */
    fun autoCategories(): Set<String> =
        SponsorSkipPolicy.categoriesWithMode(modes(), SponsorSkipPolicy.MODE_AUTO)

    /** 「询问我」的类别集合（播放页据此弹提示）。 */
    fun askCategories(): Set<String> =
        SponsorSkipPolicy.categoriesWithMode(modes(), SponsorSkipPolicy.MODE_ASK)

    /** 会把片段交给播放页的类别集合（自动 + 询问）；判断"要不要发请求"也用它。 */
    fun effectiveCategories(): Set<String> = autoCategories() + askCategories()

    /** 功能是否生效：总开关 **与** 至少有一个类别不是「关闭」。 */
    fun isEnabled(): Boolean = FEATURE_ENABLED && SponsorSkipPolicy.isAnyModeEnabled(modes())

    // ---- 自定义档的逐类别勾选 ----
    //
    // 一个类别一个布尔键（`player_sponsor_block_cat_<id>`），由「要跳的类别」页里的
    // SettingsAdapter 开关直接读写，所以这里只需要给出键名与默认值，不用另做一套写入 API。
    // 默认值 = 「推荐」档的五个类别，用户打开这一页时看到的勾就是"推荐的起点"。

    /** 某个类别的勾选存档键。 */
    fun categoryKey(category: String): String =
        SettingsKeys.PLAYER_SPONSOR_BLOCK_CAT_PREFIX + category

    /** 某个类别"从没勾过"时的默认勾选状态。 */
    fun categoryDefaultEnabled(category: String): Boolean =
        category in SponsorSkipPolicy.categoriesFor(SponsorSkipPolicy.LEVEL_RECOMMENDED)

    /** 读取全部勾选结果（已按 [SponsorSkipPolicy.sanitizeCategories] 清洗）。 */
    fun customCategories(): Set<String> = SponsorSkipPolicy.sanitizeCategories(
        SponsorSkipPolicy.ALL_CATEGORIES.filterTo(HashSet()) { category ->
            SharedPreferencesUtil.getBoolean(categoryKey(category), categoryDefaultEnabled(category))
        }
    )


    // ---- 「不准」的本地忽略集（26.11.14） ----
    //
    // 播放页跳错时用户点「不准」，除了把人送回原位，还要让**这一条**以后不再跳。存的是
    // 上游的 UUID（不是区间）：重新提交过的标注区间会变、投票会让 votes 变，只有 UUID 是它的身份。
    //
    // 分两层存：一个 cid 一个键（`player_sponsor_block_ignore_<cid>`，值 = 逗号分隔的 UUID），
    // 外加一个索引键（存出现过忽略记录的 cid 列表，最近用的排最后）——索引只为了两件事：
    // 「清除全部」要知道删哪些键，以及给 cid 数封顶（SharedPreferences 不是数据库，不能无限长）。
    // 同一视频的不同分 P 各存各的：一 P 里忽略的广告不该影响另一 P。

    /** 单个分 P 最多记多少条忽略（超出丢最早的）。一方 P 上百条广告标注基本不可能，够用。 */
    const val IGNORE_MAX_PER_VIDEO = 100

    /** 最多给多少个分 P 留忽略记录（超出丢最久没碰过的那个 cid）。 */
    const val IGNORE_MAX_VIDEOS = 50

    /** 某个 cid 的忽略集存档键。 */
    fun ignoreKey(cid: Long): String = SettingsKeys.PLAYER_SPONSOR_BLOCK_IGNORE_PREFIX + cid

    /** 读过忽略集的 cid 索引键（最近使用的排在最后）。 */
    private val ignoreIndexKey = SettingsKeys.PLAYER_SPONSOR_BLOCK_IGNORE_INDEX

    /** 这个分 P 被用户按过「不准」的标注 id。 */
    fun ignoredUuids(cid: Long): Set<String> {
        if (cid <= 0L) return emptySet()
        return parseIgnored(SharedPreferencesUtil.getString(ignoreKey(cid), null))
    }

    /** 记一条「不准」：该标注以后不再参与自动跳过（下一次 [SponsorSkipPolicy.buildSegments] 生效）。 */
    fun ignoreSegment(cid: Long, uuid: String) {
        if (cid <= 0L || uuid.isEmpty()) return
        val merged = addIgnored(
            SharedPreferencesUtil.getString(ignoreKey(cid), null),
            uuid,
            IGNORE_MAX_PER_VIDEO
        )
        SharedPreferencesUtil.putString(ignoreKey(cid), merged)
        touchIgnoreIndex(cid)
    }

    /** 清掉某个分 P 的全部忽略记录（回到"该跳的还跳"）。 */
    fun clearIgnored(cid: Long) {
        if (cid <= 0L) return
        SharedPreferencesUtil.removeValue(ignoreKey(cid))
        val rest = parseIgnored(SharedPreferencesUtil.getString(ignoreIndexKey, null)) - cid.toString()
        SharedPreferencesUtil.putString(ignoreIndexKey, rest.joinToString(","))
    }

    /** 清掉**所有**分 P 的忽略记录（设置页那个「清除已忽略的广告片段」）。 */
    fun clearAllIgnored() {
        val cids = parseIgnored(SharedPreferencesUtil.getString(ignoreIndexKey, null))
        for (cid in cids) {
            val value = cid.toLongOrNull() ?: continue
            SharedPreferencesUtil.removeValue(ignoreKey(value))
        }
        SharedPreferencesUtil.putString(ignoreIndexKey, "")
    }

    /** 有忽略记录的分 P 数（设置页显示"已忽略 N 个视频里的 M 条"时用，与索引一致）。 */
    fun ignoredVideoCount(): Int =
        parseIgnored(SharedPreferencesUtil.getString(ignoreIndexKey, null)).size

    /**
     * 逗号分隔的存档原文 → 忽略集（**纯函数**，可直接 JVM 单测）。
     *
     * 脏值（空串、多余逗号、`null` 字符串）一律当没有：这是本地存档，被手改或写坏都不该让
     * 整个跳过功能崩掉。
     */
    fun parseIgnored(raw: String?): Set<String> =
        raw.orEmpty()
            .split(',')
            .map { it.trim() }
            .filterTo(LinkedHashSet()) { it.isNotEmpty() && it != "null" }

    /**
     * 把 [uuid] 追加进存档原文并保留最多 [max] 条（**纯函数**，可直接 JVM 单测）。
     *
     * 已存在就不重复加、也不改顺序（避免反复点「不准」把老记录挤出去）。
     * 超限时从最前面删——最早记的那条最可能是"当时的错判"，先放弃它。
     */
    fun addIgnored(raw: String?, uuid: String, max: Int): String {
        if (uuid.isEmpty()) return raw.orEmpty()
        val list = ArrayList(parseIgnored(raw))
        if (uuid in list) return list.joinToString(",")
        list.add(uuid)
        while (list.size > max.coerceAtLeast(1)) list.removeAt(0)
        return list.joinToString(",")
    }

    /** 把 [cid] 记为最近使用，并给 cid 数封顶（超限时连同那个 cid 的忽略集一起删）。 */
    private fun touchIgnoreIndex(cid: Long) {
        val cids = ArrayList(parseIgnored(SharedPreferencesUtil.getString(ignoreIndexKey, null)))
        cids.remove(cid.toString())
        cids.add(cid.toString())
        while (cids.size > IGNORE_MAX_VIDEOS) {
            val evicted = cids.removeAt(0).toLongOrNull()
            if (evicted != null) SharedPreferencesUtil.removeValue(ignoreKey(evicted))
        }
        SharedPreferencesUtil.putString(ignoreIndexKey, cids.joinToString(","))
    }
}
