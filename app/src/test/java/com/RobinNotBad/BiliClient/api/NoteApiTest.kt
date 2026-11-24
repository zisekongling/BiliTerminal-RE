package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.model.NoteBlock
import com.RobinNotBad.BiliClient.model.NoteContentItem
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 笔记解析的纯函数单测（26.10.04 批次 6 的 C27）。
 *
 * 网络请求不在这里测：`getNoteIdsOfVideo` / `getNoteInfo` 需要真 Cookie，属于真机清单。
 *
 * 26.11.23 补公开笔记（`parseNoteSummaries` / `hasMore` / `normalizeImageUrl`）与
 * 详情页合并逻辑（`buildContent`）的用例；`parseBlocks` 的语义没动，既有用例原样保留。
 */
class NoteApiTest {

    @Test
    fun parseNoteIds_readsStringIdsAndSkipsBlanks() {
        val data = JSONObject().put("noteIds", JSONArray().put("3809605586518023").put("").put("2"))
        assertEquals(listOf("3809605586518023", "2"), NoteApi.parseNoteIds(data))
    }

    @Test
    fun parseNoteIds_toleratesMissingFieldAndNullData() {
        assertTrue(NoteApi.parseNoteIds(null).isEmpty())
        assertTrue(NoteApi.parseNoteIds(JSONObject()).isEmpty())
    }

    @Test
    fun pickNoteId_prefersTheStringForm() {
        // 快照示例：note_id 与 note_id_str 最后几位不一样，17 位 id 走 JSON number 已经丢精度
        val json = JSONObject()
            .put("note_id", 24508729145690110L)
            .put("note_id_str", "24508729145690112")
        assertEquals("24508729145690112", NoteApi.pickNoteId(json))
    }

    @Test
    fun pickNoteId_fallsBackToTheNumberField() {
        assertEquals("123", NoteApi.pickNoteId(JSONObject().put("note_id", 123)))
        assertEquals("", NoteApi.pickNoteId(null))
        assertEquals("", NoteApi.pickNoteId(JSONObject()))
    }

    @Test
    fun parseBlocks_readsTextAndAttributes() {
        val content = "[" +
                "{\"attributes\":{\"size\":\"24px\",\"bold\":true},\"insert\":\"关掉\"}," +
                "{\"insert\":\"，\"}," +
                "{\"attributes\":{\"background\":\"#fff359\"},\"insert\":\"一定要\"}," +
                "{\"insert\":\"\\n再不关掉那些\"}," +
                "{\"attributes\":{\"underline\":true,\"strike\":true,\"color\":\"#ff6699\",\"list\":\"bullet\"},\"insert\":\"网络游戏\"}" +
                "]"
        val blocks = NoteApi.parseBlocks(content)
        assertEquals(5, blocks.size)

        assertEquals("关掉", blocks[0].text)
        assertTrue(blocks[0].bold)
        assertEquals(NoteBlock.TYPE_TEXT, blocks[0].type)

        assertEquals("，", blocks[1].text)
        assertFalse(blocks[1].bold)
        assertEquals("", blocks[1].background)

        assertEquals("#fff359", blocks[2].background)
        assertEquals("\n再不关掉那些", blocks[3].text)
        assertTrue(blocks[4].underline)
        assertTrue(blocks[4].strike)
        assertEquals("#ff6699", blocks[4].color)
        assertEquals("bullet", blocks[4].list)
    }

    @Test
    fun parseBlocks_readsTagAndImageInserts() {
        val content = "[" +
                "{\"insert\":{\"tag\":{\"cid\":11,\"status\":0,\"index\":2,\"seconds\":125,\"cidCount\":3,\"key\":\"k\",\"title\":\"output\"}}}," +
                "{\"insert\":{\"imageUpload\":{\"url\":\"https://i0.hdslb.com/a.jpg\",\"status\":\"done\",\"width\":600}}}," +
                "{\"insert\":{\"unknown\":{}}}" +
                "]"
        val blocks = NoteApi.parseBlocks(content)
        assertEquals(2, blocks.size)

        assertEquals(NoteBlock.TYPE_TAG, blocks[0].type)
        assertEquals(11L, blocks[0].tagCid)
        assertEquals(2, blocks[0].tagIndex)
        assertEquals(125L, blocks[0].tagSeconds)

        assertEquals(NoteBlock.TYPE_IMAGE, blocks[1].type)
        assertEquals("https://i0.hdslb.com/a.jpg", blocks[1].imageUrl)
        // 服务端给的是「图片宽度 - 2」，这里原样保留
        assertEquals(600, blocks[1].imageWidth)
    }

