package com.RobinNotBad.BiliClient.api;

import android.text.TextUtils;
import android.util.Log;

import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.PerformanceManager;
import com.RobinNotBad.BiliClient.util.RecommendPagePolicy;
import com.RobinNotBad.BiliClient.util.StringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;


//推荐API 自己写的
//#如果想要增/删/改内容，可以直接把url复制到浏览器里，把得到的一大串东西交给json解析软件就能缕清结构了，然后就是拆拆拆
//2023-07-12
//2023-12-09

public class RecommendApi {
    private static final long UNIQ_ID = (long) (new Random().nextDouble() * (1500000000000L - 1300000000000L));

    /**
     * 不带分页游标的旧入口：等价于「第 1 页 + 设备档位页大小」。
     */
    public static void getRecommend(List<VideoCard> videoCardList, int freshType) throws IOException, JSONException {
        getRecommend(videoCardList, freshType, RecommendPagePolicy.FIRST_PAGE, PerformanceManager.getPageSize());
    }

    /**
     * 带分页游标的推荐流。
     *
     * <p>26.11.06 批次 3（task-16）：原来只带 {@code fresh_type}，服务端对「参数完全相同」的两次
     * 请求返回同一批内容，于是下拉加载更多只是把同一批卡片再追加一遍。现在按
     * {@link RecommendPagePolicy} 下发 {@code ps}（每页条数）与 {@code fresh_idx}（页码游标）。
     *
     * <p>接口实测（2026-11-06，匿名、不带 Cookie/WBI 签名）：
     * <ul>
     *     <li>{@code ps} 生效：ps=5 返回 5 条、ps=20 返回 20 条、不传 30 条；</li>
     *     <li>{@code fresh_idx} 生效：固定其它参数时 idx=1/2/3 三批两两 bvid 交集为 0；</li>
     *     <li>{@code brush} 单独改无效（同 idx 下 brush=1 与 brush=2 是同一批），故不下发；</li>
     *     <li>{@code fresh_type} 单独改也无效（3/4/不传 相同），只为兼容保留原值；</li>
     *     <li>深翻没有空页信号（idx=120 仍返回满页）；推荐页按无底流处理，**不**判「到底」。</li>
     * </ul>
     *
     * @param freshType 原来的 fresh_type（保留兼容，实测不影响内容）
     * @param page      页码，从 1 开始（对应 fresh_idx）
     * @param pageSize  每页条数（ps），建议用 {@link PerformanceManager#getPageSize()}
     */
    public static void getRecommend(List<VideoCard> videoCardList, int freshType, int page, int pageSize) throws IOException, JSONException {
        RecommendPagePolicy.Params params = RecommendPagePolicy.of(page, pageSize, freshType);
        String url = ("https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd");
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData().setUrlParam(true)
                .put("web_location", 1430650)
                .put("feed_version", "V8")
                .put("homepage_ver", 1)
                .put("uniq_id", UNIQ_ID)
                .put("screen", "1100-2056");
        for (Map.Entry<String, String> entry : RecommendPagePolicy.query(params).entrySet()) {
            formData.put(entry.getKey(), entry.getValue());
        }
        url += formData;

        JSONObject result = NetWorkUtil.getJson(ConfInfoApi.signWBI(url));

        JSONObject data = result.getJSONObject("data");

        JSONArray item = data.getJSONArray("item");

        for (int i = 0; i < item.length(); i++) {
            JSONObject card = item.getJSONObject(i);
            // 与 web 端/PiliPlus 对齐：番剧/直播/广告卡也可能带 bvid，必须显式判 goto，
            // 只靠「bvid 为空」会漏过它们（实测当前推荐全是 av，但接口不保证）
            String gotoType = card.optString("goto", "");
            if (!TextUtils.isEmpty(gotoType) && !"av".equals(gotoType)) {
                Log.d("BiliClient", "RecommendApi getRecommend: skip goto=" + gotoType);
                continue;
            }
            String bvid = card.optString("bvid", "");
            if (TextUtils.isEmpty(bvid)) {
                Log.d("BiliClient", "RecommendApi getRecommend: isAd");
                continue;
            }
            String cover = card.getString("pic");
            String title = card.getString("title");
            String upName = card.getJSONObject("owner").getString("name");
            String view = StringUtil.toWan(card.getJSONObject("stat").getInt("view")) + "观看";
            // 「减少推荐（不感兴趣）」要的两个定位参数：track_id 是这一条推荐的追踪号（可空），
            // mid 是 UP 主 mid（owner.mid，缺失/异常时按 0=未知处理，不能拿去当「不想看此 UP 主」的依据）。
            // 两者都不参与下面的过滤判据，只是顺手带上，供 VideoCardMenu 的「减少推荐」用。
            String trackId = card.optString("track_id", "");
            long upMid = 0L;
            JSONObject owner = card.optJSONObject("owner");
            if (owner != null) upMid = owner.optLong("mid", 0L);
            VideoCard videoCard = new VideoCard(title, upName, view, cover, card.optLong("id", 0L), bvid);
            videoCard.trackId = trackId;
            videoCard.upMid = upMid;
            videoCardList.add(videoCard);
        }
    }

