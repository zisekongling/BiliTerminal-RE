package com.RobinNotBad.BiliClient.activity.player

import com.RobinNotBad.BiliClient.ui.appearance.GlassStyle
import com.RobinNotBad.BiliClient.BuildConfig
import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.AnimationDrawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.InteractionDebugActivity
import com.RobinNotBad.BiliClient.activity.video.info.VideoInfoActivity
import com.RobinNotBad.BiliClient.adapter.QualitySelectorAdapter
import com.RobinNotBad.BiliClient.adapter.ViewPointAdapter
import com.RobinNotBad.BiliClient.api.ConfInfoApi
import com.RobinNotBad.BiliClient.api.DanmakuApi
import com.RobinNotBad.BiliClient.api.HistoryApi
import com.RobinNotBad.BiliClient.api.InteractionVideoApi
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.api.VideoInfoApi
import com.RobinNotBad.BiliClient.event.SnackEvent
import com.RobinNotBad.BiliClient.model.HighEnergyData
import com.RobinNotBad.BiliClient.model.InteractionVideoData
import com.RobinNotBad.BiliClient.model.Collection
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.Subtitle
import com.RobinNotBad.BiliClient.model.SubtitleLink
import com.RobinNotBad.BiliClient.model.VideoInfo
import com.RobinNotBad.BiliClient.model.ViewPoint
import com.RobinNotBad.BiliClient.player.DanmakuManager
import com.RobinNotBad.BiliClient.player.ExoDashMediaPlayer
import com.RobinNotBad.BiliClient.player.IjkNativeLibs
import com.RobinNotBad.BiliClient.player.PlayerDefaults
import com.RobinNotBad.BiliClient.player.LocalAudioTrackPolicy
import com.RobinNotBad.BiliClient.player.PlayerSurfaceBinder
import com.RobinNotBad.BiliClient.player.UnifiedDashPlayer
import com.RobinNotBad.BiliClient.player.PlayerStreamPolicy
import com.RobinNotBad.BiliClient.player.QualityAccessPolicy
import com.RobinNotBad.BiliClient.player.PlayerVolumePolicy
import com.RobinNotBad.BiliClient.player.SkipCancelPolicy
import com.RobinNotBad.BiliClient.player.SkipOpEdPrefs
import com.RobinNotBad.BiliClient.player.ViewPointSkip
import com.RobinNotBad.BiliClient.player.SurfaceTarget
import com.RobinNotBad.BiliClient.service.PlaybackService
import com.RobinNotBad.BiliClient.ui.widget.BatteryView
import com.RobinNotBad.BiliClient.ui.widget.HighEnergyProgressBar
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.CookieDomainPolicy
import com.RobinNotBad.BiliClient.util.DisguiseGesture
import com.RobinNotBad.BiliClient.util.DisguisePrefs
import com.RobinNotBad.BiliClient.util.LocalVideoProgress
import com.RobinNotBad.BiliClient.util.AccountStateStore
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.RobinNotBad.BiliClient.util.VideoMetaManager
import com.RobinNotBad.BiliClient.util.view.EditorEnterKeyGuard
import com.google.android.material.snackbar.Snackbar
import master.flame.danmaku.controller.IDanmakuView
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okio.BufferedSink
import okio.buffer
import okio.sink
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import tv.danmaku.ijk.media.player.misc.ITrackInfo
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.regex.Pattern
import kotlin.math.abs

class PlayerActivity : Activity(), IMediaPlayer.OnPreparedListener {
    // @Volatile：PlaybackService 的定时线程会读它（见下方「后台播放服务（PlaybackService）状态桥」），
    // 不加的话主线程写的值不保证被服务线程看见。
    @Volatile
    private var destroyed = false

    private var playerCore: IMediaPlayer? = null
    private var mDanmakuView: IDanmakuView? = null
    // 弹幕配置/解析/回调统一交给 DanmakuManager（方案 B），Activity 只保留视图引用做显隐与布局
    private var danmakuManager: DanmakuManager? = null

    private var surfaceView: SurfaceView? = null
    private var textureView: TextureView? = null
    // Surface 就绪改为事件回调：取代原来「每 200ms 轮询 surface」的 surfaceTimer。
    // 原实现每次 setDisplay()（首播/切清晰度/切分页/切听视频模式）都要新建一个 Timer 线程，
    // 且 surface 未就绪时最长要等满一个轮询周期（200ms）才开始 prepareAsync，直接拖慢首帧。
    private var surfaceBinder: PlayerSurfaceBinder? = null

    private var subtitleLinks: Array<SubtitleLink>? = null
    private var subtitles: Array<Subtitle>? = null
    private var subtitle_curr_index: Int = 0
    private var subtitle_shown_index: Int = -2 // 当前已展示的字幕下标，-2 表示尚未展示过任何字幕，-1 表示当前不显示
    private var subtitle_count: Int = 0
    private var subtitle_delta: Float = 0f

    private lateinit var layout_control: RelativeLayout
    private lateinit var layout_top: RelativeLayout
    private lateinit var layout_video: RelativeLayout
    private lateinit var layout_card_bg: RelativeLayout
    private lateinit var layout_audio_only: RelativeLayout
    private lateinit var layout_speed: LinearLayout
    private lateinit var right_control: LinearLayout
    private lateinit var loading_info: LinearLayout
    private lateinit var bottom_buttons: RelativeLayout
    private lateinit var right_second: HorizontalScrollView
    private lateinit var card_subtitle: LinearLayout
    private lateinit var card_danmaku_send: LinearLayout
    private lateinit var card_page_selector: LinearLayout
    private lateinit var card_quality_selector: LinearLayout
    private lateinit var card_viewpoint_selector: LinearLayout

    private lateinit var img_loading: ImageView
    private var anim_loading: AnimationDrawable? = null
    private lateinit var btn_control: ImageButton
    private lateinit var btn_danmaku: ImageButton
    private lateinit var btn_loop: ImageButton
    private lateinit var btn_rotate: ImageButton
    private lateinit var btn_menu: ImageButton
    private lateinit var btn_subtitle: ImageButton
    private lateinit var btn_danmaku_send: ImageButton
    private lateinit var btn_audio_only: ImageButton
    private lateinit var btn_page_selector: ImageButton
    private lateinit var btn_auto_next: ImageButton
    private lateinit var btn_quality: ImageButton
    private lateinit var btn_viewpoint: ImageButton
    private lateinit var btn_debug: ImageButton
    private lateinit var lock_btn: ImageButton
    private lateinit var lock_overlay: View
    private lateinit var left_control: LinearLayout
    private lateinit var seekbar_progress: HighEnergyProgressBar
    private lateinit var seekbar_speed: SeekBar
    private lateinit var text_progress: TextView
    private lateinit var text_online: TextView
    private lateinit var text_volume: TextView
    private lateinit var loading_text0: TextView
    private lateinit var loading_text1: TextView
    private lateinit var text_speed: TextView
    private lateinit var text_newspeed: TextView

    /**
     * 「无极调音」弹层（`layout_volume`）：软件增益滑条 0~100%，长按音量 +/- 打开。
     *
     * 与 [text_volume]（系统媒体音量提示）是两套东西：短按音量键仍调系统音量，
     * 长按弹这个滑条调播放器自己的输出电平（见 [applyVolumeGain]）。
     */
    private lateinit var layout_volume: LinearLayout
    private lateinit var seekbar_volume: SeekBar
    private lateinit var text_volume_title: TextView
    private lateinit var text_newvolume: TextView

    /** 当前软件增益（百分比 0~100），唯一真相；换算/钳制见 [PlayerVolumePolicy]。 */
    private var volumeGainPercent = PlayerVolumePolicy.DEFAULT_PERCENT

    @JvmField var text_title: TextView? = null
    private lateinit var text_subtitle: TextView
    private lateinit var text_audio_title: TextView
    private lateinit var text_audio_subtitle: TextView

    private var progressRunnable: Runnable? = null
    private var speedTimer: Timer? = null
    private var loadingRunnable: Runnable? = null
    private var onlineTimer: Timer? = null
    private var mainHandler: Handler? = null
    private var resizePostRunnable: Runnable? = null // 改变视频尺寸后的位置延时修正，避免快速旋转时叠加
    private var video_url: String? = null
    private var danmaku_url: String = ""
    private var mediaSession: MediaSession? = null

    @Volatile // 被 PlaybackService 定时线程读（serviceIsPlaying）
    private var isPlaying: Boolean = false
    // 主线程写、弹幕渲染线程读（见 bindDanmakuView 里的位置回调），必须 volatile，
    // 否则弹幕线程可能长时间读到过期的 true，继续把重建窗口期的脏位置灌进 DanmakuTimer
    // 另：PlaybackService 的定时线程也会读它（serviceIsPlaying），同样需要可见性保证
    @Volatile
    private var isPrepared: Boolean = false
    private var hasDanmaku: Boolean = false
    private var isOnlineVideo: Boolean = false
    private var isLiveMode: Boolean = false
    private var isSeeking: Boolean = false
    private var isDanmakuVisible: Boolean = false
    private var menu_opened = false

    /** 播放页触摸锁定的当前状态（状态转移见 [PlayerLockPolicy]）。 */
    private var lockPhase = PlayerLockPolicy.Phase.UNLOCKED

    /** 锁定期间被拦下的 [finish]，解锁后补执行，避免系统主动的退出请求被永久吞掉。 */
    private var pendingFinish = false
    private var isAudioOnlyMode = false
    private var isLocalAudioFile = false
    private var audioTrackUrl: String? = null // DASH外部音频轨道（本地双文件路径或在线 DASH 音轨 URL）
    private var audioPlayer: android.media.MediaPlayer? = null // 外部音频播放器

    /**
     * 账号状态（登录 / 大会员），决定清晰度列表里哪些档位要上锁。
     *
     * `null` 表示还没查到，此时用本地缓存兜底（见 [com.RobinNotBad.BiliClient.util.AccountStateStore.cached]）。
     * 只用于界面展示与点击拦截：真正的发流权限在服务端。
     */
    private var accountState: AccountStateStore.State? = null

    /** 账号状态是否正在后台补查（避免每次弹面板都重复请求）。 */
    private var accountStateRefreshing = false

    /** 当前清晰度列表的 adapter，账号状态后到时要就地把锁态刷上去。 */
    private var qualityAdapter: QualitySelectorAdapter? = null

    /**
     * 外部音轨是否已经 `onPrepared`。
     *
     * 远程音轨走 `prepareAsync`，在它就绪前 `currentPosition` 固定是 0；此时
     * `progressChange` 的偏差校正会拿它跟视频位置比，误判成"漂移"并 `seekTo`。
     * 对还没 prepared 的 MediaPlayer 调 seekTo 会直接以 `INVALID_OPERATION`(-38)
     * 触发 `onError`，把刚切好的 1080P 音轨判死（实测就是这样被踢回 720P 的）。
     */
    private var audioTrackPrepared = false

    /**
     * 外部音轨的「稳定偏差基线」与是否已采样。
     *
     * 见 [progressChange] 里的说明：两路时钟基准天然差一截（模拟器上稳定 ~850ms），
     * 只纠正偏离基线的部分，避免固定偏差把音轨每几秒 seek 一次。
     */
    private var audioDriftBaseline = 0
    private var audioDriftSampled = false

    /**
     * 在线 DASH 音轨加载失败后只回退一次：回退本身就是 `applyQuality(720P)`，
     * 再次失败若还往回退就会无限重进 720P（播放器反复重建）。
     */
    private var dashAudioFallbackInFlight = false

    /**
     * 统一时钟内核失败后是否已经自动回退过 720P 合一 MP4。
     *
     * 回退本身就会重建播放器，不设这个标志会无限「失败 → 回退 → 失败」。
     */
    private var unifiedFallbackAttempted = false

    /** 统一时钟内核的回退流程是否正在进行中（防重入）。 */
    private var unifiedFallbackInFlight = false
    private var unifiedProbeBasePos = -1L
    private val unifiedHealthCheck = Runnable { checkUnifiedPlayerHealth() }

    // 取流接口给的 CDN 备份地址（首位就是当前主地址），播放器出错时按顺序换源重试。
    // 只在在线点播、且是 PlayerApi.jumpToPlayer 下发的 Intent 时才有；本地/直播为空表。
    private var videoBackupUrls: ArrayList<String> = ArrayList()
    private var audioBackupUrls: ArrayList<String> = ArrayList()
    private var videoBackupIndex = 0

    /**
     * 在线 DASH 音轨失败后的重试次数（每次切清晰度清零）。
     *
     * 实测（模拟器 Android 14）切清晰度时旧的合一 MP4 音轨刚被释放，MediaPlayer 立刻起播
     * 会拿到 `INVALID_OPERATION`(-38)；隔一会儿再起一次就能成功。所以先重试，别急着回退
     * 720P——否则用户刚点 1080P 就被自动踢回低清，观感就是"根本切不了"。
     */
    private var dashAudioRetryCount = 0

    /**
     * 外部音频轨道的「seek 冷却截止时刻」（`SystemClock.elapsedRealtime()`）。
     * `MediaPlayer.seekTo` 是异步的：seek 还没落地时 `currentPosition` 仍停在旧位置，
     * 此时若继续按 800ms 偏差校正就会对同一次拖动再 seek 一遍，听感是"拖完停两次"。
     * 由 [seekToPosition] 写入，[progressChange] 的音频校正读取。
     */
    private var audioResyncSuppressMs: Long = 0L

    /**
     * 是否正处在「长按三倍速」期间。
     *
     * 长按加速不改滑条档位，但它是**真的在变速**：倍速值统一由 [effectiveSpeedValue] 提供，
     * 各路（滑条、长按、重建后的 [onPrepared]）都走 [applySpeedFromSeekbar] 下发，
     * 避免"滑条显示 1x、实际还在 3x"这种隐性不一致。松手回到滑条档位。
     */
    private var longPressBoostActive = false

    companion object {
        /** 长按加速的倍速值（与滑条档位无关，松手即恢复） */
        private const val LONG_PRESS_BOOST_SPEED = 3.0F

        /** `onSaveInstanceState` 保存的播放位置（毫秒），用于系统重建本页后回到原位 */
        private const val STATE_POSITION = "player_state_position"

        /** 自动恢复的最多次数，超过就只提示、交回给用户手动重试 */
        private const val MAX_STALL_RETRY = 3

        /** 两次自动恢复之间的最小间隔，避免刚重建完又被判停滞 */
        private const val STALL_RETRY_COOLDOWN_MS = 15_000L

        /**
         * seek 之后外部音频轨道偏差校正的冷却时长（毫秒）。
         * 要盖住 `MediaPlayer.seekTo` 的异步窗口：seek 完 currentPosition 才会跳到新位置，
         * 冷却太短仍会在 seek 落地前触发二次校正（重复 seek 造成可闻停顿）。
         */
        private const val AUDIO_RESYNC_COOLDOWN_MS = 1_200L

        /** 在线 DASH 音轨失败后的重试上限（每次切清晰度/重新载入都归零）。 */
        /** 统一时钟健康检查：起播多久后开始取样（毫秒）。 */
    private val UNIFIED_HEALTH_DELAY_MS = 2_500L

    /** 统一时钟健康检查：取样窗口内至少应前进的播放位置（毫秒）。 */
    private val UNIFIED_HEALTH_MIN_ADVANCE_MS = 800L

    private const val MAX_DASH_AUDIO_RETRY = 1

        /**
         * 在线 DASH 音轨重试的等待时长。
         *
         * 切清晰度时旧音轨的 `AudioTrack` 还在异步拆掉，立刻重建会撞上 `INVALID_OPERATION`；
         * 600ms 足够让底层音频通路释放干净，又短到用户感知不到卡顿。
         */
        private const val DASH_AUDIO_RETRY_DELAY_MS = 600L

        /**
         * 弹幕时钟偏差超过这个值就认为"媒体时钟没接上"（体检日志的判据）。
         *
         * 正常值只来自「250ms 采样 + 40ms/帧蠕动」，即 ±300ms 量级，所以 500ms 已是异常。
         */
        private const val DANMAKU_DRIFT_ALERT_MS = 500L

        /**
         * 弹幕时钟体检日志的最小间隔（毫秒）。
         *
         * 只在偏差超标时打，且限到这个频率：日志要拼字符串 + 写文件，健康状态下不该白烧；
         * 而不限频时，媒体时钟真没接上会变成每 250ms（一个采样周期）一条。
         */
        private const val DANMAKU_DRIFT_LOG_INTERVAL_MS = 5_000L

        /** 恢复后位置又前进了这么多毫秒，才算"真的播起来了"，恢复计数清零 */
        private const val STALL_RECOVERED_MS = 5_000

        /**
         * 本地视频续播位置的周期落盘间隔（毫秒）。
         *
         * 只靠退出钩子会丢位置：被系统直接杀进程时 onPause/onStop 里写的已经是最后一次，
         * 之后播放的那一截就没了。15 秒与投稿视频的在线心跳同频，最大丢失量可接受。
         */
        private const val LOCAL_PROGRESS_SAVE_INTERVAL_MS = 15_000L

        /**
         * 自动跳转之后，片头/片尾判定暂停多久（毫秒）。
         *
         * 要盖住「`markSeekTarget` 写了新位置、IJK 的异步 seek 还没落地」那段窗口：窗口内
         * `progressChange` 会把 `video_now` 拉回旧位置，旧位置若仍在片段内就会再判一次。
         * 取 600ms（约 2 个定时器周期），且**必须显著短于片头/片尾之间的间隔**，
         * 否则连续两段（片头紧接着片尾）时后一段会被静默期吃掉。
         */
        private const val SKIP_QUIET_AFTER_SEEK_MS = 600L

        /**
         * 字幕底边距（dp）：控制栏「显示」时取 [SUBTITLE_MARGIN_BOTTOM_CONTROLBAR_DP]，
         * 要让字幕浮在底部控制栏之上（＝控制栏 59dp + 进度文字约 17dp，与 `activity_player.xml`
         * 里字幕的初始 76dp 对齐）；控制栏「收起/锁定」时底下没有控制栏占位，取较小的
         * [SUBTITLE_MARGIN_BOTTOM_COLLAPSED_DP]（仅留进度文字那段 17dp），字幕随之下沉贴近屏幕底部。
         */
        private const val SUBTITLE_MARGIN_BOTTOM_CONTROLBAR_DP = 76f
        private const val SUBTITLE_MARGIN_BOTTOM_COLLAPSED_DP = 17f
    }

    @Volatile // 被 PlaybackService 定时线程读（serviceDurationMs）
    private var video_all: Int = 0
    // 主线程的 progressChange 定时器写、弹幕渲染线程读（见 bindDanmakuView 的位置回调），
    // 必须 volatile：弹幕线程不再直接调 playerCore.currentPosition（那是取原生锁的 JNI 调用），
    // 只读这个内存值，没有 volatile 可能长期读到 0 / 过期位置，导致弹幕时间轴错乱。
    // 另：PlaybackService 的定时线程也会读它（servicePositionMs），同样需要可见性保证
    @Volatile
    private var video_now: Int = 0
    private var video_now_last: Int = 0
    /** 上次打印弹幕时钟偏差的时间（弹幕跟随媒体时钟的体检日志，见 progressChange）。 */
    private var danmakuDriftLogMs: Long = 0
    private var lastMediaSessionSecond: Int = -1 // 上次上报 MediaSession 的整秒数，用于抑制重复 Binder IPC
    private var progress_history: Long = 0
    private var progress_str: String = ""
    /**
     * 本页是一次「系统重建」（走了 onSaveInstanceState 恢复）而非首次进入。
     * 重建后必须回到原位，且不该再提示"已从上次的位置播放"。
     */
    private var restoredFromSaveState: Boolean = false

    /**
     * 本地/缓存视频所在的目录（`.video_meta.json` 就在其中）；在线视频（含直播）为 null。
     *
     * 单文件、DASH（video.mp4 + audio.m4a）、多P 的某个分P、虚拟合集里的某个条目，
     * 传进来的 `video_url` 都指向各自的目录，所以这里靠 url 反推目录，入口侧一行都不用改。
     * 播放器内切P 时由 [doSwitchPage] 重新计算。
     */
    private var localMediaDir: File? = null
    /** 上次落盘的本地续播位置（毫秒）；-1 表示本次会话还没写过，用于跨路径去重。 */
    private var lastSavedLocalProgressMs: Long = -1L
    /** 上次落盘的墙钟时刻，用于周期落盘的 15 秒节流。 */
    private var lastLocalProgressSaveAt: Long = 0L

    private var screen_width: Int = 0
    private var screen_height: Int = 0
    private var video_width: Int = 0
    private var video_height: Int = 0

    private var audioManager: AudioManager? = null

    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var scaleGestureListener: ViewScaleGestureListener? = null
    private var doubleTapGestureDetector: GestureDetector? = null
    private var previousX: Float = 0f
    private var previousY: Float = 0f
    private var gesture_moved: Boolean = false
    // 只表示「这一轮触摸里捏合过」，仅用于吞掉手势结束时的那次点击（ACTION_UP 一到就复位）。
    // 判断「画面现在是否仍缩放」一律走 isVideoZoomed()，别再用这个标志。
    private var gesture_scaled: Boolean = false
    private var gesture_click_disabled: Boolean = false
    private var video_origX: Float = 0f
    private var video_origY: Float = 0f
    private var timestamp_click: Long = 0
    private var onLongClick: Boolean = false

    // 左右滑动控制进度
    private var swipeSeekEnabled: Boolean = false
    private var swipeSeekActive: Boolean = false
    private var swipeSeekStartX: Float = 0f
    private var swipeSeekStartPos: Long = 0L
    private var swipeSeekTarget: Long = 0L
    private val swipeSeekThreshold = 24f
    private lateinit var swipe_seek_overlay: View
    private lateinit var swipe_seek_text: TextView

    private val speed_values = floatArrayOf(0.5F, 0.75F, 1.0F, 1.25F, 1.5F, 1.75F, 2.0F, 3.0F)
    private val speed_strs = arrayOf("x 0.5", "x 0.75", "x 1.0", "x 1.25", "x 1.5", "x 1.75", "x 2.0", "x 3.0")

    private var finishWatching = false
    // 播放器错误态：onError 里置位。
    // 必须记录这个状态：IJK 的 setOnErrorListener 若返回 false，IjkMediaPlayer 会改发 onCompletion
    // （ijkplayer-java/.../IjkMediaPlayer.java:1013-1017），于是"解码/加载失败"被伪装成"这集播完"，
    // 还会自动跳下一 P。置位后 controlVideo() 会把"点播放"当成重试，走 retryAfterPlayerError() 重新载入。
    private var playerError = false
    private var loop_enabled: Boolean = false
    private var auto_next_enabled = false

    // ---- 位置停滞看门狗（弱网，见 docs/archive/superpowers/specs/2026-10-11-player-network-resilience.md 的 P0-3）----
    // IJK 没有任何"位置 N 秒不前进"的看门狗：弱网下连接可能还在、但一个字节都不来，
    // 于是既不回调 onError、也不走 onCompletion，界面永远停在转圈上（用户看到的就是"卡死"）。
    // 这里复用已有的 250ms 主线程定时器做判定：位置长时间不动 → 自动重连。
    /** 上一次观测到的位置（判定"有没有前进"） */
    private var stallLastPosition: Int = -1
    /** 上一次发现位置前进的时间（elapsedRealtime） */
    private var stallLastAdvanceMs: Long = 0L
    /** 连续自动恢复的次数（真正播起来一段后清零） */
    private var stallRetryCount: Int = 0
    /** 上次自动恢复时的时间与位置，用于冷却与"是否真的播起来了" */
    private var stallRetryMs: Long = 0L
    private var stallRetryPosition: Int = 0
    /** 放弃自动恢复后只提示一次 */
    private var stallGaveUp = false
    /** 直播候选地址表与当前下标（由 LiveInfoActivity 经 PlayerData/Intent 传入） */
    private var liveUrlCandidates: ArrayList<String> = ArrayList()
    private var liveCandidateIndex: Int = 0

    /** 停滞多久算"卡死"：直播比点播更早介入（直播没有"等缓冲"的价值，越等越落后） */
    private val stallTimeoutMs: Long get() = if (isLiveMode) 20_000L else 30_000L

    private lateinit var batteryView: BatteryView
    private var batteryManager: BatteryManager? = null

    private var danmakuFile: File? = null

    private var screen_landscape: Boolean = false
    private var screen_round: Boolean = false

    /** 「播放默认值 → 屏幕方向」的模式值（见 [PlayerDefaults.resolveLandscape]）。 */
    private var playerOrientationMode: String = SettingsKeys.PLAYER_DEFAULT_ORIENTATION_PORTRAIT

    /**
     * 「按视频分辨率选择」是否已经判过一次方向。
     *
     * 必须只做一次：`changeVideoSize()` 会被 `onConfigurationChanged` 再次调到，
     * 若不设标志，转屏 → 重新判定 → 再转屏 会来回抖。用户之后手动旋转也不该被拽回来。
     */
    private var autoOrientationApplied: Boolean = false

    /**
     * 首次 prepare 时是否已把「播放默认值 → 倍速」设到滑条上。
     *
     * 重建播放器会再次走 `onPrepared`，只有第一次才该套用默认值，之后要沿用用户本次会话的选择。
     */
    private var speedDefaultApplied: Boolean = false

    @JvmField var online_number: String = "0"

    private var aid: Long = 0
    private var cid: Long = 0
    private var mid: Long = 0

    // 番剧(PGC)专用维度：投稿视频的进度上报只有 aid/cid 两个维度，
    // 番剧必须走心跳接口并带上 epid/sid/sub_type，否则观看记录与续播进度都不会被服务端更新
    // （表现就是"在终端里看的番剧，历史里不出现，下次还得从头播"）
    private var epid: Long = 0
    private var seasonId: Long = 0
    private var seasonType: Int = 0

    // ---- 播放中周期上报 / 切P·退出即时上报的状态（详见 maybeReportProgress / reportProgressNow）----
    // 与上次上报位置的差值上限，达到才再报一次，避免每 250ms 一个请求。
    // 番剧压到 5 秒：一集十几分钟看两眼就退的人不少，间隔太长时"最后一次有效心跳"还没发出去就退出了，
    // 服务端记的"看到哪"会明显落后，续播位置不准；投稿视频维持 15 秒（远端口径）
    private val progressReportIntervalMs: Long
        get() = if (epid != 0L) 5000L else 15000L
    // 上次上报时的播放位置（毫秒），初值 -1 让"刚开播"也能自然等到播满一个间隔才首报
    private var lastReportedProgressMs: Long = -1L
    // 上次上报出去的秒数，用于跨路径去重：onPause/onStop/onDestroy/finish 会连着走，
    // 不拦就会把同一个位置重复写三次
    private var lastReportedProgressSec: Long = -1L
    // 未登录只提示一次，否则每次周期上报都会刷一条日志
    private var notLoggedInWarned: Boolean = false

    /**
     * 本次的 pagenames/cids 是不是"收藏夹虚拟合集"。
     *
     * 由构建方通过 Intent 显式告知（见 [PlayerData.virtualCollection]）。**不要改回
     * `aid != cids[i]` 那种猜测**：真实多P 的 av 号与 cid 永不相等，会恒判为虚拟合集，
     * 切P 时把 cid 当 aid 去查 → 62002 → cid 落 0 → 取流 -400 → 切P 失败卡住。
     */
    private var isVirtualCollectionMode: Boolean = false

    /** 当前播放列表对应的真实合集 id（`ugc_season.id`）；0 = 不是合集。见 [syncToCollection]。 */
    private var collectionId: Long = 0L

    /** 当前合集的标题，只用于选集卡片上显示"现在在哪一层"。 */
    private var collectionTitle: String? = null

    /**
     * 当前播放列表是不是"某个多P视频的分P层"；0 = 不是，否则是该视频的 aid。
     *
     * <p>第二类"文件夹"（第一类是合集）。合集层存合集 id、这一层存视频 aid，id 空间不同，
     * 所以各占一个字段；选集卡片按"哪个非 0"决定显示哪一层。
     * 与合集层的差别：这一层的 `cids` 是真 cid，所以 [isVirtualCollectionMode] 为 false。
     */
    private var multiPageAid: Long = 0L

    /** 分P层对应视频的标题，只用于选集卡片标题（切分P 后 [videoTitle] 会变成分P 标题）。 */
    private var multiPageTitle: String? = null

    /**
     * **根目录层**（收藏夹虚拟合集）的名字，只用于选集卡片标题。
     *
     * <p>不能用 [videoTitle]：它会被 `doSwitchPage` 改成**当前条目/分P 的标题**，于是从嵌套层返回后
     * 卡片会显示成「黑马程序员Java…（1/20）」——看着像个"文件夹名"，其实是列表里的某一条，很误导。
     */
    private var rootTitle: String? = null

    /**
     * 当前这条能不能"进得去"——能的话选集卡片上多一行「进入分P / 进入合集」。
     *
     * <p>**进入是用户主动点的，不自动**（26.10.16 用户明确要求）：自动进入会在你只是想在收藏夹里
     * 顺着听的时候，突然被换成一门 105 讲课程的分P 列表，且收藏夹里排在后面的条目全被跳过。
     * 所以这里只**记录**"这条可进入"，等用户在选集卡片上点那行才真的换列表。
     *
     * <p>两者互斥且合集优先：合集是外层"一系列视频"，分P 是内层"一个视频的段落"。
     * 每次切到根目录层的条目都会重算（`null` = 这条是普通单视频，进不去）。
     */
    private var enterableCollection: Collection? = null
    private var enterableMultiPage: VideoInfo? = null

    /**
     * 重算"当前这条能不能进得去"。**必须在主线程调用**（选集卡片直接读这两个字段）。
     *
     * <p>合集优先于分P：合集是外层"一系列视频"，分P 是内层"一个视频的段落"。
     * 传 null（普通单视频 / 分P层内部）即清空。
     */
    private fun updateEnterable(videoInfo: VideoInfo?) {
        val collection = videoInfo?.collection
        // 只有一集的"合集"不值得套一层，否则用户会莫名看到选集卡片换了个标题
        enterableCollection = collection?.takeIf { Collection.toPlaylist(it).size > 1 }
        enterableMultiPage = if (enterableCollection == null &&
            videoInfo != null && videoInfo.cids.size > 1
        ) videoInfo else null
    }

    /**
     * 一层返回栈：从收藏夹连播自动切进合集时，收藏夹那条播放列表存在这里，
     * 合集播完后再弹回来继续。见 [returnToCallerPlaylist]。
     */
    private var returnPlaylist: PlayerData? = null

    private var pagenames: ArrayList<String>? = null
    private var cids: ArrayList<Long>? = null
    private var currentPageIndex = 0
    @Volatile // 被 PlaybackService 定时线程读（serviceTitle）
    private var videoTitle: String? = null

    private var qnStrList: Array<String>? = null
    private var qnValueList: IntArray? = null
    private var currentQuality = 0

    private var interactionData: InteractionVideoData? = null
    private var interactionGraphVersion: Long = 0
    private var currentEdgeId: Long = 0
    private var initialEdgeId: Long = 0
    private var currentQuestion: InteractionVideoData.InteractionQuestion? = null
    private var questionShown = false
    private var interactionChoiceLayout: LinearLayout? = null

    private var isShortVideoMode = false
    private lateinit var btn_video_info: TextView

    private var viewPoints: MutableList<ViewPoint>? = null
    private var viewPointAdapter: ViewPointAdapter? = null

    /** 从 [viewPoints] 提炼出的可自动跳过片段（片头/片尾），由 [ViewPointSkip.buildSegments] 生成。 */
    private var skipSegments: List<ViewPointSkip.Segment> = emptyList()

