/*
 * Copyright (C) 2013 Chen Hui <calmer91@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package master.flame.danmaku.controller;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;

import java.util.LinkedList;

import master.flame.danmaku.danmaku.model.AbsDisplayer;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.renderer.IRenderer.RenderingState;
import master.flame.danmaku.danmaku.util.AndroidUtils;
import master.flame.danmaku.danmaku.util.SystemClock;
import tv.cjump.jni.DeviceUtils;

public class DrawHandler extends Handler {

    private DanmakuContext mContext;

    public interface Callback {
        public void prepared();

        public void updateTimer(DanmakuTimer timer);

        public void danmakuShown(BaseDanmaku danmaku);

        public void drawingFinished();

    }

    public static final int START = 1;

    public static final int UPDATE = 2;

    public static final int RESUME = 3;

    public static final int SEEK_POS = 4;

    public static final int PREPARE = 5;

    private static final int QUIT = 6;

    private static final int PAUSE = 7;

    private static final int SHOW_DANMAKUS = 8;

    private static final int HIDE_DANMAKUS = 9;

    private static final int NOTIFY_DISP_SIZE_CHANGED = 10;

    private static final int NOTIFY_RENDERING = 11;

    private static final int UPDATE_WHEN_PAUSED = 12;

    private static final int CLEAR_DANMAKUS_ON_SCREEN = 13;

    private static final long INDEFINITE_TIME = 10000000;

    private long pausedPosition = 0;

    private boolean quitFlag = true;

    private long mTimeBase;

    private boolean mReady;

    private Callback mCallback;

    public final DanmakuTimer timer = new DanmakuTimer();

    private BaseDanmakuParser mParser;

    public IDrawTask drawTask;

    private IDanmakuViewController mDanmakuView;

    private boolean mDanmakusVisible = true;

    private AbsDisplayer mDisp;

    private final RenderingState mRenderingState = new RenderingState();

    private static final int MAX_RECORD_SIZE = 500;

    private final LinkedList<Long> mDrawTimes = new LinkedList<>();

    private UpdateThread mThread;

    private final boolean mUpdateInNewThread;

    private long mCordonTime = 30;

    private long mCordonTime2 = 60;

    private long mFrameUpdateRate = 16;

    @SuppressWarnings("unused")
    private long mThresholdTime;

    private long mLastDeltaTime;

    private boolean mInSeekingAction;

    private long mDesireSeekingTime;

    private long mRemainingTime;

    private boolean mInSyncAction;

    private boolean mInWaitingState;

    private final boolean mIdleSleep;

    public DrawHandler(Looper looper, IDanmakuViewController view, boolean danmakuVisibile) {
        super(looper);
        mUpdateInNewThread = (Runtime.getRuntime().availableProcessors() > 3);
        mIdleSleep = !DeviceUtils.isProblemBoxDevice();
        bindView(view);
        if (danmakuVisibile) {
            showDanmakus(null);
        } else {
            hideDanmakus(false);
        }
        mDanmakusVisible = danmakuVisibile;
    }

    private void bindView(IDanmakuViewController view) {
        this.mDanmakuView = view;
    }

    public void setConfig(DanmakuContext config) {
        mContext = config;
    }

    public void setParser(BaseDanmakuParser parser) {
        mParser = parser;
    }

    public void setCallback(Callback cb) {
        mCallback = cb;
    }

    public void quit() {
        sendEmptyMessage(QUIT);
    }

    public boolean isStop() {
        return quitFlag;
    }

    @Override
    public void handleMessage(Message msg) {
        int what = msg.what;
        switch (what) {
            case PREPARE:
                mTimeBase = SystemClock.uptimeMillis();
                if (mParser == null || !mDanmakuView.isViewReady()) {
                    sendEmptyMessageDelayed(PREPARE, 100);
                } else {
                    prepare(() -> {
                        // 异步 ready 期间的 start(pos) 不能被清零，见 mPendingStartPos 注释
                        pausedPosition = mPendingStartPos >= 0 ? mPendingStartPos : 0;
                        mReady = true;
                        if (mCallback != null) {
                            mCallback.prepared();
                        }
                    });
                }
                break;
            case SHOW_DANMAKUS:
                mDanmakusVisible = true;
                Long start = (Long) msg.obj;
                boolean resume = false;
                if (drawTask != null) {
                    if (start == null) {
                        timer.update(getCurrentTime());
                        drawTask.requestClear();
                    } else {
                        drawTask.start();
                        drawTask.seek(start);
                        drawTask.requestClear();
                        resume = true;
                    }
                }
                if (quitFlag && mDanmakuView != null) {
                    mDanmakuView.drawDanmakus();
                }
                notifyRendering();
                if (!resume) {
                    break;
                }
            case START:
                Long startTime = (Long) msg.obj;
                if (startTime != null) {
                    pausedPosition = startTime;
                } else {
                    pausedPosition = 0;
                }
                mPendingStartPos = pausedPosition;
            case SEEK_POS:
                if (what == SEEK_POS) {
                    quitFlag = true;
                    quitUpdateThread();
                    Long position = (Long) msg.obj;
                    long deltaMs = position - timer.currMillisecond;
                    mTimeBase -= deltaMs;
                    timer.update(position);
                    mContext.mGlobalFlagValues.updateMeasureFlag();
                    if (drawTask != null)
                        drawTask.seek(position);
                    pausedPosition = position;
                    mClockSamplePos = -1; // 位置跳变：丢掉旧采样，下一帧按新位置重新对齐
                }
            case RESUME:
                quitFlag = false;
                if (mReady) {
                    mRenderingState.reset();
                    mDrawTimes.clear();
                    mTimeBase = SystemClock.uptimeMillis() - pausedPosition;
                    timer.update(pausedPosition);
                    mClockSamplePos = -1; // 恢复时同样丢采样：别按"暂停期间流逝的墙钟"外推，否则会前冲再回落
                    removeMessages(RESUME);
                    sendEmptyMessage(UPDATE);
                    drawTask.start();
                    notifyRendering();
                    mInSeekingAction = false;
                } else {
                    sendEmptyMessageDelayed(RESUME, 100);
                }
                break;
            case UPDATE:
                if (mUpdateInNewThread) {
                    updateInNewThread();
                } else {
                    updateInCurrentThread();
                }
                break;
            case NOTIFY_DISP_SIZE_CHANGED:
                mContext.mDanmakuFactory.notifyDispSizeChanged(mContext);
                Boolean updateFlag = (Boolean) msg.obj;
                if (updateFlag != null && updateFlag) {
                    mContext.mGlobalFlagValues.updateMeasureFlag();
                }
                break;
            case HIDE_DANMAKUS:
                mDanmakusVisible = false;
                if (mDanmakuView != null) {
                    mDanmakuView.clear();
                }
                if (this.drawTask != null) {
                    this.drawTask.requestClear();
                    this.drawTask.requestHide();
                }
                Boolean quitDrawTask = (Boolean) msg.obj;
                if (quitDrawTask && this.drawTask != null) {
                    this.drawTask.quit();
                }
                if (!quitDrawTask) {
                    break;
                }
            case PAUSE:
                removeMessages(UPDATE);
            case QUIT:
                if (what == QUIT) {
                    removeCallbacksAndMessages(null);
                }
                quitFlag = true;
                syncTimerIfNeeded();
                if (mThread != null) {
                    notifyRendering();
                    quitUpdateThread();
                }
                pausedPosition = timer.currMillisecond;
                if (what == QUIT) {
                    if (this.drawTask != null) {
                        this.drawTask.quit();
                    }
                    if (mParser != null) {
                        mParser.release();
                    }
                    if (this.getLooper() != Looper.getMainLooper())
                        this.getLooper().quit();
                }
                break;
            case NOTIFY_RENDERING:
                notifyRendering();
                break;
            case UPDATE_WHEN_PAUSED:
                if (quitFlag && mDanmakuView != null) {
                    drawTask.requestClear();
                    mDanmakuView.drawDanmakus();
                    notifyRendering();
                }
                break;
            case CLEAR_DANMAKUS_ON_SCREEN:
                if (drawTask != null) {
                    drawTask.clearDanmakusOnScreen(getCurrentTime());
                }
                break;
        }
    }

    private void quitUpdateThread() {
        if (mThread != null) {
            final UpdateThread thread = mThread;
            mThread = null;
            synchronized (drawTask) {
                drawTask.notifyAll();
            }
            thread.quit();
            try {
                //必须带超时：quitUpdateThread 由 handleMessage(QUIT/SEEK_POS) 调用，
                //若更新线程卡在原生调用上，无超时 join 会让主线程的销毁/跳转流程永久挂死。
                thread.join(2000);
                if (thread.isAlive())
                    android.util.Log.w("DrawHandler", "更新线程未在 2s 内退出，放弃等待");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateInCurrentThread() {
        if (quitFlag) {
            return;
        }
        long startMS = SystemClock.uptimeMillis();
        long d = syncTimer(startMS);
        if (d < 0) {
            removeMessages(UPDATE);
            sendEmptyMessageDelayed(UPDATE, 60 - d);
            return;
        }
        d = mDanmakuView.drawDanmakus();
        removeMessages(UPDATE);
        if (d > mCordonTime2) { // this situation may be cuased by ui-thread waiting of DanmakuView, so we
                                // sync-timer at once
            timer.add(d);
            mDrawTimes.clear();
        }
        if (!mDanmakusVisible) {
            waitRendering(INDEFINITE_TIME);
            return;
        } else if (mRenderingState.nothingRendered && mIdleSleep) {
            long dTime = mRenderingState.endTime - timer.currMillisecond;
            if (dTime > 500) {
                waitRendering(dTime - 10);
                return;
            }
        }

        if (d < mFrameUpdateRate) {
            sendEmptyMessageDelayed(UPDATE, mFrameUpdateRate - d);
            return;
        }
        sendEmptyMessage(UPDATE);
    }

    private void updateInNewThread() {
        if (mThread != null) {
            return;
        }
        mThread = new UpdateThread("DFM Update") {
            @Override
            public void run() {
                long lastTime = SystemClock.uptimeMillis();
                long dTime = 0;
                while (!isQuited() && !quitFlag) {
                    long startMS = SystemClock.uptimeMillis();
                    dTime = SystemClock.uptimeMillis() - lastTime;
                    long diffTime = mFrameUpdateRate - dTime;
                    if (diffTime > 1) {
                        SystemClock.sleep(1);
                        continue;
                    }
                    lastTime = startMS;
                    long d = syncTimer(startMS);
                    if (d < 0) {
                        SystemClock.sleep(60 - d);
                        continue;
                    }
                    d = mDanmakuView.drawDanmakus();
                    if (d > mCordonTime2) { // this situation may be cuased by ui-thread waiting of DanmakuView, so we
                                            // sync-timer at once
                        timer.add(d);
                        mDrawTimes.clear();
                    }
                    if (!mDanmakusVisible) {
                        waitRendering(INDEFINITE_TIME);
                    } else if (mRenderingState.nothingRendered && mIdleSleep) {
                        dTime = mRenderingState.endTime - timer.currMillisecond;
                        if (dTime > 500) {
                            notifyRendering();
                            waitRendering(dTime - 10);
                        }
                    }
                }
            }
        };
        mThread.start();
    }

    private long mSpeedOffsetNoRender = 0L;
    private long mSpeedOffsetRender = 0L;
    private float mSpeed = 1.0F;

    /**
     * 外部媒体时钟：返回播放器真实位置。设了它之后弹幕时钟以它为准，不再跟随墙钟。
     *
     * 实现方**必须是线程安全的读**（本接口在 DFM 的渲染线程上被每帧调用）：
     * 只能读主线程采样出来的内存值，**绝对不能**在里面调 `MediaPlayer.currentPosition` 之类
     * 会取 native 锁的方法（与主线程 seek/release 并发有卡死风险）。
     */
    public interface MediaClock {
        /** 当前播放位置（毫秒）；**负数表示不可用**（未就绪/已销毁），此时退回墙钟逻辑。 */
        long positionMs();

        /** 是否正在播放；false 时弹幕时钟冻结在 positionMs。 */
        boolean isPlaying();
    }

    private MediaClock mMediaClock;
    private long mClockSamplePos = -1;
    private long mClockSampleAt;

    /**
     * 上一帧的墙钟时刻（`syncTimer` 传入的 nowMs），用于算出**真实帧间隔**。
     *
     * 媒体时钟模式下步进上限必须跟着帧间隔走：帧率掉到 25fps 以下时固定 40ms/帧追不上
     * 媒体时间流逝，误差会周期性撞上 SNAP_ERROR_MS 变成肉眼可见的「一跳一跳」
     * （见 MediaClockSync.stepFor(float, long)）。-1 表示还没有基准（首帧 / 刚硬对齐过），
     * 此时按原行为处理。
     */
    private long mLastClockFrameAt = -1;

    /**
     * 通过 `start(position)` 指定的起播位置；`-1` 表示没指定（从头）。
     *
     * 必须单独存：`prepare()` 的 ready 回调是**异步**的（`CacheManagingDrawTask` 在后台解析完
     * 弹幕才回调），在那之前 `start(pos)` 就已经把 `pausedPosition` 设成 pos 了；
     * 如果 ready 回调无条件 `pausedPosition = 0`，续播/切页传进来的起播位置就被吞掉，
     * 时间轴会从 0 重新走一遍（用户看到弹幕先闪一下旧的再跳回来）。
     */
    private long mPendingStartPos = -1;

    public void setMediaClock(MediaClock clock) {
        mMediaClock = clock;
        mClockSamplePos = -1;
    }

    public void setSpeed(float speed) {
        mSpeed = speed;
    }

    public long syncTimer(long startMS) {
        if (mInSeekingAction || mInSyncAction) {
            return 0;
        }
        mInSyncAction = true;
        if (mMediaClock != null) {
            long done = syncTimerByMediaClock(startMS);
            if (done == 0) {
                mInSyncAction = false;
                return 0;
            }
        }
        long d = 0;
        long time = startMS - mTimeBase;
        if (!mDanmakusVisible || mRenderingState.nothingRendered || mInWaitingState) {
            timer.update(time + mSpeedOffsetNoRender + mSpeedOffsetRender);
            mSpeedOffsetNoRender += mFrameUpdateRate * (mSpeed - 1);
            mRemainingTime = 0;
            if (mCallback != null) {
                mCallback.updateTimer(timer);
            }
        } else {
            long gapTime = time - timer.currMillisecond;
            long averageTime = Math.max(mFrameUpdateRate, getAverageRenderingTime());
            if (gapTime > 2000 || mRenderingState.consumingTime > mCordonTime || averageTime > mCordonTime) {
                d = gapTime;
                gapTime = 0;
            } else {
                d = averageTime + gapTime / mFrameUpdateRate;
                d = Math.max(mFrameUpdateRate, d);
                d = Math.min(mCordonTime, d);
                long a = d - mLastDeltaTime;
                if (a > 3 && a < 8 && mLastDeltaTime >= mFrameUpdateRate && mLastDeltaTime <= mCordonTime) {
                    d = mLastDeltaTime;
                } else {
                    mSpeedOffsetRender += d * (mSpeed - 1);
                    d = (int) (d * mSpeed);
                }
                gapTime -= d;
                mLastDeltaTime = d;
            }
            mRemainingTime = gapTime;
            timer.add(d);
            // Log.e("DrawHandler", time+"|d:" + d + "RemaingTime:" + mRemainingTime +
            // ",gapTime:" + gapTime + ",rtim:" + mRenderingState.consumingTime +
            // ",average:" + averageTime);
        }
        if (mCallback != null) {
            mCallback.updateTimer(timer);
        }
        mInSyncAction = false;
        return d;
    }

    /**
     * 以播放器位置为时间源推进弹幕时钟。
     *
     * @return 0 表示已经处理完（调用方直接用当前 timer 绘制）；非 0 表示时钟不可用，退回墙钟逻辑
     */
    private long syncTimerByMediaClock(long nowMs) {
        long pos = mMediaClock.positionMs();
        if (pos < 0) {
            mLastClockFrameAt = -1;
            return -1; // 位置不可信（未就绪/已销毁），退回原生逻辑
        }
        boolean playing = mMediaClock.isPlaying();
        if (MediaClockSync.isDiscontinuity(mClockSamplePos, pos)) {
            // 首次采样 / seek / 换源 / 恢复进度：位置不连续，直接对齐，别慢慢蠕动
            mClockSamplePos = pos;
            mClockSampleAt = nowMs;
            mLastClockFrameAt = -1; // 硬对齐后重新起算帧间隔，别把对齐前的空档算进去
            timer.update(pos);
            mRemainingTime = 0;
            if (mCallback != null) {
                mCallback.updateTimer(timer);
            }
            return 0;
        }
        if (pos != mClockSamplePos) {
            mClockSamplePos = pos;
            mClockSampleAt = nowMs;
        }
        // 真实帧间隔：上一帧到现在过去了多久。过长（卡顿、后台唤醒）或回拨就不当作基准，
        // 退回固定步进——那种情况下该由下面的吸附分支处理，而不是让蠕动一帧跨半秒。
        long frameInterval = mLastClockFrameAt < 0 ? 0L : nowMs - mLastClockFrameAt;
        mLastClockFrameAt = nowMs;
        if (frameInterval < 0 || frameInterval > MediaClockSync.SNAP_ERROR_MS) {
            frameInterval = 0L;
        }
        // 暂停时目标就是当前位置（冻结）；播放中按倍速外推，避免每 250ms 才推进一次
        long ideal = playing
                ? MediaClockSync.project(pos, mClockSampleAt, nowMs, mSpeed)
                : pos;
        timer.update(MediaClockSync.nextTimerValue(timer.currMillisecond, ideal, mSpeed, frameInterval));
        mRemainingTime = 0;
        if (mCallback != null) {
            mCallback.updateTimer(timer);
        }
        return 0;
    }

    public void syncTimerIfNeeded() {
        if (mInWaitingState) {
            syncTimer(SystemClock.uptimeMillis());
        }
    }

    private void initRenderingConfigs() {
        long averageFrameConsumingTime = 16;
        mCordonTime = Math.max(33, (long) (averageFrameConsumingTime * 2.5f));
        mCordonTime2 = (long) (mCordonTime * 2.5f);
        mFrameUpdateRate = Math.max(16, averageFrameConsumingTime / 15 * 15);
        mThresholdTime = mFrameUpdateRate + 3;
        // Log.i("DrawHandler", "initRenderingConfigs test-fps:" +
        // averageFrameConsumingTime + "ms,mCordonTime:"
        // + mCordonTime + ",mFrameRefreshingRate:" + mFrameUpdateRate);
    }

    private void prepare(final Runnable runnable) {
        if (drawTask == null) {
            drawTask = createDrawTask(mDanmakuView.isDanmakuDrawingCacheEnabled(), timer,
                    mDanmakuView.getContext(), mDanmakuView.getWidth(), mDanmakuView.getHeight(),
                    mDanmakuView.isHardwareAccelerated(), new IDrawTask.TaskListener() {
                        @Override
                        public void ready() {
                            initRenderingConfigs();
                            runnable.run();
                        }

                        @Override
                        public void onDanmakuAdd(BaseDanmaku danmaku) {
                            if (danmaku.isTimeOut()) {
                                return;
                            }
                            long delay = danmaku.time - timer.currMillisecond;
                            if (delay > 0) {
                                sendEmptyMessageDelayed(NOTIFY_RENDERING, delay);
                            } else if (mInWaitingState) {
                                notifyRendering();
                            }
                        }

                        @Override
                        public void onDanmakuShown(BaseDanmaku danmaku) {
                            if (mCallback != null) {
                                mCallback.danmakuShown(danmaku);
                            }
                        }

                        @Override
                        public void onDanmakusDrawingFinished() {
                            if (mCallback != null) {
                                mCallback.drawingFinished();
                            }
                        }

                        @Override
                        public void onDanmakuConfigChanged() {
                            redrawIfNeeded();
                        }
                    });
        } else {
            runnable.run();
        }
    }

    public boolean isPrepared() {
        return mReady;
    }

    private IDrawTask createDrawTask(boolean useDrwaingCache, DanmakuTimer timer,
            Context context,
            int width, int height,
            boolean isHardwareAccelerated,
            IDrawTask.TaskListener taskListener) {
        mDisp = mContext.getDisplayer();
        mDisp.setSize(width, height);
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        mDisp.setDensities(displayMetrics.density, displayMetrics.densityDpi,
                displayMetrics.scaledDensity);
        mDisp.resetSlopPixel(mContext.scaleTextSize);
        mDisp.setHardwareAccelerated(isHardwareAccelerated);
        IDrawTask task = useDrwaingCache
                ? new CacheManagingDrawTask(timer, mContext, taskListener,
                        1024 * 1024 * AndroidUtils.getMemoryClass(context) / 3)
                : new DrawTask(timer, mContext, taskListener);
        task.setParser(mParser);
        task.prepare();
        obtainMessage(NOTIFY_DISP_SIZE_CHANGED, false).sendToTarget();
        return task;
    }

    public void seekTo(Long ms) {
        mInSeekingAction = true;
        mDesireSeekingTime = ms;
        removeMessages(DrawHandler.UPDATE);
        removeMessages(DrawHandler.RESUME);
        removeMessages(DrawHandler.SEEK_POS);
        obtainMessage(DrawHandler.SEEK_POS, ms).sendToTarget();
    }

    public void addDanmaku(BaseDanmaku item) {
        if (drawTask != null) {
            item.flags = mContext.mGlobalFlagValues;
            item.setTimer(timer);
            drawTask.addDanmaku(item);
            obtainMessage(NOTIFY_RENDERING).sendToTarget();
        }
    }

    public void invalidateDanmaku(BaseDanmaku item, boolean remeasure) {
        if (drawTask != null && item != null) {
            drawTask.invalidateDanmaku(item, remeasure);
        }
        redrawIfNeeded();
    }

    public void resume() {
        sendEmptyMessage(DrawHandler.RESUME);
    }

    public void prepare() {
        sendEmptyMessage(DrawHandler.PREPARE);
    }

    public void pause() {
        removeMessages(DrawHandler.RESUME);
        removeMessages(DrawHandler.UPDATE);
        syncTimerIfNeeded();
        sendEmptyMessage(DrawHandler.PAUSE);
    }

    public void showDanmakus(Long position) {
        if (mDanmakusVisible)
            return;
        mDanmakusVisible = true;
        removeMessages(SHOW_DANMAKUS);
        removeMessages(HIDE_DANMAKUS);
        obtainMessage(SHOW_DANMAKUS, position).sendToTarget();
    }

    public long hideDanmakus(boolean quitDrawTask) {
        if (!mDanmakusVisible)
            return timer.currMillisecond;
        mDanmakusVisible = false;
        removeMessages(SHOW_DANMAKUS);
        removeMessages(HIDE_DANMAKUS);
        obtainMessage(HIDE_DANMAKUS, quitDrawTask).sendToTarget();
        return timer.currMillisecond;
    }

    public boolean getVisibility() {
        return mDanmakusVisible;
    }

    public RenderingState draw(Canvas canvas) {
        if (drawTask == null)
            return mRenderingState;
        mDisp.setExtraData(canvas);
        mRenderingState.set(drawTask.draw(mDisp));
        recordRenderingTime();
        return mRenderingState;
    }

    private void redrawIfNeeded() {
        if (quitFlag && mDanmakusVisible) {
            obtainMessage(UPDATE_WHEN_PAUSED).sendToTarget();
        }
    }

    private void notifyRendering() {
        if (!mInWaitingState) {
            return;
        }
        if (drawTask != null) {
            drawTask.requestClear();
        }
        if (mUpdateInNewThread) {
            synchronized (this) {
                mDrawTimes.clear();
            }
            synchronized (drawTask) {
                drawTask.notifyAll();
            }
        } else {
            mDrawTimes.clear();
            removeMessages(UPDATE);
            sendEmptyMessage(UPDATE);
        }
        mInWaitingState = false;
    }

    private void waitRendering(long dTime) {
        mRenderingState.sysTime = SystemClock.uptimeMillis();
        mInWaitingState = true;
        if (mUpdateInNewThread) {
            if (mThread == null) {
                return;
            }
            try {
                synchronized (drawTask) {
                    if (dTime == INDEFINITE_TIME) {
                        drawTask.wait();
                    } else {
                        drawTask.wait(dTime);
                    }
                    sendEmptyMessage(NOTIFY_RENDERING);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            if (dTime == INDEFINITE_TIME) {
                removeMessages(NOTIFY_RENDERING);
                removeMessages(UPDATE);
            } else {
                removeMessages(NOTIFY_RENDERING);
                removeMessages(UPDATE);
                sendEmptyMessageDelayed(NOTIFY_RENDERING, dTime);
            }
        }
    }

    private synchronized long getAverageRenderingTime() {
        int frames = mDrawTimes.size();
        if (frames <= 0)
            return 0;
        try {
            long dtime = mDrawTimes.getLast() - mDrawTimes.getFirst();
            return dtime / frames;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    private synchronized void recordRenderingTime() {
        long lastTime = SystemClock.uptimeMillis();
        mDrawTimes.addLast(lastTime);
        int frames = mDrawTimes.size();
        if (frames > MAX_RECORD_SIZE) {
            mDrawTimes.removeFirst();
            frames = MAX_RECORD_SIZE;
        }
    }

    public IDisplayer getDisplayer() {
        return mDisp;
    }

    public void notifyDispSizeChanged(int width, int height) {
        if (mDisp == null) {
            return;
        }
        if (mDisp.getWidth() != width || mDisp.getHeight() != height) {
            mDisp.setSize(width, height);
            obtainMessage(NOTIFY_DISP_SIZE_CHANGED, true).sendToTarget();
        }
    }

    public void removeAllDanmakus(boolean isClearDanmakusOnScreen) {
        if (drawTask != null) {
            drawTask.removeAllDanmakus(isClearDanmakusOnScreen);
        }
    }

    public void removeAllLiveDanmakus() {
        if (drawTask != null) {
            drawTask.removeAllLiveDanmakus();
        }
    }

    public IDanmakus getCurrentVisibleDanmakus() {
        if (drawTask != null) {
            return drawTask.getVisibleDanmakusOnTime(getCurrentTime());
        }

        return null;
    }

    public long getCurrentTime() {
        if (!mReady) {
            return 0;
        }
        if (mInSeekingAction) {
            return mDesireSeekingTime;
        }
        if (quitFlag || !mInWaitingState) {
            return timer.currMillisecond - mRemainingTime;
        }
        return SystemClock.uptimeMillis() - mTimeBase;
    }

    public void clearDanmakusOnScreen() {
        obtainMessage(CLEAR_DANMAKUS_ON_SCREEN).sendToTarget();
    }

    public DanmakuContext getConfig() {
        return mContext;
    }

}
