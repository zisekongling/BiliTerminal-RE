package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Objects;

/**
 * 直播弹幕发送（反馈 #156，本轮只做**文本弹幕**，不做表情 / 礼物 / 进场特效）。
 *
 * <p>接口依据：`bilibili-API/docs/live/danmaku.md` 的「发送直播弹幕」（POST
 * {@code https://api.live.bilibili.com/msg/send}）。文档列出的**必要**参数是
 * {@code csrf}、{@code roomid}、{@code msg}、{@code rnd}（当前 Unix 秒）、
 * {@code fontsize}、{@code color}；{@code mode} 等为可选。
 *
 * <p>参数与签名口径**对齐 PiliPlus 的实际请求**（`PiliPlus/lib/http/live.dart:36-78`
 * 的 {@code LiveHttp.sendLiveMsg}）：那条请求在 query 上带 {@code web_location=444.8}
 * 并走 wbi 签名（{@code WbiSign.makSign}），body 除必要参数外还带
 * {@code statistics={"appId":100,"platform":5}}、{@code room_type} / {@code jumpfrom} /
 * {@code reply_*} 这些「不带也不报错、但不带就像个裸爬虫」的字段。文档说 {@code w_rid}
 * 非必要，可它走的是 web 端口径，与 web 端一致的请求更容易过风控，所以这里也签名。
 * 签名取密钥要走一次网络，失败时**退回未签名 URL** 继续发——不能因为拿不到密钥就
 * 让用户完全发不出去。
 *
 * <p>返回码语义（同样来自该文档）：{@code 0} 成功、{@code -101} 账号未登录、
 * {@code -111} csrf 校验失败、{@code -400} 请求错误、{@code 1003212} 超出长度、
 * {@code 10031} 发送频率过快。这里把它们映射成 {@link Kind}，让调用方能分别给出
 * 「未登录 / 超限 / 风控 / 其它」四种提示，而不是把裸 code 甩给用户。
 *
 * <p>本类**不抛裸异常**：网络失败、返回体不是 JSON（风控页常是 HTML）都会归一成
 * {@link Kind#ERROR} 的 {@link Result}，调用方永远只处理返回值。
 *
 * <p>与视频弹幕的分工：视频弹幕仍在 {@link DanmakuApi}，这里只负责直播，两者参数与
 * 返回码都不一样，混在一个类里会难以阅读。
 */
public class LiveDanmakuApi {

    /** 发送接口（视频弹幕的是 {@code api.bilibili.com/x/v2/dm/post}，别混用）。 */
    public static final String SEND_URL = "https://api.live.bilibili.com/msg/send";

    /**
     * 直播弹幕字数上限。B 站直播间对普通用户是 20 字，但这个值**没有**自查接口
     * （`xlive/web-room/v1/dM/GetDMConfigByGroup` 只给颜色组与模式），所以本地按 20 字
     * 先挡一道，服务端返回 {@code 1003212} 时再用 {@link Kind#TOO_LONG} 兜底。
     */
    public static final int MAX_TEXT_LENGTH = 20;

    /** 本地最小发送间隔：3 秒。缓解连点刷屏，也降低撞上 {@code 10031} 的概率。 */
    public static final long MIN_INTERVAL_MS = 3000L;

    /** 文档给的默认值：fontsize 25、color 白色（文档注明 color 实际无效果，但它是必要参数）。 */
    public static final int DEFAULT_FONT_SIZE = 25;
    public static final int DEFAULT_COLOR = 0xFFFFFF;
    public static final int DEFAULT_MODE = 1;

    /**
     * wbi 签名与 body 里的 {@code web_location} 取值，跟 PiliPlus 一致（444.8）。
     * 这个数字是「web 端直播间发弹幕」的埋点位置码，风控会看它是否与 Referer 对得上。
     */
    public static final String WEB_LOCATION = "444.8";

    /** 客户端标识，与 PiliPlus 一致（appId 100 = web 端，platform 5 = web）。 */
    public static final String DEFAULT_STATISTICS = "{\"appId\":100,\"platform\":5}";

