package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.util.UiText;

import com.RobinNotBad.BiliClient.R;

import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.model.ArticleCard;
import com.RobinNotBad.BiliClient.model.At;
import com.RobinNotBad.BiliClient.model.Dynamic;
import com.RobinNotBad.BiliClient.model.Emote;
import com.RobinNotBad.BiliClient.model.LiveRoom;
import com.RobinNotBad.BiliClient.model.Stats;
import com.RobinNotBad.BiliClient.model.TopicNode;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.DmImgParamUtil;
import com.RobinNotBad.BiliClient.util.EmoteUrlCollector;
import com.RobinNotBad.BiliClient.util.EmoteUtil;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Response;
import okhttp3.ResponseBody;

//新的动态api，旧的那个实在太蛋疼而且说不定随时会被弃用（

public class DynamicApi {

    /** 表情文本形如 [doge]，长度上限按 B 站惯例取 32。 */
    private static final Pattern EMOTE_PATTERN = Pattern.compile("\\[[^\\[\\]]{1,32}\\]");

    /**
     * 编辑动态接口的固定 meta。query 与 body 里各要一份，两份内容必须一致。
     * 与 PiliPlus 的 {@code DynamicsHttp.editDyn} 对齐。
     */
    private static final String EDIT_DYN_META = "{\"app_meta\":{\"from\":\"create.dynamic.web\",\"mobi_app\":\"web\"}}";

    /**
     * 编辑动态接口附在 query 上的设备参数。这个参数在别的接口里通常是请求头，
     * 但编辑动态这条接口上游是原样放进 query 一起签名的，这里照做。
     */
    private static final String EDIT_DYN_DEVICE_JSON = "{\"platform\":\"web\",\"device\":\"pc\",\"spmid\":\"333.1368\"}";

    /**
     * 发送纯文本动态
     *
     * @param content 文字内容
     * @return 发送成功返回的动态id，失败返回-1
     */
    public static long publishTextContent(String content) throws IOException {
        String url = "https://api.vc.bilibili.com/dynamic_svr/v1/dynamic_svr/create";
        Response resp = Objects.requireNonNull(NetWorkUtil.post(url, new NetWorkUtil.FormData()
                .put("dynamic_id", 0)
                .put("type", 4)
                .put("rid", 0)
                .put("content", content)
                .put("csrf", NetWorkUtil.currentCsrf())
                .toString(), NetWorkUtil.webHeaders));
        try {
            ResponseBody body = resp.body();
            if (body == null) return -1;
            JSONObject result = new JSONObject(body.string());
            if (result.getString("code").equals("0") && result.has("data"))
                return result.getJSONObject("data").getLong("dynamic_id");
        } catch (JSONException ignored) {
            return -1;
        }
        return -1;
    }

    /**
     * 发送复杂动态
     *
     * @param contents 动态内容
     * @param pics     携带图片
     * @param option   选项
     * @param topic    话题
     * @param scene    动态类型
     * @param attachCard 附加卡片（如投票），会放入 dyn_req.attach_card
     * @return 发送成功返回的动态id，失败返回-1
     */
    public static long publishComplex(@NonNull JSONArray contents, JSONArray pics, JSONObject option, JSONObject topic, int scene, JSONObject attachCard, Map<String, Object> otherArgs) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/dynamic/feed/create/dyn?csrf=" + NetWorkUtil.currentCsrf();
        JSONObject reqBody = new JSONObject()
                .put("content", new JSONObject().put("contents", contents))
                .put("scene", scene)
                .put("meta", new JSONObject().put("app_meta", new JSONObject()
                        .put("from", "create.dynamic.web")
                        .put("mobi_app", "web")));
        if (pics != null && pics.length() > 0) reqBody.put("pics", pics);
        if (option != null && option.length() > 0) reqBody.put("option", option);
        if (topic != null) reqBody.put("topic", topic);
        if (attachCard != null) reqBody.put("attach_card", attachCard);
        reqBody = new JSONObject().put("dyn_req", reqBody);
        if (otherArgs != null) {
            for (Map.Entry<String, Object> entry : otherArgs.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                reqBody.put(key, val);
            }
        }

