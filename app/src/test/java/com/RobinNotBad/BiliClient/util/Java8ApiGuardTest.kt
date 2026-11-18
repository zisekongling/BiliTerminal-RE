package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 守一条线：**不能用 API 24+ 才有的 Java 8 类库**（26.11.19）。
 *
 * 来路：26.11.18 用户在下载列表上看到「下载异常：java.lang.NoClassDefFoundError:
 * com.RobinNotBad.BiliClient.ui.widget.e」。那个短名类 R8 解出来是
 * `RotaryEncoderSupport$$ExternalSyntheticLambda2`，实现的是 `java.util.function.Function`——
 * 全 dex 里唯一一个 Java 8 函数接口实现类，源头就是 `ConcurrentHashMap.computeIfAbsent(k) { }`
 * 的 λ。`java.util.function` **API 24 才进 Android**，而 [`app/build.gradle`] 里 minSdk 是 19：
 * API 19~23 的真机上，只要那个 λ 类被加载/链接就抛 NoClassDefFoundError；下载线程走
 * `DownloadProgressStore` 的字节预算与速度采样，所以只有下载报这个错。
 *
 * 这不是第一次踩：`util/SharedPreferencesUtil.java`、`util/TimeUtil.kt`、
 * `util/AsyncLayoutInflaterX.java` 的注释里都记着同一条教训，只是新代码又漏了一遍。
 * 所以把「不许用」写成单测，改坏了当场红。
 *
 * 扫描范围：`src/main/java` 与 vendored 的 `DanmakuFlameMaster/src/main/java`（Gradle 单测
 * 工作目录是 `app/`）。只扫**代码**，注释先剥掉——注释里本来就有好几处拿
 * `java.util.function` 当反面教材，不剥掉会误报。
 *
 * 本条**只管**这些「一碰就炸」的类库令牌；`java.util.Map` 那组默认方法（`putIfAbsent` /
 * `getOrDefault` / `merge` / `replaceAll` / `forEach` / `computeIfAbsent`）同样是 API 24，
 * 但用文本判断不出来接收者到底是 `Map` 还是 `ConcurrentHashMap`（后者的同名成员是 API 1，
 * 可以用），所以只记在 `docs/agents/pitfalls.md` 里靠人守。
 */
class Java8ApiGuardTest {

    /** 一条禁令：令牌 + 为什么。 */
    private data class Ban(val token: String, val why: String)

    private val bans = listOf(
        Ban(".computeIfAbsent(", "它的 λ 是 java.util.function.Function（API 24）；改用 ConcurrentHashMap.putIfAbsent"),
        Ban("java.util.function", "整个包 API 24 才有"),
        Ban("java.util.stream", "整个包 API 24 才有"),
        Ban("java.util.Optional", "API 24 才有"),
        Ban("java.time.", "整个包 API 26 才有"),
        Ban("java.util.Base64", "API 26 才有"),
    )

    private val roots = listOf(
        File("src/main/java"),
        File("../DanmakuFlameMaster/src/main/java"),
    ).filter { it.isDirectory }

    private fun sources(): List<File> =
        roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".java")) }
                .toList()
        }.sortedBy { it.path }

    /** 去掉行注释与块注释，保留换行（行号与原文对齐）；字符串字面量原样留着。 */
    private fun stripComments(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        var inBlock = false
        var inLine = false
        var quote: Char? = null
        while (i < text.length) {
            val c = text[i]
            val next = if (i + 1 < text.length) text[i + 1] else ' '
            if (inLine) {
                if (c == '\n') {
                    inLine = false
                    sb.append(c)
                }
                i++
                continue
            }
            if (inBlock) {
                if (c == '*' && next == '/') {
                    inBlock = false
                    i += 2
                    continue
                }
                if (c == '\n') sb.append(c)
                i++
                continue
            }
            val q = quote
            if (q != null) {
                if (c == '\\' && i + 1 < text.length) {
                    sb.append(c).append(text[i + 1])
                    i += 2
                    continue
                }
                if (c == q) quote = null
                sb.append(c)
                i++
                continue
            }
            when {
                c == '/' && next == '/' -> {
                    inLine = true
                    i += 2
                }
                c == '/' && next == '*' -> {
                    inBlock = true
                    i += 2
                }
                // Kotlin 的 """...""" 与 Java 的文本块：整段原样保留（含换行），
                // 否则里面的引号会把状态机带偏、把后续代码当成字符串吞掉。
                c == '"' && next == '"' && i + 2 < text.length && text[i + 2] == '"' -> {
                    val end = text.indexOf("\"\"\"", i + 3)
                    val stop = if (end < 0) text.length else end + 3
                    sb.append(text, i, stop)
                    i = stop
                }
                c == '"' || c == '\'' -> {
                    quote = c
                    sb.append(c)
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }

    @Test
    fun theScanActuallyFindsSources() {
        val files = sources()
        assertTrue("没找到源码目录，扫描空转：${roots.map { it.absolutePath }}", roots.isNotEmpty())
        assertTrue("扫到的源文件只有 ${files.size} 份，太少，疑似路径错了", files.size > 200)
    }

    @Test
    fun noJava8OnlyApiInMainSources() {
        val problems = mutableListOf<String>()
        for (file in sources()) {
            val lines = stripComments(file.readText()).split("\n")
            for ((index, line) in lines.withIndex()) {
                for (ban in bans) {
                    if (line.contains(ban.token)) {
                        problems += "${file.path}:${index + 1}  命中 ${ban.token}（${ban.why}）"
                    }
                }
            }
        }
        assertTrue(
            "主源码里用了 minSdk 19 跑不了的 Java 8 API（API 19~23 会 NoClassDefFoundError）：\n" +
                problems.joinToString("\n"),
            problems.isEmpty(),
        )
    }
}
