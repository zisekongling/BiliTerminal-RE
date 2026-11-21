package com.RobinNotBad.BiliClient.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 顶栏标题必须给右侧控件留位（26.11.19 用户截图：设置子页「播放器界面（实验性）」的标题压住右侧
 * 「完整设置」按钮，右边缘直接把字截断、没有省略号）。
 *
 * `@id/pageName` 的省略号靠**两条腿**，缺一条就不生效：
 *
 * 1. **运行时**：`BaseActivity.setRound()` 把 `maxLines = 1` + `TruncateAt.END`；
 * 2. **布局**：标题要有一条**右边界**规则（`android:layout_toStartOf` / `layout_toLeftOf`），
 *    把可读宽度收窄到右侧控件（`@id/timeText` 时钟＝灵动岛胶囊位、`@id/btnFull` 之类的按钮）左边。
 *
 * 少了第 2 条为什么会「既不省略号、又重叠」：RelativeLayout 拿整屏宽度当上界去量标题，
 * 文字比「屏宽 − 左内距」短时 TextView 就按文字宽排完、根本不触发 ellipsize，
 * 于是标题直接铺到右侧控件身上（`activity_setting_player_ui`），
 * 或者从时钟 / 胶囊底下穿过去（`activity_message` / `activity_update`）。
 *
 * 灵动岛不需要额外留白：`TopbarIsland.applyStyle()` 把时钟自己的 `layoutParams.width` 改成
 * 胶囊宽度（72dp，小屏按屏宽 31% 封顶），胶囊背景就画在这个时钟上——所以「收在
 * `@id/timeText` 左边」就等于「收在胶囊左边」。
 *
 * 纯 JVM 单测，直接读 `src/main/res/layout` 下的 XML（Gradle 单测工作目录是 `app/`），
 * 做法同 [TopbarBackArrowTest]。它只守「新页面别漏边界」这条线，**不证明看起来好看**
 * （省略号落在第几个字、12sp 下能放下几个字）——那属 L2 人工看画面。
 */
class TopbarTitleRoomTest {

    private val layoutDir = File("src/main/res/layout")

    private fun Element.attr(ns: String, name: String): String? =
        getAttributeNS(ns, name).takeIf { it.isNotEmpty() }

    private fun layoutFiles(): List<File> {
        assertTrue("找不到布局目录：${layoutDir.absolutePath}", layoutDir.isDirectory)
        return layoutDir.listFiles { f -> f.isFile && f.name.endsWith(".xml") }!!.sortedBy { it.name }
    }

    private fun parse(file: File): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(file)

    private fun elementById(doc: Document, id: String): Element? {
        val nodes = doc.getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            if (el.attr(ANDROID_NS, "id") == id) return el
        }
        return null
    }

    /** `@id/timeText` / `@+id/timeText` / `@id/btnFull` 都归一成裸 id 名。 */
    private fun bareId(ref: String): String = ref.substringAfter('/')

    /**
     * 每份带顶栏标题的布局都必须写右边界规则，且被引用的那个 id 要真的在同一份文件里。
     * 这两条合起来才保证「标题宽度 = 屏宽 − 右邻控件宽」，ellipsize 才有机会生效。
     */
    @Test
    fun everyTopbarTitleKeepsRoomOnTheRight() {
        val problems = ArrayList<String>()
        for (file in layoutFiles()) {
            val doc = parse(file)
            val pageName = elementById(doc, "@+id/pageName") ?: continue
            val rule = pageName.attr(ANDROID_NS, "layout_toStartOf")
                ?: pageName.attr(ANDROID_NS, "layout_toLeftOf")
            if (rule == null) {
                problems += "${file.name}: @id/pageName 没有 layout_toStartOf。长标题会一直铺到右侧控件" +
                    "（时钟 / 灵动岛胶囊 / 按钮）身上且不出现省略号；给右侧那个控件加一条 " +
                    "android:layout_toStartOf=\"@id/…\""
                continue
            }
            val target = bareId(rule)
            if (elementById(doc, "@+id/$target") == null && elementById(doc, "@id/$target") == null) {
                problems += "${file.name}: @id/pageName 的 layout_toStartOf 指向 $rule，但这份布局里没有这个 id"
            }
        }
        assertEquals("以下顶栏标题没有给右侧控件留位：\n" + problems.joinToString("\n"), 0, problems.size)
    }

    /** 扫描本身要有效：扫不到足够多的顶栏标题，上面的断言就是空转。 */
    @Test
    fun theScanActuallyFindsTopbars() {
        val withTitle = layoutFiles().count { elementById(parse(it), "@+id/pageName") != null }
        assertTrue("只扫到 $withTitle 份带 @id/pageName 的布局，测试可能失效", withTitle > 40)
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
