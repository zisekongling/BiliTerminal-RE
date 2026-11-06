package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `Set-Cookie` 合并策略的单测（26.11.06.1「升级后掉登录」返工）。
 *
 * 这组用例锁的是「**任何响应都不能把登录凭据写没**」这条底线：
 * 旧实现只要收到一条 `SESSDATA=`（服务端清 Cookie / Max-Age=0 / Expires 过期）就把本地
 * SESSDATA 覆盖成空串，下一次冷启动 `hasLocalSession()` 判空 → 清空整套凭证 → 用户掉登录。
 */
class CookieJarPolicyTest {

    // ---------------- parse：属性截断、过期语义 ----------------

    @Test
    fun parse_按分号切属性_分号后没有空格也要切干净() {
        val cookie = CookieJarPolicy.parse(
            "SESSDATA=abc123;Path=/;Domain=bilibili.com;HttpOnly;Secure"
        )
        assertEquals("SESSDATA", cookie!!.key)
        assertEquals("值里不能带任何属性", "abc123", cookie.value)
        assertFalse(cookie.expired)
    }

    @Test
    fun parse_值本身含等号时只按第一个等号切() {
        // bili_ticket / buvid3 的值是 base64，末尾常有 '='
        val cookie = CookieJarPolicy.parse("bili_ticket=QUJDRA==; Path=/")
        assertEquals("bili_ticket", cookie!!.key)
        assertEquals("QUJDRA==", cookie.value)
    }

    @Test
    fun parse_MaxAge为0视为删除() {
        assertTrue(CookieJarPolicy.parse("SESSDATA=; Max-Age=0")!!.expired)
        // 负数同样是过期
        assertTrue(CookieJarPolicy.parse("SESSDATA=; Max-Age=-1")!!.expired)
        // 正数不是过期
        assertFalse(CookieJarPolicy.parse("SESSDATA=abc; Max-Age=3600")!!.expired)
    }

    @Test
    fun parse_Expires已过期视为删除_未来时间不算() {
        assertTrue(
            "1970 年的 Expires 必然是过期（服务端删 Cookie 的另一种写法）",
            CookieJarPolicy.parse("SESSDATA=; Expires=Thu, 01 Jan 1970 00:00:00 GMT")!!.expired
        )
        assertFalse(
            CookieJarPolicy.parse("SESSDATA=abc; Expires=Wed, 15 Nov 2033 07:34:10 GMT")!!.expired
        )
    }

    @Test
    fun parse_空值但没有过期语义仍然标记为过期() {
        // 空值不能写进本地串（否则 getInfoFromCookie 取到空串，等于没有）
        assertTrue(CookieJarPolicy.parse("SESSDATA=")!!.expired)
    }

    @Test
    fun parse_非法输入返回null() {
        assertNull(CookieJarPolicy.parse(null))
        assertNull(CookieJarPolicy.parse(""))
        assertNull(CookieJarPolicy.parse("   "))
        assertNull(CookieJarPolicy.parse("=abc"))
        assertNull(CookieJarPolicy.parse("没有等号"))
        assertNull("头里有换行一律拒收（防注入）", CookieJarPolicy.parse("SESSDATA=a\nb"))
    }

    @Test
    fun parseAll_跳过非法条目() {
        val list = CookieJarPolicy.parseAll(
            listOf("SESSDATA=abc; Path=/", "garbage", "buvid3=xyz")
        )
        assertEquals(2, list.size)
        assertEquals("SESSDATA", list[0].key)
        assertEquals("buvid3", list[1].key)
    }

    // ---------------- merge：凭据保护 ----------------

    @Test
    fun merge_普通Cookie正常替换且不带尾随分隔符() {
        val merged = CookieJarPolicy.merge(
            "SESSDATA=old; buvid3=b1",
            listOf(CookieJarPolicy.parse("buvid3=b2; Path=/")!!)
        )
        assertEquals("SESSDATA=old; buvid3=b2", merged.cookies)
        assertFalse("末尾不能留分隔符", merged.cookies.endsWith(";"))
        assertFalse(merged.cookies.endsWith(" "))
        assertTrue(merged.protectedKeys.isEmpty())
    }

