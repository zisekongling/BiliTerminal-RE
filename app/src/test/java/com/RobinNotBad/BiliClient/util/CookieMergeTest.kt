package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CookieMerge] 的鉴权键白名单层（O5）。
 *
 * 覆盖的坑都来自真实线上反馈：
 * - 响应里来一条小写 `sessdata=` 就照删 → 下次冷启动判「登录过期」；
 * - 大小写不同的同一个键在本地串里各存一份 → `getInfoFromCookie` 读哪份看顺序；
 * - `ac_time_value` 被空值覆盖 → Cookie 刷新链路静默坏掉、过一阵子掉登录。
 */
class CookieMergeTest {

    private fun cookie(key: String, value: String, expired: Boolean = false) =
        CookieJarPolicy.IncomingCookie(key, value, expired)

    // ==================== 鉴权键识别 ====================

    @Test
    fun isAuthCookie_大小写与两端空白都算命中() {
        assertTrue(CookieMerge.isAuthCookie("SESSDATA"))
        assertTrue(CookieMerge.isAuthCookie("sessdata"))
        assertTrue(CookieMerge.isAuthCookie("  Bili_Jct  "))
        assertTrue(CookieMerge.isAuthCookie("DedeUserID__ckMd5"))
        assertTrue(CookieMerge.isAuthCookie("ac_time_value"))
        assertTrue(CookieMerge.isAuthCookie("AC_TIME_VALUE"))
    }

    @Test
    fun isAuthCookie_普通键与空值不算() {
        assertFalse(CookieMerge.isAuthCookie("buvid3"))
        assertFalse(CookieMerge.isAuthCookie("bili_ticket"))
        assertFalse(CookieMerge.isAuthCookie(""))
        assertFalse(CookieMerge.isAuthCookie("   "))
        assertFalse(CookieMerge.isAuthCookie(null))
    }

    @Test
    fun authCookieNames_六个键全为小写() {
        // 比对依赖「集合里只有小写」，有人写成大写就会让大小写不敏感失效
        for (name in CookieMerge.AUTH_COOKIE_NAMES) {
            assertEquals("鉴权键必须全小写：$name", name, name.lowercase())
        }
        assertEquals(6, CookieMerge.AUTH_COOKIE_NAMES.size)
    }

    // ==================== 删除 / 清空保护 ====================

    @Test
    fun merge_小写sessdata空值不得清除本地大写SESSDATA() {
        val result = CookieMerge.merge(
            "SESSDATA=alive; bili_jct=jct",
            listOf(cookie("sessdata", "", expired = true)),
            allowAuthCookieUpdates = true
        )
        assertEquals("SESSDATA=alive; bili_jct=jct", result.cookies)
        // 记录用的是本地原拼写
        assertEquals(listOf("SESSDATA"), result.protectedKeys)
    }

    @Test
    fun merge_acTimeValue空值不得清除() {
        val result = CookieMerge.merge(
            "ac_time_value=abc; buvid3=b3",
            listOf(cookie("ac_time_value", "", expired = true)),
            allowAuthCookieUpdates = true
        )
        assertEquals("ac_time_value=abc; buvid3=b3", result.cookies)
        assertEquals(listOf("ac_time_value"), result.protectedKeys)
    }

    @Test
    fun merge_acTimeValue非空可以正常更新() {
        val result = CookieMerge.merge(
            "ac_time_value=old",
            listOf(cookie("Ac_Time_Value", "new")),
            allowAuthCookieUpdates = true
        )
        assertEquals("ac_time_value=new", result.cookies)
        assertTrue(result.protectedKeys.isEmpty())
    }

    @Test
    fun merge_本地没有的鉴权键被清空时不算protected() {
        // 本地本来就没有登录态：没什么可保护，也不该刷一条「拒绝删除凭据」的日志
        val result = CookieMerge.merge(
            "buvid3=b3",
            listOf(cookie("SESSDATA", "", expired = true)),
            allowAuthCookieUpdates = true
        )
        assertEquals("buvid3=b3", result.cookies)
        assertTrue(result.protectedKeys.isEmpty())
    }

