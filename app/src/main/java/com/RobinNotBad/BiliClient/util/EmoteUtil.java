package com.RobinNotBad.BiliClient.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ImageSpan;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.model.Emote;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.FutureTarget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


//表情包工具，用于将文本中的表情包替换为对应图片，部分代码来自catGPT
//2023-07-23
//
//26.11.18 线上崩溃「java.lang.IllegalStateException: Cannot recycle a resource while it is still acquired」
//（#366/#368/#369/#371，栈顶 EngineResource.recycle <- ResourceRecycler$ResourceRecyclerCallback.handleMessage
//<- Handler.dispatchMessage <- Looper.loop，即主线程消息队列里的一次延迟回收）。
//
//机制（Glide 4.16 源码；端到端未能复现，但下面每一步都能在源码上对上）：
//  抛的是 EngineResource.recycle()（EngineResource.java:66-77，acquired > 0 就在 :68 抛），而这条栈里的
//  「延迟回收」只有一个来源：内存缓存淘汰时 Engine.onResourceRemoved（Engine.java:391-395，
//  forceNextFrame=true）→ ResourceRecycler.recycle 把消息投给主线程 Handler（ResourceRecycler.java:15-19）
//  → handleMessage 里 :37 的 resource.recycle()。
//  要抛，就必须存在「已经被放进内存缓存、同时 acquired 仍大于 0」的 EngineResource。本类旧实现正好制造它：
//  同一个 URL 有两套生命周期——prefetch() 成功即 clear()，而 textReplaceEmote()/replaceSingle() 只 submit()
//  从不 clear。release() 把计数减到 0 之后是**离开 synchronized 块**才回调 listener.onResourceReleased
//  （EngineResource.java:107-117 → Engine.java:398-405），那里先 deactivate 再 cache.put，两步之间没有
//  Engine 的锁；此刻另一条线程的 loadFromActiveResources（Engine.java:326-333）仍能从 ActiveResources
//  拿到同一个 wrapper 并 acquire()——这块资源就带着 acquired=1 进了内存缓存，等它被 LRU 淘汰就是上面那条
//  主线程延迟 recycle → 抛。也就是说「本类 acquired 只增不减 + 同一 key 上两套生命周期」正是这个交错的产生条件。
//现在是四条硬约定，改这里请一并守住：
//  1) 一律 asBitmap().disallowHardwareConfig() + submit()，拿到 Bitmap 后**先复制一份本进程自持的位图**（copyOwned）；
//  2) 唯一出口 clearFuture 里 manager.clear(future)——成功/失败/超时都还，acquired 一定回到 0；
//  3) submit 与 clear 都在 GLIDE_LOCK 里做，同一个表情 URL 的 acquire / release 不再跨线程交错（见 GLIDE_LOCK）；
//  4) ImageSpan 只画那份自有位图，绝不引用 Glide 的资源。
//为什么选择「复制」而不是「维持 acquired」：acquired 不归零就没有确定的释放时机（页面关掉也不一定触发），
//而一只 18sp 的表情位图复制一份约 12KB，一个 Spannable 用完就连同 span 一起被 GC，代价远小于崩溃。
public class EmoteUtil {
    /**
     * JSON 版入口（本仓库内已无调用方，保留给外部/私有模块）。
     *
     * <p>26.11.18：改成与 {@link #textReplaceEmote(String, ArrayList, float, Context, CharSequence)}
     * 走同一条路径——原来每个表情各自 {@code submit()}、各自等满 {@link #PARSE_TIMEOUT_MS}，
     * 同一 URL 在表里重复出现时还会被重复 acquire。现在先整体解析成 {@link Emote} 列表，
     * 交给 ArrayList 版：按 URL 去重提交、共用一个截止时间。
     *
     * <p>{@code throws} 保留原来的三样，避免影响既有调用方的签名适配（虽然已不再抛出）。
     */
    public static SpannableStringBuilder textReplaceEmote(String text, JSONArray emote, float scale, Context context) throws JSONException, ExecutionException, InterruptedException {
        SpannableStringBuilder result = new SpannableStringBuilder(text);
        if (emote == null || emote.length() == 0) return result;
        ArrayList<Emote> emotes = new ArrayList<>(emote.length());
        for (int i = 0; i < emote.length(); i++) {    //遍历每一个表情包
            JSONObject key = emote.getJSONObject(i);
            //B站十分贴心的帮你把表情包大小都写好了，快说谢谢蜀黍
            emotes.add(new Emote(key.getString("name"), key.getString("url"), key.getInt("size")));
        }
        return textReplaceEmote(result.toString(), emotes, scale, context, result);
    }