    @Test
    fun merge_登录凭据不接受删除_MaxAge为0也保留旧值() {
        val old = "SESSDATA=alive; buvid3=b1"
        val merged = CookieJarPolicy.merge(old, listOf(CookieJarPolicy.parse("SESSDATA=; Max-Age=0")!!))
        assertEquals("SESSDATA 不能被普通响应删掉", "SESSDATA=alive; buvid3=b1", merged.cookies)
        assertEquals(listOf("SESSDATA"), merged.protectedKeys)
    }

    @Test
    fun merge_登录凭据不接受删除_Expires过期同样保留() {
        val old = "SESSDATA=alive; bili_jct=csrf"
        val merged = CookieJarPolicy.merge(
            old,
            listOf(CookieJarPolicy.parse("SESSDATA=; Expires=Thu, 01 Jan 1970 00:00:00 GMT")!!)
        )
        assertEquals(old, merged.cookies)
        assertEquals(listOf("SESSDATA"), merged.protectedKeys)
    }

    @Test
    fun merge_凭据的空值写入被忽略() {
        val merged = CookieJarPolicy.merge("SESSDATA=alive", listOf(CookieJarPolicy.parse("SESSDATA=")!!))
        assertEquals("SESSDATA=alive", merged.cookies)
        assertEquals(listOf("SESSDATA"), merged.protectedKeys)
    }

    @Test
    fun merge_非凭据键的删除正常生效() {
        val merged = CookieJarPolicy.merge(
            "SESSDATA=alive; buvid3=b1; bili_ticket=t1",
            listOf(
                CookieJarPolicy.parse("buvid3=; Max-Age=0")!!,
                CookieJarPolicy.parse("bili_ticket=; Expires=Thu, 01 Jan 1970 00:00:00 GMT")!!
            )
        )
        assertEquals("SESSDATA=alive", merged.cookies)
        assertTrue(merged.protectedKeys.isEmpty())
    }

    @Test
    fun merge_新键追加在末尾_已有键保持原顺序() {
        val merged = CookieJarPolicy.merge(
            "SESSDATA=a; buvid3=b",
            listOf(
                CookieJarPolicy.parse("_uuid=u1")!!,
                CookieJarPolicy.parse("SESSDATA=a2")!!
            )
        )
        assertEquals("SESSDATA=a2; buvid3=b; _uuid=u1", merged.cookies)
    }

    @Test
    fun merge_空底串可以直接写入() {
        val merged = CookieJarPolicy.merge(null, listOf(CookieJarPolicy.parse("SESSDATA=new; Path=/")!!))
        assertEquals("SESSDATA=new", merged.cookies)
    }

    @Test
    fun merge_相同输入输出与旧串完全一致() {
        // 调用方靠 equals 判断「这次响应没动 Cookie」，从而跳过落盘；必须保证幂等、无多余空格
        val current = "SESSDATA=a; buvid3=b"
        val merged = CookieJarPolicy.merge(current, listOf(CookieJarPolicy.parse("buvid3=b")!!))
        assertEquals(current, merged.cookies)
    }

    @Test
    fun merge_容忍旧数据里的尾随分隔符与空值项() {
        // 旧实现会写出 "...; " 以及（历史故障留下的）"SESSDATA="，这里要能自愈成干净串
        val merged = CookieJarPolicy.merge(
            "SESSDATA=a; buvid3=b; ; ",
            emptyList()
        )
        assertEquals("SESSDATA=a; buvid3=b", merged.cookies)
    }

    @Test
    fun merge_旧数据里的空凭据不会被当成有效值保留() {
        // 历史故障可能已把 SESSDATA 写成空：合并层修不了（没有旧值可救），但至少不能继续传下去
        val merged = CookieJarPolicy.merge(
            "SESSDATA=; buvid3=b",
            listOf(CookieJarPolicy.parse("SESSDATA=new")!!)
        )
        assertEquals("buvid3=b; SESSDATA=new", merged.cookies)
    }

    // ---------------- parseCookieString：按第一个等号切分 ----------------

    @Test
    fun parseCookieString_值里的等号保留_空值项跳过() {
        val list = CookieJarPolicy.parseCookieString("a=1; b=QUJD==; c=; ; d")
        assertEquals(2, list.size)
        assertEquals("a" to "1", list[0])
        assertEquals("b" to "QUJD==", list[1])
    }

    @Test
    fun parseCookieString_空串与null返回空表() {
        assertTrue(CookieJarPolicy.parseCookieString(null).isEmpty())
        assertTrue(CookieJarPolicy.parseCookieString("").isEmpty())
    }
}
