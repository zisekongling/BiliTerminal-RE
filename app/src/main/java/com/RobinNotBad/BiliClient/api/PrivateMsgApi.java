package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.R;

import com.RobinNotBad.BiliClient.BiliTerminal;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.util.Log;

import com.RobinNotBad.BiliClient.model.PrivateMessage;
import com.RobinNotBad.BiliClient.model.PrivateMsgSession;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.util.EmoteUtil;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import okhttp3.Response;

public class PrivateMsgApi {

    public static final int MSG_TYPE_TEXT = 1;
    public static final int MSG_TYPE_PIC = 2;
    public static final int MSG_TYPE_RETRACT = 5;

    /** 会话类型：与用户私信 */
    public static final int SESSION_TYPE_USER = 1;
    /** 会话类型：粉丝团（talker_id 是粉丝团 id 而不是 mid） */
    public static final int SESSION_TYPE_FAN_GROUP = 2;

    // 返回的是倒序的消息列表，使用时记得列表倒置
    //seqno传0时只看size，不进行seqno的筛选
    public static JSONObject getPrivateMsg(long talkerId, int size, long beginSeqno, long endSeqno)
            throws IOException, JSONException {
        String url =
                "https://api.vc.bilibili.com/svr_sync/v1/svr_sync/fetch_session_msgs?session_type=1&talker_id=" + talkerId
                        + "&size=" + size
                        + "&begin_seqno=" + beginSeqno
                        + "&end_seqno=" + endSeqno;
        JSONObject allMsgJson = NetWorkUtil.getJson(url);
        if (allMsgJson.has("data") && !allMsgJson.isNull("data"))
            return allMsgJson.getJSONObject("data");
        else return new JSONObject();
    }

    public static ArrayList<PrivateMessage> getPrivateMsgList(JSONObject allMsgJson)
            throws IOException, JSONException {
        ArrayList<PrivateMessage> list = new ArrayList<>();
        if (!allMsgJson.isNull("messages")) {
            JSONArray messages = allMsgJson.getJSONArray("messages");
            UserInfo myInfo = UserInfoApi.getCurrentUserInfo();
            UserInfo targetInfo = new UserInfo();

            // list.add(new PrivateMessage(114514,1,new JSONObject("{\"content\":\"
            // 。\"}"),11111111,"aaaaa",111));

            boolean isReqTargetInfo = false;
            for (int i = 0; i < messages.length(); i++) {
                PrivateMessage msgObject = new PrivateMessage();
                JSONObject msgJson = messages.getJSONObject(i);
                msgObject.uid = msgJson.getLong("sender_uid");
                msgObject.type = msgJson.getInt("msg_type");
                if (msgObject.uid == myInfo.mid) {
                    msgObject.name = myInfo.name;
                } else {
                    if (!isReqTargetInfo) {
                        targetInfo = UserInfoApi.getUserInfo(msgObject.uid);
                        isReqTargetInfo = true;
                    }
                    if (targetInfo != null) msgObject.name = targetInfo.name;
                }
                msgObject.content = new JSONObject("{\"content\":\" .\"}"); // 防止内容不为json时解析错误
                if (msgJson.getString("content").endsWith("}")
                        && msgJson.getString("content").startsWith("{")) {
                    try{
                        msgObject.content =
                                new JSONObject(msgJson.getString("content") /*.replace("\\","")*/);
                    }catch (Exception ignore){}
                    try{
                    msgObject.content_array =
                            new JSONArray(msgJson.getString("content") /*.replace("\\","")*/);
                    }catch (Exception ignore){}
                }
                msgObject.timestamp = msgJson.getLong("timestamp");
                msgObject.msg_source = msgJson.getInt("msg_source");
                msgObject.msgId = msgJson.getLong("msg_key");
                msgObject.msgSeqno = msgJson.getLong("msg_seqno");
                msgObject.msgStatus = msgJson.optInt("msg_status", PrivateMessage.STATUS_NORMAL);
                if (!shouldDisplay(msgObject.msgStatus)) continue;
                list.add(msgObject);

            }
            // 修复 M12：原实现逐条 Log.e 打印 name/uid/msgId/content，等于把私信正文
            // （含图片、撤回内容）长期留在 logcat 里，任何拿到日志的进程都能读到。
            // 这里只保留条数，调试信息量足够且不再落正文。
            Log.d("PrivateMsgApi", "返回 msgList，共 " + list.size() + " 条");
        }
        return list;
    }

