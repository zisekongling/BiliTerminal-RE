package com.RobinNotBad.BiliClient.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 视频 AI 摘要结果映射的纯函数单测（反馈 #127）。
 *
 * 改动前 `getVideoConclusion` 把四种互不相同的情况压成同一个 `null`：HTTP/解析失败、
 * `code != 0`（风控 -352/-412、未登录 -101、无权限、接口未开放）、该视频本来就没有摘要、
 * summary 是空串。界面只能统一弹「获取视频摘要失败」，用户分不清是号被风控、这视频不支持
 * 还是网络问题。这里逐条钉住 [VideoInfoApi.parseConclusion] 的四种类别，别再退回去。
 */
class VideoInfoConclusionTest {

    private fun parse(json: String): VideoInfoApi.ConclusionResult =
        VideoInfoApi.parseConclusion(JSONObject(json))

    // ===== 成功：code=0 且真的拿到摘要 =====

    @Test
    fun ok_returnsSummaryText() {
        val result = parse(
            """{"code":0,"message":"0","ttl":1,"data":{"code":0,"model_result":{
                "result_type":1,"summary":"本期视频讲了三个要点","nid":"123","outline":[]}}}"""
        )

        assertEquals(VideoInfoApi.ConclusionKind.OK, result.kind)
        assertEquals(0, result.code)
        assertEquals("本期视频讲了三个要点", result.text)
        assertTrue(result.isSuccess())
    }

    @Test
    fun ok_resultTypeAboveOneStillOk() {
        val result = parse(
            """{"code":0,"data":{"code":0,"model_result":{"result_type":2,"summary":"摘要"}}}"""
        )

        assertEquals(VideoInfoApi.ConclusionKind.OK, result.kind)
        assertEquals("摘要", result.text)
    }

    @Test
    fun ok_keepsSummaryAsIs() {
        // trim 只用于判断空不空，回给界面的仍是服务端原文（多行摘要不能在这里被吃掉空格）
        val result = parse(
            """{"code":0,"data":{"code":0,"model_result":{"result_type":1,"summary":"  第一段\n第二段  "}}}"""
        )

        assertEquals(VideoInfoApi.ConclusionKind.OK, result.kind)
        assertEquals("  第一段\n第二段  ", result.text)
    }

    // ===== 该视频没有摘要：接口正常，但就是没有 =====

    @Test
    fun noSummary_resultTypeZero() {
        val result = parse(
            """{"code":0,"data":{"code":0,"model_result":{"result_type":0,"summary":""}}}"""
        )

        assertEquals(VideoInfoApi.ConclusionKind.NO_SUMMARY, result.kind)
        assertEquals(0, result.code)
        assertEquals("", result.text)
        assertFalse(result.isSuccess())
    }

    @Test
    fun noSummary_emptySummary() {
        val result = parse(
            """{"code":0,"data":{"code":0,"model_result":{"result_type":1,"summary":""}}}"""
        )

        assertEquals(VideoInfoApi.ConclusionKind.NO_SUMMARY, result.kind)
    }

    @Test
    fun noSummary_blankSummary() {
        val result = parse(
            """{"code":0,"data":{"code":0,"model_result":{"result_type":1,"summary":"  \n "}}}"""
        )

        assertEquals(VideoInfoApi.ConclusionKind.NO_SUMMARY, result.kind)
    }

    @Test
    fun noSummary_missingModelResult() {
        val result = parse("""{"code":0,"message":"0","data":{"code":0}}""")

        assertEquals(VideoInfoApi.ConclusionKind.NO_SUMMARY, result.kind)
    }

    @Test
    fun noSummary_modelResultIsNotObject() {
        // 字段类型不对也按「没有摘要」降级，不抛 JSONException（旧实现这里会整次崩掉）
        val result = parse("""{"code":0,"data":{"code":0,"model_result":"oops"}}""")

        assertEquals(VideoInfoApi.ConclusionKind.NO_SUMMARY, result.kind)
    }

