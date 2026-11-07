package com.RobinNotBad.BiliClient.player

import android.content.Context
import com.RobinNotBad.BiliClient.api.DanmakuApi
import com.RobinNotBad.BiliClient.model.DmSegMobileReply
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.DanmakuDiskCache
import com.RobinNotBad.BiliClient.util.Logu
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.TreeSet

/**
 * 弹幕分段窗口加载器（批次 2）。
 *
 * ## 为什么要有它
 *
 * 旧链路（`PlayerActivity.downdanmuNew()`）一次性 `DanmakuApi.getAllVideoDanmaku()` 把**整段视频**
 * 的 6 分钟分段全下完再交给 DFM。2 小时视频 = 20 次带 WBI 签名的请求 + 20 段 protobuf 全量进内存，
 * 而手表上用户通常只看开头几分钟。对方分支的做法是「窗口」：只保证当前位置前后各几段在内存里，
 * 随着播放推进再补。本类就是那个窗口加载器。
 *
 * ## 窗口怎么算
 *
 * 分段固定 360 秒（`DanmakuApi.SEGMENT_SECONDS`，`segment_index` **从 1 开始**），窗口 = 当前段
 * 前后各 [radius] 段（默认 2，即最多 5 段 ≈ 30 分钟）。窗口算法抽成纯函数 [DanmakuWindow]，
 * 单测见 `DanmakuWindowTest`。
 *
 * ## 三段式用法（播放页接线，见 `docs/review/fix-progress.md`）
 *
 * ```kotlin
 * val loader = DanmakuStreamLoader()          // 一个视频实例一个
 * loader.attach(context, aid, cid, durationSec)
 * loader.setLoadPolicy(danmakuLoadPolicy)     // 可选：注入数量保护，拉段前先问它
 * if (loader.prepare(manager)) { /* 首窗口已交给 DFM */ } else { /* 回退旧版 XML */ }
 * loader.ensureWindow(video_now)              // 进度回调 / seek 后调用，内部按段去重
 * loader.release()                            // onDestroy
 * ```
 *
 * ## 失败策略：静默降级
 *
 * - 单段失败 ⇒ 该段留空，等 [ensureWindow] 在 15 秒后重试（`RETRY_DELAY_MS`）；
 * - 首窗口全部失败 ⇒ [prepare] 返回 `false`，调用方回退旧版 XML 链路（与旧行为一致）；
 * - 任何异常都不外抛：日志用 `Logu`，绝不让弹幕把播放搞崩。
 *
 * ## 与 DFM 的配合（**改动前必读**）
 *
 * 后续分段不是重新 `prepare()`，而是走 [DanmakuManager.appendSegments] →
 * `DanmakuView.addDanmaku(item)` 逐条追加。DFM 的 `DrawHandler.addDanmaku` 在 `drawTask == null`
 * （即 prepare 还没完成）时会**静默丢弃**，所以 `DanmakuManager` 内部有 pending 队列，
 * 在 `prepared()` 回调里补灌 —— 这也是为什么不要绕过 [DanmakuManager.appendSegments] 直接调 view。
 *
 * `DrawTask.drawDanmakus` 在发现「当前时间超出已加载区间」时会重取 `danmakuList.sub(...)`，
 * 所以后期追加的弹幕能被渲染到；但**已追加的分段不要再追加第二次**（`DanmakuManager` 不去重，
 * 重复追加会看到重影），本类用 `loadedSegments` 保证每段只喂一次。
 */
class DanmakuStreamLoader(
    /** 窗口半径：当前位置的前后各几段。默认 2（窗口最多 5 段 = 30 分钟）。 */
    private val radius: Int = DEFAULT_WINDOW_RADIUS,
    /** 分段时长（秒），默认与 `DanmakuApi.SEGMENT_SECONDS` 一致。 */
    private val segmentSeconds: Int = DEFAULT_SEGMENT_SECONDS
) {

    companion object {
        /** 默认窗口半径：前后各 2 段。 */
        const val DEFAULT_WINDOW_RADIUS = 2

        /** 默认分段时长（秒），与 `DanmakuApi.SEGMENT_SECONDS` 保持一致。 */
        const val DEFAULT_SEGMENT_SECONDS = 360

        /** 失败重试间隔：15 秒（与对方分支的窗口加载器一致）。 */
        const val RETRY_DELAY_MS = 15_000L

        /** 单次补窗口最多跑几轮（播放推进快时可能要连续补两三个窗口）。 */
        private const val MAX_FETCH_ROUNDS = 4
    }

    private var appContext: Context? = null
    private var aid: Long = 0L
    private var cid: Long = 0L
    private var maxDurationSec: Int = 0

    /** 懒创建：真正的缓存实例在第一次拉段时才建（`context.cacheDir` 在这里可能还没就绪）。 */
    private var cache: DanmakuDiskCache? = null

    /** 用弱引用：加载器生命周期可能比 DanmakuManager 长（切 P 会重建 manager），不该把旧的钉死。 */
    private var managerRef: WeakReference<DanmakuManager>? = null

    private var policy: DanmakuLoadPolicy? = null

    /** 已经喂给（或确认为空）的分段号，1-based。工作线程写、主线程读，全部走同步集合。 */
    private val loadedSegments: MutableSet<Int> = Collections.synchronizedSet(TreeSet<Int>())

    @Volatile
    private var released = false

    /** 是否已有一次补窗口在跑（保证**单个串行 worker**，并发拉段只会互相抢带宽）。 */
    @Volatile
    private var fetching = false

    /** 最近一次 ensureWindow 的播放位置，供 worker 反复核对「该补的段现在到底是哪几段」。 */
    @Volatile
    private var lastPositionMs: Long = -1L

    /** 失败后的下一次重试时间（墙钟毫秒）；0 = 不限制。 */
    @Volatile
    private var retryAfterMs: Long = 0L

    /**
     * 「这一轮」的代号，[attach] / [prepare] / [release] 各 +1。
     *
     * 补窗口 worker 在后台跑，切 P / 换视频时它可能还在拉**上一条视频**的分段。光靠 [released]
     * 挡不住：新视频 [attach] 一进来就把 [released] 改回 false、把 [fetching] 清掉，旧 worker
     * 于是以为自己还有效，继续把旧 cid 的分段塞进 [loadedSegments] 和新 manager——结果是两代
     * 数据混在一起，而且新旧两个 worker 会同时抢带宽。worker 只认启动时记下的这一代。
     */
    @Volatile
    private var generation: Int = 0

    /**
     * 绑定一条视频。可重复调用（切 P / 换视频）：会清空上一轮的分段记录。
     *
     * @param context 任意 Context（内部只取 `applicationContext` 的 `cacheDir`）
     * @param aid 稿件 avid（`seg.so` 的 `pid`）
     * @param cid 视频 cid（`seg.so` 的 `oid`，也是缓存键的一部分）
     * @param maxDurationSec 视频时长（秒），**未知时传 0**（按 1 段处理，与
     *   `DanmakuApi.segmentCountFor` 的约定一致）
     */
    @Synchronized
    fun attach(context: Context, aid: Long, cid: Long, maxDurationSec: Int) {
        this.appContext = context.applicationContext ?: context
        this.aid = aid
        this.cid = cid
        this.maxDurationSec = maxDurationSec
        generation++
        released = false
        fetching = false
        lastPositionMs = -1L
        retryAfterMs = 0L
        managerRef = null
        cache = null
        synchronized(loadedSegments) { loadedSegments.clear() }
    }

    /**
     * 注入弹幕数量保护（可空 = 不保护）。
     *
     * 窗口加载必须在**拉段之前**问一次：大视频被保护时连首段都不该请求，
     * 否则「不自动加载弹幕」的开关会在网络层被绕过。`DanmakuManager` 内部的策略检查是第二道闸。
     */
    fun setLoadPolicy(policy: DanmakuLoadPolicy?) {
        this.policy = policy
    }

    /**
     * 加载**起始窗口**并交给 DFM，用于 `prepareDanmaku { }` 里同步调用。
     *
     * 会真正发网络请求，**必须在后台线程调用**（旧链路的 `downdanmuNew()` 本来就在
     * `CenterThreadPool.run { }` 里跑）。内部会把全部非空分段一次性交给
     * [DanmakuManager.loadFromProtobufSegments]，等于「第一次 prepare 就把窗口内的弹幕全灌进 parser」。
     *
     * @return `true` 表示已成功交给 DFM；`false` 表示一段都没拿到（网络全失败 / 全部为空 /
     *   被数量保护抑制），调用方应按旧逻辑回退到 XML 弹幕
     */
    fun prepare(manager: DanmakuManager): Boolean {
        if (appContext == null) {
            Logu.e("弹幕窗口", "prepare 前没有 attach()，跳过窗口加载")
            return false
        }
        managerRef = WeakReference(manager)
        generation++
        released = false
        fetching = false
        retryAfterMs = 0L
        synchronized(loadedSegments) { loadedSegments.clear() }

        val activePolicy = policy
        if (activePolicy != null && !activePolicy.shouldLoad()) {
            Logu.d(
                "弹幕窗口",
                "数量保护：已抑制窗口加载（count=" + activePolicy.knownCount() + "）"
            )
            return false
        }

        val wanted = windowAt(0L)
        val replies = ArrayList<DmSegMobileReply>(wanted.count())
        var anySuccess = false
        for (index in wanted) {
            if (released) break
            val reply = fetchOne(index)
            if (reply == null) continue
            anySuccess = true
            synchronized(loadedSegments) { loadedSegments.add(index) }
            if (reply.elems.isNotEmpty()) replies.add(reply)
        }

        // 「一段都没成功」与「成功但全空」是两件事，但对调用方来说结论一样：
        // 不能靠窗口链路出弹幕了，回退旧版 XML（旧链路也是 segments.isEmpty() 就回退）。
        if (replies.isEmpty()) {
            if (!anySuccess) {
                Logu.w("弹幕窗口", "起始窗口全部拉取失败（aid=$aid cid=$cid），回退 XML 弹幕")
            } else {
                Logu.d("弹幕窗口", "起始窗口全部为空（aid=$aid cid=$cid），回退 XML 弹幕")
            }
            return false
        }

        manager.loadFromProtobufSegments(replies)
        Logu.d(
            "弹幕窗口",
            "起始窗口已加载：segments=$wanted 非空=${replies.size}/${wanted.count()} 条数=" +
                replies.sumOf { it.elems.size }
        )
        return true
    }

    /**
     * 按播放位置确保窗口完整（**可在主线程频繁调用**）。
     *
     * 内部只做一次「该区间 vs 已加载」的集合差；缺段才会排一次后台补拉（串行、可失败重试）。
     * 所以调用点在进度回调（每 30 秒或每次进度刷新）与 seek 之后各调一次即可，重复调用无副作用。
     */
    fun ensureWindow(positionMs: Long) {
        if (released) return
        if (managerRef?.get() == null) return
        lastPositionMs = positionMs
        val missing = DanmakuWindow.missing(snapshotLoaded(), windowAt(positionMs))
        if (missing.isEmpty()) return
        if (fetching) return
        if (System.currentTimeMillis() < retryAfterMs) return
        fetching = true
        val gen = generation
        CenterThreadPool.run { fetchWindowLoop(gen) }
    }

    /** 释放：停止后续补窗口（已经在跑的 worker 会在下一段前自行退出）。 */
    @Synchronized
    fun release() {
        generation++
        released = true
        managerRef = null
        fetching = false
        retryAfterMs = 0L
        synchronized(loadedSegments) { loadedSegments.clear() }
    }

    /** 已加载（含确认为空）的分段号快照，1-based，升序。 */
    fun loadedSegments(): Set<Int> = snapshotLoaded()

    /** 已加载分段覆盖到的区间（空 = 一段都没加载）。 */
    fun loadedRange(): IntRange {
        val snapshot = snapshotLoaded()
        if (snapshot.isEmpty()) return IntRange.EMPTY
        return snapshot.min()..snapshot.max()
    }

    // ---------------------------------------------------------------- 内部实现

    /** 当前视频的总分段数（1 起）。 */
    private fun segmentCount(): Int = DanmakuApi.segmentCountFor(maxDurationSec)

    /** 位置 → 该保证的窗口区间。 */
    private fun windowAt(positionMs: Long): IntRange =
        DanmakuWindow.rangeFor(positionMs, radius, segmentCount(), segmentSeconds)

    private fun snapshotLoaded(): Set<Int> =
        synchronized(loadedSegments) { TreeSet(loadedSegments) }

    private fun ensureCache(): DanmakuDiskCache? {
        cache?.let { return it }
        val context = appContext ?: return null
        val created = DanmakuDiskCache.forContext(context)
        cache = created
        return created
    }

    /**
     * 后台补窗口：串行拉缺段 → 追加给 DFM。**单个 worker**，异常全部吞掉。
     *
     * @param gen 启动时记下的代号；与当前 [generation] 不一致说明这期间又 attach/prepare/release 过，
     *   本轮立刻收手，绝不能把上一条视频的分段塞进新的一代。
     */
    private fun fetchWindowLoop(gen: Int) {
        try {
            var rounds = 0
            while (!released && gen == generation && rounds++ < MAX_FETCH_ROUNDS) {
                val manager = managerRef?.get() ?: return
                val missing = DanmakuWindow.missing(snapshotLoaded(), windowAt(lastPositionMs))
                if (missing.isEmpty()) {
                    retryAfterMs = 0L
                    return
                }
                val replies = ArrayList<DmSegMobileReply>(missing.size)
                var failed = 0
                for (index in missing) {
                    if (released || gen != generation) return
                    val reply = fetchOne(index)
                    if (reply == null) {
                        failed++
                        continue
                    }
                    synchronized(loadedSegments) { loadedSegments.add(index) }
                    if (reply.elems.isNotEmpty()) replies.add(reply)
                }
                if (replies.isNotEmpty()) {
                    retryAfterMs = 0L
                    // 追加必须回主线程：DanmakuView.addDanmaku 会直接改 DFM 的 handler 状态，
                    // 且 DanmakuManager 的 pending 队列按「主线程 / 渲染线程」假设写的。
                    CenterThreadPool.runOnUiThread {
                        if (!released && gen == generation) manager.appendSegments(replies)
                    }
                    Logu.d(
                        "弹幕窗口",
                        "补齐窗口：segments=$missing 非空=${replies.size}/${missing.size} 条数=" +
                            replies.sumOf { it.elems.size }
                    )
                }
                if (failed > 0 && replies.isEmpty()) {
                    // 一段都没成功：留 15 秒冷却再试，避免进度回调每次刷都打一轮请求。
                    retryAfterMs = System.currentTimeMillis() + RETRY_DELAY_MS
                    Logu.w("弹幕窗口", "补窗口失败（$failed/${missing.size} 段），${RETRY_DELAY_MS}ms 后重试")
                    return
                }
            }
        } catch (e: Exception) {
            Logu.e("弹幕窗口", "补窗口异常：${e.message}")
        } finally {
            // 只有当前这一代才允许清标志：切视频时 attach 已经把 fetching 归零并起了新一代的
            // worker，旧 worker 收尾时再清一次会把新一代的「正在补」状态抹掉，导致重复起 worker。
            if (gen == generation) fetching = false
        }
    }

    /**
     * 拉一段：缓存优先，未命中走网络（内部已重试一次）并回写缓存。
     *
     * @return 解析好的分段（**可能为空段**，空段也要算「已加载」，否则会反复请求）；
     *   失败返回 `null`
     */
    private fun fetchOne(segmentIndex: Int): DmSegMobileReply? {
        if (released) return null
        val diskCache = ensureCache()
        val cachedBytes = diskCache?.read(cid, segmentIndex)
        if (cachedBytes != null) {
            val parsed = runCatching { DanmakuApi.parseDanmakuSegment(cachedBytes) }.getOrNull()
            if (parsed != null) return parsed
            // 缓存损坏（例如旧版本留下的半截文件）：删掉，退回网络。
            diskCache.remove(cid, segmentIndex)
        }
        val bytes = DanmakuApi.getVideoDanmakuSegmentBytesWithRetry(aid, cid, segmentIndex)
            ?: return null
        diskCache?.write(cid, segmentIndex, bytes)
        return runCatching { DanmakuApi.parseDanmakuSegment(bytes) }.getOrNull()
    }
}

/**
 * 窗口区间的纯逻辑（不碰 Android / 网络，单测见 `DanmakuWindowTest`）。
 *
 * 单独抽出来的原因：窗口算错的表现是**静默的**——
 * - 少一段 ⇒ 该 6 分钟整段没有弹幕，用户只会说「弹幕突然没了」；
 * - 多一段 ⇒ 白拉一次带 WBI 签名的请求，手表上很贵；
 * - 段号越界（0 或超过总段数）⇒ 服务端返回空包，同样是白请求。
 */
object DanmakuWindow {

    /** 分段时长（秒）。与 `DanmakuApi.SEGMENT_SECONDS` 一致；这里写成字面量是为了保持纯逻辑不依赖 api 层。 */
    const val SEGMENT_SECONDS = 360

    private const val MS_PER_SECOND = 1000L

    /**
     * 位置（毫秒）→ 分段号（1-based）。负数/未就绪一律算第 1 段。
     *
     * 边界：`359999ms` 仍在第 1 段，`360000ms` 已是第 2 段 —— 与 `segment_index` 的语义
     * 「第 N 段覆盖 [(N-1)*360s, N*360s)」一致。
     */
    @JvmStatic
    fun segmentIndexAt(positionMs: Long, segmentSeconds: Int = SEGMENT_SECONDS): Int {
        val durationSeconds = if (segmentSeconds <= 0) SEGMENT_SECONDS else segmentSeconds
        val position = if (positionMs < 0L) 0L else positionMs
        return (position / (durationSeconds * MS_PER_SECOND)).toInt() + 1
    }

    /**
     * 位置 → 需要保证的窗口区间 `[from, to]`（1-based，含两端）。
     *
     * 两端都会按 `[1, segmentCount]` 裁剪：开头的前段不足一个窗口时只取后面，
     * 结尾同样不会去请求不存在的段。
     *
     * @param radius 前后各几段（0 = 只当前段）
     * @param segmentCount 总段数（<=0 按 1 处理）
     */
    @JvmStatic
    fun rangeFor(
        positionMs: Long,
        radius: Int,
        segmentCount: Int,
        segmentSeconds: Int = SEGMENT_SECONDS
    ): IntRange {
        val total = if (segmentCount <= 0) 1 else segmentCount
        val center = segmentIndexAt(positionMs, segmentSeconds).coerceIn(1, total)
        val span = if (radius < 0) 0 else radius
        val from = (center - span).coerceAtLeast(1)
        val to = (center + span).coerceAtMost(total)
        return from..to
    }

    /** 窗口里还没加载的分段号（升序）。 */
    @JvmStatic
    fun missing(loaded: Set<Int>, wanted: IntRange): List<Int> =
        wanted.filter { it !in loaded }
}
