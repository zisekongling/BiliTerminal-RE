package com.RobinNotBad.BiliClient.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.MimeTypes;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import tv.danmaku.ijk.media.player.AbstractMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.MediaInfo;
import tv.danmaku.ijk.media.player.misc.IMediaDataSource;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;

/**
 * 统一时钟 DASH 播放器（ExoPlayer 内核），实现本仓库既有的 {@link IMediaPlayer} 契约。
 *
 * <h3>为什么需要它</h3>
 * 旧方案是「IjkMediaPlayer 播纯视频 DASH 流 + android.media.MediaPlayer 播纯音频 DASH 流」，
 * 两个播放器各有一套时钟，靠轮询比较/校正位置，结果就是音画不同步，且音频轨经常不加载。
 * 官方客户端和兄弟仓库 BiliTerminal-Revival 的做法都是**一个播放器时钟同时拥有音、视频两条轨**，
 * 本类把 DASH 的视频流和音频流用 {@link MergingMediaSource} 合并成一个 MediaSource 交给
 * 单个 {@link SimpleExoPlayer}：视频渲染、音频输出、seek、倍速、暂停全部由同一个时钟驱动，
 * 不存在「谁等谁」的问题，也就不需要轮询校正。
 *
 * <h3>线程模型（关键约束）</h3>
 * SimpleExoPlayer 的**所有** API 调用都必须在创建它的 Looper（这里固定是主线程）上执行，
 * 否则会抛 IllegalStateException 或被内部断言拦下。因此本类对外的方法可以在任意线程调用，
 * 内部统一经 {@link #runOnMain(Runnable)} / {@link #callOnMain(Callable, Object)} 切到主线程：
 * 有返回值的（getCurrentPosition 等）用 FutureTask 同步等待结果，没返回值的直接 post。
 * 构造方法只保存参数，**不创建** ExoPlayer——这样构造不会因为播放内核初始化失败而崩溃，
 * 真正的内核在第一次 {@code prepareAsync()} 的主线程里创建，失败则走 notifyOnError + 日志。
 *
 * <h3>生命周期</h3>
 * {@link #release()} 之后置 {@code released} 标志，此后所有方法一律空转（getter 返回兜底值），
 * 避免 release 与后台线程的 get/set 竞争导致操作已释放的 ExoPlayer。
 */
