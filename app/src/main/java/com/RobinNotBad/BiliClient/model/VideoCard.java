package com.RobinNotBad.BiliClient.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

public class VideoCard implements Parcelable, Serializable {
    public String title;
    public String upName;
    public String view;
    public String cover;
    public String type = "video";
    public long aid;
    public String bvid;
    public long cid = 0;
    public long viewAt = 0;
    /**
     * 番剧剧集 id（epid）。只有来源是历史记录的番剧卡片（type=media_bangumi）才有值：
     * 观看记录里的 aid（history.oid）是剧集 avid 而不是 media_id，必须靠 epid 反查真正的
     * media_id，并用它定位"上次看到哪一集"。追番列表/动态等来源的番剧卡片为 0。
     */
    public long epid = 0;
    /**
     * 已观看进度，<b>单位是秒</b>（与 x/web-interface/history/cursor 返回的 progress 字段一致）。
     * 注意别和 PlayerData.progress 混淆，后者单位是毫秒；换算只在传给播放器时做一次。
     */
    public int progress = 0;
    /**
     * 视频总时长，<b>单位是秒</b>。只有稍后再看列表（x/v2/history/toview/web 的 duration 字段）
     * 会填，用来把「已看完」和「没看完」分开；其它来源保持 0（未知）。
     */
    public long duration = 0;
    /**
     * 收藏内容类型（x/v3/fav/resource/list 每条 media 的 type 字段）：
     * 2=视频稿件 / 12=音频 / 21=视频合集。只有收藏夹列表会填，其它来源默认 2。
     * 批量操作（batch-deal / copy / move）的 resources 参数必须带它，
     * 否则音频的 auid、合集的 id 会被当成视频 avid 发出去。
     */
    public int favType = 2;
    /**
     * 推荐流里的 track_id，用于「减少推荐（不感兴趣）」；非推荐流来源的卡片为空串。
     * 它与 aid 一起构成 dislike 接口的定位参数（见 {@code FeedbackApi.dislike}）。
     */
    public String trackId = "";
    /**
     * UP 主 mid（接口的 {@code owner.mid}），用于「减少推荐」；0 表示未知。
     * 0 时仍可发请求（服务端按 aid 定位），但「不想看此 UP 主」这类按 UP 主生效的原因就用不了。
     */
    public long upMid = 0;

    public VideoCard(String title, String upName, String view, String cover, long aid, String bvid, String type) {
        this.title = title;
        this.upName = upName;
        this.view = view;
        this.cover = cover;
        this.aid = aid;
        this.bvid = bvid;
        this.type = type;
    }

    public VideoCard(String title, String upName, String view, String cover, long aid, String bvid, Long cid) {
        this.title = title;
        this.upName = upName;
        this.view = view;
        this.cover = cover;
        this.aid = aid;
        this.bvid = bvid;
        this.cid = cid;
    }

    public VideoCard(String title, String upName, String view, String cover, long aid, String bvid) {
        this.title = title;
        this.upName = upName;
        this.view = view;
        this.cover = cover;
        this.aid = aid;
        this.bvid = bvid;
    }

    public VideoCard() {
    }

    protected VideoCard(Parcel in) {
        title = in.readString();
        upName = in.readString();
        view = in.readString();
        cover = in.readString();
        type = in.readString();
        aid = in.readLong();
        bvid = in.readString();
        cid = in.readLong();
        viewAt = in.readLong();
        // 新增字段只能追加在末尾，且读/写顺序必须严格一致（顺序错位不会崩，只会静默串数据）
        epid = in.readLong();
        progress = in.readInt();
        duration = in.readLong();
        // 与 writeToParcel 末尾一一对应：新增字段一律追加在最后，顺序错位不会崩，只会静默串数据
        favType = in.readInt();
        trackId = in.readString();
        upMid = in.readLong();
    }

    public static final Creator<VideoCard> CREATOR = new Creator<>() {
        @Override
        public VideoCard createFromParcel(Parcel in) {
            return new VideoCard(in);
        }

        @Override
        public VideoCard[] newArray(int size) {
            return new VideoCard[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(title);
        parcel.writeString(upName);
        parcel.writeString(view);
        parcel.writeString(cover);
        parcel.writeString(type);
        parcel.writeLong(aid);
        parcel.writeString(bvid);
        parcel.writeLong(cid);
        parcel.writeLong(viewAt);
        // 与构造器里的读取顺序一一对应
        parcel.writeLong(epid);
        parcel.writeInt(progress);
        parcel.writeLong(duration);
        parcel.writeInt(favType);
        // 「减少推荐」的定位参数，追加在最后（与构造器读取顺序一致）
        parcel.writeString(trackId);
        parcel.writeLong(upMid);
    }
}
