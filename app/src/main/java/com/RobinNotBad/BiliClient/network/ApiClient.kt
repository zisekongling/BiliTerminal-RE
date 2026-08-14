package com.RobinNotBad.BiliClient.network

import com.RobinNotBad.BiliClient.BuildConfig
import com.RobinNotBad.BiliClient.util.PerformanceManager
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiClient @Inject constructor() {

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        prettyPrint = false
    }

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val cookies = SharedPreferencesUtil.getString("cookies", "")
        val request = if (cookies.isNotEmpty()) {
            original.newBuilder()
                .header("Cookie", cookies)
                .header("User-Agent", USER_AGENT_WEB)
                .header("Referer", "https://www.bilibili.com/")
                .build()
        } else {
            original.newBuilder()
                .header("User-Agent", USER_AGENT_WEB)
                .header("Referer", "https://www.bilibili.com/")
                .build()
        }
        chain.proceed(request)
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .connectionPool(
            okhttp3.ConnectionPool(
                PerformanceManager.getOkHttpConnectionPoolSize(),
                PerformanceManager.getOkHttpKeepAliveMinutes().toLong(),
                TimeUnit.MINUTES
            )
        )
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .build()

    inline fun <reified T> createService(baseUrl: String): T {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(T::class.java)
    }

    companion object {
        const val BILIBILI_API_BASE = "https://api.bilibili.com/"
        const val BILIBILI_APP_BASE = "https://app.bilibili.com/"
        const val BILIBILI_GRPC_BASE = "https://grpc.biliapi.net/"
        const val USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
