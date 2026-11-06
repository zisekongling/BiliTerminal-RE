package com.RobinNotBad.BiliClient.player

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 取流回退判据的单测。
 *
 * 这些判据决定「WBI 失败后要不要再打一次旧接口」「番剧 v2 的播放数据在哪」。
 * 写反了要么在 WBI 风控时白丢一次可播机会，要么把 v2 的 `result.video_info`
 * 当成播放数据去读 durl 而永远取不到地址。真机复现风控成本高，这里先钉死纯逻辑。
 */
class PlayUrlFallbackPolicyTest {

    @Test
    fun `视频主端点走WBI回退端点走旧接口`() {
        assertEquals(
            "https://api.bilibili.com/x/player/wbi/playurl?avid=1&cid=2",
            PlayUrlFallbackPolicy.videoWbiUrl("?avid=1&cid=2")
        )
        assertEquals(
            "https://api.bilibili.com/x/player/playurl?avid=1&cid=2",
            PlayUrlFallbackPolicy.videoLegacyUrl("?avid=1&cid=2")
        )
    }

    @Test
    fun `番剧主端点走v2回退端点走v1`() {
        assertEquals(
            "https://api.bilibili.com/pgc/player/web/v2/playurl?aid=1",
            PlayUrlFallbackPolicy.bangumiV2Url("?aid=1")
        )
        assertEquals(
            "https://api.bilibili.com/pgc/player/web/playurl?aid=1",
            PlayUrlFallbackPolicy.bangumiV1Url("?aid=1")
        )
    }

    @Test
    fun `响应为空按接口错误处理`() {
        assertEquals(
            PlayUrlFallbackPolicy.Failure.API_ERROR,
            PlayUrlFallbackPolicy.classifyVideoBody(null, requireDash = true)
        )
        assertEquals(
            PlayUrlFallbackPolicy.Failure.API_ERROR,
            PlayUrlFallbackPolicy.classifyBangumiBody(null)
        )
    }

    @Test
    fun `非零code一律视为接口错误`() {
        val body = JSONObject()
            .put("code", -352)
            .put("message", "风控校验失败")
        assertEquals(
            PlayUrlFallbackPolicy.Failure.API_ERROR,
            PlayUrlFallbackPolicy.classifyVideoBody(body, requireDash = true)
        )
        assertEquals(
            PlayUrlFallbackPolicy.Failure.API_ERROR,
            PlayUrlFallbackPolicy.classifyBangumiBody(body)
        )
    }

    @Test
    fun `要DASH时没有dash属于空载荷`() {
        val body = JSONObject().put("code", 0).put("data", JSONObject().put("durl", emptyArray<Any>()))
        assertEquals(
            PlayUrlFallbackPolicy.Failure.NO_DASH,
            PlayUrlFallbackPolicy.classifyVideoBody(body, requireDash = true)
        )
        assertEquals(
            PlayUrlFallbackPolicy.Failure.NO_DASH,
            PlayUrlFallbackPolicy.classifyVideoBody(JSONObject().put("code", 0), requireDash = true)
        )
    }

    @Test
    fun `有dash时可用`() {
        val data = JSONObject().put("dash", JSONObject().put("duration", 100))
        val body = JSONObject().put("code", 0).put("data", data)
        assertNull(PlayUrlFallbackPolicy.classifyVideoBody(body, requireDash = true))
        assertEquals(data, PlayUrlFallbackPolicy.videoData(body))
    }

    @Test
    fun `要durl时durl为空属于空载荷`() {
        val emptyDurl = JSONObject().put("code", 0).put("data", JSONObject().put("durl", emptyArray<Any>()))
        assertEquals(
            PlayUrlFallbackPolicy.Failure.NO_DURL,
            PlayUrlFallbackPolicy.classifyVideoBody(emptyDurl, requireDash = false)
        )
        val nullData = JSONObject().put("code", 0)
        assertEquals(
            PlayUrlFallbackPolicy.Failure.NO_DURL,
            PlayUrlFallbackPolicy.classifyVideoBody(nullData, requireDash = false)
        )
    }

    @Test
    fun `要durl时有内容即可用`() {
        val durl = listOf(JSONObject().put("url", "https://v.example/x.mp4"))
        val data = JSONObject().put("durl", org.json.JSONArray(durl))
        val body = JSONObject().put("code", 0).put("data", data)
        assertNull(PlayUrlFallbackPolicy.classifyVideoBody(body, requireDash = false))
    }

    @Test
    fun `番剧v2的播放数据在result_video_info里`() {
        val videoInfo = JSONObject()
            .put("durl", org.json.JSONArray(listOf(JSONObject().put("url", "https://v/x.mp4"))))
        val body = JSONObject()
            .put("code", 0)
            .put(
                "result",
                JSONObject()
                    .put("video_info", videoInfo)
                    .put("durl", org.json.JSONArray())  // v2 外层 durl 为空，不能误读
            )
        assertNull(PlayUrlFallbackPolicy.classifyBangumiBody(body))
        assertEquals(videoInfo, PlayUrlFallbackPolicy.bangumiPayload(body))
    }

    @Test
    fun `番剧v1的播放数据在result本身`() {
        val result = JSONObject()
            .put("durl", org.json.JSONArray(listOf(JSONObject().put("url", "https://v/x.mp4"))))
        val body = JSONObject().put("code", 0).put("result", result)
        assertEquals(result, PlayUrlFallbackPolicy.bangumiPayload(body))
        assertNull(PlayUrlFallbackPolicy.classifyBangumiBody(body))
    }

    @Test
    fun `番剧没有result或durl为空都不可用`() {
        assertEquals(
            PlayUrlFallbackPolicy.Failure.NO_DURL,
            PlayUrlFallbackPolicy.classifyBangumiBody(JSONObject().put("code", 0))
        )
        assertEquals(
            PlayUrlFallbackPolicy.Failure.NO_DURL,
            PlayUrlFallbackPolicy.classifyBangumiBody(
                JSONObject().put("code", 0).put("result", JSONObject().put("video_info", JSONObject()))
            )
        )
        assertNull(PlayUrlFallbackPolicy.bangumiPayload(JSONObject().put("code", 0)))
    }

    @Test
    fun `番剧v2外层result有durl但video_info缺失时仍可回退解析`() {
        // 防御：个别响应把 durl 放在 result 下的同时也带 video_info=null
        val result = JSONObject()
            .put("video_info", JSONObject.NULL)
            .put("durl", org.json.JSONArray(listOf(JSONObject().put("url", "https://v/x.mp4"))))
        val body = JSONObject().put("code", 0).put("result", result)
        assertEquals(result, PlayUrlFallbackPolicy.bangumiPayload(body))
        assertTrue(PlayUrlFallbackPolicy.classifyBangumiBody(body) == null)
    }
}
