package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialLoginParserTest {

    private val fullInfo = """
        {"cookies":"SESSDATA=abc; bili_jct=csrf; DedeUserID=12345; buvid3=x","refresh_token":"rt","access_key":"ak"}
    """.trimIndent()

    @Test
    fun parse_fullInfo_success() {
        val result = SpecialLoginParser.parse(fullInfo)
        assertTrue("完整登录信息应解析成功：$result", result is SpecialLoginParser.Result.Success)
        result as SpecialLoginParser.Result.Success
        assertEquals("SESSDATA=abc; bili_jct=csrf; DedeUserID=12345; buvid3=x", result.cookies)
        assertEquals("rt", result.refreshToken)
        assertEquals("ak", result.accessKey)
        assertEquals(12345L, result.mid)
    }

    @Test
    fun parse_withoutAccessKey_successAndAccessKeyNull() {
        val result = SpecialLoginParser.parse("{\"cookies\":\"DedeUserID=1\",\"refresh_token\":\"rt\"}")
        assertTrue(result is SpecialLoginParser.Result.Success)
        assertEquals(null, (result as SpecialLoginParser.Result.Success).accessKey)
    }

    @Test
    fun parse_surroundingWhitespace_trimmed() {
        val result = SpecialLoginParser.parse("  \n$fullInfo \n ")
        assertTrue("前后空白应被容忍：$result", result is SpecialLoginParser.Result.Success)
    }

    @Test
    fun parse_emptyOrNull_failure() {
        assertTrue(SpecialLoginParser.parse(null) is SpecialLoginParser.Result.Failure)
        assertTrue(SpecialLoginParser.parse("") is SpecialLoginParser.Result.Failure)
        assertTrue(SpecialLoginParser.parse("   ") is SpecialLoginParser.Result.Failure)
    }

    @Test
    fun parse_notJson_failure() {
        assertTrue(SpecialLoginParser.parse("随便一段文字") is SpecialLoginParser.Result.Failure)
        assertTrue(SpecialLoginParser.parse("[1,2,3]") is SpecialLoginParser.Result.Failure)
        assertTrue(SpecialLoginParser.parse("cookies=abc;DedeUserID=1") is SpecialLoginParser.Result.Failure)
    }

    @Test
    fun parse_missingCookiesField_failure() {
        val result = SpecialLoginParser.parse("{\"refresh_token\":\"rt\"}")
        assertTrue(result is SpecialLoginParser.Result.Failure)
    }

    @Test
    fun parse_emptyCookies_failure() {
        assertTrue(
            SpecialLoginParser.parse("{\"cookies\":\"  \",\"refresh_token\":\"rt\"}")
                    is SpecialLoginParser.Result.Failure
        )
    }

    @Test
    fun parse_missingRefreshToken_failure() {
        val result = SpecialLoginParser.parse("{\"cookies\":\"DedeUserID=1\"}")
        assertTrue(result is SpecialLoginParser.Result.Failure)
    }

    @Test
    fun parse_cookiesWithoutDedeUserId_failure() {
        val result = SpecialLoginParser.parse("{\"cookies\":\"SESSDATA=abc\",\"refresh_token\":\"rt\"}")
        assertTrue("cookies 里没有 DedeUserID 应失败", result is SpecialLoginParser.Result.Failure)
    }

    @Test
    fun parse_dedeUserIdNotANumber_failure() {
        val result = SpecialLoginParser.parse("{\"cookies\":\"DedeUserID=abc\",\"refresh_token\":\"rt\"}")
        assertTrue("DedeUserID 不是数字应失败而不是崩溃", result is SpecialLoginParser.Result.Failure)
    }

    @Test
    fun parse_failureMessagesAreNotEmpty() {
        val cases = listOf(
            "",
            "不是json",
            "{\"refresh_token\":\"rt\"}",
            "{\"cookies\":\"SESSDATA=abc\",\"refresh_token\":\"rt\"}",
        )
        for (case in cases) {
            val result = SpecialLoginParser.parse(case)
            assertTrue("应失败：$case", result is SpecialLoginParser.Result.Failure)
            assertTrue("失败原因不能为空", (result as SpecialLoginParser.Result.Failure).message.isNotBlank())
        }
    }

    // ==================== parseAny：单账号 / 多账号 / 裸数组 ====================
    // 多账号格式是本机「一键导出全部账号」后换设备搬家的载体。这组用例锁的是
    // 「导出的东西必须能被自己读回来」以及「一条坏数据不能让整批静默半成功」。

    @Test
    fun parseAny_singleAccount_returnsSingle() {
        val parsed = SpecialLoginParser.parseAny(fullInfo)
        assertTrue("单账号输入应走 Single：$parsed", parsed is SpecialLoginParser.ParseResult.Single)
        assertEquals(12345L, (parsed as SpecialLoginParser.ParseResult.Single).account.mid)
    }

    @Test
    fun parseAny_bareArray_returnsBatch() {
        val raw = """
            [
              {"cookies":"SESSDATA=a; DedeUserID=1","refresh_token":"r1"},
              {"cookies":"SESSDATA=b; DedeUserID=2","refresh_token":"r2"}
            ]
        """.trimIndent()

        val parsed = SpecialLoginParser.parseAny(raw)
        assertTrue(parsed is SpecialLoginParser.ParseResult.Batch)
        parsed as SpecialLoginParser.ParseResult.Batch
        assertEquals(listOf(1L, 2L), parsed.accounts.map { it.mid })
        assertEquals(0, parsed.deduped)
    }

    @Test
    fun parseAny_exportEnvelope_returnsBatchAndKeepsNameAndAvatar() {
        val raw = """
            {"format":"rebili.accounts","version":1,"exported_at":123,
             "accounts":[
               {"mid":1,"name":"甲","avatar":"https://a/1.png","cookies":"SESSDATA=a; DedeUserID=1","refresh_token":"r1","access_key":"k1"},
               {"mid":2,"name":"乙","cookies":"SESSDATA=b; DedeUserID=2","refresh_token":"r2"}
             ]}
        """.trimIndent()

        val parsed = SpecialLoginParser.parseAny(raw) as SpecialLoginParser.ParseResult.Batch
        assertEquals(2, parsed.accounts.size)
        assertEquals("甲", parsed.accounts[0].name)
        assertEquals("https://a/1.png", parsed.accounts[0].avatar)
        assertEquals("k1", parsed.accounts[0].accessKey)
        // 第二条没有 access_key → null（而不是字符串 "null"）
        assertEquals(null, parsed.accounts[1].accessKey)
    }

    @Test
    fun parseAny_duplicateMidInBatch_keepsLastAndCountsDeduped() {
        val raw = """
            [
              {"cookies":"SESSDATA=old; DedeUserID=1","refresh_token":"r-old"},
              {"cookies":"SESSDATA=new; DedeUserID=1","refresh_token":"r-new"}
            ]
        """.trimIndent()

        val parsed = SpecialLoginParser.parseAny(raw) as SpecialLoginParser.ParseResult.Batch

        assertEquals(1, parsed.accounts.size)
        assertEquals(1, parsed.deduped)
        assertEquals("后出现的那条应胜出", "r-new", parsed.accounts[0].refreshToken)
        assertEquals("SESSDATA=new; DedeUserID=1", parsed.accounts[0].cookies)
    }

    @Test
    fun parseAny_declaredMidMismatchCookie_failure() {
        val raw = """{"mid":2,"cookies":"SESSDATA=a; DedeUserID=1","refresh_token":"r"}"""

        val parsed = SpecialLoginParser.parseAny(raw)

        assertTrue("mid 与 Cookie 不一致必须整条拒绝：$parsed", parsed is SpecialLoginParser.ParseResult.Failure)
    }

    @Test
    fun parseAny_oneBrokenAccount_failsWholeBatch() {
        val raw = """
            [
              {"cookies":"SESSDATA=a; DedeUserID=1","refresh_token":"r1"},
              {"cookies":"SESSDATA=b","refresh_token":"r2"}
            ]
        """.trimIndent()

        val parsed = SpecialLoginParser.parseAny(raw)

        assertTrue("不许写半截：只要有一条坏数据就整批失败", parsed is SpecialLoginParser.ParseResult.Failure)
        assertTrue(
            "错误提示要指出是第几个账号",
            (parsed as SpecialLoginParser.ParseResult.Failure).message.contains("第 2 个账号")
        )
    }

    @Test
    fun parseAny_emptyAccountsArray_failure() {
        assertTrue(
            SpecialLoginParser.parseAny("[]") is SpecialLoginParser.ParseResult.Failure
        )
        assertTrue(
            SpecialLoginParser.parseAny("""{"format":"rebili.accounts","accounts":[]}""")
                    is SpecialLoginParser.ParseResult.Failure
        )
    }

    @Test
    fun parseAny_accountsFieldNotArray_failure() {
        assertTrue(
            SpecialLoginParser.parseAny("""{"accounts":"oops"}""")
                    is SpecialLoginParser.ParseResult.Failure
        )
    }

    @Test
    fun parseAny_explicitNullNameAndAvatar_becomeEmptyStrings() {
        val raw = """{"cookies":"SESSDATA=a; DedeUserID=1","refresh_token":"r","name":null,"avatar":null}"""

        val parsed = SpecialLoginParser.parseAny(raw) as SpecialLoginParser.ParseResult.Single

        assertEquals("", parsed.account.name)
        assertEquals("", parsed.account.avatar)
    }

    @Test
    fun parseAny_emptyInput_failure() {
        assertTrue(SpecialLoginParser.parseAny(null) is SpecialLoginParser.ParseResult.Failure)
        assertTrue(SpecialLoginParser.parseAny("   ") is SpecialLoginParser.ParseResult.Failure)
    }
}
