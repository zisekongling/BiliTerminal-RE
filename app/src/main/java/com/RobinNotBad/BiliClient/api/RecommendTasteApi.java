package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.recommend.RecommendTasteEntry;
import com.RobinNotBad.BiliClient.util.recommend.RecommendTasteProfile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 观看历史取数，只服务于「X 式重排」的画像构建（对齐参考实现的 `buildTaste`）。
 *
 * <p>与 {@link HistoryApi} 的区别：那边是「给用户看的观看记录列表」（要封面、要进度文案、
 * 只留 archive/pgc 两类），这边只要**画像要用的四个数**——作者 mid、观看时间、进度、时长，
 * 外加 bvid 用来算「已看过」。两者取数口径不同，但都不该互相 import 业务逻辑，所以各自成类；
 * 请求参数与游标字段沿用历史接口（`type=all&ps&view_at&business&max`，游标在 `data.cursor`）。
 *
 * <p>只有在重排开启时才调用；未登录时接口返回 code=-101，这里直接返回空结果（重排退回
 * 「先验 + 多样性」两段，不报错、不影响推荐页正常出内容）。
 */
public class RecommendTasteApi {

    private static final String URL_HISTORY = "https://api.bilibili.com/x/web-interface/history/cursor";

    /** 最多往前翻几页历史（参考实现是 4 页）。 */
    public static final int MAX_PAGES = 4;

    /** 每页条数。20 条 × 4 页 ≈ 80 条，足够刻画「最近常看谁」。 */
    public static final int PAGE_SIZE = 20;

    /**
     * 拉最近的历史并算成画像条目。
     *
     * @param watchedBvids 出参：这批历史里的 bvid（重排时用来剔除「已看过」），调用方可传 null
     * @return upMid → 偏好分之外的原始条目；失败或未登录时返回空列表
     */
    public static List<RecommendTasteEntry> fetchTasteEntries(Set<String> watchedBvids)
            throws IOException, JSONException {
        List<RecommendTasteEntry> entries = new ArrayList<>();
        if (watchedBvids == null) watchedBvids = new LinkedHashSet<>();

        long viewAt = 0L;
        String business = "";
        long max = 0L;
        long nowSeconds = System.currentTimeMillis() / 1000;

        for (int page = 0; page < MAX_PAGES; page++) {
            String url = URL_HISTORY + "?type=all&ps=" + PAGE_SIZE
                    + "&view_at=" + viewAt + "&business=" + business + "&max=" + max;
            JSONObject result = NetWorkUtil.getJson(url);
            if (result == null || result.optInt("code", -1) != 0) break;

            JSONObject data = result.optJSONObject("data");
            if (data == null) break;
            JSONArray list = data.optJSONArray("list");
            if (list == null || list.length() == 0) break;

            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) continue;

                JSONObject history = item.optJSONObject("history");
                String bvid = history != null ? history.optString("bvid", "") : "";
                if (!bvid.isEmpty() && watchedBvids.size() < RecommendTasteProfile.SEEN_LIMIT) {
                    watchedBvids.add(bvid);
                }

                long upMid = item.optLong("author_mid", 0L);
                if (upMid <= 0L) continue;

                long itemViewAt = item.optLong("view_at", 0L);
                long progress = item.optLong("progress", 0L);
                long duration = item.optLong("duration", 0L);
                entries.add(RecommendTasteEntry.of(upMid, itemViewAt, progress, duration, nowSeconds));
            }

            JSONObject cursor = data.optJSONObject("cursor");
            if (cursor == null) break;
            long nextMax = cursor.optLong("max", 0L);
            long nextViewAt = cursor.optLong("view_at", 0L);
            String nextBusiness = cursor.optString("business", "");
            // 游标没前进就停下，别在脏游标上死循环
            if (nextMax == max && nextViewAt == viewAt && nextBusiness.equals(business)) break;
            max = nextMax;
            viewAt = nextViewAt;
            business = nextBusiness;
        }
        return entries;
    }
}
