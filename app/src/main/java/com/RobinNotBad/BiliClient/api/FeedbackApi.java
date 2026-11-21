package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.util.NetWorkUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.Response;

/**
 * 内容反馈（负反馈）API —— 目前只有「减少推荐（不感兴趣）」这一条链路。
 *
 * <p>接口（已确认，见 {@code bilibili-API} 快照与网页端抓包同款）：
 * <pre>
 * POST https://api.bilibili.com/x/web-interface/feedback/dislike
 * POST https://api.bilibili.com/x/web-interface/feedback/dislike/cancel   （同参数，用于撤销）
 * Content-Type: application/x-www-form-urlencoded
 * </pre>
 *
 * <p><b>参数从哪来</b>：
 * <ul>
 *   <li>{@code goto=av} + {@code id=<aid>}：定位这一条稿件。注意是 <b>aid 不是 bvid</b>，
 *       调用方传的是 {@code VideoCard.aid}；</li>
 *   <li>{@code mid=<upMid>}：UP 主 mid（接口里的 {@code owner.mid}），
 *       0 表示本客户端不知道（例如非推荐流来源的卡片），此时仍按 aid 定位；</li>
 *   <li>{@code track_id}：推荐流给这一条内容的追踪号，<b>可为空串</b>——非推荐流来源
 *       （搜索、排行、收藏夹等）本来就没有 track_id，服务端接受空串；</li>
 *   <li>{@code reason_id}：{@link #REASON_CONTENT}（1，对内容不感兴趣）或
 *       {@link #REASON_UP}（4，不想看此 UP 主）；</li>
 *   <li>{@code app_id=100} / {@code platform=5} / {@code feedback_page=1} /
 *       {@code from_spmid=（空串）} / {@code spmid=333.1007.0.0}：网页端原样下发的固定埋点值；</li>
 *   <li>{@code csrf}：取自 Cookie 的 bili_jct，走 {@link NetWorkUtil#currentCsrf()}，与其它写接口一致。</li>
 * </ul>
 *
 * <p><b>为什么不需要 WBI 签名</b>：这是网页端 {@code feedback/dislike} 写接口，只校验 Cookie + csrf，
 * 请求里没有 {@code w_rid}/{@code wts}，因此<b>不要</b>调用 {@link ConfInfoApi#signWBI(String)}
 * ——多签一次反而会让服务端按未知参数处理。
 *
 * <p>响应外壳是 {@code {code,message,ttl,data}}，{@code data} 的内容查不到，
 * 所以这里<b>只按 {@code code==0} 判成功</b>，绝不依赖 data。
 */
public class FeedbackApi {

    /** 原因：对内容不感兴趣（reason_id=1） */
    public static final int REASON_CONTENT = 1;
    /** 原因：不想看此 UP 主（reason_id=4） */
    public static final int REASON_UP = 4;

    /** 提交「减少推荐」 */
    private static final String URL_DISLIKE = "https://api.bilibili.com/x/web-interface/feedback/dislike";
    /** 撤销「减少推荐」（同参数） */
    private static final String URL_DISLIKE_CANCEL = "https://api.bilibili.com/x/web-interface/feedback/dislike/cancel";

    /**
     * 拼 dislike 系列接口的表单字段（<b>纯函数</b>：不读 Context / 不发网络 / 不碰偏好，
     * 只依赖入参，供 JVM 单测直接断言字段）。
     *
     * <p>返回顺序按 {@link LinkedHashMap} 的插入顺序固定下来，方便单测与排查对照；
     * 真正发出时 {@link NetWorkUtil.FormData} 会用 URLEncoder 编码（track_id 等含特殊字符也安全）。
     *
     * @param aid     稿件 avid（不是 bvid）
     * @param upMid   UP 主 mid，0 表示未知
     * @param trackId 推荐流 track_id，<b>null 按空串处理</b>
     * @param reasonId {@link #REASON_CONTENT} 或 {@link #REASON_UP}
     * @return 字段名 → 字段值，不含 csrf（csrf 属于凭据，由网络方法单独追加）
     */
    public static Map<String, String> buildForm(long aid, long upMid, String trackId, int reasonId) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("goto", "av");
        form.put("id", String.valueOf(aid));
        form.put("mid", String.valueOf(upMid));
        // 非推荐流来源没有 track_id，服务端接受空串
        form.put("track_id", trackId == null ? "" : trackId);
        form.put("reason_id", String.valueOf(reasonId));
        form.put("app_id", "100");
        form.put("platform", "5");
        form.put("feedback_page", "1");
        form.put("from_spmid", "");
        form.put("spmid", "333.1007.0.0");
        return form;
    }

    /**
     * 提交「减少推荐」。
     *
     * @return 服务端 {@code code}（0 成功；-101 未登录、-111 csrf 校验失败；
     *         -400 表示入参不合法、本地没发请求；-1 表示响应里没有 code 字段）
     */
    public static int dislike(long aid, long upMid, String trackId, int reasonId) throws IOException, JSONException {
        return postDislike(URL_DISLIKE, aid, upMid, trackId, reasonId);
    }

    /**
     * 撤销「减少推荐」（参数与 {@link #dislike} 完全一致）。
     *
     * @return 服务端 {@code code}，语义同 {@link #dislike}
     */
    public static int cancelDislike(long aid, long upMid, String trackId, int reasonId) throws IOException, JSONException {
        return postDislike(URL_DISLIKE_CANCEL, aid, upMid, trackId, reasonId);
    }

    /**
     * 把 {@link #buildForm} 的字段发出去并解析 code。
     *
     * <p>请求写法照抄 {@code FavoriteApi.addFavorite} / {@code WatchLaterApi.add}：
     * 复用全局 {@link NetWorkUtil#webHeaders}（Cookie / Origin / Referer / UA），
     * {@code NetWorkUtil.post(url, body, headers)} 默认就是 x-www-form-urlencoded。
     */
    private static int postDislike(String url, long aid, long upMid, String trackId, int reasonId)
            throws IOException, JSONException {
        if (aid <= 0) return -400;

        NetWorkUtil.FormData formData = new NetWorkUtil.FormData();
        for (Map.Entry<String, String> entry : buildForm(aid, upMid, trackId, reasonId).entrySet()) {
            formData.put(entry.getKey(), entry.getValue());
        }
        formData.put("csrf", NetWorkUtil.currentCsrf());

        // 非 2xx（412 风控页 / 5xx HTML）由 NetWorkUtil.post 抛 IOException，不会走到 new JSONObject
        try (Response response = NetWorkUtil.post(url, formData.toString(), NetWorkUtil.webHeaders)) {
            if (response.body() == null) return -1;
            // 外壳固定 {code,message,ttl,data}，data 内容未知，只认 code
            return new JSONObject(response.body().string()).optInt("code", -1);
        }
    }
}
