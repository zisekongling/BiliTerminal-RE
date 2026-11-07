package com.RobinNotBad.BiliClient.ui.a11y

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 布局无障碍硬约束（26.11.07 调研清单第 11 条）。
 *
 * 纯 JVM 单测，直接读 `src/main/res/layout` 下的 XML（Gradle 单测的工作目录就是 `app/`）。
 * 它守的是「以后新加图片控件别又把无障碍丢掉」这条线，**不证明 TalkBack 真机可走**——
 * 那要装 Debug 包开 TalkBack 逐屏听，属 L2 交接单。
 *
 * 规则（与 `docs/review/fix-progress.md` §2.4 的处置口径一致）：
 * 1. 每个 `<ImageView>` / `<ImageButton>` 必须显式写 `android:contentDescription`；
 * 2. 装饰图写 `@null` 时，必须同时 `android:importantForAccessibility="no"`（读屏直接跳过）；
 * 3. `<ImageButton>` 或者显式 `android:clickable="true"` 的 `<ImageView>` 是交互控件，
 *    不能只说「这是装饰」——必须给可读文案；
 * 4. 引用的 `@string/a11y_*` / `@string/setting_player_btn_*` 之类必须真实存在于 `strings.xml`
 *    （防止改名后布局里留着一个悬空引用，编译期只报 warning 不报错的情况也有）。
 */
class LayoutAccessibilityTest {

    private val layoutDir = File("src/main/res/layout")
    private val valuesDir = File("src/main/res/values")

    private fun Element.attr(name: String): String? =
        getAttributeNS("http://schemas.android.com/apk/res/android", name).takeIf { it.isNotEmpty() }

    private fun imageElements(file: File): List<Element> {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(file)
        val tags = listOf("ImageView", "ImageButton")
        val nodes = doc.getElementsByTagName("*")
        val result = ArrayList<Element>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            if (el.tagName.substringAfter(':') in tags) result.add(el)
        }
        return result
    }

    private fun layoutFiles(): List<File> {
        assertTrue("找不到布局目录：${layoutDir.absolutePath}", layoutDir.isDirectory)
        return layoutDir.listFiles { f -> f.isFile && f.name.endsWith(".xml") }!!.sortedBy { it.name }
    }

    @Test
    fun everyImageHasExplicitAccessibilityDeclaration() {
        val problems = ArrayList<String>()
        for (file in layoutFiles()) {
            for (el in imageElements(file)) {
                val cd = el.attr("contentDescription")
                if (cd == null) {
                    problems += "${file.name}: <${el.tagName}> 缺 android:contentDescription"
                }
            }
        }
        assertEquals("以下图片控件没写无障碍声明：\n" + problems.joinToString("\n"), 0, problems.size)
    }

    @Test
    fun decorativeImagesAreHiddenFromAccessibility() {
        val problems = ArrayList<String>()
        for (file in layoutFiles()) {
            for (el in imageElements(file)) {
                if (el.attr("contentDescription") != "@null") continue
                if (el.attr("importantForAccessibility") != "no") {
                    problems += "${file.name}: contentDescription=@null 但没写 importantForAccessibility=no"
                }
            }
        }
        assertEquals("装饰图必须对读屏隐藏：\n" + problems.joinToString("\n"), 0, problems.size)
    }

    @Test
    fun interactiveImagesHaveReadableLabel() {
        val problems = ArrayList<String>()
        for (file in layoutFiles()) {
            for (el in imageElements(file)) {
                val interactive = el.tagName.substringAfter(':') == "ImageButton" ||
                    el.attr("clickable") == "true"
                if (!interactive) continue
                val cd = el.attr("contentDescription")
                if (cd == null || cd == "@null") {
                    problems += "${file.name}: 交互控件 <${el.tagName}> 没有可读的 contentDescription"
                }
            }
        }
        assertEquals("交互控件必须有可读描述：\n" + problems.joinToString("\n"), 0, problems.size)
    }

    @Test
    fun referencedAccessibilityStringsExist() {
        // 字符串不只在 strings.xml：播放器队列、社交的移植文案各有自己的 values 文件，
        // 所以要把 values/ 下整批都读进来，别只认一个文件。
        assertTrue("找不到 values 目录：${valuesDir.absolutePath}", valuesDir.isDirectory)
        val declared = HashSet<String>()
        for (file in valuesDir.listFiles { f -> f.isFile && f.name.endsWith(".xml") }!!) {
            Regex("""<string name="([^"]+)"""").findAll(file.readText())
                .forEach { declared += it.groupValues[1] }
        }
        val referenced = LinkedHashMap<String, String>()
        for (file in layoutFiles()) {
            for (el in imageElements(file)) {
                val cd = el.attr("contentDescription") ?: continue
                if (!cd.startsWith("@string/")) continue
                referenced[cd.removePrefix("@string/")] = file.name
            }
        }
        val missing = referenced.filterKeys { it !in declared }
        if (missing.isNotEmpty()) {
            fail("布局引用了不存在的字符串：\n" + missing.entries.joinToString("\n") { "${it.value}: ${it.key}" })
        }
        assertTrue("没有扫描到任何 @string 描述，测试可能失效", referenced.isNotEmpty())
    }
}
