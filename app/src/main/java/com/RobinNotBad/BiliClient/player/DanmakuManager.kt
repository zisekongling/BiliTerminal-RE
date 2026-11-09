package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.util.UiText

import android.content.Context
import android.graphics.Color
import com.RobinNotBad.BiliClient.model.DmSegMobileReply
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.controller.IDanmakuView
import master.flame.danmaku.danmaku.loader.ILoader
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.model.android.Danmakus
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import master.flame.danmaku.danmaku.parser.android.BiliDanmukuParser
import master.flame.danmaku.danmaku.parser.android.BiliProtobufDanmakuParser
import master.flame.danmaku.danmaku.util.DanmakuUtils
import org.json.JSONObject
import java.io.File
import java.io.InputStream

data class DanmakuState(
    val isVisible: Boolean = true,
    val isPrepared: Boolean = false,
    val maxLines: Int = 15,
    val speedFactor: Float = 1.0f,
    val textSizeScale: Float = 0.7f,
    val transparency: Float = 0.5f,
    val mergeDuplicate: Boolean = false,
    val allowOverlap: Boolean = true,
    /** 「强制为滚动弹幕」：开启后顶部/底部固定弹幕按滚动渲染（见 [DanmakuStylePolicy]）。 */
    val forceScroll: Boolean = false,
    /** 直播滚动弹幕停留时长倍率（与 [speedFactor] 独立，见 [LiveDanmakuFactorPolicy]）。 */
    val liveDurationFactor: Float = LiveDanmakuFactorPolicy.DEFAULT
)

