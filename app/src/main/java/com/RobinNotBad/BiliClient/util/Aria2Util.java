package com.RobinNotBad.BiliClient.util;

/**
 * 内置多线程下载引擎参数读取。
 *
 * 已移除外部 Aria2（JSON-RPC）对接与单文件快速下载服务（SpeedDownloadService），
 * 下载统一由 DownloadService 承担，本类仅保留其参数读取。
 */
public class Aria2Util {

    private static final String PREF_ARIA2_ENABLED = "aria2_enabled";
    private static final String PREF_ARIA2_SPLIT = "aria2_split";
    private static final String PREF_PARALLEL_DOWNLOAD_VIDEOS = "parallel_download_videos";

    public static final int DEFAULT_PARALLEL_DOWNLOAD_VIDEOS = 3;

    /** 是否启用高速（多线程）下载模式。 */
    public static boolean isEnabled() {
        return SharedPreferencesUtil.getBoolean(PREF_ARIA2_ENABLED, true);
    }

    /** 单个文件的分片数上限。 */
    public static int getSplit() {
        return SharedPreferencesUtil.getInt(PREF_ARIA2_SPLIT, 5);
    }

    /** 并行下载的视频任务数。 */
    public static int getParallelDownloadVideos() {
        return SharedPreferencesUtil.getInt(PREF_PARALLEL_DOWNLOAD_VIDEOS, DEFAULT_PARALLEL_DOWNLOAD_VIDEOS);
    }
}
