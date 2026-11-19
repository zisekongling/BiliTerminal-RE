package com.RobinNotBad.BiliClient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 守两条线：**debug 包每个页面都能被 adb 直接拉起**，而 release 包一个字节都不变。
 *
 * 来路（26.11.19）：手表上的 UI 自动化又慢又不可靠，「先点主页 → 再点入口 → 才到目标页」
 * 这条路径里任何一次弹窗、布局改版或列表滚动都会让脚本卡住；能 `adb shell am start -n
 * <包名>/.activity.xxx` 直接进页面，验证成本差一个数量级。所以 debug 包把主清单里所有
 * 非导出 Activity 都翻成 exported=true 并挂上 `android.permission.DUMP`（签名级权限，
 * 第三方应用拿不到、adb shell 持有、本应用自己放行），声明全部写在 app/src/debug/
 * AndroidManifest.xml —— release 用的主清单不动，所以 release 的可达面完全不变。
 *
 * 这个测试盯的是「两张清单有没有走神」：
 *  - 主清单新增了页面，却忘了在 debug 清单里补一行 → 当场红（并列出漏了哪个）；
 *  - debug 清单里某个页面漏了 DUMP 权限 → 当场红（那等于真把页面暴露给第三方应用）；
 *  - 有人把 SplashActivity 也挂进去 → 当场红（它是 LAUNCHER，挂权限后桌面拉不起 debug 包）。
 *
 * 注意：私有功能（private/manifest/）里的 Activity 由**构建期注入**的清单声明，公开仓库
 * 没有 private/ 时它们连类都不存在，因此这条测试只管主清单里声明过的页面。
 *
 * Gradle 单测的工作目录是 `app/`，所以路径从 `src/` 起（与 DebugPopupSuppressionTest 同一套约定）。
 */
class DebugActivityExportTest {

    /** 清单里一条 <activity> 的三个属性；没有的属性为 null。 */
    private data class Entry(val name: String, val exported: String?, val permission: String?)

    private fun File.activityEntries(): List<Entry> {
        val xml = readText()
        return Regex("<activity\\b[^>]*>").findAll(xml).map { match ->
            val tag = match.value
            fun attr(qualified: String): String? =
                Regex("$qualified\\s*=\\s*\"([^\"]*)\"").find(tag)?.groupValues?.get(1)
            Entry(
                name = attr("android:name") ?: "",
                exported = attr("android:exported"),
                permission = attr("android:permission"),
            )
        }.toList()
    }

    private val mainManifest = File("src/main/AndroidManifest.xml")
    private val debugManifest = File("src/debug/AndroidManifest.xml")
    private val dumpPermission = "android.permission.DUMP"

    /** 主清单里 export=false（或没写 exported）的页面，就是 debug 包必须额外开放的那批。 */
    private fun closedInMain(): List<Entry> =
        mainManifest.activityEntries().filter { it.exported != "true" }

    @Test
    fun `debug 清单把主清单里每个非导出页面都挂 DUMP 开放了`() {
        assertTrue("找不到 ${mainManifest.path}，路径可能变了", mainManifest.isFile)
        assertTrue("找不到 ${debugManifest.path}，路径可能变了", debugManifest.isFile)

        val closed = closedInMain()
        // 防解析空转：主清单现在有 120 个页面，其中 117 个非导出，解析失败时这条先红。
        assertTrue("主清单只解析出 ${closed.size} 个非导出 Activity，解析八成坏了", closed.size >= 100)

        val byName = debugManifest.activityEntries().filter { it.name.isNotEmpty() }.associateBy { it.name }
        val missing = closed.filter { entry ->
            val open = byName[entry.name]
            open == null || open.exported != "true" || open.permission != dumpPermission
        }
        assertTrue(
            buildString {
                append("这些页面在 debug 包里的声明缺了 exported=true 或 permission=android.permission.DUMP：")
                missing.forEach { append("\n- ").append(it.name) }
            },
            missing.isEmpty()
        )
    }

    @Test
    fun `debug 清单里没有任何不带 DUMP 的导出页面`() {
        val bad = debugManifest.activityEntries()
            .filter { it.name.isNotEmpty() && it.permission != dumpPermission }
        assertTrue(
            buildString {
                append("debug 清单里的导出页面必须挂 android:permission=\"android.permission.DUMP\"：")
                bad.forEach { append("\n- ").append(it.name).append("（permission=").append(it.permission).append("）") }
            },
            bad.isEmpty()
        )
    }

    /**
     * 主清单里已经导出的三个页面（SplashActivity 是 LAUNCHER，另两个带着 intent-filter）
     * 不许被搬进 debug 清单：给 SplashActivity 挂上 DUMP 之后，桌面图标就再也拉不起 debug 包了。
     */
    @Test
    fun `主清单里已经导出的页面不被 debug 清单重复声明`() {
        val alreadyExported = mainManifest.activityEntries()
            .filter { it.exported == "true" }
            .map { it.name }
            .filter { it.isNotEmpty() }
        assertEquals("主清单已导出页面数量变了，本用例的前提要重看", 3, alreadyExported.size)

        val reopened = debugManifest.activityEntries().map { it.name }.filter { it in alreadyExported }
        assertTrue(
            "这些页面 main 里已经导出，debug 清单不该再声明（尤其 SplashActivity 挂 DUMP 会让桌面拉不起调试包）：${reopened}",
            reopened.isEmpty()
        )
    }
}
