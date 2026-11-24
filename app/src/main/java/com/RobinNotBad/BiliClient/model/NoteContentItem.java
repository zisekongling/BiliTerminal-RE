package com.RobinNotBad.BiliClient.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 笔记详情页的一行渲染单元（26.11.23 笔记改造）。
 *
 * <p>与 {@link NoteBlock} 的区别：NoteBlock 忠实对应 Quill delta 的每个元素
 * （一个元素可能只写半句话、只带一种样式），这里已经把「连续的文本元素」合并成段落，
 * 图片与跳转标签各自单独成行。生成逻辑是 {@code NoteApi.buildContent}，纯函数、有单测。
 */
public class NoteContentItem {

    /** 普通段落。 */
    public static final String TYPE_PARAGRAPH = "text";

    /** 图片。 */
    public static final String TYPE_IMAGE = "image";

    /** 跳转标签（点一下跳到视频对应进度）。 */
    public static final String TYPE_TAG = "tag";

    public String type = TYPE_PARAGRAPH;

    /** 段落文本；列表项已带 {@code "1. "} / {@code "• "} 前缀。图片/标签行不用。 */
    public String text = "";

    /** 段内样式段，下标相对 {@link #text}；段落整体没有样式时为空表。 */
    public List<NoteTextRun> runs = new ArrayList<>();

    /** 图片地址（协议相对的 {@code //i0.hdslb.com/...} 已在解析时补成 https）。 */
    public String imageUrl = "";

    /** 图片宽度（服务端存的值比实际小 2，只作参考）。 */
    public int imageWidth;

    public long tagCid;

    /** 分P 序号（0 起）；-1 表示服务端没给。 */
    public int tagIndex = -1;

    public long tagSeconds;

    public String tagTitle = "";
}
