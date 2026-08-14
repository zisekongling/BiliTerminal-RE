package com.RobinNotBad.BiliClient.network.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val code: Int = -1,
    val message: String = "",
    val data: T? = null
) {
    val isSuccess: Boolean get() = code == 0
}

@Serializable
data class ApiListResponse<T>(
    val code: Int = -1,
    val message: String = "",
    val data: ApiListData<T>? = null
) {
    val isSuccess: Boolean get() = code == 0
}

@Serializable
data class ApiListData<T>(
    val list: List<T>? = null,
    val has_more: Boolean = false,
    val offset: String? = null,
    val total: Int = 0
)

@Serializable
data class VideoSearchResult(
    val type: String = "",
    val id: Long = 0,
    val author: String = "",
    val mid: Long = 0,
    val typeid: String = "",
    val typename: String = "",
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val description: String = "",
    val pic: String = "",
    val play: Int = 0,
    val video_review: Int = 0,
    val favorites: Int = 0,
    val tag: String = "",
    val review: Int = 0,
    val pubdate: Long = 0,
    val senddate: Long = 0,
    val duration: String = "",
    val rank_score: Long = 0,
    val badgepay: Boolean = false
)

@Serializable
data class PopularVideoItem(
    val stat: String = "",
    val title: String = "",
    val short_link: String = "",
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val copyright: Int = 0,
    val cover: String = "",
    val desc: String = "",
    val duration: Long = 0,
    val owner: VideoOwner = VideoOwner(),
    val pubdate: Long = 0,
    val stat_view: Int = 0,
    val stat_danmaku: Int = 0,
    val stat_reply: Int = 0,
    val stat_favorite: Int = 0,
    val stat_coin: Int = 0,
    val stat_share: Int = 0,
    val stat_like: Int = 0,
    val tname: String = "",
    val videos: Int = 1
)

@Serializable
data class VideoOwner(
    val mid: Long = 0,
    val name: String = "",
    val face: String = ""
)

@Serializable
data class UserCardInfo(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val sign: String = "",
    val level: Int = 0,
    val sex: String = "",
    val fans: Int = 0,
    val attention: Int = 0,
    val archive_count: Int = 0,
    val article_count: Int = 0,
    val is_followed: Int = 0
)

@Serializable
data class VideoDetailInfo(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    val desc: String = "",
    val cover: String = "",
    val owner: VideoOwner = VideoOwner(),
    val pages: List<VideoPage> = emptyList(),
    val stat: VideoStat = VideoStat(),
    val pubdate: Long = 0,
    val duration: Long = 0,
    val tname: String = "",
    val videos: Int = 1,
    val cids: Map<String, Long> = emptyMap(),
    val short_link: String = ""
)

@Serializable
data class VideoPage(
    val cid: Long = 0,
    val page: Int = 0,
    val part: String = "",
    val duration: Long = 0
)

@Serializable
data class VideoStat(
    val view: Int = 0,
    val danmaku: Int = 0,
    val reply: Int = 0,
    val favorite: Int = 0,
    val coin: Int = 0,
    val share: Int = 0,
    val like: Int = 0
)

sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val code: Int, val message: String, val exception: Throwable? = null) : NetworkResult<Nothing>()
    data object Loading : NetworkResult<Nothing>()
}