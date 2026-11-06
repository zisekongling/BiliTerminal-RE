package com.RobinNotBad.BiliClient.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Inflater;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 被 luern0313 创建于 2019/10/13.
 * #以下代码来源于腕上哔哩的开源项目，感谢开源者做出的贡献！
 */

public class NetWorkUtil {
    private static final AtomicReference<OkHttpClient> INSTANCE = new AtomicReference<>();
    private static volatile String cachedCookies = null;

    /**
     * 账号代际（审计 §7.1「切号没有 Cookie 代际隔离」）。
     *
     * <p>问题：切号 / 重新登录时只换了 SP 里的 Cookie 字符串，但**旧账号还在飞的请求**回来时，
     * 拦截器照样把它的 Set-Cookie 合并进存储——于是新账号的 SESSDATA 被旧账号的覆盖，
     * csrf 快照被改成旧账号的 bili_jct，表现为「切完号偶尔又变回上一个人 / 发评论 -111」。
     *
     * <p>做法：每次全局登录态被替换（切号、登录完成、退出登录）就把代际 +1；
     * 每个请求在**创建时**打上当时的代际标签（{@link Request.Builder#tag(Class, Object)}），
     * 响应回来要写 Cookie 前在**同一把锁里**再比一次：代际不同就整个丢弃。
     * 锁保证「比代际 + 写 Cookie」与「升代际 + 写新 Cookie」互斥，不存在写了一半的中间态。
     */
    private static volatile long accountGeneration = 0L;

    /**
     * HTTP 追踪总开关（26.10.14，由 [com.RobinNotBad.BiliClient.BiliTerminal.applyLogSwitches] 写入）。
     *
     * <p>原实现只在 `get()` / `post()` 里记 URL 和 POST 正文，**不记状态码、不记响应体、不记耗时**：
     * 风控拦截返回 HTML、接口返回 `code=-412`、请求根本没发出去，这三种情况在日志里长得一模一样，
     * 只能靠复现和猜。打开这个开关后每次请求都会落一行
     * `方法 脱敏URL -> 状态码 (耗时ms) + 响应体摘要`，落盘到 `logs/http.log`。
     *
     * <p>Release 构建恒为 false（赋值处被 `BuildConfig.DEBUG` 折叠），因此发布包零开销、零泄漏。
     */
    public static volatile boolean HTTP_TRACE = false;

    /** 追踪时最多预览多少字节的响应体：够看清 `code` / `message`，又不至于把大响应全读一遍。 */
    private static final long HTTP_TRACE_MAX_BODY = 4096;

    /**
     * URL 查询串里需要脱敏的参数名。
     *
     * <p>`access_key` / `csrf`(bili_jct) 都是**可直接冒用身份的凭证**——写进日志等于把会话明文
     * 留在设备与 logcat 里（与审计 S2「只记 Cookie 键名、不记值」同一条原则）。
     *
     * <p>**公开可见是为了让 [com.RobinNotBad.BiliClient.util.LogSanitizer] 复用同一份清单**：
     * 日志缓冲与 HTTP 追踪都要脱敏，各写一份迟早改漏一处；此处是唯一真相来源。
     */
    public static final String[] SENSITIVE_QUERY_KEYS = {
            "access_key", "csrf", "bili_jct", "SESSDATA", "refresh_token", "token", "sign", "appkey"
    };

    /**
     * 查询串里的**噪音**参数：值替换成 `…`，但保留键名。
     *
     * <p>这几个是 B 站 WBI 风控的浏览器指纹 blob（`dm_img_list` 单个就上千字符），对排障毫无价值，
     * 却能让一行日志从 600 字符涨到 2200+。实测打开追踪后跑几个页面，日志就飙到 74KB——
     * 按 2MB 滚动上限算，几十次页面加载就把历史冲掉了。
     *
     * <p>**公开可见是为了让 [com.RobinNotBad.BiliClient.util.LogSanitizer] 复用同一份清单**，
     * 理由同上（SENSITIVE_QUERY_KEYS）。
     */
    public static final String[] NOISE_QUERY_KEYS = {
            "dm_img_list", "dm_img_inter", "dm_img_str", "dm_cover_img_str"
    };

    /** 追踪行里 URL 的长度上限，兜住未知的超长参数。 */
    private static final int MAX_TRACE_URL_CHARS = 600;

    /**
     * 追踪行里请求体 / 响应体摘要的长度上限。
     *
     * <p>实测：即便把 WBI 指纹 blob 压掉，一行仍会到 1779 字符——大头就是响应体预览。
     * 追踪日志的职责是「哪个接口、什么状态码、耗时多少、大致回了什么」，600 字符足够看清
     * `code` / `message` 和头一两条数据；要看**完整**响应体请用调试桥的 `http` 命令
     * （它会把 body 全文落到 `bridge-http-response.txt`）。
     */
    private static final int MAX_TRACE_BODY_CHARS = 600;

    /**
     * 返回内存缓存的 cookie 字符串，避免每次请求都读 SharedPreferences。
     * 在 putCookie / setCookies / saveCookiesFromResponse 中更新。
     */
    public static String getCachedCookies() {
        String cookies = cachedCookies;
        if (cookies == null) {
            cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
            // 只有 SharedPreferences 已就绪时才允许写缓存：webHeaders 的静态初始化可能
            // 发生在 Application.onCreate 赋值 sharedPreferences 之前，此时读到的必然是
            // 默认空串。若把它当成真实 Cookie 缓存下来，整个进程会一直误判为"未登录"，
            // 直到下一次 Cookie 写入才恢复。
            if (SharedPreferencesUtil.sharedPreferences != null) {
                cachedCookies = cookies;
            }
        }
        return cookies;
    }

