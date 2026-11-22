package com.RobinNotBad.BiliClient.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/**
 * APP 端短信登录的纯逻辑（反馈：把验证码登录换成官方客户端那条链路）。
 *
 * 端点与参数名来自仓库内的公开快照 `bilibili-API/docs/login/login_action/SMS.md`，
 * 这里把「参数名 / 固定串 / buvid 格式 / 回退判据」钉死——这类东西写错**不会报错**，
 * 只会让服务端回一句笼统的「请求错误」，用户看到的是「收不到验证码」。
 */
class LoginApiSmsAppTest {

    /** `FormData` 底层是 HashMap，顺序不确定，只能解成 map 比对（见 docs/agents/pitfalls.md）。 */
    private fun decode(form: Any): Map<String, String> =
        form.toString().split("&").filter { it.isNotEmpty() }.associate {
            val i = it.indexOf('=')
            URLDecoder.decode(it.substring(0, i), "UTF-8") to
                URLDecoder.decode(it.substring(i + 1), "UTF-8")
        }

    private val sendForm by lazy {
        decode(
            LoginApi.buildSmsSendAppForm(
                "13888888888",
                "abc123",
                "tok",
                "chal",
                "val",
                null,
                "XY0123456789abcdef0123456789abcdef012"
            )
        )
    }

    // ---- login_session_id ----

    @Test
    fun newLoginSessionId_isDashlessUuid() {
        val id = LoginApi.newLoginSessionId()
        assertEquals("必须是 32 位（uuid 去掉横线）", 32, id.length)
        assertFalse("不能带横线：$id", id.contains("-"))
        assertTrue("必须是十六进制：$id", id.all { it in "0123456789abcdef" })
        assertNotEquals("每次都要重新生成", id, LoginApi.newLoginSessionId())
    }

    // ---- buvid ----

    @Test
    fun genBuvid_hasDocumentedShape() {
        val buvid = LoginApi.genBuvid()
        assertTrue("必须以 XY 开头：$buvid", buvid.startsWith("XY"))
        // XY(2) + 三位取样 + 32 位 md5
        assertEquals("固定 37 位：$buvid", 37, buvid.length)
        assertTrue("XY 之后必须全是十六进制：$buvid", buvid.substring(2).all { it in "0123456789abcdef" })
        // 第 6 位起是整个 md5，前 3 位是它的第 3/13/23 个字符
        val md5 = buvid.substring(5)
        assertEquals("md5 段应正好 32 位", 32, md5.length)
        assertEquals("取样位应与 md5 对应位一致", "" + md5[2] + md5[12] + md5[22], buvid.substring(2, 5))
    }

    @Test
    fun genBuvid_isRandomPerCall() {
        assertNotEquals("每次都要换一个，否则同机多账号会串", LoginApi.genBuvid(), LoginApi.genBuvid())
    }

    // ---- 极验 seccode 归一 ----

    @Test
    fun normalizeSeccode_appendsJordanSuffix() {
        assertEquals("val|jordan", LoginApi.normalizeSeccode("val", "val"))
        assertEquals("val|jordan", LoginApi.normalizeSeccode("val", null))
        assertEquals("val|jordan", LoginApi.normalizeSeccode("val", ""))
    }

    @Test
    fun normalizeSeccode_keepsExistingSuffix() {
        assertEquals("x|jordan", LoginApi.normalizeSeccode("val", "x|jordan"))
    }

    // ---- 发送短信的请求体 ----

    @Test
    fun buildSmsSendAppForm_usesDocumentedKeys() {
        assertEquals("86", sendForm["cid"])
        assertEquals("13888888888", sendForm["tel"])
        assertEquals("abc123", sendForm["login_session_id"])
        assertEquals("APP 端叫 recaptcha_token，不是 web 的 token", "tok", sendForm["recaptcha_token"])
        assertEquals("chal", sendForm["gee_challenge"])
        assertEquals("val", sendForm["gee_validate"])
        assertEquals("val|jordan", sendForm["gee_seccode"])
        assertEquals("bili", sendForm["channel"])
    }

    @Test
    fun buildSmsSendAppForm_buvidAndLocalIdAreTheSame() {
        assertEquals(sendForm["buvid"], sendForm["local_id"])
        assertEquals("XY0123456789abcdef0123456789abcdef012", sendForm["buvid"])
    }

    @Test
    fun buildSmsSendAppForm_carriesStatisticsVerbatim() {
        assertEquals(LoginApi.SMS_APP_STATISTICS, sendForm["statistics"])
        assertTrue(sendForm["statistics"]!!.contains("\"appId\":1"))
    }

    @Test
    fun buildSmsSendAppForm_hasNoWebOnlyKeys() {
        // web 端那套参数（source / token / challenge / validate / seccode）在 APP 端不该出现
        for (key in listOf("source", "token", "challenge", "validate", "seccode")) {
            assertFalse("APP 端请求体不该带 $key", sendForm.containsKey(key))
        }
    }

    // ---- 登录的请求体 ----

    @Test
    fun buildSmsLoginAppForm_usesSessionIdAndCaptchaKey() {
        val form = decode(LoginApi.buildSmsLoginAppForm("13888888888", "abc123", "123456", "ck"))
        assertEquals("86", form["cid"])
        assertEquals("13888888888", form["tel"])
        assertEquals("abc123", form["login_session_id"])
        assertEquals("123456", form["code"])
        assertEquals("ck", form["captcha_key"])
        assertFalse("APP 端登录不带 web 的 source / keep", form.containsKey("source"))
        assertFalse(form.containsKey("keep"))
    }

    // ---- 回退判据 ----

    @Test
    fun smsShouldFallbackToWeb_onlyWhenNoSmsWasSent() {
        // 成功、已发送、次数上限、永久封禁、未登录：都不能重发
        assertFalse(LoginApi.smsShouldFallbackToWeb(0))
        assertFalse("1003 已发送，回退就会重复发一条", LoginApi.smsShouldFallbackToWeb(1003))
        assertFalse(LoginApi.smsShouldFallbackToWeb(86203))
        assertFalse(LoginApi.smsShouldFallbackToWeb(1025))
        assertFalse(LoginApi.smsShouldFallbackToWeb(-101))
    }

    @Test
    fun smsShouldFallbackToWeb_onParameterStyleFailures() {
        assertTrue(LoginApi.smsShouldFallbackToWeb(-400))
        assertTrue(LoginApi.smsShouldFallbackToWeb(2400))
        assertTrue(LoginApi.smsShouldFallbackToWeb(2406))
    }
}
