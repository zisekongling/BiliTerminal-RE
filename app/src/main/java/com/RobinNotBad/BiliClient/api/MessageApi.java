package com.RobinNotBad.BiliClient.api;

import android.text.SpannableString;
import android.util.Pair;

import com.RobinNotBad.BiliClient.model.MessageCard;
import com.RobinNotBad.BiliClient.model.Reply;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.OptionalFeatures;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Response;

public class MessageApi {
    private static JSONObject getUnreadData() throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/msgfeed/unread";
        JSONObject all = NetWorkUtil.getJson(url);
        if (all.has("data") && !all.isNull("data")) {
            return all.getJSONObject("data");
        }
        return new JSONObject();
    }

    private static final Pattern BVID_PATTERN = Pattern.compile("BV[0-9A-Za-z]{10}");

    /**
     * 从消息里的 uri 抠出 BV 号（带 "BV" 前缀），抠不到返回空串。
     * uri 形如 `https://www.bilibili.com/video/BV1xx411c7mD?p=1#…`，后缀不固定，
     * 所以不能像 video / reply 分支那样直接 replace 掉前缀。纯函数，见 MessageApiTest。
     */
    public static String parseBvid(String uri) {
        if (uri == null) return "";
        Matcher matcher = BVID_PATTERN.matcher(uri);
        return matcher.find() ? matcher.group() : "";
    }

    /**
     * 读消息条目的 {@code business_id}。**不是每条都有**：有一类消息条目（由可选私有功能解析，
     * 见 {@link #getReplyMsg} 的 default 分支）根本不带这个字段。原来三个消息流都用 {@code getInt}，
     * 遇到这种条目直接抛 JSONException，把整页消息带崩（不是少一条，是整页空白）。
     * 统一走这里，缺省 {@code -1}。
     */
    public static int parseBusinessId(JSONObject item) {
        return item == null ? -1 : item.optInt("business_id", -1);
    }

    /**
     * like 流的 items 容器。真实响应是 {@code data.total.items} —— 比 reply 流的
     * {@code data.items} 多包了一层 {@code total}（26.11.11 反馈 #150 的根因之一：
     * 上游接口按这个结构返回，代码却按 reply 的结构取）。
     * 顺手兼容只有 {@code data.items} 的形态：都取不到时返回空数组而不是抛异常，
     * 免得一整个点赞页空白。纯函数，见 MessageApiTest。
     */
    public static JSONArray parseLikeItems(JSONObject data) {
        if (data == null) return new JSONArray();
        JSONObject total = data.optJSONObject("total");
        JSONArray items = total == null ? null : total.optJSONArray("items");
        if (items == null) items = data.optJSONArray("items");
        return items == null ? new JSONArray() : items;
    }

    /**
     * like 流的游标，与 items 同层，同样两处都认（{@code data.total.cursor} 优先，
     * 回落 {@code data.cursor}）。原来只按 {@code data.cursor} 取，取不到就是 null，
     * 列表页拿到 null 游标后再也不翻页。取不到返回 null。纯函数，见 MessageApiTest。
     */
    public static JSONObject parseLikeCursor(JSONObject data) {
        if (data == null) return null;
        JSONObject total = data.optJSONObject("total");
        JSONObject cursor = total == null ? null : total.optJSONObject("cursor");
        return cursor != null ? cursor : data.optJSONObject("cursor");
    }

    /**
     * like 流的翻页参数。游标时间字段是 {@code like_time}，{@code reply_time} 是 reply 流的，
     * 原来照抄过来，服务端认不出这个参数，第二页拿回来的还是第一页的内容。
     * 首页（id/time 未给）返回空串。纯函数，见 MessageApiTest。
     */
    public static String likePageQuery(long id, long time) {
        if (id <= 0 || time <= 0) return "";
        return String.format("&id=%s&like_time=%s", id, time);
    }

    public static JSONObject getUnread() throws IOException, JSONException {
        JSONObject data = getUnreadData();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("at", data.optInt("at", 0));
        jsonObject.put("like", data.optInt("like", 0));
        jsonObject.put("reply", data.optInt("reply", 0));
        jsonObject.put("system", data.optInt("sys_msg", 0));
        return jsonObject;
    }

    /**
     * 「消息」入口的未读角标数：@（回复我的）+ 回复 + 点赞，缺字段按 0 算。
     * 原来漏了点赞（{@code like}），点赞再多角标也不涨，用户以为没有新点赞（26.11.11 反馈 #150）。
     * 纯函数，见 MessageApiTest。
     */
    public static int sumUnread(JSONObject data) {
        if (data == null) return 0;
        int total = 0;
        total += data.optInt("at", 0);
        total += data.optInt("reply", 0);
        total += data.optInt("like", 0);
        return total;
    }

    public static int checkMessageUnread() throws IOException, JSONException {
        return sumUnread(getUnreadData());
    }

    /**
     * 私信未读的真值：只认 {@code follow_unread} + {@code unfollow_unread}。
     *
     * <p>26.11.23：{@code single_unread} 里其余六个桶（unfollow_push_msg / dustbin_push_msg /
     * dustbin_unread / biz_msg_unfollow_unread / biz_msg_follow_unread / custom_unread）在会话
     * 列表里**没有对应的未读会话行**，把它们也加进角标，就得到一个点不掉的数——用户反馈
     * 「一直有消息消除不掉」（#281）。
     *
     * <p>26.11.20 为了让系统会话有入口而放开过滤，方向是对的，代价是角标虚高；这里的取舍是
     * **入口要有，角标不能虚高**：系统会话照常显示，它的通知未读改由 sys-msg 的置读消掉。
     *
     * <p>纯函数，见 MessageApiTest。
     */
    public static int parsePrivateUnread(JSONObject data) {
        if (data == null) return 0;
        return data.optInt("follow_unread", 0) + data.optInt("unfollow_unread", 0);
    }

    public static int checkPrivateMsgUnread() throws IOException, JSONException {
        String url = "https://api.vc.bilibili.com/session_svr/v1/session_svr/single_unread";
        JSONObject all = NetWorkUtil.getJson(url);
        if (all.has("data") && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            // 其余六个桶不参与角标，但留着日志：下次再有人报「数不对」能直接对上号
            Logu.d("MessageApi", "single_unread 全量: " + data);
            return parsePrivateUnread(data);
        }
        return 0;
    }

    public static int checkGroupMsgUnread() throws IOException, JSONException {
        String url = "https://api.vc.bilibili.com/session_svr/v1/session_svr/my_group_unread";
        JSONObject all = NetWorkUtil.getJson(url);
        if (all.has("data") && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            return data.optInt("unread_count", 0);
        }
        return 0;
    }

    public static Pair<MessageCard.Cursor, List<MessageCard>> getLikeMsg(long id, long time)
            throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/msgfeed/like?platform=web&build=0&mobi_app=web"
                + likePageQuery(id, time);
        JSONObject all = NetWorkUtil.getJson(url);
        if (all.has("data") && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            // 所有消息
            ArrayList<MessageCard> totalArray = new ArrayList<>();
            JSONArray items = parseLikeItems(data);
            for (int i = 0; i < items.length(); i++) {
                JSONObject object = ((JSONObject) items.get(i));
                MessageCard likeInfo = new MessageCard();

                ArrayList<UserInfo> userList = new ArrayList<>();
                for (int j = 0; j < object.getJSONArray("users").length(); j++) {
                    JSONObject userArrayInfo = ((JSONObject) object.getJSONArray("users").get(j));
                    userList.add(new UserInfo(userArrayInfo.getLong("mid"), userArrayInfo.getString("nickname"),
                            userArrayInfo.getString("avatar"), "", userArrayInfo.getInt("fans"), 0, 0,
                            userArrayInfo.getBoolean("follow"), "", 0, "", 0));
                }

                likeInfo.id = object.getLong("id");
                likeInfo.user = userList;
                likeInfo.timeStamp = object.getLong("like_time");

                JSONObject item = object.getJSONObject("item");
                likeInfo.businessId = parseBusinessId(item);
                likeInfo.subjectId = item.getLong("item_id");
                likeInfo.sourceId = item.optLong("source_id", -1);
                likeInfo.rootId = item.optLong("root_id", -1);
                likeInfo.itemType = item.getString("type");

                switch (likeInfo.itemType) {
                    case "video":
                        likeInfo.content = "等总共 " + object.getLong("counts") + " 人点赞了你的视频";
                        VideoCard videoCard = new VideoCard();
                        videoCard.aid = 0;
                        videoCard.bvid = item.getString("uri").replace("https://www.bilibili.com/video/BV", "");
                        videoCard.upName = "";
                        videoCard.title = item.getString("title");
                        videoCard.cover = item.getString("image");
                        videoCard.view = "";
                        likeInfo.videoCard = videoCard;
                        break;
                    case "reply":
                        likeInfo.content = "等总共 " + object.getLong("counts") + " 人点赞了你的评论";
                        Reply replyInfo = new Reply();
                        replyInfo.rpid = item.getLong("item_id");
                        replyInfo.sender = null;
                        replyInfo.message = new SpannableString(item.getString("title"));
                        replyInfo.pictureList = new ArrayList<>();
                        replyInfo.likeCount = 0;
                        replyInfo.upLiked = false;
                        replyInfo.upReplied = false;
                        replyInfo.liked = false;
                        replyInfo.childCount = 0;
                        replyInfo.ofBvid = item.getString("uri").replace("https://www.bilibili.com/video/", "");
                        replyInfo.childMsgList = new ArrayList<>();
                        likeInfo.replyInfo = replyInfo;
                        break;
                    case "dynamic":
                    case "album":
                        likeInfo.content = "等总共 " + object.getLong("counts") + " 人点赞了你的动态";
                        Reply replyInfo_dynamic = new Reply();
                        replyInfo_dynamic.rpid = item.getLong("item_id");
                        replyInfo_dynamic.sender = null;
                        replyInfo_dynamic.message = new SpannableString(item.getString("title"));
                        replyInfo_dynamic.pictureList = new ArrayList<>();
                        replyInfo_dynamic.likeCount = 0;
                        replyInfo_dynamic.upLiked = false;
                        replyInfo_dynamic.upReplied = false;
                        replyInfo_dynamic.liked = false;
                        replyInfo_dynamic.isDynamic = true;
                        replyInfo_dynamic.childCount = 0;
                        replyInfo_dynamic.childMsgList = new ArrayList<>();
                        likeInfo.dynamicInfo = replyInfo_dynamic;
                        break;
                    case "article":
                        likeInfo.content = "等总共 " + object.getLong("counts") + " 人点赞了你的专栏";
                        // 实在是抽象 但是我没时间改那么多
                        Reply replyChildInfo = new Reply();
                        replyChildInfo.rpid = item.getLong("target_id");
                        replyChildInfo.message = new SpannableString(item.getString("title"));
                        replyChildInfo.childCount = 0;
                        likeInfo.replyInfo = replyChildInfo;
                        break;
                    default:
                        // 可选私有功能可能认得这一类（填充 content / replyInfo）；
                        // 不在场或不认识就照旧显示「无法识别这个类别」。
                        // 先判 available 再取 item.toString()：公开构建里不为每条消息多生成一次 JSON 文本。
                        if (!(OptionalFeatures.INSTANCE.getAvailable()
                                && OptionalFeatures.INSTANCE.fillOptionalMessage(likeInfo, item.toString(), true)))
                            likeInfo.content = "无法识别这个类别：" + likeInfo.itemType;
                }

                likeInfo.getType = MessageCard.GET_TYPE_LIKE;
                totalArray.add(likeInfo);
            }

            JSONObject cursor = parseLikeCursor(data);
            return new Pair<>(cursor == null ? null
                    : new MessageCard.Cursor(cursor.optBoolean("is_end", true), cursor.optLong("id", -1),
                    cursor.optLong("time", -1)),
                    totalArray);
        } else {
            return new Pair<>(null, new ArrayList<>());
        }
    }

    public static Pair<MessageCard.Cursor, List<MessageCard>> getReplyMsg(long id, long time)
            throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/msgfeed/reply?platform=web&build=0&mobi_app=web";
        if (id > 0 && time > 0)
            url += String.format("&id=%s&reply_time=%s", id, time);
        JSONObject all = NetWorkUtil.getJson(url);
        if (all.has("data") && !all.isNull("data")) {
            ArrayList<MessageCard> totalArray = new ArrayList<>();
            for (int i = 0; i < all.getJSONObject("data").getJSONArray("items").length(); i++) {
                JSONObject object = ((JSONObject) all.getJSONObject("data").getJSONArray("items").get(i));
                MessageCard replyInfo = new MessageCard();

                List<UserInfo> userList = new ArrayList<>();
                userList.add(new UserInfo(object.getJSONObject("user").getLong("mid"),
                        object.getJSONObject("user").getString("nickname"),
                        object.getJSONObject("user").getString("avatar"), "",
                        object.getJSONObject("user").getInt("fans"), 0, 0,
                        object.getJSONObject("user").getBoolean("follow"), "", 0, "", 0));
                replyInfo.user = userList;

                replyInfo.id = object.getLong("id");
                replyInfo.timeStamp = object.getLong("reply_time");

                JSONObject item = object.getJSONObject("item");
                replyInfo.businessId = parseBusinessId(item);
                replyInfo.subjectId = item.getLong("subject_id");
                replyInfo.sourceId = item.optLong("source_id", -1);
                replyInfo.rootId = item.optLong("root_id", -1);
                replyInfo.itemType = item.getString("type");
                replyInfo.getType = MessageCard.GET_TYPE_REPLY;
                replyInfo.targetId = item.optLong("target_id", -1);

                // source_content 是「被回复的内容」。回复流对所有类型都读它，但弹幕类条目
                // 未必带这个字段；用 getString 会抛 JSONException 把整页带崩，所以改成 optString。
                replyInfo.content = item.optString("source_content", "");

                switch (replyInfo.itemType) {
                    case "video":
                        VideoCard videoCard = new VideoCard();
                        videoCard.aid = 0;
                        videoCard.bvid = item.getString("uri").replace("https://www.bilibili.com/video/BV", "");
                        videoCard.upName = "";
                        videoCard.title = item.getString("title");
                        videoCard.cover = item.getString("image");
                        videoCard.view = "";
                        replyInfo.videoCard = videoCard;
                        break;
                    case "reply":
                        Reply replyChildInfo = new Reply();
                        replyChildInfo.rpid = item.getLong("target_id");
                        replyChildInfo.sender = null;
                        replyChildInfo.message = new SpannableString("[评论] " + item.getString("title"));
                        replyChildInfo.pictureList = new ArrayList<>();
                        replyChildInfo.likeCount = 0;
                        replyChildInfo.upLiked = false;
                        replyChildInfo.upReplied = false;
                        replyChildInfo.liked = false;
                        replyChildInfo.childCount = 0;
                        replyChildInfo.ofBvid = item.getString("uri").replace("https://www.bilibili.com/video/", "");
                        replyChildInfo.childMsgList = new ArrayList<>();
                        replyInfo.replyInfo = replyChildInfo;
                        break;
                    case "dynamic":
                    case "album":
                        Reply replyChildInfo_dynamic = new Reply();
                        replyChildInfo_dynamic.rpid = item.getLong("target_id");
                        replyChildInfo_dynamic.sender = null;
                        replyChildInfo_dynamic.message = new SpannableString("[动态] " + item.getString("title"));
                        replyChildInfo_dynamic.pictureList = new ArrayList<>();
                        replyChildInfo_dynamic.likeCount = 0;
                        replyChildInfo_dynamic.upLiked = false;
                        replyChildInfo_dynamic.upReplied = false;
                        replyChildInfo_dynamic.liked = false;
                        replyChildInfo_dynamic.childCount = 0;
                        replyChildInfo_dynamic.isDynamic = true;
                        replyChildInfo_dynamic.childMsgList = new ArrayList<>();
                        replyInfo.dynamicInfo = replyChildInfo_dynamic;
                        break;
                    case "article":
                        Reply replyChildInfo_article = new Reply();
                        replyChildInfo_article.rpid = item.getLong("target_id");
                        replyChildInfo_article.message = new SpannableString("[专栏] " + item.getString("title"));
                        replyChildInfo_article.childCount = 0;
                        replyInfo.replyInfo = replyChildInfo_article;
                        break;
                    default:
                        // 可选私有功能可能认得这一类（填充 replyInfo）；不在场或不认识就照旧提示。
                        // 先判 available 再取 item.toString()：公开构建里不为每条消息多生成一次 JSON 文本。
                        if (!(OptionalFeatures.INSTANCE.getAvailable()
                                && OptionalFeatures.INSTANCE.fillOptionalMessage(replyInfo, item.toString(), false)))
                            replyInfo.content = "无法识别这个类别：" + replyInfo.itemType;
                }

                totalArray.add(replyInfo);
            }
            JSONObject cursor = all.getJSONObject("data").optJSONObject("cursor");
            return new Pair<>(cursor == null ? null
                    : new MessageCard.Cursor(cursor.optBoolean("is_end", true), cursor.optLong("id", -1),
                    cursor.optLong("time", -1)),
                    totalArray);
        } else {
            return new Pair<>(null, new ArrayList<>());
        }
    }

    public static Pair<MessageCard.Cursor, List<MessageCard>> getAtMsg(long id, long time)
            throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/msgfeed/at?platform=web&build=0&mobi_app=web";
        if (id > 0 && time > 0)
            url += String.format("&id=%s&at_time=%s", id, time);
        JSONObject all = NetWorkUtil.getJson(url);
        if (all.has("data") && !all.isNull("data")) {
            ArrayList<MessageCard> totalArray = new ArrayList<>();
            for (int i = 0; i < all.getJSONObject("data").getJSONArray("items").length(); i++) {
                JSONObject object = ((JSONObject) all.getJSONObject("data").getJSONArray("items").get(i));
                MessageCard replyInfo = new MessageCard();

                List<UserInfo> userList = new ArrayList<>();
                userList.add(new UserInfo(object.getJSONObject("user").getLong("mid"),
                        object.getJSONObject("user").getString("nickname"),
                        object.getJSONObject("user").getString("avatar"), "",
                        object.getJSONObject("user").getInt("fans"), 0, 0,
                        object.getJSONObject("user").getBoolean("follow"), "", 0, "", 0));
                replyInfo.user = userList;

                replyInfo.id = object.getLong("id");
                replyInfo.timeStamp = object.getLong("at_time");
                replyInfo.content = "提到了我";

                if (object.getJSONObject("item").getString("type").equals("video")) {
                    VideoCard videoCard = new VideoCard();
                    videoCard.aid = 0;
                    videoCard.bvid = object.getJSONObject("item").getString("uri")
                            .replace("https://www.bilibili.com/video/BV", "");
                    videoCard.upName = "";
                    videoCard.title = object.getJSONObject("item").getString("title");
                    videoCard.cover = object.getJSONObject("item").getString("image");
                    videoCard.view = "";
                    replyInfo.videoCard = videoCard;
                } else if (object.getJSONObject("item").getString("type").equals("reply")) {
                    Reply replyChildInfo = new Reply();
                    replyChildInfo.rpid = object.getJSONObject("item").getLong("target_id");
                    replyChildInfo.sender = null;
                    replyChildInfo.message = new SpannableString(
                            "[评论] " + object.getJSONObject("item").getString("title"));
                    replyChildInfo.pictureList = new ArrayList<>();
                    replyChildInfo.likeCount = 0;
                    replyChildInfo.upLiked = false;
                    replyChildInfo.upReplied = false;
                    replyChildInfo.liked = false;
                    replyChildInfo.childCount = 0;
                    replyChildInfo.ofBvid = object.getJSONObject("item").getString("uri")
                            .replace("https://www.bilibili.com/video/", "");
                    replyChildInfo.childMsgList = new ArrayList<>();
                    replyInfo.replyInfo = replyChildInfo;
                } else if (object.getJSONObject("item").getString("type").equals("dynamic")) {
                    Reply replyChildInfo = new Reply();
                    replyChildInfo.rpid = object.getJSONObject("item").getLong("target_id");
                    replyChildInfo.sender = null;
                    replyChildInfo.message = new SpannableString(
                            "[动态] " + object.getJSONObject("item").getString("title"));
                    replyChildInfo.pictureList = new ArrayList<>();
                    replyChildInfo.likeCount = 0;
                    replyChildInfo.upLiked = false;
                    replyChildInfo.upReplied = false;
                    replyChildInfo.liked = false;
                    replyChildInfo.isDynamic = true;
                    replyChildInfo.childCount = 0;
                    replyChildInfo.childMsgList = new ArrayList<>();
                    replyInfo.dynamicInfo = replyChildInfo;
                } else if (object.getJSONObject("item").getString("type").equals("article")) {
                    Reply replyChildInfo = new Reply();
                    replyChildInfo.rpid = object.getJSONObject("item").getLong("target_id");
                    replyChildInfo.message = new SpannableString(
                            "[专栏] " + object.getJSONObject("item").getString("title"));
                    replyChildInfo.childCount = 0;
                    replyInfo.replyInfo = replyChildInfo;
                }
                JSONObject item = object.getJSONObject("item");
                replyInfo.businessId = parseBusinessId(item);
                replyInfo.subjectId = item.getLong("subject_id");
                replyInfo.sourceId = item.optLong("source_id", -1);
                replyInfo.rootId = item.optLong("root_id", -1);
                replyInfo.itemType = item.getString("type");
                replyInfo.getType = MessageCard.GET_TYPE_AT;

                totalArray.add(replyInfo);
            }

            JSONObject cursor = all.getJSONObject("data").optJSONObject("cursor");
            return new Pair<>(cursor == null ? null
                    : new MessageCard.Cursor(cursor.optBoolean("is_end", true), cursor.optLong("id", -1),
                    cursor.optLong("time", -1)),
                    totalArray);
        } else {
            return new Pair<>(null, new ArrayList<>());
        }
    }

    private static final String SYSTEM_FEED = "https://message.bilibili.com/x/sys-msg/";

    /**
     * 系统通知列表。
     *
     * <p>26.11.23：{@code page_size} 从 35 改成 20。web 系统通知接口对 {@code page_size=35}
     * 直接回业务码 -6，于是 data 为空、整页空白——这是原来「系统消息页什么都没有」的根因。
     */
    public static ArrayList<MessageCard> getSystemMsg() throws IOException, JSONException {
        ArrayList<MessageCard> totalArray = new ArrayList<>();
        for (JSONObject object : parseSystemNotices(fetchSystemNotices("query_user_notify?page_size=20"))) {
            MessageCard replyInfo = new MessageCard();

            replyInfo.user = new ArrayList<>();

            replyInfo.id = object.optLong("id", 0);
            replyInfo.cursor = number(object, "cursor");
            replyInfo.timeDesc = object.optString("time_at", "");
            replyInfo.content = object.optString("title", "") + "\n" + object.optString("content", "");

            totalArray.add(replyInfo);
        }
        return totalArray;
    }

    /**
     * 系统通知接口（message.bilibili.com）的请求头：在通用 web 头基础上，把 Origin / Referer
     * 换成本域的。update_cursor 这类写接口会校验来源，拿主站 Origin 去调有可能被拒
     * （26.11.23，同隔壁的做法）。
     */
    private static ArrayList<String> sysNoticeHeaders() {
        ArrayList<String> headers = new ArrayList<>(NetWorkUtil.webHeaders);
        for (int i = 0; i + 1 < headers.size(); i += 2) {
            if ("Origin".equalsIgnoreCase(headers.get(i))) {
                headers.set(i + 1, "https://message.bilibili.com");
            } else if ("Referer".equalsIgnoreCase(headers.get(i))) {
                headers.set(i + 1, "https://message.bilibili.com/");
            }
        }
        return headers;
    }

    /** 拉一段系统通知。失败由调用方处理：这里是同步阻塞的，抛出去比吞掉更好排查。 */
    private static JSONObject fetchSystemNotices(String path) throws IOException, JSONException {
        return NetWorkUtil.getJson(SYSTEM_FEED + path + "&build=0&mobi_app=web", sysNoticeHeaders());
    }

    /** 从系统通知响应里取 {@code data.system_notify_list}（纯解析，见 MessageApiTest）。 */
    public static ArrayList<JSONObject> parseSystemNotices(JSONObject response) {
        ArrayList<JSONObject> items = new ArrayList<>();
        if (response == null) return items;
        JSONObject data = response.optJSONObject("data");
        if (data == null) return items;
        JSONArray list = data.optJSONArray("system_notify_list");
        if (list == null) return items;
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item != null) items.add(item);
        }
        return items;
    }

    /**
     * 一批系统通知里最大的已读游标（纯函数，见 MessageApiTest）。
     *
     * <p>cursor 在响应里是字符串型（"1700000000000000"），用 {@code optString} 再 parse，
     * 直接用 {@code optLong} 拿到的是 0。
     */
    public static long maxCursorOf(List<JSONObject> notices) {
        long max = 0;
        if (notices == null) return 0;
        for (JSONObject item : notices) {
            long cursor = number(item, "cursor");
            if (cursor > max) max = cursor;
        }
        return max;
    }

    /**
     * 把系统通知标为已读：两段通知各拉一次取最大 cursor，再发 update_cursor。
     *
     * <p>为什么是两段：{@code query_user_notify} 只装一部分，另一部分在
     * {@code query_unified_notify} 里；后者不渲染到列表，只用来算游标（隔壁同款做法）。
     *
     * @return 接口返回码，0 表示成功；没有可读条目时也回 0（无事可做不算失败）
     */
    public static int acknowledgeSystem() throws IOException, JSONException {
        long cursor = 0;
        for (String path : new String[]{"query_user_notify?page_size=20", "query_unified_notify?page_size=10"}) {
            JSONObject response = fetchSystemNotices(path);
            int code = response.optInt("code", -1);
            if (code != 0) {
                Logu.w("MessageApi", "系统通知请求失败: " + path + " code=" + code);
                continue;
            }
            cursor = Math.max(cursor, maxCursorOf(parseSystemNotices(response)));
        }
        if (cursor <= 0) return 0;
        JSONObject result = NetWorkUtil.getJson(
                SYSTEM_FEED + "update_cursor?cursor=" + cursor + "&has_up=0&build=0&mobi_app=web",
                sysNoticeHeaders());
        return result.optInt("code", -1);
    }

    /** 响应里的数字字段可能给字符串，parse 不出来按 0 算。 */
    private static long number(JSONObject data, String key) {
        if (data == null) return 0;
        try {
            return Long.parseLong(data.optString(key, "0"));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static JSONObject getMsgSettings() throws IOException, JSONException {
        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct",
                SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
        String url = "https://api.vc.bilibili.com/link_setting/v1/link_setting/get";
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .put("msg_notify", 1)
                .put("show_unfollowed_msg", 1)
                .put("build", 0)
                .put("mobi_app", "web")
                .put("csrf_token", csrf)
                .put("csrf", csrf);

        Response response = NetWorkUtil.post(url, formData.toString(), NetWorkUtil.webHeaders);
        String result = response.body().string();
        return new JSONObject(result);
    }

    public static JSONObject setMsgSettings(JSONObject settings) throws IOException, JSONException {
        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct",
                SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
        String url = "https://api.vc.bilibili.com/link_setting/v1/link_setting/set";
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .put("csrf_token", csrf)
                .put("csrf", csrf)
                .put("build", 0)
                .put("mobi_app", "web");

        if (settings.has("msg_notify"))
            formData.put("msg_notify", settings.getInt("msg_notify"));
        if (settings.has("show_unfollowed_msg"))
            formData.put("show_unfollowed_msg", settings.getInt("show_unfollowed_msg"));
        if (settings.has("is_group_fold"))
            formData.put("is_group_fold", settings.getInt("is_group_fold"));
        if (settings.has("should_receive_group"))
            formData.put("should_receive_group", settings.getInt("should_receive_group"));
        if (settings.has("receive_unfollow_msg"))
            formData.put("receive_unfollow_msg", settings.getInt("receive_unfollow_msg"));
        if (settings.has("ai_intercept"))
            formData.put("ai_intercept", settings.getInt("ai_intercept"));

        Response response = NetWorkUtil.post(url, formData.toString(), NetWorkUtil.webHeaders);
        String result = response.body().string();
        return new JSONObject(result);
    }
}