    public static JSONArray getEmoteJsonArray(JSONObject allMsgJson) throws JSONException {
        if (allMsgJson.has("e_infos") && !allMsgJson.isNull("e_infos")) {
            return allMsgJson.getJSONArray("e_infos");
        } else {
            return new JSONArray();
        }
    }

    /**
     * 把 uid 列表拼成 {@code user/cards} 接口要的 {@code uids} 参数（逗号分隔）。
     *
     * <p>抽成纯函数便于单测：空列表必须返回空串，**调用方要先判空**——旧实现无条件
     * {@code substring(0, length - 1)}，列表为空时就是 {@code substring(0, -1)}，
     * 抛 {@code StringIndexOutOfBoundsException}（反馈 #84：打开消息页每次都弹「Adapter错误」）。
     */
    public static String buildUidsParam(ArrayList<Long> uidList) {
        if (uidList == null || uidList.isEmpty()) return "";
        StringBuilder userString = new StringBuilder();
        for (Long uid : uidList) {
            // 列表里混进 null 时不能拼出 "null,"（StringBuilder 会把它当字符串写进去）
            if (uid == null) continue;
            if (userString.length() > 0) userString.append(',');
            userString.append(uid.longValue());
        }
        return userString.toString();
    }

    public static HashMap<Long, UserInfo> getUsersInfo(ArrayList<Long> uidList)
            throws IOException, JSONException {
        HashMap<Long, UserInfo> userMap = new HashMap<>();
        String uids = buildUidsParam(uidList);
        // 一个 uid 都没有时不该发请求：会话列表为空（未登录 / 没有会话）是正常情况，
        // 不是错误，更不是「Adapter错误」。#84 的崩溃就出在这里。
        if (uids.isEmpty()) return userMap;
        String url = "https://api.vc.bilibili.com/account/v1/user/cards?uids=" + uids;
        JSONObject root = NetWorkUtil.getJson(url);
        if (root.has("data") && !root.isNull("data")) {
            JSONArray data = root.getJSONArray("data");
            for (int i = 0; i < data.length(); ++i) {
                JSONObject userJson = data.getJSONObject(i);
                UserInfo user = new UserInfo();
                user.mid = userJson.getLong("mid");
                user.name = userJson.getString("name");
                user.avatar = userJson.getString("face");
                // 不再打印 mid + 昵称 + 头像：三者拼起来足以定位一个真实用户（审计 M12-a 的同类）
                userMap.put(user.mid, user);
            }
        }
        return userMap;
    }

    public static ArrayList<PrivateMsgSession> getSessionsList(int size)
            throws IOException, JSONException {
        String url =
                "https://api.vc.bilibili.com/session_svr/v1/session_svr/get_sessions?session_type=1&size=" + size;
        return parseSessionsList(NetWorkUtil.getJson(url));
    }

    /**
     * 解析会话列表响应（纯解析，便于 JVM 单测）。
     *
     * <p>account_info 仅在系统会话中出现（见 bilibili-API/docs/message/private_msg.md），
     * 系统会话不是普通私信，需要过滤掉。用 isNull 判断同时覆盖"字段缺失"和"值为 null"两种情况；
     * 旧写法 {@code !has && isNull} 恒等于 {@code !has}，会把 account_info 显式为 null 的普通会话一起丢弃。
     */
    public static ArrayList<PrivateMsgSession> parseSessionsList(JSONObject root) throws JSONException {
        ArrayList<PrivateMsgSession> sessionList = new ArrayList<>();
        if (root == null || root.isNull("data")) {
            return sessionList;
        }
        JSONArray sessions = root.getJSONObject("data").getJSONArray("session_list");
        for (int i = 0; i < sessions.length(); ++i) {
            PrivateMsgSession session = new PrivateMsgSession();
            JSONObject sessionJson = sessions.getJSONObject(i);
            session.talkerUid = sessionJson.getLong("talker_id");

            if (!sessionJson.isNull("last_msg")) {
                session.contentType = sessionJson.getJSONObject("last_msg").getInt("msg_type");
                String content = sessionJson.getJSONObject("last_msg").getString("content");

                if (content.endsWith("}") && content.startsWith("{"))
                    session.content = new JSONObject(content);
            }

            session.unread = sessionJson.getInt("unread_count");
            session.topTs = sessionJson.optLong("top_ts", 0);

            if (sessionJson.isNull("account_info"))
                sessionList.add(session);
        }
        return sessionList;
    }

