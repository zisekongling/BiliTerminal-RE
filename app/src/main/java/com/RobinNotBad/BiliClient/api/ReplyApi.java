package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.R;

import com.RobinNotBad.BiliClient.BiliTerminal;

import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.RobinNotBad.BiliClient.model.ContentType;
import com.RobinNotBad.BiliClient.model.Reply;
import com.RobinNotBad.BiliClient.util.EmoteUrlCollector;
import com.RobinNotBad.BiliClient.util.EmoteUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.Result;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Objects;

//腕上哔哩那边注释里写了一连串的麻烦麻烦麻烦，顿时预感不妙
//其实还好
//用Log直接替代注释了，应该都能看懂吧
//2023-07-22

public class ReplyApi {

    public static final int REPLY_TYPE_VIDEO_CHILD = 0;
    public static final int REPLY_TYPE_VIDEO = 1;
    public static final int REPLY_TYPE_ARTICLE = 12;
    public static final int REPLY_TYPE_DYNAMIC_CHILD = 11;
    public static final int REPLY_TYPE_DYNAMIC = 17;
    public static final String TOP_TIP = "[置顶]";

    /**
     * 图片动态的业务标识。
     *
     * <p><b>评论图也用这个</b>：图床 `upload_bfs` 的 `biz` 只决定图片落在哪个 BFS 目录，
     * 而评论的 `pictures` 只认 `/bfs/new_dyn/` 下的图（与官方客户端一致）。
     * 26.10.02 曾把评论图改成自造的 `new_reply`，结果图能传上去、发评论时被服务端以
     * 12066 拒绝（`docs/review/fix-progress.md` §三.107）。这里恢复成 `new_dyn`。
     */
    public static final String BIZ_DYNAMIC = "new_dyn";
    /**
     * 私信图片的业务标识（`/bfs/im/`）。
     *
     * <p>与动态/评论图不同：私信走 IM 图床目录，上游 PiliPlus 同样是 `biz: 'im'`
     * （`lib/pages/whisper_detail/view.dart`）。原先私信错误地复用了评论的 biz。
     */
    public static final String BIZ_MESSAGE = "im";

    /** getRepliesLazy 返回码：请求或解析失败。 */
    public static final int PAGE_ERROR = -1;
    /** getRepliesLazy 返回码：正常拿到一页评论。 */
    public static final int PAGE_OK = 0;
    /** getRepliesLazy 返回码：已经到底了。 */
    public static final int PAGE_END = 1;
    /**
     * getRepliesLazy 返回码：服务端返回了空页，但游标显示还没到底。
     * 这不是"到底了"，上层应当带着原游标重试，否则这一段评论会被整段丢掉。
     */
    public static final int PAGE_EMPTY = 2;

    /**
     * @param originId       评论区id，为评论所属内容的id，例如视频aid
     * @param rpid           父评论的id，无父评论则为0
     * @param pageNumber     分页，需要拉取的评论的页号
     * @param type           评论所属内容类型
     * @param sort           评论区排序方式，0=时间；1=点赞数量；2=回复数量
     * @param replyArrayList 填充数组
     * @return -1错误,0正常，1到底了
     * @throws JSONException json解析异常
     * @throws IOException   网络异常
     */
    public static int getReplies(long originId, long rpid, int pageNumber, ContentType type, int sort, List<Reply> replyArrayList) throws JSONException, IOException {

        String url = "https://api.bilibili.com/x/v2/reply" + (rpid == 0 ? "" : "/reply") + "?pn=" + pageNumber
                + "&type=" + type.getTypeCode() + "&oid=" + originId + "&sort=" + sort + (rpid == 0 ? "" : ("&root=" + rpid));
        JSONObject all = NetWorkUtil.getJson(url);

        //Log.e("debug-评论区",all.toString());

        int size = replyArrayList.size();
        if (all.getInt("code") == 0 && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            JSONObject page = data.getJSONObject("page");
            if (!data.isNull("replies") && page.getInt("size") > 0) {
                if (rpid == 0 && data.has("top_replies") && page.getInt("num") == 1)
                    analyzeReplyArray(true, data.getJSONArray("top_replies"), replyArrayList);
                JSONArray replies = data.getJSONArray("replies");
                analyzeReplyArray(rpid == 0, replies, replyArrayList);
                if (replyArrayList.size() == size) return 1;
                else return 0;
            } else return 1;
        } else return -1;
    }