    /**
     * 直播页的 Referer。{@code NetWorkUtil.webHeaders} 里写的是 {@code www.bilibili.com}，
     * 而直播间接口的风控口径是 {@code live.bilibili.com}，所以只在直播发送这一处替换，
     * 不去动全局 header（全局那份被首页 / 动态等接口共用）。
     */
    public static final String LIVE_REFERER = "https://live.bilibili.com/";

    private static final String KEY_REFERER = "Referer";

    private static final Throttle THROTTLE = new Throttle(MIN_INTERVAL_MS);

    private LiveDanmakuApi() {
    }

    /** 发送结果类别。{@link #SUCCESS} 之外的每一种都对应一句明确的用户提示。 */
    public enum Kind {
        /** code 0：发送成功。 */
        SUCCESS,
        /** 本地节流：距上次发送不足 {@link #MIN_INTERVAL_MS}，**没有发请求**。 */
        LOCAL_THROTTLED,
        /** 本地校验：内容为空 / 全空白，**没有发请求**。 */
        EMPTY_TEXT,
        /** 本地校验或服务端 1003212：超过 {@link #MAX_TEXT_LENGTH} 字。 */
        TOO_LONG,
        /** csrf 为空（没登录）或服务端 -101。 */
        NOT_LOGGED_IN,
        /** 服务端 -111：Cookie 里的 bili_jct 与请求里的 csrf 不一致。 */
        CSRF_INVALID,
        /** 服务端 10031：发送频率过快。 */
        RATE_LIMITED,
        /** 服务端风控拦截（-352 / -412）。 */
        RISK_CONTROL,
        /** 其它一切失败：非 2xx、返回体不是 JSON、未知 code。 */
        ERROR
    }

    /** 结构化返回：类别 + 原始 code + 服务端原话（可能为空）。 */
    public static final class Result {
        public final Kind kind;
        public final int code;
        public final String message;

        public Result(Kind kind, int code, String message) {
            this.kind = kind;
            this.code = code;
            this.message = message == null ? "" : message;
        }

        public boolean isSuccess() {
            return kind == Kind.SUCCESS;
        }

        @Override
        public String toString() {
            return "Result{kind=" + kind + ", code=" + code + ", message=" + message + "}";
        }
    }

    /** 本地字数校验结果。 */
    public enum TextCheck {
        OK,
        EMPTY,
        TOO_LONG
    }

    // ------------------------------------------------------------------
    // 纯函数区：不碰网络、不碰 Android，可直接跑 JVM 单测
    // ------------------------------------------------------------------

