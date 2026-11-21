package com.RobinNotBad.BiliClient.util;

/*
 * 未完工的log
 */

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.RobinNotBad.BiliClient.BiliTerminal;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Logu {

    /**
     * **不要**把 logcat 的 tag 改成统一常量（26.10.14 一度这么改过，已回退）。
     *
     * <p>`getCaller()` 返回的 `类名>方法名` **本身就是既有诊断契约**，被写死在一堆文档与源码注释里：
     * `docs/archive/review/device-test-plan.md` D3-11~D3-14 的 `adb logcat -s "PlayerActivity>onPrepared"`
     * / `"PlayerActivity>getExtras"` / `"PlayerActivity>checkStall"`，以及 `api/NoteApi.java` 的
     * `adb logcat -s BiliNote` 同一套思路。全工程 269 处 `Logu.*` 调用点都吃这套约定，
     * 统一 tag 会把它们一次性作废。
     *
     * <p>「一次抓全」的正解不是改 tag，而是**按进程过滤**或直接读落盘日志：
     * <pre>
     * adb logcat --pid=$(adb shell pidof -s &lt;包名&gt;)     # 不看 tag，本进程全量
     * ... --es cmd log                                   # 读落盘日志（见 docs/review/agent-test-guide.md）
     * </pre>
     */
    /**
     * 日志级别开关。
     *
     * <p>26.10.14 起是 `volatile`：以前它们只在 `Application.onCreate` 里写一次（早于任何业务线程），
     * 非 volatile 也碰不到问题；现在开关能在运行期被改（调试桥 `agent` 命令跑在后台线程、
     * 设置页回调跑在主线程），而 `Logu.d` 会被任意线程读——不加 volatile 就可能"改完不生效"，
     * 且是偶发、无法复现的那种。
     */
    public static volatile boolean LOGV_ENABLED = false;
    public static volatile boolean LOGD_ENABLED = false;
    public static volatile boolean LOGI_ENABLED = false;

    /**
     * Agent 测试模式（由 [com.RobinNotBad.BiliClient.BiliTerminal.applyLogSwitches] 写入）。
     * Release 构建下恒为 false——那边的赋值处用 `BuildConfig.DEBUG` 做编译期折叠。
     */
    public static volatile boolean AGENT_MODE = false;

    /** 是否把日志落盘。开启后日志同时进 logcat 与文件，agent 不必保持 adb 连接也能事后取证。 */
    public static volatile boolean LOG_TO_FILE = false;

    /** 日志文件主名（业务日志）。HTTP 追踪见 [HTTP_LOG_NAME]。 */
    public static final String APP_LOG_NAME = "rebili.log";

    /** HTTP 请求/响应追踪文件名。 */
    public static final String HTTP_LOG_NAME = "http.log";

    /**
     * 单个日志文件的体积上限。超过就砍掉前半、只留后半：
     * 手表存储小，且 agent 关心的永远是**最近发生了什么**，不是三个月前的第一条日志。
     */
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;

    /** 落盘队列容量。满了直接丢并计数（丢日志可以，阻塞业务线程不行）。 */
    private static final int QUEUE_CAPACITY = 4096;

    /** 每行日志正文的长度上限，防止一条巨大的响应体把文件冲爆。 */
    private static final int MAX_LINE_CHARS = 4000;

    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    /**
     * 落盘任务队列。
     *
     * <p>队列里放的是**任务**而不是"文件名 + 内容"：这样 `写一行`、`flush`、`清空文件` 三种操作
     * 全都在同一条落盘线程上串行执行，[WRITERS] 这个 writer 表就只被一条线程碰过，
     * 不存在"业务线程 flush 的同时落盘线程正在写"这种跨线程操作 `BufferedWriter` 的情况。
     */
    private static final BlockingQueue<Runnable> QUEUE = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private static final AtomicLong DROPPED = new AtomicLong(0);

    private static volatile Thread writerThread;
    private static final Object WRITER_LOCK = new Object();

    /** 文件名 → 已打开的写入器。**只允许落盘线程访问**（见 [QUEUE] 的说明）。 */
    private static final Map<String, BufferedWriter> WRITERS = new HashMap<>();

    // ==================== logcat ====================
    //
    // 下面每个方法都严格保持**改动前的 logcat 输出**：tag 一律是 getCaller()（`类名>方法名`），
    // 消息一律是原样（两参重载是 `tag + ">" + info`）。落盘那一路才额外带上调用者，
    // 因为文件里只有本应用的日志，不需要靠 tag 区分来源，但需要能定位到代码。
    // 详见类顶部关于「不要统一 tag」的说明。

    /**
     * 写 logcat。**绝不抛**。
     *
     * <p>纯 JVM 单测里 `android.util.Log` 是桩实现（任何方法都 `RuntimeException: Stub!`），
     * 生产代码「打了一条日志」不该让单测变红；真机上日志链路本身出问题也不该把进程带崩。
     * 拿不到 logcat 就退回 stdout —— CI 与单测输出里仍看得到这条日志。
     *
     * <p>不开 `testOptions.unitTests.returnDefaultValues`（那会让**所有**未 mock 的框架调用
     * 静默返回默认值，单测里误用 Android API 就再也发现不了），兜在这一处就够了。
     */
    private static void cat(int priority, String tag, String message) {
        try {
            Log.println(priority, tag, message);
        } catch (RuntimeException e) {
            System.out.println(tag + ": " + message);
        }
    }

    /** 取堆栈串；同样兜住 JVM 单测里的桩实现。 */
    private static String stackTraceOf(Throwable throwable) {
        try {
            return Log.getStackTraceString(throwable);
        } catch (RuntimeException e) {
            StringWriter out = new StringWriter();
            throwable.printStackTrace(new PrintWriter(out));
            return out.toString();
        }
    }

    public static void v(String s) {
        if (!LOGV_ENABLED) return;
        String caller = getCaller();
        cat(Log.VERBOSE, caller, s);
        toFile("V", caller, s);
    }

    public static void i(String s) {
        if (!LOGI_ENABLED) return;
        String caller = getCaller();
        cat(Log.INFO, caller, s);
        toFile("I", caller, s);
    }

    public static void d(String s) {
        if (!LOGD_ENABLED) return;
        String caller = getCaller();
        cat(Log.DEBUG, caller, s);
        toFile("D", caller, s);
    }

    public static void w(String s) {
        String caller = getCaller();
        cat(Log.WARN, caller, s);
        toFile("W", caller, s);
    }

    public static void e(String s) {
        String caller = getCaller();
        cat(Log.ERROR, caller, s);
        toFile("E", caller, s);
    }

    public static void wtf(String s) {
        String caller = getCaller();
        cat(Log.ASSERT, caller, s);
        toFile("F", caller, s);
    }

    public static void v(String tag, String info) {
        if (!LOGV_ENABLED) return;
        logPair(Log.VERBOSE, "V", tag, info);
    }

    public static void i(String tag, String info) {
        if (!LOGI_ENABLED) return;
        logPair(Log.INFO, "I", tag, info);
    }

    public static void d(String tag, String info) {
        if (!LOGD_ENABLED) return;
        logPair(Log.DEBUG, "D", tag, info);
    }

    public static void w(String tag, String info) {
        logPair(Log.WARN, "W", tag, info);
    }

    public static void e(String tag, String info) {
        logPair(Log.ERROR, "E", tag, info);
    }

    public static void wtf(String tag, String info) {
        logPair(Log.ASSERT, "F", tag, info);
    }

    /**
     * 两参重载的公共实现。
     *
     * <p>用 `Log.println(priority, tag, msg)` 而不是六个 `Log.v/d/i/w/e/wtf` 分支：
     * 输出完全等价（`Log.d(t,m)` 就是 `println(DEBUG,t,m)`），但这里只需要写一遍。
     * 消息保持 `tag>info` 的老格式、tag 仍是调用者——见类顶部「不要统一 tag」的说明。
     */
    private static void logPair(int priority, String fileLevel, String tag, String info) {
        String caller = getCaller();
        String message = tag + ">" + info;
        cat(priority, caller, message);
        toFile(fileLevel, caller, message);
    }

    /** 把异常按错误级打出来（含堆栈），同时进 logcat 与日志文件。 */
    public static void e(String tag, String info, Throwable throwable) {
        e(tag, info + " : " + throwable);
        if (throwable != null) toFile("E", getCaller(), stackTraceOf(throwable));
    }

    private static String getCaller() {
        // 原来硬编码取 stack[4]，等于把"调用深度"写死：v(String) 与 v(String,String) 这类
        // 重载深度不同，一旦深度变化就会取到上一层调用者甚至越界。改为从栈顶向下找
        // 第一个不属于 Logu 的帧——那才是真正调用日志方法的业务代码。
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StackTraceElement caller = stack[stack.length - 1];
        for (int i = 3; i < stack.length; i++) {
            if (!stack[i].getClassName().equals(Logu.class.getName())) {
                caller = stack[i];
                break;
            }
        }
        String name = caller.getClassName();
        int index = name.length();
        for (; index > 1; index--) {
            if (name.charAt(index - 1) == '.') break;
        }
        return name.substring(index) + ">" + caller.getMethodName();
    }

    // ==================== 内存环形缓冲 ====================
    //
    // 目标：让用户能「随反馈 / 崩溃附带日志」。做法是平时一直在内存里记、**不落盘**
    // （零磁盘 IO、零存储占用），崩溃或反馈时再从内存导出。
    //
    // 为什么是内存而不是落盘：手表存储小，而「随反馈附日志」是个低频动作，为此让全量日志
    // 长期占磁盘不划算；内存快照在崩溃瞬间取一次，成本只在那一刻。
    //
    // 为什么天然只记 W/E/F 级别：v/d/i 三个方法开头有 LOGV/LOGD/LOGI_ENABLED 开关
    // （release 下为 false，见 BiliTerminal.applyLogSwitches），release 包里它们**根本不执行**、
    // 连 toFile 都进不来；而 w/e/wtf 恒执行、本来就调 getCaller()。所以缓冲挂在 w/e/wtf
    // 共用的 toFile 里，既不新增开关、不新增 getCaller()，release 行为也只是多一次入队。
    // 一旦打开「详细模式」，v/d/i 会自动经同一条 toFile 进缓冲——不需要额外逻辑。

    /**
     * 环形缓冲的字符数上限（65536 ≈ 64K 字符）。
     *
     * <p>UTF-16 下约 128KB 内存，手表可接受；同时**必须远小于 Binder 事务上限**：
     * 崩溃快照要经 Intent extra 跨进程传给 `:error_activity` 的崩溃页，而 Binder 事务
     * 上限约 1MB，超了直接 TransactionTooLargeException。64K 字符留足了安全余量。
     */
    private static final int RING_MAX_CHARS = 65536;

    /**
     * 日志行的环形缓冲。
     *
     * <p>用 [ArrayDeque] 而不是 ArrayList：超容量时只从头部删一条，常数时间、不做数组搬移
     * （同 [com.RobinNotBad.BiliClient.util.CrashTrail] 的思路）。
     *
     * <p>线程安全：全工程 269 处 `Logu.*` 调用点可能来自任意线程，故 [RING] 与 [ringChars]
     * 的读写一律用 `synchronized (RING)` 包住。
     */
    private static final ArrayDeque<String> RING = new ArrayDeque<>();

    /** 缓冲里所有行的累计字符数（与 [RING] 同步维护，只允许在 `synchronized (RING)` 内读写）。 */
    private static int ringChars = 0;

    /**
     * 记一行进内存缓冲，超出 [RING_MAX_CHARS] 时从头部逐条淘汰直到回到上限内。
     *
     * <p>行内正文已经过脱敏 + `shorten()`（见 [toFile]），单行最长约 [MAX_LINE_CHARS]，
     * 远小于上限，因此正常情况下不会出现「一行进来就把整段历史挤空」。
     */
    private static void recordToRing(String line) {
        if (line == null) return;
        synchronized (RING) {
            RING.addLast(line);
            ringChars += line.length();
            while (ringChars > RING_MAX_CHARS && !RING.isEmpty()) {
                ringChars -= RING.removeFirst().length();
            }
        }
    }

    /**
     * 崩溃 / 反馈时取内存日志快照。
     *
     * <p>返回拼接好的多行文本（每行形如 `MM-dd HH:mm:ss.SSS E 类名&gt;方法名 | 正文`），
     * 缓冲为空时返回空串（**不返回 null**，调用方直接拼字符串）。
     *
     * <p>只读内存、不碰磁盘，可在崩溃路径上安全调用。
     */
    public static String ringSnapshot() {
        synchronized (RING) {
            if (RING.isEmpty()) return "";
            StringBuilder sb = new StringBuilder(ringChars + RING.size());
            boolean first = true;
            for (String line : RING) {
                if (!first) sb.append('\n');
                sb.append(line);
                first = false;
            }
            return sb.toString();
        }
    }

    /** 当前缓冲累计字符数。agent / 调试桥可据此判断快照是否被截断过。 */
    public static int ringChars() {
        synchronized (RING) {
            return ringChars;
        }
    }

    /** 清空内存缓冲（例如反馈已提交，避免同一份日志被反复附带）。 */
    public static void ringClear() {
        synchronized (RING) {
            RING.clear();
            ringChars = 0;
        }
    }

    // ==================== 落盘 ====================

    /**
     * 日志目录：优先应用专属外部目录（无需任何存储权限，`adb pull` 或 `run-as` 都能取），
     * 外部存储不可用时退回内部 `files/logs`。
     *
     * @return 目录；Context 尚未就绪或创建失败时返回 null
     */
    @Nullable
    public static File getLogDir() {
        Context ctx = BiliTerminal.context;
        if (ctx == null) return null;
        try {
            File dir = ctx.getExternalFilesDir("logs");
            if (dir == null) dir = new File(ctx.getFilesDir(), "logs");
            if (!dir.exists() && !dir.mkdirs()) return null;
            return dir;
        } catch (Exception e) {
            return null;
        }
    }

    /** 取日志文件；目录不可用时返回 null。 */
    @Nullable
    public static File getLogFile(String fileName) {
        File dir = getLogDir();
        return dir == null ? null : new File(dir, fileName);
    }

    /**
     * 落盘一路（与 logcat 那一路分开）。
     *
     * <p>文件里带上调用者（`类名&gt;方法名`）：文件只装本应用的日志，不需要靠 tag 区分来源，
     * 但需要能一眼定位到代码。logcat 那一路的 tag 仍是调用者本身，两条路互不影响。
     *
     * <p>顺序是**先脱敏、再 `shorten()`**：若先截断再脱敏，一条超长行被截断后敏感值可能只剩
     * 前半截，脱敏就匹配不全、凭证反而漏出去。脱敏后的同一行**同时进内存缓冲与落盘**，
     * 两条路内容完全一致（[recordToRing] 与 [enqueue] 收到的是同一个 line）。
     */
    private static void toFile(String level, String caller, String text) {
        String sanitized = text == null ? null : LogSanitizer.sanitize(text);
        String line = TIME_FORMAT.format(new Date()) + " " + level + " " + caller + " | " + shorten(sanitized);
        recordToRing(line);
        enqueue(APP_LOG_NAME, line);
    }

    /**
     * 供 HTTP 追踪等模块写入自有日志文件的公开入口。
     * 没有独立的「调用者」字段——调用方把需要定位的信息自己写进 [text]。
     */
    public static void appendToFile(String fileName, String level, String text) {
        // HTTP 追踪入口同样先脱敏：请求/响应 body 里可能带 csrf（bili_jct）、refresh_token 等凭据，
        // 不脱敏就直接落盘等于把它们写进 http.log。顺序与 [toFile] 一致——先脱敏、再 shorten()：
        // 若先截断再脱敏，超长行被截断后敏感值可能只剩前半截，脱敏就匹配不全、凭证反而漏出去。
        String sanitized = text == null ? null : LogSanitizer.sanitize(text);
        enqueue(fileName, TIME_FORMAT.format(new Date()) + " " + level + " " + shorten(sanitized));
    }

    private static void enqueue(String fileName, String line) {
        if (!LOG_TO_FILE) return;
        if (!submit(() -> writeLine(fileName, line))) {
            DROPPED.incrementAndGet();
        }
    }

    private static String shorten(String text) {
        String body = text == null ? "null" : text;
        if (body.length() > MAX_LINE_CHARS) body = body.substring(0, MAX_LINE_CHARS) + "…(truncated)";
        return body;
    }

    /**
     * 入队一个落盘任务。
     *
     * <p>业务线程只做一次 `offer`（O(1)、不碰磁盘），真正的 I/O 全在 [writerLoop] 里。
     * 刻意做成异步：`Logu.d` 会被 UI 线程调用，同步写文件会把卡顿直接带给用户；
     * 而 agent 测试模式下日志量会成倍上涨，同步写的代价更不可接受。
     *
     * @return 是否入队成功；队列满时返回 false（调用方计数丢弃）
     */
    private static boolean submit(Runnable task) {
        if (!LOG_TO_FILE) return false;
        ensureWriterThread();
        return QUEUE.offer(task);
    }

    private static void ensureWriterThread() {
        if (writerThread != null) return;
        synchronized (WRITER_LOCK) {
            if (writerThread != null) return;
            Thread t = new Thread(Logu::writerLoop, "rebili-log-writer");
            t.setDaemon(true);
            writerThread = t;
            t.start();
        }
    }

    private static void writerLoop() {
        while (true) {
            try {
                QUEUE.take().run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable ignored) {
                // 落盘是尽力而为，任何异常都不能影响业务；下一轮继续
            }
        }
    }

    private static void writeLine(String fileName, String line) {
        try {
            File file = getLogFile(fileName);
            if (file == null) {
                LOG_TO_FILE = false;
                return;
            }
            BufferedWriter writer = WRITERS.get(fileName);
            if (file.length() > MAX_FILE_BYTES) {
                // 超限先关掉句柄再截断，否则部分设备上文件被占用会删不掉
                closeWriter(fileName);
                rotate(file);
                writer = null;
            }
            if (writer == null) {
                writer = new BufferedWriter(new FileWriter(file, true), 8192);
                WRITERS.put(fileName, writer);
            }
            writer.write(line);
            writer.write('\n');
            // 队列排空后再 flush：既避免每行一次系统调用，又保证 agent 读文件时数据已经落盘
            if (QUEUE.isEmpty()) writer.flush();
        } catch (Exception e) {
            // 写不进去（存储满、目录被删）就整体关掉，避免每条日志都抛一次异常拖慢业务
            LOG_TO_FILE = false;
            closeWriter(fileName);
        }
    }

    /** 关闭某个文件的写入器。**只在落盘线程上调用**。 */
    private static void closeWriter(String fileName) {
        BufferedWriter writer = WRITERS.remove(fileName);
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** 保留文件后半段（约一半），前半丢弃。**只在落盘线程上调用**。 */
    private static void rotate(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            long keep = MAX_FILE_BYTES / 2;
            long length = raf.length();
            if (length <= keep) return;
            byte[] tail = new byte[(int) keep];
            raf.seek(length - keep);
            raf.readFully(tail);
            raf.seek(0);
            raf.write(tail);
            raf.setLength(keep);
        } catch (Exception ignored) {
        }
    }

    /**
     * 等落盘队列排空并把已缓冲的内容刷进磁盘（最多 [timeoutMs] 毫秒）。
     *
     * <p>agent 读日志前先调它，避免"刚打的日志还没落盘"造成误判。
     * 实现是往队列尾塞一个「flush 任务 + 闩」：它排在所有已入队任务之后，
     * 执行到它就说明前面的都写完了——**不跨线程碰 `BufferedWriter`**（见 [QUEUE] 的说明）。
     */
    public static void flush(long timeoutMs) {
        if (!LOG_TO_FILE) return;
        CountDownLatch latch = new CountDownLatch(1);
        boolean queued = submit(() -> {
            for (BufferedWriter writer : WRITERS.values()) {
                try {
                    writer.flush();
                } catch (IOException ignored) {
                }
            }
            latch.countDown();
        });
        if (!queued) {
            // 队列满（说明积压严重）时闩永远不会被放行，直接等一小会儿就返回，别把调用方挂死
            sleep(Math.min(Math.max(timeoutMs, 0), 200));
            return;
        }
        try {
            latch.await(Math.max(timeoutMs, 0), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 读日志文件尾部。
     *
     * @param fileName 文件名（[APP_LOG_NAME] / [HTTP_LOG_NAME]）
     * @param maxBytes 最多读多少字节（从尾部往前）
     * @return 文本内容；文件不存在或不可读时返回空串（**不返回 null**，调用方直接拼字符串）
     */
    public static String readTail(String fileName, int maxBytes) {
        flush(800);
        File file = getLogFile(fileName);
        if (file == null || !file.exists()) return "";
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long length = raf.length();
            long start = Math.max(0, length - Math.max(0, maxBytes));
            raf.seek(start);
            byte[] buf = new byte[(int) (length - start)];
            raf.readFully(buf);
            return new String(buf, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    /** 清空某个日志文件。 */
    public static void clearFile(String fileName) {
        if (!LOG_TO_FILE) {
            truncate(getLogFile(fileName));
            return;
        }
        flush(500);
        submit(() -> {
            closeWriter(fileName);
            truncate(getLogFile(fileName));
        });
    }

    private static void truncate(File file) {
        if (file == null) return;
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            // 以截断模式打开即清空，无需写入内容
        } catch (Exception ignored) {
        }
    }

    /** 已丢弃的日志行数（队列满时累加）。agent 可据此判断日志是否完整。 */
    public static long getDroppedCount() {
        return DROPPED.get();
    }

    /** 列出日志目录下的文件，形如 `rebili.log:12345` 的多行文本。 */
    public static String listLogFiles() {
        flush(500);
        File dir = getLogDir();
        if (dir == null) return "";
        File[] files = dir.listFiles();
        if (files == null) return "";
        StringBuilder sb = new StringBuilder();
        for (File f : files) {
            sb.append(f.getName()).append(':').append(f.length()).append('\n');
        }
        return sb.toString();
    }
}
