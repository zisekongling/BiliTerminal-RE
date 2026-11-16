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
    public long lastModified;       // 媒体文件最后修改时间（毫秒），缓存页「按更新时间」排序用；0 表示未知

    /**
     * 这条缓存是不是扫自**别的分支**的目录（绝对路径）；null 表示来自本机下载根。
     *
     * 分叉仓库各有各的 applicationId，缓存却都落在 Android/media/<包名> 或
     * Android/data/<包名>/files 下，所以缓存页会把这些目录当额外来源扫一遍（见
     * util/ExternalCacheRoots）。这类条目一律**只读**：删除、写元数据、写续播都要按这个字段避让，
     * 否则就是在别人分支的数据上动手。
     */
    public String externalRoot;

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