    /**
     * 本地字数校验。按**码点**计（一个 emoji 算一个字），与直播间「20 字」的口径一致，
     * 而不是按 UTF-16 char 计——后者会让 20 个 emoji 被判成 40 字。
     */
    public static TextCheck checkText(String text) {
        if (text == null) return TextCheck.EMPTY;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return TextCheck.EMPTY;
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_TEXT_LENGTH) return TextCheck.TOO_LONG;
        return TextCheck.OK;
    }

    /** 服务端 code → 类别。文档未列出的风控码按 B 站通用错误码兜底。 */
    public static Kind mapCode(int code) {
        switch (code) {
            case 0:
                return Kind.SUCCESS;
            case -101:
                return Kind.NOT_LOGGED_IN;
            case -111:
                return Kind.CSRF_INVALID;
            case 10031:
                return Kind.RATE_LIMITED;
            case 1003212:
                return Kind.TOO_LONG;
            case -352: // 风控校验失败
            case -412: // 请求被拦截
                return Kind.RISK_CONTROL;
            default:
                return Kind.ERROR;
        }
    }

    /** 默认参数版的请求体。 */
    public static String buildBody(long roomId, String msg, String csrf, long rndSeconds) {
        return buildBody(roomId, msg, csrf, rndSeconds, DEFAULT_FONT_SIZE, DEFAULT_COLOR, DEFAULT_MODE);
    }

    /**
     * 拼 {@code application/x-www-form-urlencoded} 请求体。
     *
     * <p>刻意**不用** {@link NetWorkUtil.FormData}：那是个 HashMap，键顺序不确定，
     * 单测没法断言整串。这里固定顺序、逐项 URL 编码（中文弹幕必须编码成 UTF-8 百分号形式）。
     *
     * <p>键序与取值对齐 PiliPlus 的 {@code LiveHttp.sendLiveMsg}：除了文档列的必要参数，
     * 还带 {@code room_type} / {@code jumpfrom} / {@code reply_*}（本 App 没有「@某人发弹幕」
     * 的入口，所以固定填 0 与空串）与 {@code statistics}。少这些字段服务端一般也收，但
     * 请求看起来不像 web 端时更容易被风控回 {@code -352}，所以补齐。
     */
    public static String buildBody(long roomId, String msg, String csrf, long rndSeconds,
                                   int fontSize, int color, int mode) {
        String safeCsrf = csrf == null ? "" : csrf;
        StringBuilder sb = new StringBuilder();
        appendParam(sb, "bubble", "0");
        appendParam(sb, "msg", msg == null ? "" : msg);
        appendParam(sb, "color", String.valueOf(color));
        appendParam(sb, "mode", String.valueOf(mode));
        appendParam(sb, "room_type", "0");
        appendParam(sb, "jumpfrom", "0");
        // 回复类字段：本 App 只发普通弹幕，不 @ 人、不回别人的弹幕，故全填默认值
        appendParam(sb, "reply_mid", "0");
        appendParam(sb, "reply_attr", "0");
        appendParam(sb, "replay_dmid", "");
        appendParam(sb, "statistics", DEFAULT_STATISTICS);
        appendParam(sb, "reply_type", "0");
        appendParam(sb, "reply_uname", "");
        appendParam(sb, "fontsize", String.valueOf(fontSize));
        appendParam(sb, "rnd", String.valueOf(rndSeconds));
        appendParam(sb, "roomid", String.valueOf(roomId));
        appendParam(sb, "csrf", safeCsrf);
        // csrf_token 文档列为非必要、取值同 csrf；web 端会带上，这里跟齐以减少差异
        appendParam(sb, "csrf_token", safeCsrf);
        return sb.toString();
    }

    private static void appendParam(StringBuilder sb, String key, String value) {
        if (sb.length() > 0) sb.append('&');
        sb.append(urlEncode(key));
        sb.append('=');
        sb.append(urlEncode(value));
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 一定存在，这里只是 Java 的受检异常签名；退回原文比抛出去好
            return value;
        }
    }

    /**
     * 复制一份 header 表并只替换 {@code Referer} 的值（见 {@link #LIVE_REFERER} 的说明）。
     * 入参与返回值都是「键、值、键、值…」的扁平表，与 {@link NetWorkUtil#webHeaders} 一致。
     */
    public static ArrayList<String> liveHeaders(ArrayList<String> base) {
        if (base == null) return new ArrayList<>();
        ArrayList<String> copied = new ArrayList<>(base.size());
        for (int i = 0; i < base.size(); i += 2) {
            String key = base.get(i);
            String value = (i + 1 < base.size()) ? base.get(i + 1) : "";
            if (KEY_REFERER.equalsIgnoreCase(key)) value = LIVE_REFERER;
            copied.add(key);
            copied.add(value);
        }
        return copied;
    }

    // ------------------------------------------------------------------
    // 网络调用
    // ------------------------------------------------------------------

    /**
     * 发送用的 URL：{@code /msg/send?web_location=444.8&w_rid=…&wts=…}。
     *
     * <p>签名密钥要现取（{@code ConfInfoApi.signWBI} 内部按 TTL 缓存），取不到时
     * **退回未签名 URL** 而不是让发送失败：未签名最多是被风控拒一次（用户看到
     * {@code RISK_CONTROL}），比「根本发不出去」可恢复。
     */
    public static String signedSendUrl() {
        String base = SEND_URL + "?web_location=" + WEB_LOCATION;
        try {
            return ConfInfoApi.signWBI(base);
        } catch (JSONException | IOException | RuntimeException e) {
            Logu.e("直播弹幕", "wbi 签名失败，退回未签名请求：" + e.getMessage());
            return base;
        }
    }

    /**
     * 发送一条直播文本弹幕（**同步阻塞**，调用方负责包 {@code CenterThreadPool.run {}}）。
     *
     * <p>顺序：本地校验（空 / 超长）→ 未登录判断（csrf 为空）→ 本地节流 → 发请求。
     * 前两步与节流都不发网络请求，所以「连点」不会产生任何流量。
     *
     * @param roomId 直播间号（播放页里就是 Activity 的 aid）
     * @param text   弹幕正文
     */
    public static Result send(long roomId, String text) {
        TextCheck check = checkText(text);
        if (check == TextCheck.EMPTY) return new Result(Kind.EMPTY_TEXT, 0, "");
        if (check == TextCheck.TOO_LONG) return new Result(Kind.TOO_LONG, 0, "");

        String csrf = NetWorkUtil.currentCsrf();
        if (csrf == null || csrf.isEmpty()) return new Result(Kind.NOT_LOGGED_IN, 0, "");

        if (!THROTTLE.tryAcquire(System.currentTimeMillis())) {
            return new Result(Kind.LOCAL_THROTTLED, 0, "");
        }

        String msg = text.trim();
        String body = buildBody(roomId, msg, csrf, System.currentTimeMillis() / 1000L);
        String raw;
        try {
            raw = Objects.requireNonNull(
                    NetWorkUtil.post(signedSendUrl(), body, liveHeaders(NetWorkUtil.webHeaders)).body()).string();
        } catch (IOException | RuntimeException e) {
            // post() 对非 2xx 直接抛 IOException（风控页常见 412 / 403）
            Logu.e("直播弹幕", "发送请求失败：" + e.getMessage());
            return new Result(Kind.ERROR, -1, e.getMessage());
        }

        try {
            JSONObject json = new JSONObject(raw);
            int code = json.optInt("code", -1);
            // 文档里 message 与 msg 同义，实测两种都出现过
            String message = json.optString("message", json.optString("msg", ""));
            Kind kind = mapCode(code);
            Logu.i("直播弹幕", "发送结果：code=" + code + " kind=" + kind + " msg=" + message);
            return new Result(kind, code, message);
        } catch (JSONException e) {
            // 返回体不是 JSON：多半是风控 HTML 页，截一段留给日志排查
            String snippet = raw.length() > 120 ? raw.substring(0, 120) : raw;
            Logu.e("直播弹幕", "返回体不是 JSON：" + snippet);
            return new Result(Kind.ERROR, -1, snippet);
        }
    }

    /** 给界面层查询「还要等多久才能再发」，只用于提示，不参与判定。 */
    public static long remainingThrottleMs(long nowMs) {
        return THROTTLE.remainingMs(nowMs);
    }

    /** 供单测重置节流状态。生产代码不要调用。 */
    public static void resetThrottle() {
        THROTTLE.reset();
    }

    /**
     * 最小间隔节流器：只有「距上次放行不足 {@code minIntervalMs}」才拒绝。
     *
     * <p>系统时间被往回调（用户改时间 / NTP 校时）时 {@code nowMs < lastMs}，
     * 直接放行并重设基准，否则会一直卡到时钟追上来。
     */
    public static final class Throttle {
        private final long minIntervalMs;
        private long lastMs = Long.MIN_VALUE;

        public Throttle(long minIntervalMs) {
            this.minIntervalMs = minIntervalMs;
        }

        public synchronized boolean tryAcquire(long nowMs) {
            if (lastMs == Long.MIN_VALUE || nowMs < lastMs) {
                lastMs = nowMs;
                return true;
            }
            if (nowMs - lastMs < minIntervalMs) return false;
            lastMs = nowMs;
            return true;
        }

        public synchronized long remainingMs(long nowMs) {
            if (lastMs == Long.MIN_VALUE || nowMs < lastMs) return 0L;
            long elapsed = nowMs - lastMs;
            return elapsed >= minIntervalMs ? 0L : minIntervalMs - elapsed;
        }

        public synchronized void reset() {
            lastMs = Long.MIN_VALUE;
        }
    }
}