    /** 本次播放已经处理过的片段 key：自动跳过一次、或用户手动拖进去过，都不再重复跳。 */
    private val skipHandled = HashSet<String>()

    /** 最近一次自动跳过的片段，供「撤回」回到原位；跳完即置位，撤回后清空。 */
    private var lastSkippedSegment: ViewPointSkip.Segment? = null

    /**
     * 自动跳过判定的静默期截止时刻（`SystemClock.elapsedRealtime()`，0 = 不静默）。
     *
     * 跳转后 `video_now` 先写、IJK 的 `seekTo` 后落地，落地前 `currentPosition` 仍返回旧位置；
     * 旧位置若还在片段内就会触发下一轮判定 —— 用户看到的是「跳完又跳回去」。
     * 原先只靠 `skipHandled` 一条防线，现在加一道时间兜底（见 [SkipCancelPolicy]）。
     */
    private var skipQuietUntilMs: Long = 0L

    /**
     * 本次 `seekToVideo` 调用是否来自自动跳过，供 [markSeekTarget] 判断要不要起静默期。
     *
     * 用字段而不是给 `markSeekTarget` 加参数：它在别处还有调用点（重播、切页、恢复现场），
     * 那些位置默认不该起静默期，加默认参数反而容易漏掉真正的调用者意图。
     */
    private var pendingSeekIsAutoSkip: Boolean = false

    private var eventBusInit = false

    @JvmField var liveWebSocket: WebSocket? = null
    private var okHttpClient: OkHttpClient? = null
    private var subtitle_selected = -1

