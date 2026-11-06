package com.RobinNotBad.BiliClient.api;

import android.util.Base64;

import com.RobinNotBad.BiliClient.util.AccountManager;
import com.RobinNotBad.BiliClient.util.CookieRefreshPolicy;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import okhttp3.Response;

/*
这里是Cookie刷新逻辑
https://socialsisteryi.github.io/bilibili-API-collect/docs/login/cookie_refresh.html#%E8%8E%B7%E5%8F%96refresh-csrf
 */
public class CookieRefreshApi {
    public static JSONObject cookieInfo() throws IOException, JSONException {
        String url = "https://passport.bilibili.com/x/passport-login/web/cookie/info";
        JSONObject result = NetWorkUtil.getJson(url);
        return result.getJSONObject("data");
    }

    public static String getCorrespondPath(long timestamp) { //时间戳要精准到毫秒！
        /*
        https://socialsisteryi.github.io/bilibili-API-collect/docs/login/cookie_refresh.html#%E7%94%9F%E6%88%90correspondpath%E7%AE%97%E6%B3%95
         */
        try {
            String publicKeyPEM = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDLgd2OAkcGVtoE3ThUREbio0EgUc/prcajMKXvkCKFCWhJYJcLkcM2DKKcSeFpD/j6Boy538YXnR6VhcuUJOhH2x71nzPjfdTcqMz7djHum0qSZA0AyCBDABUqCrfNgCiJ00Ra7GmRj+YCK1NJEuewlb40JNrRuoEUXpabUzGB8QIDAQAB";
            X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(Base64.decode(publicKeyPEM, Base64.DEFAULT));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(x509EncodedKeySpec);
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            OAEPParameterSpec oaepParameterSpec = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParameterSpec);
            String data = "refresh_" + timestamp;
            return base16Encode(cipher.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String getRefreshCsrf(String CorrespondPath) throws IOException {
        if (Objects.equals(CorrespondPath, "")) return "";
        String url = "https://www.bilibili.com/correspond/1/" + CorrespondPath;
        Response response = NetWorkUtil.get(url);
        if (response.body() != null) {
            Document document = Jsoup.parse(response.body().string());
            if (document.select("#1-name").size() > 0)
                return document.select("#1-name").get(0).text();
            else return "";
        } else return "";
    }

    /**
     * 一次 Web Cookie 刷新的结果（26.11.06.1「升级后掉登录」返工）。
     *
     * 调用方必须区分「这次没成功」与「服务端说登录态已经没了」：前者只能退避重试、**绝不能清
     * 本地凭证**（网络抖动一次就要求重新登录，正是历史上「老是掉登录」投诉的来源）；后者才该
     * 走登录失效流程。
     */
    public enum RefreshResult {
        /** 刷新并落盘成功，登录态已续期 */
        OK,
        /** 这次没成功，但本地登录态没被破坏：保留全部凭证，过一会儿再试 */
        RETRY,
        /** 服务端明确说会话/刷新口令失效（-101）：需要用户重新登录 */
        NEED_LOGIN
    }

    /**
     * 刷新一次 Web Cookie，成功时把整套新凭据**原子落盘**。
     *
     * <h3>顺序为什么必须是这样</h3>
     * 按官方流程（`bilibili-API/docs/login/cookie_refresh.md`）：
     * <ol>
     *   <li>`cookie/refresh` 通过 `Set-Cookie` 下发**新的** `SESSDATA` / `bili_jct` /
     *       `DedeUserID` / `sid`，并在 JSON 里返回**新的** `refresh_token`（一次性轮换）；</li>
     *   <li>`confirm/refresh` 让**旧** `refresh_token` 对应的 Cookie 失效。</li>
     * </ol>
     * 所以：新 `refresh_token` 一到手就必须先落盘（旧代码要等 confirm 成功才写，中途任何异常
     * 都会让本地永远续不上会话 → 迟早掉登录）；confirm 之前必须核对新 Cookie 真的到了本地
     * （否则「旧会话被作废、本地还是旧的」= 必掉登录）。
     *
     * @param RefreshCsrf 实时刷新口令（`getRefreshCsrf()` 取到的那段）
     * @return 见 {@link RefreshResult}；网络/解析异常直接抛出，由调用方按「保留登录态」处理
     */
    public static RefreshResult refreshCookie(String RefreshCsrf) throws JSONException, IOException {
        //必须的参数不能为空
        if (Objects.equals(RefreshCsrf, "")) return RefreshResult.RETRY;
        String refreshTokenOld = SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "");
        if (Objects.equals(refreshTokenOld, "")) return RefreshResult.NEED_LOGIN;

        String cookies_old = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");

        //请求一个新的cookie
        String url = "https://passport.bilibili.com/x/passport-login/web/cookie/refresh";
        String args = "csrf=" + NetWorkUtil.getInfoFromCookie("bili_jct", cookies_old) + "&refresh_csrf=" + RefreshCsrf + "&source=main_web&refresh_token=" + refreshTokenOld;
        Response response = NetWorkUtil.post(url, args, NetWorkUtil.webHeaders);
        if (response.body() == null) {
            Logu.e("Cookie刷新失败", "刷新接口返回空响应");
            return RefreshResult.RETRY;
        }
        JSONObject result = new JSONObject(response.body().string());
        int code = result.optInt("code", -1);
        if (code == -101) {
            // 服务端明确判定会话/口令失效：这是唯一可以引导用户重新登录的信号
            Logu.e("Cookie刷新失败", "刷新接口返回 -101，需要重新登录");
            return RefreshResult.NEED_LOGIN;
        }
        if (code != 0) {
            // 86095（refresh_csrf 错误 / refresh_token 与 cookie 不匹配）等：本地凭据没被动过，
            // 保留登录态、退避后再试，不要自己把用户登出
            Logu.e("Cookie刷新失败", "刷新时返回:" + code);
            return RefreshResult.RETRY;
        }

        JSONObject data = result.optJSONObject("data");
        String refreshTokenNew = data == null ? "" : data.optString("refresh_token", "");
        if (refreshTokenNew.isEmpty()) {
            Logu.e("Cookie刷新失败", "刷新响应缺 refresh_token");
            return RefreshResult.RETRY;
        }

        //先落盘新的 refresh_token：一次性轮换口令，服务端发过就必须保存
        SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, refreshTokenNew);

        // 新 Cookie 由 CookieSaveInterceptor 随刷新响应落盘；确认之前必须核对确实到了本地。
        String cookiesNew = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        long expectedMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L);
        String dedeUserId = NetWorkUtil.getInfoFromCookie("DedeUserID", cookiesNew);
        long cookieMid;
        try {
            cookieMid = Long.parseLong(dedeUserId);
        } catch (NumberFormatException e) {
            cookieMid = 0L;
        }
        CookieRefreshPolicy.RefreshCheck check = CookieRefreshPolicy.checkRefreshedCredentials(
                refreshTokenNew,
                NetWorkUtil.getInfoFromCookie("SESSDATA", cookiesNew),
                cookieMid,
                expectedMid);
        if (check != CookieRefreshPolicy.RefreshCheck.READY) {
            // 绝不 confirm：旧 Cookie 一旦被作废而本地又没有新 Cookie，用户就是彻底掉登录
            Logu.e("Cookie刷新中止", "新凭据没落全（" + check + "），不确认刷新以免作废旧会话");
            return RefreshResult.RETRY;
        }

