package com.RobinNotBad.BiliClient.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

public class UserInfo implements Parcelable, Serializable {

    /**
     * 显式序列化版本号（批次 1 补）。
     *
     * <p>类里实现了 {@code Serializable} 却没有声明 UID，一旦哪天有人把它写进文件/缓存，
     * 之后每加一个字段默认 UID 就会变，老数据直接 {@code InvalidClassException}。
     * 钉死成 1 之后，新增字段对老数据是「读不到就给默认值」，与本批新增的三个字段语义一致。
     *
     * <p>当前全仓没有任何地方把 UserInfo 序列化落盘（grep putSerializable / ObjectOutputStream 均无命中），
     * 所以现在补是零风险；Parcelable 那条路不受 UID 影响。
     */
    private static final long serialVersionUID = 1L;

    public long mid;
    public String name;
    public String avatar;
    public String sign;
    public int fans;
    public int level;
    public int following;
    public boolean followed;
    public String notice;

    public int official;
    public String officialDesc;
    public long mtime;

    public int vip_role = 0;
    public String vip_nickname_color = "";

    public long current_exp = 0;
    public long next_exp = 0;

    public String medal_name = "";
    public int medal_level = 0;

    public String sys_notice = "";

    public LiveRoom live_room = null;

    public int is_senior_member = 0;

    public boolean is_follow_display = false;

    // ==================== 批次 1 新增（社交与信息） ====================
    /**
     * 性别，接口原值（`x/space/wbi/acc/info` 的 `sex`：男 / 女 / 保密）。
     *
     * <p>保持原值不做加工，展示时统一走 {@link #displaySex()}——「保密要不要显示成一行」
     * 这类判断只应该有一处。
     */
    public String sex = "";

    /** IP 属地（同一接口的 `ip_location`，如「上海」）；隐私设置关闭时为空串。 */
    public String ipLocation = "";

    /**
     * 是否与「列表主体」互相关注（B 站 `attribute == 6`）。
     *
     * <p>只在**查看自己的**关注/粉丝/分组列表时才有意义：看别人的列表时
     * `attribute == 6` 表示「那个人与这一条互关」，不是「我与他互关」，
     * 直接显示「互关」会误导用户。所以由 {@code FollowApi} 在解析时就把「是不是自己的列表」
     * 折进来，adapter 只管按这个字段显示/隐藏。
     */
    public boolean mutualFollow = false;

    public UserInfo(long mid, String name, String avatar, String sign, int fans, int following, int level, boolean followed, String notice, int official, String officialDesc, int is_senior_member) {
        this.mid = mid;
        this.name = name;
        this.avatar = avatar;
        this.sign = sign;
        this.fans = fans;
        this.level = level;
        this.following = following;
        this.followed = followed;
        this.notice = notice;
        this.official = official;
        this.officialDesc = officialDesc;
        this.is_senior_member = is_senior_member;
    }

    public UserInfo(long mid, String name, String avatar, String sign, int fans, int following, int level, boolean followed, String notice, int official, String officialDesc, String sys_notice, LiveRoom live_room, int is_senior_member) {
        this.mid = mid;
        this.name = name;
        this.avatar = avatar;
        this.sign = sign;
        this.fans = fans;
        this.level = level;
        this.following = following;
        this.followed = followed;
        this.notice = notice;
        this.official = official;
        this.officialDesc = officialDesc;
        this.sys_notice = sys_notice;
        this.live_room = live_room;
        this.is_senior_member = is_senior_member;
    }

    public UserInfo(long mid, String name, String avatar, String sign, int fans, int following, int level, boolean followed, String notice, int official, String officialDesc, long current_exp, long next_exp, int is_senior_member) {
        this.mid = mid;
        this.name = name;
        this.avatar = avatar;
        this.sign = sign;
        this.fans = fans;
        this.level = level;
        this.following = following;
        this.followed = followed;
        this.notice = notice;
        this.official = official;
        this.officialDesc = officialDesc;
        this.current_exp = current_exp;
        this.next_exp = next_exp;
        this.is_senior_member = is_senior_member;
    }

    public UserInfo(long mid, String name, String avatar, String sign, int fans, int following, int level, boolean followed, String notice, int official, String officialDesc, int vip_role, String sys_notice, LiveRoom live_room, int is_senior_member) {
        this.mid = mid;
        this.name = name;
        this.avatar = avatar;
        this.sign = sign;
        this.fans = fans;
        this.level = level;
        this.following = following;
        this.followed = followed;
        this.notice = notice;
        this.official = official;
        this.officialDesc = officialDesc;
        this.vip_role = vip_role;
        this.sys_notice = sys_notice;
        this.live_room = live_room;
        this.is_senior_member = is_senior_member;
    }

