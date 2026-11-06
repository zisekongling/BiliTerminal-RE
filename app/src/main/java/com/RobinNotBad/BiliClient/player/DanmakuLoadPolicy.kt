package com.RobinNotBad.BiliClient.player

import org.json.JSONObject

/**
 * 「弹幕数量保护」的纯状态机。
 *
 * 背景：一条热门视频可能有几万条弹幕，一次性把全部分段塞进 DanmakuFlameMaster 会吃掉大量内存，
 * 低内存手表上直接表现为卡顿甚至被杀。这里的策略是：**先查总数，超过阈值就不自动加载**，
 * 但用户可以在播放器里手动强制加载（[forceLoad]），手动选择永远优先。
 *
 * 纯逻辑：不碰网络、不碰 SharedPreferences。数量由调用方查好后通过 [setCount] 注入；
 * 查询失败时用 [setCountUnavailable] 标记「不知道」，此时**放行**（失败开放）——
 * 查不到数量不该变成「弹幕全没」，那是比卡顿更直接的体验倒退。
 *
 * 阈值默认 [AUTO_LOAD_LIMIT]（5000）条，与上游/兄弟分支口径一致。
 */
class DanmakuLoadPolicy(
    requestedInitially: Boolean,
    private val protectLargeVideos: Boolean
) {

    /** 用户是否要弹幕（播放页 Intent 里有弹幕地址 = 要）。 */
    private var requested: Boolean = requestedInitially

    /** 用户是否在播放器里手动强制加载过。 */
    private var manualOverride: Boolean = false

    /** 是否已经拿到过数量结论（成功或失败都算「查过」）。 */
    private var countChecked: Boolean = false

    /** 弹幕总数；[COUNT_UNKNOWN] = 不知道。 */
    private var count: Long = COUNT_UNKNOWN

    /** 是否还需要先查一次弹幕数量。 */
    fun needsCount(): Boolean = requested && protectLargeVideos && !manualOverride && !countChecked

    /** 注入查到的数量（负数按「不知道」处理）。 */
    fun setCount(count: Long) {
        this.count = if (count < 0) COUNT_UNKNOWN else count
        countChecked = true
    }

    /** 数量查询失败：标记「查过但不知道」，按失败开放处理。 */
    fun setCountUnavailable() {
        count = COUNT_UNKNOWN
        countChecked = true
    }

    /** 当前已知的弹幕数量，未知时为 [COUNT_UNKNOWN]。 */
    fun knownCount(): Long = count

    /** 用户是否要弹幕（关掉弹幕按钮/没有弹幕地址时为 false）。 */
    fun setRequested(requested: Boolean) {
        this.requested = requested
    }

    /**
     * 是否应当加载弹幕。
     *
     * 失败开放：数量未知时返回 true（宁可多花内存，也别让用户以为「这个视频没有弹幕」）。
     * 等于阈值不算超限（`<=`），与 `> AUTO_LOAD_LIMIT` 才抑制的 [isSuppressed] 保持对偶。
     */
    fun shouldLoad(): Boolean = requested && (
        !protectLargeVideos || manualOverride || !countChecked ||
            count == COUNT_UNKNOWN || count <= AUTO_LOAD_LIMIT
        )

    /** 是否因为「弹幕太多」被抑制加载（供播放器提示与手动入口判断）。 */
    fun isSuppressed(): Boolean =
        requested && protectLargeVideos && !manualOverride && countChecked && count > AUTO_LOAD_LIMIT

    /** 手动强制加载：此后 [shouldLoad] 恒为 true，[isSuppressed] 恒为 false。 */
    fun forceLoad() {
        manualOverride = true
        requested = true
    }

    /** 换视频/换分P 时重置数量结论（手动强制加载保留：同一会话里用户已经表过态）。 */
    fun resetCount() {
        count = COUNT_UNKNOWN
        countChecked = false
    }

    companion object {

        /** 自动加载阈值：超过这个数量就不自动加载。 */
        const val AUTO_LOAD_LIMIT = 5_000L

        /** 数量未知。 */
        const val COUNT_UNKNOWN = -1L

        /** 「弹幕数量保护」开关的存档键（与长按倍速等同级，放策略类里方便成对引用）。 */
        const val KEY_PROTECT = "player_danmaku_large_guard"

        /** 默认开启：低内存手表是主要目标设备，默认保护更划算。 */
        const val DEFAULT_PROTECT = true

        /**
         * 从 `x/web-interface/view` 的响应里取 `data.stat.danmaku`。
         *
         * 这是详情页「弹幕数」显示的同一个数，但播放页不拉详情，所以用这个轻量接口查
         * （响应仍解析成 JSON，但**不下载任何弹幕内容**）。
         *
         * @return 数量；接口失败/字段缺失/非法值一律返回 [COUNT_UNKNOWN]
         */
        @JvmStatic
        fun parseCount(body: JSONObject?): Long {
            if (body == null || body.optInt("code", -1) != 0) return COUNT_UNKNOWN
            val stat = body.optJSONObject("data")?.optJSONObject("stat") ?: return COUNT_UNKNOWN
            if (!stat.has("danmaku")) return COUNT_UNKNOWN
            val value = stat.optLong("danmaku", COUNT_UNKNOWN)
            return if (value < 0) COUNT_UNKNOWN else value
        }
    }
}
