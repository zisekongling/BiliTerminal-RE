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

    /** 会话类型：与用户私信（会话对象里的 session_type，也是 session_svr 接口的参数值） */
    public static final int SESSION_TYPE_USER = 1;
    /** 会话类型：粉丝团（talker_id 是粉丝团 id 而不是 mid） */
    public static final int SESSION_TYPE_FAN_GROUP = 2;

    /**
     * get_sessions 的**请求参数** session_type：1 用户与系统。
     *
     * <p>26.11.23 踩过的坑：请求参数和会话对象里的 session_type **不是同一套枚举**。
     * 请求侧是 1 用户与系统 / 2 未关注人 / 3 粉丝团 / 4 所有 / 5 被拦截 / 6 花火 / 7 所有系统 /
     * 8 陌生人 / 9 关注的人与系统（bilibili-API/docs/message/private_msg.md:254）；
     * 会话对象侧只有 1 用户 / 2 粉丝团（同文件第 10 行）。之前这里混用了
     * {@link #SESSION_TYPE_USER}，结果应援团私信永远拉不到。
     */
    public static final int LIST_TYPE_USER = 1;

    /** get_sessions 的请求参数 session_type：3 粉丝团（应援团 / 官方群）。 */
    public static final int LIST_TYPE_FAN_GROUP = 3;

    /** 拉用户会话的消息（session_type=1）。粉丝团会话走带 sessionType 的重载。 */
    public static JSONObject getPrivateMsg(long talkerId, int size, long beginSeqno, long endSeqno)
            throws IOException, JSONException {
        return getPrivateMsg(talkerId, SESSION_TYPE_USER, size, beginSeqno, endSeqno);
    }

    /**
     * 拉一个会话的消息（返回的是倒序的列表，使用时记得列表倒置）。
     *
     * @param sessionType 会话类型：1 用户会话、2 粉丝团（口径同会话对象里的 session_type，
     *                    见 bilibili-API/docs/message/private_msg.md:10、1489）
     * @param beginSeqno  起点序号，传 0 时不按序号筛
     * @param endSeqno    终点序号，传 0 表示取最新的一页
     */
    public static JSONObject getPrivateMsg(long talkerId, int sessionType, int size, long beginSeqno, long endSeqno)
            throws IOException, JSONException {
        JSONObject allMsgJson = NetWorkUtil.getJson(buildFetchMsgsUrl(talkerId, sessionType, size, beginSeqno, endSeqno));
        if (allMsgJson.has("data") && !allMsgJson.isNull("data"))
            return allMsgJson.getJSONObject("data");
        else return new JSONObject();
    }

    /**
     * 拼拉消息的 URL（纯函数，便于 JVM 单测）。
     *
     * <p>26.11.23：原来 session_type 写死 1，粉丝团会话（talker_id 是粉丝团 id）一条也拉不出来，
     * 现在由调用方按会话类型传进来。
     */
    public static String buildFetchMsgsUrl(long talkerId, int sessionType, int size, long beginSeqno, long endSeqno) {
        return "https://api.vc.bilibili.com/svr_sync/v1/svr_sync/fetch_session_msgs?session_type=" + sessionType
                + "&talker_id=" + talkerId
                + "&size=" + size
                + "&begin_seqno=" + beginSeqno
                + "&end_seqno=" + endSeqno;
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

    /** 会话列表默认页大小。接口 size 上限是 100（见 bilibili-API/docs/message/private_msg.md:260）。 */
    public static final int SESSIONS_PAGE_SIZE = 20;

    /** 一页会话：服务端给的会话 + 还有没有下一页（`data.has_more`）。 */
    public static class SessionsPage {
        public final ArrayList<PrivateMsgSession> sessions;
        public final boolean hasMore;

        public SessionsPage(ArrayList<PrivateMsgSession> sessions, boolean hasMore) {
            this.sessions = sessions;
            this.hasMore = hasMore;
        }
    }

    public static ArrayList<PrivateMsgSession> getSessionsList(int size)
            throws IOException, JSONException {
        return getSessionsPage(size, 0).sessions;
    }

    /**
     * 拉一页会话（反馈 #166：只加载十几个就不加载了）。
     *
     * <p>接口事实（bilibili-API/docs/message/private_msg.md:258-260、280-281）：`get_sessions`
     * 用 `end_ts`（**微秒级**时间戳）当游标往前翻，`size` 默认 20、上限 100，响应里
     * `data.has_more` 表示还有没有下一页。原来客户端只调一次 size=20、也从来不看 has_more，
     * 所以永远只有二十来条。
     *
     * @param size  本页条数
     * @param endTs 游标：上一页里最老的会话 `session_ts`（微秒）。传 0 表示拉第一页。
     */
    public static SessionsPage getSessionsPage(int size, long endTs)
            throws IOException, JSONException {
        return getSessionsPage(size, endTs, LIST_TYPE_USER);
    }

    /**
     * 拉一页指定类型的会话（26.11.23 加：应援团/官方群走 {@link #LIST_TYPE_FAN_GROUP}）。
     *
     * @param size        本页条数
     * @param endTs       游标：上一页里最老的会话 session_ts（微秒），传 0 表示第一页
     * @param listType    get_sessions 的请求参数 session_type（{@link #LIST_TYPE_USER} /
     *                    {@link #LIST_TYPE_FAN_GROUP}），**不是**会话对象的 session_type
     */
    public static SessionsPage getSessionsPage(int size, long endTs, int listType)
            throws IOException, JSONException {
        JSONObject root = NetWorkUtil.getJson(buildSessionsUrl(size, endTs, listType));
        requireOk(root);
        return new SessionsPage(parseSessionsList(root), parseSessionsHasMore(root));
    }

    /** 拼会话列表 URL（纯函数，便于单测）；endTs 才决定带不带翻页游标。 */
    static String buildSessionsUrl(int size, long endTs) {
        return buildSessionsUrl(size, endTs, LIST_TYPE_USER);
    }

    /** 拼会话列表 URL：listType 是 get_sessions 的请求参数 session_type。 */
    public static String buildSessionsUrl(int size, long endTs, int listType) {
        StringBuilder url = new StringBuilder(
                "https://api.vc.bilibili.com/session_svr/v1/session_svr/get_sessions?session_type=")
                .append(listType).append("&size=").append(size);
        if (endTs > 0) url.append("&end_ts=").append(endTs);
        return url.toString();
    }

    /** 响应里的 `data.has_more`（纯解析，便于 JVM 单测）。缺字段视为没有更多。 */
    public static boolean parseSessionsHasMore(JSONObject root) {
        if (root == null || root.isNull("data")) return false;
        JSONObject data = root.optJSONObject("data");
        return data != null && data.optInt("has_more", 0) == 1;
    }

    /**
     * 检查会话列表的业务码（纯函数，便于 JVM 单测）。
     *
     * <p>26.11.23（反馈 #177）：掉登录后 `get_sessions` 回的是 `{"code":-101,...}`，没有 `data`，
     * 而 {@link #parseSessionsList} 对 `data==null` 直接返回空表 → 界面上就是「0 条会话」，
     * 用户以为私信被清空了，只有退出重进才知道是登录态没了。业务码不是 0 时按码给一句
     * 能照着操作的话，让界面分得清「没登录 / 被风控 / 请求太频繁 / 真没消息」。
     *
     * @throws JSONException 业务码非 0；message 里带 code，便于用户直接抄给开发者
     */
    public static void requireOk(JSONObject root) throws JSONException {
        if (root == null) throw new JSONException("会话列表返回为空");
        int code = root.optInt("code", 0);
        if (code == 0) return;
        String message = root.optString("message", "");
        String detail = message.isEmpty() ? "" : " " + message;
        switch (code) {
            case -101:
                throw new JSONException("请先登录后查看私信（code=-101）");
            case -352:
                throw new JSONException("被风控拦了，过一会儿再试（code=-352）" + detail);
            case -412:
                throw new JSONException("请求太频繁，歇一会儿再试（code=-412）" + detail);
            default:
                throw new JSONException("会话列表加载失败：code=" + code + detail);
        }
    }

    /**
     * 解析会话列表响应（纯解析，便于 JVM 单测）。
     *
     * <p>26.11.20：**不再丢弃系统会话**。get_sessions 用的 session_type=1 语义本来就是「用户与系统」，
     * UP主小助手 / 系统通知 / 客服这些官方会话本来就在 session_list 里；以前按 account_info 把它们
     * 过滤掉之后，single_unread 里 biz_msg_* / custom_unread 那部分未读照样被算进主菜单角标，
     * 可 App 里没有任何入口能点进去读掉 → 角标只涨不退（用户反馈「一直有消息消除不掉」）。
     */
    public static ArrayList<PrivateMsgSession> parseSessionsList(JSONObject root) throws JSONException {
        ArrayList<PrivateMsgSession> sessionList = new ArrayList<>();
        if (root == null || root.isNull("data")) {
            return sessionList;
        }
        // 用 opt 系列而不是 get：没有会话时服务端会把 session_list 直接给 null
        // （bilibili-API/docs/message/private_msg.md:280），getJSONArray 会抛 JSONException，
        // 翻到最后一页时正好会撞上。
        JSONObject data = root.optJSONObject("data");
        if (data == null) return sessionList;
        JSONArray sessions = data.optJSONArray("session_list");
        if (sessions == null) return sessionList;
        for (int i = 0; i < sessions.length(); ++i) {
            sessionList.add(parseSession(sessions.getJSONObject(i)));
        }
        return sessionList;
    }

    /** 接口里的长整型字段常常给字符串，parse 不出来按 0 算（26.11.23）。 */
    private static long parseLongOrZero(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * 解析单条会话（纯解析，便于 JVM 单测）。
     *
     * <p>普通会话与系统会话共用同一套字段，差别只在 account_info（仅系统会话有）、
     * system_msg_type 与 biz_msg_unread_count（通知类未读，角标要看 unreadTotal()）。
     */
    public static PrivateMsgSession parseSession(JSONObject sessionJson) throws JSONException {
        PrivateMsgSession session = new PrivateMsgSession();
        session.talkerUid = sessionJson.getLong("talker_id");

        JSONObject lastMsg = sessionJson.optJSONObject("last_msg");
        if (lastMsg != null) {
            session.contentType = lastMsg.optInt("msg_type", 0);
            String content = lastMsg.optString("content", "");

            if (content.endsWith("}") && content.startsWith("{"))
                session.content = new JSONObject(content);
        }

        // opt 而不是 get：系统会话不一定带 unread_count，缺字段按 0 算
        session.unread = sessionJson.optInt("unread_count", 0);
        session.bizUnread = sessionJson.optInt("biz_msg_unread_count", 0);
        session.systemMsgType = sessionJson.optInt("system_msg_type", 0);
        JSONObject accountInfo = sessionJson.optJSONObject("account_info");
        if (accountInfo != null) {
            session.accountName = accountInfo.optString("name", "");
            session.accountAvatar = accountInfo.optString("pic_url", "");
        }
        session.topTs = sessionJson.optLong("top_ts", 0);
        // 免打扰状态（26.11.23）：会话里是 is_dnd，0 关闭 / 1 开启
        session.dnd = sessionJson.optInt("is_dnd", 0) == 1;
        // 微秒级的会话时间戳，翻页时当 end_ts 游标用（#166）
        session.sessionTs = sessionJson.optLong("session_ts", 0);
        // 已读游标（26.11.23）：注意是字符串型，optLong 直接取会拿到 0
        session.maxSeqno = parseLongOrZero(sessionJson.optString("max_seqno", "0"));
        session.ackSeqno = parseLongOrZero(sessionJson.optString("ack_seqno", "0"));
        // 会话类型（26.11.23）：会话对象自己的口径，缺字段按 1（用户会话）算。
        // 粉丝团会话（talker_id 是粉丝团 id）要靠它决定拉消息/已读时带 session_type=2。
        session.sessionType = sessionJson.optInt("session_type", SESSION_TYPE_USER);
        // 粉丝团字段只在 session_type=2 时出现，缺了就是空
        session.groupName = sessionJson.optString("group_name", "");
        session.groupCover = sessionJson.optString("group_cover", "");
        session.groupType = sessionJson.optInt("group_type", 0);
        // 推送设置：0 接收 / 1 不接收（会话对象里的 setting，见 private_msg.md:22）
        session.pushSetting = sessionJson.optInt("setting", 0);
        // 是否被拦截（is_intercept）
        session.intercept = sessionJson.optInt("is_intercept", 0) == 1;
        return session;
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
                    // 与 get_sessions 共用同一条解析，免得两处各写一遍、慢慢跑偏
                    sessionList.add(parseSession(sessions.getJSONObject(i)));
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

    /** 视频链接前缀：拼私信正文与「失败回退复制」共用，避免两处各写一遍拼出不同链接。 */
    public static final String VIDEO_LINK_PREFIX = "https://www.bilibili.com/video/";

    /** 私信正文里标题最多保留的字符数（超出截断加省略号），防止长标题把 2000 字节上限顶穿。 */
    public static final int VIDEO_SHARE_TITLE_MAX = 60;

    /**
     * 拼视频完整链接（纯函数）。
     *
     * <p>私信正文和「发送失败时复制链接」走同一个函数，免得两处各拼一次、拼出两种链接。
     * bvid 为空时返回空串，调用方据此跳过「复制链接」这一步。
     */
    public static String buildVideoLink(String bvid) {
        return (bvid == null || bvid.isEmpty()) ? "" : VIDEO_LINK_PREFIX + bvid;
    }

    /**
     * 组装「把视频发给好友」的私信正文（纯函数，见 PrivateMsgApiTest）。
     *
     * <p>为什么发文本而不是分享卡片：发送私信的文档
     * （{@code bilibili-API/docs/message/private_msg.md}「发送私信（web端）」）明确写着
     * 「仅支持发送 msg[msg_type] 为 1、2 或 5 的私信」；卡片是 {@code msg_type=7}
     * （{@code private_msg_content.md}「分享消息」），不在支持范围内，硬发只会拿到
     * 21035「该类消息暂时无法发送」。所以这里退一步用 {@code msg_type=1} 发
     * 「标题 + 完整链接」——对方点链接照样能打开视频，也不至于静默失败。
     *
     * <p>正文里只有标题和链接，不带任何中文前缀：本方法要能被 JVM 单测直接调用，
     * 而纯函数读不到 strings.xml（见 docs/agents/dev-conventions.md）。
     *
     * @param title 视频标题，可为 null
     * @param bvid  BV 号；为空时只发标题，避免拼出半截链接
     */
    public static String buildVideoShareText(String title, String bvid) {
        String safeTitle = title == null ? "" : title.trim();
        if (safeTitle.length() > VIDEO_SHARE_TITLE_MAX)
            safeTitle = safeTitle.substring(0, VIDEO_SHARE_TITLE_MAX) + "…";
        String link = buildVideoLink(bvid);
        if (link.isEmpty()) return safeTitle;
        if (safeTitle.isEmpty()) return link;
        return safeTitle + "\n" + link;
    }

    /**
     * 组装 {@code msg_type=1} 的 content：接口只认 {@code {"content":"..."}} 这一种结构
     * （{@code private_msg_content.md}「文字消息」）。
     *
     * <p>用 JSONObject 序列化而不是手拼字符串，标题里带引号/换行也不会拼出非法 JSON。
     */
    public static String buildTextContent(String text) {
        JSONObject content = new JSONObject();
        try {
            content.put("content", text == null ? "" : text);
        } catch (JSONException ignored) {
            // put 一个 String 不会失败，这里只是满足受检异常签名
        }
        return content.toString();
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

        return postJson(url, per.toString());
    }

    /**
     * POST 一个表单并解析成 JSON。
     *
     * <p>session_svr 这一族的接口失败时会回 4xx 且 body 是 HTML，直接 new JSONObject 会抛；
     * 这里统一收口：拿不到 2xx 就返回空对象，调用方 optInt("code", -1) 自然拿到 -1。
     */
    private static JSONObject postJson(String url, String form) throws IOException, JSONException {
        Response response = NetWorkUtil.post(url, form, NetWorkUtil.webHeaders);
        if (response == null) return new JSONObject();
        try (Response resp = response) {
            if (!resp.isSuccessful() || resp.body() == null) return new JSONObject();
            return new JSONObject(resp.body().string());
        }
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

    /** 拦截状态值（bilibili-API/docs/message/private_msg.md:1320-1379）：0 取消拦截、1 设置拦截。 */
    public static final int INTERCEPT_OFF = 0;
    public static final int INTERCEPT_ON = 1;

    /** 布尔转 update_intercept 的 status 值（纯函数，便于 JVM 单测）：true→1 拦截，false→0 取消。 */
    public static int statusForIntercept(boolean intercept) {
        return intercept ? INTERCEPT_ON : INTERCEPT_OFF;
    }

    /**
     * 拦截 / 取消拦截一个会话（消息进「被拦截」列表，对方消息不再打扰）。
     *
     * <p>接口事实（bilibili-API/docs/message/private_msg.md:1320-1379）：POST
     * session_svr/v1/session_svr/update_intercept，表单是 **talker_id + status**，
     * **没有 session_type 参数**——所以不能复用 {@link #postSessionAction}。
     *
     * @return 接口返回码，0 表示成功
     */
    public static int updateIntercept(long talkerId, boolean intercept)
            throws IOException, JSONException {
        String url = "https://api.vc.bilibili.com/session_svr/v1/session_svr/update_intercept";
        String csrf = NetWorkUtil.currentCsrf();
        String per = "talker_id=" + talkerId
                + "&status=" + statusForIntercept(intercept)
                + "&csrf_token=" + csrf
                + "&csrf=" + csrf
                + "&build=0"
                + "&mobi_app=web";
        JSONObject result = postJson(url, per);
        return result.optInt("code", -1);
    }

    /** 免打扰设置值（bilibili-API/docs/message/private_msg.md:1210）：0 取消免打扰、1 开启免打扰。 */
    public static final int DND_OFF = 0;
    public static final int DND_ON = 1;

    /** 布尔转接口的 setting 值（纯函数，便于 JVM 单测）：true→1 开启，false→0 取消。 */
    public static int settingForDnd(boolean dnd) {
        return dnd ? DND_ON : DND_OFF;
    }

    /**
     * 修改会话免打扰状态。
     *
     * <p>接口事实（bilibili-API/docs/message/private_msg.md:1197-1242）：这个接口**不在 session_svr 下**，
     * 而是 link_setting（和聊天设置页的推送开关同一族）；参数也不是 talker_id，而是
     * uid（自己的 mid）+ setting + dnd_uid（对方 mid）。
     *
     * @param ownUid    自己的 mid（接口的 uid 参数）
     * @param talkerUid 会话对方的 mid（接口的 dnd_uid 参数）
     * @param dnd       true 开启免打扰，false 取消
     * @return 接口返回码，0 表示成功
     */
    public static int setMsgDnd(long ownUid, long talkerUid, boolean dnd)
            throws IOException, JSONException {
        String url = "https://api.vc.bilibili.com/link_setting/v1/link_setting/set_msg_dnd";
        String csrf = NetWorkUtil.currentCsrf();
        String per = "uid=" + ownUid
                + "&setting=" + settingForDnd(dnd)
                + "&dnd_uid=" + talkerUid
                + "&csrf_token=" + csrf
                + "&csrf=" + csrf
                + "&build=0"
                + "&mobi_app=web";

        Response response = NetWorkUtil.post(url, per, NetWorkUtil.webHeaders);
        if (response == null) return -1;
        try (Response resp = response) {
            if (!resp.isSuccessful() || resp.body() == null) return -1;
            return parseDndCode(resp.body().string());
        }
    }

    /**
     * 解析 set_msg_dnd 响应体里的业务码（纯解析，便于 JVM 单测）。
     *
     * <p>非 2xx（412 风控 / 5xx）时 body 是 HTML，解析不出来给 -1，再由调用方翻成能看懂的话。
     */
    public static int parseDndCode(String body) {
        if (body == null || body.isEmpty()) return -1;
        try {
            return new JSONObject(body).optInt("code", -1);
        } catch (JSONException e) {
            return -1;
        }
    }

    /** 会话推送设置值（bilibili-API/docs/message/private_msg.md:941-1011、1259-1318）：0 接收推送、1 不接收。 */
    public static final int PUSH_ON = 0;
    public static final int PUSH_OFF = 1;

    /** 布尔转 set_push_ss 的 setting 值（纯函数，便于 JVM 单测）：true→0 接收，false→1 不接收。 */
    public static int pushSettingFor(boolean push) {
        return push ? PUSH_ON : PUSH_OFF;
    }

    /** follow_status 的拉黑值（bilibili-API/docs/message/private_msg.md:941-1011）。 */
    public static final int FOLLOW_STATUS_BLOCKED = 128;

    /**
     * 单会话的聊天设置（{@code get_session_ss} 里聊天设置页要用的那几项）。
     *
     * <p>只留三项：是否接收推送、服务器要不要显示推送开关、与对方的关系状态
     * （128 表示已拉黑）。其余字段（special 特别关注等）本页不用。
     */
    public static class SessionSs {
        /** true 接收推送。服务器缺字段时按接收处理。 */
        public boolean pushEnabled = true;
        /** 服务器是否要求显示「接收消息推送」开关（0 时不显示，官方建议按此隐藏）。 */
        public boolean showPushSetting = false;
        /** 关系状态：0 未关注 / 2 已关注 / 6 已互粉 / 128 已拉黑。 */
        public int followStatus = 0;
    }

    /**
     * 构造 get_session_ss 的地址（纯函数，便于 JVM 单测）。
     *
     * <p>接口事实（bilibili-API/docs/message/private_msg.md:941-1011）：GET
     * link_setting/v1/link_setting/get_session_ss，**仅对用户会话有效**（粉丝团会话没有这个概念）。
     */
    public static String buildSessionSsUrl(long talkerUid) {
        return "https://api.vc.bilibili.com/link_setting/v1/link_setting/get_session_ss"
                + "?talker_uid=" + talkerUid
                + "&build=0"
                + "&mobi_app=web";
    }

    /** 解析 get_session_ss 的响应（纯函数）：缺字段时给「接收推送 + 不显示开关」。 */
    public static SessionSs parseSessionSs(JSONObject root) {
        SessionSs ss = new SessionSs();
        if (root == null) return ss;
        JSONObject data = root.optJSONObject("data");
        if (data == null) return ss;
        ss.pushEnabled = data.optInt("push_setting", PUSH_ON) == PUSH_ON;
        ss.showPushSetting = data.optInt("show_push_setting", 0) == 1;
        ss.followStatus = data.optInt("follow_status", 0);
        return ss;
    }

    /**
     * 拉一个用户会话的聊天设置。
     *
     * @return 解析结果；接口报错（code 非 0）时抛异常，由调用方兜底
     */
    public static SessionSs getSessionSs(long talkerUid) throws IOException, JSONException {
        JSONObject root = NetWorkUtil.getJson(buildSessionSsUrl(talkerUid));
        requireOk(root);
        return parseSessionSs(root);
    }

    /**
     * 设置会话推送（图文消息与稿件推送；通知类消息不受影响）。
     *
     * <p>接口事实（bilibili-API/docs/message/private_msg.md:1259-1318）：POST
     * link_setting/v1/link_setting/set_push_ss，表单是 talker_uid + setting，**没有 session_type**，
     * 仅支持用户会话。
     *
     * @return 接口返回码，0 表示成功
     */
    public static int setPushSs(long talkerUid, boolean push) throws IOException, JSONException {
        String url = "https://api.vc.bilibili.com/link_setting/v1/link_setting/set_push_ss";
        String csrf = NetWorkUtil.currentCsrf();
        String per = "talker_uid=" + talkerUid
                + "&setting=" + pushSettingFor(push)
                + "&csrf_token=" + csrf
                + "&csrf=" + csrf
                + "&build=0"
                + "&mobi_app=web";
        JSONObject result = postJson(url, per);
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

        // 非 2xx（412 风控 / 5xx）时 body 是 HTML，解析会抛 JSONException；postJson 返回空对象，
        // 调用方 optInt("code", -1) 拿到 -1，再由 sessionErrorMsg 翻译成人话
        return postJson(url, per);
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