        //使老的Cookie失效（用**旧的** refresh_token，按文档语义）
        int confirmCode;
        try {
            Response confirm = NetWorkUtil.post(
                    "https://passport.bilibili.com/x/passport-login/web/confirm/refresh",
                    "csrf=" + NetWorkUtil.getInfoFromCookie("bili_jct", cookiesNew)
                            + "&refresh_token=" + refreshTokenOld,
                    NetWorkUtil.webHeaders);
            if (confirm.body() == null) {
                Logu.e("Cookie刷新失败", "确认刷新返回空响应");
                return RefreshResult.RETRY;
            }
            confirmCode = new JSONObject(confirm.body().string()).optInt("code", -1);
        } catch (Exception e) {
            // 网络/解析异常：新会话与新口令都已落盘，旧会话还在（confirm 没成功），状态自洽
            Logu.e("Cookie刷新失败", "确认刷新异常：" + e.getClass().getSimpleName());
            return RefreshResult.RETRY;
        }
        if (confirmCode != 0) {
            Logu.e("Cookie刷新失败", "确认刷新时返回:" + confirmCode);
            return RefreshResult.RETRY;
        }

        // 整套凭据一次性落库：同一把锁里写 mid / cookies / csrf / refresh_token / access_key，
        // 并升账号代际（在飞的旧响应不能再把旧 Cookie 写回来）
        String accessKey = SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, "");
        if (!AccountManager.applyCredentials(cookieMid, "", cookiesNew, refreshTokenNew, accessKey, "")) {
            Logu.e("Cookie刷新失败", "新凭据落库被拒绝（mid=" + cookieMid + "），稍后重试");
            return RefreshResult.RETRY;
        }

        // 只记录长度这类非敏感信息（审计 S2：refresh_token / Cookie 本身就是凭证，不能进日志）
        Logu.v("Cookie刷新", "refresh_token长度=" + refreshTokenNew.length()
                + "，cookies长度=" + cookiesNew.length());
        return RefreshResult.OK;
    }

    public static String base16Encode(byte[] src) {
        StringBuilder strbuf = new StringBuilder(src.length * 2);
        int i;

        for (i = 0; i < src.length; i++) {
            if (((int) src[i] & 0xff) < 0x10)
                strbuf.append("0");

            strbuf.append(Long.toString((int) src[i] & 0xff, 16));
        }

        return strbuf.toString();
    }
}
