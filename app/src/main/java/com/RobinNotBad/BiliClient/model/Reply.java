package com.RobinNotBad.BiliClient.model;

import static com.RobinNotBad.BiliClient.api.ReplyApi.TOP_TIP;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.util.EmoteUtil;
import com.RobinNotBad.BiliClient.util.JsonUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;
import com.RobinNotBad.BiliClient.util.TimeUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Reply implements Serializable {
    public long rpid;
    public long oid;
    public long root;
    public long parent;
    public boolean forceDelete;
    public String ofBvid = "";
    public String pubTime;
    public UserInfo sender;
    public CharSequence message;
    public ArrayList<String> pictureList = new ArrayList<>();
    public int likeCount;
    public boolean upLiked;
    public boolean upReplied;
    public boolean liked;
    public int childCount;
    public boolean isDynamic;
    public ArrayList<Reply> childMsgList = new ArrayList<>();
    public boolean isTop;
    public long voteId;  // 评论中的投票 id，-1 表示无投票

    public Reply() {
    }

    /**
     * @param isRoot    是否是根评论
     * @param replyJson 评论json对象
     * @throws JSONException json解析异常
     */
    public Reply(boolean isRoot, JSONObject replyJson) throws JSONException {
        this.rpid = replyJson.getLong("rpid");
        this.oid = replyJson.getLong("oid");
        this.root = replyJson.getLong("root");
        this.parent = replyJson.getLong("parent");
        this.sender = new UserInfo(replyJson.getJSONObject("member"));

        JSONObject content = replyJson.getJSONObject("content");

        JSONObject replyCtrl = replyJson.getJSONObject("reply_control");
        long ctime = replyJson.getLong("ctime") * 1000;

        String time;
        if (System.currentTimeMillis() - ctime < 3 * 24 * 60 * 60 * 1000 && replyCtrl.has("time_desc")) {
            time = replyCtrl.getString("time_desc");
        } else {
            time = TimeUtil.formatDateTime(ctime, Locale.SIMPLIFIED_CHINESE);
        }

        if (replyCtrl.has("location") && !replyCtrl.isNull("location")) {
            // location 形如 "IP属地：xx"，去掉前缀只留地址；空串或异常格式时直接用原值，避免越界
            String location = replyCtrl.getString("location");
            if (location.length() > 5) location = location.substring(5);
            time += " | IP:" + location;
        }
        this.pubTime = time;
        this.voteId = -1;

        if (replyCtrl.has("is_up_top")) {
            if (replyCtrl.getBoolean("is_up_top")) {
                this.isTop = true;
            }
        }

        SpannableStringBuilder messageSpannable = new SpannableStringBuilder((isTop ? TOP_TIP : "")
                + StringUtil.htmlToString(content.getString("message")));

        if (isTop) StringUtil.setTopSpan(messageSpannable);

        this.likeCount = replyJson.getInt("like");
        this.liked = replyJson.getInt("action") == 1;

        if (content.has("emote") && !content.isNull("emote")) {
            ArrayList<Emote> emoteList = new ArrayList<>();
            JSONObject emoteJson = content.getJSONObject("emote");
            ArrayList<String> emoteKeys = JsonUtil.getJsonKeys(emoteJson);

            for (String emoteKey : emoteKeys) {
                JSONObject key = emoteJson.getJSONObject(emoteKey);
                emoteList.add(new Emote(
                        emoteKey,
                        key.getString("url"),
                        key.getJSONObject("meta").getInt("size")
                ));
            }

            EmoteUtil.textReplaceEmote(messageSpannable.toString(), emoteList, 1.0f, BiliTerminal.context, messageSpannable);
        }

        // 检测并替换投票占位符 {vote:vote_id}
        Pattern votePattern = Pattern.compile("\\{vote:(\\d+)\\}");
        Matcher voteMatcher = votePattern.matcher(messageSpannable);
        if (voteMatcher.find()) {
            this.voteId = Long.parseLong(voteMatcher.group(1));
            String voteLabel = " 投票 ";
            int start = voteMatcher.start();
            int end = voteMatcher.end();
            messageSpannable.replace(start, end, voteLabel);
            messageSpannable.setSpan(new ForegroundColorSpan(0xFFFE679A), start, start + voteLabel.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            messageSpannable.setSpan(new UnderlineSpan(), start, start + voteLabel.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        StringUtil.setLink(messageSpannable);

        if (content.has("at_name_to_mid") && !content.isNull("at_name_to_mid")) {
            JSONObject jsonObject = content.getJSONObject("at_name_to_mid");
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                long val = jsonObject.getLong(key);
                StringUtil.setSingleAt(messageSpannable, key, val);
            }
        }

        JSONObject upAction = replyJson.getJSONObject("up_action");
        this.upLiked = upAction.getBoolean("like");
        this.upReplied = upAction.getBoolean("reply");


        if (isRoot) {
            if (content.has("pictures") && !content.isNull("pictures")) {
                ArrayList<String> pictureList = new ArrayList<>();
                JSONArray pictures = content.getJSONArray("pictures");
                for (int j = 0; j < pictures.length(); j++) {
                    JSONObject picture = pictures.getJSONObject(j);
                    pictureList.add(picture.getString("img_src"));
                }
                this.pictureList = pictureList;
            }

            this.childCount = replyJson.getInt("rcount");

            if (replyJson.has("replies") && !replyJson.isNull("replies")) {
                ArrayList<Reply> childMsgList = new ArrayList<>();
                JSONArray childReplies = replyJson.getJSONArray("replies");
                for (int j = 0; j < childReplies.length(); j++) {
                    JSONObject childReply = childReplies.getJSONObject(j);
                    childMsgList.add(new Reply(false, childReply));
                }
                this.childMsgList = childMsgList;
            }
        }

        this.message = messageSpannable;
    }
}
