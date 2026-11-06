package com.RobinNotBad.BiliClient.player

/**
 * 弹幕里「空降」时间戳的解析（**纯逻辑，零 Android 依赖**，JVM 单测见 `DanmakuTimestampPolicyTest`）。
 *
 * 来源：B 站弹幕里常见「空降 01:23」「空降1:23:45」这种把观众带到某个时间点的玩法。
 * 播放页原先只能看，不能点；接入后点中这类弹幕后直接 seek 过去。
 *
 * 解析规则（刻意收紧，避免把普通数字误判成时间戳）：
 * 1. 只接受 **`mm:ss`（1–2 位分钟 + 2 位秒）** 与 **`hh:mm:ss`（1–2 位小时 + 2 位分 + 2 位秒）**；
 * 2. 时间戳必须是**独立 token**：扫描时把「连续的 `[0-9:]`」当一个整体，
 *    所以 `12:34:56:78`（四段）不会被截成 `12:34:56` 而误判；
 * 3. 秒必须恰好 2 位（`1:2` 不认）、分/秒必须 `00..59`（`99:99` 不认）、
 *    小时/分钟最多 2 位（`123:45` 不认）；
 * 4. 普通数字（`1080p`、`av12345`）里没有冒号，天然不会命中。
 *
 * 命中时返回**命中位置**（原文下标）与毫秒数：播放页需要原文片段做「已跳到 xx:xx」的提示，
 * 保留下标也方便以后做高亮/替换。
 */
object DanmakuTimestampPolicy {

    /**
     * 一次命中。
     *
     * @param startIndex 时间戳在原文中的起始下标（含）
     * @param endIndex 时间戳在原文中的结束下标（**不含**）
     * @param timeMs 对应的播放位置（毫秒）
     * @param raw 时间戳原文（如 `01:23`、`1:23:45`），用于提示文案
     */
    data class Match(
        val startIndex: Int,
        val endIndex: Int,
        val timeMs: Long,
        val raw: String
    )

    /**
     * 在文本里找第一个合法时间戳；没有则返回 `null`。
     *
     * 弹幕正文可能为空、超长、含多段数字，这个函数不抛异常、不分配大对象（只扫描一遍）。
     */
    fun find(text: CharSequence?): Match? {
        if (text.isNullOrEmpty()) return null
        val length = text.length
        var i = 0
        while (i < length) {
            if (!isTimestampChar(text[i])) {
                i++
                continue
            }
            // 一段连续的 [0-9:]，整段拿去解析：这样 12:34:56:78 不会被截成 12:34:56
            val start = i
            while (i < length && isTimestampChar(text[i])) i++
            val raw = text.subSequence(start, i).toString()
            val timeMs = parseToken(raw)
            if (timeMs != null) {
                return Match(startIndex = start, endIndex = i, timeMs = timeMs, raw = raw)
            }
        }
        return null
    }

    /**
     * 解析单独一个时间戳 token（不接受任何多余字符）。
     *
     * 供单测与 `find` 复用；非法输入返回 `null`。
     */
    fun parseToken(raw: String?): Long? {
        if (raw.isNullOrEmpty()) return null
        val parts = raw.split(':')
        if (parts.size != 2 && parts.size != 3) return null
        if (parts.any { it.isEmpty() || it.any { c -> !c.isDigit() } }) return null

        val hours: Int
        val minutes: Int
        val seconds: Int
        if (parts.size == 2) {
            // mm:ss —— 分钟 1..2 位、秒恰好 2 位
            if (parts[0].length > 2) return null
            if (parts[1].length != 2) return null
            hours = 0
            minutes = parts[0].toIntOrNull() ?: return null
            seconds = parts[1].toIntOrNull() ?: return null
            if (minutes > 99) return null
        } else {
            // hh:mm:ss —— 小时 1..2 位、分与秒都恰好 2 位
            if (parts[0].length > 2) return null
            if (parts[1].length != 2 || parts[2].length != 2) return null
            hours = parts[0].toIntOrNull() ?: return null
            minutes = parts[1].toIntOrNull() ?: return null
            seconds = parts[2].toIntOrNull() ?: return null
        }
        if (minutes > 59 || seconds > 59) return null
        return ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L
    }

    private fun isTimestampChar(c: Char): Boolean = c.isDigit() || c == ':'
}
