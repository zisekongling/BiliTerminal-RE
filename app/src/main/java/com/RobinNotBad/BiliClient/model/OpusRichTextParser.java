package com.RobinNotBad.BiliClient.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 把 B 站富文本节点解析成 {@link RichTextSegment}。**纯函数 + org.json，不碰 Android**，
 * 所以能整体放进 JVM 单测（`OpusRichTextParserTest`）。
 *
 * 节点字段以仓库里的接口快照为准（`bilibili-API/docs/opus/rich_text_nodes.md`，18 种
 * `RICH_TEXT_NODE_TYPE_*`）：
 * - AT：`{orig_text, rid, text, type}`，rid 是用户 mid；
 * - TOPIC：`{orig_text, text, jump_url, type}`；
 * - WEB：`{jump_url, orig_text, text, style, type}`，**text 可能被服务端缩短**
 *   （示例里 `text="av1"`、`jump_url="//www.bilibili.com/video/av1"`），点击必须用 jump_url；
 * - EMOJI：`{text, emoji:{icon_url, size, ...}, type}`；
 * - 其余（TEXT / GOODS / BV / AV / CV / LOTTERY / VOTE / VIEW_PICTURE / MAIL / OGV_* …）
 *   一律按文字输出：内容不丢，只是暂时不可点。
 *
 * 两条 Opus 解析路径（段落 `text.nodes[].rich`、整篇 `rich_list`）都走这里，
 * 保证同一种节点在摘要和正文里表现一致。
 */
public final class OpusRichTextParser {

    private OpusRichTextParser() {
    }

    /**
     * 解析一组富文本节点。
     *
     * @param nodes 服务端下发的节点数组，可为 null
     * @return 顺序与入参一致；null 元素与空文本节点被跳过（空文本挂 span 会在渲染时抛越界）
     */
    public static List<RichTextSegment> parse(JSONArray nodes) {
        if (nodes == null || nodes.length() == 0) return Collections.emptyList();
        List<RichTextSegment> segments = new ArrayList<>(nodes.length());
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node == null) continue;
            RichTextSegment segment = parseOne(node);
            if (segment != null && !segment.text.isEmpty()) segments.add(segment);
        }
        return segments;
    }

    /**
     * 解析单个富文本节点。
     *
     * @return 永远非 null（未知类型也会退化成文字节点）；入参为 null 时返回 null
     */
    public static RichTextSegment parseOne(JSONObject node) {
        if (node == null) return null;
        String type = node.optString("type", "");
        // text 是显示文本，orig_text 是原始文本：两者一般相同，个别节点（如 WEB）只有 orig_text
        String text = node.optString("text", "");
        if (text.isEmpty()) text = node.optString("orig_text", "");
        switch (type) {
            case "RICH_TEXT_NODE_TYPE_AT":
                return new RichTextSegment(RichTextSegment.KIND_AT, text, node.optLong("rid", 0), null, null, 0);
            case "RICH_TEXT_NODE_TYPE_TOPIC":
                return new RichTextSegment(RichTextSegment.KIND_TOPIC, text, 0, node.optString("jump_url", ""), null, 0);
            case "RICH_TEXT_NODE_TYPE_WEB":
                return new RichTextSegment(RichTextSegment.KIND_WEB, text, 0, node.optString("jump_url", ""), null, 0);
            case "RICH_TEXT_NODE_TYPE_EMOJI": {
                JSONObject emoji = node.optJSONObject("emoji");
                if (emoji == null) {
                    // 没有表情对象时按文字走：[xxx] 这样的占位文本比空白好
                    return new RichTextSegment(RichTextSegment.KIND_TEXT, text, 0, null, null, 0);
                }
                return new RichTextSegment(RichTextSegment.KIND_EMOJI, text, 0, null,
                        emoji.optString("icon_url", ""), emoji.optInt("size", 0));
            }
            case "RICH_TEXT_NODE_TYPE_TEXT":
            default:
                // 快照里不存在的类型（历史上段落路径曾写死一个 RICH_TEXT_NODE_TYPE_RICH）
                // 与新增类型一律按文字输出：宁可不可点，也不要整段消失
                return new RichTextSegment(RichTextSegment.KIND_TEXT, text, 0, null, null, 0);
        }
    }
}
