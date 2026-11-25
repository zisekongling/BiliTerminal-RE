package com.RobinNotBad.BiliClient.util

import org.json.JSONObject
import java.util.Locale

/**
 * 视频卡片时长：把各列表接口五花八门的时长字段收敛成「秒」，再按需渲染成 `MM:SS`。
 *
 * <h3>为什么要收敛</h3>
 * 同一个「这段视频有多长」，各接口给的口径并不一致（形状取自仓库里的公开接口文档快照
 * `bilibili-API/docs/`，26.11.25 复核；**不认单一键、也不认单一类型**）：
 * - 热门 / 排行 / 入站必刷 / 推荐 / 相关视频 / 稍后再看 / 合集：字段名 `duration`，
 *   **数值，单位秒**（`docs/video_ranking/popular.md`、`ranking.md`、`precious_videos.md`、
 *   `docs/video/collection.md`、`docs/historytoview/toview.md`）；
 * - 搜索：字段名也是 `duration`，但**是字符串 `"MM:SS"`**，且不保证补零
 *   （`docs/search/search_response.md` 的样例里有 `"4:2"`、`"217:7"`）；
 * - 空间投稿、动态里的视频卡：`duration_text`，字符串 `"05:14"`
 *   （`docs/dynamic/space.md`、`docs/video_ranking/dynamic.md`）；
 * - 空间投稿的另一份响应里叫 `length`，格式同上。
 *
 * 所以调用方统一走 [secondsOf]：传入那条视频的原始 JSON，拿回秒数（取不到就是 0）。
 * 卡片渲染时再用 [format] 变成右下角那个角标上的字。
 *
 * 纯函数，无 Android 依赖，单测见 `DurationTextTest`。
 */
object DurationText {

    /**
     * 取值顺序：先 `duration`，再 `duration_text`，最后 `length`。
     *
     * 顺序不能随便调：`duration` 是数值秒的那一批接口的主力字段，而同时带 `duration`
     * 与 `duration_text` 的响应里两者语义一致（都是这段视频的长度），先取哪个都不会错，
     * 但先取数值的那个可以省掉一次字符串解析。
     */
    private val KEYS = arrayOf("duration", "duration_text", "length")

    /** 从一条视频的原始 JSON 里取时长（秒）；字段缺失、为 null 或解析不出正数都返回 0。 */
    @JvmStatic
    fun secondsOf(json: JSONObject?): Long {
        if (json == null) return 0L
        for (key in KEYS) {
            if (!json.has(key) || json.isNull(key)) continue
            val seconds = when (val raw = json.opt(key)) {
                is Number -> raw.toLong()
                is String -> parse(raw)
                else -> 0L
            }
            if (seconds > 0L) return seconds
        }
        return 0L
    }

    /**
     * 解析时长文本 → 秒。
     *
     * 接受 `"MM:SS"`、`"HH:MM:SS"`（各段不要求补零）、以及纯数字字符串（`"754"`）。
     * 任何非法输入（负号、空段、非数字）一律返回 0，不抛异常——角标只是锦上添花，
     * 不能因为它让整张卡片渲染失败。
     */
    @JvmStatic
    fun parse(text: String?): Long {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return 0L
        if (!raw.contains(':')) return raw.toLongOrNull()?.takeIf { it > 0L } ?: 0L
        var total = 0L
        for (part in raw.split(':')) {
            val value = part.trim().toLongOrNull() ?: return 0L
            if (value < 0L) return 0L
            total = total * 60 + value
        }
        return total
    }

    /**
     * 秒 → 角标上的字：不到一小时是 `MM:SS`，满一小时是 `H:MM:SS`。
     *
     * 不足 1 秒 / 未知（<= 0）返回空串，由调用方据此隐藏角标。
     * 与 [StringUtil.toTime] 的区别：那边固定两位小时（`00:MM:SS`），角标位置太窄，
     * 这里把小时位收成不补零。
     */
    @JvmStatic
    fun format(seconds: Long): String {
        if (seconds <= 0L) return ""
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0L) {
            String.format(Locale.CHINA, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.CHINA, "%02d:%02d", minutes, secs)
        }
    }
}