    public static ArrayList<PrivateMsgSession> getNewSessionsList(long beginTs, int size, int build, String mobiApp)
            throws IOException, JSONException {
        StringBuilder url = new StringBuilder("https://api.vc.bilibili.com/session_svr/v1/session_svr/new_sessions?");
        if (beginTs > 0) {
            url.append("begin_ts=").append(beginTs).append("&");
        }
        if (size > 0) {
            url.append("size=").append(size).append("&");
        }
        if (build > 0) {
            url.append("build=").append(build).append("&");
        }
        if (mobiApp != null && !mobiApp.isEmpty()) {
            url.append("mobi_app=").append(mobiApp).append("&");
        }
        if (url.charAt(url.length() - 1) == '&') {
            url.setLength(url.length() - 1);
        }
        JSONObject root = NetWorkUtil.getJson(url.toString());
        ArrayList<PrivateMsgSession> sessionList = new ArrayList<>();
        if (root.has("data") && !root.isNull("data")) {
            JSONObject data = root.getJSONObject("data");
            if (data.has("session_list") && !data.isNull("session_list")) {
                JSONArray sessions = data.getJSONArray("session_list");
                for (int i = 0; i < sessions.length(); ++i) {
                    PrivateMsgSession session = new PrivateMsgSession();
                    JSONObject sessionJson = sessions.getJSONObject(i);
                    session.talkerUid = sessionJson.getLong("talker_id");

                    if (!sessionJson.isNull("last_msg")) {
                        session.contentType = sessionJson.getJSONObject("last_msg").getInt("msg_type");
                        String content = sessionJson.getJSONObject("last_msg").getString("content");

                        if (content.endsWith("}") && content.startsWith("{"))
                            session.content = new JSONObject(content);
                    }

                    session.unread = sessionJson.getInt("unread_count");
                    session.topTs = sessionJson.optLong("top_ts", 0);
                    sessionList.add(session);
                }
            }
        }
        return sessionList;
    }

