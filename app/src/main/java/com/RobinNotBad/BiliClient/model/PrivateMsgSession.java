package com.RobinNotBad.BiliClient.model;

import org.json.JSONObject;

public class PrivateMsgSession {
    public long talkerUid = 0;
    public int unread = 0;
    public int contentType = 0;
    public JSONObject content;

    /**
     * 会话置顶时间（微秒级时间戳），未置顶为 0。
     * 服务端没有布尔型的"是否置顶"字段，只能判非零，见 {@link #isTop()}。
     */
    public long topTs = 0;

    /**
     * 会话最后一条消息的时间（微秒级时间戳），翻页时作为 get_sessions 的 end_ts 游标。
     * 见 PrivateMsgApi.getSessionsPage()（反馈 #166）。
     */
    public long sessionTs = 0;

    /**
     * 会话里已读到哪一条（max_seqno）。
     * 26.11.23：update_ack 的 ack_seqno 要的就是它；原来没解析，ack 只能传 0，
     * 服务端不认，会话未读永远消不掉（#281）。
     */
    public long maxSeqno = 0;

    /**
     * 未读的"通知"类消息数（会话里的 biz_msg_unread_count）。
     *
     * <p>系统会话（UP主小助手 / 系统通知 / 客服…）的未读主要记在这个字段里，
     * 普通私信走 {@link #unread}，所以角标要么看 {@link #unreadTotal()}，别单看 unread。
     */
    public int bizUnread = 0;

    /**
     * 系统会话类型（会话里的 system_msg_type）：0 非系统会话、1 主播小助手、5 系统通知、
     * 7 UP主小助手、8 客服、9 支付小助手。见 bilibili-API/docs/message/private_msg.md:31。
     */
    public int systemMsgType = 0;

    /** 系统会话的显示名（account_info.name）；普通会话为空串。 */
    public String accountName = "";

    /** 系统会话的头像（account_info.pic_url）；普通会话为空串。 */
    public String accountAvatar = "";

    public PrivateMsgSession() {
    }

    /**
     * 是不是 B 站官方的系统会话（UP主小助手 / 系统通知 / 客服 / 支付小助手…）。
     *
     * <p>这些会话的 talker_id 是服务端合成的（如 844424930131966），不是真实 mid，
     * 既查不到 user/cards、也没有用户主页可跳。主判据是 system_msg_type 非 0，
     * 字段缺失时退回"有没有 account_info.name"（account_info 只在系统会话里出现）。
     */
    public boolean isSystem() {
        return systemMsgType > 0 || (accountName != null && !accountName.isEmpty());
    }

    /**
     * 角标与排序要用的未读总数：普通私信未读 + 通知未读。
     *
     * <p>26.11.20：原来角标只看 unread，系统会话的 biz_msg_unread_count 永远显示不出来。
     */
    public int unreadTotal() {
        return unread + bizUnread;
    }

    public boolean isTop() {
        return topTs > 0;
    }
}
