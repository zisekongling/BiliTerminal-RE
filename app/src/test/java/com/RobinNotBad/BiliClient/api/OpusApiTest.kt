package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.model.ArticleInfo
import com.RobinNotBad.BiliClient.model.Opus
import com.RobinNotBad.BiliClient.model.OpusParagraph
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class OpusApiTest {

    @Test
    fun analyzeCommentInfo_validBasic_fillsCommentFields() {
        val opus = Opus()
        val detail = JSONObject("""{"basic":{"comment_id_str":"51850291","comment_type":12}}""")

        OpusApi.analyzeCommentInfo(opus, detail, 1229683324833759235L)

        assertEquals("应解析出commentId", 51850291L, opus.commentId)
        assertEquals("应解析出commentType", 12, opus.commentType)
    }

    @Test
    fun analyzeCommentInfo_missingBasic_fallsBackToIdAnd17() {
        val opus = Opus()
        val detail = JSONObject("""{"modules":[]}""")

        OpusApi.analyzeCommentInfo(opus, detail, 123456789L)

        assertEquals("commentId应回退为id", 123456789L, opus.commentId)
        assertEquals("commentType应回退为17", 17, opus.commentType)
    }

    @Test
    fun analyzeCommentInfo_nonNumericCommentId_doesNotThrow() {
        val opus = Opus()
        val detail = JSONObject("""{"basic":{"comment_id_str":"abc","comment_type":12}}""")

        OpusApi.analyzeCommentInfo(opus, detail, 987654321L)

        assertEquals("非数字commentId应回退为id", 987654321L, opus.commentId)
        assertEquals("commentType保持解析值", 12, opus.commentType)
    }

    // ---------- 专栏正文兜底（反馈 #142） ----------

    @Test
    fun articleParagraphs_正文HTML转成段落() {
        val article = ArticleInfo().apply {
            id = 123L
            content = "<p>第一段</p><h2>小标题</h2><img src=\"//i0.hdslb.com/a.jpg\">"
            summary = "摘要不该出现"
        }

        val paragraphs = OpusApi.articleParagraphs(article)

        assertEquals("正文优先，不该混进摘要", 3, paragraphs.size)
        assertEquals("第一段", paragraphs[0].content)
        assertEquals(OpusParagraph.TYPE_HEADING, paragraphs[1].type)
        assertEquals(OpusParagraph.TYPE_PIC, paragraphs[2].type)
    }

    @Test
    fun articleParagraphs_正文为空时退回摘要() {
        val article = ArticleInfo().apply {
            id = 123L
            content = ""
            summary = "这篇专栏只剩摘要了"
        }

        val paragraphs = OpusApi.articleParagraphs(article)

        assertEquals("正文没有内容时至少让用户读到摘要", 1, paragraphs.size)
        assertEquals("这篇专栏只剩摘要了", paragraphs[0].content)
    }

    @Test
    fun articleParagraphs_正文和摘要都空时返回空列表() {
        val article = ArticleInfo().apply {
            id = 123L
            content = ""
            summary = ""
        }

        assertEquals("调用方靠空列表抛「这篇文章没有正文」", 0, OpusApi.articleParagraphs(article).size)
    }

    @Test
    fun articleParagraphs_正文只有认不出的标签时也退回摘要() {
        val article = ArticleInfo().apply {
            id = 123L
            content = "<div><span>   </span></div>"
            summary = "摘要兜底"
        }

        val paragraphs = OpusApi.articleParagraphs(article)

        assertEquals(1, paragraphs.size)
        assertEquals("摘要兜底", paragraphs[0].content)
    }

    @Test
    fun articleParagraphs_article为null时返回空列表() {
        assertEquals(0, OpusApi.articleParagraphs(null).size)
    }
}
