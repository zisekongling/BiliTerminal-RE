package com.RobinNotBad.BiliClient.event;

import com.RobinNotBad.BiliClient.model.Reply;

public class ReplyEvent {
    private int type;
    private Reply message;
    private int pos;
    private long oid;

    public ReplyEvent(int type, Reply message, long oid) {
        this.type = type;
        this.message = message;
        this.oid = oid;
    }

    public ReplyEvent(int type, Reply message, int pos, long oid) {
        this.type = type;
        this.message = message;
        this.pos = pos;
        this.oid = oid;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public Reply getMessage() {
        return message;
    }

    public void setMessage(Reply message) {
        this.message = message;
    }

    public int getPos() {
        return pos;
    }

    public void setPos(int pos) {
        this.pos = pos;
    }

    public long getOid() {
        return oid;
    }

    public void setOid(long oid) {
        this.oid = oid;
    }

    /**
     * 这条事件是否表示「新的一级评论」（不是楼中楼回复）。
     *
     * <p>一级评论要插进列表顶部附近，楼中楼要挂到它所属的父评论下面——两种处理完全不同。
     * 判断条件集中在这里，免得每个接收方各写一遍（写歪一个就会出现「楼中楼被插到列表顶部」）。
     */
    public boolean isRootReply() {
        return message != null && message.root == 0;
    }

    /** 新评论的 rpid；没有评论体（异常事件）时返回 0。 */
    public long rpid() {
        return message != null ? message.rpid : 0L;
    }
}
