package com.RobinNotBad.BiliClient.api;

import android.graphics.Bitmap;

import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.QRCodeUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

import okhttp3.Response;

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

    private static String tvSign(Map<String, String> params) {
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
            byte[] digest = md.digest(signStr.getBytes());
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
        JSONObject listResult = new JSONObject(NetWorkUtil.post(listUrl, new NetWorkUtil.FormData().put("csrf", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, "")).toString()).body().string());
        if (listResult.has("data") && !listResult.isNull("data")) {
            JSONArray sso = listResult.getJSONObject("data").getJSONArray("sso");
            for (int i = 0; i < sso.length(); i++) {
                NetWorkUtil.post(sso.getString(i), "");
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

}