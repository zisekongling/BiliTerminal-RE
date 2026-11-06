package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 「上次播放会话」哨兵：定周期把「在播什么、播到哪、队列是什么」落盘，
 * 应用被杀/崩溃/重启后由主页或播放器一键续播。
 *
 * 为什么单独存队列 token：会话记录每 15 秒写一次，而队列 token 可能有几十 KB，
 * 不能跟着高频写；token 只在队列变化/退出时更新，会话里只存「有没有 token」标记。
 */
data class PlaySession(
    val aid: Long,
    val cid: Long,
    /** 当前登录账号的 mid（**不是**视频作者，见 [com.RobinNotBad.BiliClient.model.PlayerData#mid]）。 */
    val mid: Long,
    val epid: Long,
    val seasonId: Long,
    val seasonType: Int,
    /** 对应 [com.RobinNotBad.BiliClient.model.PlayerData] 的 TYPE_*。 */
    val type: Int,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val source: PlayQueueSource,
    val pageIndex: Int,
    val hasQueueToken: Boolean,
    val updatedAt: Long,
    /**
     * 视频作者 mid（连播用，见 [com.RobinNotBad.BiliClient.model.PlayerData#uploaderMid]）；0 = 未知。
     *
     * 放在参数表**末尾**是有意的：它与 [epid] 同为 `Long`，将来谁写一次位置传参就会把 epid
     * 静默塞进作者字段（编译不报错）。末尾与文本 / Parcel 的追加顺序也一致。
     */
    val uploaderMid: Long = 0L
) {
    fun isMeaningful(): Boolean = positionMs > 0L || cid > 0L || aid > 0L || epid > 0L
}

/** 纯函数编解码，便于 JVM 单测（不碰 SharedPreferences）。 */
object PlaySessionCodec {
    private const val HEADER = "PLAYSESSION1"

    fun encode(session: PlaySession): String {
        val sb = StringBuilder()
        sb.append(HEADER).append('\t')
            .append(session.aid).append('\t')
            .append(session.cid).append('\t')
            .append(session.mid).append('\t')
            .append(session.epid).append('\t')
            .append(session.seasonId).append('\t')
            .append(session.seasonType).append('\t')
            .append(session.type).append('\t')
            .append(session.positionMs).append('\t')
            .append(session.durationMs).append('\t')
            .append(session.source.storageValue).append('\t')
            .append(session.pageIndex).append('\t')
            .append(if (session.hasQueueToken) 1 else 0).append('\t')
            .append(session.updatedAt).append('\t')
            .append(escape(session.title))
        // uploaderMid 只能追加在**末尾**：标题在它前面且已转义（不含制表符），
        // 老版本写下的 15 段文本仍然能按前 15 段正确解析，只是读不到作者（回落 0）。
        // 插到标题之前会让老文本的标题被当成数字解析，标题静默丢失。
        sb.append('\t').append(session.uploaderMid)
        return sb.toString()
    }

    fun decode(text: String?): PlaySession? {
        if (text.isNullOrBlank()) return null
        val parts = text.split('\t')
        if (parts.size < 15 || parts[0] != HEADER) return null
        val aid = parts[1].toLongOrNull() ?: return null
        val cid = parts[2].toLongOrNull() ?: 0L
        val mid = parts[3].toLongOrNull() ?: 0L
        val epid = parts[4].toLongOrNull() ?: 0L
        val seasonId = parts[5].toLongOrNull() ?: 0L
        val seasonType = parts[6].toIntOrNull() ?: 0
        val type = parts[7].toIntOrNull() ?: 0
        val positionMs = parts[8].toLongOrNull() ?: return null
        val durationMs = parts[9].toLongOrNull() ?: 0L
        val source = PlayQueueSource.fromStorage(parts[10])
        val pageIndex = parts[11].toIntOrNull() ?: 0
        val hasQueueToken = parts[12] == "1"
        val updatedAt = parts[13].toLongOrNull() ?: 0L
        val title = unescape(parts[14])
        // 老会话没有这一段：缺了当 0（作者未知），播放器会按 aid 现查一次
        val uploaderMid = parts.getOrNull(15)?.toLongOrNull() ?: 0L
        return PlaySession(
            aid = aid, cid = cid, mid = mid, uploaderMid = uploaderMid, epid = epid, seasonId = seasonId,
            seasonType = seasonType, type = type, title = title,
            positionMs = positionMs.coerceAtLeast(0L), durationMs = durationMs.coerceAtLeast(0L),
            source = source, pageIndex = pageIndex, hasQueueToken = hasQueueToken,
            updatedAt = updatedAt
        )
    }

    private fun escape(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '\t' -> sb.append("\\t")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun unescape(text: String): String {
        if (text.isEmpty()) return ""
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == '\\' && i + 1 < text.length) {
                when (text[i + 1]) {
                    '\\' -> sb.append('\\')
                    't' -> sb.append('\t')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    else -> sb.append(text[i + 1])
                }
                i += 2
            } else {
                sb.append(ch); i++
            }
        }
        return sb.toString()
    }
}

/**
 * 会话哨兵的落盘层。用 [SharedPreferencesUtil]（全局偏好，和播放器其它设置同源），
 * 不改 SettingsKeys/SharedPreferencesUtil 本体。
 */
object PlaySessionStore {
    const val KEY_SESSION = "player_last_session_v1"
    const val KEY_QUEUE_TOKEN = "player_last_queue_token_v1"

    fun save(session: PlaySession) {
        SharedPreferencesUtil.putString(KEY_SESSION, PlaySessionCodec.encode(session))
    }

    fun last(): PlaySession? =
        PlaySessionCodec.decode(SharedPreferencesUtil.getString(KEY_SESSION, null))

    fun saveQueueToken(token: String?) {
        if (token.isNullOrEmpty()) SharedPreferencesUtil.removeValue(KEY_QUEUE_TOKEN)
        else SharedPreferencesUtil.putString(KEY_QUEUE_TOKEN, token)
    }

    fun queueToken(): String? =
        SharedPreferencesUtil.getString(KEY_QUEUE_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun clear() {
        SharedPreferencesUtil.removeValue(KEY_SESSION)
        SharedPreferencesUtil.removeValue(KEY_QUEUE_TOKEN)
    }
}