    @Test
    fun parseBlocks_keepsEmptyTextOnlyWhenItIsAListItem() {
        val content = "[{\"insert\":\"\"},{\"insert\":\"\",\"attributes\":{\"list\":\"bullet\"}}]"
        val blocks = NoteApi.parseBlocks(content)
        assertEquals(1, blocks.size)
        assertEquals("bullet", blocks[0].list)
    }

    @Test
    fun parseBlocks_toleratesBrokenInput() {
        assertTrue(NoteApi.parseBlocks(null).isEmpty())
        assertTrue(NoteApi.parseBlocks("").isEmpty())
        assertTrue(NoteApi.parseBlocks("不是 json").isEmpty())
        // 根是对象不是数组，也当空
        assertTrue(NoteApi.parseBlocks("{\"insert\":\"x\"}").isEmpty())
    }

    @Test
    fun parseNoteDetail_readsArcAndContent() {
        val data = JSONObject()
            .put("title", "标题")
            .put("summary", "预览")
            .put("content", "[{\"insert\":\"正文\"}]")
            .put(
                "arc",
                JSONObject()
                    .put("oid", 970322090L)
                    .put("bvid", "BV1xx")
                    .put("title", "视频标题")
                    .put("desc", "视频简介")
            )
        val note = NoteApi.parseNoteDetail(data)
        assertEquals("标题", note.title)
        assertEquals("预览", note.summary)
        assertEquals(970322090L, note.aid)
        assertEquals("BV1xx", note.bvid)
        assertEquals("视频标题", note.videoTitle)
        assertEquals("视频简介", note.videoDesc)
        assertEquals(1, note.blocks.size)
        assertEquals("正文", note.blocks[0].text)
    }

    @Test
    fun parseNoteDetail_toleratesNull() {
        val note = NoteApi.parseNoteDetail(null)
        assertEquals("", note.title)
        assertEquals(0L, note.aid)
        assertTrue(note.blocks.isEmpty())
    }

    @Test
    fun formatTagSeconds_padsMinutesAndHours() {
        assertEquals("00:00", NoteApi.formatTagSeconds(0))
        assertEquals("00:05", NoteApi.formatTagSeconds(5))
        assertEquals("01:05", NoteApi.formatTagSeconds(65))
        assertEquals("59:59", NoteApi.formatTagSeconds(3599))
        assertEquals("1:00:00", NoteApi.formatTagSeconds(3600))
        assertEquals("1:02:05", NoteApi.formatTagSeconds(3725))
        // 负数按 0 处理，别拼出 "-1:59"
        assertEquals("00:00", NoteApi.formatTagSeconds(-1))
    }

    @Test
    fun noteErrorMsg_mapsKnownCodes() {
        assertEquals("", NoteApi.noteErrorMsg(0))
        assertTrue(NoteApi.noteErrorMsg(-101).contains("登录"))
        assertTrue(NoteApi.noteErrorMsg(79502).contains("笔记"))
        assertTrue(NoteApi.noteErrorMsg(79503).contains("正文"))
        assertTrue(NoteApi.noteErrorMsg(12345).contains("12345"))
    }

    // ------------------------------------------------------------ 失败提示分级

    @Test
    fun failureText_givesNetworkAdviceForIoExceptions() {
        // 断网时异常描述是 java.net.UnknownHostException: api.bilibili.com 这种，
        // 手表上没法看，也不能显示给用户
        val e = java.net.UnknownHostException("api.bilibili.com")
        assertEquals("网络异常，请检查网络后重试", NoteApi.failureText(e))
        // SocketTimeoutException 也继承 IOException，同样走网络文案
        assertEquals(
            "网络异常，请检查网络后重试",
            NoteApi.failureText(java.net.SocketTimeoutException("timeout"))
        )
    }