    public UserInfo(long mid, String name, String avatar, String sign, int fans, int following, int level, boolean followed, String notice, int official, String officialDesc, long mtime, int is_senior_member) {
        this.mid = mid;
        this.name = name;
        this.avatar = avatar;
        this.sign = sign;
        this.fans = fans;
        this.level = level;
        this.following = following;
        this.followed = followed;
        this.notice = notice;
        this.official = official;
        this.officialDesc = officialDesc;
        this.mtime = mtime;
        this.is_senior_member = is_senior_member;
    }

    public UserInfo() {
    }

    public UserInfo(JSONObject userInfoJson) throws JSONException {
        this.level = userInfoJson.getJSONObject("level_info").getInt("current_level");
        this.mid = userInfoJson.getLong("mid");
        this.name = userInfoJson.getString("uname");
        this.avatar = userInfoJson.getString("avatar");
        this.is_senior_member = userInfoJson.getInt("is_senior_member");
        JSONObject vip = userInfoJson.getJSONObject("vip");
        this.vip_role = vip.getInt("vipStatus");
        this.vip_nickname_color = vip.getString("nickname_color");
        if ((!userInfoJson.isNull("fans_detail")) && (!SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_MEDAL, false))) {
            JSONObject fans_detail = userInfoJson.getJSONObject("fans_detail");
            this.medal_name = fans_detail.getString("medal_name");
            this.medal_level = fans_detail.getInt("level");
        }
    }

    protected UserInfo(Parcel in) {
        mid = in.readLong();
        name = in.readString();
        avatar = in.readString();
        sign = in.readString();
        fans = in.readInt();
        level = in.readInt();
        following = in.readInt();
        followed = in.readByte() != 0;
        notice = in.readString();
        official = in.readInt();
        officialDesc = in.readString();
        mtime = in.readLong();
        vip_role = in.readInt();
        vip_nickname_color = in.readString();
        current_exp = in.readLong();
        next_exp = in.readLong();
        medal_name = in.readString();
        medal_level = in.readInt();
        sys_notice = in.readString();
        live_room = in.readParcelable(LiveRoom.class.getClassLoader());
        is_senior_member = in.readInt();
        is_follow_display = in.readByte() != 0;
        // 批次 1 追加字段：**必须**与 writeToParcel 的顺序一一对应，且只往末尾追加，
        // 否则跨页面传参（Activity 间 Bundle 走 Parcel）会整体错位。
        String parcelSex = in.readString();
        sex = parcelSex == null ? "" : parcelSex;
        String parcelIp = in.readString();
        ipLocation = parcelIp == null ? "" : parcelIp;
        mutualFollow = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mid);
        dest.writeString(name);
        dest.writeString(avatar);
        dest.writeString(sign);
        dest.writeInt(fans);
        dest.writeInt(level);
        dest.writeInt(following);
        dest.writeByte((byte) (followed ? 1 : 0));
        dest.writeString(notice);
        dest.writeInt(official);
        dest.writeString(officialDesc);
        dest.writeLong(mtime);
        dest.writeInt(vip_role);
        dest.writeString(vip_nickname_color);
        dest.writeLong(current_exp);
        dest.writeLong(next_exp);
        dest.writeString(medal_name);
        dest.writeInt(medal_level);
        dest.writeString(sys_notice);
        dest.writeParcelable(live_room, flags);
        dest.writeInt(is_senior_member);
        dest.writeByte((byte) (is_follow_display ? 1 : 0));
        // 与 readFromParcel 同序：sex → ipLocation → mutualFollow，只追加不改顺序
        dest.writeString(sex == null ? "" : sex);
        dest.writeString(ipLocation == null ? "" : ipLocation);
        dest.writeByte((byte) (mutualFollow ? 1 : 0));
    }

    /**
     * 可展示的性别：接口回「保密」/空串时返回空串，表示这一项不显示。
     *
     * <p>纯函数（不碰 Android），便于 JVM 单测覆盖「保密不显示」这个判定。
     */
    public String displaySex() {
        String raw = sex == null ? "" : sex.trim();
        if (raw.isEmpty()) return "";
        // 服务端在用户设置「保密」时回的就是这两个字；个别老接口回「未知」
        if ("保密".equals(raw) || "未知".equals(raw)) return "";
        return raw;
    }

    /** 可展示的 IP 属地：空串表示不显示（与 {@link #displaySex()} 同口径）。 */
    public String displayIpLocation() {
        String raw = ipLocation == null ? "" : ipLocation.trim();
        return raw;
    }

    /** 性别与 IP 属地是否至少有一项可展示——整行显隐由它决定。 */
    public boolean hasGenderIpMeta() {
        return !displaySex().isEmpty() || !displayIpLocation().isEmpty();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<UserInfo> CREATOR = new Creator<>() {
        @Override
        public UserInfo createFromParcel(Parcel in) {
            return new UserInfo(in);
        }

        @Override
        public UserInfo[] newArray(int size) {
            return new UserInfo[size];
        }
    };
}
