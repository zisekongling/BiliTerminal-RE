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

    /**
     * 这条外部缓存在**对方**分支里属于哪个文件夹（空 = 未分类），只用于显示和迁移。
     *
     * 来自对方根目录的 .folders_info.json（缺失时退回该视频自己的 .video_meta.json）。
     * 刻意不写进本机的 folderName：那是本机 .folders_info.json 的键，直接搬过来会在本机
     * 凭空多出几个分组，以后同名的本机视频还会被它拽进夹里。真正落进本机夹发生在迁移时
     * （见 CacheMigrator 与 FolderManager.ensureFolderAndAdd）。
     */
    public String externalFolder;

    /**
     * 每一页（与 [pageList] 同下标）真正落在哪个根：空串 = 本机下载根，别的路径 = 那个外部根；
     * null 表示这条没有跨来源的页（单文件条目、以及本版之前扫出来的条目）。
     *
     * 为什么需要（反馈 #217 / #214）：番剧与多 P 合集的落盘结构是「季名父目录 + 集名子目录」，
     * 用户改过缓存位置之后，同一季的集可能一半在新根、一半留在旧根（旧根被当成外部来源）。
     * 缓存页把两边按页合并成一条显示，于是 [externalRoot] 那条「整条只读」的判据不够用了——
     * 删除、写弹幕、写续播都得按**这一页**落在哪算，否则要么删不到、要么写到别人分支的目录里。
     */
    public ArrayList<String> pageRootList;

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