    /**
     * 26.10.27 性能：原实现逐个 {@code submit().get()}——一条评论里 5 个表情就是 5 次**串行**的
     * 阻塞请求，而 {@code Reply} 构造时就调本方法，一页 20 条评论 + 楼中楼的表情全部串起来等，
     * 手表弱网下评论区首屏要转很久才出来。现在先把本条文本里**真正出现**的表情一次性全部提交给 Glide
     * （并发下载），再按同一个截止时间逐个取结果；超时的表情保留原文字（如「[doge]」），不再无限等。
     *
     * <p>26.11.18：提交清单改成「按 URL 去重后各提交一次」（{@link #distinctEmoteUrls}）——同一个表情条目
     * 在一段文本里重复出现、或不同名字指向同一张图时，原来会重复 submit()，同一块 Engine 资源被 acquire 多次。
     * 取图统一走 {@link #loadOwnedBitmap}：拿到的是自有位图，Glide 那份在返回前已经 clear 掉。
     */
    public static SpannableStringBuilder textReplaceEmote(String text, ArrayList<Emote> emotes, float scale, Context context, CharSequence source) {
        SpannableStringBuilder result = (source instanceof SpannableStringBuilder)
                ? (SpannableStringBuilder) source
                : new SpannableStringBuilder(text);

        if (emotes == null || emotes.isEmpty() || context == null) return result;

        String origText = result.toString();
        List<Emote> present = presentEmotes(origText, emotes);
        if (present.isEmpty()) return result;

        List<String> urls = distinctEmoteUrls(present);
        RequestManager manager = emoteManager(context);
        if (manager == null) return result;

        // 一次全部提交（并发下载），再按同一个截止时间逐个取结果——这就是 26.10.27 那版性能改动的本意，
        // 26.11.18 只是把「取到后要 clear、要复制自有位图」塞进同一个出口
        Map<String, Bitmap> owned = loadOwnedBitmaps(manager, urls, SystemClock.elapsedRealtime() + PARSE_TIMEOUT_MS);

        for (Emote key : present) {
            Bitmap bitmap = owned.get(key.url);
            if (bitmap == null) continue;   // 超时的表情保留原文字
            applySpans(result, origText, key.name, ownedDrawable(bitmap), key.size, scale);
        }
        return result;
    }

    /**
     * 表情取图的 submit / clear 互斥锁。
     *
     * <p>为什么需要：{@code EngineResource.release()} 把计数减到 0 之后是**离开 synchronized 块**才回调
     * {@code listener.onResourceReleased}（EngineResource.java:107-117），而 {@code Engine.onResourceReleased}
     * （Engine.java:398-405）先 {@code activeResources.deactivate} 再 {@code cache.put}，中间没有 Engine 的锁。
     * 另一条线程此刻若经 {@code loadFromActiveResources}（Engine.java:326-333）拿到同一个 wrapper 并
     * {@code acquire()}，这块资源就会带着 acquired=1 进内存缓存；它被 LRU 淘汰时走
     * {@code Engine.onResourceRemoved}（Engine.java:391-395）→ 主线程延迟 recycle → 抛
     * 「Cannot recycle a resource while it is still acquired」。
     *
     * <p>把 submit（acquire 在 {@code RequestBuilder.into -> Engine.load} 里）与 clear（release 在
     * {@code RequestManager.clear -> request.clear} 里）串到同一把锁上，同一个 URL 的 acquire / release 就不再
     * 跨线程交错。锁**只**包住 submit 与 clear，取结果的 {@code future.get()} 在锁外，并发下载不受影响。
     */
    private static final Object GLIDE_LOCK = new Object();

