package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.util.AccountManager;
import com.RobinNotBad.BiliClient.util.AppTokenRefreshPolicy;
import com.RobinNotBad.BiliClient.util.Cookies;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.RefreshGuard;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.TreeMap;

import okhttp3.Response;

/**
 * TV/APP 端 access_token（OAuth2）刷新逻辑。
 *
 * 与 web 登录不同，TV 登录（passport-tv-login）会额外返回 access_token 与 refresh_token。调用本刷新
 * 接口后，服务器会同时返回：
 * <ul>
 *   <li>token_info：新的 access_token 与（通常会换新的）refresh_token，以及有效期字段（秒）；</li>
 *   <li>cookie_info：新的 web cookies（SESSDATA / bili_jct 等）。</li>
 * </ul>
 * 也就是说，同一个 refresh_token 既能续 access_token 也能续 cookie。拿到的一组凭据要**立刻整份落盘**：
 * 慢一步的那些窗口正是历史上「用着用着掉登录」的来源。
 *
 * <p>刷新失败分档（见 {@link RefreshResult}）：临时失败只能退避重试、绝不能清本地凭证；服务端明确说
 * 凭据失效才走重新登录；服务端说「这份凭据不是本端能续期的」则停下例行重试，免得每 30 分钟白试一次。
 *
 * 参考（云视听小电视实现）：POST https://passport.bilibili.com/api/v2/oauth2/refresh_token
 */
public class AppTokenRefreshApi {

    private static final String REFRESH_URL = "https://passport.bilibili.com/api/v2/oauth2/refresh_token";

    /** 服务端表示「这份凭据已失效 / 会话没了」：需要用户重新登录。 */
    private static final int CODE_TOKEN_INVALID = -101;

    /**
     * 服务端表示「这份凭据不是本客户端可以续期的」（例如它由别的端签发）。
     *
     * <p>与 {@link #CODE_TOKEN_INVALID} 的区别很重要：这类失败换参数重试、等一会再试都不会有不同结果，
     * 该停下来提示用户重新登录，而不是按退避无限重试。
     */
    private static final int CODE_ISSUER_MISMATCH = 86033;

    /**
     * 一次 APP token 刷新的结果（26.11.16 由 boolean 改成分档）。
     *
     * <p>与 {@link CookieRefreshApi.RefreshResult} 同一口径：调用方必须区分「这次没成功」与「服务端
     * 说口令已经没了」。前者只能退避重试、**绝不能清本地凭证**——「刷新失败就等于掉登录」是历史上
     * 最招骂的一类问题；后者才该走登录失效流程。原来返回 boolean 时这两种情况都是 false，调用方
     * 只能记一行日志，失效的 refresh_token 于是被永久保留，用户每 30 分钟白刷一次、只能自己猜。
     */
    public enum RefreshResult {
        /** 刷新并落盘成功 */
        OK,

        /** 这次没成功，但本地登录态没被破坏：保留全部凭证，退避后再试 */
        RETRY,

        /** 服务端明确说 refresh_token 已失效（-101）：需要用户重新登录 */
        NEED_LOGIN,

        /**
         * 服务端拒绝续期，且原样重试也不会有不同结果（例如这份凭据不是本客户端签发的）。
         *
         * <p>同一套凭据再试多少次都不会成功：调用方应当提示用户重新登录，并记住这个事实、不再按例行
         * 计划反复请求。旧实现把所有非 0 都当 {@link #RETRY}，于是每 30 分钟白刷一次，日志里反复出现
         * 同一条失败，用户却一直等不到「为什么」。
         */
        MISMATCH
    }

    /**
     * 用 TV 登录返回的 refresh_token 刷新 access_token 与 cookies（例行刷新，受间隔约束）。
     *
     * <p>等价于 {@code refreshAppToken(false)}：不足 {@link AppTokenRefreshPolicy#MIN_INTERVAL_MS}
     * 时直接当成功返回，不做网络请求。
     *
     * @return 见 {@link RefreshResult}；网络 / 解析异常直接抛出，由调用方按「保留登录态」处理
     */
    public static RefreshResult refreshAppToken() throws IOException, JSONException {
        return refreshAppToken(false);
    }

