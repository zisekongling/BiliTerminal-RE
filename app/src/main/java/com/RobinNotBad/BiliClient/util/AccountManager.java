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
 * {@link #exportAllAccounts()} 把所有账号（含当前账号的最新凭证）打成一份 JSON；
 * {@link #importAccounts} 只写账号列表、**不动当前登录态**，所以导入不会把用户正在用的账号踢掉。
 */
public class AccountManager {

    private static final String ACCOUNT_LIST_KEY = "account_list";
    private static final String CURRENT_ACCOUNT_KEY = "current_account_mid";
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
            });
            // 必须在 SP 写完、且在锁内确定的顺序上"最后"发生：它升代际，把旧账号在飞的响应挡在门外。
            // 先升代际后写 Cookie 都不影响正确性——两者同在 NetWorkUtil 的锁里，不会插进一次旧响应。
            NetWorkUtil.applyAccountCookies(safeCookies);

            List<AccountInfo> accounts = getAccounts();
            boolean found = false;
            for (int i = 0; i < accounts.size(); i++) {
                AccountInfo info = accounts.get(i);
                if (info.mid != mid) continue;
                info.cookies = safeCookies;
                info.refreshToken = safeRefresh;
                info.accessKey = safeAccessKey;
                info.csrf = csrf;
                // 昵称/头像只在拿到新值时才覆盖，避免把已有昵称冲成 "UID:xxx"
                if (!safeName.isEmpty()) info.name = safeName;
                if (!safeAvatar.isEmpty()) info.avatar = safeAvatar;
                if (info.name == null || info.name.isEmpty()) info.name = "UID:" + mid;
                found = true;
                break;
            }
            if (!found) {
                accounts.add(new AccountInfo(mid, safeName.isEmpty() ? "UID:" + mid : safeName,
                        safeCookies, safeRefresh, safeAccessKey, csrf, safeAvatar));
            }
            saveAccounts(accounts);
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
