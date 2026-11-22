package com.RobinNotBad.BiliClient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 守 `AGENTS.md`「安全红线」里那些**没有任何构建步骤会拦住**的条目。
 *
 * 来路（26.11.22）：红线原先只以文字形式存在，违反它既不会编译失败也不会让单测变红，
 * 只能指望改动者记得读过文档。能机器判断的部分放进这里，判据一律对齐
 * `docs/agents/security.md`（该分册是权威，本测试只是它的可执行版本）。
 *
 * 与 `DebugActivityExportTest` 的分工：那边守「debug 清单有没有走神」，
 * 且只用 `assertEquals(3, ...)` 验**数量**；数量相等时把 `GetIntentActivity` 换成
 * 另一个页面仍然能全绿，所以「到底是哪三个」放在这里验。
 *
 * 路径约定同 `DebugActivityExportTest`：Gradle 单测工作目录是 `app/`，因此从 `src/` 起算。
 */
class SecurityRedLineTest {

    private val mainManifest = File("src/main/AndroidManifest.xml")
    private val networkConfig = File("src/main/res/xml/network_security_config.xml")

    /** `AGENTS.md` 与 `docs/agents/security.md` 共同写死的四个明文白名单域名；加域名必须先改文档并写理由。 */
    private val allowedCleartextDomains = setOf(
        "bilibili.com",
        "hdslb.com",
        "bilivideo.com",
        "afdiancdn.com",
    )

    /** Release 包允许对外可达的三个页面，理由见 `docs/agents/security.md`。 */
    private val allowedExportedActivities = setOf(
        ".activity.SplashActivity",
        ".activity.GetIntentActivity",
        ".activity.player.ExternalVideoActivity",
    )

    @Test
    fun `主清单 allowBackup 关闭且保留 tools_replace`() {
        assertTrue("找不到 ${mainManifest.path}，路径可能变了", mainManifest.isFile)

        val application = Regex("<application\\b[^>]*>").find(mainManifest.readText())?.value
            ?: throw AssertionError("主清单里找不到 <application>")

        assertTrue(
            "allowBackup 必须是 false：登录态（SESSDATA 等 Cookie）就在 SharedPreferences 里，\n" +
                "开着备份等于让系统把它同步出去。当前 <application>：\n$application",
            Regex("android:allowBackup\\s*=\\s*\"false\"").containsMatchIn(application)
        )
        assertTrue(
            "必须保留 tools:replace=\"android:allowBackup\"：依赖库的清单可能声明 allowBackup=true，\n" +
                "合并时不 override 就会被改回去。当前 <application>：\n$application",
            Regex("tools:replace\\s*=\\s*\"[^\"]*android:allowBackup").containsMatchIn(application)
        )
    }

    @Test
    fun `明文流量默认禁止且只放行约定的四个域名`() {
        assertTrue("找不到 ${networkConfig.path}，路径可能变了", networkConfig.isFile)
        val xml = networkConfig.readText()

        val baseConfig = Regex("<base-config\\b[^>]*>").find(xml)?.value
            ?: throw AssertionError("network_security_config.xml 里找不到 <base-config>")
        assertTrue(
            "默认必须是禁止明文（cleartextTrafficPermitted=\"false\"）：否则任意域名的请求都能被中间人读改。\n" +
                "当前 <base-config>：$baseConfig",
            Regex("cleartextTrafficPermitted\\s*=\\s*\"false\"").containsMatchIn(baseConfig)
        )

        // 只统计「允许明文」的 domain-config 块里的域名：新增一个域名就必然进这个集合。
        val permittedDomains = Regex("<domain-config\\b[^>]*>[\\s\\S]*?</domain-config>")
            .findAll(xml)
            .filter { Regex("cleartextTrafficPermitted\\s*=\\s*\"true\"").containsMatchIn(it.value) }
            .flatMap { block ->
                Regex("<domain\\b[^>]*>\\s*([^<]+?)\\s*</domain>")
                    .findAll(block.value)
                    .map { it.groupValues[1].trim() }
            }
            .toSet()

        assertEquals(
            "允许明文的域名变了。加域名等于放宽安全边界，必须先改 AGENTS.md / docs/agents/security.md " +
                "并写清理由，再改这里与守门测试：",
            allowedCleartextDomains,
            permittedDomains
        )
    }

    @Test
    fun `主清单对外可达的页面就是约定的三个`() {
        assertTrue("找不到 ${mainManifest.path}，路径可能变了", mainManifest.isFile)

        val exported = Regex("<activity\\b[^>]*>")
            .findAll(mainManifest.readText())
            .map { it.value }
            .filter { Regex("android:exported\\s*=\\s*\"true\"").containsMatchIn(it) }
            .map { tag -> Regex("android:name\\s*=\\s*\"([^\"]*)\"").find(tag)?.groupValues?.get(1) ?: "" }
            .filter { it.isNotEmpty() }
            .toSet()

        assertEquals(
            "Release 包的导出面变了。新增 Activity 一律 exported=\"false\"；" +
                "确实要对外（LAUNCHER / 外链 / 打开方式）才开 true，并同步 docs/agents/security.md：",
            allowedExportedActivities,
            exported
        )
    }
}
