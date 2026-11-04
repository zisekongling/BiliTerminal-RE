package com.RobinNotBad.BiliClient.api;

import android.util.Log;
import android.util.Pair;

import com.RobinNotBad.BiliClient.model.SubscriptionFolder;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 「我的订阅」——我订阅的收藏夹与合集。
 *
 * <p>与收藏夹（{@link FavoriteApi}）的区别：这里读的是「<b>别人创建、我订阅</b>」的条目。
 * 列表接口 {@code x/v3/fav/folder/collected/list} 必须带 {@code platform=web}，
 * 否则只返回收藏夹、不返回合集（见 bilibili-API/docs/fav/info.md）。
 *
 * <p>全部是 Web 接口，用 Cookie 鉴权，<b>不需要 APP 签名</b>；写操作（取消订阅）
 * 需要 {@code csrf}（即 cookie 里的 bili_jct），由 {@link NetWorkUtil#currentCsrf()} 提供。
 *
 * <p>解析逻辑都拆成了可单独调用的纯函数（{@link #parseSubscribedFolders}、
 * {@link #parseSeasonVideos}），不碰网络，方便 JVM 单测——接口字段缺失/类型不对时
 * 一律跳过该条而不是整页失败，服务端偶发脏数据不能让整页白屏。
 */
public class SubscriptionApi {

    public static final int TYPE_FAV_FOLDER = SubscriptionFolder.TYPE_FAV_FOLDER;
    public static final int TYPE_SEASON = SubscriptionFolder.TYPE_SEASON;

    /** 列表与详情统一每页 20 条：与收藏夹列表一致，手表弱网下翻页次数也不会太多。 */
    public static final int PS = 20;

    private static final String URL_COLLECTED = "https://api.bilibili.com/x/v3/fav/folder/collected/list";
    private static final String URL_SEASON_LIST = "https://api.bilibili.com/x/space/fav/season/list";
    private static final String URL_FOLDER_INFO = "https://api.bilibili.com/x/v3/fav/folder/info";
    private static final String URL_FAV_FOLDER = "https://api.bilibili.com/x/v3/fav/folder/fav";
    private static final String URL_UNFAV_FOLDER = "https://api.bilibili.com/x/v3/fav/folder/unfav";
    private static final String URL_FAV_SEASON = "https://api.bilibili.com/x/v3/fav/season/fav";
    /**
     * 取消订阅合集。
     *
     * <p><b>注意前缀是 v3</b>：PiliPlus 的常量写的是 {@code /x/fav/season/unfav}（没有 v3），
     * 实测该路径 **HTTP 404**；正确路径是 {@code /x/v3/fav/season/unfav}（本机 2026-11-02 实测）。
     */
    private static final String URL_UNFAV_SEASON = "https://api.bilibili.com/x/v3/fav/season/unfav";

    /**
     * 拉一页「我的订阅」。
     *
     * @param mid 目标用户（自己的 mid）
     * @return (返回码, 是否还有下一页)
     */
    public static Pair<Integer, Boolean> getSubscribedFolders(
            long mid, int page, List<SubscriptionFolder> out) throws IOException, JSONException {
        String url = URL_COLLECTED + new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("platform", "web")
                .put("up_mid", mid)
                .put("pn", page)
                .put("ps", PS);
        JSONObject result = NetWorkUtil.getJson(url);
        int code = result.optInt("code", -1);
        boolean hasMore = false;
        JSONObject data = result.optJSONObject("data");
        if (code == 0 && data != null) {
            hasMore = parseSubscribedFolders(data, out);
        }
        return new Pair<>(code, hasMore);
    }

    /**
     * 解析 {@code collected/list} 的 {@code data} 对象（纯函数，可单测）。
     *
     * @return 是否还有下一页；{@code data} 里的 {@code has_more} 缺失时按「没有下一页」处理，
     * 宁可提前停止翻页，也不要无限请求。
     */
    public static boolean parseSubscribedFolders(JSONObject data, List<SubscriptionFolder> out) throws JSONException {
        if (data == null) return false;
        JSONArray list = data.optJSONArray("list");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) continue;
                long id = item.optLong("id", 0);
                // 没有 id 的条目点进去也打不开（收藏夹要 media_id、合集要 season_id），直接丢
                if (id <= 0) continue;
                SubscriptionFolder folder = new SubscriptionFolder();
                folder.id = id;
                folder.type = item.optInt("type", TYPE_FAV_FOLDER);
                folder.state = item.optInt("state", 0);
                folder.title = item.optString("title", "");
                folder.cover = item.optString("cover", "");
                folder.mediaCount = item.optInt("media_count", 0);
                folder.viewCount = item.optLong("view_count", 0);
                JSONObject upper = item.optJSONObject("upper");
                if (upper != null) folder.upperName = upper.optString("name", "");
                out.add(folder);
            }
        }
        return data.optBoolean("has_more", false);
    }

    /**
     * 拉一页「订阅的合集」里的视频。
     *
     * <p>为什么不用 {@code ugc_season}：那是「某个视频所属的 UP 主合集」，
     * 而订阅的合集是一个独立的 season_id，只能走 {@code x/space/fav/season/list}。
     *
     * @return (返回码, 合集总视频数)；总数为 0 表示接口没给 {@code info.media_count}，
     * 调用方应退化成「用返回条数是否满页判断还有没有下一页」。
     */
    public static Pair<Integer, Integer> getSeasonVideos(
            long seasonId, int page, List<VideoCard> out) throws IOException, JSONException {
        String url = URL_SEASON_LIST + new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("season_id", seasonId)
                .put("pn", page)
                .put("ps", PS);
        JSONObject result = NetWorkUtil.getJson(url);
        int code = result.optInt("code", -1);
        int total = 0;
        JSONObject data = result.optJSONObject("data");
        if (code == 0 && data != null) {
            total = parseSeasonVideos(data, out);
        }
        return new Pair<>(code, total);
    }

    /**
     * 解析 {@code season/list} 的 {@code data} 对象（纯函数，可单测）。
     *
     * @return 合集总视频数（{@code info.media_count}），缺失时为 0。
     */
    public static int parseSeasonVideos(JSONObject data, List<VideoCard> out) throws JSONException {
        if (data == null) return 0;
        String upName = "";
        int total = 0;
        JSONObject info = data.optJSONObject("info");
        if (info != null) {
            total = info.optInt("media_count", 0);
            JSONObject upper = info.optJSONObject("upper");
            if (upper != null) upName = upper.optString("name", "");
        }
        JSONArray medias = data.optJSONArray("medias");
        if (medias != null) {
            for (int i = 0; i < medias.length(); i++) {
                JSONObject item = medias.optJSONObject(i);
                if (item == null) continue;
                long aid = item.optLong("id", 0);
                // 合集里每一条都是独立视频，id 是 avid；没有 avid 的脏条目跳过
                if (aid <= 0) continue;
                VideoCard card = new VideoCard();
                card.title = item.optString("title", "");
                card.cover = item.optString("cover", "");
                card.aid = aid;
                card.bvid = item.optString("bvid", "");
                card.upName = upName;
                card.duration = item.optLong("duration", 0);
                JSONObject cntInfo = item.optJSONObject("cnt_info");
                if (cntInfo != null) {
                    int play = cntInfo.optInt("play", -1);
                    if (play >= 0) card.view = StringUtil.toWan(play);
                }
                out.add(card);
            }
        }
        return total;
    }

    // ==================== 添加 / 取消订阅 ====================

    /**
     * 订阅一个收藏夹（收藏别人公开的收藏夹）。
     *
     * <p>{@code POST /x/v3/fav/folder/fav}，参数 {@code media_id} + {@code csrf}。
     */
    public static int subscribeFolder(long mediaId) throws IOException, JSONException {
        return postFavFolder(URL_FAV_FOLDER, mediaId, "media_id", "debug-订阅收藏夹");
    }

    /**
     * 取消订阅一个收藏夹。
     *
     * <p><b>参数名两个口径：</b>B 站 Web 文档与 PiliPlus 的「取消订阅」主口径都是
     * {@code media_id}（单数），但 {@code folder/unfav} 的历史文档里也写过
     * {@code media_ids}（复数）。这里主发单数，只有服务端明确报参数错误（-400）
     * 才用复数重试一次——不要无脑双发，那会多一倍写请求。
     */
    public static int cancelFolderSubscription(long mediaId) throws IOException, JSONException {
        int code = postFavFolder(URL_UNFAV_FOLDER, mediaId, "media_id", "debug-取消订阅收藏夹");
        if (code == -400) {
            int retry = postFavFolder(URL_UNFAV_FOLDER, mediaId, "media_ids", "debug-取消订阅收藏夹");
            if (retry == 0) return 0;
        }
        return code;
    }

    private static int postFavFolder(String url, long mediaId, String paramName, String logTag)
            throws IOException, JSONException {
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .put(paramName, String.valueOf(mediaId))
                .put("csrf", NetWorkUtil.currentCsrf());
        JSONObject result = new JSONObject(Objects.requireNonNull(
                NetWorkUtil.post(url, formData.toString(), NetWorkUtil.webHeaders).body()).string());
        Log.d(logTag, result.toString());
        return result.optInt("code", -1);
    }

    /**
     * 订阅一个合集。
     *
     * <p>{@code POST /x/v3/fav/season/fav}，参数 {@code platform=web} + {@code season_id}
     * + {@code csrf}（与 PiliPlus 同口径）。
     */
    public static int subscribeSeason(long seasonId) throws IOException, JSONException {
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .put("platform", "web")
                .put("season_id", String.valueOf(seasonId))
                .put("csrf", NetWorkUtil.currentCsrf());
        JSONObject result = new JSONObject(Objects.requireNonNull(
                NetWorkUtil.post(URL_FAV_SEASON, formData.toString(), NetWorkUtil.webHeaders).body()).string());
        Log.d("debug-订阅合集", result.toString());
        return result.optInt("code", -1);
    }

    /**
     * 取消订阅一个合集。
     *
     * <p>{@code platform=web} 与 PiliPlus 的实现保持一致；缺它时部分账号会返回 -400。
     */
    public static int cancelSeasonSubscription(long seasonId) throws IOException, JSONException {
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .put("season_id", String.valueOf(seasonId))
                .put("platform", "web")
                .put("csrf", NetWorkUtil.currentCsrf());
        JSONObject result = new JSONObject(Objects.requireNonNull(
                NetWorkUtil.post(URL_UNFAV_SEASON, formData.toString(), NetWorkUtil.webHeaders).body()).string());
        Log.d("debug-取消订阅合集", result.toString());
        return result.optInt("code", -1);
    }

    // ==================== 当前是否已订阅 ====================

    /**
     * 查收藏夹的订阅状态。
     *
     * <p>{@code GET /x/v3/fav/folder/info?media_id=} 返回的 {@code data.fav_state}：
     * 1=已订阅该收藏夹，0=未订阅（bilibili-API/docs/fav/info.md:49）。未登录时 code=-101。
     *
     * @return (返回码, fav_state)
     */
    public static Pair<Integer, Integer> getFolderFavState(long mediaId) throws IOException, JSONException {
        String url = URL_FOLDER_INFO + new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("media_id", mediaId);
        JSONObject result = NetWorkUtil.getJson(url);
        int code = result.optInt("code", -1);
        int state = 0;
        JSONObject data = result.optJSONObject("data");
        if (code == 0 && data != null) state = parseFolderFavState(data);
        return new Pair<>(code, state);
    }

    /** 从 {@code folder/info} 的 data 里读订阅状态（纯函数，可单测）。 */
    public static int parseFolderFavState(JSONObject data) throws JSONException {
        if (data == null) return 0;
        return data.optInt("fav_state", 0);
    }

    /**
     * 查合集的订阅状态。
     *
     * <p>先读 {@code x/space/fav/season/list} 的 {@code data.info.fav_state}；但实测匿名请求
     * 的 `info` 里**没有这个字段**（只有 id/title/cover/upper/cnt_info/media_count/intro），
     * 登录态是否返回未验证。拿不到时回退翻「我的订阅」前 3 页（每页 20）找这个 season——
     * 绝大多数账号订阅数不到 60，代价可接受；找不到按未订阅处理。
     *
     * @return (返回码, fav_state)
     */
    public static Pair<Integer, Integer> getSeasonFavState(long seasonId) throws IOException, JSONException {
        String url = URL_SEASON_LIST + new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("season_id", seasonId)
                .put("pn", 1)
                .put("ps", 1);
        JSONObject result = NetWorkUtil.getJson(url);
        int code = result.optInt("code", -1);
        if (code != 0) return new Pair<>(code, 0);

        JSONObject data = result.optJSONObject("data");
        int state = data == null ? -1 : parseSeasonFavStateOrUnknown(data);
        if (state >= 0) return new Pair<>(0, state);

        // 服务端没给 fav_state：回退查「我的订阅」列表
        return new Pair<>(0, isInSubscriptions(TYPE_SEASON, seasonId) ? 1 : 0);
    }

    /** 读 {@code info.fav_state}；字段不存在返回 -1（用于区分「未订阅」和「服务端没给」）。 */
    private static int parseSeasonFavStateOrUnknown(JSONObject data) {
        if (data == null) return -1;
        JSONObject info = data.optJSONObject("info");
        if (info == null || !info.has("fav_state")) return -1;
        return info.optInt("fav_state", 0);
    }

    /**
     * 在「我的订阅」前 3 页里找某个收藏夹/合集是否已订阅。
     *
     * <p>只在服务器不返回 fav_state 时用（目前是合集详情）。未登录直接返回 false，
     * 请求失败也返回 false——宁可让按钮显示「订阅」让用户点一下得到服务端提示，
     * 也不要因为一次查询失败把「已订阅」误标成可订阅（反向的误标更糟）。
     */
    private static boolean isInSubscriptions(int type, long id) throws IOException, JSONException {
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (mid == 0) return false;
        for (int page = 1; page <= 3; page++) {
            List<SubscriptionFolder> list = new ArrayList<>();
            Pair<Integer, Boolean> res = getSubscribedFolders(mid, page, list);
            if (res.first != 0) return false;
            for (SubscriptionFolder folder : list) {
                if (folder.id == id && folder.type == type) return true;
            }
            if (!res.second) break;
        }
        return false;
    }

    /** 从 {@code space/fav/season/list} 的 data 里读 {@code info.fav_state}（纯函数，可单测）。 */
    public static int parseSeasonFavState(JSONObject data) throws JSONException {
        if (data == null) return 0;
        JSONObject info = data.optJSONObject("info");
        if (info == null) return 0;
        return info.optInt("fav_state", 0);
    }
}
