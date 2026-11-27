package com.RobinNotBad.BiliClient.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;

/**
 * 「我的反馈」列表里的一条反馈（26.10.16 新增）。
 *
 * <p>字段名与服务端 {@code GET /terminal/feedback/mine} 返回的 JSON 一致，
 * 解析在 {@code TerminalApi.parseMyFeedback}（纯函数，有 JVM 单测）。
 *
 * <p>实现 {@link Parcelable} 是为了从列表页把整条（含回复）直接传给详情页：
 * 既不用详情页再发一次网络请求，也不用把回复列表拆成多个 Intent extra。
 */
public class FeedbackItem implements Parcelable {
    public int id;
    /** wire 值：bug / suggestion / content / performance / other（显示名见 {@code R.string.feedback_category_*}）。 */
    public String category;
    public String content;
    /** 用户填的联系方式，可能为空。 */
    public String contact;
    /** 已格式化的可读时间（服务端返回的是 epoch 秒，解析时已换算）。 */
    public String ctime;
    /**
     * 开发者是否已受理。
     *
     * <p><b>文案必须用「待受理 / 已受理」，绝不要写「已处理」</b>：这只是开发者的待办状态，
     * 「无法复现」「不是 bug」也会被标掉，写「已处理」就是承诺了它做不到的事。
     */
    public boolean handled;
    /** 开发者回复，可能为空列表。 */
    public ArrayList<FeedbackReply> replies;
    /**
     * 服务端派生的三态：{@code waiting_dev} 待回复 / {@code replied} 已回复 / {@code closed} 已结束
     * （常数见 {@link com.RobinNotBad.BiliClient.util.FeedbackState}）。
     *
     * <p>与 {@link #handled} 正交：handled 是开发者的内部待办标记，用户看它得不到任何信息
     * （线上 88% 的反馈都停在同一个值上）；三态才是给用户看的「现在轮到谁」。
     *
     * <p>老服务端不返回这个字段，解析时落空串，显示前由 {@code FeedbackState.resolve}
     * 按回复情况回落成两态，所以拿不到它也不会显示错。
     */
    public String state;

    public FeedbackItem(int id, String category, String content, String contact,
                        String ctime, boolean handled, ArrayList<FeedbackReply> replies,
                        String state) {
        this.id = id;
        this.category = category;
        this.content = content;
        this.contact = contact;
        this.ctime = ctime;
        this.handled = handled;
        this.replies = replies;
        this.state = state;
    }

    protected FeedbackItem(Parcel in) {
        id = in.readInt();
        category = in.readString();
        content = in.readString();
        contact = in.readString();
        ctime = in.readString();
        handled = in.readByte() != 0;
        ArrayList<FeedbackReply> parsed = in.createTypedArrayList(FeedbackReply.CREATOR);
        replies = parsed != null ? parsed : new ArrayList<>();
        // 追加在末尾：老进程写出的 Parcel 没有这一项，读到末尾即 EOF，不会错位
        state = in.readString();
    }

    public static final Creator<FeedbackItem> CREATOR = new Creator<FeedbackItem>() {
        @Override
        public FeedbackItem createFromParcel(Parcel in) {
            return new FeedbackItem(in);
        }

        @Override
        public FeedbackItem[] newArray(int size) {
            return new FeedbackItem[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(category);
        dest.writeString(content);
        dest.writeString(contact);
        dest.writeString(ctime);
        dest.writeByte((byte) (handled ? 1 : 0));
        dest.writeTypedList(replies);
        dest.writeString(state);
    }
}