    /**
     * 用 TV 登录返回的 refresh_token 刷新 access_token 与 cookies。
     *
     * <h3>为什么要 force 与间隔约束（26.11.22「老是掉登录」返工）</h3>
     * 每刷一次，服务端就下发一整套新凭据。旧实现在「启动页每次冷启动」和「30 分钟定时器」两处无条件
     * 调用，等于一天轮换几十次——每一次都是一个「本地没写成功 / 进程被杀」就丢凭据的窗口，而兄弟分支
     * 半年才刷 1～2 次。现在例行路径按 {@link AppTokenRefreshPolicy} 的到期时间决定什么时候续
     * （拿不到到期时间时退回固定下限），只有请求层报「凭据失效」之类的实证才 force 强刷一次。
     *
     * <p>整个刷新在 {@link RefreshGuard#LOCK} 里排队：启动页与定时调度同时调用时，后到的那个
     * 会用上一轮刚被换掉的旧凭据去请求，结果必然是失败——而失败在过去就是「登出」的触发器。
     *
     * @param force true 时忽略间隔约束，立刻请求（只在「疑似口令失效」时用）
     * @return 见 {@link RefreshResult}；网络 / 解析异常直接抛出，由调用方按「保留登录态」处理
     */
    public static RefreshResult refreshAppToken(boolean force) throws IOException, JSONException {
        synchronized (RefreshGuard.LOCK) {
            long nowMs = System.currentTimeMillis();
            long lastRefresh = SharedPreferencesUtil.getLong(SharedPreferencesUtil.app_token_last_refresh, AppTokenRefreshPolicy.NO_REFRESH);
            long expiresAt = SharedPreferencesUtil.getLong(SharedPreferencesUtil.app_token_expires_at, AppTokenRefreshPolicy.NO_REFRESH);
            if (!force && !AppTokenRefreshPolicy.isDue(nowMs, lastRefresh, expiresAt)) {
                // 不是失败：离到期还早，什么都不用做
                Logu.v("APP token 刷新跳过", "还没到续期时间");
                return RefreshResult.OK;
            }
            return refreshAppTokenLocked(force, nowMs);
        }
    }

    /** {@link #refreshAppToken(boolean)} 的主体；调用方必须持有 {@link RefreshGuard#LOCK}。 */
    private static RefreshResult refreshAppTokenLocked(boolean force, long nowMs) throws IOException, JSONException {
        String refreshToken = SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "");
        if (refreshToken.isEmpty()) {
            Logu.d("APP token 刷新跳过", "缺少 refresh_token");
            return RefreshResult.RETRY;
        }