public final class ExoDashMediaPlayer extends AbstractMediaPlayer
        implements UnifiedDashPlayer, IMediaPlayer {

    private static final String TAG = "ExoDashMediaPlayer";

    /**
     * 手表内存小：旧方案让 ExoPlayer 用默认缓冲（最长 50s、targetBufferBytes 无上限）在低内存
     * 手表上很容易被系统杀。这里显式收紧：最长缓冲 15s（播放中）/ 30s（暂停时预载上限），
     * 起播只需 1.5s、重缓冲后 3s 即可继续；总字节上限 16MB。
     */
    private static final int BUFFER_MIN_MS = 15_000;
    private static final int BUFFER_MAX_MS = 30_000;
    private static final int BUFFER_FOR_PLAYBACK_MS = 1_500;
    private static final int BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 3_000;
    private static final int TARGET_BUFFER_BYTES = 16 * 1024 * 1024;

    /** progressive 流的续加载检查间隔：每读 256KB 检查一次缓冲水位；越小越快发现缓冲不足，但请求更碎。 */
    private static final int CONTINUE_LOADING_CHECK_INTERVAL_BYTES = 256 * 1024;

    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    /** 本类所有 ExoPlayer 调用都投递到主线程执行。 */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context context;

    /** 构造时传入的音频地址（setAudioSource 未调用时的兜底值）。 */
    private final String constructorAudioUrl;

    /** 主线程创建、主线程访问；volatile 只为让其他线程做空值判断时不读到半初始化对象。 */
    private volatile SimpleExoPlayer player;
    private volatile boolean released;

    // ── 媒体源参数（可在任意线程写入，prepare 时在主线程读取）─────────────────────
    private volatile String videoUrl;
    private volatile Map<String, String> videoHeaders;
    private volatile String resolvedAudioUrl;
    private volatile Map<String, String> audioHeaders;
    /**
     * 「听视频 / 纯音频」模式。true 时 prepare 完全不创建视频轨数据源：
     * B 站听视频场景只该拉音频流，旧方案在这里会抛 IJK -10000。
     * volatile 保证 prepareInternal 在主线程读取时能看到 setAudioOnly 的写入。
     */
    private volatile boolean audioOnly;

    // ── surface（setDisplay / setSurface 记录，建 player 时与后续变更时套用）──────
    private volatile SurfaceHolder surfaceHolder;
    private volatile Surface surface;

    // ── 状态（只在主线程读写，个别 getter 字段用 volatile）───────────────────────
    private boolean preparedNotified;
    private boolean buffering;
    private volatile int videoWidth;
    private volatile int videoHeight;
    /** 兄弟仓库里从未赋值的字段（它的 bug）：onTracksChanged 时记下视频轨 Format 供 getMediaInfo 用。 */
    private volatile Format videoFormat;
    private volatile float playbackSpeed = 1.0f;
    private float pendingVolume = 1.0f;
    private boolean looping;
    private boolean pendingPlay;
    /** prepare 之前调用 seekTo 时先记下，prepare 完成后再补一次。 */
    private long pendingSeekMs = C.TIME_UNSET;

    /** 播放器事件 → IMediaPlayer 回调的状态映射。 */
    private final Player.Listener playerListener = new Player.Listener() {

        @Override
        public void onPlaybackStateChanged(int state) {
            if (released) return;
            // 1=IDLE 2=BUFFERING 3=READY 4=ENDED。排查“卡住”时先看这条：
            // ENDED 说明是播放完了（哪怕进度条还停在中间），BUFFERING 才是真的没数据。
            Logu.d("播放状态", "state=" + state + " pos="
                    + (player != null ? player.getCurrentPosition() : -1));
            if (state == Player.STATE_BUFFERING) {
                if (!buffering) {
                    buffering = true;
                    notifyOnInfo(IMediaPlayer.MEDIA_INFO_BUFFERING_START, 0);
                }
            } else if (state == Player.STATE_READY) {
                if (buffering) {
                    buffering = false;
                    notifyOnInfo(IMediaPlayer.MEDIA_INFO_BUFFERING_END, 0);
                }
                // 只通知一次 onPrepared：音频轨后挂导致的「重建媒体源」不应该让上层误以为
                // 是一次全新的准备流程（否则上层可能重复初始化弹幕、重复统计等）。
                if (!preparedNotified) {
                    preparedNotified = true;
                    notifyOnPrepared();
                }
            } else if (state == Player.STATE_ENDED) {
                notifyOnCompletion();
            }
        }

        @Override
        public void onIsLoadingChanged(boolean isLoading) {
            if (released) return;
            Logu.v("加载状态", "isLoading=" + isLoading);
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            if (released) return;
            buffering = false;
            // ExoPlayer 的错误码里 2000~2999 全是 IO 类（网络、HTTP 状态、文件、权限……），
            // 其余（解析失败、解码器初始化失败等）统一归为未知，上层据此决定「重试网络」还是「换线路」。
            int code = IMediaPlayer.MEDIA_ERROR_UNKNOWN;
            if (error.errorCode >= PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                    && error.errorCode < 3000) {
                code = IMediaPlayer.MEDIA_ERROR_IO;
            }
            Logu.e(TAG, "播放错误 errorCode=" + error.errorCode
                    + " (" + error.getErrorCodeName() + ")", error);
            notifyOnError(code, error.errorCode);
        }

        @Override
        public void onVideoSizeChanged(com.google.android.exoplayer2.video.VideoSize size) {
            if (released) return;
            // VideoSize 的 width/height 是旋转前的编码尺寸；上层按 1:1 的 SAR 处理即可（B 站无变形视频）。
            if (size.width > 0) videoWidth = size.width;
            if (size.height > 0) videoHeight = size.height;
            notifyOnVideoSizeChanged(videoWidth, videoHeight, 1, 1);
        }

        @Override
        public void onRenderedFirstFrame() {
            if (released) return;
            notifyOnInfo(IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START, 0);
        }

        @Override
        public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
                                            Player.PositionInfo newPosition,
                                            int reason) {
            if (released) return;
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                notifyOnSeekComplete();
            }
        }

        @Override
        public void onTracksChanged(Tracks tracks) {
            if (released) return;
            // 兄弟实现里 videoFormat 声明了却从未赋值，导致 getMediaInfo() 的 videoDecoder
            // 永远是 null。这里在轨道确定后把视频轨的 Format 记下来（第一个视频轨）。
            Format found = null;
            for (Tracks.Group group : tracks.getGroups()) {
                if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
                for (int i = 0; i < group.length; i++) {
                    Format format = group.getTrackFormat(i);
                    if (format != null) {
                        found = format;
                        break;
                    }
                }
                if (found != null) break;
            }
            if (found != null) {
                videoFormat = found;
                // onVideoSizeChanged 有时会比 onTracksChanged 更晚到，先拿 Format 里的尺寸兜底，
                // 保证上层第一次查 getVideoWidth/Height 不一定是 0。
                if (videoWidth <= 0 && found.width > 0) videoWidth = found.width;
                if (videoHeight <= 0 && found.height > 0) videoHeight = found.height;
                Logu.d(TAG, "视频轨格式已记录：" + found.sampleMimeType
                        + " " + found.width + "x" + found.height);
            }
        }
    };

    public ExoDashMediaPlayer(Context context, @Nullable String audioUrl) {
        // 必须用 ApplicationContext：播放器可能比 Activity 活得久，持有 Activity 会泄漏。
        this.context = context.getApplicationContext();
        this.constructorAudioUrl = audioUrl == null ? "" : audioUrl;
    }

    // ==================== 线程工具 ====================

    /** 把无返回值的操作切到主线程执行；release 之后一律丢弃。 */
    private void runOnMain(Runnable action) {
        if (released) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!released) action.run();
        } else {
            mainHandler.post(() -> {
                if (!released) action.run();
            });
        }
    }

    /**
     * 把有返回值的操作切到主线程同步执行。
     *
     * <p>加 2 秒超时而不是无限 {@code get()}：主线程若被其他逻辑卡住（ANR 边缘），
     * 查询类方法（getCurrentPosition 等）宁可返回兜底值也不能再拖死调用线程。
     * 注意 ExoPlayer 内部不会因为释放而永久阻塞，正常情况下这点时间绰绰有余。
     */
    private <T> T callOnMain(Callable<T> action, T fallback) {
        if (released) return fallback;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                return action.call();
            } catch (Throwable t) {
                return fallback;
            }
        }
        FutureTask<T> task = new FutureTask<>(action);
        if (!mainHandler.post(task)) return fallback;
        try {
            return task.get(2, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    // ==================== 播放器与媒体源 ====================

    /**
     * 延迟创建 SimpleExoPlayer（只能在主线程调用）。
     *
     * <p>构造时不建：ExoPlayer 初始化涉及音频焦点/渲染器等系统资源，抛出的 Throwable 应该发生在
     * prepare 阶段由上层决定是否回退到旧播放器，而不是在 Activity 构造播放器对象时炸掉。
     */
    private SimpleExoPlayer ensurePlayer() {
        if (player != null) return player;
        // 手表内存约束见 BUFFER_* 常量注释；setPrioritizeTimeOverSizeThresholds(true)
        // 表示时间优先：宁可多占一点字节也要保证起播/续播的流畅度。
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(BUFFER_MIN_MS, BUFFER_MAX_MS,
                        BUFFER_FOR_PLAYBACK_MS, BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
                .setTargetBufferBytes(TARGET_BUFFER_BYTES)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
        SimpleExoPlayer exo = new SimpleExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build();
        // 把创建前记录的状态套用上去（这些 setter 在 prepare 前后调用都合法）。
        exo.setPlaybackSpeed(playbackSpeed);
        exo.setVolume(pendingVolume);
        exo.setRepeatMode(looping ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);
        player = exo;
        attachSurfaceInternal();
        return exo;
    }

    /** 当前生效的音频地址：setAudioSource 设置的优先，否则用构造时传入的。 */
    private String effectiveAudioUrl() {
        return resolvedAudioUrl == null || resolvedAudioUrl.isEmpty()
                ? constructorAudioUrl : resolvedAudioUrl;
    }

    /** 音频流应使用的请求头：单独给了就用单独的，否则沿用视频流的。 */
    private Map<String, String> effectiveAudioHeaders() {
        return (audioHeaders == null || audioHeaders.isEmpty()) ? videoHeaders : audioHeaders;
    }

    /**
     * 根据当前记录的视频/音频地址与「听视频」开关构造 MediaSource。
     *
     * @return 构造好的媒体源；若当前模式下没有任何可用地址则返回 null（由调用方报错）
     */
    private MediaSource buildMediaSource() {
        // 听视频模式：只建音频轨的 MediaSource。**不**创建 videoSource、**不**用 MergingMediaSource，
        // 这样完全不会去请求/下载纯视频 DASH 轨，也没有视频解码开销；
        // 旧双播放器方案在这条路径上会抛 IJK -10000，统一时钟方案从根上绕开。
        if (audioOnly) {
            String audioUrl = effectiveAudioUrl();
            if (audioUrl.isEmpty()) return null;
            DataSource.Factory audioFactory = createDataSourceFactory(effectiveAudioHeaders());
            return createSource(audioUrl, audioFactory, MimeTypes.AUDIO_MP4);
        }

        if (videoUrl == null || videoUrl.isEmpty()) return null;
        DataSource.Factory videoFactory = createDataSourceFactory(videoHeaders);
        MediaSource videoSource = createSource(videoUrl, videoFactory, MimeTypes.VIDEO_MP4);

        String audioUrl = effectiveAudioUrl();
        if (audioUrl.isEmpty()) return videoSource;

        DataSource.Factory audioFactory = createDataSourceFactory(effectiveAudioHeaders());
        MediaSource audioSource = createSource(audioUrl, audioFactory, MimeTypes.AUDIO_MP4);

        // 两个 true 很关键，缺一不可：
        //  1) adjustPeriodTimeOffsets = true —— B 站 DASH 的视频/音频是两个独立的 fragmented MP4，
        //     各自的 period 起始时间戳可能不同（edit list / 首帧 pts 偏移）。不校正的话
        //     MergingMediaSource 会把它们当成错开的两段，音画从一开始就差一截。
        //  2) clipDurations = true —— 两条流的总时长经常差几十毫秒到几百毫秒（编码尾部/Fragmented
        //     MP4 的 duration 取整）。不裁到较短轨，合并后的时间线会比实际内容长，
        //     播放到末尾可能出现「一个轨道结束了另一个还在等」的卡顿甚至起播/循环异常。
        return new MergingMediaSource(true, true, videoSource, audioSource);
    }

    /**
     * 把一个视频/音频地址包装成 ProgressiveMediaSource。
     *
     * <p>B 站 DASH 的分片就是标准 fragmented MP4，但 CDN 响应不一定带准确的 Content-Type，
     * 地址也常常没有 .mp4 后缀。这里显式用 MediaItem.setMimeType 告诉 ExoPlayer 轨道类型，
     * 避免它在 prepare 阶段反复「猜 extractor」烧时间；ConstantBitrateSeeking 让无索引的
     * fMP4 也能做出较精确的 seek。
     */
    private MediaSource createSource(String url, DataSource.Factory factory, String mimeType) {
        MediaItem item = new MediaItem.Builder()
                .setUri(toUri(url))
                .setMimeType(mimeType)
                .build();
        DefaultExtractorsFactory extractors = new DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true);
        return new ProgressiveMediaSource.Factory(factory, extractors)
                .setContinueLoadingCheckIntervalBytes(CONTINUE_LOADING_CHECK_INTERVAL_BYTES)
                .createMediaSource(item);
    }

    /**
     * Uri 归一化：带 scheme 的（http/https/file/…) 原样用；没有 scheme 的按本地绝对路径处理。
     *
     * <p>PlayerActivity 下载缓存后播放走的就是本地绝对路径（无 scheme），先用
     * {@link Uri#fromFile(File)} 转成 file:// 形式，让路径里的空格/中文被正确转义，
     * 交给 DefaultDataSource 的 FileDataSource 读取。
     */
    private Uri toUri(String url) {
        Uri parsed = Uri.parse(url);
        if (parsed.getScheme() != null) return parsed;
        if (url.startsWith("/")) return Uri.fromFile(new File(url));
        return parsed;
    }

    private DataSource.Factory createDataSourceFactory(@Nullable Map<String, String> requestHeaders) {
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                // B 站对 UA 有校验：用 NetWorkUtil 里统一的 Web UA，和 API 请求保持一致
                .setUserAgent(NetWorkUtil.USER_AGENT_WEB)
                .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(READ_TIMEOUT_MS)
                // B 站 CDN 会做 http→https / 跨域跳转，不允许的话直接 403/协议错误
                .setAllowCrossProtocolRedirects(true);
        if (requestHeaders != null && !requestHeaders.isEmpty()) {
            // Referer / Cookie 等由上层通过 setDataSource(..., Map) 传进来
            httpFactory.setDefaultRequestProperties(requestHeaders);
        }
        // 用 DefaultDataSource 包一层：http(s) 走上面的 httpFactory，本地 file 路径走 FileDataSource，
        // 这样同一个播放器同时支持在线流和下载缓存文件。
        return new DefaultDataSource.Factory(context, httpFactory);
    }

    // ==================== source 准备 / 重建 ====================

    /** prepareAsync 的实际实现（主线程执行）。 */
    private void prepareInternal() {
        if (released) return;
        SimpleExoPlayer exo;
        try {
            exo = ensurePlayer();
        } catch (Throwable t) {
            // 不吞异常：内核初始化失败交由上层兜底（回退旧播放器），这里把原因记清楚。
            Logu.e(TAG, "ExoPlayer 初始化失败，无法播放 DASH 流", t);
            notifyOnError(IMediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        }
        // 注意：地址合法性交给 buildMediaSource 判断——听视频模式下只要求音频地址，
        // 不要求 setDataSource 传入视频地址；普通模式才要求视频地址。
        MediaSource source;
        try {
            source = buildMediaSource();
        } catch (Throwable t) {
            Logu.e(TAG, "构造 MediaSource 失败", t);
            notifyOnError(IMediaPlayer.MEDIA_ERROR_MALFORMED, 0);
            return;
        }
        if (source == null) {
            if (audioOnly) {
                Logu.e(TAG, "听视频模式已开启，但没有设置音频流地址（setAudioSource）");
            } else {
                Logu.e(TAG, "prepareAsync 之前没有通过 setDataSource 设置视频地址");
            }
            notifyOnError(IMediaPlayer.MEDIA_ERROR_MALFORMED, 0);
            return;
        }
        preparedNotified = false;
        buffering = false;
        videoFormat = null;
        videoWidth = 0;
        videoHeight = 0;
        exo.setPlaybackSpeed(playbackSpeed);
        exo.setMediaSource(source);
        exo.prepare();
        if (pendingSeekMs != C.TIME_UNSET) {
            exo.seekTo(Math.max(0L, pendingSeekMs));
        }
        // start() 可能在 prepareAsync() 之后立刻被调用（玩家层的常见顺序），记录的状态在这里补发。
        if (pendingPlay) exo.play();
    }

    /**
     * setAudioSource 在**准备完成之后**被调用：重建媒体源，尽量保持当前播放位置。
     *
     * <p>ExoPlayer 没有「动态往已有时间线里插一条音频轨」的 API，只能换 MediaSource 重新 prepare。
     * 用 {@link SimpleExoPlayer#setMediaSource(MediaSource, long)} 把新源从旧位置起播，
     * 避免用户看到画面跳回开头。preparedNotified 刻意不复位：对上层来说这仍是一次播放，
     * 不该再收到一次 onPrepared。
     */
    private void applyAudioSourceChangeOnMain() {
        if (released) return;
        SimpleExoPlayer exo = player;
        // 还没 prepare 或还没到过 READY：下一次 prepareAsync 自然会带上新音频地址，无需重建。
        if (exo == null || !preparedNotified) return;
        long position = Math.max(0L, exo.getCurrentPosition());
        boolean wasPlaying = exo.isPlaying() || pendingPlay;
        MediaSource source;
        try {
            source = buildMediaSource();
        } catch (Throwable t) {
            Logu.e(TAG, "音频源变更后重建 MediaSource 失败", t);
            notifyOnError(IMediaPlayer.MEDIA_ERROR_MALFORMED, 0);
            return;
        }
        if (source == null) {
            Logu.e(TAG, "音频源变更后没有可用的媒体源（听视频模式缺音频地址）");
            notifyOnError(IMediaPlayer.MEDIA_ERROR_MALFORMED, 0);
            return;
        }
        try {
            // 从旧位置继续；prepare() 后会重新进入 BUFFERING → READY。
            exo.setMediaSource(source, position);
            exo.prepare();
            if (wasPlaying) {
                exo.play();
            } else {
                exo.pause();
            }
            Logu.d(TAG, "已按新音频源重建媒体源，保留位置 " + position + "ms");
        } catch (Throwable t) {
            Logu.e(TAG, "音频源变更后重新 prepare 失败", t);
            notifyOnError(IMediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
        }
    }

    // ==================== surface ====================

    /** 把已记录的 SurfaceHolder / Surface 套到当前 player 上（主线程调用）。 */
    private void attachSurfaceInternal() {
        SimpleExoPlayer exo = player;
        if (exo == null) return;
        if (surfaceHolder != null) {
            exo.setVideoSurfaceHolder(surfaceHolder);
        } else if (surface != null) {
            exo.setVideoSurface(surface);
        }
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (released) return;
        this.surfaceHolder = holder;
        this.surface = null;
        runOnMain(() -> {
            SimpleExoPlayer exo = player;
            if (exo != null) exo.setVideoSurfaceHolder(holder);
        });
    }

    @Override
    public void setSurface(Surface surface) {
        if (released) return;
        this.surfaceHolder = null;
        this.surface = surface;
        runOnMain(() -> {
            SimpleExoPlayer exo = player;
            if (exo != null) exo.setVideoSurface(surface);
        });
    }

    // ==================== 数据源 ====================

    @Override
    public void setDataSource(Context context, Uri uri) throws IOException {
        setDataSource(context, uri, null);
    }

    @Override
    public void setDataSource(Context context, Uri uri, Map<String, String> headers)
            throws IOException {
        if (released) return;
        // 这里只记录地址与请求头，真正的 MediaSource 在 prepareAsync() 里构造：
        // 1) 音频地址可能在 setDataSource 之后才通过 setAudioSource 补上；
        // 2) 构造 MediaSource 会碰 ExoPlayer，必须回主线程。
        this.videoUrl = uri == null ? null : uri.toString();
        this.videoHeaders = headers == null ? null : new HashMap<>(headers);
    }

    @Override
    public void setDataSource(String path) throws IOException {
        if (released) return;
        this.videoUrl = path;
        this.videoHeaders = null;
    }

    @Override
    public void setDataSource(FileDescriptor fd) {
        // 手表客户端只播 http(s) 流/本地文件，不走 fd 重载
        throw new UnsupportedOperationException("ExoDashMediaPlayer 不支持 FileDescriptor 数据源");
    }

    @Override
    public void setDataSource(IMediaDataSource mediaDataSource) {
        throw new UnsupportedOperationException("ExoDashMediaPlayer 不支持 IMediaDataSource 数据源");
    }

    @Override
    public String getDataSource() {
        return videoUrl;
    }

    @Override
    public void prepareAsync() throws IllegalStateException {
        runOnMain(this::prepareInternal);
    }

    // ==================== 播放控制 ====================

    @Override
    public void start() throws IllegalStateException {
        if (released) return;
        // 记录意图：若 start() 早于 player 创建/早于 prepare 完成，prepareInternal 里会补发 play()。
        pendingPlay = true;
        runOnMain(() -> {
            SimpleExoPlayer exo = player;
            if (exo != null) exo.play();
        });
    }

    @Override
    public void pause() throws IllegalStateException {
        pendingPlay = false;
        runOnMain(() -> {
            SimpleExoPlayer exo = player;
            if (exo != null) exo.pause();
        });
    }

    @Override
    public void stop() throws IllegalStateException {
        pendingPlay = false;
        runOnMain(() -> {
            SimpleExoPlayer exo = player;
            if (exo != null) exo.stop();
        });
    }

    @Override
    public void seekTo(long msec) throws IllegalStateException {
        if (released) return;
        long target = Math.max(0L, msec);
        if (player == null) {
            // 还没 prepare：记下来，prepareInternal 里补一次
            pendingSeekMs = target;
            return;
        }
        runOnMain(() -> {
            SimpleExoPlayer exo = player;
            if (exo != null) exo.seekTo(target);
        });
    }

    @Override
    public boolean isPlaying() {

        return callOnMain(() -> {
            SimpleExoPlayer exo = player;
            return exo != null && exo.isPlaying();
        }, false);
    }

    @Override
    public long getCurrentPosition() {
        return callOnMain(() -> {
            SimpleExoPlayer exo = player;
            return exo == null ? 0L : Math.max(0L, exo.getCurrentPosition());
        }, 0L);
    }

    @Override
    public long getDuration() {
        return callOnMain(() -> {
            SimpleExoPlayer exo = player;
            if (exo == null) return 0L;
            long duration = exo.getDuration();
            // C.TIME_UNSET 是「未知时长」（直播/尚未解析完），对上层按 0 处理
            return duration == C.TIME_UNSET || duration < 0 ? 0L : duration;
        }, 0L);
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        // IMediaPlayer 是左右声道分开的，ExoPlayer 只有一个总音量：取平均是旧代码已经用的近似。
        float volume = (leftVolume + rightVolume) / 2.0f;
        pendingVolume = volume;
        runOnMain(() -> {
            SimpleExoPlayer exo = player;
            if (exo != null) exo.setVolume(volume);
        });
    }

    @Override
    public void setLooping(boolean looping) {
        this.looping = looping;
        runOnMain(() -> {
            SimpleExoPlayer exo = player;
            if (exo != null) {
                exo.setRepeatMode(looping ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);
            }
        });
    }

    @Override
    public boolean isLooping() {
        return callOnMain(() -> {
            SimpleExoPlayer exo = player;
            return exo != null
                    ? exo.getRepeatMode() == Player.REPEAT_MODE_ALL
                    : looping;
        }, looping);
    }

    @Override
    public void setPlaybackSpeed(float speed) {
        // 0 或负数是非法倍速，按原速处理，避免 ExoPlayer 内部除零/反转。
        playbackSpeed = speed > 0.0f ? speed : 1.0f;
        runOnMain(() -> {
            SimpleExoPlayer exo = player;
            if (exo != null) exo.setPlaybackSpeed(playbackSpeed);
        });
    }

    // ==================== 信息 / 其他 ====================

    @Override
    public int getVideoWidth() {
        return videoWidth;
    }

    @Override
    public int getVideoHeight() {
        return videoHeight;
    }

    @Override
    public void setScreenOnWhilePlaying(boolean screenOn) {
        SurfaceHolder holder = surfaceHolder;
        if (holder != null) holder.setKeepScreenOn(screenOn);
    }

    @Override
    public int getAudioSessionId() {
        // 没有 player 时按「未设置」返回 0（AudioManager 的约定）。
        return callOnMain(() -> {
            SimpleExoPlayer exo = player;
            return exo == null ? 0 : exo.getAudioSessionId();
        }, 0);
    }

    @Override
    public MediaInfo getMediaInfo() {
        MediaInfo info = new MediaInfo();
        info.mMediaPlayerName = "ExoPlayer DASH 统一时钟";
        // onTracksChanged 里记录的真实视频轨 MIME；还没解析出来时用 "exo" 占位。
        Format format = videoFormat;
        info.mVideoDecoder = format != null && format.sampleMimeType != null
                ? format.sampleMimeType : "exo";
        info.mVideoDecoderImpl = "ExoPlayer";
        info.mAudioDecoder = "exo";
        info.mAudioDecoderImpl = "ExoPlayer";
        return info;
    }

    @Override
    public int getVideoSarNum() {
        return 1;
    }

    @Override
    public int getVideoSarDen() {
        return 1;
    }

    @Override
    public ITrackInfo[] getTrackInfo() {
        // IjkPlayer 的音视频轨信息结构 ExoPlayer 没有等价物，也不需要对外暴露。
        return null;
    }

    @Override
    public void release() {
        synchronized (this) {
            if (released) return;
            released = true;
        }
        // teardown 必须执行，所以不能走 runOnMain（它会因为 released 而丢弃任务）。
        Runnable teardown = () -> {
            SimpleExoPlayer exo = player;
            player = null;
            if (exo != null) {
                try {
                    exo.removeListener(playerListener);
                } catch (Throwable ignored) {
                }
                try {
                    exo.release();
                } catch (Throwable t) {
                    Logu.e(TAG, "释放 ExoPlayer 时抛异常（已忽略）", t);
                }
            }
            preparedNotified = false;
            buffering = false;
            videoFormat = null;
            videoWidth = 0;
            videoHeight = 0;
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            teardown.run();
        } else {
            mainHandler.post(teardown);
        }
    }

    @Override
    public void reset() {
        runOnMain(() -> {
            // 保留 videoUrl / headers / 音频地址：reset 后允许再次 prepareAsync 重新播放。
            SimpleExoPlayer exo = player;
            if (exo != null) exo.stop();
            preparedNotified = false;
            buffering = false;
            videoFormat = null;
            videoWidth = 0;
            videoHeight = 0;
            pendingSeekMs = C.TIME_UNSET;
        });
    }

    // ── 兼容旧 IMediaPlayer 的弃用/可选方法（无实际语义，给空实现）────────────────

    @Override
    @Deprecated
    public void setLogEnabled(boolean enable) {
    }

    @Override
    @Deprecated
    public boolean isPlayable() {
        return !released && player != null;
    }

    @Override
    public void setAudioStreamType(int streamtype) {
    }

    @Override
    @Deprecated
    public void setKeepInBackground(boolean keepInBackground) {
    }

    @Override
    @Deprecated
    public void setWakeMode(Context context, int mode) {
    }

    // ==================== UnifiedDashPlayer ====================

    @Override
    public void setAudioSource(String audioUrl, Map<String, String> headers) {
        if (released) return;
        this.resolvedAudioUrl = audioUrl == null ? "" : audioUrl;
        this.audioHeaders = headers == null ? null : new HashMap<>(headers);
        // 兼容旧调用习惯：音频头和视频头在 B 站是同一套，视频头为空时用音频头兜底。
        if ((this.videoHeaders == null || this.videoHeaders.isEmpty())
                && headers != null && !headers.isEmpty()) {
            this.videoHeaders = new HashMap<>(headers);
        }
        // prepare 之前调用：只记录，下一次 prepareAsync 会带上音频轨；
        // prepare 之后调用：重建媒体源并保留播放位置。
        runOnMain(this::applyAudioSourceChangeOnMain);
    }

    @Override
    public void setAudioOnly(boolean audioOnly) {
        if (released) return;
        // 字段本身 volatile：prepareInternal 在主线程读取时一定能看到最终值。
        this.audioOnly = audioOnly;
        // 正常约定是在 prepareAsync 之前调用；若上一条流已经在播，这里顺手按新模式重建媒体源，
        // 不会让「打开听视频后还继续下载视频轨」的旧状态挂住。
        runOnMain(this::applyAudioSourceChangeOnMain);
    }
}
