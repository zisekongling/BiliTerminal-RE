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
    /**
     * 这条视频的 BV 号（{@code BV1xxxxxxxxx}），空串表示入口不知道。
     *
     * <p>**为什么要有它**：SponsorBlock 的查询键是 BVID（哈希接口也是对它做 SHA-256），
     * 而 B 站播放接口只给 aid/cid。以前播放器里的 {@code bvid} 只能从
     * {@code enterable_info} 里抠（还只在多P 时才带），UGC 视频基本拿不到
     * （见 {@code PlayerActivity#loadHighEnergyData} 的同名局部变量）。
     *
     * <p>由 {@code VideoInfo.toPlayerData} 填，随 Intent 进播放器；为空时播放器按 aid 现查一次。
     */
    public String bvid = "";
    /**
     * **当前登录账号**的 mid，不是视频作者。
     *
     * <p>它只用来回答「我是谁」：直播弹幕握手包的 {@code uid}、以及各入口
     * 「有没有登录态」的粗判据（如 {@code JumpToPlayerActivity.reportProgressAsync}）。
     *
     * <p>**别拿它当 UP 主**：连播「按 UP 主投稿续播」要的是这条视频的作者，
     * 见 {@link #uploaderMid}。历史上两者共用一个字段，`VideoInfo.toPlayerData` 往这里
     * 塞登录 mid，于是连播拉到的是登录账号自己的投稿（26.11.06 修）。
     */
    public long mid;
    /**
     * 这条视频的 UP 主 mid（连播「按 UP 主投稿续播」用）。
     *
     * <p>由 {@code VideoInfo.toPlayerData} 从 {@code staff[0]}（联合投稿取第一作者）填，
     * 随 Intent / 播放会话哨兵一路传到播放器。{@code 0} 表示入口不知道作者
     * （本地缓存、番剧、旧版会话），播放器会按 aid 现查一次
     * （{@code PlayerActivity#resolveUploaderMid}），查不到就不提供 UP 主连播。
     */
    public long uploaderMid = 0;
    public int progress = 0;
    public long cidHistory = 0;
    /**
     * 服务端 `last_play_time` 的原值（毫秒，未做单位换算/合理性过滤前的数字）。
     *
     * <p>**为什么需要它**（反馈 #83）：`progress` 只在 `last_play_cid == cid` 时才会采纳
     * `last_play_time`（见 `PlayerApi.adoptLastPlayTime`），所以 `progress == 0` 分不清
     * 「从来没看过」和「上次看的是别的分P」。"要不要续到上次那个分P"必须看这个原值 &gt; 0，
     * 判据收敛在 `MultiPageResumePolicy.resumePageIndex`。
     */
    public long lastPlayTimeMs = 0;
    // 番剧（PGC）专用字段。投稿视频的观看进度走 x/v2/history/report，只有 aid/cid 两个维度；
    // 番剧必须走心跳接口并带上 epid/sid/sub_type，否则观看记录与续播进度都不会被服务端更新——
    // 表现就是"在终端里看的番剧，历史里不出现，下次还得从头播"。
    public long epid = 0;        // 剧集 epid，就是 Bangumi.Episode#id
    public long seasonId = 0;    // 所在季的 season_id
    public int seasonType = 0;   // 所在季类型（Bangumi.Info#type），作为心跳的 sub_type
    public int type = 0;
    public long timeStamp;
    public ArrayList<String> pagenames;
    public ArrayList<Long> cids;
    /**
     * 这一份 pagenames/cids 是不是"收藏夹虚拟合集"。
     *
     * <p>**必须显式标记，不能靠猜**：两种模式的 cids 语义完全不同——
     * 真实多P 存的是真 cid，收藏夹虚拟合集存的是每个视频的 **aid**。
     * 播放器曾用 `aid != cids[pageIndex]` 来分辨，而真实多P 下 av 号与 cid 永不相等，
     * 于是恒判为虚拟合集：切P 时把 cid 当 aid 去查，`x/web-interface/view` 返回
     * 62002 稿件不可见 → cid 落成 0 → 取流 -400 → **切P 直接失败、播放器卡在原地**
     * （26.10.15 真机复现）。
     */
    public boolean virtualCollection = false;
    // 注：合集层 / 分P层（"子合集"）的进出**由用户在选集卡片上点那一行**触发，层状态本身是
    // PlayerActivity 的私有字段、不走 Intent。
    //
    // 唯一的例外是**起播的那一条**：它的播放列表是构建方直接拼的，不走播放器的
    // switchToOnlinePage，所以播放器拿不到它的 videoInfo、也就不知道"这条能不能进得去"。
    // 构建方已经把这份数据请求过了（为了拿首个 cid），顺手带过去，省掉播放器再请求一次。
    public VideoInfo enterableInfo = null;
    public int currentPageIndex = 0;
    public DashData dashData; // DASH格式数据
    public String audioUrl = ""; // 单独的音频URL（用于仅音频下载）
    // 视频 / 音频流的候选线路（主线路在前，playurl 的 backup_url 备用线路按序去重追加）。
    // 只给下载器用：主线路在某台设备/某个视频上持续掐断时换线重试（反馈 #136）。
    // 和 dashData 一样不参与 Parcel —— 它是"这一次取流"的附属信息，跨 Intent / 存盘后本就过期，
    // 需要的一方重新取流即可（见 PlayerApi#rememberUrlCandidates）。
    public ArrayList<String> videoUrlCandidates = new ArrayList<>();
    public ArrayList<String> audioUrlCandidates = new ArrayList<>();
    // 老接口（合一的 MP4/flv）的**完整分段清单**（反馈 #87/#90）：服务端会把一份较大的文件切成
    // 多段 durl，下载器要按 order 逐段下载再拼接，只下第 0 段就是一个能打开但短了的残片。
    // 与 videoUrlCandidates 同样是"这一次取流"的附属信息，不参与 Parcel。
    public ArrayList<DownloadSegment> videoSegments = new ArrayList<>();
    // 直播专用：进入播放器前已经把全部可选地址排好序（见 util/live/LiveStreamSelector）。
    // 直播流"连接还在、但长时间没有数据"时，播放器按这个顺序换 CDN / 换 HLS 重连，
    // 不必回头再请求一次取流接口（也就不会因为请求失败而彻底没得救）。
    public ArrayList<String> liveUrlCandidates = new ArrayList<>();

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
        // cidHistory / lastPlayTimeMs 必须一起序列化：多P 视频「续到上次那个P」要跟着 Intent
        // 传到 MultiPageActivity（视频详情页 → 选集页），漏掉的话那边拿到的永远是 0。
        cidHistory = in.readLong();
        lastPlayTimeMs = in.readLong();
        type = in.readInt();
        timeStamp = in.readLong();
        pagenames = in.createStringArrayList();
        cids = new ArrayList<>();
        in.readList(cids, Long.class.getClassLoader());
        currentPageIndex = in.readInt();
        audioUrl = in.readString();
        epid = in.readLong();
        seasonId = in.readLong();
        seasonType = in.readInt();
        liveUrlCandidates = in.createStringArrayList();
        if (liveUrlCandidates == null) liveUrlCandidates = new ArrayList<>();
        virtualCollection = in.readInt() != 0;
        enterableInfo = in.readParcelable(VideoInfo.class.getClassLoader());
        // 新字段一律追加在末尾：read/write 顺序必须严格一致，插在中间会把后面所有字段读错位
        uploaderMid = in.readLong();
        bvid = in.readString();
        if (bvid == null) bvid = "";
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
        dest.writeLong(cidHistory);
        dest.writeLong(lastPlayTimeMs);
        dest.writeInt(type);
        dest.writeLong(timeStamp);
        dest.writeStringList(pagenames);
        dest.writeList(cids);
        dest.writeInt(currentPageIndex);
        dest.writeString(audioUrl);
        dest.writeLong(epid);
        dest.writeLong(seasonId);
        dest.writeInt(seasonType);
        dest.writeStringList(liveUrlCandidates);
        dest.writeInt(virtualCollection ? 1 : 0);
        dest.writeParcelable(enterableInfo, flags);
        // 新字段一律追加在末尾（见构造函数里的同一处注释）
        dest.writeLong(uploaderMid);
        dest.writeString(bvid);
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