    /** 单条文本解析时等表情的总时长上限（毫秒）。整页已预取时这里通常是内存缓存命中、瞬间返回。 */
    private static final long PARSE_TIMEOUT_MS = 5000;

    /** 整页预取的总时长上限（毫秒）。 */
    private static final long PREFETCH_TIMEOUT_MS = 10000;

    /**
     * 把一页内容里用到的全部表情**并发**预取进 Glide 缓存，随后逐条解析时就是缓存命中。
     *
     * <p>只在后台线程调用（会阻塞，最多 {@link #PREFETCH_TIMEOUT_MS}）。
     *
     * <p>26.11.18：**每一个** future 都 clear，包括超时未完成的。原实现只 clear 成功的那批，
     * 超时的 future 从此永远 acquired（一个弱网页面几十张图，每次翻页都攒一批），
     * 这是本类里最大的一处只增不减。代价是超时的那张图会被取消、随后解析时重下一遍——
     * 解析侧现在自己也要 clear（见 {@link #loadOwnedBitmap}），本来就不依赖「合并到预取请求上」这点收益。
     *
     * <p>用 {@code asBitmap()} 与解析侧保持一致：EngineKey 含 transcodeClass，两边都用 Bitmap
     * 才能落在同一个 key 上，预取预热的内存缓存解析阶段才命中得到。
     */
    public static void prefetch(Collection<String> urls) {
        Context context = BiliTerminal.context;
        if (context == null || urls == null || urls.isEmpty()) return;
        RequestManager manager = emoteManager(context);
        if (manager == null) return;
        List<String> distinct = distinctUrls(urls);
        if (distinct.isEmpty()) return;

        ArrayList<FutureTarget<Bitmap>> futures = new ArrayList<>(distinct.size());
        long begin = SystemClock.elapsedRealtime();
        long deadline = begin + PREFETCH_TIMEOUT_MS;
        int ok = 0;
        try {
            for (String url : distinct) futures.add(submitOwned(manager, url));
            for (FutureTarget<Bitmap> future : futures) {
                if (future != null && awaitBitmap(future, deadline) != null) ok++;
            }
        } catch (Throwable e) {
            // 预取只是加速，失败了逐条解析时照常加载
            Logu.w("EmoteUtil", "表情预取失败: " + e);
        } finally {
            // 单个 future 清不掉不影响其它 future 归还
            for (FutureTarget<Bitmap> future : futures) clearFuture(manager, future);
            Logu.d("EmoteUtil", "表情预取 " + ok + "/" + futures.size() + " 张，用时 " + (SystemClock.elapsedRealtime() - begin) + "ms");
        }
    }

    /**
     * 取一张表情图，返回**本进程自持**的 Bitmap；Glide 那块 Engine 资源在返回前必定 clear 掉。
     *
     * <p>为什么要自持一份：clear 之后 acquired 归零，资源退回内存 LRU（Engine.onResourceReleased，
     * Engine.java:398-405），随时会被淘汰并交回 BitmapPool 复用；ImageSpan 若还画着它，
     * 画的时候就是「trying to use a recycled bitmap」。复制一份 12KB 左右，换掉整类崩溃。
     *
     * <p>{@code finally} 是唯一出口：超时、解码失败、复制 OOM 都要还，否则就是老 bug 的原样复现。
     * 注意顺序——{@code copyOwned} 在 try 里先跑（此时 Glide 那块资源还没还、位图一定有效），
     * {@code finally} 里的 clear 在其后，拷贝出来的位图与 Glide 的资源再无关系。
     */
    private static Bitmap loadOwnedBitmap(RequestManager manager, String url, long deadline) {
        if (manager == null || TextUtils.isEmpty(url)) return null;
        return loadOwnedBitmaps(manager, Collections.singletonList(url), deadline).get(url);
    }

