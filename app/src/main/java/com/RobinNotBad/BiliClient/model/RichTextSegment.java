package com.RobinNotBad.BiliClient.model;

/**
 * 一个富文本节点（`rich_text_nodes` / Opus 段落里的 `text_node.rich`）的解析结果。
 *
 * 纯数据类，**不引用任何 Android 类型**：这样节点解析可以整体搬到 JVM 单测里跑
 * （见 {@link OpusRichTextParser} 与 `OpusRichTextParserTest`）。
 *
 * 背景：Opus 段落正文有两条解析路径——`OpusParagraph.analyzeText()`（段落 `text.nodes`
 * 里的 `TEXT_NODE_TYPE_RICH`）和 `analyzeOpus()`（整篇的 `rich_list`）。两条路径各写一份
 * `switch (type)`，段落那条漏了 AT / WEB，还留了一个 bilibili-API 快照里不存在的
 * `RICH_TEXT_NODE_TYPE_RICH` 死分支，于是同一篇图文里「@某某」在摘要能点、在段落里点不动。
 */
public class RichTextSegment {

    /** 普通文字（含服务端新增、客户端还不认识的类型：退化成文字，至少不丢内容）。 */
    public static final int KIND_TEXT = 0;
    /** `RICH_TEXT_NODE_TYPE_AT`：@用户，[atMid] 是用户 mid。 */
    public static final int KIND_AT = 1;
    /** `RICH_TEXT_NODE_TYPE_TOPIC`：话题，[jumpUrl] 用于解析话题 id。 */
    public static final int KIND_TOPIC = 2;
    /** `RICH_TEXT_NODE_TYPE_EMOJI`：表情，[emojiUrl] 是图片地址。 */
    public static final int KIND_EMOJI = 3;
    /** `RICH_TEXT_NODE_TYPE_WEB`：网页链接，[text] 是显示文本、[jumpUrl] 才是真地址。 */
    public static final int KIND_WEB = 4;

    public final int kind;
    /** 显示文本（可能为空：调用方会丢掉空文本节点，避免挂零长度 span）。 */
    public final String text;
    /** KIND_AT 的用户 mid；其余类型为 0。 */
    public final long atMid;
    /** KIND_TOPIC / KIND_WEB 的跳转地址；其余类型为 null。 */
    public final String jumpUrl;
    /** KIND_EMOJI 的图片地址；其余类型为 null。 */
    public final String emojiUrl;
    /** KIND_EMOJI 的相对大小（B 站给 1/2 之类的档位）；其余类型为 0。 */
    public final int emojiSize;

    public RichTextSegment(int kind, String text, long atMid, String jumpUrl, String emojiUrl, int emojiSize) {
        this.kind = kind;
        this.text = text == null ? "" : text;
        this.atMid = atMid;
        this.jumpUrl = jumpUrl;
        this.emojiUrl = emojiUrl;
        this.emojiSize = emojiSize;
    }
}
