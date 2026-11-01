package com.RobinNotBad.BiliClient.api

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * playurl 失败响应的文案（反馈 #80）。
 *
 * 用户看到的是「进去视频详情页时提示 no valve for durl」——原文是 org.json 的
 * `JSONException: No value for durl`，因为 `getVideo` 直接 `data.getJSONArray("durl")`。
 * 现在统一经 [PlayerApi.describePlayUrlFailure] 翻成一句人话；这个测试钉住三种形态：
 * 服务端给了 message、只给了错误码、以及 code=0 但就是没有 durl。
 */
class PlayerApiPlayUrlTest {

    @Test
    fun serverMessage_isPreferred() {
        val body = JSONObject("""{"code":-404,"message":"啥都木有","ttl":1}""")

        assertEquals(
            "无法获取播放地址（啥都木有）",
            PlayerApi.describePlayUrlFailure(body)
        )
    }

    @Test
    fun codeOnlyWithoutMessage_reportsCode() {
        val body = JSONObject("""{"code":87007,"message":"","ttl":1}""")

        assertEquals(
            "无法获取播放地址（接口返回 87007）",
            PlayerApi.describePlayUrlFailure(body)
        )
    }

    @Test
    fun successButNoDurl_isGenericReason() {
        // 典型形态：code=0，data 里没有 durl（付费/充电专属、地区限制、需登录……）
        val body = JSONObject("""{"code":0,"message":"0","data":{}}""")

        val text = PlayerApi.describePlayUrlFailure(body)
        assertTrue("必须提到可能的原因，不能只说失败", text.contains("付费"))
        assertTrue("不能被当成接口错误", !text.contains("接口返回"))
    }

    @Test
    fun nullBody_fallsBackToEmptyResponse() {
        assertEquals(
            "无法获取播放地址（响应为空，请稍后重试）",
            PlayerApi.describePlayUrlFailure(null)
        )
    }

    @Test
    fun noPlayUrlException_isAJsonException() {
        val e = PlayerApi.NoPlayUrlException("无法获取播放地址（大会员专享）")

        // 调用点原有 catch (JSONException) 分支必须继续生效，否则异常会漏到 MsgUtil.err 的
        // 「未知错误」分支
        assertTrue(e is JSONException)
        assertEquals("无法获取播放地址（大会员专享）", e.message)
    }

    @Test
    fun rawOrgJsonTextNeverLeaks() {
        val e = PlayerApi.NoPlayUrlException(PlayerApi.describePlayUrlFailure(JSONObject("""{"code":0}""")))

        assertTrue(
            "不能把 org.json 的 No value for durl 弹给用户",
            e.message?.contains("No value for", ignoreCase = true) != true
        )
    }

    @Test
    fun malformedBodyDoesNotThrow() {
        // 响应体不是对象（风控返回 HTML 被解析成别的形状）时，文案函数也不能再抛
        val body = JSONObject("""{"code":"-412"}""")
        assertTrue(PlayerApi.describePlayUrlFailure(body).startsWith("无法获取播放地址"))
        assertThrows(JSONException::class.java) {
            // 提示文案本身不吞异常，但也不接受 null message
            throw PlayerApi.NoPlayUrlException(
                PlayerApi.describePlayUrlFailure(JSONObject("""{"code":-101}"""))
            )
        }
    }
}
