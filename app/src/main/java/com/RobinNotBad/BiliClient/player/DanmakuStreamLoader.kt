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
 *
 * **`loadedSegments` 之外还有一道时序开关 [windowReady]**：[prepare] 是同步拉段（网络，几百
 * 毫秒到几秒），而播放页的进度回调从 250ms 起就在调 [ensureWindow]。若这段期间放行
 * `ensureWindow`，它会把「正在拉的首窗口」当成缺段再拉一遍：这一批走 `appendSegments` 进待灌
 * 队列，`prepared()` 时与 parser 里的首窗口一起灌进 DFM —— 同一条弹幕被画两次。同理，
 * [prepare] 失败（调用方回退整段接口 / 旧版 XML）时也必须保持关闭：调用方已经用别的路径灌了
 * 全量，之后再走窗口追加只会把同一批弹幕再叠一遍。
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

        /**
         * 失败后的冷却时间。**快速失败**，不再反复重试（反馈 #143）。
         *
         * 旧实现是「单次最多 4 轮 + 每轮失败再等 15 秒」：网络差或撞风控时表现为
         * 「跳过去之后弹幕没了，十几分钟后才零星回来」，用户只能反复重进播放页。
         * 现在一遍扫不到就算这一轮失败，失败后冷却一会儿；而且冷却只对**同一个窗口**
         * 生效——跳转 / 倍速重建换了要拉的段就立刻重试（见 [ensureWindow]、[rebuildWindow]）。
         */
        const val RETRY_DELAY_MS = 20_000L

        /** 单次补窗口最多扫几遍缺段：1 = 扫一遍就停，不再自我重试。 */
        private const val MAX_FETCH_ROUNDS = 1
    }

    private var appContext: Context? = null
    private var aid: Long = 0L
    private var cid: Long = 0L
    private var maxDurationSec: Int = 0

    /** 懒创建：真正的缓存实例在第一次拉段时才建（`context.cacheDir` 在这里可能还没就绪）。 */
    private var cache: DanmakuDiskCache? = null

    /** 用弱引用：加载器生命周期可能比 DanmakuManager 长（切 P 会重建 manager），不该把旧的钉死。 */
    private var managerRef: WeakReference<DanmakuManager>? = null

    /**
     * 首窗口是否已经交付给 DFM —— 也就是「现在允许 [ensureWindow] 补拉后续分段」的**唯一开关**。
     *
     * 不要拿 [managerRef] 是否为 null 兼任这个语义：它只是 worker 取 manager 的引用，表达不了
     * 「窗口加载现在该不该跑」。单独一个 `@Volatile` 标志让状态显式、可读：
     * - [prepare] 要同步拉首窗口（网络），这期间任何 `ensureWindow` 都必须返回，否则同一批分段
     *   会被 parser 与 [DanmakuManager.appendSegments] 各灌一份 —— 同一条弹幕画两次（重影）；
     * - [prepare] 失败后状态必须是明确的「窗口加载关闭」：调用方已经改用整段接口 / 旧版 XML
     *   灌数据，之后再追加窗口只会重复；下一次 [attach] / [prepare] 会重新打开；
     * - 同一实例第二次 `prepare` 失败时，旧的首窗口还挂在 parser 里，`isPrepared` 也可能仍为
     *   true（只有 [DanmakuManager.release] 会复位），`windowReady == false` 能挡住
     *   [ensureWindow]，不会把新视频的分段追加到旧 parser 上（串台）。
     */
    @Volatile
    private var windowReady = false

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
     * 上一次失败对应的窗口区间（`null` = 没有失败记录）。
     *
     * 冷却只对**同一个窗口**生效：跳到别的位置意味着要拉的是另外几段，必须立刻重试，
     * 否则用户看到的就是「跳过来之后一直没有弹幕」（反馈 #143）。
     */
    @Volatile
    private var failedRange: IntRange? = null

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
        failedRange = null
        managerRef = null
        windowReady = false
        cache = null
        synchronized(loadedSegments) { loadedSegments.clear() }
    }

    /**
     * 播放器拿到真实时长后回填（G03，26.11.13）。
     *
     * ## 为什么必须有这个入口
     *
     * [attach] 那一刻播放器多半还没 prepare，`ijkPlayer.duration` 是 0，播放页只能退回一个
     * 写死的估计值（3600 秒，见 `PlayerActivity.downdanmuNew`）。而总分段数
     * [segmentCount] 是用 `ceil(时长 / 360)` 算的——估计值对 1 小时以上的视频**必然偏小**，
     * 于是后半段的 `segment_index` 永远不在任何窗口里，用户看到的就是「后半段一条弹幕都没有」。
     * 时长一旦得知就回填，窗口随之扩大到覆盖全片。
     *
     * ## 语义
     *
     * - 只接受正整数，`<= 0` 视为未知、直接忽略（保留原值，不要用未知去覆盖已知）；
     * - 相同值不重复处理；
     * - **不清空 [loadedSegments]**：已拉到的分段仍然有效，新时长只是让 `segmentCount` 变大，
     *   下一次 [ensureWindow] 的 `missing()` 自然会把新出现的分段补上；
     * - 只在**变大**时打日志，变小（少数接口给的是片段时长）也接受但不重拉已加载段。
     *
     * 线程：与 [attach] 一样用 `@Synchronized`；调用方可以是主线程（onPrepared 回调）。
     */
    @Synchronized
    fun updateDuration(maxDurationSec: Int) {
        if (maxDurationSec <= 0) return
        val old = this.maxDurationSec
        if (maxDurationSec == old) return
        this.maxDurationSec = maxDurationSec
        if (maxDurationSec > old) {
            Logu.d(
                "弹幕窗口",
                "时长回填：${old}s → ${maxDurationSec}s，总分段数 " +
                    "${DanmakuApi.segmentCountFor(old)} → ${DanmakuApi.segmentCountFor(maxDurationSec)}"
            )
            // 时长变长意味着「当前位置之后的段数」变了，之前记的失败冷却区间可能已经无效，
            // 清掉让下一轮 ensureWindow 立刻重新尝试，不必等 RETRY_DELAY_MS。
            retryAfterMs = 0L
            failedRange = null
        } else {
            Logu.d("弹幕窗口", "时长回填（变短）：${old}s → ${maxDurationSec}s")
        }
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
     *
     * **本方法全程把 [windowReady] 压住**（见它的注释）：`prepare` 期间 [ensureWindow] 不会把
     * 同一批分段再拉一遍并追加成重影；只有首窗口真正交给 DFM 之后才打开。返回 `false` 时保持
     * 关闭——调用方会用整段接口 / 旧版 XML 灌全量，之后追加窗口只会重复；下一次
     * [attach] / [prepare] 会重新打开。状态是显式的，不依赖 [managerRef] 是否为空。
     */
    fun prepare(manager: DanmakuManager): Boolean {
        if (appContext == null) {
            Logu.e("弹幕窗口", "prepare 前没有 attach()，跳过窗口加载")
            return false
        }
        // 先关窗口加载：首窗口交付前不允许 [ensureWindow] 追加（见 windowReady）。
        windowReady = false
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
        // 首窗口已经进 parser，现在才允许 ensureWindow 补拉后续分段（见 windowReady）。
        windowReady = true
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
        // 窗口加载的显式开关：首窗口交付前、prepare 失败后都是 false（见 windowReady）。
        if (!windowReady) return
        if (managerRef?.get() == null) return
        lastPositionMs = positionMs
        val wanted = windowAt(positionMs)
        val missing = DanmakuWindow.missing(snapshotLoaded(), wanted)
        if (missing.isEmpty()) return
        if (fetching) return
        // 冷却只压「同一个窗口」：位置变了就是要拉另外几段，必须立刻重试（反馈 #143）。
        if (System.currentTimeMillis() < retryAfterMs && failedRange == wanted) return
        fetching = true
        val gen = generation
        CenterThreadPool.run { fetchWindowLoop(gen) }
    }

    /**
     * 现在能不能按新时间窗重建（供调用方在**清屏之前**判断：清完没人补就等于把弹幕清没了）。
     */
    fun canRebuildWindow(): Boolean =
        !released && windowReady && managerRef?.get() != null

    /**
     * 按**新时间窗重建**弹幕时间轴（反馈 #143）。
     *
     * 为什么不能只补缺段：DFM 的绘制子集与缓存窗口都还停在旧位置
     * （`DrawTask.drawDanmakus` 的 `mLastBeginMills/mLastEndMills`、`CacheManagingDrawTask` 的
     * `mCacheTimer`），远跳之后补进来的新段进不了当前窗口，用户看到的就是「跳过去弹幕明显变少」。
     *
     * 分工：**视图侧的清空由调用方做**（`clearDanmakusOnScreen()` + `removeAllDanmakus(true)`，
     * 见 `PlayerActivity.rebuildDanmakuTimeline`），这里只负责窗口记账——作废在飞的取数、
     * 清掉「已加载」与冷却、按新位置重开一次补窗口（命中磁盘缓存的段是同步读，不额外发请求）。
     *
     * @return true = 已经接管这次位置同步，调用方不必再调 [ensureWindow]
     */
    fun rebuildWindow(positionMs: Long): Boolean {
        if (released) return false
        if (!windowReady) return false
        if (managerRef?.get() == null) return false
        // 在飞的 worker 属于旧时间窗，作废它：它 append 之前会核对 generation，见 fetchWindowLoop。
        generation++
        val gen = generation
        fetching = false
        retryAfterMs = 0L
        failedRange = null
        lastPositionMs = positionMs
        synchronized(loadedSegments) { loadedSegments.clear() }
        val wanted = windowAt(positionMs)
        fetching = true
        CenterThreadPool.run { fetchWindowLoop(gen) }
        Logu.d("弹幕窗口", "按新时间窗重建：position=${positionMs}ms window=$wanted")
        return true
    }

    /** 释放：停止后续补窗口（已经在跑的 worker 会在下一段前自行退出）。 */
    @Synchronized
    fun release() {
        generation++
        released = true
        managerRef = null
        windowReady = false
        fetching = false
        retryAfterMs = 0L
        failedRange = null
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
                    failedRange = null
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
                    failedRange = null
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
                    // 一段都没成功：快速失败收手，只压同一窗口的冷却（见 ensureWindow）。
                    // 不再「等 15 秒再自己重试一轮」——旧实现在网络差时会让弹幕消失十几分钟。
                    retryAfterMs = System.currentTimeMillis() + RETRY_DELAY_MS
                    failedRange = windowAt(lastPositionMs)
                    Logu.w("弹幕窗口", "补窗口失败（$failed/${missing.size} 段），本窗口 ${RETRY_DELAY_MS}ms 内不再重试")
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

    /**
     * seek 之后该不该按新时间窗**重建**时间轴（而不是只增量补窗口）。
     *
     * 判据是「有没有跨过分段边界」：
     * - 同一段内的小幅拖动（双击快进 10 秒、拖一点点）旧窗口的段落仍然有效，
     *   增量补窗口就够，重建反而会清屏闪一下；
     * - 一旦跨段（片头 → 片尾、拖到十几分钟后），旧窗口的段落基本用不上，
     *   而 DFM 的绘制子集/缓存窗口还钉在旧位置，只补段会表现为「弹幕明显变少」（反馈 #143）。
     *
     * @param previousMs 跳转前的位置（负值/未知按 0 处理）
     */
    @JvmStatic
    fun needsTimelineRebuild(
        previousMs: Long,
        targetMs: Long,
        segmentSeconds: Int = SEGMENT_SECONDS
    ): Boolean = segmentIndexAt(previousMs, segmentSeconds) != segmentIndexAt(targetMs, segmentSeconds)
}