        // 带上 access_key / mid / refresh_token，由 LoginApi.tvSign 补 appkey、ts、sign
        TreeMap<String, String> params = new TreeMap<>();
        String accessKey = SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, "");
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L);
        if (!accessKey.isEmpty()) params.put("access_key", accessKey);
        if (mid != 0L) params.put("mid", String.valueOf(mid));
        params.put("refresh_token", refreshToken);
        String body = LoginApi.tvSign(params);

        JSONObject result;
        try {
            result = postRefresh(body);
        } catch (IOException | JSONException e) {
            // 同一组参数原样再提交一次不会产生副作用（服务端只会再发一组新凭据），而失败退避到下一轮
            // 可能是几小时之后：网络抖动 / 响应解析失败时就地重试一次，比直接放弃划算得多
            Logu.w("APP token 刷新", "首次请求失败，原样重试一次：" + e.getMessage());
            result = postRefresh(body);
        }
        if (result == null) {
            Logu.e("APP token 刷新失败", "刷新接口返回空响应");
            return RefreshResult.RETRY;
        }
        int code = result.optInt("code", -1);
        if (code == CODE_TOKEN_INVALID) {
            // 与 Web 那条链路同一口径：只有服务端明确说口令/会话失效，才需要用户重新登录
            Logu.e("APP token 刷新失败", "刷新接口返回 -101（refresh_token 已失效），需要重新登录");
            return RefreshResult.NEED_LOGIN;
        }
        if (code == CODE_ISSUER_MISMATCH) {
            // 服务端说「这份凭据不是本端能续期的」：换参数重试、等一会再试都不会有不同结果
            Logu.e("APP token 刷新失败", "服务端拒绝续期（code=" + code + "），需要用户重新登录");
            return RefreshResult.MISMATCH;
        }
        if (code != 0) {
            Logu.e("APP token 刷新失败", "code=" + code + " message=" + result.optString("message"));
            return RefreshResult.RETRY;
        }

        JSONObject data = result.getJSONObject("data");

        // 优先从 token_info 读取，个别版本直接平铺在 data 上，做兼容处理
        JSONObject tokenInfo = data.optJSONObject("token_info");
        JSONObject src = tokenInfo != null ? tokenInfo : data;
        String newAccess = src.optString("access_token", "");
        String newRefresh = src.optString("refresh_token", "");
        if (newAccess.isEmpty() || newRefresh.isEmpty()) {
            Logu.e("APP token 刷新失败", "响应缺少新的 access_token/refresh_token");
            return RefreshResult.RETRY;
        }

        // 一次性落盘新的 token：这两行必须先做——refresh_token 服务端发过就已经轮换，
        // 后面任何一步失败都不能让它丢掉，否则下次刷新必然失败
        SharedPreferencesUtil.putString(SharedPreferencesUtil.access_key, newAccess);
        SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, newRefresh);
        long newMid = src.has("mid") ? src.optLong("mid", mid) : mid;
        if (newMid > 0L) SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, newMid);

        // 同步刷新返回的 cookies（SESSDATA / bili_jct 等），拿到新串就整份原子落库
        String cookiesNew = applyCookieInfo(data);
        if (cookiesNew != null && newMid > 0L) {
            // 与 Web 刷新走同一条落库路径：原子写 + 升账号代际（在飞的旧响应作废）+ 同步账号快照。
            // 被拒（Cookie 里的 DedeUserID 与 mid 对不上）也不影响上面已经写好的 token，
            // 只是退回「逐字段写入」的效果——所以这里不把它当失败。
            if (!AccountManager.applyCredentials(newMid, "", cookiesNew, newRefresh, newAccess, "")) {
                Logu.w("APP token 刷新", "新凭据没能整份落库（mid=" + newMid + "），已按字段写入");
            }
        }

        // 记下这组凭据的到期时刻（服务端给的是「还能用多少秒」），下次据此决定什么时候续期。
        // 响应没带有效期字段就写 0，让策略退回固定间隔兜底——宁可用保守口径，也不要拿错的时间把刷新冻住
        long expiresInSec = src.optLong("expires_in", 0L);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.app_token_expires_at,
                expiresInSec > 0L ? nowMs + expiresInSec * 1000L : AppTokenRefreshPolicy.NO_REFRESH);

        // 只有真的拿到新口令才记「这次刷过了」：失败的、被拒的都不算，下一次进来还会重试
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.app_token_last_refresh, nowMs);
        Logu.v("APP token 刷新成功" + (force ? "（强制）" : ""));
        return RefreshResult.OK;
    }

    /**
     * 发一次刷新请求并解析成 JSON。
     *
     * <p>响应必须关：这条链路每次冷启动 + CookieRefreshScheduler 每 30 分钟一次，任何一个出口都不该
     * 把一个没关的 Response 留在原地（兄弟路径 CookieRefreshApi 也是 try-with-resources）。
     *
     * @return 解析好的响应体；body 为空时返回 null
     */
    private static JSONObject postRefresh(String body) throws IOException, JSONException {
        try (Response response = NetWorkUtil.post(REFRESH_URL, body, NetWorkUtil.webHeaders)) {
            if (response.body() == null) return null;
            return new JSONObject(response.body().string());
        }
    }

    /**
     * 把响应里的 cookie_info 并进本地 Cookie 并落盘。
     *
     * <p>APP 端登录类接口（如短信登录的 `x/passport-login/login/sms`）与 TV 登录一样
     * **把 Cookie 放在响应体的 `cookie_info.cookies` 里，不发 `Set-Cookie`**——所以 OkHttp 的
     * CookieJar 收不到它们，必须显式解析落盘，否则「登录成功」之后读到的还是上一套（或空）Cookie。
     *
     * @return 合并后的 Cookie 串；响应里没带 cookie_info / cookies 时返回 null
     */
    public static String applyCookieInfo(JSONObject data) {
        try {
            JSONObject cookieInfo = data.optJSONObject("cookie_info");
            if (cookieInfo == null) return null;
            JSONArray cookiesArray = cookieInfo.optJSONArray("cookies");
            if (cookiesArray == null || cookiesArray.length() == 0) return null;

            Cookies cookies = new Cookies(SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
            for (int i = 0; i < cookiesArray.length(); i++) {
                JSONObject cookie = cookiesArray.getJSONObject(i);
                String name = cookie.optString("name", "");
                String value = cookie.optString("value", "");
                if (!name.isEmpty()) cookies.set(name, value);
            }
            String cookieStr = cookies.toString();
            if (cookieStr.isEmpty()) return null;
            NetWorkUtil.setCookiesString(cookieStr);
            SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, cookieStr);
            SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf,
                    cookies.getOrDefault("bili_jct", ""));
            return cookieStr;
        } catch (JSONException e) {
            Logu.e("APP token 刷新失败", "解析 cookie_info 失败: " + e.getMessage());
            return null;
        }
    }
}
