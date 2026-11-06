package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.UiText

/**
 * 「自动跳过片头片尾」两个设置位的**默认值与判据**（纯逻辑，无 Android 依赖，可直接 JVM 单测）。
 *
 * 为什么单独抽出来：这两个默认值原先散在四处 `getBoolean(KEY, false)` / `"false"`
 * （`PlayerActivity` 的 `needViewPoints()` / `maybeAutoSkipOpEd()` / `maybeShowSkipGuide()`
 * 三处，以及 `SettingTerminalPlayerActivity` 的设置项默认值），
 * 默认值一改就要四处同步，漏一处的症状是「设置页显示开着、播放器却不跳」这种不报错的错位。
 * 所以改默认值时**只改本文件的 [DEFAULT_ENABLED]**，四个调用点全部引用它。
 *
 * **默认开启**（26.10.04 后续）：PiliPlus 的同类开关 `pgcSkipType` 默认就是 `SkipType.skipOnce`
 * （`lib/utils/storage_pref.dart:977-979`），B 站官方播放器同样默认跳过；且跳过之后有「撤回」兜底，
 * 误跳的代价远小于「用户不知道有这功能」。
 *
 * **默认关闭**（26.10.22 恢复）：初版是默认开启，26.10.10 又因「乱跳」紧急撤回并在设置页隐藏；
 * 26.10.22 修掉乱跳后恢复，口径由用户拍板为**默认关闭**——不让任何没主动开启的用户被动跳片头，
 * 想要的人自己去设置页打开。
 *
 * 注意 [DEFAULT_ENABLED] 只用于**读取**：`SharedPreferences.getBoolean(key, def)` 的语义就是
 * 「键不存在才用 def」，所以**老用户若显式开过，恢复后仍然是开的**——这正是我们要的行为
 * （他们当初主动开过，不该因为这次版本升级被悄悄关掉）。
 *
 * **26.10.10 的紧急撤回已于 26.10.22 解除**（[FEATURE_ENABLED] 回到 `true`）：撤回的三处配套改动
 * （设置页入口、搜索索引、`needViewPoints` 的自动跳过分支）已一并恢复。
 */
object SkipOpEdPrefs {

    /** `player_skip_op_ed` 的默认值：26.10.22 恢复后改为**关闭**（见类注释）。 */
    const val DEFAULT_ENABLED = false

    /**
     * **功能总开关：26.10.22 已恢复为 `true`。**
     *
     * 它存在的意义没有变——它是一个**与用户存档无关**的否决位，紧急撤回时改 `false` 即可整体下线，
     * 而不必去动 [DEFAULT_ENABLED]（改默认值对存档里已是 `true` 的老用户毫无作用）。
     * 也就是说：以后再要撤一次，仍然只改这一个常量。
     *
     * 当前（恢复后）的实际表现：
     * - 自动跳过是否生效由 [isEnabled] 决定：总开关为真**且**用户存档为真；
     * - 播放器只为跳过/分段显示而拉取 `view_points`（见 `PlayerActivity.needViewPoints()`）；
     * - 首次引导会弹出（引导里带「开启」按钮，会写回存档）；
     * - 设置页入口与搜索索引均已恢复（`SettingTerminalPlayerActivity` / `SettingsIndex`）。
     */
    const val FEATURE_ENABLED = true

    /**
     * 自动跳过当前是否生效：功能总开关 **与** 用户档位（非「关闭」）。
     *
     * 抽成纯函数是为了能直接单测「撤回期间存档里写着 `true` 也不能跳」这条断言——
     * 它恰恰是这次撤回最容易被改错的地方（只改默认值是最常见的错法）。
     */
    fun isEnabled(prefValue: Boolean): Boolean = FEATURE_ENABLED && prefValue

