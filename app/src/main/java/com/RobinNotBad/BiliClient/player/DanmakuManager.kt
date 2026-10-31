package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.util.UiText

import android.content.Context
import android.graphics.Color
import com.RobinNotBad.BiliClient.model.DmSegMobileReply
import com.RobinNotBad.BiliClient.util.Logu
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
    val allowOverlap: Boolean = true
)

class DanmakuManager(
    private val danmakuView: IDanmakuView
) {
    private var danmakuContext: DanmakuContext? = null
    private var danmakuParser: BaseDanmakuParser? = null

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
                maxLines = SharedPreferencesUtil.getInt("player_danmaku_maxline", 15),
                speedFactor = SharedPreferencesUtil.getFloat("player_danmaku_speed", 1.0f),
                textSizeScale = SharedPreferencesUtil.getFloat("player_danmaku_size", 0.7f),
                transparency = SharedPreferencesUtil.getFloat("player_danmaku_transparency", 0.5f),
                mergeDuplicate = SharedPreferencesUtil.getBoolean("player_danmaku_mergeduplicate", false),
                allowOverlap = SharedPreferencesUtil.getBoolean("player_danmaku_allowoverlap", true)
            )
        }
    }

    fun loadFromXmlFile(xmlFilePath: String) {
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
        danmakuParser = parser
        val st = _state.value

        val maxLinesPair = hashMapOf(BaseDanmaku.TYPE_SCROLL_RL to st.maxLines)
        val overlap = hashMapOf(
            BaseDanmaku.TYPE_SCROLL_LR to st.allowOverlap,
            BaseDanmaku.TYPE_FIX_BOTTOM to st.allowOverlap
        )

        danmakuContext = DanmakuContext.create().apply {
            setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 1f)
            setDuplicateMergingEnabled(st.mergeDuplicate)
            setScrollSpeedFactor(st.speedFactor)
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
        danmakuView.prepare(parser, danmakuContext)
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
        val danmaku = ctx.mDanmakuFactory.createDanmaku(type, ctx) ?: return
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
        try { danmakuView.release() } catch (_: Exception) {}
        // 复位 isPrepared：show/hide/pause/resume/seekTo 都有 `if (!isPrepared) return` 守卫，
        // 释放后不复位会让复用同一 DanmakuManager 的路径以为弹幕还挂在已释放的 view 上
        _state.update { it.copy(isPrepared = false) }
    }
}