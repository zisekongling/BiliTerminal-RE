package com.RobinNotBad.BiliClient.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * 视频文件夹模型，用于管理缓存视频的分类文件夹
 */
public class VideoFolder {
    public String name;           // 文件夹名称
    public long createTime;       // 创建时间戳
    public ArrayList<String> videoTitles;  // 文件夹内的视频标题列表

    public VideoFolder() {
        this.videoTitles = new ArrayList<>();
    }

    public VideoFolder(String name) {
        this.name = name;
        this.createTime = System.currentTimeMillis();
        this.videoTitles = new ArrayList<>();
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("name", name != null ? name : "");
            json.put("createTime", createTime);
            JSONArray arr = new JSONArray();
            if (videoTitles != null) {
                for (String title : videoTitles) {
                    arr.put(title);
                }
            }
            json.put("videoTitles", arr);
        } catch (Exception ignored) {}
        return json;
    }

    public static VideoFolder fromJson(JSONObject json) {
        VideoFolder folder = new VideoFolder();
        try {
            folder.name = json.optString("name", "");
            folder.createTime = json.optLong("createTime", System.currentTimeMillis());
            folder.videoTitles = new ArrayList<>();
            JSONArray arr = json.optJSONArray("videoTitles");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    folder.videoTitles.add(arr.optString(i, ""));
                }
            }
        } catch (Exception ignored) {}
        return folder;
    }

    public int getVideoCount() {
        return videoTitles != null ? videoTitles.size() : 0;
    }

    public void addVideo(String title) {
        if (videoTitles == null) videoTitles = new ArrayList<>();
        if (!videoTitles.contains(title)) {
            videoTitles.add(title);
        }
    }

    public boolean removeVideo(String title) {
        if (videoTitles != null) {
            return videoTitles.remove(title);
        }
        return false;
    }

    public boolean containsVideo(String title) {
        return videoTitles != null && videoTitles.contains(title);
    }
}