    override fun onBackPressed() {
        // 触摸锁定期间不允许离开播放页：先把锁定按钮点出来作为反馈（再点它才解锁）
        if (interceptLockedExit()) return
        // 开启「左右滑动控制进度」时视频页屏蔽返回键：否则系统返回 / 手表自带右滑返回会在滑动调进度时
        // 直接退出播放器（issue #1）。蓝牙/表冠的返回键、系统级返回都走这里。
        if (swipeSeekEnabled) return
        if (!SharedPreferencesUtil.getBoolean(SettingsKeys.BACK_DISABLE, false)) {
            super.onBackPressed()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        // forceDark：播放器永远暗色（黑底、白图标），不随「亮色模式」切换，见 ColorMode
        super.attachBaseContext(BiliTerminal.getFitDisplayContext(newBase, forceDark = true))
    }

    private fun getExtras(): Boolean {
        val intent = intent ?: return false

        video_url = intent.getStringExtra("url")
        danmaku_url = intent.getStringExtra("danmaku") ?: ""
        val title = intent.getStringExtra("title")

        if (video_url == null) return false
        if (danmaku_url.isNotEmpty()) Logu.v("弹幕", danmaku_url)
        Logu.v("视频", video_url)
        Logu.v("标题", title)
        text_title!!.text = title
        videoTitle = title
        // 从根目录层起播时，Intent 的 title 就是收藏夹名（见 FavoriteVideoListActivity
        // 的 "$folderName（虚拟合集）"）；返回嵌套层时会被覆盖成返回栈里的 title
        rootTitle = title

        aid = intent.getLongExtra("aid", 0)
        cid = intent.getLongExtra("cid", 0)
        mid = intent.getLongExtra("mid", 0)
        // 番剧心跳的三个维度。PlayerApi.jumpToPlayer 没有传，由 JumpToPlayerActivity.jump() 补进 Intent；
        // 缺了它们就退化成投稿上报，番剧观看记录不会被更新
        epid = intent.getLongExtra("epid", 0)
        seasonId = intent.getLongExtra("seasonId", 0)
        seasonType = intent.getIntExtra("seasonType", 0)

        progress_history = intent.getIntExtra("progress", 0).toLong()
        Logu.d("history", progress_history.toString())

        isLiveMode = intent.getBooleanExtra("live_mode", false)
        // 直播候选地址表（LiveInfoActivity 用 util/live/LiveStreamSelector 排好序后随 Intent 传入）：
        // 弱网下换 CDN / 换 HLS 就靠它，不必回头再请求一次取流接口
        liveUrlCandidates = intent.getStringArrayListExtra("live_url_candidates") ?: ArrayList()
        if (isLiveMode && liveUrlCandidates.isNotEmpty()) {
            liveCandidateIndex = liveUrlCandidates.indexOf(video_url.orEmpty()).takeIf { it >= 0 } ?: 0
            Logu.d("直播候选", "${liveUrlCandidates.size} 条，当前 $liveCandidateIndex")
        }
        isOnlineVideo = video_url!!.contains("http")
        // CDN 备份地址表（PlayerApi.jumpToPlayer 传入，已按主机优先级排过序；首位=主地址）。
        // 播放器报错重建时换下一个候选，能绕开被墙/被限速的单个 CDN。
        videoBackupUrls = intent.getStringArrayListExtra("video_backup_urls") ?: ArrayList()
        audioBackupUrls = intent.getStringArrayListExtra("audio_backup_urls") ?: ArrayList()
        videoBackupIndex = videoBackupUrls.indexOf(video_url).takeIf { it >= 0 } ?: 0
        if (videoBackupUrls.size > 1) {
            Logu.d("CDN候选", "${videoBackupUrls.size} 条，当前 $videoBackupIndex")
        }
        hasDanmaku = danmaku_url.isNotEmpty()

        // 本地/缓存视频没有服务端历史可读，续播位置就存在视频目录的 `.video_meta.json` 里
        // （见 util/VideoMetaManager.readResumeMs）。Intent 里没带 progress 时用它补上，
        // 后面的续播逻辑（onPrepared 里那段）与在线视频完全共用。
        if (!isOnlineVideo) {
            localMediaDir = File(video_url!!).absoluteFile.parentFile
            if (progress_history <= 0) {
                val savedMs = localMediaDir?.let { VideoMetaManager.readResumeMs(it) } ?: 0L
                if (savedMs > 0) {
                    progress_history = savedMs
                    Logu.d("本地续播", savedMs.toString())
                }
            }
        }

        if (intent.hasExtra("pagenames")) {
            pagenames = intent.getStringArrayListExtra("pagenames")
            if (intent.hasExtra("cids")) {
                val cidList = ArrayList<Long>()
                val cidArray = intent.getLongArrayExtra("cids")
                if (cidArray != null) {
                    for (c in cidArray) cidList.add(c)
                }
                cids = cidList
            } else {
                cids = ArrayList()
            }
            currentPageIndex = intent.getIntExtra("currentPageIndex", 0)
            // 由构建方显式告知，不再靠 aid != cids[i] 猜（真实多P 下那个判据恒真，会把 cid 当 aid）
            isVirtualCollectionMode = intent.getBooleanExtra("virtual_collection", false)
            // 合集层 / 分P层由选集卡片上的「进入…」在播放器内部建立，不由 Intent 预置
            @Suppress("DEPRECATION")
            updateEnterable(intent.getParcelableExtra("enterable_info"))
        }

        initialEdgeId = intent.getLongExtra("edgeId", 0)
        if (initialEdgeId > 0) {
            currentEdgeId = initialEdgeId
        }

        if (intent.hasExtra("qnStrList") && intent.hasExtra("qnValueList")) {
            qnStrList = intent.getStringArrayExtra("qnStrList")
            qnValueList = intent.getIntArrayExtra("qnValueList")
            currentQuality = intent.getIntExtra("currentQuality", SharedPreferencesUtil.getInt(SettingsKeys.PLAY_QN, 16))
        }

        // 起播清晰度按账号权限夹紧：设置里可能选了 4K/1080P60，但没登录或不是大会员时
        // 请求会被服务端降级，不夹紧的话清晰度面板会把「播不了的那档」显示成当前档。
        // 这里只用缓存判断（不查网络），大会员第一次开播最多先按 1080P 起，随后 refresh 会纠正。
        if (isOnlineVideo) {
            currentQuality = AccountStateStore.clampQuality(currentQuality)
        }

        isShortVideoMode = intent.getBooleanExtra("isShortVideoMode", false)

        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Logu.v("加载", "加载")
        val theme = SharedPreferencesUtil.getString(ColorScheme.PREF_KEY_THEME, ColorScheme.THEME_DEFAULT)
        // 主题映射统一在 ColorScheme.themeResId（此前这里复制了一份与 BaseActivity 相同的 when）
        //
        // 播放页**无条件**关掉系统窗口层的右滑返回（android:windowSwipeToDismiss）：
        // 手表自带的右滑返回在窗口层直接 finish()，不走 onBackPressed，应用根本收不到，
        // 表现为「看视频时手指一划就退出播放器」，且会顺手把「左右滑动控制进度」的手势抢走（issue #1）。
        // 此前只在「左右滑动控制进度」或全局「禁用返回键」开启时才换 noSwipe 主题，
        // 于是两个开关都关着时右滑返回照常生效——26.10.22 起改为无条件。
        //
        // 这里只换窗口属性（*.NoSwipe.AppCompat 相对主主题只多 windowSwipeToDismiss=false，
        // 配色/圆角/字体完全一致），**不碰任何触摸分发**：滑动调进度、双击、长按倍速、
        // 捏合缩放与拖动画面仍由本类自己的手势逻辑处理，语义不变。
        // 退出播放页仍有顶栏返回按钮与返回键（onBackPressed），右滑只是不再生效。
        swipeSeekEnabled = SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_SWIPE_SEEK, false)
        setTheme(ColorScheme.themeResId(theme, noSwipe = true))
        super.onCreate(savedInstanceState)

        // 「播放默认值 → 屏幕方向」：横屏/竖屏立即生效；「按视频分辨率选择」要先拿到视频宽高
        // （见 changeVideoSize），所以这里先按竖屏进，等 prepare 后再切过去。
        // 旧实现里全局「界面横屏」(ui_landscape) 会强制播放器横屏；改造后播放器的方向由它自己的
        // 默认值决定，未设置时用旧的 player_autolandscape 迁移过来（行为与升级前一致）。
        playerOrientationMode = SharedPreferencesUtil.getString(
            SettingsKeys.PLAYER_DEFAULT_ORIENTATION, defaultOrientationMode()
        )
        screen_landscape = PlayerDefaults.resolveLandscape(playerOrientationMode, 0, 0)
        if (SharedPreferencesUtil.getBoolean("dev_player_rotate_software", false) && screen_landscape) {
            MsgUtil.showMsg(UiText.of("不支持默认横屏！"))
            screen_landscape = false
        } else {
            requestedOrientation = if (screen_landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        setContentView(R.layout.activity_player)
        findview()
        if (!getExtras()) {
            finish()
            return
        }

        // 系统重建本页时（内存压力、以及 configChanges 未覆盖的配置变化如深色模式/字体大小），
        // Intent 仍是"进入页面那一刻"的那一份，progress_history 会退回旧值 → 表现为位置莫名跳回。
        // 用 onSaveInstanceState 存下的真实位置覆盖它。
        if (savedInstanceState != null && !isLiveMode) {
            val savedPosition = savedInstanceState.getInt(STATE_POSITION, 0)
            if (savedPosition > 0) {
                progress_history = savedPosition.toLong()
                restoredFromSaveState = true
                Logu.d("恢复位置", savedPosition.toString())
            }
        }

        initUI()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PLAYER_MEDIA_SESSION_ENABLE, false)) {
            initMediaSession()
        }

        // 原生库加载失败在个别设备上是常态（#48：dlopen 报 Bad file descriptor），
        // 必须走能兜住 Error 的路径——失败时提示用户并结束页面，绝不让 UnsatisfiedLinkError
        // 冒到主线程。往后的初始化（battery / 动画 / mainHandler / setDisplay 的定时任务）
        // 全都依赖 playerCore 非空，所以这里直接 return，避免半初始化状态继续跑。
        playerCore = createPlayerOrBail() ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
            batteryView.setPower(batteryManager!!.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
        } else
            batteryView.visibility = View.GONE

        // 「播放默认值」：开/关直接决定，沿用上次读 PLAYER_LAST_*；lastUsed 的默认值取各功能的
        // 原默认值（循环/听视频原本都是 false），于是"选了沿用上次但还没有历史"时行为与改造前一致。
        // 旧开关（player_loop / player_audio_only）只用来迁移默认模式，保证升级后行为不变。
        loop_enabled = PlayerDefaults.resolveTriState(
            SharedPreferencesUtil.getString(
                SettingsKeys.PLAYER_DEFAULT_LOOP,
                if (SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_LOOP, false))
                    SettingsKeys.PLAYER_DEFAULT_MODE_ON else SettingsKeys.PLAYER_DEFAULT_MODE_OFF
            ),
            SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_LAST_LOOP, false)
        )
        isAudioOnlyMode = PlayerDefaults.resolveTriState(
            SharedPreferencesUtil.getString(
                SettingsKeys.PLAYER_DEFAULT_AUDIO_ONLY,
                if (SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_AUDIO_ONLY, false))
                    SettingsKeys.PLAYER_DEFAULT_MODE_ON else SettingsKeys.PLAYER_DEFAULT_MODE_OFF
            ),
            SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_LAST_AUDIO_ONLY, false)
        )
        auto_next_enabled = PlayerDefaults.resolveTriState(
            SharedPreferencesUtil.getString(SettingsKeys.PLAYER_DEFAULT_AUTONEXT, SettingsKeys.PLAYER_DEFAULT_MODE_OFF),
            SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_LAST_AUTONEXT, false)
        )
        isLocalAudioFile = intent.getBooleanExtra("audio_only", false)
        audioTrackUrl = intent.getStringExtra("audio_track_url")
        if (isLocalAudioFile) {
            isAudioOnlyMode = true
        }
        img_loading.setImageResource(R.drawable.loading_tv_shaking)
        anim_loading = img_loading.drawable as AnimationDrawable
        anim_loading!!.start()

        val cachepath = cacheDir
        if (!cachepath.exists()) cachepath.mkdirs()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mainHandler = Handler(Looper.getMainLooper())

        setVideoGestures()
        initLockUi()
        autohideReset()

        initSeekbars()
        // 无极调音的滑条/存档在 findview() 之后、开始播放之前就位：无网络、无播放器依赖，
        // 只读一次 SharedPreferences 并接线，放这里与 initSeekbars 并列最直观。
        initVolumePanel()

        if (isLiveMode) {
            btn_control.visibility = View.GONE
            seekbar_progress.visibility = View.GONE
            seekbar_progress.isEnabled = false
            prepareDanmaku { it.prepareEmpty() }
        }

        layout_control.postDelayed({
            CenterThreadPool.run {
                if (isLiveMode) {
                    runOnUiThread {
                        btn_menu.visibility = View.GONE
                        btn_quality.visibility = View.GONE
                        btn_loop.visibility = View.GONE
                        btn_audio_only.visibility = View.GONE
                        btn_auto_next.visibility = View.GONE
                        btn_page_selector.visibility = View.GONE
                    }
                    setDisplay()
                    return@run
                }

                runOnUiThread {
                    loading_text0.text = UiText.of("装填弹幕中")
                    loading_text1.text = "(≧∇≦)"
                }
                if (isOnlineVideo) {
                    danmakuFile = File(cachepath, "danmaku.xml")
                    downdanmu()
                } else {
                    runOnUiThread { btn_danmaku_send.visibility = View.GONE }
                    danmakuFile = File(danmaku_url)
                    if (danmakuFile!!.exists())
                        prepareDanmaku { it.loadFromXmlFile(danmakuFile!!.toString()) }
                    else
                        hasDanmaku = false
                }

                if (!destroyed) maybeAutoSubtitle()

                if (!destroyed && isOnlineVideo && aid > 0 && cid > 0) {
                    loadHighEnergyData()
                }

                if (!destroyed && isOnlineVideo && aid > 0 && cid > 0 && needViewPoints()) {
                    loadViewPoints()
                }

                if (!destroyed && isOnlineVideo && aid > 0 && cid > 0) {
                    loadInteractionVideo()
                }

                // 提前查账号状态，用户第一次打开清晰度面板时锁态就是准的（失败会退回缓存）
                refreshAccountStateIfNeeded()

                if (!destroyed) setDisplay()
            }
        }, 60)
    }

    private fun findview() {
        layout_control = findViewById(R.id.control_layout)
        layout_top = findViewById(R.id.top)
        right_control = findViewById(R.id.right_control)
        right_second = findViewById(R.id.right_second)
        layout_card_bg = findViewById(R.id.card_bg)
        card_subtitle = findViewById(R.id.subtitle_card)
        card_danmaku_send = findViewById(R.id.danmaku_send_card)
        card_page_selector = findViewById(R.id.page_selector_card)
        card_quality_selector = findViewById(R.id.quality_selector_card)
        card_viewpoint_selector = findViewById(R.id.viewpoint_selector_card)
        layout_audio_only = findViewById(R.id.audio_only_layout)

        loading_info = findViewById(R.id.loading_info)

        img_loading = findViewById(R.id.circle)
        text_progress = findViewById(R.id.text_progress)
        text_online = findViewById(R.id.text_online)
        btn_danmaku = findViewById(R.id.danmaku_btn)
        btn_loop = findViewById(R.id.loop_btn)
        btn_rotate = findViewById(R.id.rotate_btn)
        btn_menu = findViewById(R.id.menu_btn)
        btn_danmaku_send = findViewById(R.id.danmaku_send_btn)
        btn_subtitle = findViewById(R.id.subtitle_btn)
        btn_audio_only = findViewById(R.id.audio_only_btn)
        btn_control = findViewById(R.id.button_video)
        btn_page_selector = findViewById(R.id.button_page_selector)
        btn_auto_next = findViewById(R.id.auto_next_btn)
        btn_quality = findViewById(R.id.button_quality)
        btn_viewpoint = findViewById(R.id.viewpoint_btn)
        seekbar_progress = findViewById(R.id.videoprogress)
        swipe_seek_overlay = findViewById(R.id.swipe_seek_overlay)
        swipe_seek_text = findViewById(R.id.swipe_seek_text)
        loading_text0 = findViewById(R.id.loading_text0)
        loading_text1 = findViewById(R.id.loading_text1)
        text_title = findViewById(R.id.text_title)
        text_volume = findViewById(R.id.showsound)
        layout_volume = findViewById(R.id.layout_volume)
        seekbar_volume = findViewById(R.id.seekbar_volume)
        text_volume_title = findViewById(R.id.text_volume_title)
        text_newvolume = findViewById(R.id.text_newvolume)
        layout_video = findViewById(R.id.videoArea)
        bindDanmakuView()
        batteryView = findViewById(R.id.battery)

        text_speed = findViewById(R.id.text_speed)
        layout_speed = findViewById(R.id.layout_speed)
        seekbar_speed = findViewById(R.id.seekbar_speed)
        text_newspeed = findViewById(R.id.text_newspeed)
        bottom_buttons = findViewById(R.id.bottom_buttons)
        btn_debug = findViewById(R.id.btn_debug)
        lock_btn = findViewById(R.id.lock_btn)
        lock_overlay = findViewById(R.id.lock_overlay)
        left_control = findViewById(R.id.left_control)

        text_subtitle = findViewById(R.id.text_subtitle)
        text_audio_title = findViewById(R.id.audio_title)
        text_audio_subtitle = findViewById(R.id.audio_subtitle)

        btn_video_info = findViewById(R.id.video_info_btn)

        // 字幕字号来自设置页，这里先按存档刷一次；之后每次 onResume 再刷（改完设置返回即生效）
        applySubtitleTextSize()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setVideoGestures() {
        val doubleTapSeekEnabled = SharedPreferencesUtil.getBoolean("player_doubletap_seek", false)
        val doubleTapSeekSeconds = SharedPreferencesUtil.getInt("player_doubletap_seek_seconds", 10)
        swipeSeekEnabled = SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_SWIPE_SEEK, false)

        if (doubleTapSeekEnabled) {
            doubleTapGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (playerCore != null && isPrepared && !isLiveMode) {
                        val x = e.x
                        val viewWidth = layout_control.width.toFloat()
                        // 双击快进的起点取主线程进度定时器维护的 video_now，不读 playerCore.currentPosition：
                        // 后者是取 native 锁的 JNI 调用，缓冲/seek 期间可能阻塞主线程
                        val currentPosition = video_now.toLong()
                        val seekOffset = doubleTapSeekSeconds * 1000L

                        gesture_click_disabled = true

                        val third = viewWidth / 3.0f
                        when {
                            x > third * 2 -> {
                                var newPosition = currentPosition + seekOffset
                                val duration = playerCore!!.duration
                                if (newPosition > duration) newPosition = duration
                                seekToPosition(newPosition)
                            }
                            x < third -> {
                                var newPosition = currentPosition - seekOffset
                                if (newPosition < 0) newPosition = 0
                                seekToPosition(newPosition)
                            }
                            else -> {
                                handleDoubleTapAction()
                            }
                        }
                        return true
                    }
                    return false
                }
            })
        }

        if (SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_SCALE, true)) {
            scaleGestureListener = ViewScaleGestureListener(layout_video)
            scaleGestureDetector = ScaleGestureDetector(this, scaleGestureListener!!)

            val doublemove_enabled = SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_DOUBLEMOVE, true)

            layout_control.setOnTouchListener { v, event ->
                if (doubleTapSeekEnabled && doubleTapGestureDetector != null) {
                    doubleTapGestureDetector!!.onTouchEvent(event)
                }
                val action = event.actionMasked
                val pointerCount = event.pointerCount
                val singleTouch = pointerCount == 1
                val doubleTouch = pointerCount == 2

                scaleGestureDetector!!.onTouchEvent(event)
                val gesture_scaling = scaleGestureListener!!.scaling

                if (!gesture_scaled && gesture_scaling) gesture_scaled = true

                when (action) {
                    MotionEvent.ACTION_MOVE -> {
                        if (singleTouch) {
                            if (gesture_scaling) {
                                videoMoveBy(0f, 0f)
                            } else if (!handleSwipeSeekTouch(event) &&
                                (!isVideoZoomed() || doublemove_enabled)
                            ) {
                                // 单指拖动只为查看放大后的画面：未缩放时 videoMoveBy 会被夹回原位，
                                // 缩放后则取决于「缩放时可移动」。以前这里判 gesture_scaled，
                                // 那个标志松手就复位，导致关掉「缩放时可移动」后仍能拖动。
                                val currentX = event.getX(0)
                                val currentY = event.getY(0)
                                val deltaX = currentX - previousX
                                val deltaY = currentY - previousY
                                if (deltaX != 0f || deltaY != 0f) {
                                    videoMoveBy(deltaX, deltaY)
                                    previousX = currentX
                                    previousY = currentY
                                }
                            }
                        }
                        if (doubleTouch && doublemove_enabled) {
                            val currentX = (event.getX(0) + event.getX(1)) / 2
                            val currentY = (event.getY(0) + event.getY(1)) / 2
                            val deltaX = currentX - previousX
                            val deltaY = currentY - previousY
                            if (deltaX != 0f || deltaY != 0f) {
                                videoMoveBy(deltaX, deltaY)
                                previousX = currentX
                                previousY = currentY
                            }
                        }
                    }
                    MotionEvent.ACTION_DOWN -> {
                        if (singleTouch) {
                            previousX = event.getX(0)
                            previousY = event.getY(0)
                            handleSwipeSeekTouch(event)
                        }
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if (doubleTouch) {
                            previousX = (event.getX(0) + event.getX(1)) / 2
                            previousY = (event.getY(0) + event.getY(1)) / 2
                        }
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        if (doubleTouch) {
                            val index = event.actionIndex
                            previousX = event.getX(if (index == 0) 1 else 0)
                            previousY = event.getY(if (index == 0) 1 else 0)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handleSwipeSeekTouch(event)
                        endLongPressBoost()
                        if (gesture_moved) gesture_moved = false
                        if (gesture_scaled) gesture_scaled = false
                    }
                }

                if (!gesture_click_disabled && (gesture_moved || gesture_scaled)) {
                    gesture_click_disabled = true
                    hidecon.run()
                }

                false
            }
        } else {
            layout_control.setOnTouchListener { _, motionEvent ->
                if (doubleTapSeekEnabled && doubleTapGestureDetector != null) {
                    doubleTapGestureDetector!!.onTouchEvent(motionEvent)
                }
                handleSwipeSeekTouch(motionEvent)
                if (motionEvent.action == MotionEvent.ACTION_UP ||
                    motionEvent.action == MotionEvent.ACTION_CANCEL
                ) {
                    endLongPressBoost()
                }
                false
            }
        }

        layout_control.setOnClickListener {
            if (gesture_click_disabled) gesture_click_disabled = false
            else clickUI()
        }
        layout_control.setOnLongClickListener {
            if (SharedPreferencesUtil.getBoolean("player_longclick", true) && playerCore != null && isPlaying && !isLiveMode) {
                if (!onLongClick && !gesture_click_disabled) {
                    hidecon.run()
                    longPressBoostActive = true
                    onLongClick = true
                    // 不直接 setSpeed(3f)：统一走倍速下发，长按/滑条/重建后的 onPrepared 都用同一套口径
                    applySpeedFromSeekbar()
                    Logu.v("gesture", "longclick_down")
                    return@setOnLongClickListener true
                }
                return@setOnLongClickListener false
            }
            false
        }
    }

    private fun autohideReset() {
        layout_control.removeCallbacks(hidecon)
        layout_control.postDelayed(hidecon, 4000)
    }

    private fun clickUI() {
        val nowTimestamp = System.currentTimeMillis()
        if (nowTimestamp - timestamp_click < 300) {
            handleDoubleTapAction()
        } else {
            timestamp_click = nowTimestamp
            if (layout_top.visibility == View.GONE) showcon()
            else hidecon.run()
        }
    }

    private fun handleDoubleTapAction() {
        // 普通双击与快进手势的中间区域共用一套判据；手动旋转不属于缩放。
        val action = PlayerDoubleTapPolicy.resolve(
            restoreScaleFirst = SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_DOUBLETAP_RESTORE_SCREEN, false),
            scaleEnabled = SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_SCALE, true),
            scaleX = layout_video.scaleX,
            scaleY = layout_video.scaleY,
            isLive = isLiveMode
        )
        when (action) {
            PlayerDoubleTapPolicy.Action.RESET_SCALE -> {
                layout_video.scaleX = 1f
                layout_video.scaleY = 1f
                layout_video.x = video_origX
                layout_video.y = video_origY
            }
            PlayerDoubleTapPolicy.Action.TOGGLE_PLAYBACK -> {
                if (isPlaying) playerPause()
                else playerResume()
                showcon()
            }
            PlayerDoubleTapPolicy.Action.IGNORE -> Unit
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showcon() {
        // 锁定期间控制栏必须整体保持隐藏，任何「显示控制栏」的路径都在这里被挡掉
        if (PlayerLockPolicy.isLocked(lockPhase)) return
        right_control.visibility = View.VISIBLE
        layout_top.visibility = View.VISIBLE
        bottom_buttons.visibility = View.VISIBLE
        seekbar_progress.visibility = View.VISIBLE
        seekbar_progress.isEnabled = false
        seekbar_progress.postDelayed(progressbarEnable, 200)
        if (isPrepared && !isLiveMode && !isAudioOnlyMode) {
            text_speed.visibility = View.VISIBLE
            updateDebugButtonVisibility()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryView.setPower(batteryManager!!.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
        }
        if (screen_round) {
            text_progress.gravity = Gravity.NO_GRAVITY
            text_progress.setPadding(ToolsUtil.dp2px(24f), 0, 0, 0)
            if (onlineTimer != null) text_online.visibility = View.VISIBLE
        }

        // 锁定按钮在 control_layout 之外（锁定时要把整条控制栏一起藏掉），显隐要跟控制栏同步
        lock_btn.visibility = View.VISIBLE

        // 控制栏浮现，字幕要抬高到控制栏之上，否则会被控制栏盖住
        setSubtitleBottomMargin(SUBTITLE_MARGIN_BOTTOM_CONTROLBAR_DP)

        autohideReset()
    }

    /**
     * 字幕在 control_layout 之外（锁定时不被整条控制栏一起置 GONE），底边距不会随控制栏显隐自动变化，
     * 所以这里手动调：控制栏收起后底下腾出空间，字幕要下沉，不能继续悬在控制栏原来的高度。
     */
    private fun setSubtitleBottomMargin(marginDp: Float) {
        val lp = text_subtitle.layoutParams as RelativeLayout.LayoutParams
        lp.bottomMargin = ToolsUtil.dp2px(marginDp)
        text_subtitle.layoutParams = lp
    }

    private val progressbarEnable = Runnable { seekbar_progress.isEnabled = true }

    private val hidecon = Runnable {
        // 锁定期间由锁定状态自己管显隐，别让自动隐藏去动控制栏（会顶掉浮现中的锁定按钮）
        if (PlayerLockPolicy.isLocked(lockPhase)) return@Runnable
        right_control.visibility = View.GONE
        layout_top.visibility = View.GONE
        bottom_buttons.visibility = View.GONE
        seekbar_progress.visibility = View.GONE
        // 控制栏自动隐藏时，无极调音弹层也要收掉：它盖住画面且拦截触摸，留着就点不到播放区。
        if (::layout_volume.isInitialized && layout_volume.visibility == View.VISIBLE) {
            layout_volume.removeCallbacks(hideVolumePanel)
            layout_volume.visibility = View.GONE
            persistVolumeGain()
        }
        if (isPrepared && !isAudioOnlyMode) {
            text_speed.visibility = View.GONE
            btn_debug.visibility = View.GONE
        }
        lock_btn.visibility = View.GONE
        // 控制栏收起后底下没了控制栏占位，字幕下沉到贴近屏幕底部；锁定态同样走这里，字幕保持可见且落在低位
        setSubtitleBottomMargin(SUBTITLE_MARGIN_BOTTOM_COLLAPSED_DP)
        if (screen_round) {
            text_progress.gravity = Gravity.CENTER
            text_progress.setPadding(0, 0, 0, ToolsUtil.dp2px(8f))
            if (onlineTimer != null) text_online.visibility = View.GONE
        }
        if (menu_opened) btn_menu.performClick()
    }

    // ==================== 播放页触摸锁定（防误触） ====================

    /**
     * 接线锁定相关的两个控件。
     *
     * 布局层级（`activity_player.xml` 末尾）：`lock_overlay` 全屏盖住 `control_layout` 与各种弹层，
     * `lock_btn` 声明在它之后、画在它上面——所以锁定时只有左下/左侧这个按钮可点。
     */
    private fun initLockUi() {
        lock_overlay.setOnClickListener { onLockScreenTap() }
        lock_btn.setOnClickListener { onLockButtonClick() }
        alignLeftControlToRight()
    }

    /**
     * 把左侧按钮列对齐到右侧竖列（弹幕/旋转/更多）的竖向中线。
     *
     * 右列的上下锚点（`top` 与 `bottom_control`）都在 `control_layout` 内部，位于 root 层的
     * `left_control` 够不到它们，所以这里量一次两条列的中线差、用 translationY 平移。
     * 一旦量到就不再重复——顶栏/底栏高度变化会触发重新布局，那时会重新量。
     */
    private fun alignLeftControlToRight() {
        left_control.post {
            if (left_control.height <= 0 || right_control.height <= 0) return@post
            val dy = (right_control.top + right_control.bottom) / 2f -
                    (left_control.top + left_control.bottom) / 2f
            left_control.translationY = dy
        }
    }

    /** 浮现中的锁定按钮到点自动隐去。 */
    private val hideLockIcon = Runnable {
        applyLockPhase(PlayerLockPolicy.phaseAfterTimeout(lockPhase))
    }

    private fun onLockButtonClick() {
        val before = lockPhase
        val next = PlayerLockPolicy.phaseAfterLockButtonClick(before)
        applyLockPhase(next)
        if (before == PlayerLockPolicy.Phase.UNLOCKED) {
            Logu.d("锁定", "锁定播放画面")
            showLockHintIfNeeded()
        } else {
            Logu.d("锁定", "解锁播放画面")
            // 锁定期间被拦下的 finish（系统重建、外部调用等）在解锁后补执行，别把退出请求吞掉
            if (pendingFinish) {
                pendingFinish = false
                finish()
            }
        }
    }

    private fun onLockScreenTap() {
        applyLockPhase(PlayerLockPolicy.phaseAfterScreenTap(lockPhase), PlayerLockPolicy.ICON_VISIBLE_MS)
    }

    /**
     * 把锁定状态落到视图上；[autoHideMs] > 0 时在这么多毫秒后自动隐去浮现的按钮。
     *
     * 状态只由 [PlayerLockPolicy] 决定，这里不重复判断"该不该锁"。
     */
    private fun applyLockPhase(phase: PlayerLockPolicy.Phase, autoHideMs: Long = 0L) {
        lockPhase = phase
        mainHandler?.removeCallbacks(hideLockIcon)
        when (phase) {
            PlayerLockPolicy.Phase.UNLOCKED -> {
                lock_overlay.visibility = View.GONE
                // 整条控制栏在锁定时被藏掉，解锁时连 button 组一起恢复，再走既有的 4 秒自动隐藏
                layout_control.visibility = View.VISIBLE
                // 图标画的是「当前状态」：未锁定＝开锁，锁定＝闭锁（26.10.21 用户反馈原先把状态画反了）
                lock_btn.setImageResource(R.drawable.icon_player_unlock)
                lock_btn.contentDescription = getString(R.string.desc_player_lock_locked)
                showcon()
                // 解锁后 control_layout 重新可见，右列位置可能变过，重新对齐左列
                alignLeftControlToRight()
            }

            PlayerLockPolicy.Phase.LOCKED_HIDDEN -> {
                lock_overlay.visibility = View.VISIBLE
                layout_control.visibility = View.GONE
                lock_btn.visibility = View.GONE
                // 倍速文字现在挂在左侧列里，不随 control_layout 一起隐藏，得单独收掉
                text_speed.visibility = View.GONE
                // 锁定时控制栏整体 GONE 而字幕仍显示，底下没了控制栏占位，字幕要下沉到低位
                setSubtitleBottomMargin(SUBTITLE_MARGIN_BOTTOM_COLLAPSED_DP)
                lock_btn.setImageResource(R.drawable.icon_player_lock)
                lock_btn.contentDescription = getString(R.string.desc_player_lock_unlocked)
            }

            PlayerLockPolicy.Phase.LOCKED_ICON_VISIBLE -> {
                lock_overlay.visibility = View.VISIBLE
                layout_control.visibility = View.GONE
                lock_btn.visibility = View.VISIBLE
                text_speed.visibility = View.GONE
                // 锁定时控制栏整体 GONE 而字幕仍显示，底下没了控制栏占位，字幕要下沉到低位
                setSubtitleBottomMargin(SUBTITLE_MARGIN_BOTTOM_COLLAPSED_DP)
                lock_btn.setImageResource(R.drawable.icon_player_lock)
                lock_btn.contentDescription = getString(R.string.desc_player_lock_unlocked)
            }
        }
        if (autoHideMs > 0L) mainHandler?.postDelayed(hideLockIcon, autoHideMs)
    }

    private fun showLockHintIfNeeded() {
        if (SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_LOCK_HINT_SHOWN, false)) return
        SharedPreferencesUtil.putBoolean(SettingsKeys.PLAYER_LOCK_HINT_SHOWN, true)
        MsgUtil.showMsg(getString(R.string.msg_player_lock_hint))
    }

    /**
     * 锁定期间尝试离开播放页：拦住，并让锁定按钮短暂浮现作为反馈。
     *
     * @return true 表示这次退出已被拦下，调用方必须直接返回。
     */
    private fun interceptLockedExit(): Boolean {
        if (!PlayerLockPolicy.shouldSwallowExit(lockPhase)) return false
        applyLockPhase(PlayerLockPolicy.phaseAfterExitAttempt(lockPhase), PlayerLockPolicy.EXIT_HINT_MS)
        return true
    }

    /**
     * 本安装版本是否已被判定为「统一时钟播放器不兼容」。
     *
     * 判定结果按 [BuildConfig.VERSION_NAME] 记录：同一版本内不再尝试统一时钟，避免每次播放都
     * 先卡住再回退；升级后版本号变化，会自动重新尝试一次。
     */
    private fun isUnifiedPlayerBlockedOnThisBuild(): Boolean =
        SharedPreferencesUtil.getString("player_unified_blocked_version", "") == BuildConfig.VERSION_NAME

    private fun markUnifiedPlayerBlocked(reason: String) {
        SharedPreferencesUtil.putString("player_unified_blocked_version", BuildConfig.VERSION_NAME)
        Logu.w("PlayerCore", "统一时钟播放器在本机不可用（$reason），本次安装改用 IJK 双播放器方案")
    }

    /**
     * 起播后做一次健康检查：统一时钟内核在部分设备上会出现「画面冻结在起播 1 秒、位置不再前进」的
     * 卡缓冲，且 ExoPlayer 自身不报错。与其让用户面对永久转圈，不如把这类设备自动切回 IJK。
     */
    private fun scheduleUnifiedHealthCheck() {
        mainHandler?.removeCallbacks(unifiedHealthCheck)
        unifiedProbeBasePos = -1L
        mainHandler?.postDelayed(unifiedHealthCheck, UNIFIED_HEALTH_DELAY_MS)
    }

    private fun checkUnifiedPlayerHealth() {
        val player = playerCore
        if (destroyed || isFinishing || player !is UnifiedDashPlayer) return
        if (!isPrepared || !isPlaying) return
        val pos = try {
            player.currentPosition
        } catch (t: Throwable) {
            return
        }
        if (unifiedProbeBasePos < 0L) {
            unifiedProbeBasePos = pos
            mainHandler?.postDelayed(unifiedHealthCheck, 2_000L)
            return
        }
        val advanced = pos - unifiedProbeBasePos
        if (advanced >= UNIFIED_HEALTH_MIN_ADVANCE_MS) return
        if (unifiedFallbackAttempted) return
        unifiedFallbackAttempted = true
        markUnifiedPlayerBlocked("起播后播放位置仅前进 ${advanced}ms")
        MsgUtil.showMsg(UiText.of("当前设备播放器不兼容，已自动切换兼容播放器"))
        retryAfterPlayerError(pos.coerceAtLeast(0L))
    }

    /**
     * 创建播放器实例。
     *
     * 在线点播且已经拿到 DASH 音频轨时，优先使用统一时钟内核 [ExoDashMediaPlayer]：视频轨、音频轨
     * 由同一个 ExoPlayer 消费，天然只有一条时钟，不会再出现「ijk 播视频 + MediaPlayer 播音频 +
     * 轮询校正」那种必然漂移的组合（音频轨加载失败、听视频模式 -10000 也都源于它）。
     *
     * 本地视频、直播（HLS）、只有合一 MP4 的场景仍走 IjkMediaPlayer。
     */
    private fun createPlayerOrBail(): IMediaPlayer? {
        // 注意：onCreate 里本方法早于 audioTrackUrl 字段赋值被调用，所以这里同时看一眼 Intent
        val audioUrl = audioTrackUrl ?: intent.getStringExtra("audio_track_url")
        val remoteAudio = audioUrl?.takeIf { PlayerStreamPolicy.isRemoteAudioTrack(it) }
        if (isOnlineVideo && !isLiveMode && remoteAudio != null && !isUnifiedPlayerBlockedOnThisBuild()) {
            try {
                val unified = ExoDashMediaPlayer(this, remoteAudio)
                unified.setAudioOnly(isAudioOnlyMode)
                Logu.v("PlayerCore", "使用统一时钟 DASH 播放器：视频轨与音频轨共用一条时钟")
                return unified
            } catch (t: Throwable) {
                // ExoPlayer 初始化失败（极端机型/ABI）时不让播放中断：回退老方案
                Logu.e("PlayerCore", "统一时钟播放器创建失败，回退 IJK：${t.message}")
            }
        }
        return createIjkPlayerOrBail()
    }

    /**
     * 创建播放器实例；原生库不可用时提示用户并结束页面，返回 null。
     *
     * 为什么集中在这里而不是各调用点各写一遍 try/catch：
     * 1. `IjkMediaPlayer.loadLibrariesOnce` 抛的是 `UnsatisfiedLinkError`（继承自 `Error`，
     *    不是 `Exception`），本文件里既有的 `catch (e: Exception)` 一律兜不住；
     * 2. `IjkMediaPlayer()` 构造内部会**再加载一次**同样的库（`mIsLibLoaded` 在失败时仍为
     *    false），所以只包住 `loadLibrariesOnce` 不够，构造本身也必须兜住；
     * 3. [IjkNativeLibs] 会把"本进程已失败"锁死，避免用户每开一个页面就重试一遍、刷一遍日志。
     *
     * 注意：失败时**不抛异常**，所以调用方必须判空。
     */
    private fun createIjkPlayerOrBail(): IjkMediaPlayer? {
        if (!IjkNativeLibs.ensureLoaded(this)) {
            bailOnNativeFailure()
            return null
        }
        return try {
            IjkMediaPlayer()
        } catch (t: Throwable) {
            // 库加载成功、但构造内部（initNativeOnce 等）仍抛 Throwable：按"不可用"处理
            IjkNativeLibs.reportCreateFailure(this, t)
            bailOnNativeFailure()
            null
        }
    }

    /**
     * 原生库不可用时的统一降级：提示 + 退出播放页。
     *
     * 本项目没有系统播放器回落能力（ijk 是唯一内核），所以不做"降级播放"，只做可靠退出。
     * 诊断信息（ABI、库目录、每个 .so 的存在/可读/大小）已由 [IjkNativeLibs] 写日志并按需上报。
     *
     * 提示用 Toast 而不是 `MsgUtil.showMsgLong`：SnackEvent 走 EventBus sticky，而本页在
     * onCreate 里就 finish() 了（本页的 onEvent 见 `isFinishing` 直接 return、不消费事件），
     * 能不能弹出来取决于"上一个页面返回时还读不读得到这条 sticky"，不可靠；
     * Toast 与 Activity 生命周期无关，是这条"立刻退出"路径上唯一保证用户能看见的通道。
     */
    private fun bailOnNativeFailure() {
        Logu.e("PlayerActivity", "原生播放库不可用，无法创建播放器，退出播放页")
        MsgUtil.toastLong(IjkNativeLibs.failureText(this))
        finish()
    }

    /**
     * 当前播放器是否走「统一时钟」内核（ExoPlayer 用同一个时钟同时消费 DASH 的视频轨与音频轨）。
     */
    private fun isUnifiedPlayer(): Boolean = playerCore is UnifiedDashPlayer

    /**
     * 统一时钟内核的配置：听视频模式只加载音频轨、把当前有效倍速同步进去。
     *
     * 听视频模式此前对 DASH 的纯视频流用 `vn=1` 让 ijk 只解音频，而纯视频流里根本没有音频轨，
     * 播放器于是直接报 -10000；统一时钟内核这边只喂音频轨，从根上没有了这个问题。
     */
    private fun applyUnifiedPlayerConfig() {
        val unified = playerCore as? UnifiedDashPlayer ?: return
        unified.setAudioOnly(isAudioOnlyMode)
        unified.setPlaybackSpeed(effectiveSpeedValue())
    }

    /**
     * 给当前播放器设倍速：统一时钟内核与 IJK 内核的入口不同，不能直接调 `setSpeed`。
     */
    private fun applyPlayerSpeed(speed: Float) {
        when (val player = playerCore) {
            is UnifiedDashPlayer -> player.setPlaybackSpeed(speed)
            is IjkMediaPlayer -> player.setSpeed(speed)
        }
    }

    /**
     * 只有 IJK 内核吃 IJK 的 option 表；统一时钟内核走 ExoPlayer，不需要这些参数。
     */
    private fun setIjkOption(player: IMediaPlayer?, category: Int, name: String, value: Any) {
        val ijk = player as? IjkMediaPlayer ?: return
        when (value) {
            is Int -> ijk.setOption(category, name, value.toLong())
            is Long -> ijk.setOption(category, name, value)
            is Float -> ijk.setOption(category, name, value.toLong())
            is String -> ijk.setOption(category, name, value)
        }
    }

    private fun setDisplay() {
        Logu.v("创建播放器")
        Logu.v("url", video_url)
        // 统一时钟内核：听视频模式只喂音频轨，并同步当前倍速
        applyUnifiedPlayerConfig()

        runOnUiThread { loading_text0.text = UiText.of("初始化播放") }

        if (isAudioOnlyMode) {
            setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "vn", 1)
            setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_frame", 48)
            setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48)
        } else {
            setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec",
                if (SharedPreferencesUtil.getBoolean("player_codec", true)) 1 else 0)
            setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48)
        }

        setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles",
            if (SharedPreferencesUtil.getBoolean("player_audio", false)) 1 else 0)
        setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1)
        setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1)
        setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 4)
        setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1)
        setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 100)
        setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1)
        setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1)
        setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "flush_packets")
        setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1)

        if (!isLiveMode) {
            // 不精确 seek（默认 0）会落到目标前最近的关键帧：切后台回来重挂 surface、
            // 用户拖进度条都会"往回跳一个 GOP"，表现为位置出错。VideoPlayerCore 也开了它
            // （player/VideoPlayerCore.kt:133）。直播不做 seek，故只在非直播下开。
            setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1)
        }

        if (isOnlineVideo) {
            setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1)
            setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 15 * 1024 * 1024)
            setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", NetWorkUtil.USER_AGENT_WEB)
            // 弱网韧性（详见 docs/archive/superpowers/specs/2026-10-11-player-network-resilience.md 的 P0-1）：
            // 1) rw_timeout：默认 0 = "读可以永久阻塞"，而且卡住时 IJK 不会回调 onError，
            //    界面上只会一直转圈 —— 这是弱网下直播/长视频"卡死不动"的直接原因；
            // 2) reconnect 只在"某次 read 返回负值"时才生效，而 chunked 直播（is_streamed=1）
            //    在 ffmpeg http.c 里还有一道 `!reconnect_streamed` 就 break 的判定，两者都要开；
            // 3) reconnect_delay_max 单位是秒（默认 120）。
            setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rw_timeout", 10_000_000L)
            setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_FORMAT, "addrinfo_timeout", 5_000_000L)
            setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_streamed", 1L)
            setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_delay_max", 5L)
            // 4) reconnect_at_eof：直播流被服务端"正常收尾"（chunked 响应干净 EOF）时，
            //    ffmpeg 会当成播完了，reconnect/reconnect_streamed 都不会触发。只有直播加这一项
            //    （spec 的 P0-1 明确要求，见 docs/archive/superpowers/specs/2026-10-11-player-network-resilience.md
            //    §1 第 3 条、§4 P0-1）；点播的 EOF 就是真的播完，加了会无限重连，故只放在直播分支。
            if (isLiveMode) {
                setIjkOption(playerCore, IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_at_eof", 1L)
            }
            Logu.v("设置ua")
        }

        Logu.v("准备设置显示")
        // 事件驱动：surface 已就绪则同步立即挂载并 prepare，未就绪则等 surface 回调。
        // 不再轮询、不再新建 Timer 线程，也不再有最长 200ms 的首帧等待。
        // 听视频模式只消费音频轨，不依赖 surface（此时 layout_video 可能已不可见），
        // 直接起播；普通模式仍等 surface 就绪后再 prepare，首帧更快。
        if (isUnifiedPlayer() && isAudioOnlyMode) {
            runOnUiThread { video_url?.let { if (!destroyed) MPPrepare(it) } }
        } else {
            surfaceBinder?.await()
        }
    }

    /** 把 IjkMediaPlayer 挂到当前 surface 上（SurfaceView 与 TextureView 两种模式）。 */
    private fun attachSurface(target: SurfaceTarget) {
        val player = playerCore ?: return
        when (target) {
            is SurfaceTarget.Holder -> {
                Logu.v("surface", "挂载 SurfaceHolder")
                player.setDisplay(target.holder)
            }
            is SurfaceTarget.Texture -> {
                Logu.v("surfacetexture", "挂载 Surface")
                player.setSurface(target.surface)
            }
        }
    }

    /** surface 消失时解绑，避免 IJK 继续往已销毁的 surface 上画。 */
    private fun detachSurface() {
        val player = playerCore ?: return
        if (textureView != null) player.setSurface(null) else player.setDisplay(null)
    }

    private fun MPPrepare(nowurl: String) {
        // 重载期间不能让上一轮的外部音频继续出声；onPrepared 会按新媒体轨道重新选择。
        releaseAudioTrack()
        // 新一轮载入重置 DASH 音轨重试额度，否则上一轮用掉的重试会让这一轮直接回退 720P
        dashAudioRetryCount = 0
        playerCore!!.setOnPreparedListener(this)
        // 任何一次新的载入（切P / 切清晰度 / 出错重试）都要清掉上一次的错误态，
        // 否则重试后 controlVideo() 仍会认为处于错误态而再次走重新载入分支。
        playerError = false

        if (isLiveMode) {
            runOnUiThread { loading_text0.text = UiText.of("载入直播中") }
            danmuSocketConnect()
        } else
            runOnUiThread { loading_text0.text = UiText.of("载入视频中") }
        try {
            val headers = HashMap<String, String>()
            headers["Referer"] = "https://www.bilibili.com/"
            headers["Cookie"] = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")
            val unified = playerCore as? UnifiedDashPlayer
            if (unified != null) {
                // 统一时钟内核：视频轨与音频轨交给同一个 ExoPlayer；音频流单独补地址与请求头。
                // 听视频模式下视频轨根本不会被加载（setAudioOnly 已在 setDisplay 里设过）。
                unified.setAudioOnly(isAudioOnlyMode)
                playerCore!!.setDataSource(this, android.net.Uri.parse(nowurl), headers)
                audioTrackUrl?.takeIf { PlayerStreamPolicy.isRemoteAudioTrack(it) }
                    ?.let { unified.setAudioSource(it, headers) }
                unified.setPlaybackSpeed(effectiveSpeedValue())
            } else if (isOnlineVideo) {
                (playerCore as IjkMediaPlayer).setDataSource(nowurl, headers)
            } else
                playerCore!!.setDataSource(nowurl)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        playerCore!!.setOnCompletionListener { completedPlayer ->
            if (destroyed || completedPlayer !== playerCore) return@setOnCompletionListener
            finishWatching = true
            // 看完立刻上报 -1（"已看完"）：退出路径补报的是当前位置，服务端只会记成
            // "看到最后几秒"，续播就停在本集末尾而不是接着看下一集。番剧（epid）才有这个语义
            if (epid != 0L) reportProgressNow(true, "看完上报")
            // 本地视频没有服务端，直接落盘"看完"：下次进来从头播（loop/自动连播不受影响，
            // 重新播起来后周期落盘会再把真实位置写回去）
            if (!isOnlineVideo) saveLocalProgressNow("看完清零")

            if (interactionData != null && interactionData!!.edges != null &&
                interactionData!!.edges!!.questions != null && !questionShown) {
                checkEndInteractionQuestions()
                if (questionShown) {
                    isPlaying = false
                    releaseAudioTrack()
                    if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.pause()
                    btn_control.setImageResource(R.drawable.btn_player_play)
                    return@setOnCompletionListener
                }
            }

            if (loop_enabled) {
                // 单曲循环＝又从头播起来了，不再是"看完了"：不清这个标志的话，
                // 本地视频的周期落盘会一直按"看完"写 0，第二轮看了半天也续不上
                finishWatching = false
                playerCore!!.seekTo(0)
                if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.seekTo(0L)
                playerCore!!.start()
            } else if (auto_next_enabled && hasMultiplePages() && currentPageIndex < pagenames!!.size - 1) {
                switchToPage(currentPageIndex + 1)
            } else if (auto_next_enabled && returnPlaylist != null) {
                // 合集放完了 → 弹回收藏夹接着放。跟"自动下一P"一样属于自动前进，所以同样受
                // auto_next_enabled 管；关掉自动播放的用户不该被这里偷偷换掉播放列表。
                returnToCallerPlaylist(advance = true)
            } else {
                isPlaying = false
                try { audioPlayer?.pause() } catch (_: Exception) {}
                if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.pause()
                btn_control.setImageResource(R.drawable.btn_player_play)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
                    updateMediaSessionPlaybackState()
                }
            }
        }

        playerCore!!.setOnErrorListener { failedPlayer, what, extra ->
            if (destroyed || failedPlayer !== playerCore) return@setOnErrorListener true
            val EReport = UiText.of("播放器可能遇到错误！\n错误码：") + what + UiText.of("\n附加：") + extra
            Logu.e("ijk-err", EReport)
            // 返回 false 会被 IjkMediaPlayer 改判成 onCompletion（IjkMediaPlayer.java:1013-1017），
            // 用户看到的是"这集看完了"并自动跳下一 P，而实际是解码/加载失败。
            // 必须返回 true 阻断该改判，并记录错误态：点播放键时 controlVideo() 会走重新载入重试。
            val firstError = !playerError
            playerError = true
            // 统一时钟内核失败：自动回退到 720P 合一 MP4（单 ijk 播放器），不让用户干等
            if (failedPlayer is UnifiedDashPlayer && !unifiedFallbackAttempted) {
                unifiedFallbackAttempted = true
                runOnUiThread { onUnifiedPlayerFailed() }
            }
            if (!destroyed) {
                // 只在第一次出错时提示，避免 IJK 连续回调 onError 时 toast 刷屏
                if (firstError) MsgUtil.showMsgLong(EReport)
                runOnUiThread {
                    isPlaying = false
                    releaseAudioTrack()
                    if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.pause()
                    btn_control.setImageResource(R.drawable.btn_player_play)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
                        updateMediaSessionPlaybackState()
                    }
                }
            }
            true
        }

        playerCore!!.setOnBufferingUpdateListener { _, percent ->
            seekbar_progress.secondaryProgress = percent * video_all / 100
        }

        if (isOnlineVideo || isLiveMode)
            playerCore!!.setOnInfoListener { _, what, _ ->
                if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    runOnUiThread {
                        loading_info.visibility = View.VISIBLE
                        anim_loading!!.start()
                        loading_text0.text = UiText.of("正在缓冲")
                        showLoadingSpeed()
                        if (hasDanmaku && mDanmakuView != null && isPlaying) mDanmakuView!!.pause()
                    }
                } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    runOnUiThread {
                        loadingRunnable?.let { mainHandler?.removeCallbacks(it) }
                        loading_info.visibility = View.GONE
                        anim_loading!!.stop()
                        if (hasDanmaku && mDanmakuView != null && isPlaying) mDanmakuView!!.resume()
                    }
                }
                false
            }

        playerCore!!.setScreenOnWhilePlaying(true)
        playerCore!!.prepareAsync()
        Logu.v("开始准备")
    }

    private fun showLoadingSpeed() {
        // 主线程自循环，重复调用时先移除上一个，避免多个轮询叠加
        loadingRunnable?.let { mainHandler?.removeCallbacks(it) }
        loadingRunnable = object : Runnable {
            override fun run() {
                val player = playerCore ?: return
                // 统一时钟内核没有 ijk 的 tcpSpeed，显示固定文案即可
                if (player !is IjkMediaPlayer) {
                    loading_text1.text = UiText.of("加载中")
                    if (!destroyed) mainHandler?.postDelayed(this, 1000)
                    return
                }
                val text = String.format(Locale.CHINA, "%.1f", player.tcpSpeed / 1024f) + "KB/s"
                loading_text1.text = text
                if (!destroyed) mainHandler?.postDelayed(this, 500)
            }
        }
        mainHandler?.post(loadingRunnable!!)
    }

    /**
     * 迁移旧「默认横屏」开关：开 → 横屏，关/未设置 → 竖屏（与升级前的行为一致）。
     *
     * 只在新的 [SettingsKeys.PLAYER_DEFAULT_ORIENTATION] 还没被写过时充当默认值。
     */
    private fun defaultOrientationMode(): String =
        if (SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_AUTOLANDSCAPE, false))
            SettingsKeys.PLAYER_DEFAULT_ORIENTATION_LANDSCAPE
        else SettingsKeys.PLAYER_DEFAULT_ORIENTATION_PORTRAIT

    /**
     * 「按视频分辨率选择」：等拿到真实视频宽高后判一次方向。
     *
     * 只判一次（[autoOrientationApplied]）——本方法会被 `onConfigurationChanged` 再次调到，
     * 转屏后重新判定会来回抖；用户手动旋转也不该被拽回来。
     */
    private fun applyAutoOrientationIfNeeded(videoWidth: Int, videoHeight: Int) {
        if (autoOrientationApplied || playerOrientationMode != SettingsKeys.PLAYER_DEFAULT_ORIENTATION_AUTO) return
        autoOrientationApplied = true

        val wantLandscape = PlayerDefaults.resolveLandscape(playerOrientationMode, videoWidth, videoHeight)
        if (wantLandscape == screen_landscape) return

        if (SharedPreferencesUtil.getBoolean("dev_player_rotate_software", false)) {
            // 软件旋屏设备：与 onCreate 一致，不自动转（那里的提示是"不支持默认横屏"），
            // 交给用户用旋转按钮，否则会走进一条转不动的死路。
            Logu.d("screen", "按视频分辨率选择：本机走软件旋屏，跳过自动转屏")
            return
        }
        screen_landscape = wantLandscape
        // 本方法可能在 IJK 线程上被调用（见 onPrepared 注释），转屏必须回主线程
        runOnUiThread {
            requestedOrientation = if (wantLandscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    /**
     * 「播放默认值 → 倍速」：把解析出的倍速设到滑条上，再同步给播放器与弹幕。
     *
     * 用 `seekbar_speed.progress =` 而不是模拟拖动：这样 `onProgressChanged` 的 `fromUser` 为 false，
     * 不会把"默认值"误写成「沿用上次」的记录位。
     */
    private fun applyDefaultSpeed() {
        val speed = PlayerDefaults.resolveSpeed(
            SharedPreferencesUtil.getString(SettingsKeys.PLAYER_DEFAULT_SPEED, "1.0"),
            SharedPreferencesUtil.getFloat(SettingsKeys.PLAYER_LAST_SPEED, 1.0f)
        )
        seekbar_speed.progress = speed_values.indexOfFirst { abs(it - speed) < 0.001f }.takeIf { it >= 0 } ?: 2
        applySpeedFromSeekbar()
    }

    /**
     * 按滑条当前档位重新应用到播放器与弹幕。
     *
     * 重建播放器（切清晰度 / 切听视频 / 重试）后会再次走 `onPrepared`，此时必须沿用本次会话里
     * 用户选的倍速，否则会掉回 1x —— 原实现就是这样（滑条还显示着别的值，属于隐性不一致）。
     */
    private fun applySpeedFromSeekbar() {
        val index = seekbar_speed.progress.coerceIn(0, speed_values.size - 1)
        val speed = effectiveSpeedValue()
        // 长按加速期间控件已隐藏，文案直接显示 3x；松手后再回到滑条档位
        text_speed.text = if (longPressBoostActive) boostLabel() else speed_strs[index]
        text_newspeed.text = speed_strs[index]
        applyPlayerSpeed(speed)
        danmakuManager?.setSpeed(speed)
        applyAudioSpeed(speed)
    }

    /** 倍速滑条当前档位对应的倍速值（越界时夹到合法档，避免崩溃）。 */
    private fun currentSpeedValue(): Float =
        speed_values[seekbar_speed.progress.coerceIn(0, speed_values.size - 1)]

    /**
     * 当前**实际生效**的倍速值：长按加速期间是 3x，否则就是滑条档位。
     *
     * 下发倍速必须用它——只看滑条的话，长按加速在 DASH 高清片源上会"看起来生效、其实画面照旧 1x"。
     */
    private fun effectiveSpeedValue(): Float =
        if (longPressBoostActive) LONG_PRESS_BOOST_SPEED else currentSpeedValue()

    /** 长按加速时显示在倍速文案上的字符串（`x 3.0`）。 */
    private fun boostLabel(): String = "x ${LONG_PRESS_BOOST_SPEED}"

    private fun changeVideoSize() {
        if (!isPrepared || playerCore == null) return
        val width = playerCore!!.videoWidth
        val height = playerCore!!.videoHeight
        Logu.v("screen", screen_width.toString() + "x" + screen_height)
        Logu.v("video", width.toString() + "x" + height)

        if (width == 0 || height == 0) {
            Logu.v("视频尺寸", "视频宽高为0，跳过尺寸调整（可能处于听视频模式）")
            return
        }

        applyAutoOrientationIfNeeded(width, height)

        if (SharedPreferencesUtil.getBoolean("player_ui_round", false)) {
            val videoMul = height.toFloat() / width.toFloat()
            val sqrt = Math.sqrt((screen_width * screen_width / ((height.toDouble() * height / (width * width)) + 1)).toDouble())
            video_height = (sqrt * videoMul + 0.5).toInt()
            video_width = (sqrt + 0.5).toInt()
        } else {
            val widthCase1 = width * screen_height / height
            val heightCase2 = height * screen_width / width

            if (widthCase1 <= screen_width) {
                video_width = widthCase1
                video_height = screen_height
            } else {
                video_width = screen_width
                video_height = heightCase2
            }
        }

        runOnUiThread {
            layout_video.layoutParams = RelativeLayout.LayoutParams(video_width, video_height)
            Logu.v("改变视频区域大小", video_width.toString() + "x" + video_height)
            video_origX = (screen_width - video_width) / 2f
            video_origY = (screen_height - video_height) / 2f

            resizePostRunnable?.let { layout_video.removeCallbacks(it) }
            resizePostRunnable = Runnable {
                layout_video.x = video_origX
                layout_video.y = video_origY
                Logu.v("改变视频位置", ((screen_width - video_width) / 2).toString() + "," + ((screen_height - video_height) / 2))
            }
            layout_video.postDelayed(resizePostRunnable!!, 60)
        }
    }

    private fun progressChange() {
        // 主线程自循环，替代原 Timer + runOnUiThread 的两层跨线程投递
        progressRunnable?.let { mainHandler?.removeCallbacks(it) }
        progressRunnable = object : Runnable {
            @SuppressLint("SetTextI18n")
            override fun run() {
                if (isPrepared && isPlaying && !isSeeking && !destroyed) {
                    // 取不到播放器时保持上次位置，避免进度条突然跳回 0
                    video_now = playerCore?.currentPosition?.toInt() ?: video_now
                    // 外部音频轨道（DASH分离文件fallback）同步校正：偏差过大时重新对齐，避免音画漂移。
                    // seek 的冷却期内不校正：MediaPlayer.seekTo 异步，seek 未完成时 currentPosition
                    // 还停在旧位置，这时再校正等于同一位置 seek 两遍（用户听到的就是停顿两次）。
                    val nowElapsed = android.os.SystemClock.elapsedRealtime()
                    audioPlayer?.let { ap ->
                        if (audioTrackPrepared && nowElapsed >= audioResyncSuppressMs) {
                            try {
                                val audioPos = ap.currentPosition
                                // 只纠「还在变大」的漂移，不纠固定偏差：
                                // 两路各自的时钟基准本就差一截（模拟器上稳定差 ~850ms，视频渲染慢），
                                // 拿绝对偏差当判据会每 2~5 秒 seek 一次音轨（听感是持续一顿一顿）。
                                // 首次采样把当前偏差记成基线，之后只有偏离基线超过阈值才对齐。
                                val drift = audioPos - video_now
                                if (!audioDriftSampled) {
                                    audioDriftSampled = true
                                    audioDriftBaseline = drift
                                } else if (audioPos >= 0 && Math.abs(drift - audioDriftBaseline) > 800) {
                                    ap.seekTo(video_now)
                                    // 重新采样：seek 落地后偏差要重新算基线
                                    audioDriftSampled = false
                                    // 并进冷却期：MediaPlayer.seekTo 是异步的，seek 未完成时
                                    // currentPosition 还报旧值，下一拍再判就会把「seek 没生效」
                                    // 当成新的漂移，连着 seek 三下（模拟器实测 1x 下就冒出 3 连 seek，
                                    // 听感是音频连跳三下）。冷却期沿用拖动 seek 的同一套常量。
                                    audioResyncSuppressMs = nowElapsed + AUDIO_RESYNC_COOLDOWN_MS
                                    Logu.d("AudioTrack", "音频轨道重新同步: $audioPos -> $video_now")
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    // 弹幕时钟**不再做周期性纠偏**：自 26.10.22 起弹幕时间轴由
                    // `DanmakuManager.bindMediaClock` 直接以 `video_now` 为时间源（见
                    // DanmakuFlameMaster 的 `MediaClockSync` / `DrawHandler.syncTimer`），
                    // 播放器跑到多少弹幕就跟到多少，不再存在"速率性发散"，也就不需要吸附。
                    //
                    // 历史坑：以前这里的判据是"偏差 >1.5s 且距上次 >1.5s 就 seekTo"，看起来限了频，
                    // 但 `seekTo` 会走 `DrawTask.seek() → reset()` 把屏上弹幕清空、再靠
                    // `prepareCaches(true)` 重建，于是漂移持续时等于每隔十来秒清一次屏
                    // （用户看到的「弹幕闪一下又没了」）。而漂移的根因是 DFM 用墙钟计时：
                    // 实测视频只跑 0.9x 墙钟时，弹幕按 1.0x 走，每 4 秒必然多跑约 490ms。
                    // **不要再把周期性 seekTo 加回来**，用户拖动/快进的对齐走 seekToVideo()。
                    //
                    // 体检：媒体时钟生效时偏差只应来自「250ms 采样 + 40ms/帧蠕动」，即 ±300ms 量级；
                    // 持续超过 500ms 就说明媒体时钟没接上（比如位置一直是负数走了墙钟回退分支）。
                    //
                    // 只在**异常**时打，且每 [DANMAKU_DRIFT_LOG_INTERVAL_MS] 最多一条：
                    // 健康状态下每条日志都要拼字符串、写文件，纯属白烧；而"一超标就立刻打"
                    // 在媒体时钟真没接上时会变成每 250ms 一条（一个采样周期一条），比不打更糟。
                    if (hasDanmaku && mDanmakuView != null) {
                        val drift = video_now - mDanmakuView!!.currentTime
                        if (Math.abs(drift) > DANMAKU_DRIFT_ALERT_MS &&
                            nowElapsed - danmakuDriftLogMs >= DANMAKU_DRIFT_LOG_INTERVAL_MS
                        ) {
                            danmakuDriftLogMs = nowElapsed
                            Logu.d("弹幕时钟", "视频=$video_now 弹幕=${mDanmakuView!!.currentTime} 偏差=$drift")
                        }
                    }
                    if (video_now_last != video_now) {
                        video_now_last = video_now
                        val currSec = video_now / 1000f
                        if (isLiveMode) {
                            text_progress.text = StringUtil.toTime(currSec.toInt())
                            text_online.text = online_number
                        } else {
                            seekbar_progress.progress = video_now
                        }
                        if (subtitles == null) text_subtitle.visibility = View.GONE

                        if (viewPointAdapter != null && viewPoints != null && viewPoints!!.isNotEmpty()) {
                            viewPointAdapter!!.updateCurrentPosition(currSec.toInt())
                        }
                        // 复用同一个 250ms 主线程定时器做片头/片尾判定，不另起 Timer
                        maybeAutoSkipOpEd(currSec.toDouble())
                        if (subtitles != null) showSubtitle(currSec + subtitle_delta)

                        // 只在整秒变化时上报，避免每 250ms 一次 MediaSession Binder IPC
                        if (video_now / 1000 != lastMediaSessionSecond) {
                            lastMediaSessionSecond = video_now / 1000
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
                                updateMediaSessionPlaybackState()
                            }
                        }

                        // 复用这个已有的 250ms 主线程定时器做周期上报，不再另起 Timer/Handler：
                        // 位置没变化时进不来（暂停/缓冲/播完都停在上一个位置），天然不会空转上报
                        maybeReportProgress()
                        // 本地视频没有服务端心跳，靠这条周期落盘（15 秒节流）防止被系统杀进程后丢位置
                        maybeSaveLocalProgress()
                    }
                }
                // 停滞判定放在 if 之外：暂停/seek/未准备好时也需要复位计数器
                checkStall()
                if (!destroyed) mainHandler?.postDelayed(this, 250)
            }
        }
        mainHandler?.post(progressRunnable!!)
    }

    /**
     * 位置停滞看门狗：由 [progressChange] 的 250ms 主线程定时器驱动。
     *
     * 判据只有一条——**位置长时间不前进**（而不是"有没有收到 onInfo/onError"）：
     * 弱网下最常见的坏形态是 TCP 连接还在、数据一个字节都不来，IJK 不会报错也不会结束，
     * 界面就永远停在转圈上。直播 20 秒、点播 30 秒不动就自动恢复：
     * - 直播：按候选表换下一条 CDN（换完一轮还有 HLS 兜底），地址来自 LiveInfoActivity 传给播放器的候选表；
     * - 点播：原地重建播放器并从当前位置续播。
     * 最多连续恢复 [MAX_STALL_RETRY] 次，之后只提示一次，交回给用户手动重试。
     */
    private fun checkStall() {
        if (destroyed) return
        // 未准备/暂停/拖拽/已知错误态都不算停滞：这些都是"用户意图"或"已有处理路径"
        if (!isPrepared || !isPlaying || isSeeking || playerError) {
            resetStallTracking()
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()

        if (video_now != stallLastPosition) {
            stallLastPosition = video_now
            stallLastAdvanceMs = now
            // 恢复之后真的又播了一段（而不是刚重建完又卡），把恢复预算还回去
            if (stallRetryCount > 0 && video_now - stallRetryPosition >= STALL_RECOVERED_MS) {
                stallRetryCount = 0
                stallGaveUp = false
            }
            return
        }

        if (stallLastAdvanceMs == 0L) {
            // 刚开始播放/刚恢复：以第一次观测为起点，不立刻判定
            stallLastAdvanceMs = now
            return
        }
        if (now - stallLastAdvanceMs < stallTimeoutMs) return

        if (stallRetryCount >= MAX_STALL_RETRY) {
            if (!stallGaveUp) {
                stallGaveUp = true
                Logu.d("停滞看门狗", "已连续自动恢复 $stallRetryCount 次，放弃自动恢复")
                MsgUtil.showMsg(UiText.of("网络似乎已断开，可稍后手动重试"))
            }
            // 继续刷新基准时间，避免每 250ms 都来一次提示
            stallLastAdvanceMs = now
            return
        }
        if (now - stallRetryMs < STALL_RETRY_COOLDOWN_MS) return

        stallRetryCount++
        stallRetryMs = now
        stallRetryPosition = video_now
        stallLastAdvanceMs = now
        Logu.d("停滞看门狗", "位置停在 ${video_now}ms 超过 ${stallTimeoutMs}ms，第 $stallRetryCount 次自动恢复（直播=$isLiveMode）")
        if (isLiveMode) switchLiveCandidate() else retryAfterPlayerError()
    }

    /** 暂停/拖拽/错误态下复位，避免"暂停十分钟后一恢复播放就被判停滞" */
    private fun resetStallTracking() {
        stallLastPosition = video_now
        stallLastAdvanceMs = 0L
    }

    /**
     * 直播换线路：按候选表顺序切到下一个地址后重建播放器。
     * 候选表是进入播放器前就排好的（FLV 各线路在前、HLS 在后），所以这里不需要网络请求，
     * 即使取流接口此刻不可用也能换 —— 这正是弱网下最需要的能力。
     */
    private fun switchLiveCandidate() {
        if (liveUrlCandidates.size < 2) {
            // 只有一条地址（或调用点没传候选表）：退化成原地重连
            Logu.d("停滞看门狗", "无备用直播线路，原地重连")
            retryAfterPlayerError(0L)
            return
        }
        liveCandidateIndex = (liveCandidateIndex + 1) % liveUrlCandidates.size
        val next = liveUrlCandidates[liveCandidateIndex]
        video_url = next
        Logu.d("停滞看门狗", "切换直播线路 → $liveCandidateIndex : $next")
        MsgUtil.showMsg(UiText.of("网络不佳，正在切换线路"))
        // 直播不续播位置：从最新的流位置重新开始
        retryAfterPlayerError(0L)
    }

    /**
     * 周期上报当前进度（番剧每 5 秒一次，投稿视频每 15 秒一次，见 [progressReportIntervalMs]）。
     *
     * 只由 [progressChange] 的 250ms 定时器在主线程调用；节流用"与上次上报位置的毫秒差值"而不是墙钟时间，
     * 所以暂停、缓冲、seek 中都不会补报，也就不会有积压的请求在恢复播放后一起炸出来。
     */
    private fun maybeReportProgress() {
        if (destroyed || !isPrepared || !isPlaying || isSeeking) return
        if (!isOnlineVideo || isLiveMode) return
        if (!canReportProgress()) return
        // 位置没走够就不报。初值 -1 意味着开播/切P后播满一个上报间隔才首报
        if (Math.abs(video_now.toLong() - lastReportedProgressMs) < progressReportIntervalMs) return
        val progressSec = video_now / 1000L
        // 0 秒上报没有信息量还会覆盖服务端记录（见 HistoryApi.reportHistoryPgc 的说明），直接跳过；
        // 这里不更新 lastReportedProgressMs，等真正播满一个间隔再报
        if (progressSec <= 0) return
        lastReportedProgressMs = video_now.toLong()
        lastReportedProgressSec = progressSec
        sendProgressReport(progressSec, UiText.of("周期上报"))
    }

    /**
     * 周期落盘本地续播位置。
     *
     * 由 [progressChange] 的 250ms 定时器驱动，按墙钟 15 秒节流（不按位置差值：暂停时位置不动，
     * 但"最后停在哪"本身就是要保住的信息）。写完的结果会被 [lastSavedLocalProgressMs] 记住，
     * 退出钩子再走到同一个位置时不会重复写盘。
     */
    private fun maybeSaveLocalProgress() {
        if (isOnlineVideo || isLiveMode || destroyed || !isPrepared) return
        val now = System.currentTimeMillis()
        if (now - lastLocalProgressSaveAt < LOCAL_PROGRESS_SAVE_INTERVAL_MS) return
        lastLocalProgressSaveAt = now
        saveLocalProgressNow("周期落盘")
    }

    /**
     * 把本地视频当前播放位置写进它所在目录的 `.video_meta.json`。
     *
     * 本地视频没有服务端历史可上报（[maybeReportProgress] 的第一行就把它们挡掉了），
     * 所以续播位置只能自己存。由退出路径（onPause / onStop / onDestroy / finish）、
     * [onCompletion][IjkMediaPlayer.setOnCompletionListener] 与 [maybeSaveLocalProgress] 调用。
     *
     * 刻意在主线程同步写：文件不到 1KB，而如果改成后台线程，"退出时的补写"可能被
     * 已在队列里的周期写反过来覆盖，最后存下的是更早的位置。跨线程的并发保护由
     * [VideoMetaManager.saveProgress] 的 `@Synchronized` 负责。
     *
     * @param reason 只用于日志，便于区分是哪条路径写的
     */
    private fun saveLocalProgressNow(reason: String) {
        if (isOnlineVideo || isLiveMode) return
        // 还没 prepare 完就退出时 video_now 还是 0，写下去会把已有的续播位置清掉
        if (destroyed || !isPrepared) return
        val dir = localMediaDir ?: return
        // 同理：刚进页面还没播就退出（位置 0）不该覆盖上次看到的位置
        if (!finishWatching && video_now <= 0) return
        val durationMs = video_all.toLong()
        // 播完 = 下次从头。durationMs 未知时 positionMs 取 0，progressToSave 同样返回 0
        val positionMs = if (finishWatching) durationMs else video_now.toLong()
        val target = LocalVideoProgress.progressToSave(positionMs, durationMs)
        if (lastSavedLocalProgressMs >= 0 && target == lastSavedLocalProgressMs) return
        lastSavedLocalProgressMs = target
        VideoMetaManager.saveProgress(dir, positionMs, durationMs)
        Logu.d("本地进度", "$reason → ${target}ms（总长 ${durationMs}ms）")
    }

    /**
     * 立即上报一次当前进度。
     *
     * @param force  true 表示"退出/切P前的最后一次"，即使进度比上次上报的小（用户往回 seek 过）也要报真实位置；
     *               false 用于切后台等场景，只报前进的进度，避免把服务端的较大记录覆盖成小值。
     * @param reason 只用于日志，便于区分是哪条退出路径上报的
     * @return 是否真的把请求发出去了（用于和 JumpToPlayerActivity 的退出上报去重）
     */
    private fun reportProgressNow(force: Boolean, reason: String): Boolean {
        if (destroyed || !isOnlineVideo || isLiveMode) return false
        if (!canReportProgress()) return false
        // 取内存里的 video_now，而不是 playerCore.currentPosition：后者是取原生锁的 JNI 调用，
        // seek/缓冲期间可能长时间不返回，放在退出路径上就是 ANR。
        // video_now 由 progressChange 每 250ms 刷新，差半秒对进度上报无影响。
        val currentSec = video_now / 1000L
        // 这一集看完之后一律报 -1（"已看完"）：onCompletion 已经报过一次，之后的退出补报如果改报当前位置，
        // 服务端会把"已看完"覆盖回"看到最后几秒"，续播就会停在本集末尾而不是接着往下走
        val finished = finishWatching && epid != 0L
        val progressSec = if (finished) HistoryApi.PROGRESS_FINISHED else currentSec
        // 0 秒没有可续播的位置；-1 是有信息的"已看完"终态，必须放行（所以这里只挡 0，不是 <= 0）
        if (progressSec == 0L) return false
        if (finished) {
            // -1 是终态，报过一次就够，免得 onPause/onStop/onDestroy 连着报三遍
            if (lastReportedProgressSec == HistoryApi.PROGRESS_FINISHED) return false
        } else {
            // 同一秒在任何路径上已经报过就跳过：onPause → onStop → onDestroy、finish → onPause 都是连着走的，
            // 不做这一步就会把同一个位置重复写三遍
            if (progressSec == lastReportedProgressSec) return false
            // 非强制的路径只报前进的进度（seek 回退后没继续播时，别把服务端的大进度改成小进度）
            if (!force && progressSec < lastReportedProgressSec) return false
        }

        lastReportedProgressMs = video_now.toLong()
        lastReportedProgressSec = progressSec
        sendProgressReport(progressSec, reason)
        return true
    }

    /** 上报的前置检查：aid/cid 有没有、以及当前到底登没登录。 */
    private fun canReportProgress(): Boolean {
        if (aid == 0L || cid == 0L) return false
        // mid 是随 Intent 进来的快照，换设备/清数据/刷新 Cookie 后可能滞后；
        // 实时 Cookie 里的 DedeUserID 才代表当前登录态（与 HistoryApi.currentMid 同一口径）
        if (mid != 0L) return true
        val cookie = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")
        val midStr = NetWorkUtil.getInfoFromCookie("DedeUserID", cookie)
        if (midStr != null && midStr.isNotEmpty()) {
            try {
                if (midStr.toLong() != 0L) return true
            } catch (_: NumberFormatException) {
                // Cookie 形态异常就当作未登录处理，不因为解析失败把请求发出去
            }
        }
        if (!notLoggedInWarned) {
            notLoggedInWarned = true
            Logu.e("进度上报", "跳过：未登录（mid=0），观看记录与续播进度无法写入")
        }
        return false
    }

    /** 真正把上报丢到线程池里。所有网络请求都必须在 [CenterThreadPool] 上跑（AGENTS.md 硬约定）。 */
    private fun sendProgressReport(progressSec: Long, reason: String) {
        val fAid = aid
        val fCid = cid
        val fEpid = epid
        val fSeasonId = seasonId
        val fSeasonType = seasonType
        Logu.d("进度上报", "$reason aid=$fAid cid=$fCid epid=$fEpid sid=$fSeasonId progress=${progressSec}s")
        CenterThreadPool.run {
            try {
                // 番剧必须两个维度都齐才走心跳接口：缺 seasonId 会发出 sid=0，服务端直接判参数错误
                if (fEpid != 0L && fSeasonId != 0L) {
                    HistoryApi.reportHistoryPgc(fAid, fCid, fEpid, fSeasonId, fSeasonType, progressSec)
                } else {
                    HistoryApi.reportHistory(fAid, fCid, progressSec)
                }
            } catch (e: Exception) {
                MsgUtil.err(UiText.of("进度上报："), e)
            }
        }
    }

    private fun onlineChange() {
        if (!SharedPreferencesUtil.getBoolean("player_show_online", false) || isLiveMode || aid == 0L || cid == 0L)
            return

        onlineTimer?.cancel()
        onlineTimer = null
        onlineTimer = Timer()
        val task = object : TimerTask() {
            @SuppressLint("SetTextI18n")
            override fun run() {
                if (playerCore != null) {
                    try {
                        online_number = VideoInfoApi.getWatching(aid, cid)
                        runOnUiThread {
                            if (online_number.isNotEmpty()) text_online.text = online_number + UiText.of("人在看")
                            else text_online.text = ""
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            MsgUtil.err(e)
                            text_online.visibility = View.GONE
                        }
                        this.cancel()
                    }
                }
            }
        }
        onlineTimer!!.schedule(task, 0, 5000)
    }

    private fun getSubtitle(subtitleUrl: String) {
        if (subtitleUrl.isEmpty()) return
        try {
            subtitles = if (isOnlineVideo) PlayerApi.getSubtitle(subtitleUrl)
            else PlayerApi.getSubtitle(File(subtitleUrl))

            if (subtitles == null) return

            subtitle_count = subtitles!!.size
            subtitle_curr_index = 0
            // 换了字幕轨必须复位展示去重标记：否则新轨道的第 0 条与旧轨道当时展示的下标相同
            // 时会被 showSubtitle 的 `subtitle_shown_index != subtitle_curr_index` 判定为"无需刷新"
            subtitle_shown_index = -2
            runOnUiThread { btn_subtitle.setImageResource(R.mipmap.subtitle_on) }
        } catch (e: Exception) {
            MsgUtil.err(e)
        }
    }

    private fun showSubtitle(currSec: Float) {
        val subs = subtitles
        if (subs == null || subtitle_count == 0) {
            if (subtitle_shown_index != -1) {
                subtitle_shown_index = -1
                text_subtitle.visibility = View.GONE
            }
            return
        }

        var subtitleCurr = subs[subtitle_curr_index]

        var needAdjust = true
        var needShow = true

        while (needAdjust) {
            if (currSec < subtitleCurr.from) {
                if (subtitle_curr_index != 0 && currSec < subs[subtitle_curr_index - 1].to) {
                    subtitle_curr_index--
                } else {
                    needAdjust = false
                    needShow = false
                }
            } else if (currSec > subtitleCurr.to) {
                if (subtitle_curr_index + 1 < subtitle_count && currSec > subs[subtitle_curr_index + 1].from) {
                    subtitle_curr_index++
                } else {
                    needAdjust = false
                    needShow = false
                }
            } else needAdjust = false
            subtitleCurr = subs[subtitle_curr_index]
        }

        if (needShow) {
            // 内容没变就不刷 UI，避免每 250ms 重复 setText/setVisibility
            if (subtitle_shown_index != subtitle_curr_index) {
                subtitle_shown_index = subtitle_curr_index
                text_subtitle.text = subs[subtitle_curr_index].content
                text_subtitle.visibility = View.VISIBLE
            }
        } else if (subtitle_shown_index != -1) {
            subtitle_shown_index = -1
            text_subtitle.visibility = View.GONE
        }
    }

    /**
     * 按设置刷新字幕字号。
     *
     * 钳制放在读取侧：设置页的 `input_int` 只校验「能解析成整数」，0、负数、999 都会原样落盘，
     * 直接交给 `setTextSize` 会让字幕消失或把布局撑爆。未设置时读 [SettingsKeys.PLAYER_SUBTITLE_SIZE_DEFAULT]，
     * 与 `activity_player.xml` 里的 11sp 一致。
     */
    private fun applySubtitleTextSize() {
        val size = PlayerDefaults.clampSubtitleSize(
            SharedPreferencesUtil.getInt(
                SettingsKeys.PLAYER_SUBTITLE_SIZE,
                SettingsKeys.PLAYER_SUBTITLE_SIZE_DEFAULT
            )
        )
        text_subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
    }

    /**
     * 「播放默认值 → 字幕」的入口：决定开播时怎么处理字幕。
     *
     * - 中文：自动选中文字幕（优先人工，其次 AI），**不弹**选择框；
     * - 自行选择：沿用既有「自动弹出字幕选择」开关（开才弹选择框）。
     *
     * 三个调用点原本都跑在 `CenterThreadPool` 后台线程里，这里不做线程切换。
     */
    private fun maybeAutoSubtitle() {
        val mode = SharedPreferencesUtil.getString(
            SettingsKeys.PLAYER_DEFAULT_SUBTITLE, SettingsKeys.PLAYER_DEFAULT_SUBTITLE_MANUAL
        )
        if (mode == SettingsKeys.PLAYER_DEFAULT_SUBTITLE_ZH) {
            downSubtitle(false, autoChinese = true)
        } else if (SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_SUBTITLE_AUTOSHOW, true)) {
            downSubtitle(false)
        }
    }

    /**
     * 拉取字幕列表并按需展示选择框。
     *
     * @param fromBtn 用户点了字幕按钮（true 时无字幕要提示；且忽略「允许仅AI字幕」限制）
     * @param autoChinese 「播放默认值 → 字幕 = 中文」：自动选中文字幕（优先人工，其次 AI），
     *   **不弹选择框**。此时不走下面那段选择框逻辑。
     */
    private fun downSubtitle(fromBtn: Boolean, autoChinese: Boolean = false) {
        try {
            if (subtitleLinks == null) {
                subtitleLinks = if (isOnlineVideo) PlayerApi.getSubtitleLinks(aid, cid)
                else PlayerApi.getSubtitleLinks(File(danmakuFile!!.parentFile, "subtitles"))
            }

            if (subtitleLinks!!.size == 1) {
                if (fromBtn) MsgUtil.showMsg(UiText.of("本视频无字幕"))
                return
            }

            subtitle_delta = SharedPreferencesUtil.getFloat("player_subtitle_delta", 0.3f)

            if (autoChinese) {
                val index = PlayerDefaults.pickChineseSubtitleIndex(
                    subtitleLinks!!.map { PlayerDefaults.SubtitleCandidate(it.lang, it.isAI) }
                )
                Logu.d("subtitle", "默认中文字幕自动选择：index=$index")
                if (index < 0) return
                subtitle_selected = index
                CenterThreadPool.run { getSubtitle(subtitleLinks!![index].url) }
                return
            }

            val aiNotOnly = (subtitleLinks!!.size > 2 || (subtitleLinks!!.size == 2 && !subtitleLinks!![0].isAI))
            val aiAllowed = (fromBtn || SharedPreferencesUtil.getBoolean("player_subtitle_ai_allowed", false))

            if (aiNotOnly || aiAllowed) {
                if (subtitle_selected == -1) subtitle_selected = subtitleLinks!!.size

                runOnUiThread {
                    val subtitleRecycler = findViewById<RecyclerView>(R.id.subtitle_list)
                    val adapter = SubtitleAdapter()
                    adapter.setData(subtitleLinks!!)
                    adapter.selectedItemIndex = subtitle_selected
                    adapter.setOnItemClickListener { index ->
                        layout_card_bg.visibility = View.GONE
                        card_subtitle.visibility = View.GONE
                        subtitle_selected = index

                        if (subtitleLinks!![index].id == -1L) {
                            subtitles = null
                            subtitle_shown_index = -1
                            btn_subtitle.setImageResource(R.mipmap.subtitle_off)
                        } else
                            CenterThreadPool.run { getSubtitle(subtitleLinks!![index].url) }
                    }
                    subtitleRecycler.layoutManager = CustomLinearManager(this, LinearLayoutManager.HORIZONTAL, false)
                    subtitleRecycler.setHasFixedSize(true)
                    subtitleRecycler.adapter = adapter
                    layout_card_bg.visibility = View.VISIBLE
                    card_subtitle.visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            MsgUtil.err(e)
        }
    }

    private fun downdanmu() {
        if (danmaku_url.isEmpty()) return

        val useNewApi = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NEW_DANMAKU_API, true)

        if (useNewApi) downdanmuNew()
        else downdanmuOld()
    }

    private fun downdanmuOld() {
        try {
            // use{} 保证 createNewFile()/sink() 抛异常提前跳出时连接也归还
            NetWorkUtil.get(danmaku_url, NetWorkUtil.webHeaders).use { response ->
                var bufferedSink: BufferedSink? = null
                try {
                    if (!danmakuFile!!.exists()) danmakuFile!!.createNewFile()
                    val sink = danmakuFile!!.sink()
                    val decompressBytes = NetWorkUtil.decompress(response.body!!.bytes())
                    bufferedSink = sink.buffer()
                    bufferedSink!!.write(decompressBytes)
                    bufferedSink!!.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    bufferedSink?.close()
                }
            }
            prepareDanmaku { it.loadFromXmlFile(danmakuFile!!.toString()) }
        } catch (e: Exception) {
            runOnUiThread { MsgUtil.err(e) }
        }
    }

    private fun downdanmuNew() {
        try {
            var estimatedDuration = 3600

            if (playerCore != null) {
                val duration = playerCore!!.duration
                if (duration > 0) estimatedDuration = (duration / 1000).toInt()
            }

            Logu.d("新版弹幕", "开始获取新版弹幕，aid=" + aid + ", cid=" + cid)

            val segments = DanmakuApi.getAllVideoDanmaku(aid, cid, estimatedDuration)

            if (segments.isEmpty()) {
                Logu.w("新版弹幕", "未获取到弹幕，尝试使用旧版接口")
                CenterThreadPool.run { downdanmuOld() }
                return
            }

            Logu.d("新版弹幕", "成功获取 " + segments.size + " 个弹幕分段")

            prepareDanmaku { it.loadFromProtobufSegments(segments) }
        } catch (e: Exception) {
            e.printStackTrace()
            Logu.e("新版弹幕", "获取失败: " + e.message + "，回退到旧版接口")
            runOnUiThread { MsgUtil.toast(UiText.of("新版弹幕获取失败，使用旧版接口")) }
            CenterThreadPool.run { downdanmuOld() }
        }
    }

    /**
     * 弹幕链路已统一到 [DanmakuManager]（方案 B：两个播放器共用一套弹幕栈）。
     * 保留同名公开方法只是为了不改动 `PlayerDanmuClientListener` 等既有调用方。
     */
    fun addDanmaku(text: String?, color: Int) = addDanmaku(text, color, 25, 1, 0)

    fun addDanmaku(text: String?, color: Int, textSize: Int, type: Int, backgroundColor: Int) {
        if (text == null) return
        danmakuManager?.addDanmaku(text, color, textSize, type, backgroundColor)
    }

    /** 重新绑定弹幕视图与弹幕管理器（弹幕视图在切分页/切互动分支后会重新取一次引用）。 */
    private fun bindDanmakuView() {
        mDanmakuView = findViewById(R.id.sv_danmaku)
        danmakuManager = DanmakuManager(mDanmakuView!!).also { manager ->
            // 弹幕时间轴跟随播放器位置（而不是墙钟）。**直播没有有意义的播放位置**，不绑。
            //
            // 两个 provider 都在 DFM 渲染线程上被每帧调用，只能读主线程采样好的内存值：
            // video_now 就是 progressChange 每 250ms 从 playerCore 读一次的那个 @Volatile 字段。
            if (!isLiveMode) {
                manager.bindMediaClock(
                    { if (isPrepared && !destroyed) video_now.toLong() else -1L },
                    { isPrepared && isPlaying && !destroyed }
                )
            }
        }
    }

    /** 释放弹幕视图与管理器。 */
    private fun releaseDanmaku() {
        danmakuManager?.release()
        danmakuManager = null
        mDanmakuView = null
    }

    /**
     * 加载弹幕前的统一入口：先重新读一遍弹幕设置，再执行具体的加载。
     * 与原实现"每次 streamDanmaku 都重建 DanmakuContext"的语义一致（设置改动即时生效）。
     */
    private fun prepareDanmaku(block: (DanmakuManager) -> Unit) {
        val manager = danmakuManager
        if (manager == null) {
            // 静默失败会让"弹幕不显示"完全无从排查，这里必须留下痕迹
            Logu.e("弹幕", "danmakuManager 为空，弹幕未加载")
            return
        }
        manager.init()
        block(manager)
    }

    fun controlVideo() {
        // 上一次播放器报错后，用户按播放键的真实意图是"再试一次"，而不是"从暂停处继续"。
        // 不加这个分支的话，playerError 状态下只会对一个已经停住的播放器调 resume，界面毫无反应。
        if (playerError) {
            retryAfterPlayerError()
            autohideReset()
            return
        }
        if (isPlaying) {
            playerPause()
        } else {
            if (video_now >= video_all - 250) {
                if (interactionData != null && interactionData!!.edges != null &&
                    interactionData!!.edges!!.questions != null && !questionShown) {
                    if (!questionShown) {
                        markSeekTarget(0L)
                        playerCore!!.seekTo(0)
                        if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.seekTo(0L)
                        Logu.v("播完重播")
                    }
                } else {
                    markSeekTarget(0L)
                    playerCore!!.seekTo(0)
                    if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.seekTo(0L)
                    Logu.v("播完重播")
                }
            }
            playerResume()
        }
        autohideReset()
    }

    /**
     * 播放器报错后重新载入当前视频。
     * 之所以重建整个 IjkMediaPlayer 实例，而不是简单地 seekTo(0) + start()：IJK 出错后
     * （解码失败 / 加载失败）自身状态机已不可信，对同一实例重试往往立刻再次进入 onError。
     *
     * @param resumePositionMs 重建后从哪继续；默认取主线程维护的 [video_now]（点播续播）。
     *   直播换线路时传 0——直播要的是"最新的流位置"，不是换线路前的那个位置。
     */
    /**
     * 换下一个 CDN 候选（主地址失败时按优先级换源）。
     *
     * 只对在线点播生效：直播有自己的 switchLiveCandidate，本地的备份表永远是空的。
     * 换源只改视频地址，不动取流结果——重建播放器时会重新走 MPPrepare。
     *
     * @return true 表示已经换到新地址，false 表示没有下一个候选（按原地址重试）
     */
    private fun rotateVideoBackupUrl(): Boolean {
        if (!isOnlineVideo || isLiveMode) return false
        val nextIndex = videoBackupIndex + 1
        if (nextIndex >= videoBackupUrls.size) return false
        val nextUrl = videoBackupUrls[nextIndex]
        if (nextUrl.isEmpty()) return false
        videoBackupIndex = nextIndex
        Logu.w("CDN候选", "换源重试：$video_url -> $nextUrl")
        video_url = nextUrl
        // 音轨候选与视频候选是两套列表，长度不一定一致，越界就不动音轨（重建时用主地址）
        if (nextIndex < audioBackupUrls.size && audioBackupUrls[nextIndex].isNotEmpty()) {
            audioTrackUrl = audioBackupUrls[nextIndex]
        }
        return true
    }

    private fun retryAfterPlayerError(resumePositionMs: Long = video_now.toLong()) {
        playerError = false
        CenterThreadPool.run {
            // 与 toggleAudioOnlyMode 一致：重建用的续播位置由调用方给定（点播默认取主线程维护的 video_now），
            // 不在这里调 playerCore.currentPosition（后台线程碰 jni 且此刻播放器正要被释放）。
            val resumePosition = resumePositionMs
            runOnUiThread {
                if (destroyed || isFinishing()) return@runOnUiThread
                // 顺序：先摘状态标志，再 release，最后置空引用（理由同 toggleAudioOnlyMode）
                isPrepared = false
                isPlaying = false
                releaseAudioTrack()
                // 先换 CDN 再重建：同一个地址连续失败（被限速/被墙/单个边缘节点坏掉）时
                // 原样重试通常还是失败，换成备份地址往往一次就好。
                rotateVideoBackupUrl()
                if (playerCore != null) {
                    playerCore!!.stop()
                    playerCore!!.release()
                    playerCore = null
                }
                loading_info.visibility = View.VISIBLE
                anim_loading!!.start()
                loading_text0.text = UiText.of("重新载入")
            }
            try {
                Thread.sleep(100)
                runOnUiThread {
                    // 这 100ms 里用户可能已经退出页面：onDestroy 会 release 并置空 playerCore，
                    // 此处若无守卫就会重建出一个永不释放的 native 播放器。
                    if (destroyed || isFinishing()) return@runOnUiThread
                    // 重试同样可能撞上原生库不可用（例如加载过一次成功、之后库文件被系统回收），
                    // 因此和 onCreate 一样走 helper：失败时它已提示并 finish，这里只收掉加载动画。
                    val player = createPlayerOrBail()
                    if (player == null) {
                        loading_info.visibility = View.GONE
                        anim_loading!!.stop()
                        return@runOnUiThread
                    }
                    playerCore = player
                    progress_history = resumePosition
                    setDisplay()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (destroyed) return@runOnUiThread
                    MsgUtil.showMsg(UiText.of("重新载入失败，请重试"))
                    loading_info.visibility = View.GONE
                    anim_loading!!.stop()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    fun changeVolume(addOrCut: Boolean) {
        // 短按音量键仍按老行为调系统媒体音量 ±1 档：手表上单手握持时短按最顺手，
        // 改成 1% 步进会出现「按十下才动一格」的体感倒退；细调走长按弹出的无极调音滑条。
        // 唯一要兜的坑：增益被拉到 0% 时系统音量再大也没声，用户会以为播放器坏了 ——
        // 所以短按「音量+」顺手把软件增益救回默认 100%（与手机上「音量+ 解除静音」的直觉一致）。
        if (addOrCut && PlayerVolumePolicy.isMuted(volumeGainPercent)) {
            volumeGainPercent = PlayerVolumePolicy.DEFAULT_PERCENT
            persistVolumeGain()
            applyVolumeGain()
            syncVolumePanel()
        }
        var volumeNow = audioManager!!.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volumeMax = audioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumeNew = volumeNow + (if (addOrCut) 1 else -1)
        if (volumeNew in 0..volumeMax) {
            audioManager!!.setStreamVolume(AudioManager.STREAM_MUSIC, volumeNew, 0)
            volumeNow = volumeNew
        }
        val show = (volumeNow.toFloat() / volumeMax.toFloat() * 100).toInt()

        text_volume.visibility = View.VISIBLE
        text_volume.text = UiText.fmt(UiText.of("音量：%1\$s%%"), show)

        text_volume.removeCallbacks(hideVolume)
        text_volume.postDelayed(hideVolume, 3000)
        autohideReset()
    }

    private val hideVolume = Runnable { text_volume.visibility = View.GONE }

    // ==================== 无极调音（软件增益 0~100%） ====================

    /**
     * 读存档、接线滑条，只在 onCreate 调一次。
     *
     * 存档值来自 SharedPreferences，旧版本/被手改过都可能是脏值，统一走
     * [PlayerVolumePolicy.sanitize] 钳到 0~100 再用于播放。
     */
    private fun initVolumePanel() {
        volumeGainPercent = PlayerVolumePolicy.sanitize(
            SharedPreferencesUtil.getInt(
                PlayerVolumePolicy.KEY_GAIN_PERCENT,
                PlayerVolumePolicy.DEFAULT_PERCENT
            )
        )
        text_volume_title.text = UiText.of("无极调音")
        seekbar_volume.max = PlayerVolumePolicy.MAX_PERCENT
        syncVolumePanel()

        seekbar_volume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                volumeGainPercent = PlayerVolumePolicy.clampPercent(progress)
                text_newvolume.text = volumePercentText(volumeGainPercent)
                applyVolumeGain()
                // 拖动过程中不落盘（1% 一档会连写几十次存储），只把自动收起往后推
                layout_volume.removeCallbacks(hideVolumePanel)
                layout_volume.postDelayed(hideVolumePanel, VOLUME_PANEL_AUTO_HIDE_MS)
                // 同步把控制栏的自动隐藏计时推后：否则控制栏 4s 一到 hidecon 会连面板一起收掉，
                // 正拖着滑条时面板突然消失。
                autohideReset()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                layout_volume.removeCallbacks(hideVolumePanel)
                autohideReset()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                persistVolumeGain()
                layout_volume.removeCallbacks(hideVolumePanel)
                layout_volume.postDelayed(hideVolumePanel, VOLUME_PANEL_HIDE_AFTER_TRACK_MS)
            }
        })

        // 点面板空白处关闭（与 layout_speed 同款交互），关闭时把当前百分比落盘。
        layout_volume.setOnClickListener { hideVolumePanel.run() }
        applyVolumeGain()
    }

    /** 面板上的百分比文案。先内联，等文案统一进 strings.xml 后再换资源。 */
    private fun volumePercentText(percent: Int): CharSequence =
        UiText.fmt(UiText.of("%1\$s%%"), percent)

    /** 把 [volumeGainPercent] 同步到滑条/文字（打开面板、静音救回、初始化都走这里）。 */
    private fun syncVolumePanel() {
        if (!::seekbar_volume.isInitialized) return
        seekbar_volume.progress = volumeGainPercent
        text_newvolume.text = volumePercentText(volumeGainPercent)
    }

    /**
     * 把百分比增益同时打到两路音频上。
     *
     * ijk 的 `setVolume` 是解码后的采样级线性乘算（0.0~1.0），与系统媒体音量是两层：
     * 系统音量决定整机声音大小，这里决定播放器自己的输出电平，最终是两者相乘。它只对**当前播放器实例**
     * 生效：换清晰度/切 P/出错重试都会重建 IjkMediaPlayer（见 [retryAfterPlayerError]），所以
     * onPrepared 里必须再调一次（见 [onPrepared] 末尾）。
     * 本地 DASH 视频还会额外起一条 `android.media.MediaPlayer` 外部音轨（见 [initAudioTrack]），
     * 那条也要一起调，否则会出现「视频有声、音轨没声」的一半静音。
     */
    private fun applyVolumeGain() {
        val gain = PlayerVolumePolicy.percentToGain(volumeGainPercent)
        try {
            playerCore?.setVolume(gain, gain)
        } catch (e: Exception) {
            Logu.e("无极调音", "设置播放器增益失败：${e.message}")
        }
        try {
            audioPlayer?.setVolume(gain, gain)
        } catch (e: Exception) {
            Logu.e("无极调音", "设置外部音轨增益失败：${e.message}")
        }
    }

    /** 落盘当前百分比。拖动过程中不写，松手/关闭面板时各写一次。 */
    private fun persistVolumeGain() {
        SharedPreferencesUtil.putInt(PlayerVolumePolicy.KEY_GAIN_PERCENT, volumeGainPercent)
    }

    /**
     * 长按音量 +/- 打开无极调音滑条。
     *
     * 短按仍调系统音量（见 [changeVolume]）。面板不会常驻：4s 无操作或松手 1.2s 后收起，
     * 手表屏幕小，弹层挡画面。
     */
    private fun showVolumePanel() {
        if (destroyed || !::layout_volume.isInitialized) return
        syncVolumePanel()
        layout_volume.visibility = View.VISIBLE
        layout_volume.removeCallbacks(hideVolumePanel)
        layout_volume.postDelayed(hideVolumePanel, VOLUME_PANEL_AUTO_HIDE_MS)
        autohideReset()
    }

    /** 收起面板并落盘。用 `layout_volume` 这个 View 投递，onDestroy 才可以用同一个 View 移除。 */
    private val hideVolumePanel = Runnable { hideVolumePanelNow() }

    private fun hideVolumePanelNow() {
        if (!::layout_volume.isInitialized) return
        layout_volume.removeCallbacks(hideVolumePanel)
        layout_volume.visibility = View.GONE
        persistVolumeGain()
    }

    /** 无操作自动收起时长（毫秒）。 */
    private val VOLUME_PANEL_AUTO_HIDE_MS = 4_000L

    /** 松手后多久收起（毫秒）：留一点时间让用户看到调完的百分比。 */
    private val VOLUME_PANEL_HIDE_AFTER_TRACK_MS = 1_200L

    private fun softwareRotate() {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        screen_width = if (screen_landscape) displayMetrics.heightPixels else displayMetrics.widthPixels
        screen_height = if (screen_landscape) displayMetrics.widthPixels else displayMetrics.heightPixels

        val rootLayout = findViewById<ViewGroup>(R.id.root_layout)
        val params = rootLayout.layoutParams
        params.width = screen_width
        params.height = screen_height

        if (isPrepared && !destroyed)
            runOnUiThread {
                rootLayout.layoutParams = params
                rootLayout.pivotX = 0f
                rootLayout.pivotY = 0f
                rootLayout.x = if (screen_landscape) screen_height.toFloat() else 0f
                rootLayout.rotation = if (screen_landscape) 90f else 0f
                if (SharedPreferencesUtil.getBoolean("player_display", Build.VERSION.SDK_INT < 26)) {
                    if (textureView != null) {
                        val matrix = Matrix()
                        textureView!!.getTransform(matrix)
                        matrix.postRotate(0f)
                        textureView!!.setTransform(matrix)
                    }
                } else {
                    MsgUtil.showMsg(UiText.of("请切换为TextureView才能支持软件旋屏！"))
                }
            }
        changeVideoSize()
    }

    /** 画面当前是否处于缩放状态。判据在 [PlayerZoomPolicy]，不要改读手势标志。 */
    private fun isVideoZoomed(): Boolean =
        PlayerZoomPolicy.isZoomed(layout_video.scaleX, layout_video.scaleY)

    private fun videoMoveBy(dx: Float, dy: Float) {
        var x = dx + layout_video.x
        var y = dy + layout_video.y

        val widthDelta = 0.5f * video_width * (layout_video.scaleX - 1f)
        val heightDelta = 0.5f * video_height * (layout_video.scaleY - 1f)
        val videoXMin = video_origX - widthDelta
        val videoXMax = video_origX + widthDelta
        val videoYMin = video_origY - heightDelta
        val videoYMax = video_origY + heightDelta

        if (x < videoXMin) x = videoXMin
        if (x > videoXMax) x = videoXMax
        if (y < videoYMin) y = videoYMin
        if (y > videoYMax) y = videoYMax

        if (layout_video.x != x || layout_video.y != y) {
            layout_video.x = x
            layout_video.y = y
            if (!gesture_moved && (abs(video_origX - x) > 5f || abs(video_origY - y) > 5f)) {
                gesture_moved = true
            }
        }
    }

    private fun playerPause() {
        isPlaying = false
        if (playerCore != null && isPrepared) {
            playerCore!!.pause()
            if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.pause()
        }
        audioPlayer?.pause()
        btn_control.setImageResource(R.drawable.btn_player_play)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
            updateMediaSessionPlaybackState()
        }
    }

    private fun playerResume() {
        isPlaying = true
        if (playerCore != null && isPrepared) {
            playerCore!!.start()
            if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.resume()
        }
        audioPlayer?.start()
        btn_control.setImageResource(R.drawable.btn_player_pause)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
            updateMediaSessionPlaybackState()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Logu.v("开始旋转屏幕")

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        screen_width = displayMetrics.widthPixels
        screen_height = displayMetrics.heightPixels
        changeVideoSize()

        Logu.v("旋转屏幕结束")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Logu.v("onNewIntent")
        finish()
    }

    //======= 后台播放服务（PlaybackService）状态桥 =======
    // 播放器实例与全部播放状态仍只归本页持有，服务只是"保活 + 通知栏遥控"的壳。
    // 下面这些方法会被服务每秒的定时线程调用，所以只允许读内存字段，
    // 绝不能在服务线程里做 playerCore.currentPosition 这类 JNI 调用（会取原生锁）。
    // 需要动手作的一律 runOnUiThread 回主线程。

    fun serviceGone(): Boolean = destroyed

    fun serviceIsPlaying(): Boolean = isPlaying && isPrepared && !destroyed

    fun serviceTitle(): String = videoTitle ?: ""

    fun servicePositionMs(): Int = video_now

    fun serviceDurationMs(): Int = video_all

    fun serviceTogglePlay() {
        runOnUiThread { if (!destroyed && !isFinishing) controlVideo() }
    }

    fun serviceStopPlayback() {
        runOnUiThread { if (!isFinishing) finish() }
    }

    fun serviceReportNow() {
        runOnUiThread { reportProgressNow(true, UiText.of("后台服务兜底")) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // 只存原始类型。存的是"播放到哪了"这个瞬时状态——官方 saving-states 指南里
        // 瞬时状态就该走 onSaveInstanceState（大对象才需要 ViewModel/持久化）。
        super.onSaveInstanceState(outState)
        if (!isLiveMode && isPrepared) outState.putInt(STATE_POSITION, video_now)
    }

    override fun onPause() {
        super.onPause()
        Logu.v("onPause")
        // 退到后台/被别的页面盖住时补报一次：正常退出走 finish() 已经报过同一秒，这里会被去重拦掉
        reportProgressNow(false, UiText.of("切后台"))
        // 本地视频的续播位置只存在磁盘上，切后台是"可能回不来"的第一个时点，先落一次
        saveLocalProgressNow("切后台")
        if (!SharedPreferencesUtil.getBoolean("player_background", false)) {
            playerPause()
        } else if (!isFinishing && isPrepared && !finishWatching && isPlaying) {
            // 开了"后台/熄屏继续播放"：交给前台服务保活，同时挂出通知栏遥控。
            // 起前台服务放在 onPause 是刻意的——此时应用还有可见界面，落在 Android 12
            // 后台启动限制的豁免范围内；退到完全后台后再起会被平台直接拒绝。
            // 暂停态不必起服务：没有声音要保活，通知也无事可报；若起了，onResume 会立刻
            // PlaybackService.stop 掉，等于白起白停一次（还闪一下通知）。
            PlaybackService.start(this, this)
        }
    }

    override fun onResume() {
        super.onResume()
        Logu.v("onResume")
        // 回到前台后通知栏遥控没有存在意义，服务持有的弱引用也不再需要
        PlaybackService.stop(this)
        // 设置页里改完「字幕字号大小」返回播放页立即生效。
        // findview() 在异步 inflate 回调里才跑，onResume 可能先到，必须判初始化。
        if (::text_subtitle.isInitialized) applySubtitleTextSize()
    }

    override fun onStop() {
        super.onStop()
        Logu.v("onStop")
        // onPause 之后位置不会再走，这里基本都会被去重拦掉；留一路是防 onPause 那条路径没走成
        reportProgressNow(false, "onStop")
        saveLocalProgressNow("onStop")
    }

    override fun onDestroy() {
        // 无条件清理：系统因内存压力/"不保留活动"销毁本页时 isFinishing == false，
        // 旧代码在此提前 return 会跳过 playerCore/Timer/WebSocket/EventBus 的全部释放，
        // 泄漏的回调还会继续操作已销毁的 Activity。用 destroyed 标志防止重复释放。
        if (destroyed) {
            super.onDestroy()
            return
        }

        Logu.v("结束")
        if (eventBusInit) {
            EventBus.getDefault().unregister(this)
            eventBusInit = false
        }
        // 销毁前的兜底上报：必须在 destroyed = true 与 release 播放器之前做，
        // 且只依赖内存里的 video_now（不碰 playerCore 的 JNI 调用）。正常退出时会被去重拦掉。
        reportProgressNow(true, UiText.of("销毁兜底"))
        saveLocalProgressNow("销毁兜底")
        destroyed = true
        // 锁定按钮自动隐去的延时任务要撤掉，别在 Activity 销毁后再去动 View
        mainHandler?.removeCallbacks(hideLockIcon)

        // 无论哪种退出原因（用户返回、被系统回收、后台通知里点"关闭"），
        // 后台播放服务与它的常驻通知都不能留存。
        PlaybackService.stop(this)

        cancelAllTimers()

        // 先解绑 surface 回调，避免解绑前 surface 回调又反过来操作已 release 的播放器
        mainHandler?.removeCallbacks(unifiedHealthCheck)
        surfaceBinder?.release()
        surfaceBinder = null

        releaseDanmaku()
        playerCore?.release()
        playerCore = null
        releaseAudioTrack()

        if (isOnlineVideo && danmakuFile != null && danmakuFile!!.exists())
            danmakuFile!!.delete()

        liveWebSocket?.close(1000, "")
        liveWebSocket = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
            mediaSession!!.release()
            mediaSession = null
        }

        // 退出播放器时把方向锁回设置里的默认值。**必须包 try/catch**：
        // 「Only fullscreen activities can request orientation」是 Android 8.0 的已知行为——
        // 页面不在全屏（分屏 / 悬浮窗 / 某些 ROM 的窗口化）时 setRequestedOrientation 抛 ISE，
        // 而这里是 onDestroy，异常会升级成 "Unable to destroy activity" 直接崩在退出路径上。
        // 线上实测 4 例，全部是 sdk 26 的 HONOR HWDUK。
        try {
            requestedOrientation = if (SharedPreferencesUtil.getBoolean("ui_landscape", false))
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } catch (ignored: IllegalStateException) {
            Logu.w("退出时恢复屏幕方向失败（非全屏窗口），忽略")
        }

        super.onDestroy()
    }

    private fun cancelAllTimers() {
        speedTimer?.cancel()
        speedTimer = null
        onlineTimer?.cancel()
        onlineTimer = null
        progressRunnable?.let { mainHandler?.removeCallbacks(it) }
        progressRunnable = null
        loadingRunnable?.let { mainHandler?.removeCallbacks(it) }
        loadingRunnable = null
        // 用同一个 View 移除：runnable 是 layout_video.postDelayed 投递的，
        // 若当时未 attach 会进 per-view RunQueue，用别的 View 移除会失效
        resizePostRunnable?.let { layout_video.removeCallbacks(it) }
        resizePostRunnable = null
        mainHandler?.removeCallbacksAndMessages(null)
        layout_control.removeCallbacks(hidecon)
        text_volume.removeCallbacks(hideVolume)
        // 无极调音面板的自动收起：runnable 是 layout_volume.postDelayed 投递的，必须用同一个 View 移除
        if (::layout_volume.isInitialized) layout_volume.removeCallbacks(hideVolumePanel)
        seekbar_progress.removeCallbacks(progressbarEnable)
    }

    private fun danmuSocketConnect() {
        CenterThreadPool.run {
            try {
                var url = "https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo?type=0&id=" + aid
                val mHeaders = ArrayList<String>().apply {
                    add("Cookie")
                    add(SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))
                    add("Referer")
                    add("https://live.bilibili.com/" + aid)
                    add("Origin")
                    add("https://live.bilibili.com")
                    add("User-Agent")
                    add(NetWorkUtil.USER_AGENT_WEB)
                }
                val response = NetWorkUtil.get(ConfInfoApi.signWBI(url), mHeaders)
                val data = JSONObject(response.body!!.string()).getJSONObject("data")
                val hostInfo = data.getJSONArray("host_list").getJSONObject(0)

                // 审计 P2-6：host_list 由服务端下发，不校验就等于把「携带 Cookie 的握手」送去
                // 任意域名（接口响应被劫持/中间人改包即可实现）。只接受 bilibili.com 及其子域，
                // 端口也做一次范围检查；不通过就提示失败并放弃，不建立连接。
                val wssHost = hostInfo.optString("host", "")
                val wssPort = hostInfo.optInt("wss_port", 0)
                if (!CookieDomainPolicy.isBilibiliDomain(wssHost) || wssPort !in 1..65535) {
                    Logu.e("danmuSocket", "弹幕服务器地址非法，已放弃连接：$wssHost:$wssPort")
                    MsgUtil.showMsg(UiText.of("直播弹幕连接失败"))
                    return@run
                }

                url = "wss://" + wssHost + ":" + wssPort + "/sub"
                Logu.v("连接WebSocket", url)

                okHttpClient = OkHttpClient()
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("Origin", "https://live.bilibili.com")
                    .header("User-Agent", NetWorkUtil.USER_AGENT_WEB)
                // 审计 P2-6：握手只需要「我是谁」，不需要写权限。原来把整串 Cookie（含 bili_jct）
                // 发给弹幕服务器，对方若是伪造域就等于交出 csrf；这里只带弹幕必需的 SESSDATA，
                // 取不到就不带 Cookie 头（游客态也能收弹幕，发弹幕本来另有接口）。
                val sessdata = NetWorkUtil.getInfoFromCookie(
                    "SESSDATA",
                    SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")
                )
                if (sessdata.isNotEmpty()) requestBuilder.header("Cookie", "SESSDATA=$sessdata")
                val request = requestBuilder.build()

                val listener = PlayerDanmuClientListener()
                listener.mid = mid
                listener.roomid = aid
                listener.key = data.getString("token")
                listener.playerActivity = this

                liveWebSocket = okHttpClient!!.newWebSocket(request, listener)
            } catch (e: Exception) {
                MsgUtil.showMsg(UiText.of("直播弹幕连接失败"))
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("WrongConstant")
    private fun initMediaSession() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        mediaSession = MediaSession(this, "BiliClientPlayer")
        mediaSession!!.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mediaSession!!.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                super.onPlay()
                runOnUiThread {
                    if (!isPlaying) {
                        playerResume()
                        updateMediaSessionPlaybackState()
                    }
                }
            }

            override fun onPause() {
                super.onPause()
                runOnUiThread {
                    if (isPlaying) {
                        playerPause()
                        updateMediaSessionPlaybackState()
                    }
                }
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                runOnUiThread {
                    if (hasMultiplePages() && currentPageIndex < pagenames!!.size - 1) {
                        switchToPage(currentPageIndex + 1)
                    }
                }
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                runOnUiThread {
                    if (hasMultiplePages() && currentPageIndex > 0) {
                        switchToPage(currentPageIndex - 1)
                    }
                }
            }

            override fun onSeekTo(pos: Long) {
                super.onSeekTo(pos)
                runOnUiThread {
                    seekToPosition(pos)
                    updateMediaSessionPlaybackState()
                }
            }
        })
        mediaSession!!.isActive = true
    }

    private fun updateMediaSessionMetadata() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || mediaSession == null) return
        val metadataBuilder = MediaMetadata.Builder()
        if (videoTitle != null) metadataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, videoTitle)
        if (video_all > 0) metadataBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, video_all.toLong())
        mediaSession!!.setMetadata(metadataBuilder.build())
    }

    private fun updateMediaSessionPlaybackState() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || mediaSession == null) return
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        // 位置取 video_now（progressChange 每 250ms 刷新）。本方法在播放中每秒都会被调用一次，
        // 用 playerCore.currentPosition 等于每秒在主线程上摸一次 native 锁
        val position = if (isPrepared) video_now.toLong() else 0L
        var actions = (PlaybackState.ACTION_PLAY.toLong()
                or PlaybackState.ACTION_PAUSE.toLong()
                or PlaybackState.ACTION_SEEK_TO.toLong()
                or PlaybackState.ACTION_SKIP_TO_NEXT.toLong()
                or PlaybackState.ACTION_SKIP_TO_PREVIOUS.toLong())
        if (!hasMultiplePages() || currentPageIndex >= pagenames!!.size - 1) {
            actions = actions and PlaybackState.ACTION_SKIP_TO_NEXT.inv()
        }
        if (!hasMultiplePages() || currentPageIndex <= 0) {
            actions = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS.inv()
        }
        val stateBuilder = PlaybackState.Builder()
            .setState(state, position, 1.0f)
            .setActions(actions)
        mediaSession!!.setPlaybackState(stateBuilder.build())
    }

    private fun initUI() {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        screen_width = displayMetrics.widthPixels
        screen_height = displayMetrics.heightPixels

        if (SharedPreferencesUtil.getBoolean("player_ui_showRotateBtn", true))
            btn_rotate.visibility = View.VISIBLE
        else btn_rotate.visibility = View.GONE

        screen_round = SharedPreferencesUtil.getBoolean("player_ui_round", false)
        if (screen_round) {
            val padding = (screen_width * 0.03).toInt()

            val progressParams = seekbar_progress.layoutParams as LinearLayout.LayoutParams
            progressParams.leftMargin = padding * 4
            progressParams.rightMargin = padding * 4
            seekbar_progress.layoutParams = progressParams

            text_online.setPadding(0, 0, padding * 3, 0)
            text_progress.setPadding(padding * 3, 0, 0, 0)

            bottom_buttons.setPadding(padding, 0, padding, padding)

            right_control.setPadding(0, 0, padding, 0)

            val danmakuParams = mDanmakuView!!.layoutParams as RelativeLayout.LayoutParams
            danmakuParams.setMargins(0, padding * 3, 0, padding * 3)
            mDanmakuView!!.layoutParams = danmakuParams

            text_subtitle.maxWidth = (screen_width * 0.65).toInt()

            layout_top.setPadding(padding * 7, padding, padding * 7, 0)

            val clockLayout = findViewById<LinearLayout>(R.id.clock_layout)
            clockLayout.orientation = LinearLayout.HORIZONTAL
            val clockLayoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            clockLayoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
            clockLayout.layoutParams = clockLayoutParams

            val titleParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            titleParams.addRule(RelativeLayout.BELOW, R.id.clock_layout)
            titleParams.topMargin = padding / 2
            text_title!!.layoutParams = titleParams
            text_title!!.gravity = Gravity.CENTER

            val textClock = findViewById<TextView>(R.id.clock)
            val textClockParams = textClock.layoutParams as LinearLayout.LayoutParams
            textClockParams.leftMargin = padding / 2
            textClockParams.topMargin = padding / 4
            textClock.layoutParams = textClockParams
        }

        if (!SharedPreferencesUtil.getBoolean("player_show_online", false) || aid == 0L || cid == 0L)
            text_online.visibility = View.GONE

        layout_top.setOnClickListener { finish() }

        val params = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        val binder = PlayerSurfaceBinder(
            onReadyForPrepare = { target ->
                // surface 就绪且正在等开播：挂载后立即 prepare
                attachSurface(target)
                video_url?.let { MPPrepare(it) }
            },
            onSurfaceReattached = { target ->
                // surface 重建（例如退到后台再回来）：播放器已经 prepare 过，只重新挂上，
                // 绝不能重复 prepare。SurfaceView 还需要 seek 回当前位置强制出画面（与原实现一致）。
                attachSurface(target)
                if (isPrepared && !isLiveMode && target is SurfaceTarget.Holder) {
                    // seek 目标用主线程维护的 video_now（播放状态真值），不用 seekbar_progress.progress：
                    // 直播下进度条恒为 0（progressChange 只在非直播写它），拖拽/缓冲期间它也可能与真实位置不一致。
                    playerCore?.seekTo(video_now.toLong())
                }
            },
            onSurfaceLost = { detachSurface() }
        )
        surfaceBinder = binder
        if (SharedPreferencesUtil.getBoolean("player_display", Build.VERSION.SDK_INT < 26)) {
            textureView = TextureView(this)
            binder.bindTextureView(textureView!!)
            layout_video.addView(textureView, params)
        } else {
            surfaceView = SurfaceView(this)
            binder.bindSurfaceView(surfaceView!!)
            layout_video.addView(surfaceView, params)
        }

        btn_rotate.setOnClickListener {
            Logu.v("点击旋转按钮")
            screen_landscape = !screen_landscape
            if (SharedPreferencesUtil.getBoolean("dev_player_rotate_software", false)) softwareRotate()
            else requestedOrientation = if (screen_landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        findViewById<View>(R.id.button_sound_add).setOnClickListener { changeVolume(true) }
        findViewById<View>(R.id.button_sound_cut).setOnClickListener { changeVolume(false) }
        // 长按任一个音量键 → 打开「无极调音」滑条（0~100% 软件增益，1% 一档，松手落盘）。
        // 短按保留系统音量 ±1 档的老手感，长按才进细调；两个键都能开，不用记方向。
        findViewById<View>(R.id.button_sound_add).setOnLongClickListener {
            showVolumePanel()
            true
        }
        findViewById<View>(R.id.button_sound_cut).setOnLongClickListener {
            showVolumePanel()
            true
        }

        btn_menu.setOnClickListener {
            if (menu_opened) {
                right_second.visibility = View.GONE
                btn_menu.setImageResource(R.mipmap.morehide)
            } else {
                right_second.visibility = View.VISIBLE
                btn_menu.setImageResource(R.mipmap.moreshow)
            }
            menu_opened = !menu_opened
        }

        layout_card_bg.setOnClickListener {
            layout_card_bg.visibility = View.GONE
            card_subtitle.visibility = View.GONE
            card_danmaku_send.visibility = View.GONE
            card_page_selector.visibility = View.GONE
            card_quality_selector.visibility = View.GONE
            card_viewpoint_selector.visibility = View.GONE
        }
        btn_danmaku_send.setOnClickListener {
            layout_card_bg.visibility = View.VISIBLE
            card_danmaku_send.visibility = View.VISIBLE
        }
        findViewById<View>(R.id.danmaku_send).setOnClickListener {
            val editText = findViewById<EditText>(R.id.danmaku_send_edit)
            if (editText.text.toString().isEmpty()) {
                MsgUtil.showMsg(UiText.of("不能发送空弹幕喵"))
            } else {
                layout_card_bg.visibility = View.GONE
                card_danmaku_send.visibility = View.GONE

                CenterThreadPool.run {
                    try {
                        MsgUtil.showMsg(UiText.of("正在发送~"))

                        val result = DanmakuApi.sendVideoDanmakuByAid(cid, editText.text.toString(), aid,
                            video_now.toLong(), ToolsUtil.getRgb888(Color.WHITE), 1)

                        if (result == 0) {
                            MsgUtil.showMsg(UiText.of("发送成功喵~"))
                            runOnUiThread {
                                addDanmaku(editText.text.toString(), Color.WHITE)
                                editText.setText("")
                            }
                        } else
                            MsgUtil.showMsg(UiText.fmt(UiText.of("发送失败：%1\$s"), result))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        MsgUtil.err(e)
                    }
                }
            }
        }
    }

    private fun initSeekbars() {
        seekbar_progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            @SuppressLint("SetTextI18n")
            override fun onProgressChanged(seekBar: SeekBar, position: Int, fromUser: Boolean) {
                // SeekBar 回调本身就在主线程，无需再 runOnUiThread
                if (!isLiveMode) text_progress.text = StringUtil.toTime(position / 1000) + "/" + progress_str
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isSeeking = false
                if (isPrepared && !destroyed) {
                    // 统一走 seekToVideo：它同时 seek 视频、弹幕、外部音频轨，并压住
                    // progressChange 的偏差校正。原来这里只 seek 了视频（和弹幕），DASH 双文件
                    // 播放时 audioPlayer 仍停在旧位置，随后被 800ms 偏差校正再 seek 一次 ——
                    // 用户听到的就是"松手后声音停半秒到一秒"。
                    seekToVideo(seekbar_progress.progress.toLong())
                    autohideReset()
                }
            }
        })

        seekbar_speed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, position: Int, fromUser: Boolean) {
                if (fromUser) {
                    text_newspeed.text = speed_strs[position]
                    text_speed.text = speed_strs[position]
                    applyPlayerSpeed(speed_values[position])
                    danmakuManager?.setSpeed(speed_values[position])
                    applyAudioSpeed(speed_values[position])
                    // 「播放默认值 → 倍速 = 沿用上次」的记录位：只有用户自己调过才算"上次用过的"。
                    SharedPreferencesUtil.putFloat(SettingsKeys.PLAYER_LAST_SPEED, speed_values[position])
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                speedTimer?.cancel()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                speedTimer = Timer()
                val timerTask = object : TimerTask() {
                    override fun run() {
                        runOnUiThread { layout_speed.visibility = View.GONE }
                    }
                }
                speedTimer!!.schedule(timerTask, 200)
            }
        })
    }

    /**
     * 兜住平台「回车推进焦点」自己抛的 ISE（线上崩溃 #66，成因见 [EditorEnterKeyGuard]）。
     *
     * 播放页**不继承 BaseActivity**（直接 extends Activity，见类声明），所以基类那层
     * dispatchKeyEvent 兜底覆盖不到它；而播放页恰好有单行输入框 `danmaku_send_edit`
     * （`activity_player.xml`，singleLine + 无 OnEditorActionListener），正是平台那条
     * `shouldAdvanceFocusOnEnter()` 为真的形态，必须单独兜一次。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return try {
            super.dispatchKeyEvent(event)
        } catch (e: IllegalStateException) {
            if (!EditorEnterKeyGuard.swallowFocusSearchFailure("PlayerActivity", e)) throw e
            true
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // 锁定期间播放器按键（表冠/方向键/确认键）一律不响应，必须先解锁
        if (PlayerLockPolicy.shouldBlockPlaybackInput(lockPhase)) return super.onKeyDown(keyCode, event)
        if (isPrepared)
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> controlVideo()
                // 相对位移的起点用 video_now，避免在主线程上取 native 锁（顺带去掉 playerCore 的非空断言）
                KeyEvent.KEYCODE_DPAD_LEFT -> seekToPosition(video_now - 10000L)
                KeyEvent.KEYCODE_DPAD_RIGHT -> seekToPosition(video_now + 10000L)
                KeyEvent.KEYCODE_DPAD_UP -> changeVolume(true)
                KeyEvent.KEYCODE_DPAD_DOWN -> changeVolume(false)
            }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * 跳转入口。
     *
     * @param userInitiated 是否由**用户手势/按键**发起。必须由调用点如实声明：自动跳过自己
     *   调这个方法时传 `false`，否则它会被当成「用户想留在这段里」而把片段误标成已处理，
     *   真正该跳的片段就被吃掉了（26.10.22「乱跳」的成因之一，见 [SkipCancelPolicy]）。
     */
    private fun seekToPosition(position: Long, userInitiated: Boolean = true) {
        if (playerCore != null && isPrepared) {
            seekToVideo(position, userInitiated)
        }
    }

    /**
     * 用户主动跳转（拖进度条 / 快进快退）的**唯一入口**。
     *
     * 为什么不能各处各写一遍「playerCore.seekTo + mDanmakuView.seekTo」：
     * ① 跳转后必须立刻把 `video_now` 改成新位置——它就是弹幕时钟的时间源
     * （见 [bindDanmakuView] 里绑的媒体时钟），不改的话弹幕会继续按旧位置推进。
     * 同时清 `video_now_last`，让进度条/字幕下一帧立即按新位置刷新。
     *
     * ② DASH 双文件（video.mp4 + audio.m4a）是视频、音频两个播放器，**只 seek 视频会让音轨留在旧位置**；
     * 而 `progressChange` 的 800ms 偏差校正随后会再 seek 一次音频，用户听到的是"停顿两次"。
     * 所以这里一并 seek 音频，并压住校正冷却（[AUDIO_RESYNC_COOLDOWN_MS]）。
     *
     * @param userInitiated 见 [seekToPosition]：默认 true。自动跳过自身调用时必须传 false。
     */
    private fun seekToVideo(position: Long, userInitiated: Boolean = true) {
        // 用户手动拖进片头/片尾就算「已处理」，否则下一轮定时器会立刻把他弹走。
        // 自动跳过自己调用时不标记（userInitiated=false），两路不互相污染。
        onUserSeekTo(position, userInitiated)
        // markSeekTarget 依据它决定要不要起片头/片尾判定的静默期
        pendingSeekIsAutoSkip = !userInitiated
        markSeekTarget(position)
        pendingSeekIsAutoSkip = false

        playerCore?.seekTo(position)
        if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.seekTo(position)
        // 同步外部音频轨道：DASH 双文件时只 seek 视频会让音轨留在旧位置
        if (audioPlayer != null) {
            try {
                audioPlayer!!.seekTo(position.toInt())
            } catch (_: Exception) {}
            // seek 的冷却期内 progressChange 不再校正音频：MediaPlayer.seekTo 异步，
            // seek 未完成时 currentPosition 还停在旧位置，这时再校正等于同一位置 seek 两遍。
            audioResyncSuppressMs = android.os.SystemClock.elapsedRealtime() + AUDIO_RESYNC_COOLDOWN_MS
            // 拖动后偏差基线作废，冷却结束后重新采样（否则旧基线会把正常位置当漂移再 seek 一次）
            audioDriftSampled = false
        }
    }

    /**
     * 视频位置被动跳变（重播、切页、恢复现场）时同步 `video_now`。
     *
     * 不更新 `video_now` 的后果见 [seekToVideo]：弹幕时钟（媒体时钟）会继续按旧位置推进。
     */
    private fun markSeekTarget(position: Long) {
        video_now = position.toInt()
        video_now_last = -1 // 让 progressChange 下一帧立刻按新位置刷新进度条/字幕
        // 片头/片尾判定同样要等 seek 落地，否则 progressChange 把 video_now 拉回旧位置后
        // 会被立刻再判一次（「跳完又跳回去」）。只由自动跳过自己起静默期：用户主动拖动的落点
        // 已经被 onUserSeekTo 正式记为「已处理」，不需要时间兜底，也不该因为拖一下
        // 就压掉 600ms 的自动跳过。
        //
        // 合并说明：这里原本还写 `danmakuSnapCount = 0` / `danmakuResyncMs = now`，弹幕时钟改成
        // 媒体时钟（见 bindDanmakuView）后这两个字段已删除，静默期直接按当前时刻算，语义不变。
        if (pendingSeekIsAutoSkip) {
            skipQuietUntilMs = android.os.SystemClock.elapsedRealtime() + SKIP_QUIET_AFTER_SEEK_MS
        }
    }

    /**
     * 是否需要 `view_points` 数据：「显示视频分段」与「自动跳过片头片尾」都用得到。
     *
     * 26.10.10 撤回期间这里只有分段显示一项（撤回后没人消费片段表，白拉接口）；
     * 26.10.22 恢复后把 `PLAYER_SKIP_OP_ED` 加回来——**按存档原文判断，不要包一层
     * [SkipOpEdPrefs.isEnabled]**：那会把总开关状态也算进来，将来一旦再撤回，
     * 老用户存档里的 `true` 仍会让接口白拉一次。
     */
    private fun needViewPoints(): Boolean =
        SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_SHOW_VIEWPOINTS, true) ||
                SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_SKIP_OP_ED, SkipOpEdPrefs.DEFAULT_ENABLED)

    /**
     * 自动跳过片头片尾当前是否生效。
     *
     * 是否生效 = [SkipOpEdPrefs.FEATURE_ENABLED]（与存档无关的总否决位）**且** 用户存档。
     * 26.10.22 恢复后默认关闭：没在设置页主动打开过的用户，这里恒为 `false`。
     * 默认值与判据统一走 [SkipOpEdPrefs]，别在调用点写死字面量。
     */
    private fun skipOpEdEnabled(): Boolean =
        SkipOpEdPrefs.isEnabled(
            SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_SKIP_OP_ED, SkipOpEdPrefs.DEFAULT_ENABLED)
        )

    /**
     * 自动跳过片头/片尾。
     *
     * 触发条件：用户开了设置、且当前位置落在一段**还没处理过**的片头/片尾里。
     * 跳一次就把片段记进 [skipHandled]，这样用户点「撤回」回到原地后不会被立刻再弹走。
     */
    private fun maybeAutoSkipOpEd(positionSec: Double) {
        if (skipSegments.isEmpty()) return
        if (!skipOpEdEnabled()) return
        // 刚跳过一次：等 IJK 的异步 seek 落地再判，否则 video_now 回弹到旧位置会再判一次
        if (android.os.SystemClock.elapsedRealtime() < skipQuietUntilMs) return
        val segment = ViewPointSkip.shouldSkip(positionSec, skipSegments, skipHandled) ?: return
        skipHandled.add(ViewPointSkip.keyOf(segment))
        Logu.d("跳过片头片尾", "跳过 type=" + segment.type + " " + segment.fromSec + "s -> " + segment.toSec + "s")
        // 落点是区间的右端点（右开区间之外），所以不会被自己再次判定
        seekToPosition((segment.toSec * 1000.0).toLong(), userInitiated = false)
        lastSkippedSegment = segment
        showSkipUndoSnack(segment)
    }

    /** 跳过后给一次反悔机会：屏幕下方留一个「撤回」按钮，点一下回到被跳过的地方。 */
    private fun showSkipUndoSnack(segment: ViewPointSkip.Segment) {
        val isOpening = segment.type == ViewPointSkip.TYPE_OP
        val action = MsgUtil.Action(UiText.of("撤回"), View.OnClickListener {
            // 期间又跳过别的片段、或用户自己拖过进度，这条「撤回」就过期了，别把人拽回去
            if (lastSkippedSegment !== segment) return@OnClickListener
            lastSkippedSegment = null
            // 跳回片段起点；此时该片段已在 skipHandled 里，不会再被自动跳过
            seekToPosition((segment.fromSec * 1000.0).toLong())
            MsgUtil.showMsg(if (isOpening) UiText.of("已回到片头") else UiText.of("已回到片尾"))
        })
        val anchor = findViewById<View>(android.R.id.content) ?: return
        MsgUtil.createSnack(anchor, if (isOpening) UiText.of("已跳过片头") else UiText.of("已跳过片尾"), Snackbar.LENGTH_LONG, action).show()
    }

    /**
     * 用户主动拖动/按键跳转时，把落点所在的片头/片尾标记成「已处理」。
     *
     * 用户主动拖进片头，说明他就是想看看片头，不能被下一轮定时器又弹走。
     * 自动跳过自己跳到区间右端点（区间之外），所以不会误伤这里。
     *
     * @param userInitiated 只有用户发起时才标记。自动跳过自己、以及自动流程调用 seekToVideo 时
     *   必须传 false——传错了会让片段被误标，真正该跳的片段反而被吃掉
     *   （26.10.22「乱跳」的成因之一，判据收敛在 [SkipCancelPolicy.cancelKeyFor]）。
     */
    private fun onUserSeekTo(positionMs: Long, userInitiated: Boolean) {
        if (skipSegments.isEmpty()) return
        val segmentKey = ViewPointSkip.keyForManualSeek(positionMs / 1000.0, skipSegments, skipHandled)
        val key = SkipCancelPolicy.cancelKeyFor(userInitiated, segmentKey, skipHandled) ?: return
        skipHandled.add(key)
        lastSkippedSegment = null
    }

    /**
     * 第一次在「有片头片尾」的视频里播放时提示一次。
     *
     * **判据是「引导过没有」，不是「开关开着没有」**（见 [SkipOpEdPrefs.shouldShowGuide]）：
     * 26.10.22 恢复后默认关闭，所以「首次遇到有片头片尾的视频」正是告诉用户「这功能存在」的时机，
     * 界面上带一个「开启」按钮；若还按「开关开着就不引导」，没主动开过的用户永远不会知道有它。
     * 开关已开时文案改为「已自动跳过、可撤回」，不再劝用户「开启」。
     */
    private fun maybeShowSkipGuide() {
        // 总开关为假（再次紧急撤回）时连引导都不能出现：引导上带「开启」按钮，点了会往存档里写 true，
        // 等于给了用户一条绕过闸门的路（虽然闸门仍然拦得住，但弹一条已经下线的功能提示本身就是骚扰）。
        if (!SkipOpEdPrefs.FEATURE_ENABLED) return
        val enabled = skipOpEdEnabled()
        if (!SkipOpEdPrefs.shouldShowGuide(
                skipSegments.isNotEmpty(),
                SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_SKIP_OP_ED_GUIDED, false)
            )
        ) return
        SharedPreferencesUtil.putBoolean(SettingsKeys.PLAYER_SKIP_OP_ED_GUIDED, true)
        val action = MsgUtil.Action(SkipOpEdPrefs.guideActionText(enabled), View.OnClickListener {
            if (!enabled) {
                SharedPreferencesUtil.putBoolean(SettingsKeys.PLAYER_SKIP_OP_ED, true)
                MsgUtil.showMsg(UiText.of("已开启自动跳过片头片尾"))
            }
        })
        val anchor = findViewById<View>(android.R.id.content) ?: return
        MsgUtil.createSnack(
            anchor, SkipOpEdPrefs.guideText(enabled), Snackbar.LENGTH_LONG, action
        ).show()
    }

    /**
     * 左右滑动控制进度（横向滑动快进/快退）。返回 true 表示事件已被滑动进度消费。
     * 仅在开启设置、未缩放视频、非直播、已准备就绪且**没有按住长按三倍速**时生效。
     */
    private fun handleSwipeSeekTouch(event: MotionEvent): Boolean {
        // 缩放状态取画面的实际比例，不读 gesture_scaled（那个标志在 ACTION_UP 就复位，
        // 捏合放大松手后会把调进度重新放行——26.10.20 修的正是这个）。
        // 长按三倍速期间同理：按住时不消费横滑，松手（含 ACTION_CANCEL）后自动恢复。
        val seekAllowed = PlayerSwipeSeekPolicy.isSeekAllowed(
            swipeSeekEnabled, isPrepared, isLiveMode, playerCore != null,
            layout_video.scaleX, layout_video.scaleY, onLongClick
        )
        if (!seekAllowed) {
            // 门闸关闭时顺手收掉可能残留的浮层：横滑激活浮层后再捏合缩放，最终 ACTION_UP 会走到
            // 这里提前返回，ACTIVE 分支的清理永远执行不到；而缩放期间又进不来，浮层就会一直挂着。
            cancelSwipeSeekIfActive()
            return false
        }
        if (event.pointerCount != 1) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeSeekStartX = event.x
                swipeSeekActive = false
                // 拖动是"相对起点 + 位移"，起点用 video_now 即可，不必为了几十毫秒的精度在主线程上摸 native 锁
                swipeSeekStartPos = video_now.toLong()
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - swipeSeekStartX
                if (!swipeSeekActive && abs(deltaX) > swipeSeekThreshold) {
                    swipeSeekActive = true
                    swipeSeekStartPos = video_now.toLong()
                    gesture_click_disabled = true
                    hidecon.run()
                    swipe_seek_overlay.visibility = View.VISIBLE
                }
                if (swipeSeekActive) {
                    val width = layout_control.width.toFloat().coerceAtLeast(1f)
                    val duration = playerCore!!.duration
                    var target = swipeSeekStartPos + (deltaX / width * duration).toLong()
                    if (target < 0) target = 0
                    if (duration > 0 && target > duration) target = duration
                    swipeSeekTarget = target
                    seekbar_progress.progress = target.toInt()
                    updateSwipeSeekOverlay(target, duration)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (swipeSeekActive) {
                    swipeSeekActive = false
                    swipe_seek_overlay.visibility = View.GONE
                    if (isPrepared && playerCore != null) {
                        seekToPosition(swipeSeekTarget)
                    }
                    autohideReset()
                    return true
                }
                return false
            }
        }
        return false
    }

    /**
     * 结束长按三倍速，恢复用户选中的倍速与文案。
     *
     * **ACTION_UP 与 ACTION_CANCEL 都要调用**：长按加速期间横滑不再消费（见 [PlayerSwipeSeekPolicy]
     * 的 longPressBoost 门闸），若触摸被父容器/失焦 CANCEL 后 `onLongClick` 卡在 true，
     * 滑动调进度会被永久拦掉、播放速度也停在 3 倍。
     *
     * 走 [applySpeedFromSeekbar] 而不是直接下发：长按/滑条/重建后的 onPrepared 都用同一套
     * 「有效倍速」口径（见 [effectiveSpeedValue]），松手就回到滑条档位。
     */
    private fun endLongPressBoost() {
        if (!onLongClick) return
        onLongClick = false
        longPressBoostActive = false
        applySpeedFromSeekbar()
    }

    /** 收掉可能残留的滑动进度浮层；浮层与标志都不存在时不动 UI。 */
    private fun cancelSwipeSeekIfActive() {
        if (!swipeSeekActive && swipe_seek_overlay.visibility != View.VISIBLE) return
        swipeSeekActive = false
        swipe_seek_overlay.visibility = View.GONE
    }

    @SuppressLint("SetTextI18n")
    private fun updateSwipeSeekOverlay(targetMs: Long, durationMs: Long) {
        val currentStr = StringUtil.toTime((targetMs / 1000).toInt())
        val totalStr = if (durationMs > 0) StringUtil.toTime((durationMs / 1000).toInt()) else "--:--"
        val percent = if (durationMs > 0) (targetMs * 100 / durationMs).toInt() else 0
        swipe_seek_text.text = "$currentStr / $totalStr ($percent%)"
    }

    /** 先摘引用再释放，防止重载覆盖引用后，旧音频播放器失联且继续出声。 */
    private fun releaseAudioTrack() {
        val previous = audioPlayer
        audioPlayer = null
        audioTrackPrepared = false
        audioDriftSampled = false
        try {
            previous?.release()
        } catch (e: Exception) {
            Logu.e("AudioTrack", "释放外部音轨失败：${e.message}")
        }
    }

    /**
     * 主播放器已解析媒体后，确认主文件没有音轨才使用外部音频。
     * 直接使用 IJK 的轨道元数据，不再额外打开文件扫描，也不删除任何缓存文件。
     */
    private fun initAudioTrack(mediaPlayer: IMediaPlayer) {
        // 统一时钟内核里音视频已经是同一个播放器，绝不能再起外挂 MediaPlayer（否则又是两套时钟）
        if (mediaPlayer is UnifiedDashPlayer) {
            releaseAudioTrack()
            return
        }
        releaseAudioTrack()
        val url = audioTrackUrl?.takeIf { it.isNotBlank() } ?: return
        // 在线 DASH 是音视频分离的：音轨是网络流，必须带 Referer/Cookie 异步加载，走下面那条远程分支。
        // 本地缓存/下载的双文件仍然是文件路径，继续走原来的"确认主文件没有内嵌音轨"逻辑。
        if (PlayerStreamPolicy.isRemoteAudioTrack(url)) {
            initRemoteAudioTrack(url)
            return
        }
        val audioFile = File(url)
        val fileUsable = audioFile.isFile && audioFile.canRead() && audioFile.length() > 0L
        val tracks = if (!isOnlineVideo && !isLiveMode && fileUsable) {
            try {
                mediaPlayer.trackInfo?.map { track ->
                    when (track.trackType) {
                        ITrackInfo.MEDIA_TRACK_TYPE_AUDIO -> LocalAudioTrackPolicy.TrackKind.AUDIO
                        ITrackInfo.MEDIA_TRACK_TYPE_VIDEO -> LocalAudioTrackPolicy.TrackKind.VIDEO
                        else -> LocalAudioTrackPolicy.TrackKind.OTHER
                    }
                }
            } catch (e: Exception) {
                Logu.e("AudioTrack", "读取主文件音轨失败：${e.message}")
                null
            }
        } else null
        val selection = LocalAudioTrackPolicy.select(
            mainIsLocal = !isOnlineVideo && !isLiveMode,
            externalFileUsable = fileUsable,
            embeddedAudio = LocalAudioTrackPolicy.embeddedAudio(tracks)
        )
        Logu.d("AudioTrack", "主文件轨道=$tracks，外部文件可用=$fileUsable，选择=$selection")
        when (selection) {
            LocalAudioTrackPolicy.Selection.MAIN_ONLY -> {
                Logu.d("AudioTrack", "使用主文件音轨，不启动外部音频")
                return
            }
            LocalAudioTrackPolicy.Selection.UNKNOWN -> {
                Logu.e("AudioTrack", "主文件音轨未知，已阻止启动第二路音频")
                MsgUtil.showMsg(UiText.of("无法确认本地音轨，暂未启用外部音频"))
                return
            }
            LocalAudioTrackPolicy.Selection.EXTERNAL -> Unit
        }
        // 创建后立即保存引用：setDataSource/prepare 抛异常时也能释放新实例。
        try {
            val player = android.media.MediaPlayer()
            audioPlayer = player
            player.setOnErrorListener { failedPlayer, what, extra ->
                if (audioPlayer === failedPlayer) {
                    Logu.e("AudioTrack", "外部音频播放失败：$what/$extra")
                    releaseAudioTrack()
                    if (!destroyed) MsgUtil.showMsg(UiText.of("本地音频播放失败，请重新载入"))
                }
                true
            }
            player.setDataSource(url)
            player.prepare()
            audioTrackPrepared = true
            if (isPrepared) player.seekTo(video_now)
            player.isLooping = false
            applyAudioSpeed(speed_values[seekbar_speed.progress.coerceIn(0, speed_values.size - 1)])
            if (isPlaying) player.start()
            Logu.d("AudioTrack", "已启用唯一的外部音频轨道")
        } catch (e: Exception) {
            Logu.e("AudioTrack", "音频轨道初始化失败: ${e.message}")
            releaseAudioTrack()
        }
    }

    /**
     * 在线 DASH 音轨（1080P 及以上才有的那条纯音频流）。
     *
     * 与本地双文件不同，这里不能同步 `prepare()`（网络流会阻塞到首包到达），也不该去读主文件的
     * trackInfo —— DASH 的视频流本来就没有内嵌音轨。起播位置由 `isPrepared` 决定，
     * 音量与倍速跟主播放器保持同步，两路的时间偏差由 `progressChange` 的 800ms 校正兜底。
     */
    private fun initRemoteAudioTrack(url: String) {
        if (!isOnlineVideo || isLiveMode) {
            // 直播没有"第二条音轨"这回事；本地视频也不可能拿到在线音轨 URL
            Logu.w("AudioTrack", "非在线点播场景忽略远程音轨：$url")
            return
        }
        try {
            val player = android.media.MediaPlayer()
            audioPlayer = player
            player.setOnPreparedListener { mp ->
                if (audioPlayer !== mp) return@setOnPreparedListener
                try {
                    // 先标记已就绪：progressChange 的偏差校正只对 prepared 的音轨生效
                    // （对 preparing 中的 MediaPlayer seekTo 会触发 -38 直接把音轨判死）
                    audioTrackPrepared = true
                    // 起播位置刚 seek 过，偏差基线要重新采样
                    audioDriftSampled = false
                    mp.isLooping = false
                    if (isPrepared) mp.seekTo(video_now)
                    applyAudioSpeed(speed_values[seekbar_speed.progress.coerceIn(0, speed_values.size - 1)])
                    if (isPlaying) mp.start() else mp.pause()
                    applyVolumeGain()
                    // 刚起播时两路的时间基准不同，先压住偏差校正，避免一上来就互相 seek
                    audioResyncSuppressMs = android.os.SystemClock.elapsedRealtime() + AUDIO_RESYNC_COOLDOWN_MS
                    Logu.d("AudioTrack", "在线 DASH 音轨已就绪")
                } catch (e: Exception) {
                    Logu.e("AudioTrack", "在线音轨起播失败：${e.message}")
                    releaseAudioTrack()
                }
            }
            player.setOnErrorListener { failedPlayer, what, extra ->
                if (audioPlayer === failedPlayer) {
                    Logu.e("AudioTrack", "在线 DASH 音轨播放失败：$what/$extra")
                    releaseAudioTrack()
                    // 先重试一次再回退：切清晰度瞬间旧音轨还在拆，直接重建多半失败
                    if (retryRemoteAudioTrack(url)) return@setOnErrorListener true
                    onHighQualityAudioFailed()
                }
                true
            }
            val headers = mapOf(
                "Referer" to "https://www.bilibili.com/",
                "User-Agent" to NetWorkUtil.USER_AGENT_WEB,
                "Cookie" to SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")
            )
            // MediaPlayer 只有 setDataSource(Context, Uri, Map) 这个带请求头的重载：
            // B 站 CDN 不带 Referer/Cookie 会回 403，所以三样都得给。
            player.setDataSource(this, android.net.Uri.parse(url), headers)
            player.prepareAsync()
        } catch (e: Exception) {
            Logu.e("AudioTrack", "在线音轨初始化失败：${e.message}")
            releaseAudioTrack()
            if (!retryRemoteAudioTrack(url)) onHighQualityAudioFailed()
        }
    }

    /**
     * 在线 DASH 音轨失败后按 [url] 再试一次（同一次切清晰度最多 [MAX_DASH_AUDIO_RETRY] 次）。
     *
     * @return true 表示已经排好重试，调用方不要再走回退
     */
    private fun retryRemoteAudioTrack(url: String): Boolean {
        if (dashAudioRetryCount >= MAX_DASH_AUDIO_RETRY) return false
        dashAudioRetryCount++
        Logu.w("AudioTrack", "在线 DASH 音轨失败，${DASH_AUDIO_RETRY_DELAY_MS}ms 后重试")
        layout_control.postDelayed({
            if (destroyed || isFinishing) return@postDelayed
            // 期间已经换过音轨（新的成功实例或别的清晰度）就别再插手
            if (audioPlayer != null || audioTrackUrl != url) return@postDelayed
            if (currentQuality <= PlayerStreamPolicy.QN_720P) return@postDelayed
            initRemoteAudioTrack(url)
        }, DASH_AUDIO_RETRY_DELAY_MS)
        return true
    }

    /**
     * 在线 DASH 音轨加载失败时的兜底：切回 720P 的合一 MP4。
     *
     * 不这么做的后果是「有画面没声音」且用户在播放器里无路可走；720P 是唯一能保证音画同源的档位。
     * 只在当前确实是 DASH 档时回退，并用 [dashAudioFallbackInFlight] 防重入（否则会反复重建播放器）。
     */
    /**
     * 统一时钟内核失败后的降级：回退到 720P 合一 MP4（单 ijk 播放器）。
     *
     * DASH 在不同设备/账号权限下差异很大，回退到合一 MP4 至少保证「能看」。
     */
    private fun onUnifiedPlayerFailed() {
        if (unifiedFallbackInFlight) return
        unifiedFallbackInFlight = true
        Logu.w("PlayerCore", "统一时钟播放器失败，回退 720P 合一 MP4")
        MsgUtil.showMsg(UiText.of("高清音轨播放失败，已切回 720P"))
        applyQuality(PlayerStreamPolicy.QN_720P, false)
    }

    private fun onHighQualityAudioFailed() {
        if (destroyed || isFinishing) return
        if (dashAudioFallbackInFlight) return
        if (currentQuality <= PlayerStreamPolicy.QN_720P) return
        dashAudioFallbackInFlight = true
        runOnUiThread {
            if (destroyed || isFinishing) {
                dashAudioFallbackInFlight = false
                return@runOnUiThread
            }
            MsgUtil.showMsg(UiText.of("高清音轨加载失败，已切回 720P"))
            applyQuality(PlayerStreamPolicy.QN_720P, false)
        }
    }

    /**
     * 让外部音轨跟主播放器同速。
     *
     * 不做的后果：倍速播 1080P 时视频按 1.5x 走、音频还是 1x，`progressChange` 的 800ms 校正
     * 会不停 seek 音轨，听感是"每个 800ms 顿一下"。`PlaybackParams` 是 API 23 才有的接口，
     * 更低版本只能靠上面的偏差校正，不做额外处理。
     *
     * 主播放器那一路是 DASH 的纯视频流，它能跟着变速是因为随包的 ijkplayer 打了
     * `ijkpatch/0001-ffplay-video-playback-rate.patch`（无音轨时让 ffplay 按
     * `pf_playback_rate` 缩放帧间隔）。两路各自按倍速跑之后，残余的时钟漂移由
     * [progressChange] 的 800ms 基线校正兜底（正常只会在开头对齐时纠一两次）。
     */
    private fun applyAudioSpeed(speed: Float) {
        val player = audioPlayer ?: return
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return
        try {
            player.playbackParams = android.media.PlaybackParams().setSpeed(speed)
        } catch (e: Exception) {
            Logu.e("AudioTrack", "设置音轨倍速失败：${e.message}")
        }
    }

    private fun toggleAudioOnlyMode() {
        val oldMode = isAudioOnlyMode
        isAudioOnlyMode = !isAudioOnlyMode
        // 「播放默认值 → 听视频模式 = 沿用上次」的记录位；切换失败会在下面 catch 里回滚
        SharedPreferencesUtil.putBoolean(SettingsKeys.PLAYER_LAST_AUDIO_ONLY, isAudioOnlyMode)

        if (isPrepared && playerCore != null) {
            // 重建前的续播位置由 video_now 提供（与 retryAfterPlayerError 同一口径），
            // 不读 playerCore.currentPosition：那是取 native 锁的 JNI 调用，主线程调它有卡死风险
            val currentPosition = video_now.toLong()
            val wasPlaying = isPlaying

            MsgUtil.showMsg(if (isAudioOnlyMode) UiText.of("正在切换到听视频模式...") else UiText.of("正在切换到普通模式..."))

            CenterThreadPool.run {
                try {
                    runOnUiThread {
                        if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.pause()
                        // 顺序要求：先把状态标志摘掉，再 release，最后置空引用。
                        // 若反过来（先 release 再摘 isPrepared），release 与下面 Thread.sleep 之间的窗口里，
                        // 弹幕渲染线程仍会认为播放器可用而 deref 已释放的 native 实例；
                        // 置空引用则是为了任何后续误用立刻暴露成 NPE / null 判断，而不是 use-after-release。
                        isPrepared = false
                        isPlaying = false
                        releaseAudioTrack()
                        if (playerCore != null) {
                            playerCore!!.stop()
                            playerCore!!.release()
                            playerCore = null
                        }

                        loading_info.visibility = View.VISIBLE
                        anim_loading!!.start()
                        loading_text0.text = if (isAudioOnlyMode) UiText.of("切换到听视频模式") else UiText.of("切换到普通模式")

                        updateAudioOnlyButton()
                        updateAudioOnlyUI()
                    }

                    Thread.sleep(100)

                    runOnUiThread {
                        // 本方法前半段在 CenterThreadPool 线程上 sleep(100ms)，期间用户可能已经退出页面：
                        // onDestroy 已置 destroyed=true 并 release/置空 playerCore，此处若无守卫就会重建出一个
                        // 永不释放的 native 播放器（泄漏，且 surface 已销毁时可能直接崩）。
                        if (destroyed || isFinishing()) return@runOnUiThread
                        // 原生库不可用时 helper 自己提示并 finish，这里只需停下重建
                        playerCore = createPlayerOrBail() ?: return@runOnUiThread
                        progress_history = currentPosition
                        setDisplay()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        MsgUtil.showMsg(UiText.of("切换失败，请重试"))
                        isAudioOnlyMode = oldMode
                        // 切换失败就不该污染「沿用上次」的记录
                        SharedPreferencesUtil.putBoolean(SettingsKeys.PLAYER_LAST_AUDIO_ONLY, oldMode)
                        updateAudioOnlyButton()
                        updateAudioOnlyUI()
                        loading_info.visibility = View.GONE
                        anim_loading!!.stop()
                    }
                }
            }
        } else {
            updateAudioOnlyButton()
            updateAudioOnlyUI()
            MsgUtil.showMsg(if (isAudioOnlyMode) UiText.of("已切换到听视频模式") else UiText.of("已切换到普通模式"))
        }
    }

    private fun updateAudioOnlyButton() {
        btn_audio_only.setImageResource(if (isAudioOnlyMode) R.drawable.icon_audio_only_on else R.drawable.icon_audio_only_off)
    }

    private fun updateAudioOnlyUI() {
        runOnUiThread {
            if (isAudioOnlyMode) {
                text_speed.visibility = View.GONE
                btn_debug.visibility = View.GONE
                btn_danmaku.visibility = View.GONE
                layout_video.visibility = View.GONE
                layout_audio_only.visibility = View.VISIBLE
                mDanmakuView?.setVisibility(View.GONE)
                if (text_audio_title != null) {
                    val title = text_title!!.text.toString()
                    text_audio_title.text = if (title.isEmpty()) UiText.of("听视频模式") else title
                }
            } else {
                text_speed.visibility = View.VISIBLE
                updateDebugButtonVisibility()
                btn_danmaku.visibility = View.VISIBLE
                layout_video.visibility = View.VISIBLE
                layout_audio_only.visibility = View.GONE
                if (mDanmakuView != null && isDanmakuVisible) mDanmakuView!!.setVisibility(View.VISIBLE)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (eventBusEnabled() && !eventBusInit) {
            EventBus.getDefault().register(this)
            Logu.v("event", "register")
            eventBusInit = true
        }
        // 伪装的「双击顶栏」：本页不继承 BaseActivity（那边统一安装），必须自己接一次线。
        // 观看中是最需要一键遮屏的场景，所以播放页不能漏。
        // 关掉开关时要摘掉：install 只在开启时生效，但已装上的触摸层不会自己消失，
        // 不摘的话单击顶栏会一直多等一个双击判定窗口。
        if (::layout_top.isInitialized) {
            if (DisguisePrefs.isEnabled()) DisguiseGesture.install(this, layout_top)
            else DisguiseGesture.detach(layout_top)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onEvent(snackEvent: SnackEvent) {
        if (isFinishing) return
        Logu.v("event", "onEvent")

        val currentTime = System.currentTimeMillis()

        val duration = when {
            snackEvent.duration > 0 -> snackEvent.duration
            snackEvent.duration == Snackbar.LENGTH_SHORT -> 1950
            snackEvent.duration == Snackbar.LENGTH_INDEFINITE -> Int.MAX_VALUE
            else -> 2750
        }

        val endTime = snackEvent.startTime + duration
        if (currentTime >= endTime) {
            EventBus.getDefault().removeStickyEvent(snackEvent)
        } else {
            MsgUtil.toast(snackEvent.message)
        }
    }

    protected fun eventBusEnabled(): Boolean {
        return SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.SNACKBAR_ENABLE, true)
    }

    private fun loadHighEnergyData() {
        if (!SharedPreferencesUtil.getBoolean("player_high_energy", false)) {
            Logu.d("高能进度条", "功能已禁用")
            return
        }

        CenterThreadPool.run {
            try {
                // bvid 不是每个入口都会传（PlayerData 里就没有这个字段），
                // 拿不到就交给 getHighEnergyData 用 av 号拼 Referer
                val bvid = intent?.getStringExtra("bvid") ?: ""
                Logu.d("高能进度条", "开始加载数据 aid=" + aid + " cid=" + cid + " bvid=" + bvid)
                val data = PlayerApi.getHighEnergyData(cid, aid, bvid)

                if (data != null && data.hasValidData()) {
                    runOnUiThread {
                        if (!destroyed) {
                            seekbar_progress.setHighEnergyData(data.events, data.stepSec)
                            Logu.d("高能进度条", "数据加载成功并设置到进度条")
                        }
                    }
                } else {
                    Logu.w("高能进度条", "未获取到有效数据")
                }
            } catch (e: Exception) {
                Logu.e("高能进度条", "加载失败: " + e.message)
                e.printStackTrace()
            }
        }
    }

    private fun hasMultiplePages(): Boolean = pagenames != null && pagenames!!.size > 1

    private fun showPageSelectorCard() {
        if (!hasMultiplePages()) return

        runOnUiThread {
            val pageSelectorRecycler = findViewById<RecyclerView>(R.id.page_selector_list)
            val adapter = PageSelectorAdapter()
            adapter.setData(pagenames!!, currentPageIndex)
            adapter.setOnItemClickListener { index ->
                layout_card_bg.visibility = View.GONE
                card_page_selector.visibility = View.GONE
                if (index != currentPageIndex) switchToPage(index)
            }
            pageSelectorRecycler.layoutManager = CustomLinearManager(this)
            pageSelectorRecycler.adapter = adapter

            // 标题要说清"现在在哪一层"：同一个"第 3 集"，在收藏夹连播里、在合集里、
            // 在多P视频的分P 里指的是完全不同的东西
            val size = pagenames!!.size
            // 层级标题 + 位置后缀：「合集 · 名字（3/12）」这类文案不再硬编码，
            // 前缀复用 UiText 里已有的「合集 · %s」，位置后缀是共享模板（三种层级都用）。
            val pagePosition = getString(R.string.text_player_page_position, currentPageIndex + 1, size)
            findViewById<TextView>(R.id.page_selector_title).text = when {
                collectionId != 0L ->
                    UiText.fmt(UiText.of("合集 · %s"), collectionTitle ?: "") + pagePosition
                multiPageAid != 0L ->
                    getString(R.string.text_player_multi_page_prefix, multiPageTitle ?: "") + pagePosition
                isVirtualCollectionMode ->
                    "${rootTitle ?: videoTitle ?: UiText.of("收藏夹连播")}" + pagePosition
                else -> UiText.of("选择分P")
            }

            // 嵌套层的进出入口，两个方向互斥：
            //  · 已经在层里（returnPlaylist != null）→「← 返回主合集」
            //  · 在根目录层、且当前这条是"文件夹"（多P / 属于合集）→「进入分P / 进入合集」
            // **进入必须由用户点这里**，不自动（见 enterableCollection 的注释）。
            val nestEntry = findViewById<TextView>(R.id.page_selector_nest)
            val enterable = when {
                returnPlaylist != null -> null
                enterableCollection != null -> getString(
                    R.string.text_player_enter_collection,
                    enterableCollection!!.title ?: "",
                    Collection.toPlaylist(enterableCollection!!).size
                )
                enterableMultiPage != null ->
                    getString(R.string.text_player_enter_multi_page, enterableMultiPage!!.cids.size)
                else -> null
            }
            nestEntry.visibility = if (returnPlaylist != null || enterable != null) View.VISIBLE else View.GONE
            nestEntry.text = if (returnPlaylist != null) getString(R.string.text_player_back_to_root) else enterable
            nestEntry.setOnClickListener {
                layout_card_bg.visibility = View.GONE
                card_page_selector.visibility = View.GONE
                if (returnPlaylist != null) {
                    returnToCallerPlaylist(advance = false)
                } else {
                    enterCurrentNestLayer()
                }
            }

            layout_card_bg.visibility = View.VISIBLE
            card_page_selector.visibility = View.VISIBLE
        }
    }

    private fun switchToPage(pageIndex: Int) {
        if (!hasMultiplePages() || pageIndex < 0 || pageIndex >= pagenames!!.size) return
        if (pageIndex == currentPageIndex) return

        // 切P之前先把"当前这一P"的进度报出去，否则这一P的尾部进度只有 15 秒周期上报兜底，
        // 切走时的最后十几秒会丢；必须在 currentPageIndex 被改写之前调用（aid/cid 还指向旧P）
        reportProgressNow(true, UiText.of("切P前"))

        currentPageIndex = pageIndex
        val newTitle = pagenames!![pageIndex]

        MsgUtil.showMsg(UiText.fmt(UiText.of("切换到 P%1\$s"), pageIndex + 1))

        if (isOnlineVideo) {
            switchToOnlinePage(pageIndex, newTitle)
        } else {
            switchToLocalPage(pageIndex, newTitle)
        }
    }

    /**
     * 切换到在线视频的指定分页
     * 支持两种场景：
     * 1. 多P视频：cids中存储的是真实cid，直接使用
     * 2. 虚拟合集（收藏夹）：cids中存储的是aid，需要通过aid获取视频信息
     */
    private fun switchToOnlinePage(pageIndex: Int, newTitle: String) {
        CenterThreadPool.run {
            try {
                val playerData = PlayerData()
                val cidValue = cids!![pageIndex]

                // 这条能不能"进得去"（多P 视频 / 属于某个真实合集）。**只记录，不自动进**——
                // 用户明确要求"点击后主动进入"（26.10.16）：自动进会在你只想顺着收藏夹听的时候
                // 突然被换成一门 105 讲课程的分P 列表，收藏夹里后面的条目全被跳过。
                // 这一次 getVideoInfo 本来就要发（为了拿首个 cid），响应里的 ugc_season 是白拿的。
                var videoInfoForEnter: VideoInfo? = null

                if (isVirtualCollectionMode) {
                    // 虚拟合集模式：cids 里存的是每个视频的 **aid**，要拿它反查该视频的首个 cid
                    playerData.aid = cidValue
                    playerData.cid = 0 // 需要从API获取
                    val videoInfo = com.RobinNotBad.BiliClient.api.VideoInfoApi.getVideoInfo(cidValue)
                    videoInfoForEnter = videoInfo
                    if (videoInfo != null && videoInfo.cids.isNotEmpty()) {
                        // 不进去时只播第 1 个分P——想听其它分P 就点选集卡片上的「进入分P」
                        playerData.cid = videoInfo.cids[0]
                    }
                } else {
                    // 真实多P：cids 里就是真 cid，aid 不变
                    playerData.aid = aid
                    playerData.cid = cidValue
                }

                playerData.title = newTitle
                playerData.mid = mid
                playerData.qn = AccountStateStore.clampQuality(
                    SharedPreferencesUtil.getInt(SettingsKeys.PLAY_QN, 16)
                )
                playerData.pagenames = pagenames
                playerData.cids = cids
                playerData.currentPageIndex = currentPageIndex

                // 切P同样要按清晰度选接口：走 fnval=1 的话 1080P 会被服务端降级成 720P
                if (epid != 0L) PlayerApi.getPlaybackBangumi(playerData)
                else PlayerApi.getPlaybackVideo(playerData)

                runOnUiThread {
                    if (destroyed) return@runOnUiThread
                    // 必须把 Activity 的 cid 字段同步到新分P：doSwitchPage 之后拉弹幕/字幕/看点
                    // 都用这个字段，finish() 回传给上一页的也是它。不同步的话切P后弹幕、
                    // 字幕、看点以及观看进度上报全都还挂在切P前的那个 cid 上。
                    if (playerData.cid > 0) cid = playerData.cid
                    // aid 同理，而且**虚拟合集下必须同步**：cids 装的是 aid，切到第 N 个视频后
                    // aid 还停在第一个视频上，进度上报就会变成 reportHistory(视频1, 视频N的cid)——
                    // 把 N 的进度写到 1 的记录里（26.10.15 修）
                    if (playerData.aid > 0) aid = playerData.aid
                    // 在 UI 线程落地"这条能不能进得去"，供选集卡片画那一行（见 enterableCollection）
                    updateEnterable(videoInfoForEnter)
                    // 第 5 个参数是外部音轨：DASH 1080P+ 的视频流没声音，必须把新P的音轨一起带过去
                    doSwitchPage(
                        newTitle, playerData.videoUrl, playerData.danmakuUrl, pageIndex,
                        playerData.audioUrl?.takeIf { it.isNotBlank() }
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    MsgUtil.err(e)
                    MsgUtil.showMsg(UiText.of("切换失败"))
                }
            }
        }
    }

    /**
     * 把播放列表整体换成某个真实合集（`ugc_season`），从 [startAid] 那一集开始播。
     *
     * <p>与"收藏夹虚拟合集"共用同一套数据形状（`cids` 里装 aid、切集时反查 cid），
     * 所以切页逻辑一行都不用改；差别只在于多了 [collectionId] 这个身份，用来防止
     * "正在放合集 C 的第 5 集时又切进 C"把位置重置。
     *
     * <p>切进来时把**收藏夹那条播放列表**压进 [returnPlaylist]（只压一层）：
     * 否则合集动辄上百集，收藏夹里排在后面的条目永远轮不到，等于被吞了。
     *
     * @param triggeringIndex 触发这次切换的条目在**收藏夹那条列表**里的下标
     */
    private fun enterCollectionPlaylist(
        collection: Collection,
        items: List<Collection.PlaylistItem>,
        startAid: Long,
        triggeringIndex: Int
    ) {
        if (collectionId == 0L) {
            val back = PlayerData()
            back.title = videoTitle ?: ""
            back.pagenames = ArrayList(pagenames ?: emptyList())
            back.cids = ArrayList(cids ?: emptyList())
            // 记住触发的那一条：手动"返回主合集"回到它，合集播完自动返回则用它 +1
            back.currentPageIndex = triggeringIndex
            back.virtualCollection = true
            returnPlaylist = back
        }
        collectionId = collection.id.toLong()
        collectionTitle = collection.title
        multiPageAid = 0L
        multiPageTitle = null
        // 已经进层了，选集卡片那一行该换成「← 返回主合集」
        enterableCollection = null
        enterableMultiPage = null
        pagenames = ArrayList(items.map { it.title })
        cids = ArrayList(items.map { it.aid })
        isVirtualCollectionMode = true
        val target = items.indexOfFirst { it.aid == startAid }.coerceAtLeast(0)
        currentPageIndex = target
        MsgUtil.showMsg(getString(R.string.msg_player_entered_collection, collection.title ?: ""))
        switchToOnlinePage(target, pagenames!![target])
    }

    /**
     * 用户点了选集卡片上的「进入分P / 进入合集」——把当前这条"文件夹"打开。
     *
     * <p>合集优先于分P（外层"一系列视频" vs 内层"一个视频的段落"），与 [enterableCollection]
     * 的判定顺序保持一致。
     */
    private fun enterCurrentNestLayer() {
        val collection = enterableCollection
        val multiPage = enterableMultiPage
        when {
            collection != null ->
                enterCollectionPlaylist(collection, Collection.toPlaylist(collection), aid, currentPageIndex)
            multiPage != null -> enterMultiPageLayer(multiPage, currentPageIndex)
        }
    }

    /**
     * 进入"分P层"：把播放列表换成某个多P视频的各分P，从第 1 个分P 起播。
     *
     * <p>这是第二类"文件夹"。用户的心智模型是一棵目录树：虚拟合集是根目录，单视频是根目录下的
     * MP4，而**多P视频本身是个可进入的目录**——进去看它的各分P，退出来回根目录。
     *
     * <p>与合集层的差别：这一层的 `cids` 是**真 cid**（同一个 aid 的多个分P），所以
     * [isVirtualCollectionMode] 置 false，切页走普通多P 分支（`aid` 不变、`cid = cids[i]`）。
     * 也正因如此，`aid` 必须在这里换成该视频的 aid——它此前是"收藏夹里的第 N 条"那个概念。
     *
     * @param triggeringIndex 触发这次进入的条目在**收藏夹那条列表**里的下标
     */
    private fun enterMultiPageLayer(videoInfo: VideoInfo, triggeringIndex: Int) {
        if (returnPlaylist == null) {
            val back = PlayerData()
            back.title = videoTitle ?: ""
            back.pagenames = ArrayList(pagenames ?: emptyList())
            back.cids = ArrayList(cids ?: emptyList())
            back.currentPageIndex = triggeringIndex
            back.virtualCollection = true
            returnPlaylist = back
        }
        collectionId = 0L
        collectionTitle = null
        multiPageAid = videoInfo.aid
        multiPageTitle = videoInfo.title
        // 已经进层了，选集卡片那一行该换成「← 返回主合集」
        enterableCollection = null
        enterableMultiPage = null
        pagenames = ArrayList(videoInfo.pagenames)
        cids = ArrayList(videoInfo.cids)
        isVirtualCollectionMode = false
        aid = videoInfo.aid
        // 从第 1 个分P 起。VideoInfo 没带"上次看到哪个分P"，续播到具体分P 需要另读 player/v2 的
        // last_play_cid，本轮不做（见台账 §3.24 的未覆盖项）
        currentPageIndex = 0
        MsgUtil.showMsg(getString(R.string.msg_player_entered_multi_page, videoInfo.title ?: ""))
        switchToOnlinePage(0, pagenames!![0])
    }

    /**
     * 弹出一层返回栈，回到切进合集之前的那条播放列表。
     *
     * @param advance true 用于"合集播完了"（接着放触发条的**下一条**）；
     *                false 用于用户在选集卡片里点「返回主合集」（回到触发的那一条本身）
     */
    private fun returnToCallerPlaylist(advance: Boolean) {
        val back = returnPlaylist ?: return
        val backPages = back.pagenames
        val backCids = back.cids
        if (backPages.isNullOrEmpty() || backCids.isNullOrEmpty()) return

        // 先记下"正要离开的是哪一层"：下面会把两个层字段都清零，之后再判断就只能看到 0 了
        val leavingCollection = collectionId != 0L

        val resume = (back.currentPageIndex + if (advance) 1 else 0)
            .coerceIn(0, backPages.size - 1)

        reportProgressNow(true, UiText.of("返回主合集前"))

        // 返回栈里存的就是根目录层那条列表，它的 title 就是收藏夹名——拿它当根层标题，
        // 否则卡片会退化成"当前条目名"（见 rootTitle 的注释）
        rootTitle = back.title
        pagenames = ArrayList(backPages)
        cids = ArrayList(backCids)
        isVirtualCollectionMode = back.virtualCollection
        collectionId = 0L
        collectionTitle = null
        // 两个嵌套层都要清：返回后我们回到的是根目录（收藏夹虚拟合集），不在任何"文件夹"里
        multiPageAid = 0L
        multiPageTitle = null
        returnPlaylist = null
        currentPageIndex = resume

        MsgUtil.showMsg(
            when {
                !advance -> UiText.of("返回主合集")
                leavingCollection -> UiText.of("合集播放完毕，继续收藏夹")
                else -> UiText.of("分P播放完毕，继续收藏夹")
            }
        )
        switchToOnlinePage(resume, backPages[resume])
    }

    /**
     * 切换到本地虚拟合集的指定分页
     */
    private fun switchToLocalPage(pageIndex: Int, newTitle: String) {
        // 从 intent 中获取视频文件和弹幕文件列表
        val videoFileList = intent.getStringArrayListExtra("videoFileList") ?: return
        val danmakuFileList = intent.getStringArrayListExtra("danmakuFileList")

        if (pageIndex < 0 || pageIndex >= videoFileList.size) return

        val videoUrl = videoFileList[pageIndex]
        val danmakuUrl = danmakuFileList?.getOrElse(pageIndex) { "" } ?: ""
        // 文件夹虚拟合集会带上与 videoFileList 对齐的外部音轨（未合并的 DASH 缓存，反馈 #51）
        val audioUrl = intent.getStringArrayListExtra("audioFileList")
            ?.getOrElse(pageIndex) { "" }?.takeIf { it.isNotEmpty() }

        runOnUiThread {
            doSwitchPage(newTitle, videoUrl, danmakuUrl, pageIndex, audioUrl)
        }
    }

    /**
     * 执行分页切换的通用UI操作
     *
     * @param newAudioTrackUrl 新分页的外部音轨；在线切P与无音轨的本地文件传 null
     */
    private fun doSwitchPage(
        newTitle: String,
        newVideoUrl: String,
        newDanmakuUrl: String,
        pageIndex: Int,
        newAudioTrackUrl: String? = null
    ) {
        if (destroyed) return

        releaseAudioTrack()
        audioTrackUrl = newAudioTrackUrl // 新分页不能继续复用上一页的外部音轨。
        // 换页就是换一整套取流结果，上一页的 CDN 候选表必须作废，否则报错重试会拿旧地址。
        videoBackupUrls = ArrayList()
        audioBackupUrls = ArrayList()
        videoBackupIndex = 0
        playerCore?.stop()
        playerCore?.release()
        releaseDanmaku()

        video_url = newVideoUrl
        danmaku_url = newDanmakuUrl
        // 切P 后本地视频的目录与落盘去重都要跟着换：每个分P 各有自己的 `.video_meta.json`，
        // 沿用上一P 的目录会把这一P 的位置写进别人的文件
        localMediaDir = if (newVideoUrl.contains("http")) null else File(newVideoUrl).absoluteFile.parentFile
        lastSavedLocalProgressMs = -1L
        text_title!!.text = newTitle
        videoTitle = newTitle

        loading_info.visibility = View.VISIBLE
        anim_loading!!.start()
        loading_text0.text = UiText.fmt(UiText.of("加载P%1\$s"), pageIndex + 1)
        isPrepared = false
        isPlaying = false
        finishWatching = false
        progress_history = 0
        // 新的一P要从头开始计时：不重置的话旧P的位置（比如 300000ms）会一直满足 15 秒差值条件，
        // 新P播到 1 秒就被上报一次，把该P原本的续播进度冲掉
        lastReportedProgressMs = -1L
        lastReportedProgressSec = -1L
        subtitles = null
        subtitleLinks = null
        subtitle_selected = -1
        viewPoints = null
        viewPointAdapter = null
        btn_viewpoint.visibility = View.GONE

        interactionData = null
        currentEdgeId = 0
        currentQuestion = null
        questionShown = false
        interactionChoiceLayout?.visibility = View.GONE
        interactionChoiceLayout?.removeAllViews()

        // 原生库不可用时 helper 自己提示并 finish，这里只需停下重建
        playerCore = createPlayerOrBail() ?: return
        bindDanmakuView()

        setDisplay()

        layout_control.postDelayed({
            CenterThreadPool.run {
                if (destroyed) return@run

                runOnUiThread {
                    loading_text0.text = UiText.of("装填弹幕中")
                    loading_text1.text = "(≧∇≦)"
                }

                if (isOnlineVideo) {
                    danmakuFile = File(cacheDir, "danmaku.xml")
                    if (danmakuFile!!.exists()) danmakuFile!!.delete()
                    downdanmu()
                }

                if (!destroyed) {
                    maybeAutoSubtitle()
                }

                if (!destroyed && isOnlineVideo && aid > 0 && cid > 0) {
                    loadHighEnergyData()
                }

                if (!destroyed && isOnlineVideo && aid > 0 && cid > 0 && needViewPoints()) {
                    loadViewPoints()
                }

                if (!destroyed && isOnlineVideo && aid > 0 && cid > 0) {
                    loadInteractionVideo()
                }
            }
        }, 60)
    }

    private fun toggleAutoNext() {
        auto_next_enabled = !auto_next_enabled
        updateAutoNextButton()
        // 「播放默认值 → 自动连播 = 沿用上次」的记录位
        SharedPreferencesUtil.putBoolean(SettingsKeys.PLAYER_LAST_AUTONEXT, auto_next_enabled)
        MsgUtil.showMsg(if (auto_next_enabled) UiText.of("已开启自动连播") else UiText.of("已关闭自动连播"))
    }

    private fun updateAutoNextButton() {
        btn_auto_next.setImageResource(if (auto_next_enabled) R.drawable.icon_auto_next_on else R.drawable.icon_auto_next_off)
    }

    /**
     * 弹清晰度选择卡片。
     *
     * 列表里的档位来自服务端 `accept_quality`，它列的是「视频有哪些清晰度」，**不是当前账号
     * 有没有权限播**：未登录时 1080P 及以上、非大会员时 1080P60 及以上都只是「看得见点不动」，
     * 服务端不会报错，只会静默给低一档的流。所以这里按账号状态把它们标成锁定态（复用播放器
     * 的锁图标，见 [QualitySelectorAdapter.lockedMaxQuality]），点了给提示而不是假装切换。
     */
    private fun showQualitySelectorCard() {
        if (qnStrList == null || qnValueList == null || qnStrList!!.isEmpty()) {
            MsgUtil.showMsg(UiText.of("清晰度列表未加载"))
            return
        }

        // 先用已知/缓存状态渲染，账号状态还没查到时后台补查（见 refreshAccountStateIfNeeded）
        val state = accountState ?: AccountStateStore.cached()
        refreshAccountStateIfNeeded()

        runOnUiThread {
            val qualitySelectorRecycler = findViewById<RecyclerView>(R.id.quality_selector_list)
            val adapter = QualitySelectorAdapter()
            adapter.lockedMaxQuality = QualityAccessPolicy.maxUnlockedQn(state.loggedIn, state.vip)
            adapter.onLockedItemClickListener = { lockedQuality ->
                MsgUtil.showMsg(qualityLockedHint(state, lockedQuality))
                Logu.d(
                    "QualitySelector",
                    "锁定档位被点击：qn=$lockedQuality 登录=${state.loggedIn} 大会员=${state.vip}"
                )
            }
            // 服务端对没权限的账号不下发 4K 档（accept_quality 里连 120 都没有），
            // 这里补一个锁定项，用户才知道开大会员能解锁这一档
            val displayNames = ArrayList(qnStrList!!.toList())
            val displayValues = ArrayList<Int>(qnValueList!!.toList())
            if (QualityAccessPolicy.needsLocked4kHint(displayValues.toIntArray(), state.loggedIn, state.vip)) {
                displayNames.add(UiText.of("4K 超清"))
                displayValues.add(QualityAccessPolicy.QN_4K)
            }
            adapter.setData(displayNames.toTypedArray(), displayValues.toIntArray(), currentQuality)
            adapter.setOnItemClickListener { index ->
                layout_card_bg.visibility = View.GONE
                card_quality_selector.visibility = View.GONE
                if (index >= 0 && index < displayValues.size && displayValues[index] != currentQuality) {
                    switchQuality(displayValues[index])
                }
            }
            qualitySelectorRecycler.layoutManager = CustomLinearManager(this, LinearLayoutManager.HORIZONTAL, false)
            qualitySelectorRecycler.adapter = adapter
            qualityAdapter = adapter
            layout_card_bg.visibility = View.VISIBLE
            card_quality_selector.visibility = View.VISIBLE
        }
    }

    /**
     * 被锁住的清晰度点了以后该说什么。
     *
     * 4K 是大会员专属，不管登没登录都是「开通大会员」这一句；其余按未登录/非大会员分两套口径。
     */
    private fun qualityLockedHint(state: AccountStateStore.State, lockedQuality: Int = 0): String = when {
        lockedQuality >= QualityAccessPolicy.QN_4K -> UiText.of("4K 超清需要开通大会员")
        !state.loggedIn -> UiText.of("登录后可选择 1080P 及以上的清晰度")
        else -> UiText.of("开通大会员后可选择 1080P60 帧及以上的清晰度")
    }

    /**
     * 后台补查一次账号状态；拿到结果后就地刷新清晰度列表的锁态（不重建列表，保留滚动位置）。
     *
     * 失败时 [AccountStateStore.refresh] 会退回缓存，所以这里不用处理异常。
     */
    private fun refreshAccountStateIfNeeded() {
        if (accountState != null || accountStateRefreshing) return
        accountStateRefreshing = true
        CenterThreadPool.run {
            val fresh = AccountStateStore.refresh()
            accountState = fresh
            accountStateRefreshing = false
            runOnUiThread {
                qualityAdapter?.let { adapter ->
                    adapter.lockedMaxQuality = QualityAccessPolicy.maxUnlockedQn(fresh.loggedIn, fresh.vip)
                    adapter.notifyDataSetChanged()
                }
                // 面板正开着时按新状态重建一次：4K 提示项是"没权限才补"的，账号状态从缓存翻成
                // 大会员后必须把它去掉，否则会出现一个点不动的假 4K
                if (card_quality_selector.visibility == View.VISIBLE) showQualitySelectorCard()
            }
        }
    }

    private fun switchQuality(newQuality: Int) {
        if (!isOnlineVideo || newQuality == currentQuality) return
        // 一切档位都优先走 DASH：并不是因为低档位只能 DASH 拿，而是清晰度面板要靠 DASH 的
        // 完整档位清单（fnval=1 会按请求档位裁剪，720P 请求只回 720P/360P，1080P60/4K 直接消失）。
        // 只有 DASH 取流失败时才在 getPlaybackVideo 内回退合一 MP4。
        // 倍速下也一样：随包的 ijkplayer 已打补丁，纯视频流能跟着变速，不用再换成合一 MP4。
        applyQuality(newQuality, true)
    }

    /**
     * 真正执行清晰度切换：重新取流 → 重建 ijk → 重新 setDisplay。
     *
     * <p>把「取流 + 落地」从 [switchQuality] 里拆出来，是因为在线 DASH 音轨加载失败时也要
     * 走同一条路回退 720P（见 [onHighQualityAudioFailed]），那时唯一的差别就是 `allowDash=false`。
     *
     * @param allowDash 是否允许用 DASH 双流（无声视频 + 独立音轨）；false 时强制走合一 MP4
     */
    private fun applyQuality(newQuality: Int, allowDash: Boolean) {
        MsgUtil.showMsg(UiText.of("正在切换清晰度..."))

        CenterThreadPool.run {
            try {
                // 超权档位（例如没大会员却请求 4K）先夹到权限内：服务端对超权请求会降级发流，
                // 有时降得比权限内最高档还低，夹紧后界面显示的档位与实际流才一致
                val requestedQuality = AccountStateStore.clampQuality(newQuality)
                val playerData = PlayerData()
                playerData.aid = aid
                playerData.cid = cid
                playerData.title = text_title!!.text.toString()
                playerData.mid = mid
                playerData.qn = requestedQuality
                playerData.pagenames = pagenames
                playerData.cids = cids
                playerData.currentPageIndex = currentPageIndex
                // 番剧维度要带上：缺了它 DASH 会走投稿接口，1080P 照样拿不到
                playerData.epid = epid
                playerData.seasonId = seasonId
                playerData.seasonType = seasonType

                if (allowDash) {
                    if (epid != 0L) PlayerApi.getPlaybackBangumi(playerData)
                    else PlayerApi.getPlaybackVideo(playerData)
                } else {
                    if (epid != 0L) PlayerApi.getBangumi(playerData)
                    else PlayerApi.getVideo(playerData, false)
                }

                val newVideoUrl = playerData.videoUrl
                if (newVideoUrl.isNullOrBlank()) throw IllegalStateException("取流结果为空")
                // 合一 MP4（fnval=1）会按账号权限降级发流，真实档位由响应 quality 回填进
                // playerData.qn（见 PlayerApi#applyActualQuality）；DASH 成功时就是请求档位。
                val dashUsable = allowDash && !playerData.audioUrl.isNullOrBlank()
                val appliedQuality = when {
                    allowDash && !dashUsable -> PlayerStreamPolicy.QN_720P
                    !allowDash && playerData.qn > 0 -> playerData.qn
                    else -> requestedQuality
                }

                runOnUiThread {
                    if (destroyed) return@runOnUiThread

                    // 重建前的续播位置同样用 video_now（见 toggleAudioOnlyMode / retryAfterPlayerError），
                    // 这里虽然已经在 runOnUiThread 里，仍然不该在主线程上摸 native 锁
                    val currentPosition = video_now.toLong()

                    releaseAudioTrack()
                    // 只有 DASH 双流才挂外部音轨：合一 MP4 自带音轨，再挂一路会重复出声
                    audioTrackUrl = if (dashUsable) playerData.audioUrl else null
                    // 新一轮切清晰度：重置音轨重试额度（上一次用掉的重试不该影响这一次）
                    dashAudioRetryCount = 0
                    playerCore?.stop()
                    playerCore?.release()

                    video_url = newVideoUrl
                    currentQuality = appliedQuality

                    // 新一轮取流的 CDN 候选：优先用接口返回的排序结果，接口没给就至少带上主地址。
                    val freshVideoBackups = playerData.videoBackupUrls
                    videoBackupUrls = if (!freshVideoBackups.isNullOrEmpty()) ArrayList(freshVideoBackups)
                    else arrayListOf(newVideoUrl)
                    val freshAudioBackups = playerData.audioBackupUrls
                    audioBackupUrls = if (dashUsable && !freshAudioBackups.isNullOrEmpty())
                        ArrayList(freshAudioBackups) else ArrayList()
                    videoBackupIndex = videoBackupUrls.indexOf(newVideoUrl).takeIf { it >= 0 } ?: 0

                    val newValues = playerData.qnValueList
                    if (playerData.qnStrList != null && newValues != null) {
                        // 回退成合一 MP4 时服务端只回 720P/360P；若直接覆盖，清晰度列表会被降级，
                        // 用户再也点不到 1080P（连"再试一次"的入口都没了）。只有新列表不比旧列表
                        // 短档（最高档不降）时才采用它。
                        val oldMax = qnValueList?.maxOrNull() ?: -1
                        if ((newValues.maxOrNull() ?: -1) >= oldMax) {
                            qnStrList = playerData.qnStrList
                            qnValueList = newValues
                        }
                    }

                    loading_info.visibility = View.VISIBLE
                    anim_loading!!.start()
                    loading_text0.text = UiText.of("切换清晰度中")
                    isPrepared = false
                    isPlaying = false

                    playerCore = createPlayerOrBail() ?: return@runOnUiThread
                    progress_history = currentPosition

                    setDisplay()

                    dashAudioFallbackInFlight = false
                    unifiedFallbackAttempted = false
                    unifiedFallbackInFlight = false
                    if (allowDash && !dashUsable) {
                        MsgUtil.showMsg(UiText.of("这条视频暂时只能提供 720P"))
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    dashAudioFallbackInFlight = false
                    unifiedFallbackInFlight = false
                    MsgUtil.err(e)
                    MsgUtil.showMsg(UiText.of("清晰度切换失败"))
                }
            }
        }
    }

    private fun loadViewPoints() {
        CenterThreadPool.run {
            try {
                Logu.d("视频分段", "开始加载分段数据 aid=" + aid + " cid=" + cid)
                viewPoints = PlayerApi.getViewPoints(aid, cid)
                // 片头/片尾只是 view_points 的子集（type=1/2），这里单独提炼一份给自动跳过用。
                // 功能总开关 [SkipOpEdPrefs.FEATURE_ENABLED] 为假（即再次紧急撤回）时直接留空，
                // 不白跑 buildSegments：唯一的消费点 maybeAutoSkipOpEd 已被 SkipOpEdPrefs.isEnabled
                // 拦死，算了也没人看。所以「撤一次/恢复一次」都只需改那个常量，这里不用动。
                val segments = if (SkipOpEdPrefs.FEATURE_ENABLED) {
                    ViewPointSkip.buildSegments(
                        (viewPoints ?: emptyList()).map {
                            ViewPointSkip.Segment(it.type, it.from.toDouble(), it.to.toDouble())
                        }
                    )
                } else {
                    emptyList()
                }

                runOnUiThread {
                    if (destroyed) return@runOnUiThread
                    // 只有**片段内容真的变了**（换集/换P）才清空已处理集合与撤回位。
                    // 原先无条件 clear()：loadViewPoints() 在一次播放里会被调用多次
                    // （启动、投屏恢复等），响应晚到时会把「片头已处理」抹掉 → 下一轮定时器
                    // 重新命中 → 开播十几秒后自己又往前跳一次，每多拉一次多跳一次。
                    // 判据收敛在 SkipCancelPolicy.segmentsChanged（有单测）。
                    if (SkipCancelPolicy.segmentsChanged(skipSegments, segments)) {
                        skipHandled.clear()
                        lastSkippedSegment = null
                    }
                    skipSegments = segments

                    if (viewPoints != null && viewPoints!!.isNotEmpty()) {
                        // 只开了自动跳过的用户不需要这个按钮
                        if (SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_SHOW_VIEWPOINTS, true)) {
                            btn_viewpoint.visibility = View.VISIBLE
                            btn_viewpoint.setOnClickListener { showViewPointSelectorCard() }
                        }
                        Logu.d("视频分段", "成功加载 " + viewPoints!!.size + " 个分段")
                        maybeShowSkipGuide()
                    } else {
                        Logu.d("视频分段", "未获取到分段数据")
                    }
                }
            } catch (e: Exception) {
                Logu.e("视频分段", "加载失败: " + e.message)
                e.printStackTrace()
            }
        }
    }

    private fun showViewPointSelectorCard() {
        if (viewPoints == null || viewPoints!!.isEmpty()) return

        runOnUiThread {
            val viewPointRecycler = findViewById<RecyclerView>(R.id.viewpoint_selector_list)
            if (viewPointAdapter == null) {
                viewPointAdapter = ViewPointAdapter()
                viewPointAdapter!!.setData(viewPoints!!)
                viewPointAdapter!!.setOnItemClickListener { index ->
                    layout_card_bg.visibility = View.GONE
                    card_viewpoint_selector.visibility = View.GONE
                    if (index >= 0 && index < viewPoints!!.size) {
                        val vp = viewPoints!![index]
                        seekToPosition(vp.from * 1000L)
                        MsgUtil.showMsg(UiText.of("跳转到: ") + vp.content)
                    }
                }
                viewPointRecycler.layoutManager = CustomLinearManager(this, LinearLayoutManager.HORIZONTAL, false)
                viewPointRecycler.adapter = viewPointAdapter
            }
            if (isPrepared) {
                // 看点列表只需要"当前第几秒"这种整秒粒度，直接用 video_now，不必摸 native 锁
                val currentPos = video_now / 1000
                viewPointAdapter!!.updateCurrentPosition(currentPos)
            }
            layout_card_bg.visibility = View.VISIBLE
            card_viewpoint_selector.visibility = View.VISIBLE
        }
    }

    private fun loadInteractionVideo() {
        CenterThreadPool.run {
            try {
                val graphVersion = PlayerApi.getInteractionGraphVersion(aid, cid)
                if (graphVersion > 0) {
                    interactionGraphVersion = graphVersion
                    Logu.d("互动视频", "检测到互动视频，graph_version: " + interactionGraphVersion + ", cid: " + cid)

                    runOnUiThread {
                        questionShown = false
                        currentQuestion = null
                        interactionChoiceLayout?.visibility = View.GONE
                        interactionChoiceLayout?.removeAllViews()
                    }

                    var edgeId: Long = 0
                    if (initialEdgeId > 0) {
                        edgeId = initialEdgeId
                        initialEdgeId = 0
                    } else if (interactionData != null && currentEdgeId > 0) {
                        edgeId = currentEdgeId
                    }

                    interactionData = InteractionVideoApi.getEdgeInfo(aid, null, interactionGraphVersion, edgeId)
                    if (interactionData != null) {
                        currentEdgeId = interactionData!!.edgeId
                        Logu.d("互动视频", "成功加载互动视频数据，edge_id: " + currentEdgeId)
                        runOnUiThread { updateDebugButtonVisibility() }
                    }
                } else {
                    interactionData = null
                    currentEdgeId = 0
                    runOnUiThread {
                        questionShown = false
                        currentQuestion = null
                        updateDebugButtonVisibility()
                    }
                }
            } catch (e: Exception) {
                Logu.e("互动视频", "加载失败: " + e.message)
                e.printStackTrace()
                interactionData = null
                currentEdgeId = 0
                runOnUiThread {
                    questionShown = false
                    currentQuestion = null
                    updateDebugButtonVisibility()
                }
            }
        }
    }

    private fun updateDebugButtonVisibility() {
        val debugEnabled = SharedPreferencesUtil.getBoolean("player_interaction_debug", false)
        if (debugEnabled && interactionData != null && interactionData!!.hiddenVars != null && !interactionData!!.hiddenVars!!.isEmpty() && !isLiveMode && !isAudioOnlyMode) {
            btn_debug.visibility = layout_top.visibility
        } else {
            btn_debug.visibility = View.GONE
        }
    }

    private fun checkEndInteractionQuestions() {
        if (interactionData == null || interactionData!!.edges == null ||
            interactionData!!.edges!!.questions == null || questionShown) return

        for (question in interactionData!!.edges!!.questions!!) {
            if (question.type == 0) {
                if (question.choices != null && question.choices!!.isNotEmpty()) {
                    for (choice in question.choices!!) {
                        if (choice.isHidden == 1) continue

                        if (choice.condition != null && choice.condition!!.isNotEmpty()) {
                            if (!evaluateCondition(choice.condition!!)) continue
                        }

                        handleChoiceSelection(choice)
                        break
                    }
                }
                continue
            }
            showInteractionQuestion(question)
        }
    }

    private fun showInteractionQuestion(question: InteractionVideoData.InteractionQuestion) {
        if (questionShown || question.choices == null || question.choices!!.isEmpty()) return

        runOnUiThread {
            questionShown = true
            currentQuestion = question

            if (question.pauseVideo == 1 && isPlaying) {
                playerCore!!.pause()
                isPlaying = false
                btn_control.setImageResource(R.drawable.btn_player_play)
            }

            if (interactionChoiceLayout == null) createInteractionChoiceLayout()

            interactionChoiceLayout!!.removeAllViews()

            for (choice in question.choices!!) {
                if (choice.isHidden == 1) continue

                if (choice.condition != null && choice.condition!!.isNotEmpty()) {
                    if (!evaluateCondition(choice.condition!!)) continue
                }

                val choiceView = createChoiceView(choice)
                interactionChoiceLayout!!.addView(choiceView)
            }

            if (interactionChoiceLayout!!.childCount > 0) {
                interactionChoiceLayout!!.visibility = View.VISIBLE
            }
        }
    }

    private fun createChoiceView(choice: InteractionVideoData.InteractionChoice): TextView {
        val choiceView = LayoutInflater.from(this).inflate(R.layout.cell_interaction_choice, null) as TextView
        choiceView.text = choice.option
        // 玻璃质感：互动选项原是写死粉色按下态的 background_interaction_choice，换成玻璃选项（圆角沿用原件 12dp）
        if (GlassStyle.isEnabled()) {
            choiceView.background = GlassStyle.choiceBackground(this, false, 12f * resources.displayMetrics.density)
        }
        val fontSize = SharedPreferencesUtil.getFloat(
            SettingsKeys.PLAYER_INTERACTION_CHOICE_SIZE,
            SettingsKeys.PLAYER_INTERACTION_CHOICE_SIZE_DEFAULT
        )
        choiceView.textSize = fontSize
        choiceView.setOnClickListener { handleChoiceSelection(choice) }
        return choiceView
    }

    private fun createInteractionChoiceLayout() {
        val rootLayout = findViewById<RelativeLayout>(R.id.root_layout)
        interactionChoiceLayout = LinearLayout(this)
        interactionChoiceLayout!!.orientation = LinearLayout.VERTICAL
        interactionChoiceLayout!!.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL

        val params = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        params.setMargins(0, 0, 0, 100)

        interactionChoiceLayout!!.layoutParams = params
        interactionChoiceLayout!!.visibility = View.GONE
        rootLayout.addView(interactionChoiceLayout)
    }

    private fun evaluateCondition(condition: String): Boolean {
        if (interactionData == null || interactionData!!.hiddenVars == null || condition.isEmpty()) return true

        try {
            var result = condition
            for (v in interactionData!!.hiddenVars!!) {
                if (v.idV2 != null && v.idV2!!.isNotEmpty()) {
                    val pattern = "\\b" + Pattern.quote(v.idV2!!) + "\\b"
                    result = result.replace(pattern.toRegex(), v.value.toString())
                }
                if (v.id != null && v.id!!.isNotEmpty() && v.id != v.idV2) {
                    val pattern = "\\b" + Pattern.quote(v.id!!) + "\\b"
                    result = result.replace(pattern.toRegex(), v.value.toString())
                }
            }

            return evaluateExpression(result)
        } catch (e: Exception) {
            Logu.e("互动视频", "条件判断失败: " + e.message)
            return true
        }
    }

    private fun evaluateExpression(expr: String): Boolean {
        var stripped = expr.trim()
        try {
            stripped = expr.trim()
            if (stripped.contains(">=")) {
                val parts = stripped.split(">=")
                val left = parts[0].trim().toLong()
                val right = parts[1].trim().toLong()
                return left >= right
            } else if (stripped.contains("<=")) {
                val parts = stripped.split("<=")
                val left = parts[0].trim().toLong()
                val right = parts[1].trim().toLong()
                return left <= right
            } else if (stripped.contains(">")) {
                val parts = stripped.split(">")
                val left = parts[0].trim().toLong()
                val right = parts[1].trim().toLong()
                return left > right
            } else if (stripped.contains("<")) {
                val parts = stripped.split("<")
                val left = parts[0].trim().toLong()
                val right = parts[1].trim().toLong()
                return left < right
            } else if (stripped.contains("==")) {
                val parts = stripped.split("==")
                val left = parts[0].trim().toLong()
                val right = parts[1].trim().toLong()
                return left == right
            } else if (stripped.contains("!=")) {
                val parts = stripped.split("!=")
                val left = parts[0].trim().toLong()
                val right = parts[1].trim().toLong()
                return left != right
            }
        } catch (e: Exception) {
            Logu.e("互动视频", "表达式计算失败: " + stripped)
        }
        return true
    }

    private fun handleChoiceSelection(choice: InteractionVideoData.InteractionChoice) {
        hideInteractionChoices()

        if (choice.nativeAction != null && choice.nativeAction!!.isNotEmpty()) {
            executeNativeAction(choice.nativeAction!!)
        }

        CenterThreadPool.run {
            try {
                val targetEdgeId = choice.id
                val newData = InteractionVideoApi.getEdgeInfo(aid, null, interactionGraphVersion, targetEdgeId)

                if (newData == null) {
                    runOnUiThread { MsgUtil.showMsg(UiText.of("获取互动视频数据失败")) }
                    return@run
                }

                interactionData = newData
                currentEdgeId = newData.edgeId

                val targetCid = choice.cid
                if (targetCid > 0 && targetCid != cid) {
                    jumpToInteractionPage(targetCid, newData)
                } else {
                    resumePlaybackIfPaused()
                }
            } catch (e: Exception) {
                Logu.e("互动视频", "处理选择失败: " + e.message)
                runOnUiThread { MsgUtil.showMsg("处理选择失败: " + e.message) }
            }
        }
    }

    private fun hideInteractionChoices() {
        runOnUiThread {
            interactionChoiceLayout?.visibility = View.GONE
            questionShown = false
            currentQuestion = null
        }
    }

    private fun jumpToInteractionPage(targetCid: Long, newData: InteractionVideoData) {
        CenterThreadPool.run {
            try {
                val playerData = PlayerData()
                playerData.aid = aid
                playerData.cid = targetCid
                playerData.title = newData.title
                playerData.mid = mid
                playerData.qn = AccountStateStore.clampQuality(getTargetQuality())

                if (pagenames != null && cids != null) {
                    playerData.pagenames = pagenames
                    playerData.cids = cids
                    val newPageIndex = cids!!.indexOf(targetCid)
                    if (newPageIndex >= 0) {
                        playerData.currentPageIndex = newPageIndex
                        currentPageIndex = newPageIndex
                    }
                }

                // 互动跳转的清晰度同样按清晰度选接口（DASH 才能拿 1080P）
                if (epid != 0L) PlayerApi.getPlaybackBangumi(playerData)
                else PlayerApi.getPlaybackVideo(playerData)
                val interactionAudioUrl = playerData.audioUrl?.takeIf { it.isNotBlank() }
                // DASH 取流回退成合一 MP4 时服务端只给到 720P，不能继续显示请求的 1080P
                val interactionQuality =
                    if (playerData.qn > PlayerStreamPolicy.QN_720P && interactionAudioUrl == null) {
                        PlayerStreamPolicy.QN_720P
                    } else playerData.qn

                runOnUiThread {
                    if (destroyed) return@runOnUiThread

                    releaseAudioTrack()
                    audioTrackUrl = interactionAudioUrl
                    // 互动分页是另一条流，旧候选表作废（它的取流结果不走 backups 那套 extra）
                    videoBackupUrls = ArrayList()
                    audioBackupUrls = ArrayList()
                    videoBackupIndex = 0
                    playerCore?.stop()
                    playerCore?.release()
                    releaseDanmaku()

                    cid = targetCid
                    video_url = playerData.videoUrl
                    danmaku_url = playerData.danmakuUrl
                    text_title!!.text = newData.title
                    videoTitle = newData.title
                    currentEdgeId = newData.edgeId

                    if (playerData.qnStrList != null && playerData.qnValueList != null) {
                        qnStrList = playerData.qnStrList
                        qnValueList = playerData.qnValueList
                        currentQuality = interactionQuality
                    }

                    loading_info.visibility = View.VISIBLE
                    anim_loading!!.start()
                    loading_text0.text = UiText.of("加载互动分P")
                    isPrepared = false
                    isPlaying = false
                    finishWatching = false
                    progress_history = 0
                    subtitles = null
                    subtitleLinks = null
                    subtitle_selected = -1
                    viewPoints = null
                    viewPointAdapter = null
                    btn_viewpoint.visibility = View.GONE

                    interactionData = newData
                    currentQuestion = null
                    questionShown = false
                    interactionChoiceLayout?.visibility = View.GONE
                    interactionChoiceLayout?.removeAllViews()

                    playerCore = createPlayerOrBail() ?: return@runOnUiThread
                    bindDanmakuView()

                    setDisplay()

                    layout_control.postDelayed({
                        CenterThreadPool.run {
                            if (destroyed) return@run

                            runOnUiThread {
                                loading_text0.text = UiText.of("装填弹幕中")
                                loading_text1.text = "(≧∇≦)"
                            }

                            if (isOnlineVideo) {
                                danmakuFile = File(cacheDir, "danmaku.xml")
                                if (danmakuFile!!.exists()) danmakuFile!!.delete()
                                downdanmu()
                            }

                            if (!destroyed) {
                                maybeAutoSubtitle()
                            }

                            if (!destroyed && isOnlineVideo && aid > 0 && cid > 0) {
                                loadHighEnergyData()
                            }

                            if (!destroyed && isOnlineVideo && aid > 0 && cid > 0 && needViewPoints()) {
                                loadViewPoints()
                            }
                        }
                    }, 60)
                }
            } catch (e: Exception) {
                Logu.e("互动视频", "跳转失败: " + e.message)
                runOnUiThread { MsgUtil.showMsg("跳转失败: " + e.message) }
            }
        }
    }

    private fun resumePlaybackIfPaused() {
        runOnUiThread {
            if (currentQuestion != null && currentQuestion!!.pauseVideo == 1 && !isPlaying) {
                playerCore!!.start()
                isPlaying = true
                btn_control.setImageResource(R.drawable.btn_player_pause)
            }
        }
    }

    private fun getTargetQuality(): Int {
        val defaultQn = SharedPreferencesUtil.getInt(SettingsKeys.PLAY_QN, 16)
        if (qnValueList == null || qnValueList!!.isEmpty()) return if (currentQuality > 0) currentQuality else defaultQn

        for (qn in qnValueList!!) {
            if (qn == currentQuality) return currentQuality
        }

        return if (currentQuality > 0) currentQuality else defaultQn
    }

    private fun executeNativeAction(nativeAction: String) {
        if (interactionData == null || interactionData!!.hiddenVars == null || nativeAction.isEmpty()) return

        val actions = nativeAction.split(";")
        for (action in actions) {
            val trimmed = action.trim()
            if (trimmed.isEmpty()) continue

            try {
                if (trimmed.contains("=")) {
                    val parts = trimmed.split("=")
                    if (parts.size == 2) {
                        val varId = parts[0].trim()
                        val valueExpr = parts[1].trim()

                        val value = evaluateValueExpression(valueExpr)

                        for (v in interactionData!!.hiddenVars!!) {
                            if ((v.idV2 != null && v.idV2 == varId) ||
                                (v.id != null && v.id == varId)) {
                                v.value = value
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Logu.e("互动视频", "执行动作失败: " + trimmed)
            }
        }
    }

    private fun evaluateValueExpression(expr: String): Long {
        var stripped = expr.trim()
        try {
            stripped = expr.trim()
            if (stripped.contains("+")) {
                val parts = stripped.split("\\+".toRegex()).toTypedArray()
                var sum: Long = 0
                for (part in parts) sum += evaluateValueExpression(part.trim())
                return sum
            } else if (stripped.contains("-")) {
                val parts = stripped.split("-")
                var result = evaluateValueExpression(parts[0].trim())
                for (i in 1 until parts.size) result -= evaluateValueExpression(parts[i].trim())
                return result
            } else {
                if (interactionData != null && interactionData!!.hiddenVars != null) {
                    for (v in interactionData!!.hiddenVars!!) {
                        if ((v.idV2 != null && stripped == v.idV2) ||
                            (v.id != null && stripped == v.id)) {
                            return v.value
                        }
                    }
                }
                if (stripped.contains(".")) return stripped.toDouble().toLong()
                else return stripped.toLong()
            }
        } catch (e: Exception) {
            Logu.e("互动视频", "值表达式计算失败: " + stripped + ", 错误: " + e.message)
            return 0
        }
    }

    private fun showInteractionDebugDialog() {
        if (interactionData == null || interactionData!!.hiddenVars == null || interactionData!!.hiddenVars!!.isEmpty()) {
            MsgUtil.showMsg(UiText.of("当前没有互动视频变量"))
            return
        }

        InteractionDebugActivity.setInteractionData(interactionData!!)
        val intent = Intent(this, InteractionDebugActivity::class.java)
        startActivity(intent)
    }

    override fun onPrepared(mediaPlayer: IMediaPlayer) {
        // 丢弃旧实例的迟到回调，不能让它重建外部音轨或启动新的当前播放器。
        if (destroyed || mediaPlayer !== playerCore) return

        isPrepared = true
        video_all = playerCore!!.duration.toInt()

        changeVideoSize()

        // 起播弹幕：走 DanmakuManager 并带上当前播放位置。
        // 不能裸调 `mDanmakuView.start()`——DFM 的 START 会把时间轴锚到 0，
        // 续播/直播已经跑了一段时再 start 会让弹幕跳回开头闪一下。
        // 直播不绑媒体时钟，从头开始是正确的。
        if (isLiveMode || hasDanmaku) danmakuManager?.start(video_now.toLong())
        if (SharedPreferencesUtil.getBoolean("player_ui_showDanmakuBtn", true)) {
            // 「播放默认值 → 弹幕」：开/关直接决定；沿用上次读 pref_switch_danmaku
            // （沿用既有键，默认开 —— 与改造前完全一致）。
            isDanmakuVisible = PlayerDefaults.resolveTriState(
                SharedPreferencesUtil.getString(SettingsKeys.PLAYER_DEFAULT_DANMAKU, SettingsKeys.PLAYER_DEFAULT_MODE_LAST),
                SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_LAST_DANMAKU, true)
            )
            // 直接把视图与图标设成解析结果。旧写法是"先设成反值、再 performClick() 翻一次"，
            // 那种取反技巧叠加三种默认模式后很容易对不上（开/关会互相打架），改成直白设置。
            if (mDanmakuView != null) {
                if (isDanmakuVisible) mDanmakuView!!.show() else mDanmakuView!!.hide()
            }
            btn_danmaku.setImageResource(if (isDanmakuVisible) R.mipmap.danmakuon else R.mipmap.danmakuoff)
            btn_danmaku.setOnClickListener {
                if (mDanmakuView == null) return@setOnClickListener
                if (isDanmakuVisible) mDanmakuView!!.hide()
                else mDanmakuView!!.show()
                btn_danmaku.setImageResource(if (isDanmakuVisible) R.mipmap.danmakuoff else R.mipmap.danmakuon)
                isDanmakuVisible = !isDanmakuVisible
                // 「播放默认值 → 弹幕 = 沿用上次」的记录位
                SharedPreferencesUtil.putBoolean(SettingsKeys.PLAYER_LAST_DANMAKU, isDanmakuVisible)
            }
            btn_danmaku.visibility = View.VISIBLE
        } else btn_danmaku.visibility = View.GONE

        if (!isLiveMode) {
            if (loop_enabled) btn_loop.setImageResource(R.mipmap.loopon)
            else btn_loop.setImageResource(R.mipmap.loopoff)
            btn_loop.setOnClickListener {
                btn_loop.setImageResource(if (loop_enabled) R.mipmap.loopoff else R.mipmap.loopon)
                loop_enabled = !loop_enabled
                // 「播放默认值 → 循环播放 = 沿用上次」的记录位
                SharedPreferencesUtil.putBoolean(SettingsKeys.PLAYER_LAST_LOOP, loop_enabled)
            }
            btn_loop.visibility = View.VISIBLE

            // 「播放默认值 → 倍速」：首播套用默认值；之后（切清晰度/切听视频/重试的重建）
            // 沿用本次会话里滑条上的选择，别掉回 1x。
            if (!speedDefaultApplied) {
                speedDefaultApplied = true
                applyDefaultSpeed()
            } else {
                applySpeedFromSeekbar()
            }

            if (isLocalAudioFile) {
                btn_audio_only.visibility = View.GONE
            } else {
                updateAudioOnlyButton()
                btn_audio_only.setOnClickListener { toggleAudioOnlyMode() }
                btn_audio_only.visibility = View.VISIBLE
            }

            if (hasMultiplePages()) {
                btn_page_selector.visibility = View.VISIBLE
                btn_page_selector.setOnClickListener { showPageSelectorCard() }
                btn_auto_next.visibility = View.VISIBLE
                updateAutoNextButton()
                btn_auto_next.setOnClickListener { toggleAutoNext() }
            } else {
                btn_page_selector.visibility = View.GONE
                btn_auto_next.visibility = View.GONE
            }

            if (SharedPreferencesUtil.getBoolean("player_ui_showQualityBtn", true) && isOnlineVideo) {
                btn_quality.visibility = View.VISIBLE
                btn_quality.setOnClickListener { showQualitySelectorCard() }
            } else {
                btn_quality.visibility = View.GONE
            }

            if (!SharedPreferencesUtil.getBoolean("player_ui_showPageBtn", true))
                btn_page_selector.visibility = View.GONE
        } else {
            btn_loop.visibility = View.GONE
            btn_audio_only.visibility = View.GONE
            btn_page_selector.visibility = View.GONE
            btn_auto_next.visibility = View.GONE
            btn_quality.visibility = View.GONE
        }

        seekbar_progress.max = video_all
        progress_str = StringUtil.toTime(video_all / 1000)

        if (isAudioOnlyMode) updateAudioOnlyUI()

        if (!isLiveMode) {
            val resumeTarget = when {
                // ① 系统重建本页：必须回到原位，且不受"从上次位置播放"开关影响
                restoredFromSaveState -> progress_history
                // ② 首次进入且开关开启：按历史进度续播
                SharedPreferencesUtil.getBoolean("player_from_last", true) -> progress_history
                else -> 0L
            }
            // 起播时把弹幕时钟锚到视频位置（0 也要锚）。
            // DFM 的时钟是以 prepare 时刻为基准自走的（DrawHandler.PREPARE 里 mTimeBase = uptimeMillis），
            // 起播前那段缓冲时间里它会一直跑在视频前面；而我们的容差校正只改 timer、不改 mTimeBase，
            // 于是 syncTimer 里的 gapTime 又把它拽回去，"跑快→拽回"反复发生 —— 真机实测 2 秒内校正 84 次、
            // 瞬时偏差最大 1803ms，肉眼就是弹幕一抖一抖。seekTo 会同时重设 mTimeBase（DrawHandler SEEK_POS）。
            //
            // 同时要更新 video_now（markSeekTarget）：它现在是媒体时钟的时间源（bindMediaClock），
            // 不更新的话弹幕会先按旧位置推进一段再被吸附回来，肉眼就是起播闪一下。
            markSeekTarget(resumeTarget)
            if (resumeTarget > 5) {
                playerCore!!.seekTo(resumeTarget)
                Logu.d("进度跳转", resumeTarget.toString())
                // 只有"按历史进度续播"才提示；恢复现场（系统重建）不该再提示一次
                if (!restoredFromSaveState) runOnUiThread { MsgUtil.showMsg(UiText.of("已从上次的位置播放")) }
            }
            if (hasDanmaku && mDanmakuView != null) mDanmakuView!!.seekTo(resumeTarget)
        }

        loading_info.visibility = View.GONE
        anim_loading!!.stop()

        isPlaying = true

        btn_control.setImageResource(if (isPlaying) R.drawable.btn_player_pause else R.drawable.btn_player_play)

        text_speed.visibility = layout_top.visibility
        if (isLiveMode) text_speed.visibility = View.GONE
        text_speed.setOnClickListener { layout_speed.visibility = View.VISIBLE }
        layout_speed.setOnClickListener { layout_speed.visibility = View.GONE }

        btn_debug.setOnClickListener { showInteractionDebugDialog() }
        updateDebugButtonVisibility()

        progressChange()
        onlineChange()

        // 判断实际轨道，含内嵌音轨时禁止再启动第二路音频；重复 prepared 也不会遗留旧实例。
        initAudioTrack(mediaPlayer)
        // 新建的 IjkMediaPlayer/外部音轨默认增益都是 1.0，必须把用户记忆的「无极调音」重新打上去，
        // 否则换清晰度、切 P、出错重试之后音量会跳回满格。
        applyVolumeGain()

        if (isPlaying) {
            playerCore!!.start()
        }

        if (isPlaying && playerCore is UnifiedDashPlayer) {
            scheduleUnifiedHealthCheck()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
            updateMediaSessionMetadata()
            updateMediaSessionPlaybackState()
        }

        btn_control.setOnClickListener { controlVideo() }
        btn_subtitle.setOnClickListener { CenterThreadPool.run { downSubtitle(true) } }

        if (isShortVideoMode && ::btn_video_info.isInitialized) {
            btn_video_info.visibility = View.VISIBLE
            btn_video_info.setOnClickListener {
                val intent = Intent(this, VideoInfoActivity::class.java)
                intent.putExtra("aid", aid)
                startActivity(intent)
            }
        }
    }

    override fun finish() {
        // 锁定期间拦下一切 finish：手表右滑返回是在窗口层直接 finish 的（不走 onBackPressed），
        // 只有在这里兜一道，才能真正做到「必须解锁才能离开播放页」。
        // 不丢请求：先记住，解锁时再补执行（见 onLockButtonClick）。
        if (interceptLockedExit()) {
            pendingFinish = true
            return
        }
        if (isPlaying) playerPause()
        // 退出前把最后进度报一次。返回 true 说明请求确实发出去了，就用 progressReported 标记告诉
        // 调用页（JumpToPlayerActivity）别再报一遍——否则同一进度会被写两次
        val progressReported = reportProgressNow(true, UiText.of("退出前"))
        // 本地视频没有服务端历史：在这里落一次盘，等价于在线视频的"退出前上报"
        saveLocalProgressNow("退出前")
        if (playerCore != null) {
            val result = Intent()
            result.putExtra("progress", playerCore!!.currentPosition.toInt())
            // 回传最终观看的 cid：播放器内可以切分P，上一页若沿用"进入时"的旧 cid，
            // 就会拿新P的进度去覆盖旧P的历史记录，把正确的续播位置冲掉
            result.putExtra("cid", cid)
            if (progressReported) result.putExtra("progressReported", true)
            result.putExtra("isPlaying", isPlaying)
            result.putExtra("isDanmakuEnabled", !isDanmakuVisible)
            result.putExtra("quality", currentQuality)
            Logu.d("进度回传", playerCore!!.currentPosition.toString())
            setResult(RESULT_OK, result)
        } else setResult(RESULT_CANCELED)
        super.finish()
    }

}
