package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Response;

public class HistoryApi {

    //观看记录里可能混有直播/专栏等其它业务，这两类之外的一律不展示，保持与旧实现(type=archive)一致的可见范围
    private static final String BUSINESS_ARCHIVE = "archive";
    private static final String BUSINESS_PGC = "pgc";
    //观看记录翻页定位时最多翻几页，避免脏游标导致死循环
    private static final int LOCATE_MAX_PAGES = 5;

    /**
     * "已看完"的进度约定值。
     *
     * <p>番剧心跳接口用 progress=-1 表示这一集已经看完，服务端据此把它标成"看完"
     * （下一集才能被当作"上次看到的那一集"的后续）。报真实位置（≈时长）只会得到"看到最后几秒"。
     */
    public static final long PROGRESS_FINISHED = -1L;

    /**
     * 上传历史记录
     *
     * @param aid      视频aid
     * @param cid      分集cid
     * @param progress 观看进度，单位为s
     * @throws IOException
     */
    public static void reportHistory(long aid, long cid, long progress) throws IOException {
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PRIVACY_MODE, false)) return;
        String url = "https://api.bilibili.com/x/v2/history/report";
        String csrf = NetWorkUtil.currentCsrf();
        if (csrf.isEmpty()) Logu.e("history-report", "csrf 为空，上报必被服务端拒绝(-111)，请确认已登录");
        String per = "aid=" + aid + "&cid=" + cid
                + "&progress=" + (progress >= 0 ? progress : "")
                + "&platform=pc"
                + "&csrf=" + csrf;
        Response response = NetWorkUtil.post(url, per, NetWorkUtil.webHeaders);
        logReportResult("reportHistory", response, "aid=" + aid + " cid=" + cid + " progress=" + progress);
    }

    //番剧(PGC)的观看进度必须走心跳接口：x/v2/history/report 只有 aid/cid 维度，
    //没有 epid/sid/type/sub_type，拿它上报不会被记成番剧记录——
    //表现就是"在终端里看的番剧，观看记录与进度都不更新，续播和详情页定位都拿不到数据"。
    private static final String HEARTBEAT_URL = "https://api.bilibili.com/x/click-interface/web/heartbeat";
    //type：3 投稿视频 / 4 剧集 / 10 课程
    private static final int HEARTBEAT_TYPE_SEASON = 4;
    //sub_type 取值与 season_type 一致；不在集合内就不发该字段，交给服务端按 epid/sid 判定
    private static final int[] HEARTBEAT_SUB_TYPES = {1, 2, 3, 4, 5, 7};

    /**
     * 上报番剧剧集的观看进度（番剧专用，投稿视频用 {@link #reportHistory}）。
     *
     * @param epid       剧集 epid（即 Bangumi.Episode#id）
     * @param seasonId   番剧 season_id
     * @param seasonType 剧集副类型（Bangumi.Info#type）
     * @param progress   已观看秒数；{@link #PROGRESS_FINISHED}(-1) 表示已看完
     */
    public static void reportHistoryPgc(long aid, long cid, long epid, long seasonId, int seasonType, long progress) throws IOException {
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PRIVACY_MODE, false)) return;
        //0 的上报零信息量且有害：服务端对本季"最近观看"按季粒度维护，
        //报 0 会把本季记录覆盖成"该集 + 0 进度"，详情页"定位上次观看分集"会因此失效。
        //-1(PROGRESS_FINISHED) 是"已看完"这个有信息的终态，必须放行
        if (progress == 0) {
            Logu.d("history-report", "跳过 0 进度上报 epid=" + epid);
            return;
        }
        String csrf = NetWorkUtil.currentCsrf();
        if (csrf.isEmpty()) Logu.e("history-report", "csrf 为空，上报必被服务端拒绝(-111)，请确认已登录");

        long nowSec = System.currentTimeMillis() / 1000;
        NetWorkUtil.FormData form = new NetWorkUtil.FormData()
                .put("aid", aid)
                .put("cid", cid)
                .put("epid", epid)
                .put("sid", seasonId)
                .put("mid", currentMid())
                .put("type", HEARTBEAT_TYPE_SEASON)
                .put("played_time", progress)
                //接口文档明确说明这几个"持续时间"字段算不准时可以都填同一个值
                .put("realtime", progress)
                .put("real_played_time", progress)
                .put("last_play_progress_time", progress)
                .put("max_play_progress_time", progress)
                //start_ts 是"开始播放时刻"：已知看了 progress 秒，倒推大致起点比直接填当前时间更接近语义。
                //下界必须夹到 0：设备时钟偏慢时倒推结果会变负数，服务端直接判参数错误(-400)
                .put("start_ts", Math.max(0, nowSec - progress))
                .put("play_type", 0)   //0 播放中
                .put("dt", 2)
                .put("outer", 0)
                .put("csrf", csrf);
        if (isKnownSeasonType(seasonType)) form.put("sub_type", seasonType);

        Response response = NetWorkUtil.post(HEARTBEAT_URL, form.toString(), NetWorkUtil.webHeaders);
        logReportResult("reportHistoryPgc", response,
                "aid=" + aid + " cid=" + cid + " epid=" + epid + " sid=" + seasonId
                        + " sub_type=" + seasonType + " progress=" + progress);
    }

    /** 与 {@link NetWorkUtil#currentCsrf()} 同理：mid 也可能因换设备/刷新 Cookie 而与实时 Cookie 不一致。 */
    private static long currentMid() {
        String midStr = NetWorkUtil.getInfoFromCookie("DedeUserID",
                SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
        if (midStr != null && !midStr.isEmpty()) {
            try {
                return Long.parseLong(midStr);
            } catch (NumberFormatException ignored) {
                //Cookie 形态异常时退回本地记录，不能让上报直接失败
            }
        }
        return SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
    }

    private static boolean isKnownSeasonType(int seasonType) {
        for (int t : HEARTBEAT_SUB_TYPES) {
            if (t == seasonType) return true;
        }
        return false;
    }

    /**
     * 历史/进度上报是"静默失败"成本最高的调用之一，返回码必须落日志，
     * 否则 -101(未登录)/-111(csrf 失效)/-400(请求错误) 之类的失败无从排查。
     */
    private static void logReportResult(String tag, Response response, String detail) throws IOException {
        try {
            if (response == null || response.body() == null) {
                Logu.e(tag, "无返回体 " + detail);
                return;
            }
            //peekBody 只读取一份副本，不会消费原 body，调用方后续仍可读
            String body = response.peekBody(1024 * 1024).string();
            Logu.d(tag, body);
            int code = new JSONObject(body).optInt("code", -1);
            if (code != 0) Logu.e(tag, "上报失败 code=" + code + " " + detail);
        } catch (JSONException e) {
            Logu.e(tag, "上报返回解析失败 " + detail);
        }
    }

    /**
     * 获取视频历史记录
     *
     * @param lastResult 上一次获取返回的ApiResult，如果是第一次就传入新对象
     * @param videoList  已有的视频列表
     * @return 新的ApiResult，包含了返回码、文本信息以及翻页所需的offset
     * @throws IOException
     * @throws JSONException
     */
    public static ApiResult getHistory(ApiResult lastResult, List<VideoCard> videoList) throws IOException, JSONException {
        //type=archive 只会返回投稿视频，番剧(business=pgc)必须用 type=all 才会出现在 list 中
        String url = "https://api.bilibili.com/x/web-interface/history/cursor?type=all&view_at=" + lastResult.timestamp + "&business=" + lastResult.business + "&max=" + lastResult.offset;
        JSONObject result = NetWorkUtil.getJson(url);
        ApiResult apiResult = new ApiResult(result);
        if (!result.isNull("data")) {
            JSONObject data = result.getJSONObject("data");
            JSONArray list = data.getJSONArray("list");
            for (int i = 0; i < list.length(); i++) {
                JSONObject videoCard = list.getJSONObject(i);
                String cover = videoCard.optString("cover", "");
                String upName = videoCard.optString("author_name", "");
                int progress = videoCard.optInt("progress", 0);

                JSONObject history = videoCard.optJSONObject("history");
                //business 优先取 history.business，其次顶层，都没有时按投稿视频处理
                String business = (history != null && !history.isNull("business"))
                        ? history.optString("business", BUSINESS_ARCHIVE)
                        : videoCard.optString("business", BUSINESS_ARCHIVE);
                //改用 type=all 后列表里会混入直播/专栏等业务，这类不展示，保持与旧实现一样的可见范围
                if (!BUSINESS_ARCHIVE.equals(business) && !BUSINESS_PGC.equals(business)) continue;

                long aid = history != null ? history.optLong("oid", 0) : videoCard.optLong("oid", 0);
                String bvid = history != null ? history.optString("bvid", "") : "";
                String title = videoCard.optString("title", "");
                if (BUSINESS_PGC.equals(business)) {
                    String longTitle = videoCard.optString("long_title", "");
                    //番剧标题在历史里被拆成 title + long_title(第几话)，不拼起来就分不清看的是哪一集
                    if (!longTitle.isEmpty()) title = title + " " + longTitle;
                }
                long viewAt = videoCard.optLong("view_at", 0);

                String viewStr;
                if (progress == 0) viewStr = "还没看过";
                else viewStr = "看到" + StringUtil.toTime(progress);

                VideoCard card = new VideoCard(title, upName, viewStr, cover, aid, bvid);
                card.viewAt = viewAt;
                if (BUSINESS_PGC.equals(business)) {
                    card.type = "media_bangumi";
                    //番剧卡片必须带上 epid：aid(history.oid) 是剧集 avid 而不是 media_id，
                    //详情页要先用 epid 反查 media_id，再靠它在剧集列表里定位上次观看的那一集
                    card.epid = history != null ? history.optLong("epid", 0) : 0;
                    //progress 的单位是秒（接口如此，viewStr 里也是按秒格式化的），
                    //VideoCard 里一律存秒；换算成毫秒只在传给播放器时做一次
                    card.progress = progress;
                }
                videoList.add(card);
            }
            if (list.length() == 0) apiResult.isBottom = true;

            JSONObject cursor = data.getJSONObject("cursor");
            apiResult.business = cursor.optString("business");
            apiResult.offset = cursor.optLong("max");
            apiResult.timestamp = cursor.optLong("view_at");
        }
        return apiResult;
    }

    public static int deleteHistory(long aid, String bvid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/history/delete";
        String per = "kid=archive_" + aid + "&csrf=" + NetWorkUtil.currentCsrf();
        JSONObject result = new JSONObject(NetWorkUtil.post(url, per, NetWorkUtil.webHeaders).body().string());
        return result.getInt("code");
    }

    // ==================== 观看记录多选删除（批次 1，社交与信息） ====================

    /**
     * 多选删除一次最多提交多少条。
     *
     * <p>上限不是服务端限制（删除接口本来就是单条的），而是别让一次点击排出几百个串行请求：
     * 手表弱网下要跑几分钟，用户会以为卡死。超过就只取前 N 条，由调用方提示。
     */
    public static final int MAX_BATCH_DELETE = 100;

    /** 纯函数：勾选数量是否超过批量上限（超了要提示 {@code 一次最多删除 N 条}）。 */
    public static boolean exceedsBatchLimit(int checkedCount) {
        return checkedCount > MAX_BATCH_DELETE;
    }

    /** 纯函数：是否已全选。空数组不算全选，避免「0 条已全选」这种自相矛盾的状态。 */
    public static boolean isAllSelected(boolean[] checked) {
        if (checked == null || checked.length == 0) return false;
        for (boolean value : checked) {
            if (!value) return false;
        }
        return true;
    }

    /** 纯函数：勾选数量。 */
    public static int countChecked(boolean[] checked) {
        if (checked == null) return 0;
        int count = 0;
        for (boolean value : checked) {
            if (value) count++;
        }
        return count;
    }

    /**
     * 纯函数：把已加载的观看记录整理成「可多选快照」。
     *
     * <p>三条规则：按 `aid` 去重（历史里同一稿件可能出现多次，删一次就够）、跳过 `aid<=0`
     * 的脏条目（删不掉还会让计数对不上）、最多 [limit] 条（见 {@link #MAX_BATCH_DELETE}）。
     */
    public static List<VideoCard> buildSelectableSnapshot(List<VideoCard> loaded, int limit) {
        List<VideoCard> snapshot = new ArrayList<>();
        if (loaded == null || limit <= 0) return snapshot;
        Set<Long> seen = new HashSet<>();
        for (VideoCard card : loaded) {
            if (card == null || card.aid <= 0) continue;
            if (!seen.add(card.aid)) continue;
            snapshot.add(card);
            if (snapshot.size() >= limit) break;
        }
        return snapshot;
    }

    /** 纯函数：勾选状态 → 待删除的 aid 列表（保持快照顺序）。 */
    public static List<Long> collectSelectedAids(List<VideoCard> snapshot, boolean[] checked) {
        List<Long> aids = new ArrayList<>();
        if (snapshot == null || checked == null) return aids;
        int size = Math.min(snapshot.size(), checked.length);
        for (int i = 0; i < size; i++) {
            if (!checked[i]) continue;
            VideoCard card = snapshot.get(i);
            if (card != null && card.aid > 0) aids.add(card.aid);
        }
        return aids;
    }

    /**
     * 批量删除：逐条复用现有的单条删除接口，**不新增写接口**。
     *
     * <p>单条失败不中断整批——一条被风控挡下不该让其余记录也删不掉；失败只记日志，
     * 由调用方用「成功条数 / 失败条数」汇总提示用户。
     *
     * @param aids 待删除的稿件 aid（调用方先用 {@link #collectSelectedAids} 收好）
     * @return **成功删除**的 aid 列表（UI 据此从本地列表移除，不必整页重拉）
     */
    public static List<Long> deleteHistoryBatch(List<Long> aids) {
        List<Long> succeeded = new ArrayList<>();
        if (aids == null) return succeeded;
        int limit = Math.min(aids.size(), MAX_BATCH_DELETE);
        for (int i = 0; i < limit; i++) {
            Long aid = aids.get(i);
            if (aid == null || aid <= 0) continue;
            try {
                // 单条接口只认 aid（拼成 kid=archive_<aid>），bvid 形参不参与拼参
                if (deleteHistory(aid, "") == 0) succeeded.add(aid);
            } catch (Exception e) {
                Logu.e("history-delete", "批量删除失败 aid=" + aid + " " + e.getMessage());
            }
        }
        return succeeded;
    }

    /**
     * 从观看记录里取某个稿件/剧集最近一次的播放进度（毫秒），作为续播进度的兜底来源。
     *
     * 为什么需要兜底：番剧续播进度走 x/player/wbi/v2，该接口需要 WBI 签名和登录态，
     * 一旦密钥异常/被风控/未登录就静默返回 0，表现为"续播永远从 0 开始"；
     * 而观看记录列表接口不需要 WBI，只要登录过就能拿到 progress（秒），可靠性更高。
     *
     * @param aid 稿件/剧集 aid（即观看记录里的 history.oid）
     * @return 毫秒；未登录、无记录或进度为 0/-1（已看完）时返回 0
     */
    public static long findProgressMsByAid(long aid) {
        return findProgressMsByAid(aid, 0);
    }

    /**
     * 同上，但额外要求观看记录里的 epid 也一致（番剧续播用）。
     *
     * <p>番剧观看记录里 oid 就是剧集 avid，正常情况下按 aid 已能锁定那一集；
     * 但"同一部番的 aid 在历史里被别的集复用/串号"时只按 aid 匹配会拿到别的集的进度，
     * 所以调用方手上有 epid 时（详情页与历史卡片都带）要多校验一层。
     *
     * @param epid 剧集 epid；0 表示不校验（投稿视频没有 epid）
     */
    public static long findProgressMsByAid(long aid, long epid) {
        if (aid == 0) return 0;
        //未登录时没有观看记录，直接跳过，避免发无谓请求
        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0) return 0;

        long viewAt = 0, max = 0;
        String business = "";
        try {
            for (int page = 1; page <= LOCATE_MAX_PAGES; page++) {
                String url = "https://api.bilibili.com/x/web-interface/history/cursor?type=all&view_at=" + viewAt
                        + "&business=" + business + "&max=" + max;
                JSONObject result = NetWorkUtil.getJson(url);
                if (result.isNull("data")) break;
                JSONObject data = result.getJSONObject("data");
                JSONArray list = data.optJSONArray("list");
                if (list == null) break;

                long progress = parseHistoryProgressMs(list, aid, epid);
                if (progress > 0) return progress;

                JSONObject cursor = data.optJSONObject("cursor");
                if (list.length() == 0 || cursor == null) break;
                long nextViewAt = cursor.optLong("view_at", 0);
                long nextMax = cursor.optLong("max", 0);
                String nextBusiness = cursor.optString("business", "");
                //游标没有前进说明已经没有更多数据，必须跳出，否则会无限翻页
                if (nextViewAt == viewAt && nextMax == max && nextBusiness.equals(business)) break;
                viewAt = nextViewAt;
                max = nextMax;
                business = nextBusiness;
            }
        } catch (Exception e) {
            Logu.e("history-locate", "兜底查询观看记录失败: " + e.getMessage());
        }
        return 0;
    }

    /**
     * 纯解析：在一页观看记录里找 aid（epid 非 0 时还要求 epid 相同）对应的播放进度，单位毫秒。
     *
     * <p>抽成纯函数是为了能被 JVM 单测覆盖——"按集身份校验"正是防串集的那道闸门，
     * 光靠真机很难复现"另一集的进度被套到这一集"的场景。
     *
     * @return 毫秒；没有匹配记录、或记录里 progress 为 0/-1（已看完）时返回 0
     */
    public static long parseHistoryProgressMs(JSONArray list, long aid, long epid) {
        if (list == null) return 0;
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            JSONObject history = item.optJSONObject("history");
            if (history == null) continue;
            if (history.optLong("oid", 0) != aid) continue;
            // epid 是"这一条记录属于哪一集"的身份：它与请求的剧集不一致时必须丢弃，
            // 否则看着这一集点开另一集会从上一集的位置起播
            if (epid != 0 && history.optLong("epid", 0) != epid) continue;
            //progress 单位是秒；0/-1 都没有可续播的位置
            int progress = item.optInt("progress", 0);
            if (progress > 0) return progress * 1000L;
        }
        return 0;
    }

}