    public static Result<Reply> getRootReply(ContentType contentType, long originId, long rpid) {
        String url = "https://api.bilibili.com/x/v2/reply/reply" + "?type=" + contentType.getTypeCode() + "&oid=" + originId + "&root=" + rpid;
        try {
            JSONObject json = NetWorkUtil.getJson(url);
            if (json.getInt("code") != 0 || json.isNull("data")) {
                return Result.failure(new Exception(json.getString("message")));
            }
            JSONObject data = json.getJSONObject("data");
            if (data.isNull("root")) {
                return Result.failure(new Exception("未找到根评论"));
            }
            return Result.success(new Reply(true, data.getJSONObject("root")));
        } catch (Exception e) {
            return Result.failure(e);
        }
    }


    /**
     * 获取评论列表（/x/v2/reply/wbi/main）
     *
     * @param oid        评论区oid
     * @param rpid       指定要寻找的评论的rpid
     * @param pagination 页
     * @param type       评论区类型
     * @param sort       排序方式
     * @return 返回码（见 {@link #PAGE_ERROR}/{@link #PAGE_OK}/{@link #PAGE_END}/{@link #PAGE_EMPTY}）与下一页的pagination。
     *         {@link #PAGE_EMPTY} 时第二个值是把原游标原样带回，调用方应带原游标重试而不是当成到底。
     */
    @NonNull
    public static Pair<Integer, String> getRepliesLazy(long oid, long rpid, String pagination, int type, int sort, List<Reply> replyArrayList) throws JSONException, IOException {

        NetWorkUtil.FormData reqData = new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("type", type)
                .put("oid", oid)
                .put("plat", 1)
                .put("web_location", "1315875")
                .put("mode", sort);
        reqData.put("pagination_str", new JSONObject().put("offset", TextUtils.isEmpty(pagination) ? "" : pagination));
        if (rpid > 0) reqData.put("seek_rpid", rpid);
        String url = "https://api.bilibili.com/x/v2/reply/wbi/main" + reqData;

        //Log.e("debug-评论区链接", url);

        JSONObject all = NetWorkUtil.getJson(ConfInfoApi.signWBI(url));

        //Log.e("debug-评论区",all.toString());

        if (all.getInt("code") == 0 && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            JSONObject cursor = data.getJSONObject("cursor");
            JSONObject paginationReply = cursor.optJSONObject("pagination_reply");
            String nextOffset = paginationReply == null ? null : paginationReply.optString("next_offset");
            // 是否真的到底，由服务端的 is_end 与游标共同决定
            boolean isEnd = cursor.optBoolean("is_end", false) || TextUtils.isEmpty(nextOffset);
            if (!data.isNull("replies") && data.getJSONArray("replies").length() > 0) {
                if (rpid <= 0 && data.has("top_replies") && !data.isNull("top_replies") && cursor.getBoolean("is_begin"))
                    analyzeReplyArray(true, data.getJSONArray("top_replies"), replyArrayList);
                JSONArray replies = data.getJSONArray("replies");
                analyzeReplyArray(true, replies, replyArrayList);
                if (isEnd) {
                    return new Pair<>(PAGE_END, "");
                } else {
                    return new Pair<>(PAGE_OK, nextOffset);
                }
            } else if (rpid <= 0 && data.has("top_replies") && !data.isNull("top_replies") && cursor.getBoolean("is_begin")) {
                analyzeReplyArray(true, data.getJSONArray("top_replies"), replyArrayList);
                return new Pair<>(PAGE_END, "");
            } else {
                // 空页。服务端偶发会回一页空 replies，如果这时游标还没到底，就不能当成"评论到底"，
                // 否则这一段评论会整段缺失。返回 PAGE_EMPTY 并把原游标带回去让上层重试。
                // 首页（pagination 为空）不给重试机会，避免"这个视频本来就没评论"时白白重试 5 次。
                if (isEnd || TextUtils.isEmpty(pagination)) {
                    return new Pair<>(PAGE_END, "");
                } else {
                    return new Pair<>(PAGE_EMPTY, pagination);
                }
            }
        } else return new Pair<>(PAGE_ERROR, "");
    }  //-1错误,0正常，1到底了，2空页(未到底，可带原游标重试)

