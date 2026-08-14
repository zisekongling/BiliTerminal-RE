package com.RobinNotBad.BiliClient.model

import org.json.JSONObject

data class ShortVideoItem(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    val cover: String = "",
    val duration: Long = 0,
    val authorName: String = "",
    val authorFace: String = "",
    val authorMid: Long = 0,
    val viewCount: String = "",
    val danmakuCount: String = "",
    val likeCount: Long = 0,
    val desc: String = "",
    var videoUrl: String = "",
    var danmakuUrl: String = "",
    var qnStrList: Array<String>? = null,
    var qnValueList: IntArray? = null,
    var currentQuality: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShortVideoItem) return false
        return aid == other.aid && cid == other.cid
    }

    override fun hashCode(): Int = 31 * aid.hashCode() + cid.hashCode()

    companion object {
        fun fromFeedItem(json: JSONObject): ShortVideoItem? {
            val goto = json.optString("goto", json.optString("card_goto", ""))
            if (goto != "av" && goto != "vertical_av") return null

            val cardType = json.optString("card_type", "")
            if (cardType == "banner_v8") return null

            val playerArgs = json.optJSONObject("player_args")
            val args = json.optJSONObject("args")
            val owner = json.optJSONObject("owner")
            val stat = json.optJSONObject("stat")

            val aid = when {
                playerArgs != null -> playerArgs.optLong("aid", 0)
                args != null -> args.optLong("aid", 0)
                else -> json.optString("param", "0").toLongOrNull() ?: 0L
            }
            if (aid == 0L) return null

            val cid = playerArgs?.optLong("cid", 0) ?: 0L
            val bvid = json.optString("bvid", args?.optString("bvid", "") ?: "")

            val duration = when {
                playerArgs != null && playerArgs.has("duration") -> playerArgs.optLong("duration", 0)
                else -> json.optLong("duration", 0)
            }

            val authorName = when {
                owner != null -> owner.optString("name", "")
                args != null -> args.optString("up_name", "")
                else -> json.optJSONObject("desc_button")?.optString("text", "") ?: ""
            }

            val authorMid = when {
                owner != null -> owner.optLong("mid", 0)
                args != null -> args.optLong("up_id", 0)
                else -> 0L
            }

            val authorFace = when {
                owner != null -> owner.optString("face", "")
                else -> ""
            }

            val viewCount = when {
                stat != null -> formatCount(stat.optLong("view", 0))
                json.has("cover_left_text_1") -> json.optString("cover_left_text_1", "")
                json.has("sub_title") -> json.optString("sub_title", "")
                json.has("view_content") -> json.optString("view_content", "")
                else -> ""
            }

            val danmakuCount = when {
                stat != null -> formatCount(stat.optLong("danmaku", 0))
                else -> json.optString("cover_left_text_2", "")
            }

            val title = json.optString("title", "")
            val cover = json.optString("cover", "")
            val desc = json.optString("desc", json.optString("talk_back", ""))
            val likeCount = stat?.optLong("like", 0) ?: 0L

            return ShortVideoItem(
                aid = aid,
                bvid = bvid,
                cid = cid,
                title = title,
                cover = cover,
                duration = duration,
                authorName = authorName,
                authorFace = authorFace,
                authorMid = authorMid,
                viewCount = viewCount,
                danmakuCount = danmakuCount,
                likeCount = likeCount,
                desc = desc
            )
        }

        private fun formatCount(count: Long): String {
            if (count <= 0) return ""
            return when {
                count >= 100000000 -> "${count / 100000000}.${(count % 100000000) / 10000000}亿"
                count >= 10000 -> "${count / 10000}.${(count % 10000) / 1000}万"
                else -> count.toString()
            }
        }
    }
}