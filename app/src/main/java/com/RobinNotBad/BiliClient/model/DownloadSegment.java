package com.RobinNotBad.BiliClient.model;

import java.util.ArrayList;

/**
 * playurl 里的**一段** durl（反馈 #87/#90）。
 *
 * <p>背景：老接口（`fnval=1`，720P 及以下的合一 MP4/flv）在文件较大时会把同一份文件切成多段，
 * `durl` 数组里每一段各有一个地址。以前下载链路只取 `durl.getJSONObject(0)`，于是只下了第一段，
 * 落盘的是一个能打开、但只有开头的残片。
 *
 * <p>这里保留服务端给出的全部信息，供下载器按 {@link #order} 顺序逐段下载再拼接：
 * <ul>
 *   <li>{@link #url} 主线路，{@link #backupUrls} playurl 的 `backup_url`（CDN 掐断时换线重试用）；</li>
 *   <li>{@link #sizeBytes} 这一段的字节数，-1 = 服务端没给（此时进度预算退回探测）；</li>
 *   <li>{@link #lengthMs} 这一段的时长（毫秒），-1 = 未知。仅用于日志与核对，不参与拼接判据。</li>
 * </ul>
 *
 * <p>这个类刻意不依赖 Android，也不参与 PlayerData 的 Parcel：它属于"这一次取流"的附属信息，
 * 跨 Intent/存盘之后本就过期（候选线路同理）。要长期保留请序列化到 download 表的 `segments` 列。
 */
public class DownloadSegment {
    /** 段序号，0 起，**连续**无空洞；拼接顺序就是它。 */
    public int order;
    /** 主线路地址。 */
    public String url = "";
    /** 备用线路（`backup_url`），主线路在前。 */
    public ArrayList<String> backupUrls = new ArrayList<>();
    /** 这一段的字节数，-1 = 服务端未声明。 */
    public long sizeBytes = -1L;
    /** 这一段的时长（毫秒），-1 = 服务端未声明。 */
    public long lengthMs = -1L;
}
