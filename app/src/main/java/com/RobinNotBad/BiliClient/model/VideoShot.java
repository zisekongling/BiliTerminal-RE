package com.RobinNotBad.BiliClient.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频快照（缩略图拼版）信息，来自 {@code x/player/videoshot}（{@code index=1}）。
 *
 * <p>依据 {@code bilibili-API/docs/video/snapshot.md} §获取视频快照（web端）：
 * {@code data} 给出拼版尺寸与时间表——
 * {@code img_x_len} × {@code img_y_len} 是每张拼版的网格，每格
 * {@code img_x_size} × {@code img_y_size} 像素；
 * {@code index} 是**秒级**时间表，与格子一一对应，顺序为「从左到右、从上到下」。
 *
 * <p>三个实测细节（26.11.27 无 Cookie 打 {@code aid=2&cid=62131} 得到）：
 * <ul>
 *   <li>{@code image} 是**数组**，长视频会有多张拼版（该例就是 2 张）；</li>
 *   <li>url 是**协议相对**写法（{@code //i0.hdslb.com/...}），用之前要补 {@code https:}；</li>
 *   <li>{@code index} 里**有重复秒值**（如 {@code 0,0,16,33}、{@code 136,136,153}），
 *       所以「按时间找格子」取的是最后一个不晚于它的那个，不能假设严格递增。</li>
 * </ul>
 *
 * @see com.RobinNotBad.BiliClient.util.player.VideoShotGrid
 */
public class VideoShot {

    /** 拼版图 url（已补全协议），通常 1 张、长视频多张 */
    public final List<String> images;

    /** 每个格子的时间点，单位**秒**；可能为空（服务端没给 {@code index}） */
    public final List<Integer> timeTableSec;

    /** 每张拼版每行的格子数（文档的「每行图片数」，一般 10） */
    public final int xLen;

    /** 每张拼版的行数（文档的「每列图片数」，一般 10） */
    public final int yLen;

    /** 单格像素宽（一般 160） */
    public final int xSize;

    /** 单格像素高（一般 90） */
    public final int ySize;

    public VideoShot(List<String> images, List<Integer> timeTableSec,
                     int xLen, int yLen, int xSize, int ySize) {
        this.images = images == null ? new ArrayList<>() : images;
        this.timeTableSec = timeTableSec == null ? new ArrayList<>() : timeTableSec;
        this.xLen = xLen;
        this.yLen = yLen;
        this.xSize = xSize;
        this.ySize = ySize;
    }
}