    public static ArrayList<VideoCard> getRelated(long aid) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/web-interface/archive/related?aid=" + aid;

        JSONObject result = NetWorkUtil.getJson(url);

        ArrayList<VideoCard> videoList = new ArrayList<>();
        if (result.has("data") && !result.isNull("data")) {
            JSONArray data = result.getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                JSONObject card = data.getJSONObject(i);
                VideoCard videoCard = new VideoCard();
                videoCard.aid = card.getLong("aid");
                videoCard.view = StringUtil.toWan(card.getJSONObject("stat").getLong("view")) + "观看";
                videoCard.cover = card.getString("pic");
                videoCard.title = card.getString("title");
                videoCard.upName = card.getJSONObject("owner").getString("name");
                videoList.add(videoCard);
            }

        }

        return videoList;
    }

    public static void getPopular(List<VideoCard> videoCardList, int page) throws JSONException, IOException {
        // 26.10.04 批次 3（B1+B5）：页大小改为按设备档位取（低端 10 / 其余 20）
        String url = "https://api.bilibili.com/x/web-interface/popular?pn=" + page + "&ps=" + PerformanceManager.getPageSize();

        JSONObject result = NetWorkUtil.getJson(url);

        if (result.has("data") && !result.isNull("data")) {
            if (result.getJSONObject("data").has("list")) {
                JSONArray list = result.getJSONObject("data").getJSONArray("list");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject card = list.getJSONObject(i);
                    VideoCard videoCard = new VideoCard();
                    videoCard.aid = card.getLong("aid");
                    videoCard.cover = card.getString("pic");
                    videoCard.title = card.getString("title");
                    videoCard.upName = card.getJSONObject("owner").getString("name");
                    videoCard.view = StringUtil.toWan(card.getJSONObject("stat").getLong("view")) + "观看";
                    videoCardList.add(videoCard);
                }
            }
        }
    }

    public static void getPrecious(List<VideoCard> videoCardList, int page) throws JSONException, IOException {
        // 26.10.04 批次 3（B1+B5）：页大小改为按设备档位取（低端 10 / 其余 20）
        String url = "https://api.bilibili.com/x/web-interface/popular/precious?page=" + page + "&page_size=" + PerformanceManager.getPageSize();

        JSONObject result = NetWorkUtil.getJson(url);

        if (result.has("data") && !result.isNull("data")) {
            if (result.getJSONObject("data").has("list")) {
                JSONArray list = result.getJSONObject("data").getJSONArray("list");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject card = list.getJSONObject(i);
                    VideoCard videoCard = new VideoCard();
                    videoCard.aid = card.getLong("aid");
                    videoCard.cover = card.getString("pic");
                    videoCard.title = card.getString("title");
                    videoCard.upName = card.getJSONObject("owner").getString("name");
                    videoCard.view = StringUtil.toWan(card.getJSONObject("stat").getLong("view")) + "观看";
                    videoCardList.add(videoCard);
                }
            }
        }
    }
}