    // ==================== 大小写归一 ====================

    @Test
    fun merge_大小写不同视为同一个键_更新后只留本地拼写一份() {
        val result = CookieMerge.merge(
            "SESSDATA=old; buvid3=b3",
            listOf(cookie("sessdata", "new")),
            allowAuthCookieUpdates = true
        )
        assertEquals("SESSDATA=new; buvid3=b3", result.cookies)
    }

    @Test
    fun merge_本地没有该键时保留服务端给的原拼写() {
        val result = CookieMerge.merge(
            null,
            listOf(cookie("Buvid3", "abc")),
            allowAuthCookieUpdates = true
        )
        assertEquals("Buvid3=abc", result.cookies)
    }

    // ==================== 更新门控 ====================

    @Test
    fun merge_不允许更新鉴权键时_值不变且记protected() {
        val result = CookieMerge.merge(
            "SESSDATA=old",
            listOf(cookie("sessdata", "hijacked")),
            allowAuthCookieUpdates = false
        )
        assertEquals("SESSDATA=old", result.cookies)
        assertEquals(listOf("SESSDATA"), result.protectedKeys)
    }

    @Test
    fun merge_不允许更新鉴权键时_普通键照旧更新() {
        val result = CookieMerge.merge(
            "SESSDATA=old; buvid3=b3",
            listOf(cookie("sessdata", "hijacked"), cookie("buvid3", "b4")),
            allowAuthCookieUpdates = false
        )
        assertEquals("SESSDATA=old; buvid3=b4", result.cookies)
        assertEquals(listOf("SESSDATA"), result.protectedKeys)
    }

    // ==================== 其余规则仍走 CookieJarPolicy ====================

    @Test
    fun merge_非鉴权键空值走删除语义() {
        val result = CookieMerge.merge(
            "buvid3=b3; bili_ticket=t1",
            listOf(cookie("buvid3", "", expired = true)),
            allowAuthCookieUpdates = true
        )
        assertEquals("bili_ticket=t1", result.cookies)
        assertTrue(result.protectedKeys.isEmpty())
    }

    @Test
    fun merge_空入站原样返回且不带尾随分隔符() {
        val result = CookieMerge.merge("buvid3=b3", emptyList(), allowAuthCookieUpdates = true)
        assertEquals("buvid3=b3", result.cookies)
        assertTrue(result.protectedKeys.isEmpty())
    }

    @Test
    fun merge_incoming为null也能用() {
        val result = CookieMerge.merge("buvid3=b3", null, allowAuthCookieUpdates = true)
        assertEquals("buvid3=b3", result.cookies)
    }

    // ==================== mergeHeaders（原始 Set-Cookie 头） ====================

    @Test
    fun mergeHeaders_分号后不带空格也要切干净() {
        val result = CookieMerge.mergeHeaders(
            null,
            listOf("buvid3=abc;Path=/;Domain=bilibili.com"),
            allowAuthCookieUpdates = true
        )
        assertEquals("buvid3=abc", result.cookies)
    }

    @Test
    fun mergeHeaders_解析不出键的头直接丢弃() {
        val result = CookieMerge.mergeHeaders(
            null,
            listOf("garbage", "=nokey", "buvid3=abc"),
            allowAuthCookieUpdates = true
        )
        assertEquals("buvid3=abc", result.cookies)
    }

    @Test
    fun mergeHeaders_小写凭据空值不动本地登录态() {
        val result = CookieMerge.mergeHeaders(
            "SESSDATA=alive",
            listOf("sessdata=; Max-Age=0; Path=/; Domain=bilibili.com"),
            allowAuthCookieUpdates = true
        )
        assertEquals("SESSDATA=alive", result.cookies)
        assertEquals(listOf("SESSDATA"), result.protectedKeys)
    }
}
