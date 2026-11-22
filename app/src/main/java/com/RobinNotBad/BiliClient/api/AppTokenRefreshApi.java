package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.util.AccountManager;
import com.RobinNotBad.BiliClient.util.Cookies;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
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
 * 与 web 登录不同，TV 登录（passport-tv-login）会额外返回短效的 access_token（约 30 天）与长效的
 * refresh_token（约 180 天，一次性轮换）。调用本刷新接口后，服务器会同时返回：
 * <ul>
 *   <li>token_info：新的 access_token 与新的 refresh_token（refresh_token 会轮换，必须立刻落盘）；</li>
 *   <li>cookie_info：新的 web cookies（SESSDATA / bili_jct 等）。</li>
 * </ul>
 * 也就是说，同一个 refresh_token 既能续 access_token 也能续 cookie。
 *
 * 参考（云视听小电视实现）：POST https://passport.bilibili.com/api/v2/oauth2/refresh_token
 */
public class AppTokenRefreshApi {

    private static final String REFRESH_URL = "https://passport.bilibili.com/api/v2/oauth2/refresh_token";

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
        NEED_LOGIN
    }

    /**
     * 用 TV 登录返回的 refresh_token 刷新 access_token 与 cookies。
     *
     * 注意：refresh_token 为一次性轮换，且 App 可能被系统回收。因此刷新响应一到就必须立刻把新的
     * access_token / refresh_token / cookies 全部落盘，再继续后续逻辑。
     *
     * @return 见 {@link RefreshResult}；网络 / 解析异常直接抛出，由调用方按「保留登录态」处理
     */
    public static RefreshResult refreshAppToken() throws IOException, JSONException {
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

        // 响应必须关：这条链路每次冷启动 + CookieRefreshScheduler 每 30 分钟一次，下面每条 return
        // 都不该把一个没关的 Response 留在原地（兄弟路径 CookieRefreshApi 也是 try-with-resources）
        JSONObject result;
        try (Response response = NetWorkUtil.post(REFRESH_URL, body, NetWorkUtil.webHeaders)) {
            if (response.body() == null) {
                Logu.e("APP token 刷新失败", "刷新接口返回空响应");
                return RefreshResult.RETRY;
            }
            result = new JSONObject(response.body().string());
        }
        int code = result.optInt("code", -1);
        if (code == -101) {
            // 与 Web 那条链路同一口径：只有 -101 是服务端明确说口令/会话失效
            Logu.e("APP token 刷新失败", "刷新接口返回 -101（refresh_token 已失效），需要重新登录");
            return RefreshResult.NEED_LOGIN;
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

        Logu.v("APP token 刷新成功");
        return RefreshResult.OK;
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
