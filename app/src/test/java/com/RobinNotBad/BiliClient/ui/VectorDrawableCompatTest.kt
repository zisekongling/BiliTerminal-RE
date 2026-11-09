package com.RobinNotBad.BiliClient.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 矢量图兼容硬约束（26.11.09 扫码登录页崩溃复盘）。
 *
 * 背景：`app/build.gradle` 的 `vectorDrawables { useSupportLibrary true }` 关掉了 API<21 的 PNG
 * 兜底（`drawable/` 与 `drawable-v21/` 有同名双份，关掉这个开关 AAPT 会在 mergeResources 撞
 * `Duplicate resources`），所以 `<vector>` 只有 `AppCompatResources` / `VectorDrawableCompat` 能解析。
 *
 * 而 `android:` 命名空间的这些属性是由**控件构造函数自己**调 `Resources.getDrawable` 加载的，
 * 根本不经过 AppCompat：
 * - API<21 抛 `Resources$NotFoundException: File res/xx.xml ... invalid drawable tag vector`
 *   （26.11.09 用户反馈：教程走完进登录页就崩，`fragment_qr_login.xml` 的 `android:src="@drawable/icon_help"`）；
 * - API≥21 才认 `<vector>`，所以这类坑只在老设备上炸，本机没有 API 19/20 镜像，只能靠这条单测守。
 *
 * 规则：`res/layout*` 与 `res/drawable*` 里，凡是上面这些属性引用了「含矢量」的 drawable 就失败。
 * 正确写法是把属性换到 app 命名空间的 Compat 版（`app:srcCompat` / `app:drawableStartCompat` …），
 * 或者用自读 Compat 属性的控件（`ui/widget/VectorImageView.kt`）。
 *
 * 纯 JVM 单测，直接读 `src/main/res`（Gradle 单测的工作目录就是 `app/`）。
 */
class VectorDrawableCompatTest {

    private val resDir = File("src/main/res")
    private val androidNs = "http://schemas.android.com/apk/res/android"

    /** 由控件构造函数（或 drawable 容器）直接 `Resources.loadDrawable` 的属性。 */
    private val nativeLoadAttrs = setOf(
        "src",
        "background",
        "drawable",
        "drawableStart",
        "drawableEnd",
        "drawableLeft",
        "drawableRight",
        "drawableTop",
        "drawableBottom",
        "thumb",
        "progressDrawable",
        "indeterminateDrawable",
    )

    /**
     * 白名单：这些文件里的 `android:` 直引矢量是**有意的**，改之前先读原因。
     * 键是相对 `res/` 的路径，值是原因。
     */
    private val allowList = mapOf(
        // 只做 SeekBar 的 thumb 用：SDK≥21 由 HighEnergyProgressBar 用 ResourcesCompat 加载（原生就支持矢量），
        // SDK<21 走手搓 StateListDrawable 直接取 player_tv_white / player_tv_blue，不会加载这个 selector。
        "drawable/player_selector_progress.xml" to "只在 API>=21 加载（见 HighEnergyProgressBar.thumb）",
    )

    private fun read(p: File): String = p.readText()

    private fun drawableFiles(): List<File> {
        assertTrue("找不到 res 目录：${resDir.absolutePath}", resDir.isDirectory)
        return resDir.listFiles { f -> f.isDirectory && f.name.startsWith("drawable") }!!
            .flatMap { dir -> dir.listFiles { f -> f.isFile && f.name.endsWith(".xml") }!!.asList() }
            .sortedBy { it.absolutePath }
    }

    private fun layoutFiles(): List<File> {
        val dir = File(resDir, "layout")
        assertTrue("找不到布局目录：${dir.absolutePath}", dir.isDirectory)
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".xml") }!!.sortedBy { it.name }
    }

    /** 含 `<vector>` 的 drawable 名字。 */
    private fun vectorNames(files: List<File>): Set<String> {
        return files.filter { "<vector" in read(it) }
            .map { it.nameWithoutExtension }
            .toSet()
    }

    /** 复合 drawable（selector / layer-list）里引用了矢量的，也算「含矢量」。 */
    private fun withComposite(files: List<File>, vectors: MutableSet<String>) {
        var changed = true
        while (changed) {
            changed = false
            for (file in files) {
                val name = file.nameWithoutExtension
                if (name in vectors) continue
                val text = read(file)
                for (ref in Regex("""@(?:drawable|mipmap)/([A-Za-z0-9_]+)""").findAll(text).map { it.groupValues[1] }) {
                    if (ref in vectors) {
                        vectors += name
                        changed = true
                        break
                    }
                }
            }
        }
    }

    private fun referencedDrawable(value: String): String? {
        val m = Regex("""^@(?:drawable|mipmap)/([A-Za-z0-9_]+)$""").find(value.trim()) ?: return null
        return m.groupValues[1]
    }

    private fun relPath(file: File): String = file.absolutePath
        .substringAfter(resDir.absolutePath + File.separator)
        .replace(File.separatorChar, '/')

    private fun androidAttr(el: Element, name: String): String? =
        el.getAttributeNS(androidNs, name).takeIf { it.isNotEmpty() }

    private fun elements(file: File): List<Element> {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("*")
        val result = ArrayList<Element>(nodes.length)
        for (i in 0 until nodes.length) {
            (nodes.item(i) as? Element)?.let { result.add(it) }
        }
        return result
    }

    @Test
    fun nativeNamespaceDoesNotLoadVectorDrawables() {
        val files = drawableFiles()
        val vectors = vectorNames(files).toMutableSet()
        withComposite(files, vectors)
        assertTrue("没找到任何矢量图，扫描逻辑可能坏了", vectors.isNotEmpty())

        val problems = ArrayList<String>()
        for (file in layoutFiles() + files) {
            val rel = relPath(file)
            if (rel in allowList) continue
            for (el in elements(file)) {
                for (attr in nativeLoadAttrs) {
                    val value = androidAttr(el, attr) ?: continue
                    val name = referencedDrawable(value) ?: continue
                    if (name in vectors) {
                        problems += "$rel: <${el.tagName.substringAfterLast('.')}> android:$attr=\"$value\"" +
                            " —— API<21 会崩在原生 Resources.loadDrawable，改用 app:*Compat 或 VectorImage* 控件"
                    }
                }
            }
        }
        assertEquals(
            "以下位置用 android: 命名空间直引了矢量图（API<21 必崩）：\n" + problems.joinToString("\n"),
            0,
            problems.size,
        )
    }

    @Test
    fun allowListEntriesStillExist() {
        // 白名单会随着文件改名/删除而失效，失效后必须回来删掉它，否则等于悄悄放宽规则。
        for ((path, reason) in allowList) {
            assertTrue("白名单里的 $path 已不存在（原因：$reason），请清理 allowList", File(resDir, path).isFile)
        }
    }
}
