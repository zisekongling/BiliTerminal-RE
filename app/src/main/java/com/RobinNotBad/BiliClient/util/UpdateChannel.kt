package com.RobinNotBad.BiliClient.util

import androidx.annotation.StringRes
import com.RobinNotBad.BiliClient.BuildConfig
import com.RobinNotBad.BiliClient.R

/**
 * 三通道（稳定 / 测试 / 实验）的渠道语义：tag 怎么读渠道、某渠道收哪些版本、一组候选里挑哪个。
 *
 * 口径与 `docs/version-channels.md` 一一对应，**改口径先改那份文档**。这里的判定逻辑全部是纯函数
 * （只有 [current] / [save] 碰 SharedPreferences），JVM 单测见 `UpdateChannelTest`。
 *
 * 为什么渠道看 **tag 后缀**而不是 Release 的 `prerelease` 字段：GitHub 会正确地在
 * `releases/latest` 里排除预发布，但 Gitee 的行为没实测过，中转服务也未必把该字段完整落库；
 * 而 tag 是我们自己起的名字，`26.11.08-beta.1` 里的 `-beta` 是唯一可靠的依据。
 */
object UpdateChannel {

    const val STABLE = "stable"
    const val BETA = "beta"
    const val EXP = "exp"

    /** 三个渠道，按「激进程度」递增排列（设置页的选项顺序也用它）。 */
    val ALL = listOf(STABLE, BETA, EXP)

    /** 供更新检查挑选的一条候选（只留判定要用到的两项）。 */
    data class Candidate(val tag: String, val versionCode: Int)

    /**
     * 归一化渠道名：只认 [ALL] 里的三个值。
     *
     * 空串 / null / 大小写不一致 / 任何不认识的值一律当**稳定版**——这是最保守的错误方向：
     * 顶多多收不到测试版，绝不会把没测过的包推给普通用户。
     */
    fun normalize(raw: String?): String = when (raw?.trim()?.lowercase()) {
        BETA -> BETA
        EXP -> EXP
        else -> STABLE
    }

    /**
     * 渠道码，编进 `versionCode = YYMMDD × 100 + 渠道码 × 10 + 当日序号`。
     *
     * 保证「同一天：稳定 < 测试 < 实验」，于是客户端那句「`code > 当前` 才算有更新」不用改。
     */
    fun codeOf(channel: String?): Int = when (normalize(channel)) {
        BETA -> 1
        EXP -> 2
        else -> 0
    }

    /**
     * 从 tag 读渠道：`26.11.08` → [STABLE]；`26.11.08-beta.1` → [BETA]；`26.11.08-exp.2` → [EXP]。
     *
     * **只认 `beta.N` / `exp.N` 这种带点的形式**（CI 的 tag 正则也只放行这一种），其余后缀一律返回
     * **空串**让调用方拒收。历史上发过 `26.09.24-BETA1` 这类没点的老 tag——它们既不该推给稳定用户，
     * 也不该被当成"当前测试版"参与选版；退一步说，就算认成 beta，它们的 `versionCode` 也远老于现在，
     * 但"能收"这件事本身就容易在日后被误读，所以直接不认。
     */
    fun channelOfTag(tag: String?): String {
        val cleaned = tag.orEmpty().trim()
        if (cleaned.isEmpty()) return ""
        if (!cleaned.contains('-')) return STABLE
        val suffix = cleaned.substringAfter('-').trim().lowercase()
        return when {
            suffix.startsWith("$BETA.") -> BETA
            suffix.startsWith("$EXP.") -> EXP
            else -> ""
        }
    }

    /**
     * 当前渠道能不能收到这个 tag 的版本。
     *
     * | 渠道 | 能收到 |
     * | --- | --- |
     * | stable | 只有无后缀 tag |
     * | beta | 无后缀 + `-beta.*` |
     * | exp | 全部 |
     *
     * 「强制更新」不需要单独隔离：强制标记跟在被选中的那条 Release 上，选版已经按渠道过滤，
     * 测试版的强制更新天然落不到稳定用户头上。
     */
    fun accepts(channel: String?, tag: String?): Boolean {
        val tagChannel = channelOfTag(tag)
        if (tagChannel.isEmpty()) return false
        return when (normalize(channel)) {
            STABLE -> tagChannel == STABLE
            BETA -> tagChannel != EXP
            else -> true
        }
    }

    /**
     * 从一组候选里挑出该渠道能装的最新一条；一条都没有时返回 null。
     *
     * **不能依赖接口返回顺序**：Gitee 的 `/releases` 实测是升序、GitHub 是降序，两者相反，
     * 只能按 `versionCode` 自己比。同码时（理论上不会发生）取 tag 字典序大的，行为稳定可测。
     * `versionCode <= 0` 的候选一律跳过：那是解析不出元数据又没有可推 tag 的脏数据。
     */
    fun bestOf(candidates: List<Candidate>, channel: String?): Candidate? {
        var best: Candidate? = null
        for (c in candidates) {
            if (c.versionCode <= 0) continue
            if (!accepts(channel, c.tag)) continue
            val current = best
            if (current == null || c.versionCode > current.versionCode ||
                (c.versionCode == current.versionCode && c.tag > current.tag)
            ) {
                best = c
            }
        }
        return best
    }

    /**
     * 渠道名字符串资源（`稳定版` / `测试版` / `实验版`）。
     *
     * 显示名与渠道的对应关系只留这一份：设置页的选项、切通道提示、「已是最新版」提示都用它，
     * 免得三处各写一份 `when`、加第四个渠道时漏改。
     */
    @StringRes
    fun nameRes(channel: String?): Int = when (normalize(channel)) {
        BETA -> R.string.update_channel_beta
        EXP -> R.string.update_channel_exp
        else -> R.string.update_channel_stable
    }

    /**
     * 更新检查用的渠道。取值优先级：
     *
     * 1. **用户在设置里显式选过**的那个（`update_channel`）——尊重选择，默认值不得覆盖它；
     * 2. 没选过时**默认测试版**（26.11.08 用户拍板）：这个项目绝大多数时间在发测试包，
     *    默认收稳定版会让「愿意帮忙测的人」白等一个稳定版周期；
     * 3. **例外：装着实验版包的人默认仍是实验版**。他已经装了实验包，默认若降到测试版，
     *    实验版就再也送不到他手上，是反向的意外。
     *
     * 注意这与上报给后台的渠道不是一回事：上报报的是 [BuildConfig.UPDATE_CHANNEL]，
     * 即「这个包是什么通道」，不能因为用户把检查渠道改成 stable 就把一台 beta 包装设备报成稳定版。
     */
    fun current(): String {
        val saved = SharedPreferencesUtil.getString(SharedPreferencesUtil.UPDATE_CHANNEL, null)
        if (!saved.isNullOrBlank()) return normalize(saved)
        return if (BuildConfig.UPDATE_CHANNEL == EXP) EXP else BETA
    }

    /** 记住用户的选择（设置页 `list_choose` 回写时调用）。 */
    fun save(channel: String) {
        SharedPreferencesUtil.putString(SharedPreferencesUtil.UPDATE_CHANNEL, normalize(channel))
    }
}
