package com.RobinNotBad.BiliClient.helper

import android.content.Context
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.PerformanceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

@GlideModule
class CustomGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // 根据设备性能动态调整缓存大小
        val diskCacheSizeMB = PerformanceManager.getGlideDiskCacheSizeMB()
        val memoryCacheSizeMB = PerformanceManager.getGlideMemoryCacheSizeMB()
        val memoryCacheSizeBytes = memoryCacheSizeMB.toLong() * 1024 * 1024

        builder.setDiskCache(InternalCacheDiskCacheFactory(context, diskCacheSizeMB * 1024 * 1024))
            .setMemoryCache(com.bumptech.glide.load.engine.cache.LruResourceCache(memoryCacheSizeBytes))
            .setDefaultRequestOptions(
                RequestOptions()
                    .format(DecodeFormat.PREFER_RGB_565)
                    .apply {
                        // 仅在非高性能设备上禁用硬件位图以节省内存
                        if (!PerformanceManager.isHardwareBitmapEnabled()) {
                            disallowHardwareConfig()
                        }
                    }
            )
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val builder: OkHttpClient.Builder = NetWorkUtil.setOkHttpSsl(OkHttpClient.Builder())
        builder.addInterceptor { chain ->
            val headers: ArrayList<String> = NetWorkUtil.webHeaders
            val requestBuilder: Request.Builder = chain.request().newBuilder()
            var i = 0
            while (i < headers.size) {
                requestBuilder.addHeader(headers[i], headers[i + 1])
                i += 2
            }
            chain.proceed(requestBuilder.build())
        }

        registry.replace(
            GlideUrl::class.java, InputStream::class.java,
            OkHttpUrlLoader.Factory(
                builder
                    .dns(NetWorkUtil.Inet4Selector())
                    .pingInterval(8, TimeUnit.SECONDS)
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(16, TimeUnit.SECONDS).build()
            )
        )
    }
}