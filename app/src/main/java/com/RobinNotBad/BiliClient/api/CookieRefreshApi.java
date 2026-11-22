package com.RobinNotBad.BiliClient.api;

import android.util.Base64;

import com.RobinNotBad.BiliClient.util.AccountManager;
import com.RobinNotBad.BiliClient.util.CookieMerge;
import com.RobinNotBad.BiliClient.util.CookieRefreshPolicy;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.RefreshGuard;
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
import java.util.Collections;
import java.util.List;
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
    private static final String COOKIE_REFRESH_URL = "https://passport.bilibili.com/x/passport-login/web/cookie/refresh";
    private static final String CONFIRM_REFRESH_URL = "https://passport.bilibili.com/x/passport-login/web/confirm/refresh";

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
        // 响应用 try-with-resources 收掉：这是「每次冷启动 + 每 30 分钟」都会走的高频路径。
        // 注意别把这条改动说成「修了一个连接泄漏」——OkHttp 读完 body 就会归还连接，不 close 并不
        // 会漏到连接池耗尽；真正需要 close 的是不读 body 就 return 的异常路径（解析中途抛异常时
        // 连接不归还），以及全仓 NetWorkUtil 返回裸 Response 的统一写法。
        try (Response response = NetWorkUtil.get(url)) {
            if (response.body() != null) {
                Document document = Jsoup.parse(response.body().string());
                if (document.select("#1-name").size() > 0)
                    return document.select("#1-name").get(0).text();
                else return "";
            } else return "";
        }
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
     * <h3>顺序为什么必须是这样（26.11.22「老是掉登录」返工）</h3>
     * 按官方流程（`bilibili-API/docs/login/cookie_refresh.md`）：
     * <ol>
     *   <li>`cookie/refresh` 通过 `Set-Cookie` 下发**新的** `SESSDATA` / `bili_jct` /
     *       `DedeUserID` / `sid`，并在 JSON 里返回**新的** `refresh_token`（一次性轮换）；</li>
     *   <li>`confirm/refresh` 让**旧** `refresh_token` 对应的 Cookie 失效。</li>
     * </ol>
     *
     * <p>旧实现有三处会直接把用户踢下线，这里逐条改掉：
     * <ol>
     *   <li><b>把 SP 里现存的 Cookie 当成"新 Cookie"来校验。</b>刷新响应里的 `Set-Cookie` 是不可靠的
     *       中间环节（代际过滤、域策略、拦截器任何一环丢一条就没了），一旦丢了，旧代码会拿**旧的**
     *       `bili_jct` + 旧 `refresh_token` 去 confirm——服务端作废旧会话，本地还是旧 Cookie，
     *       于是「必定掉登录」。现在改为直接解析本次响应的 `Set-Cookie`：没有新 `SESSDATA` /
     *       `bili_jct` 就根本不去 confirm。</li>
     *   <li><b>confirm 在落盘之前。</b>confirm 失败就把刚落盘的新 `refresh_token` 一起丢了，一次性
     *       口令丢了就永久断链。现在新凭据（连同"待确认的旧口令"）一次原子提交，confirm 变成事后
     *       可补发的动作（见 {@link #confirmPendingRefresh()}）。</li>
     *   <li><b>没有互斥。</b>启动页和 30 分钟调度器可能同时拿同一个一次性 `refresh_token` 去刷，
     *       必有一个拿到 -101。现在两边都在 {@link RefreshGuard#LOCK} 上排队。</li>
     * </ol>
     *
     * @param RefreshCsrf 实时刷新口令（`getRefreshCsrf()` 取到的那段）
     * @return 见 {@link RefreshResult}；网络/解析异常直接抛出，由调用方按「保留登录态」处理
     */
    public static RefreshResult refreshCookie(String RefreshCsrf) throws JSONException, IOException {
        //必须的参数不能为空
        if (Objects.equals(RefreshCsrf, "")) return RefreshResult.RETRY;
        // 两条刷新入口（启动页 / 定时调度）共用一把锁：refresh_token 是一次性轮换口令，
        // 谁先拿到谁赢，并发进来的那个必然吃 -101；而 -101 一旦被当成「掉登录」就真掉登录了
        synchronized (RefreshGuard.LOCK) {
            // 上一轮的新凭据已经落盘、但 confirm 还没发出去：先补发。
            // 补不掉就别开新的一轮——否则 PENDING_CONFIRM 会被新值覆盖，那条旧会话永远活着
            if (!confirmPendingRefresh()) {
                Logu.w("Cookie刷新中止", "上一轮的 confirm 还没补发成功，稍后再试");
                return RefreshResult.RETRY;
            }
            return refreshCookieLocked(RefreshCsrf);
        }
    }

    /** {@link #refreshCookie} 的主体；调用方必须持有 {@link RefreshGuard#LOCK}。 */
    private static RefreshResult refreshCookieLocked(String refreshCsrf) throws JSONException, IOException {
        String refreshTokenOld = SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "");
        if (Objects.equals(refreshTokenOld, "")) return RefreshResult.NEED_LOGIN;

        String cookiesOld = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        String csrfOld = NetWorkUtil.getInfoFromCookie("bili_jct", cookiesOld);
        if (csrfOld.isEmpty()) {
            // 没有 csrf 这个请求发出去也是 -111，别拿它当「登录失效」
            Logu.w("Cookie刷新中止", "本地 Cookie 里没有 bili_jct，跳过这次刷新");
            return RefreshResult.RETRY;
        }

        //请求一个新的cookie
        String args = "csrf=" + csrfOld + "&refresh_csrf=" + refreshCsrf
                + "&source=main_web&refresh_token=" + refreshTokenOld;
        JSONObject result;
        List<String> setCookies;
        try (Response response = NetWorkUtil.post(COOKIE_REFRESH_URL, args, NetWorkUtil.webHeaders)) {
            if (response.body() == null) {
                Logu.e("Cookie刷新失败", "刷新接口返回空响应");
                return RefreshResult.RETRY;
            }
            // Set-Cookie 必须在这里取：NetWorkUtil 的通用落盘对刷新接口是让路的
            // （见 saveCookiesFromResponse），这些 Cookie 唯一的入口就是这里
            setCookies = response.headers("Set-Cookie");
            result = new JSONObject(response.body().string());
        }
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

        // ---- 先核对「这次响应自己带来的会话」，再谈落盘与确认 ----
        List<String> issued = setCookies == null ? Collections.<String>emptyList() : setCookies;
        CookieMerge.Result issuedAuth = CookieMerge.mergeHeaders("", issued, true);
        String issuedSess = NetWorkUtil.getInfoFromCookie("SESSDATA", issuedAuth.getCookies());
        String issuedJct = NetWorkUtil.getInfoFromCookie("bili_jct", issuedAuth.getCookies());
        long expectedMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L);
        long issuedMid = parseMid(NetWorkUtil.getInfoFromCookie("DedeUserID", issuedAuth.getCookies()));
        // 响应没带 DedeUserID 时无法核对身份，按「同账号」处理（同一条会话本来就是同一个账号）；
        // 带了就必须与本地一致，否则是串号，绝不能写进当前账号
        if (issuedMid <= 0L) issuedMid = expectedMid;
        CookieRefreshPolicy.RefreshCheck check = CookieRefreshPolicy.checkIssuedSession(
                issuedSess, issuedJct, issuedMid, expectedMid,
                NetWorkUtil.getInfoFromCookie("SESSDATA", cookiesOld));
        if (check != CookieRefreshPolicy.RefreshCheck.READY) {
            // 绝不 confirm：旧 Cookie 一旦被作废而本地又没有新 Cookie，用户就是彻底掉登录
            Logu.e("Cookie刷新中止", "响应没给出可用的新会话（" + check + "），不确认刷新以免作废旧会话");
            return RefreshResult.RETRY;
        }

        // 合并成完整 Cookie（鉴权键只接受响应下发的新值，其余键覆盖式合并；空值/删除一律丢弃）
        String cookiesNew = CookieMerge.mergeHeaders(cookiesOld, issued, true).getCookies();
        String accessKey = SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, "");

        // 新凭据 + 「待确认的旧口令」原子落盘，并做 CAS 复核（期间没切号、口令没被别的路径换掉）
        if (!AccountManager.commitRefreshedSession(cookiesOld, refreshTokenOld, cookiesNew, refreshTokenNew, accessKey)) {
            Logu.w("Cookie刷新中止", "新凭据提交被拒（期间已被别的路径更新），丢弃这次结果");
            return RefreshResult.RETRY;
        }

        // 使老的Cookie失效（用**旧的** refresh_token，按文档语义）；失败不影响登录态，下次补发
        if (!confirmPendingRefresh()) {
            Logu.w("Cookie刷新", "新凭据已生效，但旧会话的确认没发出去，稍后补发");
        }

        // 只记录长度这类非敏感信息（审计 S2：refresh_token / Cookie 本身就是凭证，不能进日志）
        Logu.v("Cookie刷新", "refresh_token长度=" + refreshTokenNew.length()
                + "，cookies长度=" + cookiesNew.length());
        return RefreshResult.OK;
    }

    /**
     * 补发尚未确认的旧 refresh_token（26.11.22 新增，对应 BiliTerminal-Revival 的
     * `confirmPendingRefresh()`）。
     *
     * <p>confirm/refresh 的作用只是让**旧**会话失效，所以它对新凭据没有任何影响：失败、超时、
     * 进程被杀都只是「那条旧会话多活几天」，下次任意刷新入口进来都会先把它补掉。
     * 这正是「confirm 失败了也不能丢新口令」能成立的前提。
     *
     * @return true 表示「没有待确认的」或「已经确认掉」；false 表示这次没确认成功，可稍后重试
     */
    public static boolean confirmPendingRefresh() {
        String pending = AccountManager.getPendingConfirm();
        if (pending.isEmpty()) return true;

        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", NetWorkUtil.getCachedCookies());
        if (csrf.isEmpty()) csrf = SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, "");
        if (csrf.isEmpty()) {
            Logu.w("Cookie刷新中止", "本地没有 csrf，无法补发 confirm");
            return false;
        }

        int code;
        try (Response confirm = NetWorkUtil.post(CONFIRM_REFRESH_URL,
                "csrf=" + csrf + "&refresh_token=" + pending, NetWorkUtil.webHeaders)) {
            if (confirm.body() == null) {
                Logu.w("Cookie刷新", "确认刷新返回空响应");
                return false;
            }
            code = new JSONObject(confirm.body().string()).optInt("code", -1);
        } catch (Exception e) {
            // 网络/解析异常：新会话与新口令都已落盘，旧会话还在（confirm 没成功），状态自洽
            Logu.w("Cookie刷新", "确认刷新异常：" + e.getClass().getSimpleName());
            return false;
        }
        if (code == 0) {
            AccountManager.clearPendingConfirm();
            Logu.v("Cookie刷新", "旧会话已确认失效");
            return true;
        }
        if (code == -101) {
            // 待确认的那条旧会话本来就已经没了：确认的目标达成了
            AccountManager.clearPendingConfirm();
            Logu.w("Cookie刷新", "确认刷新返回 -101（旧口令已失效），清掉待确认记录");
            return true;
        }
        Logu.w("Cookie刷新", "确认刷新时返回:" + code + "，下次再补发");
        return false;
    }

    /** 从 Cookie 里的 DedeUserID 解析 mid；解析不出按 0 处理。 */
    private static long parseMid(String raw) {
        if (raw == null || raw.isEmpty()) return 0L;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
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
