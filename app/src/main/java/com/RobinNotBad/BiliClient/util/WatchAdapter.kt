package com.RobinNotBad.BiliClient.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

object WatchAdapter {

    private const val WATCH_MIN_DP_WIDTH = 180f
    private const val WATCH_MAX_DP_WIDTH = 250f

    fun isWatchDevice(context: Context): Boolean {
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val dpWidth = metrics.widthPixels / density
        val dpHeight = metrics.heightPixels / density

        return (dpWidth in WATCH_MIN_DP_WIDTH..WATCH_MAX_DP_WIDTH) ||
                (dpHeight in WATCH_MIN_DP_WIDTH..WATCH_MAX_DP_WIDTH)
    }

    fun isRoundWatch(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val config = context.resources.configuration
            return config.isScreenRound
        }
        return false
    }

    fun getOptimalTextSize(context: Context): Float {
        return if (isWatchDevice(context)) 13f else 15f
    }

    fun getOptimalPadding(context: Context): Int {
        val density = context.resources.displayMetrics.density
        return if (isWatchDevice(context)) {
            (6 * density).toInt()
        } else {
            (12 * density).toInt()
        }
    }

    fun getOptimalGridColumns(context: Context): Int {
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val dpWidth = metrics.widthPixels / density
        return when {
            dpWidth < 200 -> 2
            dpWidth < 300 -> 3
            else -> 4
        }
    }

    fun getScreenSizeCategory(context: Context): ScreenSizeCategory {
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val dpWidth = metrics.widthPixels / density

        return when {
            dpWidth < 180 -> ScreenSizeCategory.SMALL_WATCH
            dpWidth < 250 -> ScreenSizeCategory.LARGE_WATCH
            dpWidth < 600 -> ScreenSizeCategory.PHONE
            else -> ScreenSizeCategory.TABLET
        }
    }

    enum class ScreenSizeCategory {
        SMALL_WATCH,
        LARGE_WATCH,
        PHONE,
        TABLET
    }
}

object PerformanceOptimizer {

    private val imageCache = object : LinkedHashMap<String, android.graphics.Bitmap>(
        20, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, android.graphics.Bitmap>?): Boolean {
            return size > 20
        }
    }

    fun cacheImage(key: String, bitmap: android.graphics.Bitmap) {
        synchronized(imageCache) {
            imageCache[key] = bitmap
        }
    }

    fun getCachedImage(key: String): android.graphics.Bitmap? {
        synchronized(imageCache) {
            return imageCache[key]
        }
    }

    fun clearImageCache() {
        synchronized(imageCache) {
            imageCache.values.forEach { it.recycle() }
            imageCache.clear()
        }
    }

    fun estimateMemoryUsage(context: Context): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    fun suggestGarbageCollectIfNeeded(context: Context): Boolean {
        val usedMemory = estimateMemoryUsage(context)
        val maxMemory = Runtime.getRuntime().maxMemory()
        return if (usedMemory > maxMemory * 0.75) {
            System.gc()
            true
        } else {
            false
        }
    }
}

object CompatHelper {

    fun supportsNotificationChannels(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    fun supportsPictureInPicture(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    fun supportsAdaptiveIcons(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    fun supportsDarkTheme(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    fun supportsBluetoothLe(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
    }

    fun getDeviceInfo(context: Context): Map<String, String> {
        return mapOf(
            "model" to (Build.MODEL ?: "Unknown"),
            "manufacturer" to (Build.MANUFACTURER ?: "Unknown"),
            "sdk_version" to Build.VERSION.SDK_INT.toString(),
            "release" to (Build.VERSION.RELEASE ?: "Unknown"),
            "is_watch" to WatchAdapter.isWatchDevice(context).toString(),
            "is_round" to WatchAdapter.isRoundWatch(context).toString(),
            "screen_category" to WatchAdapter.getScreenSizeCategory(context).name
        )
    }
}