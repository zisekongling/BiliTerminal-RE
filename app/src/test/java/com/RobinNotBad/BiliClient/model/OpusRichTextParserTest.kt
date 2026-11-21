package com.RobinNotBad.BiliClient.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住 [OpusRichTextParser] 的节点解析（调研清单第 2 条）。
 *
 * 背景：Opus 段落正文（`text.nodes[].rich`）与整篇 `rich_list` 是两条独立解析路径，
 * 段落那条漏了 AT / WEB，还留了一个 bilibili-API 快照里不存在的 `RICH_TEXT_NODE_TYPE_RICH`：
 * 于是「@某某」在图文摘要里能点、在正文段落里点不动，链接更是整段退化成纯文本。
 * 现在两条路径共用这里的解析结果，字段口径按 `bilibili-API/docs/opus/rich_text_nodes.md`。
 */
class OpusRichTextParserTest {

    private fun node(json: String) = JSONObject(json)

    @Test
    fun `AT 节点解析出用户 mid`() {
        val segment = OpusRichTextParser.parseOne(
            node("""{"orig_text":"@wuziqian211 ","rid":"425503913","text":"@wuziqian211 ","type":"RICH_TEXT_NODE_TYPE_AT"}""")
        )
        assertEquals(RichTextSegment.KIND_AT, segment.kind)
        assertEquals("@wuziqian211 ", segment.text)
        assertEquals(425503913L, segment.atMid)
    }

    @Test
    fun `话题节点带跳转地址`() {
        val segment = OpusRichTextParser.parseOne(
            node("""{"orig_text":"#洛天依#","text":"#洛天依#","jump_url":"//search.bilibili.com/all?keyword=%E6%B4%9B","type":"RICH_TEXT_NODE_TYPE_TOPIC"}""")
        )
        assertEquals(RichTextSegment.KIND_TOPIC, segment.kind)
        assertEquals("#洛天依#", segment.text)
        assertEquals("//search.bilibili.com/all?keyword=%E6%B4%9B", segment.jumpUrl)
    }

    @Test
    fun `网页链接用 jump_url 而不是被缩短的显示文本`() {
        // 快照里的真实例子：text 是 "av1"，jump_url 才是完整地址
        val segment = OpusRichTextParser.parseOne(
            node("""{"jump_url":"//www.bilibili.com/video/av1","orig_text":"//www.bilibili.com/video/av1","style":null,"text":"av1","type":"RICH_TEXT_NODE_TYPE_WEB"}""")
        )
        assertEquals(RichTextSegment.KIND_WEB, segment.kind)
        assertEquals("av1", segment.text)
        assertEquals("//www.bilibili.com/video/av1", segment.jumpUrl)
    }

    @Test
    fun `表情节点解析出图片地址与相对大小`() {
        val segment = OpusRichTextParser.parseOne(
            node(
                """{"emoji":{"icon_url":"https://i0.hdslb.com/bfs/emote/x.png","size":2,"text":"[doge]"},
                   "orig_text":"[doge]","text":"[doge]","type":"RICH_TEXT_NODE_TYPE_EMOJI"}"""
            )
        )
        assertEquals(RichTextSegment.KIND_EMOJI, segment.kind)
        assertEquals("https://i0.hdslb.com/bfs/emote/x.png", segment.emojiUrl)
        assertEquals(2, segment.emojiSize)
    }

    @Test
    fun `文字节点与未知类型都退化成纯文本`() {
        val text = OpusRichTextParser.parseOne(node("""{"orig_text":"一句话","text":"一句话","type":"RICH_TEXT_NODE_TYPE_TEXT"}"""))
        assertEquals(RichTextSegment.KIND_TEXT, text.kind)
        assertEquals("一句话", text.text)

        // 快照里不存在 RICH_TEXT_NODE_TYPE_RICH：旧实现的死分支，现在按文字处理，内容不丢
        val unknown = OpusRichTextParser.parseOne(node("""{"text":"内容","type":"RICH_TEXT_NODE_TYPE_RICH"}"""))
        assertEquals(RichTextSegment.KIND_TEXT, unknown.kind)
        assertEquals("内容", unknown.text)
    }

    @Test
    fun `只有 orig_text 的节点也能取到显示文本`() {
        val segment = OpusRichTextParser.parseOne(node("""{"orig_text":"只有原始文本","type":"RICH_TEXT_NODE_TYPE_TEXT"}"""))
        assertEquals("只有原始文本", segment.text)
    }

    @Test
    fun `解析数组时跳过 null 元素与空文本节点`() {
        val nodes = JSONArray()
        nodes.put(node("""{"text":"第一段","type":"RICH_TEXT_NODE_TYPE_TEXT"}"""))
        nodes.put(JSONObject.NULL)
        nodes.put(node("""{"text":"","type":"RICH_TEXT_NODE_TYPE_TEXT"}"""))
        nodes.put(node("""{"text":"@某人 ","rid":1,"type":"RICH_TEXT_NODE_TYPE_AT"}"""))
        val segments = OpusRichTextParser.parse(nodes)
        assertEquals(2, segments.size)
        assertEquals("第一段", segments[0].text)
        assertEquals(RichTextSegment.KIND_AT, segments[1].kind)
    }

    @Test
    fun `空参数返回空结果而不是抛异常`() {
        assertTrue(OpusRichTextParser.parse(null).isEmpty())
        assertTrue(OpusRichTextParser.parse(JSONArray()).isEmpty())
        assertNull(OpusRichTextParser.parseOne(null))
    }
}
