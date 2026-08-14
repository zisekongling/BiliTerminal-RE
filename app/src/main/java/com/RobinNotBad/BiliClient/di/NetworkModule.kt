package com.RobinNotBad.BiliClient.di

import com.RobinNotBad.BiliClient.network.ApiClient
import com.RobinNotBad.BiliClient.network.api.BiliApiService
import com.RobinNotBad.BiliClient.network.api.VideoFeedApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideBiliApiService(apiClient: ApiClient): BiliApiService {
        return apiClient.createService(ApiClient.BILIBILI_API_BASE)
    }

    @Provides
    @Singleton
    fun provideVideoFeedApiService(apiClient: ApiClient): VideoFeedApiService {
        return apiClient.createService(ApiClient.BILIBILI_API_BASE)
    }
}