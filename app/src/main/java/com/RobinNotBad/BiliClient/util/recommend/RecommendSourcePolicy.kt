package com.RobinNotBad.BiliClient.util.recommend

import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 推荐流「从哪取」的判定（纯逻辑，便于 JVM 单测）。
 *
 * 背景：设置里「推荐源」有网页源 / APP源 / 混合使用三个选项，但在此之前**只有设置页写过它、
 * 没有任何地方读过**——三个选项等于死设置。这里补上真正的判定：
 *
 * - APP 推荐流（app.bilibili.com/x/v2/feed/index）不带 access_key 时虽然也能拿到 code=0，
 *   但内容没有账号相关性（等同随便刷一批兜底推荐）。所以**无论用户选了哪一项，只要本机没有
 *   APP 推荐流所需的凭据，一律降级网页源**——这是本轮需求里用户明确要求的口径。
 * - 有凭据时按设置走：网页源 / APP源 / 混合（两个都取再合流，见 [RecommendMixer]）。
 */
enum class RecommendSource {
    /** 只走 api.bilibili.com 的网页推荐流（原有行为）。 */
    WEB,

    /** 只走 app.bilibili.com 的 APP 推荐流。 */
    APP,

    /** 两个都取，按 [RecommendMixer.WEB_PER_APP] 的比例合流。 */
    MIXED
}

object RecommendSourcePolicy {

    /** 读设置项 [SharedPreferencesUtil.RECOMMEND_SOURCE]；没存过时按网页源。 */
    @JvmStatic
    fun configured(): String =
        SharedPreferencesUtil.getString(
            SharedPreferencesUtil.RECOMMEND_SOURCE,
            SharedPreferencesUtil.RECOMMEND_SOURCE_WEB
        ) ?: SharedPreferencesUtil.RECOMMEND_SOURCE_WEB

    /**
     * 判定实际来源。
     *
     * @param configured        设置里的取值（三个常量之一，未知值按网页源）
     * @param hasAppCredential  本机是否具备 APP 推荐流所需的凭据（access_key 非空）
     */
    @JvmStatic
    fun decide(configured: String?, hasAppCredential: Boolean): RecommendSource {
        if (!hasAppCredential) return RecommendSource.WEB
        return when (configured) {
            SharedPreferencesUtil.RECOMMEND_SOURCE_APP -> RecommendSource.APP
            SharedPreferencesUtil.RECOMMEND_SOURCE_BOTH -> RecommendSource.MIXED
            else -> RecommendSource.WEB
        }
    }

    /** [decide] 的便捷包装：自己去读设置。 */
    @JvmStatic
    fun resolve(hasAppCredential: Boolean): RecommendSource = decide(configured(), hasAppCredential)
}