        Logu.v("publishComplex reqBody=" + reqBody);
        Response resp = Objects.requireNonNull(NetWorkUtil.postJson(url, reqBody.toString()));
        try {
            ResponseBody body = resp.body();
            if (body == null) return -1;
            JSONObject result = new JSONObject(body.string());
            if (result.getString("code").equals("0") && result.has("data"))
                return result.getJSONObject("data").getLong("dyn_id");
        } catch (JSONException e) {
            MsgUtil.err(UiText.of("发送动态"), e);
            return -1;
        }
        return -1;
    }

    /**
     * 发送复杂动态（无 attach_card 兼容重载）
     *
     * @param contents 动态内容
     * @param pics     携带图片
     * @param option   选项
     * @param topic    话题
     * @param scene    动态类型
     * @return 发送成功返回的动态id，失败返回-1
     */
    public static long publishComplex(@NonNull JSONArray contents, JSONArray pics, JSONObject option, JSONObject topic, int scene, Map<String, Object> otherArgs) throws IOException, JSONException {
        return publishComplex(contents, pics, option, topic, scene, null, otherArgs);
    }

    /**
     * 编辑自己已经发过的动态。
     *
     * <p>报文与 PiliPlus 的 {@code DynamicsHttp.editDyn} 对齐：POST
     * {@code https://api.bilibili.com/x/dynamic/feed/edit/dyn}，query 经 WBI 签名，
     * body 形如 {@code {"dyn_req": {...}, "dyn_id_str": "<动态id>"}}。
     * 与发布（{@link #publishComplex}）的区别：URL 不同、query 要签名、body 外层多一个
     * {@code dyn_id_str}、且 {@code dyn_req} 里多带 {@code upload_id}。
     *
     * <p>注意本接口没有 {@code timer_pub_time}：定时发布得走发布接口。
     *
     * @param dynId    要编辑的动态 id
     * @param contents 正文（用 {@link #buildContents} 构造）
     * @param pics     图片（可为 null；文本动态传 null 即可）
     * @param option   选项（可为 null）
     * @param topic    话题（可为 null）
     * @param scene    动态类型，纯文本为 1
     * @return 服务端 code，0 为成功
     */
    public static int editDynamic(long dynId, @NonNull JSONArray contents, JSONArray pics, JSONObject option, JSONObject topic, int scene) throws IOException, JSONException {
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        String uploadId = buildUploadId(mid, System.currentTimeMillis() / 1000, 1000 + (int) (Math.random() * 9000));
        String url = "https://api.bilibili.com/x/dynamic/feed/edit/dyn"
                + "?platform=web"
                + "&csrf=" + NetWorkUtil.currentCsrf()
                + "&x-bili-device-req-json=" + URLEncoder.encode(EDIT_DYN_DEVICE_JSON, "UTF-8")
                + "&w_dyn_req.upload_id=" + uploadId
                + "&w_dyn_req.meta=" + URLEncoder.encode(EDIT_DYN_META, "UTF-8");
        String signedUrl = ConfInfoApi.signWBI(url);

        JSONObject reqBody = new JSONObject()
                .put("content", new JSONObject().put("contents", contents))
                .put("scene", scene)
                .put("meta", new JSONObject().put("app_meta", new JSONObject()
                        .put("from", "create.dynamic.web")
                        .put("mobi_app", "web")))
                .put("upload_id", uploadId);
        if (pics != null && pics.length() > 0) reqBody.put("pics", pics);
        if (option != null && option.length() > 0) reqBody.put("option", option);
        if (topic != null) reqBody.put("topic", topic);

        JSONObject body = new JSONObject()
                .put("dyn_req", reqBody)
                .put("dyn_id_str", String.valueOf(dynId));

        Logu.v("editDynamic reqBody=" + body);
        Response resp = NetWorkUtil.postJson(signedUrl, body.toString());
        // 412 风控页 / 5xx 时 body 是 HTML，直接 new JSONObject 会把「失败」变成一次 JSONException；
        // 用 -1 表示「请求没成功」，调用方按非 0 码提示即可
        if (resp == null) return -1;
        try (Response response = resp) {
            if (!response.isSuccessful() || response.body() == null) return -1;
            return new JSONObject(response.body().string()).optInt("code", -1);
        }
    }

    /**
     * 置顶 / 取消置顶一条自己的动态。
     *
     * <p>两个接口只差路径上的一段：{@link #topPath}。正文都是 {@code {"dyn_str": "<id>"}}，
     * csrf 走 query；服务端还要求 Cookie 里 {@code buvid3} 非空（登录时 {@code api/CookiesApi.java} 会补齐）。
     *
     * @param dynId 动态 id
     * @param top   true 置顶、false 取消置顶
     * @return 服务端 code，0 为成功
     */
    public static int setDynamicTop(long dynId, boolean top) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/dynamic/feed/" + topPath(top)
                + "?csrf=" + NetWorkUtil.currentCsrf();
        JSONObject body = new JSONObject().put("dyn_str", String.valueOf(dynId));
        Response resp = NetWorkUtil.postJson(url, body.toString());
        // 同上：非 2xx 时不解析，直接按失败返回
        if (resp == null) return -1;
        try (Response response = resp) {
            if (!response.isSuccessful() || response.body() == null) return -1;
            return new JSONObject(response.body().string()).optInt("code", -1);
        }
    }

    /**
     * 置顶与取消置顶的接口路径片段。抽出来是为了能单测，而不用真的请求网络。
     */
    public static String topPath(boolean top) {
        return top ? "space/set_top" : "space/rm_top";
    }

    /**
     * 置顶 / 取消置顶成功后的提示文案。
     */
    public static String topSuccessMsg(boolean top) {
        return top ? "置顶成功~" : "已取消置顶~";
    }

    /**
     * 置顶 / 取消置顶失败时给用户看的文案。
     *
     * @param code 服务端 code
     * @return code 为 0 时返回空串
     */
    public static String topErrorMsg(int code) {
        switch (code) {
            case 0:
                return "";
            case -101:
                return ApiErrorText.loginRequired();
            case -102:
            case -111:
                return ApiErrorText.tokenExpired();
            case 4100001:
                return "动态 id 不对，请刷新后重试";
            case -404:
                return "动态不存在，可能已经被删除了";
            default:
                return ApiErrorText.opFailed(code);
        }
    }


    /**
     * 拼上传 id，格式与上游一致：{@code mid_秒级时间戳_四位随机数}。
     * 抽出来是为了能单测格式，而不用真的请求网络。
     */
    public static String buildUploadId(long mid, long seconds, int random) {
        return mid + "_" + seconds + "_" + random;
    }

    /**
     * 编辑动态失败时给用户看的文案。
     *
     * @param code 服务端 code
     * @return code 为 0 时返回空串
     */
    public static String editErrorMsg(int code) {
        switch (code) {
            case 0:
                return "";
            case -101:
            case -102:
            case -111:
                return ApiErrorText.tokenExpired();
            case -400:
                return "编辑的内容不合规范，请修改后再试";
            case -403:
                return "没有权限编辑这条动态";
            case -404:
                return "动态不存在，可能已经被删除了";
            case -509:
                return "操作过于频繁，请稍后再试";
            default:
                return "编辑失败（错误码 " + code + "）";
        }
    }

    /**
     * 构造发布用的 contents：先按 @ 拆节点，再把文本节点里的表情拆成 type 9 节点。
     *
     * <p>与上游一致：只有 emoteTexts 里确实存在的 [xxx] 才会转成表情节点，否则保持纯文本，
     * 免得把用户随手打的方括号当成表情发出去。emoteTexts 为空时行为与旧版完全一致。
     *
     * @param content    正文
     * @param atUserUid  正文内 @ 到的人（可为 null，转发引用等场景不解析 @）
     * @param emoteTexts 可用表情名集合（可为 null / 空）
     * @return Content JSON 数组
     */
    public static JSONArray buildContents(String content, Map<String, Long> atUserUid, Set<String> emoteTexts) throws JSONException {
        JSONArray contents = parseAtContent(content, atUserUid != null ? atUserUid : new HashMap<>());
        if (emoteTexts == null || emoteTexts.isEmpty()) return contents;

        JSONArray result = new JSONArray();
        for (int i = 0; i < contents.length(); i++) {
            JSONObject node = contents.optJSONObject(i);
            if (node == null) continue;
            if (node.optInt("type", 1) != 1) {
                result.put(node);
                continue;
            }
            String raw = node.optString("raw_text", "");
            if (raw.isEmpty()) {
                result.put(node);
                continue;
            }
            Matcher matcher = EMOTE_PATTERN.matcher(raw);
            int pos = 0;
            boolean matched = false;
            while (matcher.find()) {
                if (!emoteTexts.contains(matcher.group())) continue;
                matched = true;
                if (matcher.start() > pos) result.put(Content.create(raw.substring(pos, matcher.start()), 1, null));
                result.put(Content.create(matcher.group(), 9, null));
                pos = matcher.end();
            }
            if (!matched) {
                result.put(node);
            } else if (pos < raw.length()) {
                result.put(Content.create(raw.substring(pos), 1, null));
            }
        }
        return result;
    }

    /**
     * 正文里是否真的含有可用表情文本（形如 [doge] 且确实在 emoteTexts 里）。
     *
     * <p>用来决定纯文本动态要不要改走复杂动态接口：只有真的含表情时才值得改接口，
     * 不含表情就继续走老的 dynamic_svr/create，避免无谓地改变发布链路。
     */
    public static boolean containsEmoteText(String content, Set<String> emoteTexts) {
        if (content == null || content.isEmpty() || emoteTexts == null || emoteTexts.isEmpty()) return false;
        Matcher matcher = EMOTE_PATTERN.matcher(content);
        while (matcher.find()) {
            if (emoteTexts.contains(matcher.group())) return true;
        }
        return false;
    }

    /**
     * 发布可包含艾特信息的文本动态
     *
     * @param content   文本内容
     * @param atUserUid 文本内at到的人的用户名uid map
     * @return 发送成功返回的动态id，失败返回-1
     */
    public static long publishTextContent(String content, Map<String, Long> atUserUid) throws JSONException, IOException {
        return publishComplex(parseAtContent(content, atUserUid), null, null, null,
                1, null);
    }

    /**
     * 发布文本动态（可带发布选项与表情）
     *
     * @param content    文本内容
     * @param atUserUid  文本内at到的人的用户名uid map
     * @param option     发布选项，见 {@link #buildPublishOption}，可为 null
     * @param emoteTexts 可用表情名集合，可为 null
     * @return 发送成功返回的动态id，失败返回-1
     */
    public static long publishTextContent(String content, Map<String, Long> atUserUid, JSONObject option, Set<String> emoteTexts) throws JSONException, IOException {
        return publishComplex(buildContents(content, atUserUid, emoteTexts), null, option, null, 1, null);
    }

    /**
     * 发布带图动态。
     *
     * <p>图片需先用 {@code ImageApi} 上传图床，再按其返回的 {@code toDynamicPicJson()} 组装成 pics 数组。
     * 带图时 scene=2，无图时退回 scene=1（与 B 站 web 端一致）。
     *
     * @param content   文本内容
     * @param atUserUid 文本内at到的人的用户名uid map
     * @param pics      图片 JSON 数组，见 B 站 pics 结构
     * @return 发送成功返回的动态id，失败返回-1
     */
    public static long publishImageContent(String content, Map<String, Long> atUserUid, JSONArray pics) throws JSONException, IOException {
        return publishImageContent(content, atUserUid, pics, null, null);
    }

    /**
     * 发布带图动态（带发布选项与表情）
     *
     * @param content    文本内容
     * @param atUserUid  文本内at到的人的用户名uid map
     * @param pics       图片 JSON 数组
     * @param option     发布选项，可为 null
     * @param emoteTexts 可用表情名集合，可为 null
     * @return 发送成功返回的动态id，失败返回-1
     */
    public static long publishImageContent(String content, Map<String, Long> atUserUid, JSONArray pics, JSONObject option, Set<String> emoteTexts) throws JSONException, IOException {
        boolean hasPics = pics != null && pics.length() > 0;
        return publishComplex(buildContents(content, atUserUid, emoteTexts), hasPics ? pics : null, option, null,
                hasPics ? 2 : 1, null);
    }

    /**
     * 构造发布选项。参数为 null / 假时对应项不下发，交给服务端走默认值。
     *
     * <p><b>定时发布的坑</b>：{@code timer_pub_time} 要的是**秒级时间戳（整数）**，不是
     * {@code yyyy-MM-dd HH:mm} 这种字符串——本方法早先的注释写的是后者，是错的（上游 web 端
     * 与 PiliPlus 传的都是 int 时间戳）。编辑接口没有定时能力，只有发布接口吃这个字段。
     *
     * @param privatePub      是否仅自己可见
     * @param closeComment    是否关闭评论，null 表示不改
     * @param upChooseComment 是否开启评论精选，null 表示不改
     * @param timerPubTime    定时发布时间的秒级时间戳（int），null 表示不定时
     */
    public static JSONObject buildPublishOption(boolean privatePub, Integer closeComment, Integer upChooseComment, Integer timerPubTime) throws JSONException {
        JSONObject option = new JSONObject();
        if (privatePub) option.put("private_pub", true);
        if (closeComment != null) option.put("close_comment", closeComment);
        if (upChooseComment != null) option.put("up_choose_comment", upChooseComment);
        if (timerPubTime != null) option.put("timer_pub_time", timerPubTime);
        return option;
    }

    /**
     * 定时发布的目标时间戳：当前秒 + 若干分钟。
     *
     * <p>抽成纯函数是为了能单测（时间来源由调用方给，不在测试里读系统时钟）。
     *
     * @param nowSeconds   当前秒级时间戳
     * @param addMinutes   往后推的分钟数，&lt;= 0 时原样返回 nowSeconds
     * @return 秒级时间戳
     */
    public static long timerSecondsAt(long nowSeconds, int addMinutes) {
        if (addMinutes <= 0) return nowSeconds;
        return nowSeconds + addMinutes * 60L;
    }

    /**
     * 构造转发时的自动引用内容：{@code //@原作者:原内容}
     *
     * <p>原作者用 type 2 节点（这样点得进主页），":" 与 "//" 是普通文本，
     * 原内容再走一遍 {@link #buildContents} 以便里面的表情/at 也是结构化节点。
     * authorName 为空时什么都不做——拿不到作者就宁可不加引用，也不要拼出半个 "@"。
     */
    private static void appendRepostQuote(JSONArray userNodes, String authorName, long authorMid, String authorContent, Set<String> emoteTexts) throws JSONException {
        if (authorName == null || authorName.isEmpty()) return;
        userNodes.put(Content.create("//", 1, null));
        userNodes.put(Content.create("@" + authorName, 2, String.valueOf(authorMid)));
        userNodes.put(Content.create(":", 1, null));
        if (authorContent != null && !authorContent.isEmpty()) {
            JSONArray origNodes = buildContents(authorContent, null, emoteTexts);
            for (int i = 0; i < origNodes.length(); i++) {
                JSONObject node = origNodes.optJSONObject(i);
                if (node != null) userNodes.put(node);
            }
        }
    }

    /**
     * 转发视频到动态，瞎扒的api
     *
     * @param text 附加文字
     * @param aid  aid
     * @return 发送成功返回的动态id，失败返回-1
     */
    public static long relayVideo(String text, Map<String, Long> atUserUid, long aid) throws JSONException, IOException {
        return relayVideo(text, atUserUid, aid, null, 0, null, null);
    }

    /**
     * 转发视频到动态（自动带上 //@UP主:原标题 的引用）
     *
     * @param text          附加文字
     * @param atUserUid     附加文字内at到的人的用户名uid map
     * @param aid           aid
     * @param authorName    原作者昵称，为空则不加引用
     * @param authorMid     原作者uid
     * @param authorContent 被转发内容的纯文本（视频标题）
     * @param emoteTexts    可用表情名集合，可为 null
     * @return 发送成功返回的动态id，失败返回-1
     */
    public static long relayVideo(String text, Map<String, Long> atUserUid, long aid, String authorName, long authorMid, String authorContent, Set<String> emoteTexts) throws JSONException, IOException {
        JSONArray contents = text == null ? new JSONArray().put(Content.create("", 1, null)) : buildContents(text, atUserUid, emoteTexts);
        appendRepostQuote(contents, authorName, authorMid, authorContent, emoteTexts);
        Map<String, Object> repostSrc = new HashMap<>();
        repostSrc.put("web_repost_src", new JSONObject().put("revs_id", new JSONObject()
                .put("dyn_type", 8)
                .put("rid", aid)));
        return publishComplex(contents, null, null, null, 5, repostSrc);
    }

    /**
     * 转发动态
     *
     * @param text 文字内容
     * @param dyid 动态id
     * @return 发送成功返回的动态id，失败返回-1
     */
    public static long relayDynamic(String text, long dyid) throws IOException {
        String url = "https://api.vc.bilibili.com/dynamic_repost/v1/dynamic_repost/repost";
        Response resp = Objects.requireNonNull(NetWorkUtil.post(url, new NetWorkUtil.FormData()
                .put("dynamic_id", dyid)
                .put("content", text)
                .put("csrf_token", NetWorkUtil.currentCsrf())
                .toString(), NetWorkUtil.webHeaders));
        try {
            ResponseBody body = resp.body();
            if (body == null) return -1;
            JSONObject result = new JSONObject(body.string());
            if (result.getString("code").equals("0") && result.has("data"))
                return result.getJSONObject("data").getLong("dynamic_id");
        } catch (JSONException ignored) {
            return -1;
        }
        return -1;
    }

    /**
     * 转发动态（复杂动态api），还是自己瞎扒的api
     *
     * @param text      文字内容
     * @param atUserUid 文本内at到的人的用户名uid map
     * @param dyid      动态id
     * @return 发送成功返回的动态id，失败返回-1
     */
    public static long relayDynamic(String text, Map<String, Long> atUserUid, long dyid) throws JSONException, IOException {
        return relayDynamic(text, atUserUid, dyid, null, 0, null, null);
    }

    /**
     * 转发动态（自动带上 //@原作者:原内容 的引用）
     *
     * @param text          附加文字
     * @param atUserUid     附加文字内at到的人的用户名uid map
     * @param dyid          动态id
     * @param authorName    原作者昵称，为空则不加引用
     * @param authorMid     原作者uid
     * @param authorContent 被转发动态的纯文本
     * @param emoteTexts    可用表情名集合，可为 null
     * @return 发送成功返回的动态id，失败返回-1
     */
    public static long relayDynamic(String text, Map<String, Long> atUserUid, long dyid, String authorName, long authorMid, String authorContent, Set<String> emoteTexts) throws JSONException, IOException {
        JSONArray contents = text == null ? new JSONArray().put(Content.create("", 1, null)) : buildContents(text, atUserUid, emoteTexts);
        appendRepostQuote(contents, authorName, authorMid, authorContent, emoteTexts);
        Map<String, Object> repostSrc = new HashMap<>();
        repostSrc.put("web_repost_src", new JSONObject().put("dyn_id_str", String.valueOf(dyid)));
        return publishComplex(contents, null, null, null, 4, repostSrc);
    }

    /**
     * 解析包含艾特信息的文本动态内容
     *
     * @param content   文本内容
     * @param atUserUid 文本内at到的人的用户名uid map
     * @return Content JSON数组
     */
    public static JSONArray parseAtContent(String content, Map<String, Long> atUserUid) throws JSONException {
        JSONArray contentJSONArray = new JSONArray();

        Set<AtRange> indexes = new HashSet<>();
        Map<AtRange, Long> uidIndexes = new HashMap<>();
        for (Map.Entry<String, Long> entry : atUserUid.entrySet()) {
            String key = entry.getKey();
            long val = entry.getValue();

            Pattern pattern = Pattern.compile("@" + Pattern.quote(key) + " ");  //昵称里可能带 .*[\ 等正则元字符，不转义会抛 PatternSyntaxException
            Matcher matcher = pattern.matcher(content);
            List<AtRange> mIndex = new ArrayList<>();
            while (matcher.find()) {
                int start = matcher.start();
                // 不包含空格，我直接按照我抓的请求内容弄的
                int end = matcher.end();
                AtRange range = new AtRange(start, end);
                mIndex.add(range);
                uidIndexes.put(range, val);
            }
            indexes.addAll(mIndex);
        }

        ArrayList<AtRange> indexesList = new ArrayList<>(indexes);
        // 多个 @ 时必须按出现位置升序切；HashSet 的顺序不保证升序，乱序时 substring(pos, start) 会 start < pos 直接抛越界
        Collections.sort(indexesList, new Comparator<AtRange>() {
            @Override
            public int compare(AtRange a, AtRange b) {
                return Integer.compare(a.start, b.start);
            }
        });
        int pos = 0;
        for (AtRange index : indexesList) {
            String sub = content.substring(pos, index.start);
            if (!sub.isEmpty()) contentJSONArray.put(Content.create(sub, 1, null));
            String subAt = content.substring(index.start, index.end);
            if (!subAt.isEmpty())
                contentJSONArray.put(Content.create(subAt, 2, String.valueOf(uidIndexes.get(index))));
            pos = index.end;
        }
        // 尾部剩余正文只在这里补一次。旧实现下面还有一行 indexesList.isEmpty() 时整段 content 再 put 一遍，
        // 没有 @ 的文本会被当成两个正文节点发出去（buildContents 的调用方全都会走这里）
        String sub = content.substring(pos);
        if (!sub.isEmpty()) contentJSONArray.put(Content.create(sub, 1, null));
        return contentJSONArray;
    }

    /**
     * 一个 @ 片段在正文里的位置区间。
     * <p>
     * 刻意不用 {@code android.util.Pair}：它在 JVM 单测里是 Android 桩，构造出的对象字段恒为 null，
     * 会让 {@link #parseAtContent} / {@link #buildContents} 完全无法单测。
     */
    private static final class AtRange {
        final int start;
        final int end;

        AtRange(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AtRange)) return false;
            AtRange other = (AtRange) o;
            return start == other.start && end == other.end;
        }

        @Override
        public int hashCode() {
            return start * 31 + end;
        }
    }

    /**
     * 动态点赞/取消赞
     *
     * @param dyid 动态id
     * @param up   是否为点赞
     * @return resultCode
     */
    public static int likeDynamic(long dyid, boolean up) throws IOException {
        String url = "https://api.vc.bilibili.com/dynamic_like/v1/dynamic_like/thumb";
        Response resp = Objects.requireNonNull(NetWorkUtil.post(url, new NetWorkUtil.FormData()
                .put("dynamic_id", dyid)
                .put("up", up ? 1 : 2)
                .put("csrf_token", NetWorkUtil.currentCsrf())
                .toString(), NetWorkUtil.webHeaders));
        try {
            ResponseBody responseBody = resp.body();
            if (responseBody == null) return -1;
            JSONObject result = new JSONObject(responseBody.string());
            return result.getInt("code");
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static int deleteDynamic(long dyid) throws IOException {
        String url = "https://api.vc.bilibili.com/dynamic_svr/v1/dynamic_svr/rm_dynamic";
        Response resp = Objects.requireNonNull(NetWorkUtil.post(url, new NetWorkUtil.FormData()
                .put("dynamic_id", dyid)
                .put("csrf_token", NetWorkUtil.currentCsrf())
                .toString(), NetWorkUtil.webHeaders));
        try {
            ResponseBody body = resp.body();
            if (body == null) return -1;
            JSONObject result = new JSONObject(body.string());
            return result.getInt("code");
        } catch (JSONException ignored) {
            return -1;
        }
    }

    /**
     * 寻找用户（完全匹配），仍然自己瞎扒的，不清楚是否有更好方案
     *
     * @param name 名称
     * @return 用户UID，未找到返回-1
     */
    public static long mentionAtFindUser(String name) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/mention/search?keyword=" + name;

        JSONObject resp = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
        if (resp.has("data") && !resp.isNull("data")) {
            JSONObject data = resp.getJSONObject("data");
            if (data.has("groups") && !data.isNull("groups")) {
                JSONArray groups = data.getJSONArray("groups");
                for (int i = 0; i < groups.length(); i++) {
                    JSONArray items = groups.getJSONObject(i).getJSONArray("items");
                    for (int j = 0; j < items.length(); j++) {
                        if (items.getJSONObject(j).getString("name").equals(name))
                            return Long.parseLong(items.getJSONObject(j).getString("uid"));
                    }
                }
            }
        }

        return -1;
    }

    public static long getDynamicList(List<Dynamic> dynamicList, long offset, long mid, String type) throws IOException, JSONException {
        String url;
        String features = "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,forwardListHidden,decorationCard,commentsNewVersion,onlyfansAssetsV2,ugcDelete,onlyfansQaCard,avatarAutoTheme,sunflowerStyle,eva3CardOpus,eva3CardVideo,eva3CardComment";
        if (mid == 0) {
            url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all?type=" + type
                    + (offset == 0 ? "" : "&offset=" + offset)
                    + "&features=" + features;
        } else {
            url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space?host_mid=" + mid
                    + "&platform=web"
                    + "&web_location=333.1387"
                    + "&timezone_offset=-480"
                    + (offset == 0 ? "" : "&offset=" + offset)
                    + "&features=" + features;
        }

        // 风控对抗：添加 dm_img 参数后再进行 WBI 签名
        String signedUrl = ConfInfoApi.signWBI(DmImgParamUtil.getDmImgParamsUrl(url));
        JSONObject all = NetWorkUtil.getJson(signedUrl);

        if (all.getInt("code") != 0) throw new JSONException(all.getString("message"));

        JSONObject data = all.getJSONObject("data");

        boolean has_more = data.optBoolean("has_more", false);
        if (!has_more) {
            has_more = data.optInt("has_more", 0) == 1;
        }
        long offset_new = has_more ? Long.parseLong(data.getString("offset")) : -1;

        if (mid == 0) {
            long update_baseline = data.optLong("update_baseline", -1);
            if (update_baseline > -1) SharedPreferencesUtil.putLong("dynamic_update_baseline", update_baseline);
            else if (offset_new != -1) {
                SharedPreferencesUtil.putLong("dynamic_update_baseline", offset_new);
            }
        }

        JSONArray items = data.getJSONArray("items");
        // 整页表情并发预取，逐条 analyzeDynamic 时就是缓存命中（见 EmoteUtil.prefetch）
        EmoteUtil.prefetch(EmoteUrlCollector.fromDynamicItems(items));
        for (int i = 0; i < items.length(); i++) {
            dynamicList.add(analyzeDynamic(items.getJSONObject(i)));
        }

        return offset_new;
    }

    public static Dynamic getDynamic(long id) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/detail?id=" + id
                + "&timezone_offset=-480"
                + "&platform=web"
                + "&features=itemOpusStyle,opusBigCover,onlyfansVote,decorationCard,onlyfansAssetsV2,ugcDelete,onlyfansQaCard,avatarAutoTheme"
                + "&web_location=333.1368";

        // 风控对抗：添加 dm_img 参数后再进行 WBI 签名
        String signedUrl = ConfInfoApi.signWBI(DmImgParamUtil.getDmImgParamsUrl(url));
        JSONObject all = NetWorkUtil.getJson(signedUrl);
        if (all.getInt("code") != 0) throw new JSONException(all.getString("message"));

        JSONObject data = all.getJSONObject("data");
        JSONObject item = data.getJSONObject("item");
        return analyzeDynamic(item);
    }

    public static int checkDynamicUpdate(String type, long updateBaseline) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all/update?type=" + type + "&update_baseline=" + updateBaseline + "&web_location=333.1365";
        JSONObject result = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
        if (result.getInt("code") != 0) throw new JSONException(result.getString("message"));
        if (result.has("data") && !result.isNull("data")) {
            JSONObject data = result.getJSONObject("data");
            return data.optInt("update_num", 0);
        }
        return 0;
    }

    public static Dynamic analyzeDynamic(JSONObject dynamic_json) throws JSONException {
        Logu.v("--------------");
        Dynamic dynamic = new Dynamic();

        if (!dynamic_json.isNull("id_str"))
            try {
                dynamic.dynamicId = Long.parseLong(dynamic_json.optString("id_str", "0"));
            } catch (Exception ignored) {
            }
        else {
            dynamic.dynamicId = 0;
        }
        dynamic.type = dynamic_json.optString("type");

        JSONObject basic = dynamic_json.getJSONObject("basic");
        String comment_id = basic.optString("comment_id_str", "0");
        if (!TextUtils.isEmpty(comment_id))
            try {
                dynamic.comment_id = Long.parseLong(comment_id);
            } catch (Exception ignored) {
            }
        else
            dynamic.comment_id = 0;

        dynamic.comment_type = basic.optInt("comment_type");

        Logu.v("id", String.valueOf(dynamic.dynamicId));
        Logu.v("oid", String.valueOf(dynamic.comment_id));
        Logu.v("type", dynamic.type);
        Logu.v("otype", String.valueOf(dynamic.comment_type));

        JSONObject modules = dynamic_json.getJSONObject("modules");

        //发布者
        UserInfo userInfo = new UserInfo();
        if (!modules.isNull("module_author")) {
            JSONObject module_author = modules.getJSONObject("module_author");
            userInfo.mid = module_author.getLong("mid");
            userInfo.name = module_author.getString("name");
            if (!module_author.isNull("following")) {
                // following 字段为整数：1=未关注，2=已关注（非布尔值）
                userInfo.followed = module_author.optInt("following", 0) == 2;
            }
            userInfo.avatar = module_author.getString("face");
            JSONObject vipJson = module_author.optJSONObject("vip");
            if (vipJson != null) {
                userInfo.vip_nickname_color = vipJson.optString("nickname_color", "");
            }
            Logu.v("sender", userInfo.name);
            dynamic.pubTime = module_author.getString("pub_time");
        }
        dynamic.userInfo = userInfo;

        if (dynamic.type.equals("DYNAMIC_TYPE_NONE")) {
            dynamic.content = "[动态不存在]";
            return dynamic;
        }

        //动态主体
        if (!modules.isNull("module_dynamic")) {
            JSONObject module_dynamic = modules.getJSONObject("module_dynamic");

            //内容
            if (!module_dynamic.isNull("desc")) {
                JSONObject desc = module_dynamic.getJSONObject("desc");
                JSONArray rich_text_nodes = desc.optJSONArray("rich_text_nodes");
                dynamic.content = analyzeTextContent(rich_text_nodes);
            } else dynamic.content = "";

            //这里面什么都有，直译为主要的
            if (!module_dynamic.isNull("major")) {
                JSONObject major = module_dynamic.getJSONObject("major");
                String major_type = major.optString("type", "UNKNOWN");
                dynamic.major_type = major_type;
                Logu.d(major_type);
                try {
                    switch (major_type) {
                        case "MAJOR_TYPE_ARCHIVE":
                            dynamic.major_object = analyzeVideoCard(major.getJSONObject("archive"));
                            break;
                        case "MAJOR_TYPE_UGC_SEASON":
                            dynamic.major_object = analyzeVideoCard(major.getJSONObject("ugc_season"));
                            break;
                        case "MAJOR_TYPE_PGC":
                            JSONObject bangumi = major.getJSONObject("pgc");
                            VideoCard card = new VideoCard();
                            card.type = "media_bangumi";
                            card.aid = BangumiApi.getMdidFromEpid(bangumi.optLong("epid", 0));
                            card.title = bangumi.optString("title", "未知番剧");
                            card.cover = bangumi.optString("cover", "");
                            JSONObject pgcStat = bangumi.optJSONObject("stat");
                            card.view = pgcStat != null ? pgcStat.optString("play", "0") : "0";
                            dynamic.major_object = card;
                            break;
                        case "MAJOR_TYPE_ARTICLE":
                            JSONObject article = major.getJSONObject("article");
                            dynamic.major_object = new ArticleCard(
                                    article.optString("title", "未知文章"),
                                    article.optLong("id", 0),
                                    (article.has("covers") && !article.isNull("covers") ? article.getJSONArray("covers").optString(0, "") : ""),
                                    "投稿文章",
                                    article.optString("label", "")
                            );
                            break;

                        case "MAJOR_TYPE_DRAW":
                            JSONObject draw = major.getJSONObject("draw");
                            JSONArray items = draw.getJSONArray("items");
                            ArrayList<String> picture_list = new ArrayList<>();
                            for (int i = 0; i < items.length(); i++) {
                                picture_list.add(items.getJSONObject(i).getString("src"));
                            }
                            dynamic.major_object = picture_list;
                            break;

                        case "MAJOR_TYPE_COMMON":
                            dynamic.content = dynamic.content + "\n[无法显示活动类动态的附加内容]";
                            break;

                        case "MAJOR_TYPE_LIVE_RCMD":
                            JSONObject live_rcmd = new JSONObject(major.getJSONObject("live_rcmd").getString("content")).getJSONObject("live_play_info");
                            LiveRoom room = new LiveRoom();
                            room.roomid = live_rcmd.getLong("room_id");
                            room.title = live_rcmd.getString("title");
                            room.cover = live_rcmd.getString("cover");
                            room.online = live_rcmd.getInt("online");
                            dynamic.major_object = room;
                            dynamic.content = (TextUtils.isEmpty(dynamic.content) ? "" : dynamic.content + "\n");
                            break;

                        case "MAJOR_TYPE_LIVE":
                            JSONObject live = major.getJSONObject("live");
                            LiveRoom room_card = new LiveRoom();
                            room_card.roomid = live.getLong("id");
                            room_card.title = live.getString("title");
                            room_card.cover = live.getString("cover");
                            dynamic.major_object = room_card;
                            dynamic.content = (TextUtils.isEmpty(dynamic.content) ? "" : dynamic.content + "\n");
                            break;

                        case "MAJOR_TYPE_OPUS":
                            JSONObject opusJson = major.getJSONObject("opus");

                            String title = opusJson.optString("title");
                            if (!TextUtils.isEmpty(title) && !"null".equals(title))
                                dynamic.title = title;

                            JSONArray pics = opusJson.optJSONArray("pics");
                            if (pics != null) {
                                ArrayList<String> opusPicList = new ArrayList<>();
                                for (int i = 0; i < pics.length(); i++)
                                    opusPicList.add(pics.getJSONObject(i).optString("url"));

                                dynamic.major_object = opusPicList;
                            }

                            JSONObject summary = opusJson.optJSONObject("summary");
                            if (summary != null)
                                dynamic.content = analyzeTextContent(summary.optJSONArray("rich_text_nodes"));
                            else
                                dynamic.content = "";

                            break;

                        default:
                            dynamic.content = dynamic.content + "\n[*哔哩终端暂时无法查看此动态的附加内容QwQ|类型：" + major_type + "]";
                            break;
                    }
                } catch (JSONException e) {
                    // 风控场景下API可能返回不完整数据，捕获解析异常并降级显示
                    Logu.d("DynamicRetry", "动态主体解析异常（可能因风控导致数据缺失）: " + e.getMessage());
                    dynamic.content = dynamic.content + "\n[*该动态附加内容暂无法显示|类型：" + major_type + "]";
                }
            }
            if (modules.has("module_additional") && !modules.isNull("module_additional")) {
                try {
                    JSONObject module_additional = modules.getJSONObject("module_additional");
                    String addiType = module_additional.getString("type");
                    dynamic.additional_type = addiType;
                    if (addiType.equals("ADDITIONAL_TYPE_UGC")) {
                        dynamic.major_type = "MAJOR_TYPE_ARCHIVE";
                        dynamic.major_object = analyzeVideoCard(module_additional.getJSONObject("ugc"));
                    } else if (addiType.equals("ADDITIONAL_TYPE_VOTE")) {
                        JSONObject voteJson = module_additional.getJSONObject("vote");
                        dynamic.vote = VoteApi.parseVoteInfo(voteJson);
                    } else Logu.v("addi", addiType);
                } catch (JSONException e) {
                    Logu.d("DynamicRetry", "附加模块解析异常: " + e.getMessage());
                }
            }
        }

        // 动态Stats
        if (modules.has("module_stat") && !modules.isNull("module_stat")) {
            JSONObject module_stat = modules.getJSONObject("module_stat");
            JSONObject like = module_stat.optJSONObject("like");
            Stats stats = new Stats();
            if (like != null) {
                stats.like = like.optInt("count", 0);
                stats.liked = like.optBoolean("status", false);
                stats.like_disabled = like.optBoolean("forbidden", false);
            }
            // 评论数（task-8）：`DynamicHolder` 的「评论」入口要拿它显示 万/亿 数字，
            // 之前这里只有 like，stats.reply 恒为 0 → 入口永远只能显示「评论」两个字。
            // 字段位置与 like.count 同层（`module_stat.comment.count`），缺失时保持 0 即显示标签。
            JSONObject comment = module_stat.optJSONObject("comment");
            if (comment != null) {
                stats.reply = comment.optInt("count", 0);
                stats.reply_disabled = comment.optBoolean("forbidden", false)
                        || comment.optBoolean("hidden", false);
            }
            // TODO 转发

            dynamic.stats = stats;
        }

        if (modules.has("module_more") && !modules.isNull("module_more")) {
            List<String> supportItemTypes = new ArrayList<>();
            JSONArray three_point_items = modules.getJSONObject("module_more").getJSONArray("three_point_items");
            for (int i = 0; i < three_point_items.length(); i++) {
                supportItemTypes.add(three_point_items.getJSONObject(i).getString("type"));
            }
            dynamic.canDelete = supportItemTypes.contains("THREE_POINT_DELETE");
            // 能不能编辑由服务端下发的三点菜单决定：只有自己的动态带 THREE_POINT_EDIT。
            // 不能用 canDelete 顶替——删除与编辑是两个独立开关。
            dynamic.canEdit = supportItemTypes.contains("THREE_POINT_EDIT");
        }

        // 新版动态API：解析 module_tag（置顶标记）
        if (modules.has("module_tag") && !modules.isNull("module_tag")) {
            JSONObject module_tag = modules.getJSONObject("module_tag");
            String tagText = module_tag.optString("text", "");
            dynamic.isTop = "置顶".equals(tagText);
        }

        if (dynamic_json.has("orig") && !dynamic_json.isNull("orig")) {
            dynamic.dynamic_forward = analyzeDynamic(dynamic_json.getJSONObject("orig"));
        }

        return dynamic;
    }

    private static VideoCard analyzeVideoCard(JSONObject jsonObject) throws JSONException {
        String cover = jsonObject.optString("cover", "");
        String title = jsonObject.optString("title", "未知视频");
        String bvid = jsonObject.optString("bvid", "");

        String play = "0";
        JSONObject stat = jsonObject.optJSONObject("stat");
        if (stat != null) {
            play = stat.optString("play", "0");
            if (play.isEmpty()) play = String.valueOf(stat.optInt("view", 0));
        }

        long aid = 0;
        String aidStr = jsonObject.optString("aid", "0");
        try {
            aid = Long.parseLong(aidStr);
        } catch (NumberFormatException ignored) {
        }

        return new VideoCard(
                title,
                "投稿视频",
                play,
                cover,
                aid,
                bvid
        );
    }

    /**
     * 从 {@code rich_text_nodes} 里挑出全部话题节点（{@code RICH_TEXT_NODE_TYPE_TOPIC}）。
     *
     * 纯函数，不碰 Context/表情，便于 JVM 单测；同时也是「话题解析没漏」的守卫——
     * 动态正文、动态摘要、Opus 段落三处的 TOPIC 分支都靠它对齐。
     *
     * 返回 {@link TopicNode} 而非 {@code android.util.Pair}：后者在 JVM 单测里是 Android 桩，
     * 构造出的对象字段恒为 null（详见 TopicNode 类注释）。
     *
     * @param richTextNodes 服务端下发的富文本节点数组，可为 null
     * @return 顺序与服务端一致；无话题时返回空列表
     */
    public static java.util.List<TopicNode> parseTopicNodes(JSONArray richTextNodes) {
        java.util.List<TopicNode> topics = new java.util.ArrayList<>();
        if (richTextNodes == null) return topics;
        for (int i = 0; i < richTextNodes.length(); i++) {
            JSONObject node = richTextNodes.optJSONObject(i);
            if (node == null) continue;
            if (!"RICH_TEXT_NODE_TYPE_TOPIC".equals(node.optString("type"))) continue;
            String text = node.optString("text");
            if (text.isEmpty()) continue;
            // 没有 jump_url 也照样收集：话题仍要染主色展示，只是不可点。
            topics.add(new TopicNode(text, node.optString("jump_url")));
        }
        return topics;
    }

    private static SpannableStringBuilder analyzeTextContent(JSONArray rich_text_nodes) {
        if (rich_text_nodes == null) return new SpannableStringBuilder("[动态内容解析异常]");

        ArrayList<Emote> emoteList = new ArrayList<>();
        ArrayList<At> atList = new ArrayList<>();
        SpannableStringBuilder content = new SpannableStringBuilder();
        for (int i = 0; i < rich_text_nodes.length(); i++) {
            JSONObject rich_text_node = rich_text_nodes.optJSONObject(i);
            if (rich_text_node == null) continue;
            String type = rich_text_node.optString("type");
            switch (type) {
                case "RICH_TEXT_NODE_TYPE_EMOJI":
                    content.append(rich_text_node.optString("text"));
                    JSONObject emoji = rich_text_node.optJSONObject("emoji");
                    if (emoji == null) continue;
                    emoteList.add(new Emote(emoji.optString("text"), emoji.optString("icon_url"), emoji.optInt("size")));
                    break;
                case "RICH_TEXT_NODE_TYPE_AT":
                    Pair<Integer, Integer> indexs = StringUtil.appendString(content, rich_text_node.optString("text"));
                    atList.add(new At(rich_text_node.optLong("rid"), indexs.first, indexs.second));
                    break;
                case "RICH_TEXT_NODE_TYPE_TOPIC":
                    // 话题（#话题名#）：上主题主色 + 点击跳转。
                    // 此前没有本分支，话题落进 default 只被当普通文本 append，
                    // 于是既不蓝也点不动 —— 用户报告的"#A# 没有正常解析"根因就在这里。
                    Pair<Integer, Integer> topicIndexs = StringUtil.appendString(content, rich_text_node.optString("text"));
                    StringUtil.setSingleTopic(content, topicIndexs.first, topicIndexs.second,
                            rich_text_node.optString("jump_url"));
                    break;
                case "RICH_TEXT_NODE_TYPE_WEB":
                    content.append(rich_text_node.optString("orig_text"));
                    break;
                case "RICH_TEXT_NODE_TYPE_TEXT":
                default:
                    content.append(rich_text_node.optString("text"));
                    break;
            }
        }

        EmoteUtil.textReplaceEmote(content.toString(), emoteList, 1.0f, BiliTerminal.context, content);
        for (At at : atList) {
            StringUtil.setSingleAt(content, at);
        }

        return content;
    }

    public static class Content {
        public static JSONObject create(@NonNull String raw_text, int type, String biz_id) throws JSONException {
            return new JSONObject()
                    .put("raw_text", raw_text)
                    .put("type", type)
                    .put("biz_id", biz_id == null ? "" : biz_id);
        }
    }

    public static List<UpInfo> getRecentUpList() throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/portal";
        JSONObject result = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
        if (result.getInt("code") != 0) throw new JSONException(result.getString("message"));
        
        List<UpInfo> upList = new ArrayList<>();
        if (result.has("data") && !result.isNull("data")) {
            JSONObject data = result.getJSONObject("data");
            if (data.has("up_list") && !data.isNull("up_list")) {
                JSONArray upListArray = data.getJSONArray("up_list");
                for (int i = 0; i < upListArray.length(); i++) {
                    JSONObject upJson = upListArray.getJSONObject(i);
                    UpInfo upInfo = new UpInfo();
                    upInfo.mid = upJson.getLong("mid");
                    upInfo.uname = upJson.getString("uname");
                    upInfo.face = upJson.getString("face");
                    upInfo.has_update = upJson.optBoolean("has_update", false);
                    upList.add(upInfo);
                }
            }
        }
        return upList;
    }

    public static class UpInfo {
        public long mid;
        public String uname;
        public String face;
        public boolean has_update;
    }
}
