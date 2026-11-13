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
 * 不该让没主动开启的人被动跳内容；开启入口在「播放器 → 自动跳过」分组里（26.11.11 起
 * 与「片头片尾」同组，之前混在「画面与显示」里）。
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

    /** 默认档位：关闭（见类注释）。 */
    const val DEFAULT_LEVEL = SponsorSkipPolicy.LEVEL_OFF

    /** 用户存档里的档位（不看 [FEATURE_ENABLED]）。 */
    fun currentLevel(): Int =
        SponsorSkipPolicy.fromStored(SharedPreferencesUtil.getString(SettingsKeys.PLAYER_SPONSOR_BLOCK_MODE, null))

    /** 写入档位（设置页选完走这里）。 */
    fun storeLevel(level: Int) {
        SharedPreferencesUtil.putString(SettingsKeys.PLAYER_SPONSOR_BLOCK_MODE, level.toString())
    }

    /** 自动跳过当前是否生效：总开关 **与** 用户档位（非「关闭」）。 */
    fun isEnabled(): Boolean = FEATURE_ENABLED && SponsorSkipPolicy.isEnabled(currentLevel())

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

    /**
     * 当前「档位 + 勾选」实际生效的类别集合。
     *
     * 播放页每次应用片段表都从这里取，避免"档位改了但勾选没跟着算"这类错位。
     */
    fun effectiveCategories(): Set<String> =
        SponsorSkipPolicy.resolveCategories(currentLevel(), customCategories())
}
