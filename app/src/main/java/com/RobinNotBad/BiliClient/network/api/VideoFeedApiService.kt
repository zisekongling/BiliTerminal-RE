package com.RobinNotBad.BiliClient.network.api

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

interface VideoFeedApiService {

    @GET("x/web-interface/wbi/index/top/feed/rcmd")
    suspend fun getRecommend(
        @Query("fresh_type") freshType: Int = 3,
        @Query("ps") ps: Int = 20,
        @Query("version") version: Int = 1
    ): JsonObject

    @GET("x/web-interface/popular")
    suspend fun getPopular(
        @Query("pn") pn: Int,
        @Query("ps") ps: Int = 20
    ): JsonObject

    @GET("x/web-interface/ranking/v2")
    suspend fun getRanking(
        @Query("rid") rid: Int = 0,
        @Query("type") type: String = "all"
    ): JsonObject

    @GET("x/space/wbi/arc/search")
    suspend fun getUserVideos(
        @Query("mid") mid: Long,
        @Query("ps") ps: Int = 30,
        @Query("pn") pn: Int = 1
    ): JsonObject

    @GET("x/web-interface/history/cursor")
    suspend fun getHistory(
        @Query("max") max: Long = 0,
        @Query("view_at") viewAt: Long = 0,
        @Query("business") business: String = "",
        @Query("type") type: String = "archive",
        @Query("ps") ps: Int = 30
    ): JsonObject

    @GET("x/relation/stat")
    suspend fun getRelationStat(
        @Query("vmid") vmid: Long
    ): JsonObject
}