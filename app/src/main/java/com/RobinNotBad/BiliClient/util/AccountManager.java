package com.RobinNotBad.BiliClient.util;

import com.RobinNotBad.BiliClient.api.UserInfoApi;
import com.RobinNotBad.BiliClient.model.UserInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 多账号登录态的本地管理：账号列表快照 + 当前账号凭证的切换。
 *
 * <h3>为什么有 {@link #applyCredentials}</h3>
 * 登录完成（扫码/密码/短信）、特殊登录导入、账号切换、退出登录，这四条路原来各写各的
 * SharedPreferences 写入。漏写一个字段就是一类线上问题：
 * <ul>
 *     <li>特殊登录导入的数据里没有 `access_key` 时，旧账号的 access_key 留在原地，
 *     请求带着上一个账号的 token —— 就是「凭据混乱」；</li>
 *     <li>csr 只在 Cookie 里有 `bili_jct` 时才更新，没带就沿用旧快照，POST 全部 -111；</li>
 *     <li>退出登录不清 access_key，下次启动仍被判成已登录。</li>
 * </ul>
 * 现在所有路径统一走 {@link #applyCredentials}：**缺省的凭证显式写空**，Cookie 与 mid 必须一致，
 * 并且同时升 {@link NetWorkUtil} 的账号代际，把上一个账号还在飞的请求挡掉。
 *
 * <h3>多账号导入导出</h3>
 * {@link #exportCurrentAccount()} 只导出当前账号（三字段单条，给特殊登录页「查看登录信息」）；
 * {@link #exportAllAccounts()} 把所有账号（含当前账号的最新凭证）打成一份 JSON，供独立的导出页；
 * {@link #importAccounts} 只写账号列表、**不动当前登录态**，所以导入不会把用户正在用的账号踢掉。
 */
public class AccountManager {

    private static final String ACCOUNT_LIST_KEY = "account_list";
    private static final String CURRENT_ACCOUNT_KEY = "current_account_mid";
    /**
     * 已落盘、但还没向服务端确认的**旧** refresh_token（26.11.22「老是掉登录」返工，Step 3）。
     *
     * <p>confirm/refresh 要做的事是「让旧会话失效」，它必须用**旧的** refresh_token。旧实现把
     * confirm 放在落盘之前跑，失败就把新口令丢掉——一次性轮换口令丢了就再也续不上（永久断链）。
     * 现在改成：整套新凭据（含本键）一次原子写入，confirm 变成事后可以补发的动作；
     * 进程被杀 / 没网 / 服务端抖动都不影响，下次刷新入口先补发它。
     */
    public static final String PENDING_CONFIRM_KEY = "cookie_pending_confirm";
    private static final Object lock = new Object();

    public static class AccountInfo {
        public long mid;
        public String name;
        public String cookies;
        public String refreshToken;
        public String accessKey;
        public String csrf;
        public String avatar;

        public AccountInfo(long mid, String name, String cookies, String refreshToken, String csrf) {
            this(mid, name, cookies, refreshToken, "", csrf, "");
        }

        public AccountInfo(long mid, String name, String cookies, String refreshToken, String csrf, String avatar) {
            this(mid, name, cookies, refreshToken, "", csrf, avatar);
        }

        public AccountInfo(long mid, String name, String cookies, String refreshToken, String accessKey, String csrf, String avatar) {
            this.mid = mid;
            this.name = name != null ? name : "";
            this.cookies = cookies != null ? cookies : "";
            this.refreshToken = refreshToken != null ? refreshToken : "";
            this.accessKey = accessKey != null ? accessKey : "";
            this.csrf = csrf != null ? csrf : "";
            this.avatar = avatar != null ? avatar : "";
        }

        public static AccountInfo fromJson(JSONObject json) throws JSONException {
            if (json == null) return null;
            return new AccountInfo(
                    json.optLong("mid", 0),
                    json.optString("name", ""),
                    json.optString("cookies", ""),
                    json.optString("refresh_token", ""),
                    json.optString("access_key", ""),
                    json.optString("csrf", ""),
                    json.optString("avatar", "")
            );
        }

        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("mid", mid);
            json.put("name", name);
            json.put("cookies", cookies);
            json.put("refresh_token", refreshToken);
            json.put("access_key", accessKey);
            json.put("csrf", csrf);
            json.put("avatar", avatar);
            return json;
        }

        public boolean isValid() {
            return mid > 0 && cookies != null && !cookies.isEmpty();
        }
    }

    /** 批量导入的统计结果，UI 只拿它拼提示，不含任何凭据。 */
    public static class ImportResult {
        public int added;
        public int updated;
        public int skipped;

        public int total() {
            return added + updated;
        }
    }

    public static List<AccountInfo> getAccounts() {
        synchronized (lock) {
            List<AccountInfo> accounts = new ArrayList<>();
            String jsonStr = SharedPreferencesUtil.getString(ACCOUNT_LIST_KEY, "");
            if (jsonStr.isEmpty()) return accounts;
            try {
                JSONArray array = new JSONArray(jsonStr);
                for (int i = 0; i < array.length(); i++) {
                    AccountInfo info = AccountInfo.fromJson(array.getJSONObject(i));
                    if (info != null && info.isValid()) {
                        accounts.add(info);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return accounts;
        }
    }

    private static void saveAccounts(List<AccountInfo> accounts) {
        synchronized (lock) {
            JSONArray array = new JSONArray();
            for (AccountInfo account : accounts) {
                if (account != null && account.isValid()) {
                    try {
                        array.put(account.toJson());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
            SharedPreferencesUtil.putString(ACCOUNT_LIST_KEY, array.toString());
        }
    }

    /**
     * 把当前 SP 里的登录态同步进账号列表快照（昵称/头像要发一次网络）。
     *
     * <p>唯一会发网络的账号管理方法，因此**不能在主线程调用**：调用方是启动页、
     * Cookie 刷新成功后的后台线程、以及各登录 Fragment 的后台线程。
     *
     * <p>拉用户信息的这几秒里用户完全可能切了号——所以写回前要再确认「还是同一个账号、
     * 还是同一个代际」，否则会把 CURRENT_ACCOUNT_KEY 指回旧账号（表现为切完号又被切回去）。
     */
    public static void saveCurrentAccount() {
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (mid == 0) return;

        String cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        if (cookies.isEmpty()) return;

        String refreshToken = SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "");
        String accessKey = SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, "");
        // 存"当前有效"的 csrf 而不是登录时的快照：账号切换时会把它写回（见 applyCredentials），
        // bili_jct 若已轮换，存快照会导致切回来以后所有 POST 拿 -111
        String csrf = NetWorkUtil.currentCsrf();
        long generationAtStart = NetWorkUtil.getAccountGeneration();

        String avatar = "";
        String name = "UID:" + mid;

        try {
            UserInfo userInfo = UserInfoApi.getUserInfo(mid);
            if (userInfo != null) {
                avatar = userInfo.avatar;
                name = userInfo.name;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        synchronized (lock) {
            // 网络期间切号/退出：手里的 mid 已经是"上一任"，整笔丢弃
            if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) != mid) return;
            if (NetWorkUtil.getAccountGeneration() != generationAtStart) return;

            List<AccountInfo> accounts = getAccounts();
            boolean found = false;
            for (int i = 0; i < accounts.size(); i++) {
                if (accounts.get(i).mid == mid) {
                    accounts.get(i).cookies = cookies;
                    accounts.get(i).refreshToken = refreshToken;
                    accounts.get(i).accessKey = accessKey;
                    accounts.get(i).csrf = csrf;
                    accounts.get(i).avatar = avatar;
                    accounts.get(i).name = name;
                    found = true;
                    break;
                }
            }
            if (!found) {
                accounts.add(new AccountInfo(mid, name, cookies, refreshToken, accessKey, csrf, avatar));
            }
            saveAccounts(accounts);
            SharedPreferencesUtil.putLong(CURRENT_ACCOUNT_KEY, mid);
        }
    }

    /**
     * 登录完成 / 特殊登录导入 / 切号 的唯一落库入口：一次性替换整套凭证。
     *
     * <p>**缺省的凭证会显式写空**，绝不沿用上一个账号的值——这是「凭据混乱」的根因。
     * 同时会：升账号代际（让在飞的旧请求作废）、清掉按账号走的本地缓存（大会员状态、
     * 未读红点、追番快照）、重置 Cookie 刷新的退避状态。
     *
     * <p>不做网络请求，可以在主线程调用；但昵称/头像的补全仍靠后续
     * {@link #saveCurrentAccount()}（那个要放后台线程）。
     *
     * @param csrfHint Cookie 里没有 `bili_jct` 时使用的 csrf 快照（切号时传账号快照，其余传 null）
     * @return 是否写入成功；mid 与 Cookie 里的 DedeUserID 对不上时拒绝
     */
    public static boolean applyCredentials(long mid, String name, String cookies,
                                           String refreshToken, String accessKey, String avatar,
                                           String csrfHint) {
        String safeCookies = cookies == null ? "" : cookies.trim();
        if (mid <= 0 || safeCookies.isEmpty()) return false;

        // 无条件比对：以前 Cookie 里没有 DedeUserID 就放行，等于给「只拿到半截 Cookie 的写入」开口子——
        // 写进去的 mid 与凭据对不上，之后切号 / 刷新全是错的，而且很难回溯。
        String cookieMid = NetWorkUtil.getInfoFromCookie("DedeUserID", safeCookies);
        if (!cookieMid.equals(String.valueOf(mid))) {
            Logu.w("AccountManager", "拒绝写入凭据：mid=" + mid
                    + " 与 Cookie 里的 DedeUserID=" + cookieMid + " 不一致");
            return false;
        }

        // bili_jct 只认 Cookie 里的实时值；Cookie 没带才退回快照，两者都没有就写空，
        // 绝不留上一个账号的 csrf（那是会静默 -111 的典型来源）
        String liveCsrf = NetWorkUtil.getInfoFromCookie("bili_jct", safeCookies);
        String csrf = liveCsrf.isEmpty() ? (csrfHint == null ? "" : csrfHint) : liveCsrf;

        String safeRefresh = refreshToken == null ? "" : refreshToken;
        String safeAccessKey = accessKey == null ? "" : accessKey;
        String safeName = name == null ? "" : name;
        String safeAvatar = avatar == null ? "" : avatar;

        synchronized (lock) {
            SharedPreferencesUtil.edit(editor -> {
                editor.putLong(SharedPreferencesUtil.mid, mid);
                editor.putString(SharedPreferencesUtil.cookies, safeCookies);
                editor.putString(SharedPreferencesUtil.refresh_token, safeRefresh);
                editor.putString(SharedPreferencesUtil.access_key, safeAccessKey);
                editor.putString(SharedPreferencesUtil.csrf, csrf);
                editor.putLong(CURRENT_ACCOUNT_KEY, mid);
                editor.putBoolean(SharedPreferencesUtil.cookie_refresh, true);
                editor.putBoolean(SharedPreferencesUtil.setup, true);
                // 换了账号就等于换了一份一次性 refresh_token：例行刷新的计时必须重新起算，
                // 否则新账号会被上一个账号的时间戳挡住（或者被当成「该刷了」立刻多刷一次）
                editor.putLong(SharedPreferencesUtil.app_token_last_refresh, AppTokenRefreshPolicy.NO_REFRESH);
                // 上一个账号待确认的旧口令对新账号毫无意义，留着只会拿错 csrf 去发 confirm
                editor.remove(PENDING_CONFIRM_KEY);
            });
            // 必须在 SP 写完、且在锁内确定的顺序上"最后"发生：它升代际，把旧账号在飞的响应挡在门外。
            // 先升代际后写 Cookie 都不影响正确性——两者同在 NetWorkUtil 的锁里，不会插进一次旧响应。
            NetWorkUtil.applyAccountCookies(safeCookies);

            upsertSnapshotLocked(mid, safeCookies, safeRefresh, safeAccessKey, csrf, safeName, safeAvatar);
            clearPerAccountStateLocked();
        }
        // 退避状态是给上一个账号算的：不清掉，切到"Cookie 刚过期"的账号会先等最长 4 小时
        CookieRefreshScheduler.onAccountChanged();
        return true;
    }

    /** {@link #applyCredentials} 的便捷重载：csrf 只从 Cookie 里取。 */
    public static boolean applyCredentials(long mid, String name, String cookies,
                                           String refreshToken, String accessKey, String avatar) {
        return applyCredentials(mid, name, cookies, refreshToken, accessKey, avatar, null);
    }

    // ================= 刷新台账：待确认口令 / 会话原子提交 / 启动修复 =================
    // 26.11.22「老是掉登录」返工。核心思路来自 BiliTerminal-Revival：新凭据先一次性原子落盘，
    // confirm/refresh 只负责让旧会话失效，失败可以事后补发；任何一步失败都不许把本地已经拿到的
    // 新口令丢掉（一次性轮换口令丢了就永久断链）。

    /** 读「已落盘、但还没向服务端确认」的旧 refresh_token；没有则返回空串。 */
    public static String getPendingConfirm() {
        String token = SharedPreferencesUtil.getString(PENDING_CONFIRM_KEY, "");
        return token == null ? "" : token;
    }

    /** 记下待确认的旧 refresh_token（正常由 {@link #commitRefreshedSession} 代写）。 */
    public static void setPendingConfirm(String oldRefreshToken) {
        String token = oldRefreshToken == null ? "" : oldRefreshToken;
        if (token.isEmpty()) {
            clearPendingConfirm();
            return;
        }
        synchronized (lock) {
            SharedPreferencesUtil.putString(PENDING_CONFIRM_KEY, token);
        }
    }

    /** 确认已完成（或确认已经毫无意义）时清掉待确认口令。 */
    public static void clearPendingConfirm() {
        synchronized (lock) {
            if (getPendingConfirm().isEmpty()) return;
            SharedPreferencesUtil.removeValue(PENDING_CONFIRM_KEY);
        }
    }

    /** 两份 Cookie 是否是同一条会话（比 SESSDATA；任一侧缺失按「不是同一条」处理）。 */
    public static boolean sameSession(String cookiesA, String cookiesB) {
        String sessA = NetWorkUtil.getInfoFromCookie("SESSDATA", cookiesA);
        String sessB = NetWorkUtil.getInfoFromCookie("SESSDATA", cookiesB);
        if (sessA.isEmpty() || sessB.isEmpty()) return false;
        return sessA.equals(sessB);
    }

    /** 从 Cookie 串里取 DedeUserID；取不到或不是数字时返回 0。 */
    private static long midOfCookies(String cookies) {
        String raw = NetWorkUtil.getInfoFromCookie("DedeUserID", cookies);
        if (raw == null || raw.isEmpty()) return 0L;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 把一次**已确认拿到新会话**的刷新结果原子落库（换票 / Cookie 轮换共用，26.11.22 新增）。
     *
     * <p>与 {@link #applyCredentials} 的区别，也是它存在的理由：
     * <ul>
     *   <li>这是 refresh 场景而不是登录场景——不动 setup、不清按账号走的缓存、不重置退避；</li>
     *   <li>做 CAS 复核：账号没换、口令还是「发起这次刷新时」那一份、Cookie 还是同一条会话。
     *       复核不过说明期间切过号/退出过/另一条刷新链已经成功，**绝不覆盖**，直接丢弃这些结果；</li>
     *   <li>顺手把旧口令写进 {@link #PENDING_CONFIRM_KEY}，让 confirm 变成事后可补发的动作。</li>
     * </ul>
     *
     * @param expectedCookies 发起刷新时的 Cookie 串（判断期间有没有换账号/换会话）
     * @param expectedRefreshToken 发起刷新时的 refresh_token（乐观锁的版本号）
     * @param newCookies 服务端这次下发的**合并后**的完整 Cookie
     * @param newRefreshToken 服务端这次下发的新 refresh_token
     * @param accessKey 新的 access_key；没有就传空串（显式写空，不沿用旧值）
     * @return 是否写入；false 表示这次结果已被别的写入抢先，调用方应丢弃而不是重试覆盖
     */
    public static boolean commitRefreshedSession(String expectedCookies, String expectedRefreshToken,
                                                 String newCookies, String newRefreshToken,
                                                 String accessKey) {
        String safeNewCookies = newCookies == null ? "" : newCookies.trim();
        String safeNewToken = newRefreshToken == null ? "" : newRefreshToken;
        if (safeNewCookies.isEmpty() || safeNewToken.isEmpty()) return false;

        String sessData = NetWorkUtil.getInfoFromCookie("SESSDATA", safeNewCookies);
        String jct = NetWorkUtil.getInfoFromCookie("bili_jct", safeNewCookies);
        long newMid = midOfCookies(safeNewCookies);
        if (sessData.isEmpty() || jct.isEmpty() || newMid <= 0L) {
            // 半截凭据：写进去等于把本地登录态换成假的，之后每次请求都是 -101
            Logu.w("AccountManager", "拒绝提交刷新结果：新 Cookie 缺 SESSDATA/bili_jct/DedeUserID"
                    + "（sess=" + sessData.length() + ", jct=" + jct.length() + ", mid=" + newMid + "）");
            return false;
        }

        String expected = expectedRefreshToken == null ? "" : expectedRefreshToken;
        String safeAccess = accessKey == null ? "" : accessKey;

        synchronized (lock) {
            long currentMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
            if (currentMid != newMid) {
                Logu.w("AccountManager", "丢弃刷新结果：期间换过账号（当前 mid=" + currentMid
                        + "，结果 mid=" + newMid + "）");
                return false;
            }
            String currentToken = SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "");
            if (!expected.isEmpty() && !expected.equals(currentToken)) {
                // 一次性口令：说明另一条刷新链已经成功过，这份结果是旧的，写下去会把好口令换成废口令
                Logu.w("AccountManager", "丢弃刷新结果：refresh_token 已被别的路径更新（乐观锁不匹配）");
                return false;
            }
            String currentCookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
            if (!sameSession(currentCookies, expectedCookies)) {
                Logu.w("AccountManager", "丢弃刷新结果：本地会话已经换过（不是发起刷新时那条）");
                return false;
            }

            SharedPreferencesUtil.edit(editor -> {
                editor.putLong(SharedPreferencesUtil.mid, newMid);
                editor.putString(SharedPreferencesUtil.cookies, safeNewCookies);
                editor.putString(SharedPreferencesUtil.refresh_token, safeNewToken);
                editor.putString(SharedPreferencesUtil.access_key, safeAccess);
                editor.putString(SharedPreferencesUtil.csrf, jct);
                // 待确认的旧口令和整套新凭据在同一笔提交里落盘：进程随时被杀也不会出现
                // 「新口令在、确认线索没了」这种既续不上又确认不了的死局
                editor.putString(PENDING_CONFIRM_KEY, expected);
                editor.putLong(CURRENT_ACCOUNT_KEY, newMid);
                editor.putBoolean(SharedPreferencesUtil.cookie_refresh, true);
                editor.putBoolean(SharedPreferencesUtil.setup, true);
            });
            // 同一账号内的同步替换（不升代际）：在飞的请求本来就用这份会话，升代际只会白丢它们的结果
            NetWorkUtil.applyAccountCookies(safeNewCookies);
            upsertSnapshotLocked(newMid, safeNewCookies, safeNewToken, safeAccess, jct, "", "");
        }
        Logu.i("AccountManager", "已提交刷新后的凭据：mid=" + newMid
                + "，sess=" + sessData.length() + "，token=" + safeNewToken.length());
        return true;
    }

    /**
     * 启动时修元数据（26.11.22 新增，对应 BiliTerminal-Revival 的 restoreCurrentAccount）。
     *
     * <p>只修**元数据**，不碰 Cookie 本身，也不拿快照去覆盖一份看起来正常的登录。
     * 历史上有过几种「半死」状态：mid=0 但 Cookie 里明明有 DedeUserID、csrf 与 bili_jct 不一致、
     * refresh_token 丢了但账号列表快照里还在。它们都会让刷新链路直接放弃
     * （{@code CookieRefreshScheduler.isLoggedIn()} 要求 mid!=0 且有 refresh_token），
     * 于是表现成「一直掉登录」。
     *
     * @return 是否修过（仅用于日志）
     */
    public static boolean restoreCurrentAccount() {
        synchronized (lock) {
            String cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
            if (cookies.isEmpty()) return false;

            long storedMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
            long cookieMid = midOfCookies(cookies);
            long mid = cookieMid > 0L ? cookieMid : storedMid;
            if (mid <= 0L) return false;

            boolean changed = false;
            if (cookieMid > 0L && cookieMid != storedMid) {
                // 以 Cookie 为准：真正随请求发出去的是它，mid 只是本地记账
                Logu.w("AccountManager", "修复 mid：" + storedMid + " -> " + cookieMid + "（以 Cookie 里的 DedeUserID 为准）");
                changed = true;
            }

            String liveCsrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookies);
            String storedCsrf = SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, "");
            boolean csrfStale = !liveCsrf.isEmpty() && !liveCsrf.equals(storedCsrf);
            if (csrfStale) {
                Logu.w("AccountManager", "修复 csrf：与 Cookie 里的 bili_jct 不一致");
                changed = true;
            }

            String refreshToken = SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "");
            String accessKey = SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, "");
            if (refreshToken.isEmpty()) {
                AccountInfo snapshot = getAccountByMid(mid);
                if (snapshot != null) {
                    String snapToken = snapshot.refreshToken == null ? "" : snapshot.refreshToken;
                    if (!snapToken.isEmpty()) {
                        refreshToken = snapToken;
                        accessKey = snapshot.accessKey == null ? "" : snapshot.accessKey;
                        Logu.w("AccountManager", "从账号快照补回 refresh_token（mid=" + mid + "）");
                        changed = true;
                    }
                }
            }

            long currentAccountMid = SharedPreferencesUtil.getLong(CURRENT_ACCOUNT_KEY, 0);
            if (currentAccountMid != mid) changed = true;
            boolean refreshFlag = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.cookie_refresh, false);
            if (!refreshFlag) changed = true;

            if (!changed) return false;

            final long fixMid = mid;
            final String fixRefresh = refreshToken;
            final String fixAccess = accessKey;
            final String fixCsrf = liveCsrf;
            final boolean fixCsrfNeeded = csrfStale;
            SharedPreferencesUtil.edit(editor -> {
                editor.putLong(SharedPreferencesUtil.mid, fixMid);
                editor.putLong(CURRENT_ACCOUNT_KEY, fixMid);
                if (fixCsrfNeeded) editor.putString(SharedPreferencesUtil.csrf, fixCsrf);
                editor.putString(SharedPreferencesUtil.refresh_token, fixRefresh);
                editor.putString(SharedPreferencesUtil.access_key, fixAccess);
                editor.putBoolean(SharedPreferencesUtil.cookie_refresh, true);
            });
            return true;
        }
    }

    /**
     * 会话被服务端判死（-101）之后，用账号列表里的快照把客户端拉回一份「可能还有效」的凭据
     * （26.11.22 新增，对应 BiliTerminal-Revival 的 restoreRejectedSession）。
     *
     * <p>典型场景：某次刷新把 confirm 发早了 / 新 Cookie 没拿到就拿旧 Cookie 去确认，
     * 服务端作废了会话而本地还留着旧 Cookie——这时快照里那条**上一条**会话可能仍然有效，
     * 拉回来至少能让用户继续用，而不是直接踢去登录页。回滚走 {@link #applyCredentials}，
     * 所以会升代际、清按账号走的缓存，属于重操作，只在真的判死时调用。
     *
     * @param rejectedCookies 被判死的 Cookie（通常是本地当前 Cookie）
     * @return 是否成功回滚
     */
    public static boolean restoreRejectedSession(String rejectedCookies) {
        String rejected = rejectedCookies == null ? "" : rejectedCookies;
        long mid = midOfCookies(rejected);
        if (mid <= 0L) mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (mid <= 0L) return false;

        AccountInfo snapshot;
        synchronized (lock) {
            snapshot = getAccountByMid(mid);
            if (snapshot == null || snapshot.cookies == null || snapshot.cookies.isEmpty()) return false;
            String rejectedSess = NetWorkUtil.getInfoFromCookie("SESSDATA", rejected);
            String snapshotSess = NetWorkUtil.getInfoFromCookie("SESSDATA", snapshot.cookies);
            if (rejectedSess.isEmpty() || rejectedSess.equals(snapshotSess)) {
                // 快照里就是这份被拒的 Cookie：没有更早的会话可退
                return false;
            }
            Logu.w("AccountManager", "会话被判死，回滚到账号快照（mid=" + mid + "）");
            return applyCredentials(mid, snapshot.name, snapshot.cookies, snapshot.refreshToken,
                    snapshot.accessKey, snapshot.avatar, snapshot.csrf);
        }
    }

    /**
     * 写账号列表快照（找不到就新增）。**调用方必须持有 {@link #lock}**。
     *
     * <p>昵称/头像只在拿到新值时才覆盖，避免把已有昵称冲成 "UID:xxx"。
     */
    private static void upsertSnapshotLocked(long mid, String cookies, String refreshToken,
                                            String accessKey, String csrf, String name, String avatar) {
        String safeName = name == null ? "" : name;
        String safeAvatar = avatar == null ? "" : avatar;
        List<AccountInfo> accounts = getAccounts();
        for (int i = 0; i < accounts.size(); i++) {
            AccountInfo info = accounts.get(i);
            if (info.mid != mid) continue;
            info.cookies = cookies;
            info.refreshToken = refreshToken;
            info.accessKey = accessKey;
            info.csrf = csrf;
            if (!safeName.isEmpty()) info.name = safeName;
            if (!safeAvatar.isEmpty()) info.avatar = safeAvatar;
            if (info.name == null || info.name.isEmpty()) info.name = "UID:" + mid;
            saveAccounts(accounts);
            return;
        }
        accounts.add(new AccountInfo(mid, safeName.isEmpty() ? "UID:" + mid : safeName,
                cookies, refreshToken, accessKey, csrf, safeAvatar));
        saveAccounts(accounts);
    }

    /**
     * 切到列表里的某个账号。
     *
     * <p>走 {@link #applyCredentials}：整套凭证替换 + 代际 +1，所以切号瞬间在飞的
     * 旧账号请求回来时的 Set-Cookie 会被丢弃（审计 §7.1）。
     */
    public static boolean switchToAccount(AccountInfo account) {
        if (account == null || !account.isValid()) return false;
        return applyCredentials(account.mid, account.name, account.cookies,
                account.refreshToken, account.accessKey, account.avatar, account.csrf);
    }

    /**
     * 退出登录 / 登录态失效：把当前账号凭据整个清掉，并让在飞的请求作废。
     *
     * <p>账号列表里的快照**保留**（Cookie 可能还有效，用户切回来还能用），
     * 只是不再作为当前账号。
     */
    public static void clearCurrentLogin() {
        synchronized (lock) {
            SharedPreferencesUtil.edit(editor -> {
                editor.putLong(SharedPreferencesUtil.mid, 0);
                editor.putString(SharedPreferencesUtil.cookies, "");
                editor.putString(SharedPreferencesUtil.csrf, "");
                editor.putString(SharedPreferencesUtil.refresh_token, "");
                editor.putString(SharedPreferencesUtil.access_key, "");
                editor.putBoolean(SharedPreferencesUtil.cookie_refresh, false);
                editor.putLong(CURRENT_ACCOUNT_KEY, 0);
                editor.putLong(SharedPreferencesUtil.app_token_last_refresh, AppTokenRefreshPolicy.NO_REFRESH);
                editor.remove(PENDING_CONFIRM_KEY);
            });
            // 空 Cookie + 升代际：退出后旧账号的任何迟到响应都不能把登录态写回来
            NetWorkUtil.applyAccountCookies("");
            clearPerAccountStateLocked();
        }
        CookieRefreshScheduler.onAccountChanged();
    }

    /**
     * 切号 / 登录 / 退出后必须清掉的「按账号走」的本地状态。
     *
     * <p>这些值原来是跨账号共享的：大会员开关按账号不同（不清的话，从大会员账号切到普通账号，
     * 缓存里 vip 还是 true，仍能点开 4K，实际拿到的却是降级流）；未读红点、追番更新快照
     * 也都是按账号算的。清空后各自重新拉一遍——宁可多一次请求，也不能串号。
     */
    private static void clearPerAccountStateLocked() {
        AccountStateStore.INSTANCE.onAccountChanged();
        SharedPreferencesUtil.removeValue(SharedPreferencesUtil.DYNAMIC_UPDATE_NUM);
        SharedPreferencesUtil.removeValue(SharedPreferencesUtil.MESSAGE_UPDATE_NUM);
        SharedPreferencesUtil.removeValue(SharedPreferencesUtil.BANGUMI_UPDATE_SNAPSHOT);
    }

    public static AccountInfo getAccountByMid(long mid) {
        synchronized (lock) {
            List<AccountInfo> accounts = getAccounts();
            for (AccountInfo account : accounts) {
                if (account.mid == mid) {
                    return account;
                }
            }
            return null;
        }
    }

    public static void updateAccountName(long mid, String name) {
        synchronized (lock) {
            List<AccountInfo> accounts = getAccounts();
            for (AccountInfo account : accounts) {
                if (account.mid == mid) {
                    account.name = name;
                    break;
                }
            }
            saveAccounts(accounts);
        }
    }

    /**
     * 从账号列表里删掉一个账号。
     *
     * <p>**当前正在用的账号不允许删**：删了以后 SP 里还留着它的完整凭证，下次
     * {@link #saveCurrentAccount()} 又会把它加回列表，于是「删了又出现」。要换账号请先切走。
     *
     * @return 是否真的删掉了
     */
    public static boolean removeAccount(long mid) {
        if (mid <= 0) return false;
        if (mid == getCurrentMid()) return false;
        synchronized (lock) {
            List<AccountInfo> accounts = getAccounts();
            boolean removed = false;
            for (int i = accounts.size() - 1; i >= 0; i--) {
                if (accounts.get(i).mid == mid) {
                    accounts.remove(i);
                    removed = true;
                }
            }
            if (removed) saveAccounts(accounts);

            if (SharedPreferencesUtil.getLong(CURRENT_ACCOUNT_KEY, 0) == mid) {
                SharedPreferencesUtil.putLong(CURRENT_ACCOUNT_KEY, 0);
            }
            return removed;
        }
    }

    /**
     * 导出**当前账号**的单条登录信息（26.11.02 的三字段格式，见 {@link SpecialLoginParser}）。
     *
     * <p>这是特殊登录页「查看登录信息」里显示 / 复制的内容：只有 cookies / refresh_token /
     * access_key 三个字段，与 26.11.02 发版逐字一致——老设备、别的终端都按这个格式读。
     * 全部账号的多账号信封仍由 {@link #exportAllAccounts()} 提供，改由独立的导出页展示，
     * 两边互不影响。
     *
     * @return 单条 JSON；本机未登录（没有 Cookie）时返回空串，调用方据此提示「还没有可导出的账号」
     */
    public static String exportCurrentAccount() {
        String cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        if (cookies == null || cookies.trim().isEmpty()) return "";
        try {
            JSONObject json = new JSONObject();
            json.put("cookies", cookies);
            json.put("refresh_token", SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, ""));
            json.put("access_key", SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, ""));
            return json.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 导出所有账号为一份 JSON 文本（多账号格式，见 {@link SpecialLoginParser}）。
     *
     * <p>当前账号用 SP 里的**实时**凭证覆盖快照：快照只有 refresh_token 轮换后才同步，
     * 而 SESSDATA 刷新不会触发同步，直接导快照会导出一份可能已经过期的 Cookie。
     *
     * @return 导出的 JSON；没有任何账号时返回空串
     */
    public static String exportAllAccounts() {
        synchronized (lock) {
            List<AccountInfo> accounts = getAccounts();
            long currentMid = getCurrentMid();
            String currentCookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
            String currentRefresh = SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "");
            String currentAccessKey = SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, "");

            if (currentMid > 0 && !currentCookies.isEmpty()) {
                boolean merged = false;
                for (AccountInfo account : accounts) {
                    if (account.mid != currentMid) continue;
                    account.cookies = currentCookies;
                    account.refreshToken = currentRefresh;
                    account.accessKey = currentAccessKey;
                    account.csrf = NetWorkUtil.currentCsrf();
                    merged = true;
                    break;
                }
                if (!merged) {
                    // 登录了但列表里还没有这条（例如刚登录、快照还没同步）
                    accounts.add(new AccountInfo(currentMid, "UID:" + currentMid, currentCookies,
                            currentRefresh, currentAccessKey, NetWorkUtil.currentCsrf(), ""));
                }
            }

            if (accounts.isEmpty()) return "";

            try {
                JSONObject root = new JSONObject();
                root.put("format", SpecialLoginParser.EXPORT_FORMAT);
                root.put("version", SpecialLoginParser.EXPORT_VERSION);
                root.put("exported_at", System.currentTimeMillis() / 1000L);
                JSONArray array = new JSONArray();
                for (AccountInfo account : accounts) {
                    if (account.isValid()) array.put(account.toJson());
                }
                root.put("accounts", array);
                return root.toString();
            } catch (JSONException e) {
                e.printStackTrace();
                return "";
            }
        }
    }

    /**
     * 批量导入账号：只写账号列表，**不切换当前登录账号**（导入动作不该把用户正在用的账号踢掉）。
     *
     * <p>同一个 mid 已存在时用导入数据**整体替换**它的 Cookie / refresh_token / access_key
     * （导入方认为自己的更新，这是换设备搬家的语义）；昵称/头像为空的条目保留本地已有的，
     * 避免导入一份干净 JSON 后列表里全变成 "UID:xxx"。
     *
     * <p>不做网络请求，可以在后台线程调用。
     */
    public static ImportResult importAccounts(List<AccountInfo> incoming) {
        ImportResult result = new ImportResult();
        if (incoming == null || incoming.isEmpty()) return result;

        synchronized (lock) {
            List<AccountInfo> accounts = getAccounts();
            Set<Long> seen = new HashSet<>();
            for (AccountInfo candidate : incoming) {
                if (candidate == null || !candidate.isValid()) {
                    result.skipped++;
                    continue;
                }
                // 防串行：mid 与 Cookie 必须自洽（解析层已校验，这里是最后一道）
                String cookieMid = NetWorkUtil.getInfoFromCookie("DedeUserID", candidate.cookies);
                if (!cookieMid.isEmpty() && !cookieMid.equals(String.valueOf(candidate.mid))) {
                    result.skipped++;
                    continue;
                }
                if (!seen.add(candidate.mid)) {
                    // 同一次导入里同一个 mid 出现多次，以前一条为准（解析层已按"后者优先"去过重）
                    result.skipped++;
                    continue;
                }

                candidate.csrf = NetWorkUtil.pickCsrf(candidate.cookies, candidate.csrf);
                if (candidate.name == null || candidate.name.isEmpty()) candidate.name = "UID:" + candidate.mid;

                int index = indexOfMid(accounts, candidate.mid);
                if (index >= 0) {
                    AccountInfo existing = accounts.get(index);
                    existing.cookies = candidate.cookies;
                    existing.refreshToken = candidate.refreshToken;
                    existing.accessKey = candidate.accessKey;
                    existing.csrf = candidate.csrf;
                    if (candidate.name != null && !candidate.name.startsWith("UID:")) existing.name = candidate.name;
                    if (candidate.avatar != null && !candidate.avatar.isEmpty()) existing.avatar = candidate.avatar;
                    result.updated++;
                } else {
                    accounts.add(candidate);
                    result.added++;
                }
            }
            if (result.total() > 0) saveAccounts(accounts);
        }
        return result;
    }

    private static int indexOfMid(List<AccountInfo> accounts, long mid) {
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).mid == mid) return i;
        }
        return -1;
    }

    public static boolean isCurrentAccount(long mid) {
        long currentMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        return currentMid == mid;
    }

    public static long getCurrentMid() {
        return SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
    }

    public static boolean hasAccounts() {
        return !getAccounts().isEmpty();
    }

    public static int getAccountCount() {
        return getAccounts().size();
    }
}
