package com.RobinNotBad.BiliClient.api;

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
     * 用 TV 登录返回的 refresh_token 刷新 access_token 与 cookies。
     *
     * 注意：refresh_token 为一次性轮换，且 App 可能被系统回收。因此刷新响应一到就必须立刻把新的
     * access_token / refresh_token / cookies 全部落盘，再继续后续逻辑。
     *
     * @return 是否刷新成功（无 refresh_token 时直接返回 false）
     */
    public static boolean refreshAppToken() throws IOException, JSONException {
        String refreshToken = SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "");
        if (refreshToken.isEmpty()) {
            Logu.d("APP token 刷新跳过", "缺少 refresh_token");
            return false;
        }

        // 带上 access_key / mid / refresh_token，由 LoginApi.tvSign 补 appkey、ts、sign
        TreeMap<String, String> params = new TreeMap<>();
        String accessKey = SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, "");
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L);
        if (!accessKey.isEmpty()) params.put("access_key", accessKey);
        if (mid != 0L) params.put("mid", String.valueOf(mid));
        params.put("refresh_token", refreshToken);
        String body = LoginApi.tvSign(params);

        Response response = NetWorkUtil.post(REFRESH_URL, body, NetWorkUtil.webHeaders);
        if (response.body() == null) return false;
        JSONObject result = new JSONObject(response.body().string());
        int code = result.getInt("code");
        if (code != 0) {
            Logu.e("APP token 刷新失败", "code=" + code + " message=" + result.optString("message"));
            return false;
        }

        JSONObject data = result.getJSONObject("data");

        // 优先从 token_info 读取，个别版本直接平铺在 data 上，做兼容处理
        JSONObject tokenInfo = data.optJSONObject("token_info");
        JSONObject src = tokenInfo != null ? tokenInfo : data;
        String newAccess = src.optString("access_token", "");
        String newRefresh = src.optString("refresh_token", "");
        if (newAccess.isEmpty() || newRefresh.isEmpty()) {
            Logu.e("APP token 刷新失败", "响应缺少新的 access_token/refresh_token");
            return false;
        }

        // 一次性落盘新的 token
        SharedPreferencesUtil.putString(SharedPreferencesUtil.access_key, newAccess);
        SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, newRefresh);
        if (src.has("mid")) {
            SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, src.getLong("mid"));
        }

        // 同步刷新返回的 cookies（SESSDATA / bili_jct 等）
        updateCookies(data);

        Logu.v("APP token 刷新成功");
        return true;
    }

    private static void updateCookies(JSONObject data) {
        try {
            JSONObject cookieInfo = data.optJSONObject("cookie_info");
            if (cookieInfo == null) return;
            JSONArray cookiesArray = cookieInfo.optJSONArray("cookies");
            if (cookiesArray == null || cookiesArray.length() == 0) return;

            Cookies cookies = new Cookies(SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
            for (int i = 0; i < cookiesArray.length(); i++) {
                JSONObject cookie = cookiesArray.getJSONObject(i);
                String name = cookie.optString("name", "");
                String value = cookie.optString("value", "");
                if (!name.isEmpty()) cookies.set(name, value);
            }
            String cookieStr = cookies.toString();
            if (!cookieStr.isEmpty()) {
                NetWorkUtil.setCookiesString(cookieStr);
                SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, cookieStr);
                SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf,
                        cookies.getOrDefault("bili_jct", ""));
            }
        } catch (JSONException e) {
            Logu.e("APP token 刷新失败", "解析 cookie_info 失败: " + e.getMessage());
        }
    }
}
