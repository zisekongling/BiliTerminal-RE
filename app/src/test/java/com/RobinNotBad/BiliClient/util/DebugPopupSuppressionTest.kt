package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 守一条线：**debug 包不许自动弹教程与信息弹窗**（26.11.19）。
 *
 * 来路：调试包每次冷启动都被一串弹窗按固定顺序糊满屏幕——更新日志 → 公告 → 全屏教程 →
 * 隐私说明 → 免责声明，手测和页面自动化都得先手动点掉才看得到目标页。
 * `docs/review/fix-progress.md` §2「26.11.16 反馈页顶栏补返回箭头（#174）」的 L1 就是因为
 * 「首启弹窗串行占屏」没能在模拟器里打开目标页。
 *
 * 门面是 `BiliTerminal.isAutoPopupSuppressed()`（= `BuildConfig.DEBUG`），各调用点自己判一次。
 * 这里把「哪些地方必须判」钉死：新加一个自动弹窗忘了判、或者有人把门面改成别的判据，当场红。
 *
 * 扫的是源码 token 而不是运行期行为——它只能保证「写在这里的调用点还带着门」，不能证明
 * 弹窗真的没弹出来（那要上设备，见 docs/agents/testing.md 的 L1/L2）。
 *
 * Gradle 单测的工作目录是 `app/`，所以下面路径都从 `src/main/java` 起（与
 * `Java8ApiGuardTest` 同一套约定）。
 */
class DebugPopupSuppressionTest {

    /** 一个必须带门的调用点：路径 + 必须出现的源码片段 + 它保护的是什么。 */
    private data class Site(val path: String, val needle: String, val what: String)

    private val sites = listOf(
        Site(
            "src/main/java/com/RobinNotBad/BiliClient/BiliTerminal.kt",
            "fun isAutoPopupSuppressed(): Boolean = BuildConfig.DEBUG",
            "门面本身：必须仍然跟随 BuildConfig.DEBUG（不是 BETA，也不是运行期开关）"
        ),
        Site(
            "src/main/java/com/RobinNotBad/BiliClient/activity/base/BaseActivity.kt",
            "if (BiliTerminal.isAutoPopupSuppressed()) return",
            "自动教程：全屏 GUIDE/NOTICE 与页面滑动 HINT 都在 autoTriggerTutorial 里"
        ),
        Site(
            "src/main/java/com/RobinNotBad/BiliClient/activity/SplashActivity.kt",
            "pendingUpdateLog = !BiliTerminal.isAutoPopupSuppressed() && shouldShowUpdateLogAfterUpgrade()",
            "升级后首启自动打开的更新日志页"
        ),
        Site(
            "src/main/java/com/RobinNotBad/BiliClient/activity/SplashActivity.kt",
            "if (BiliTerminal.isAutoPopupSuppressed() || !TerminalApi.needsPrivacyConsent())",
            "隐私说明闸门（debug 直接进主流程，且不写「已同意」）"
        ),
        Site(
            "src/main/java/com/RobinNotBad/BiliClient/api/AppInfoApi.java",
            "if (BiliTerminal.isAutoPopupSuppressed()) return;",
            "showStartupNotices：免责声明与「夜深了」提醒"
        ),
        Site(
            "src/main/java/com/RobinNotBad/BiliClient/api/AppInfoApi.java",
            "if (ToolsUtil.isDebugBuild() && !BiliTerminal.isAutoPopupSuppressed())",
            "check：BETA 通道包的警告"
        ),
        Site(
            "src/main/java/com/RobinNotBad/BiliClient/api/TerminalApi.java",
            "if (showPopup) {",
            "checkAnnouncement：公告弹窗（已读计数仍要推进，所以门只拦 showText，不在方法开头返回）"
        ),
        Site(
            "src/main/java/com/RobinNotBad/BiliClient/activity/player/PlayerActivity.kt",
            "if (BiliTerminal.isAutoPopupSuppressed()) return",
            "maybeShowSkipGuide：片头片尾跳过引导"
        ),
        Site(
            "src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt",
            "if (!BiliTerminal.isAutoPopupSuppressed() && SharedPreferencesUtil.getBoolean(\"first_play\", true))",
            "playClick：第一次点播放的说明（也不许吞掉这次点击）"
        ),
    )

    @Test
    fun `每个自动弹窗调用点都还带着门`() {
        val missing = sites.filterNot { site ->
            val file = File(site.path)
            file.isFile && file.readText().contains(site.needle)
        }
        assertTrue(
            buildString {
                append("这些调用点没有带 BiliTerminal.isAutoPopupSuppressed() 门（或门面判据被改了）：")
                missing.forEach { append("\n- ").append(it.path).append("：").append(it.what) }
            },
            missing.isEmpty()
        )
    }

    /**
     * 三条**刻意保留**的，不许被门挡掉（见 BiliTerminal.isAutoPopupSuppressed 的 KDoc）：
     * 强制更新拦截是功能性拦截不是弹窗；首启初始化向导是整页向导；下载时的通知权限是系统能力
     * （用户明确要求留着，测权限流程要用）。前两条没有开关可查，这条只盯得住第三个。
     */
    @Test
    fun `刻意保留的下载通知权限请求没有被门挡掉`() {
        val file = File("src/main/java/com/RobinNotBad/BiliClient/activity/video/info/DownloadNotificationGate.kt")
        assertTrue("找不到 ${file.path}，路径可能变了", file.isFile)
        assertTrue(
            "下载时的通知权限请求是刻意保留的（要留着测权限流程），不要给它加 isAutoPopupSuppressed",
            !file.readText().contains("isAutoPopupSuppressed")
        )
    }
}
