package com.RobinNotBad.BiliClient;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.RobinNotBad.BiliClient.activity.CatchActivity;
import com.RobinNotBad.BiliClient.util.CrashTrail;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.PublicLogRecorder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

public class ErrorCatch implements Thread.UncaughtExceptionHandler {
    @SuppressLint("StaticFieldLeak")
    public static ErrorCatch instance;
    private Context context;
    /** 安装本处理器之前的那一个（可能来自框架或崩溃上报 SDK），用于处理完自己的逻辑后交还。 */
    private Thread.UncaughtExceptionHandler previousHandler;

    public static ErrorCatch getInstance() {
        if (instance == null) instance = new ErrorCatch();
        return instance;
    }

    public void init(Context context) {
        this.context = context;
        // 记住被本类顶掉的那一个：不保存的话，任何先注册的崩溃上报都会静默失效
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        previousHandler = (previous == this) ? null : previous;
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(@NonNull Thread thread, @NonNull Throwable throwable) {
        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        throwable.printStackTrace(printWriter);

        // 诊断包专用：先在崩溃线程把堆栈同步追加到公共日志。
        // 平时这里连磁盘都不碰（见下面注释），但诊断包的意义就是「崩溃页万一在
        // 安卓 5 上起不来也要留下堆栈」；只写一小段文本、整体 try/catch 包住，
        // 失败也绝不会影响后面的崩溃页拉起与进程退出。
        if (BuildConfig.DEBUG && BuildConfig.DIAG_FULL_LOG) {
            try {
                PublicLogRecorder.appendCrash(context, "CRASH-THREAD", writer.toString());
            } catch (Throwable ignored) {
                // 崩溃路径上不允许二次抛出
            }
        }

        try {
            Intent intent = new Intent(context, CatchActivity.class);
            intent.putExtra("stack", writer.toString());
            // 26.10.09（自建崩溃报告）：这些是「只有崩溃瞬间才拿得到」的字段——
            // 进程马上就被 killProcess 了，页面轨迹、出错线程、异常类名都随之消失，
            // 所以必须在 startActivity 之前塞进 Intent 带过去。
            // 这里刻意只做取值，不做采集/网络/磁盘：崩溃线程里任何多余的工作都可能让
            // 「崩溃页打不开」变成第二种崩溃。真正的组装与上传在 :error_activity 进程里做。
            intent.putExtra("exception", throwable.getClass().getName());
            intent.putExtra("message", String.valueOf(throwable.getMessage()));
            intent.putExtra("thread", thread.getName());
            intent.putExtra("trail", CrashTrail.snapshot());
            // 内存日志快照（最近若干行 W/E/F 日志）走的是和 trail 完全一样的一条路：
            // 只在崩溃瞬间取一次值、塞进 Intent 带过去。
            //
            // 为什么这里不「顺便多采集点」（比如去读磁盘日志、多截几段上下文、顺手做点清洗）：
            // 本方法跑在**正在崩溃的那条线程**上，取完值进程马上就被 killProcess，
            // 这里任何多余的工作都可能把「崩溃页打不开」升级成第二种崩溃。
            // Logu.ringSnapshot() 只读内存、不碰磁盘、不发网络，是这条约束下唯一能接受的操作；
            // 需要落盘 / 网络 / 组装的活全部留给崩溃页所在的 :error_activity 进程
            // （见 CatchActivity / CrashReporter）。
            //
            // 注意：崩溃页跑在独立进程，那边读不到主进程的这份内存缓冲，
            // 所以必须经 Intent 传过去——这正是本行存在的意义。
            intent.putExtra("ring_log", Logu.ringSnapshot());
            intent.putExtra("uptime", SystemClock.elapsedRealtime() / 1000);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); //这句是安卓4必须有的
            context.startActivity(intent);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        throwable.printStackTrace();
        // 26.10.04 批次 3（B8）：这里原本 sleep(300ms) 等崩溃页起来。
        // 现在 CatchActivity 跑在独立进程 :error_activity（见 AndroidManifest），
        // startActivity 的请求已经同步交给 AMS 了，本进程立刻死掉也不影响那边被拉起，
        // 所以这 300ms 纯属白等——崩溃线程多卡 300ms，用户看到的就是多卡 300ms。

        // 交还给安装本处理器之前的处理器，让先注册的崩溃上报/终止逻辑仍有机会执行
        // （框架默认实现会自行结束进程；自定义实现若只是上报，则继续走下面的 killProcess）
        Thread.UncaughtExceptionHandler previous = previousHandler;
        if (previous != null) {
            try {
                previous.uncaughtException(thread, throwable);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