    public static void analyzeReplyArray(boolean isRoot, JSONArray replies, List<Reply> replyArrayList) throws JSONException {
        // 先把整页（含楼中楼）的表情并发预取，逐条构造 Reply 时就是缓存命中（见 EmoteUtil.prefetch）
        EmoteUtil.prefetch(EmoteUrlCollector.fromReplies(replies));
        for (int i = 0; i < replies.length(); i++) {
            JSONObject reply = replies.getJSONObject(i);
            Reply replyReturn = new Reply(isRoot, reply);
            replyArrayList.add(replyReturn);
        }
    }

    public static Pair<Integer, Reply> sendReply(long oid, long root, long parent, String text, int type) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/reply/add";
        String arg = "oid=" + oid + "&type=" + type + (root == 0 ? "" : ("&root=" + root + "&parent=" + parent))
                + "&message=" + URLEncoder.encode(text, "UTF-8") + "&jsonp=jsonp&csrf=" + NetWorkUtil.currentCsrf();
        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-发送评论", result.toString());
        JSONObject reply = null;
        if (result.has("data") && !result.isNull("data") && result.getJSONObject("data").has("reply") && !result.getJSONObject("data").isNull("reply")) {
            reply = result.getJSONObject("data").getJSONObject("reply");
        }
        return new Pair<>(result.getInt("code"), reply == null ? null : new Reply(root != 0, reply));
    }

    public static Pair<Integer, Reply> sendReply(long oid, long root, long parent, String text) throws IOException, JSONException {
        return sendReply(oid, root, parent, text, REPLY_TYPE_VIDEO);
    }

    public static Pair<Integer, Reply> sendDynamicReply(long oid, long root, long parent, String text) throws IOException, JSONException {
        return sendReply(oid, root, parent, text, REPLY_TYPE_DYNAMIC);
    }

    /** 评论图片上传结果。 */
    public static class UploadImageData {
        public String image_url;
        public int image_width;
        public int image_height;
        /**
         * 图片大小，单位 KB。
         *
         * <p><b>必须用 double</b>：服务端返回的是小数 KB，官方响应示例为
         * {@code "img_size": 6.261}（bilibili-API/docs/dynamic/publish.md:66），
         * 卡片接口里还有 {@code 1425.97998046875}（docs/dynamic/card_info.md:133）。
         * 上游 PiliPlus 同样是按 double 读的
         * （lib/models_new/upload_bfs/data.dart:5,19）。历史上这里是 {@code long} +
         * {@code optLong}，会把小数截断（6.261 → 6、662.6 → 662）。
         */
        public double img_size;
    }

    /**
     * 上传评论图片（大会员带图评论）。
     *
     * <p>这个重载假定图片已被压成 JPEG，只为兼容旧调用；能拿到真实类型时请用
     * {@link #uploadReplyImage(byte[], String, String, String)}，
     * 否则 GIF/PNG 原样上传会被服务端按 JPEG 处理，动图变静帧、透明通道丢失。
     *
     * @param imageData JPEG 图片数据
     * @param fileName  文件名（含扩展名）
     */
    public static Result<UploadImageData> uploadReplyImage(byte[] imageData, String fileName) {
        return uploadReplyImage(imageData, fileName, "image/jpeg", BIZ_DYNAMIC);
    }

    /**
     * 图片上传失败。
     *
     * <p>必须带上服务端 `code`：`upload_bfs` 失败时只会给一个数字码（4100001 参数错误、
     * -101 未登录、-352 风控…），而界面原先只显示「图片上传失败」，用户和开发者都无从判断。
     */
    public static class UploadException extends Exception {
        public final int code;
        public final String serverMessage;

        public UploadException(int code, String serverMessage) {
            super("上传图片失败，code=" + code
                    + (serverMessage == null || serverMessage.isEmpty() ? "" : "，" + serverMessage));
            this.code = code;
            this.serverMessage = serverMessage == null ? "" : serverMessage;
        }
    }

    /**
     * 上传评论图片（大会员带图评论）。
     *
     * @param imageData 图片数据
     * @param fileName  文件名（含扩展名）
     * @param mimeType  图片真实的 MIME 类型。必须与数据真实格式一致（GIF/PNG 原样透传时尤其重要），
     *                  否则服务端按 image/jpeg 解码会丢掉动图帧与透明通道
     * @param biz       业务标识，评论图与动态图都传 {@link #BIZ_DYNAMIC}，私信图传 {@link #BIZ_MESSAGE}
     */
    public static Result<UploadImageData> uploadReplyImage(byte[] imageData, String fileName, String mimeType, String biz) {
        String url = "https://api.bilibili.com/x/dynamic/feed/draw/upload_bfs";
        String cookiesStr = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookiesStr);
        try {
            okhttp3.RequestBody fileBody = okhttp3.RequestBody.create(okhttp3.MediaType.parse(mimeType), imageData);
            okhttp3.MultipartBody multipartBody = new okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart("file_up", fileName, fileBody)
                    .addFormDataPart("category", "daily")
                    .addFormDataPart("biz", biz)
                    .addFormDataPart("csrf", csrf)
                    .build();
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .post(multipartBody)
                    .addHeader("Cookie", cookiesStr)
                    .addHeader("Referer", "https://www.bilibili.com/")
                    .addHeader("Origin", "https://www.bilibili.com")
                    .addHeader("User-Agent", NetWorkUtil.USER_AGENT_WEB)
                    .addHeader("Accept", "application/json, text/plain, */*")
                    .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .build();
            okhttp3.Response response = NetWorkUtil.getOkHttpInstance().newCall(request).execute();
            String json = response.body() == null ? "" : response.body().string();
            JSONObject result = new JSONObject(json);
            int code = result.optInt("code", -1);
            String serverMessage = result.optString("message", "");
            if (code != 0 || result.isNull("data")) {
                // 只记 code/message：响应体里没有凭据，但整包 json 也没必要进日志
                Log.e("debug-上传图片", "code=" + code + " message=" + serverMessage + " biz=" + biz);
                return Result.failure(new UploadException(code, serverMessage));
            }
            JSONObject data = result.getJSONObject("data");
            UploadImageData d = new UploadImageData();
            d.image_url = data.optString("image_url", "");
            d.image_width = data.optInt("image_width", 0);
            d.image_height = data.optInt("image_height", 0);
            d.img_size = data.optDouble("img_size", 0);
            return Result.success(d);
        } catch (Exception e) {
            return Result.failure(e);
        }
    }

    /**
     * 把已上传成功的评论图片拼成 {@code pictures} 参数（JSON 数组字符串）。
     *
     * <p>字段名与单位以服务端文档为准（bilibili-API/docs/comment/readme.md:289-296）：
     * {@code img_src} 图片地址、{@code img_width} 宽、{@code img_height} 高、
     * {@code img_size} 大小（**单位 KB，小数**）。与上游 PiliPlus
     * {@code lib/pages/common/publish/common_rich_text_pub_page.dart:519-524} 的组装方式一致。
     *
     * <p>纯函数：不读全局状态、不碰界面，便于 JVM 单测（AGENTS.md「请求与解析分离」）。
     *
     * @param images 已上传成功的图片；为 null 时按空列表处理
     * @return JSON 数组字符串；没有任何图片时返回空串，调用方据此不带 pictures 参数
     */
    public static String buildPictures(List<UploadImageData> images) {
        if (images == null || images.isEmpty()) return "";
        JSONArray jsonArray = new JSONArray();
        for (UploadImageData data : images) {
            if (data == null) continue;
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("img_src", data.image_url);
                jsonObject.put("img_width", data.image_width);
                jsonObject.put("img_height", data.image_height);
                // 不能取整：服务端返回的是小数 KB，截断会改变图片大小语义
                jsonObject.put("img_size", data.img_size);
            } catch (JSONException e) {
                // put 在 key 非 null 时不会抛；真抛了也是脏数据，跳过这一张而不是整条评论发不出去
                continue;
            }
            jsonArray.put(jsonObject);
        }
        if (jsonArray.length() == 0) return "";
        return jsonArray.toString();
    }

    /**
     * 发表评论的完整结果。
     *
     * <p>原来的返回值只有 code 和 reply，服务端那句 `message` 被丢掉了 —— 遇到未收录的码
     * （例如带图评论的 12066）时，界面上只剩一个数字，用户报障和自查都无从下手。
     */
    public static class SendReplyResult {
        public final int code;
        public final String message;
        public final Reply reply;

        public SendReplyResult(int code, String message, Reply reply) {
            this.code = code;
            this.message = message == null ? "" : message;
            this.reply = reply;
        }
    }

    /** 发送带图评论（pictures 为评论图片 JSON 数组字符串）。 */
    public static Pair<Integer, Reply> sendReply(long oid, long root, long parent, String text, int type, String pictures) throws IOException, JSONException {
        SendReplyResult result = sendReplyDetailed(oid, root, parent, text, type, pictures);
        return new Pair<>(result.code, result.reply);
    }

    /**
     * 发送评论并带回服务端 message。
     *
     * <p>只给需要把「未收录错误码 + 服务端原话」展示给用户的调用方用（带图评论页）；
     * 其余调用方继续用 {@link #sendReply(long, long, long, String, int, String)}。
     *
     * @param pictures 评论图片 JSON 数组字符串；没有图片传空串
     */
    public static SendReplyResult sendReplyDetailed(long oid, long root, long parent, String text, int type, String pictures) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/reply/add";
        String arg = "oid=" + oid + "&type=" + type + (root == 0 ? "" : ("&root=" + root + "&parent=" + parent))
                + "&message=" + URLEncoder.encode(text, "UTF-8")
                + (pictures.isEmpty() ? "" : ("&pictures=" + URLEncoder.encode(pictures, "UTF-8")))
                + "&jsonp=jsonp&csrf=" + NetWorkUtil.currentCsrf();
        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-发送评论", result.toString());
        int code = result.getInt("code");
        String message = result.optString("message", "");
        JSONObject reply = null;
        if (result.has("data") && !result.isNull("data") && result.getJSONObject("data").has("reply") && !result.getJSONObject("data").isNull("reply")) {
            reply = result.getJSONObject("data").getJSONObject("reply");
        }
        return new SendReplyResult(code, message, reply == null ? null : new Reply(root != 0, reply));
    }

    /**
     * 给评论点赞/取消点赞。
     *
     * <p>这个重载固定按视频评论区（type=1）发送，只为兼容旧调用；新代码请用
     * {@link #likeReply(long, long, int, boolean)} 并传入真实评论区类型，
     * 否则对动态/专栏的评论点赞会被服务端静默拒绝（评论数不动、也不报错）。
     */
    public static int likeReply(long oid, long root, boolean action) throws IOException, JSONException {
        return likeReply(oid, root, REPLY_TYPE_VIDEO, action);
    }

    /**
     * 给评论点赞/取消点赞。
     *
     * @param oid    oid
     * @param root   要操作的评论 rpid
     * @param type   必须是该评论所属的评论区类型（1=视频 11=图片动态 12=专栏 17=文字动态等）。
     *               这里原来硬编码为 1，导致给动态/专栏的评论点赞被服务端拒绝，
     *               故拆出这个带 type 的重载，调用方必须传对。
     * @param action true=点赞 false=取消
     * @return 返回码
     */
    public static int likeReply(long oid, long root, int type, boolean action) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/reply/action";
        String arg = "oid=" + oid + "&type=" + type + "&rpid=" + root + "&action=" + (action ? "1" : "0") + "&jsonp=jsonp&csrf=" + NetWorkUtil.currentCsrf();
        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-点赞评论", result.toString());
        return result.getInt("code");
    }

    /**
     * 点踩/取消点踩评论（{@code POST https://api.bilibili.com/x/v2/reply/hate}）。
     *
     * <p>服务端语义：点踩成功会同时消去该评论的点赞，反向亦然（见 bilibili-API/docs/comment/action.md）。
     * 因此调用方成功后必须把 {@code liked}/{@code disliked} 两个状态**同时**改成互斥值，
     * 否则本地状态会和下一次拉取到的 {@code action} 字段对不上。
     *
     * @param oid    目标评论区 id
     * @param rpid   目标评论 rpid
     * @param type   评论区类型（与 {@link #likeReply(long, long, int, boolean)} 同口径）
     * @param action true=点踩 false=取消点踩
     * @return 返回码（0 成功，其余见 {@link #actionErrorMsg(int)}）
     */
    public static int dislikeReply(long oid, long rpid, int type, boolean action) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/reply/hate";
        String reqBody = new NetWorkUtil.FormData()
                .put("type", type)
                .put("oid", oid)
                .put("rpid", rpid)
                .put("action", action ? 1 : 0)
                .put("csrf", NetWorkUtil.currentCsrf())
                .toString();
        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, reqBody, NetWorkUtil.webHeaders).body()).string());
        return result.getInt("code");
    }

    /**
     * 纯逻辑：评论的 action 字段 → 是否已点赞。
     *
     * <p>该字段含义见 bilibili-API/docs/comment/readme.md：0=无、1=已点赞、2=已点踩。
     * 原实现写成 {@code action == 1}，把 2（已点踩）和 0（无操作）混为一谈，
     * 于是「我踩过的评论」重进页面后显示成「没操作过」。
     */
    public static boolean isLikedAction(int action) {
        return action == 1;
    }

    /** 纯逻辑：评论的 action 字段 → 是否已点踩。见 {@link #isLikedAction(int)}。 */
    public static boolean isDislikedAction(int action) {
        return action == 2;
    }

    /**
     * 纯逻辑：点赞/点踩接口错误码 → 给用户看的中文提示。
     *
     * <p>这些接口失败时不会抛异常，只回一个 code，所以「点了没反应」到底是
     * 没登录、csrf 失效还是被限流，只能靠这张表区分，否则用户只看到一句「失败」。
     *
     * @param code 接口返回码
     * @return 中文提示；成功（0）返回空串，表示无需提示
     */
    public static String actionErrorMsg(int code) {
        switch (code) {
            case 0:
                return "";
            case -101:
                return ApiErrorText.loginRequired();
            case -102:
                return "账号已被封停";
            case -111:
                return ApiErrorText.tokenExpired();
            case -400:
                return ApiErrorText.requestIllegal();
            case -404:
                return "评论不存在";
            case -509:
                return "操作过于频繁，请稍后再试";
            case 12002:
                return "评论区已关闭";
            case 12004:
                return "禁止对该评论点赞或点踩";
            case 12006:
                return "没有这条评论";
            case 12009:
                return "评论主体类型不合法";
            case 12011:
                return "不合法的赞或踩";
            case 12029:
                return "已经有置顶评论了，请先取消原置顶";
            case 12030:
                return "只能置顶一级评论";
            case 65004:
                return "取消赞失败：没有点过赞";
            case 65005:
                return "取消踩失败：没有点过踩";
            case 65006:
                return "已经点过赞了";
            case 65007:
                return "已经点过踩了";
            default:
                return ApiErrorText.opFailed(code);
        }
    }

    /**
     * 纯逻辑：现在能不能发送评论。
     *
     * <p>带图评论要走「压缩 → 上传图床 → 拿 image_url → 拼 pictures 再发评论」这条链，
     * 图还没传完就点发送的话，调用方拼出来的 pictures 只含**已经成功**的那几张，
     * 评论会「少图发出且没有任何提示」（见 {@code activity/reply/WriteReplyActivity.kt}）。
     * 所以发送前必须先看还有几张图片在上传。
     *
     * @param pendingUploads 仍在上传中的图片张数
     * @return true 表示可以发送
     */
    public static boolean canSendReply(int pendingUploads) {
        return pendingUploads <= 0;
    }

    /**
     * 纯逻辑：还有图片在上传时的提示文案。
     *
     * @param pendingUploads 仍在上传中的图片张数
     * @return 提示文案；没有图片在上传时返回空串
     */
    public static String uploadPendingTip(int pendingUploads) {
        if (pendingUploads <= 0) return "";
        return "还有 " + pendingUploads + " 张图片正在上传，请稍候";
    }

    /**
     * 删除评论
     *
     * @param oid  oid
     * @param rpid rpid
     * @param type 评论区类型
     * @return 返回码
     */
    public static int deleteReply(long oid, long rpid, int type) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/reply/del";
        String reqBody = new NetWorkUtil.FormData()
                .put("type", type)
                .put("oid", oid)
                .put("rpid", rpid)
                .put("csrf", NetWorkUtil.currentCsrf())
                .toString();
        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, reqBody, NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-点赞评论", result.toString());
        return result.getInt("code");
    }

    /**
     * 纯逻辑：置顶参数的取值。
     *
     * <p>接口文档（bilibili-API/docs/comment/action.md:411-418）写的是 {@code action}
     * 默认 0，且「0=取消置顶、1=设为置顶」——**与直觉相反**（一般 1 才是"有效"，
     * 但这里 1 是"设为置顶"、0 是"取消"）。写反了不会报错，只会静默地把置顶做成取消，
     * 所以抽成纯函数并由单测钉死。
     *
     * @param top true=设为置顶，false=取消置顶
     * @return 接口的 action 取值
     */
    public static int topActionFor(boolean top) {
        return top ? 1 : 0;
    }

    /**
     * 置顶 / 取消置顶评论。
     *
     * <p>只能操作**自己管理的评论区**（视频 UP 主、合作稿 staff）里的**一级评论**；
     * 服务端一个评论区只有一个置顶位，已有置顶时再置顶别的评论会回 12029，
     * 调用方要先取消原置顶（或用 {@link #actionErrorMsg(int)} 把它翻译给用户）。
     *
     * <p>置顶状态在客户端靠 {@code reply_control.is_up_top} 体现（见 {@code model/Reply.java}），
     * 该字段不在接口快照里，属未文档化字段。
     *
     * @param oid  评论区 oid
     * @param rpid 要置顶的一级评论 rpid
     * @param type 评论区类型
     * @param top  true=置顶，false=取消置顶
     * @return 返回码，0 表示成功
     */
    public static int topReply(long oid, long rpid, int type, boolean top) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/reply/top";
        String reqBody = new NetWorkUtil.FormData()
                .put("type", type)
                .put("oid", oid)
                .put("rpid", rpid)
                .put("action", topActionFor(top))
                .put("csrf", NetWorkUtil.currentCsrf())
                .toString();
        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, reqBody, NetWorkUtil.webHeaders).body()).string());
        return result.getInt("code");
    }

    public static long getReplyCount(long oid, int type) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/v2/reply/count?oid=" + oid + "&type=" + type;
        JSONObject all = NetWorkUtil.getJson(url);
        if (all.has("data") && (!all.isNull("data"))) {
            return all.getJSONObject("data").getLong("count");
        }
        return 0;
    }
}
