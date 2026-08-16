package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.model.Opus
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
}
