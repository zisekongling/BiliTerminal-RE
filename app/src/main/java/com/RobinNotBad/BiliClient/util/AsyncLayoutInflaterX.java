package com.RobinNotBad.BiliClient.util;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Choreographer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.util.Pools;
import androidx.core.view.LayoutInflaterCompat;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncLayoutInflaterX {
    private static final String TAG = "AsyncLayoutInflatePlus";

    private final Pools.SynchronizedPool<InflateRequest> mRequestPool = new Pools.SynchronizedPool<>(10);

    final LayoutInflater mInflater;
    final Handler mHandler;
    final Dispather mDispatcher;


    public AsyncLayoutInflaterX(@NonNull Context context) {
        mInflater = new BasicInflater(context);
        mHandler = new Handler(mHandlerCallback);
        mDispatcher = new Dispather();
    }

    @UiThread
    public void inflate(@LayoutRes int resid, @Nullable ViewGroup parent,
                        @NonNull OnInflateFinishedListener callback) {
        // API 23 以下一律回主线程 inflate（26.11.27）。
        //
        // 5.x 上后台线程 inflate 与主线程并发跑时，克隆 StateListAnimator 会共享
        // AnimatorSet 内部那个临时 Node 槽位，两条线程互相踩，稳定复现 NullPointerException。
        // 本工程 minSdk 19，且 4.4/5.x 的手表用户是真实存在的（见 app/build.gradle:86 与
        // docs/agents/build.md 里恢复 4.4 的记录），所以这道闸不能省。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.ASYNC_INFLATE_ENABLE, true)) {
            InflateRequest request = obtainRequest();
            request.inflater = this;
            request.resid = resid;
            request.parent = parent;
            request.callback = callback;
            mDispatcher.enqueue(request);
        } else {
            InflateRequest request = new InflateRequest();
            request.inflater = this;
            request.resid = resid;
            request.parent = parent;
            request.callback = callback;
            Message.obtain(mHandler, 0, request)
                    .sendToTarget();
        }
    }

    /**
     * 已取消标志。inflate 任务已经在线程池里跑起来时无法中断，所以取消只能靠这一层：
     * 回调到达主线程后先看标志，已取消就直接丢弃结果。
     *
     * 用 volatile 是因为置位发生在主线程（BaseActivity.onDestroy），而读取发生在
     * Handler 回调里（也在主线程，但语义上要求跨线程可见，避免以后被挪到别的 Looper 上出问题）。
     */
    private volatile boolean mCancelled = false;

    // 必须是 final：Handler 在构造时就已经捕获了这个引用，cancel() 里把字段置 null 是无效的
    // （字段被置 null 后 Handler 仍然持有原对象，回调照样会执行并打到已销毁的页面上）。
    private final Handler.Callback mHandlerCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            InflateRequest request = (InflateRequest) msg.obj;
            // 页面已销毁 / 已调用 cancel()：丢弃这次结果，绝不再回调宿主
            if (mCancelled) {
                releaseRequest(request);
                return true;
            }
            if (request.view == null) {
                request.view = mInflater.inflate(
                        request.resid, request.parent, false);
            }
            request.callback.onInflateFinished(
                    request.view, request.resid, request.parent);
            releaseRequest(request);
            return true;
        }
    };

    public interface OnInflateFinishedListener {
        void onInflateFinished(@NonNull View view, @LayoutRes int resid,
                               @Nullable ViewGroup parent);
    }

    /**
     * 取消判定回调：返回 true 表示宿主已销毁、应当立刻停止续帧。
     *
     * 为什么不直接用 java.util.function.BooleanSupplier：该包是 Java 8 才加进 Android 的
     * （API 24），本工程 minSdk 19，运行到 19~23 的设备上会在类加载时抛 NoClassDefFoundError。
     * 这里自带一个单方法接口，Kotlin/Java 的 lambda 都能照常做 SAM 转换，调用点无需改动。
     */
    public interface CancelledChecker {
        boolean isCancelled();
    }

    private static class InflateRequest {
        AsyncLayoutInflaterX inflater;
        ViewGroup parent;
        int resid;
        View view;
        OnInflateFinishedListener callback;

        InflateRequest() {
        }
    }


    private static class Dispather {

        //获得当前CPU的核心数
        private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
        //设置线程池的核心线程数2-4之间,但是取决于CPU核数
        private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
        //设置线程池的最大线程数为 CPU核数 * 2 + 1
        private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
        //设置线程池空闲线程存活时间30s
        private static final int KEEP_ALIVE_SECONDS = 30;

        private static final ThreadFactory sThreadFactory = new ThreadFactory() {
            private final AtomicInteger mCount = new AtomicInteger(1);

            public Thread newThread(Runnable r) {
                return new Thread(r, "AsyncLayoutInflatePlus #" + mCount.getAndIncrement());
            }
        };

        //LinkedBlockingQueue 默认构造器，队列容量是Integer.MAX_VALUE
        private static final BlockingQueue<Runnable> sPoolWorkQueue =
                new LinkedBlockingQueue<>();

        /**
         * An {@link Executor} that can be used to execute tasks in parallel.
         */
        public static final ThreadPoolExecutor THREAD_POOL_EXECUTOR;

        static {
            Log.i(TAG, "static initializer: " + " CPU_COUNT = " + CPU_COUNT + " CORE_POOL_SIZE = " + CORE_POOL_SIZE + " MAXIMUM_POOL_SIZE = " + MAXIMUM_POOL_SIZE);
            ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                    CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                    sPoolWorkQueue, sThreadFactory);
            threadPoolExecutor.allowCoreThreadTimeOut(true);
            THREAD_POOL_EXECUTOR = threadPoolExecutor;
        }

        public void enqueue(InflateRequest request) {
            THREAD_POOL_EXECUTOR.execute((new InflateRunnable(request)));

        }

    }

    private static class BasicInflater extends LayoutInflater {
        private static final String[] sClassPrefixList = {
                "android.widget.",
                "android.webkit.",
                "android.app."
        };

        BasicInflater(Context context) {
            super(context);
            if (context instanceof AppCompatActivity) {
                // 手动setFactory2，兼容AppCompatTextView等控件
                AppCompatDelegate appCompatDelegate = ((AppCompatActivity) context).getDelegate();
                if (appCompatDelegate instanceof LayoutInflater.Factory2) {
                    LayoutInflaterCompat.setFactory2(this, (LayoutInflater.Factory2) appCompatDelegate);
                }
            }
        }

        @Override
        public LayoutInflater cloneInContext(Context newContext) {
            return new BasicInflater(newContext);
        }

        @Override
        protected View onCreateView(String name, AttributeSet attrs) throws ClassNotFoundException {
            for (String prefix : sClassPrefixList) {
                try {
                    View view = createView(name, prefix, attrs);
                    if (view != null) {
                        return view;
                    }
                } catch (ClassNotFoundException e) {
                    // In this case we want to let the base class take a crack
                    // at it.
                }
            }

            return super.onCreateView(name, attrs);
        }
    }


    private static class InflateRunnable implements Runnable {
        private final InflateRequest request;
        private boolean isRunning;

        public InflateRunnable(InflateRequest request) {
            this.request = request;
        }

        @Override
        public void run() {
            isRunning = true;
            try {
                request.view = request.inflater.mInflater.inflate(
                        request.resid, request.parent, false);
            } catch (Throwable ex) {
                // Probably a Looper failure, retry on the UI thread
                Log.w(TAG, "Failed to inflate resource in the background! Retrying on the UI"
                        + " thread", ex);
            }
            Message.obtain(request.inflater.mHandler, 0, request)
                    .sendToTarget();
        }

        public boolean isRunning() {
            return isRunning;
        }
    }


    public InflateRequest obtainRequest() {
        InflateRequest obj = mRequestPool.acquire();
        if (obj == null) {
            obj = new InflateRequest();
        }
        return obj;
    }

    public void releaseRequest(InflateRequest obj) {
        obj.callback = null;
        obj.inflater = null;
        obj.parent = null;
        obj.resid = 0;
        obj.view = null;
        mRequestPool.release(obj);
    }


    /**
     * 取消本次异步 inflate：丢弃尚未交付的回调。
     *
     * 调用时机：宿主（[com.RobinNotBad.BiliClient.activity.base.BaseActivity]）在 onDestroy 时调用。
     * inflate 任务可能已经在线程池里跑完甚至已经 post 到主线程队列，所以这里做两件事：
     * 1. 置取消标志 —— 拦下正在主线程队列里排队、或之后才被 post 的回调；
     * 2. 清掉 Handler 队列里还没执行的 post。
     *
     * 注意 cancel() 后该实例不应再次使用（标志不会自动复位也没有必要复位，
     * BaseActivity 每次 asyncInflate 都会 new 一个新实例）。
     */
    public void cancel() {
        mCancelled = true;
        mHandler.removeCallbacksAndMessages(null);
    }

    /**
     * 把刚替换上去的内容视图做一次淡入，避免「白屏硬切」的观感。
     *
     * 为什么不用 ViewPropertyAnimator / ObjectAnimator：系统动画会被「动画时长缩放」设置影响，
     * 部分手表 ROM 把它置 0，这类动画在那些设备上完全不播，等同于没做。
     * 所以这里自己按帧驱动，不读任何系统动画缩放。
     *
     * 为什么必须按「帧数」而不是「时间」驱动：低配手表单帧绘制耗时可能就超过动画总时长
     * （对方在 W527 上实测如此），纯时间型淡入会在第一帧绘制时就已经算完，观感仍是硬切。
     * 因此取「3 帧」和「300ms」两个上限中的更小者：帧数保证至少跨 3 次绘制才到不透明，
     * 时间上限只作兜底，防止极端情况下长时间停在半透明。
     *
     * @param view      刚 setContentView 上去的内容视图
     * @param cancelled 宿主是否已销毁的回调；返回 true 时立刻收尾（置为完全不透明）并停止续帧，
     *                  避免对已销毁页面持续请求下一帧
     */
    public static void fadeIn(@NonNull final View view, @Nullable final CancelledChecker cancelled) {
        view.setAlpha(0f);
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            private final long startMs = SystemClock.uptimeMillis();
            private int frameCount = 0;

            @Override
            public void doFrame(long frameTimeNanos) {
                frameCount++;
                long elapsedMs = SystemClock.uptimeMillis() - startMs;
                float alpha = Math.min(Math.min(frameCount / 3f, elapsedMs / 300f), 1f);
                view.setAlpha(alpha);
                if (alpha < 1f && (cancelled == null || !cancelled.isCancelled())) {
                    Choreographer.getInstance().postFrameCallback(this);
                } else {
                    view.setAlpha(1f);
                }
            }
        });
    }
}