    @Test
    fun failureText_passesThroughServerMessage() {
        // list/archive 与 info 在 code != 0 时抛的 JSONException 带的是 errorText 的产物，
        // 即「服务端 message 优先」。79502/79503 的真实文案就是这么上来的
        assertEquals("没有找到这篇笔记", NoteApi.failureText(JSONException("没有找到这篇笔记")))
        assertEquals("这篇笔记还没有正文", NoteApi.failureText(JSONException("这篇笔记还没有正文")))
        assertEquals("还没有登录喵~", NoteApi.failureText(JSONException("还没有登录喵~")))
        // message 里带异常类名前缀时也要剥掉，界面不该出现 org.json.JSONException
        assertEquals("服务端原话", NoteApi.failureText(JSONException("org.json.JSONException:服务端原话")))
    }

    @Test
    fun failureText_hidesGenericFallback() {
        // noteErrorMsg 的兜底文案是「获取笔记失败（错误码 x）」，再显示一遍没意义
        assertEquals("获取笔记失败", NoteApi.failureText(JSONException("获取笔记失败（错误码 -999）")))
        // 没有 message 的 JSONException
        assertEquals("获取笔记失败", NoteApi.failureText(JSONException(null as String?)))
        // 传入 null 异常也要给文案，不能让页面空白
        assertEquals("获取笔记失败", NoteApi.failureText(null as Throwable?))
    }

    @Test
    fun jsonExceptionWithoutMessage_keepsNullMessage() {
        // 这条用例是上一轮的失败根因所在，保留为回归防护：
        // NoteApi.failureText 是 Java 方法，返回类型对 Kotlin 是平台类型 String!。
        // 上一轮这里写的是 `JSONException(null as String)` —— `as String`（非空）会把 null 直接
        // 断言成非空类型，构造器调用当场抛
        // `NullPointerException: null cannot be cast to non-null type kotlin.String`，
        // 异常在进入被测方法之前就抛了。该报错与 failureText 的实现无关（它本身有 null 兜底）。
        // 正确写法是 `as String?`，让 null 真正传进去。
        val e = JSONException(null as String?)
        assertNull(e.message)
        assertEquals("获取笔记失败", NoteApi.failureText(e))
    }

    @Test
    fun failureText_hidesInternalDetailForUnknownTypes() {
        // 其余异常（解析异常、类型转换等）不外泄类名与堆栈描述
        assertEquals("获取笔记失败", NoteApi.failureText(IllegalStateException("内部细节")))
        assertEquals("获取笔记失败", NoteApi.failureText(RuntimeException("boom")))
    }

    // ------------------------------------------------------------ 26.11.23 公开笔记与合并渲染

    @Test
    fun normalizeImageUrl_addsHttpsToProtocolRelativeUrls() {
        // 正文里的图片地址是 //i0.hdslb.com/...，没有 scheme，Glide 会直接失败
        assertEquals(
            "https://i0.hdslb.com/bfs/note/a.jpg",
            NoteApi.normalizeImageUrl("//i0.hdslb.com/bfs/note/a.jpg")
        )
        // 已经完整的地址原样返回，两边空白去掉
        assertEquals("https://i0.hdslb.com/a.jpg", NoteApi.normalizeImageUrl("  https://i0.hdslb.com/a.jpg "))
        assertEquals("", NoteApi.normalizeImageUrl(null))
        assertEquals("", NoteApi.normalizeImageUrl("   "))
    }

    @Test
    fun parseBlocks_completesProtocolRelativeImageUrls() {
        val content = "[{\"insert\":{\"imageUpload\":{\"url\":\"//i0.hdslb.com/bfs/note/x.jpg\",\"width\":388}}}]"
        val blocks = NoteApi.parseBlocks(content)
        assertEquals(1, blocks.size)
        assertEquals("https://i0.hdslb.com/bfs/note/x.jpg", blocks[0].imageUrl)
    }