    /**
     * 读取**当前五档**（0=关 / 1=仅头 / 2=仅尾 / 3=都跳 / 4=询问）。
     *
     * 顺序：
     * 1. 功能总开关为假 → 恒为 [SkipOpEdPolicy.MODE_OFF]（紧急撤回时整体下线）；
     * 2. 新键 `player_skip_op_ed_mode` 合法 → 用它；
     * 3. 否则回落旧布尔键 `player_skip_op_ed`（true→3 / false/缺失→0），**并把迁移结果写回新键**，
     *    这样后续读取不再重复迁移，设置页也能直接显示迁移后的档位。
     *
     * 播放页每次判定都重读（与「改完即生效」的约定一致），不在 Activity 启动时缓存。
     */
    fun currentMode(): Int {
        if (!FEATURE_ENABLED) return SkipOpEdPolicy.MODE_OFF
        val newRaw = SharedPreferencesUtil.getString(SettingsKeys.PLAYER_SKIP_OP_ED_MODE, null)
        val mode = SkipOpEdPolicy.migrate(
            newRaw,
            SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_SKIP_OP_ED, DEFAULT_ENABLED)
        )
        // 只在「新键缺失/非法」时写回，避免每次判定都碰 SharedPreferences
        if (SkipOpEdPolicy.fromStored(newRaw) != mode) {
            SharedPreferencesUtil.putString(SettingsKeys.PLAYER_SKIP_OP_ED_MODE, mode.toString())
        }
        return mode
    }

    /**
     * 读取**存档原文对应的档位**（不看 [FEATURE_ENABLED]）。
     *
     * 只给 `needViewPoints()` 用：是否要拉 `view_points` 取决于「用户配过跳过档位」，
     * 与总开关无关——总开关若为假，跳过不会执行，但用户自己的配置不该因此丢失。
     */
    fun storedMode(): Int = SkipOpEdPolicy.migrate(
        SharedPreferencesUtil.getString(SettingsKeys.PLAYER_SKIP_OP_ED_MODE, null),
        SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_SKIP_OP_ED, DEFAULT_ENABLED)
    )

    /** 写入五档档位（设置页选完 / 迁移写回都走这里）。 */
    fun storeMode(mode: Int) {
        SharedPreferencesUtil.putString(
            SettingsKeys.PLAYER_SKIP_OP_ED_MODE,
            SkipOpEdPolicy.fromInt(mode).toString()
        )
    }

    /**
     * 首次引导是否该弹。
     *
     * **判据只看「引导过没有」，不看开关当前值**。原实现是「开关开着就 return」，
     * 一旦默认变成开启，这条引导（真机清单第 200 条）就永远不会出现，
     * 新用户也就没有机会知道「刚才那段被跳过了、还能撤回」。
     * 改成与开关解耦后：没引导过就引导一次，见过一次就永不再弹。
     *
     * @param hasSegments 本视频是否真的有片头/片尾可跳；没有就不引导（否则是骚扰）
     * @param alreadyGuided `player_skip_op_ed_guided` 记账位
     */
    fun shouldShowGuide(hasSegments: Boolean, alreadyGuided: Boolean): Boolean =
        hasSegments && !alreadyGuided

    /**
     * 引导文案：开关已开时不该再劝用户「开启」，只提示「已自动跳过、可撤回」。
     *
     * 做成纯函数而不是在 `PlayerActivity` 里写 if，是为了让两句话都能被单测覆盖——
     * 文案错位（开着还说"可以开启"）不会报错，只能靠测试发现。
     */
    fun guideText(enabled: Boolean): String =
        if (enabled) UiText.of("已自动跳过片头/片尾，可在下方撤回") else UiText.of("这个番剧有片头片尾，可以自动跳过")

    /** 引导上的按钮文案：已经开着就没有「开启」可点，只留一个「知道了」。 */
    fun guideActionText(enabled: Boolean): String = if (enabled) UiText.of("知道了") else UiText.of("开启")

    /** 引导里「开启」按钮写回的档位：都跳（与升级前开关打开的行为一致）。 */
    const val GUIDE_ENABLE_MODE = SkipOpEdPolicy.MODE_BOTH
}