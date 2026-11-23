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

    /**
     * 会话类型（会话对象里的 session_type）：1 与用户私信、2 粉丝团（应援团 / 官方群）。
     *
     * <p>别和 get_sessions 的**请求参数**同名枚举混了：那边是 1 用户与系统、3 粉丝团
     * （bilibili-API/docs/message/private_msg.md:254）；这里是会话对象自己的口径
     * （同文件第 10 行）。调 session_svr 的接口（update_ack / set_top / remove_session）时，
     * 要用的是**这个**值。
     */
    public int sessionType = 1;

    /** 粉丝团名（group_name）；用户会话为空串。 */
    public String groupName = "";

    /** 粉丝团头像（group_cover）；用户会话为空串。 */
    public String groupCover = "";

    /** 粉丝团类型（group_type）：0 应援团、2 官方群；用户会话为 0。 */
    public int groupType = 0;

    /** 推送设置（会话里的 setting）：0 接收推送、1 不接收。缺字段按 0（当作接收）。 */
    public int pushSetting = 0;

    /** 是否被拦截（会话里的 is_intercept）：拦截后对方消息进「被拦截」列表。 */
    public boolean intercept = false;

    /** 会话已读到哪一条（会话里的 ack_seqno，字符串型）。 */
    public long ackSeqno = 0;

    public PrivateMsgSession() {
    }

    /**
     * 是不是粉丝团会话（应援团 / 官方群）。
     *
     * <p>26.11.23：这类会话的 talker_id 是**粉丝团 id**（比 mid 大得多），既不是真实用户，
     * 也不能按用户会话的方式拉消息——拉消息/已读都要带 session_type=2。
     */
    public boolean isFanGroup() {
        return sessionType == 2;
    }

    /** 是否接收这个会话的消息推送（会话里的 setting：0 接收 / 1 不接收）。 */
    public boolean isPushOn() {
        return pushSetting == 0;
    }

    /** 会话的显示名：粉丝团用团名，其余留给调用方按用户资料填；系统会话用 account_info。 */
    public String sessionName() {
        if (isFanGroup()) return groupName;
        if (isSystem()) return accountName;
        return "";
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

    /**
     * 是否对该会话开启了消息免打扰（会话里的 is_dnd：0 关闭、1 开启）。
     *
     * <p>见 bilibili-API/docs/message/private_msg.md:16；只影响服务端是否给这台设备推消息提醒，
     * 改状态走 {@link com.RobinNotBad.BiliClient.api.PrivateMsgApi#setMsgDnd}。
     */
    public boolean dnd = false;

    public boolean isDnd() {
        return dnd;
    }

    public boolean isTop() {
        return topTs > 0;
    }
}
