package com.RobinNotBad.BiliClient.activity.video

/**
 * 番剧索引页的「一次只允许一轮请求」闸门。
 *
 * 纯 Kotlin、无 Android 依赖，可直接 JVM 单测。
 *
 * ## 为什么需要
 *
 * 索引页的请求由三处触发：下拉刷新、触底翻页、**改筛选条件**。改条件时整页要重拉，而这时
 * 很可能还有一轮请求在飞。旧实现是「在飞就整个丢掉这次调用」，于是出现两个用户可见问题
 * （26.11.08 验收：筛选 loading 竞态）：
 *
 * 1. 筛选行已经是新条件、列表还是旧条件——新条件的重拉被丢了；
 * 2. 在飞的那一轮回来时仍然照常上屏，把**旧条件**的结果画在**新条件**的筛选行下面。
 *
 * 这里把口径收成三条：
 *
 * 1. 有在飞请求时不再发新请求（避免请求风暴），但「条件变了 → 重拉第一页」这件事**记下来**；
 * 2. 在飞请求一收尾，如果记着这件事，调用方立刻按最新条件重拉一次；
 * 3. 非「条件变」的调用（下拉刷新 / 触底翻页）在飞时仍然直接丢弃，行为不变。
 *
 * 调用方另外还要负责「让在飞那轮的结果作废」（按代数丢弃），两者配合才是完整的：
 * 本类只保证**不会漏掉重拉**，不保证旧响应不上屏。
 */
class BangumiIndexLoadGate {

    /** 是否有在飞请求。 */
    var loading: Boolean = false
        private set

    /** 在飞期间是否出现过「条件变了，要重拉第一页」。 */
    var pendingReload: Boolean = false
        private set

    /**
     * 申请开一轮请求。
     *
     * @param reload true = 这是「条件变了 → 重拉第一页」；在飞时会被记成待办
     * @return true = 现在可以发请求；false = 已有在飞请求，调用方不要发
     */
    fun start(reload: Boolean): Boolean {
        if (loading) {
            if (reload) pendingReload = true
            return false
        }
        loading = true
        pendingReload = false
        return true
    }

    /**
     * 一轮请求收尾（无论成功、失败还是被当作过期丢弃，都必须调用）。
     *
     * @return true = 在飞期间条件变过，调用方应立刻按最新条件再拉一轮
     */
    fun finish(): Boolean {
        loading = false
        val again = pendingReload
        pendingReload = false
        return again
    }
}
