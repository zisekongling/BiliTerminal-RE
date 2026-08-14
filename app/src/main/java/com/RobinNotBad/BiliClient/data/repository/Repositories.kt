package com.RobinNotBad.BiliClient.data.repository

import com.RobinNotBad.BiliClient.network.api.BiliApiService
import com.RobinNotBad.BiliClient.network.model.NetworkResult
import com.RobinNotBad.BiliClient.network.model.PopularVideoItem
import com.RobinNotBad.BiliClient.network.model.UserCardInfo
import com.RobinNotBad.BiliClient.network.model.VideoDetailInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val apiService: BiliApiService
) {
    suspend fun getPopularVideos(): NetworkResult<List<PopularVideoItem>> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getPopularVideos()
                if (response.isSuccess && response.data != null) {
                    NetworkResult.Success(response.data.list ?: emptyList())
                } else {
                    NetworkResult.Error(response.code, response.message)
                }
            } catch (e: Exception) {
                NetworkResult.Error(-1, e.message ?: "网络请求失败", e)
            }
        }

    suspend fun getVideoDetail(aid: Long? = null, bvid: String? = null): NetworkResult<VideoDetailInfo> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getVideoDetail(aid, bvid)
                if (response.isSuccess && response.data != null) {
                    NetworkResult.Success(response.data)
                } else {
                    NetworkResult.Error(response.code, response.message)
                }
            } catch (e: Exception) {
                NetworkResult.Error(-1, e.message ?: "网络请求失败", e)
            }
        }

    suspend fun getRecommendVideos(): NetworkResult<Any> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getRecommendVideos()
                if (response.isSuccess) {
                    NetworkResult.Success(response.data ?: Any())
                } else {
                    NetworkResult.Error(response.code, response.message)
                }
            } catch (e: Exception) {
                NetworkResult.Error(-1, e.message ?: "网络请求失败", e)
            }
        }

    suspend fun getRankingVideos(rid: Int = 0): NetworkResult<Any> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getRankingVideos(rid)
                if (response.isSuccess) {
                    NetworkResult.Success(response.data ?: Any())
                } else {
                    NetworkResult.Error(response.code, response.message)
                }
            } catch (e: Exception) {
                NetworkResult.Error(-1, e.message ?: "网络请求失败", e)
            }
        }
}

@Singleton
class UserRepository @Inject constructor(
    private val apiService: BiliApiService
) {
    suspend fun getUserInfo(mid: Long): NetworkResult<UserCardInfo> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getUserInfo(mid)
                if (response.isSuccess && response.data != null) {
                    NetworkResult.Success(response.data)
                } else {
                    NetworkResult.Error(response.code, response.message)
                }
            } catch (e: Exception) {
                NetworkResult.Error(-1, e.message ?: "网络请求失败", e)
            }
        }

    suspend fun getUserVideos(mid: Long, page: Int = 1): NetworkResult<Any> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getUserVideos(mid, pn = page)
                if (response.isSuccess) {
                    NetworkResult.Success(response.data ?: Any())
                } else {
                    NetworkResult.Error(response.code, response.message)
                }
            } catch (e: Exception) {
                NetworkResult.Error(-1, e.message ?: "网络请求失败", e)
            }
        }

    suspend fun getFollowings(mid: Long, page: Int = 1): NetworkResult<Any> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getFollowings(mid, pn = page)
                if (response.isSuccess) {
                    NetworkResult.Success(response.data ?: Any())
                } else {
                    NetworkResult.Error(response.code, response.message)
                }
            } catch (e: Exception) {
                NetworkResult.Error(-1, e.message ?: "网络请求失败", e)
            }
        }
}

@Singleton
class SearchRepository @Inject constructor(
    private val apiService: BiliApiService
) {
    suspend fun search(keyword: String, page: Int = 1): NetworkResult<Any> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.searchVideos(keyword, page)
                if (response.isSuccess) {
                    NetworkResult.Success(response.data ?: Any())
                } else {
                    NetworkResult.Error(response.code, response.message)
                }
            } catch (e: Exception) {
                NetworkResult.Error(-1, e.message ?: "网络请求失败", e)
            }
        }
}