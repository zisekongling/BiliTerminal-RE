package com.RobinNotBad.BiliClient.api;

import android.util.Log;
import android.util.Pair;

import com.RobinNotBad.BiliClient.model.SubscriptionFolder;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
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
    private static final String URL_UNFAV_FOLDER = "https://api.bilibili.com/x/v3/fav/folder/unfav";
    private static final String URL_UNFAV_SEASON = "https://api.bilibili.com/x/fav/season/unfav";

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

    /**
     * 取消订阅一个收藏夹。
     *
     * <p><b>参数名两个口径：</b>B 站 Web 文档写的是 {@code media_ids}（复数，可逗号分隔），
     * 而 PiliPlus 的实现发的是 {@code media_id}（单数）。这里先按文档发复数，只有服务端
     * 明确报参数错误（-400）时才用单数重试一次——不要无脑双发，那会多一倍写请求。
     */
    public static int cancelFolderSubscription(long mediaId) throws IOException, JSONException {
        int code = postUnfavFolder(mediaId, "media_ids");
        if (code == -400) {
            int retry = postUnfavFolder(mediaId, "media_id");
            if (retry == 0) return 0;
        }
        return code;
    }

    private static int postUnfavFolder(long mediaId, String paramName) throws IOException, JSONException {
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .put(paramName, String.valueOf(mediaId))
                .put("platform", "web")
                .put("csrf", NetWorkUtil.currentCsrf());
        JSONObject result = new JSONObject(Objects.requireNonNull(
                NetWorkUtil.post(URL_UNFAV_FOLDER, formData.toString(), NetWorkUtil.webHeaders).body()).string());
        Log.d("debug-取消订阅收藏夹", result.toString());
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
}