    @Test
    fun parseNoteSummaries_readsListAndSkipsRowsWithoutCvid() {
        val data = JSONObject().put(
            "list",
            JSONArray()
                .put(
                    JSONObject()
                        .put("cvid", 15080841L)
                        .put("title", "标题")
                        .put("summary", "预览")
                        .put("pubtime", 1700000000L)
                        .put("likes", 3)
                        .put("author", JSONObject().put("name", "某人").put("face", "https://x/a.jpg"))
                )
                // 没有 cvid 就没法打开详情，这种行直接丢掉
                .put(JSONObject().put("title", "没有 cvid 的行"))
        )
        val items = NoteApi.parseNoteSummaries(data)
        assertEquals(1, items.size)
        assertEquals(15080841L, items[0].cvid)
        assertEquals("标题", items[0].title)
        assertEquals("预览", items[0].summary)
        assertEquals(1700000000L, items[0].pubtime)
        assertEquals(3, items[0].likes)
        assertEquals("某人", items[0].authorName)
        assertEquals("https://x/a.jpg", items[0].authorFace)
        assertFalse(items[0].own)
    }

    @Test
    fun parseNoteSummaries_parsesFormattedPubtime() {
        // 实测（cvid 43796340）：接口给的是 "2025-11-20 10:27" 这种字符串，不是时间戳。
        // 换算按北京时间，2025-11-20 10:27 GMT+8 == 1763605620 秒。
        val data = JSONObject().put(
            "list",
            JSONArray().put(JSONObject().put("cvid", 43796340L).put("pubtime", "2025-11-20 10:27"))
        )
        assertEquals(1763605620L, NoteApi.parseNoteSummaries(data)[0].pubtime)
    }

    @Test
    fun parseNoteSummaries_keepsNumericPubtime() {
        val data = JSONObject().put(
            "list",
            JSONArray().put(JSONObject().put("cvid", 1L).put("pubtime", "1700000000"))
        )
        assertEquals(1700000000L, NoteApi.parseNoteSummaries(data)[0].pubtime)
    }

    @Test
    fun parseNoteSummaries_unparsablePubtimeBecomesZero() {
        val data = JSONObject().put(
            "list",
            JSONArray().put(JSONObject().put("cvid", 1L).put("pubtime", "昨天"))
        )
        // 解析不了就 0：调用方据此整段隐去时间，而不是显示 1970 年
        assertEquals(0L, NoteApi.parseNoteSummaries(data)[0].pubtime)
    }

    @Test
    fun parseNoteSummaries_toleratesMissingFields() {
        assertTrue(NoteApi.parseNoteSummaries(null).isEmpty())
        assertTrue(NoteApi.parseNoteSummaries(JSONObject()).isEmpty())
        // 「还没有人发布笔记」的真实响应里 list 就是 null
        assertTrue(NoteApi.parseNoteSummaries(JSONObject().put("list", JSONObject.NULL)).isEmpty())
    }

    @Test
    fun hasMore_usesTotalSizeAndNum() {
        assertTrue(NoteApi.hasMore(JSONObject().put("total", 25).put("size", 10).put("num", 1)))
        assertFalse(NoteApi.hasMore(JSONObject().put("total", 25).put("size", 10).put("num", 3)))
        // 字段没给全时宁可少一页，也不要无限往下翻
        assertFalse(NoteApi.hasMore(null))
        assertFalse(NoteApi.hasMore(JSONObject()))
        assertFalse(NoteApi.hasMore(JSONObject().put("total", 0).put("size", 10).put("num", 1)))
    }

    @Test
    fun parseNoteDetail_readsPublicFields() {
        val data = JSONObject()
            .put("cvid", 15080841L)
            .put("pubtime", 1700000000L)
            .put("likes", 12)
            .put("author", JSONObject().put("name", "作者").put("face", "https://x/f.jpg"))
        val note = NoteApi.parseNoteDetail(data)
        assertEquals(15080841L, note.cvid)
        assertEquals(1700000000L, note.pubtime)
        assertEquals(12, note.likes)
        assertEquals("作者", note.authorName)
        assertEquals("https://x/f.jpg", note.authorFace)
    }

