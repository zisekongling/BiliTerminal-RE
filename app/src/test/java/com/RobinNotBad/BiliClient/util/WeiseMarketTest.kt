package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WeiseMarket] 的纯 JVM 单测（26.11.16）。
 *
 * 微思商店的「去微思下载」按钮只在**商店上的版本号与本次检测到的更新版本号一致**时才显示：
 * 那个包是开发者手动上传的，落后一版时把用户导流过去，等于让用户「更新」回旧版本。
 *
 * 接口 `POST https://api.wysteam.cn/market/app/info/`（body `id=2749`）实测返回**扁平 JSON**，
 * `verid` 在顶层；但没找到正式文档，也可能被包一层 `data`，所以两种形状都要能读。
 */
class WeiseMarketTest {

    private val flatBody = """
        {"id":2749,"name":"RE：哔哩终端 by紫色空灵","pack":"com.RobinNotBad.BiliClient.coexist",
         "version":"26.11.16","verid":26111600,"size":7358023,"code":200}
    """.trimIndent()

    @Test
    fun parseVerid_flatJson() {
        assertEquals(
            "实测的真实形状：verid 直接在顶层",
            26111600,
            WeiseMarket.parseVerid(flatBody)
        )
    }

    @Test
    fun parseVerid_wrappedInData() {
        assertEquals(
            "兜底：万一接口改成 {code, data} 包裹",
            26111600,
            WeiseMarket.parseVerid("""{"code":200,"data":{"verid":26111600}}""")
        )
    }

    @Test
    fun parseVerid_missingOrBroken_returnsZero() {
        assertEquals(0, WeiseMarket.parseVerid(null))
        assertEquals(0, WeiseMarket.parseVerid(""))
        assertEquals("不是 JSON 就当作读不到", 0, WeiseMarket.parseVerid("<html>502</html>"))
        assertEquals("没有 verid 字段也是 0", 0, WeiseMarket.parseVerid("""{"code":200}"""))
    }

    @Test
    fun versionMatches_onlyWhenEqualAndPositive() {
        assertTrue(WeiseMarket.versionMatches(26111600, 26111600))
        assertFalse("商店落后一版：不能导流过去", WeiseMarket.versionMatches(26111000, 26111600))
        assertFalse("商店更新（还没发版）：同样不显示", WeiseMarket.versionMatches(26111700, 26111600))
        assertFalse("读不到商店版本时一律不显示", WeiseMarket.versionMatches(0, 26111600))
        assertFalse(WeiseMarket.versionMatches(26111600, 0))
    }

    @Test
    fun pickMarketPackage_prefersWatchPackage() {
        assertEquals(
            "两个都装时用手表端那个（com.qx.wysappmarket）",
            "com.qx.wysappmarket",
            WeiseMarket.pickMarketPackage { it == "com.qx.wysappmarket" || it == "com.wys.appmarket" }
        )
        assertEquals(
            "只装手机端那个也能跳",
            "com.wys.appmarket",
            WeiseMarket.pickMarketPackage { it == "com.wys.appmarket" }
        )
    }

    @Test
    fun pickMarketPackage_noneInstalled_returnsNull() {
        assertNull(
            "没装商店时不能瞎猜包名，交给浏览器兜底",
            WeiseMarket.pickMarketPackage { false }
        )
    }
}
