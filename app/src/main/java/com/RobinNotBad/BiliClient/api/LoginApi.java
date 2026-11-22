package com.RobinNotBad.BiliClient.api;

import android.graphics.Bitmap;

import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.QRCodeUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.ToolsUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;

import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Created by liupe on 2018/10/6.
 * 各位大佬好
 * #以下代码修改自腕上哔哩的开源项目，感谢开源者做出的贡献！
 */

public class LoginApi {
    private static String oauthKey;
    private static String tvAuthCode;

    private static final String TV_APP_KEY = "4409e2ce8ffd12b8";
    private static final String TV_APP_SEC = "59b43e04ad6965f34319062b478f83dd";

    static String tvSign(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        sorted.put("appkey", TV_APP_KEY);
        sorted.put("ts", String.valueOf(System.currentTimeMillis() / 1000));

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        String signStr = sb.toString() + TV_APP_SEC;

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(signStr.getBytes(StandardCharsets.UTF_8));
            String sign = new BigInteger(1, digest).toString(16);
            while (sign.length() < 32) sign = "0" + sign;

            sb.append("&sign=").append(sign);
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static Bitmap getLoginQR() throws JSONException, IOException {
        String url = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-fe-header&go_url=https:%2F%2Fwww.bilibili.com%2F";
        JSONObject loginUrlJson = NetWorkUtil.getJson(url, CookiesApi.genWebHeaders()).getJSONObject("data");
        oauthKey = loginUrlJson.getString("qrcode_key");
        return QRCodeUtil.createQRCodeBitmap(loginUrlJson.getString("url"), 320, 320);
    }

    public static Response getLoginState() throws IOException {
        return NetWorkUtil.get("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?source=main-fe-header&qrcode_key=" + oauthKey, CookiesApi.genWebHeaders());
    }

    public static Bitmap getTVLoginQR() throws JSONException, IOException {
        String url = "https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code";
        Map<String, String> params = new TreeMap<>();
        params.put("local_id", "0");
        String signedBody = tvSign(params);

        JSONObject result = new JSONObject(NetWorkUtil.post(url, signedBody, NetWorkUtil.webHeaders).body().string());
        JSONObject data = result.getJSONObject("data");
        tvAuthCode = data.getString("auth_code");
        return QRCodeUtil.createQRCodeBitmap(data.getString("url"), 320, 320);
    }

    public static Response getTVLoginState() throws IOException {
        String url = "https://passport.bilibili.com/x/passport-tv-login/qrcode/poll";
        Map<String, String> params = new TreeMap<>();
        params.put("auth_code", tvAuthCode);
        params.put("local_id", "0");
        String signedBody = tvSign(params);

        return NetWorkUtil.post(url, signedBody, NetWorkUtil.webHeaders);
    }

    public static void requestSSOs() throws JSONException, IOException {
        String listUrl = "https://passport.bilibili.com/x/passport-login/web/sso/list";
        JSONObject listResult;
        try (Response listResponse = NetWorkUtil.post(listUrl, new NetWorkUtil.FormData().put("csrf", NetWorkUtil.currentCsrf()).toString())) {
            ResponseBody listBody = listResponse.body();
            if (listBody == null) return;
            listResult = new JSONObject(listBody.string());
        }
        if (listResult.has("data") && !listResult.isNull("data")) {
            JSONArray sso = listResult.getJSONObject("data").getJSONArray("sso");
            for (int i = 0; i < sso.length(); i++) {
                // SSO 广播只要发出去，不看响应内容；不读 body 就必须显式关闭，
                // 否则每条广播都会漏掉一个连接（一次登录可累积 N 条）。
                try (Response ignored = NetWorkUtil.post(sso.getString(i), "")) {
                    // 故意留空
                }
            }
        }
    }

    public static JSONObject getCaptcha() throws IOException, JSONException {
        String url = "https://passport.bilibili.com/x/passport-login/captcha?source=main_web";
        return NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
    }

    public static JSONObject getWebKey() throws IOException, JSONException {
        String url = "https://passport.bilibili.com/x/passport-login/web/key";
        return NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
    }

    public static JSONObject passwordLogin(String username, String password, String captchaToken, String challenge, String validate, String seccode) throws IOException, JSONException {
        String url = "https://passport.bilibili.com/x/passport-login/web/login";
        String body = new NetWorkUtil.FormData()
                .put("source", "main_web")
                .put("username", username)
                .put("password", password)
                .put("keep", "0")
                .put("token", captchaToken)
                .put("challenge", challenge)
                .put("validate", validate)
                .put("seccode", seccode)
                .toString();
        return new JSONObject(NetWorkUtil.post(url, body, NetWorkUtil.webHeaders).body().string());
    }

    public static JSONObject smsSend(String phone, String captchaToken, String challenge, String validate, String seccode) throws IOException, JSONException {
        String url = "https://passport.bilibili.com/x/passport-login/web/sms/send";
        String body = new NetWorkUtil.FormData()
                .put("cid", "86")
                .put("tel", phone)
                .put("source", "main_web")
                .put("token", captchaToken)
                .put("challenge", challenge)
                .put("validate", validate)
                .put("seccode", seccode)
                .toString();
        return new JSONObject(NetWorkUtil.post(url, body, NetWorkUtil.webHeaders).body().string());
    }

    public static JSONObject smsLogin(String phone, String code, String captchaKey) throws IOException, JSONException {
        String url = "https://passport.bilibili.com/x/passport-login/web/login/sms";
        String body = new NetWorkUtil.FormData()
                .put("cid", "86")
                .put("tel", phone)
                .put("source", "main_web")
                .put("code", code)
                .put("captcha_key", captchaKey != null ? captchaKey : "")
                .put("keep", "1")
                .toString();
        return new JSONObject(NetWorkUtil.post(url, body, NetWorkUtil.webHeaders).body().string());
    }

    // ==================== APP 端短信登录 ====================
    //
    // 端点、参数名、`|jordan` 后缀、`statistics` 取值与 buvid 生成算法**全部来自仓库内的公开快照**
    // （`bilibili-API/docs/login/login_action/SMS.md` 的「发送短信验证码_app端」「使用短信验证码登录_app端」
    // 两节），没有额外的私有知识；公开构建里也不含任何凭据。
    //
    // 与 web 端的三点差异（也是换成它要付的代价）：
    //  1. 需要 `login_session_id`（uuid 去掉 `-`），**发送与登录必须是同一个值**，否则服务端关联不上；
    //  2. 极验那组参数改名成 `gee_challenge` / `gee_validate` / `gee_seccode`（`recaptcha_token` 即 web 的 token）；
    //  3. 额外要 `channel=bili`、`buvid`、`local_id`（与 buvid 同值）与 `statistics` 统计串。

    static final String SMS_SEND_APP_URL = "https://passport.bilibili.com/x/passport-login/sms/send";
    static final String SMS_LOGIN_APP_URL = "https://passport.bilibili.com/x/passport-login/login/sms";

    /** APP 端固定统计串（快照原文：`{"appId":1,"platform":3,"version":"7.27.0","abtest":""}`）。 */
    public static final String SMS_APP_STATISTICS = "{\"appId\":1,\"platform\":3,\"version\":\"7.27.0\",\"abtest\":\"\"}";

    /** 生成 APP 端需要的 `login_session_id`：uuid 去掉横线（快照备注原文）。 */
    public static String newLoginSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 按快照给的算法生成 buvid：随机 6 段 MAC → 用 `:` 拼起来取 md5 → `XY` + md5 的第 3/13/23 个字符 + 整个 md5。
     *
     * <p>产物固定 37 位（`XY` + 3 位取样 + 32 位 md5）、以 `XY` 开头。抽成纯函数是为了能被单测钉住格式——
     * 服务端对 buvid 的格式不做校验也说不准，改错了不会有明显报错，只会让风控更容易打回来。
     */
    public static String genBuvid() {
        Random random = new Random();
        StringBuilder mac = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) mac.append(':');
            mac.append(String.format("%02x", random.nextInt(256)));
        }
        String md5 = ToolsUtil.md5(mac.toString());
        if (md5.length() < 23) return "XY" + md5; // 理论上不会发生；宁可返回一个可用的短串也不抛
        return "XY" + md5.charAt(2) + md5.charAt(12) + md5.charAt(22) + md5;
    }

    /**
     * 极验结果归一成 `xxx|jordan`。
     *
     * <p>`CaptchaWebViewActivity` 有时直接给 seccode、有时给带后缀的串，两个端点的要求都一样是带后缀；
     * 抽出来是因为这段逻辑原来写在 `SMSLoginFragment` 里，换成 APP 端后两处都要用。
     */
    public static String normalizeSeccode(String validate, String seccode) {
        if (seccode != null && !seccode.isEmpty() && seccode.contains("|jordan")) return seccode;
        return (validate == null ? "" : validate) + "|jordan";
    }

    /** APP 端「发送短信验证码」的请求体（纯函数，供单测断言参数名与取值）。 */
    public static NetWorkUtil.FormData buildSmsSendAppForm(String phone, String loginSessionId,
                                                          String recaptchaToken, String challenge,
                                                          String validate, String seccode, String buvid) {
        return new NetWorkUtil.FormData()
                .put("cid", "86")
                .put("tel", phone == null ? "" : phone)
                .put("login_session_id", loginSessionId == null ? "" : loginSessionId)
                .put("recaptcha_token", recaptchaToken == null ? "" : recaptchaToken)
                .put("gee_challenge", challenge == null ? "" : challenge)
                .put("gee_validate", validate == null ? "" : validate)
                .put("gee_seccode", normalizeSeccode(validate, seccode))
                .put("channel", "bili")
                .put("buvid", buvid == null ? "" : buvid)
                .put("local_id", buvid == null ? "" : buvid)
                .put("statistics", SMS_APP_STATISTICS);
    }

    /** APP 端「短信验证码登录」的请求体（纯函数）。`login_session_id` 必须与发送时一致。 */
    public static NetWorkUtil.FormData buildSmsLoginAppForm(String phone, String loginSessionId,
                                                           String code, String captchaKey) {
        return new NetWorkUtil.FormData()
                .put("cid", "86")
                .put("tel", phone == null ? "" : phone)
                .put("login_session_id", loginSessionId == null ? "" : loginSessionId)
                .put("code", code == null ? "" : code)
                .put("captcha_key", captchaKey == null ? "" : captchaKey);
    }

    /**
     * APP 端发送短信验证码。成功时 `data.captcha_key` 要留着给登录用。
     *
     * <p>失败码里 {@code 1003}（验证码已经发送）、{@code 86203}（发送次数达上限）说明**短信确实发出去了**，
     * 调用方不要因此回退到 web 端重发；只有 {@link #smsShouldFallbackToWeb(int)} 为 true 时才回退。
     */
    public static JSONObject smsSendApp(String phone, String loginSessionId, String recaptchaToken,
                                        String challenge, String validate, String seccode, String buvid)
            throws IOException, JSONException {
        String body = buildSmsSendAppForm(phone, loginSessionId, recaptchaToken, challenge, validate, seccode, buvid)
                .toString();
        return new JSONObject(NetWorkUtil.post(SMS_SEND_APP_URL, body, NetWorkUtil.webHeaders).body().string());
    }

    /** APP 端短信验证码登录。成功返回 `data.token_info`（access_token / refresh_token）与 `cookie_info`。 */
    public static JSONObject smsLoginApp(String phone, String loginSessionId, String code, String captchaKey)
            throws IOException, JSONException {
        String body = buildSmsLoginAppForm(phone, loginSessionId, code, captchaKey).toString();
        return new JSONObject(NetWorkUtil.post(SMS_LOGIN_APP_URL, body, NetWorkUtil.webHeaders).body().string());
    }

    /**
     * APP 端发送失败时，能不能安全地回退到 web 端重试。
     *
     * <p>只有「**短信没发出去**」的失败才允许回退，否则用户会收到两条短信、还会被 60 秒 CD 挡住。
     * 因此 {@code 1003}（已发送）、{@code 86203}（次数达上限）、{@code 1025}（永久封禁）
     * 与 {@code -101}（未登录，端点一类的语义问题）一律不回退。
     */
    public static boolean smsShouldFallbackToWeb(int code) {
        switch (code) {
            case 0:
            case 1003:
            case 86203:
            case 1025:
            case -101:
                return false;
            default:
                // -400 / 2400 / 2406 与其它负数码都按「没发出去」处理：APP 端参数或极验不接受，
                // web 端的参数名不同，换一套可能就过了
                return true;
        }
    }

}