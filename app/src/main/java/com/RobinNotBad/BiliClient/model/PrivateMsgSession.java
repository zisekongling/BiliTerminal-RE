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

    public PrivateMsgSession() {
    }

    public boolean isTop() {
        return topTs > 0;
    }
}
