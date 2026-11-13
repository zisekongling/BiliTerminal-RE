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

    /**
     * 这条视频的 UP 主 mid：没有 staff（稿件被删 / 接口没返回）时返回 0。
     *
     * <p>**口径**：非联合投稿时 `staff` 就是由 `owner` 拼出来的（见 `VideoInfoApi.getInfoByJson`），
     * 取第一个即投稿人；联合投稿时接口下发的 `staff` 列表把投稿者排在首位（样例里
     * `staff[0].title == "UP主"`），但接口没有明文契约保证顺序——真遇到排错再按 `title` 筛。
     *
     * <p>连播「按 UP 主投稿续播」只认这个值，**不能用登录账号的 mid 顶替**
     * （见 {@link PlayerData#uploaderMid}）。
     */
    public long uploaderMid() {
        if (staff == null || staff.isEmpty()) return 0L;
        UserInfo first = staff.get(0);
        return first == null ? 0L : first.mid;
    }

    public PlayerData toPlayerData(int index) {
        PlayerData data = new PlayerData();
        data.aid = aid;
        data.cid = cids.get(index);
        data.title = pagenames.size() == 1 ? title : pagenames.get(index);
        // 两个 mid 不是一回事：mid = 当前登录账号（直播弹幕 uid / 登录态判据），
        // uploaderMid = 这条视频的作者（连播用）。以前这里把登录 mid 塞进去当作者，
        // 连播就会去拉登录账号自己的投稿，连播层标题也变成自己的昵称。
        data.mid = SharedPreferencesUtil.getLong("mid", 0);
        data.uploaderMid = uploaderMid();
        // SponsorBlock 只认 BVID：详情页拿得到就随数据带进播放器，省掉播放器再按 aid 查一次
        data.bvid = bvid == null ? "" : bvid;
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
