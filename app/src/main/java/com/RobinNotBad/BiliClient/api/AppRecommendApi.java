package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * APP 端推荐流（app.bilibili.com/x/v2/feed/index）。
 *
 * <p>对齐参考实现（github.com/jiuyang5354/bilibili-margin 的 `src/content/api/appfeed.ts`），
 * 但**只搬参数与解析口径，不搬它的凭据与伪装**：
 *
 * <ul>
 *   <li>签名复用本项目 {@link LoginApi#tvSign(Map)}（TV 客户端 appkey，与扫码登录同一套）——
 *       不新增任何硬编码凭据。历史上兄弟分支把 APP_KEY/APP_SEC 写死在代码里 + 伪装
 *       官方客户端 UA + 绑账号，那套在本项目是**明确禁止**的（见 docs/review/forks/revival.md），
 *       这里不重蹈：UA 用 NetWorkUtil 默认值，不加设备指纹参数之外的伪装。</li>
 *   <li>个性化内容取决于 `access_key`：没有它接口照样返回 code=0，但拿到的是一批与账号
 *       无关的兜底推荐。所以调用方必须先问 {@link #hasCredential()}，没有就整个不用
 *       （见 util/recommend/RecommendSourcePolicy）。</li>
 * </ul>
 *
 * <p>参数（2026-11 实测可用的一组，匿名就能返回 15 条 goto=av 且带 bvid 的卡片）：
 * build=74800100 / c_locate=CN / column=4 / device=pad / flush=0 / fnval=272 / idx=游标 /
 * mobi_app=iphone / pull=首页 1 否则 0 / s_locale=zh-CN。实测 `device=phone + mobi_app=android`
 * 那一组返回的卡片 **没有 bvid**，不能用于本项目（VideoCard 依赖 bvid 去重与跳转）。
 *
 * <p>游标：`idx` 是服务端给的大整数（如 1795224033），取**本批最后一条**的 idx 作下一页，
 * 首页传 {@link #CURSOR_START}（"0"）。
 */
public class AppRecommendApi {

    /** 首页游标；也是「拿不到新游标」时的回落值。 */
    public static final String CURSOR_START = "0";

    private static final String URL_FEED = "https://app.bilibili.com/x/v2/feed/index";

    /** 每批条数由服务端决定（实测 15 条），这里不传 ps；`column` 必须给。 */
    private static final String COLUMN = "4";
    private static final String BUILD = "74800100";

    /**
     * 本机是否具备 APP 推荐流所需的凭据（TV / 网页 / 短信登录都会落 access_key）。
     *
     * <p>注意 {@link SharedPreferencesUtil#access_key} 是可变静态字段而不是常量，取值必须走
     * {@link SharedPreferencesUtil#getString(String, String)}，不能直接当 key 字面量用。
     */
    public static boolean hasCredential() {
        String accessKey = SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, "");
        return accessKey != null && !accessKey.isEmpty();
    }

    /**
     * 拉一批 APP 推荐。
     *
     * @param out    解析结果追加到这里（跳过广告与无 bvid 的卡片）
     * @param cursor 上一批返回的游标；首页传 {@link #CURSOR_START}
     * @return 下一批的游标（拿不到时返回原值，调用方据此判到底）
     */
    public static String getRecommend(List<VideoCard> out, String cursor) throws IOException, JSONException {
        String idx = (cursor == null || cursor.isEmpty()) ? CURSOR_START : cursor;

        Map<String, String> params = new LinkedHashMap<>();
        params.put("access_key", SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, ""));
        params.put("build", BUILD);
        params.put("c_locale", "CN");
        params.put("column", COLUMN);
        params.put("device", "pad");
        params.put("flush", "0");
        params.put("fnval", "272");
        params.put("idx", idx);
        params.put("mobi_app", "iphone");
        params.put("pull", CURSOR_START.equals(idx) ? "1" : "0");
        params.put("s_locale", "zh-CN");

        // tvSign 内部会按 key 排序补 appkey/ts 并算 sign，返回值可直接拼进 GET query
        String query = LoginApi.tvSign(params);
        JSONObject result = NetWorkUtil.getJson(URL_FEED + "?" + query);
        return parseItems(result, out);
    }

    /**
     * 纯解析：把一批 items 转成 {@link VideoCard} 并算出下一批游标（JVM 单测直接喂 JSON）。
     *
     * @return 下一批游标；本批没有任何 item 时返回 {@link #CURSOR_START}
     */
    static String parseItems(JSONObject result, List<VideoCard> out) throws JSONException {
        if (result == null || out == null) return CURSOR_START;
        JSONObject data = result.optJSONObject("data");
        if (data == null) return CURSOR_START;
        JSONArray items = data.optJSONArray("items");
        if (items == null || items.length() == 0) return CURSOR_START;

        String next = CURSOR_START;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            // 游标取最后一条的 idx：即使这条被过滤掉，它也代表服务端这一批翻到了哪里
            String idx = item.optString("idx", "");
            if (!idx.isEmpty()) next = idx;

            // 广告卡是 ad_av / cm_v1，直接跳过；只要 goto 与 card_goto 双 av 的普通稿件
            if (!"av".equals(item.optString("goto", ""))) continue;
            if (!"av".equals(item.optString("card_goto", ""))) continue;

            String bvid = item.optString("bvid", "");
            if (bvid.isEmpty()) continue;

            JSONObject args = item.optJSONObject("args");
            String upName = args != null ? args.optString("up_name", "") : "";
            long upMid = args != null ? args.optLong("up_id", 0L) : 0L;

            long aid = item.optJSONObject("player_args") != null
                    ? item.optJSONObject("player_args").optLong("aid", 0L) : 0L;
            if (aid <= 0L && args != null) aid = args.optLong("aid", 0L);
            if (aid <= 0L) {
                String param = item.optString("param", "");
                try {
                    aid = Long.parseLong(param);
                } catch (NumberFormatException ignored) {
                    aid = 0L;
                }
            }

            String cover = item.optString("cover", "");
            if (cover.startsWith("http:")) cover = "https:" + cover.substring(5);

            // cover_left_text_1 已经是「193.9万」这种格式化好的播放数，不要再 toWan 一次
            String views = item.optString("cover_left_text_1", "");
            String view = views.isEmpty() ? "" : views + "观看";

            VideoCard card = new VideoCard(item.optString("title", ""), upName, view, cover, aid, bvid);
            card.upMid = upMid;
            // APP 侧的 track_id 与网页侧反馈埋点不通用，传给 FeedbackApi.dislike 反而会错配，一律留空
            card.trackId = "";
            out.add(card);
        }
        return next;
    }
}