    // ===== 接口拒绝：code 非 0，要保留原始码 =====

    @Test
    fun rejected_riskControl352() {
        val result = parse("""{"code":-352,"message":"风控校验失败","ttl":1}""")

        assertEquals(VideoInfoApi.ConclusionKind.REJECTED, result.kind)
        assertEquals(-352, result.code)
        assertEquals("风控校验失败", result.message)
        assertFalse(result.isSuccess())
    }

    @Test
    fun rejected_riskControl412() {
        val result = parse("""{"code":-412,"message":"请求被拦截"}""")

        assertEquals(VideoInfoApi.ConclusionKind.REJECTED, result.kind)
        assertEquals(-412, result.code)
    }

    @Test
    fun rejected_notLogin101() {
        val result = parse("""{"code":-101,"message":"账号未登录"}""")

        assertEquals(VideoInfoApi.ConclusionKind.REJECTED, result.kind)
        assertEquals(-101, result.code)
        assertEquals("账号未登录", result.message)
    }

    @Test
    fun rejected_dataCodeUsesDataMessage() {
        val result = parse(
            """{"code":0,"message":"0","data":{"code":-352,"message":"风控校验失败"}}"""
        )

        assertEquals(VideoInfoApi.ConclusionKind.REJECTED, result.kind)
        assertEquals(-352, result.code)
        assertEquals("风控校验失败", result.message)
    }

    @Test
    fun rejected_dataCodeFallsBackToTopMessage() {
        val result = parse("""{"code":0,"message":"啥都木有","data":{"code":-404}}""")

        assertEquals(VideoInfoApi.ConclusionKind.REJECTED, result.kind)
        assertEquals(-404, result.code)
        assertEquals("啥都木有", result.message)
    }

    @Test
    fun rejected_missingMessageBecomesEmpty() {
        // 服务端常常不给 message，界面要能拿到空串而不是 "null"
        val result = parse("""{"code":-352}""")

        assertEquals(VideoInfoApi.ConclusionKind.REJECTED, result.kind)
        assertEquals("", result.message)
    }

    // ===== 网络或解析失败：响应不是本接口的形状 =====

    @Test
    fun failed_nullResponse() {
        val result = VideoInfoApi.parseConclusion(null)

        assertEquals(VideoInfoApi.ConclusionKind.FAILED, result.kind)
        assertEquals(-1, result.code)
        assertEquals("", result.text)
    }

    @Test
    fun failed_emptyResponse() {
        val result = parse("{}")

        assertEquals(VideoInfoApi.ConclusionKind.FAILED, result.kind)
        assertEquals(-1, result.code)
    }

    @Test
    fun failed_nullCode() {
        val result = parse("""{"code":null,"message":"0"}""")

        assertEquals(VideoInfoApi.ConclusionKind.FAILED, result.kind)
    }

    @Test
    fun failed_codeZeroWithoutData() {
        val result = parse("""{"code":0,"message":"0"}""")

        assertEquals(VideoInfoApi.ConclusionKind.FAILED, result.kind)
        assertEquals(0, result.code)
    }

    @Test
    fun failed_dataIsString() {
        val result = parse("""{"code":0,"message":"0","data":"oops"}""")

        assertEquals(VideoInfoApi.ConclusionKind.FAILED, result.kind)
    }

    @Test
    fun failed_dataIsArray() {
        val result = parse("""{"code":0,"data":[1,2,3]}""")

        assertEquals(VideoInfoApi.ConclusionKind.FAILED, result.kind)
    }

    // ===== 类别之间不能互相顶替 =====

    @Test
    fun onlyOkCountsAsSuccess() {
        assertTrue(parse("""{"code":0,"data":{"code":0,"model_result":{"result_type":1,"summary":"x"}}}""").isSuccess())
        assertFalse(parse("""{"code":0,"data":{"code":0}}""").isSuccess())
        assertFalse(parse("""{"code":-352}""").isSuccess())
        assertFalse(VideoInfoApi.parseConclusion(null).isSuccess())
    }
}
