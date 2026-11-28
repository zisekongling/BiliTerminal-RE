package com.RobinNotBad.BiliClient.player

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.Logu
import com.bumptech.glide.Glide

/**
 * 拼版图的内存缓存与异步加载。
 *
 * 为什么要自己缓存：一张拼版是 1600×900（10×10 格 × 160×90），ARGB_8888 下约 5.8MB。
 * 拖动时每一帧都要从它上面裁一格，绝不能每帧都让 Glide 走一遍请求/解码；
 * 而长视频会有 2~3 张拼版，也不能无限留。
 *
 * <p>**不用 `into(ImageView)`**：Glide 的 `with(applicationContext)` 是进程级的
 * RequestManager，不随 Activity 销毁清理，匿名 target 忘了 clear 就是「销毁后仍回调」
 * 那类崩溃（本仓库修过，见 `applyCoverTint` 的注释）。这里走 `submit()` 拿 Future，
 * 没有 target 生命周期问题。
 *
 * <p>容量取 3：拖动最多前后各跨一张，再多也留不住；超出按访问顺序淘汰最久没用的。
 */
object VideoShotLoader {

    private const val MAX_SHEETS = 3

    private val cache = object : LinkedHashMap<String, Bitmap>(MAX_SHEETS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
            size > MAX_SHEETS
    }

    private val inFlight = HashSet<String>()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 已缓存的拼版；没有就返回 null（调用方先画底衬，等 [load] 回调）。 */
    fun cached(url: String): Bitmap? = synchronized(cache) { cache[url] }

    /**
     * 异步取一张拼版，成功后回主线程。
     *
     * @param onReady `(url, bitmap)`；失败时 bitmap 为 null。**url 一并回传**，
     *   调用方据此判断「等回来的是不是当前这一张」，避免拖到别处后回包把画面盖回旧的。
     */
    fun load(context: Context, url: String, onReady: (String, Bitmap?) -> Unit) {
        cached(url)?.let {
            mainHandler.post { onReady(url, it) }
            return
        }
        synchronized(inFlight) {
            // 同一张图拖动时会反复请求，已经在路上的就别再排一次
            if (!inFlight.add(url)) return
        }
        val appContext = context.applicationContext
        CenterThreadPool.run {
            val bitmap = try {
                Glide.with(appContext).asBitmap().load(url).submit().get()
            } catch (e: Exception) {
                Logu.d("缩略图预览", "拼版加载失败 ${e.javaClass.simpleName} <- $url")
                null
            }
            synchronized(inFlight) { inFlight.remove(url) }
            if (bitmap != null) {
                synchronized(cache) { cache[url] = bitmap }
            }
            mainHandler.post { onReady(url, bitmap) }
        }
    }

    /** 清空缓存（退出播放页时调，别让几 MB 的拼版跟着进程活）。 */
    fun clear() {
        synchronized(cache) { cache.clear() }
    }
}
