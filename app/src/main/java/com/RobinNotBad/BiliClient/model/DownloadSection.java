package com.RobinNotBad.BiliClient.model;

import android.database.Cursor;

import com.RobinNotBad.BiliClient.util.FileUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import java.io.File;

public class DownloadSection {
    public long id;
    public String type;
    public long aid;
    public long cid;
    public int qn;
    public String url_cover;
    public String title;
    public String child;
    public String name_short;
    public String state;
    public String downloadType; // 下载类型：video, audio_only
    public String audioUrl; // 音频流URL
    // 分段下载清单的 JSON（反馈 #87/#90，格式见 service/download/DownloadSegmentPlan）。
    // 空串 = 没有清单（老记录 / 单段文件），下载器按"重新取流"处理。
    public String segmentsJson;
    // 上次失败的可读原因（"服务器返回 HTTP 403" / "第 2/3 段下载失败"），列表直接显示；
    // 空串 = 没有原因可显示。两列都是 26.11.11 新增，老库 ALTER 之前读不到，故沿用
    // download_type / audio_url 的"列不存在就回落默认值"写法。
    public String failReason;
    
    // 并行下载进度追踪
    public volatile float progress = 0f;
    public volatile String downloadState = "";

    public DownloadSection() {
    }

    public DownloadSection(Cursor cursor) {
        id = cursor.getInt(0);
        type = cursor.getString(1);
        state = cursor.getString(2);
        aid = cursor.getLong(3);
        cid = cursor.getLong(4);
        qn = cursor.getInt(5);
        title = cursor.getString(6);
        child = cursor.getString(7);
        url_cover = cursor.getString(8);

        int downloadTypeIndex = cursor.getColumnIndex("download_type");
        if (downloadTypeIndex != -1) {
            downloadType = cursor.getString(downloadTypeIndex);
        }
        if (downloadType == null || downloadType.isEmpty()) {
            downloadType = "video";
        }

        int audioUrlIndex = cursor.getColumnIndex("audio_url");
        if (audioUrlIndex != -1) {
            audioUrl = cursor.getString(audioUrlIndex);
        }
        if (audioUrl == null) {
            audioUrl = "";
        }

        int segmentsIndex = cursor.getColumnIndex("segments");
        segmentsJson = segmentsIndex != -1 ? cursor.getString(segmentsIndex) : null;
        if (segmentsJson == null) {
            segmentsJson = "";
        }

        int failReasonIndex = cursor.getColumnIndex("fail_reason");
        failReason = failReasonIndex != -1 ? cursor.getString(failReasonIndex) : null;
        if (failReason == null) {
            failReason = "";
        }

        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(title.substring(0, Math.min(8, title.length())));
        sBuilder.append(title.length() > 7 ? "..." : "");

        if (type.equals("video_multi")) {
            sBuilder.append("-");
            sBuilder.append(child.substring(0, Math.min(8, child.length())));
            sBuilder.append(child.length() > 7 ? "..." : "");
        }

        // 如果是仅音频下载，在名称后添加标识
        if ("audio_only".equals(downloadType)) {
            sBuilder.append("[音频]");
        }

        name_short = sBuilder.toString();

    }

    /**
     * 只算出任务目录，<b>不创建目录</b>。
     *
     * <p>删除前要先判断「文件原本在不在」；{@link #getPath()} 会对视频任务顺手 mkdirs，
     * 拿它的结果去 exists() 永远为真，判不出文件是否真的缺失。需要判断存在性时用本方法。
     */
    public File peekPath() {
        if (type.contains("video")) {
            return FileUtil.getVideoDownloadPath(title, child);
        } else {
            return FileUtil.getPicturePath();
        }
    }

    public File getPath() {
        File path = peekPath();
        if (type.contains("video") && !path.exists()) {
            path.mkdirs();
        }
        return path;
    }

    /**
     * 是否是仅音频下载
     */
    public boolean isAudioOnly() {
        return "audio_only".equals(downloadType);
    }

    public PlayerData toPlayerData() {
        PlayerData data = new PlayerData();
        data.aid = aid;
        data.cid = cid;
        data.title = title;
        data.qn = qn;
        data.mid = SharedPreferencesUtil.getLong("mid", 0);
        return data;
    }
}
