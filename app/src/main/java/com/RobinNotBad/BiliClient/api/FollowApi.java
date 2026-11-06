package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.R;

import com.RobinNotBad.BiliClient.BiliTerminal;

//关注api
//2023-08-27

import com.RobinNotBad.BiliClient.model.FollowTag;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


public class FollowApi {

    // ==================== 互关标记（批次 1，社交与信息） ====================

    /**
     * 纯函数：列表主体是不是当前登录的账号。
     *
     * <p>「互关」只有在对**自己的**关注/粉丝/分组列表里才等价于「他和我互相关注」；
     * 看别人的列表时，`attribute == 6` 表达的是「那个人与这一条互关」，对当前用户没有意义。
     */
    public static boolean isSelfList(long listOwnerMid, long currentMid) {
        return listOwnerMid > 0 && currentMid > 0 && listOwnerMid == currentMid;
    }

    /**
     * 纯函数：这一条是否该标「互关」。
     *
     * <p>B 站 `x/relation/*` 列表项的 `attribute`：1 已关注 / 2 被关注 / 6 互相关注。
     * 必须同时满足「自己的列表」与「attribute == 6」两个条件，缺一不可。
     */
    public static boolean isMutualFollow(boolean selfList, int attribute) {
        return selfList && attribute == 6;
    }

    /** 当前登录账号的 mid（未登录为 0）。抽出来只为让上面的纯函数不碰 SharedPreferences。 */
    private static long currentLoginMid() {
        return SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
    }