    /**
     * 批量取表情图：所有请求**一次性提交**（Glide 自己在后台线程池里并发解），再用同一个截止时间
     * 逐个取结果、复制成自有位图；{@code finally} 里把**每一个** future 都 clear 掉。
     *
     * <p>为什么必须批量：26.10.27 记下的性能问题——一条评论 5 个表情就是 5 次串行阻塞，
     * 整页评论首屏要等很久。提交循环与取结果循环必须分开，别为了「顺手 clear」把 submit 也挪进
     * 取结果的循环里，那样又变回串行。
     *
     * <p>为什么 finally 对**全部** future 都 clear：{@code submit()} 返回的 {@code RequestFutureTarget}
     * 只有 clear 才会让 Engine 的 acquired 归零（RequestManager.clear -> request.clear -> engine.release）；
     * 漏掉任何一个（超时的、提交到一半抛异常的）就是那块位图永久留在 ActiveResources 里。
     *
     * <p>为什么先复制再 clear：clear 之后资源退回内存 LRU、随时可能被淘汰并交回 BitmapPool 复用，
     * 那时再读就是「trying to use a recycled bitmap」。
     */
    private static Map<String, Bitmap> loadOwnedBitmaps(RequestManager manager, List<String> urls, long deadline) {
        Map<String, Bitmap> owned = new HashMap<>(urls == null ? 4 : Math.max(4, urls.size() * 2));
        if (manager == null || urls == null || urls.isEmpty()) return owned;
        ArrayList<FutureTarget<Bitmap>> futures = new ArrayList<>(urls.size());
        try {
            // 提交失败也占位（null），保证下标与 urls 对应
            for (String url : urls) futures.add(submitOwned(manager, url));
            for (int i = 0; i < urls.size(); i++) {
                FutureTarget<Bitmap> future = futures.get(i);
                if (future == null) continue;
                try {
                    Bitmap decoded = awaitBitmap(future, deadline);
                    Bitmap copy = decoded == null ? null : copyOwned(decoded);
                    if (copy != null) owned.put(urls.get(i), copy);
                } catch (Throwable e) {
                    // 这一张失败（含硬件位图画不进软件 Canvas）只丢这个表情，其余照常
                }
            }
        } catch (Throwable e) {
            // 队列/内存异常：已经取到的照常返回
        } finally {
            for (FutureTarget<Bitmap> future : futures) clearFuture(manager, future);
        }
        return owned;
    }

    /** 在 {@link #GLIDE_LOCK} 内提交一次请求；失败（manager 已销毁、OOM 等）返回 null，由调用方按「这张图没有」处理。 */
    private static FutureTarget<Bitmap> submitOwned(RequestManager manager, String url) {
        if (manager == null || TextUtils.isEmpty(url)) return null;
        try {
            synchronized (GLIDE_LOCK) {
                return manager.asBitmap().disallowHardwareConfig().load(url).submit();
            }
        } catch (Throwable e) {
            return null;
        }
    }