    public static JSONObject sendMsg(
            long senderUid, long receiverUid, int msgType, long timestamp, String content)
            throws IOException, JSONException {
        String url = "https://api.vc.bilibili.com/web_im/v1/web_im/send_msg?";
        String per =
                "msg[dev_id]=" + getDevId()
                        + "&msg[msg_type]=" + msgType
                        + "&msg[content]=" + URLEncoder.encode(content, "UTF-8")
                        + "&msg[receiver_type]=1&csrf=" + NetWorkUtil.currentCsrf()
                        + "&msg[sender_uid]=" + senderUid
                        + "&msg[receiver_id]=" + receiverUid
                        + "&msg[timestamp]=" + timestamp;

        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, per, NetWorkUtil.webHeaders).body()).string());

        // 不再以 Log.e 输出私信内容与完整请求头（含 Cookie），避免凭证/隐私进 logcat
        Logu.i("发送私信结果: " + result.optInt("code"));
        return result;
    }

    /** 撤回有效期：B 站只允许撤回 120 秒内发出的私信（private_msg_content.md「撤回消息」）。 */
    public static final long RECALL_WINDOW_SECONDS = 120;

    /**
     * 被撤回的原消息不再显示（反馈 #57 配套）。
     *
     * 接口对撤回的原消息仍回原文（{@code msg_status=1}），同时另有一条 {@code msg_type=5} 的撤回通知；
     * 原先两条都显示，等于撤回了也照样能看到原文。只过滤 1：被系统撤回（2）的接口本就不返回，
     * 图片失效（50）的内容是一张提示图，照常显示。
     *
     * 过滤不会弄乱分页：撤回通知的 seqno 一定比原消息大，所以列表末尾（refresh 的起点）
     * 永远是保留下来的消息；列表头被滤掉时 loadMore 只会多取回它一次、再被滤掉。
     */
    public static boolean shouldDisplay(int msgStatus) {
        return msgStatus != PrivateMessage.STATUS_RETRACTED;
    }

    /**
     * 这条私信能不能撤回：只能撤回自己发的、仍在 120 秒内的，撤回通知本身不能再撤回。
     * 本地时间只用来决定显不显示入口，最终以服务端判定为准（手表时间可能不准，见 §3.72）。
     */
    public static boolean canRecall(PrivateMessage msg, long selfUid, long nowSeconds) {
        if (msg == null || selfUid <= 0 || msg.uid != selfUid) return false;
        if (msg.type == PrivateMessage.TYPE_RETRACT || msg.msgId == 0) return false;
        // 本地时间比服务端慢时 elapsed 会是负数，照样给入口，交给服务端判
        return nowSeconds - msg.timestamp <= RECALL_WINDOW_SECONDS;
    }

    /**
     * 撤回一条自己发的私信：发一条 {@code msg_type=5}、内容为目标 {@code msg_key} 的私信。
     * 成功后服务端把原消息的 {@code msg_status} 置 1，并在会话里留一条撤回通知。
     */
    public static JSONObject recallMsg(long senderUid, long receiverUid, long msgKey)
            throws IOException, JSONException {
        return sendMsg(senderUid, receiverUid, MSG_TYPE_RETRACT,
                System.currentTimeMillis() / 1000, String.valueOf(msgKey));
    }

    public static JSONObject updateAck(long talkerId, int sessionType, long ackSeqno)
            throws IOException, JSONException {
        String url = "https://api.vc.bilibili.com/session_svr/v1/session_svr/update_ack";
        String csrf = NetWorkUtil.currentCsrf();
        StringBuilder per = new StringBuilder();
        per.append("talker_id=").append(talkerId);
        per.append("&session_type=").append(sessionType);
        if (ackSeqno > 0) {
            per.append("&ack_seqno=").append(ackSeqno);
        }
        per.append("&csrf_token=").append(csrf);
        per.append("&csrf=").append(csrf);
        per.append("&build=0");
        per.append("&mobi_app=web");

        Response response = NetWorkUtil.post(url, per.toString(), NetWorkUtil.webHeaders);
        if (response.body() != null) {
            return new JSONObject(response.body().string());
        }
        return new JSONObject();
    }

    /**
     * set_top 接口的 op_type：0 是置顶、1 是取消置顶（与直觉相反，接口文档就是这么写的）。
     * 抽成纯函数，免得调用点写反——写反的后果是"点了置顶结果取消置顶"，不报错、难发现。
     */
    public static int opTypeForTop(boolean top) {
        return top ? 0 : 1;
    }

    /**
     * 修改会话置顶状态。
     *
     * @param top true 置顶，false 取消置顶
     * @return 接口返回码，0 表示成功
     */
    public static int setSessionTop(long talkerId, int sessionType, boolean top)
            throws IOException, JSONException {
        String url = "https://api.vc.bilibili.com/session_svr/v1/session_svr/set_top";
        JSONObject result = postSessionAction(url, talkerId, sessionType,
                "&op_type=" + opTypeForTop(top));
        return result.optInt("code", -1);
    }

    /**
     * 把会话从会话列表中移除。注意服务端只是移除会话，<b>不会删除聊天记录</b>，
     * 对方再发消息时会话会重新出现。
     *
     * @return 接口返回码，0 表示成功
     */
    public static int removeSession(long talkerId, int sessionType)
            throws IOException, JSONException {
        String url = "https://api.vc.bilibili.com/session_svr/v1/session_svr/remove_session";
        JSONObject result = postSessionAction(url, talkerId, sessionType, "");
        return result.optInt("code", -1);
    }

    /**
     * session_svr 系列接口的公共请求体（talker_id/session_type/csrf/build/mobi_app）。
     *
     * @param extra 额外的参数串，需要以 & 开头（如 "&op_type=0"），可为空串
     */
    private static JSONObject postSessionAction(String url, long talkerId, int sessionType, String extra)
            throws IOException, JSONException {
        String csrf = NetWorkUtil.currentCsrf();
        String per = "talker_id=" + talkerId
                + "&session_type=" + sessionType
                + extra
                + "&csrf_token=" + csrf
                + "&csrf=" + csrf
                + "&build=0"
                + "&mobi_app=web";

        Response response = NetWorkUtil.post(url, per, NetWorkUtil.webHeaders);
        if (response == null) return new JSONObject();
        // 非 2xx（412 风控 / 5xx）时 body 是 HTML，解析会抛 JSONException；返回空对象，
        // 调用方 optInt("code", -1) 拿到 -1，再由 sessionErrorMsg 翻译成人话
        try (Response resp = response) {
            if (!resp.isSuccessful() || resp.body() == null) return new JSONObject();
            return new JSONObject(resp.body().string());
        }
    }

    /**
     * 构造图片消息（{@code msg_type=2}）的 content 对象。
     *
     * <p>接口要求 {@code url} 必须是 B 站图床地址，否则返回 21037「图片格式不合法，不要调戏接口啦」；
     * 本项目的图床来自 {@link ImageApi#uploadImage}（upload_bfs），满足要求。
     * {@code width}/{@code height} 不传会导致消息显示异常，所以调用方必须把上传返回的尺寸带上。
     *
     * @param byteSize  原图字节数，接口按 KB 计（见 {@link #sizeToKb(long)}）
     * @param imageType 不带 {@code image/} 前缀的格式名，见 {@link #imageTypeOf(String)}
     */
    public static JSONObject buildImageContent(String url, int width, int height, long byteSize, String imageType)
            throws JSONException {
        return buildImageContentOfKb(url, width, height, sizeToKb(byteSize), imageType);
    }

    /**
     * 构造图片消息（{@code msg_type=2}）的 content 对象，大小直接以**千字节**给出。
     *
     * <p>存在的理由：图床上传接口（upload_bfs）返回的 {@code img_size} 本身就是千字节
     * （官方示例 {@code "img_size": 6.261}，bilibili-API/docs/dynamic/publish.md:40,66），
     * 而 {@link ImageApi.UploadedImage#size} 存的正是这个值。若把它当成"字节数"再走
     * {@link #buildImageContent(String, int, int, long, String)}，会被 {@link #sizeToKb(long)}
     * 再除一次 1024，发出去的 {@code size} 只有真实值的 1/1024。
     *
     * <p>{@code size} 字段单位见 bilibili-API/docs/message/private_msg_content.md:42
     * （千字节，示例 {@code 55.443}）。
     *
     * @param sizeKb 图片大小，单位千字节；非正数按 0 处理，避免发出 NaN / 负数
     */
    public static JSONObject buildImageContentOfKb(String url, int width, int height, double sizeKb, String imageType)
            throws JSONException {
        return new JSONObject()
                .put("url", url)
                .put("width", width)
                .put("height", height)
                .put("imageType", imageType)
                .put("original", 1)
                .put("size", sizeKb > 0 ? sizeKb : 0d);
    }

    /**
     * 字节 → 千字节，保留三位小数（接口文档里 size 的单位就是 KB，示例值 55.443）。
     * 非法输入（0 或负数）返回 0，避免发出 NaN / 负数。
     */
    public static double sizeToKb(long byteSize) {
        if (byteSize <= 0) return 0d;
        return Math.round(byteSize / 1024d * 1000d) / 1000d;
    }

    /**
     * 把 MIME 类型转成接口要的 {@code imageType}（去掉 {@code image/} 前缀，{@code jpg} 归一成 {@code jpeg}）。
     * 空值按 jpeg 处理，与 {@link ImageApi} 的 MIME 兜底保持一致。
     */
    public static String imageTypeOf(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) return "jpeg";
        String type = mimeType.toLowerCase(java.util.Locale.ROOT);
        int slash = type.indexOf('/');
        if (slash >= 0) type = type.substring(slash + 1);
        if ("jpg".equals(type)) return "jpeg";
        return type;
    }

    /**
     * 把会话管理接口的返回码翻译成给用户看的提示。0 返回空串（成功，由调用方自己提示）。
     */
    public static String sessionErrorMsg(int code) {
        switch (code) {
            case 0:
                return "";
            case -101:
                return ApiErrorText.loginRequired();
            case -400:
                return "请求错误，会话可能已不存在";
            default:
                return ApiErrorText.opFailed(code);
        }
    }

    private static String getDevId() {
        char[] b = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        char[] s = "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".toCharArray();
        for (int i = 0; i < s.length; i++) {
            if ('-' == s[i] || '4' == s[i]) {
                continue;
            }
            int randomInt = (int) (16 * Math.random());
            if ('x' == s[i]) {
                s[i] = b[randomInt];
            } else {
                s[i] = b[3 & randomInt | 8];
            }
        }
        return new String(s);
    }

    // 由于接口特殊性定制的textReplaceEmote
    public static SpannableStringBuilder textReplaceEmote(String text, JSONArray emote, float scale, Context context) throws JSONException, ExecutionException, InterruptedException {
        SpannableStringBuilder result = new SpannableStringBuilder(text);
        if (emote != null && emote.length() > 0) {
            for (int i = 0; i < emote.length(); i++) {    //遍历每一个表情包
                JSONObject key = emote.getJSONObject(i);

                String name = key.getString("text");
                String emoteUrl = key.getString("url");
                int size = key.getInt("size");  //B站十分贴心的帮你把表情包大小都写好了，快说谢谢蜀黍

                EmoteUtil.replaceSingle(result, name, emoteUrl, size, scale, context);
            }
        }
        return result;
    }
}
