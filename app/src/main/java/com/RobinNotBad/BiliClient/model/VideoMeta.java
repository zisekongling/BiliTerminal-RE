package com.RobinNotBad.BiliClient.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频元数据模型，存储在视频文件夹内的 .video_meta.json 中
 * 用于持久化视频的文件夹归属、画质、弹幕等额外信息
 */
public class VideoMeta {
    public String folderName;       // 所属文件夹名称，空字符串表示未分类
    public long aid;                // B站视频aid
    public long cid;                // B站视频cid
    public int qn;                  // 当前画质值
    public String title;            // 视频标题
    public String[] qnStrList;      // 可选画质标签列表，如 ["4K", "1080P", "720P"]
    public int[] qnValueList;       // 可选画质值列表，如 [120, 80, 64]
    public String downloadType;     // 下载类型："video" 或 "audio_only"

    public VideoMeta() {
        this.folderName = "";
        this.aid = 0;
        this.cid = 0;
        this.qn = 0;
        this.title = "";
        this.qnStrList = null;
        this.qnValueList = null;
        this.downloadType = "video";
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("folderName", folderName != null ? folderName : "");
            json.put("aid", aid);
            json.put("cid", cid);
            json.put("qn", qn);
            json.put("title", title != null ? title : "");
            json.put("downloadType", downloadType != null ? downloadType : "video");

            // 保存画质列表
            if (qnStrList != null) {
                JSONArray strArr = new JSONArray();
                for (String s : qnStrList) strArr.put(s);
                json.put("qnStrList", strArr);
            }
            if (qnValueList != null) {
                JSONArray valArr = new JSONArray();
                for (int v : qnValueList) valArr.put(v);
                json.put("qnValueList", valArr);
            }
        } catch (Exception ignored) {}
        return json;
    }

    public static VideoMeta fromJson(JSONObject json) {
        VideoMeta meta = new VideoMeta();
        try {
            meta.folderName = json.optString("folderName", "");
            meta.aid = json.optLong("aid", 0);
            meta.cid = json.optLong("cid", 0);
            meta.qn = json.optInt("qn", 0);
            meta.title = json.optString("title", "");
            meta.downloadType = json.optString("downloadType", "video");

            // 读取画质列表
            if (json.has("qnStrList")) {
                JSONArray strArr = json.getJSONArray("qnStrList");
                meta.qnStrList = new String[strArr.length()];
                for (int i = 0; i < strArr.length(); i++) {
                    meta.qnStrList[i] = strArr.optString(i);
                }
            }
            if (json.has("qnValueList")) {
                JSONArray valArr = json.getJSONArray("qnValueList");
                meta.qnValueList = new int[valArr.length()];
                for (int i = 0; i < valArr.length(); i++) {
                    meta.qnValueList[i] = valArr.optInt(i);
                }
            }
        } catch (Exception ignored) {}
        return meta;
    }
}