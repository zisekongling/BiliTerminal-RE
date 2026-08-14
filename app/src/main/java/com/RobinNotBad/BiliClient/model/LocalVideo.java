package com.RobinNotBad.BiliClient.model;

import java.util.ArrayList;

public class LocalVideo {
    public String cover;
    public String title;
    public String folderName;       // 所属文件夹名称，空字符串表示未分类
    public ArrayList<String> pageList;
    public ArrayList<String> videoFileList;
    public ArrayList<String> danmakuFileList;
    public ArrayList<Long> sizeList;
    public ArrayList<String> qualityList;
    public long size;
    public long aid;                // B站视频aid（用于API调用）
    public long cid;                // B站视频cid（用于API调用）

    public LocalVideo() {
    }

    public void calcTotalSize() {
        size = 0;
        for (long pageSize : sizeList) {
            size += pageSize;
        }
    }

    /**
     * 获取画质显示标签
     * 根据 B站 qn 值返回对应的画质文本
     */
    public static String getQualityLabel(int qn) {
        if (qn >= 120) return "4K";
        if (qn >= 116) return "2K";
        if (qn >= 112) return "1080P高码率";
        if (qn >= 80) return "1080P";
        if (qn >= 74) return "720P60";
        if (qn >= 64) return "720P";
        if (qn >= 32) return "480P";
        if (qn >= 16) return "360P";
        return "";
    }
}