    /** 归还一个 {@code submit()} 出来的 future；清不掉（已被清过、manager 已销毁）只吞掉，不影响其它 future。 */
    private static void clearFuture(RequestManager manager, FutureTarget<?> future) {
        if (manager == null || future == null) return;
        try {
            synchronized (GLIDE_LOCK) {
                manager.clear(future);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 复制一份自有位图。
     *
     * <p>调用方一律带了 {@code disallowHardwareConfig()}：Glide 4.16 对没有 Transformation 的请求
     * 默认允许解出 {@link Bitmap.Config#HARDWARE} 位图，而硬件位图**画不进软件 Canvas**
     * （Glide 自己在 {@code BaseRequestOptions.java:670-672} 写明了这一点：「HARDWARE cannot be drawn to
     * Canvases」），{@code Canvas.drawBitmap} 会抛 "Software rendering doesn't support hardware bitmaps"。
     * 所以这条链上两件事必须同时成立：请求侧禁掉硬件位图 + 这里用 Canvas 拷一份软件位图。
     *
     * <p>不用 {@code Bitmap.copy()}：它对 HARDWARE 源的行为各版本不一致，而 Canvas 画一遍对所有
     * 软件 config（ARGB_8888 / RGB_565 / null）都成立。
     *
     * <p>万一仍拿到硬件位图（配置被别处改掉），这里抛的 IllegalArgumentException 会被
     * {@link #loadOwnedBitmaps} 里那张图的兜底 catch 吃掉，退化成「这个表情显示原文字」，不会崩页面。
     */
    private static Bitmap copyOwned(Bitmap decoded) {
        int width = decoded.getWidth();
        int height = decoded.getHeight();
        if (width <= 0 || height <= 0) return null;
        Bitmap owned = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        new Canvas(owned).drawBitmap(decoded, 0f, 0f, null);
        return owned;
    }

    /** 在截止时间前取结果；超时/失败返回 null。截止时间已过也再非阻塞地试一次（已完成的仍能拿到）。 */
    private static Bitmap awaitBitmap(FutureTarget<Bitmap> future, long deadline) {
        try {
            long remain = Math.max(1, deadline - SystemClock.elapsedRealtime());
            return future.get(remain, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 表情图一律走 **Application 级** RequestManager，三个理由：
     * <ol>
     *   <li>{@code Glide.with(ctx)} 在主线程传 Activity 拿到的是 Activity 的 manager，它 onDestroy 时会
     *       clear 掉自己名下所有 target，而表情解析常常在后台线程里比页面活得久；</li>
     *   <li>{@code RequestManager.untrackOrDelegate}（RequestManager.java:646-666）明确把「后台线程用
     *       非 Application manager 调 clear()」记为 error；本类的 clear 都在解析线程上，必须避开这种用法；</li>
     *   <li>只有同一个 manager 才保证预取与解析拿到同一套默认 RequestOptions → 同一个 EngineKey
     *       （EngineKey 含 transcodeClass 与 transformations），预取预热的内存缓存才命中得到。</li>
     * </ol>
     * 注意 {@code Glide.with(Context)} 本身在后台线程就会回落到 Application manager
     * （RequestManagerRetriever.java:87-104），这里显式取一次应用 Context 是为了让主线程上的调用方
     * （私信 Adapter 在主线程绑定）也落在同一个 manager 上。
     */
    private static RequestManager emoteManager(Context context) {
        Context app = null;
        if (context != null) {
            Context applicationContext = context.getApplicationContext();
            app = applicationContext != null ? applicationContext : context;
        }
        if (app == null) app = BiliTerminal.context;
        if (app == null) return null;
        try {
            return Glide.with(app);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 把自持位图包成 Drawable（每个名字一个实例：setBounds 记在 Drawable 上，共用会互相覆盖）。 */
    private static Drawable ownedDrawable(Bitmap bitmap) {
        Context context = BiliTerminal.context;
        // 只用到 Resources 的 density 元数据；本类一律 setBounds 且按 bounds 绘制，传 null 也能画
        return new BitmapDrawable(context == null ? null : context.getResources(), bitmap);
    }

    /**
     * 纯逻辑（不碰 android.*，便于 JVM 单测）：挑出文本里**真的出现**的表情，按表情表顺序、跳过空项。
     *
     * <p>先判出现再提交，避免为用不到的图白下载（26.10.27 性能）。
     */
    public static List<Emote> presentEmotes(String origText, Collection<Emote> emotes) {
        List<Emote> present = new ArrayList<>();
        if (origText == null || emotes == null) return present;
        for (Emote emote : emotes) {
            if (emote == null) continue;
            if (emote.name == null || emote.name.isEmpty()) continue;
            if (emote.url == null || emote.url.isEmpty()) continue;
            if (!origText.contains(emote.name)) continue;
            present.add(emote);
        }
        return present;
    }

    /** 纯逻辑：按出现顺序去重 URL（同一张图在一页里常被多个位置引用，只该提交/复制一次）。 */
    public static List<String> distinctUrls(Collection<String> urls) {
        List<String> out = new ArrayList<>();
        if (urls == null) return out;
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String url : urls) {
            if (url == null || url.isEmpty()) continue;
            if (seen.add(url)) out.add(url);
        }
        return out;
    }

    /** 纯逻辑：{@link #presentEmotes} 结果的 URL 去重清单（提交顺序 = 表情表顺序）。 */
    public static List<String> distinctEmoteUrls(Collection<Emote> emotes) {
        List<String> out = new ArrayList<>();
        if (emotes == null) return out;
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Emote emote : emotes) {
            if (emote == null || emote.url == null || emote.url.isEmpty()) continue;
            if (seen.add(emote.url)) out.add(emote.url);
        }
        return out;
    }

    /** 给文本里所有出现的 name 挂上同一张图（ImageSpan 每处要新建，drawable 可共用，与旧实现一致）。 */
    private static void applySpans(SpannableStringBuilder spannableString, String origText, String name,
                                   Drawable drawable, int size, float scale) {
        // 防御：name 为空时 origText.indexOf("") 恒为 0，下面那个 while 会死循环（调用方目前都已过滤，这里再兜一层）
        if (TextUtils.isEmpty(name)) return;
        int px = (int) (size * ToolsUtil.sp2px(18) * scale);  //参考了隔壁腕上哔哩并进行了改进
        drawable.setBounds(0, 0, px, px);
        int start = origText.indexOf(name);
        while (start >= 0) {
            int end = start + name.length();
            spannableString.setSpan(new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM), start, end, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = origText.indexOf(name, end);
        }
    }

    public static SpannableStringBuilder textReplaceEmote(String text, ArrayList<Emote> emotes, float scale, Context context) {
        return textReplaceEmote(text, emotes, scale, context, null);
    }

    public static void replaceSingle(SpannableStringBuilder spannableString, String name, String url, int size, float scale, Context context) {
        try {
            String origText = spannableString.toString();
            // 26.10.27 性能：先判文本里有没有这个表情再下载。私信页传进来的是整个会话的表情表，
            // 原实现对每条消息都把表里**每一张**图同步取一遍，绝大多数根本不在这条消息里。
            if (TextUtils.isEmpty(name) || !origText.contains(name)) return;
            Bitmap bitmap = loadOwnedBitmap(emoteManager(context), url,
                    SystemClock.elapsedRealtime() + PARSE_TIMEOUT_MS);
            if (bitmap == null) return;
            applySpans(spannableString, origText, name, ownedDrawable(bitmap), size, scale);
        } catch (Exception ignored) {
        }
    }

    public static void replaceSingle(SpannableStringBuilder spannableString, String url, int size, int start, int end, float scale) {
        try {
            Bitmap bitmap = loadOwnedBitmap(emoteManager(BiliTerminal.context), url,
                    SystemClock.elapsedRealtime() + PARSE_TIMEOUT_MS);
            if (bitmap == null) return;
            Drawable drawable = ownedDrawable(bitmap);
            int px = (int) (size * ToolsUtil.sp2px(18) * scale);
            drawable.setBounds(0, 0, px, px);
            ImageSpan imageSpan = new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM);
            spannableString.setSpan(imageSpan, start, end, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (Exception ignored) {
        }
    }

}
