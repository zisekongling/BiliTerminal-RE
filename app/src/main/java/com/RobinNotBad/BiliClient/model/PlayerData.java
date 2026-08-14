package com.RobinNotBad.BiliClient.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;

public class PlayerData implements Parcelable {
    public static int TYPE_VIDEO = 0;
    public static int TYPE_BANGUMI = 1;
    public static int TYPE_LIVE = 2;
    public static int TYPE_LOCAL = 4;

    public String title = "";
    public String videoUrl = "";
    public String danmakuUrl = "";
    public int qn = -1;
    public String[] qnStrList;
    public int[] qnValueList;
    public long aid;
    public long cid;
    public long mid;
    public int progress = 0;
    public long cidHistory = 0;
    public int type = 0;
    public long timeStamp;
    public ArrayList<String> pagenames;
    public ArrayList<Long> cids;
    public int currentPageIndex = 0;
    public DashData dashData; // DASH格式数据
    public String audioUrl = ""; // 单独的音频URL（用于仅音频下载）

    public PlayerData() {
    }

    public PlayerData(int type) {
        this.type = type;
    }

    protected PlayerData(Parcel in) {
        title = in.readString();
        videoUrl = in.readString();
        danmakuUrl = in.readString();
        qn = in.readInt();
        qnStrList = in.createStringArray();
        qnValueList = in.createIntArray();
        aid = in.readLong();
        cid = in.readLong();
        mid = in.readLong();
        progress = in.readInt();
        type = in.readInt();
        timeStamp = in.readLong();
        pagenames = in.createStringArrayList();
        cids = new ArrayList<>();
        in.readList(cids, Long.class.getClassLoader());
        currentPageIndex = in.readInt();
        audioUrl = in.readString();
        // dashData不序列化，下载时会重新获取
    }

    public static final Creator<PlayerData> CREATOR = new Creator<>() {
        @Override
        public PlayerData createFromParcel(Parcel in) {
            return new PlayerData(in);
        }

        @Override
        public PlayerData[] newArray(int size) {
            return new PlayerData[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(title);
        dest.writeString(videoUrl);
        dest.writeString(danmakuUrl);
        dest.writeInt(qn);
        dest.writeStringArray(qnStrList);
        dest.writeIntArray(qnValueList);
        dest.writeLong(aid);
        dest.writeLong(cid);
        dest.writeLong(mid);
        dest.writeInt(progress);
        dest.writeInt(type);
        dest.writeLong(timeStamp);
        dest.writeStringList(pagenames);
        dest.writeList(cids);
        dest.writeInt(currentPageIndex);
        dest.writeString(audioUrl);
        // dashData不序列化，下载时会重新获取
    }

    public boolean isVideo() {
        return type == TYPE_VIDEO;
    }

    public boolean isBangumi() {
        return type == TYPE_BANGUMI;
    }

    public boolean isLive() {
        return type == TYPE_LIVE;
    }

    public boolean isLocal() {
        return type == TYPE_LOCAL;
    }
}