    @Test
    fun buildContent_mergesAdjacentTextAndKeepsStyles() {
        // Quill 会把「关掉」和「，」拆成两个元素，合并后应该是一段
        val items = NoteApi.buildContent(
            listOf(
                textBlock("关掉", bold = true),
                textBlock("，"),
                textBlock("\n"),
                textBlock("第二段")
            )
        )
        assertEquals(2, items.size)
        assertEquals("关掉，", items[0].text)
        assertEquals(NoteContentItem.TYPE_PARAGRAPH, items[0].type)
        // 样式段只覆盖「关掉」两个字，下标相对段落文本
        assertEquals(1, items[0].runs.size)
        assertEquals(0, items[0].runs[0].start)
        assertEquals(2, items[0].runs[0].end)
        assertTrue(items[0].runs[0].bold)
        // 换行之后是新段落，且不带上一段的样式
        assertEquals("第二段", items[1].text)
        assertTrue(items[1].runs.isEmpty())
    }

    @Test
    fun buildContent_numbersOrderedListItems() {
        val items = NoteApi.buildContent(
            listOf(
                textBlock("第一", list = "ordered"),
                textBlock("\n第二", list = "ordered"),
                textBlock("\n第三", list = "ordered")
            )
        )
        assertEquals(listOf("1. 第一", "2. 第二", "3. 第三"), items.map { it.text })
    }

    @Test
    fun buildContent_prefixesBulletItems() {
        val items = NoteApi.buildContent(
            listOf(
                textBlock("项一", list = "bullet"),
                textBlock("\n项二", list = "bullet")
            )
        )
        assertEquals(listOf("• 项一", "• 项二"), items.map { it.text })
    }

    @Test
    fun buildContent_resetsOrderedNumberingAfterPlainParagraph() {
        val items = NoteApi.buildContent(
            listOf(
                textBlock("一", list = "ordered"),
                textBlock("\n普通段"),
                textBlock("\n二", list = "ordered")
            )
        )
        assertEquals(listOf("1. 一", "普通段", "1. 二"), items.map { it.text })
    }

    @Test
    fun buildContent_splitsImagesAndTagsIntoTheirOwnRows() {
        val image = NoteBlock()
        image.type = NoteBlock.TYPE_IMAGE
        image.imageUrl = "//i0.hdslb.com/a.jpg"
        image.imageWidth = 388

        val tag = NoteBlock()
        tag.type = NoteBlock.TYPE_TAG
        tag.tagCid = 497532206L
        tag.tagIndex = 1
        tag.tagSeconds = 80L
        tag.tagTitle = "标题"

        val items = NoteApi.buildContent(
            listOf(textBlock("前"), image, textBlock("后"), tag, textBlock("尾"))
        )
        assertEquals(5, items.size)
        assertEquals("前", items[0].text)
        assertEquals(NoteContentItem.TYPE_IMAGE, items[1].type)
        assertEquals("https://i0.hdslb.com/a.jpg", items[1].imageUrl)
        assertEquals(388, items[1].imageWidth)
        assertEquals("后", items[2].text)
        assertEquals(NoteContentItem.TYPE_TAG, items[3].type)
        assertEquals(497532206L, items[3].tagCid)
        assertEquals(1, items[3].tagIndex)
        assertEquals(80L, items[3].tagSeconds)
        assertEquals("尾", items[4].text)
    }

    @Test
    fun buildContent_skipsImagesWithoutUrl() {
        val image = NoteBlock()
        image.type = NoteBlock.TYPE_IMAGE
        image.imageUrl = "  "
        assertTrue(NoteApi.buildContent(listOf(image)).isEmpty())
        assertTrue(NoteApi.buildContent(null).isEmpty())
        assertTrue(NoteApi.buildContent(emptyList()).isEmpty())
    }

    /** 造一个文本块：`{"insert": text}` 加可选样式，等价于 parseBlocks 的产物。 */
    private fun textBlock(text: String, bold: Boolean = false, list: String = ""): NoteBlock {
        val block = NoteBlock()
        block.text = text
        block.bold = bold
        block.list = list
        return block
    }
}