    /**
     * 直接写入 cookie 字符串并同步内存缓存（供登录/刷新等场景使用）
     */
    public static void setCookiesString(String cookies) {
        SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, cookies);
        cachedCookies = cookies;
        refreshHeaders();
    }

    /** 当前账号代际；请求创建时打标签、响应落盘前用它判断是否已经过期。 */
    public static long getAccountGeneration() {
        return accountGeneration;
    }

    /**
     * 代际 +1，让所有在飞的旧请求失效（不写 Cookie）。
     *
     * <p>真的换 Cookie 请直接调 {@link #applyAccountCookies(String)}——它把「升代际」和
     * 「写新 Cookie」放在同一把锁里，避免旧响应在两步之间挤进来写回旧值。
     */
    public static void bumpAccountGeneration() {
        synchronized (NetWorkUtil.class) {
            accountGeneration++;
        }
    }

    /**
     * 原子地替换整套 Cookie：升代际 + 写新值 + 刷新请求头，全部在同一把锁内完成。
     *
     * <p>登录完成、切号、退出登录都必须走这里，而不是 {@link #setCookiesString(String)}：
     * 后者不升代际，旧账号在飞的响应回来还能把 Cookie 写回去。
     *
     * @param cookies 新账号的完整 Cookie 字符串，退出登录时传空串
     */
    public static void applyAccountCookies(String cookies) {
        synchronized (NetWorkUtil.class) {
            accountGeneration++;
            SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, cookies == null ? "" : cookies);
            cachedCookies = cookies == null ? "" : cookies;
            refreshHeaders();
        }
    }

    /**
     * 创建带账号代际标签的请求构造器。
     *
     * <p>**所有**出口请求都必须从这里构造：响应回来时 {@link #saveCookiesFromResponse(Response)}
     * 靠这个标签判断它是不是上一任账号的「在飞请求」，是就丢弃 Set-Cookie（审计 §7.1）。
     * 新增请求入口时漏掉这一行，代际隔离对那条请求就失效了。
     */
    private static Request.Builder newTaggedRequestBuilder() {
        // 注意这里是 new Request.Builder()，不是递归调自己——之前把全文件的
        // `new Request.Builder()` 无脑替换成本方法时，把方法体里这一行也替换了
        return new Request.Builder().tag(Long.class, accountGeneration);
    }

    /**
     * 判断一条响应对应的请求是否属于「已经过去的账号代际」。
     *
     * <p>没有打标签的请求（理论上不该有，兜底按当前代际处理）当新请求放行。
     * 抽成纯函数是为了能在 JVM 单测里覆盖，不用起 OkHttp。
     */
    static boolean isStaleGeneration(Long requestGeneration, long currentGeneration) {
        return requestGeneration != null && requestGeneration != currentGeneration;
    }

    public static class Inet4Selector implements Dns {
        @NonNull
        @Override
        public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
            List<InetAddress> hosts = Dns.SYSTEM.lookup(hostname);
            List<InetAddress> inet4Hosts = new ArrayList<>();
            for (InetAddress host : hosts) {
                if (host.getAddress().length == 4) inet4Hosts.add(host);
            }
            return inet4Hosts;    //筛选IPV4地址，IPV6请求有异常
        }
    }

    public static OkHttpClient getOkHttpInstance() {
        while (INSTANCE.get() == null) {
            INSTANCE.compareAndSet(null, setOkHttpSsl(new OkHttpClient.Builder())
                    .followRedirects(false)
                    .addInterceptor(chain -> {
                        Request request = chain.request();
                        Response response = chain.proceed(request);
                        RedirectHandler handler;
                        String location = response.header("Location");
                        boolean isSslRedirect = false;
                        try {
                            if (location != null && !request.isHttps()) {
                                URI locationUri = new URI(location);
                                String scheme = locationUri.getScheme();
                                String host = locationUri.getHost();
                                // 相对路径 Location 时 scheme/host 为 null，必须先判空避免 NPE
                                if (scheme != null && host != null) {
                                    isSslRedirect = scheme.equalsIgnoreCase("https") && request.url().host().equalsIgnoreCase(host);
                                }
                            }
                        } catch (URISyntaxException ignored) {
                        }

                        if (response.isRedirect() && location != null) {
                            if (request.url().host().equals("b23.tv") && !isSslRedirect && (handler = request.tag(RedirectHandler.class)) != null) {
                                handler.handleRedirect(location);
                            } else {
                                // 安全边界（审计 S1）：手动跟跳会把完整请求头（含登录 Cookie）带到新地址，
                                // 因此 Location 必须解析成绝对地址并落在 B 站域名白名单内才允许跟随。
                                // 被劫持/伪造的 302 再也无法把凭据引到任意主机：命中不了就原样返回这枚 3xx。
                                HttpUrl target = request.url().resolve(location);
                                if (target == null || !isBilibiliHost(target.host())) {
                                    return response;
                                }
                                // 用 request tag 累计跳数，封死"白名单内互相跳转"构成的无限重定向环
                                Integer hopsTag = request.tag(Integer.class);
                                int hops = hopsTag != null ? hopsTag : 0;
                                if (hops >= 5) {
                                    return response;
                                }
                                // 手动跟进重定向前必须先关闭原响应，否则连接泄漏
                                response.close();
                                Request newRequest = request.newBuilder()
                                        .url(target)
                                        .tag(Integer.class, hops + 1)
                                        .build();
                                return chain.proceed(newRequest);
                            }
                        }
                        return response;
                    })
                    .addInterceptor(new CookieSaveInterceptor())
                    .addInterceptor(new HttpTraceInterceptor())
                    .dns(new Inet4Selector())
                    .pingInterval(8, TimeUnit.SECONDS)
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(16, TimeUnit.SECONDS)
                    .writeTimeout(16, TimeUnit.SECONDS).build());
            // 有意不设 callTimeout：本实例是全 App 唯一的 OkHttpClient，既跑普通 API，也跑
            // DownloadActivity / UpdateManager 的大文件流式下载；callTimeout 覆盖"整个调用含读完 body"，
            // 一旦设置就会把耗时正常的长下载一并掐断。单次调用的时长上限交给
            // connectTimeout/readTimeout，以及下载侧自己的进度/取消逻辑。
        }
        return INSTANCE.get();
    }

    /**
     * 本方法历史上在 Android 5.1 及以下会启用 trust-all 证书校验（信任任意自签证书）。
     * 那个分支当年靠 `Build.VERSION.SDK_INT > 22` 兜住（当时 minSdk 24，恒真），但留在源码里
     * 等于给未来预留了一个"静默降级 TLS"的开关。26.10.10 minSdk 已下调到 21，那层闸再也
     * 兜不住这件事，所以分支被整体删除，本方法无条件返回调用方传入的 builder
     * （即系统默认证书校验，登录 Cookie 不会被中间人截获）。
     * 方法签名保留：CustomGlideModule 与本类仍按此入口构造 OkHttpClient。
     */
    public synchronized static OkHttpClient.Builder setOkHttpSsl(OkHttpClient.Builder okhttpBuilder) {
        return okhttpBuilder;
    }

    /**
     * akamai 镜像白名单（B 站 PCDN/直链会跳到这里）。
     * 必须用精确主机名而不是 `endsWith(".akamaized.net")`：akamaized.net 是 Akamai 的共享域，
     * 后缀放行等于允许任意第三方 akamaized.net 主机接到携带登录 Cookie 的跟跳请求。
     */
    private static final List<String> BILIBILI_AKAMAI_MIRROR_HOSTS = Arrays.asList(
            "upos-sz-mirrorakam.akamaized.net",
            "upos-hz-mirrorakam.akamaized.net");

    /**
     * 判断主机是否属于 B 站自有/可信域名。
     * 供手动跟随重定向（本类拦截器）与 OpusApi 手动跟跳复用：
     * 只有命中白名单才允许把带 Cookie 的请求发过去，否则一律停止跟随（fail-closed）。
     * 注意 bilibili.cn 是本项目在对方白名单之外额外放行的 B 站自有域名。
     */
    public static boolean isBilibiliHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("b23.tv")
                || h.equals("bilibili.com") || h.endsWith(".bilibili.com")
                || h.equals("bilibili.cn") || h.endsWith(".bilibili.cn")
                || h.endsWith(".bilivideo.com")
                || h.endsWith(".hdslb.com")
                || BILIBILI_AKAMAI_MIRROR_HOSTS.contains(h);
    }

    public static JSONObject getJson(String url) throws IOException, JSONException {
        return executeJsonWithRiskRetry(url, webHeaders);
    }

    public static JSONObject getJson(String url, ArrayList<String> headers) throws IOException, JSONException {
        return executeJsonWithRiskRetry(url, headers);
    }

    /**
     * 不跑任何重试的 JSON 请求：只发**一次**，`-352` / `-412` 原样返回，由调用方自己决定怎么处理。
     *
     * 给「已经确定处在风控状态」的场景用：搜索在 wbi 接口被拦后要回退旧接口，那次回退再走
     * 通用重试的话两条接口各重试 5 次 = 一次搜索最多 10 个请求，只会把限流拖得更久
     * （26.10.24 审查指出）。默认的 {@link #getJson(String)} 行为不变。
     *
     * **连 doctype 重试也一并跳过**：`get()` 走 {@link #executeWithDoctypeRetry}，会把
     * `<!doctype html>` 响应当成风控拦截再重试 5 次——风控页恰恰就是一张 HTML，兜底链路上
     * 再重试 5 次等于没省（26.10.25 审查指出）。这里直连一次：拿到 HTML 就按「不是 JSON」
     * 在解析处失败，由调用方决定要不要回退 / 上抛。
     */
    public static JSONObject getJsonOnce(String url) throws IOException, JSONException {
        return executeJsonOnce(url, webHeaders);
    }

    /**
     * 隐私模式下的JSON请求，使用游客Cookie（剔除登录态Cookie），避免详情请求暴露个人状态
     */
    public static JSONObject getJsonPrivacy(String url) throws IOException, JSONException {
        ArrayList<String> headers = new ArrayList<>(webHeaders);
        headers.set(1, buildGuestCookieString(getCachedCookies()));
        return executeJsonWithRiskRetry(url, headers);
    }

    /**
     * 从完整Cookie字符串构建游客Cookie：剔除登录相关项，保留 buvid3/buvid4/bili_ticket/_uuid 等游客身份Cookie
     *
     * @param cookieString 完整Cookie字符串（形如 "a=1; b=2"）
     * @return 游客Cookie字符串
     */
    public static String buildGuestCookieString(String cookieString) {
        if (cookieString == null || cookieString.isEmpty()) return "";
        String[] excluded = {"SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "sid"};
        StringBuilder sb = new StringBuilder();
        String[] cookies = cookieString.split("; ");
        for (String cookie : cookies) {
            int eq = cookie.indexOf('=');
            if (eq <= 0) continue;
            String name = cookie.substring(0, eq);
            boolean skip = false;
            for (String ex : excluded) {
                if (ex.equals(name)) {
                    skip = true;
                    break;
                }
            }
            if (skip) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(cookie);
        }
        return sb.toString();
    }

    private static JSONObject executeJsonWithRiskRetry(String url, ArrayList<String> headers) throws IOException, JSONException {
        int maxRetries = Math.max(1, SharedPreferencesUtil.getInt("api_retry_max_times", 5));
        long retryIntervalMs = Math.max(0L, (long) (SharedPreferencesUtil.getFloat("api_retry_interval_seconds", 0.1f) * 1000));

        JSONObject json = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try (ResponseBody body = get(url, headers).body()) {
                if (body != null) json = new JSONObject(body.string());
                else throw new JSONException("在访问" + url + "时返回数据为空");
            }
            int code = json.optInt("code", 0);
            // #60：统一响应入口识别「账号未登录 / 登录态过期」（-101）。这里**只打标记**，
            // 真正的刷新由 CookieRefreshScheduler 按节流策略派到后台线程池执行——当前线程是
            // 调用方的请求线程，在这里嵌套发起刷新会拖慢本次调用，也可能让一次性 refresh_token
            // 被并发轮换。只认「带着 SESSDATA 的请求仍被回 -101」：未登录 / 隐私模式本来就用
            // 游客 Cookie，那种 -101 是预期内的，不该触发登录态刷新。
            if (code == -101 && carriesSessionCookie(headers)) {
                CookieRefreshScheduler.onLoginExpired();
            }
            if (code != -352 && code != -412) {
                return json;
            }
            Logu.d("RiskRetry", "检测到风控错误码 " + code + "，第" + attempt + "次重试...");
            if (attempt < maxRetries && retryIntervalMs > 0) {
                try {
                    Thread.sleep(retryIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return json;
    }

    /**
     * 这次请求的 Cookie 里是否带着登录态（SESSDATA）。
     *
     * <p>用途：判断一个 `code=-101` 到底是不是「登录态过期了」。#60 的轻量钩子只在
     * 「我们明明带着 SESSDATA 去请求，服务端还说未登录」时才标记需要刷新；未登录用户与
     * 隐私模式（{@link #buildGuestCookieString(String)} 会剔除登录 Cookie）的 -101 都是预期内的。
     *
     * @param headers 与 {@link #getJson(String, ArrayList)} 同格式的键值对列表（name, value, name, value…）
     */
    private static boolean carriesSessionCookie(ArrayList<String> headers) {
        if (headers == null) return false;
        for (int i = 0; i + 1 < headers.size(); i += 2) {
            String name = headers.get(i);
            if (name == null || !"Cookie".equalsIgnoreCase(name)) continue;
            String cookie = headers.get(i + 1);
            return cookie != null && !cookie.isEmpty()
                    && !getInfoFromCookie("SESSDATA", cookie).isEmpty();
        }
        return false;
    }

    /**
     * {@link #getJsonOnce(String)} 的实现：**单次直连**，不经过 {@link #executeWithDoctypeRetry}。
     *
     * 直接用 OkHttp 发一次，是为了让风控回退链路真的只产生一个请求（见 {@link #getJsonOnce}）。
     */
    private static JSONObject executeJsonOnce(String url, ArrayList<String> headers) throws IOException, JSONException {
        Logu.d("get-url", url);
        Request.Builder requestBuilder = newTaggedRequestBuilder().url(url).get();
        for (int i = 0; i < headers.size(); i += 2)
            requestBuilder.addHeader(headers.get(i), headers.get(i + 1));
        try (Response response = getOkHttpInstance().newCall(requestBuilder.build()).execute()) {
            saveCookiesFromResponse(response);
            ResponseBody body = response.body();
            if (body == null) throw new JSONException("在访问" + url + "时返回内容为空");
            return new JSONObject(body.string());
        }
    }

    public static Response get(String url) throws IOException {
        return get(url, webHeaders);
    }

    /**
     * 抓取 HTML 页面（如 Opus/专栏详情页）。
     * HTML 响应本就以 {@code <!doctype html>} 开头，不能走 {@link #executeWithDoctypeRetry}
     * （它会把合法的 DOCTYPE 页面误判为风控拦截并丢弃，导致整篇文章加载失败），
     * 因此这里直接执行请求，不做 doctype 重试。
     */
    public static Response getHtml(String url) throws IOException {
        Logu.d("get-url", url);
        OkHttpClient client = getOkHttpInstance();
        Request.Builder requestBuilder = newTaggedRequestBuilder().url(url).get();
        for (int i = 0; i < webHeaders.size(); i += 2)
            requestBuilder.addHeader(webHeaders.get(i), webHeaders.get(i + 1));
        Response response = client.newCall(requestBuilder.build()).execute();
        saveCookiesFromResponse(response);
        return response;
    }

    public static Response get(String url, ArrayList<String> headers) throws IOException {
        return get(url, headers, null);
    }

    public static Response get(String url, ArrayList<String> headers, RedirectHandler redirectHandler) throws IOException {
        Logu.d("get-url", url);
        OkHttpClient client = getOkHttpInstance();
        Request.Builder requestBuilder = newTaggedRequestBuilder().url(url).get();
        for (int i = 0; i < headers.size(); i += 2)
            requestBuilder.addHeader(headers.get(i), headers.get(i + 1));
        if (redirectHandler != null) requestBuilder.tag(RedirectHandler.class, redirectHandler);
        Request request = requestBuilder.build();
        return executeWithDoctypeRetry(client, request);
    }

    private static Response executeWithDoctypeRetry(OkHttpClient client, Request request) throws IOException {
        int maxRetries = Math.max(1, SharedPreferencesUtil.getInt("api_retry_max_times", 5));
        long retryIntervalMs = Math.max(0L, (long) (SharedPreferencesUtil.getFloat("api_retry_interval_seconds", 0.1f) * 1000));
        
        IOException lastException = null;
        Response response = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            if (response != null) {
                response.close();
                response = null;
            }
            
            try {
                response = client.newCall(request).execute();
                
                if (!isDoctypeResponse(response)) {
                    return response;
                }
                
                Logu.d("DoctypeRetry", "检测到DOCTYPE响应（风控拦截），第" + attempt + "次重试...");
                response.close();
                response = null;
                
            } catch (IOException e) {
                lastException = e;
                // 调用方主动取消时（OkHttp 抛 IOException("Canceled")）重试没有意义：
                // 结果已经没人要了，重试只会再发一次请求、再占一条连接，而且新 call 未必继承取消状态。
                if ("Canceled".equals(e.getMessage())) {
                    throw e;
                }
                Logu.d("DoctypeRetry", "网络异常，第" + attempt + "次重试: " + e.getMessage());
            }
            
            if (attempt < maxRetries && retryIntervalMs > 0) {
                try {
                    Thread.sleep(retryIntervalMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        if (response != null) {
            return response;
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new IOException("请求失败");
    }

    private static boolean isDoctypeResponse(Response response) {
        ResponseBody body = response.body();
        if (body == null) return false;
        
        try {
            okio.BufferedSource source = body.source();
            source.request(128);
            okio.Buffer buffer = source.buffer();
            String peek = buffer.clone().readUtf8(128).trim().toLowerCase();
            return peek.startsWith("<!doctype");
        } catch (Exception e) {
            return false;
        }
    }

    public static Response post(String url, String data, List<String> headers, String contentType) throws IOException {
        Logu.d("post-url", url);
        // 只记长度、不记 body：body 里常有 csrf（bili_jct）、refresh_token、access_key 等凭据，
        // 而 Logu.d 的 logcat 这一路**不做脱敏**（脱敏只发生在落盘的 toFile 里），原文会直接进 logcat。
        Logu.d("post-data", data == null ? "null" : ("length=" + data.length()));
        OkHttpClient client = getOkHttpInstance();
        RequestBody body = RequestBody.create(MediaType.parse(contentType + "; charset=utf-8"), data);
        Request.Builder requestBuilder = newTaggedRequestBuilder().url(url).post(body);
        for (int i = 0; i < headers.size(); i += 2) {
            String key = headers.get(i);
            String val = headers.get(i + 1);
            if (key.equalsIgnoreCase("Content-Type")) val = contentType;
            requestBuilder.addHeader(key, val);
        }
        Request request = requestBuilder.build();
        Response response = client.newCall(request).execute();
        // 非 2xx 失败关闭：body 往往是空串或风控 HTML，交给调用方 new JSONObject() 只会退化成
        // 「未知错误」，也可能让调用方把半截内容当成成功数据。口径与响应消费侧一致
        // （UpdateManager.kt / DownloadService.kt 都先判 isSuccessful）。3xx 同样落在这里：
        // 白名单外重定向由拦截器原样放回，本来就不该当成功处理。
        if (!response.isSuccessful()) {
            int code = response.code();
            response.close();
            throw new IOException("HTTP " + code + " " + request.url().encodedPath());
        }
        return response;
    }

    public static Response post(String url, String data, List<String> headers) throws IOException {
        return post(url, data, headers, "application/x-www-form-urlencoded");
    }

    public static Response postJson(String url, String data, List<String> headers) throws IOException {
        return post(url, data, headers, "application/json");
    }

    public static Response postJson(String url, String data) throws IOException {
        return post(url, data, webHeaders, "application/json");
    }

    public static Response post(String url, String data) throws IOException {
        return post(url, data, webHeaders);
    }


    /**
     * 从 Cookie 串里取指定键对应的值，取不到返回空串。
     *
     * 审计 M1：原实现是
     * <pre>if (i.contains(name + "=")) return i.substring(name.length() + 1);</pre>
     * 匹配用子串、取值用前缀裁剪，两者并不一致：
     * 键名出现在条目中间时（例如查 SESSDATA 而条目是 {@code x_SESSDATA=abc}）也会命中，
     * 但 substring 是按 name 的长度从头切的，切回来的是错位的垃圾串；
     * 条目带前导空格时同样错位。这里改为严格按 "key=" 前缀比对。
     */
    public static String getInfoFromCookie(String name, String cookie) {
        if (name == null || name.isEmpty() || cookie == null || cookie.isEmpty()) return "";
        String prefix = name + "=";
        for (String i : cookie.split(";")) {
            String item = i.trim();
            if (item.startsWith(prefix))
                return item.substring(prefix.length());
        }
        return "";
    }

    /**
     * 取当前有效的 csrf。
     *
     * 不能只读 {@code SharedPreferencesUtil.csrf}：bilibili 会在**任意响应**里通过 Set-Cookie 轮换
     * bili_jct，而那个字段只在「登录成功 / 刷新 Cookie 成功」那一刻写入。两者一旦错位，所有
     * POST 都会拿到 -111，而 GET 一切正常 —— 表现出来只是「点赞/评论/收藏偶尔点了没反应」，
     * 且同一份代码在不同设备/登录时机表现不同，极难复现。
     *
     * 这里是全库唯一入口：优先从实时 Cookie 派生，取不到再退回快照。所有 POST 的 csrf 参数
     * 都应调本方法，不要再直接读 {@code SharedPreferencesUtil.csrf}。
     */
    public static String currentCsrf() {
        return pickCsrf(getCachedCookies(), SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, ""));
    }

    /**
     * {@link #currentCsrf()} 的纯逻辑部分（单独抽出以便 JVM 单测）。
     *
     * @param cookieString 实时 Cookie 串，可为 null
     * @param storedCsrf   登录/刷新时写入的快照，可为 null
     * @return 实时 Cookie 里的 bili_jct；没有则退回快照；都没有则空串
     */
    public static String pickCsrf(String cookieString, String storedCsrf) {
        String live = getInfoFromCookie("bili_jct", cookieString);
        if (live != null && !live.isEmpty()) return live;
        return storedCsrf == null ? "" : storedCsrf;
    }

    private static void saveCookiesFromResponse(Response response) {
        if (response == null || response.request() == null) return;

        // 审计 P2-5：Set-Cookie 只收 B 站域响应下发的。手动跟跳、下载、图片等请求都复用这套
        // 客户端，若不管来源，一次跳去站外的响应就能往 Cookie 存储里塞任意键值
        // （覆盖 SESSDATA / bili_jct 即等于劫持会话）。来源判定复用 isBilibiliHost。
        HttpUrl url = response.request().url();
        String responseHost = url == null ? null : url.host();
        if (!isBilibiliHost(responseHost)) {
            Logu.d("saveCookiesFromResponse", "跳过非 B 站来源的 Set-Cookie：" + responseHost);
            return;
        }

        List<String> newCookies = response.headers("Set-Cookie");

        //如果没有新cookies，直接返回
        if (newCookies.isEmpty()) return;
        // 审计 §7.1：这条响应发出的那一刻属于哪个账号代际？标签在请求创建时打上（见
        // newTaggedRequestBuilder），此处必须**在锁内**比较——锁外比完再进锁，
        // 中间完全可能插进一次切号，那这次比较就白做了。
        Long requestGeneration = response.request().tag(Long.class);
        // 本方法由 CookieSaveInterceptor 在任意线程调用，必须与 putCookie/setCookies/getCookies
        // 共用同一把锁，否则 read-modify-write 互相覆盖会导致偶发登录态丢失。
        synchronized (NetWorkUtil.class) {
            if (isStaleGeneration(requestGeneration, accountGeneration)) {
                // 只记代际数字，不记 Cookie 键值（审计 S2）
                Logu.d("saveCookiesFromResponse", "丢弃旧账号代际的 Set-Cookie：req="
                        + requestGeneration + " cur=" + accountGeneration);
                return;
            }
            saveCookiesLocked(newCookies, responseHost);
        }
    }

    private static void saveCookiesLocked(List<String> newCookies, String responseHost) {
        // 26.11.06.1：SharedPreferences 还没就绪时**绝不能落盘**。此时 getCachedCookies() 读到的是
        // 空串（Application.onCreate 尚未给它赋值），拿空串当底合并出来的 Cookie 会丢掉
        // SESSDATA / DedeUserID —— 等于把登录态写没了，下一次冷启动就成了「掉登录」。
        // 宁可丢掉这一次响应下发的 Set-Cookie（下次请求服务端会重发）。
        if (SharedPreferencesUtil.sharedPreferences == null) {
            Logu.d("newCookie", "SharedPreferences 未就绪，丢弃本次 Set-Cookie（避免写丢登录态）");
            return;
        }
        String cookiesStr = getCachedCookies();
        ArrayList<String> acceptedHeaders = new ArrayList<>(newCookies.size());

        for (String newCookie : newCookies) {  //对每一条新cookie遍历

            // 审计 P2-5：原来是 cookies.get("Domain").endsWith("bilibili.com")——不看点边界，
            // evilbilibili.com 也能过；而且没有 Domain 属性时干脆无条件收下。
            // 现在交给 CookieDomainPolicy：带 Domain 的必须落在 bilibili.com 内；不带 Domain 的
            // 按第一方 Cookie 处理，要求响应来源本身是 B 站域（domainAttributeOf 大小写不敏感）。
            String cookieDomain = CookieDomainPolicy.domainAttributeOf(newCookie);
            if (!CookieDomainPolicy.shouldAccept(responseHost, cookieDomain)) {
                Logu.d("newCookie", "跳过域不符的 Cookie：" + cookieDomain);
                continue;
            }

            acceptedHeaders.add(newCookie);
        }

        // 解析与合并交给纯策略 CookieJarPolicy（有 JVM 单测）：
        // · 属性按 ';' 切（不再只认 "; "），不会被 `Domain=` 之类属性污染值；
        // · SESSDATA / bili_jct / DedeUserID 等**登录凭据不接受删除或清空**——Cookie 刷新接口
        //   成功后会下发新 SESSDATA，确认接口才让旧 Cookie 失效；若这里把凭据按空值覆盖，
        //   本地就变成「没有 SESSDATA」，下次启动直接判登录过期（掉登录）；
        // · 空值写入一律忽略，非凭据键的 Max-Age=0 / Expires 过期正常删除。
        CookieJarPolicy.MergeResult merged =
                CookieJarPolicy.merge(cookiesStr, CookieJarPolicy.parseAll(acceptedHeaders));
        if (!merged.getProtectedKeys().isEmpty()) {
            // 只记键名，不记值（这些就是凭据本身，审计 S2）
            Logu.d("newCookie", "拒绝删除/清空登录凭据：" + merged.getProtectedKeys());
        }
        String result = merged.getCookies();
        if (!result.equals(cookiesStr)) {
            SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, result);
            cachedCookies = result;
            refreshHeaders();
            // 这里也是 bili_jct 轮换的唯一落点：顺手同步 csrf 快照，否则所有读快照的 POST
            // 都会静默拿 -111（详见 currentCsrf() 的说明）。只在值真的变了时才写，避免每次
            // 响应都多一次 SharedPreferences 写入。
            String liveJct = getInfoFromCookie("bili_jct", result);
            if (!liveJct.isEmpty()
                    && !liveJct.equals(SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, ""))) {
                SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf, liveJct);
            }
        }
    }

    /**
     * 存储单个Cookie
     *
     * @param key 键
     * @param val 值
     */
    public static void putCookie(String key, String val) {
        synchronized (NetWorkUtil.class) {
            // 与 saveCookiesLocked 同因：SharedPreferences 未就绪时读到的 Cookie 是空串，
            // 拿它当底写回会丢掉 SESSDATA 等登录凭据（26.11.06.1 掉登录返工）
            if (SharedPreferencesUtil.sharedPreferences == null) {
                Logu.d("putCookie", "SharedPreferences 未就绪，跳过写入 " + key);
                return;
            }
            Cookies cookies = new Cookies(getCachedCookies());
            cookies.set(key, val);
            String result = cookies.toString();
            SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, result);
            cachedCookies = result;
            refreshHeaders();
        }
    }

    /**
     * 获取存储的Cookies
     *
     * @return 存储的Cookies
     */
    public static Cookies getCookies() {
        synchronized (NetWorkUtil.class) {
            return new Cookies(getCachedCookies());
        }
    }

    public static final String USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36";

    /**
     * 全局请求头快照。volatile + 每次整体替换（copy-on-write）：
     * 原实现是 `static final ArrayList` 被 refreshHeaders() 原地 set(1, ...)，
     * 而所有 API 都在多线程里按索引遍历它——读线程可能看到"改了一半"的表，
     * 非 volatile 还会让它长期读到旧值（可见性问题）。改成 volatile 引用后，
     * 读线程要么拿到完整旧快照、要么拿到完整新快照，且不需要加锁。
     * 语义不变：全项目没有任何调用点原地修改该列表（唯一原地 set 就在 refreshHeaders）。
     */
    public static volatile ArrayList<String> webHeaders = buildWebHeaders();

    /** 构造一份全新的请求头列表；每次调用返回独立对象，避免共享可变状态。 */
    private static ArrayList<String> buildWebHeaders() {
        ArrayList<String> headers = new ArrayList<>();
        headers.add("Cookie");
        headers.add(getCachedCookies());

        headers.add("Origin");
        headers.add("https://www.bilibili.com");

        headers.add("Referer");
        headers.add("https://www.bilibili.com/");

        headers.add("User-Agent");
        headers.add(USER_AGENT_WEB);

        headers.add("Sec-Ch-Ua");
        headers.add("\"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"122\"");

        headers.add("Sec-Ch-Ua-Platform");
        headers.add("\"Windows\"");

        headers.add("Sec-Ch-Ua-Mobile");
        headers.add("?0");
        return headers;
    }

    public static void refreshHeaders() {
        // 整体替换而非原地 set：见 webHeaders 字段注释（并发读线程不能看到半成品表）
        webHeaders = buildWebHeaders();
    }

    /**
     * 本地判断当前是否有可用网络：只查系统连接状态，**不发起任何请求**，毫秒级返回。
     *
     * <p>启动页用它决定「进用户自定义的启动页」还是「进缓存页」：断网时自定义启动页里的
     * 推荐 / 动态 / 消息全在等网络，进去只看得到加载失败；缓存页读本地数据库，断网也完整可用。
     *
     * <p>**判断不出来就按「有网」处理**（fail-open）：没有网络状态权限、ROM 裁掉系统服务、
     * 查询抛异常时都返回 true，让启动退回原来的失败兜底路径，而不是把用户误锁进缓存页。
     *
     * <p>故意用已废弃的 [ConnectivityManager#getActiveNetworkInfo]（API 1 起可用）而不是
     * API 23 的 {@code NetworkCapabilities}：本项目 minSdk 19，老设备上后者更容易出兼容问题。
     */
    @SuppressWarnings("deprecation")
    public static boolean isNetworkAvailable(Context context) {
        try {
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) return true;
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnected();
        } catch (Throwable t) {
            return true;
        }
    }

    public static class FormData {
        private final Map<String, String> data;
        private boolean isUrlParam;
        // 默认不自动加 access_key（B 站 web 接口用 Cookie 鉴权，带 access_key 会触发风控返回 HTML）
        // 参考 BiliTerminal-Revival：FormData 不自动加 access_key
        private boolean autoAddAccessKey = false;

        public FormData() {
            data = new HashMap<>();
        }

        public FormData remove(String key) {
            data.remove(key);
            return this;
        }

        public FormData put(String key, Object value) {
            data.put(key, String.valueOf(value));
            return this;
        }

        public FormData setUrlParam(boolean isUrlParam) {
            this.isUrlParam = isUrlParam;
            return this;
        }

        public FormData setAutoAddAccessKey(boolean autoAddAccessKey) {
            this.autoAddAccessKey = autoAddAccessKey;
            return this;
        }

        @NonNull
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            if (isUrlParam) sb.append("?");

            try {
                if (autoAddAccessKey && !data.containsKey("access_key")) {
                    String accessKey = SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, "");
                    if (!accessKey.isEmpty()) {
                        if (sb.length() > (isUrlParam ? 1 : 0)) {
                            sb.append("&");
                        }
                        sb.append("access_key=");
                        sb.append(URLEncoder.encode(accessKey, "UTF-8"));
                    }
                }

                for (String key : data.keySet()) {
                    if (sb.length() > (isUrlParam ? 1 : 0)) {
                        sb.append("&");
                    }
                    sb.append(URLEncoder.encode(key, "UTF-8"));
                    sb.append("=");
                    sb.append(URLEncoder.encode(data.get(key), "UTF-8"));
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }

            return sb.toString();
        }
    }

    /**
     * 解压 CDN 返回的裸 deflate 数据（raw deflate，无 zlib 头）。
     *
     * 合并自原先三份逐字相同的副本（DownloadService / DownloadActivity / PlayerActivity），
     * 权威实现放在这里，调用方不要再各写一份。
     * 解压失败时回退返回原始数据；Inflater 持有 native 资源，必须在 finally 里 end()，
     * 否则一次异常就会泄漏一份 native 内存。
     */
    public static byte[] decompress(byte[] data) {
        byte[] output;
        Inflater decompresser = new Inflater(true);
        decompresser.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        try {
            byte[] buf = new byte[2048];
            while (!decompresser.finished()) {
                // 数据被截断/损坏时，inflate 会恒返回 0、finished() 恒为 false 且不抛异常
                // （外层 catch 兜不住），原写法就是 100% CPU 的死循环（审计 P15）。
                // needsInput/needsDictionary 表示流已无法继续推进，直接跳出（此时返回的是已解出的部分数据）。
                if (decompresser.needsInput() || decompresser.needsDictionary()) break;
                int i = decompresser.inflate(buf);
                if (i == 0) break;
                out.write(buf, 0, i);
            }
            output = out.toByteArray();
        } catch (Exception e) {
            output = data;
            e.printStackTrace();
        } finally {
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            decompresser.end();
        }
        return output;
    }

    public interface RedirectHandler {
        void handleRedirect(String location);
    }

    private static class CookieSaveInterceptor implements Interceptor {
        @NonNull
        @Override
        public Response intercept(Chain chain) throws IOException {
            Response response = chain.proceed(chain.request());
            saveCookiesFromResponse(response);
            return response;
        }
    }

    /**
     * HTTP 请求/响应追踪（26.10.14）。
     *
     * <p>给 AI agent / 自动化测试用：一次请求对应日志里的一行，包含方法、脱敏后的 URL、状态码、
     * 耗时和响应体摘要，同时进 logcat（tag 按 `Logu` 的既有约定是调用者，即
     * `adb logcat -s "HttpTraceInterceptor>intercept"`）与 `logs/http.log`。
     *
     * <p>三条硬约束：
     * <ul>
     *   <li>开关关闭时**只做一次 boolean 读**就透传，绝不给正常请求加任何开销；</li>
     *   <li>只预览文本类响应体（json/text/xml/html），视频、图片等二进制响应直接跳过——
     *       否则每次播放视频都会白读一遍磁盘，还会拖慢流式下载；</li>
     *   <li>自身任何异常都必须吞掉，追踪功能不能把一次本来正常的请求搞挂。</li>
     * </ul>
     */
    private static class HttpTraceInterceptor implements Interceptor {
        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            if (!HTTP_TRACE) return chain.proceed(chain.request());

            Request request = chain.request();
            long start = System.currentTimeMillis();
            Response response = chain.proceed(request);
            long cost = System.currentTimeMillis() - start;

            try {
                StringBuilder sb = new StringBuilder();
                sb.append(request.method())
                        .append(' ')
                        .append(maskUrl(request.url().toString()))
                        .append(" -> ")
                        .append(response.code())
                        .append(" (").append(cost).append("ms)");

                String requestBody = previewRequestBody(request);
                if (requestBody != null) sb.append(" | req=").append(requestBody);

                String responseBody = previewResponseBody(response);
                if (responseBody != null) sb.append(" | res=").append(responseBody);

                String line = sb.toString();
                // logcat 这一路不过 [Logu.toFile]，不会自动脱敏：req=/res= 摘要里可能带 csrf（bili_jct）、
                // refresh_token 等凭据，所以这里显式脱敏后再进 logcat；落盘那一路由 appendToFile 内部脱敏。
                Logu.d("http", LogSanitizer.sanitize(line));
                Logu.appendToFile(Logu.HTTP_LOG_NAME, "H", line);
            } catch (Exception ignored) {
                // 追踪失败不影响请求本身
            }
            return response;
        }
    }

    /**
     * URL 脱敏 + 去噪 + 限长。
     *
     * <p>只动查询串，路径原样保留（agent 需要靠路径判断调的是哪个接口）：
     * <ul>
     *   <li>[SENSITIVE_QUERY_KEYS] 的值 → `***`（凭证，绝不能落盘）；</li>
     *   <li>[NOISE_QUERY_KEYS] 的值 → `…`（WBI 指纹 blob，几百到上千字符且无排障价值）；</li>
     *   <li>整串超过 [MAX_TRACE_URL_CHARS] 时截断，防止未知的超长参数把日志撑爆。</li>
     * </ul>
     */
    static String maskUrl(String url) {
        if (url == null) return "";
        int q = url.indexOf('?');
        if (q < 0) return truncateUrl(url);
        String head = url.substring(0, q + 1);
        String[] params = url.substring(q + 1).split("&");
        StringBuilder sb = new StringBuilder(head);
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append('&');
            String param = params[i];
            int eq = param.indexOf('=');
            if (eq < 0) {
                sb.append(param);
                continue;
            }
            String key = param.substring(0, eq);
            String replacement = null;
            for (String s : SENSITIVE_QUERY_KEYS) {
                if (s.equalsIgnoreCase(key)) {
                    replacement = "***";
                    break;
                }
            }
            if (replacement == null) {
                for (String s : NOISE_QUERY_KEYS) {
                    if (s.equalsIgnoreCase(key)) {
                        replacement = "…";
                        break;
                    }
                }
            }
            sb.append(key).append('=').append(replacement != null ? replacement : param.substring(eq + 1));
        }
        return truncateUrl(sb.toString());
    }

    private static String truncateUrl(String url) {
        if (url.length() <= MAX_TRACE_URL_CHARS) return url;
        return url.substring(0, MAX_TRACE_URL_CHARS) + "…(url truncated)";
    }

    /** 预览请求体；multipart/文件体与超大请求体一律跳过（读了也没用，还可能很贵）。 */
    private static String previewRequestBody(Request request) {
        RequestBody body = request.body();
        if (body == null || body.isDuplex() || body.isOneShot()) return null;
        try {
            long length = body.contentLength();
            if (length <= 0 || length > HTTP_TRACE_MAX_BODY) return null;
            MediaType type = body.contentType();
            if (type != null && "multipart".equalsIgnoreCase(type.type())) return null;
            okio.Buffer buffer = new okio.Buffer();
            body.writeTo(buffer);
            return compact(buffer.readUtf8(), MAX_TRACE_BODY_CHARS);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 预览响应体。
     *
     * <p>用 `peekBody` 而不是读 `body.string()`：前者会克隆一份缓冲，**不会消费原始流**，
     * 调用方照常能读到完整响应；后者会把流读干，调用方拿到空 body。
     */
    private static String previewResponseBody(Response response) {
        ResponseBody body = response.body();
        if (body == null) return null;
        MediaType type = body.contentType();
        String subtype = type == null ? "" : type.subtype().toLowerCase(Locale.ROOT);
        boolean textLike = subtype.contains("json")
                || subtype.startsWith("text")
                || subtype.contains("xml")
                || subtype.contains("javascript")
                || subtype.contains("html");
        if (!textLike) return null;
        try {
            long declared = body.contentLength();
            long len = (declared > 0 && declared < HTTP_TRACE_MAX_BODY) ? declared : HTTP_TRACE_MAX_BODY;
            String text = response.peekBody(len).string();
            return compact(text, MAX_TRACE_BODY_CHARS);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 压成单行：agent 按行 grep 日志时，一条请求必须正好占一行。
     *
     * @param limit 结果长度上限；超限截断并标注
     */
    private static String compact(String text, int limit) {
        if (text == null) return "";
        String s = text.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        StringBuilder sb = new StringBuilder(s.length());
        boolean lastSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                if (lastSpace) continue;
                lastSpace = true;
            } else {
                lastSpace = false;
            }
            sb.append(c);
        }
        String result = sb.toString().trim();
        if (result.length() > limit) result = result.substring(0, limit) + "…(truncated)";
        return result;
    }

}