class DanmakuManager(
    private val danmakuView: IDanmakuView,
    /** 直播模式：滚动弹幕用 [LiveDanmakuFactorPolicy] 的独立倍率，而不是录播的弹幕速度。 */
    private val isLiveMode: Boolean = false
) {
    private var danmakuContext: DanmakuContext? = null
    private var danmakuParser: BaseDanmakuParser? = null

    /**
     * 弹幕数量保护的状态机（可空）。注入后，超出阈值的弹幕源在 [loadFromXmlFile] /
     * [loadFromXmlInput] / [loadFromProtobufSegments] 入口直接跳过——计数、提示与「手动强制加载」
     * 都在播放页（见 `player/DanmakuLoadPolicy`），这里只负责最后一道「到底要不要真加载」。
     */
    private var loadPolicy: DanmakuLoadPolicy? = null

    private var mediaPosition: (() -> Long)? = null
    private var mediaPlaying: (() -> Boolean)? = null

    /**
     * 最近一次请求的倍速。
     *
     * 存下来是因为 [IDanmakuView.setSpeed] 只在 DFM 已经 prepared 时才吃进去，
     * 而播放器 `onPrepared` 往往早于 DFM 的 `prepared()`（后者异步）——
     * 那一次调用会被静默丢弃。`prepared()` 回调里会用它补设一次。
     */
    private var pendingSpeed: Float? = null

    /**
     * 窗口加载（批次 2）追加分段时的同步锁与待灌队列。
     *
     * 追加路径有两个线程：`DanmakuStreamLoader` 在后台拉完段后 `runOnUiThread` 调
     * [appendSegments]；而 DFM 的 `prepared()` 回调跑在**渲染线程**上补灌。
     * 另外 [appendSegments] 可能在 `prepare()` 完成之前就到了 —— `DrawHandler.addDanmaku`
     * 在 `drawTask == null` 时会**静默丢弃**，直接调就永久丢段，所以先入队、`prepared()` 里再灌。
     */
    private val appendLock = Any()
    private val pendingSegments = ArrayList<DmSegMobileReply>()

    /**
     * 批次 3「本人弹幕边框」的判定快照：是否开启、本人 mid、边框色。
     *
     * 为什么是快照而不是每次读 SharedPreferences：
     * - 判定发生在 DFM 的**渲染线程**（`prepared()` 回调里遍历全部弹幕）与逐条追加路径上，
     *   那里绝不能做 SharedPreferences / 主题缓存的 IO；
     * - 设置项本来也只在「下一次加载弹幕」时生效（换视频 / 切 P / 重进播放页会重新 prepare），
     *   在 [configureAndPrepare] 里刷新一次正是这个粒度。
     */
    private var selfBorderEnabled = false
    private var selfBorderOwnMid = 0L
    private var selfBorderColor = 0

    private val _state = MutableStateFlow(DanmakuState())
    val state: StateFlow<DanmakuState> = _state.asStateFlow()

    /**
     * 绑定媒体时钟：弹幕时间轴从此跟随播放器位置，而不再跟随墙钟。
     *
     * 不绑定的后果见 [MediaClockSync] / [DrawHandler.MediaClock] 的注释：视频实际播放速率不是
     * 1.0x 时（弱机解码跟不上、倍速、缓冲），弹幕与画面会按百分比持续拉开，只能靠周期性
     * `seekTo` 硬拉回，而 `seekTo` 会清空屏上弹幕 —— 表现就是「弹幕每隔十来秒闪一下没掉」。
     *
     * [position] 与 [isPlaying] **都在 DFM 渲染线程上被每帧调用**，实现方只能返回主线程采样好的
     * 内存值；位置不可用（未就绪/已销毁）时返回**负数**，DFM 会自动退回墙钟逻辑。
     *
     * 直播没有有意义的播放位置，不要绑。
     */
    fun bindMediaClock(position: () -> Long, isPlaying: () -> Boolean) {
        mediaPosition = position
        mediaPlaying = isPlaying
        applyMediaClock()
    }

    private fun applyMediaClock() {
        val position = mediaPosition ?: return
        val playing = mediaPlaying ?: return
        danmakuView.setMediaClock(object : DrawHandler.MediaClock {
            override fun positionMs(): Long = position()

            override fun isPlaying(): Boolean = playing()
        })
    }

    fun init() {
        val prefs = SharedPreferencesUtil.getSharedPreferences()
        _state.update {
            DanmakuState(
                // 弹幕行数完全听用户设置（26.11.08 撤掉「按设备档位封顶」）：
                // 旧实现对低端机把用户设的 15 行静默压到 6 行，设置页却仍显示用户填的值，
                // 且没有任何提示——与 26.11.07 那个「低端机被静默切软解」是同一类 bug。
                // 用户拍板：这类按性能偷偷改用户选择的行为一律去掉，弱机掉帧由用户自己权衡。
                maxLines = SharedPreferencesUtil.getInt(SettingsKeys.PLAYER_DANMAKU_MAXLINE, 15),
                speedFactor = SharedPreferencesUtil.getFloat("player_danmaku_speed", 1.0f),
                textSizeScale = SharedPreferencesUtil.getFloat("player_danmaku_size", 0.7f),
                transparency = SharedPreferencesUtil.getFloat("player_danmaku_transparency", 0.5f),
                mergeDuplicate = SharedPreferencesUtil.getBoolean("player_danmaku_mergeduplicate", false),
                allowOverlap = SharedPreferencesUtil.getBoolean("player_danmaku_allowoverlap", true),
                // E2：设置项一直存在（SettingTerminalPlayerActivity「强制为滚动弹幕」），这里接上消费方。
                forceScroll = SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_DANMAKU_FORCE_R2L, false),
                // D5：直播倍率存档是 list_choose 的字符串，解析与钳制统一走策略类。
                liveDurationFactor = LiveDanmakuFactorPolicy.parseStored(
                    SharedPreferencesUtil.getString(
                        LiveDanmakuFactorPolicy.KEY,
                        LiveDanmakuFactorPolicy.DEFAULT.toString()
                    )
                )
            )
        }
    }

    /**
     * 注入弹幕数量保护状态机（可空 = 不保护）。播放页在绑定弹幕视图后注入同一个实例，
     * 这样「查数量 → 判定 → 手动强制」只有一份真相，管理器只做最后一道守卫。
     */
    fun setLoadPolicy(policy: DanmakuLoadPolicy?) {
        loadPolicy = policy
    }

    private fun shouldLoadByPolicy(): Boolean {
        val policy = loadPolicy ?: return true
        if (policy.shouldLoad()) return true
        Logu.d("弹幕", "数量保护：已抑制本次弹幕加载（count=" + policy.knownCount() + "）")
        return false
    }

    fun loadFromXmlFile(xmlFilePath: String) {
        if (!shouldLoadByPolicy()) return
        val file = File(xmlFilePath)
        if (!file.exists()) return

        try {
            // 必须把文件内容**整体读进内存**再交给 parser，不能直接交 FileInputStream。
            // 原因：DFM 的 XML 解析是懒执行的 —— 真正的解析发生在 DanmakuView 的渲染线程
            // （DrawTask.loadDanmakus → BiliDanmukuParser.parse → xmlReader.parse(source.data())），
            // 而 configureAndPrepare() 返回时 parser 只是**存了流引用**，一个字节都还没读。
            // 26.10.02 的审计把这里改成了 `file.inputStream().use { ... }`，流在
            // configureAndPrepare() 返回的瞬间就被关掉，懒解析去读时拿到的是已关闭的流：
            // BiliDanmukuParser 捕获 IOException 后 return null → 弹幕列表为空且**没有任何提示**。
            // 表现就是「所有本地缓存视频都没有弹幕，点『更新弹幕』重下也没用」（文件本身是好的）。
            // ByteArrayInputStream 的 close() 是空操作，即使之后有人关流也仍可读，正好适合这种晚读。
            loadFromXmlInput(java.io.ByteArrayInputStream(file.readBytes()))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadFromXmlInput(inputStream: InputStream) {
        if (!shouldLoadByPolicy()) return
        try {
            val parser = createParser(inputStream)
            configureAndPrepare(parser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 用新版分段弹幕（protobuf）准备弹幕视图。
     *
     * 这是普通播放器原来内联在 `PlayerActivity.downdanmuNew()` 里的能力，合并弹幕栈后归到这里，
     * 两个播放器共用同一套配置与回调。
     */
    fun loadFromProtobufSegments(segments: List<DmSegMobileReply>) {
        if (!shouldLoadByPolicy()) return
        try {
            val parser = BiliProtobufDanmakuParser()
            parser.sharedPreferences = SharedPreferencesUtil.getSharedPreferences()
            parser.setDanmakuSegments(segments)
            configureAndPrepare(parser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 直播模式：没有历史弹幕数据，先准备一个空 parser，后续用 [addDanmaku] 实时喂入。
     * （原 `PlayerActivity.streamDanmaku(null)` 就是这个语义。）
     */
    fun prepareEmpty() {
        configureAndPrepare(object : BaseDanmakuParser() {
            override fun parse(): Danmakus = Danmakus()
        })
    }

    /**
     * 构造弹幕 parser。**注意：本方法只能在主线程调用，不可并发。**
     *
     * `DanmakuLoaderFactory.create(TAG_BILI)` 返回的是进程级单例 `BiliDanmakuLoader`，
     * 而 `dataSource` 是它的**实例字段**（BiliDanmakuLoader.java:29,44-51），
     * 这里 `load()` 写字段、`loader.dataSource` 读字段是一段 check-then-act。
     * 两个 holder 同时加载弹幕时会交错，导致两个 parser 拿到同一个数据源 → 弹幕串台/解析为空。
     *
     * 另注：本方法**不做 XML 解析**——`AndroidFileSource(InputStream)` 与
     * `BaseDanmakuParser.load()` 都只是存引用，真解析在 `getDanmakus()` → `parse()` 里懒执行，
     * 跑在 `DanmakuView` 自己的渲染线程（调用点只有 `DrawTask.java:283`）。
     * 所以把它挪到后台线程并不会减少主线程耗时，只会引入上面那个竞态。
     */
    private fun createParser(inputStream: InputStream): BaseDanmakuParser {
        val loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI)
        loader.load(inputStream)
        val parser = BiliDanmukuParser()
        parser.sharedPreferences = SharedPreferencesUtil.getSharedPreferences()
        val dataSource = loader.dataSource
        parser.load(dataSource)
        return parser
    }

    private fun configureAndPrepare(parser: BaseDanmakuParser) {
        // 新一次 prepare 意味着一批全新的弹幕源（换视频 / 切 P / 回退 XML）：上一轮窗口加载
        // 还没灌进去的分段必须丢掉，否则会串到新视频的时间轴上（弹幕「提前出现」或重复）。
        synchronized(appendLock) { pendingSegments.clear() }
        // 新一轮 prepare 开始：旧 parser 的数据即将被替换，在 `prepared()` 回来之前必须把
        // `isPrepared` 复位。否则同一实例第二次加载失败时，复用该字段的 `appendSegments`
        // 会把新分段直接灌进还挂着旧 parser 的 view（串台）；`release()` 之外没有别的地方复位。
        _state.update { it.copy(isPrepared = false) }

        // 批次 3：刷新「本人弹幕边框」的判定快照（开关 / 本人 mid / 主题主色）。
        // 必须在这里（调用方的主线程）算好，`prepared()` 跑在渲染线程上只能读字段。
        refreshSelfBorderSpec()

        val st = _state.value
        // E2：强制为滚动弹幕开启时，解析结果里的顶部/底部弹幕要换成滚动弹幕
        // （DFM 的类型由具体子类决定，必须换实例，见 [ForceScrollParser]）。
        val activeParser: BaseDanmakuParser = if (st.forceScroll) ForceScrollParser(parser) else parser
        danmakuParser = activeParser

        val maxLinesPair = hashMapOf(BaseDanmaku.TYPE_SCROLL_RL to st.maxLines)
        val overlap = hashMapOf(
            BaseDanmaku.TYPE_SCROLL_LR to st.allowOverlap,
            BaseDanmaku.TYPE_FIX_BOTTOM to st.allowOverlap
        )

        danmakuContext = DanmakuContext.create().apply {
            setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 1f)
            setDuplicateMergingEnabled(st.mergeDuplicate)
            // D5：直播用独立倍率（默认 2.0），录播沿用「弹幕速度」；
            // DFM 里 factor 越大停留越久，等于这里的「停留时长倍率」。
            setScrollSpeedFactor(
                LiveDanmakuFactorPolicy.scrollSpeedFactor(isLiveMode, st.speedFactor, st.liveDurationFactor)
            )
            setScaleTextSize(st.textSizeScale)
            setMaximumLines(maxLinesPair)
            setDanmakuTransparency(st.transparency)
            preventOverlapping(overlap)
        }

        danmakuView.setCallback(object : DrawHandler.Callback {
            override fun prepared() {
                // 必须兜异常：本回调运行在 DanmakuFlameMaster 的渲染线程上，
                // 一旦异常逸出会打断该线程，表现就是"弹幕一条都不显示"且没有任何上层报错。
                try {
                    _state.update { it.copy(isPrepared = true) }
                    // 窗口加载（批次 2）：`prepare()` 之前/期间到达的后续分段此时补灌。
                    // 必须放在这里 —— `DrawHandler.addDanmaku` 在 drawTask 就绪前会静默丢弃，
                    // 那正是「弹幕只出前几分钟」这类问题的根因。
                    flushPendingSegments()
                    // 批次 3：给本人的弹幕加边框。必须放在 prepared()（首帧绘制之前）——
                    // DFM 的 `paintWidth/paintHeight` 是首帧 measure 时才定（带边框会多出
                    // 2*BORDER_WIDTH 的内边距），之后再设 borderColor 会出现「框比字小」。
                    markSelfDanmakuBorders()
                    // 补设可见性：`show()` / `hide()` 在 prepare 之前调用时只把意图写进 state，
                    // 真正作用到 DanmakuView 的这一步必须在这里补。漏掉的话，用户明明已经关掉弹幕，
                    // 新建的 DanmakuManager（短视频每条一建、长视频切 P 也重建）却按默认可见起播，
                    // 弹幕照样滚出来 —— 表现就是「点了关闭还是会出弹幕」（反馈 #55 / #78）。
                    if (!_state.value.isVisible) {
                        try {
                            danmakuView.hide()
                        } catch (e: Exception) {
                            Logu.e("弹幕", "prepared 补设隐藏失败: ${e.message}")
                        }
                    }
                    // 补设倍速：`DanmakuView.setSpeed` 有 `handler.isPrepared()` 守卫，
                    // 播放器的 onPrepared 往往早于 DFM 的 prepared（DFM 是异步 prepare），
                    // 那一次调用会被直接丢弃 → 弹幕时钟永远按 1.0x 走。
                    // 3x 播放时 MediaClockSync 的步进补偿就失效了（见 §7.51）。
                    pendingSpeed?.let { danmakuView.setSpeed(it) }
                    addDanmaku(UiText.of("弹幕准备完毕"), Color.WHITE)
                    // 数据就绪：只有「此刻真的在播」或「没绑媒体时钟（直播）」才启动渲染循环。
                    // 暂停中启动会让用户手动暂停的弹幕被重新拉起（见 configureAndPrepare 的注释）。
                    val shouldRender = runCatching { mediaPlaying?.invoke() != false }.getOrDefault(true)
                    if (shouldRender) {
                        start(runCatching { mediaPosition?.invoke() ?: -1L }.getOrDefault(-1L))
                    }
                } catch (e: Exception) {
                    Logu.e("弹幕", "prepared 回调异常: ${e.message}")
                    e.printStackTrace()
                }
            }

            override fun updateTimer(timer: DanmakuTimer) {
                // 这里**刻意什么都不做**：既不 `timer.update(pos)`，也不去读播放位置。
                //
                // 历史（26.09.10）：本回调曾经把"当前播放位置"喂给 DanmakuTimer，并且要求实现方在
                // "播放器未就绪/正在重建"时返回负数——因为 IjkMediaPlayer 的 native 层在
                // setDataSource / prepareAsync / release 期间不是线程安全的，而本回调运行在
                // DanmakuView 的渲染线程上，与主线程重建播放器的动作并发，窗口期的脏位置一旦灌进
                // timer，整批弹幕会被判定为"已过期"而一条都不显示（间歇性"弹幕没了"）。
                // 现在连"读位置"本身都搬去了主线程，这条守卫随之取消。
                //
                // 那正确的时间源怎么给？走 [bindMediaClock]：宿主在主线程采样 `video_now`，
                // 由 DFM 的 [DrawHandler.MediaClock] 每帧读取（只读内存值，不碰 native 锁），
                // 在 `DrawHandler.syncTimer` 里以「采样位置 + 墙钟外推 + 限幅蠕动」推进 timer。
                // **不要**再在这里 `timer.update(pos)`：`mTimeBase` 不随之改变，会被 syncTimer 立刻拉回。
                //
                // 另外：本回调运行在 DanmakuView 的渲染线程上，绝不能在这里调
                // ijkPlayer.currentPosition（取 native 锁，与主线程 seek/release 并发有卡死风险）。
            }

            override fun danmakuShown(danmaku: BaseDanmaku?) {}

            override fun drawingFinished() {}
        })

        danmakuView.enableDanmakuDrawingCache(true)
        danmakuView.prepare(activeParser, danmakuContext)
        applyMediaClock()
        // **不在这里 start**：prepare() 只准备数据，而它何时 ready 是异步的（后台解析）。
        // 启动渲染循环放到 `prepared()` 回调里按「此刻是否真的在播」判断 —— 否则暂停中
        // 加载完弹幕会被无条件拉起（用户手动暂停的弹幕被恢复、暂停画面上的弹幕开始跑）。
        // 暂停中不启动的页面，之后走各自的 resume 路径（`IDanmakuView.resume()` 在
        // mReady 后会自行启动 UPDATE 循环），不会漏。
    }

    /**
     * 往当前弹幕视图里即时插入一条弹幕。
     *
     * 参数与原 `PlayerActivity.addDanmaku(text, color, textSize, type, backgroundColor)` **完全一致**，
     * 这样直播弹幕链路（`PlayerDanmuClientListener` 直接调用 `playerActivity.addDanmaku(...)`）
     * 与"发送弹幕"链路可以原样迁移过来，行为不变。
     */
    fun addDanmaku(
        text: String,
        color: Int = Color.WHITE,
        textSize: Int = 25,
        type: Int = BaseDanmaku.TYPE_SCROLL_RL,
        backgroundColor: Int = 0
    ) {
        val ctx = danmakuContext ?: return
        // E2：实时插入的弹幕（直播聊天 / 用户自己发的）同样按「强制为滚动弹幕」换算类型，
        // 否则直播的「进入直播间」等底部固定弹幕仍会挡住画面。
        val effectiveType = DanmakuStylePolicy.effectiveType(_state.value.forceScroll, type)
        val danmaku = ctx.mDanmakuFactory.createDanmaku(effectiveType, ctx) ?: return
        danmaku.text = text
        danmaku.padding = 5
        danmaku.priority = 1
        danmaku.isLive = false
        danmaku.time = danmakuView.currentTime + 100
        danmaku.textSize = textSize * (ctx.displayer.density - 0.6f)
        danmaku.textColor = color
        danmaku.backgroundColor = backgroundColor
        danmaku.textShadowColor = Color.BLACK
        danmakuView.addDanmaku(danmaku)
    }

    /**
     * 窗口加载：把**后续分段**增量追加进当前弹幕视图（批次 2，供 [DanmakuStreamLoader] 调用）。
     *
     * 与 [loadFromProtobufSegments] 的区别：后者重建 parser（等价于「重新 prepare 一份弹幕源」），
     * 这里只往**已经准备好的**视图里补条目，不打断正在滚的弹幕、不重置时间轴。
     *
     * 线程与时机：
     * - 应由调用方 `runOnUiThread`（`DanmakuStreamLoader` 已经这么做）；
     * - DFM 还没 prepared 时**不会丢**：分段先入 [pendingSegments]，在 `prepared()` 里补灌；
     * - 同一次 prepare 周期内同一段只该喂一次（重复喂会重影），去重由调用方负责。
     *
     * 数量保护：仍走 [shouldLoadByPolicy]，与首次加载同一道闸。
     */
    fun appendSegments(segments: List<DmSegMobileReply>) {
        if (segments.isEmpty()) return
        if (!shouldLoadByPolicy()) return

        val ready = _state.value.isPrepared && danmakuContext != null
        if (!ready) {
            synchronized(appendLock) { pendingSegments.addAll(segments) }
            Logu.d("弹幕", "DFM 未就绪，${segments.size} 个分段先入待灌队列")
            return
        }
        for (segment in segments) {
            appendSegmentInternal(segment)
        }
    }

    /** 把 pending 队列灌进视图（`prepared()` 回调里调用，运行在 DFM 渲染线程）。 */
    private fun flushPendingSegments() {
        var pending: List<DmSegMobileReply>? = null
        synchronized(appendLock) {
            if (pendingSegments.isNotEmpty()) {
                pending = ArrayList(pendingSegments)
                pendingSegments.clear()
            }
        }
        val list = pending ?: return
        var ok = 0
        for (segment in list) {
            if (appendSegmentInternal(segment)) ok++
        }
        Logu.d("弹幕", "补灌 ${list.size} 个分段（成功 $ok）")
    }

    /**
     * 批次 3：刷新「本人弹幕边框」的判定快照。
     *
     * 只在 [configureAndPrepare] 里调用（调用方主线程）。开关默认开
     * （`SettingsKeys.PLAYER_DANMAKU_SELF_BORDER`）；未登录时 [selfBorderOwnMid] <= 0，
     * 即使开关开着也不会有任何弹幕命中。
     */
    private fun refreshSelfBorderSpec() {
        selfBorderEnabled = SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_DANMAKU_SELF_BORDER, true)
        selfBorderOwnMid = SharedPreferencesUtil.getLong(SettingsKeys.MID, 0L)
        // 主题主色可能带透明度；DFM 画的是描边矩形，半透明在视频画面上几乎看不见，
        // 所以统一置为不透明（见 DanmakuSelfStylePolicy.borderColorFor）。
        selfBorderColor = DanmakuSelfStylePolicy.borderColorFor(ColorScheme.PRIMARY)
    }

    /**
     * 批次 3：遍历解析结果，给本人的弹幕设置边框色（在 DFM 渲染线程的 `prepared()` 里调用）。
     *
     * - 时机：此时 `DrawTask` 已 `getDanmakus()` 拿到缓存集合，遍历不会触发重新解析；
     *   且首帧尚未绘制，`measure()` 会算上边框内边距（`AndroidDisplayer` 在
     *   `borderColor != 0` 时给宽高各加 `2 * BORDER_WIDTH`），边框不会压住文字。
     * - 复杂度：单次 O(n) 的字符串归一化比较；5000 条弹幕量级约 1~2ms，**一次性**，
     *   不进渲染循环、不产生额外对象分配（`normalize` 最多生成短字符串）。
     * - 覆盖范围：首窗口与「prepare 期间补灌的分段」都在解析结果里，见 [flushPendingSegments]。
     */
    private fun markSelfDanmakuBorders() {
        if (!selfBorderEnabled || selfBorderOwnMid <= 0L) return
        val items = danmakuParser?.danmakus ?: return
        val iterator = items.iterator()
        var marked = 0
        while (iterator.hasNext()) {
            val item = iterator.next() ?: continue
            if (DanmakuSelfStylePolicy.matchesOwnHash(selfBorderOwnMid, item.userHash)) {
                item.borderColor = selfBorderColor
                marked++
            }
        }
        if (marked > 0) Logu.d("弹幕", "本人弹幕边框：已标记 $marked 条")
    }

    /**
     * 单个分段 → 逐条 `addDanmaku`。
     *
     * 字段换算**必须与 `BiliProtobufDanmakuParser.parseDanmakuElem` 保持一致**（那条路径仍在用：
     * 首窗口就是交给 parser 解析的），否则「首窗口」与「后续窗口」的弹幕字号/颜色/高度会不一样：
     * - `mode 7/8`（高级/代码弹幕）跳过 —— 与解析器一致，DFM 也不支持；
     * - `DanmakuUtils.fillText` 负责换行符分段（`\n`），不能自己写 `item.text = content`；
     * - `textSize = fontsize * (density - 0.6f)`、`textColor = color | 0xFF000000`（不透明化）。
     */
    private fun appendSegmentInternal(segment: DmSegMobileReply): Boolean {
        val context = danmakuContext ?: return false
        val st = _state.value
        val elems = segment.elems ?: return false
        var added = 0
        for (elem in elems) {
            try {
                if (elem.mode == 7 || elem.mode == 8) continue
                val content = elem.content
                if (content.isNullOrEmpty()) continue
                val type = DanmakuStylePolicy.effectiveType(st.forceScroll, elem.mode)
                val item = context.mDanmakuFactory.createDanmaku(type, context) ?: continue
                item.time = elem.progress.toLong()
                DanmakuUtils.fillText(item, content)
                item.textSize = elem.fontsize * (context.displayer.density - 0.6f)
                item.textColor = elem.color or 0xFF000000.toInt()
                item.textShadowColor =
                    if ((elem.color or 0xFF000000.toInt()) <= Color.BLACK) Color.WHITE else Color.BLACK
                // 批次 3：窗口追加的分段同样要识别本人弹幕。首窗口走解析器 + `prepared()` 遍历，
                // 后续分段是应用层逐条建实例，两条路径都得覆盖，
                // 否则「视频后半段自己发的弹幕没有边框」。
                if (DanmakuSelfStylePolicy.shouldApplyBorder(
                        selfBorderOwnMid, elem.midHash, selfBorderEnabled
                    )
                ) {
                    item.borderColor = selfBorderColor
                }
                // 不在这里 setTimer / 设 flags：`DrawHandler.addDanmaku` 会自己补
                // `item.flags = mGlobalFlagValues` 与 `item.setTimer(timer)`，
                // 我们拿不到渲染线程的 timer（也没有稳定句柄），重复设置反而会打乱计时基准。
                danmakuView.addDanmaku(item)
                added++
            } catch (e: Exception) {
                Logu.e("弹幕", "追加弹幕失败: ${e.message}")
            }
        }
        return added > 0
    }

    fun show() {
        _state.update { it.copy(isVisible = true) }
        if (!_state.value.isPrepared) return
        try { danmakuView.show() } catch (_: Exception) {}
    }

    fun hide() {
        _state.update { it.copy(isVisible = false) }
        if (!_state.value.isPrepared) return
        try { danmakuView.hide() } catch (_: Exception) {}
    }

    fun pause() {
        if (!_state.value.isPrepared) return
        try { danmakuView.pause() } catch (_: Exception) {}
    }
    fun resume() {
        if (!_state.value.isPrepared) return
        try { danmakuView.resume() } catch (_: Exception) {}
    }

    /**
     * 启动 DFM 的渲染循环，从 [positionMs] 起播（<0 表示从头）。
     *
     * **DFM 的 `prepare()` 只准备数据，不启动更新循环**：`DrawHandler` 的 UPDATE 只由
     * START / SEEK_POS / RESUME 三个分支拉起（`DrawHandler.java` 的 `handleMessage`），
     * 而 RESUME 分支要求 `mReady` 为真。所以「加载完弹幕但一条都不显示」的原因是没人调过
     * `start()` —— 旧实现靠周期性 `seekTo` 的副作用启动，那个副作用随弹幕时钟改造一起没了
     * （见 `docs/review/fix-progress.md` §3.56）。
     *
     * `start()` 在未 ready 时会每 100ms 自行重试（START 落穿到 RESUME 分支），所以不必等
     * `prepared()` 回调，[configureAndPrepare] 末尾直接调即可。
     *
     * @param positionMs 起始位置；续播 / 切页回来时传已知播放位置，别让时间轴从 0 重新走
     */
    fun start(positionMs: Long = -1L) {
        try {
            if (positionMs > 0) danmakuView.start(positionMs) else danmakuView.start()
        } catch (e: Exception) {
            Logu.e("弹幕", "启动渲染循环失败: ${e.message}")
        }
    }

    /**
     * 设置播放倍速。
     *
     * **不要直接调 `mDanmakuView.setSpeed(...)`**：DFM 未 prepared 时那次调用会被丢弃
     * （`DanmakuView.setSpeed` 的 `handler.isPrepared()` 守卫），而播放器 `onPrepared`
     * 通常在 DFM `prepared()` 之前。这里记下倍速，`prepared()` 回调会补设一次。
     */
    fun setSpeed(speed: Float) {
        pendingSpeed = speed
        try { danmakuView.setSpeed(speed) } catch (_: Exception) {}
    }

    fun seekTo(ms: Long) {
        if (!_state.value.isPrepared) return
        try { danmakuView.seekTo(ms) } catch (_: Exception) {}
    }

    fun release() {
        // 窗口加载（批次 2）：待灌队列里的分段属于这次播放，释放后不再有意义。
        // 不清的话，复用同一 DanmakuManager 的下一个视频会把上一部的尾段灌进新时间轴。
        synchronized(appendLock) { pendingSegments.clear() }
        try { danmakuView.release() } catch (_: Exception) {}
        // 复位 isPrepared：show/hide/pause/resume/seekTo 都有 `if (!isPrepared) return` 守卫，
        // 释放后不复位会让复用同一 DanmakuManager 的路径以为弹幕还挂在已释放的 view 上
        _state.update { it.copy(isPrepared = false) }
    }
}