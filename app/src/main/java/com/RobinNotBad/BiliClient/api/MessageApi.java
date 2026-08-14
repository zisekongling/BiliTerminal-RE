package com.RobinNotBad.BiliClient.api;

import android.text.SpannableString;
import android.util.Pair;

import com.RobinNotBad.BiliClient.model.MessageCard;
import com.RobinNotBad.BiliClient.model.Reply;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    public static JSONObject getUnread() throws IOException, JSONException {
        JSONObject data = getUnreadData();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("at", data.optInt("at", 0));
        jsonObject.put("like", data.optInt("like", 0));
        jsonObject.put("reply", data.optInt("reply", 0));
        jsonObject.put("system", data.optInt("sys_msg", 0));
        return jsonObject;
    }

    public static int checkMessageUnread() throws IOException, JSONException {
        JSONObject data = getUnreadData();
        int total = 0;
        total += data.optInt("at", 0);
        total += data.optInt("reply", 0);
        return total;
    }

    public static int checkPrivateMsgUnread() throws IOException, JSONException {
        String url = "https://api.vc.bilibili.com/session_svr/v1/session_svr/single_unread";
        JSONObject all = NetWorkUtil.getJson(url);
        int total = 0;
        if (all.has("data") && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            total += data.optInt("unfollow_unread", 0);
            total += data.optInt("follow_unread", 0);
            total += data.optInt("unfollow_push_msg", 0);
            total += data.optInt("dustbin_push_msg", 0);
            total += data.optInt("dustbin_unread", 0);
            total += data.optInt("biz_msg_unfollow_unread", 0);
            total += data.optInt("biz_msg_follow_unread", 0);
            total += data.optInt("custom_unread", 0);
        }
        return total;
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
        String url = "https://api.bilibili.com/x/msgfeed/like?platform=web&build=0&mobi_app=web";
        if (id > 0 && time > 0)
            url += String.format("&id=%s&reply_time=%s", id, time);
        JSONObject all = NetWorkUtil.getJson(url);
        if (all.has("data") && !all.isNull("data")) {
            // 所有消息
            ArrayList<MessageCard> totalArray = new ArrayList<>();
            for (int i = 0; i < all.getJSONObject("data").getJSONObject("total").getJSONArray("items").length(); i++) {
                JSONObject object = ((JSONObject) all.getJSONObject("data").getJSONObject("total").getJSONArray("items")
                        .get(i));
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
                likeInfo.businessId = item.getInt("business_id");
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
                        likeInfo.content = "无法识别这个类别：" + likeInfo.itemType;
                }

                likeInfo.getType = MessageCard.GET_TYPE_LIKE;
                totalArray.add(likeInfo);
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
                replyInfo.businessId = item.getInt("business_id");
                replyInfo.subjectId = item.getLong("subject_id");
                replyInfo.sourceId = item.optLong("source_id", -1);
                replyInfo.rootId = item.optLong("root_id", -1);
                replyInfo.itemType = item.getString("type");
                replyInfo.getType = MessageCard.GET_TYPE_REPLY;
                replyInfo.targetId = item.optLong("target_id", -1);

                replyInfo.content = item.getString("source_content");

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
                replyInfo.businessId = item.getInt("business_id");
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

    public static ArrayList<MessageCard> getSystemMsg() throws IOException, JSONException {
        String url = "https://message.bilibili.com/x/sys-msg/query_user_notify?csrf="
                + NetWorkUtil.getInfoFromCookie("bili_jct",
                SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))
                + "&page_size=35&build=0&mobi_app=web";
        JSONObject all = NetWorkUtil.getJson(url);
        if (all.has("data") && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            ArrayList<MessageCard> totalArray = new ArrayList<>();
            if (data.has("system_notify_list") && !data.isNull("system_notify_list")) {
                for (int i = 0; i < data.getJSONArray("system_notify_list").length(); i++) {
                    JSONObject object = data.getJSONArray("system_notify_list").getJSONObject(i);
                    MessageCard replyInfo = new MessageCard();

                    replyInfo.user = new ArrayList<>();

                    replyInfo.id = object.getLong("id");
                    replyInfo.timeDesc = object.getString("time_at");
                    replyInfo.content = object.getString("title") + "\n" + object.getString("content");

                    totalArray.add(replyInfo);
                }
            }

            return totalArray;
        } else
            return new ArrayList<>();
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
