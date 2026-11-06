package com.RobinNotBad.BiliClient.player

/**
 * 「自动跳过片头片尾」的五档策略（**纯逻辑，零 Android 依赖**，JVM 单测见 `SkipOpEdPolicyTest`）。
 *
 * 为什么要有五档：原来的开关只有「开/关」两态，只有「片头片尾都自动跳」一种行为。
 * 但用户实际需求分成几类：
 * - 只想跳片头（片尾字幕/彩蛋想看）；
 * - 只想跳片尾（片头是主题曲，想听）；
 * - 两个都跳；
 * - **不想被自动改变播放位置**，但希望在发现片段时问一句（询问）。
 *
 * 档位取值与 `SettingsKeys.PLAYER_SKIP_OP_ED_MODE` 的注释一致：
 * `0=关 / 1=仅头 / 2=仅尾 / 3=都跳 / 4=询问`。
 *
 * 读档约定（与设置页 `list_choose` 的落盘格式一致：字符串）：
 * 1. **新键优先**：`player_skip_op_ed_mode` 能解析成 0..4 就用它；
 * 2. 新键缺失/脏值时回落旧布尔键 `player_skip_op_ed`：`true → 3`（保持升级前「都跳」的行为）、
 *    `false`/缺失 → `0`；
 * 3. 迁移只发生在读取侧，且由 `SkipOpEdPrefs.currentMode()` 负责把结果写回，避免每次读都迁移。
 *
 * 「询问」档的交互约束（由播放页实现，不在本文件）：
 * - **绝对不能自动 seek**，只弹一次询问；
 * - 同一个片段在本集内只问一次（用户答过就不再问，答「不跳」也不反复骚扰）。
 */
object SkipOpEdPolicy {

    /** 关闭：完全不处理片头片尾（也不参与自动判定）。 */
    const val MODE_OFF = 0

    /** 仅跳过片头。 */
    const val MODE_OP = 1

    /** 仅跳过片尾。 */
    const val MODE_ED = 2

    /** 片头片尾都跳过（= 改造前「开关打开」的行为）。 */
    const val MODE_BOTH = 3

    /** 询问：发现片段只提示，用户点「跳过」才 seek。 */
    const val MODE_ASK = 4

    /** 默认档位（= 改造前默认关闭，26.10.22 的口径）。 */
    const val DEFAULT_MODE = MODE_OFF

    /** 合法的档位范围（连续整数）。 */
    fun isValid(mode: Int): Boolean = mode in MODE_OFF..MODE_ASK

    /** 把任意 int 收敛到合法档位；越界回默认。 */
    fun fromInt(mode: Int): Int = if (isValid(mode)) mode else DEFAULT_MODE

    /** 把设置页落盘的字符串解析成档位；解析失败回默认。 */
    fun fromStored(raw: String?): Int = fromInt(raw?.trim()?.toIntOrNull() ?: DEFAULT_MODE)

    /**
     * 迁移：新键优先，新键无效时回落旧布尔键。
     *
     * @param newRaw 新键原文（`player_skip_op_ed_mode`）
     * @param legacyEnabled 旧布尔键原文（`player_skip_op_ed`），键不存在时传 `null`
     */
    fun migrate(newRaw: String?, legacyEnabled: Boolean?): Int {
        val parsed = newRaw?.trim()?.toIntOrNull()
        if (parsed != null && isValid(parsed)) return parsed
        return if (legacyEnabled == true) MODE_BOTH else MODE_OFF
    }

    /** 该档位是否自动跳片头。 */
    fun wantsOp(mode: Int): Boolean = mode == MODE_OP || mode == MODE_BOTH

    /** 该档位是否自动跳片尾。 */
    fun wantsEd(mode: Int): Boolean = mode == MODE_ED || mode == MODE_BOTH

    /** 该档位是否处于「启用」状态（询问也算启用：会提示）。 */
    fun isEnabled(mode: Int): Boolean = mode != MODE_OFF

    /**
     * 该档位下，这种类型的片段是否**自动**跳。
     *
     * @param type `ViewPointSkip.TYPE_OP` / `ViewPointSkip.TYPE_ED`
     */
    fun shouldAutoSkip(mode: Int, type: Int): Boolean = when (type) {
        ViewPointSkip.TYPE_OP -> wantsOp(mode)
        ViewPointSkip.TYPE_ED -> wantsEd(mode)
        else -> false
    }

    /**
     * 该档位下，这种类型的片段是否应该**询问**而不是自动跳。
     *
     * 只有「询问」档返回 true；其它档位（含关）都不问。
     */
    fun shouldAsk(mode: Int, type: Int): Boolean =
        mode == MODE_ASK && (type == ViewPointSkip.TYPE_OP || type == ViewPointSkip.TYPE_ED)
}
