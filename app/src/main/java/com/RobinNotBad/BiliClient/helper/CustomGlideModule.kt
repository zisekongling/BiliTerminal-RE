package com.RobinNotBad.BiliClient.helper

import android.content.Context
import android.util.Log
import com.RobinNotBad.BiliClient.BuildConfig
import com.RobinNotBad.BiliClient.util.ImageRequestPolicy
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
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.InputStream
import java.util.concurrent.TimeUnit

@GlideModule
class CustomGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // 根据设备性能动态调整缓存大小
        val diskCacheSizeMB = PerformanceManager.getGlideDiskCacheSizeMB()
        val memoryCacheSizeMB = PerformanceManager.getGlideMemoryCacheSizeMB()
        val memoryCacheSizeBytes = memoryCacheSizeMB.toLong() * 1024 * 1024

        // 26.11.09：只在 Debug 包里把 Glide 自己的日志开到 VERBOSE。
        // 用途是排查线上崩溃 `Cannot recycle a resource while it is still acquired`：那条崩溃是
        // 「延迟回收」（ResourceRecycler 投递到主线程队列）里命中 acquired > 0，现场没有异常可抓，
        // 只能靠 Glide 自己的 Engine 日志看「从 cache 取到 / 放回 cache / 被淘汰」的先后顺序。
        // 用 BuildConfig.DEBUG（编译期常量）而不是运行期开关：Release 里这段会被 R8 折叠，
        // 发布包既不打这些日志、也没有任何运行期开销。
        if (BuildConfig.DEBUG) {
            builder.setLogLevel(Log.VERBOSE)
        }

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
        // 审计 P0-1：原实现把 NetWorkUtil.webHeaders（第一组就是完整登录 Cookie）全量追加到
        // 每一个图片请求上，站外图床（afdiancdn.com 等）也会收到 SESSDATA / bili_jct，等于把
        // 账号会话交给第三方。现在按目标域名收敛：只有 B 站白名单域才带会话头，站外域只留 UA。
        // 判定逻辑抽在 ImageRequestPolicy（纯 JVM、有单测），这里只负责写头。
        val sessionFilter = Interceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            val requestBuilder = request.newBuilder()
            // 先删再写：站外域必须显式摘掉会话头，防止跨主机重定向把上一跳的 Cookie 带过去。
            ImageRequestPolicy.headersToRemove(url).forEach { requestBuilder.removeHeader(it) }
            ImageRequestPolicy.headersFor(url, NetWorkUtil.webHeaders)
                .forEach { (name, value) -> requestBuilder.header(name, value) }
            chain.proceed(requestBuilder.build())
        }

        val builder: OkHttpClient.Builder = NetWorkUtil.setOkHttpSsl(OkHttpClient.Builder())
            .addInterceptor(sessionFilter)
            // 网络拦截器对「每一跳」再过滤一次：OkHttp 跟跨主机重定向会保留自定义头，
            // 只在应用层过滤挡不住「B 站图 302 到站外」。两个拦截器共用同一份策略，不会漂移。
            .addNetworkInterceptor(sessionFilter)

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