package com.RobinNotBad.BiliClient.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.util.NetworkPlaybackQuality;
import com.RobinNotBad.BiliClient.util.SettingsKeys;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class VideoInfo implements Parcelable, Serializable { // 自定义类需要加这个才能传输

    public static final int COPYRIGHT_SELF = 1;
    public static final int COPYRIGHT_REPRINT = 2;

    public String bvid;
    public long aid;
    public String title;
    public ArrayList<UserInfo> staff = new ArrayList<>(); // UP主列表
    public String cover;
    public String description;
    public String duration;
    public Stats stats;
    public String timeDesc;
    public ArrayList<String> pagenames = new ArrayList<>();
    public ArrayList<Long> cids = new ArrayList<>();
    public List<At> descAts = new ArrayList<>();

    public boolean upowerExclusive; // 充电专属
    public String argueMsg; // 争议信息
    public boolean isCooperation; // 联合投稿
    public boolean isSteinGate; // 互动视频
    public boolean is360; // 全景视频

    public long epid; // 如果是番剧则不为空，应自动跳转
    public int copyright; // 是否转载
    public Collection collection;

    public VideoInfo() {
    }

    protected VideoInfo(Parcel in) {
        bvid = in.readString();
        aid = in.readLong();
        title = in.readString();
        staff = in.createTypedArrayList(UserInfo.CREATOR);
        cover = in.readString();
        description = in.readString();
        duration = in.readString();
        stats = in.readParcelable(Stats.class.getClassLoader());
        timeDesc = in.readString();
        pagenames = in.createStringArrayList();
        in.readList(cids, Long.class.getClassLoader());
        descAts = in.createTypedArrayList(At.CREATOR);
        upowerExclusive = in.readByte() != 0;
        argueMsg = in.readString();
        isCooperation = in.readByte() != 0;
        isSteinGate = in.readByte() != 0;
        is360 = in.readByte() != 0;
        epid = in.readLong();
        copyright = in.readInt();
        collection = in.readParcelable(Collection.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(bvid);
        dest.writeLong(aid);
        dest.writeString(title);
        dest.writeTypedList(staff);
        dest.writeString(cover);
        dest.writeString(description);
        dest.writeString(duration);
        dest.writeParcelable(stats, flags);
        dest.writeString(timeDesc);
        dest.writeStringList(pagenames);
        dest.writeList(cids);
        dest.writeTypedList(descAts);
        dest.writeByte((byte) (upowerExclusive ? 1 : 0));
        dest.writeString(argueMsg);
        dest.writeByte((byte) (isCooperation ? 1 : 0));
        dest.writeByte((byte) (isSteinGate ? 1 : 0));
        dest.writeByte((byte) (is360 ? 1 : 0));
        dest.writeLong(epid);
        dest.writeInt(copyright);
        dest.writeParcelable(collection, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<VideoInfo> CREATOR = new Creator<>() {
        @Override
        public VideoInfo createFromParcel(Parcel in) {
            return new VideoInfo(in);
        }

        @Override
        public VideoInfo[] newArray(int size) {
            return new VideoInfo[size];
        }
    };

    public VideoCard toCard() {
        // 不能直接取 staff.get(0)：联合投稿接口没返回 staff、UP主已注销、稿件被删等情况下 staff 会是空列表；
        // stats 同理可能为 null。转发预览卡（SendDynamicActivity）会走这里，越界/空指针会直接崩在发送页。
        String upName = "";
        if (staff != null && !staff.isEmpty() && staff.get(0) != null) {
            upName = staff.get(0).name == null ? "" : staff.get(0).name;
        }
        String viewStr = stats == null ? "" : StringUtil.toWan(stats.view);
        return new VideoCard(title, upName, viewStr, cover, aid, bvid);
    }

    public PlayerData toPlayerData(int index) {
        PlayerData data = new PlayerData();
        data.aid = aid;
        data.cid = cids.get(index);
        data.title = pagenames.size() == 1 ? title : pagenames.get(index);
        data.mid = SharedPreferencesUtil.getLong("mid", 0);
        // 起播清晰度按当前网络类型决定（Wi-Fi / 移动数据可分别配置，判定统一在 NetworkPlaybackQuality）。
        // BiliTerminal.context 在极早期/单测环境可能为 null，此时退回「只读默认清晰度」的老行为，不崩。
        android.content.Context context = BiliTerminal.context;
        data.qn = context != null
                ? NetworkPlaybackQuality.defaultQn(context)
                : SharedPreferencesUtil.getInt(SettingsKeys.PLAY_QN, NetworkPlaybackQuality.DEFAULT_QN);
        data.pagenames = pagenames;
        data.cids = cids;
        data.currentPageIndex = index;
        return data;
    }
}
