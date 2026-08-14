package com.RobinNotBad.BiliClient.network.api

import com.RobinNotBad.BiliClient.network.model.*
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface BiliApiService {

    @GET("x/web-interface/popular/series/one")
    suspend fun getPopularVideos(
        @Query("number") number: Int = 1
    ): ApiListResponse<PopularVideoItem>

    @GET("x/web-interface/ranking/v2")
    suspend fun getRankingVideos(
        @Query("rid") rid: Int = 0,
        @Query("type") type: String = "all"
    ): ApiResponse<Any>

    @GET("x/web-interface/search/all/v2")
    suspend fun searchVideos(
        @Query("keyword") keyword: String,
        @Query("page") page: Int = 1,
        @Query("type") type: String = "video"
    ): ApiResponse<Any>

    @GET("x/web-interface/wbi/view")
    suspend fun getVideoDetail(
        @Query("aid") aid: Long? = null,
        @Query("bvid") bvid: String? = null
    ): ApiResponse<VideoDetailInfo>

    @GET("x/space/wbi/acc/info")
    suspend fun getUserInfo(
        @Query("mid") mid: Long
    ): ApiResponse<UserCardInfo>

    @GET("x/web-interface/dynamic/region")
    suspend fun getDynamicFeeds(
        @Query("rid") rid: Long = 0,
        @Query("type") type: String = "all"
    ): ApiResponse<Any>

    @GET("x/relation/stat")
    suspend fun getRelationStat(
        @Query("vmid") vmid: Long
    ): ApiResponse<Any>

    @GET("x/space/wbi/arc/search")
    suspend fun getUserVideos(
        @Query("mid") mid: Long,
        @Query("ps") ps: Int = 30,
        @Query("pn") pn: Int = 1
    ): ApiResponse<Any>

    @GET("x/relation/followings")
    suspend fun getFollowings(
        @Query("vmid") vmid: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20
    ): ApiResponse<Any>

    @GET("x/web-interface/wbi/index/top/feed/rcmd")
    suspend fun getRecommendVideos(
        @Query("fresh_type") freshType: Int = 3,
        @Query("ps") ps: Int = 20,
        @Query("version") version: Int = 1
    ): ApiResponse<Any>

    @GET("x/v2/reply/main")
    suspend fun getReplies(
        @Query("oid") oid: Long,
        @Query("type") type: Int = 1,
        @Query("mode") mode: Int = 3,
        @Query("next") next: Int = 0,
        @Query("ps") ps: Int = 20
    ): ApiResponse<Any>
}