    public static int getFollowingList(long mid, int page, List<UserInfo> userList) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/relation/followings?vmid=" + mid + "&pn=" + page + "&ps=20&order=desc&order_type=attention";
        JSONObject callback = NetWorkUtil.getJson(url);
        if (callback.optInt("code", -1) != 0)
            throw new JSONException(callback.optInt("code", -1) + "：" + callback.optString("message", "未知API错误"));
        JSONObject data = callback.getJSONObject("data");
        JSONArray list = data.getJSONArray("list");
        if (list.length() == 0) return 1;
        else {
            boolean selfList = isSelfList(mid, currentLoginMid());
            for (int i = 0; i < list.length(); i++) {
                JSONObject userInfo = list.getJSONObject(i);
                String name = userInfo.getString("uname");
                long uid = userInfo.getLong("mid");
                String avatar = userInfo.getString("face");
                String sign = userInfo.getString("sign");
                UserInfo user = new UserInfo(uid, name, avatar, sign, 0, 0, 0, true, "", 0, "", userInfo.optLong("mtime", 0), 0);
                user.mutualFollow = isMutualFollow(selfList, userInfo.optInt("attribute", 0));
                userList.add(user);
            }
            return 0;
        }
    }

    public static int getFollowerList(long mid, int page, List<UserInfo> userList) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/relation/followers?vmid=" + mid + "&pn=" + page + "&ps=20&order=desc&order_type=attention";
        JSONObject callback = NetWorkUtil.getJson(url);
        if (callback.optInt("code", -1) != 0)
            throw new JSONException(callback.optInt("code", -1) + "：" + callback.optString("message", "未知API错误"));
        JSONObject data = callback.getJSONObject("data");
        JSONArray list = data.getJSONArray("list");
        if (list.length() == 0) return 1;
        else {
            boolean selfList = isSelfList(mid, currentLoginMid());
            for (int i = 0; i < list.length(); i++) {
                JSONObject userInfo = list.getJSONObject(i);
                String name = userInfo.getString("uname");
                long uid = userInfo.getLong("mid");
                String avatar = userInfo.getString("face");
                String sign = userInfo.getString("sign");
                UserInfo user = new UserInfo(uid, name, avatar, sign, 0, 0, 0, true, "", 0, "", userInfo.optLong("mtime", 0), 0);
                user.mutualFollow = isMutualFollow(selfList, userInfo.optInt("attribute", 0));
                userList.add(user);
            }
            return 0;
        }
    }

    public static List<FollowTag> getFollowTags() throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/relation/tags";
        JSONObject callback = NetWorkUtil.getJson(url);
        if (callback.optInt("code", -1) != 0)
            throw new JSONException(callback.optInt("code", -1) + "：" + callback.optString("message", "未知API错误"));
        JSONArray data = callback.getJSONArray("data");
        List<FollowTag> tagList = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject tag = data.getJSONObject(i);
            int tagid = tag.getInt("tagid");
            String name = tag.getString("name");
            int count = tag.getInt("count");
            tagList.add(new FollowTag(tagid, name, count));
        }
        return tagList;
    }

    public static int getFollowTagUsers(int tagid, int page, List<UserInfo> userList) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/relation/tag?tagid=" + tagid + "&pn=" + page + "&ps=20";
        JSONObject callback = NetWorkUtil.getJson(url);
        if (callback.optInt("code", -1) != 0)
            throw new JSONException(callback.optInt("code", -1) + "：" + callback.optString("message", "未知API错误"));
        JSONArray data = callback.getJSONArray("data");
        if (data.length() == 0) return 1;
        else {
            for (int i = 0; i < data.length(); i++) {
                JSONObject userInfo = data.getJSONObject(i);
                String name = userInfo.getString("uname");
                long uid = userInfo.getLong("mid");
                String avatar = userInfo.getString("face");
                String sign = userInfo.getString("sign");
                UserInfo user = new UserInfo(uid, name, avatar, sign, 0, 0, 0, true, "", 0, "", 0);
                // 分组列表只能查自己的（接口没有 vmid 参数），所以主体恒为当前账号
                user.mutualFollow = isMutualFollow(true, userInfo.optInt("attribute", 0));
                userList.add(user);
            }
            return 0;
        }
    }

    // ==================== 分组增删改（26.10.04 批次 7 的 C21） ====================

    /** 分组名最长 16 字符（服务端限制，超了会回 22103） */
    public static final int TAG_NAME_MAX_LENGTH = 16;

    /**
     * 分组名的本地预校验（不联网）。
     *
     * @return 空串表示通过，否则是要展示给用户的中文提示
     */
    public static String checkTagName(String name) {
        if (name == null) return "分组名不能为空";
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "分组名不能为空";
        if (trimmed.length() > TAG_NAME_MAX_LENGTH) return "分组名最多 " + TAG_NAME_MAX_LENGTH + " 个字";
        return "";
    }

    /**
     * 创建关注分组。
     *
     * @return 服务端 code（0 成功）
     */
    public static int createFollowTag(String name) throws IOException, JSONException {
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .put("tag", name == null ? "" : name.trim())
                .put("csrf", NetWorkUtil.currentCsrf());
        return postTag("https://api.bilibili.com/x/relation/tag/create", formData);
    }

    /**
     * 重命名关注分组。
     *
     * @return 服务端 code（0 成功）
     */
    public static int renameFollowTag(int tagid, String name) throws IOException, JSONException {
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .put("tagid", tagid)
                .put("name", name == null ? "" : name.trim())
                .put("csrf", NetWorkUtil.currentCsrf());
        return postTag("https://api.bilibili.com/x/relation/tag/update", formData);
    }

    /**
     * 删除关注分组（分组里的关注不会被取关，只是回到默认分组）。
     *
     * @return 服务端 code（0 成功）
     */
    public static int deleteFollowTag(int tagid) throws IOException, JSONException {
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .put("tagid", tagid)
                .put("csrf", NetWorkUtil.currentCsrf());
        return postTag("https://api.bilibili.com/x/relation/tag/del", formData);
    }

    /**
     * 把关注的人从一个分组移动到另一个分组（26.10.04 批次 7 的 C22）。
     *
     * 对应 `POST https://api.bilibili.com/x/relation/tags/moveUsers`，
     * 参数 `beforeTagids`（原分组 id）/ `afterTagids`（新分组 id）/ `fids`（用户 mid 列表，逗号分隔）。
     *
     * 注意：这里是**移动**，不是取关，也不是「复制到多个分组」（那是 tags/addUsers）。
     * 想把人移出分组而不取关，就把 [afterTagid] 传 0（默认分组）。
     *
     * @param beforeTagid 原分组 id（当前所在分组）
     * @param afterTagid  新分组 id；0 表示默认分组
     * @param fids        待移动的用户 mid 列表
     * @return 服务端 code（0 成功）
     */
    public static int moveFollowTagUsers(int beforeTagid, int afterTagid, List<Long> fids) throws IOException, JSONException {
        if (fids == null || fids.isEmpty()) return -400;
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData();
        for (Map.Entry<String, String> entry : moveUsersFields(beforeTagid, afterTagid, fids, NetWorkUtil.currentCsrf()).entrySet()) {
            formData.put(entry.getKey(), entry.getValue());
        }
        return postTag("https://api.bilibili.com/x/relation/tags/moveUsers", formData);
    }

    /**
     * 组装 `tags/moveUsers` 的请求字段（纯函数，便于 JVM 单测）。
     *
     * 抽出来是为了**能断言字段名**：`beforeTagids`/`afterTagids`/`fids` 这三个名字是本接口最容易
     * 写错又最难在真机上发现的坑（服务端对错字段名一律回 -400，看起来像「请求出错」）。
     * 注意接口用的是**复数** `beforeTagids`/`afterTagids`（单分组也传单值）。
     * 抽成 `Map` 而非 `FormData` 是为了不把 `NetWorkUtil`（依赖 Android）拖进单测。
     *
     * @return 字段名 → 字段值（顺序不保证）
     */
    public static Map<String, String> moveUsersFields(int beforeTagid, int afterTagid, List<Long> fids, String csrf) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("beforeTagids", String.valueOf(beforeTagid));
        fields.put("afterTagids", String.valueOf(afterTagid));
        fields.put("fids", joinMids(fids));
        fields.put("csrf", csrf == null ? "" : csrf);
        return fields;
    }

    /**
     * 查询某个用户当前在哪些关注分组里（反馈 #88 的弹窗要标出「已加入」）。
     *
     * 对应 `GET https://api.bilibili.com/x/relation/tag/user?fid=`，
     * 响应 `data` 是「分组 id → 分组名」的对象，且**只在用户不在默认分组时才出现**；
     * 特别关注是 `tagid = -10`，调用方一般只处理 `tagid > 0` 的自建分组。
     *
     * @return 分组列表（count 恒为 0，这个接口不返回人数）；接口失败抛异常
     */
    public static List<FollowTag> getUserFollowTags(long fid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/relation/tag/user?fid=" + fid;
        JSONObject callback = NetWorkUtil.getJson(url);
        if (callback.optInt("code", -1) != 0)
            throw new JSONException(callback.optInt("code", -1) + "：" + callback.optString("message", "未知API错误"));
        List<FollowTag> tagList = new ArrayList<>();
        JSONObject data = callback.optJSONObject("data");
        if (data == null) return tagList;
        Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                tagList.add(new FollowTag(Integer.parseInt(key), data.optString(key, ""), 0));
            } catch (NumberFormatException ignored) {
                // 键不是数字（理论上不会发生）：跳过而不是让整个弹窗失效
            }
        }
        return tagList;
    }

    /**
     * 把一个已关注的人**加入**若干关注分组（反馈 #88）。
     *
     * 对应 `POST https://api.bilibili.com/x/relation/tags/addUsers`，参数是
     * `tagids`（逗号分隔的分组 id）与 `fids`（逗号分隔的用户 mid）。
     * 与 {@link #moveFollowTagUsers} 的区别：这里是**追加**，不会把他从原分组里移走，
     * 也不会取消关注——正是「长按已关注 → 加入分组」要的语义。
     *
     * @param tagids 目标分组 id（只该传 tagid > 0 的自建分组）
     * @param fid    目标用户 mid
     * @return 服务端 code（0 成功）
     */
    public static int addFollowTagUsers(List<Integer> tagids, long fid) throws IOException, JSONException {
        if (tagids == null || tagids.isEmpty() || fid <= 0) return -400;
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData();
        Map<String, String> fields = addUsersFields(tagids, Collections.singletonList(fid), NetWorkUtil.currentCsrf());
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            formData.put(entry.getKey(), entry.getValue());
        }
        return postTag("https://api.bilibili.com/x/relation/tags/addUsers", formData);
    }

    /**
     * 组装 `tags/addUsers` 的请求字段（纯函数，便于 JVM 单测）。
     *
     * 与 {@link #moveUsersFields} 一样抽出来是为了**钉死字段名**：这里用的是复数
     * `tagids` / `fids`，写错只会在真机上收到一个 -400，看起来完全不像拼写问题。
     *
     * @return 字段名 → 字段值（顺序不保证）
     */
    public static Map<String, String> addUsersFields(List<Integer> tagids, List<Long> fids, String csrf) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("tagids", joinTagIds(tagids));
        fields.put("fids", joinMids(fids));
        fields.put("csrf", csrf == null ? "" : csrf);
        return fields;
    }

    /**
     * 把分组 id 列表拼成接口要的逗号分隔形式（纯函数，便于 JVM 单测）。
     */
    public static String joinTagIds(List<Integer> tagids) {
        if (tagids == null || tagids.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tagids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(tagids.get(i));
        }
        return sb.toString();
    }

    /**
     * 把 mid 列表拼成接口要的逗号分隔形式（纯函数，便于 JVM 单测）。
     */
    public static String joinMids(List<Long> fids) {
        if (fids == null || fids.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(fids.get(i));
        }
        return sb.toString();
    }

    private static int postTag(String url, NetWorkUtil.FormData formData) throws IOException, JSONException {
        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, formData.toString(), NetWorkUtil.webHeaders).body()).string());
        return result.optInt("code", -1);
    }

    /** 分组增删改的错误码文案（纯函数，供 UI 直接显示） */
    public static String tagErrorMsg(int code) {
        switch (code) {
            case 0:
                return "";
            case -101:
                return ApiErrorText.loginRequired();
            case -111:
                return ApiErrorText.tokenExpired();
            case -400:
                return ApiErrorText.requestIllegal();
            case 22101:
                return "分组名里有不允许的字符";
            case 22102:
                return "分组数量已达上限";
            case 22103:
                return "分组名太长了，最多 " + TAG_NAME_MAX_LENGTH + " 个字";
            case 22104:
                return "这个分组不存在，可能已经被删了";
            case 22105:
                return "你还没有关注这个人";
            case 22106:
                return "已经有同名的分组了";
            default:
                return ApiErrorText.opFailed(code);
        }
    }
}
