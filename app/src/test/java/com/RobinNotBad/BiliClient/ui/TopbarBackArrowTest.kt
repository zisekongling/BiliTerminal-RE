package com.RobinNotBad.BiliClient.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 顶栏返回箭头的布局硬约束（26.11.16 反馈 #174「反馈与建议页左上角没有返回箭头」）。
 *
 * 本仓库的箭头**不是**独立 `ImageView`，而是 `@id/pageName`（顶栏标题 TextView）的
 * `app:drawableStartCompat`。这是 26.10.25 定下的两半规则（见 `docs/review/fix-progress.md` §3.68）：
 *
 * 1. **返回级页面**（`BaseActivity` 子类）在**布局**里写 `@drawable/arrow_back`；
 * 2. **菜单级一级页**（`InstanceActivity` 子类）布局里**不写**，由
 *    `InstanceActivity.setMenuClick()` 在运行时换成 `@drawable/arrow_down`——
 *    布局文件会被两类页面复用（如 `activity_myspace.xml` 同时被「我的」和「更多」用），
 *    布局里只能写一个箭头，所以角色判定只能在代码里做。
 *
 * 这两半点合起来才成立：少了第一条，返回级页面左上角就空着（#174 就是这么来的——
 * `activity_feedback.xml` 是当时全仓唯一漏了箭头的返回式布局）。
 *
 * 纯 JVM 单测，直接读 `src/main/res/layout` 下的 XML（Gradle 单测工作目录就是 `app/`），
 * 同 `ui/a11y/LayoutAccessibilityTest` 的做法。它守住「新加返回式页面别忘了箭头」这条线，
 * **不证明箭头画出来好看**（大小、与标题的间距、圆屏会不会被切）——那属 L2 人工看画面。
 */
class TopbarBackArrowTest {

    private val layoutDir = File("src/main/res/layout")

    private fun Element.attr(ns: String, name: String): String? =
        getAttributeNS(ns, name).takeIf { it.isNotEmpty() }

    private fun layoutFiles(): List<File> {
        assertTrue("找不到布局目录：${layoutDir.absolutePath}", layoutDir.isDirectory)
        return layoutDir.listFiles { f -> f.isFile && f.name.endsWith(".xml") }!!.sortedBy { it.name }
    }

    private fun pageNameElement(file: File): Element? {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            if (el.attr(ANDROID_NS, "id") == "@+id/pageName") return el
        }
        return null
    }

    private fun startDrawable(el: Element): String? =
        el.attr(AUTO_NS, "drawableStartCompat") ?: el.attr(ANDROID_NS, "drawableStart")

    /**
     * 布局里刻意不写箭头的文件，每个都要说清是哪一类页面：
     * 全部是**菜单级一级页**（`InstanceActivity`）或菜单根页，箭头由
     * `InstanceActivity.setMenuClick()` 在运行时换成 `arrow_down`（或本就不需要箭头）。
     */
    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val AUTO_NS = "http://schemas.android.com/apk/res-auto"

        val NO_LAYOUT_ARROW = mapOf(
            "activity_bangumi.xml" to "BangumiActivity : InstanceActivity（一级页，运行时 arrow_down）",
            "activity_menu.xml" to "MenuActivity：菜单根页，本身没有「上一级」",
            "activity_message.xml" to "MessageActivity : InstanceActivity（一级页，运行时 arrow_down）",
            "activity_search.xml" to "SearchActivity : InstanceActivity（一级页，运行时 arrow_down）",
            "activity_setting_main.xml" to "SettingMainActivity : InstanceActivity（一级页，运行时 arrow_down）",
            "activity_simple_main_refresh.xml" to
                "RefreshMainActivity / Popular / Ranking / Precious / LocalList 等一级页（运行时 arrow_down）"
        )
    }

    @Test
    fun everyReturningPageLayoutDrawsTheBackArrow() {
        val problems = ArrayList<String>()
        for (file in layoutFiles()) {
            val pageName = pageNameElement(file) ?: continue
            if (file.name in NO_LAYOUT_ARROW) continue
            val drawable = startDrawable(pageName)
            when {
                drawable == null -> problems +=
                    "${file.name}: @id/pageName 没有 drawableStartCompat。返回级页面要写 " +
                        "@drawable/arrow_back；若它其实是菜单级一级页，把它加进 NO_LAYOUT_ARROW 并写明原因"

                drawable != "@drawable/arrow_back" ->
                    "${file.name}: @id/pageName 的起始图标是 $drawable，返回级页面应为 @drawable/arrow_back"
            }
        }
        assertEquals("以下顶栏缺返回箭头或图标不对：\n" + problems.joinToString("\n"), 0, problems.size)
    }

    /** 白名单里的文件必须还在：布局改名 / 删除后白名单会静默失效，等于这条线悄悄断了。 */
    @Test
    fun whitelistedLayoutsStillExist() {
        val names = layoutFiles().map { it.name }.toSet()
        val gone = NO_LAYOUT_ARROW.keys.filter { it !in names }
        assertEquals("白名单引用了不存在的布局，请同步 NO_LAYOUT_ARROW：\n" + gone.joinToString("\n"), 0, gone.size)
    }

    /** 扫描本身要有效：至少扫到一批带箭头的顶栏，否则上面的断言是空转。 */
    @Test
    fun arrowIsActuallyFound() {
        val withArrow = layoutFiles().count { f ->
            pageNameElement(f)?.let { startDrawable(it) == "@drawable/arrow_back" } == true
        }
        assertTrue("没有扫描到任何带 @drawable/arrow_back 的布局，测试可能失效（实际 $withArrow）", withArrow > 30)
    }
}
